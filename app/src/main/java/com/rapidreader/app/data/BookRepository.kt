package com.rapidreader.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.bookDao()
    private val booksDir: File by lazy {
        File(context.applicationContext.filesDir, "books").apply { mkdirs() }
    }

    fun observeBooks(): Flow<List<BookEntity>> = dao.getAll()

    suspend fun getBook(id: String): BookEntity? = dao.getById(id)

    suspend fun getBookText(id: String): String = withContext(Dispatchers.IO) {
        val f = File(booksDir, "$id.txt")
        if (f.exists()) f.readText() else ""
    }

    suspend fun addBook(title: String, source: String, text: String, wordCount: Int): String =
        withContext(Dispatchers.IO) {
            val id = "b_" + System.currentTimeMillis().toString(36) +
                (1..5).map { ('a'..'z').random() }.joinToString("")
            File(booksDir, "$id.txt").writeText(text)
            dao.upsert(
                BookEntity(
                    id = id,
                    title = title,
                    source = source,
                    wordCount = wordCount,
                    idx = 0,
                    wpm = 300,
                    updatedAt = System.currentTimeMillis()
                )
            )
            id
        }

    suspend fun updateProgress(id: String, idx: Int, wpm: Int) = withContext(Dispatchers.IO) {
        dao.updateProgress(id, idx, wpm, System.currentTimeMillis())
    }

    suspend fun deleteBook(id: String) = withContext(Dispatchers.IO) {
        File(booksDir, "$id.txt").delete()
        dao.delete(id)
    }
}
