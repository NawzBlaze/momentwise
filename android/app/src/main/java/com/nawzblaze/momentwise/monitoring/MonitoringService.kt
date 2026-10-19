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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nawzblaze.momentwise.MainActivity
import com.nawzblaze.momentwise.MomentwiseApplication
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.intervention.DetectorSignal
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
    private var lastQueryMillis = 0L
    @Volatile
    private var pauseVisible = false

    @Volatile
    private var stopRequested = false

    /** One recovery per process. An empty usage window does not count as a decision. */
    private var recoveredUnanswered = false

    private val gate = Any()
    private var startGeneration = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startAsForeground()) return START_NOT_STICKY
        PausePresenter.onDismissed = { pauseVisible = false }
        PausePresenter.onShown = { pauseVisible = true }
        synchronized(gate) {
            if (pollJob?.isActive == true && !stopRequested) return START_STICKY
            stopRequested = false
            startGeneration += 1
            pollJob?.cancel()
            pollJob = scope.launch { runPoll() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        PausePresenter.dismiss(this)
        super.onDestroy()
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
        seedWithoutFiring()
        while (coroutineContext[Job]?.isActive != false && !stopRequested) {
            tick()
            if (stopRequested) break
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private suspend fun seedWithoutFiring() {
        val now = System.currentTimeMillis()
        val observed = readEvents(now - SEED_WINDOW_MILLIS, now)
        detector = UsageEventReducer.reduce(
            state = DetectorState(),
            events = observed,
            selfPackage = packageName,
            ignoredPackages = ignoredPackages(),
            ignoreSelf = false,
            pauseClassName = PauseActivity::class.java.name,
        ).state
        lastQueryMillis = now
        val snapshot = sessions().current()
        val reconciled = SessionClock.reconcileAfterRestart(snapshot, detector.lastPackage)
        if (reconciled != snapshot) sessions().save(reconciled)
        maybeRecoverUnanswered(detector.lastPackage, now)
    }

    private suspend fun tick() {
        if (stopRequested) return
        if (!stillAllowed()) {
            stopQuietly()
            return
        }
        val rules = app().container.appRuleRepository.rules.first()
        val enabled = rules.filter { it.enabled }
        if (enabled.isEmpty()) {
            stopQuietly()
            return
        }
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val events = readEvents((lastQueryMillis - OVERLAP_MILLIS).coerceAtLeast(0), now)
        lastQueryMillis = now
        val reduced = UsageEventReducer.reduce(
            state = detector,
            events = events,
            selfPackage = packageName,
            ignoredPackages = ignoredPackages(),
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

    private fun ignoredPackages(): Set<String> {
        val launcher = app().container.installedApps.defaultLauncherPackage()
        return setOfNotNull(launcher, "com.android.systemui", "android")
    }

    private suspend fun stillAllowed(): Boolean {
        val grants = GrantChecks.read(this)
        val anyEnabled = app().container.appRuleRepository.rules.first().any { it.enabled }
        return anyEnabled && grants.canMonitor
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
            true
        } catch (_: Exception) {
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
            ServiceCompat.stopForeground(this@MonitoringService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun app(): MomentwiseApplication = application as MomentwiseApplication

    private fun sessions() = app().container.guardSessions

    companion object {
        private const val POLL_INTERVAL_MILLIS = 1_000L
        private const val SEED_WINDOW_MILLIS = 5_000L
        private const val OVERLAP_MILLIS = 1_000L

        fun start(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // A background start can be rejected. The next time Momentwise
                // is in the foreground, or the boot receiver, tries again.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
