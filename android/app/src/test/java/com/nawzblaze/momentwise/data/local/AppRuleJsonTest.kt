package com.nawzblaze.momentwise.data.local

import com.nawzblaze.momentwise.data.RuleDefaults
import com.nawzblaze.momentwise.data.model.ReminderFrequency
import com.nawzblaze.momentwise.data.model.RuleAction
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRuleJsonTest {

    @Test
    fun roundTripPreservesAnIndependentCatalog() {
        val original = RuleDefaults.sampleCatalog()
        val decoded = AppRuleJson.decodeList(AppRuleJson.encodeList(original))

        assertEquals(original, decoded)
    }

    @Test
    fun legacyOpeningNoticeIsMigratedWithoutTouchingCustomCopy() {
        val legacy = JSONArray().put(
            JSONObject()
                .put("appId", "instagram")
                .put("appName", "Instagram")
                .put("openingNotice", "You Are Distracted")
                .put("option1Label", "Quit")
                .put("option2Label", "Anyways"),
        )
        val custom = JSONArray().put(
            JSONObject()
                .put("appId", "reddit")
                .put("appName", "Reddit")
                .put("openingNotice", "You are distracted by design"),
        )

        assertEquals(
            RuleDefaults.OPENING_NOTICE,
            AppRuleJson.decodeList(legacy.toString())!!.single().openingNotice,
        )
        assertEquals(
            "You are distracted by design",
            AppRuleJson.decodeList(custom.toString())!!.single().openingNotice,
        )
    }

    @Test
    fun missingFieldsFallBackToApprovedDefaultsAndFixedActions() {
        val sparse = JSONArray().put(JSONObject().put("appId", "example").put("appName", "Example"))
        val rule = AppRuleJson.decodeList(sparse.toString())!!.single()

        assertEquals(RuleDefaults.OPENING_NOTICE, rule.openingNotice)
        assertEquals(RuleDefaults.QUIT_OPTION_LABEL, rule.quitOption.label)
        assertEquals(RuleAction.LEAVE_APP, rule.quitOption.action)
        assertEquals(RuleAction.CONTINUE_INTO_APP, rule.continueOption.action)
        assertEquals(ReminderFrequency.Never, rule.reminderFrequency)
        assertEquals(RuleDefaults.REMINDER_NOTICE, rule.reminderNotice)
        assertEquals(10, rule.reminderActions.snoozeMinutes)
    }

    @Test
    fun savedOpeningCopyIsNotRewrittenWhenDefaultsChange() {
        val saved = JSONArray().put(
            JSONObject()
                .put("appId", "com.instagram.android")
                .put("appName", "Instagram")
                .put("openingNotice", "What brought you here?")
                .put("option1Label", "Quit")
                .put("option2Label", "Anyways")
                .put("reminderFrequency", "never"),
        )
        val rule = AppRuleJson.decodeList(saved.toString())!!.single()

        assertEquals("What brought you here?", rule.openingNotice)
        assertEquals("Quit", rule.quitOption.label)
        assertEquals("Anyways", rule.continueOption.label)
        assertEquals(ReminderFrequency.Never, rule.reminderFrequency)
    }

    @Test
    fun shippedGenericPromptAndContinueLabelAreReplaced() {
        val saved = JSONArray().put(
            JSONObject()
                .put("appId", "com.instagram.android")
                .put("appName", "Instagram")
                .put("openingNotice", "What do you want to do right now?")
                .put("option2Label", "Continue")
                .put("reminderFrequency", "minutes:5"),
        )
        val rule = AppRuleJson.decodeList(saved.toString())!!.single()

        assertEquals(RuleDefaults.OPENING_NOTICE, rule.openingNotice)
        assertEquals(RuleDefaults.CONTINUE_OPTION_LABEL, rule.continueOption.label)
        assertEquals(ReminderFrequency.EveryMinutes(5), rule.reminderFrequency)
    }

    @Test
    fun shippedBreathCopyAndPurposeLabelAreReplaced() {
        val saved = JSONArray().put(
            JSONObject()
                .put("appId", "com.instagram.android")
                .put("appName", "Instagram")
                .put("openingNotice", "Take a breath. Choose what you want to do next.")
                .put("option2Label", "I'm here with purpose")
                .put("advDismissLabel", "Dismiss reminder")
                .put("reminderFrequency", "minutes:12"),
        )
        val rule = AppRuleJson.decodeList(saved.toString())!!.single()

        assertEquals("Take a second. What do you want from this app right now?", rule.openingNotice)
        assertEquals("Open Intentionally", rule.continueOption.label)
        assertEquals("Dismiss for today", rule.reminderActions.dismissLabel)
        assertEquals("Take a break", rule.reminderActions.quitLabel)
        assertEquals("Continue for now", rule.reminderActions.snoozeLabel)
        assertEquals("You've been here for a while.", rule.reminderNotice)
        assertEquals(ReminderFrequency.EveryMinutes(12), rule.reminderFrequency)
    }

    @Test
    fun shippedInterventionDefaultsAreMigratedButCustomCopyStays() {
        val shipped = JSONArray().put(
            JSONObject()
                .put("appId", "com.instagram.android")
                .put("appName", "Instagram")
                .put("openingNotice", "Take a second. What do you want from this visit?")
                .put("option1Label", "Leave Instagram")
                .put("option2Label", "Continue intentionally")
                .put("reminderNotice", "You're still here?")
                .put("advQuitLabel", "Okay, I'm quitting")
                .put("advSnoozeLabel", "Wait")
                .put("advDismissLabel", "Dismiss reminder")
                .put("reminderFrequency", "minutes:5"),
        )
        val migrated = AppRuleJson.decodeList(shipped.toString())!!.single()

        assertEquals(RuleDefaults.OPENING_NOTICE, migrated.openingNotice)
        assertEquals(RuleDefaults.QUIT_OPTION_LABEL, migrated.quitOption.label)
        assertEquals(RuleDefaults.CONTINUE_OPTION_LABEL, migrated.continueOption.label)
        assertEquals(RuleDefaults.REMINDER_NOTICE, migrated.reminderNotice)
        assertEquals(RuleDefaults.ADV_QUIT_LABEL, migrated.reminderActions.quitLabel)
        assertEquals(RuleDefaults.ADV_SNOOZE_LABEL, migrated.reminderActions.snoozeLabel)
        assertEquals(RuleDefaults.ADV_DISMISS_LABEL, migrated.reminderActions.dismissLabel)
        assertEquals(ReminderFrequency.EveryMinutes(5), migrated.reminderFrequency)

        val custom = JSONArray().put(
            JSONObject()
                .put("appId", "com.reddit.frontpage")
                .put("appName", "Reddit")
                .put("openingNotice", "My own opening question")
                .put("option1Label", "Exit this time")
                .put("option2Label", "Keep browsing")
                .put("reminderNotice", "Pause and decide")
                .put("advQuitLabel", "I'm done")
                .put("advSnoozeLabel", "Give me a bit")
                .put("advDismissLabel", "No more today")
                .put("reminderFrequency", "minutes:7"),
        )
        val preserved = AppRuleJson.decodeList(custom.toString())!!.single()

        assertEquals("My own opening question", preserved.openingNotice)
        assertEquals("Exit this time", preserved.quitOption.label)
        assertEquals("Keep browsing", preserved.continueOption.label)
        assertEquals("Pause and decide", preserved.reminderNotice)
        assertEquals("I'm done", preserved.reminderActions.quitLabel)
        assertEquals("Give me a bit", preserved.reminderActions.snoozeLabel)
        assertEquals("No more today", preserved.reminderActions.dismissLabel)
        assertEquals(ReminderFrequency.EveryMinutes(7), preserved.reminderFrequency)
    }

    @Test
    fun customPromptLabelAndIntervalStay() {
        val saved = JSONArray().put(
            JSONObject()
                .put("appId", "com.instagram.android")
                .put("appName", "Instagram")
                .put("openingNotice", "What brought you here?")
                .put("option2Label", "Stay a minute")
                .put("advDismissLabel", "Not today, thanks")
                .put("reminderFrequency", "minutes:7"),
        )
        val rule = AppRuleJson.decodeList(saved.toString())!!.single()

        assertEquals("What brought you here?", rule.openingNotice)
        assertEquals("Stay a minute", rule.continueOption.label)
        assertEquals("Not today, thanks", rule.reminderActions.dismissLabel)
        assertEquals(ReminderFrequency.EveryMinutes(7), rule.reminderFrequency)
    }

    @Test
    fun corruptStorageDoesNotThrow() {
        assertNull(AppRuleJson.decodeList(null))
        assertNull(AppRuleJson.decodeList(""))
        assertNull(AppRuleJson.decodeList("not-json"))
        assertTrue(AppRuleJson.decodeList("[]")!!.isEmpty())
    }
}
