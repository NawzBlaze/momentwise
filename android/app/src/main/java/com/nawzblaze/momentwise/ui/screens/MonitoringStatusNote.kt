package com.nawzblaze.momentwise.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nawzblaze.momentwise.MomentwiseApplication
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.monitoring.GrantStatus
import com.nawzblaze.momentwise.monitoring.MonitorHealth
import com.nawzblaze.momentwise.monitoring.MonitorPresence
import com.nawzblaze.momentwise.monitoring.MonitoringCoordinator
import com.nawzblaze.momentwise.ui.components.InfoNote
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberMonitorHealth(shouldRun: Boolean): MonitorHealth {
    val context = LocalContext.current
    var health by remember(shouldRun) {
        mutableStateOf(MonitorPresence.health(context, shouldRun, System.currentTimeMillis()))
    }
    LaunchedEffect(shouldRun) {
        while (true) {
            health = MonitorPresence.health(context, shouldRun, System.currentTimeMillis())
            delay(2_000)
        }
    }
    return health
}

fun monitoringShouldRun(enabledRuleCount: Int, paused: Boolean, grants: GrantStatus): Boolean =
    enabledRuleCount > 0 && !paused && grants.canMonitor

/**
 * Shown only when rules and grants say monitoring should be on, and the
 * service heartbeat says it is not. Does not claim a phone was measured.
 */
@Composable
fun MonitoringStoppedNote(shouldRun: Boolean, health: MonitorHealth) {
    if (!shouldRun || health != MonitorHealth.STOPPED) return
    val context = LocalContext.current
    InfoNote(icon = Icons.Outlined.Info, text = stringResource(R.string.home_monitor_stopped))
    VerticalSpacer(10)
    Button(
        onClick = {
            val app = context.applicationContext as MomentwiseApplication
            app.applicationScope.launch { MonitoringCoordinator.sync(app) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.home_monitor_resume))
    }
}
