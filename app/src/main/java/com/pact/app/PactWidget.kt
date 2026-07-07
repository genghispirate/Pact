package com.pact.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pact.app.core.PactState
import com.pact.app.service.BlockerService

/**
 * Glanceable home-screen widget: shield state, streak, and today's blocks.
 * Refreshed by [PactState] whenever state changes and by the system's
 * periodic onUpdate.
 */
class PactWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PactWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun buildViews(context: Context): RemoteViews {
            val snapshot = PactState.get(context).snapshot.value
            val shieldOn = BlockerService.isEnabled(context)
            val views = RemoteViews(context.packageName, R.layout.widget_pact)

            views.setTextViewText(
                R.id.widget_status,
                context.getString(
                    if (shieldOn) R.string.widget_shield_on else R.string.widget_shield_off
                ),
            )
            views.setTextColor(
                R.id.widget_status,
                if (shieldOn) 0xFF5EEAD4.toInt() else 0xFFFFC97E.toInt(),
            )
            views.setTextViewText(
                R.id.widget_streak,
                context.getString(R.string.widget_streak, snapshot.streakDays()),
            )
            views.setTextViewText(
                R.id.widget_blocks,
                context.getString(R.string.widget_blocks_today, snapshot.today.blocks),
            )

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
