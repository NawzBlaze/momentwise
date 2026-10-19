package com.nawzblaze.momentwise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nawzblaze.momentwise.MomentwiseViewModel
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.data.model.AppRule
import com.nawzblaze.momentwise.ui.components.InfoNote
import com.nawzblaze.momentwise.ui.components.MomentwiseTopBar
import com.nawzblaze.momentwise.ui.components.RuleSwitch
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors
import kotlinx.coroutines.launch

/**
 * Screen 3 — Apps.
 * Lists the guardable apps with per-app toggles; tapping a row opens its own
 * Configure Rule screen. First-time enablement routes straight into
 * configuration, exactly like the approved prototype.
 *
 * Honesty note: until device app detection is wired (later milestone), the list
 * shows the seeded sample catalog and says so explicitly in a banner.
 */
@Composable
fun AppsScreen(
    viewModel: MomentwiseViewModel,
    snackbarHostState: SnackbarHostState,
    onConfigureRule: (String) -> Unit,
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val colors = LocalMomentwiseColors.current
    val scope = rememberCoroutineScope()
    val loadedRules = rules.orEmpty()
    val activeCount = loadedRules.count { it.enabled }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MomentwiseTopBar(
            title = stringResource(R.string.apps_title),
            actions = {
                Text(
                    text = "$activeCount active",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accentSubtle)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
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
            Text(
                text = stringResource(R.string.apps_heading),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            VerticalSpacer(6)
            Text(
                text = stringResource(R.string.apps_subtext),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            VerticalSpacer(14)

            InfoNote(
                icon = Icons.Filled.Info,
                text = stringResource(R.string.apps_sample_banner),
            )

            VerticalSpacer(14)

            if (rules == null) {
                Text(
                    text = "Loading your rules…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                loadedRules.forEach { rule ->
                    val firstEnableMessage = stringResource(R.string.toast_first_enable, rule.appName)
                    val guardedMessage = stringResource(R.string.toast_app_guarded, rule.appName)
                    val inactiveMessage = stringResource(R.string.toast_app_inactive, rule.appName)
                    AppRow(rule = rule, onConfigure = { onConfigureRule(rule.appId) }) { enabled ->
                        viewModel.setRuleEnabled(rule.appId, enabled)
                        val firstTimeConfigure = enabled && !rule.configuredEver
                        if (firstTimeConfigure) onConfigureRule(rule.appId)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                when {
                                    firstTimeConfigure -> firstEnableMessage
                                    enabled -> guardedMessage
                                    else -> inactiveMessage
                                }
                            )
                        }
                    }
                }
            }

            VerticalSpacer(16)

            InfoNote(
                icon = Icons.Filled.Lightbulb,
                text = stringResource(R.string.apps_tip),
            )

            VerticalSpacer(24)
        }
    }
}

@Composable
private fun AppRow(
    rule: AppRule,
    onConfigure: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalMomentwiseColors.current
    val statusText = rule.appsRowStatusLabel(
        notGuarded = stringResource(R.string.apps_status_not_guarded),
        openingOnly = stringResource(R.string.apps_status_opening_only),
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(
                1.dp,
                if (rule.enabled) colors.border else colors.borderSubtle,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onConfigure)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Letter badge stands in for real app icons until device detection is wired.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (rule.enabled) colors.accentSubtle else colors.surfaceSubtle),
        ) {
            Text(
                text = rule.appName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (rule.enabled) colors.accentText else colors.textTertiary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = rule.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surfaceSubtle)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            VerticalSpacer(2)
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (rule.enabled) colors.statusGreen else colors.textTertiary,
            )
        }
        IconButton(onClick = onConfigure, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Configure ${rule.appName}",
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        RuleSwitch(checked = rule.enabled, onCheckedChange = onToggle)
    }
}
