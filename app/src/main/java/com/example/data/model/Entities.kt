package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String = "Unknown Author",
    val fileUri: String = "",
    val localFilePath: String = "",
    val fileType: String, // "EPUB" or "PDF"
    val totalPages: Int = 1,
    val currentPage: Int = 0,
    val progressPercent: Int = 0,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val coverColorHex: String = "#1E293B",
    val isSample: Boolean = false,
    val fileSizeFormatted: String = ""
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val pageIndex: Int,
    val chapterTitle: String,
    val excerpt: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val pageIndex: Int,
    val toolType: String, // "PEN" or "HIGHLIGHTER"
    val colorHex: String,
    val strokeWidth: Float,
    val pointsJson: String, // serialized x,y coordinates
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_summaries")
data class StudySummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val pageIndex: Int,
    val chapterTitle: String,
    val summaryText: String,
    val keyPointsJson: String = "[]",
    val flashcardsJson: String = "[]",
    val isAiGenerated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
