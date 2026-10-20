package com.nawzblaze.momentwise.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nawzblaze.momentwise.MomentwiseApplication
import com.nawzblaze.momentwise.data.RuleDefaults
import com.nawzblaze.momentwise.monitoring.InterventionController
import com.nawzblaze.momentwise.monitoring.MonitoringCoordinator
import kotlinx.coroutines.launch

/**
 * Lets an instrumentation test enable one real rule without tapping through
 * onboarding. It is not in the release source set.
 */
class DebugRuleBridge : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val packageName = (intent.getStringExtra(EXTRA_PACKAGE) ?: intent.getStringExtra("package"))
            ?.takeIf { it.isNotBlank() } ?: return
        if (intent.getBooleanExtra(EXTRA_CONTINUE, false)) {
            val pending = goAsync()
            val app = context.applicationContext as MomentwiseApplication
            app.applicationScope.launch {
                try {
                    InterventionController(
                        context = app,
                        rules = app.container.appRuleRepository,
                        sessions = app.container.guardSessions,
                    ).continueIntoApp(packageName)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        val label = intent.getStringExtra(EXTRA_LABEL)?.ifBlank { null } ?: packageName
        val pending = goAsync()
        val app = context.applicationContext as MomentwiseApplication
        app.applicationScope.launch {
            try {
                app.container.appSettingsRepository.setGuardingDisclosureAccepted(true)
                app.container.appSettingsRepository.setMonitoringPaused(false)
                app.container.appRuleRepository.upsertRule(
                    RuleDefaults.ruleFor(
                        appId = packageName,
                        appName = label,
                        category = "Test",
                        enabled = true,
                    ).copy(configuredEver = true),
                )
                MonitoringCoordinator.sync(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.nawzblaze.momentwise.DEBUG_ENABLE_RULE"
        const val EXTRA_PACKAGE = "target_package"
        const val EXTRA_LABEL = "label"
        const val EXTRA_CONTINUE = "inject_continue"
    }
}
