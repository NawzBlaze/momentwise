package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.ReminderFrequency

/**
 * Stable string encoding for [ReminderFrequency] used by local persistence
 * ("never" / "minutes:10"). Pure Kotlin so it is covered by JVM unit tests.
 */
object ReminderFrequencyCodec {

    private const val NEVER = "never"
    private const val MINUTES_PREFIX = "minutes:"

    fun encode(frequency: ReminderFrequency): String = when (frequency) {
        ReminderFrequency.Never -> NEVER
        is ReminderFrequency.EveryMinutes -> "$MINUTES_PREFIX${frequency.minutes}"
    }

    /**
     * Decodes persisted text. Unknown, malformed, or out-of-range values fall
     * back to [ReminderFrequency.Never] rather than throwing, so corrupt storage
     * can never crash the app or invent an unapproved interval.
     */
    fun decode(raw: String?): ReminderFrequency {
        if (raw == null || raw.equals(NEVER, ignoreCase = true)) return ReminderFrequency.Never
        if (!raw.startsWith(MINUTES_PREFIX, ignoreCase = true)) return ReminderFrequency.Never
        val minutes = raw.removePrefix(MINUTES_PREFIX).trim().toIntOrNull()
            ?: return ReminderFrequency.Never
        if (minutes !in ReminderFrequency.MIN_MINUTES..ReminderFrequency.MAX_MINUTES) {
            return ReminderFrequency.Never
        }
        return ReminderFrequency.EveryMinutes(minutes)
    }
}
