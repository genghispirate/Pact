package com.pact.app.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pact.app.R
import java.text.DateFormat
import java.util.Date

/**
 * Quiet by design: one low-priority notification while a break is running
 * (with a relock action, auto-dismissing when the break ends), and a single
 * alert if the shield is switched off. Nothing else — no streaks, no nags.
 */
object Notifications {

    private const val CHANNEL_BREAKS = "breaks"
    private const val CHANNEL_SHIELD = "shield"
    private const val CHANNEL_NETWORK = "network"
    private const val ID_SHIELD_DOWN = 1
    const val EXTRA_PKG = "pkg"

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BREAKS,
                context.getString(R.string.notif_channel_breaks),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SHIELD,
                context.getString(R.string.notif_channel_shield),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NETWORK,
                context.getString(R.string.notif_channel_network),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    /** Trust-network events: new request, approval, denial, message, new pairing. */
    fun showNetworkEvent(context: Context, event: String, name: String, detail: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val (title, text) = when (event) {
            TrustNetwork.EVENT_REQUEST ->
                context.getString(R.string.notif_request_title, name) to
                    context.getString(R.string.notif_request_body, detail)
            TrustNetwork.EVENT_APPROVED ->
                context.getString(R.string.notif_approved_title) to
                    context.getString(R.string.notif_approved_body, name, detail)
            TrustNetwork.EVENT_DENIED ->
                context.getString(R.string.notif_denied_title) to
                    context.getString(R.string.notif_denied_body, name, detail)
            TrustNetwork.EVENT_MESSAGE ->
                context.getString(R.string.notif_message_title, name) to detail
            TrustNetwork.EVENT_PAIRED ->
                context.getString(R.string.notif_paired_title) to
                    context.getString(R.string.notif_paired_body, name)
            else -> return
        }
        val open = PendingIntent.getActivity(
            context,
            event.hashCode(),
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_NETWORK)
            .setSmallIcon(R.drawable.ic_notif_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify((event + name).hashCode(), notification)
    }

    fun showBreak(context: Context, pkg: String, durationMillis: Long) {
        if (!canPost(context)) return
        ensureChannels(context)
        val endTime = DateFormat.getTimeInstance(DateFormat.SHORT)
            .format(Date(System.currentTimeMillis() + durationMillis))
        val relockIntent = PendingIntent.getBroadcast(
            context,
            pkg.hashCode(),
            Intent(context, RelockReceiver::class.java).putExtra(EXTRA_PKG, pkg),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_BREAKS)
            .setSmallIcon(R.drawable.ic_notif_shield)
            .setContentTitle(context.getString(R.string.notif_break_title, Apps.label(context, pkg)))
            .setContentText(context.getString(R.string.notif_break_body, endTime))
            .setOngoing(false)
            .setSilent(true)
            .setTimeoutAfter(durationMillis)
            .addAction(0, context.getString(R.string.notif_relock_now), relockIntent)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(pkg.hashCode(), notification)
    }

    fun cancelBreak(context: Context, pkg: String) {
        context.getSystemService(NotificationManager::class.java).cancel(pkg.hashCode())
    }

    fun showShieldDown(context: Context) {
        if (!canPost(context)) return
        if (!PactState.get(context).snapshot.value.setupComplete) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_SHIELD)
            .setSmallIcon(R.drawable.ic_notif_shield)
            .setContentTitle(context.getString(R.string.notif_shield_down_title))
            .setContentText(context.getString(R.string.notif_shield_down_body))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(ID_SHIELD_DOWN, notification)
    }
}

/** "Relock now" from the break notification. */
class RelockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(Notifications.EXTRA_PKG) ?: return
        PactState.get(context).relock(pkg)
        Notifications.cancelBreak(context, pkg)
    }
}
