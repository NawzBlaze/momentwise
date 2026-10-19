package com.nawzblaze.momentwise.monitoring

import android.content.Context

/**
 * Heartbeat for the monitoring service. SharedPreferences, not DataStore:
 * a tick must not rewrite the rule store.
 */
object MonitorPresence {
    private const val PREFS = "monitor_presence.v1"
    private const val KEY_BEAT = "beat_millis"
    private const val KEY_ATTEMPT = "start_attempt_millis"
    private const val KEY_FAILED = "start_failed"

    data class Snapshot(
        val lastBeatMillis: Long,
        val startAttemptMillis: Long,
        val startFailed: Boolean,
    )

    fun markStartAttempt(context: Context, nowMillis: Long) {
        prefs(context).edit()
            .putLong(KEY_ATTEMPT, nowMillis)
            .putBoolean(KEY_FAILED, false)
            .apply()
    }

    fun beat(context: Context, nowMillis: Long) {
        prefs(context).edit()
            .putLong(KEY_BEAT, nowMillis)
            .putBoolean(KEY_FAILED, false)
            .apply()
    }

    fun markStartFailed(context: Context, nowMillis: Long) {
        prefs(context).edit()
            .putLong(KEY_ATTEMPT, nowMillis)
            .putBoolean(KEY_FAILED, true)
            .apply()
    }

    fun markStopped(context: Context) {
        prefs(context).edit()
            .putLong(KEY_BEAT, 0L)
            .putLong(KEY_ATTEMPT, 0L)
            .putBoolean(KEY_FAILED, false)
            .apply()
    }

    fun read(context: Context): Snapshot {
        val prefs = prefs(context)
        return Snapshot(
            lastBeatMillis = prefs.getLong(KEY_BEAT, 0L),
            startAttemptMillis = prefs.getLong(KEY_ATTEMPT, 0L),
            startFailed = prefs.getBoolean(KEY_FAILED, false),
        )
    }

    fun health(context: Context, shouldRun: Boolean, nowMillis: Long): MonitorHealth {
        val snapshot = read(context)
        return MonitorHealthPolicy.assess(
            shouldRun = shouldRun,
            lastBeatMillis = snapshot.lastBeatMillis,
            startAttemptMillis = snapshot.startAttemptMillis,
            startFailed = snapshot.startFailed,
            nowMillis = nowMillis,
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
