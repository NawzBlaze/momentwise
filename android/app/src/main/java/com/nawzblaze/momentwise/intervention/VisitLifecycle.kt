package com.nawzblaze.momentwise.intervention

/**
 * One visit model for the opening pause and the use timer.
 *
 * States, stored on [GuardSnapshot] rather than as a second object:
 * - No visit: phase [GuardSnapshot.Phase.NONE], no active package.
 * - Opening owed or visible: phase [GuardSnapshot.Phase.AWAITING_OPENING].
 * - Continued: phase [GuardSnapshot.Phase.CONTINUING], [GuardSnapshot.backgroundedAtElapsed] null.
 * - Temporarily backgrounded: an open visit with [GuardSnapshot.backgroundedAtElapsed] set.
 *   Home, an app switch, or a brief leave. Not explicit Leave.
 * - Explicitly left: phase none and [GuardSnapshot.quitSuppressedPackage] set until a
 *   leave is observed, so the overlay handback is not a new opening. The next entry is.
 * - Dismissed without a choice: [GuardSnapshot.clearedWithoutChoice] on the open visit.
 * - Ended: phase none, no suppression, no grace.
 *
 * A return within [GRACE_MILLIS] restores the same visit and does not show another
 * opening. At or after that boundary, the next entry is a new visit. Explicit Leave
 * ends the visit immediately, so a quick return still gets an opening.
 *
 * Eligible use is monotonic and only credited for a confirmed-foreground gap of at
 * most [MAX_FOREGROUND_GAP_MILLIS]. A slower poll still counts. A gap longer than
 * that, including process death, does not. On a phone the reminder is therefore
 * expected within about [MAX_FOREGROUND_GAP_MILLIS] after the true threshold, not
 * on the exact millisecond. Unit tests that step a fake clock by one second can
 * treat one sample as the tolerance.
 */
object VisitLifecycle {
    const val GRACE_MILLIS = 60_000L

    /**
     * Confirmed-foreground gaps longer than this are not eligible use.
     * A one-second poll plus UsageStats and a disk write can exceed 2.5 seconds
     * on a phone. Crediting nothing in that case made a five-minute reminder
     * never arrive. Fifteen seconds covers a slow poll. It does not cover a
     * missed minute.
     */
    const val MAX_FOREGROUND_GAP_MILLIS = 15_000L

    fun withinGrace(backgroundedAtElapsed: Long?, elapsedRealtime: Long): Boolean {
        val start = backgroundedAtElapsed ?: return false
        if (elapsedRealtime < start) return false
        return elapsedRealtime - start < GRACE_MILLIS
    }

    fun graceExpired(backgroundedAtElapsed: Long?, elapsedRealtime: Long): Boolean {
        val start = backgroundedAtElapsed ?: return false
        if (elapsedRealtime < start) return true
        return elapsedRealtime - start >= GRACE_MILLIS
    }
}
