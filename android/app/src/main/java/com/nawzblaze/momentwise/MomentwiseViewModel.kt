package com.nawzblaze.momentwise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nawzblaze.momentwise.data.AppRuleRepository
import com.nawzblaze.momentwise.data.AppSettingsRepository
import com.nawzblaze.momentwise.data.CatalogEntry
import com.nawzblaze.momentwise.data.GuardSessionRepository
import com.nawzblaze.momentwise.data.InstalledCatalog
import com.nawzblaze.momentwise.data.LaunchableApp
import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.data.model.ThemeMode
import com.nawzblaze.momentwise.intervention.GuardSnapshot
import com.nawzblaze.momentwise.intervention.SessionClock
import com.nawzblaze.momentwise.intervention.shouldExplainBeforeGuarding
import com.nawzblaze.momentwise.monitoring.InstalledAppsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity-scoped view model. Screens collect persisted state; mutations go
 * through the repositories so every screen sees the same truth.
 */
class MomentwiseViewModel(
    private val appRuleRepository: AppRuleRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val guardSessions: GuardSessionRepository,
    private val installedAppsSource: InstalledAppsSource,
) : ViewModel() {

    val rules: StateFlow<List<AppRule>?> = appRuleRepository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val installedApps = MutableStateFlow<List<LaunchableApp>?>(null)

    val catalog: StateFlow<List<CatalogEntry>?> = combine(installedApps, rules) { installed, saved ->
        if (installed == null || saved == null) null else InstalledCatalog.merge(installed, saved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<ThemeMode> = appSettingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.DEFAULT)

    val onboardingCompleted: StateFlow<Boolean?> = appSettingsRepository.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sampleMigrationNotePending: StateFlow<Boolean> = appSettingsRepository.sampleMigrationNotePending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val guardingDisclosureAccepted: StateFlow<Boolean> = appSettingsRepository.guardingDisclosureAccepted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val session: StateFlow<GuardSnapshot> = guardSessions.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuardSnapshot())

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.Default) { installedAppsSource.load() }
            installedApps.value = loaded
        }
    }

    fun ruleFor(appId: String): AppRule? =
        catalog.value?.find { it.rule.appId == appId }?.rule
            ?: rules.value?.find { it.appId == appId }

    fun needsDisclosure(canMonitor: Boolean): Boolean =
        shouldExplainBeforeGuarding(guardingDisclosureAccepted.value, canMonitor)

    fun completeOnboarding() = viewModelScope.launch {
        appSettingsRepository.setOnboardingCompleted(true)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        appSettingsRepository.setThemeMode(mode)
    }

    fun setDisplayedRuleEnabled(rule: AppRule, enabled: Boolean) = viewModelScope.launch {
        appRuleRepository.upsertRule(
            rule.copy(
                enabled = enabled,
                configuredEver = rule.configuredEver || enabled,
                isSample = false,
            ),
        )
    }

    fun acceptGuardingDisclosure() = viewModelScope.launch {
        appSettingsRepository.setGuardingDisclosureAccepted(true)
    }

    fun dismissSampleMigrationNote() = viewModelScope.launch {
        appSettingsRepository.setSampleMigrationNotePending(false)
    }

    fun acknowledgeBootDrop() = viewModelScope.launch {
        guardSessions.update { SessionClock.acknowledgeBootDrop(it) }
    }

    fun saveRule(rule: AppRule) = viewModelScope.launch {
        appRuleRepository.upsertRule(rule.copy(isSample = false))
    }

    fun resetRuleToDefaults(appId: String) = viewModelScope.launch {
        appRuleRepository.resetRuleToDefaults(appId)
    }

    fun resetAllRules() = viewModelScope.launch {
        appRuleRepository.resetAllToDefaults()
        guardSessions.save(GuardSnapshot())
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MomentwiseViewModel(
                    appRuleRepository = container.appRuleRepository,
                    appSettingsRepository = container.appSettingsRepository,
                    guardSessions = container.guardSessions,
                    installedAppsSource = container.installedApps,
                )
            }
        }
    }
}
