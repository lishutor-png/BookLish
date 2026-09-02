package com.example.data.local

import androidx.room.*
import com.example.data.model.AnnotationEntity
import com.example.data.model.BookEntity
import com.example.data.model.BookmarkEntity
import com.example.data.model.StudySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Long): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET currentPage = :page, progressPercent = :progress, lastReadTimestamp = :timestamp WHERE id = :bookId")
    suspend fun updateProgress(bookId: Long, page: Int, progress: Int, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY pageIndex ASC")
    fun getBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND pageIndex = :pageIndex")
    suspend fun deleteBookmarkAtPage(bookId: Long, pageIndex: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE bookId = :bookId AND pageIndex = :pageIndex)")
    fun isPageBookmarked(bookId: Long, pageIndex: Int): Flow<Boolean>
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND pageIndex = :pageIndex ORDER BY createdAt ASC")
    fun getAnnotationsForPage(bookId: Long, pageIndex: Int): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY pageIndex ASC, createdAt ASC")
    fun getAllAnnotationsForBook(bookId: Long): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<AnnotationEntity>)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: Long)

    @Query("DELETE FROM annotations WHERE bookId = :bookId AND pageIndex = :pageIndex")
    suspend fun clearPageAnnotations(bookId: Long, pageIndex: Int)
}

@Dao
interface SummaryDao {
    @Query("SELECT * FROM study_summaries WHERE bookId = :bookId ORDER BY pageIndex ASC, createdAt DESC")
    fun getSummariesForBook(bookId: Long): Flow<List<StudySummaryEntity>>

    @Query("SELECT * FROM study_summaries WHERE bookId = :bookId AND pageIndex = :pageIndex LIMIT 1")
    suspend fun getSummaryForPage(bookId: Long, pageIndex: Int): StudySummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: StudySummaryEntity): Long

    @Query("DELETE FROM study_summaries WHERE id = :id")
    suspend fun deleteSummary(id: Long)
}
