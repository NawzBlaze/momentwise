package com.nawzblaze.momentwise.monitoring

import com.nawzblaze.momentwise.intervention.DetectorState
import com.nawzblaze.momentwise.intervention.EventWindow
import com.nawzblaze.momentwise.intervention.ForegroundEvent
import com.nawzblaze.momentwise.intervention.GuardSnapshot
import com.nawzblaze.momentwise.intervention.SessionClock
import com.nawzblaze.momentwise.intervention.UsageEventReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorReliabilityTest {

    @Test
    fun trailingWindowStillContainsALateUsageEvent() {
        val now = 120_000L
        val eventAt = now - 8_000L
        val start = EventWindow.queryStart(now)
        assertTrue(eventAt >= start)
        assertTrue(eventAt < now)
        // The old one-second overlap would have started at now - 1s and dropped this event.
        assertTrue(eventAt < now - 1_000L)
    }

    @Test
    fun reducerStillAppliesAnEventInsideTheTrailingWindow() {
        val now = 120_000L
        val late = ForegroundEvent("com.instagram.android", now - 8_000L)
        val result = UsageEventReducer.reduce(
            state = DetectorState(lastPackage = "com.android.launcher", lastEventTimeMillis = now - 20_000L),
            events = listOf(late),
            selfPackage = "com.nawzblaze.momentwise",
            ignoredPackages = setOf("com.android.launcher"),
            ignoreSelf = false,
        )
        assertEquals("com.instagram.android", result.packageName)
    }

    @Test
    fun missedOpeningIsRequiredWhenTheSessionNeverStarted() {
        assertTrue(
            SessionClock.needsOpening(
                snapshot = GuardSnapshot(),
                foregroundPackage = "com.instagram.android",
                ruleEnabled = true,
            ),
        )
    }

    @Test
    fun missedOpeningIsNotASecondOpeningAfterContinue() {
        val continuing = SessionClock.onContinue(
            snapshot = SessionClock.onOpened(
                GuardSnapshot(),
                "com.instagram.android",
                true,
                "2026-10-19",
            ).first,
            intervalMinutes = 5,
            nowMillis = 1_000L,
            today = "2026-10-19",
        )
        assertFalse(
            SessionClock.needsOpening(continuing, "com.instagram.android", true),
        )
    }

    @Test
    fun aFreshHeartbeatIsRunning() {
        val now = 50_000L
        assertEquals(
            MonitorHealth.RUNNING,
            MonitorHealthPolicy.assess(
                shouldRun = true,
                lastBeatMillis = now - 5_000L,
                startAttemptMillis = now - 10_000L,
                startFailed = false,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun aStaleHeartbeatIsStoppedEvenIfRulesLookEnabled() {
        val now = 50_000L
        assertEquals(
            MonitorHealth.STOPPED,
            MonitorHealthPolicy.assess(
                shouldRun = true,
                lastBeatMillis = now - 30_000L,
                startAttemptMillis = now - 40_000L,
                startFailed = false,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun startFailureIsStoppedImmediately() {
        assertEquals(
            MonitorHealth.STOPPED,
            MonitorHealthPolicy.assess(
                shouldRun = true,
                lastBeatMillis = 0L,
                startAttemptMillis = 1_000L,
                startFailed = true,
                nowMillis = 1_500L,
            ),
        )
    }

    @Test
    fun pausedMonitoringIsIdleRatherThanAFalseActiveState() {
        assertEquals(
            MonitorHealth.IDLE,
            MonitorHealthPolicy.assess(
                shouldRun = false,
                lastBeatMillis = 0L,
                startAttemptMillis = 0L,
                startFailed = true,
                nowMillis = 5_000L,
            ),
        )
    }
}
