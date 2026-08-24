package com.rapidreader.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.rapidreader.app.extract.OpenLibraryCovers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.bookDao()
    private val booksDir: File by lazy {
        File(appContext.filesDir, "books").apply { mkdirs() }
    }
    private val originals by lazy { OriginalStore(booksDir) }
    // Outlives any single addBook() call so the background Open Library
    // lookup isn't cancelled if the screen that triggered the import closes.
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeBooks(): Flow<List<BookEntity>> = dao.getAll()

    suspend fun getBook(id: String): BookEntity? = dao.getById(id)

    suspend fun getBookText(id: String): String = withContext(Dispatchers.IO) {
        val f = File(booksDir, "$id.txt")
        if (f.exists()) f.readText() else ""
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

    suspend fun deleteBook(id: String) = withContext(Dispatchers.IO) {
        File(booksDir, "$id.txt").delete()
        File(booksDir, "$id.cover").delete()
        originals.delete(id)
        dao.delete(id)
    }
}
