package com.nawzblaze.momentwise.debug

import android.app.Activity
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.nawzblaze.momentwise.MomentwiseApplication
import com.nawzblaze.momentwise.data.RuleDefaults
import com.nawzblaze.momentwise.intervention.GuardSnapshot
import com.nawzblaze.momentwise.monitoring.GrantChecks
import com.nawzblaze.momentwise.monitoring.MonitoringCoordinator
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/**
 * Foreground entry for instrumentation. A shell broadcast can be delivered
 * while the process is backgrounded, and Android 12+ then rejects the
 * foreground-service start. This activity does the same rule write while
 * Momentwise is in front. Debug source set only.
 *
 * The worker starts only after resume, and not on the main thread. Starting
 * the service during [onCreate] raced the activity launch: API 34 and API 35
 * then killed the process because [android.app.Service.startForeground] had
 * not run yet. The worker does not keep this Activity.
 */
class DebugRuleActivity : Activity() {
    private var packageName: String? = null
    private var label: String? = null
    private var scheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (wantsMeasure(intent)) {
            setContentView(TextView(this).apply { text = "Measuring usage query" })
            Log.i(TAG, "USAGE_QUERY activity-created")
            return
        }
        packageName = intent.getStringExtra(DebugRuleBridge.EXTRA_PACKAGE)
            ?: intent.getStringExtra("package")
        label = intent.getStringExtra(DebugRuleBridge.EXTRA_LABEL)?.ifBlank { null } ?: packageName
        if (packageName.isNullOrBlank() || label.isNullOrBlank()) {
            Log.w(TAG, "missing package extra")
            finish()
            return
        }
        setContentView(TextView(this).apply { text = "Enabling rule" })
    }

    override fun onResume() {
        super.onResume()
        if (wantsMeasure(intent)) {
            if (scheduled) return
            scheduled = true
            val app = application
            window.decorView.post {
                thread(name = "usage-measure") { measureUsage(app) }
            }
            return
        }
        val packageName = packageName ?: return
        val label = label ?: return
        if (scheduled) return
        scheduled = true
        val app = application as MomentwiseApplication
        val resetSession = wantsReset(intent)
        // After this frame, so launch work is not still occupying the main thread.
        window.decorView.post {
            val started = startEnable(app, packageName, label, resetSession)
            if (!started) Log.i(TAG, "enable already running")
        }
    }

    companion object {
        const val TAG = "MomentwiseDebug"
        const val EXTRA_MEASURE = "measure_usage"
        const val EXTRA_RESET_SESSION = "reset_session"

        fun wantsMeasure(intent: android.content.Intent): Boolean =
            intent.getBooleanExtra(EXTRA_MEASURE, false) ||
                intent.getStringExtra(EXTRA_MEASURE) == "true"

        fun wantsReset(intent: android.content.Intent): Boolean =
            intent.getBooleanExtra(EXTRA_RESET_SESSION, false) ||
                intent.getStringExtra(EXTRA_RESET_SESSION) == "true"
        private val running = AtomicBoolean(false)

        /**
         * Times UsageStats.queryEvents in this process. This is an emulator or
         * device measurement of that call, not a battery result and not the
         * JVM reducer benchmark.
         */
        fun measureUsage(app: Context) {
            val usage = app.getSystemService(UsageStatsManager::class.java)
            if (usage == null) {
                writeStatus(app, "USAGE_QUERY error=no-service")
                return
            }
            val samples = ArrayList<Long>(5)
            repeat(5) {
                val now = System.currentTimeMillis()
                val started = System.nanoTime()
                try {
                    usage.queryEvents(now - 20_000L, now)
                } catch (error: Exception) {
                    writeStatus(app, "USAGE_QUERY error=${error.javaClass.simpleName}")
                    return
                }
                samples += (System.nanoTime() - started) / 1_000_000L
            }
            val mean = samples.average()
            val meanText = String.format(Locale.US, "%.3f", mean)
            writeStatus(
                app,
                "USAGE_QUERY samples=${samples.joinToString(",")} meanMs=$meanText windowMs=20000",
            )
        }

        private fun writeStatus(app: Context, line: String) {
            app.openFileOutput("debug-status.txt", Context.MODE_APPEND).bufferedWriter().use {
                it.appendLine(line)
            }
            Log.i(TAG, line)
        }

        /** @return false when a worker is already in flight. One worker only. */
        fun startEnable(
            app: MomentwiseApplication,
            packageName: String,
            label: String,
            resetSession: Boolean = false,
        ): Boolean {
            if (!running.compareAndSet(false, true)) return false
            thread(name = "debug-rule") {
                try {
                    enable(app, packageName, label, resetSession)
                } finally {
                    running.set(false)
                }
            }
            return true
        }

        private fun enable(
            app: MomentwiseApplication,
            packageName: String,
            label: String,
            resetSession: Boolean,
        ) {
            val grants = GrantChecks.read(app)
            if (resetSession) {
                runBlocking { app.container.guardSessions.save(GuardSnapshot()) }
                writeStatus(app, "session reset")
            }
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
