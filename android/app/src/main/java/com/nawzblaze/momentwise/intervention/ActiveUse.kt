package com.nawzblaze.momentwise.intervention

/**
 * Accumulated foreground-use for one guarded app.
 *
 * Time counts only while a sample says this app is eligible: it is the
 * foreground, the screen is on, Momentwise is not covering it, and monitoring
 * is allowed. A gap larger than [MAX_CREDIT_MILLIS] is not credited. That
 * covers a missed poll, a stalled process, screen-off, and a reboot. The open
 * segment anchor is not persisted, so process death cannot invent the gap.
 */
data class AppUse(
    val accumulatedMillis: Long = 0,
    val budgetMillis: Long = 0,
    val phase: Phase = Phase.IDLE,
    /** In-process only. Not written to storage. */
    val anchorElapsed: Long? = null,
    val reminderShown: Boolean = false,
) {
    enum class Phase { IDLE, ACCRUING, SNOOZE, DUE }
}

object ActiveUse {
    /**
     * See [VisitLifecycle.MAX_FOREGROUND_GAP_MILLIS]. A 2.5 second cap dropped
     * every sample once UsageStats and disk IO made the poll slower than that,
     * so a five-minute reminder never arrived.
     */
    const val MAX_CREDIT_MILLIS = VisitLifecycle.MAX_FOREGROUND_GAP_MILLIS

    fun minutesToMillis(minutes: Int?): Long {
        if (minutes == null || minutes <= 0) return 0
        return minutes.toLong() * 60_000L
    }

    /**
     * Continue starts a budget only from idle. An in-progress accrual or snooze
     * resumes. A due reminder stays due so the opening choice is not also a
     * silent dismissal of the check-in.
     */
    fun continueInto(current: AppUse, intervalMinutes: Int?): AppUse {
        val budget = minutesToMillis(intervalMinutes)
        return when (current.phase) {
            AppUse.Phase.DUE -> current.copy(anchorElapsed = null, reminderShown = false)
            AppUse.Phase.ACCRUING, AppUse.Phase.SNOOZE ->
                current.copy(anchorElapsed = null, reminderShown = false)
            AppUse.Phase.IDLE -> AppUse(
                accumulatedMillis = 0,
                budgetMillis = budget,
                phase = if (budget > 0) AppUse.Phase.ACCRUING else AppUse.Phase.IDLE,
            )
        }
    }

    /**
     * Editing the interval does not restart an in-progress snooze or a reminder
     * that is already due. While accruing, the new interval is the target and
     * accumulated use is kept. Never stops the reminder without deleting use.
     */
    fun applyBudget(current: AppUse, intervalMinutes: Int?): AppUse {
        if (current.phase == AppUse.Phase.SNOOZE || current.phase == AppUse.Phase.DUE) return current
        val budget = minutesToMillis(intervalMinutes)
        if (budget <= 0) {
            return current.copy(
                phase = AppUse.Phase.IDLE,
                budgetMillis = 0,
                anchorElapsed = null,
                reminderShown = false,
            )
        }
        val due = current.accumulatedMillis >= budget
        return current.copy(
            budgetMillis = budget,
            phase = if (due) AppUse.Phase.DUE else AppUse.Phase.ACCRUING,
            reminderShown = false,
        )
    }

    fun snooze(snoozeMinutes: Int): AppUse = AppUse(
        accumulatedMillis = 0,
        budgetMillis = minutesToMillis(snoozeMinutes.coerceIn(1, 60)),
        phase = AppUse.Phase.SNOOZE,
    )

    /**
     * @return the updated clock, and whether a reminder should be shown now.
     */
    fun sample(
        current: AppUse,
        elapsedRealtime: Long,
        eligible: Boolean,
        maxCreditMillis: Long = MAX_CREDIT_MILLIS,
    ): Pair<AppUse, Boolean> {
        if (current.phase == AppUse.Phase.DUE) {
            val show = eligible && !current.reminderShown && current.budgetMillis > 0
            return current.copy(anchorElapsed = null, reminderShown = current.reminderShown || show) to show
        }
        if (!eligible || current.budgetMillis <= 0 ||
            (current.phase != AppUse.Phase.ACCRUING && current.phase != AppUse.Phase.SNOOZE)
        ) {
            return current.copy(anchorElapsed = null) to false
        }
        val anchor = current.anchorElapsed
        if (anchor == null || elapsedRealtime < anchor) {
            return current.copy(anchorElapsed = elapsedRealtime) to false
        }
        val gap = elapsedRealtime - anchor
        val credit = if (gap in 1..maxCreditMillis) gap else 0L
        val accumulated = current.accumulatedMillis + credit
        if (accumulated >= current.budgetMillis) {
            return current.copy(
                accumulatedMillis = accumulated,
                phase = AppUse.Phase.DUE,
                anchorElapsed = null,
                reminderShown = true,
            ) to true
        }
        return current.copy(
            accumulatedMillis = accumulated,
            anchorElapsed = elapsedRealtime,
        ) to false
    }

    fun close(current: AppUse): AppUse = current.copy(anchorElapsed = null)

    /** Drop an open segment. Keep completed use. Do not credit unknown time. */
    fun onBoot(current: AppUse): AppUse = current.copy(anchorElapsed = null, reminderShown = false)
}
