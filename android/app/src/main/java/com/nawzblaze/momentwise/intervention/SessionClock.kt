package com.nawzblaze.momentwise.intervention

/**
 * Reminder-session decisions, free of Android types so process-death and
 * day-boundary behaviour can be tested without a device.
 *
 * Wall-clock instants are stored, not [android.os.SystemClock.elapsedRealtime],
 * because elapsed time resets on reboot. A reboot drops the in-progress session:
 * after boot, the next observed opening starts a new one.
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

    fun onOpened(
        snapshot: GuardSnapshot,
        packageName: String,
        ruleEnabled: Boolean,
        today: String,
    ): Pair<GuardSnapshot, GuardCommand> {
        val cleared = clearStaleDismissals(snapshot, today)
        if (!ruleEnabled) {
            return endSession(cleared) to GuardCommand.None
        }
        if (cleared.activePackage == packageName &&
            cleared.phase != GuardSnapshot.Phase.NONE &&
            cleared.openingHandledForPackage == packageName
        ) {
            return cleared to GuardCommand.None
        }
        val next = endSession(cleared).copy(
            activePackage = packageName,
            phase = GuardSnapshot.Phase.AWAITING_OPENING,
            openingHandledForPackage = packageName,
        )
        return next to GuardCommand.ShowOpening(packageName)
    }

    /**
     * A guarded package is in front, and this session has not already shown
     * its opening. Used when a resume was missed because the service was down
     * or the usage event arrived late. It is not a second opening.
     */
    fun needsOpening(snapshot: GuardSnapshot, foregroundPackage: String?, ruleEnabled: Boolean): Boolean {
        if (foregroundPackage == null || !ruleEnabled) return false
        if (snapshot.activePackage == foregroundPackage && snapshot.phase != GuardSnapshot.Phase.NONE) {
            return false
        }
        return true
    }

    fun onLeft(snapshot: GuardSnapshot): Pair<GuardSnapshot, GuardCommand> =
        endSession(snapshot) to GuardCommand.None

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
        val dueAt = cleared.nextReminderAtMillis
        if (dueAt == null || nowMillis < dueAt || cleared.reminderPresentedFor == dueAt) {
            return cleared to GuardCommand.None
        }
        return cleared.copy(reminderPresentedFor = dueAt) to GuardCommand.ShowReminder(pkg)
    }

    /** Home-intent Quit. Does not disable the rule and does not start a reminder. */
    fun onQuit(snapshot: GuardSnapshot): GuardSnapshot = endSession(snapshot)

    fun onContinue(
        snapshot: GuardSnapshot,
        intervalMinutes: Int?,
        nowMillis: Long,
        today: String,
    ): GuardSnapshot {
        val pkg = snapshot.activePackage
        val dismissed = pkg != null && snapshot.dismissedOn(pkg, today)
        val next = if (intervalMinutes != null && intervalMinutes > 0 && !dismissed) {
            nowMillis + intervalMinutes * 60_000L
        } else {
            null
        }
        return snapshot.copy(
            phase = GuardSnapshot.Phase.CONTINUING,
            nextReminderAtMillis = next,
            reminderPresentedFor = null,
        )
    }

    fun onSnooze(snapshot: GuardSnapshot, snoozeMinutes: Int, nowMillis: Long): GuardSnapshot {
        val minutes = snoozeMinutes.coerceIn(1, 60)
        val next = nowMillis + minutes * 60_000L
        return snapshot.copy(
            phase = GuardSnapshot.Phase.CONTINUING,
            nextReminderAtMillis = next,
            reminderPresentedFor = null,
        )
    }

    /** Stops reminders for this app until the next local day. The rule stays enabled. */
    fun onDismissForToday(snapshot: GuardSnapshot, packageName: String, today: String): GuardSnapshot {
        val pkg = snapshot.activePackage ?: packageName
        return snapshot.copy(
            phase = if (snapshot.activePackage != null) GuardSnapshot.Phase.CONTINUING else snapshot.phase,
            nextReminderAtMillis = null,
            reminderPresentedFor = null,
            dismissals = snapshot.dismissals + (pkg to today),
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
     * new opening and does not reset the interval.
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
        if (cleared.phase == GuardSnapshot.Phase.AWAITING_OPENING) {
            return cleared to GuardCommand.ShowOpening(pkg)
        }
        if (cleared.phase != GuardSnapshot.Phase.CONTINUING) return cleared to GuardCommand.None
        if (!remindersEnabled || cleared.dismissedOn(pkg, today)) return cleared to GuardCommand.None
        val dueAt = cleared.nextReminderAtMillis ?: return cleared to GuardCommand.None
        if (nowMillis < dueAt) return cleared to GuardCommand.None
        return cleared.copy(reminderPresentedFor = dueAt) to GuardCommand.ShowReminder(pkg)
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
    )
}
