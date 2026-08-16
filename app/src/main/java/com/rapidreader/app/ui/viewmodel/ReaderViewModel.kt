package com.rapidreader.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidreader.app.data.BookRepository
import com.rapidreader.app.data.OriginalKind
import com.rapidreader.app.data.originalKind
import com.rapidreader.app.rsvp.RsvpEngine
import com.rapidreader.app.tts.SpeechController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReaderUiState(
    val title: String = "",
    val words: List<String> = emptyList(),
    val idx: Int = 0,
    val wpm: Int = 300,
    val playing: Boolean = false,
    val audioMode: Boolean = false,
    val ttsOk: Boolean = true,
    val loading: Boolean = true,
    val originalKind: OriginalKind? = null
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository(app)
    private val speech = SpeechController(app)

    private val _ui = MutableStateFlow(ReaderUiState())
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()

    private var bookId: String? = null
    private var playJob: Job? = null
    private var saveJob: Job? = null
    private var offs: List<Int> = emptyList()
    private var joined: String = ""

    fun load(id: String) {
        if (bookId == id && !_ui.value.loading) return
        bookId = id
        viewModelScope.launch {
            val entry = repo.getBook(id) ?: return@launch
            val text = repo.getBookText(id)
            val words = RsvpEngine.tokenize(text)
            joined = words.joinToString(" ")
            var pos = 0
            offs = words.map { w -> val p = pos; pos += w.length + 1; p }
            _ui.value = ReaderUiState(
                title = entry.title,
                words = words,
                idx = entry.idx.coerceIn(0, (words.size - 1).coerceAtLeast(0)),
                wpm = entry.wpm,
                loading = false,
                originalKind = entry.originalKind()
            )
        }
    }

    fun play() {
        _ui.value = _ui.value.copy(playing = true)
        if (_ui.value.audioMode) speakFrom(_ui.value.idx) else visualTick()
    }

    fun pause() {
        playJob?.cancel()
        speech.stop()
        _ui.value = _ui.value.copy(playing = false)
        persist()
    }

    fun scrub(newIdx: Int) {
        playJob?.cancel()
        speech.stop()
        _ui.value = _ui.value.copy(idx = newIdx, playing = false)
        schedulePersist()
    }

    fun setWpm(wpm: Int) {
        _ui.value = _ui.value.copy(wpm = wpm)
        if (_ui.value.playing) {
            playJob?.cancel(); speech.stop()
            if (_ui.value.audioMode) speakFrom(_ui.value.idx) else visualTick()
        }
        schedulePersist()
    }

    fun setAudioMode(on: Boolean) {
        playJob?.cancel(); speech.stop()
        _ui.value = _ui.value.copy(audioMode = on, playing = false)
    }

    private fun visualTick() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (true) {
                val s = _ui.value
                if (s.idx >= s.words.size - 1) { _ui.value = s.copy(playing = false); persist(); break }
                delay(RsvpEngine.wordDelayMs(s.words[s.idx], s.wpm))
                val next = s.idx + 1
                _ui.value = _ui.value.copy(idx = next)
                if (next % 15 == 0) schedulePersist()
            }
        }
    }

    private fun speakFrom(startIdx: Int) {
        val startChar = offs.getOrElse(startIdx) { 0 }
        val chunk = joined.substring(startChar.coerceAtMost(joined.length))
        val rate = _ui.value.wpm / 180f
        speech.speak(
            text = chunk,
            rate = rate,
            onRangeStart = { charIndex ->
                val abs = startChar + charIndex
                var lo = 0
                for (k in offs.indices) { if (offs[k] <= abs) lo = k else break }
                _ui.value = _ui.value.copy(idx = lo, ttsOk = true)
                if (lo % 15 == 0) schedulePersist()
            },
            onDone = {
                _ui.value = _ui.value.copy(playing = false)
                persist()
            },
            onError = {
                _ui.value = _ui.value.copy(ttsOk = false)
                visualTick()
            }
        )
    }

    private fun schedulePersist() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1500); persist() }
    }

    private fun persist() {
        val id = bookId ?: return
        val s = _ui.value
        viewModelScope.launch { repo.updateProgress(id, s.idx, s.wpm) }
    }

    override fun onCleared() {
        playJob?.cancel()
        speech.shutdown()
        persist()
        super.onCleared()
    }
}
