package com.nawzblaze.momentwise.intervention

/**
 * Pure reducer for Usage Access resume and stop events.
 *
 * An empty window is a gap in the event log, not proof that the foreground
 * changed. A repeated resume of the same package is not a new opening.
 *
 * [ignoreSelf] is true only while Momentwise's own pause is showing, so that
 * pause does not count as leaving the other app. When the user opens
 * Momentwise itself, self is a leave — otherwise the next open of the guarded
 * app looks like a duplicate and the pause never appears.
 *
 * [ForegroundEvent.Kind.STOPPED] is an activity stop, not a pause. A stop and
 * a resume of the same package in one window is in-app navigation. A stop that
 * ends the window is a leave, even if the next app was not in that window.
 * While [suspendStops] is true the pause itself may have stopped the other
 * app, so the stop is remembered and not treated as a leave yet.
 */
data class ForegroundEvent(
    val packageName: String,
    val timeMillis: Long,
    val className: String? = null,
    val kind: Kind = Kind.RESUMED,
) {
    enum class Kind { RESUMED, STOPPED }
}

data class DetectorState(
    val lastPackage: String? = null,
    val lastEventTimeMillis: Long = Long.MIN_VALUE,
    /**
     * A stop was seen while a pause activity was showing, so it was not yet a leave.
     * Cleared when the pause ends in a choice, or when a later resume decides.
     */
    val stoppedPackage: String? = null,
    val stoppedAtMillis: Long = Long.MIN_VALUE,
)

enum class DetectorSignal {
    /** No new foreground package. Includes an empty window and a self-package event. */
    NONE,

    /** A non-ignored package became the foreground, and it is not the previous one. */
    OPENED,

    /** The launcher, another ignored package, or a completed stop. The user left. */
    LEFT,
}

data class DetectorResult(
    val state: DetectorState,
    val signal: DetectorSignal,
    val packageName: String? = null,
    /** Wall-clock time of the event that produced [signal]. Not a reminder deadline. */
    val signalTimeMillis: Long = Long.MIN_VALUE,
)

object UsageEventReducer {

    fun reduce(
        state: DetectorState,
        events: List<ForegroundEvent>,
        selfPackage: String,
        ignoredPackages: Set<String>,
        ignoreSelf: Boolean = true,
        pauseClassName: String? = null,
        suspendStops: Boolean = false,
    ): DetectorResult {
        val fresh = events
            .filter { it.timeMillis > state.lastEventTimeMillis && it.packageName.isNotBlank() }
            .sortedBy { it.timeMillis }
        if (fresh.isEmpty()) {
            return DetectorResult(state, DetectorSignal.NONE)
        }

        var lastPackage = state.lastPackage
        var lastTime = state.lastEventTimeMillis
        var stoppedPackage = state.stoppedPackage
        var stoppedAtMillis = state.stoppedAtMillis
        var stoppedThisWindow: String? = null
        var signal = DetectorSignal.NONE
        var signaledPackage: String? = null
        var signalTime = Long.MIN_VALUE

        for (event in fresh) {
            lastTime = event.timeMillis
            if (event.kind == ForegroundEvent.Kind.STOPPED) {
                if (event.packageName == selfPackage && ignoreSelf) continue
                if (lastPackage != null && event.packageName != lastPackage) continue
                stoppedPackage = event.packageName
                stoppedAtMillis = event.timeMillis
                stoppedThisWindow = event.packageName
                continue
            }

            val isSelf = event.packageName == selfPackage
            val isPauseUi = pauseClassName != null && event.className == pauseClassName
            // The pause screen is Momentwise, but it is not the user leaving.
            if (isSelf && (ignoreSelf || isPauseUi)) continue

            val samePackage = event.packageName == lastPackage
            val returnedAfterEarlierStop = samePackage &&
                stoppedPackage == event.packageName &&
                stoppedThisWindow != event.packageName
            if (returnedAfterEarlierStop) {
                val gap = event.timeMillis - stoppedAtMillis
                stoppedPackage = null
                stoppedAtMillis = Long.MIN_VALUE
                stoppedThisWindow = null
                // The pause activity stopping the other app, then that app
                // resuming as the pause closes, is the same visit. A later
                // reopen is not.
                if (gap >= HELD_STOP_IS_NEW_VISIT_AFTER_MILLIS) {
                    signal = DetectorSignal.OPENED
                    signaledPackage = event.packageName
                    signalTime = event.timeMillis
                }
                continue
            }
            if (samePackage && stoppedThisWindow == event.packageName) {
                // Stop and resume in one window. Not a new visit.
                stoppedPackage = null
                stoppedAtMillis = Long.MIN_VALUE
                stoppedThisWindow = null
                continue
            }
            if (samePackage) continue

            stoppedPackage = null
            stoppedAtMillis = Long.MIN_VALUE
            stoppedThisWindow = null
            lastPackage = event.packageName
            if (isSelf || event.packageName in ignoredPackages) {
                signal = DetectorSignal.LEFT
                signaledPackage = event.packageName
            } else {
                signal = DetectorSignal.OPENED
                signaledPackage = event.packageName
            }
            signalTime = event.timeMillis
        }

        if (stoppedThisWindow != null && signal == DetectorSignal.NONE && !suspendStops) {
            signal = DetectorSignal.LEFT
            signaledPackage = stoppedThisWindow
            signalTime = stoppedAtMillis
            lastPackage = null
            stoppedPackage = null
            stoppedAtMillis = Long.MIN_VALUE
            stoppedThisWindow = null
        }

        return DetectorResult(
            state = DetectorState(lastPackage, lastTime, stoppedPackage, stoppedAtMillis),
            signal = signal,
            packageName = signaledPackage,
            signalTimeMillis = signalTime,
        )
    }

    /**
     * A stop was held because the pause was showing. The pause is gone and no
     * choice was made, so that stop is a leave. Does nothing when no stop is pending.
     */
    fun confirmPendingStop(state: DetectorState): DetectorResult {
        val stopped = state.stoppedPackage ?: return DetectorResult(state, DetectorSignal.NONE)
        return DetectorResult(
            state = state.copy(lastPackage = null, stoppedPackage = null, stoppedAtMillis = Long.MIN_VALUE),
            signal = DetectorSignal.LEFT,
            packageName = stopped,
        )
    }

    fun clearPendingStop(state: DetectorState): DetectorState =
        if (state.stoppedPackage == null) state else state.copy(stoppedPackage = null, stoppedAtMillis = Long.MIN_VALUE)

    /** A resume this soon after a held stop is the pause handing the app back, not a new visit. */
    const val HELD_STOP_IS_NEW_VISIT_AFTER_MILLIS = 3_000L

    /**
     * Records the package already in front when monitoring starts or the phone
     * reboots, without treating that observation as a new opening.
     */
    fun seed(observedPackage: String?, timeMillis: Long): DetectorState =
        DetectorState(lastPackage = observedPackage, lastEventTimeMillis = timeMillis)
}
