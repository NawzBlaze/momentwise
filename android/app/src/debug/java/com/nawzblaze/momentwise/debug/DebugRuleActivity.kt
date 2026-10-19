package com.nawzblaze.momentwise.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.nawzblaze.momentwise.MomentwiseApplication
import com.nawzblaze.momentwise.data.RuleDefaults
import com.nawzblaze.momentwise.monitoring.GrantChecks
import com.nawzblaze.momentwise.monitoring.MonitoringCoordinator
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/**
 * Foreground entry for instrumentation. A shell broadcast can be delivered
 * while the process is backgrounded, and Android 12+ then rejects the
 * foreground-service start. This activity does the same rule write while
 * Momentwise is in front. Debug source set only.
 */
class DebugRuleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(DebugRuleBridge.EXTRA_PACKAGE)
            ?: intent.getStringExtra("package")
        val label = intent.getStringExtra(DebugRuleBridge.EXTRA_LABEL)?.ifBlank { null }
            ?: packageName
        if (packageName.isNullOrBlank() || label == null) {
            Log.w(TAG, "missing package extra")
            finish()
            return
        }
        val app = application as MomentwiseApplication
        setContentView(TextView(this).apply { text = "Enabling rule" })
        // Must not block the main thread. startForegroundService posts
        // onStartCommand there, and API 34 kills the process if that call
        // does not reach startForeground in time.
        thread(name = "debug-rule") {
            val grants = GrantChecks.read(app)
            Log.i(
                TAG,
                "enabling $packageName usage=${grants.usageAccess} notifications=${grants.notifications} overlay=${grants.overlay}",
            )
            runBlocking {
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
            }
            openFileOutput("debug-status.txt", MODE_PRIVATE).bufferedWriter().use {
                it.appendLine(
                    "sync finished usage=${grants.usageAccess} notifications=${grants.notifications} overlay=${grants.overlay}",
                )
            }
            Log.i(TAG, "sync finished for $packageName")
        }
    }

    companion object {
        const val TAG = "MomentwiseDebug"
    }
}
