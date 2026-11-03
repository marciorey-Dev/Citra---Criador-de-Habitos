package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class CitraNotesListWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_notes_list)

            // Setup service intent for list view
            val serviceIntent = Intent(context, CitraNotesListWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_notes_list_view, serviceIntent)
            views.setEmptyView(R.id.widget_notes_list_view, R.id.widget_notes_empty)

            // Header "+ Nova" button opens new note editor
            val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                action = CitraWidgetHelper.ACTION_NAVIGATE_NEW_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val newNotePendingIntent = PendingIntent.getActivity(
                context,
                4001,
                newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_notes_btn_add, newNotePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_notes_empty, newNotePendingIntent)

            // Template PendingIntent for list items click
            val itemClickIntent = Intent(context, MainActivity::class.java).apply {
                action = CitraWidgetHelper.ACTION_NAVIGATE_OPEN_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val itemClickPendingIntent = PendingIntent.getActivity(
                context,
                4002,
                itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_notes_list_view, itemClickPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
