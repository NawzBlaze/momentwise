package com.nawzblaze.momentwise.ui.screens

import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.data.model.ReminderFrequency

/** Short chip label for a rule's reminder schedule ("No reminders" / "10m reminders"). */
fun AppRule.reminderChipLabel(): String = when (val frequency = reminderFrequency) {
    ReminderFrequency.Never -> "No reminders"
    is ReminderFrequency.EveryMinutes -> "${frequency.minutes}m reminders"
}

/** Status line shown under each app in the Apps list. */
fun AppRule.appsRowStatusLabel(notGuarded: String, openingOnly: String): String = when {
    !enabled -> notGuarded
    reminderFrequency is ReminderFrequency.Never -> openingOnly
    else -> "Guarded • ${reminderChipLabel().removeSuffix(" reminders")} of use"
}

/** "Scheduled check-in after N minutes in X" style copy; null when reminders are off. */
fun AppRule.scheduledCheckInLabel(appNamePlaceholder: String = appName): String? =
    reminderFrequency.minutesOrNull()
        ?.let { "Reminder after $it minutes of use in $appNamePlaceholder" }
