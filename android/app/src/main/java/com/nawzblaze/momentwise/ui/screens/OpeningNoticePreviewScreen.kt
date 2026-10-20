package com.nawzblaze.momentwise.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.nawzblaze.momentwise.intervention.InterventionSurface
import com.nawzblaze.momentwise.intervention.PauseRequest
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors
import kotlinx.coroutines.launch

private enum class OpeningPreviewResult { CHOOSING, QUIT, CONTINUE }

/**
 * Screen 5 — Opening-notice preview.
 * Renders the saved rule for one app. It does not detect or cover another app;
 * the disclaimer says so. Quit is described as returning home, never as a force-stop.
 */
@Composable
fun OpeningNoticePreviewScreen(
    viewModel: MomentwiseViewModel,
    appId: String,
    snackbarHostState: SnackbarHostState,
    onExit: () -> Unit,
    onEditRule: () -> Unit,
    onQuitResultBack: () -> Unit,
    onContinueResultBackHome: () -> Unit,
    onTestCheckIn: () -> Unit,
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val rule = rules?.find { it.appId == appId }
    val colors = LocalMomentwiseColors.current
    val scope = rememberCoroutineScope()
    val quitMessage = stringResource(R.string.toast_preview_quit)
    val continueMessage = stringResource(R.string.toast_preview_continue)
    var result by rememberSaveable { mutableStateOf(OpeningPreviewResult.CHOOSING) }

    if (rules == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    if (rule == null) {
        LaunchedEffect(appId) { onExit() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_back))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.preview_opening_testing) + " " + rule.appName,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onEditRule) {
                Text(stringResource(R.string.preview_opening_edit_rule), color = colors.accentText)
            }
        }

        if (result == OpeningPreviewResult.CHOOSING) {
            Text(
                text = stringResource(R.string.preview_opening_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Box(Modifier.fillMaxWidth().height(560.dp)) {
                PauseScreen(
                    request = previewPauseRequest(rule),
                    onQuit = {
                        result = OpeningPreviewResult.QUIT
                        scope.launch { snackbarHostState.showSnackbar(quitMessage) }
                    },
                    onSecondary = {
                        result = OpeningPreviewResult.CONTINUE
                        scope.launch { snackbarHostState.showSnackbar(continueMessage) }
                    },
                    onDismissReminder = {},
                )
            }
        } else {
        VerticalSpacer(28)

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
            Box(
                Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .border(1.dp, colors.border, CircleShape)
            )
            Box(
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceSubtle)
            )
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        }
        VerticalSpacer(10)
        Text(
            text = stringResource(R.string.preview_opening_breath),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textTertiary,
        )

        VerticalSpacer(28)
        Text(
            text = stringResource(R.string.pause_notification_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.accentText,
            textAlign = TextAlign.Center,
        )
        VerticalSpacer(8)
        Text(
            text = rule.openingNotice,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        VerticalSpacer(6)
        Text(
            text = stringResource(R.string.secondary_line),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        VerticalSpacer(28)

        when (result) {
            OpeningPreviewResult.CHOOSING -> Unit
            OpeningPreviewResult.QUIT -> {
                ResultCard(
                    icon = Icons.Filled.Check,
                    title = stringResource(R.string.preview_opening_quit_result_title),
                    body = stringResource(R.string.preview_quit_body, rule.appName),
                )
                VerticalSpacer(16)
                Button(
                    onClick = onQuitResultBack,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.preview_opening_quit_result_back))
                }
            }
            OpeningPreviewResult.CONTINUE -> {
                val minutes = rule.reminderFrequency.minutesOrNull()
                val body = if (minutes == null) {
                    stringResource(R.string.preview_continue_without_reminder, rule.appName)
                } else {
                    stringResource(R.string.preview_continue_with_reminder, rule.appName, minutes)
                }
                ResultCard(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.preview_opening_continue_result_title),
                    body = body,
                )
                VerticalSpacer(16)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onTestCheckIn,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.preview_opening_test_checkin))
                    }
                    Button(
                        onClick = onContinueResultBackHome,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.preview_opening_back_home))
                    }
                }
            }
        }

        VerticalSpacer(24)
        Text(
            text = stringResource(R.string.preview_opening_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        VerticalSpacer(16)
        }
    }
}

private fun previewPauseRequest(rule: com.nawzblaze.momentwise.data.model.AppRule) = PauseRequest(
    packageName = rule.appId,
    appName = rule.appName,
    kind = PauseRequest.Kind.OPENING,
    notice = rule.openingNotice,
    heading = InterventionSurface.OPENING_HEADING,
    primaryLabel = rule.quitOption.label,
    secondaryLabel = rule.continueOption.label,
    primarySupport = InterventionSurface.LEAVE_SUPPORT,
    secondarySupport = InterventionSurface.continueSupport(rule.reminderFrequency.minutesOrNull()),
)

@Composable
private fun ResultCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    val colors = LocalMomentwiseColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
            .padding(20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.statusGreenBackground),
        ) {
            Icon(icon, contentDescription = null, tint = colors.statusGreen)
        }
        VerticalSpacer(12)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        VerticalSpacer(6)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
