package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.AppRule

/**
 * Pure catalog mutations shared by every [AppRuleRepository] implementation.
 *
 * Kept free of coroutines and Android types so rule isolation — one app's
 * update never changes another app's rule — can be unit-tested on the JVM.
 */
object RuleCatalog {

    fun upsert(current: List<AppRule>, rule: AppRule): List<AppRule> {
        val index = current.indexOfFirst { it.appId == rule.appId }
        return if (index >= 0) {
            current.toMutableList().also { it[index] = rule }
        } else {
            current + rule
        }
    }

    fun setEnabled(current: List<AppRule>, appId: String, enabled: Boolean): List<AppRule> =
        current.map { rule ->
            if (rule.appId == appId) {
                rule.copy(
                    enabled = enabled,
                    configuredEver = rule.configuredEver || enabled,
                )
            } else {
                rule
            }
        }

    /** Restores one app's copy and schedule to the approved defaults. Enabled state is kept. */
    fun resetOne(current: List<AppRule>, appId: String): List<AppRule> =
        current.map { rule ->
            if (rule.appId == appId) {
                RuleDefaults.ruleFor(
                    appId = rule.appId,
                    appName = rule.appName,
                    category = rule.category,
                    enabled = rule.enabled,
                    isSample = rule.isSample,
                )
            } else {
                rule
            }
        }

    /** Clears saved rules. Does not restore the foundation sample catalog. */
    fun resetAll(): List<AppRule> = emptyList()
}
