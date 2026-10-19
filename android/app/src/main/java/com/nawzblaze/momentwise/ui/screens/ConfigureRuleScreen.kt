package com.nawzblaze.momentwise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nawzblaze.momentwise.MomentwiseViewModel
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.data.AppRuleDraft
import com.nawzblaze.momentwise.data.RuleDefaults
import com.nawzblaze.momentwise.data.RuleField
import com.nawzblaze.momentwise.data.RuleValidator
import com.nawzblaze.momentwise.data.model.FrequencySelection
import com.nawzblaze.momentwise.ui.components.FrequencyChipRow
import com.nawzblaze.momentwise.ui.components.InfoNote
import com.nawzblaze.momentwise.ui.components.MomentwiseTextField
import com.nawzblaze.momentwise.ui.components.MomentwiseTopBar
import com.nawzblaze.momentwise.ui.components.RuleSwitch
import com.nawzblaze.momentwise.ui.components.SectionCard
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors
import kotlinx.coroutines.launch

/**
 * Screen 4 — Configure Rule (per app).
 * Sections mirror the approved prototype exactly:
 *  A. Opening notice ("What brought you here?" default).
 *  B. Response options — Option 1 "Quit" (leave app), Option 2 "Anyways" (continue).
 *  C. Reminders — Never / 5 / 10 / 15 / 20 / Custom, reminder notice
 *     ("You're still here?" default), disabled-note when Never.
 *  D. Advanced reminder options — collapsed by default; quit / snooze (10 min) /
 *     dismiss-for-today labels and per-app reset.
 */
@Composable
fun ConfigureRuleScreen(
    viewModel: MomentwiseViewModel,
    appId: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTestPreview: () -> Unit,
    onRequestGuard: () -> Unit,
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val rule = catalog?.find { it.rule.appId == appId }?.rule
        ?: rules?.find { it.appId == appId }

    if (rules == null && catalog == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }
    if (rule == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        LaunchedEffect(appId) { onBack() }
        return
    }
    ConfigureRuleForm(
        viewModel = viewModel,
        rule = rule,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTestPreview = onTestPreview,
        onRequestGuard = onRequestGuard,
    )
}

@Composable
private fun ConfigureRuleForm(
    viewModel: MomentwiseViewModel,
    rule: com.nawzblaze.momentwise.data.model.AppRule,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTestPreview: () -> Unit,
    onRequestGuard: () -> Unit,
) {
    val appId = rule.appId
    val grants = rememberGrantStatus()
    val colors = LocalMomentwiseColors.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(appId, stateSaver = AppRuleDraftSaver) {
        mutableStateOf(AppRuleDraft.fromRule(rule))
    }
    var enabled by rememberSaveable(appId) { mutableStateOf(rule.enabled) }
    LaunchedEffect(rule.enabled) { enabled = rule.enabled }
    var showErrors by rememberSaveable(appId) { mutableStateOf(false) }
    // Advanced options are collapsed by default, per the approved specification.
    var advancedExpanded by rememberSaveable(appId) { mutableStateOf(false) }

    val validation = if (showErrors) RuleValidator.validate(draft.copy(enabled = enabled)) else null

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MomentwiseTopBar(
            title = "Configure ${rule.appName}",
            onBack = onBack,
            actions = {
                RuleSwitch(
                    checked = enabled,
                    onCheckedChange = { next ->
                        if (next && viewModel.needsDisclosure(grants.canMonitor)) {
                            onRequestGuard()
                            return@RuleSwitch
                        }
                        enabled = next
                        viewModel.setDisplayedRuleEnabled(rule.copy(enabled = next), next)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "${rule.appName} rule ${if (next) "activated" else "paused"}"
                            )
                        }
                    },
                )
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            VerticalSpacer(16)

            // --- Section A: Opening notice ---
            FormSectionLabel(stringResource(R.string.configure_section_opening))
            MomentwiseTextField(
                value = draft.openingNotice,
                onValueChange = { draft = draft.copy(openingNotice = it) },
                label = "Opening notice",
                placeholder = RuleDefaults.OPENING_NOTICE,
                helpText = stringResource(R.string.configure_opening_help),
                errorText = validation?.errorFor(RuleField.OPENING_NOTICE),
                maxLength = RuleDefaults.MAX_NOTICE_LENGTH,
            )

            VerticalSpacer(20)

            // --- Section B: Response options ---
            FormSectionLabel(stringResource(R.string.configure_section_options))
            Text(
                text = stringResource(R.string.configure_options_help),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            OptionConfigCard(
                pill = stringResource(R.string.configure_option_1_pill),
                pillEmphasis = true,
                actionTag = stringResource(R.string.configure_option_1_action),
                label = draft.option1Label,
                placeholder = RuleDefaults.QUIT_OPTION_LABEL,
                onLabelChange = { draft = draft.copy(option1Label = it) },
                errorText = validation?.errorFor(RuleField.OPTION1_LABEL),
                behavior = stringResource(R.string.configure_option_1_behavior),
            )
            VerticalSpacer(10)
            OptionConfigCard(
                pill = stringResource(R.string.configure_option_2_pill),
                pillEmphasis = false,
                actionTag = stringResource(R.string.configure_option_2_action),
                label = draft.option2Label,
                placeholder = RuleDefaults.CONTINUE_OPTION_LABEL,
                onLabelChange = { draft = draft.copy(option2Label = it) },
                errorText = validation?.errorFor(RuleField.OPTION2_LABEL),
                behavior = stringResource(R.string.configure_option_2_behavior),
            )

            VerticalSpacer(20)

            // --- Section C: Reminders ---
            FormSectionLabel(stringResource(R.string.configure_section_reminders))
            Text(
                text = stringResource(R.string.configure_reminders_help),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FrequencyChipRow(
                selected = draft.frequencySelection,
                onSelect = { draft = draft.copy(frequencySelection = it) },
                neverLabel = stringResource(R.string.configure_freq_never),
                customLabel = stringResource(R.string.configure_freq_custom),
            )

            if (draft.frequencySelection == FrequencySelection.CUSTOM) {
                VerticalSpacer(12)
                MomentwiseTextField(
                    value = draft.customMinutes?.toString() ?: "",
                    onValueChange = { next ->
                        if (next.length <= 3 && next.all { it.isDigit() }) {
                            draft = draft.copy(customMinutes = next.toIntOrNull())
                        }
                    },
                    label = stringResource(R.string.configure_custom_interval_label),
                    placeholder = RuleDefaults.CUSTOM_MINUTES_START.toString(),
                    errorText = validation?.errorFor(RuleField.CUSTOM_INTERVAL),
                    keyboardType = KeyboardType.Number,
                )
            }

            if (draft.frequencySelection != FrequencySelection.NEVER) {
                VerticalSpacer(14)
                MomentwiseTextField(
                    value = draft.reminderNotice,
                    onValueChange = { draft = draft.copy(reminderNotice = it) },
                    label = stringResource(R.string.configure_reminder_notice_label),
                    placeholder = RuleDefaults.REMINDER_NOTICE,
                    helpText = stringResource(R.string.configure_reminder_notice_help),
                    errorText = validation?.errorFor(RuleField.REMINDER_NOTICE),
                    maxLength = RuleDefaults.MAX_NOTICE_LENGTH,
                )
            } else {
                VerticalSpacer(12)
                InfoNote(
                    icon = Icons.Filled.Info,
                    text = stringResource(R.string.configure_reminders_disabled_note),
                )
            }

            VerticalSpacer(20)

            // --- Section D: Advanced reminder options (collapsed by default) ---
            AdvancedSection(
                expanded = advancedExpanded,
                onToggleExpanded = { advancedExpanded = !advancedExpanded },
                draft = draft,
                onDraftChange = { draft = it },
                onResetApp = {
                    viewModel.resetRuleToDefaults(appId)
                    // Rebuild the draft from the approved defaults immediately
                    // (matches the prototype's reset-this-app behaviour).
                    val fresh = RuleDefaults.ruleFor(
                        appId = rule.appId,
                        appName = rule.appName,
                        category = rule.category,
                        enabled = enabled,
                        isSample = rule.isSample,
                    )
                    draft = AppRuleDraft.fromRule(fresh)
                    showErrors = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Defaults restored for ${rule.appName}")
                    }
                },
            )

            VerticalSpacer(24)
        }

        // Sticky action bar
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    val candidate = draft.copy(enabled = enabled)
                    val result = RuleValidator.validate(candidate)
                    if (result.isValid) {
                        viewModel.saveRule(candidate.toRule())
                        scope.launch {
                            snackbarHostState.showSnackbar("Saved rule for ${rule.appName}")
                        }
                        onBack()
                    } else {
                        showErrors = true
                        scope.launch {
                            snackbarHostState.showSnackbar("Please fix the errors above.")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(stringResource(R.string.action_save_rule), fontWeight = FontWeight.SemiBold)
            }
            VerticalSpacer(6)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val candidate = draft.copy(enabled = enabled)
                        if (RuleValidator.validate(candidate).isValid) {
                            viewModel.saveRule(candidate.toRule())
                            onTestPreview()
                        } else {
                            showErrors = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(R.string.action_test_rule_preview),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = LocalMomentwiseColors.current.textSecondary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun OptionConfigCard(
    pill: String,
    pillEmphasis: Boolean,
    actionTag: String,
    label: String,
    placeholder: String,
    onLabelChange: (String) -> Unit,
    errorText: String?,
    behavior: String,
) {
    val colors = LocalMomentwiseColors.current
    SectionCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pill,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (pillEmphasis) colors.accentText else colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (pillEmphasis) colors.accentSubtle else colors.surfaceSubtle)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = actionTag,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        }
        VerticalSpacer(10)
        MomentwiseTextField(
            value = label,
            onValueChange = onLabelChange,
            label = stringResource(R.string.configure_button_label),
            placeholder = placeholder,
            errorText = errorText,
            maxLength = RuleDefaults.MAX_OPTION_LABEL_LENGTH,
        )
        VerticalSpacer(8)
        Text(
            text = behavior,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun AdvancedSection(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    draft: AppRuleDraft,
    onDraftChange: (AppRuleDraft) -> Unit,
    onResetApp: () -> Unit,
) {
    val colors = LocalMomentwiseColors.current
    SectionCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.configure_advanced_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse advanced options" else "Expand advanced options",
                tint = colors.textMuted,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(
                    text = stringResource(R.string.configure_advanced_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                VerticalSpacer(12)

                // Synced reminder notice — bound to the same draft state as
                // Section C, so the two fields can never disagree.
                MomentwiseTextField(
                    value = draft.reminderNotice,
                    onValueChange = { onDraftChange(draft.copy(reminderNotice = it)) },
                    label = stringResource(R.string.configure_reminder_notice_label),
                    placeholder = RuleDefaults.REMINDER_NOTICE,
                    helpText = stringResource(R.string.configure_advanced_notice_synced),
                    maxLength = RuleDefaults.MAX_NOTICE_LENGTH,
                )
                VerticalSpacer(14)

                AdvancedActionCard(title = stringResource(R.string.configure_advanced_action_1)) {
                    MomentwiseTextField(
                        value = draft.advancedQuitLabel,
                        onValueChange = { onDraftChange(draft.copy(advancedQuitLabel = it)) },
                        label = stringResource(R.string.configure_button_label),
                        placeholder = RuleDefaults.ADV_QUIT_LABEL,
                        maxLength = RuleDefaults.MAX_OPTION_LABEL_LENGTH,
                    )
                }
                VerticalSpacer(10)

                AdvancedActionCard(title = stringResource(R.string.configure_advanced_action_2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MomentwiseTextField(
                            value = draft.advancedSnoozeLabel,
                            onValueChange = { onDraftChange(draft.copy(advancedSnoozeLabel = it)) },
                            label = stringResource(R.string.configure_button_label),
                            placeholder = RuleDefaults.ADV_SNOOZE_LABEL,
                            maxLength = RuleDefaults.MAX_OPTION_LABEL_LENGTH,
                            modifier = Modifier.weight(1.6f),
                        )
                        MomentwiseTextField(
                            value = draft.snoozeMinutes?.toString() ?: "",
                            onValueChange = { next ->
                                if (next.length <= 2 && next.all { it.isDigit() }) {
                                    onDraftChange(draft.copy(snoozeMinutes = next.toIntOrNull()))
                                }
                            },
                            label = stringResource(R.string.configure_snooze_label),
                            placeholder = "10",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                VerticalSpacer(10)

                AdvancedActionCard(title = stringResource(R.string.configure_advanced_action_3)) {
                    MomentwiseTextField(
                        value = draft.advancedDismissLabel,
                        onValueChange = { onDraftChange(draft.copy(advancedDismissLabel = it)) },
                        label = stringResource(R.string.configure_button_label),
                        placeholder = RuleDefaults.ADV_DISMISS_LABEL,
                        maxLength = RuleDefaults.MAX_OPTION_LABEL_LENGTH,
                    )
                    VerticalSpacer(6)
                    Text(
                        text = stringResource(R.string.configure_dismiss_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }

                VerticalSpacer(12)
                TextButton(onClick = onResetApp) {
                    Text(
                        stringResource(R.string.configure_reset_app),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedActionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalMomentwiseColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceSubtle)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
        )
        VerticalSpacer(8)
        content()
    }
}

/** Survives rotation without putting an unsaveable data class straight into a Bundle. */
private val AppRuleDraftSaver = listSaver<AppRuleDraft, Any>(
    save = { draft ->
        listOf(
            draft.appId,
            draft.appName,
            draft.category,
            draft.isSample,
            draft.enabled,
            draft.openingNotice,
            draft.option1Label,
            draft.option2Label,
            draft.frequencySelection.name,
            draft.customMinutes ?: -1,
            draft.reminderNotice,
            draft.advancedQuitLabel,
            draft.advancedSnoozeLabel,
            draft.snoozeMinutes ?: -1,
            draft.advancedDismissLabel,
        )
    },
    restore = { saved ->
        AppRuleDraft(
            appId = saved[0] as String,
            appName = saved[1] as String,
            category = saved[2] as String,
            isSample = saved[3] as Boolean,
            enabled = saved[4] as Boolean,
            openingNotice = saved[5] as String,
            option1Label = saved[6] as String,
            option2Label = saved[7] as String,
            frequencySelection = FrequencySelection.valueOf(saved[8] as String),
            customMinutes = (saved[9] as Int).takeIf { it >= 0 },
            reminderNotice = saved[10] as String,
            advancedQuitLabel = saved[11] as String,
            advancedSnoozeLabel = saved[12] as String,
            snoozeMinutes = (saved[13] as Int).takeIf { it >= 0 },
            advancedDismissLabel = saved[14] as String,
        )
    },
)
