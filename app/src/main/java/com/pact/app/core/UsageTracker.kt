package com.pact.app.core

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.pact.app.service.ShieldService
import java.util.Calendar

/**
 * Accurate device screen time via [UsageStatsManager]. This is the real thing —
 * total foreground app time since midnight — not just the apps Pact watches.
 * Needs the user's "Usage access" grant; returns -1 when it isn't available so
 * callers can fall back to the on-device estimate.
 */
object UsageTracker {

    /** Minutes of foreground app use today across all apps, or -1 if unknown. */
    fun screenTimeTodayMinutes(context: Context): Int {
        if (!ShieldService.hasUsageAccess(context)) return -1
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return -1
        val now = System.currentTimeMillis()
        val start = midnight(now)

        return runCatching {
            val events = usm.queryEvents(start, now)
            val openedAt = HashMap<String, Long>()
            var total = 0L
            val e = UsageEvents.Event()
            val self = context.packageName
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val pkg = e.packageName ?: continue
                if (pkg == self || pkg in IGNORED) continue
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND, UsageEvents.Event.ACTIVITY_RESUMED ->
                        openedAt[pkg] = e.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND, UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val s = openedAt.remove(pkg)
                        if (s != null && e.timeStamp > s) total += e.timeStamp - s
                    }
                }
            }
            // whatever is still on screen right now
            for ((_, s) in openedAt) if (now > s) total += now - s
            (total / 60_000L).toInt().coerceAtLeast(0)
        }.getOrDefault(-1)
    }

    private val IGNORED = setOf("com.android.systemui", "com.android.launcher", "com.google.android.apps.nexuslauncher")

    private fun midnight(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
