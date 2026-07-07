package com.pact.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.pact.app.ui.BlockWall
import com.pact.app.ui.theme.PactTheme

/**
 * Owns the full-screen lock wall drawn directly by the accessibility service
 * as a TYPE_ACCESSIBILITY_OVERLAY window. Unlike launching an activity from a
 * service (which modern Android silently blocks as a "background activity
 * start"), an accessibility overlay is guaranteed to appear, instantly, over
 * whatever is on screen.
 */
class BlockOverlay(private val service: AccessibilityService) {

    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private val pkgState = mutableStateOf<String?>(null)

    val isShowing: Boolean get() = view != null
    val currentPkg: String? get() = pkgState.value

    /** Show (or retarget) the wall for [pkg]. Returns false if the window couldn't be added. */
    fun show(pkg: String): Boolean {
        pkgState.value = pkg
        if (view != null) return true

        val lifecycleOwner = OverlayOwner()
        val composeView = ComposeView(service).apply {
            // Paint the wall's base colour immediately so the app underneath never
            // flashes through in the instant before Compose first draws.
            setBackgroundColor(0xFF070A14.toInt())
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PactTheme {
                    val target by pkgState
                    target?.let { p ->
                        BlockWall(
                            pkg = p,
                            onGoHome = {
                                com.pact.app.core.PactState.get(service).recordWalkAway(p)
                                dismiss()
                                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                            },
                            onDismissQuietly = { dismiss() },
                            onUnlocked = { durationMillis, tier, trigger ->
                                com.pact.app.core.PactState.get(service)
                                    .unlockFor(p, durationMillis, tier, trigger)
                                com.pact.app.core.Notifications.showBreak(service, p, durationMillis)
                                dismiss()
                            },
                        )
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            // Cover the whole screen, including behind the status and nav bars, so
            // the wall reads as one solid surface rather than a floating panel.
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        return runCatching {
            service.getSystemService(WindowManager::class.java).addView(composeView, params)
            lifecycleOwner.resume()
            view = composeView
            owner = lifecycleOwner
            // Approvals can arrive while the wall is up — sync fast meanwhile.
            (service.applicationContext as? com.pact.app.PactApp)?.acquireLiveSync()
            true
        }.getOrElse {
            lifecycleOwner.destroy()
            false
        }
    }

    fun dismiss() {
        val v = view ?: return
        view = null
        pkgState.value = null
        runCatching { service.getSystemService(WindowManager::class.java).removeView(v) }
        owner?.destroy()
        owner = null
        (service.applicationContext as? com.pact.app.PactApp)?.releaseLiveSync()
    }
}

/**
 * Minimal lifecycle plumbing so a ComposeView can live in a WindowManager
 * window owned by a service (Compose requires view-tree owners).
 */
private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun resume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
