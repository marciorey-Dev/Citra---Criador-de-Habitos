package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CitraNotePreviewWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val notes = db.noteDao().getAllNotesSync().filter { !it.isDeleted }
                    .sortedWith(compareByDescending<com.example.data.model.Note> { it.isPinned }.thenByDescending { it.updatedAt })

                val targetNote = notes.firstOrNull()

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_note_preview)

                    if (targetNote != null) {
                        val isPinned = targetNote.isPinned
                        val headerLabel = if (isPinned) "📌 Nota Fixada" else "📝 Nota Recente"
                        views.setTextViewText(R.id.widget_note_header_title, headerLabel)
                        views.setTextViewText(R.id.widget_note_category_chip, targetNote.category.ifBlank { "Geral" })
                        views.setTextViewText(R.id.widget_note_title, targetNote.title.ifBlank { "Sem título" })

                        val cleanBody = CitraWidgetHelper.cleanMarkdown(targetNote.content)
                        views.setTextViewText(
                            R.id.widget_note_body,
                            cleanBody.ifBlank { "Nota vazia. Toque para editar..." }
                        )

                        val dateStr = SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(Date(targetNote.updatedAt))
                        views.setTextViewText(R.id.widget_note_date, "Editada $dateStr")

                        // PendingIntent to open note
                        val openNoteIntent = Intent(context, MainActivity::class.java).apply {
                            action = CitraWidgetHelper.ACTION_NAVIGATE_OPEN_NOTE
                            putExtra(CitraWidgetHelper.EXTRA_NOTE_ID, targetNote.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val openNotePendingIntent = PendingIntent.getActivity(
                            context,
                            3000 + appWidgetId,
                            openNoteIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        views.setOnClickPendingIntent(R.id.widget_note_preview_root, openNotePendingIntent)
                        views.setOnClickPendingIntent(R.id.widget_note_btn_open, openNotePendingIntent)
                    } else {
                        views.setTextViewText(R.id.widget_note_header_title, "📝 Citra Notas")
                        views.setTextViewText(R.id.widget_note_category_chip, "Citra")
                        views.setTextViewText(R.id.widget_note_title, "Nenhuma nota ainda")
                        views.setTextViewText(R.id.widget_note_body, "Escreva seus pensamentos, resumos e rascunhos em Markdown no Citra!")
                        views.setTextViewText(R.id.widget_note_date, "Toque para criar")
                        views.setTextViewText(R.id.widget_note_btn_open, "Criar")

                        val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                            action = CitraWidgetHelper.ACTION_NAVIGATE_NEW_NOTE
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val newNotePendingIntent = PendingIntent.getActivity(
                            context,
                            3999 + appWidgetId,
                            newNoteIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        views.setOnClickPendingIntent(R.id.widget_note_preview_root, newNotePendingIntent)
                        views.setOnClickPendingIntent(R.id.widget_note_btn_open, newNotePendingIntent)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
