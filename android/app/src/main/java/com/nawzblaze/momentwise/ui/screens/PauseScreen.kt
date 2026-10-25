package com.nawzblaze.momentwise.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.intervention.InterventionSurface
import com.nawzblaze.momentwise.intervention.PauseRequest

private val OverlayScrim = Color(0xB808100D)
private val PanelTop = Color(0xFF111A15)
private val PanelBottom = Color(0xFF070D0A)
private val PanelBorder = Color(0xFF2B3A31)
private val Cream = Color(0xFFF2EBDD)
private val Ink = Color(0xFF101712)
private val TextMain = Color(0xFFF4F5F0)
private val TextMuted = Color(0xFFA5B0A8)
private val SunriseGold = Color(0xFFE6B15A)
private val RowSurface = Color(0xFF111814)
private val RowBorder = Color(0xFF29372E)

/**
 * Premium Momentwise pause surfaces. The opening is a deliberate question;
 * the reminder is a quieter check-in. The overlay remains visibly branded and
 * never imitates the target app's own UI.
 */
@Composable
fun PauseScreen(
    request: PauseRequest,
    onQuit: () -> Unit,
    onSecondary: () -> Unit,
    onDismissReminder: () -> Unit,
) {
    val reminder = request.kind == PauseRequest.Kind.REMINDER
    val scrimDescription = stringResource(R.string.pause_scrim_description, request.appName)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (reminder) PanelBottom else OverlayScrim)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .semantics { contentDescription = scrimDescription },
        contentAlignment = if (reminder) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        val surfaceModifier = if (reminder) {
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(PanelTop, PanelBottom)))
                .verticalScroll(rememberScrollState())
                .semantics { contentDescription = InterventionSurface.WINDOW_TITLE }
        } else {
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .heightIn(max = 760.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(PanelTop, PanelBottom)))
                .border(1.dp, PanelBorder, RoundedCornerShape(30.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp)
                .semantics { contentDescription = InterventionSurface.WINDOW_TITLE }
        }
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = surfaceModifier,
        ) {
            if (reminder) {
                ReminderContent(
                    request = request,
                    onQuit = onQuit,
                    onSecondary = onSecondary,
                    onDismissReminder = onDismissReminder,
                )
            } else {
                OpeningDragHandle()
                BrandHeader(showTagline = false)
                OpeningContent(
                    request = request,
                    onQuit = onQuit,
                    onSecondary = onSecondary,
                    onDismissReminder = onDismissReminder,
                )
            }
        }
    }
}

@Composable
private fun OpeningDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF8A9187)),
        )
    }
}

@Composable
private fun BrandHeader(showTagline: Boolean, onClose: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Cream),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = InterventionSurface.WORDMARK,
                color = TextMain,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = (-0.15).sp,
            )
            if (showTagline) {
                Text(
                    text = "Make room for what matters.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(if (showTagline) 44.dp else 40.dp)
                .clip(CircleShape)
                .background(Color(0xFF273128))
                .then(
                    if (showTagline && onClose != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (showTagline) Icons.Outlined.Close else Icons.Outlined.MoreHoriz,
                contentDescription = if (showTagline) "Dismiss reminder" else null,
                tint = Cream,
                modifier = Modifier.size(if (showTagline) 23.dp else 22.dp),
            )
        }
    }
}

@Composable
private fun OpeningContent(
    request: PauseRequest,
    onQuit: () -> Unit,
    onSecondary: () -> Unit,
    onDismissReminder: () -> Unit,
) {
    Spacer(Modifier.height(30.dp))
    Text(
        text = "Before You Dive In",
        color = Cream,
        fontFamily = FontFamily.Serif,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.85).sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = request.notice.ifBlank { InterventionSurface.OPENING_SUPPORT },
        color = TextMuted,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(24.dp))
    OpeningAction(
        label = "Open Intentionally",
        support = "Dismiss for this session",
        emphasized = true,
        icon = Icons.Outlined.RadioButtonChecked,
        onClick = onSecondary,
    )
    Spacer(Modifier.height(10.dp))
    if (request.tertiaryLabel != null) {
        Spacer(Modifier.height(10.dp))
        OpeningAction(
            label = "Remind Me Later",
            support = "Snooze the reminder",
            emphasized = false,
            icon = Icons.Outlined.Schedule,
            onClick = onDismissReminder,
        )
    }
    Spacer(Modifier.height(10.dp))
    OpeningAction(
        label = "Leave App",
        support = "Return to your home screen.",
        emphasized = false,
        icon = Icons.Outlined.Close,
        onClick = onQuit,
    )
    if (request.fromNotification) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.pause_from_notification),
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ReminderContent(
    request: PauseRequest,
    onQuit: () -> Unit,
    onSecondary: () -> Unit,
    onDismissReminder: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(286.dp),
    ) {
        SunriseLandscape(Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x55060C08), Color.Transparent, Color(0xD9070D09)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            BrandHeader(showTagline = true, onClose = onSecondary)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = "GENTLE REMINDER",
        color = SunriseGold,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.1.sp,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = request.notice.ifBlank { "You've been here for a while." },
        color = Cream,
        fontFamily = FontFamily.Serif,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.7).sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(9.dp))
    Text(
        text = "A small pause can help you feel more in control of your time.",
        color = TextMuted,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )
    Spacer(Modifier.height(20.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ReminderStat(
            value = request.usedMinutes?.let { "$it min" } ?: "—",
            label = "Time this visit",
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Timelapse,
        )
        ReminderStat(
            value = request.reminderIntervalMinutes?.let { "$it min" } ?: "Off",
            label = "Check-in interval",
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Schedule,
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101813))
            .border(1.dp, RowBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 17.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Spa,
            contentDescription = null,
            tint = Cream,
            modifier = Modifier.size(27.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = "Come back with a clearer mind.",
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "You can always open it again.",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    PrimaryOverlayAction(
        label = request.primaryLabel,
        support = null,
        onClick = onQuit,
    )
    Spacer(Modifier.height(9.dp))
    SecondaryOverlayAction(
        label = request.secondaryLabel,
        support = null,
        onClick = onSecondary,
    )
    if (request.tertiaryLabel != null) {
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = onDismissReminder,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp),
        ) {
            Text(
                text = request.tertiaryLabel,
                color = TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
    if (request.fromNotification) {
        Text(
            text = stringResource(R.string.pause_from_notification),
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
    }
}

@Composable
private fun OpeningAction(
    label: String,
    support: String,
    emphasized: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val background = if (emphasized) Cream else RowSurface
    val foreground = if (emphasized) Ink else TextMain
    val secondary = if (emphasized) Color(0xFF4F5A50) else TextMuted
    val border = if (emphasized) Cream else RowBorder
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(21.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = 82.dp)
            .padding(horizontal = 13.dp, vertical = 13.dp)
            .semantics {
                contentDescription = if (support.isBlank()) label else label + ". " + support
            },
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (emphasized) Ink else Color(0xFF242D27)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (emphasized) Cream else TextMain,
                modifier = Modifier.size(23.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = foreground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = support,
                color = secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.ArrowForward,
            contentDescription = null,
            tint = secondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ReminderStat(
    value: String,
    label: String,
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(17.dp))
            .background(Color(0xFF101813))
            .border(1.dp, RowBorder, RoundedCornerShape(17.dp))
            .padding(horizontal = 11.dp, vertical = 15.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF202A23)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Cream, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                text = value,
                color = TextMain,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun PrimaryOverlayAction(
    label: String,
    support: String?,
    onClick: () -> Unit,
) {
    val description = if (support.isNullOrBlank()) label else label + ". " + support
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Cream)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 15.dp)
            .semantics { contentDescription = description },
    ) {
        Spacer(Modifier.weight(1f))
        Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Outlined.ArrowForward,
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SecondaryOverlayAction(
    label: String,
    support: String?,
    onClick: () -> Unit,
) {
    val description = if (support.isNullOrBlank()) label else label + ". " + support
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color(0xFF0B120E))
            .border(1.dp, Color(0xFF354238), RoundedCornerShape(17.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 13.dp)
            .semantics { contentDescription = description },
    ) {
        Text(label, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        if (!support.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(support, color = TextMuted, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SunriseLandscape(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF263326), Color(0xFF101A14), Color(0xFF080F0B)),
                startY = 0f,
                endY = h,
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x66F4BF6C), Color(0x1AF4BF6C), Color.Transparent),
                center = Offset(w * 0.73f, h * 0.40f),
                radius = h * 0.52f,
            ),
            radius = h * 0.52f,
            center = Offset(w * 0.73f, h * 0.40f),
        )
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFD98A), Color(0xFFF0A94A)),
                startY = h * 0.14f,
                endY = h * 0.72f,
            ),
            radius = h * 0.29f,
            center = Offset(w * 0.72f, h * 0.46f),
        )
        val farHill = Path().apply {
            moveTo(0f, h * 0.62f)
            lineTo(w * 0.10f, h * 0.52f)
            lineTo(w * 0.28f, h * 0.57f)
            lineTo(w * 0.43f, h * 0.37f)
            lineTo(w * 0.58f, h * 0.58f)
            lineTo(w * 0.76f, h * 0.44f)
            lineTo(w, h * 0.54f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(farHill, Color(0xFF152219))
        val nearHill = Path().apply {
            moveTo(0f, h * 0.74f)
            lineTo(w * 0.20f, h * 0.61f)
            lineTo(w * 0.41f, h * 0.79f)
            lineTo(w * 0.65f, h * 0.59f)
            lineTo(w * 0.84f, h * 0.69f)
            lineTo(w, h * 0.60f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(nearHill, Color(0xFF0D1711))
        val frontHill = Path().apply {
            moveTo(0f, h * 0.88f)
            lineTo(w * 0.18f, h * 0.79f)
            lineTo(w * 0.37f, h * 0.88f)
            lineTo(w * 0.61f, h * 0.75f)
            lineTo(w * 0.79f, h * 0.84f)
            lineTo(w, h * 0.72f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(frontHill, Color(0xFF070D09))
        val ridge = Path().apply {
            moveTo(0f, h * 0.74f)
            lineTo(w * 0.20f, h * 0.61f)
            lineTo(w * 0.41f, h * 0.79f)
            lineTo(w * 0.65f, h * 0.59f)
            lineTo(w * 0.84f, h * 0.69f)
            lineTo(w, h * 0.60f)
        }
        drawPath(ridge, SunriseGold.copy(alpha = 0.62f), style = Stroke(width = 1.dp.toPx()))

        // A quiet foreground branch anchors the scene, echoing the botanical
        // detail in the approved artwork without adding a raster dependency.
        val branch = Path().apply {
            moveTo(w * 0.91f, h * 1.04f)
            cubicTo(w * 0.88f, h * 0.83f, w * 0.91f, h * 0.63f, w * 0.98f, h * 0.34f)
        }
        drawPath(branch, Color(0xFF08100B), style = Stroke(width = 3.2.dp.toPx()))
        val leaves = listOf(
            Path().apply {
                moveTo(w * 0.91f, h * 0.80f)
                cubicTo(w * 0.76f, h * 0.74f, w * 0.75f, h * 0.62f, w * 0.80f, h * 0.59f)
                cubicTo(w * 0.88f, h * 0.63f, w * 0.91f, h * 0.71f, w * 0.91f, h * 0.80f)
                close()
            },
            Path().apply {
                moveTo(w * 0.93f, h * 0.68f)
                cubicTo(w * 0.90f, h * 0.53f, w * 0.96f, h * 0.47f, w * 0.99f, h * 0.45f)
                cubicTo(w * 1.02f, h * 0.56f, w * 0.98f, h * 0.64f, w * 0.93f, h * 0.68f)
                close()
            },
            Path().apply {
                moveTo(w * 0.90f, h * 0.90f)
                cubicTo(w * 0.78f, h * 0.88f, w * 0.73f, h * 0.80f, w * 0.74f, h * 0.76f)
                cubicTo(w * 0.84f, h * 0.77f, w * 0.89f, h * 0.83f, w * 0.90f, h * 0.90f)
                close()
            },
        )
        leaves.forEach { drawPath(it, Color(0xFF0A120D)) }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x66070D09)),
                startY = h * 0.42f,
                endY = h,
            ),
            topLeft = Offset.Zero,
            size = size,
        )
    }
}
