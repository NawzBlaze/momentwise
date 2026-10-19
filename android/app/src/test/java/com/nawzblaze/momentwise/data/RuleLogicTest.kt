package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.data.model.FrequencySelection
import com.nawzblaze.momentwise.data.model.ReminderActions
import com.nawzblaze.momentwise.data.model.ReminderFrequency
import com.nawzblaze.momentwise.data.model.RuleAction
import com.nawzblaze.momentwise.data.model.ThemeMode
import com.nawzblaze.momentwise.data.model.resolveFrequency
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleDefaultsTest {

    @Test
    fun freshRuleUsesApprovedCopy() {
        val rule = RuleDefaults.ruleFor("example", "Example", "Test")

        assertEquals("What brought you here?", rule.openingNotice)
        assertEquals("Quit", rule.quitOption.label)
        assertEquals(RuleAction.LEAVE_APP, rule.quitOption.action)
        assertEquals("Anyways", rule.continueOption.label)
        assertEquals(RuleAction.CONTINUE_INTO_APP, rule.continueOption.action)
        assertEquals(ReminderFrequency.Never, rule.reminderFrequency)
        assertEquals("You're still here?", rule.reminderNotice)
        assertEquals("Okay, I'm quitting", rule.reminderActions.quitLabel)
        assertEquals(RuleAction.LEAVE_APP, rule.reminderActions.quitAction)
        assertEquals("Wait", rule.reminderActions.snoozeLabel)
        assertEquals(10, rule.reminderActions.snoozeMinutes)
        assertEquals(RuleAction.SNOOZE_REMINDER, rule.reminderActions.snoozeAction)
        assertEquals("Dismiss reminder", rule.reminderActions.dismissLabel)
        assertEquals(RuleAction.DISMISS_FOR_TODAY, rule.reminderActions.dismissAction)
        assertFalse(rule.enabled)
    }

    @Test
    fun sampleCatalogMatchesThePrototypeSeed() {
        val catalog = RuleDefaults.sampleCatalog()

        assertEquals(listOf("instagram", "tiktok", "x", "youtube", "reddit", "threads"), catalog.map { it.appId })
        assertTrue(catalog.all { it.isSample })
        assertTrue(catalog.all { it.openingNotice == RuleDefaults.OPENING_NOTICE })

        val instagram = catalog.first { it.appId == "instagram" }
        val tiktok = catalog.first { it.appId == "tiktok" }
        assertTrue(instagram.enabled)
        assertEquals(ReminderFrequency.EveryMinutes(10), instagram.reminderFrequency)
        assertTrue(tiktok.enabled)
        assertEquals(ReminderFrequency.EveryMinutes(15), tiktok.reminderFrequency)
        assertTrue(catalog.filter { it.appId != "instagram" && it.appId != "tiktok" }.all { !it.enabled })
        assertTrue(catalog.filter { it.appId != "instagram" && it.appId != "tiktok" }
            .all { it.reminderFrequency == ReminderFrequency.Never })
    }

    @Test
    fun eachDefaultRuleIsAnIndependentInstance() {
        val first = RuleDefaults.ruleFor("a", "A", "Cat", enabled = true)
        val second = RuleDefaults.ruleFor("b", "B", "Cat", enabled = true)

        assertNotSame(first.reminderActions, second.reminderActions)
        assertNotSame(first.quitOption, second.quitOption)
    }
}

class ReminderFrequencyTest {

    @Test
    fun presetsResolveWithoutACustomValue() {
        assertEquals(ReminderFrequency.Never, resolveFrequency(FrequencySelection.NEVER, customMinutes = 8))
        assertEquals(ReminderFrequency.EveryMinutes(5), resolveFrequency(FrequencySelection.MINUTES_5, null))
        assertEquals(ReminderFrequency.EveryMinutes(10), resolveFrequency(FrequencySelection.MINUTES_10, null))
        assertEquals(ReminderFrequency.EveryMinutes(15), resolveFrequency(FrequencySelection.MINUTES_15, null))
        assertEquals(ReminderFrequency.EveryMinutes(20), resolveFrequency(FrequencySelection.MINUTES_20, null))
    }

    @Test
    fun customRequiresAnInRangeInteger() {
        assertEquals(ReminderFrequency.EveryMinutes(1), resolveFrequency(FrequencySelection.CUSTOM, 1))
        assertEquals(ReminderFrequency.EveryMinutes(8), resolveFrequency(FrequencySelection.CUSTOM, 8))
        assertEquals(ReminderFrequency.EveryMinutes(300), resolveFrequency(FrequencySelection.CUSTOM, 300))
        assertNull(resolveFrequency(FrequencySelection.CUSTOM, null))
        assertNull(resolveFrequency(FrequencySelection.CUSTOM, 0))
        assertNull(resolveFrequency(FrequencySelection.CUSTOM, 301))
    }

    @Test
    fun chipSelectionFollowsTheSavedInterval() {
        assertEquals(FrequencySelection.NEVER, FrequencySelection.forFrequency(ReminderFrequency.Never))
        assertEquals(FrequencySelection.MINUTES_10, FrequencySelection.forFrequency(ReminderFrequency.EveryMinutes(10)))
        assertEquals(FrequencySelection.CUSTOM, FrequencySelection.forFrequency(ReminderFrequency.EveryMinutes(8)))
        assertEquals(FrequencySelection.CUSTOM, FrequencySelection.forFrequency(ReminderFrequency.EveryMinutes(12)))
    }

    @Test
    fun everyMinutesRejectsOutOfRangeValues() {
        assertIllegal { ReminderFrequency.EveryMinutes(0) }
        assertIllegal { ReminderFrequency.EveryMinutes(301) }
    }
}

class RuleValidatorTest {

    private fun draft(
        openingNotice: String = RuleDefaults.OPENING_NOTICE,
        option1: String = RuleDefaults.QUIT_OPTION_LABEL,
        option2: String = RuleDefaults.CONTINUE_OPTION_LABEL,
        selection: FrequencySelection = FrequencySelection.NEVER,
        customMinutes: Int? = 8,
        reminderNotice: String = RuleDefaults.REMINDER_NOTICE,
    ) = AppRuleDraft.fromRule(RuleDefaults.ruleFor("id", "Name", "Cat")).copy(
        openingNotice = openingNotice,
        option1Label = option1,
        option2Label = option2,
        frequencySelection = selection,
        customMinutes = customMinutes,
        reminderNotice = reminderNotice,
    )

    @Test
    fun approvedDefaultsAreValid() {
        assertTrue(RuleValidator.validate(draft()).isValid)
    }

    @Test
    fun blankOpeningNoticeIsRejected() {
        val result = RuleValidator.validate(draft(openingNotice = "   "))
        assertFalse(result.isValid)
        assertEquals("Please enter an opening notice.", result.errorFor(RuleField.OPENING_NOTICE))
    }

    @Test
    fun optionLabelsMustBePresentAndDistinctIgnoringCase() {
        assertEquals(
            "Label cannot be empty.",
            RuleValidator.validate(draft(option1 = " ")).errorFor(RuleField.OPTION1_LABEL),
        )
        assertEquals(
            "Option labels must be distinct.",
            RuleValidator.validate(draft(option1 = "Leave", option2 = " leave ")).errorFor(RuleField.OPTION2_LABEL),
        )
    }

    @Test
    fun customIntervalIsValidatedOnlyWhenCustomIsSelected() {
        val missing = RuleValidator.validate(draft(selection = FrequencySelection.CUSTOM, customMinutes = null))
        assertTrue(missing.errorFor(RuleField.CUSTOM_INTERVAL)!!.contains("1"))
        assertTrue(missing.errorFor(RuleField.CUSTOM_INTERVAL)!!.contains("300"))

        val preset = RuleValidator.validate(draft(selection = FrequencySelection.MINUTES_5, customMinutes = null))
        assertTrue(preset.isValid)
    }

    @Test
    fun reminderNoticeIsRequiredOnlyWhenRemindersAreOn() {
        val off = RuleValidator.validate(draft(selection = FrequencySelection.NEVER, reminderNotice = ""))
        assertTrue(off.isValid)

        val on = RuleValidator.validate(draft(selection = FrequencySelection.MINUTES_10, reminderNotice = "  "))
        assertEquals("Please provide a reminder notice message.", on.errorFor(RuleField.REMINDER_NOTICE))
    }

    @Test
    fun overlongCopyIsRejected() {
        val longNotice = "x".repeat(RuleDefaults.MAX_NOTICE_LENGTH + 1)
        val longLabel = "y".repeat(RuleDefaults.MAX_OPTION_LABEL_LENGTH + 1)
        val result = RuleValidator.validate(draft(openingNotice = longNotice, option1 = longLabel))
        assertTrue(result.errorFor(RuleField.OPENING_NOTICE)!!.contains("120"))
        assertTrue(result.errorFor(RuleField.OPTION1_LABEL)!!.contains("24"))
    }
}

class AppRuleDraftTest {

    @Test
    fun saveNormalisesBlankAdvancedLabelsAndSnooze() {
        val draft = AppRuleDraft.fromRule(RuleDefaults.ruleFor("id", "Name", "Cat")).copy(
            openingNotice = "  Why this app?  ",
            option1Label = " Step away ",
            option2Label = " Go in ",
            frequencySelection = FrequencySelection.CUSTOM,
            customMinutes = 8,
            reminderNotice = "  Still scrolling? ",
            advancedQuitLabel = " ",
            advancedSnoozeLabel = "",
            snoozeMinutes = null,
            advancedDismissLabel = "   ",
            enabled = true,
        )

        val saved = draft.toRule()
        assertEquals("Why this app?", saved.openingNotice)
        assertEquals("Step away", saved.quitOption.label)
        assertEquals(RuleAction.LEAVE_APP, saved.quitOption.action)
        assertEquals("Go in", saved.continueOption.label)
        assertEquals(RuleAction.CONTINUE_INTO_APP, saved.continueOption.action)
        assertEquals(ReminderFrequency.EveryMinutes(8), saved.reminderFrequency)
        assertEquals("Still scrolling?", saved.reminderNotice)
        assertEquals(RuleDefaults.ADV_QUIT_LABEL, saved.reminderActions.quitLabel)
        assertEquals(RuleDefaults.ADV_SNOOZE_LABEL, saved.reminderActions.snoozeLabel)
        assertEquals(ReminderActions.DEFAULT_SNOOZE_MINUTES, saved.reminderActions.snoozeMinutes)
        assertEquals(RuleDefaults.ADV_DISMISS_LABEL, saved.reminderActions.dismissLabel)
        assertTrue(saved.configuredEver)
        assertTrue(saved.enabled)
    }

    @Test
    fun snoozeAboveTheFieldMaximumIsClamped() {
        val draft = AppRuleDraft.fromRule(RuleDefaults.ruleFor("id", "Name", "Cat"))
            .copy(snoozeMinutes = 99)
        assertEquals(ReminderActions.MAX_SNOOZE_MINUTES, draft.toRule().reminderActions.snoozeMinutes)
    }

    @Test
    fun invalidDraftIsNotWhatCallersShouldPersist() {
        val draft = AppRuleDraft.fromRule(RuleDefaults.ruleFor("id", "Name", "Cat")).copy(
            frequencySelection = FrequencySelection.CUSTOM,
            customMinutes = 0,
            openingNotice = "",
        )
        assertFalse(RuleValidator.validate(draft).isValid)
    }
}

class ReminderFrequencyCodecTest {

    @Test
    fun roundTrip() {
        val values = listOf(
            ReminderFrequency.Never,
            ReminderFrequency.EveryMinutes(1),
            ReminderFrequency.EveryMinutes(10),
            ReminderFrequency.EveryMinutes(300),
        )
        values.forEach { frequency ->
            assertEquals(frequency, ReminderFrequencyCodec.decode(ReminderFrequencyCodec.encode(frequency)))
        }
    }

    @Test
    fun corruptValuesFallBackToNever() {
        assertEquals(ReminderFrequency.Never, ReminderFrequencyCodec.decode(null))
        assertEquals(ReminderFrequency.Never, ReminderFrequencyCodec.decode(""))
        assertEquals(ReminderFrequency.Never, ReminderFrequencyCodec.decode("minutes:"))
        assertEquals(ReminderFrequency.Never, ReminderFrequencyCodec.decode("minutes:0"))
        assertEquals(ReminderFrequency.Never, ReminderFrequencyCodec.decode("minutes:999"))
        assertEquals(ReminderFrequency.Never, ReminderFrequencyCodec.decode("hourly"))
    }
}

class RuleCatalogIsolationTest {

    @Test
    fun updatingOneAppDoesNotChangeAnother() {
        val catalog = RuleDefaults.sampleCatalog()
        val updated = RuleCatalog.upsert(
            catalog,
            catalog.first { it.appId == "instagram" }.copy(openingNotice = "A different question"),
        )

        assertEquals("A different question", updated.first { it.appId == "instagram" }.openingNotice)
        assertEquals(
            RuleDefaults.OPENING_NOTICE,
            updated.first { it.appId == "tiktok" }.openingNotice,
        )
        assertEquals(catalog.size, updated.size)
    }

    @Test
    fun enablingOneAppDoesNotEnableTheOthers() {
        val catalog = RuleDefaults.sampleCatalog()
        val updated = RuleCatalog.setEnabled(catalog, "reddit", enabled = true)

        assertTrue(updated.first { it.appId == "reddit" }.enabled)
        assertTrue(updated.first { it.appId == "reddit" }.configuredEver)
        assertFalse(updated.first { it.appId == "youtube" }.enabled)
        assertEquals(
            catalog.first { it.appId == "instagram" },
            updated.first { it.appId == "instagram" },
        )
    }

    @Test
    fun resettingOneAppKeepsTheOthersAndItsEnabledFlag() {
        val catalog = RuleCatalog.upsert(
            RuleDefaults.sampleCatalog(),
            RuleDefaults.sampleCatalog().first { it.appId == "instagram" }.copy(
                openingNotice = "Custom",
                reminderFrequency = ReminderFrequency.EveryMinutes(5),
            ),
        )
        val reset = RuleCatalog.resetOne(catalog, "instagram")

        val instagram = reset.first { it.appId == "instagram" }
        assertEquals(RuleDefaults.OPENING_NOTICE, instagram.openingNotice)
        assertEquals(ReminderFrequency.Never, instagram.reminderFrequency)
        assertTrue(instagram.enabled)
        assertEquals(
            ReminderFrequency.EveryMinutes(15),
            reset.first { it.appId == "tiktok" }.reminderFrequency,
        )
    }

    @Test
    fun repositoryUpsertIsolatesRules() = runBlocking {
        val repository = InMemoryAppRuleRepository()
        val tiktokBefore = repository.ruleFor("tiktok")
        repository.upsertRule(
            repository.ruleFor("instagram")!!.copy(reminderNotice = "Time to leave?"),
        )

        assertEquals("Time to leave?", repository.ruleFor("instagram")!!.reminderNotice)
        assertEquals(tiktokBefore, repository.ruleFor("tiktok"))
    }
}

class ThemeModeTest {

    @Test
    fun systemIsTheDefaultAndUnknownKeysFallBackToIt() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DEFAULT)
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageKey("nope"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorageKey("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorageKey("LIGHT"))
    }
}

private fun assertIllegal(block: () -> Unit) {
    try {
        block()
    } catch (_: IllegalArgumentException) {
        return
    }
    throw AssertionError("Expected IllegalArgumentException")
}
