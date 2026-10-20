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

    fun queryStart(nowMillis: Long, trailingMillis: Long = TRAILING_MILLIS): Long =
        (nowMillis - trailingMillis).coerceAtLeast(0L)

    fun seedStart(nowMillis: Long): Long = queryStart(nowMillis, SEED_MILLIS)
}
