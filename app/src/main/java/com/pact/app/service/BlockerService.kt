package com.pact.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.pact.app.core.PactState

/**
 * The shield. Watches the foreground app (offline, event-driven, negligible
 * battery) and enforces each app's daily allowance:
 *
 *  - A limited app that still has time today opens normally; the service
 *    quietly measures how long it stays in the foreground and books that
 *    against the day's budget.
 *  - The moment the budget runs out — whether the user switches to the app
 *    with none left, or burns through it mid-session — the lock wall goes up.
 *    (A hard-locked app, limit 0, has no budget and walls on every open.)
 *
 * Reliability is the whole game here. Rather than trust a single event type,
 * every relevant event is funnelled through one **idempotent** [reconcile]:
 * work out the true foreground app, then make the wall's state match it. That
 * self-heals the classic failure — leaving a blocked app and coming back, where
 * some launchers never re-fire WINDOW_STATE_CHANGED — because WINDOWS_CHANGED
 * (window layering) still fires on return, and we re-derive the top app from
 * [getRootInActiveWindow] instead of a stale event package.
 *
 * The wall is an accessibility overlay drawn by this service — see
 * [BlockOverlay] for why that matters.
 */
class BlockerService : AccessibilityService() {

    private val overlay by lazy { BlockOverlay(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val budgetCheck = Runnable { enforceBudget() }

    private var lastBlockPkg: String? = null
    private var lastBlockAt: Long = 0L
    private var lastReconcileAt: Long = 0L

    // The limited app currently in the foreground, and when it came forward.
    private var trackedPkg: String? = null
    private var trackedStart: Long = 0L
    // The app the wall is currently covering (null when down).
    private var shownFor: String? = null

    private val homePackage: String? by lazy {
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        val now = System.currentTimeMillis()
        // WINDOWS_CHANGED can arrive in rapid bursts; a light debounce keeps the
        // active-window lookups cheap without hurting responsiveness.
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED && now - lastReconcileAt < 250L) return
        lastReconcileAt = now

        // Trust the event's package for a state change; otherwise ask the system
        // which window is actually active right now.
        val pkg = event.packageName?.toString()?.takeIf { type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED }
            ?: foregroundPackage()
            ?: return
        reconcile(pkg, now)
    }

    /** The package of the window the user is actually looking at, best-effort. */
    private fun foregroundPackage(): String? =
        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()

    /**
     * Make the wall's state match reality for [pkg]. Idempotent: safe to call on
     * every event, and re-asserts a block that a previous dismissal dropped.
     */
    private fun reconcile(pkg: String, now: Long) {
        // Our own overlay / the status bar are not "apps" to police.
        if (pkg == packageName || pkg == SYSTEM_UI_PACKAGE) return

        val state = PactState.get(this)

        if (state.isBlockedNow(pkg, now)) {
            flushUsage(now)
            // Already covering this app? Nothing to do — don't flicker.
            if (shownFor == pkg && overlay.isShowing) return
            if (pkg != lastBlockPkg || now - lastBlockAt >= 1000L) state.recordBlock(pkg)
            lastBlockPkg = pkg
            lastBlockAt = now
            if (overlay.show(pkg)) {
                shownFor = pkg
            } else {
                shownFor = null
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // Not blocked. If it's a limited app with time left, let it run and start
        // the countdown to when the budget will be gone.
        if (state.isManaged(pkg) && state.snapshot.value.hasLimit(pkg)) {
            val remaining = state.snapshot.value.remainingMillis(pkg)
            if (remaining > 0L) {
                dismissWall()
                trackedPkg = pkg
                trackedStart = now
                scheduleBudgetCheck(remaining)
                return
            }
        }

        // A real app or the launcher took over — stand the wall down. Keyboards,
        // dialogs, and system windows are ignored so they can't tear it away.
        flushUsage(now)
        if (overlay.isShowing) {
            val isRealApp = pkg == homePackage ||
                packageManager.getLaunchIntentForPackage(pkg) != null
            if (isRealApp) dismissWall()
        }
    }

    private fun dismissWall() {
        if (overlay.isShowing) overlay.dismiss()
        shownFor = null
    }

    /** Book the elapsed foreground time of the app we were tracking, and stop the clock. */
    private fun flushUsage(now: Long) {
        val pkg = trackedPkg ?: return
        val elapsed = now - trackedStart
        trackedPkg = null
        handler.removeCallbacks(budgetCheck)
        if (elapsed > 0L) PactState.get(this).recordUsage(pkg, elapsed)
    }

    private fun scheduleBudgetCheck(remaining: Long) {
        handler.removeCallbacks(budgetCheck)
        // Re-check at least once a minute so enforcement stays snappy and usage
        // is booked in small, accurate slices even during a long sitting.
        handler.postDelayed(budgetCheck, remaining.coerceIn(1_000L, CHECK_INTERVAL_MILLIS))
    }

    /** Fired while a limited app is still foreground — the budget may now be spent. */
    private fun enforceBudget() {
        val pkg = trackedPkg ?: return
        val now = System.currentTimeMillis()
        flushUsage(now)
        val state = PactState.get(this)
        if (state.isBlockedNow(pkg, now)) {
            state.recordBlock(pkg)
            if (overlay.show(pkg)) shownFor = pkg else performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            // Still within budget — resume measuring.
            trackedPkg = pkg
            trackedStart = now
            scheduleBudgetCheck(state.snapshot.value.remainingMillis(pkg))
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(budgetCheck)
        dismissWall()
        // Graceful handling of permission loss: tell the user instead of
        // failing silently. (Also fires on reboot; the service re-binds and
        // Home shows green again.)
        com.pact.app.core.Notifications.showShieldDown(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(budgetCheck)
        dismissWall()
        super.onDestroy()
    }

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val CHECK_INTERVAL_MILLIS = 60_000L

        /**
         * Whether the shield is enabled in system accessibility settings.
         * Devices report entries in either full or short component form, so
         * compare parsed ComponentNames rather than raw strings.
         */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(context, BlockerService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
