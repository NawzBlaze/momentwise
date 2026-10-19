package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.AppRule

/** A launchable app, identified by package name. Labels come from the device. */
data class LaunchableApp(
    val packageName: String,
    val label: String,
)

data class CatalogEntry(
    val rule: AppRule,
    val installed: Boolean,
)

/**
 * Joins the device's launchable apps with saved rules. Saved rules stay keyed
 * by package name. Sample ids are not reintroduced here.
 */
object InstalledCatalog {

    fun merge(installed: List<LaunchableApp>, saved: List<AppRule>): List<CatalogEntry> {
        val kept = SampleCatalogMigration.dropSampleIds(saved)
        val savedById = kept.associateBy { it.appId }
        val installedIds = installed.map { it.packageName }.toSet()
        val onDevice = installed.map { app ->
            val existing = savedById[app.packageName]
            val rule = if (existing != null) {
                existing.copy(appName = app.label, isSample = false)
            } else {
                RuleDefaults.ruleFor(app.packageName, app.label, category = "")
            }
            CatalogEntry(rule = rule, installed = true)
        }
        val missing = kept
            .filter { it.appId !in installedIds }
            .map { CatalogEntry(rule = it.copy(isSample = false), installed = false) }
        return onDevice + missing
    }
}
