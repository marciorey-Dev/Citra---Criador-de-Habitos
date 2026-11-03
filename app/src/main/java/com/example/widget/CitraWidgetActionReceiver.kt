package com.example.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.data.local.AppDatabase
import com.example.data.repository.CitraRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CitraWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            CitraWidgetHelper.ACTION_TOGGLE_HABIT -> {
                val habitId = intent.getLongExtra(CitraWidgetHelper.EXTRA_HABIT_ID, -1L)
                if (habitId > 0) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getInstance(context)
                            val repository = CitraRepository(context, db)
                            val habit = db.habitDao().getHabitById(habitId)
                            if (habit != null) {
                                val isDone = repository.toggleHabitToday(habit)
                                CitraWidgetHelper.updateAllWidgets(context)

                                Handler(Looper.getMainLooper()).post {
                                    val msg = if (isDone) {
                                        "🍊 ${habit.title} concluído hoje!"
                                    } else {
                                        "${habit.title} desmarcado."
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            CitraWidgetHelper.ACTION_UPDATE_WIDGETS -> {
                CitraWidgetHelper.updateAllWidgets(context)
            }
        }
    }
}
