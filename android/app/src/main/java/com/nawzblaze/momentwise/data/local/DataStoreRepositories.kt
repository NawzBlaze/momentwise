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
import com.nawzblaze.momentwise.data.GuardSessionRepository
import com.nawzblaze.momentwise.data.RuleCatalog
import com.nawzblaze.momentwise.data.SampleCatalogMigration
import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.data.model.ThemeMode
import com.nawzblaze.momentwise.intervention.GuardSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Single DataStore file for all Momentwise local preferences. */
private val Context.momentwiseDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "momentwise_settings"
)

private object Keys {
    val RULES_JSON = stringPreferencesKey("app_rules_json.v1")
    val THEME_MODE = stringPreferencesKey("theme_mode.v1")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed.v1")
    val SAMPLE_MIGRATED = booleanPreferencesKey("sample_catalog_migrated.v1")
    val SAMPLE_NOTE = booleanPreferencesKey("sample_migration_note.v1")
    val DISCLOSURE_ACCEPTED = booleanPreferencesKey("guarding_disclosure_accepted.v1")
    val MONITORING_PAUSED = booleanPreferencesKey("monitoring_paused.v1")
    val GUARD_SESSION = stringPreferencesKey("guard_session_json.v1")
}

/**
 * Saved rules, keyed by package name. The foundation sample catalog is not
 * seeded. Known sample ids are dropped on read and by [LocalPersistence.migrateFoundationSamples].
 */
class DataStoreAppRuleRepository(
    private val dataStore: DataStore<Preferences>,
) : AppRuleRepository {

    override val rules: Flow<List<AppRule>> = dataStore.data.map { prefs ->
        SampleCatalogMigration.dropSampleIds(AppRuleJson.decodeList(prefs[Keys.RULES_JSON]).orEmpty())
    }

    override suspend fun ruleFor(appId: String): AppRule? =
        rules.first().find { it.appId == appId }

    override suspend fun upsertRule(rule: AppRule) = mutate { RuleCatalog.upsert(it, rule.copy(isSample = false)) }

    override suspend fun setRuleEnabled(appId: String, enabled: Boolean) =
        mutate { RuleCatalog.setEnabled(it, appId, enabled) }

    override suspend fun resetRuleToDefaults(appId: String) =
        mutate { RuleCatalog.resetOne(it, appId) }

    override suspend fun resetAllToDefaults() {
        dataStore.edit { prefs ->
            prefs[Keys.RULES_JSON] = AppRuleJson.encodeList(emptyList())
        }
    }

    private suspend fun mutate(transform: (List<AppRule>) -> List<AppRule>) {
        dataStore.edit { prefs ->
            val current = SampleCatalogMigration.dropSampleIds(
                AppRuleJson.decodeList(prefs[Keys.RULES_JSON]).orEmpty(),
            )
            prefs[Keys.RULES_JSON] = AppRuleJson.encodeList(transform(current))
        }
    }
}

class DataStoreAppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromStorageKey(prefs[Keys.THEME_MODE])
    }

    override val onboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    override val sampleMigrationNotePending: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SAMPLE_NOTE] ?: false
    }

    override val guardingDisclosureAccepted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DISCLOSURE_ACCEPTED] ?: false
    }

    override val monitoringPaused: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.MONITORING_PAUSED] ?: false
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setSampleMigrationNotePending(pending: Boolean) {
        dataStore.edit { it[Keys.SAMPLE_NOTE] = pending }
    }

    override suspend fun setGuardingDisclosureAccepted(accepted: Boolean) {
        dataStore.edit { it[Keys.DISCLOSURE_ACCEPTED] = accepted }
    }

    override suspend fun setMonitoringPaused(paused: Boolean) {
        dataStore.edit { it[Keys.MONITORING_PAUSED] = paused }
    }
}

class DataStoreGuardSessionRepository(
    private val dataStore: DataStore<Preferences>,
) : GuardSessionRepository {

    override val session: Flow<GuardSnapshot> = dataStore.data.map { prefs ->
        GuardSnapshotJson.decode(prefs[Keys.GUARD_SESSION])
    }

    override suspend fun current(): GuardSnapshot = session.first()

    override suspend fun save(snapshot: GuardSnapshot) {
        dataStore.edit { it[Keys.GUARD_SESSION] = GuardSnapshotJson.encode(snapshot) }
    }

    override suspend fun update(transform: (GuardSnapshot) -> GuardSnapshot) {
        dataStore.edit { prefs ->
            val current = GuardSnapshotJson.decode(prefs[Keys.GUARD_SESSION])
            prefs[Keys.GUARD_SESSION] = GuardSnapshotJson.encode(transform(current))
        }
    }
}

object LocalPersistence {
    fun appRuleRepository(context: Context): AppRuleRepository =
        DataStoreAppRuleRepository(context.momentwiseDataStore)

    fun appSettingsRepository(context: Context): AppSettingsRepository =
        DataStoreAppSettingsRepository(context.momentwiseDataStore)

    fun guardSessions(context: Context): GuardSessionRepository =
        DataStoreGuardSessionRepository(context.momentwiseDataStore)

    /** Drops persisted foundation sample ids once. Real package rules are kept. */
    suspend fun migrateFoundationSamples(context: Context) {
        context.momentwiseDataStore.edit { prefs ->
            if (prefs[Keys.SAMPLE_MIGRATED] == true) return@edit
            val decoded = AppRuleJson.decodeList(prefs[Keys.RULES_JSON]).orEmpty()
            val dropped = decoded.any { SampleCatalogMigration.isFoundationSample(it) }
            prefs[Keys.RULES_JSON] = AppRuleJson.encodeList(SampleCatalogMigration.dropSampleIds(decoded))
            prefs[Keys.SAMPLE_MIGRATED] = true
            if (dropped) prefs[Keys.SAMPLE_NOTE] = true
        }
    }
}
