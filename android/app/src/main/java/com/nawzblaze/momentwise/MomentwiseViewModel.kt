package com.nawzblaze.momentwise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nawzblaze.momentwise.data.AppRuleRepository
import com.nawzblaze.momentwise.data.AppSettingsRepository
import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.data.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single activity-scoped view model over the two repositories.
 *
 * Screens collect [rules], [themeMode], and [onboardingCompleted]; mutations go
 * through the repository so every screen observes the same persisted truth.
 */
class MomentwiseViewModel(
    private val appRuleRepository: AppRuleRepository,
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    /**
     * Null until the first repository emission, so screens can show a quiet
     * loading state instead of a flash of "no rules".
     */
    val rules: StateFlow<List<AppRule>?> = appRuleRepository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<ThemeMode> = appSettingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.DEFAULT)

    /** Null while the first DataStore read is in flight (start-destination decision). */
    val onboardingCompleted: StateFlow<Boolean?> = appSettingsRepository.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun ruleFor(appId: String): AppRule? = rules.value?.find { it.appId == appId }

    fun completeOnboarding() = viewModelScope.launch {
        appSettingsRepository.setOnboardingCompleted(true)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        appSettingsRepository.setThemeMode(mode)
    }

    fun setRuleEnabled(appId: String, enabled: Boolean) = viewModelScope.launch {
        appRuleRepository.setRuleEnabled(appId, enabled)
    }

    fun saveRule(rule: AppRule) = viewModelScope.launch {
        appRuleRepository.upsertRule(rule)
    }

    fun resetRuleToDefaults(appId: String) = viewModelScope.launch {
        appRuleRepository.resetRuleToDefaults(appId)
    }

    fun resetAllRules() = viewModelScope.launch {
        appRuleRepository.resetAllToDefaults()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MomentwiseViewModel(
                    appRuleRepository = container.appRuleRepository,
                    appSettingsRepository = container.appSettingsRepository,
                )
            }
        }
    }
}
