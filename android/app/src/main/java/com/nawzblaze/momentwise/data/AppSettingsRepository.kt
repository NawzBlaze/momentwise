package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * App-wide preferences: appearance and onboarding state.
 * SYSTEM is the default theme mode, per the approved product behaviour.
 */
interface AppSettingsRepository {
    val themeMode: Flow<ThemeMode>
    val onboardingCompleted: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOnboardingCompleted(completed: Boolean)
}
