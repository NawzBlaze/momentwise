package com.nawzblaze.momentwise.intervention

/**
 * Pure reducer for Usage Access resume events.
 *
 * An empty window is a gap in the event log, not proof that the foreground
 * changed. A repeated resume of the same package is not a new opening.
 *
 * [ignoreSelf] is true only while Momentwise's own pause is showing, so that
 * pause does not count as leaving the other app. When the user opens
 * Momentwise itself, self is a leave — otherwise the next open of the guarded
 * app looks like a duplicate and the pause never appears.
 */
data class ForegroundEvent(
    val packageName: String,
    val timeMillis: Long,
    val className: String? = null,
)

data class DetectorState(
    val lastPackage: String? = null,
    val lastEventTimeMillis: Long = Long.MIN_VALUE,
)

enum class DetectorSignal {
    /** No new foreground package. Includes an empty window and a self-package event. */
    NONE,

    /** A non-ignored package became the foreground, and it is not the previous one. */
    OPENED,

    /** The launcher or another ignored package became the foreground. The user left. */
    LEFT,
}

data class DetectorResult(
    val state: DetectorState,
    val signal: DetectorSignal,
    val packageName: String? = null,
)

object UsageEventReducer {

    fun reduce(
        state: DetectorState,
        events: List<ForegroundEvent>,
        selfPackage: String,
        ignoredPackages: Set<String>,
        ignoreSelf: Boolean = true,
        pauseClassName: String? = null,
    ): DetectorResult {
        val fresh = events
            .filter { it.timeMillis > state.lastEventTimeMillis && it.packageName.isNotBlank() }
            .sortedBy { it.timeMillis }
        if (fresh.isEmpty()) {
            return DetectorResult(state, DetectorSignal.NONE)
        }

        var lastPackage = state.lastPackage
        var lastTime = state.lastEventTimeMillis
        var signal = DetectorSignal.NONE
        var signaledPackage: String? = null

        for (event in fresh) {
            lastTime = event.timeMillis
            val isSelf = event.packageName == selfPackage
            val isPauseUi = pauseClassName != null && event.className == pauseClassName
            // The pause screen is Momentwise, but it is not the user leaving.
            if (isSelf && (ignoreSelf || isPauseUi)) continue
            if (event.packageName == lastPackage) continue
            lastPackage = event.packageName
            if (isSelf || event.packageName in ignoredPackages) {
                signal = DetectorSignal.LEFT
                signaledPackage = event.packageName
            } else {
                signal = DetectorSignal.OPENED
                signaledPackage = event.packageName
            }
        }

        return DetectorResult(
            state = DetectorState(lastPackage, lastTime),
            signal = signal,
            packageName = signaledPackage,
        )
    }

    /**
     * Records the package already in front when monitoring starts or the phone
     * reboots, without treating that observation as a new opening.
     */
    fun seed(observedPackage: String?, timeMillis: Long): DetectorState =
        DetectorState(lastPackage = observedPackage, lastEventTimeMillis = timeMillis)
}
