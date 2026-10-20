package com.nawzblaze.momentwise.intervention

/**
 * Bounds for [android.app.usage.UsageStatsManager.queryEvents].
 *
 * Usage events are often written a few seconds after the activity resumes.
 * A cursor that advances to wall-clock "now" with a one-second overlap drops
 * those late events, so the guarded app never looks like it opened.
 *
 * The window stays bounded. It is not a scan of the whole history. Events
 * already applied are ignored by timestamp in [UsageEventReducer].
 */
object EventWindow {
    const val TRAILING_MILLIS = 20_000L
    const val SEED_MILLIS = 60_000L

    /**
     * Steady polls already applied events up to the cursor. Re-reading the
     * whole trailing window every second allocates events the reducer drops.
     * Two seconds of slack still covers a late write whose timestamp is just
     * behind the cursor. An old cursor keeps the full trailing bound.
     */
    const val CURSOR_SLACK_MILLIS = 2_000L

    fun queryStart(nowMillis: Long, trailingMillis: Long = TRAILING_MILLIS): Long =
        (nowMillis - trailingMillis).coerceAtLeast(0L)

    fun seedStart(nowMillis: Long): Long = queryStart(nowMillis, SEED_MILLIS)

    fun steadyStart(nowMillis: Long, lastEventTimeMillis: Long): Long {
        val bounded = queryStart(nowMillis)
        if (lastEventTimeMillis <= 0L) return bounded
        val fromCursor = (lastEventTimeMillis - CURSOR_SLACK_MILLIS).coerceAtLeast(0L)
        return maxOf(bounded, fromCursor).coerceAtMost(nowMillis)
    }
}
