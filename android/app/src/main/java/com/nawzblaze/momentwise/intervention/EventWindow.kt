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
     * Steady polls advance a query cursor, not the timestamp of the most recent
     * activity event. The cursor overlaps the last query by a few seconds so
     * UsageStats events that arrive a little late can still be seen. If the
     * cursor is missing or stale, fall back to a bounded trailing window.
     */
    const val CURSOR_SLACK_MILLIS = 5_000L

    fun queryStart(nowMillis: Long, trailingMillis: Long = TRAILING_MILLIS): Long =
        (nowMillis - trailingMillis).coerceAtLeast(0L)

    fun seedStart(nowMillis: Long): Long = queryStart(nowMillis, SEED_MILLIS)

    fun steadyStart(nowMillis: Long, lastQueryEndMillis: Long): Long {
        val bounded = queryStart(nowMillis)
        if (lastQueryEndMillis <= 0L) return bounded
        val safeCursor = lastQueryEndMillis.coerceAtMost(nowMillis)
        val fromCursor = (safeCursor - CURSOR_SLACK_MILLIS).coerceAtLeast(0L)
        return maxOf(bounded, fromCursor).coerceAtMost(nowMillis)
    }
}
