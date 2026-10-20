package com.nawzblaze.momentwise.intervention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InterventionCopyTest {

    @Test
    fun openingCopyDoesNotUseTheGenericPrompt() {
        assertEquals("Is this where you want your time to go?", InterventionSurface.OPENING_HEADING)
        assertEquals("Take a breath. Choose what you want to do next.", InterventionSurface.OPENING_SUPPORT)
        assertEquals("I'm here with purpose", InterventionSurface.CONTINUE_LABEL)
        assertFalse(InterventionSurface.OPENING_HEADING.contains("Before you scroll"))
        assertFalse(InterventionSurface.OPENING_SUPPORT.contains("What do you want to do right now?"))
        assertFalse(InterventionSurface.OPENING_SUPPORT.contains("Anyways"))
    }

    @Test
    fun continueSupportFollowsTheSavedInterval() {
        assertEquals("Continue, with a reminder in 5 minutes.", InterventionSurface.continueSupport(5))
        assertEquals("Continue, with a reminder in 1 minute.", InterventionSurface.continueSupport(1))
        assertEquals("Continue, with a reminder in 12 minutes.", InterventionSurface.continueSupport(12))
        assertEquals("Continue. No reminder will be scheduled.", InterventionSurface.continueSupport(null))
        assertEquals("Continue. No reminder will be scheduled.", InterventionSurface.continueSupport(0))
        assertFalse(InterventionSurface.continueSupport(null).contains("5 minutes"))
    }
}
