package com.rapidreader.app.rsvp

/** A word from [RsvpEngine.paragraphs], carrying its index into the flat
 *  word list [RsvpEngine.tokenize] produces from the same text — so tapping
 *  a word in a paragraph view maps directly onto an RSVP reading position. */
data class BrowseWord(val text: String, val wordIndex: Int)

object RsvpEngine {
    fun tokenize(text: String): List<String> =
        text.replace(Regex("\\s+"), " ").trim().split(" ").filter { it.isNotEmpty() }

    // Caps how many words a single paragraph block can carry: without it, a
    // source with no blank lines at all (e.g. a badly-OCR'd PDF) would become
    // one giant unvirtualized LazyColumn item and stall the browse screen.
    private const val MAX_PARAGRAPH_WORDS = 120

    /** Groups the same words [tokenize] would produce into paragraphs (split on
     *  blank lines) for the full-text browse view. Word order/count is
     *  identical to [tokenize] on the same text — only where line breaks
     *  land differs — so a paragraph's [BrowseWord.wordIndex] values are
     *  valid RSVP word indices into that parallel list. */
    fun paragraphs(text: String): List<List<BrowseWord>> {
        val blocks = text.split(Regex("\n\\s*\n+"))
        val result = mutableListOf<List<BrowseWord>>()
        var wordIdx = 0
        for (block in blocks) {
            val blockWords = Regex("\\S+").findAll(block).map { m -> BrowseWord(m.value, wordIdx++) }.toList()
            if (blockWords.isEmpty()) continue
            blockWords.chunked(MAX_PARAGRAPH_WORDS).forEach { result.add(it) }
        }
        return result
    }

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
