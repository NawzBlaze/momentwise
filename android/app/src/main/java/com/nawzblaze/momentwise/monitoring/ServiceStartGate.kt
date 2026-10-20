package com.nawzblaze.momentwise.monitoring

import com.nawzblaze.momentwise.intervention.EventWindow

/**
 * Startup rules that are independent of the Android service so they can be
 * regression-tested without an emulator.
 *
 * Android delivers [android.content.Context.startForegroundService] onto the
 * main thread and kills the process if [android.app.Service.startForeground]
 * does not run within a few seconds. API 34 did that in CI when a debug
 * screen blocked the main thread. The deadline is not a measured phone lag.
 */
object ForegroundStartContract {
    /** Conservative bound. The platform timeout is on the order of 5–10 seconds. */
    const val DEADLINE_MILLIS = 5_000L

    enum class Step {
        START_FOREGROUND,
        RECORD_ATTEMPT,
        READ_RULES,
        POLL,
    }

    fun blocksTheDeadline(mainThreadWorkMillis: Long): Boolean =
        mainThreadWorkMillis >= DEADLINE_MILLIS

    /** Nothing that can touch disk may run before the foreground call. */
    fun mayRunBeforeStartForeground(step: Step): Boolean = step == Step.START_FOREGROUND
}

/**
 * One poll loop per process. A second start must not add a worker while the
 * current one is still running. A worker that has already asked to stop may
 * be replaced.
 */
object PollSupervisor {
    fun shouldLaunch(existingActive: Boolean, stopRequested: Boolean): Boolean =
        !existingActive || stopRequested
}

/**
 * The one-second poll is the latency bound, not a scan of usage history.
 * Usage Access has no resume callback, and events are often written late, so
 * a shorter interval would not remove that delay. A longer one would add lag.
 */
object UsagePoll {
    const val INTERVAL_MILLIS = 1_000L

    /** No UsageStats query while the screen is off. A screen-on broadcast wakes sooner. */
    const val SCREEN_OFF_INTERVAL_MILLIS = 30_000L

    /** AppOps and overlay checks are not repeated on every one-second tick. */
    const val GRANT_REFRESH_MILLIS = 5_000L

    fun delayMillis(screenInteractive: Boolean): Long =
        if (screenInteractive) INTERVAL_MILLIS else SCREEN_OFF_INTERVAL_MILLIS

    fun shouldRefreshGrants(lastReadAtMillis: Long, nowMillis: Long): Boolean =
        lastReadAtMillis == 0L || nowMillis - lastReadAtMillis >= GRANT_REFRESH_MILLIS

    fun queryIsBounded(nowMillis: Long, startMillis: Long): Boolean {
        if (startMillis < 0L || startMillis > nowMillis) return false
        return nowMillis - startMillis <= EventWindow.TRAILING_MILLIS
    }
}

/**
 * Debug timing for UsageStats calls. A JVM reducer benchmark is not this.
 * Recording every 30th query avoids a log line on every tick.
 */
object UsageQueryDiagnostics {
    const val RECORD_EVERY = 30

    fun shouldRecord(queryCount: Int): Boolean = queryCount > 0 && queryCount % RECORD_EVERY == 0

    fun meanMillis(totalNanos: Long, queryCount: Int): Long =
        if (queryCount <= 0) 0L else totalNanos / queryCount / 1_000_000L
}
