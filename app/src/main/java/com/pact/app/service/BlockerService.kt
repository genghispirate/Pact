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
 * The shield. Watches window changes (offline, event-driven, negligible
 * battery) and enforces each app's daily allowance:
 *
 *  - A limited app that still has time today opens normally; the service
 *    quietly measures how long it stays in the foreground and books that
 *    against the day's budget.
 *  - The moment the budget runs out — whether the user switches to the app
 *    with none left, or burns through it mid-session — the lock wall goes up.
 *    (A hard-locked app, limit 0, has no budget and walls on every open.)
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

    // The limited app currently in the foreground, and when it came forward.
    private var trackedPkg: String? = null
    private var trackedStart: Long = 0L

    private val homePackage: String? by lazy {
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == SYSTEM_UI_PACKAGE) return

        val state = PactState.get(this)
        val now = System.currentTimeMillis()

        // Whatever we were measuring, the foreground just changed — book its time.
        flushUsage(now)

        if (state.isBlockedNow(pkg, now)) {
            // Events arrive in bursts; count one intervention per second per app.
            if (pkg != lastBlockPkg || now - lastBlockAt >= 1000L) {
                state.recordBlock(pkg)
            }
            lastBlockPkg = pkg
            lastBlockAt = now
            if (!overlay.show(pkg)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // A limited app opened with time still on the clock — let it run and
        // start the countdown to when the budget will be gone.
        if (state.isManaged(pkg) && state.snapshot.value.hasLimit(pkg)) {
            val remaining = state.snapshot.value.remainingMillis(pkg)
            if (remaining > 0L) {
                trackedPkg = pkg
                trackedStart = now
                scheduleBudgetCheck(remaining)
                if (overlay.isShowing) overlay.dismiss()
                return
            }
        }

        // A non-blocked, non-tracked window came forward. Only dismiss the wall
        // when a real app or the launcher took over — keyboards, dialogs, and
        // system windows must not tear the wall down.
        if (overlay.isShowing) {
            val isRealApp = pkg == homePackage ||
                packageManager.getLaunchIntentForPackage(pkg) != null
            if (isRealApp) overlay.dismiss()
        }
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
            if (!overlay.show(pkg)) performGlobalAction(GLOBAL_ACTION_HOME)
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
        overlay.dismiss()
        // Graceful handling of permission loss: tell the user instead of
        // failing silently. (Also fires on reboot; the service re-binds and
        // Home shows green again.)
        com.pact.app.core.Notifications.showShieldDown(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(budgetCheck)
        overlay.dismiss()
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
