package com.nawzblaze.momentwise.monitoring

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nawzblaze.momentwise.MomentwiseApplication
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.data.model.ThemeMode
import com.nawzblaze.momentwise.intervention.InterventionSurface
import com.nawzblaze.momentwise.intervention.PauseRequest
import com.nawzblaze.momentwise.ui.screens.PauseScreen
import com.nawzblaze.momentwise.ui.theme.MomentwiseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Visible pause hosted in a [WindowManager] overlay.
 *
 * This is used only when the user has granted display-over-other-apps.
 * The window is the pause itself: a middle card and a visible scrim.
 * It is not invisible, it is not click-through, and it is not kept up to
 * hold a privilege. Touches on the scrim are consumed here.
 *
 * A background activity start is not required for this path. If the window
 * cannot be added, [show] returns false and the caller uses the notification.
 */
object PauseOverlay {

    const val WINDOW_TITLE = InterventionSurface.WINDOW_TITLE

    private var host: OverlayHost? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun show(context: Context, request: PauseRequest, themeModeName: String, visitId: Long = 0L): Boolean {
        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(WindowManager::class.java) ?: return false
        dismiss(appContext)
        return try {
            val themed = android.view.ContextThemeWrapper(appContext, R.style.Theme_Momentwise)
            val overlay = OverlayHost(themed, visitId)
            overlay.resume()
            overlay.contentDescription = WINDOW_TITLE
            overlay.isClickable = true
            overlay.setOnTouchListener { _, _ -> true }
            // Compose resolves the owner from this window root, not only the child.
            overlay.setViewTreeLifecycleOwner(overlay)
            overlay.setViewTreeSavedStateRegistryOwner(overlay)
            overlay.setViewTreeViewModelStoreOwner(overlay)
            val composeView = ComposeView(themed).apply {
                setViewTreeLifecycleOwner(overlay)
                setViewTreeSavedStateRegistryOwner(overlay)
                setViewTreeViewModelStoreOwner(overlay)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                isClickable = true
            }
            overlay.addView(composeView)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Focusable so Compose buttons receive touches. That can pause the
                // app underneath. VisitMachine treats the resulting usage events
                // as overlay noise, not as a leave and not as a new visit.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
                setTitle(WINDOW_TITLE)
                if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            windowManager.addView(overlay, params)
            host = overlay
            composeView.setContent {
                val mode = ThemeMode.fromStorageKey(themeModeName)
                MomentwiseTheme(themeMode = mode) {
                    PauseScreen(
                        request = request,
                        onQuit = { perform(appContext) { it.quit() } },
                        onSecondary = {
                            perform(appContext) { controller ->
                                if (request.kind == PauseRequest.Kind.OPENING) {
                                    controller.continueIntoApp(request.packageName)
                                } else {
                                    controller.snooze(request)
                                }
                            }
                        },
                        onDismissReminder = { perform(appContext) { it.dismissForToday(request) } },
                    )
                }
            }
            DebugNotes.append(appContext, "intervention=overlay shown")
            true
        } catch (error: Exception) {
            dismiss(appContext)
            DebugNotes.append(
                appContext,
                "intervention=overlay-failed ${error.javaClass.simpleName}",
            )
            false
        }
    }

    fun dismiss(context: Context) {
        val windowManager = context.applicationContext.getSystemService(WindowManager::class.java)
        val current = host ?: return
        host = null
        if (current.isAttachedToWindow && windowManager != null) {
            runCatching { windowManager.removeViewImmediate(current) }
        }
        current.destroy()
    }

    /** The system removed the window. Our own [dismiss] nulls [host] first, so it does not land here. */
    internal fun systemRemoved(removed: OverlayHost) {
        val current = host ?: return
        if (current !== removed) return
        host = null
        val visitId = removed.visitId
        current.destroy()
        PausePresenter.noteCleared(PauseDismissReason.WITHOUT_CHOICE, visitId)
    }

    private fun perform(context: Context, block: suspend (InterventionController) -> Unit) {
        val app = context.applicationContext as MomentwiseApplication
        val controller = InterventionController(
            context = app,
            rules = app.container.appRuleRepository,
            sessions = app.container.guardSessions,
        )
        scope.launch { block(controller) }
    }
}

private class OverlayHost(context: Context, val visitId: Long) :
    FrameLayout(context),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private var sawAttach = false
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sawAttach = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // addView can detach before the window is really up. That is not the
        // user closing the card, and it must not start a reshow loop.
        if (!sawAttach) return
        PauseOverlay.systemRemoved(this)
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
        viewModelStore.clear()
    }
}
