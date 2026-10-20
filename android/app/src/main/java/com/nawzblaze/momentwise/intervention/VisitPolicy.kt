package com.nawzblaze.momentwise.intervention

/**
 * Decides whether a usage signal is a new visit.
 *
 * A pause window can stop the guarded app, or make Momentwise the foreground,
 * without the user leaving. That is not a leave, and the following resume is
 * not a new opening. A new visit starts only after a different package is
 * actually in front and the guarded app is later resumed.
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
            // The card, or an opening that has not been answered, can park the
            // launcher or Momentwise. That is not the user leaving Instagram.
            latch.pauseVisible -> Effect.NONE
            latch.phase == GuardSnapshot.Phase.AWAITING_OPENING -> Effect.NONE
            else -> Effect.END_VISIT
        }
        DetectorSignal.OPENED -> {
            if (signaledPackage.isNullOrBlank() || signaledPackage == selfPackage) {
                Effect.NONE
            } else if (signaledPackage == latch.openPackage) {
                Effect.NONE
            } else if (latch.pauseVisible || latch.phase == GuardSnapshot.Phase.AWAITING_OPENING) {
                // A different app while the choice is still up is a real switch.
                Effect.BEGIN_VISIT
            } else {
                Effect.BEGIN_VISIT
            }
        }
    }
}
