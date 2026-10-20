package com.nawzblaze.momentwise.intervention

/**
 * One step of the opening-visit machine. Free of Android types so a scripted
 * usage-event stream can prove which overlay is shown and which callback is ignored.
 *
 * Showing the pause can stop the guarded app and surface the launcher. Events
 * from that window are not a leave and not a new visit. A callback applies only
 * to the visit id that created the overlay. A no-choice close does not finish
 * the visit; a later real leave does, so the next open can show once.
 */
object VisitMachine {

    data class Latch(
        /** Package whose stop or resume is the overlay handing focus back. */
        val handbackPackage: String? = null,
        /**
         * Usage events at or before this wall-clock time were produced while the
         * card was up, even if they are delivered on a later poll.
         */
        val overlayNoiseUntilMillis: Long = Long.MIN_VALUE,
        val shownVisitId: Long = 0,
        /** System removals of the current visit's opening. One reshow, then stop. */
        val systemRemovals: Int = 0,
    )

    enum class Ui { NONE, SHOW_OPENING, DISMISS }

    data class Step(
        val snapshot: GuardSnapshot,
        val latch: Latch,
        val ui: Ui = Ui.NONE,
        val uiPackage: String? = null,
    )

    enum class Removal { IGNORE, RESHOW, RECORD_NO_CHOICE }

    data class RemovalStep(
        val latch: Latch,
        val action: Removal,
        val visitId: Long = 0,
        val packageName: String? = null,
    )

    fun onForeground(
        snapshot: GuardSnapshot,
        latch: Latch,
        signal: DetectorSignal,
        signaledPackage: String?,
        signalTimeMillis: Long,
        resumedPackages: Set<String>,
        pauseVisible: Boolean,
        selfPackage: String,
        today: String,
        signaledHasRule: Boolean,
        elapsedRealtime: Long = signalTimeMillis,
    ): Step {
        val cleared = SessionClock.expireGrace(
            SessionClock.clearStaleDismissals(snapshot, today),
            elapsedRealtime,
        )
        var handback = latch.handbackPackage
        if (!pauseVisible && handback != null && handback in resumedPackages &&
            signalTimeMillis > latch.overlayNoiseUntilMillis
        ) {
            handback = null
        }
        val noise = isOverlayNoise(
            pauseVisible = pauseVisible,
            handback = handback,
            noiseUntil = latch.overlayNoiseUntilMillis,
            signal = signal,
            signaledPackage = signaledPackage,
            signalTimeMillis = signalTimeMillis,
            selfPackage = selfPackage,
        )
        val policySignal = if (noise) DetectorSignal.NONE else signal
        val policyPackage = if (noise) null else signaledPackage
        if (!noise && handback != null && policySignal == DetectorSignal.OPENED && policyPackage != handback) {
            handback = null
        }
        val effect = VisitPolicy.effect(
            latch = VisitPolicy.Latch(
                openPackage = cleared.activePackage,
                pauseVisible = pauseVisible,
                phase = cleared.phase,
            ),
            signal = policySignal,
            signaledPackage = policyPackage,
            selfPackage = selfPackage,
        )
        val nextLatch = latch.copy(handbackPackage = handback)
        return when (effect) {
            VisitPolicy.Effect.END_VISIT -> {
                // Home and an app switch keep the visit for one minute. Explicit
                // Leave already ended it through SessionClock.onQuit, so this
                // observed leave only clears that suppression.
                val away = SessionClock.onTemporarilyAway(cleared, elapsedRealtime)
                val ended = away.phase == GuardSnapshot.Phase.NONE
                Step(
                    snapshot = away,
                    latch = if (ended) nextLatch.copy(handbackPackage = null, systemRemovals = 0) else nextLatch,
                    ui = if (ended && pauseVisible) Ui.DISMISS else Ui.NONE,
                )
            }
            VisitPolicy.Effect.BEGIN_VISIT -> {
                val pkg = policyPackage ?: return Step(cleared, nextLatch)
                val (opened, command) = SessionClock.onOpened(cleared, pkg, signaledHasRule, today)
                if (command is GuardCommand.ShowOpening && signaledHasRule) {
                    Step(
                        snapshot = opened,
                        latch = Latch(
                            handbackPackage = pkg,
                            overlayNoiseUntilMillis = latch.overlayNoiseUntilMillis,
                            shownVisitId = opened.visitId,
                            systemRemovals = 0,
                        ),
                        ui = Ui.SHOW_OPENING,
                        uiPackage = pkg,
                    )
                } else {
                    Step(opened, nextLatch.copy(handbackPackage = null, systemRemovals = 0))
                }
            }
            VisitPolicy.Effect.NONE -> {
                val returned = policySignal == DetectorSignal.OPENED &&
                    policyPackage != null &&
                    policyPackage == cleared.activePackage &&
                    cleared.backgroundedAtElapsed != null
                Step(
                    snapshot = if (returned) cleared.copy(backgroundedAtElapsed = null) else cleared,
                    latch = nextLatch,
                )
            }
        }
    }

    /**
     * Continue, Leave, snooze, or dismiss-for-today removed the card.
     * The next stop of [packageName] is the overlay handing the app back.
     */
    fun onChoiceDismissed(latch: Latch, packageName: String?, nowMillis: Long): Latch =
        latch.copy(
            handbackPackage = packageName ?: latch.handbackPackage,
            overlayNoiseUntilMillis = nowMillis,
            systemRemovals = 0,
        )

    /**
     * The system removed this visit's window, or the user closed it without a
     * button. A newer visit is left alone. The first removal of an unanswered
     * opening may be put back once. The second is a no-choice, not a loop.
     */
    fun onSystemRemoved(
        snapshot: GuardSnapshot,
        latch: Latch,
        removedVisitId: Long,
        nowMillis: Long,
    ): RemovalStep {
        val currentId = snapshot.visitId
        if (removedVisitId != 0L && currentId != 0L && removedVisitId != currentId) {
            return RemovalStep(latch, Removal.IGNORE, removedVisitId, snapshot.activePackage)
        }
        if (snapshot.phase == GuardSnapshot.Phase.CONTINUING || snapshot.phase == GuardSnapshot.Phase.NONE) {
            return RemovalStep(
                latch = latch.copy(
                    handbackPackage = snapshot.activePackage ?: latch.handbackPackage,
                    overlayNoiseUntilMillis = nowMillis,
                ),
                action = Removal.IGNORE,
                visitId = currentId,
                packageName = snapshot.activePackage,
            )
        }
        val held = latch.copy(
            handbackPackage = snapshot.activePackage ?: latch.handbackPackage,
            overlayNoiseUntilMillis = nowMillis,
            systemRemovals = latch.systemRemovals + 1,
            shownVisitId = currentId,
        )
        val action = if (latch.systemRemovals < 1) Removal.RESHOW else Removal.RECORD_NO_CHOICE
        return RemovalStep(held, action, currentId, snapshot.activePackage)
    }

    /**
     * A fresh session has no visit to protect. Do not drop the latch while a
     * card is still on screen: that would treat the overlay's own stop as a leave.
     */
    fun shouldDropLatch(snapshot: GuardSnapshot, pauseVisible: Boolean): Boolean =
        !pauseVisible &&
            snapshot.phase == GuardSnapshot.Phase.NONE &&
            snapshot.activePackage == null &&
            snapshot.visitId == 0L

    /**
     * The card can be removed before the visit write is visible, so the stored
     * phase is still [GuardSnapshot.Phase.NONE]. That is not a finished visit.
     * Put the card back once. A second removal stops. Continue is never put back.
     */
    fun unpersistedRemoval(
        phase: GuardSnapshot.Phase,
        action: Removal,
        shownPackage: String?,
        systemRemovals: Int,
    ): Removal {
        if (action != Removal.IGNORE || shownPackage.isNullOrBlank()) return action
        if (phase == GuardSnapshot.Phase.CONTINUING) return action
        return if (systemRemovals < 1) Removal.RESHOW else Removal.IGNORE
    }

    fun isOverlayNoise(
        pauseVisible: Boolean,
        handback: String?,
        noiseUntil: Long,
        signal: DetectorSignal,
        signaledPackage: String?,
        signalTimeMillis: Long,
        selfPackage: String,
    ): Boolean {
        if (signal == DetectorSignal.NONE) return false
        if (pauseVisible) return true
        if (noiseUntil > 0 && signalTimeMillis != Long.MIN_VALUE && signalTimeMillis <= noiseUntil) return true
        if (handback == null) return false
        if (signaledPackage == handback || signaledPackage == selfPackage) return true
        // A stop of the guarded app has no other package name. That is the handback.
        if (signal == DetectorSignal.LEFT && signaledPackage == null) return true
        return false
    }
}
