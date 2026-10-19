package com.nawzblaze.momentwise.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nawzblaze.momentwise.MomentwiseApplication
import com.nawzblaze.momentwise.intervention.SessionClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * After reboot, an in-progress session is not treated as "they were still in
 * the app". Monitoring restarts only if a rule is enabled and the grants remain.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as MomentwiseApplication
        BootFlags.dropSessionOnNextStart = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.guardSessions.update { SessionClock.onBoot(it) }
                val enabled = app.container.appRuleRepository.rules.first().any { it.enabled }
                if (enabled && GrantChecks.read(app).canMonitor) {
                    MonitoringService.start(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
