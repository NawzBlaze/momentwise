package com.nawzblaze.momentwise.monitoring

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Special access Momentwise can check, but cannot grant itself.
 * Usage Access and display-over-other-apps are Settings screens, not runtime dialogs.
 */
data class GrantStatus(
    val usageAccess: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
) {
    /** Usage Access plus a notification channel the foreground service is allowed to post. */
    val canMonitor: Boolean get() = usageAccess && notifications

    /** The pause can appear by itself only when an overlay is also allowed. */
    val pauseAppearsOnItsOwn: Boolean get() = canMonitor && overlay
}

object GrantChecks {

    fun read(context: Context): GrantStatus = GrantStatus(
        usageAccess = usageAccessGranted(context),
        overlay = Settings.canDrawOverlays(context),
        notifications = canPostNotifications(context),
    )

    private fun usageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
