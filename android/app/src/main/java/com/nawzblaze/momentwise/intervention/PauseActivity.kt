package com.nawzblaze.momentwise.intervention

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import com.nawzblaze.momentwise.monitoring.PauseDismissReason
import com.nawzblaze.momentwise.monitoring.PausePresenter
import com.nawzblaze.momentwise.ui.screens.PauseScreen
import com.nawzblaze.momentwise.ui.theme.MomentwiseTheme
import kotlinx.coroutines.launch

/**
 * The pause as its own task. It is opened from the notification, and may also
 * be started when an overlay window could not be added. A start from the
 * service is not proof this activity was shown: background activity starts
 * are restricted, and a notification tap is the reliable path.
 *
 * Back is not Leave and not Continue. The other app is not tappable while
 * this activity is in front. It is not inserted into the other app's task.
 */
class PauseActivity : ComponentActivity() {

    private var request by mutableStateOf<PauseRequest?>(null)
    private var pauseGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // A choice is required. Back must not dismiss or send the user home.
                }
            },
        )
        pauseGeneration = PausePresenter.currentGeneration()
        PausePresenter.handOffToActivity(this)
        PausePresenter.onShown?.invoke()
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
                                    controller.continueIntoApp(current.packageName)
                                } else {
                                    controller.snooze(current)
                                }
                            }
                        },
                        onDismissReminder = { act { controller -> if (current.kind == PauseRequest.Kind.OPENING) controller.snooze(current) else controller.dismissForToday(current) } },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pauseGeneration = PausePresenter.currentGeneration()
        PausePresenter.onShown?.invoke()
        request = fromIntent(intent)
    }

    override fun onDestroy() {
        if (isFinishing && pauseGeneration == PausePresenter.currentGeneration()) {
            PausePresenter.noteCleared(PauseDismissReason.WITHOUT_CHOICE)
        }
        super.onDestroy()
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
        private const val EXTRA_HEADING = "pause_heading"
        private const val EXTRA_PRIMARY = "pause_primary"
        private const val EXTRA_SECONDARY = "pause_secondary"
        private const val EXTRA_TERTIARY = "pause_tertiary"
        private const val EXTRA_SNOOZE = "pause_snooze"
        private const val EXTRA_PRIMARY_SUPPORT = "pause_primary_support"
        private const val EXTRA_SECONDARY_SUPPORT = "pause_secondary_support"
        private const val EXTRA_FROM_NOTIFICATION = "pause_from_notification"
        private const val EXTRA_USED_MINUTES = "pause_used_minutes"
        private const val EXTRA_REMINDER_INTERVAL_MINUTES = "pause_reminder_interval_minutes"

        fun intent(context: Context, request: PauseRequest): Intent =
            Intent(context, PauseActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_PACKAGE, request.packageName)
                putExtra(EXTRA_NAME, request.appName)
                putExtra(EXTRA_KIND, request.kind.name)
                putExtra(EXTRA_NOTICE, request.notice)
                putExtra(EXTRA_HEADING, request.heading)
                putExtra(EXTRA_PRIMARY, request.primaryLabel)
                putExtra(EXTRA_SECONDARY, request.secondaryLabel)
                putExtra(EXTRA_TERTIARY, request.tertiaryLabel)
                putExtra(EXTRA_PRIMARY_SUPPORT, request.primarySupport)
                putExtra(EXTRA_SECONDARY_SUPPORT, request.secondarySupport)
                putExtra(EXTRA_SNOOZE, request.snoozeMinutes)
                putExtra(EXTRA_FROM_NOTIFICATION, request.fromNotification)
                request.usedMinutes?.let { putExtra(EXTRA_USED_MINUTES, it) }
                request.reminderIntervalMinutes?.let { putExtra(EXTRA_REMINDER_INTERVAL_MINUTES, it) }
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
                heading = intent.getStringExtra(EXTRA_HEADING)?.ifBlank { null }
                    ?: if (kind == PauseRequest.Kind.REMINDER) InterventionSurface.REMINDER_HEADING else InterventionSurface.OPENING_HEADING,
                primaryLabel = intent.getStringExtra(EXTRA_PRIMARY).orEmpty(),
                secondaryLabel = intent.getStringExtra(EXTRA_SECONDARY).orEmpty(),
                tertiaryLabel = intent.getStringExtra(EXTRA_TERTIARY),
                primarySupport = intent.getStringExtra(EXTRA_PRIMARY_SUPPORT)
                    ?.ifBlank { null } ?: InterventionSurface.LEAVE_SUPPORT,
                secondarySupport = intent.getStringExtra(EXTRA_SECONDARY_SUPPORT)
                    ?.ifBlank { null }
                    ?: if (kind == PauseRequest.Kind.REMINDER) {
                        InterventionSurface.snoozeSupport(intent.getIntExtra(EXTRA_SNOOZE, 10))
                    } else {
                        ""
                    },
                snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE, 10),
                fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false),
                usedMinutes = if (intent.hasExtra(EXTRA_USED_MINUTES)) {
                    intent.getIntExtra(EXTRA_USED_MINUTES, 0)
                } else {
                    null
                },
                reminderIntervalMinutes = if (intent.hasExtra(EXTRA_REMINDER_INTERVAL_MINUTES)) {
                    intent.getIntExtra(EXTRA_REMINDER_INTERVAL_MINUTES, 0)
                } else {
                    null
                },
            )
        }
    }
}
