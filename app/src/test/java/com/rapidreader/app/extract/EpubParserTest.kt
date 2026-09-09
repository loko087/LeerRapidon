package com.rapidreader.app.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [EpubParser.normalizePath] is the function the zip-slip defence in
 * `TextExtractor.extractEpub` and `OriginalStore.stageEpub` rests on: it
 * resolves an EPUB-internal href against the OPF's directory and, per its own
 * doc comment, collapses "." / ".." so "the result can't escape the archive
 * root". That property is what these tests check directly, rather than only
 * through the resolved path's happy-path shape.
 */
class EpubParserTest {

    @Test
    fun `resolves a plain relative href against the opf directory`() {
        assertEquals("OEBPS/chapter1.xhtml", EpubParser.normalizePath("OEBPS", "chapter1.xhtml"))
    }

    @Test
    fun `an empty opf directory means the href is already root-relative`() {
        assertEquals("chapter1.xhtml", EpubParser.normalizePath("", "chapter1.xhtml"))
    }

    @Test
    fun `current-directory segments are dropped`() {
        assertEquals("OEBPS/chapter1.xhtml", EpubParser.normalizePath("OEBPS", "./chapter1.xhtml"))
    }

    @Test
    fun `one level of parent-directory reference steps out of a subdirectory`() {
        assertEquals("OEBPS/images/cover.jpg", EpubParser.normalizePath("OEBPS/text", "../images/cover.jpg"))
    }

    @Test
    fun `multiple parent-directory references collapse across several ancestors`() {
        assertEquals("a/x.jpg", EpubParser.normalizePath("a/b/c", "../../x.jpg"))
    }

    @Test
    fun `parent-directory references cannot climb above the archive root`() {
        // Only one real directory ("OEBPS") exists to climb out of, but the
        // href asks to go up four levels — the extra ".." segments must be
        // absorbed rather than turning into a path that escapes upward.
        val result = EpubParser.normalizePath("OEBPS", "../../../../etc/passwd")

        assertEquals("etc/passwd", result)
        assertFalse("must not contain a parent-directory segment", result.contains(".."))
        assertFalse("must not start outside the resolved tree", result.startsWith("/"))
    }

    @Test
    fun `parent-directory references with no opf directory at all are absorbed the same way`() {
        val result = EpubParser.normalizePath("", "../../secrets.txt")

        assertEquals("secrets.txt", result)
        assertFalse(result.contains(".."))
    }
}
