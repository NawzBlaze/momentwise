package com.nawzblaze.momentwise.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nawzblaze.momentwise.MomentwiseApplication
import kotlinx.coroutines.launch

/**
 * Explicit user action from the ongoing monitoring notification.
 *
 * The notification is required while the foreground monitor is active. This
 * action gives the user a one-tap way to pause monitoring and remove it, without
 * disabling or deleting any of their saved app rules.
 */
class PauseMonitoringReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_PAUSE) return
        val pending = goAsync()
        val app = context.applicationContext as? MomentwiseApplication
        if (app == null) {
            pending.finish()
            return
        }

        app.applicationScope.launch {
            try {
                app.container.appSettingsRepository.setMonitoringPaused(true)
                MonitoringService.stop(app)
            } catch (error: Exception) {
                Log.w(TAG, "Could not pause monitoring from notification", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.nawzblaze.momentwise.action.PAUSE_MONITORING"
        private const val TAG = "Momentwise"
    }
}
