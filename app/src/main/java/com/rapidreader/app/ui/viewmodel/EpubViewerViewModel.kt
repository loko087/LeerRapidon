package com.rapidreader.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidreader.app.data.BookRepository
import com.rapidreader.app.extract.EpubParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class EpubUiState(
    val title: String = "",
    val chapterUrls: List<String> = emptyList(),
    val index: Int = 0,
    val loading: Boolean = true,
    val error: String? = null
)

class EpubViewerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository(app)
    private val _ui = MutableStateFlow(EpubUiState())
    val ui: StateFlow<EpubUiState> = _ui.asStateFlow()

    private var bookId: String? = null
    private var saveJob: Job? = null

    fun load(id: String) {
        if (bookId == id && !_ui.value.loading) return
        bookId = id
        viewModelScope.launch {
            val entry = repo.getBook(id)
            val dir = repo.getOriginalFile(id)
            if (entry == null || dir == null) {
                _ui.value = EpubUiState(loading = false, error = "Original file is no longer available.")
                return@launch
            }
            try {
                val structure = EpubParser.parse { path -> File(dir, path).takeIf { it.isFile }?.readBytes() }
                // Uri.fromFile, never manual "file://" + path concatenation — real
                // filenames contain spaces/#/& that naive concatenation corrupts.
                val urls = structure.spinePaths.map { Uri.fromFile(File(dir, it)).toString() }
                if (urls.isEmpty()) {
                    _ui.value = EpubUiState(loading = false, error = "No readable chapters found in this EPUB.")
                    return@launch
                }
                _ui.value = EpubUiState(
                    title = entry.title,
                    chapterUrls = urls,
                    index = (entry.originalPos ?: 0).coerceIn(0, urls.lastIndex),
                    loading = false
                )
            } catch (e: Exception) {
                _ui.value = EpubUiState(
                    loading = false,
                    error = "Couldn't open this EPUB" + (e.message?.let { ": $it" } ?: ".")
                )
            }
        }
    }

    fun goTo(i: Int) {
        val s = _ui.value
        val newIndex = i.coerceIn(0, (s.chapterUrls.size - 1).coerceAtLeast(0))
        if (newIndex == s.index) return
        _ui.value = s.copy(index = newIndex)
        schedulePersist()
    }

    fun next() = goTo(_ui.value.index + 1)
    fun prev() = goTo(_ui.value.index - 1)

    /** Maps an in-book link target back to a spine index, or null if external/unknown. */
    fun indexOfUrl(url: String): Int? {
        val stripped = url.substringBefore('#')
        val i = _ui.value.chapterUrls.indexOf(stripped)
        return if (i >= 0) i else null
    }

    private fun schedulePersist() {
        val id = bookId ?: return
        val index = _ui.value.index
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repo.updateOriginalPos(id, index)
        }
    }
}
