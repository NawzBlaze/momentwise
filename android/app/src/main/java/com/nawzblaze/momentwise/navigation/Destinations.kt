package com.nawzblaze.momentwise.navigation

/** Route definitions for the primary screens. */
object Destinations {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val APPS = "apps"
    const val SETTINGS = "settings"

    const val CONFIGURE_ARG = "appId"
    const val CONFIGURE = "configure/{$CONFIGURE_ARG}"
    fun configure(appId: String) = "configure/$appId"

    const val PREVIEW_OPENING_ARG = "appId"
    const val PREVIEW_OPENING = "preview/opening/{$PREVIEW_OPENING_ARG}"
    fun previewOpening(appId: String) = "preview/opening/$appId"

    const val PREVIEW_REMINDER_ARG = "appId"
    const val PREVIEW_REMINDER = "preview/reminder/{$PREVIEW_REMINDER_ARG}"
    fun previewReminder(appId: String) = "preview/reminder/$appId"

    /** Destinations that show the primary bottom navigation. */
    val BOTTOM_NAV_ROUTES = setOf(HOME, APPS, SETTINGS)
}
