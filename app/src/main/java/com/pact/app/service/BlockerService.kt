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
 * Reliability is the whole game here, so detection and metering are separate:
 *  - **Detection** — [onForeground] is fed by every WINDOW_STATE_CHANGED and
 *    WINDOWS_CHANGED event AND (when granted) by [ShieldService]'s once-a-second
 *    usage poll. It is idempotent: re-asserts a wall a prior dismissal dropped,
 *    which self-heals the classic "leave a blocked app and come back" failure.
 *  - **Metering** — a self-rescheduling tick books the foreground app's elapsed
 *    time every few seconds and raises the wall the instant the budget is spent,
 *    independent of whether any detection event happened to fire.
 *
 * The wall is an accessibility overlay drawn by this service — see
 * [BlockOverlay] for why that matters.
 */
class BlockerService : AccessibilityService() {

    private val overlay by lazy { BlockOverlay(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val meterTick = Runnable { onMeterTick() }

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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Keep the process alive so we don't get killed in the background.
        runCatching { ShieldService.start(this) }
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
        onForeground(pkg, now)
    }

    /**
     * The single funnel for "app X is in the foreground," fed by accessibility
     * events and by [ShieldService]'s usage poll. Idempotent.
     */
    fun onForeground(pkg: String, now: Long = System.currentTimeMillis()) {
        if (pkg == packageName || pkg == SYSTEM_UI_PACKAGE) return
        val state = PactState.get(this)

        if (state.isBlockedNow(pkg, now)) {
            if (shownFor == pkg && overlay.isShowing) return
            meterFlush(now)
            if (pkg != lastBlockPkg || now - lastBlockAt >= 1000L) state.recordBlock(pkg)
            lastBlockPkg = pkg
            lastBlockAt = now
            showWall(pkg)
            return
        }

        // A limited app with time still on the clock: let it run and meter it.
        val snap = state.snapshot.value
        if (state.isManaged(pkg) && snap.hasLimit(pkg) && snap.remainingMillis(pkg) > 0L) {
            dismissWall()
            if (trackedPkg != pkg) {
                meterFlush(now)
                trackedPkg = pkg
                trackedStart = now
                handler.removeCallbacks(meterTick)
                handler.postDelayed(meterTick, METER_INTERVAL_MILLIS)
            }
            return
        }

        // A real app or the launcher took over — stand the wall down. Keyboards,
        // dialogs, and system windows are ignored so they can't tear it away.
        meterFlush(now)
        if (overlay.isShowing) {
            val isRealApp = pkg == homePackage ||
                packageManager.getLaunchIntentForPackage(pkg) != null
            if (isRealApp) dismissWall()
        }
    }

    /** Periodic metering: book elapsed time and wall the app the moment it's spent. */
    private fun onMeterTick() {
        val pkg = trackedPkg ?: return
        val now = System.currentTimeMillis()
        val elapsed = now - trackedStart
        if (elapsed > 0L) {
            PactState.get(this).recordUsage(pkg, elapsed)
            trackedStart = now
        }
        val state = PactState.get(this)
        if (state.isBlockedNow(pkg, now)) {
            trackedPkg = null
            state.recordBlock(pkg)
            showWall(pkg)
        } else {
            handler.postDelayed(meterTick, METER_INTERVAL_MILLIS)
        }
    }

    private fun showWall(pkg: String) {
        if (overlay.show(pkg)) {
            shownFor = pkg
        } else {
            shownFor = null
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun dismissWall() {
        if (overlay.isShowing) overlay.dismiss()
        shownFor = null
    }

    /** Book the elapsed foreground time of the app we were tracking, and stop the clock. */
    private fun meterFlush(now: Long) {
        val pkg = trackedPkg ?: return
        val elapsed = now - trackedStart
        trackedPkg = null
        handler.removeCallbacks(meterTick)
        if (elapsed > 0L) PactState.get(this).recordUsage(pkg, elapsed)
    }

    /** The package of the window the user is actually looking at, best-effort. */
    private fun foregroundPackage(): String? =
        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        // Graceful handling of permission loss: tell the user instead of
        // failing silently. (Also fires on reboot; the service re-binds and
        // Home shows green again.)
        com.pact.app.core.Notifications.showShieldDown(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        instance = null
        handler.removeCallbacks(meterTick)
        dismissWall()
    }

    companion object {
        @Volatile
        var instance: BlockerService? = null

        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val METER_INTERVAL_MILLIS = 15_000L

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
