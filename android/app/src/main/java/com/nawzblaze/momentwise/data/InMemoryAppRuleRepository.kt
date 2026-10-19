package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.AppRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [AppRuleRepository] used by JVM unit tests and as the design
 * reference for the DataStore-backed implementation.
 */
class InMemoryAppRuleRepository(
    initial: List<AppRule> = RuleDefaults.sampleCatalog(),
) : AppRuleRepository {

    private val state = MutableStateFlow(initial)

    override val rules: Flow<List<AppRule>> = state

    override suspend fun ruleFor(appId: String): AppRule? =
        state.value.find { it.appId == appId }

    override suspend fun upsertRule(rule: AppRule) {
        state.update { RuleCatalog.upsert(it, rule) }
    }

    override suspend fun setRuleEnabled(appId: String, enabled: Boolean) {
        state.update { RuleCatalog.setEnabled(it, appId, enabled) }
    }

    override suspend fun resetRuleToDefaults(appId: String) {
        state.update { RuleCatalog.resetOne(it, appId) }
    }

    override suspend fun resetAllToDefaults() {
        state.value = RuleCatalog.resetAll()
    }
}
