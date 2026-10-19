package com.nawzblaze.momentwise.debug

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.nawzblaze.momentwise.MomentwiseApplication
import com.nawzblaze.momentwise.data.RuleDefaults
import com.nawzblaze.momentwise.monitoring.GrantChecks
import com.nawzblaze.momentwise.monitoring.MonitoringCoordinator
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/**
 * Foreground entry for instrumentation. A shell broadcast can be delivered
 * while the process is backgrounded, and Android 12+ then rejects the
 * foreground-service start. This activity does the same rule write while
 * Momentwise is in front. Debug source set only.
 *
 * The worker does not block the main thread: API 34 kills the process if
 * [android.app.Service.startForeground] cannot run. It also does not keep the
 * Activity, so a destroyed screen cannot race the file write.
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
        setContentView(TextView(this).apply { text = "Enabling rule" })
        val started = startEnable(application as MomentwiseApplication, packageName, label)
        if (!started) Log.i(TAG, "enable already running")
    }

    companion object {
        const val TAG = "MomentwiseDebug"
        private val running = AtomicBoolean(false)

        /** @return false when a worker is already in flight. One worker only. */
        fun startEnable(app: MomentwiseApplication, packageName: String, label: String): Boolean {
            if (!running.compareAndSet(false, true)) return false
            thread(name = "debug-rule") {
                try {
                    enable(app, packageName, label)
                } finally {
                    running.set(false)
                }
            }
            return true
        }

        private fun enable(app: MomentwiseApplication, packageName: String, label: String) {
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
            app.openFileOutput("debug-status.txt", Context.MODE_APPEND).bufferedWriter().use {
                it.appendLine(
                    "sync finished usage=${grants.usageAccess} notifications=${grants.notifications} overlay=${grants.overlay}",
                )
            }
            Log.i(TAG, "sync finished for $packageName")
        }
    }
}
