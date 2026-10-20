package com.nawzblaze.momentwise.monitoring

/**
 * One control for a grant. Allowed is not a second status row plus a review button.
 * The label comes from the grant Android reports, never from the tap itself.
 */
object GrantPresentation {
    const val ALLOW = "Allow"
    const val ALLOWED = "Allowed ✓"

    fun label(allowed: Boolean): String = if (allowed) ALLOWED else ALLOW

    fun separateStatusShown(): Boolean = false
}
