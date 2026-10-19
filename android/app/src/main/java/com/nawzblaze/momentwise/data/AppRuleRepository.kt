package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.AppRule
import kotlinx.coroutines.flow.Flow

/**
 * Per-app rule storage. Each app's rule is addressed by its stable [AppRule.appId]
 * and is fully independent of every other rule.
 */
interface AppRuleRepository {
    /** All known rules, in catalog order. */
    val rules: Flow<List<AppRule>>

    suspend fun ruleFor(appId: String): AppRule?

    /** Inserts or replaces one app's rule, leaving every other rule untouched. */
    suspend fun upsertRule(rule: AppRule)

    /** Toggles a single rule's enabled state without touching its configuration. */
    suspend fun setRuleEnabled(appId: String, enabled: Boolean)

    /** Restores one app's rule to the approved defaults (enabled state preserved). */
    suspend fun resetRuleToDefaults(appId: String)

    /** Restores the entire catalog to the seeded defaults. */
    suspend fun resetAllToDefaults()
}
