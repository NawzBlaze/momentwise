package com.nawzblaze.momentwise.intervention

/** What the pause UI needs to render. Built by the service or the notification tap. */
data class PauseRequest(
    val packageName: String,
    val appName: String,
    val kind: Kind,
    val notice: String,
    val heading: String = InterventionSurface.OPENING_HEADING,
    val primaryLabel: String,
    val secondaryLabel: String,
    val tertiaryLabel: String? = null,
    val primarySupport: String = InterventionSurface.LEAVE_SUPPORT,
    val secondarySupport: String = "",
    val snoozeMinutes: Int = 10,
    val fromNotification: Boolean = false,
    /** Foreground-use credit for the current reminder interval, not total daily use. */
    val usedMinutes: Int? = null,
    /** The configured in-app-use interval for this reminder, if enabled. */
    val reminderIntervalMinutes: Int? = null,
) {
    enum class Kind { OPENING, REMINDER }
}
