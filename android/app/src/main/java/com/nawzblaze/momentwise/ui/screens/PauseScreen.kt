package com.nawzblaze.momentwise.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.intervention.InterventionSurface
import com.nawzblaze.momentwise.intervention.PauseRequest
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors

/**
 * Opening and reminder are different moments. The opening asks why this visit
 * started. The reminder asks whether to keep going. Same brand, same actions,
 * different heading treatment. No animation. The scrim does not pass touches.
 *
 * Leave is a return home, never a force-stop. Button labels come from the rule.
 */
@Composable
fun PauseScreen(
    request: PauseRequest,
    onQuit: () -> Unit,
    onSecondary: () -> Unit,
    onDismissReminder: () -> Unit,
) {
    val colors = LocalMomentwiseColors.current
    val scrimDescription = stringResource(R.string.pause_scrim_description, request.appName)
    val reminder = request.kind == PauseRequest.Kind.REMINDER
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x6614291E))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .semantics { contentDescription = scrimDescription },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(28.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 26.dp)
                    .semantics { contentDescription = InterventionSurface.WINDOW_TITLE },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = InterventionSurface.WORDMARK,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                VerticalSpacer(18)
                Text(
                    text = stringResource(
                        if (reminder) R.string.pause_reminder_eyebrow else R.string.pause_opening_eyebrow,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentText,
                    fontWeight = FontWeight.SemiBold,
                )
                VerticalSpacer(8)
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(2.dp)
                        .background(colors.accent, RoundedCornerShape(2.dp)),
                )
                VerticalSpacer(14)
                Text(
                    text = request.heading,
                    modifier = Modifier.semantics { heading() },
                    style = if (reminder) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                if (request.notice.isNotBlank() && request.notice != request.heading) {
                    VerticalSpacer(8)
                    Text(
                        text = request.notice,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (request.fromNotification) {
                    VerticalSpacer(8)
                    Text(
                        text = stringResource(R.string.pause_from_notification),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                VerticalSpacer(22)
                PauseChoiceButton(
                    label = request.primaryLabel,
                    support = request.primarySupport,
                    emphasized = true,
                    onClick = onQuit,
                )
                VerticalSpacer(10)
                PauseChoiceButton(
                    label = request.secondaryLabel,
                    support = request.secondarySupport,
                    emphasized = false,
                    onClick = onSecondary,
                )
                VerticalSpacer(8)
                Text(
                    text = stringResource(R.string.pause_quit_honesty),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
                if (request.tertiaryLabel != null) {
                    VerticalSpacer(4)
                    TextButton(
                        onClick = onDismissReminder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = request.tertiaryLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    Text(
                        text = InterventionSurface.dismissHelp(request.appName),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseChoiceButton(
    label: String,
    support: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMomentwiseColors.current
    val description = if (support.isBlank()) label else "$label. $support"
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (support.isNotBlank()) {
                Text(
                    text = support,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f)
                    } else {
                        colors.textSecondary
                    },
                )
            }
        }
    }
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .semantics { contentDescription = description }
    if (emphasized) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            content = { content() },
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.borderHover),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            content = { content() },
        )
    }
}
