package com.pact.app.service

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pact.app.R

/**
 * A quiet, persistent foreground service whose entire job is reliability.
 *
 * Two jobs, both about the shield never silently dying:
 *  1. Being a foreground service keeps Pact's process alive, so aggressive OEM
 *     battery managers can't kill the accessibility watcher in the background —
 *     the single most common reason blockers "just stop working."
 *  2. When the user has granted Usage Access, it polls the current foreground
 *     app once a second and feeds it to [BlockerService] as a *second* source of
 *     truth, catching the rare accessibility event that never fires.
 *
 * It draws nothing and enforces nothing itself — the wall is always the
 * accessibility overlay. The app works without this service (and without Usage
 * Access); this only makes it harder to defeat by accident.
 */
class ShieldService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            currentForegroundApp()?.let { BlockerService.instance?.onForeground(it) }
            handler.postDelayed(this, POLL_INTERVAL_MILLIS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(poll)
        handler.post(poll)
        return START_STICKY   // system restarts us (and thus the process) if killed
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    private fun currentForegroundApp(): String? {
        if (!hasUsageAccess(this)) return null
        val usm = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - LOOKBACK_MILLIS, now)
        val event = UsageEvents.Event()
        var pkg: String? = null
        var latest = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.timeStamp >= latest) {
                latest = event.timeStamp
                pkg = event.packageName
            }
        }
        return pkg
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel_running), NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false) }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif_shield)
            .setContentTitle(getString(R.string.notif_running_title))
            .setContentText(getString(R.string.notif_running_body))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL = "shield_running"
        private const val NOTIF_ID = 42
        private const val POLL_INTERVAL_MILLIS = 1_000L
        private const val LOOKBACK_MILLIS = 6_000L

        /** Start (or no-op if running). Safe to call from a foreground context. */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, ShieldService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ShieldService::class.java)) }
        }

        /** Whether the user has granted "Usage access" (enables the redundant poll). */
        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
