package com.nawzblaze.momentwise.data.model

/**
 * How often Momentwise checks in while the user stays inside a guarded app.
 *
 * Modelled as a sealed type so "Never" can never carry an invalid interval and
 * every interval is an explicit, validated minute count. The UI offers the
 * approved presets (5/10/15/20) plus Custom; see [FrequencySelection].
 */
sealed interface ReminderFrequency {

    /** No reminders are scheduled; the opening notice still pauses on launch. */
    data object Never : ReminderFrequency

    /** A reminder fires every [minutes] minute(s) spent inside the app. */
    data class EveryMinutes(val minutes: Int) : ReminderFrequency {
        init {
            require(minutes in MIN_MINUTES..MAX_MINUTES) {
                "Reminder interval must be within $MIN_MINUTES..$MAX_MINUTES minutes (was $minutes)"
            }
        }
    }

    val isEnabled: Boolean
        get() = this is EveryMinutes

    /** The effective interval in minutes, or null when reminders are off. */
    fun minutesOrNull(): Int? = (this as? EveryMinutes)?.minutes

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 300

        /** Approved preset chips, mirroring the product prototype. */
        val PRESET_MINUTES = listOf(5, 10, 15, 20)
    }
}

/**
 * The reminder-frequency selector chips on the Configure Rule screen:
 * Never, 5, 10, 15, 20 minutes, or Custom.
 */
enum class FrequencySelection {
    NEVER,
    MINUTES_5,
    MINUTES_10,
    MINUTES_15,
    MINUTES_20,
    CUSTOM;

    val isCustom: Boolean get() = this == CUSTOM

    /** The fixed minute count for preset chips; null for NEVER and CUSTOM. */
    fun presetMinutesOrNull(): Int? = when (this) {
        NEVER, CUSTOM -> null
        MINUTES_5 -> 5
        MINUTES_10 -> 10
        MINUTES_15 -> 15
        MINUTES_20 -> 20
    }

    companion object {
        /** The chip that should be shown as selected for an existing frequency. */
        fun forFrequency(frequency: ReminderFrequency): FrequencySelection = when (frequency) {
            ReminderFrequency.Never -> NEVER
            is ReminderFrequency.EveryMinutes ->
                PRESET_LOOKUP[frequency.minutes] ?: CUSTOM
        }

        private val PRESET_LOOKUP: Map<Int, FrequencySelection> = mapOf(
            5 to MINUTES_5,
            10 to MINUTES_10,
            15 to MINUTES_15,
            20 to MINUTES_20,
        )
    }
}

/**
 * Resolves a chip selection (plus the custom numeric field, when Custom is
 * active) into a concrete [ReminderFrequency].
 *
 * Returns null when the custom input is missing or out of the approved
 * 1..300 minute range — the Configure Rule screen surfaces that as a field
 * validation error instead of silently clamping.
 */
fun resolveFrequency(
    selection: FrequencySelection,
    customMinutes: Int?,
): ReminderFrequency? = when (selection) {
    FrequencySelection.NEVER -> ReminderFrequency.Never
    FrequencySelection.CUSTOM -> customMinutes
        ?.takeIf { it in ReminderFrequency.MIN_MINUTES..ReminderFrequency.MAX_MINUTES }
        ?.let { ReminderFrequency.EveryMinutes(it) }
    else -> selection.presetMinutesOrNull()?.let { ReminderFrequency.EveryMinutes(it) }
}
