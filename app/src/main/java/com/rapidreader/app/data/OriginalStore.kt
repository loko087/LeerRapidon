package com.rapidreader.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

private const val MAX_EPUB_UNCOMPRESSED_BYTES = 300L * 1024 * 1024
private const val MAX_EPUB_ENTRIES = 10_000

/** A preserved original sitting in the staging area, not yet committed to a book id. */
data class StagedOriginal(val kind: OriginalKind, val root: File, val bytes: Long)

/**
 * Preserves, commits, resolves, and deletes original PDF/EPUB files so
 * "original form" reading can show the book as-is later. Every failure path
 * returns null rather than throwing — preserving the original is layered on
 * top of text extraction, never a reason to fail an otherwise-good import.
 */
class OriginalStore(private val booksDir: File) {

    private val stagingDir get() = File(booksDir, ".staging")

    /** Copies the picked file's bytes out of the SAF Uri into the staging area. */
    fun stage(context: Context, uri: Uri, ext: String, declaredSize: Long?): StagedOriginal? {
        clearStaging()
        // A 200MB file is fine with 40GB free and a bad idea with 300MB free.
        if (declaredSize != null && declaredSize > booksDir.usableSpace / 2) return null
        return when (ext) {
            "pdf" -> stagePdf(context, uri)
            "epub" -> stageEpub(context, uri)
            else -> null
        }
    }

    private fun stagePdf(context: Context, uri: Uri): StagedOriginal? = try {
        stagingDir.mkdirs()
        val target = File(stagingDir, "original.pdf")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
        if (copied == null) null else StagedOriginal(OriginalKind.PDF, target, target.length())
    } catch (_: Exception) {
        null
    }

    private fun stageEpub(context: Context, uri: Uri): StagedOriginal? = try {
        val destDir = File(stagingDir, "epub").apply { mkdirs() }
        val destCanonical = destDir.canonicalPath + File.separator
        var totalWritten = 0L
        var entryCount = 0
        var aborted = false

        val opened = context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null && !aborted) {
                    if (!entry.isDirectory) {
                        entryCount++
                        val target = File(destDir, entry.name)
                        // Zip-slip guard: an entry like "../../databases/rapid_reader.db"
                        // must not be able to write outside destDir.
                        if (entryCount > MAX_EPUB_ENTRIES || !target.canonicalPath.startsWith(destCanonical)) {
                            if (entryCount > MAX_EPUB_ENTRIES) aborted = true
                        } else {
                            target.parentFile?.mkdirs()
                            val buffer = ByteArray(8192)
                            target.outputStream().use { out ->
                                var count: Int
                                while (zis.read(buffer).also { count = it } != -1) {
                                    totalWritten += count
                                    if (totalWritten > MAX_EPUB_UNCOMPRESSED_BYTES) {
                                        aborted = true
                                        break
                                    }
                                    out.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = if (aborted) null else zis.nextEntry
                }
            }
        }
        if (opened == null || aborted) null
        else StagedOriginal(OriginalKind.EPUB, destDir, destDir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
    } catch (_: Exception) {
        null
    }

    /** Moves a staged original into place under [bookId]; returns the booksDir-relative name. */
    fun commit(staged: StagedOriginal, bookId: String): String? = try {
        val dest = when (staged.kind) {
            OriginalKind.PDF -> File(booksDir, "$bookId.pdf")
            OriginalKind.EPUB -> File(booksDir, "${bookId}_epub")
        }
        if (staged.root.renameTo(dest)) dest.name else null
    } catch (_: Exception) {
        null
    }

    /** Resolves a stored relative name to a File, or null if it's gone. */
    fun resolve(relativeName: String?): File? {
        if (relativeName.isNullOrEmpty()) return null
        val f = File(booksDir, relativeName)
        return if (f.exists()) f else null
    }

    /** Deletes any original artifacts for [bookId] (file or directory tree). */
    fun delete(bookId: String) {
        File(booksDir, "$bookId.pdf").delete()
        File(booksDir, "${bookId}_epub").deleteRecursively()
    }

    fun clearStaging() {
        stagingDir.deleteRecursively()
    }
}
