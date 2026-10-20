package com.nawzblaze.momentwise.intervention

/**
 * Shared facts about the live pause so the overlay, the activity, and tests
 * describe the same surface.
 *
 * The card is about the middle half of the screen. The rest is a visible
 * forest scrim, not a blur and not an invisible click catcher.
 */
object InterventionSurface {
    const val CARD_HEIGHT_FRACTION = 0.52f
    const val WINDOW_TITLE = "Momentwise intervention"

    /** Opening heading. The editable prompt is separate and comes from the rule. */
    const val OPENING_HEADING = "Before you scroll"

    const val REMINDER_HEADING = "Still here"
}
