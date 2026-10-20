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
    val snoozeMinutes: Int = 10,
    val fromNotification: Boolean = false,
) {
    enum class Kind { OPENING, REMINDER }
}
