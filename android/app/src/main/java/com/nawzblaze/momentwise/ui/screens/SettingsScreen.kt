package com.nawzblaze.momentwise.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nawzblaze.momentwise.MomentwiseViewModel
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.monitoring.GrantIntents
import com.nawzblaze.momentwise.data.model.ThemeMode
import com.nawzblaze.momentwise.ui.components.MomentwiseTopBar
import com.nawzblaze.momentwise.ui.components.RuleSwitch
import com.nawzblaze.momentwise.ui.components.SectionCard
import com.nawzblaze.momentwise.ui.components.ThemeSegmentedControl
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors
import kotlinx.coroutines.launch

/**
 * Screen 7 — Settings.
 * System is the default theme. Light and Dark are explicit overrides.
 * Reset clears saved rules on this device. It does not restore sample apps
 * and does not touch other apps.
 */
@Composable
fun SettingsScreen(
    viewModel: MomentwiseViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val monitoringPaused by viewModel.monitoringPaused.collectAsStateWithLifecycle()
    val colors = LocalMomentwiseColors.current
    val context = LocalContext.current
    val grants = rememberGrantStatus()
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetAllRules()
                    scope.launch {
                        snackbarHostState.showSnackbar("All rules reset to defaults")
                    }
                }) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MomentwiseTopBar(title = stringResource(R.string.settings_title))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            VerticalSpacer(16)
            Text(
                stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textSecondary,
            )
            VerticalSpacer(8)
            SectionCard {
                Text(
                    stringResource(R.string.settings_theme_name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                VerticalSpacer(4)
                Text(
                    stringResource(R.string.settings_theme_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                VerticalSpacer(12)
                ThemeSegmentedControl(
                    selected = themeMode,
                    onSelect = { mode ->
                        viewModel.setThemeMode(mode)
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        }
                        scope.launch { snackbarHostState.showSnackbar("Theme: $label") }
                    },
                    systemLabel = stringResource(R.string.settings_theme_system),
                    lightLabel = stringResource(R.string.settings_theme_light),
                    darkLabel = stringResource(R.string.settings_theme_dark),
                )
            }

            VerticalSpacer(20)
            Text(
                stringResource(R.string.settings_monitoring),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textSecondary,
            )
            VerticalSpacer(8)
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_monitoring_name),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        VerticalSpacer(2)
                        Text(
                            stringResource(R.string.settings_monitoring_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                        VerticalSpacer(4)
                        Text(
                            stringResource(
                                if (monitoringPaused) R.string.settings_monitoring_off else R.string.settings_monitoring_on,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (monitoringPaused) colors.textTertiary else colors.statusGreen,
                        )
                    }
                    RuleSwitch(
                        checked = monitoringPaused,
                        onCheckedChange = viewModel::setMonitoringPaused,
                    )
                }
            }

            VerticalSpacer(20)
            Text(
                stringResource(R.string.settings_access),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textSecondary,
            )
            VerticalSpacer(8)
            SectionCard {
                Text(
                    stringResource(R.string.settings_access_name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                VerticalSpacer(4)
                Text(
                    stringResource(R.string.settings_access_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                VerticalSpacer(12)
                AccessRow(
                    label = stringResource(R.string.disclosure_usage_title),
                    allowed = grants.usageAccess,
                    action = stringResource(R.string.disclosure_usage_action),
                    onAction = { GrantIntents.launch(context, GrantIntents.usageAccess(context)) },
                )
                VerticalSpacer(10)
                AccessRow(
                    label = stringResource(R.string.disclosure_overlay_title),
                    allowed = grants.overlay,
                    action = stringResource(R.string.disclosure_overlay_action),
                    onAction = { GrantIntents.launch(context, GrantIntents.overlay(context)) },
                )
                VerticalSpacer(10)
                AccessRow(
                    label = stringResource(R.string.disclosure_notifications_title),
                    allowed = grants.notifications,
                    action = stringResource(R.string.disclosure_notifications_action),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= 33 && !grants.notifications) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            GrantIntents.launch(context, GrantIntents.notificationSettings(context))
                        }
                    },
                )
            }

            VerticalSpacer(20)
            Text(
                stringResource(R.string.settings_platform),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textSecondary,
            )
            VerticalSpacer(8)
            SectionCard {
                Text(
                    stringResource(R.string.settings_respectful_name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                VerticalSpacer(4)
                Text(
                    stringResource(R.string.settings_respectful_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                VerticalSpacer(16)
                Text(
                    stringResource(R.string.settings_architecture_name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                VerticalSpacer(4)
                Text(
                    stringResource(R.string.settings_architecture_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }

            VerticalSpacer(20)
            Text(
                stringResource(R.string.settings_prototype_controls),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textSecondary,
            )
            VerticalSpacer(8)
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_reset_all_name),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        VerticalSpacer(2)
                        Text(
                            stringResource(R.string.settings_reset_all_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                    OutlinedButton(
                        onClick = { confirmReset = true },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(R.string.settings_reset_all_action))
                    }
                }
                VerticalSpacer(16)
                MetaRow(stringResource(R.string.settings_version_label), stringResource(R.string.settings_version_value))
                VerticalSpacer(8)
                MetaRow(stringResource(R.string.settings_storage_label), stringResource(R.string.settings_storage_value))
            }
            VerticalSpacer(24)
        }
    }
}

@Composable
private fun AccessRow(
    label: String,
    allowed: Boolean,
    action: String,
    onAction: () -> Unit,
) {
    val colors = LocalMomentwiseColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(if (allowed) R.string.grant_allowed else R.string.grant_not_allowed),
                style = MaterialTheme.typography.bodySmall,
                color = if (allowed) colors.statusGreen else colors.textTertiary,
            )
        }
        OutlinedButton(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
            Text(action)
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    val colors = LocalMomentwiseColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
    }
}
