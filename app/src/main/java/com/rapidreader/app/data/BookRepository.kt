package com.rapidreader.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
        staged: StagedOriginal? = null
    ): String = withContext(Dispatchers.IO) {
        val id = "b_" + System.currentTimeMillis().toString(36) +
            (1..5).map { ('a'..'z').random() }.joinToString("")
        File(booksDir, "$id.txt").writeText(text)
        val originalPath = staged?.let { originals.commit(it, id) }
        dao.upsert(
            BookEntity(
                id = id,
                title = title,
                source = source,
                wordCount = wordCount,
                idx = 0,
                wpm = 300,
                updatedAt = System.currentTimeMillis(),
                originalPath = originalPath
            )
        )
        id
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
        originals.delete(id)
        dao.delete(id)
    }
}
