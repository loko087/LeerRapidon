package com.rapidreader.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("UPDATE books SET idx = :idx, wpm = :wpm, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, idx: Int, wpm: Int, updatedAt: Long)

    @Query("UPDATE books SET originalPos = :pos, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOriginalPos(id: String, pos: Int, updatedAt: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}
