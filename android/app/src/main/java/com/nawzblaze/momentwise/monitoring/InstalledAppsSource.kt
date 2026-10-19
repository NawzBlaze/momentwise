package com.nawzblaze.momentwise.monitoring

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.nawzblaze.momentwise.data.LaunchableApp

/**
 * Launchable apps only (`MAIN` / `LAUNCHER`). This is not `QUERY_ALL_PACKAGES`.
 * Momentwise itself is excluded so a rule cannot pause the pause.
 */
class InstalledAppsSource(private val context: Context) {

    fun load(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        return resolved.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            val label = info.loadLabel(context.packageManager)?.toString()?.ifBlank { null } ?: packageName
            LaunchableApp(packageName = packageName, label = label)
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun defaultLauncherPackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.resolveActivity(
                home,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName
    }
}
