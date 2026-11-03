package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.Habit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CitraHabitsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val habits = db.habitDao().getAllHabitsSync()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_habits)
                    buildHabitsViews(context, views, habits, todayStr)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildHabitsViews(context: Context, views: RemoteViews, habits: List<Habit>, todayStr: String) {
        // Root / header click opens Habits screen
        val openHabitsIntent = Intent(context, MainActivity::class.java).apply {
            action = CitraWidgetHelper.ACTION_NAVIGATE_HABITS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openHabitsPendingIntent = PendingIntent.getActivity(
            context,
            1001,
            openHabitsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_habits_header_title, openHabitsPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_habits_empty, openHabitsPendingIntent)

        if (habits.isEmpty()) {
            views.setViewVisibility(R.id.widget_habits_empty, View.VISIBLE)
            views.setViewVisibility(R.id.widget_habits_list_container, View.GONE)
            views.setTextViewText(R.id.widget_habits_streak_summary, "0/0")
            return
        }

        views.setViewVisibility(R.id.widget_habits_empty, View.GONE)
        views.setViewVisibility(R.id.widget_habits_list_container, View.VISIBLE)

        var completedTodayCount = 0
        val habitRowIds = listOf(R.id.habit_row_1, R.id.habit_row_2, R.id.habit_row_3, R.id.habit_row_4)
        val habitTitleIds = listOf(R.id.habit_title_1, R.id.habit_title_2, R.id.habit_title_3, R.id.habit_title_4)
        val habitStreakIds = listOf(R.id.habit_streak_1, R.id.habit_streak_2, R.id.habit_streak_3, R.id.habit_streak_4)
        val habitCheckIds = listOf(R.id.habit_check_1, R.id.habit_check_2, R.id.habit_check_3, R.id.habit_check_4)

        for (i in 0 until 4) {
            if (i < habits.size) {
                val habit = habits[i]
                val completedDates = habit.completedDatesCsv.split(",").filter { it.isNotBlank() }
                val isDoneToday = completedDates.contains(todayStr)
                if (isDoneToday) completedTodayCount++

                views.setViewVisibility(habitRowIds[i], View.VISIBLE)

                val prefix = if (habit.iconEmoji.isNotBlank()) "${habit.iconEmoji} " else "🎯 "
                views.setTextViewText(habitTitleIds[i], "$prefix${habit.title}")
                views.setTextViewText(habitStreakIds[i], "🔥 ${habit.currentStreak}d")

                val checkIcon = if (isDoneToday) R.drawable.ic_widget_check_on else R.drawable.ic_widget_check_off
                views.setImageViewResource(habitCheckIds[i], checkIcon)

                // Toggle habit PendingIntent
                val toggleIntent = Intent(context, CitraWidgetActionReceiver::class.java).apply {
                    action = CitraWidgetHelper.ACTION_TOGGLE_HABIT
                    putExtra(CitraWidgetHelper.EXTRA_HABIT_ID, habit.id)
                }
                val togglePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (habit.id * 100 + i).toInt(),
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(habitCheckIds[i], togglePendingIntent)
                views.setOnClickPendingIntent(habitRowIds[i], openHabitsPendingIntent)
            } else {
                views.setViewVisibility(habitRowIds[i], View.GONE)
            }
        }

        views.setTextViewText(R.id.widget_habits_streak_summary, "$completedTodayCount/${habits.size}")
    }
}
