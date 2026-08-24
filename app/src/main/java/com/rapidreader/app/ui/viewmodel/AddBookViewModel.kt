package com.rapidreader.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidreader.app.data.BookRepository
import com.rapidreader.app.data.StagedOriginal
import com.rapidreader.app.extract.TextExtractor
import com.rapidreader.app.rsvp.RsvpEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AddState {
    data object Idle : AddState()
    data class Parsing(val fileName: String, val detail: String? = null) : AddState()
    data class Error(val message: String) : AddState()
    data class Ready(
        val text: String,
        val title: String,
        val source: String,
        val wordCount: Int,
        val original: StagedOriginal? = null,
        val originalNote: String? = null,
        val cover: ByteArray? = null
    ) : AddState()
}

class AddBookViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository(app)
    private val _state = MutableStateFlow<AddState>(AddState.Idle)
    val state: StateFlow<AddState> = _state

    fun reset() {
        _state.value = AddState.Idle
        viewModelScope.launch { repo.clearStagedOriginal() }
    }

    fun importFile(uri: Uri, fileName: String) {
        _state.value = AddState.Parsing(fileName)
        viewModelScope.launch {
            try {
                val ext = fileName.substringAfterLast('.', "").lowercase()

                // Preserve the original first, while the SAF grant is fresh and
                // the Parsing spinner is already on screen. Non-fatal if it fails.
                var staged: StagedOriginal? = null
                var originalNote: String? = null
                if (ext == "pdf" || ext == "epub") {
                    _state.value = AddState.Parsing(fileName, "Saving a copy of the original…")
                    staged = repo.stageOriginal(uri, ext, querySize(uri))
                    if (staged == null) originalNote = "Original-form view unavailable for this file."
                }

                val result = TextExtractor.extract(getApplication(), uri, fileName) { detail ->
                    _state.value = AddState.Parsing(fileName, detail)
                }
                val words = RsvpEngine.tokenize(result.text)
                if (words.isEmpty()) throw IllegalStateException("No text could be extracted from this file.")
                _state.value = AddState.Ready(
                    result.text, result.title, result.source, words.size, staged, originalNote, result.cover
                )
            } catch (e: Exception) {
                repo.clearStagedOriginal()
                _state.value = AddState.Error(e.message ?: "Couldn't read that file.")
            }
        }
    }

    fun importPastedText(title: String, text: String) {
        val words = RsvpEngine.tokenize(text)
        if (words.isEmpty()) { _state.value = AddState.Error("Paste some text first."); return }
        _state.value = AddState.Ready(text, title.ifBlank { "Untitled" }, "text", words.size)
    }

    suspend fun save(title: String): String {
        val s = _state.value
        check(s is AddState.Ready) { "Nothing ready to save" }
        return repo.addBook(title.ifBlank { s.title }, s.source, s.text, s.wordCount, s.original, s.cover)
    }

    private fun querySize(uri: Uri): Long? {
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) return cursor.getLong(idx)
        }
        return null
    }
}
