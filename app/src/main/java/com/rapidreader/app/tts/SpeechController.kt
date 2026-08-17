package com.rapidreader.app.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Wraps Android's native TextToSpeech engine. Uses onRangeStart (API 26+) for
 * real word-boundary callbacks, which is more reliable than the Web Speech
 * API's boundary events used in the earlier web prototype.
 */
class SpeechController(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.language = Locale.getDefault()
        }
    }

    fun speak(
        text: String,
        rate: Float,
        onRangeStart: (charIndex: Int) -> Unit,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        val engine = tts
        if (engine == null || !ready) { onError(); return }
        engine.setSpeechRate(rate.coerceIn(0.5f, 3f))

        val maxLen = TextToSpeech.getMaxSpeechInputLength().let { if (it > 200) it - 100 else 3800 }
        val chunks = chunkText(text, maxLen)
        if (chunks.isEmpty()) { onDone(); return }

        val utteranceIds = chunks.map { UUID.randomUUID().toString() }
        val offsetsByUtteranceId = mutableMapOf<String, Int>()
        var cumulativeOffset = 0
        for ((i, chunk) in chunks.withIndex()) {
            offsetsByUtteranceId[utteranceIds[i]] = cumulativeOffset
            cumulativeOffset += chunk.length + 1 // approximate — good enough for highlight sync
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == utteranceIds.last()) onDone()
            }
            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onError(utteranceId: String?) { if (utteranceId in offsetsByUtteranceId) onError() }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                // Once a newer speak() call replaces this listener, Android can still
                // deliver in-flight callbacks from the previous utterance to it — ignore
                // anything not belonging to this call rather than treating it as offset 0.
                val base = offsetsByUtteranceId[utteranceId] ?: return
                onRangeStart(base + start)
            }
        })

        for ((i, chunk) in chunks.withIndex()) {
            val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val ok = engine.speak(chunk, queueMode, Bundle(), utteranceIds[i])
            if (ok == TextToSpeech.ERROR) { onError(); return }
        }
    }

    private fun chunkText(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + maxLen).coerceAtMost(text.length)
            if (end < text.length) {
                val lastSpace = text.lastIndexOf(' ', end)
                if (lastSpace > start) end = lastSpace
            }
            chunks.add(text.substring(start, end))
            start = end
            while (start < text.length && text[start] == ' ') start++
        }
        return chunks
    }
    fun stop() { tts?.stop() }

    fun shutdown() { tts?.shutdown() }
}
