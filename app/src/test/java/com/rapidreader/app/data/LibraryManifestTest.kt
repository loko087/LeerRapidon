package com.rapidreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryManifestTest {

    private fun book(id: String, archived: Boolean) = BookEntity(
        id = id, title = "T$id", source = "text", wordCount = 10, idx = 3,
        wpm = 300, updatedAt = 5L, archived = archived
    )

    @Test
    fun archivedFlagRoundTrips() {
        val parsed = LibraryManifest.parse(
            LibraryManifest.write(listOf(book("a", true), book("b", false)), createdAt = 1L)
        )
        assertEquals(listOf(book("a", true), book("b", false)), parsed.books)
    }

    @Test
    fun backupWithoutArchivedKeyReadsAsUnarchived() {
        val old = """{"format":1,"createdAt":1,"books":[{"id":"x","title":"Old"}]}"""
        assertFalse(LibraryManifest.parse(old).books.single().archived)
    }

    @Test
    fun archivedTrueIsWrittenAsJsonBoolean() {
        assertTrue(LibraryManifest.write(listOf(book("a", true))).contains("\"archived\": true"))
    }
}
