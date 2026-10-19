package com.nawzblaze.momentwise.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nawzblaze.momentwise.data.model.FrequencySelection
import com.nawzblaze.momentwise.data.model.ThemeMode
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors

/*
 * Reusable Momentwise components. Styling comes exclusively from the shared
 * theme tokens (MaterialTheme.colorScheme + LocalMomentwiseColors) — no
 * hardcoded colours, radii, or spacing values outside this design system.
 */

private val CardShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(999.dp)
private val FieldShape = RoundedCornerShape(10.dp)

/** Standard screen top bar: optional back arrow, title, trailing actions. */
@Composable
fun MomentwiseTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalMomentwiseColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .height(56.dp)
                .padding(horizontal = 8.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack == null) 8.dp else 4.dp),
            )
            actions()
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderSubtle)
        )
    }
}

/** Quiet surface card with a hairline border — the primary content container. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalMomentwiseColors.current
    Surface(
        shape = CardShape,
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Uppercase micro-label used above cards and groups. */
@Composable
fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalMomentwiseColors.current.textMuted,
        modifier = modifier,
    )
}

/** Small green "active" status dot. */
@Composable
fun ActiveDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(LocalMomentwiseColors.current.statusGreen)
    )
}

/**
 * Momentwise text field: label above, hairline border, inline help and error text.
 * Mirrors the prototype's form-field styling and validation messages.
 */
@Composable
fun MomentwiseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    helpText: String? = null,
    errorText: String? = null,
    maxLength: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = LocalMomentwiseColors.current
    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                if (maxLength == null || next.length <= maxLength) onValueChange(next)
            },
            placeholder = { Text(placeholder, color = colors.textMuted) },
            singleLine = true,
            isError = errorText != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = FieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.borderHover,
                unfocusedBorderColor = colors.border,
                focusedContainerColor = colors.surfaceCard,
                unfocusedContainerColor = colors.surfaceCard,
                cursorColor = colors.accent,
                errorBorderColor = MaterialTheme.colorScheme.error,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else if (helpText != null) {
            Text(
                text = helpText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Reminder-frequency selector: Never / 5 / 10 / 15 / 20 / Custom chips,
 * matching the approved option set exactly.
 */
@Composable
fun FrequencyChipRow(
    selected: FrequencySelection,
    onSelect: (FrequencySelection) -> Unit,
    modifier: Modifier = Modifier,
    neverLabel: String,
    customLabel: String,
) {
    val colors = LocalMomentwiseColors.current
    val selections = listOf(
        FrequencySelection.NEVER to neverLabel,
        FrequencySelection.MINUTES_5 to "5 min",
        FrequencySelection.MINUTES_10 to "10 min",
        FrequencySelection.MINUTES_15 to "15 min",
        FrequencySelection.MINUTES_20 to "20 min",
        FrequencySelection.CUSTOM to customLabel,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        selections.forEach { (selection, label) ->
            FilterChip(
                selected = selection == selected,
                onClick = { onSelect(selection) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                shape = PillShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceCard,
                    labelColor = colors.textSecondary,
                    selectedContainerColor = colors.accentSubtle,
                    selectedLabelColor = colors.accentText,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selection == selected,
                    borderColor = colors.border,
                    selectedBorderColor = colors.accentText,
                ),
            )
        }
    }
}

/** Three-way System / Light / Dark selector (segmented control). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSegmentedControl(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    systemLabel: String,
    lightLabel: String,
    darkLabel: String,
) {
    val colors = LocalMomentwiseColors.current
    val options = listOf(
        ThemeMode.SYSTEM to systemLabel,
        ThemeMode.LIGHT to lightLabel,
        ThemeMode.DARK to darkLabel,
    )
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.accentSubtle,
                    activeContentColor = colors.accentText,
                    inactiveContainerColor = colors.surfaceCard,
                    inactiveContentColor = colors.textSecondary,
                ),
            ) { Text(label, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** A guarded-app summary chip on the Home screen. */
@Composable
fun AppRuleChip(
    appName: String,
    frequencyLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMomentwiseColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(PillShape)
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        ActiveDot()
        Spacer(Modifier.width(8.dp))
        Text(
            appName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(frequencyLabel, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
    }
}

/** Tappable row with a leading icon badge — used by Home test actions and app rows. */
@Composable
fun IconActionRow(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = LocalMomentwiseColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderSubtle, FieldShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(FieldShape)
                .background(colors.accentSubtle),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Rule-enabled switch styled with the brand colours. */
@Composable
fun RuleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMomentwiseColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = colors.statusGreen,
            checkedBorderColor = colors.statusGreen,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = colors.surfaceSubtle,
            uncheckedBorderColor = colors.border,
        ),
    )
}

/** Quiet info note with a leading icon. */
@Composable
fun InfoNote(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMomentwiseColors.current
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(colors.surfaceSubtle)
            .padding(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
fun VerticalSpacer(heightDp: Int = 16) {
    Spacer(Modifier.height(heightDp.dp))
}
