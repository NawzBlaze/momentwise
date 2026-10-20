package com.nawzblaze.momentwise.ui.screens

import android.app.AppOpsManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nawzblaze.momentwise.monitoring.GrantChecks
import com.nawzblaze.momentwise.monitoring.GrantStatus
import kotlinx.coroutines.delay

/**
 * Screens ask this after a permission dialog returns. Opening Settings is not
 * a grant; the next read still comes from Android.
 */
internal object GrantRefresh {
    var tick by mutableIntStateOf(0)
        private set

    fun bump() {
        tick++
    }
}

/**
 * Re-reads Usage Access, overlay, and notifications when the screen returns,
 * including a short delay for phones that update AppOps after ON_RESUME.
 * A button tap does not mark a grant allowed.
 */
@Composable
fun rememberGrantStatus(): GrantStatus {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val external = GrantRefresh.tick
    var resumeTick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf(GrantChecks.read(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, external) {
        val read = {
            status = GrantChecks.read(context)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                read()
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        read()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(appContext) {
        val appOps = appContext.getSystemService(AppOpsManager::class.java)
        val listener = AppOpsManager.OnOpChangedListener { _, _ ->
            Handler(Looper.getMainLooper()).post {
                status = GrantChecks.read(appContext)
            }
        }
        appOps?.startWatchingMode(AppOpsManager.OPSTR_GET_USAGE_STATS, appContext.packageName, listener)
        appOps?.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, appContext.packageName, listener)
        onDispose {
            appOps?.stopWatchingMode(listener)
        }
    }

    LaunchedEffect(resumeTick, external) {
        delay(400)
        status = GrantChecks.read(context)
        delay(900)
        status = GrantChecks.read(context)
    }
    return status
}
