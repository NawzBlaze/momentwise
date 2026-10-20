package com.nawzblaze.momentwise.monitoring

/**
 * How a grant is decided from the signals Android actually returns.
 * A button tap is not a signal. Opening Settings is not a signal.
 *
 * AppOps modes match [android.app.AppOpsManager]: allowed 0, ignored 1,
 * errored 2, default 3. A null mode means the check could not be read.
 */
object GrantSignals {
    const val MODE_ALLOWED = 0
    const val MODE_IGNORED = 1
    const val MODE_ERRORED = 2
    const val MODE_DEFAULT = 3

    fun usageAllowed(appOpsMode: Int?, selfPermissionGranted: Boolean): Boolean =
        appOpsMode == MODE_ALLOWED || (appOpsMode == MODE_DEFAULT && selfPermissionGranted)

    /**
     * Settings and AppOps disagree on some phones. Either one saying allowed
     * is the grant. Neither saying allowed stays not allowed.
     */
    fun overlayAllowed(settingsSaysYes: Boolean, appOpsMode: Int?): Boolean =
        settingsSaysYes || appOpsMode == MODE_ALLOWED

    fun notificationsAllowed(
        requiresRuntime: Boolean,
        runtimeGranted: Boolean,
        managerEnabled: Boolean,
    ): Boolean = managerEnabled && (!requiresRuntime || runtimeGranted)

    /** The more permissive of two AppOps reads, when an OEM updates only one. */
    fun preferAllowed(first: Int?, second: Int?): Int? = when {
        first == MODE_ALLOWED || second == MODE_ALLOWED -> MODE_ALLOWED
        first == MODE_DEFAULT || second == MODE_DEFAULT -> MODE_DEFAULT
        first != null -> first
        else -> second
    }
}
