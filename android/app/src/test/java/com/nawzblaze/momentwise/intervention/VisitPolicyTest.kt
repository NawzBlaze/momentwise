package com.nawzblaze.momentwise.intervention

import org.junit.Assert.assertEquals
import org.junit.Test

class VisitPolicyTest {

    private val instagram = "com.instagram.android"
    private val self = "com.nawzblaze.momentwise"
    private val chrome = "com.android.chrome"

    @Test
    fun pauseCausedStopIsNotALeave() {
        val visible = VisitPolicy.Latch(
            openPackage = instagram,
            pauseVisible = true,
            phase = GuardSnapshot.Phase.AWAITING_OPENING,
        )
        assertEquals(
            VisitPolicy.Effect.NONE,
            VisitPolicy.effect(visible, DetectorSignal.LEFT, instagram, self),
        )
        assertEquals(
            VisitPolicy.Effect.NONE,
            VisitPolicy.effect(visible, DetectorSignal.LEFT, self, self),
        )
        assertEquals(
            VisitPolicy.Effect.NONE,
            VisitPolicy.effect(visible, DetectorSignal.OPENED, self, self),
        )
        assertEquals(
            VisitPolicy.Effect.NONE,
            VisitPolicy.effect(visible, DetectorSignal.OPENED, instagram, self),
        )
        assertEquals(
            VisitPolicy.Effect.NONE,
            VisitPolicy.effect(visible, DetectorSignal.LEFT, "com.android.launcher", self),
        )
    }

    @Test
    fun samePackageResumeAfterNoChoiceIsNotANewVisit() {
        val open = VisitPolicy.Latch(openPackage = instagram, pauseVisible = false)
        assertEquals(
            VisitPolicy.Effect.NONE,
            VisitPolicy.effect(open, DetectorSignal.OPENED, instagram, self),
        )
    }

    @Test
    fun anUnansweredOpeningEndsWhenTheCardIsGoneAndTheUserLeaves() {
        val waiting = VisitPolicy.Latch(
            openPackage = instagram,
            pauseVisible = false,
            phase = GuardSnapshot.Phase.AWAITING_OPENING,
        )
        assertEquals(
            VisitPolicy.Effect.END_VISIT,
            VisitPolicy.effect(waiting, DetectorSignal.LEFT, "com.android.launcher", self),
        )
    }

    @Test
    fun aDifferentPackageWhileTheCardIsUpDoesNotReplaceTheVisit() {
        val visible = VisitPolicy.Latch(
            openPackage = instagram,
            pauseVisible = true,
            phase = GuardSnapshot.Phase.AWAITING_OPENING,
        )
        assertEquals(
            VisitPolicy.Effect.NONE,
            VisitPolicy.effect(visible, DetectorSignal.OPENED, chrome, self),
        )
    }

    @Test
    fun aDifferentPackageThenAReopenIsANewVisit() {
        val open = VisitPolicy.Latch(openPackage = instagram, pauseVisible = false)
        assertEquals(
            VisitPolicy.Effect.END_VISIT,
            VisitPolicy.effect(open, DetectorSignal.LEFT, chrome, self),
        )
        val ended = VisitPolicy.Latch(openPackage = null, pauseVisible = false)
        assertEquals(
            VisitPolicy.Effect.BEGIN_VISIT,
            VisitPolicy.effect(ended, DetectorSignal.OPENED, instagram, self),
        )
    }
}
