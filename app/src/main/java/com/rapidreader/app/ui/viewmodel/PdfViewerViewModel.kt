package com.rapidreader.app.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidreader.app.data.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PdfUiState(
    val title: String = "",
    val pageCount: Int = 0,
    val initialPage: Int = 0,
    val loading: Boolean = true,
    val error: String? = null
)

class PdfViewerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository(app)
    private val _ui = MutableStateFlow(PdfUiState())
    val ui: StateFlow<PdfUiState> = _ui.asStateFlow()

    // PdfRenderer is not thread-safe and allows only one open page at a time.
    private val renderLock = Mutex()
    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var closed = false
    private var bookId: String? = null
    private var saveJob: Job? = null

    fun load(id: String) {
        if (bookId == id && !_ui.value.loading) return
        bookId = id
        viewModelScope.launch {
            val entry = repo.getBook(id)
            val file = repo.getOriginalFile(id)
            if (entry == null || file == null) {
                _ui.value = PdfUiState(loading = false, error = "Original file is no longer available.")
                return@launch
            }
            try {
                val descriptor = withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
                val r = withContext(Dispatchers.IO) { PdfRenderer(descriptor) }
                pfd = descriptor
                renderer = r
                _ui.value = PdfUiState(
                    title = entry.title,
                    pageCount = r.pageCount,
                    initialPage = (entry.originalPos ?: 0).coerceIn(0, (r.pageCount - 1).coerceAtLeast(0)),
                    loading = false
                )
            } catch (e: Exception) {
                _ui.value = PdfUiState(
                    loading = false,
                    error = "Couldn't open this PDF" + (e.message?.let { ": $it" } ?: ".")
                )
            }
        }
    }

    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        renderLock.withLock {
            val r = renderer
            if (r == null || closed || targetWidthPx <= 0) return@withLock null
            r.openPage(index).use { page ->
                val w = targetWidthPx
                val h = (w.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                // ARGB_8888 is mandatory for PdfRenderer, and it doesn't clear the
                // bitmap itself — without this, transparent PDF backgrounds render
                // onto a transparent bitmap and look broken on the dark theme.
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    fun onPageSettled(index: Int) {
        val id = bookId ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repo.updateOriginalPos(id, index)
        }
    }

    override fun onCleared() {
        val r = renderer
        val p = pfd
        renderer = null
        pfd = null
        // viewModelScope is already cancelled by the time onCleared runs, so a
        // close launched there would never execute — use a detached scope, or
        // an in-flight render's fd gets leaked instead of closed.
        CoroutineScope(Dispatchers.IO).launch {
            renderLock.withLock {
                closed = true
                runCatching { r?.close() }
                runCatching { p?.close() }
            }
        }
        super.onCleared()
    }
}
