package com.nawzblaze.momentwise.intervention

/**
 * Pure reducer for Usage Access resume events.
 *
 * An empty window is a gap in the event log, not proof that the foreground
 * changed. Momentwise's own package is ignored so the pause does not retrigger
 * itself. A repeated resume of the same package is not a new opening.
 */
data class ForegroundEvent(
    val packageName: String,
    val timeMillis: Long,
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
            if (event.packageName == selfPackage) continue
            if (event.packageName == lastPackage) continue
            lastPackage = event.packageName
            if (event.packageName in ignoredPackages) {
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
