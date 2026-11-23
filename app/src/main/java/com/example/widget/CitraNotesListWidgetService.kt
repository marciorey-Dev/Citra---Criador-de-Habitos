package com.example.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.Note
import kotlinx.coroutines.runBlocking

class CitraNotesListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CitraNotesListRemoteViewsFactory(applicationContext)
    }
}

class CitraNotesListRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var notes: List<Note> = emptyList()

    override fun onCreate() {
        loadNotes()
    }

    override fun onDataSetChanged() {
        loadNotes()
    }

    private fun loadNotes() {
        try {
            notes = runBlocking {
                val db = AppDatabase.getInstance(context)
                db.noteDao().getAllNotesSync()
                    .filter { !it.isDeleted }
                    .sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt })
            }
        } catch (e: Exception) {
            e.printStackTrace()
            notes = emptyList()
        }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= notes.size) return null
        val note = notes[position]

        val views = RemoteViews(context.packageName, R.layout.widget_note_item)

        val docType = com.example.data.model.NoteDocumentType.fromId(note.documentType)
        val typePrefix = when {
            docType == com.example.data.model.NoteDocumentType.PDF -> "📄 [PDF] "
            docType == com.example.data.model.NoteDocumentType.DOCX -> "📘 [DOCX] "
            docType.isEbookOrComic -> "📚 [${docType.label}] "
            else -> ""
        }
        val pinPrefix = if (note.isPinned) "📌 " else ""
        views.setTextViewText(R.id.widget_item_note_title, "$pinPrefix$typePrefix${note.title.ifBlank { "Sem título" }}")
        views.setTextViewText(R.id.widget_item_note_category, note.category.ifBlank { "Geral" })

        val cleanSnippet = when {
            docType == com.example.data.model.NoteDocumentType.PDF -> "Documento PDF importado no Citra"
            docType == com.example.data.model.NoteDocumentType.DOCX -> "Documento Word .docx importado no Citra"
            docType.isEbookOrComic -> "Leitura ${docType.label} no Citra"
            else -> CitraWidgetHelper.cleanMarkdown(note.content)
        }
        views.setTextViewText(
            R.id.widget_item_note_snippet,
            cleanSnippet.ifBlank { "Nota vazia" }
        )

        // Fill in intent for click handling
        val fillInIntent = Intent().apply {
            putExtra(CitraWidgetHelper.EXTRA_NOTE_ID, note.id)
        }
        views.setOnClickFillInIntent(R.id.widget_note_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position in notes.indices) notes[position].id else position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
