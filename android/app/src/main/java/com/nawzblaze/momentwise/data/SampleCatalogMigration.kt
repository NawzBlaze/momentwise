package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.AppRule

/**
 * The foundation build stored sample ids (`instagram`, `tiktok`, …) that are
 * not Android package names. They are dropped, not copied onto real apps.
 * A kept id must look like a package name (it contains a dot) and must not be
 * one of those known sample ids.
 */
object SampleCatalogMigration {

    val SAMPLE_IDS = setOf("instagram", "tiktok", "x", "youtube", "reddit", "threads")

    fun isFoundationSample(rule: AppRule): Boolean =
        rule.appId in SAMPLE_IDS || (rule.isSample && '.' !in rule.appId)

    fun dropSampleIds(rules: List<AppRule>): List<AppRule> =
        rules.filterNot(::isFoundationSample)
}
