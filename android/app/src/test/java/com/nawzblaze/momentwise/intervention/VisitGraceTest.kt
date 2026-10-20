package com.nawzblaze.momentwise.intervention

import com.nawzblaze.momentwise.data.local.GuardSnapshotJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone failure, with a fake clock. Instagram is only a package name here.
 * This does not prove a physical device showed the pause.
 */
class VisitGraceTest {

    private val instagram = "com.instagram.android"
    private val chrome = "com.android.chrome"
    private val today = "2026-10-20"

    @Test
    fun continueHomeThirtySecondsThenThreeMoreMinutesRemindsOnce() {
        var snapshot = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        val (twoMinutes, early) = accrue(snapshot, fromElapsed = 0, millis = 2 * 60_000L)
        assertFalse(early is GuardCommand.ShowReminder)
        assertEquals(2 * 60_000L, twoMinutes.useByPackage[instagram]?.accumulatedMillis)

        val away = SessionClock.onTemporarilyAway(twoMinutes, elapsedRealtime = 2 * 60_000L)
        val duringAway = SessionClock.onTick(
            away,
            foregroundPackage = "com.android.launcher",
            nowMillis = 2 * 60_000L + 30_000L,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 2 * 60_000L + 30_000L,
            intervalMinutes = 5,
        ).first
        assertEquals(2 * 60_000L, duringAway.useByPackage[instagram]?.accumulatedMillis)
        assertTrue(VisitLifecycle.withinGrace(duringAway.backgroundedAtElapsed, 2 * 60_000L + 30_000L))

        val returned = SessionClock.onOpened(duringAway, instagram, true, today).first
            .copy(backgroundedAtElapsed = null)
        assertEquals(1L, returned.visitId)
        assertEquals(GuardSnapshot.Phase.CONTINUING, returned.phase)
        assertFalse(SessionClock.needsOpening(returned, instagram, true, 2 * 60_000L + 30_000L))

        val (due, command) = accrue(returned, fromElapsed = 2 * 60_000L + 30_000L, millis = 3 * 60_000L)
        assertTrue(command is GuardCommand.ShowReminder)
        assertEquals(5 * 60_000L, due.useByPackage[instagram]?.accumulatedMillis)
        val again = SessionClock.onTick(
            due,
            instagram,
            5 * 60_000L + 31_000L,
            today,
            true,
            elapsedRealtime = 5 * 60_000L + 31_000L,
            intervalMinutes = 5,
        )
        assertFalse(again.second is GuardCommand.ShowReminder)
    }

    @Test
    fun fourMinutesFiftyNineSecondsDoesNotRemind() {
        val continued = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        val (almost, command) = accrue(continued, fromElapsed = 0, millis = 5 * 60_000L - 1_000L)
        assertFalse(command is GuardCommand.ShowReminder)
        assertEquals(5 * 60_000L - 1_000L, almost.useByPackage[instagram]?.accumulatedMillis)
    }

    @Test
    fun fiveWallClockMinutesWithLessForegroundUseDoesNotRemind() {
        val continued = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        val (used, _) = accrue(continued, fromElapsed = 0, millis = 60_000L)
        val away = SessionClock.onTemporarilyAway(used, 60_000L)
        val later = SessionClock.onTick(
            away,
            foregroundPackage = null,
            nowMillis = 60_000L + 5 * 60_000L,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 60_000L + 5 * 60_000L,
            screenInteractive = false,
            intervalMinutes = 5,
        )
        assertFalse(later.second is GuardCommand.ShowReminder)
        assertEquals(60_000L, later.first.useByPackage[instagram]?.accumulatedMillis)
    }

    @Test
    fun graceBoundaryIsSixtySeconds() {
        val open = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        val away = SessionClock.onTemporarilyAway(open, elapsedRealtime = 10_000L)
        val justInside = SessionClock.expireGrace(away, 10_000L + VisitLifecycle.GRACE_MILLIS - 1)
        assertEquals(GuardSnapshot.Phase.CONTINUING, justInside.phase)
        assertFalse(SessionClock.needsOpening(justInside, instagram, true, 10_000L + 59_999L))

        val atBoundary = SessionClock.expireGrace(away, 10_000L + VisitLifecycle.GRACE_MILLIS)
        assertEquals(GuardSnapshot.Phase.NONE, atBoundary.phase)
        assertTrue(SessionClock.needsOpening(atBoundary, instagram, true, 70_000L))
        val (reopened, command) = SessionClock.onOpened(atBoundary, instagram, true, today)
        assertTrue(command is GuardCommand.ShowOpening)
        assertEquals(2L, reopened.visitId)
    }

    @Test
    fun wakingOntoTheLauncherDoesNotEndAVisitInsideGrace() {
        val away = SessionClock.onTemporarilyAway(
            SessionClock.onContinue(
                SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
                intervalMinutes = 5,
                nowMillis = 0,
                today = today,
            ),
            elapsedRealtime = 10_000L,
        )
        val kept = SessionClock.reconcileAfterRestart(away, "com.android.launcher", 15_000L)
        assertEquals(GuardSnapshot.Phase.CONTINUING, kept.phase)
        assertEquals(instagram, kept.activePackage)
        val ended = SessionClock.reconcileAfterRestart(
            away.copy(backgroundedAtElapsed = null),
            "com.android.launcher",
            15_000L,
        )
        assertEquals(GuardSnapshot.Phase.NONE, ended.phase)
    }

    @Test
    fun explicitLeaveShowsAgainEvenIfTheReturnIsQuick() {
        val continued = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        val left = SessionClock.onQuit(continued)
        assertEquals(GuardSnapshot.Phase.NONE, left.phase)
        assertEquals(instagram, left.quitSuppressedPackage)
        val observed = SessionClock.onTemporarilyAway(left, 2_000L)
        assertEquals(null, observed.quitSuppressedPackage)
        val (again, command) = SessionClock.onOpened(observed, instagram, true, today)
        assertTrue(command is GuardCommand.ShowOpening)
        assertEquals(2L, again.visitId)
    }

    @Test
    fun leaveDoesNotResumeAsContinueEvenIfAGraceStampRemains() {
        val continued = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        assertTrue(continued.continuedVisit)
        val left = SessionClock.onQuit(continued).copy(
            recentAwayPackage = instagram,
            recentAwayAtElapsed = 1_000,
            continuedVisit = true,
        )
        assertTrue(left.explicitLeavePending)
        val (immediate, immediateCommand) = SessionClock.onOpened(left, instagram, true, today)
        assertFalse(immediateCommand is GuardCommand.ShowOpening)
        val (again, command) = SessionClock.onOpened(left, instagram, true, today, 5_000)
        assertTrue(command is GuardCommand.ShowOpening)
        assertFalse(again.explicitLeavePending)
        assertFalse(again.continuedVisit)
        assertEquals(GuardSnapshot.Phase.AWAITING_OPENING, again.phase)
    }

    @Test
    fun graceStampWithoutContinueIsANewOpening() {
        val stamped = GuardSnapshot(
            phase = GuardSnapshot.Phase.NONE,
            lastGuardedPackage = instagram,
            recentAwayPackage = instagram,
            recentAwayAtElapsed = 5_000,
        )
        val (again, command) = SessionClock.onOpened(stamped, instagram, true, today, 8_000)
        assertTrue(command is GuardCommand.ShowOpening)
        assertEquals(GuardSnapshot.Phase.AWAITING_OPENING, again.phase)
    }

    @Test
    fun continuedVisitSurvivesAShortTripHome() {
        val continued = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        val away = SessionClock.onLeft(continued, 2_000).first
        assertTrue(away.continuedVisit)
        assertFalse(away.explicitLeavePending)
        val (back, command) = SessionClock.onOpened(away, instagram, true, today, 8_000)
        assertFalse(command is GuardCommand.ShowOpening)
        assertEquals(GuardSnapshot.Phase.CONTINUING, back.phase)
        assertEquals(1L, back.visitId)
    }

    @Test
    fun unansweredEndDoesNotResumeAsContinue() {
        val opening = SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first
        val ended = SessionClock.onLeft(opening, 5_000).first
        assertEquals(null, ended.recentAwayAtElapsed)
        val (again, command) = SessionClock.onOpened(ended, instagram, true, today, 8_000)
        assertTrue(command is GuardCommand.ShowOpening)
        assertEquals(2L, again.visitId)
    }

    @Test
    fun finishExplicitLeaveDoesNotKeepAGraceWindow() {
        val left = SessionClock.onQuit(
            SessionClock.onContinue(
                SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
                intervalMinutes = 5,
                nowMillis = 0,
                today = today,
            ),
        ).copy(recentAwayAtElapsed = 1_000, lastGuardedPackage = instagram)
        val finished = SessionClock.finishExplicitLeave(left)
        assertEquals(null, finished.quitSuppressedPackage)
        assertEquals(null, finished.recentAwayAtElapsed)
        val (again, command) = SessionClock.onOpened(finished, instagram, true, today, 3_000)
        assertTrue(command is GuardCommand.ShowOpening)
        assertEquals(2L, again.visitId)
    }

    @Test
    fun aSlowPollStillCountsAndATwentySecondGapDoesNot() {
        val started = AppUse(budgetMillis = 60_000, phase = AppUse.Phase.ACCRUING, anchorElapsed = 1_000)
        val (credited, _) = ActiveUse.sample(started, elapsedRealtime = 5_000, eligible = true)
        assertEquals(4_000L, credited.accumulatedMillis)
        val (skipped, show) = ActiveUse.sample(started, elapsedRealtime = 21_000, eligible = true)
        assertFalse(show)
        assertEquals(0L, skipped.accumulatedMillis)
    }

    @Test
    fun reloadingDiskDoesNotWipeTheAnchorOrInventDowntime() {
        val live = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.CONTINUING,
            visitId = 3,
            openingHandledForPackage = instagram,
            useByPackage = mapOf(
                instagram to AppUse(
                    accumulatedMillis = 120_000,
                    budgetMillis = 300_000,
                    phase = AppUse.Phase.ACCRUING,
                    anchorElapsed = 500_000,
                ),
            ),
        )
        val stored = GuardSnapshotJson.decode(GuardSnapshotJson.encode(live))
        assertEquals(null, stored.useByPackage[instagram]?.anchorElapsed)
        val adopted = SessionClock.adoptStored(stored, live)
        assertEquals(500_000L, adopted.useByPackage[instagram]?.anchorElapsed)
        val (next, command) = SessionClock.onTick(
            adopted,
            instagram,
            504_000,
            today,
            true,
            elapsedRealtime = 504_000,
            intervalMinutes = 5,
        )
        assertFalse(command is GuardCommand.ShowReminder)
        assertEquals(124_000L, next.useByPackage[instagram]?.accumulatedMillis)

        val restarted = SessionClock.adoptStored(stored, GuardSnapshot())
        val (afterDeath, invented) = SessionClock.onTick(
            restarted,
            instagram,
            540_000,
            today,
            true,
            elapsedRealtime = 540_000,
            intervalMinutes = 5,
        )
        assertFalse(invented is GuardCommand.ShowReminder)
        assertEquals(120_000L, afterDeath.useByPackage[instagram]?.accumulatedMillis)
    }

    @Test
    fun chromeDoesNotInheritInstagramUse() {
        val instagramUse = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        val (used, _) = accrue(instagramUse, fromElapsed = 0, millis = 60_000L)
        val switched = SessionClock.onOpened(
            SessionClock.onLeft(used).first,
            chrome,
            true,
            today,
        ).first
        assertEquals(0L, switched.useByPackage[chrome]?.accumulatedMillis ?: 0L)
        assertEquals(60_000L, switched.useByPackage[instagram]?.accumulatedMillis)
    }

    @Test
    fun neverDoesNotScheduleAReminder() {
        val continued = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = null,
            nowMillis = 0,
            today = today,
        )
        val (used, command) = SessionClock.onTick(
            continued,
            instagram,
            10 * 60_000L,
            today,
            remindersEnabled = false,
            elapsedRealtime = 10 * 60_000L,
            intervalMinutes = null,
        )
        assertFalse(command is GuardCommand.ShowReminder)
        assertEquals(AppUse.Phase.IDLE, used.useByPackage[instagram]?.phase)
    }

    @Test
    fun lastGuardedPackageCoversALostAwayPackage() {
        val ended = GuardSnapshot(
            phase = GuardSnapshot.Phase.NONE,
            visitId = 4,
            lastGuardedPackage = instagram,
            recentAwayAtElapsed = 50_000,
            continuedVisit = true,
        )
        assertFalse(SessionClock.needsOpening(ended, instagram, true, 70_000))
        assertTrue(SessionClock.needsOpening(ended.copy(continuedVisit = false), instagram, true, 70_000))
    }

    private fun accrue(
        start: GuardSnapshot,
        fromElapsed: Long,
        millis: Long,
    ): Pair<GuardSnapshot, GuardCommand> {
        var snapshot = start
        var elapsed = fromElapsed
        var command: GuardCommand = GuardCommand.None
        // The first sample only starts the in-memory anchor. It is not use.
        snapshot = SessionClock.onTick(
            snapshot,
            instagram,
            elapsed,
            today,
            true,
            elapsedRealtime = elapsed,
            intervalMinutes = 5,
        ).first
        val steps = (millis / 1_000L).toInt()
        repeat(steps) {
            elapsed += 1_000
            val step = SessionClock.onTick(
                snapshot,
                instagram,
                elapsed,
                today,
                true,
                elapsedRealtime = elapsed,
                intervalMinutes = 5,
            )
            snapshot = step.first
            command = step.second
            if (command is GuardCommand.ShowReminder) return snapshot to command
        }
        return snapshot to command
    }
}
