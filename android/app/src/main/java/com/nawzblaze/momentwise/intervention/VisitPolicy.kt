package com.nawzblaze.momentwise.intervention

/**
 * Decides whether a usage signal is a new visit.
 *
 * A pause window can stop the guarded app, or make the launcher the foreground,
 * without the user leaving. While the card is visible that is not a leave and
 * not a new visit. Once the card is gone, a later leave ends the visit so the
 * next open can show again. Overlay-era events are filtered by [VisitMachine]
 * before they reach this policy.
 */
object VisitPolicy {

    data class Latch(
        /** The guarded package whose visit is still open, including after a no-choice close. */
        val openPackage: String? = null,
        val pauseVisible: Boolean = false,
        val phase: GuardSnapshot.Phase = GuardSnapshot.Phase.NONE,
    )

    enum class Effect {
        /** Keep the current visit. Do not dismiss a visible pause and do not show another. */
        NONE,
        /** The guarded app left the foreground. */
        END_VISIT,
        /** A guarded package resumed after the previous visit had ended. */
        BEGIN_VISIT,
    }

    fun effect(
        latch: Latch,
        signal: DetectorSignal,
        signaledPackage: String?,
        selfPackage: String,
    ): Effect = when (signal) {
        DetectorSignal.NONE -> Effect.NONE
        DetectorSignal.LEFT -> when {
            // The card is up. Launcher, Momentwise, and a stop of the guarded
            // app are the overlay taking focus, not the user leaving.
            latch.pauseVisible -> Effect.NONE
            else -> Effect.END_VISIT
        }
        DetectorSignal.OPENED -> {
            if (signaledPackage.isNullOrBlank() || signaledPackage == selfPackage) {
                Effect.NONE
            } else if (signaledPackage == latch.openPackage) {
                Effect.NONE
            } else if (latch.pauseVisible) {
                // A different package while the card is up must not replace the
                // visit or dismiss the card. The overlay consumes touches; a
                // usage event here is the window change, not a chosen switch.
                Effect.NONE
            } else {
                Effect.BEGIN_VISIT
            }
        }
    }
}
