package com.rapidreader.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidreader.app.data.BookEntity
import com.rapidreader.app.data.BookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository(app)

    val books: StateFlow<List<BookEntity>> = repo.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Backfills covers for books saved before cover thumbnails existed.
        // LibraryViewModel lives for the whole app session (library is the
        // start destination and stays on the back stack), so this fires
        // once per launch rather than every time the library is revisited.
        viewModelScope.launch { repo.backfillMissingCovers() }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.deleteBook(id) }
    }

    fun coverFile(book: BookEntity): File? = repo.resolveCover(book.coverPath)
}
