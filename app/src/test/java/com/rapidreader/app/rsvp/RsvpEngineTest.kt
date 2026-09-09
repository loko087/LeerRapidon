package com.rapidreader.app.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RsvpEngine.paragraphs] carries a load-bearing claim in its own doc comment:
 * word order and count are "identical" to [RsvpEngine.tokenize] on the same
 * text, which is what makes a [BrowseWord.wordIndex] a valid index into the
 * RSVP reader's word list — the whole tap-to-jump feature in
 * `BrowseTextScreen` depends on that being true. It was previously asserted
 * only in a comment; these tests actually check it, including across the
 * MAX_PARAGRAPH_WORDS chunking boundary, which is the one place a naive
 * reimplementation could plausibly reset or duplicate the index.
 */
class RsvpEngineTest {

    // ---- tokenize ----------------------------------------------------

    @Test
    fun `tokenize splits on whitespace and drops empties`() {
        assertEquals(listOf("one", "two", "three"), RsvpEngine.tokenize("one  two\tthree"))
    }

    @Test
    fun `tokenize collapses newlines and trims leading and trailing whitespace`() {
        assertEquals(listOf("a", "b"), RsvpEngine.tokenize("\n  a\n\nb  \n"))
    }

    @Test
    fun `tokenize of blank text is empty`() {
        assertTrue(RsvpEngine.tokenize("   \n\t  ").isEmpty())
    }

    // ---- paragraphs: the cross-reference invariant --------------------

    @Test
    fun `paragraphs word sequence matches tokenize exactly`() {
        val text = "Hello world, this is one paragraph.\n\n" +
            "Here is a second paragraph\nspanning two lines.\n\n\n" +
            "A third, after extra blank lines."

        val flatFromTokenize = RsvpEngine.tokenize(text)
        val flatFromParagraphs = RsvpEngine.paragraphs(text).flatten()

        assertEquals(flatFromTokenize.size, flatFromParagraphs.size)
        flatFromParagraphs.forEachIndexed { i, word ->
            assertEquals("wordIndex at position $i", i, word.wordIndex)
            assertEquals("text at position $i", flatFromTokenize[i], word.text)
        }
    }

    @Test
    fun `paragraphs indices stay sequential across a paragraph split by MAX_PARAGRAPH_WORDS`() {
        // 300 words in one blank-line-free block: guaranteed to cross the
        // internal 120-word chunk cap at least twice, which is exactly the
        // path a naive implementation might reset wordIndex on.
        val words = (1..300).map { "word$it" }
        val text = words.joinToString(" ")

        val paragraphs = RsvpEngine.paragraphs(text)
        val flat = paragraphs.flatten()

        assertEquals(words, flat.map { it.text })
        assertEquals((0 until 300).toList(), flat.map { it.wordIndex })
        // Confirms the cap actually did something, i.e. this test would catch
        // a regression that removed chunking rather than passing vacuously.
        assertTrue("expected more than one chunk", paragraphs.size > 1)
    }

    @Test
    fun `paragraphs skips blank blocks without leaving a gap in the index sequence`() {
        val text = "First para.\n\n\n\n\nSecond para after several blank lines."
        val flat = RsvpEngine.paragraphs(text).flatten()

        assertEquals(listOf("First", "para.", "Second", "para", "after", "several", "blank", "lines."),
            flat.map { it.text })
        assertEquals((0..7).toList(), flat.map { it.wordIndex })
    }

    @Test
    fun `paragraphs of blank text is empty`() {
        assertTrue(RsvpEngine.paragraphs("\n\n   \n\n").isEmpty())
    }

    // ---- orpIndex ------------------------------------------------------

    @Test
    fun `orpIndex follows the documented length buckets`() {
        assertEquals(0, RsvpEngine.orpIndex("a"))
        assertEquals(1, RsvpEngine.orpIndex("cat"))
        assertEquals(1, RsvpEngine.orpIndex("aaaaa")) // len 5, upper edge of bucket 1
        assertEquals(2, RsvpEngine.orpIndex("aaaaaa"))  // len 6, lower edge of bucket 2
        assertEquals(2, RsvpEngine.orpIndex("aaaaaaaaa")) // len 9, upper edge
        assertEquals(3, RsvpEngine.orpIndex("aaaaaaaaaa")) // len 10, lower edge of bucket 3
        assertEquals(3, RsvpEngine.orpIndex("a".repeat(13)))
        assertEquals(4, RsvpEngine.orpIndex("a".repeat(14)))
    }

    @Test
    fun `orpIndex counts only letters and digits, ignoring surrounding punctuation`() {
        // `"magic,"` is 8 raw characters (5 letters + a leading quote, a comma,
        // and a trailing quote) — bucket 2 if punctuation were counted, but
        // only 5 characters are alphanumeric, which is bucket 1, same as
        // "magic" alone.
        assertEquals(RsvpEngine.orpIndex("magic"), RsvpEngine.orpIndex("\"magic,\""))
    }

    @Test
    fun `orpIndex falls back to raw length for a word with no letters or digits`() {
        // "—" has zero alphanumeric characters; the fallback (word.length) must
        // kick in rather than treating it as length 0 (which would also be
        // bucket 0, so use a longer punctuation-only word to distinguish them).
        val punctuationOnly = "-----" // length 5, zero alphanumeric
        assertEquals(RsvpEngine.orpIndex("aaaaa"), RsvpEngine.orpIndex(punctuationOnly))
    }

    // ---- wordDelayMs ----------------------------------------------------

    @Test
    fun `wordDelayMs at baseline is 60000 over wpm`() {
        assertEquals(200L, RsvpEngine.wordDelayMs("hi", 300))
        assertEquals(600L, RsvpEngine.wordDelayMs("hi", 100))
    }

    @Test
    fun `wordDelayMs applies the long-word multiplier past 8 characters`() {
        val base = 60000.0 / 300
        assertEquals((base * 1.3).toLong(), RsvpEngine.wordDelayMs("nineletrs", 300)) // len 9
        assertEquals(base.toLong(), RsvpEngine.wordDelayMs("eightlet", 300)) // len 8, not > 8
    }

    @Test
    fun `wordDelayMs applies the comma-semicolon-colon pause only at the end of the word`() {
        val base = 60000.0 / 300
        assertEquals((base * 1.6).toLong(), RsvpEngine.wordDelayMs("word,", 300))
        assertEquals((base * 1.6).toLong(), RsvpEngine.wordDelayMs("word;", 300))
        assertEquals((base * 1.6).toLong(), RsvpEngine.wordDelayMs("word:", 300))
        // Comma isn't trailing, so no pause multiplier applies.
        assertEquals(base.toLong(), RsvpEngine.wordDelayMs("wo,rd", 300))
    }

    @Test
    fun `wordDelayMs applies the larger sentence-ending pause, optionally before a closing quote`() {
        val base = 60000.0 / 300
        assertEquals((base * 2.2).toLong(), RsvpEngine.wordDelayMs("word.", 300))
        assertEquals((base * 2.2).toLong(), RsvpEngine.wordDelayMs("word!", 300))
        assertEquals((base * 2.2).toLong(), RsvpEngine.wordDelayMs("word?", 300))
        assertEquals((base * 2.2).toLong(), RsvpEngine.wordDelayMs("word…", 300)) // …
        assertEquals((base * 2.2).toLong(), RsvpEngine.wordDelayMs("word.\"", 300))
    }

    @Test
    fun `wordDelayMs combines the long-word and sentence-end multipliers`() {
        val base = 60000.0 / 300
        // "wonderful." is 10 letters plus a period: long-word (>8) AND sentence-ending.
        assertEquals((base * 1.3 * 2.2).toLong(), RsvpEngine.wordDelayMs("wonderful.", 300))
    }
}
