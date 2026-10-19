package com.nawzblaze.momentwise.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringPolicyTest {

    @Test
    fun runsOnlyWhenARuleIsEnabledAndGrantsExist() {
        assertTrue(MonitoringPolicy.shouldRun(anyRuleEnabled = true, canMonitor = true, pausedByUser = false))
    }

    @Test
    fun pauseStopsMonitoringWithoutDisablingRules() {
        assertFalse(MonitoringPolicy.shouldRun(anyRuleEnabled = true, canMonitor = true, pausedByUser = true))
    }

    @Test
    fun missingGrantStopsMonitoring() {
        assertFalse(MonitoringPolicy.shouldRun(anyRuleEnabled = true, canMonitor = false, pausedByUser = false))
    }

    @Test
    fun noEnabledRuleDoesNotRun() {
        assertFalse(MonitoringPolicy.shouldRun(anyRuleEnabled = false, canMonitor = true, pausedByUser = false))
    }
}
