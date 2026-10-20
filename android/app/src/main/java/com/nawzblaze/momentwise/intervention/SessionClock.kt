package com.nawzblaze.momentwise.intervention

/**
 * Reminder-session decisions, free of Android types so process-death and
 * day-boundary behaviour can be tested without a device.
 *
 * A visit starts when a guarded app becomes the foreground and ends when a
 * leave is observed. Closing the pause without Leave, Continue, snooze, or
 * dismiss-for-today is not a choice. It does not start a reminder, does not
 * suppress the next visit, and must not be stored as a conscious dismiss.
 *
 * Wall-clock instants are stored, not [android.os.SystemClock.elapsedRealtime],
 * because elapsed time resets on reboot. A reboot drops the in-progress session:
 * after boot, the next observed opening starts a new one. Reminder delivery is
 * the next poll after the interval, not an exact alarm.
 */
data class GuardSnapshot(
    val activePackage: String? = null,
    val phase: Phase = Phase.NONE,
    val nextReminderAtMillis: Long? = null,
    /** The [nextReminderAtMillis] already shown, so a due reminder is not repeated every tick. */
    val reminderPresentedFor: Long? = null,
    val openingHandledForPackage: String? = null,
    /** Package to local date (`yyyy-MM-dd`) for dismiss-for-today. Does not disable the rule. */
    val dismissals: Map<String, String> = emptyMap(),
    val sessionDroppedOnBoot: Boolean = false,
    /**
     * Set when Leave sends the user home. While this package is still the last
     * observed foreground, do not open another pause. Cleared once a leave, or
     * a different app, is actually observed. This stops Leave from immediately
     * reopening over the same resume.
     */
    val quitSuppressedPackage: String? = null,
    /**
     * The pause UI went away without a choice. Do not immediately put the same
     * visit back. A later leave, or a new opening signal, is a new visit.
     */
    val clearedWithoutChoice: Boolean = false,
    /**
     * After a reminder UI is cleared without a choice, do not show that same
     * reminder again before this wall-clock instant. The poll is not an exact alarm.
     */
    val reminderHoldUntilMillis: Long? = null,
) {
    enum class Phase { NONE, AWAITING_OPENING, CONTINUING }

    fun dismissedOn(packageName: String, today: String): Boolean =
        dismissals[packageName] == today
}

sealed interface GuardCommand {
    data object None : GuardCommand
    data class ShowOpening(val packageName: String) : GuardCommand
    data class ShowReminder(val packageName: String) : GuardCommand
}

object SessionClock {

    /** Not an exact alarm. The next poll after this hold may show the reminder again. */
    const val REMINDER_REDISPLAY_HOLD_MILLIS = 60_000L

    fun onOpened(
        snapshot: GuardSnapshot,
        packageName: String,
        ruleEnabled: Boolean,
        today: String,
    ): Pair<GuardSnapshot, GuardCommand> {
        val cleared = clearStaleDismissals(snapshot, today)
        if (cleared.quitSuppressedPackage == packageName) {
            return cleared to GuardCommand.None
        }
        val ready = if (cleared.quitSuppressedPackage != null) {
            cleared.copy(quitSuppressedPackage = null)
        } else {
            cleared
        }
        if (!ruleEnabled) {
            return endSession(ready).copy(quitSuppressedPackage = null) to GuardCommand.None
        }
        if (ready.dismissedOn(packageName, today)) {
            return ready.copy(
                activePackage = packageName,
                phase = GuardSnapshot.Phase.CONTINUING,
                nextReminderAtMillis = null,
                reminderPresentedFor = null,
                openingHandledForPackage = packageName,
                clearedWithoutChoice = false,
                quitSuppressedPackage = null,
            ) to GuardCommand.None
        }
        // Same visit. A no-choice close is not a leave, so this resume is not
        // a new opening. A later leave clears the flag, and the next resume can show.
        if (ready.clearedWithoutChoice && ready.activePackage == packageName) {
            return ready to GuardCommand.None
        }
        if (ready.activePackage == packageName &&
            ready.phase != GuardSnapshot.Phase.NONE &&
            ready.openingHandledForPackage == packageName
        ) {
            return ready to GuardCommand.None
        }
        val next = endSession(ready).copy(
            activePackage = packageName,
            phase = GuardSnapshot.Phase.AWAITING_OPENING,
            openingHandledForPackage = packageName,
        )
        return next to GuardCommand.ShowOpening(packageName)
    }

    /**
     * A guarded package is in front, and this session has not already shown
     * its opening. Used when a resume was missed because the service was down
     * or the usage event arrived late. It is not a second opening, and it does
     * not put back a pause the user just closed without choosing.
     */
    fun needsOpening(snapshot: GuardSnapshot, foregroundPackage: String?, ruleEnabled: Boolean): Boolean {
        if (foregroundPackage == null || !ruleEnabled) return false
        if (snapshot.quitSuppressedPackage == foregroundPackage) return false
        // Stale days are removed before this is called. A remaining entry is today.
        if (snapshot.dismissals[foregroundPackage] != null) return false
        if (snapshot.clearedWithoutChoice && snapshot.activePackage == foregroundPackage) return false
        if (snapshot.activePackage == foregroundPackage && snapshot.phase != GuardSnapshot.Phase.NONE) {
            return false
        }
        return true
    }

    fun onLeft(snapshot: GuardSnapshot): Pair<GuardSnapshot, GuardCommand> =
        endSession(snapshot).copy(quitSuppressedPackage = null) to GuardCommand.None

    fun onTick(
        snapshot: GuardSnapshot,
        foregroundPackage: String?,
        nowMillis: Long,
        today: String,
        remindersEnabled: Boolean,
    ): Pair<GuardSnapshot, GuardCommand> {
        val cleared = clearStaleDismissals(snapshot, today)
        val pkg = cleared.activePackage
        if (cleared.phase != GuardSnapshot.Phase.CONTINUING || pkg == null || pkg != foregroundPackage) {
            return cleared to GuardCommand.None
        }
        if (!remindersEnabled || cleared.dismissedOn(pkg, today)) {
            return cleared to GuardCommand.None
        }
        val holdUntil = cleared.reminderHoldUntilMillis
        if (holdUntil != null && nowMillis < holdUntil) {
            return cleared to GuardCommand.None
        }
        val dueAt = cleared.nextReminderAtMillis
        if (dueAt == null || nowMillis < dueAt || cleared.reminderPresentedFor == dueAt) {
            return cleared to GuardCommand.None
        }
        return cleared.copy(
            reminderPresentedFor = dueAt,
            reminderHoldUntilMillis = null,
        ) to GuardCommand.ShowReminder(pkg)
    }

    /**
     * Home-intent Leave. Does not disable the rule, does not force-stop, and
     * does not start a reminder. The same package is not paused again until
     * Usage Access has seen the user leave, or open something else.
     */
    fun onQuit(snapshot: GuardSnapshot): GuardSnapshot =
        endSession(snapshot).copy(quitSuppressedPackage = snapshot.activePackage)

    /**
     * Continue starts the reminder from this moment when the interval is enabled.
     * [packageName] is the pause the user answered. A lost session write must
     * not drop the interval by looking up a null active package.
     */
    fun onContinue(
        snapshot: GuardSnapshot,
        intervalMinutes: Int?,
        nowMillis: Long,
        today: String,
        packageName: String? = snapshot.activePackage,
    ): GuardSnapshot {
        val pkg = packageName ?: snapshot.activePackage
        val dismissed = pkg != null && snapshot.dismissedOn(pkg, today)
        val next = if (intervalMinutes != null && intervalMinutes > 0 && !dismissed) {
            nowMillis + intervalMinutes * 60_000L
        } else {
            null
        }
        return snapshot.copy(
            activePackage = pkg,
            phase = GuardSnapshot.Phase.CONTINUING,
            nextReminderAtMillis = next,
            reminderPresentedFor = null,
            quitSuppressedPackage = null,
            clearedWithoutChoice = false,
            reminderHoldUntilMillis = null,
        )
    }

    fun onSnooze(snapshot: GuardSnapshot, snoozeMinutes: Int, nowMillis: Long): GuardSnapshot {
        val minutes = snoozeMinutes.coerceIn(1, 60)
        val next = nowMillis + minutes * 60_000L
        return snapshot.copy(
            phase = GuardSnapshot.Phase.CONTINUING,
            nextReminderAtMillis = next,
            reminderPresentedFor = null,
            clearedWithoutChoice = false,
            reminderHoldUntilMillis = null,
        )
    }

    /**
     * Continues into the app and suppresses openings and reminders for this
     * app until the next local day. The rule stays enabled. Not Continue:
     * no reminder is scheduled. Not Leave: the other app is not sent home.
     */
    fun onDismissForToday(snapshot: GuardSnapshot, packageName: String, today: String): GuardSnapshot {
        val pkg = packageName.ifBlank { snapshot.activePackage } ?: return snapshot
        return snapshot.copy(
            activePackage = pkg,
            phase = GuardSnapshot.Phase.CONTINUING,
            nextReminderAtMillis = null,
            reminderPresentedFor = null,
            openingHandledForPackage = pkg,
            dismissals = snapshot.dismissals + (pkg to today),
            quitSuppressedPackage = null,
            clearedWithoutChoice = false,
            reminderHoldUntilMillis = null,
        )
    }

    /**
     * The pause UI closed and the user did not choose. Not Continue, not Leave,
     * and not dismiss-for-today. The visit stays open so the same pause is not
     * stacked again immediately. A later leave ends it.
     */
    fun onClearedWithoutChoice(snapshot: GuardSnapshot, nowMillis: Long): GuardSnapshot {
        if (snapshot.phase == GuardSnapshot.Phase.NONE) return snapshot
        val holdingReminder = snapshot.phase == GuardSnapshot.Phase.CONTINUING &&
            snapshot.reminderPresentedFor != null
        return snapshot.copy(
            clearedWithoutChoice = true,
            quitSuppressedPackage = null,
            reminderPresentedFor = if (holdingReminder) null else snapshot.reminderPresentedFor,
            reminderHoldUntilMillis = if (holdingReminder) {
                nowMillis + REMINDER_REDISPLAY_HOLD_MILLIS
            } else {
                snapshot.reminderHoldUntilMillis
            },
        )
    }

    /**
     * Reboot cannot prove the user was still in the guarded app.
     * Drop the session and keep dismiss-for-today. The caller should say so if
     * [GuardSnapshot.sessionDroppedOnBoot] becomes true.
     */
    fun onBoot(snapshot: GuardSnapshot): GuardSnapshot {
        val hadSession = snapshot.phase != GuardSnapshot.Phase.NONE || snapshot.activePackage != null
        return endSession(snapshot).copy(
            sessionDroppedOnBoot = hadSession || snapshot.sessionDroppedOnBoot,
        )
    }

    fun acknowledgeBootDrop(snapshot: GuardSnapshot): GuardSnapshot =
        snapshot.copy(sessionDroppedOnBoot = false)

    /**
     * After process death, keep the session when Usage Access has not reported
     * a package yet. An empty window is not proof the user left. End it only
     * when a different package is actually observed.
     */
    fun reconcileAfterRestart(snapshot: GuardSnapshot, observedPackage: String?): GuardSnapshot {
        if (snapshot.phase == GuardSnapshot.Phase.NONE) return snapshot
        if (observedPackage == null || observedPackage == snapshot.activePackage) return snapshot
        return endSession(snapshot)
    }

    /**
     * The pause UI is gone after process death. If the user had not chosen yet,
     * or a reminder was already due, show that same pause again. This is not a
     * new opening and does not reset the interval. A no-choice clear is not
     * process death: do not put that pause straight back.
     */
    fun recoverUnansweredPause(
        snapshot: GuardSnapshot,
        foregroundPackage: String?,
        nowMillis: Long,
        today: String,
        remindersEnabled: Boolean,
    ): Pair<GuardSnapshot, GuardCommand> {
        val cleared = clearStaleDismissals(snapshot, today)
        val pkg = cleared.activePackage
        if (pkg == null || foregroundPackage == null || pkg != foregroundPackage) {
            return cleared to GuardCommand.None
        }
        if (cleared.clearedWithoutChoice) return cleared to GuardCommand.None
        if (cleared.phase == GuardSnapshot.Phase.AWAITING_OPENING) {
            return cleared to GuardCommand.ShowOpening(pkg)
        }
        if (cleared.phase != GuardSnapshot.Phase.CONTINUING) return cleared to GuardCommand.None
        if (!remindersEnabled || cleared.dismissedOn(pkg, today)) return cleared to GuardCommand.None
        val holdUntil = cleared.reminderHoldUntilMillis
        if (holdUntil != null && nowMillis < holdUntil) return cleared to GuardCommand.None
        val dueAt = cleared.nextReminderAtMillis ?: return cleared to GuardCommand.None
        if (nowMillis < dueAt) return cleared to GuardCommand.None
        return cleared.copy(reminderPresentedFor = dueAt, reminderHoldUntilMillis = null) to
            GuardCommand.ShowReminder(pkg)
    }

    fun clearStaleDismissals(snapshot: GuardSnapshot, today: String): GuardSnapshot {
        if (snapshot.dismissals.all { it.value == today }) return snapshot
        return snapshot.copy(dismissals = snapshot.dismissals.filterValues { it == today })
    }

    private fun endSession(snapshot: GuardSnapshot): GuardSnapshot = snapshot.copy(
        activePackage = null,
        phase = GuardSnapshot.Phase.NONE,
        nextReminderAtMillis = null,
        reminderPresentedFor = null,
        openingHandledForPackage = null,
        clearedWithoutChoice = false,
        reminderHoldUntilMillis = null,
    )
}

/**
 * Which pause to put on screen. A new opening is a new visit. A no-choice
 * clear only blocks putting the same unanswered visit straight back.
 */
object PauseShowPolicy {
    fun shouldShowNewOpening(pauseVisible: Boolean, alreadyContinuing: Boolean): Boolean =
        !pauseVisible && !alreadyContinuing

    fun shouldRecoverSameVisit(
        pauseVisible: Boolean,
        clearedWithoutChoice: Boolean,
        phase: GuardSnapshot.Phase,
        activePackage: String?,
        foreground: String?,
    ): Boolean {
        if (pauseVisible || clearedWithoutChoice || foreground == null) return false
        return phase == GuardSnapshot.Phase.AWAITING_OPENING && activePackage == foreground
    }
}
