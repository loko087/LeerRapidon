package com.rapidreader.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidreader.app.data.BookRepository
import com.rapidreader.app.rsvp.BrowseWord
import com.rapidreader.app.rsvp.RsvpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrowseUiState(
    val title: String = "",
    val paragraphs: List<List<BrowseWord>> = emptyList(),
    val currentIdx: Int = 0,
    val loading: Boolean = true,
    // See ReaderUiState.textMissing — same condition, same message.
    val textMissing: Boolean = false
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
            val entry = repo.getBook(id)
            val text = entry?.let { repo.getBookText(id) }
            if (entry == null || text == null) {
                // Bailing out without clearing `loading` would spin forever.
                _ui.value = BrowseUiState(
                    title = entry?.title.orEmpty(), loading = false, textMissing = true
                )
                return@launch
            }
            wpm = entry.wpm
            // Default, not IO: paragraphs() is CPU-bound, not blocking. Run
            // inline on viewModelScope (Main.immediate) it is two regex passes
            // plus an object per word over the whole book, which visibly stalled
            // opening Browse on a long one — over a second on a 130k-word EPUB.
            val paragraphs = withContext(Dispatchers.Default) { RsvpEngine.paragraphs(text) }
            _ui.value = BrowseUiState(
                title = entry.title,
                paragraphs = paragraphs,
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
