package com.nawzblaze.momentwise.intervention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveUseTest {

    private val instagram = "com.instagram.android"
    private val today = "2026-10-20"

    @Test
    fun aShortVisitDoesNotRemindAfterALongAbsence() {
        val continued = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        val (used, command) = SessionClock.onTick(
            continued,
            foregroundPackage = instagram,
            nowMillis = 1_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 1_000,
            intervalMinutes = 5,
        )
        assertEquals(GuardCommand.None, command)
        val left = SessionClock.onLeft(used).first
        val absent = SessionClock.onTick(
            left,
            foregroundPackage = "com.android.launcher",
            nowMillis = 1_000 + 30 * 60_000L,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 1_000 + 30 * 60_000L,
            intervalMinutes = 5,
        )
        assertEquals(GuardCommand.None, absent.second)
        assertTrue((left.useByPackage[instagram]?.accumulatedMillis ?: 0) < 5 * 60_000L)
    }

    @Test
    fun useResumesAfterAnotherContinueAndRemindsOnlyAtTheBudget() {
        var snapshot = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        snapshot = sample(snapshot, 0, 1_000)
        snapshot = SessionClock.onLeft(snapshot).first
        val returned = SessionClock.onContinue(
            SessionClock.onOpened(snapshot, instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 60_000,
            today = today,
        )
        assertTrue((returned.useByPackage[instagram]?.accumulatedMillis ?: 0) < 5 * 60_000L)
        val (due, command) = accrue(returned, start = 60_000, steps = 400)
        assertTrue(command is GuardCommand.ShowReminder)
        assertTrue((due.useByPackage[instagram]?.accumulatedMillis ?: 0) >= 5 * 60_000L)
    }

    @Test
    fun aMissedPollDoesNotCreditTheGap() {
        val started = AppUse(budgetMillis = 60_000, phase = AppUse.Phase.ACCRUING, anchorElapsed = 1_000)
        val (next, show) = ActiveUse.sample(started, elapsedRealtime = 30_000, eligible = true)
        assertFalse(show)
        assertEquals(0L, next.accumulatedMillis)
    }

    @Test
    fun bootKeepsCompletedUseAndDropsTheOpenSegment() {
        val active = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.CONTINUING,
            useByPackage = mapOf(
                instagram to AppUse(
                    accumulatedMillis = 12_000,
                    budgetMillis = 60_000,
                    phase = AppUse.Phase.ACCRUING,
                    anchorElapsed = 50_000,
                ),
            ),
        )
        val booted = SessionClock.onBoot(active)
        assertEquals(GuardSnapshot.Phase.NONE, booted.phase)
        assertEquals(12_000L, booted.useByPackage[instagram]?.accumulatedMillis)
        assertEquals(null, booted.useByPackage[instagram]?.anchorElapsed)
    }

    @Test
    fun momentwiseUiAndScreenOffAreNotCredited() {
        val started = SessionClock.onContinue(
            GuardSnapshot(activePackage = instagram, phase = GuardSnapshot.Phase.AWAITING_OPENING),
            intervalMinutes = 5,
            nowMillis = 0,
            today = today,
        )
        val covered = SessionClock.onTick(
            started,
            foregroundPackage = instagram,
            nowMillis = 2_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 2_000,
            pauseVisible = true,
            intervalMinutes = 5,
        ).first
        val dark = SessionClock.onTick(
            covered,
            foregroundPackage = instagram,
            nowMillis = 4_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 4_000,
            screenInteractive = false,
            intervalMinutes = 5,
        ).first
        assertEquals(0L, dark.useByPackage[instagram]?.accumulatedMillis)
    }

    private fun sample(snapshot: GuardSnapshot, from: Long, to: Long): GuardSnapshot {
        val opened = SessionClock.onTick(
            snapshot,
            instagram,
            from,
            today,
            true,
            elapsedRealtime = from,
            intervalMinutes = 5,
        ).first
        return SessionClock.onTick(
            opened,
            instagram,
            to,
            today,
            true,
            elapsedRealtime = to,
            intervalMinutes = 5,
            maxCreditMillis = 1_000,
        ).first
    }

    private fun accrue(start: GuardSnapshot, start: Long, steps: Int): Pair<GuardSnapshot, GuardCommand> {
        var snapshot = start
        var elapsed = start
        var command: GuardCommand = GuardCommand.None
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
                maxCreditMillis = 1_000,
            )
            snapshot = step.first
            command = step.second
            if (command is GuardCommand.ShowReminder) return snapshot to command
        }
        return snapshot to command
    }
}
