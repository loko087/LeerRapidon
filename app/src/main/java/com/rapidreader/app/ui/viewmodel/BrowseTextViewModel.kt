package com.rapidreader.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidreader.app.data.BookRepository
import com.rapidreader.app.rsvp.BrowseWord
import com.rapidreader.app.rsvp.RsvpEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseUiState(
    val title: String = "",
    val paragraphs: List<List<BrowseWord>> = emptyList(),
    val currentIdx: Int = 0,
    val loading: Boolean = true
)

class BrowseTextViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository(app)

    private val _ui = MutableStateFlow(BrowseUiState())
    val ui: StateFlow<BrowseUiState> = _ui.asStateFlow()

    private var bookId: String? = null
    private var wpm: Int = 300

    fun load(id: String) {
        if (bookId == id && !_ui.value.loading) return
        bookId = id
        viewModelScope.launch {
            val entry = repo.getBook(id) ?: return@launch
            val text = repo.getBookText(id)
            wpm = entry.wpm
            _ui.value = BrowseUiState(
                title = entry.title,
                paragraphs = RsvpEngine.paragraphs(text),
                currentIdx = entry.idx,
                loading = false
            )
        }
    }

    /** Persists the tapped word as the book's reading position so the next
     *  reader screen (a fresh instance) picks it up on load. */
    suspend fun jumpTo(wordIndex: Int) {
        val id = bookId ?: return
        repo.updateProgress(id, wordIndex, wpm)
    }
}
