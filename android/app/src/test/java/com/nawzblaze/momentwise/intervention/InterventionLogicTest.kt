package com.nawzblaze.momentwise.intervention

import com.nawzblaze.momentwise.data.InstalledCatalog
import com.nawzblaze.momentwise.data.LaunchableApp
import com.nawzblaze.momentwise.data.RuleDefaults
import com.nawzblaze.momentwise.data.SampleCatalogMigration
import com.nawzblaze.momentwise.data.local.GuardSnapshotJson
import com.nawzblaze.momentwise.data.model.ReminderFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageEventReducerTest {

    private val self = "com.nawzblaze.momentwise"
    private val launcher = "com.google.android.apps.nexuslauncher"
    private val ignored = setOf(launcher, "com.android.systemui")

    @Test
    fun emptyWindowDoesNotResetTheLastPackage() {
        val state = DetectorState(lastPackage = "com.instagram.android", lastEventTimeMillis = 50)
        val result = UsageEventReducer.reduce(state, emptyList(), self, ignored)

        assertEquals(DetectorSignal.NONE, result.signal)
        assertEquals(state, result.state)
    }

    @Test
    fun selfPackageDoesNotCountAsANewOpening() {
        val state = DetectorState(lastPackage = "com.instagram.android", lastEventTimeMillis = 10)
        val result = UsageEventReducer.reduce(
            state,
            listOf(ForegroundEvent(self, 20)),
            self,
            ignored,
        )

        assertEquals(DetectorSignal.NONE, result.signal)
        assertEquals("com.instagram.android", result.state.lastPackage)
        assertEquals(20, result.state.lastEventTimeMillis)
    }

    @Test
    fun duplicateResumeIsNotANewOpening() {
        val state = DetectorState(lastPackage = "com.instagram.android", lastEventTimeMillis = 10)
        val result = UsageEventReducer.reduce(
            state,
            listOf(
                ForegroundEvent(self, 15),
                ForegroundEvent("com.instagram.android", 30),
            ),
            self,
            ignored,
        )

        assertEquals(DetectorSignal.NONE, result.signal)
        assertEquals("com.instagram.android", result.state.lastPackage)
    }

    @Test
    fun openingMomentwiseItselfIsALeaveSoTheGuardedAppCanOpenAgain() {
        val state = DetectorState(lastPackage = "com.instagram.android", lastEventTimeMillis = 10)
        val left = UsageEventReducer.reduce(
            state,
            listOf(ForegroundEvent(self, 20)),
            self,
            ignored,
            ignoreSelf = false,
        )
        assertEquals(DetectorSignal.LEFT, left.signal)
        assertEquals(self, left.state.lastPackage)

        val opened = UsageEventReducer.reduce(
            left.state,
            listOf(ForegroundEvent("com.instagram.android", 40)),
            self,
            ignored,
            ignoreSelf = false,
        )
        assertEquals(DetectorSignal.OPENED, opened.signal)
        assertEquals("com.instagram.android", opened.packageName)
    }

    @Test
    fun pauseScreenDoesNotCountAsLeavingEvenAfterTheLatchDrops() {
        val pauseClass = "com.nawzblaze.momentwise.intervention.PauseActivity"
        val state = DetectorState(lastPackage = "com.instagram.android", lastEventTimeMillis = 10)
        val result = UsageEventReducer.reduce(
            state,
            listOf(ForegroundEvent(self, 20, className = pauseClass)),
            self,
            ignored,
            ignoreSelf = false,
            pauseClassName = pauseClass,
        )

        assertEquals(DetectorSignal.NONE, result.signal)
        assertEquals("com.instagram.android", result.state.lastPackage)
        assertEquals(20, result.state.lastEventTimeMillis)
    }

    @Test
    fun appSwitchOpensTheNewPackage() {
        val state = DetectorState(lastPackage = "com.instagram.android", lastEventTimeMillis = 10)
        val result = UsageEventReducer.reduce(
            state,
            listOf(ForegroundEvent("com.zhiliaoapp.musically", 40)),
            self,
            ignored,
        )

        assertEquals(DetectorSignal.OPENED, result.signal)
        assertEquals("com.zhiliaoapp.musically", result.packageName)
        assertEquals("com.zhiliaoapp.musically", result.state.lastPackage)
    }

    @Test
    fun launcherIsLeavingNotAnOpening() {
        val state = DetectorState(lastPackage = "com.instagram.android", lastEventTimeMillis = 10)
        val result = UsageEventReducer.reduce(
            state,
            listOf(ForegroundEvent(launcher, 40)),
            self,
            ignored,
        )

        assertEquals(DetectorSignal.LEFT, result.signal)
        assertEquals(launcher, result.state.lastPackage)
    }

    @Test
    fun alreadyProcessedEventsAreIgnored() {
        val state = DetectorState(lastPackage = "com.instagram.android", lastEventTimeMillis = 100)
        val result = UsageEventReducer.reduce(
            state,
            listOf(ForegroundEvent("com.zhiliaoapp.musically", 100)),
            self,
            ignored,
        )

        assertEquals(DetectorSignal.NONE, result.signal)
        assertEquals(state, result.state)
    }

    @Test
    fun seedDoesNotEmitASignal() {
        val seeded = UsageEventReducer.seed("com.instagram.android", 80)
        assertEquals("com.instagram.android", seeded.lastPackage)
        assertEquals(80, seeded.lastEventTimeMillis)
    }
}

class SessionClockTest {

    private val instagram = "com.instagram.android"
    private val today = "2026-10-19"
    private val tomorrow = "2026-10-20"

    @Test
    fun openingAGuardedAppShowsThePauseOnce() {
        val (first, command) = SessionClock.onOpened(GuardSnapshot(), instagram, ruleEnabled = true, today)
        assertTrue(command is GuardCommand.ShowOpening)
        assertEquals(GuardSnapshot.Phase.AWAITING_OPENING, first.phase)

        val (again, second) = SessionClock.onOpened(first, instagram, ruleEnabled = true, today)
        assertEquals(GuardCommand.None, second)
        assertEquals(first.activePackage, again.activePackage)
    }

    @Test
    fun openingStillRunsAfterDismissForToday() {
        val dismissed = SessionClock.onDismissForToday(
            GuardSnapshot(activePackage = instagram, phase = GuardSnapshot.Phase.CONTINUING),
            instagram,
            today,
        )
        val left = SessionClock.onLeft(dismissed).first
        val (_, command) = SessionClock.onOpened(left, instagram, ruleEnabled = true, today)
        assertTrue(command is GuardCommand.ShowOpening)
        assertEquals(today, left.dismissals[instagram])
    }

    @Test
    fun quitEndsTheSessionWithoutAReminder() {
        val waiting = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.AWAITING_OPENING,
            openingHandledForPackage = instagram,
        )
        val quit = SessionClock.onQuit(waiting)
        assertEquals(GuardSnapshot.Phase.NONE, quit.phase)
        assertNull(quit.nextReminderAtMillis)
        assertNull(quit.activePackage)
    }

    @Test
    fun continueStartsTheIntervalAndDoesNotRelaunch() {
        val opened = SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first
        val continued = SessionClock.onContinue(opened, intervalMinutes = 10, nowMillis = 1_000, today)
        assertEquals(GuardSnapshot.Phase.CONTINUING, continued.phase)
        assertEquals(1_000 + 10 * 60_000L, continued.nextReminderAtMillis)
        assertEquals(instagram, continued.activePackage)
    }

    @Test
    fun continueAfterDismissDoesNotScheduleAReminder() {
        val opened = SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first
        val dismissed = SessionClock.onDismissForToday(opened, instagram, today)
        val continued = SessionClock.onContinue(dismissed, intervalMinutes = 10, nowMillis = 1_000, today)
        assertNull(continued.nextReminderAtMillis)
        assertEquals(today, continued.dismissals[instagram])
    }

    @Test
    fun tickShowsADueReminderOnce() {
        val continuing = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.CONTINUING,
            nextReminderAtMillis = 5_000,
        )
        val (shown, command) = SessionClock.onTick(
            continuing,
            foregroundPackage = instagram,
            nowMillis = 5_000,
            today = today,
            remindersEnabled = true,
        )
        assertTrue(command is GuardCommand.ShowReminder)
        val (again, second) = SessionClock.onTick(
            shown,
            foregroundPackage = instagram,
            nowMillis = 6_000,
            today = today,
            remindersEnabled = true,
        )
        assertEquals(GuardCommand.None, second)
        assertEquals(shown, again)
    }

    @Test
    fun dismissDoesNotDisableTheRuleAndClearsOnTheNextDay() {
        val snapshot = SessionClock.onDismissForToday(
            GuardSnapshot(activePackage = instagram, phase = GuardSnapshot.Phase.CONTINUING),
            instagram,
            today,
        )
        assertEquals(today, snapshot.dismissals[instagram])
        assertNull(snapshot.nextReminderAtMillis)

        val nextDay = SessionClock.clearStaleDismissals(snapshot, tomorrow)
        assertTrue(nextDay.dismissals.isEmpty())
    }

    @Test
    fun snoozeRewritesTheNextReminder() {
        val snapshot = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.CONTINUING,
            nextReminderAtMillis = 1_000,
            reminderPresentedFor = 1_000,
        )
        val snoozed = SessionClock.onSnooze(snapshot, snoozeMinutes = 99, nowMillis = 2_000)
        assertEquals(2_000 + 60 * 60_000L, snoozed.nextReminderAtMillis)
        assertNull(snoozed.reminderPresentedFor)
    }

    @Test
    fun leavingEndsTheSession() {
        val active = GuardSnapshot(activePackage = instagram, phase = GuardSnapshot.Phase.CONTINUING, nextReminderAtMillis = 9)
        val left = SessionClock.onLeft(active).first
        assertEquals(GuardSnapshot.Phase.NONE, left.phase)
        assertNull(left.nextReminderAtMillis)
    }

    @Test
    fun bootDropsAnActiveSessionAndSaysSo() {
        val active = GuardSnapshot(activePackage = instagram, phase = GuardSnapshot.Phase.CONTINUING, nextReminderAtMillis = 9)
        val booted = SessionClock.onBoot(active)
        assertTrue(booted.sessionDroppedOnBoot)
        assertEquals(GuardSnapshot.Phase.NONE, booted.phase)

        val idle = SessionClock.onBoot(GuardSnapshot())
        assertFalse(idle.sessionDroppedOnBoot)
    }

    @Test
    fun restartResumesOnlyIfTheSamePackageIsStillInFront() {
        val active = GuardSnapshot(activePackage = instagram, phase = GuardSnapshot.Phase.CONTINUING, nextReminderAtMillis = 9)
        assertEquals(active, SessionClock.reconcileAfterRestart(active, instagram))
        assertEquals(active, SessionClock.reconcileAfterRestart(active, null))
        assertEquals(GuardSnapshot.Phase.NONE, SessionClock.reconcileAfterRestart(active, "com.android.launcher").phase)
    }

    @Test
    fun processDeathReshowsAnUnansweredOpeningWithoutStartingANewOne() {
        val waiting = SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first
        val (recovered, command) = SessionClock.recoverUnansweredPause(
            waiting,
            foregroundPackage = instagram,
            nowMillis = 5_000,
            today = today,
            remindersEnabled = true,
        )
        assertTrue(command is GuardCommand.ShowOpening)
        assertEquals(waiting, recovered)
        assertEquals(GuardCommand.None, SessionClock.recoverUnansweredPause(
            waiting,
            foregroundPackage = null,
            nowMillis = 5_000,
            today = today,
            remindersEnabled = true,
        ).second)
    }

    @Test
    fun processDeathReshowsADueReminderWithoutResettingTheInterval() {
        val continuing = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.CONTINUING,
            nextReminderAtMillis = 5_000,
            reminderPresentedFor = 5_000,
        )
        val (recovered, command) = SessionClock.recoverUnansweredPause(
            continuing,
            foregroundPackage = instagram,
            nowMillis = 6_000,
            today = today,
            remindersEnabled = true,
        )
        assertTrue(command is GuardCommand.ShowReminder)
        assertEquals(5_000L, recovered.nextReminderAtMillis)
        assertEquals(5_000L, recovered.reminderPresentedFor)
    }

    @Test
    fun reminderDoesNotStartUntilTheUserContinues() {
        val waiting = SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first
        val (_, command) = SessionClock.onTick(
            waiting,
            foregroundPackage = instagram,
            nowMillis = 60_000,
            today = today,
            remindersEnabled = true,
        )
        assertEquals(GuardCommand.None, command)
        assertNull(waiting.nextReminderAtMillis)
    }

    @Test
    fun neverFrequencyDoesNotScheduleAReminder() {
        val opened = SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first
        val continued = SessionClock.onContinue(opened, intervalMinutes = null, nowMillis = 1_000, today)
        assertNull(continued.nextReminderAtMillis)
        val (_, command) = SessionClock.onTick(
            continued,
            foregroundPackage = instagram,
            nowMillis = 60_000,
            today = today,
            remindersEnabled = false,
        )
        assertEquals(GuardCommand.None, command)
    }

    @Test
    fun dismissingOneAppDoesNotDismissAnother() {
        val tiktok = "com.zhiliaoapp.musically"
        val instagramDismissed = SessionClock.onDismissForToday(
            GuardSnapshot(activePackage = instagram, phase = GuardSnapshot.Phase.CONTINUING),
            instagram,
            today,
        )
        val both = SessionClock.onDismissForToday(
            instagramDismissed.copy(activePackage = tiktok, phase = GuardSnapshot.Phase.CONTINUING),
            tiktok,
            today,
        )
        assertEquals(today, both.dismissals[instagram])
        assertEquals(today, both.dismissals[tiktok])
        assertEquals(2, both.dismissals.size)
    }

    @Test
    fun unguardedAppDoesNotShowAPause() {
        val (_, command) = SessionClock.onOpened(GuardSnapshot(), "com.chrome", ruleEnabled = false, today)
        assertEquals(GuardCommand.None, command)
    }
}

class CatalogMigrationTest {

    @Test
    fun sampleIdsAreDroppedAndRealPackagesAreKept() {
        val saved = RuleDefaults.sampleCatalog() + RuleDefaults.ruleFor(
            "com.instagram.android",
            "Instagram",
            "",
            enabled = true,
            reminderFrequency = ReminderFrequency.EveryMinutes(10),
        )
        val kept = SampleCatalogMigration.dropSampleIds(saved)
        assertEquals(listOf("com.instagram.android"), kept.map { it.appId })
        assertTrue(kept.single().enabled)
    }

    @Test
    fun mergeUsesInstalledLabelsAndKeepsAnUninstalledRule() {
        val saved = listOf(
            RuleDefaults.ruleFor("com.instagram.android", "Old name", "", enabled = true),
            RuleDefaults.ruleFor("com.removed.app", "Removed", "", enabled = true),
        ) + RuleDefaults.sampleCatalog()
        val merged = InstalledCatalog.merge(
            installed = listOf(LaunchableApp("com.instagram.android", "Instagram")),
            saved = saved,
        )
        assertEquals(2, merged.size)
        assertEquals("Instagram", merged[0].rule.appName)
        assertTrue(merged[0].installed)
        assertTrue(merged[0].rule.enabled)
        assertFalse(merged[1].installed)
        assertEquals("com.removed.app", merged[1].rule.appId)
        assertTrue(merged.none { SampleCatalogMigration.isFoundationSample(it.rule) })
    }

    @Test
    fun disclosureIsRequiredUntilAcceptedAndWheneverMonitoringCannotStart() {
        assertTrue(shouldExplainBeforeGuarding(disclosureAccepted = false, canMonitor = true))
        assertFalse(shouldExplainBeforeGuarding(disclosureAccepted = true, canMonitor = true))
        assertTrue(shouldExplainBeforeGuarding(disclosureAccepted = true, canMonitor = false))
    }
}

class GuardSnapshotJsonTest {

    @Test
    fun roundTripKeepsTheSessionAndDismissals() {
        val original = GuardSnapshot(
            activePackage = "com.instagram.android",
            phase = GuardSnapshot.Phase.CONTINUING,
            nextReminderAtMillis = 1_700_000_000_000,
            reminderPresentedFor = null,
            openingHandledForPackage = "com.instagram.android",
            dismissals = mapOf("com.zhiliaoapp.musically" to "2026-10-19"),
            sessionDroppedOnBoot = true,
        )
        assertEquals(original, GuardSnapshotJson.decode(GuardSnapshotJson.encode(original)))
    }

    @Test
    fun corruptStorageDoesNotThrow() {
        assertEquals(GuardSnapshot(), GuardSnapshotJson.decode(null))
        assertEquals(GuardSnapshot(), GuardSnapshotJson.decode("not-json"))
    }
}
