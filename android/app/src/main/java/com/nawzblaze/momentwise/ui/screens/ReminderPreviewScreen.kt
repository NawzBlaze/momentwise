package com.nawzblaze.momentwise.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nawzblaze.momentwise.MomentwiseViewModel
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors
import kotlinx.coroutines.launch

/**
 * Screen 6 — Reminder preview.
 * Shows the saved check-in, or an honest "reminders are off" state when the
 * frequency is Never. Actions are labelled with the user's copy. Nothing here
 * schedules a real reminder.
 */
@Composable
fun ReminderPreviewScreen(
    viewModel: MomentwiseViewModel,
    appId: String,
    snackbarHostState: SnackbarHostState,
    onExitToHome: () -> Unit,
    onEditReminderSchedule: () -> Unit,
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val rule = rules?.find { it.appId == appId }
    val colors = LocalMomentwiseColors.current
    val scope = rememberCoroutineScope()

    if (rules == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    if (rule == null) {
        LaunchedEffect(appId) { onExitToHome() }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceSubtle)
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                text = stringResource(R.string.preview_simulated_background),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
            VerticalSpacer(8)
            Text(
                text = rule.appName,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textTertiary,
            )
            VerticalSpacer(16)
            repeat(3) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.borderSubtle)
                )
                VerticalSpacer(10)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.surfaceCard)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.border)
            )
            VerticalSpacer(16)

            val minutes = rule.reminderFrequency.minutesOrNull()
            if (minutes == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(28.dp),
                    )
                    VerticalSpacer(10)
                    Text(
                        stringResource(R.string.preview_reminder_disabled_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    VerticalSpacer(6)
                    Text(
                        stringResource(R.string.preview_reminder_disabled_body, rule.appName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    VerticalSpacer(16)
                    OutlinedButton(onClick = onEditReminderSchedule, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.preview_reminder_edit_schedule))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = null,
                        modifier = Modifier.width(22.dp).height(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.preview_reminder_brand),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
                VerticalSpacer(14)
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accentSubtle),
                    ) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.accentText)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            rule.reminderNotice,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.preview_reminder_scheduled, minutes, rule.appName),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                }
                VerticalSpacer(18)
                Button(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("App closed. Intentional choice logged.")
                        }
                        onExitToHome()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(rule.reminderActions.quitLabel, fontWeight = FontWeight.SemiBold)
                }
                VerticalSpacer(8)
                OutlinedButton(
                    onClick = {
                        val snooze = rule.reminderActions.snoozeMinutes
                        scope.launch {
                            snackbarHostState.showSnackbar("Reminder snoozed for $snooze minutes.")
                        }
                        onExitToHome()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Text("${rule.reminderActions.snoozeLabel} (+${rule.reminderActions.snoozeMinutes} min)")
                }
                VerticalSpacer(4)
                TextButton(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Reminders paused for ${rule.appName} for the rest of today."
                            )
                        }
                        onExitToHome()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(rule.reminderActions.dismissLabel, color = colors.textSecondary)
                }
            }

            VerticalSpacer(8)
            Text(
                stringResource(R.string.preview_reminder_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
