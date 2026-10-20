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
        assertNull(seeded.stoppedPackage)
    }

    @Test
    fun stopThatEndsTheWindowIsALeaveAndTheNextResumeIsANewOpening() {
        val instagram = "com.instagram.android"
        val watching = DetectorState(lastPackage = instagram, lastEventTimeMillis = 10)
        val stopped = UsageEventReducer.reduce(
            watching,
            listOf(ForegroundEvent(instagram, 20, kind = ForegroundEvent.Kind.STOPPED)),
            self,
            ignored,
        )
        assertEquals(DetectorSignal.LEFT, stopped.signal)
        assertEquals(instagram, stopped.packageName)
        assertNull(stopped.state.lastPackage)
        assertNull(stopped.state.stoppedPackage)

        val returned = UsageEventReducer.reduce(
            stopped.state,
            listOf(ForegroundEvent(instagram, 40)),
            self,
            ignored,
        )
        assertEquals(DetectorSignal.OPENED, returned.signal)
        assertEquals(instagram, returned.packageName)
    }

    @Test
    fun stopAndResumeInTheSameWindowIsNotANewVisit() {
        val instagram = "com.instagram.android"
        val watching = DetectorState(lastPackage = instagram, lastEventTimeMillis = 10)
        val result = UsageEventReducer.reduce(
            watching,
            listOf(
                ForegroundEvent(instagram, 20, kind = ForegroundEvent.Kind.STOPPED),
                ForegroundEvent(instagram, 21),
            ),
            self,
            ignored,
        )
        assertEquals(DetectorSignal.NONE, result.signal)
        assertEquals(instagram, result.state.lastPackage)
        assertNull(result.state.stoppedPackage)
    }

    @Test
    fun stopWhileThePauseIsShowingIsHeldUntilThePauseIsGone() {
        val instagram = "com.instagram.android"
        val watching = DetectorState(lastPackage = instagram, lastEventTimeMillis = 10)
        val held = UsageEventReducer.reduce(
            watching,
            listOf(ForegroundEvent(instagram, 20, kind = ForegroundEvent.Kind.STOPPED)),
            self,
            ignored,
            suspendStops = true,
        )
        assertEquals(DetectorSignal.NONE, held.signal)
        assertEquals(instagram, held.state.lastPackage)
        assertEquals(instagram, held.state.stoppedPackage)

        val confirmed = UsageEventReducer.confirmPendingStop(held.state)
        assertEquals(DetectorSignal.LEFT, confirmed.signal)
        assertNull(confirmed.state.lastPackage)
        assertEquals(DetectorSignal.NONE, UsageEventReducer.confirmPendingStop(confirmed.state).signal)

        val handedBack = UsageEventReducer.reduce(
            held.state,
            listOf(ForegroundEvent(instagram, 20 + 500)),
            self,
            ignored,
        )
        assertEquals(DetectorSignal.NONE, handedBack.signal)
        assertEquals(instagram, handedBack.state.lastPackage)

        val later = UsageEventReducer.reduce(
            held.state,
            listOf(ForegroundEvent(instagram, 20 + UsageEventReducer.HELD_STOP_IS_NEW_VISIT_AFTER_MILLIS)),
            self,
            ignored,
        )
        assertEquals(DetectorSignal.OPENED, later.signal)
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
        val (_, sameDay) = SessionClock.onOpened(left, instagram, ruleEnabled = true, today)
        assertEquals(GuardCommand.None, sameDay)
        assertFalse(SessionClock.needsOpening(
            SessionClock.clearStaleDismissals(left, today),
            instagram,
            true,
        ))
        val tomorrowSnapshot = SessionClock.clearStaleDismissals(left, tomorrow)
        val (_, nextDay) = SessionClock.onOpened(tomorrowSnapshot, instagram, ruleEnabled = true, tomorrow)
        assertTrue(nextDay is GuardCommand.ShowOpening)
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
        assertEquals(instagram, quit.quitSuppressedPackage)
        assertFalse(SessionClock.needsOpening(quit, instagram, true))
        val (again, command) = SessionClock.onOpened(quit, instagram, true, today)
        assertEquals(GuardCommand.None, command)
        assertEquals(instagram, again.quitSuppressedPackage)
    }

    @Test
    fun leaveSuppressionClearsAfterARealLeave() {
        val quit = SessionClock.onQuit(
            GuardSnapshot(activePackage = instagram, phase = GuardSnapshot.Phase.AWAITING_OPENING),
        )
        val left = SessionClock.onLeft(quit).first
        assertNull(left.quitSuppressedPackage)
        val (_, command) = SessionClock.onOpened(left, instagram, true, today)
        assertTrue(command is GuardCommand.ShowOpening)
    }

    @Test
    fun continueStartsTheIntervalAndDoesNotRelaunch() {
        val opened = SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first
        val continued = SessionClock.onContinue(opened, intervalMinutes = 10, nowMillis = 1_000, today)
        assertEquals(GuardSnapshot.Phase.CONTINUING, continued.phase)
        assertNull(continued.nextReminderAtMillis)
        assertEquals(10 * 60_000L, continued.useByPackage[instagram]?.budgetMillis)
        assertEquals(0L, continued.useByPackage[instagram]?.accumulatedMillis)
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
        val due = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.CONTINUING,
            useByPackage = mapOf(instagram to AppUse(accumulatedMillis = 5_000, budgetMillis = 5_000, phase = AppUse.Phase.DUE)),
        )
        val (shown, command) = SessionClock.onTick(
            due,
            foregroundPackage = instagram,
            nowMillis = 5_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 5_000,
            intervalMinutes = 5,
        )
        assertTrue(command is GuardCommand.ShowReminder)
        val (again, second) = SessionClock.onTick(
            shown,
            foregroundPackage = instagram,
            nowMillis = 6_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 6_000,
            intervalMinutes = 5,
        )
        assertEquals(GuardCommand.None, second)
        assertEquals(shown.useByPackage[instagram]?.reminderShown, again.useByPackage[instagram]?.reminderShown)
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
        assertNull(snoozed.nextReminderAtMillis)
        assertEquals(60 * 60_000L, snoozed.useByPackage[instagram]?.budgetMillis)
        assertEquals(AppUse.Phase.SNOOZE, snoozed.useByPackage[instagram]?.phase)
        assertEquals(0L, snoozed.useByPackage[instagram]?.accumulatedMillis)
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
            useByPackage = mapOf(
                instagram to AppUse(accumulatedMillis = 5_000, budgetMillis = 5_000, phase = AppUse.Phase.DUE),
            ),
        )
        val (recovered, command) = SessionClock.recoverUnansweredPause(
            continuing,
            foregroundPackage = instagram,
            nowMillis = 6_000,
            today = today,
            remindersEnabled = true,
        )
        assertTrue(command is GuardCommand.ShowReminder)
        assertEquals(5_000L, recovered.useByPackage[instagram]?.accumulatedMillis)
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

    @Test
    fun dismissWithoutChoiceIsNotContinueAndDoesNotSuppressTheNextOpening() {
        val opened = SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first
        val cleared = SessionClock.onClearedWithoutChoice(opened, nowMillis = 2_000)
        assertEquals(GuardSnapshot.Phase.AWAITING_OPENING, cleared.phase)
        assertNull(cleared.nextReminderAtMillis)
        assertNull(cleared.quitSuppressedPackage)
        assertTrue(cleared.clearedWithoutChoice)
        assertFalse(SessionClock.needsOpening(cleared, instagram, true))

        val left = SessionClock.onLeft(cleared).first
        assertEquals(GuardSnapshot.Phase.NONE, left.phase)
        assertFalse(left.clearedWithoutChoice)
        val (_, again) = SessionClock.onOpened(left, instagram, true, today)
        assertTrue(again is GuardCommand.ShowOpening)
    }

    @Test
    fun aNewOpeningSignalAfterANoChoiceClearIsNotBlocked() {
        val opened = SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first
        val cleared = SessionClock.onClearedWithoutChoice(opened, nowMillis = 2_000)
        val (_, sameVisit) = SessionClock.onOpened(cleared, instagram, true, today)
        assertEquals(GuardCommand.None, sameVisit)
        val left = SessionClock.onLeft(cleared).first
        val (_, afterLeave) = SessionClock.onOpened(left, instagram, true, today)
        assertTrue(afterLeave is GuardCommand.ShowOpening)
        assertTrue(
            PauseShowPolicy.shouldShowNewOpening(pauseVisible = false, alreadyContinuing = false),
        )
        assertFalse(
            PauseShowPolicy.shouldRecoverSameVisit(
                pauseVisible = false,
                clearedWithoutChoice = true,
                phase = GuardSnapshot.Phase.AWAITING_OPENING,
                activePackage = instagram,
                foreground = instagram,
            ),
        )
    }

    @Test
    fun continueSchedulesFromTheAnsweredPackageWhenTheSessionWriteWasLost() {
        val continued = SessionClock.onContinue(
            snapshot = GuardSnapshot(),
            intervalMinutes = 5,
            nowMillis = 1_000,
            today = today,
            packageName = instagram,
        )
        assertEquals(instagram, continued.activePackage)
        assertEquals(GuardSnapshot.Phase.CONTINUING, continued.phase)
        assertEquals(5 * 60_000L, continued.useByPackage[instagram]?.budgetMillis)
        val (shown, command) = accrueUntilDue(continued, startElapsed = 1_000)
        assertTrue(command is GuardCommand.ShowReminder)
        assertEquals(instagram, shown.activePackage)
    }

    @Test
    fun reminderClearedWithoutChoiceDoesNotRestackUntilALaterVisit() {
        val shown = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.CONTINUING,
            useByPackage = mapOf(
                instagram to AppUse(
                    accumulatedMillis = 5_000,
                    budgetMillis = 5_000,
                    phase = AppUse.Phase.DUE,
                    reminderShown = true,
                ),
            ),
        )
        val cleared = SessionClock.onClearedWithoutChoice(shown, nowMillis = 6_000)
        assertEquals(GuardSnapshot.Phase.CONTINUING, cleared.phase)
        assertTrue(cleared.useByPackage[instagram]?.reminderShown == true)
        val (_, sameVisit) = SessionClock.onTick(
            cleared,
            foregroundPackage = instagram,
            nowMillis = 7_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 7_000,
            intervalMinutes = 5,
        )
        assertEquals(GuardCommand.None, sameVisit)
        val left = SessionClock.onLeft(cleared).first
        val resumed = SessionClock.onContinue(left, intervalMinutes = 5, nowMillis = 8_000, today, instagram)
        val (_, later) = SessionClock.onTick(
            resumed,
            foregroundPackage = instagram,
            nowMillis = 9_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 9_000,
            intervalMinutes = 5,
        )
        assertTrue(later is GuardCommand.ShowReminder)
    }

    @Test
    fun anotherAppDoesNotInheritTheReminder() {
        val continued = SessionClock.onContinue(
            SessionClock.onOpened(GuardSnapshot(), instagram, true, today).first,
            intervalMinutes = 5,
            nowMillis = 1_000,
            today = today,
        )
        val tiktok = "com.zhiliaoapp.musically"
        val (next, command) = SessionClock.onOpened(continued, tiktok, true, today)
        assertTrue(command is GuardCommand.ShowOpening)
        assertEquals(tiktok, next.activePackage)
        assertNull(next.nextReminderAtMillis)
        assertEquals(GuardSnapshot.Phase.AWAITING_OPENING, next.phase)
        assertEquals(5 * 60_000L, next.useByPackage[instagram]?.budgetMillis)
        assertNull(next.useByPackage[tiktok])
    }

    private fun accrueUntilDue(start: GuardSnapshot, startElapsed: Long): Pair<GuardSnapshot, GuardCommand> {
        var snapshot = start
        var elapsed = startElapsed
        var command: GuardCommand = GuardCommand.None
        repeat(400) {
            elapsed += 1_000
            val (next, step) = SessionClock.onTick(
                snapshot,
                foregroundPackage = instagram,
                nowMillis = elapsed,
                today = today,
                remindersEnabled = true,
                elapsedRealtime = elapsed,
                intervalMinutes = 5,
                maxCreditMillis = 1_000,
            )
            snapshot = next
            command = step
            if (step is GuardCommand.ShowReminder) return next to step
        }
        return snapshot to command
    }

    @Test
    fun stoppingMonitoringEndsTheVisitSoAStaleReminderCannotFire() {
        val continuing = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.CONTINUING,
            nextReminderAtMillis = 5_000,
        )
        val stopped = SessionClock.onLeft(continuing).first
        assertEquals(GuardSnapshot.Phase.NONE, stopped.phase)
        assertNull(stopped.nextReminderAtMillis)
        val (_, command) = SessionClock.onTick(
            stopped,
            foregroundPackage = instagram,
            nowMillis = 9_000,
            today = today,
            remindersEnabled = true,
        )
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
            clearedWithoutChoice = true,
            reminderHoldUntilMillis = 1_700_000_060_000,
            visitId = 4,
            backgroundedAtElapsed = 80_000,
        )
        assertEquals(original, GuardSnapshotJson.decode(GuardSnapshotJson.encode(original)))
    }

    @Test
    fun corruptStorageDoesNotThrow() {
        assertEquals(GuardSnapshot(), GuardSnapshotJson.decode(null))
        assertEquals(GuardSnapshot(), GuardSnapshotJson.decode("not-json"))
    }

    @Test
    fun jsonNullIsNotThePackageNameNull() {
        val saved = """{"phase":"NONE","recentAwayPackage":null,"recentAwayAtElapsed":50000,"lastGuardedPackage":"com.instagram.android","visitId":4}"""
        val decoded = GuardSnapshotJson.decode(saved)
        assertEquals(null, decoded.recentAwayPackage)
        assertEquals("com.instagram.android", decoded.lastGuardedPackage)
        assertFalse(SessionClock.needsOpening(decoded, "com.instagram.android", true, 70_000))
    }

    @Test
    fun aSnapshotSavedBeforeVisitIdsDecodesAsVisitZero() {
        val legacy = """{"activePackage":"com.instagram.android","phase":"CONTINUING","dismissals":{}}"""
        assertEquals(0L, GuardSnapshotJson.decode(legacy).visitId)
    }
}
