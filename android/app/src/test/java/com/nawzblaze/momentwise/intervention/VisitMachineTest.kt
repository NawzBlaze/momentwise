package com.nawzblaze.momentwise.intervention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scripted Usage Access stream. This is the service tick, without Android:
 * reducer, then [VisitMachine], then the same session clock the service writes.
 * A reducer-only assertion cannot see a show that the next event dismisses.
 */
class VisitMachineTest {

    private val self = "com.nawzblaze.momentwise"
    private val instagram = "com.instagram.android"
    private val chrome = "com.android.chrome"
    private val launcher = "com.google.android.apps.nexuslauncher"
    private val ignored = setOf(launcher, "com.android.systemui", "android")
    private val today = "2026-10-20"

    @Test
    fun overlayStopAndLauncherDoNotDismissTheOpening() {
        val visit = Script()
        visit.open(instagram)
        assertEquals(1, visit.shows)
        assertEquals(1L, visit.snapshot.visitId)
        assertTrue(visit.pauseVisible)

        visit.events(
            ForegroundEvent(instagram, visit.next(), kind = ForegroundEvent.Kind.STOPPED),
            ForegroundEvent(launcher, visit.next()),
        )
        assertEquals(1, visit.shows)
        assertEquals(0, visit.dismisses)
        assertEquals(GuardSnapshot.Phase.AWAITING_OPENING, visit.snapshot.phase)
        assertEquals(instagram, visit.snapshot.activePackage)
    }

    @Test
    fun aDifferentAppWhileTheCardIsUpDoesNotReplaceOrDismissIt() {
        val visit = Script()
        visit.open(instagram)
        visit.events(ForegroundEvent(chrome, visit.next()))
        assertEquals(1, visit.shows)
        assertEquals(0, visit.dismisses)
        assertEquals(instagram, visit.snapshot.activePackage)
    }

    @Test
    fun continueThenALateOverlayStopDoesNotOpenAgain() {
        val visit = Script()
        visit.open(instagram)
        val duringCard = visit.next()
        visit.chooseContinue()
        // Delivered on the next poll, but timestamped while the card was up.
        visit.events(
            ForegroundEvent(instagram, duringCard, kind = ForegroundEvent.Kind.STOPPED),
            ForegroundEvent(launcher, duringCard + 20),
        )
        assertEquals(1, visit.shows)
        assertEquals(GuardSnapshot.Phase.CONTINUING, visit.snapshot.phase)
        visit.events(ForegroundEvent(instagram, visit.next()))
        assertEquals(1, visit.shows)
        assertEquals(instagram, visit.snapshot.activePackage)
    }

    @Test
    fun continueThenHomeWithinGraceIsTheSameVisit() {
        val visit = Script()
        visit.open(instagram)
        visit.chooseContinue()
        visit.events(ForegroundEvent(instagram, visit.next()))
        visit.events(ForegroundEvent(launcher, visit.next()))
        assertEquals(GuardSnapshot.Phase.CONTINUING, visit.snapshot.phase)
        assertEquals(instagram, visit.snapshot.activePackage)
        assertTrue(visit.snapshot.backgroundedAtElapsed != null)
        visit.open(instagram)
        assertEquals(1, visit.shows)
        assertEquals(1L, visit.snapshot.visitId)
        assertEquals(null, visit.snapshot.backgroundedAtElapsed)
    }

    @Test
    fun explicitLeaveThenReopenShowsOnce() {
        val visit = Script()
        visit.open(instagram)
        visit.chooseContinue()
        visit.chooseLeave()
        visit.events(ForegroundEvent(launcher, visit.next()))
        assertEquals(GuardSnapshot.Phase.NONE, visit.snapshot.phase)
        visit.open(instagram)
        assertEquals(2, visit.shows)
        assertEquals(2L, visit.snapshot.visitId)
        visit.events(
            ForegroundEvent(instagram, visit.next(), kind = ForegroundEvent.Kind.STOPPED),
            ForegroundEvent(launcher, visit.next()),
        )
        assertEquals(2, visit.shows)
        assertTrue(visit.pauseVisible)
    }

    @Test
    fun noChoiceThenHomeWithinGraceDoesNotShowAgain() {
        val visit = Script()
        visit.open(instagram)
        visit.closeWithoutChoice()
        visit.events(ForegroundEvent(instagram, visit.next()))
        assertEquals(1, visit.shows)
        assertTrue(visit.snapshot.clearedWithoutChoice)
        visit.events(ForegroundEvent(launcher, visit.next()))
        assertEquals(GuardSnapshot.Phase.AWAITING_OPENING, visit.snapshot.phase)
        visit.open(instagram)
        assertEquals(1, visit.shows)
        assertTrue(visit.snapshot.clearedWithoutChoice)
        // The return cleared grace. Leave again, then stay away for the full minute.
        visit.events(ForegroundEvent(launcher, visit.next()))
        visit.advance(VisitLifecycle.GRACE_MILLIS)
        visit.open(instagram)
        assertEquals(2, visit.shows)
        assertFalse(visit.snapshot.clearedWithoutChoice)
    }

    @Test
    fun aStaleNoChoiceDoesNotClearTheNextVisit() {
        val visit = Script()
        visit.open(instagram)
        val first = visit.snapshot.visitId
        visit.chooseContinue()
        visit.chooseLeave()
        visit.events(ForegroundEvent(launcher, visit.next()))
        visit.open(instagram)
        val stamped = SessionClock.onClearedWithoutChoice(visit.snapshot, nowMillis = 9_000, visitId = first)
        assertEquals(visit.snapshot, stamped)
        assertFalse(stamped.clearedWithoutChoice)
        assertEquals(GuardSnapshot.Phase.AWAITING_OPENING, stamped.phase)
    }

    @Test
    fun aVisibleCardKeepsItsLatchOnAnEmptySession() {
        val empty = GuardSnapshot()
        assertFalse(VisitMachine.shouldDropLatch(empty, pauseVisible = true))
        assertTrue(VisitMachine.shouldDropLatch(empty, pauseVisible = false))
        assertFalse(
            VisitMachine.shouldDropLatch(
                empty.copy(visitId = 2, phase = GuardSnapshot.Phase.AWAITING_OPENING, activePackage = instagram),
                pauseVisible = false,
            ),
        )
    }

    @Test
    fun anUnpersistedOpeningIsReshownOnceThenHeld() {
        assertEquals(
            VisitMachine.Removal.RESHOW,
            VisitMachine.unpersistedRemoval(
                phase = GuardSnapshot.Phase.NONE,
                action = VisitMachine.Removal.IGNORE,
                shownPackage = instagram,
                systemRemovals = 0,
            ),
        )
        assertEquals(
            VisitMachine.Removal.IGNORE,
            VisitMachine.unpersistedRemoval(
                phase = GuardSnapshot.Phase.NONE,
                action = VisitMachine.Removal.IGNORE,
                shownPackage = instagram,
                systemRemovals = 1,
            ),
        )
        assertEquals(
            VisitMachine.Removal.IGNORE,
            VisitMachine.unpersistedRemoval(
                phase = GuardSnapshot.Phase.CONTINUING,
                action = VisitMachine.Removal.IGNORE,
                shownPackage = instagram,
                systemRemovals = 0,
            ),
        )
    }

    @Test
    fun systemRemovalReshowsOnceThenStops() {
        val visit = Script()
        visit.open(instagram)
        val first = VisitMachine.onSystemRemoved(visit.snapshot, visit.latch, visit.snapshot.visitId, visit.now())
        assertEquals(VisitMachine.Removal.RESHOW, first.action)
        val second = VisitMachine.onSystemRemoved(visit.snapshot, first.latch, visit.snapshot.visitId, visit.now())
        assertEquals(VisitMachine.Removal.RECORD_NO_CHOICE, second.action)
        val recorded = SessionClock.onClearedWithoutChoice(
            visit.snapshot,
            nowMillis = 4_000,
            visitId = second.visitId,
        )
        assertTrue(recorded.clearedWithoutChoice)
        val newer = visit.snapshot.copy(visitId = visit.snapshot.visitId + 1, phase = GuardSnapshot.Phase.AWAITING_OPENING)
        val stale = VisitMachine.onSystemRemoved(newer, first.latch, removedVisitId = visit.snapshot.visitId, nowMillis = 5_000)
        assertEquals(VisitMachine.Removal.IGNORE, stale.action)
    }

    @Test
    fun oneSecondOfUseThenTenMinutesAwayDoesNotRemind() {
        val visit = Script()
        visit.open(instagram)
        visit.chooseContinue()
        visit.events(ForegroundEvent(instagram, visit.next()))
        var elapsed = 10_000L
        val (used, early) = SessionClock.onTick(
            snapshot = visit.snapshot,
            foregroundPackage = instagram,
            nowMillis = 20_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = elapsed,
            pauseVisible = false,
            intervalMinutes = 5,
            maxCreditMillis = 1_000,
        )
        assertFalse(early is GuardCommand.ShowReminder)
        elapsed += 1_000
        val (second, stillEarly) = SessionClock.onTick(
            snapshot = used,
            foregroundPackage = instagram,
            nowMillis = 21_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = elapsed,
            maxCreditMillis = 1_000,
        )
        assertFalse(stillEarly is GuardCommand.ShowReminder)
        val away = SessionClock.onTick(
            snapshot = second,
            foregroundPackage = launcher,
            nowMillis = 21_000 + 10 * 60_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = elapsed + 10 * 60_000,
            maxCreditMillis = 1_000,
        )
        assertFalse(away.second is GuardCommand.ShowReminder)
        assertTrue((away.first.useByPackage[instagram]?.accumulatedMillis ?: 0) < 60_000)
    }

    @Test
    fun reminderWaitsForForegroundAndDoesNotStackOnTheOpening() {
        val due = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.CONTINUING,
            useByPackage = mapOf(
                instagram to AppUse(accumulatedMillis = 300_000, budgetMillis = 300_000, phase = AppUse.Phase.DUE),
            ),
        )
        val elsewhere = SessionClock.onTick(
            snapshot = due,
            foregroundPackage = chrome,
            nowMillis = 50_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 50_000,
        )
        assertFalse(elsewhere.second is GuardCommand.ShowReminder)
        val opening = GuardSnapshot(
            activePackage = instagram,
            phase = GuardSnapshot.Phase.AWAITING_OPENING,
            visitId = 3,
            useByPackage = due.useByPackage,
        )
        val stacked = SessionClock.onTick(
            snapshot = opening,
            foregroundPackage = instagram,
            nowMillis = 51_000,
            today = today,
            remindersEnabled = true,
            elapsedRealtime = 51_000,
            pauseVisible = true,
        )
        assertFalse(stacked.second is GuardCommand.ShowReminder)
    }

    @Test
    fun guardedAppsDoNotShareAVisit() {
        val visit = Script()
        visit.open(instagram)
        visit.chooseContinue()
        visit.events(ForegroundEvent(instagram, visit.next()))
        visit.events(ForegroundEvent(chrome, visit.next()))
        assertEquals(1, visit.shows)
        assertEquals(GuardSnapshot.Phase.CONTINUING, visit.snapshot.phase)
        assertEquals(instagram, visit.snapshot.activePackage)
        assertTrue(visit.snapshot.backgroundedAtElapsed != null)
    }

    private inner class Script {
        var snapshot = GuardSnapshot()
        var latch = VisitMachine.Latch()
        var detector = DetectorState()
        var pauseVisible = false
        var shows = 0
        var dismisses = 0
        private var clock = 1_000L

        fun next(): Long = clock.also { clock += 100 }

        fun now(): Long = clock

        fun advance(millis: Long) {
            clock += millis
        }

        fun open(packageName: String) = events(ForegroundEvent(packageName, next()))

        fun chooseLeave() {
            snapshot = SessionClock.onQuit(snapshot)
            latch = VisitMachine.onChoiceDismissed(latch, instagram, clock)
            clock += 50
            pauseVisible = false
            detector = UsageEventReducer.clearPendingStop(detector)
        }

        fun events(vararg fresh: ForegroundEvent) {
            val reduced = UsageEventReducer.reduce(
                state = detector,
                events = fresh.toList(),
                selfPackage = "com.nawzblaze.momentwise",
                ignoredPackages = setOf(launcher, "com.android.systemui", "android"),
                ignoreSelf = pauseVisible,
                suspendStops = pauseVisible,
            )
            detector = reduced.state
            val step = VisitMachine.onForeground(
                snapshot = snapshot,
                latch = latch,
                signal = reduced.signal,
                signaledPackage = reduced.packageName,
                signalTimeMillis = reduced.signalTimeMillis,
                resumedPackages = fresh.filter { it.kind == ForegroundEvent.Kind.RESUMED }.map { it.packageName }.toSet(),
                pauseVisible = pauseVisible,
                selfPackage = "com.nawzblaze.momentwise",
                today = "2026-10-20",
                signaledHasRule = reduced.packageName == instagram,
                elapsedRealtime = clock,
            )
            snapshot = step.snapshot
            latch = step.latch
            when (step.ui) {
                VisitMachine.Ui.SHOW_OPENING -> {
                    shows += 1
                    pauseVisible = true
                }
                VisitMachine.Ui.DISMISS -> {
                    dismisses += 1
                    pauseVisible = false
                }
                VisitMachine.Ui.NONE -> Unit
            }
        }

        fun chooseContinue() {
            snapshot = SessionClock.onContinue(snapshot, 5, clock, "2026-10-20", instagram)
            latch = VisitMachine.onChoiceDismissed(latch, instagram, clock)
            clock += 50
            pauseVisible = false
            detector = UsageEventReducer.clearPendingStop(detector)
        }

        fun closeWithoutChoice() {
            val removal = VisitMachine.onSystemRemoved(snapshot, latch, snapshot.visitId, clock)
            latch = removal.latch
            if (removal.action == VisitMachine.Removal.RECORD_NO_CHOICE || latch.systemRemovals > 1) {
                snapshot = SessionClock.onClearedWithoutChoice(snapshot, clock, removal.visitId)
            } else {
                val second = VisitMachine.onSystemRemoved(snapshot, latch, snapshot.visitId, clock + 10)
                latch = second.latch
                snapshot = SessionClock.onClearedWithoutChoice(snapshot, clock + 10, second.visitId)
            }
            pauseVisible = false
            clock += 100
        }
    }
}
