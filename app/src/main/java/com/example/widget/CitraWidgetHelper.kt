package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.R

object CitraWidgetHelper {
    const val ACTION_NAVIGATE_TIMER = "com.example.citra.NAVIGATE_TIMER"
    const val ACTION_NAVIGATE_NEW_NOTE = "com.example.citra.NAVIGATE_NEW_NOTE"
    const val ACTION_NAVIGATE_OPEN_NOTE = "com.example.citra.NAVIGATE_OPEN_NOTE"
    const val ACTION_NAVIGATE_HABITS = "com.example.citra.NAVIGATE_HABITS"
    const val ACTION_TOGGLE_HABIT = "com.example.citra.TOGGLE_HABIT"
    const val ACTION_UPDATE_WIDGETS = "com.example.citra.UPDATE_WIDGETS"

    const val EXTRA_NOTE_ID = "com.example.citra.EXTRA_NOTE_ID"
    const val EXTRA_HABIT_ID = "com.example.citra.EXTRA_HABIT_ID"

    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // 1. Habits Widget
        val habitsComponent = ComponentName(context, CitraHabitsWidgetProvider::class.java)
        val habitsIds = appWidgetManager.getAppWidgetIds(habitsComponent)
        if (habitsIds.isNotEmpty()) {
            val intent = Intent(context, CitraHabitsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, habitsIds)
            }
            context.sendBroadcast(intent)
        }

        // 2. Timer Widget
        val timerComponent = ComponentName(context, CitraTimerWidgetProvider::class.java)
        val timerIds = appWidgetManager.getAppWidgetIds(timerComponent)
        if (timerIds.isNotEmpty()) {
            val intent = Intent(context, CitraTimerWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, timerIds)
            }
            context.sendBroadcast(intent)
        }

        // 3. Note Preview Widget
        val notePreviewComponent = ComponentName(context, CitraNotePreviewWidgetProvider::class.java)
        val notePreviewIds = appWidgetManager.getAppWidgetIds(notePreviewComponent)
        if (notePreviewIds.isNotEmpty()) {
            val intent = Intent(context, CitraNotePreviewWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, notePreviewIds)
            }
            context.sendBroadcast(intent)
        }

        // 4. Notes List Widget
        val notesListComponent = ComponentName(context, CitraNotesListWidgetProvider::class.java)
        val notesListIds = appWidgetManager.getAppWidgetIds(notesListComponent)
        if (notesListIds.isNotEmpty()) {
            appWidgetManager.notifyAppWidgetViewDataChanged(notesListIds, R.id.widget_notes_list_view)
            val intent = Intent(context, CitraNotesListWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, notesListIds)
            }
            context.sendBroadcast(intent)
        }
    }

    fun cleanMarkdown(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            // Strip code blocks
            .replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?```"), "")
            // Strip inline code
            .replace(Regex("`([^`]+)`"), "$1")
            // Strip images
            .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")
            // Strip links
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
            // Strip markdown headers
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            // Strip blockquotes
            .replace(Regex("(?m)^>\\s*"), "")
            // Strip task lists
            .replace(Regex("(?m)^[-*+]\\s*\\[[ xX]\\]\\s*"), "• ")
            // Strip bullet points
            .replace(Regex("(?m)^[-*+]\\s+"), "• ")
            // Strip bold & italic markers
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("__([^_]+)__"), "$1")
            .replace(Regex("_([^_]+)_"), "$1")
            // Strip strikethrough
            .replace(Regex("~~([^~]+)~~"), "$1")
            // Strip horizontal rules
            .replace(Regex("(?m)^[-*_]{3,}\\s*$"), "")
            // Collapse multiple blank lines
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
