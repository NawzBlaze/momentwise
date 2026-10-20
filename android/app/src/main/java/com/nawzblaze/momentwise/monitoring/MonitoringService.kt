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
import com.nawzblaze.momentwise.intervention.VisitMachine
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

    /** Overlay handback and the visit id of the card currently shown. */
    private var machineLatch = VisitMachine.Latch()

    /**
     * The system removed an opening twice before the visit write was visible.
     * Do not show that package again until it has actually left the foreground.
     */
    private var openingHeldPackage: String? = null

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
        PausePresenter.onDismissed = { reason, visitId ->
            pauseVisible = false
            val now = System.currentTimeMillis()
            when (reason) {
                PauseDismissReason.CHOICE -> {
                    pauseClearedByUser = false
                    machineLatch = VisitMachine.onChoiceDismissed(
                        machineLatch,
                        sessionCache.activePackage,
                        now,
                    )
                    detector = UsageEventReducer.clearPendingStop(detector)
                }
                PauseDismissReason.LEFT, PauseDismissReason.STOPPED -> {
                    pauseClearedByUser = false
                    machineLatch = machineLatch.copy(overlayNoiseUntilMillis = now, handbackPackage = null)
                }
                PauseDismissReason.WITHOUT_CHOICE -> {
                    detector = UsageEventReducer.clearPendingStop(detector)
                    val removal = VisitMachine.onSystemRemoved(sessionCache, machineLatch, visitId, now)
                    val action = VisitMachine.unpersistedRemoval(
                        phase = sessionCache.phase,
                        action = removal.action,
                        shownPackage = shownPackage,
                        systemRemovals = machineLatch.systemRemovals,
                    )
                    machineLatch = if (action == VisitMachine.Removal.RESHOW && removal.action == VisitMachine.Removal.IGNORE) {
                        removal.latch.copy(
                            handbackPackage = shownPackage ?: removal.latch.handbackPackage,
                            overlayNoiseUntilMillis = now,
                            systemRemovals = machineLatch.systemRemovals + 1,
                        )
                    } else {
                        removal.latch
                    }
                    when (action) {
                        VisitMachine.Removal.IGNORE -> {
                            pauseClearedByUser = false
                            if (shownPackage != null && machineLatch.systemRemovals >= 1 &&
                                sessionCache.phase != GuardSnapshot.Phase.CONTINUING
                            ) {
                                openingHeldPackage = shownPackage
                            }
                        }
                        VisitMachine.Removal.RESHOW -> {
                            pauseClearedByUser = false
                            val pkg = removal.packageName ?: shownPackage
                            val rule = pkg?.let { id -> enabledRules.find { it.appId == id } }
                            if (rule != null) {
                                scope.launch { showPause(rule, PauseRequest.Kind.OPENING) }
                            }
                        }
                        VisitMachine.Removal.RECORD_NO_CHOICE -> {
                            pauseClearedByUser = true
                            openingHeldPackage = removal.packageName ?: shownPackage
                            scope.launch { recordClearedWithoutChoice(removal.packageName, removal.visitId) }
                        }
                    }
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
                // Storage omits the accrual anchor. A blind replace made every
                // poll look like the first sample, so a reminder never arrived.
                val adopted = SessionClock.adoptStored(snapshot, sessionCache)
                sessionCache = adopted
                sessionLoaded = true
                if (pauseVisible &&
                    adopted.visitId == 0L &&
                    adopted.phase == GuardSnapshot.Phase.NONE &&
                    adopted.activePackage == null &&
                    adopted.lastGuardedPackage == null
                ) {
                    // A test or settings reset cleared the session. The pause flag
                    // is process-local, so it would swallow the next opening.
                    scope.launch { dismissPause(PauseDismissReason.STOPPED) }
                }
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
        seedAndRecover(afterProcessStart = true)
        var resumeFromScreenOff = false
        while (coroutineContext[Job]?.isActive != false && !stopRequested) {
            if (!screenInteractive()) {
                if (!resumeFromScreenOff) {
                    noteDebug("screen off")
                    accrueUse(LocalDate.now().toString(), System.currentTimeMillis())
                }
                resumeFromScreenOff = true
                withTimeoutOrNull(UsagePoll.SCREEN_OFF_INTERVAL_MILLIS) {
                    screenOn.receive()
                }
                continue
            }
            if (resumeFromScreenOff) {
                resumeFromScreenOff = false
                seedAndRecover(afterProcessStart = false)
            }
            try {
                tick()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "tick failed", error)
                noteDebug("tick failed ${error.javaClass.simpleName}: ${error.message}")
            }
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
    private suspend fun seedAndRecover(afterProcessStart: Boolean) {
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
        val stored = sessions().current()
        sessionCache = SessionClock.adoptStored(stored, sessionCache)
        val elapsedNow = android.os.SystemClock.elapsedRealtime()
        val reconciled = if (!afterProcessStart &&
            sessionCache.phase != GuardSnapshot.Phase.NONE &&
            detector.lastPackage != null &&
            detector.lastPackage != sessionCache.activePackage
        ) {
            // The screen woke on the launcher. That is a temporary leave.
            SessionClock.onTemporarilyAway(sessionCache, elapsedNow)
        } else {
            SessionClock.reconcileAfterRestart(sessionCache, detector.lastPackage, elapsedNow)
        }
        if (reconciled.phase != sessionCache.phase) {
            noteDebug("seed ended last=${detector.lastPackage} away=${sessionCache.backgroundedAtElapsed} boot=$afterProcessStart")
        }
        persistIfUnchanged(sessionCache, reconciled)
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
        if (!rulesLoaded || enabledRules.isEmpty()) {
            val fresh = app().container.appRuleRepository.rules.first().filter { it.enabled }
            if (fresh.isEmpty()) {
                stopQuietly()
                return
            }
            enabledRules = fresh
            rulesLoaded = true
        }
        val enabled = enabledRules
        val now = System.currentTimeMillis()
        noteBeat(now)
        val today = LocalDate.now().toString()
        // Disk is the visit decision. The in-memory copy keeps the accrual anchor,
        // which storage deliberately does not. Reloading disk alone made every
        // sample look like the first one, so reminders never accumulated.
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val adopted = SessionClock.adoptStored(sessions().current(), sessionCache)
        val expired = SessionClock.expireGrace(adopted, elapsed)
        if (expired != adopted) persistIfUnchanged(adopted, expired)
        sessionCache = expired
        if (VisitMachine.shouldDropLatch(sessionCache, pauseVisible)) {
            machineLatch = VisitMachine.Latch()
        }
        val events = readEvents(EventWindow.steadyStart(now, detector.lastEventTimeMillis), now)
        val reduced = UsageEventReducer.reduce(
            state = detector,
            events = events,
            selfPackage = packageName,
            ignoredPackages = ignoredPackages(now),
            ignoreSelf = pauseVisible,
            pauseClassName = PauseActivity::class.java.name,
            // Both the overlay and the pause activity can stop the app underneath.
            // Hold that stop while the pause is visible so it is not a leave.
            suspendStops = pauseVisible,
        )
        val signal = reduced.signal
        val signaledPackage = reduced.packageName
        detector = reduced.state
        // A pause flag with no open visit is stale. It must not swallow a new opening.
        val pauseForPolicy = pauseVisible &&
            (sessionCache.phase != GuardSnapshot.Phase.NONE || sessionCache.activePackage != null)
        val heldNoise = VisitMachine.isOverlayNoise(
            pauseVisible = pauseForPolicy,
            handback = machineLatch.handbackPackage,
            noiseUntil = machineLatch.overlayNoiseUntilMillis,
            signal = signal,
            signaledPackage = signaledPackage,
            signalTimeMillis = reduced.signalTimeMillis,
            selfPackage = packageName,
        )
        val leftHeld = signal == DetectorSignal.LEFT &&
            (signaledPackage == null || signaledPackage == openingHeldPackage)
        val openedSomethingElse = signal == DetectorSignal.OPENED &&
            signaledPackage != null && signaledPackage != openingHeldPackage
        if (openingHeldPackage != null && !pauseVisible && !heldNoise && (leftHeld || openedSomethingElse)) {
            openingHeldPackage = null
        }
        if (signal != DetectorSignal.NONE) {
            noteDebug(
                "tick $signal pkg=$signaledPackage last=${detector.lastPackage} pause=$pauseVisible visit=${sessionCache.visitId}",
            )
        }
        var snapshot = SessionClock.clearStaleDismissals(sessionCache, today)
        val active = snapshot.activePackage
        if (active != null && enabled.none { it.appId == active }) {
            val left = SessionClock.onLeft(snapshot, elapsed).first
            if (persistIfUnchanged(snapshot, left)) snapshot = left
            machineLatch = machineLatch.copy(handbackPackage = null, systemRemovals = 0)
            dismissPause(PauseDismissReason.STOPPED)
        }

        val suppressed = snapshot.quitSuppressedPackage
        if (suppressed != null && !pauseForPolicy) {
            val leftForSomethingElse = detector.lastPackage != null &&
                detector.lastPackage != suppressed &&
                detector.lastPackage != packageName
            val laterEntry = signal == DetectorSignal.OPENED &&
                signaledPackage == suppressed &&
                !heldNoise
            if (leftForSomethingElse || laterEntry) {
                val finished = SessionClock.finishExplicitLeave(snapshot)
                if (persistIfUnchanged(snapshot, finished)) snapshot = finished
            }
        }

        val hasRule = signaledPackage?.let { pkg -> ruleFor(pkg) != null } == true
        val step = VisitMachine.onForeground(
            snapshot = snapshot,
            latch = machineLatch,
            signal = signal,
            signaledPackage = signaledPackage,
            signalTimeMillis = reduced.signalTimeMillis,
            resumedPackages = events.mapNotNull { event ->
                if (event.kind == com.nawzblaze.momentwise.intervention.ForegroundEvent.Kind.RESUMED) {
                    event.packageName
                } else {
                    null
                }
            }.toSet(),
            pauseVisible = pauseForPolicy,
            selfPackage = packageName,
            today = today,
            signaledHasRule = hasRule,
            elapsedRealtime = elapsed,
        )
        // A show latch is adopted only when the card is actually shown. Adopting
        // it here made a skipped show look like overlay handback on the next poll.
        if (step.ui != VisitMachine.Ui.SHOW_OPENING) {
            machineLatch = step.latch
        }
        var decided = step.snapshot
        if (!pauseVisible &&
            detector.lastPackage != null &&
            detector.lastPackage == decided.activePackage &&
            decided.backgroundedAtElapsed != null
        ) {
            // A return can be a repeated resume, which is not a new OPENED.
            // Clear grace anyway, or the minute can expire while they are back.
            decided = decided.copy(backgroundedAtElapsed = null)
        }
        val persisted = persistIfUnchanged(snapshot, decided)
        if (persisted) snapshot = decided
        if (signal != DetectorSignal.NONE) {
            Log.i(TAG, "visit ui=${step.ui} signal=$signal pkg=$signaledPackage pause=$pauseVisible")
            noteDebug(
                "tick decide ui=${step.ui} phase=${snapshot.phase} active=${snapshot.activePackage} " +
                    "away=${snapshot.recentAwayPackage}/${snapshot.lastGuardedPackage}@${snapshot.recentAwayAtElapsed} elapsed=$elapsed " +
                    "hasRule=$hasRule persisted=$persisted visit=${snapshot.visitId}",
            )
        }
        if (!persisted && decided != snapshot) {
            // A choice landed during this tick. Decide again from the stored session.
            sessionCache = sessions().current()
            presentWaitingOpening()
            accrueUse(today, now)
            return
        }
        when (step.ui) {
            VisitMachine.Ui.SHOW_OPENING -> {
                val pkg = step.uiPackage
                if (pkg != null) {
                    pauseClearedByUser = false
                    val rule = ruleFor(pkg)
                    noteDebug(
                        "opened pkg=$pkg rule=${rule != null} visit=${snapshot.visitId} " +
                            "phase=${snapshot.phase} away=${snapshot.backgroundedAtElapsed}",
                    )
                    if (rule != null &&
                        PauseShowPolicy.shouldShowNewOpening(pauseVisible, alreadyContinuing(pkg))
                    ) {
                        showPause(rule, PauseRequest.Kind.OPENING)
                    }
                }
            }
            VisitMachine.Ui.DISMISS -> {
                dismissPause(PauseDismissReason.LEFT)
                pauseClearedByUser = false
            }
            VisitMachine.Ui.NONE -> Unit
        }
        // An OPENED the machine swallowed — stale rules, overlay noise, or a
        // missed resume — still needs a card if this visit was never shown.
        // A real leave is not this case: the session is already ended.
        if (!pauseVisible && step.ui != VisitMachine.Ui.DISMISS) {
            presentWaitingOpening()
        }
        accrueUse(today, now)
        maybeRecoverUnanswered(detector.lastPackage, now)
    }

    /**
     * Puts up an opening the visit already owes, or starts one the poll missed.
     * Does not invent a visit after Continue, dismiss-for-today, or a no-choice
     * close of the same visit.
     */
    private suspend fun presentWaitingOpening() {
        if (pauseVisible || stopRequested) return
        val foreground = detector.lastPackage ?: return
        if (foreground == openingHeldPackage) return
        val snapshot = SessionClock.adoptStored(sessions().current(), sessionCache)
        sessionCache = snapshot
        if (showUnshownOpening(foreground)) return
        showMissedOpening(foreground, snapshot)
    }

    /**
     * Credits only a short gap while the guarded app is still eligible.
     * Wall-clock time since Continue is not a reminder deadline.
     */
    private suspend fun accrueUse(today: String, nowMillis: Long) {
        val snapshot = sessionCache
        val pkg = snapshot.activePackage ?: return
        val rule = enabledRules.find { it.appId == pkg }
        val (next, command) = SessionClock.onTick(
            snapshot = snapshot,
            foregroundPackage = detector.lastPackage,
            nowMillis = nowMillis,
            today = today,
            remindersEnabled = rule?.reminderFrequency?.isEnabled == true,
            elapsedRealtime = android.os.SystemClock.elapsedRealtime(),
            screenInteractive = screenInteractive(),
            pauseVisible = pauseVisible,
            intervalMinutes = rule?.reminderFrequency?.minutesOrNull(),
            monitoringAllowed = true,
            applyRuleInterval = rule != null,
        )
        // Keep the anchor even when this sample is not worth a disk write.
        // The next poll reloads storage; adoptStored puts the anchor back.
        sessionCache = next
        if (SessionClock.shouldFlush(snapshot, next) && !persistIfUnchanged(snapshot, next)) {
            sessionCache = SessionClock.adoptStored(sessions().current(), next)
            return
        }
        if (command is GuardCommand.ShowReminder && rule != null && !pauseVisible) {
            showPause(rule, PauseRequest.Kind.REMINDER)
        }
    }

    /**
     * Writes only when the stored session is still the one this decision used.
     * A Leave or Continue that landed during the tick is not overwritten.
     * An unchanged snapshot does not touch DataStore.
     */
    private suspend fun persistIfUnchanged(base: GuardSnapshot, next: GuardSnapshot): Boolean {
        if (base == next) return true
        val stamped = if (next.phase == GuardSnapshot.Phase.NONE &&
            base.phase != GuardSnapshot.Phase.NONE &&
            next.quitSuppressedPackage == null
        ) {
            next.copy(
                recentAwayPackage = base.activePackage
                    ?: base.openingHandledForPackage
                    ?: base.lastGuardedPackage
                    ?: next.lastGuardedPackage
                    ?: next.recentAwayPackage,
                recentAwayAtElapsed = android.os.SystemClock.elapsedRealtime(),
            )
        } else {
            next
        }
        var applied = false
        sessions().update { current ->
            if (current == base || SessionClock.canReplace(current, base)) {
                applied = true
                val pkg = stamped.recentAwayPackage
                    ?: current.lastGuardedPackage
                    ?: current.activePackage
                    ?: current.openingHandledForPackage
                if (stamped.phase == GuardSnapshot.Phase.NONE &&
                    stamped.quitSuppressedPackage == null &&
                    pkg != null
                ) {
                    stamped.copy(
                        recentAwayPackage = pkg,
                        lastGuardedPackage = current.lastGuardedPackage ?: pkg,
                        recentAwayAtElapsed = stamped.recentAwayAtElapsed
                            ?: android.os.SystemClock.elapsedRealtime(),
                    )
                } else {
                    stamped.copy(lastGuardedPackage = current.lastGuardedPackage ?: stamped.lastGuardedPackage)
                }
            } else {
                current
            }
        }
        if (applied) sessionCache = stamped
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

    private var shownPackage: String? = null
    private var shownKind: PauseRequest.Kind? = null

    private suspend fun showPause(rule: AppRule, kind: PauseRequest.Kind) {
        // The visit reducer decides whether this is a new opening. A process-local
        // latch here hid a real second visit after Leave, and did not survive the
        // process restarts that the grace failure actually hit.
        if (pauseVisible && shownPackage == rule.appId && shownKind == kind) return
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
                tertiaryLabel = InterventionSurface.DISMISS_FOR_TODAY,
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
        shownPackage = rule.appId
        shownKind = kind
        if (kind == PauseRequest.Kind.OPENING && LeaveSignal.packageName == rule.appId) {
            LeaveSignal.packageName = null
        }
        machineLatch = machineLatch.copy(
            handbackPackage = rule.appId,
            shownVisitId = sessionCache.visitId,
            systemRemovals = 0,
        )
        Log.i(TAG, "showing $kind for ${rule.appId}")
        noteDebug("showing $kind for ${rule.appId}")
        withContext(Dispatchers.Main) {
            PausePresenter.show(this@MonitoringService, request, theme, sessionCache.visitId)
        }
        if (PausePresenter.surface == PauseSurface.NONE) {
            pauseVisible = false
            shownPackage = null
            shownKind = null
        }
    }

    private suspend fun dismissPause(reason: PauseDismissReason) {
        if (!pauseVisible) return
        pauseVisible = false
        shownPackage = null
        shownKind = null
        withContext(Dispatchers.Main) { PausePresenter.dismiss(this@MonitoringService, reason) }
    }

    private suspend fun recordClearedWithoutChoice(packageName: String?, visitId: Long) {
        // A newer visit already replaced this one. Do not stamp it.
        if (!pauseClearedByUser) return
        val now = System.currentTimeMillis()
        sessions().update { current ->
            if (current.visitId != visitId) current
            else if (packageName != null && current.activePackage != packageName) current
            else SessionClock.onClearedWithoutChoice(current, now, visitId)
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
        if (!SessionClock.needsOpening(
                snapshot,
                foreground,
                rule != null,
                android.os.SystemClock.elapsedRealtime(),
            )
        ) {
            return false
        }
        val (next, command) = SessionClock.onOpened(
            snapshot,
            foreground!!,
            true,
            LocalDate.now().toString(),
            android.os.SystemClock.elapsedRealtime(),
        )
        if (!persistIfUnchanged(snapshot, next)) return false
        if (command is GuardCommand.ShowOpening && rule != null) {
            noteDebug(
                "missed opening pkg=$foreground phase=${next.phase} away=${snapshot.backgroundedAtElapsed} visit=${next.visitId}",
            )
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
        val active = sessionCache.activePackage
        val accrued = active?.let { sessionCache.useByPackage[it]?.accumulatedMillis } ?: 0L
        noteDebug("beat last=${detector.lastPackage} phase=${sessionCache.phase} useMs=$accrued")
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
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
        val elapsedNow = android.os.SystemClock.elapsedRealtime()
        sessions().update { SessionClock.onLeft(it, elapsedNow).first }
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
