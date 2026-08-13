package com.rapidreader.app.rsvp

object RsvpEngine {
    fun tokenize(text: String): List<String> =
        text.replace(Regex("\\s+"), " ").trim().split(" ").filter { it.isNotEmpty() }

    fun orpIndex(word: String): Int {
        val len = word.count { it.isLetterOrDigit() }.let { if (it == 0) word.length else it }
        return when {
            len <= 1 -> 0
            len <= 5 -> 1
            len <= 9 -> 2
            len <= 13 -> 3
            else -> 4
        }
    }

    fun wordDelayMs(word: String, wpm: Int): Long {
        var ms = 60000.0 / wpm
        if (word.length > 8) ms *= 1.3
        if (Regex("[,;:]$").containsMatchIn(word)) ms *= 1.6
        if (Regex("[.!?…]\"?$").containsMatchIn(word)) ms *= 2.2
        return ms.toLong()
    }
}
