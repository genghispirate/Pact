package com.pact.app.core

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppInfo(val pkg: String, val label: String)

object Apps {

    /** Packages people most often want to lock — floated to the top of the picker. */
    val SUGGESTED = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",      // TikTok
        "com.ss.android.ugc.trill",      // TikTok (some regions)
        "com.google.android.youtube",
        "com.twitter.android",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.facebook.katana",
        "com.pinterest",
        "com.twitch.android.app",
        "com.discord",
        "com.netflix.mediaclient",
    )

    fun installedApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { pkg -> AppInfo(pkg, label(context, pkg)) }
            .sortedWith(
                compareByDescending<AppInfo> { it.pkg in SUGGESTED }
                    .thenBy { it.label.lowercase() }
            )
            .toList()
    }

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    fun icon(context: Context, pkg: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(pkg)
    }.getOrNull()
}
