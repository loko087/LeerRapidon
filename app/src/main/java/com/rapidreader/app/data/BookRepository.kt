package com.rapidreader.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.rapidreader.app.extract.OpenLibraryCovers
import com.rapidreader.app.extract.TextExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Outcome of a restore: books the archive put back, and books it took away. */
data class RestoreResult(val restored: Int, val removed: Int)

/**
 * What an archive says about itself, read before anything is unpacked, so the
 * user can be told what they are about to restore — including [willRemove],
 * the books on this device that the archive does not contain and so will lose.
 */
data class BackupPreview(
    val createdAt: Long,
    val bookCount: Int,
    val willRemove: Int
)

class BookRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.bookDao()
    private val booksDir: File by lazy {
        File(appContext.filesDir, "books").apply { mkdirs() }
    }
    private val originals by lazy { OriginalStore(booksDir) }
    private val backup by lazy { LibraryBackup(booksDir) }
    /** Holds the library as it was before the last restore, for a single undo. */
    private val undoDir: File by lazy { File(appContext.filesDir, "undo") }
    // Outlives any single addBook() call so the background Open Library
    // lookup isn't cancelled if the screen that triggered the import closes.
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeBooks(): Flow<List<BookEntity>> = dao.getAll()

    suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived)

    suspend fun getBook(id: String): BookEntity? = dao.getById(id)

    /** The book's extracted text, or null when the file behind the row is gone.
     *  Returning "" instead would render as a real book with no words in it —
     *  same null-for-absent convention as [resolveCover] and [getOriginalFile]. */
    suspend fun getBookText(id: String): String? = withContext(Dispatchers.IO) {
        val f = File(booksDir, "$id.txt")
        if (f.exists()) f.readText() else null
    }

    /** Preserve the picked file's bytes before extraction commits to anything.
     *  Returns null if the original can't be preserved — a non-fatal outcome. */
    suspend fun stageOriginal(uri: Uri, ext: String, declaredSize: Long?): StagedOriginal? =
        withContext(Dispatchers.IO) {
            originals.stage(appContext, uri, ext, declaredSize)
        }

    /** Discards a staged original that was never committed (e.g. import failed/was reset). */
    suspend fun clearStagedOriginal() = withContext(Dispatchers.IO) {
        originals.clearStaging()
    }

    suspend fun addBook(
        title: String,
        source: String,
        text: String,
        wordCount: Int,
        staged: StagedOriginal? = null,
        cover: ByteArray? = null
    ): String = withContext(Dispatchers.IO) {
        val id = "b_" + System.currentTimeMillis().toString(36) +
            (1..5).map { ('a'..'z').random() }.joinToString("")
        File(booksDir, "$id.txt").writeText(text)
        val originalPath = staged?.let { originals.commit(it, id) }
        val coverPath = cover?.let { saveCover(id, it) }
        dao.upsert(
            BookEntity(
                id = id,
                title = title,
                source = source,
                wordCount = wordCount,
                idx = 0,
                wpm = 300,
                updatedAt = System.currentTimeMillis(),
                originalPath = originalPath,
                coverPath = coverPath
            )
        )
        // The file itself had no cover art (plain text, a paste, or an EPUB
        // without one) — try Open Library in the background. Non-fatal and
        // never blocks the import: the Flow-backed library list just updates
        // in place if a match turns up.
        if (coverPath == null) {
            repoScope.launch { fetchCoverFromOpenLibrary(id, title) }
        }
        id
    }

    private suspend fun fetchCoverFromOpenLibrary(id: String, title: String) {
        val bytes = OpenLibraryCovers.findByTitle(title) ?: return
        val coverPath = saveCover(id, bytes) ?: return
        dao.updateCover(id, coverPath)
    }

    /** Retroactively fills in covers for books saved before cover thumbnails
     *  existed. Same priority as a fresh import — the preserved original
     *  (PDF first page / EPUB embedded cover) first, Open Library title
     *  search otherwise — just run against rows already in the DB. Meant to
     *  be fired once in the background per app session; cheap and harmless
     *  to repeat since it only ever looks at books still missing a cover. */
    suspend fun backfillMissingCovers() = withContext(Dispatchers.IO) {
        dao.getAll().first().filter { it.coverPath == null }.forEach { book ->
            val bytes = coverFromOriginal(book) ?: OpenLibraryCovers.findByTitle(book.title)
            val coverPath = bytes?.let { saveCover(book.id, it) } ?: return@forEach
            dao.updateCover(book.id, coverPath)
        }
    }

    private suspend fun coverFromOriginal(book: BookEntity): ByteArray? {
        val kind = book.originalKind() ?: return null
        val file = getOriginalFile(book.id) ?: return null
        return when (kind) {
            OriginalKind.PDF -> TextExtractor.renderPdfCover(file)
            OriginalKind.EPUB -> TextExtractor.epubCoverFromDir(file)
        }
    }

    /** Normalizes any source (EPUB, PDF render, or a downloaded cover) down to
     *  a small on-disk JPEG so storage and later decode cost stay bounded.
     *  480px wide is bigger than the ~44dp library thumbnail needs, but the
     *  same file backs the tap-to-zoom preview, which wants the headroom. */
    private fun saveCover(id: String, bytes: ByteArray): String? = try {
        val maxWidth = 480
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = if (bounds.outWidth > maxWidth) bounds.outWidth / maxWidth else 1
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        if (decoded == null) null
        else {
            val bitmap = if (decoded.width > maxWidth) {
                val ratio = maxWidth.toFloat() / decoded.width
                Bitmap.createScaledBitmap(decoded, maxWidth, (decoded.height * ratio).toInt(), true)
            } else decoded
            val f = File(booksDir, "$id.cover")
            f.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            f.name
        }
    } catch (_: Exception) {
        null
    }

    /** File holding the cover thumbnail, or null if there isn't one (yet). */
    fun resolveCover(coverPath: String?): File? {
        if (coverPath.isNullOrEmpty()) return null
        val f = File(booksDir, coverPath)
        return if (f.exists()) f else null
    }

    /** File (or directory, for an unzipped EPUB) holding the preserved original, or null. */
    suspend fun getOriginalFile(id: String): File? = withContext(Dispatchers.IO) {
        originals.resolve(dao.getById(id)?.originalPath)
    }

    suspend fun updateProgress(id: String, idx: Int, wpm: Int) = withContext(Dispatchers.IO) {
        dao.updateProgress(id, idx, wpm, System.currentTimeMillis())
    }

    suspend fun updateOriginalPos(id: String, pos: Int) = withContext(Dispatchers.IO) {
        dao.updateOriginalPos(id, pos, System.currentTimeMillis())
    }

    /** Describes a backup without unpacking it, for the confirm-before-restore step. */
    suspend fun peekBackup(uri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val manifest = backup.peek(appContext.contentResolver, uri)
        val incoming = manifest.books.map { it.id }.toSet()
        BackupPreview(
            createdAt = manifest.createdAt,
            bookCount = manifest.books.size,
            willRemove = dao.getAll().first().count { it.id !in incoming }
        )
    }

    /** Writes the whole library to [uri] as a zip. Returns the number of books in it. */
    suspend fun backupTo(uri: Uri, onProgress: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        backup.write(appContext.contentResolver, uri, dao.getAll().first(), onProgress)
    }

    /**
     * Restores a backup zip, which is a snapshot: afterwards the library is what
     * the archive says it is. Books it does not contain are taken out.
     *
     * The single exception is how far you have read. A position you actually
     * reached is the one thing here that cannot be recovered from anywhere else,
     * so a book present in both keeps whichever point is further along.
     *
     * The previous state is stashed first, so this stays undoable — see
     * [undoRestore]. Files of removed books are moved rather than copied, so the
     * safety net costs no extra space.
     */
    suspend fun restoreFrom(uri: Uri, onProgress: (String) -> Unit): RestoreResult =
        withContext(Dispatchers.IO) {
            val before = dao.getAll().first()
            val incoming = backup.read(appContext.contentResolver, uri, onProgress)
            val incomingIds = incoming.map { it.id }.toSet()

            onProgress("Saving current state...")
            undoDir.deleteRecursively()
            undoDir.mkdirs()
            File(undoDir, "library.json").writeText(LibraryManifest.write(before))

            var removed = 0
            for (book in before) {
                if (book.id !in incomingIds) {
                    stash(book.id)
                    dao.delete(book.id)
                    removed++
                }
            }

            val byId = before.associateBy { it.id }
            for (book in incoming) {
                val local = byId[book.id]
                dao.upsert(
                    book.copy(
                        idx = maxOf(book.idx, local?.idx ?: 0),
                        updatedAt = maxOf(book.updatedAt, local?.updatedAt ?: 0L)
                    )
                )
            }
            RestoreResult(incoming.size, removed)
        }

    /** True while the last restore can still be rolled back. */
    fun hasUndo(): Boolean = File(undoDir, "library.json").exists()

    /**
     * Puts the library back exactly as it was before the last restore: books that
     * restore removed come back, books it added go away, and every row returns to
     * its previous values. Null when there is nothing to undo.
     */
    suspend fun undoRestore(): RestoreResult? = withContext(Dispatchers.IO) {
        val manifest = File(undoDir, "library.json").takeIf { it.exists() } ?: return@withContext null
        val previous = LibraryManifest.parse(manifest.readText()).books
        val previousIds = previous.map { it.id }.toSet()

        var removed = 0
        for (book in dao.getAll().first()) {
            if (book.id !in previousIds) {
                File(booksDir, book.id + ".txt").delete()
                File(booksDir, book.id + ".cover").delete()
                originals.delete(book.id)
                dao.delete(book.id)
                removed++
            }
        }
        previous.forEach { unstash(it.id); dao.upsert(it) }
        undoDir.deleteRecursively()
        RestoreResult(previous.size, removed)
    }

    private fun bookFiles(id: String) = listOf("$id.txt", "$id.cover", "$id.pdf", id + "_epub")

    private fun stash(id: String) {
        val dest = File(undoDir, "files").apply { mkdirs() }
        bookFiles(id).forEach { name ->
            val f = File(booksDir, name)
            if (f.exists()) f.renameTo(File(dest, name))
        }
    }

    private fun unstash(id: String) {
        val src = File(undoDir, "files")
        bookFiles(id).forEach { name ->
            val f = File(src, name)
            if (f.exists()) f.renameTo(File(booksDir, name))
        }
    }

    suspend fun deleteBook(id: String) = withContext(Dispatchers.IO) {
        File(booksDir, "$id.txt").delete()
        File(booksDir, "$id.cover").delete()
        originals.delete(id)
        dao.delete(id)
    }
}
