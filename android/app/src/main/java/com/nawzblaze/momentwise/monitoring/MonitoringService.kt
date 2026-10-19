package com.nawzblaze.momentwise.monitoring

import android.app.Notification
import android.app.Service
import android.app.usage.UsageStatsManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nawzblaze.momentwise.MainActivity
import com.nawzblaze.momentwise.MomentwiseApplication
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.intervention.DetectorSignal
import com.nawzblaze.momentwise.intervention.EventWindow
import com.nawzblaze.momentwise.intervention.DetectorState
import com.nawzblaze.momentwise.intervention.GuardCommand
import com.nawzblaze.momentwise.intervention.PauseActivity
import com.nawzblaze.momentwise.intervention.PauseRequest
import com.nawzblaze.momentwise.intervention.SessionClock
import com.nawzblaze.momentwise.intervention.UsageEventReducer
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Polls Usage Access while at least one rule is enabled and the grants still
 * exist. A one-second poll is not instant. An empty event window does not
 * clear the last package.
 */
class MonitoringService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var detector = DetectorState()
    @Volatile
    private var pauseVisible = false

    @Volatile
    private var stopRequested = false

    @Volatile
    private var rulesLoaded = false

    @Volatile
    private var pausedByUser = false

    @Volatile
    private var enabledRules: List<AppRule> = emptyList()

    private var collectorsStarted = false
    private var launcherPackage: String? = null
    private var launcherReadAtMillis = 0L
    private var ticksSinceBeat = 0

    /** One recovery per process. An empty usage window does not count as a decision. */
    private var recoveredUnanswered = false

    private val gate = Any()
    private var startGeneration = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MonitorPresence.markStartAttempt(this, System.currentTimeMillis())
        if (!startAsForeground()) return START_NOT_STICKY
        PausePresenter.onDismissed = { pauseVisible = false }
        PausePresenter.onShown = { pauseVisible = true }
        startCollectors()
        synchronized(gate) {
            if (pollJob?.isActive == true && !stopRequested) return START_STICKY
            stopRequested = false
            startGeneration += 1
            pollJob?.cancel()
            pollJob = scope.launch { runPoll() }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping Momentwise away is not a request to stop guarding.
        // Re-post the ongoing notification. Do not start a restart loop.
        if (!stopRequested) {
            startAsForeground()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        PausePresenter.dismiss(this)
        super.onDestroy()
    }

    private fun startCollectors() {
        if (collectorsStarted) return
        collectorsStarted = true
        scope.launch {
            app().container.appRuleRepository.rules.collect { rules ->
                enabledRules = rules.filter { it.enabled }
                rulesLoaded = true
            }
        }
        scope.launch {
            app().container.appSettingsRepository.monitoringPaused.collect { paused ->
                pausedByUser = paused
            }
        }
    }

    private suspend fun runPoll() {
        if (BootFlags.dropSessionOnNextStart) {
            BootFlags.dropSessionOnNextStart = false
            sessions().update { SessionClock.onBoot(it) }
        }
        if (!stillAllowed()) {
            stopQuietly()
            return
        }
        awaitRules()
        seedAndRecover()
        while (coroutineContext[Job]?.isActive != false && !stopRequested) {
            tick()
            if (stopRequested) break
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private suspend fun awaitRules() {
        val deadline = System.currentTimeMillis() + 3_000L
        while (!rulesLoaded && System.currentTimeMillis() < deadline && !stopRequested) {
            delay(50)
        }
    }

    /**
     * Establish the foreground from a short recent window, without treating
     * Momentwise itself as an opening. If a guarded app is already in front
     * and no opening was shown for it, show that opening. A one-second poll
     * cannot see a resume that happened while this process was dead.
     */
    private suspend fun seedAndRecover() {
        val now = System.currentTimeMillis()
        val observed = readEvents(EventWindow.seedStart(now), now)
        detector = UsageEventReducer.reduce(
            state = DetectorState(),
            events = observed,
            selfPackage = packageName,
            ignoredPackages = ignoredPackages(now),
            ignoreSelf = false,
            pauseClassName = PauseActivity::class.java.name,
        ).state
        val snapshot = sessions().current()
        val reconciled = SessionClock.reconcileAfterRestart(snapshot, detector.lastPackage)
        if (reconciled != snapshot) sessions().save(reconciled)
        showMissedOpening(detector.lastPackage)
        maybeRecoverUnanswered(detector.lastPackage, now)
        MonitorPresence.beat(this, now)
    }

    private suspend fun tick() {
        if (stopRequested) return
        if (!stillAllowed()) {
            stopQuietly()
            return
        }
        if (!rulesLoaded) return
        val enabled = enabledRules
        if (enabled.isEmpty()) {
            stopQuietly()
            return
        }
        val now = System.currentTimeMillis()
        noteBeat(now)
        val today = LocalDate.now().toString()
        val events = readEvents(EventWindow.queryStart(now), now)
        val reduced = UsageEventReducer.reduce(
            state = detector,
            events = events,
            selfPackage = packageName,
            ignoredPackages = ignoredPackages(now),
            ignoreSelf = pauseVisible,
            pauseClassName = PauseActivity::class.java.name,
        )
        detector = reduced.state
        var snapshot = SessionClock.clearStaleDismissals(sessions().current(), today)

        val active = snapshot.activePackage
        if (active != null && enabled.none { it.appId == active }) {
            snapshot = SessionClock.onLeft(snapshot).first
            sessions().save(snapshot)
            dismissPause()
        }

        when (reduced.signal) {
            DetectorSignal.OPENED -> {
                val pkg = reduced.packageName
                if (pkg != null) {
                    val rule = enabled.find { it.appId == pkg }
                    val (next, command) = SessionClock.onOpened(snapshot, pkg, rule != null, today)
                    sessions().save(next)
                    if (command is GuardCommand.ShowOpening && rule != null) {
                        showPause(rule, PauseRequest.Kind.OPENING)
                    } else if (rule == null) {
                        dismissPause()
                    }
                }
            }
            DetectorSignal.LEFT -> {
                val (next, _) = SessionClock.onLeft(snapshot)
                sessions().save(next)
                dismissPause()
            }
            DetectorSignal.NONE -> {
                if (showMissedOpening(detector.lastPackage)) return
                val rule = enabled.find { it.appId == detector.lastPackage }
                val (next, command) = SessionClock.onTick(
                    snapshot = snapshot,
                    foregroundPackage = detector.lastPackage,
                    nowMillis = now,
                    today = today,
                    remindersEnabled = rule?.reminderFrequency?.isEnabled == true,
                )
                if (next != snapshot) sessions().save(next)
                if (command is GuardCommand.ShowReminder && rule != null) {
                    showPause(rule, PauseRequest.Kind.REMINDER)
                }
            }
        }
        maybeRecoverUnanswered(detector.lastPackage, now)
    }

    /**
     * Re-present a pause the user had not answered when the process died.
     * Skipped once a foreground package has been seen, and skipped if a new
     * pause was just shown. This does not start a new opening.
     */
    private suspend fun maybeRecoverUnanswered(foreground: String?, nowMillis: Long) {
        if (recoveredUnanswered || foreground == null) return
        recoveredUnanswered = true
        if (pauseVisible || stopRequested) return
        val today = LocalDate.now().toString()
        val rules = app().container.appRuleRepository.rules.first()
        val rule = rules.find { it.enabled && it.appId == foreground }
        val (next, command) = SessionClock.recoverUnansweredPause(
            snapshot = sessions().current(),
            foregroundPackage = foreground,
            nowMillis = nowMillis,
            today = today,
            remindersEnabled = rule?.reminderFrequency?.isEnabled == true,
        )
        if (next != sessions().current()) sessions().save(next)
        when (command) {
            is GuardCommand.ShowOpening -> if (rule != null) showPause(rule, PauseRequest.Kind.OPENING)
            is GuardCommand.ShowReminder -> if (rule != null) showPause(rule, PauseRequest.Kind.REMINDER)
            GuardCommand.None -> Unit
        }
    }

    private suspend fun showPause(rule: AppRule, kind: PauseRequest.Kind) {
        if (pauseVisible) dismissPause()
        val request = if (kind == PauseRequest.Kind.OPENING) {
            PauseRequest(
                packageName = rule.appId,
                appName = rule.appName,
                kind = kind,
                notice = rule.openingNotice,
                primaryLabel = rule.quitOption.label,
                secondaryLabel = rule.continueOption.label,
            )
        } else {
            PauseRequest(
                packageName = rule.appId,
                appName = rule.appName,
                kind = kind,
                notice = rule.reminderNotice,
                primaryLabel = rule.reminderActions.quitLabel,
                secondaryLabel = rule.reminderActions.snoozeLabel,
                tertiaryLabel = rule.reminderActions.dismissLabel,
                snoozeMinutes = rule.reminderActions.snoozeMinutes,
            )
        }
        val theme = app().container.appSettingsRepository.themeMode.first().name
        pauseVisible = true
        Log.i(TAG, "showing $kind for ${rule.appId}")
        noteDebug("showing $kind for ${rule.appId}")
        withContext(Dispatchers.Main) {
            PausePresenter.show(this@MonitoringService, request, theme)
        }
    }

    private suspend fun dismissPause() {
        if (!pauseVisible) return
        pauseVisible = false
        withContext(Dispatchers.Main) { PausePresenter.dismiss(this@MonitoringService) }
    }

    private fun readEvents(from: Long, to: Long): List<com.nawzblaze.momentwise.intervention.ForegroundEvent> {
        if (!GrantChecks.read(this).usageAccess) return emptyList()
        val usage = getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        return try {
            UsageEventReader.read(usage, from, to)
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * @return true when an opening was shown, so the caller must not also
     * schedule a reminder on this tick.
     */
    private suspend fun showMissedOpening(foreground: String?): Boolean {
        val rule = enabledRules.find { it.appId == foreground }
        val snapshot = sessions().current()
        if (!SessionClock.needsOpening(snapshot, foreground, rule != null)) return false
        val (next, command) = SessionClock.onOpened(snapshot, foreground!!, true, LocalDate.now().toString())
        sessions().save(next)
        if (command is GuardCommand.ShowOpening && rule != null) {
            showPause(rule, PauseRequest.Kind.OPENING)
        }
        return command is GuardCommand.ShowOpening
    }

    private fun ignoredPackages(nowMillis: Long): Set<String> {
        if (launcherPackage == null || nowMillis - launcherReadAtMillis > LAUNCHER_CACHE_MILLIS) {
            launcherPackage = app().container.installedApps.defaultLauncherPackage()
            launcherReadAtMillis = nowMillis
        }
        return setOfNotNull(launcherPackage, "com.android.systemui", "android")
    }

    private fun noteBeat(nowMillis: Long) {
        ticksSinceBeat += 1
        if (ticksSinceBeat < BEAT_EVERY_TICKS) return
        ticksSinceBeat = 0
        MonitorPresence.beat(this, nowMillis)
    }

    private suspend fun stillAllowed(): Boolean {
        if (pausedByUser) return false
        if (!rulesLoaded) return true
        if (enabledRules.isEmpty()) return false
        return GrantChecks.read(this).canMonitor
    }

    private fun startAsForeground(): Boolean {
        val notification = monitoringNotification()
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NotificationChannels.MONITORING_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NotificationChannels.MONITORING_NOTIFICATION_ID, notification)
            }
            noteDebug("foreground started")
            true
        } catch (error: Exception) {
            Log.w(TAG, "startForeground failed", error)
            noteDebug("foreground failed ${error.javaClass.simpleName}: ${error.message}")
            MonitorPresence.markStartFailed(this, System.currentTimeMillis())
            stopSelf()
            false
        }
    }

    private fun monitoringNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.MONITORING)
            .setSmallIcon(R.drawable.ic_stat_pause)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_body))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppPendingIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun openAppPendingIntent(): PendingIntent {
        val open = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        return PendingIntent.getActivity(
            this,
            1,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun stopQuietly() {
        val generation = synchronized(gate) {
            if (stopRequested) return
            stopRequested = true
            startGeneration
        }
        dismissPause()
        withContext(Dispatchers.Main) {
            val stillCurrent = synchronized(gate) { generation == startGeneration }
            if (!stillCurrent) return@withContext
            MonitorPresence.markStopped(this@MonitoringService)
            ServiceCompat.stopForeground(this@MonitoringService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun noteDebug(line: String) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        try {
            openFileOutput("debug-status.txt", MODE_APPEND).bufferedWriter().use { it.appendLine(line) }
        } catch (_: Exception) {
            // Diagnosis only. A missing note must not stop guarding.
        }
    }

    private fun app(): MomentwiseApplication = application as MomentwiseApplication

    private fun sessions() = app().container.guardSessions

    companion object {
        private const val TAG = "Momentwise"
        private const val POLL_INTERVAL_MILLIS = 1_000L
        private const val LAUNCHER_CACHE_MILLIS = 30_000L
        private const val BEAT_EVERY_TICKS = 5

        fun start(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: Exception) {
                // A background start can be rejected. The next time Momentwise
                // is in the foreground, or the boot receiver, tries again.
                Log.w(TAG, "foreground start rejected", error)
                MonitorPresence.markStartFailed(context, System.currentTimeMillis())
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
