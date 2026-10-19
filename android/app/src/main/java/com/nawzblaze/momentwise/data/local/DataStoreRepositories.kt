package com.nawzblaze.momentwise.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nawzblaze.momentwise.data.AppRuleRepository
import com.nawzblaze.momentwise.data.AppSettingsRepository
import com.nawzblaze.momentwise.data.RuleCatalog
import com.nawzblaze.momentwise.data.RuleDefaults
import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Single DataStore file for all Momentwise local preferences. */
private val Context.momentwiseDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "momentwise_settings"
)

/**
 * DataStore-backed rule storage. Rules are persisted as one JSON array under a
 * versioned key; first launch seeds the clearly-labelled sample catalog until
 * device app detection is wired in a later milestone.
 */
class DataStoreAppRuleRepository(
    private val dataStore: DataStore<Preferences>,
) : AppRuleRepository {

    private object Keys {
        val RULES_JSON = stringPreferencesKey("app_rules_json.v1")
    }

    override val rules: Flow<List<AppRule>> = dataStore.data.map { prefs ->
        AppRuleJson.decodeList(prefs[Keys.RULES_JSON])
            ?.takeIf { it.isNotEmpty() }
            ?: RuleDefaults.sampleCatalog()
    }

    override suspend fun ruleFor(appId: String): AppRule? =
        rules.first().find { it.appId == appId }

    override suspend fun upsertRule(rule: AppRule) = mutate { RuleCatalog.upsert(it, rule) }

    override suspend fun setRuleEnabled(appId: String, enabled: Boolean) =
        mutate { RuleCatalog.setEnabled(it, appId, enabled) }

    override suspend fun resetRuleToDefaults(appId: String) =
        mutate { RuleCatalog.resetOne(it, appId) }

    override suspend fun resetAllToDefaults() {
        mutate { RuleCatalog.resetAll() }
    }

    private suspend fun mutate(transform: (List<AppRule>) -> List<AppRule>) {
        dataStore.edit { prefs ->
            val current = AppRuleJson.decodeList(prefs[Keys.RULES_JSON])
                ?.takeIf { it.isNotEmpty() }
                ?: RuleDefaults.sampleCatalog()
            prefs[Keys.RULES_JSON] = AppRuleJson.encodeList(transform(current))
        }
    }
}

/** DataStore-backed app settings (theme mode, onboarding). */
class DataStoreAppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode.v1")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed.v1")
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromStorageKey(prefs[Keys.THEME_MODE])
    }

    override val onboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }
}

/**
 * Creates the DataStore-backed repositories. Called once from
 * [com.nawzblaze.momentwise.MomentwiseApplication].
 */
object LocalPersistence {
    fun appRuleRepository(context: Context): AppRuleRepository =
        DataStoreAppRuleRepository(context.momentwiseDataStore)

    fun appSettingsRepository(context: Context): AppSettingsRepository =
        DataStoreAppSettingsRepository(context.momentwiseDataStore)
}
