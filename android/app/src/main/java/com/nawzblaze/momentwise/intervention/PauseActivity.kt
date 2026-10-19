package com.nawzblaze.momentwise.intervention

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nawzblaze.momentwise.MomentwiseApplication
import com.nawzblaze.momentwise.data.model.ThemeMode
import com.nawzblaze.momentwise.monitoring.InterventionController
import com.nawzblaze.momentwise.monitoring.PausePresenter
import com.nawzblaze.momentwise.ui.screens.PauseScreen
import com.nawzblaze.momentwise.ui.theme.MomentwiseTheme
import kotlinx.coroutines.launch

/**
 * The pause as its own task, used when the user taps the notification and as
 * a fallback if the overlay cannot be added. It is not inserted into the
 * other app's task.
 */
class PauseActivity : ComponentActivity() {

    private var request by mutableStateOf<PauseRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PausePresenter.dismiss(this)
        request = fromIntent(intent)
        if (request == null) {
            finish()
            return
        }
        val app = application as MomentwiseApplication
        setContent {
            val theme by app.container.appSettingsRepository.themeMode
                .collectAsStateWithLifecycle(ThemeMode.DEFAULT)
            val current = request
            MomentwiseTheme(themeMode = theme) {
                if (current != null) {
                    PauseScreen(
                        request = current,
                        onQuit = { act { it.quit() } },
                        onSecondary = {
                            act { controller ->
                                if (current.kind == PauseRequest.Kind.OPENING) {
                                    controller.continueIntoApp()
                                } else {
                                    controller.snooze(current)
                                }
                            }
                        },
                        onDismissReminder = { act { it.dismissForToday(current) } },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request = fromIntent(intent)
    }

    private fun act(block: suspend (InterventionController) -> Unit) {
        val app = application as MomentwiseApplication
        val controller = InterventionController(
            context = app,
            rules = app.container.appRuleRepository,
            sessions = app.container.guardSessions,
        )
        lifecycleScope.launch {
            block(controller)
            finish()
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "pause_package"
        private const val EXTRA_NAME = "pause_name"
        private const val EXTRA_KIND = "pause_kind"
        private const val EXTRA_NOTICE = "pause_notice"
        private const val EXTRA_PRIMARY = "pause_primary"
        private const val EXTRA_SECONDARY = "pause_secondary"
        private const val EXTRA_TERTIARY = "pause_tertiary"
        private const val EXTRA_SNOOZE = "pause_snooze"
        private const val EXTRA_FROM_NOTIFICATION = "pause_from_notification"

        fun intent(context: Context, request: PauseRequest): Intent =
            Intent(context, PauseActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_PACKAGE, request.packageName)
                putExtra(EXTRA_NAME, request.appName)
                putExtra(EXTRA_KIND, request.kind.name)
                putExtra(EXTRA_NOTICE, request.notice)
                putExtra(EXTRA_PRIMARY, request.primaryLabel)
                putExtra(EXTRA_SECONDARY, request.secondaryLabel)
                putExtra(EXTRA_TERTIARY, request.tertiaryLabel)
                putExtra(EXTRA_SNOOZE, request.snoozeMinutes)
                putExtra(EXTRA_FROM_NOTIFICATION, request.fromNotification)
            }

        fun fromIntent(intent: Intent?): PauseRequest? {
            val packageName = intent?.getStringExtra(EXTRA_PACKAGE) ?: return null
            val kind = runCatching { PauseRequest.Kind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }
                .getOrNull() ?: return null
            return PauseRequest(
                packageName = packageName,
                appName = intent.getStringExtra(EXTRA_NAME) ?: packageName,
                kind = kind,
                notice = intent.getStringExtra(EXTRA_NOTICE).orEmpty(),
                primaryLabel = intent.getStringExtra(EXTRA_PRIMARY).orEmpty(),
                secondaryLabel = intent.getStringExtra(EXTRA_SECONDARY).orEmpty(),
                tertiaryLabel = intent.getStringExtra(EXTRA_TERTIARY),
                snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE, 10),
                fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false),
            )
        }
    }
}
