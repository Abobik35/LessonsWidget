package com.nastlo.lessonswidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class LessonsWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget = LessonsWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefresher.scheduleNext(context.applicationContext)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefresher.ACTION_REFRESH) {
            WidgetRefresher.refreshNow(context.applicationContext)
        }
    }
}
