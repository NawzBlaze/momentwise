package com.nawzblaze.momentwise.monitoring

/**
 * Whether the foreground monitor should be running.
 * A paused switch or a missing grant stops it. Saved rules are left alone.
 */
object MonitoringPolicy {
    fun shouldRun(anyRuleEnabled: Boolean, canMonitor: Boolean, pausedByUser: Boolean): Boolean =
        anyRuleEnabled && canMonitor && !pausedByUser
}
