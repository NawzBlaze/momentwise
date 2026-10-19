package com.nawzblaze.momentwise.monitoring

/**
 * Whether the foreground monitor is actually running.
 *
 * Grants and saved rules are not enough. The guarding notification can be
 * gone while those still look fine.
 */
enum class MonitorHealth {
    /** Nothing should be watching: no enabled rule, paused, or a grant is missing. */
    IDLE,

    /** A recent heartbeat, or a start that has not had time to beat yet. */
    RUNNING,

    /** Watching was requested, and the service is not producing a heartbeat. */
    STOPPED,
}

object MonitorHealthPolicy {
    const val STALE_AFTER_MILLIS = 20_000L
    const val START_GRACE_MILLIS = 8_000L

    fun assess(
        shouldRun: Boolean,
        lastBeatMillis: Long,
        startAttemptMillis: Long,
        startFailed: Boolean,
        nowMillis: Long,
    ): MonitorHealth {
        if (!shouldRun) return MonitorHealth.IDLE
        if (startFailed) return MonitorHealth.STOPPED
        val beatFresh = lastBeatMillis > 0L && nowMillis - lastBeatMillis <= STALE_AFTER_MILLIS
        if (beatFresh) return MonitorHealth.RUNNING
        val starting = startAttemptMillis > 0L && nowMillis - startAttemptMillis <= START_GRACE_MILLIS
        if (starting) return MonitorHealth.RUNNING
        return MonitorHealth.STOPPED
    }
}
