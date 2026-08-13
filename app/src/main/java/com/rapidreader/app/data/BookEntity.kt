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
    val updatedAt: Long
)
