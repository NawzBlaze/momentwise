package com.nawzblaze.momentwise.monitoring

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** System screens for the three grants. None of these grant the access by themselves. */
object GrantIntents {

    fun usageAccess(context: Context): Intent {
        val withPackage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return if (withPackage.resolveActivity(context.packageManager) != null) {
            withPackage
        } else {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
    }

    fun overlay(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    /** Some OEM Settings screens are missing. A failed launch must not crash the disclosure. */
    fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // The screen stays put so the user can decline.
        }
    }
}
