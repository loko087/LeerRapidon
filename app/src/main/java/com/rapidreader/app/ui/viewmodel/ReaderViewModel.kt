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
    val wordsPerFrame: Int = 1,
    // In audio mode, which word within the current frame (idx-relative) speech
    // is on right now. Drives the pivot highlight so it tracks the voice.
    val spokenOffset: Int = 0,
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
    // Bumped on every stop/restart of speech so callbacks from a superseded
    // utterance (which Android can still deliver after the listener moves on)
    // are recognized as stale and dropped instead of corrupting idx.
    private var playGeneration = 0

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
        val s = _ui.value
        if (s.audioMode) speakFromIdx(s.idx + s.spokenOffset) else visualTick()
    }

    fun pause() {
        playGeneration++
        playJob?.cancel()
        speech.stop()
        _ui.value = _ui.value.copy(playing = false)
        persist()
    }

    fun scrub(newIdx: Int) {
        playGeneration++
        playJob?.cancel()
        speech.stop()
        _ui.value = _ui.value.copy(idx = newIdx, playing = false, spokenOffset = 0)
        schedulePersist()
    }

    fun setWpm(wpm: Int) {
        _ui.value = _ui.value.copy(wpm = wpm)
        if (_ui.value.playing) {
            playJob?.cancel(); speech.stop()
            val s = _ui.value
            if (s.audioMode) speakFromIdx(s.idx + s.spokenOffset) else visualTick()
        }
        schedulePersist()
    }

    fun setAudioMode(on: Boolean) {
        playGeneration++
        playJob?.cancel(); speech.stop()
        _ui.value = _ui.value.copy(audioMode = on, playing = false, spokenOffset = 0)
    }

    fun setWordsPerFrame(count: Int) {
        _ui.value = _ui.value.copy(wordsPerFrame = count.coerceIn(1, 5))
        if (_ui.value.playing) {
            playJob?.cancel(); speech.stop()
            val s = _ui.value
            if (s.audioMode) speakFromIdx(s.idx + s.spokenOffset) else visualTick()
        }
    }

    private fun visualTick() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (true) {
                val s = _ui.value
                if (s.idx >= s.words.size - 1) { _ui.value = s.copy(playing = false); persist(); break }
                val step = s.wordsPerFrame.coerceIn(1, s.words.size - s.idx)
                val frameDelay = (s.idx until s.idx + step).sumOf { RsvpEngine.wordDelayMs(s.words[it], s.wpm) }
                delay(frameDelay)
                val next = (s.idx + step).coerceAtMost(s.words.size - 1)
                _ui.value = _ui.value.copy(idx = next)
                if (next % 15 == 0) schedulePersist()
            }
        }
    }

    /** Speaks continuously from startIdx to the end of the book — one utterance,
     *  so the configured speed stays audible instead of being swamped by
     *  per-utterance engine startup latency. The on-screen frame (grouped in
     *  wordsPerFrame-sized chunks, realigned to startIdx) tracks the word
     *  speech is actually on, so audio mode still reads what's displayed. */
    private fun speakFromIdx(startIdx: Int) {
        val words = _ui.value.words
        if (words.isEmpty()) { _ui.value = _ui.value.copy(playing = false); persist(); return }
        val myGen = ++playGeneration
        val anchor = startIdx.coerceIn(0, words.size - 1)
        val startChar = offs.getOrElse(anchor) { 0 }.coerceAtMost(joined.length)
        val chunk = joined.substring(startChar)
        val rate = _ui.value.wpm / 180f
        speech.speak(
            text = chunk,
            rate = rate,
            onRangeStart = { charIndex ->
                if (myGen == playGeneration) {
                    val abs = startChar + charIndex
                    var lo = anchor
                    for (k in offs.indices) { if (offs[k] <= abs) lo = k else break }
                    val step = _ui.value.wordsPerFrame.coerceAtLeast(1)
                    val frameStart = anchor + ((lo - anchor) / step) * step
                    _ui.value = _ui.value.copy(idx = frameStart, spokenOffset = lo - frameStart, ttsOk = true)
                    if (lo % 15 == 0) schedulePersist()
                }
            },
            onDone = {
                if (myGen == playGeneration) {
                    _ui.value = _ui.value.copy(playing = false)
                    persist()
                }
            },
            onError = {
                if (myGen == playGeneration) {
                    _ui.value = _ui.value.copy(ttsOk = false)
                    visualTick()
                }
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
