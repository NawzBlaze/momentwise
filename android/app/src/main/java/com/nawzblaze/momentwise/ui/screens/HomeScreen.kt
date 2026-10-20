package com.nawzblaze.momentwise.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nawzblaze.momentwise.MomentwiseViewModel
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.ui.components.AppRuleChip
import com.nawzblaze.momentwise.ui.components.IconActionRow
import com.nawzblaze.momentwise.ui.components.InfoNote
import com.nawzblaze.momentwise.ui.components.SectionCard
import com.nawzblaze.momentwise.ui.components.SectionEyebrow
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors

/**
 * Screen 2 — Home overview.
 * Deliberately simple: a rule summary, quick previews of the two configured
 * experiences, and a how-it-works reference. No invented usage statistics —
 * counters shown are configuration facts (how many rules are active). They are
 * not usage statistics.
 */
@Composable
fun HomeScreen(
    viewModel: MomentwiseViewModel,
    onNavigateApps: () -> Unit,
    onNavigateSettings: () -> Unit,
    onTestOpeningNotice: (String) -> Unit,
    onTestReminder: (String) -> Unit,
    onConfigureRule: (String) -> Unit,
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val colors = LocalMomentwiseColors.current
    val grants = rememberGrantStatus()
    val loadedRules = rules.orEmpty()
    val activeRules = loadedRules.filter { it.enabled }
    val paused by viewModel.monitoringPaused.collectAsStateWithLifecycle()
    val shouldRun = monitoringShouldRun(activeRules.size, paused, grants)
    val monitorHealth = rememberMonitorHealth(shouldRun)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Brand top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNavigateSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                    tint = colors.textSecondary,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            VerticalSpacer(8)

            // Summary card
            SectionCard {
                SectionEyebrow(stringResource(R.string.home_summary_eyebrow))
                VerticalSpacer(6)
                val heading = when {
                    rules == null -> stringResource(R.string.apps_loading)
                    activeRules.isEmpty() -> stringResource(R.string.home_summary_none)
                    activeRules.size == 1 -> stringResource(R.string.home_summary_one)
                    else -> stringResource(R.string.home_summary_many, activeRules.size)
                }
                Text(
                    text = heading,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                VerticalSpacer(6)
                Text(
                    text = when {
                        activeRules.isEmpty() -> stringResource(R.string.home_summary_desc_empty)
                        monitorHealth == com.nawzblaze.momentwise.monitoring.MonitorHealth.STOPPED ->
                            stringResource(R.string.home_summary_desc_stopped)
                        grants.pauseAppearsOnItsOwn -> stringResource(R.string.home_summary_desc_overlay)
                        grants.canMonitor -> stringResource(R.string.home_summary_desc_notification)
                        else -> stringResource(R.string.home_summary_desc_blocked)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                VerticalSpacer(14)
                if (activeRules.isNotEmpty()) {
                    ActiveRuleChips(activeRules = activeRules, onChipClick = onConfigureRule)
                }
                VerticalSpacer(14)
                MonitoringStoppedNote(shouldRun = shouldRun, health = monitorHealth)
                VerticalSpacer(14)
                Button(
                    onClick = onNavigateApps,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceSubtle,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(
                        stringResource(R.string.home_manage_apps),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            VerticalSpacer(16)

            // Test drives
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.home_test_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.home_test_section_meta),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                }
                VerticalSpacer(4)
                Text(
                    text = stringResource(R.string.home_test_section_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                VerticalSpacer(12)

                val previewRule = activeRules.firstOrNull() ?: loadedRules.firstOrNull()
                if (previewRule != null) {
                    IconActionRow(
                        icon = Icons.Outlined.PauseCircle,
                        title = stringResource(R.string.home_test_opening),
                        hint = "Preview \"${previewRule.openingNotice}\" on ${previewRule.appName}",
                        onClick = { onTestOpeningNotice(previewRule.appId) },
                    )
                    VerticalSpacer(10)
                    IconActionRow(
                        icon = Icons.Outlined.Schedule,
                        title = stringResource(R.string.home_test_reminder),
                        hint = previewRule.scheduledCheckInLabel()
                            ?: "Reminders are off for ${previewRule.appName}",
                        onClick = { onTestReminder(previewRule.appId) },
                    )
                } else {
                    Text(
                        text = "Configure an app first to test its notices here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }

            VerticalSpacer(16)

            if (session.sessionDroppedOnBoot) {
                InfoNote(
                    icon = Icons.Outlined.Info,
                    text = stringResource(R.string.home_boot_dropped),
                )
                TextButton(onClick = viewModel::acknowledgeBootDrop) {
                    Text(stringResource(R.string.home_boot_dropped_dismiss))
                }
                VerticalSpacer(12)
            }
            InfoNote(
                icon = Icons.Outlined.PauseCircle,
                text = stringResource(R.string.home_limits),
            )

            VerticalSpacer(24)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveRuleChips(
    activeRules: List<AppRule>,
    onChipClick: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        activeRules.forEach { rule ->
            AppRuleChip(
                appName = rule.appName,
                frequencyLabel = rule.reminderChipLabel(),
                onClick = { onChipClick(rule.appId) },
            )
        }
    }
}


