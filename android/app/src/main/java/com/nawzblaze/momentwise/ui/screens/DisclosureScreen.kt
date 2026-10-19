package com.nawzblaze.momentwise.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nawzblaze.momentwise.MomentwiseViewModel
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.monitoring.GrantIntents
import com.nawzblaze.momentwise.ui.components.MomentwiseTopBar
import com.nawzblaze.momentwise.ui.components.SectionCard
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors

/**
 * Prominent disclosure shown before a rule is enabled. Decline leaves the rule
 * off. Nothing here grants access by itself — each button opens the system screen
 * or the notification dialog, and the user can stop.
 */
@Composable
fun DisclosureScreen(
    viewModel: MomentwiseViewModel,
    appId: String,
    onGuarded: (firstTimeConfigure: Boolean) -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalMomentwiseColors.current
    val grants = rememberGrantStatus()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val entry = catalog?.find { it.rule.appId == appId }
    val appName = entry?.rule?.appName ?: appId
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MomentwiseTopBar(title = stringResource(R.string.disclosure_title), onBack = onDecline)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            VerticalSpacer(16)
            Text(
                text = stringResource(R.string.disclosure_heading, appName),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            VerticalSpacer(8)
            Text(
                text = stringResource(R.string.disclosure_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            VerticalSpacer(16)

            GrantBlock(
                title = stringResource(R.string.disclosure_usage_title),
                body = stringResource(R.string.disclosure_usage_body),
                status = if (grants.usageAccess) {
                    stringResource(R.string.grant_allowed)
                } else {
                    stringResource(R.string.grant_not_allowed)
                },
                action = stringResource(R.string.disclosure_usage_action),
                onAction = { GrantIntents.launch(context, GrantIntents.usageAccess(context)) },
            )
            VerticalSpacer(12)
            GrantBlock(
                title = stringResource(R.string.disclosure_overlay_title),
                body = stringResource(R.string.disclosure_overlay_body),
                status = if (grants.overlay) {
                    stringResource(R.string.grant_allowed)
                } else {
                    stringResource(R.string.grant_notification_fallback)
                },
                action = stringResource(R.string.disclosure_overlay_action),
                onAction = { GrantIntents.launch(context, GrantIntents.overlay(context)) },
            )
            VerticalSpacer(12)
            GrantBlock(
                title = stringResource(R.string.disclosure_notifications_title),
                body = stringResource(R.string.disclosure_notifications_body),
                status = if (grants.notifications) {
                    stringResource(R.string.grant_allowed)
                } else {
                    stringResource(R.string.grant_not_allowed)
                },
                action = stringResource(R.string.disclosure_notifications_action),
                onAction = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        GrantIntents.launch(context, GrantIntents.notificationSettings(context))
                    }
                },
            )

            VerticalSpacer(16)
            Text(
                text = stringResource(R.string.disclosure_limits),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
            VerticalSpacer(20)
            Button(
                onClick = {
                    val rule = entry?.rule ?: return@Button
                    val firstTime = !rule.configuredEver
                    viewModel.acceptGuardingDisclosure()
                    viewModel.setDisplayedRuleEnabled(rule, true)
                    onGuarded(firstTime)
                },
                enabled = grants.canMonitor && entry != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (grants.canMonitor) {
                        stringResource(R.string.disclosure_guard, appName)
                    } else {
                        stringResource(R.string.disclosure_guard_waiting)
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            VerticalSpacer(8)
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.disclosure_decline))
            }
            VerticalSpacer(24)
        }
    }
}

@Composable
private fun GrantBlock(
    title: String,
    body: String,
    status: String,
    action: String,
    onAction: () -> Unit,
) {
    val colors = LocalMomentwiseColors.current
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        VerticalSpacer(4)
        Text(status, style = MaterialTheme.typography.labelLarge, color = colors.accentText)
        VerticalSpacer(8)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        VerticalSpacer(12)
        OutlinedButton(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
            Text(action)
        }
    }
}
