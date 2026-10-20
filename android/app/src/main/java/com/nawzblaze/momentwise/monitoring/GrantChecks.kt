package com.nawzblaze.momentwise.monitoring

import android.app.AppOpsManager
import android.app.NotificationManager
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

    /** The system dialog, not the Settings page. False once the runtime grant is on. */
    fun notificationRuntimeMissing(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    }

    fun read(context: Context): GrantStatus = GrantStatus(
        usageAccess = GrantSignals.usageAllowed(
            appOpsMode = opMode(context, AppOpsManager.OPSTR_GET_USAGE_STATS),
            selfPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.PACKAGE_USAGE_STATS,
            ) == PackageManager.PERMISSION_GRANTED,
        ),
        overlay = GrantSignals.overlayAllowed(
            settingsSaysYes = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false),
            appOpsMode = opMode(context, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW),
        ),
        notifications = GrantSignals.notificationsAllowed(
            requiresRuntime = Build.VERSION.SDK_INT >= 33,
            runtimeGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
            managerEnabled = context.getSystemService(NotificationManager::class.java)
                ?.areNotificationsEnabled() ?: true,
        ),
    )

    /**
     * Some phones update [AppOpsManager.checkOpNoThrow] and not
     * [AppOpsManager.unsafeCheckOpNoThrow], or the reverse. Allowed on either
     * read is the grant the Settings screen is showing.
     */
    @Suppress("DEPRECATION")
    private fun opMode(context: Context, op: String): Int? {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return null
        val uid = Process.myUid()
        val packageName = context.packageName
        return try {
            val checked = appOps.checkOpNoThrow(op, uid, packageName)
            val unsafe = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(op, uid, packageName)
            } else {
                checked
            }
            GrantSignals.preferAllowed(checked, unsafe)
        } catch (_: SecurityException) {
            null
        }
    }
}
