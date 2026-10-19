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
        if (cleared.activePackage == packageName && cleared.openingHandledForPackage == packageName) {
            return cleared to GuardCommand.None
        }
        val next = endSession(cleared).copy(
            activePackage = packageName,
            phase = GuardSnapshot.Phase.AWAITING_OPENING,
            openingHandledForPackage = packageName,
        )
        return next to GuardCommand.ShowOpening(packageName)
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
     * After process death, resume the clock only if that package is still the
     * one Usage Access last observed. Otherwise end the session quietly.
     */
    fun reconcileAfterRestart(snapshot: GuardSnapshot, observedPackage: String?): GuardSnapshot {
        if (snapshot.phase == GuardSnapshot.Phase.NONE) return snapshot
        if (observedPackage != null && observedPackage == snapshot.activePackage) return snapshot
        return endSession(snapshot)
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
