package com.example.data.local

import com.example.data.model.AnnotationEntity
import com.example.data.model.BookEntity
import com.example.data.model.BookmarkEntity
import com.example.data.model.StudySummaryEntity
import kotlinx.coroutines.flow.Flow

class BookRepository(private val database: AppDatabase) {
    private val bookDao = database.bookDao()
    private val bookmarkDao = database.bookmarkDao()
    private val annotationDao = database.annotationDao()
    private val summaryDao = database.summaryDao()

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getBookById(id: Long): BookEntity? = bookDao.getBookById(id)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun updateProgress(bookId: Long, page: Int, progress: Int) =
        bookDao.updateProgress(bookId, page, progress)

    suspend fun deleteBook(book: BookEntity) = bookDao.deleteBook(book)

    suspend fun deleteBookById(id: Long) = bookDao.deleteBookById(id)

    // Bookmarks
    fun getBookmarks(bookId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.getBookmarksForBook(bookId)

    suspend fun insertBookmark(bookmark: BookmarkEntity): Long =
        bookmarkDao.insertBookmark(bookmark)

    suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteBookmark(id)

    suspend fun deleteBookmarkAtPage(bookId: Long, pageIndex: Int) =
        bookmarkDao.deleteBookmarkAtPage(bookId, pageIndex)

    fun isPageBookmarked(bookId: Long, pageIndex: Int): Flow<Boolean> =
        bookmarkDao.isPageBookmarked(bookId, pageIndex)

    // Annotations
    fun getAnnotationsForPage(bookId: Long, pageIndex: Int): Flow<List<AnnotationEntity>> =
        annotationDao.getAnnotationsForPage(bookId, pageIndex)

    fun getAllAnnotationsForBook(bookId: Long): Flow<List<AnnotationEntity>> =
        annotationDao.getAllAnnotationsForBook(bookId)

    suspend fun insertAnnotation(annotation: AnnotationEntity): Long =
        annotationDao.insertAnnotation(annotation)

    suspend fun insertAnnotations(annotations: List<AnnotationEntity>) =
        annotationDao.insertAnnotations(annotations)

    suspend fun deleteAnnotation(id: Long) = annotationDao.deleteAnnotation(id)

    suspend fun clearPageAnnotations(bookId: Long, pageIndex: Int) =
        annotationDao.clearPageAnnotations(bookId, pageIndex)

    // Summaries
    fun getSummariesForBook(bookId: Long): Flow<List<StudySummaryEntity>> =
        summaryDao.getSummariesForBook(bookId)

    suspend fun getSummaryForPage(bookId: Long, pageIndex: Int): StudySummaryEntity? =
        summaryDao.getSummaryForPage(bookId, pageIndex)

    suspend fun insertSummary(summary: StudySummaryEntity): Long =
        summaryDao.insertSummary(summary)

    suspend fun deleteSummary(id: Long) = summaryDao.deleteSummary(id)
}
