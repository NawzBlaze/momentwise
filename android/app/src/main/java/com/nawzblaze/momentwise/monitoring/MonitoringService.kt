package com.nawzblaze.momentwise.monitoring

import android.app.ActivityManager
import android.app.Notification
import android.app.Service
import android.app.usage.UsageStatsManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
import com.nawzblaze.momentwise.intervention.GuardSnapshot
import com.nawzblaze.momentwise.intervention.InterventionSurface
import com.nawzblaze.momentwise.intervention.PauseActivity
import com.nawzblaze.momentwise.intervention.PauseRequest
import com.nawzblaze.momentwise.intervention.PauseShowPolicy
import com.nawzblaze.momentwise.intervention.SessionClock
import com.nawzblaze.momentwise.intervention.UsageEventReducer
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    /** The user swiped the pause away. Do not immediately put the same one back. */
    private var pauseClearedByUser = false

    @Volatile
    private var sessionCache: GuardSnapshot = GuardSnapshot()

    @Volatile
    private var sessionLoaded = false

    private var grants: GrantStatus? = null
    private var grantsReadAtMillis = 0L
    private var usageQueryCount = 0
    private var usageQueryNanos = 0L
    private val screenOn = Channel<Unit>(Channel.CONFLATED)
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                screenOn.trySend(Unit)
            }
        }
    }

    private val gate = Any()
    private var startGeneration = 0

    override fun onCreate() {
        super.onCreate()
        // API 34 kills the process if startForeground is late. Do it before the
        // receiver registration, and again in onStartCommand.
        startAsForeground()
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground before SharedPreferences. The first prefs read can
        // hit disk, and API 34 kills the process if this call is late.
        if (!startAsForeground()) return START_NOT_STICKY
        MonitorPresence.markStartAttempt(this, System.currentTimeMillis())
        PausePresenter.onDismissed = { reason ->
            pauseVisible = false
            when (reason) {
                PauseDismissReason.CHOICE -> {
                    pauseClearedByUser = false
                    detector = UsageEventReducer.clearPendingStop(detector)
                }
                PauseDismissReason.LEFT, PauseDismissReason.STOPPED -> pauseClearedByUser = false
                PauseDismissReason.WITHOUT_CHOICE -> {
                    // Same visit only. A later OPENED does not consult this flag.
                    pauseClearedByUser = true
                    val pkg = sessionCache.activePackage
                    scope.launch { recordClearedWithoutChoice(pkg) }
                }
            }
        }
        PausePresenter.onShown = { pauseVisible = true }
        startCollectors()
        synchronized(gate) {
            if (!PollSupervisor.shouldLaunch(pollJob?.isActive == true, stopRequested)) {
                return START_STICKY
            }
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
        PausePresenter.dismiss(this, PauseDismissReason.STOPPED)
        pollJob?.cancel()
        scope.cancel()
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
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
        scope.launch {
            sessions().session.collect { snapshot ->
                sessionCache = snapshot
                sessionLoaded = true
            }
        }
    }

    private suspend fun runPoll() {
        if (BootFlags.dropSessionOnNextStart) {
            BootFlags.dropSessionOnNextStart = false
            sessions().update { SessionClock.onBoot(it) }
            sessionCache = sessions().current()
            sessionLoaded = true
        }
        if (!stillAllowed()) {
            stopQuietly()
            return
        }
        awaitRules()
        awaitSession()
        seedAndRecover()
        var resumeFromScreenOff = false
        while (coroutineContext[Job]?.isActive != false && !stopRequested) {
            if (!screenInteractive()) {
                if (!resumeFromScreenOff) noteDebug("screen off")
                resumeFromScreenOff = true
                withTimeoutOrNull(UsagePoll.SCREEN_OFF_INTERVAL_MILLIS) {
                    screenOn.receive()
                }
                continue
            }
            if (resumeFromScreenOff) {
                resumeFromScreenOff = false
                seedAndRecover()
            }
            tick()
            if (stopRequested) break
            delay(UsagePoll.INTERVAL_MILLIS)
        }
    }

    private suspend fun awaitSession() {
        val deadline = System.currentTimeMillis() + 3_000L
        while (!sessionLoaded && System.currentTimeMillis() < deadline && !stopRequested) {
            delay(50)
        }
        if (!sessionLoaded) {
            sessionCache = sessions().current()
            sessionLoaded = true
        }
    }

    private fun screenInteractive(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive != false

    private fun grantsNow(nowMillis: Long): GrantStatus {
        val cached = grants
        if (cached != null && !UsagePoll.shouldRefreshGrants(grantsReadAtMillis, nowMillis)) {
            return cached
        }
        val read = GrantChecks.read(this)
        grants = read
        grantsReadAtMillis = nowMillis
        return read
    }

    private suspend fun awaitRules() {
        val deadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < deadline && !stopRequested) {
            if (rulesLoaded && enabledRules.isNotEmpty()) return
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
        val snapshot = sessionCache
        val reconciled = SessionClock.reconcileAfterRestart(snapshot, detector.lastPackage)
        persistIfUnchanged(snapshot, reconciled)
        noteDebug(
            "seed events=${observed.size} last=${detector.lastPackage} phase=${sessionCache.phase} screen=${screenInteractive()}",
        )
        showMissedOpening(detector.lastPackage, sessionCache)
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
        val events = readEvents(EventWindow.steadyStart(now, detector.lastEventTimeMillis), now)
        val reduced = UsageEventReducer.reduce(
            state = detector,
            events = events,
            selfPackage = packageName,
            ignoredPackages = ignoredPackages(now),
            ignoreSelf = pauseVisible,
            pauseClassName = PauseActivity::class.java.name,
            // An overlay does not stop the app underneath. A pause activity does.
            // Only the activity may hold a stop, or closing it looks like a leave
            // and the opening is stacked again immediately.
            suspendStops = PausePresenter.surface == PauseSurface.ACTIVITY,
        )
        val signal = reduced.signal
        val signaledPackage = reduced.packageName
        detector = reduced.state
        if (signal != DetectorSignal.NONE) {
            noteDebug("tick $signal pkg=$signaledPackage last=${detector.lastPackage}")
        }
        var snapshot = SessionClock.clearStaleDismissals(sessionCache, today)

        val active = snapshot.activePackage
        if (active != null && enabled.none { it.appId == active }) {
            val left = SessionClock.onLeft(snapshot).first
            if (persistIfUnchanged(snapshot, left)) snapshot = left
            dismissPause(PauseDismissReason.STOPPED)
        }

        when (signal) {
            DetectorSignal.OPENED -> {
                val pkg = signaledPackage
                if (pkg != null) {
                    // A new visit. A no-choice clear of the previous visit must not block this.
                    pauseClearedByUser = false
                    // The collector can still be on an empty emission when the
                    // resume arrives. Read the repository before dropping it.
                    val rule = ruleFor(pkg)
                    val (next, command) = SessionClock.onOpened(snapshot, pkg, rule != null, today)
                    val applied = persistIfUnchanged(snapshot, next)
                    noteDebug(
                        "opened pkg=$pkg rule=${rule != null} applied=$applied cmd=${command.javaClass.simpleName}",
                    )
                    if (command is GuardCommand.ShowOpening && rule != null) {
                        // A lost session write must not drop the pause. The other
                        // writer may have recorded the opening and then been cancelled
                        // before the cover or notification was added.
                        if (PauseShowPolicy.shouldShowNewOpening(pauseVisible, alreadyContinuing(pkg))) {
                            showPause(rule, PauseRequest.Kind.OPENING)
                        }
                    } else if (rule == null) {
                        dismissPause(PauseDismissReason.LEFT)
                    }
                }
            }
            DetectorSignal.LEFT -> {
                val (next, _) = SessionClock.onLeft(snapshot)
                persistIfUnchanged(snapshot, next)
                dismissPause(PauseDismissReason.LEFT)
                pauseClearedByUser = false
            }
            DetectorSignal.NONE -> {
                if (showUnshownOpening(detector.lastPackage)) return
                if (showMissedOpening(detector.lastPackage, snapshot)) return
                val rule = enabled.find { it.appId == detector.lastPackage }
                val (next, command) = SessionClock.onTick(
                    snapshot = snapshot,
                    foregroundPackage = detector.lastPackage,
                    nowMillis = now,
                    today = today,
                    remindersEnabled = rule?.reminderFrequency?.isEnabled == true,
                )
                val applied = persistIfUnchanged(snapshot, next)
                if (applied && command is GuardCommand.ShowReminder && rule != null) {
                    showPause(rule, PauseRequest.Kind.REMINDER)
                }
            }
        }
        maybeRecoverUnanswered(detector.lastPackage, now)
    }

    /**
     * Writes only when the stored session is still the one this decision used.
     * A Leave or Continue that landed during the tick is not overwritten.
     * An unchanged snapshot does not touch DataStore.
     */
    private suspend fun persistIfUnchanged(base: GuardSnapshot, next: GuardSnapshot): Boolean {
        if (base == next) return true
        var applied = false
        sessions().update { current ->
            if (current == base) {
                applied = true
                next
            } else {
                current
            }
        }
        if (applied) sessionCache = next
        return applied
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
        val rule = enabledRules.find { it.appId == foreground }
        val snapshot = sessionCache
        val (next, command) = SessionClock.recoverUnansweredPause(
            snapshot = snapshot,
            foregroundPackage = foreground,
            nowMillis = nowMillis,
            today = today,
            remindersEnabled = rule?.reminderFrequency?.isEnabled == true,
        )
        persistIfUnchanged(snapshot, next)
        when (command) {
            is GuardCommand.ShowOpening -> if (rule != null) showPause(rule, PauseRequest.Kind.OPENING)
            is GuardCommand.ShowReminder -> if (rule != null) showPause(rule, PauseRequest.Kind.REMINDER)
            GuardCommand.None -> Unit
        }
    }

    private suspend fun showPause(rule: AppRule, kind: PauseRequest.Kind) {
        if (pauseVisible) dismissPause(PauseDismissReason.STOPPED)
        val request = if (kind == PauseRequest.Kind.OPENING) {
            PauseRequest(
                packageName = rule.appId,
                appName = rule.appName,
                kind = kind,
                notice = rule.openingNotice,
                heading = InterventionSurface.OPENING_HEADING,
                primaryLabel = rule.quitOption.label,
                secondaryLabel = rule.continueOption.label,
                primarySupport = InterventionSurface.LEAVE_SUPPORT,
                secondarySupport = InterventionSurface.continueSupport(
                    rule.reminderFrequency.minutesOrNull(),
                ),
            )
        } else {
            PauseRequest(
                packageName = rule.appId,
                appName = rule.appName,
                kind = kind,
                notice = rule.reminderNotice,
                heading = InterventionSurface.REMINDER_HEADING,
                primaryLabel = rule.reminderActions.quitLabel,
                secondaryLabel = rule.reminderActions.snoozeLabel,
                tertiaryLabel = rule.reminderActions.dismissLabel,
                primarySupport = InterventionSurface.LEAVE_SUPPORT,
                secondarySupport = InterventionSurface.snoozeSupport(rule.reminderActions.snoozeMinutes),
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

    private suspend fun dismissPause(reason: PauseDismissReason) {
        if (!pauseVisible) return
        pauseVisible = false
        withContext(Dispatchers.Main) { PausePresenter.dismiss(this@MonitoringService, reason) }
    }

    private suspend fun recordClearedWithoutChoice(packageName: String?) {
        // A newer opening already cleared the latch. Do not stamp that visit.
        if (!pauseClearedByUser) return
        val now = System.currentTimeMillis()
        sessions().update { current ->
            if (packageName != null && current.activePackage != packageName) current
            else SessionClock.onClearedWithoutChoice(current, now)
        }
        sessionCache = sessions().current()
    }

    private fun readEvents(from: Long, to: Long): List<com.nawzblaze.momentwise.intervention.ForegroundEvent> {
        if (!grantsNow(to).usageAccess) return emptyList()
        val usage = getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val started = System.nanoTime()
        val events = try {
            UsageEventReader.read(usage, from, to)
        } catch (_: SecurityException) {
            emptyList()
        }
        usageQueryCount += 1
        usageQueryNanos += System.nanoTime() - started
        if (UsageQueryDiagnostics.shouldRecord(usageQueryCount)) {
            val mean = UsageQueryDiagnostics.meanMillis(usageQueryNanos, usageQueryCount)
            val line = "usageQuery count=$usageQueryCount meanMs=$mean windowMs=${to - from}"
            Log.i(TAG, line)
            noteDebug(line)
        }
        return events
    }

    /**
     * The session says an opening is waiting, but nothing is on screen.
     * Happens when the poll is cancelled after the write and before the UI.
     * A no-choice clear is not this case: do not stack the same visit again.
     *
     * @return true when an opening was shown, so the caller must not also
     * schedule a reminder on this tick.
     */
    private suspend fun showUnshownOpening(foreground: String?): Boolean {
        val snapshot = sessionCache
        if (!PauseShowPolicy.shouldRecoverSameVisit(
                pauseVisible = pauseVisible,
                clearedWithoutChoice = pauseClearedByUser || snapshot.clearedWithoutChoice,
                phase = snapshot.phase,
                activePackage = snapshot.activePackage,
                foreground = foreground,
            )
        ) {
            return false
        }
        val rule = ruleFor(foreground!!) ?: return false
        showPause(rule, PauseRequest.Kind.OPENING)
        return true
    }

    private suspend fun alreadyContinuing(packageName: String): Boolean {
        val current = sessions().current()
        return current.phase == GuardSnapshot.Phase.CONTINUING && current.activePackage == packageName
    }

    private suspend fun ruleFor(packageName: String): AppRule? {
        enabledRules.find { it.appId == packageName }?.let { return it }
        val fresh = app().container.appRuleRepository.rules.first().filter { it.enabled }
        if (fresh.isNotEmpty()) {
            enabledRules = fresh
            rulesLoaded = true
        }
        return fresh.find { it.appId == packageName }
    }

    private suspend fun showMissedOpening(foreground: String?, snapshot: GuardSnapshot): Boolean {
        val rule = foreground?.let { ruleFor(it) }
        if (!SessionClock.needsOpening(snapshot, foreground, rule != null)) return false
        val (next, command) = SessionClock.onOpened(snapshot, foreground!!, true, LocalDate.now().toString())
        if (!persistIfUnchanged(snapshot, next)) return false
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
        noteDebug("beat last=${detector.lastPackage} phase=${sessionCache.phase}")
    }

    private suspend fun stillAllowed(): Boolean {
        if (pausedByUser) return false
        if (!rulesLoaded) return true
        if (enabledRules.isEmpty()) return false
        return grantsNow(System.currentTimeMillis()).canMonitor
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
            .setOnlyAlertOnce(true)
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

    /**
     * Monitoring is no longer allowed. End the visit so a leftover timer does
     * not fire as a reminder when monitoring starts again. Saved rules stay.
     */
    private suspend fun endVisitBecauseMonitoringStopped() {
        val current = sessionCache
        if (current.phase == GuardSnapshot.Phase.NONE && current.activePackage == null) return
        sessions().update { SessionClock.onLeft(it).first }
        sessionCache = sessions().current()
    }

    private suspend fun stopQuietly() {
        val generation = synchronized(gate) {
            if (stopRequested) return
            stopRequested = true
            startGeneration
        }
        endVisitBecauseMonitoringStopped()
        dismissPause(PauseDismissReason.STOPPED)
        withContext(Dispatchers.Main) {
            val stillCurrent = synchronized(gate) { generation == startGeneration }
            if (!stillCurrent) return@withContext
            MonitorPresence.markStopped(this@MonitoringService)
            ServiceCompat.stopForeground(this@MonitoringService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun noteDebug(line: String) {
        DebugNotes.append(this, line)
    }

    private fun app(): MomentwiseApplication = application as MomentwiseApplication

    private fun sessions() = app().container.guardSessions

    companion object {
        private const val TAG = "Momentwise"
        private const val LAUNCHER_CACHE_MILLIS = 30_000L
        private const val BEAT_EVERY_TICKS = 5

        fun start(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            try {
                if (processIsForeground(context)) {
                    // The activity is up. startForegroundService's short deadline
                    // is what API 34 turns into a process crash if the main thread
                    // is busy. An ordinary start still promotes in onStartCommand.
                    context.startService(intent)
                } else {
                    ContextCompat.startForegroundService(context, intent)
                }
            } catch (error: Exception) {
                // A background start can be rejected. The next time Momentwise
                // is in the foreground, or the boot receiver, tries again.
                Log.w(TAG, "foreground start rejected", error)
                MonitorPresence.markStartFailed(context, System.currentTimeMillis())
            }
        }

        private fun processIsForeground(context: Context): Boolean {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return false
            val mine = android.os.Process.myPid()
            return manager.runningAppProcesses.orEmpty().any { process ->
                process.pid == mine &&
                    process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
