package com.rapidreader.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val source: String,
    val wordCount: Int,
    val idx: Int,
    val wpm: Int,
    val updatedAt: Long,
    // Name (relative to <filesDir>/books) of the preserved original: "<id>.pdf"
    // or the directory "<id>_epub". Null when no original form applies (pasted
    // text, .txt) or the copy failed. Relative, not absolute, so it survives a
    // backup/restore into a different data dir (allowBackup="true" is set).
    val originalPath: String? = null,
    // Last position in original-form mode: PDF page index, or EPUB spine index.
    // Null = never opened in that mode. Deliberately separate from `idx`, which
    // is the RSVP word index.
    val originalPos: Int? = null
)

enum class OriginalKind { PDF, EPUB }

/** Which original-form viewer (if any) this book supports. */
fun BookEntity.originalKind(): OriginalKind? = when {
    originalPath.isNullOrEmpty() -> null
    source == "epub" -> OriginalKind.EPUB
    source.startsWith("pdf") -> OriginalKind.PDF // covers "pdf" and "pdf-ocr"
    else -> null
}
