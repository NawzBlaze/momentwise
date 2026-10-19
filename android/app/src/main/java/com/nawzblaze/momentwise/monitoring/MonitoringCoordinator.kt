package com.nawzblaze.momentwise.monitoring

import com.nawzblaze.momentwise.MomentwiseApplication
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts monitoring only while a rule is enabled and the required grants exist.
 * Does not request those grants. The disclosure screen does that, and only
 * when the user is enabling a rule.
 */
object MonitoringCoordinator {

    fun install(app: MomentwiseApplication) {
        app.applicationScope.launch {
            app.container.appRuleRepository.rules.collect { sync(app) }
        }
        app.applicationScope.launch {
            app.container.appSettingsRepository.monitoringPaused.collect { sync(app) }
        }
    }

    suspend fun sync(app: MomentwiseApplication) {
        val rules = app.container.appRuleRepository.rules.first()
        val paused = app.container.appSettingsRepository.monitoringPaused.first()
        val grants = GrantChecks.read(app)
        if (MonitoringPolicy.shouldRun(rules.any { it.enabled }, grants.canMonitor, paused)) {
            MonitoringService.start(app)
        } else {
            MonitoringService.stop(app)
        }
    }
}
