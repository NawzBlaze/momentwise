package com.nawzblaze.momentwise.data.model

/**
 * App appearance preference. SYSTEM is the default and follows the OS setting;
 * LIGHT and DARK are the manual overrides — matching the approved prototype
 * behaviour from the very first build.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        val DEFAULT = SYSTEM

        /** Stable persistence key; unknown values fall back to [DEFAULT]. */
        fun fromStorageKey(key: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}
