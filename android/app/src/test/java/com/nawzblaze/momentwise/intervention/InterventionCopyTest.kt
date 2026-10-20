package com.nawzblaze.momentwise.intervention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InterventionCopyTest {

    @Test
    fun openingCopyDoesNotUseTheGenericPrompt() {
        assertEquals("What did you come here for?", InterventionSurface.OPENING_HEADING)
        assertEquals("Take a moment. Choose what comes next.", InterventionSurface.OPENING_SUPPORT)
        assertEquals("Continue intentionally", InterventionSurface.CONTINUE_LABEL)
        assertEquals("Return to your home screen.", InterventionSurface.LEAVE_SUPPORT)
        assertEquals("Dismiss for today", InterventionSurface.DISMISS_FOR_TODAY)
        assertFalse(InterventionSurface.OPENING_HEADING.contains("Before you scroll"))
        assertFalse(InterventionSurface.OPENING_SUPPORT.contains("What do you want to do right now?"))
        assertFalse(InterventionSurface.OPENING_SUPPORT.contains("Anyways"))
    }

    @Test
    fun continueSupportFollowsTheSavedInterval() {
        assertEquals("Remind me in 5 minutes.", InterventionSurface.continueSupport(5))
        assertEquals("Remind me in 2 minutes.", InterventionSurface.continueSupport(2))
        assertEquals("Remind me in 1 minute.", InterventionSurface.continueSupport(1))
        assertEquals("Remind me in 12 minutes.", InterventionSurface.continueSupport(12))
        assertEquals("Continue without a reminder.", InterventionSurface.continueSupport(null))
        assertEquals("Continue without a reminder.", InterventionSurface.continueSupport(0))
        assertFalse(InterventionSurface.continueSupport(null).contains("5 minutes"))
    }
}
