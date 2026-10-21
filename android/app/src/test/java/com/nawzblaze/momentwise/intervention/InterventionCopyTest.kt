package com.nawzblaze.momentwise.intervention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InterventionCopyTest {

    @Test
    fun openingCopyDoesNotUseTheGenericPrompt() {
        assertEquals("Before you scroll", InterventionSurface.OPENING_HEADING)
        assertEquals("Take a second. What do you want from this app right now?", InterventionSurface.OPENING_SUPPORT)
        assertEquals("Open intentionally", InterventionSurface.CONTINUE_LABEL)
        assertEquals("Return to the home screen.", InterventionSurface.LEAVE_SUPPORT)
        assertEquals("Dismiss for today", InterventionSurface.DISMISS_FOR_TODAY)
        assertEquals("No more prompts for Instagram today.", InterventionSurface.dismissHelp("Instagram"))
        assertFalse(InterventionSurface.OPENING_HEADING.contains("What did you come here for"))
        assertFalse(InterventionSurface.OPENING_SUPPORT.contains("What do you want to do right now?"))
        assertFalse(InterventionSurface.OPENING_SUPPORT.contains("Anyways"))
    }

    @Test
    fun continueSupportFollowsTheSavedInterval() {
        assertEquals("Reminder after 5 minutes of use.", InterventionSurface.continueSupport(5))
        assertEquals("Reminder after 2 minutes of use.", InterventionSurface.continueSupport(2))
        assertEquals("Reminder after 1 minute of use.", InterventionSurface.continueSupport(1))
        assertEquals("Reminder after 12 minutes of use.", InterventionSurface.continueSupport(12))
        assertEquals("Continue without a reminder.", InterventionSurface.continueSupport(null))
        assertEquals("Continue without a reminder.", InterventionSurface.continueSupport(0))
        assertFalse(InterventionSurface.continueSupport(null).contains("5 minutes"))
    }
}
