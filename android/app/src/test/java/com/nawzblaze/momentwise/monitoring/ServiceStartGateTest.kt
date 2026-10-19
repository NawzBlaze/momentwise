package com.nawzblaze.momentwise.monitoring

import com.nawzblaze.momentwise.intervention.DetectorSignal
import com.nawzblaze.momentwise.intervention.DetectorState
import com.nawzblaze.momentwise.intervention.EventWindow
import com.nawzblaze.momentwise.intervention.ForegroundEvent
import com.nawzblaze.momentwise.intervention.GuardCommand
import com.nawzblaze.momentwise.intervention.GuardSnapshot
import com.nawzblaze.momentwise.intervention.SessionClock
import com.nawzblaze.momentwise.intervention.UsageEventReducer
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStartGateTest {

    @Test
    fun aMainThreadBlockAtTheDeadlineIsUnsafe() {
        assertFalse(ForegroundStartContract.blocksTheDeadline(ForegroundStartContract.DEADLINE_MILLIS - 1))
        assertTrue(ForegroundStartContract.blocksTheDeadline(ForegroundStartContract.DEADLINE_MILLIS))
        assertTrue(ForegroundStartContract.blocksTheDeadline(10_000L))
    }

    @Test
    fun onlyTheForegroundCallMayPrecedeStartForeground() {
        assertTrue(
            ForegroundStartContract.mayRunBeforeStartForeground(
                ForegroundStartContract.Step.START_FOREGROUND,
            ),
        )
        assertFalse(
            ForegroundStartContract.mayRunBeforeStartForeground(
                ForegroundStartContract.Step.RECORD_ATTEMPT,
            ),
        )
        assertFalse(
            ForegroundStartContract.mayRunBeforeStartForeground(
                ForegroundStartContract.Step.READ_RULES,
            ),
        )
        assertFalse(
            ForegroundStartContract.mayRunBeforeStartForeground(
                ForegroundStartContract.Step.POLL,
            ),
        )
    }

    @Test
    fun anActivePollIsNotDuplicated() {
        assertFalse(PollSupervisor.shouldLaunch(existingActive = true, stopRequested = false))
    }

    @Test
    fun aStoppingPollMayBeReplaced() {
        assertTrue(PollSupervisor.shouldLaunch(existingActive = true, stopRequested = true))
        assertTrue(PollSupervisor.shouldLaunch(existingActive = false, stopRequested = false))
    }

    @Test
    fun permissionLossDoesNotLookLikeARunningMonitor() {
        val now = 80_000L
        assertEquals(
            MonitorHealth.IDLE,
            MonitorHealthPolicy.assess(
                shouldRun = false,
                lastBeatMillis = now - 1_000L,
                startAttemptMillis = now - 2_000L,
                startFailed = false,
                nowMillis = now,
            ),
        )
        assertFalse(MonitoringPolicy.shouldRun(anyRuleEnabled = true, canMonitor = false, pausedByUser = false))
    }

    @Test
    fun aSeparatePackageIsDeliveredOnceAndAReplayIsNot() {
        val self = "com.nawzblaze.momentwise"
        val fixture = "com.nawzblaze.momentwise.fixture"
        assertNotEquals(self, fixture)
        val opened = UsageEventReducer.reduce(
            state = DetectorState(lastPackage = self, lastEventTimeMillis = 1_000L),
            events = listOf(
                ForegroundEvent(fixture, 5_000L, "com.nawzblaze.momentwise.fixture.FixtureActivity"),
            ),
            selfPackage = self,
            ignoredPackages = setOf("com.android.launcher"),
            ignoreSelf = false,
        )
        assertEquals(DetectorSignal.OPENED, opened.signal)
        assertEquals(fixture, opened.packageName)
        val (snapshot, command) = SessionClock.onOpened(GuardSnapshot(), fixture, true, "2026-10-19")
        assertTrue(command is GuardCommand.ShowOpening)
        assertEquals(fixture, (command as GuardCommand.ShowOpening).packageName)

        val replay = UsageEventReducer.reduce(
            state = opened.state,
            events = listOf(ForegroundEvent(fixture, 5_000L)),
            selfPackage = self,
            ignoredPackages = setOf("com.android.launcher"),
            ignoreSelf = false,
        )
        assertEquals(DetectorSignal.NONE, replay.signal)
        assertFalse(SessionClock.needsOpening(snapshot, fixture, true))
    }

    @Test
    fun thePollWindowIsBoundedAndAlreadyAppliedEventsAreDropped() {
        val now = 60_000L
        val start = EventWindow.queryStart(now)
        assertTrue(UsagePoll.queryIsBounded(now, start))
        assertEquals(EventWindow.TRAILING_MILLIS, now - start)
        assertEquals(UsagePoll.INTERVAL_MILLIS, 1_000L)
        val events = (0 until 40).map { index ->
            ForegroundEvent("com.example.app", now - 15_000L + index * 100L)
        }
        val first = UsageEventReducer.reduce(
            DetectorState(),
            events,
            "com.nawzblaze.momentwise",
            emptySet(),
            ignoreSelf = false,
        )
        val second = UsageEventReducer.reduce(
            first.state,
            events,
            "com.nawzblaze.momentwise",
            emptySet(),
            ignoreSelf = false,
        )
        assertEquals(DetectorSignal.NONE, second.signal)
        assertEquals(first.state, second.state)
    }

    /**
     * Measures the pure reducer, not UsageStats IPC and not a phone.
     * The budget is the poll interval: a tick must not overlap the next one.
     */
    @Test
    fun reducerWorkForADenseWindowStaysInsideThePollInterval() {
        val now = 60_000L
        val events = (0 until 200).map { index ->
            ForegroundEvent(
                packageName = if (index % 2 == 0) "com.example.one" else "com.example.two",
                timeMillis = now - EventWindow.TRAILING_MILLIS + index * 50L,
            )
        }
        val repeats = 50
        val started = System.nanoTime()
        repeat(repeats) {
            UsageEventReducer.reduce(
                DetectorState(),
                events,
                "com.nawzblaze.momentwise",
                setOf("com.android.launcher"),
                ignoreSelf = false,
            )
        }
        val meanMillis = (System.nanoTime() - started) / 1_000_000.0 / repeats
        val meanText = String.format(Locale.US, "%.3f", meanMillis)
        val line = "POLL_MEASURE events=200 repeats=$repeats meanMillis=$meanText intervalMillis=${UsagePoll.INTERVAL_MILLIS}"
        File("build").mkdirs()
        File("build/poll-measure.txt").writeText(line)
        assertTrue(line, meanMillis < UsagePoll.INTERVAL_MILLIS)
    }
}
