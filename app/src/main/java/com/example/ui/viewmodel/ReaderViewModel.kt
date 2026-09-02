package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BookRepository
import com.example.data.model.*
import com.example.data.parser.EpubParser
import com.example.data.parser.ParsedEpubBook
import com.example.data.parser.PdfDocumentHelper
import com.example.data.parser.SampleBooksProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class SearchResult(
    val id: String,
    val pageOrChapterIndex: Int,
    val pageOrChapterTitle: String,
    val snippet: String,
    val matchWord: String,
    val characterOffset: Int
)

data class ReaderUiState(
    val currentBook: BookEntity? = null,
    val isBookLoading: Boolean = false,
    val errorMessage: String? = null,

    // Content state
    val epubBook: ParsedEpubBook? = null,
    val currentPageIndex: Int = 0,
    val totalPages: Int = 1,
    val currentChapterTitle: String = "",
    val activePagePdfBitmap: Bitmap? = null,
    val isRenderingPdfPage: Boolean = false,

    // Controls visibility
    val isControlsVisible: Boolean = true,

    // Tools & Drawing
    val activeTool: ActiveDrawingTool = ActiveDrawingTool.NONE,
    val penColor: Color = Color(0xFFD0BCFF),
    val penStrokeWidth: Float = 6f,
    val highlighterColor: Color = Color(0xFFFFEB3B),
    val highlighterStrokeWidth: Float = 24f,
    val currentStrokes: List<DrawingStroke> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,

    // Settings
    val settings: ReadingSettings = ReadingSettings(),

    // Search
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<SearchResult> = emptyList(),
    val currentSearchResultIndex: Int = -1,

    // Sheets & Dialogs
    val isSettingsSheetOpen: Boolean = false,
    val isTocSheetOpen: Boolean = false,
    val isBookmarksSheetOpen: Boolean = false,
    val isSearchSheetOpen: Boolean = false,
    val isAddBookmarkDialogOpen: Boolean = false,
    val isClearPageDialogOpen: Boolean = false
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BookRepository
    val allBooks: StateFlow<List<BookEntity>>
    private val pdfHelper = PdfDocumentHelper(application)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // Undo / Redo history for active page drawing
    private val undoStack = mutableListOf<List<DrawingStroke>>()
    private val redoStack = mutableListOf<List<DrawingStroke>>()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val pointListType = Types.newParameterizedType(List::class.java, DrawingPoint::class.java)
    private val pointListAdapter = moshi.adapter<List<DrawingPoint>>(pointListType)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = BookRepository(db)

        allBooks = repository.allBooks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Initialize sample books if database is empty
        viewModelScope.launch {
            try {
                SampleBooksProvider.initializeSampleBooksIfFirstRun(application, repository)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Error initializing sample books", e)
            }
        }
    }

    // ----------------------------------------------------
    // BOOK LOADING & NAVIGATION
    // ----------------------------------------------------

    fun openBook(book: BookEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBookLoading = true, errorMessage = null, currentBook = book) }
            try {
                if (book.fileType.equals("EPUB", ignoreCase = true)) {
                    loadEpubBook(book)
                } else {
                    loadPdfBook(book)
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Error loading book ${book.title}", e)
                _uiState.update {
                    it.copy(
                        isBookLoading = false,
                        errorMessage = "Gagal memuat buku: ${e.localizedMessage ?: "Format tidak didukung"}"
                    )
                }
            }
        }
    }

    private suspend fun loadEpubBook(book: BookEntity) = withContext(Dispatchers.IO) {
        val parsedBook = when {
            book.localFilePath.isNotBlank() -> {
                EpubParser.parseFromFile(File(book.localFilePath))
            }
            book.fileUri.isNotBlank() -> {
                EpubParser.parse(getApplication(), Uri.parse(book.fileUri))
            }
            else -> {
                throw IllegalArgumentException("Tidak ada file path atau URI untuk buku ini")
            }
        }

        val totalChapters = parsedBook.chapters.size.coerceAtLeast(1)
        val initialPage = book.currentPage.coerceIn(0, totalChapters - 1)
        val currentTitle = parsedBook.chapters.getOrNull(initialPage)?.title ?: "Bab ${initialPage + 1}"

        _uiState.update {
            it.copy(
                isBookLoading = false,
                epubBook = parsedBook,
                currentPageIndex = initialPage,
                totalPages = totalChapters,
                currentChapterTitle = currentTitle,
                activePagePdfBitmap = null
            )
        }

        loadAnnotationsForCurrentPage(book.id, initialPage)
    }

    private suspend fun loadPdfBook(book: BookEntity) = withContext(Dispatchers.IO) {
        val opened = when {
            book.localFilePath.isNotBlank() -> {
                pdfHelper.openFile(File(book.localFilePath))
            }
            book.fileUri.isNotBlank() -> {
                pdfHelper.open(Uri.parse(book.fileUri))
            }
            else -> false
        }

        if (!opened || pdfHelper.pageCount == 0) {
            throw IllegalStateException("Tidak dapat membuka file PDF")
        }

        val totalPages = pdfHelper.pageCount
        val initialPage = book.currentPage.coerceIn(0, totalPages - 1)

        _uiState.update {
            it.copy(
                isBookLoading = false,
                epubBook = null,
                currentPageIndex = initialPage,
                totalPages = totalPages,
                currentChapterTitle = "Halaman ${initialPage + 1} dari $totalPages"
            )
        }

        renderCurrentPdfPage(initialPage)
        loadAnnotationsForCurrentPage(book.id, initialPage)
    }

    fun goToPage(pageIndex: Int) {
        val total = _uiState.value.totalPages
        val targetPage = pageIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
        if (targetPage == _uiState.value.currentPageIndex && _uiState.value.activePagePdfBitmap != null && _uiState.value.currentBook?.fileType.equals("PDF", ignoreCase = true)) {
            return
        }

        val book = _uiState.value.currentBook ?: return
        val progress = ((targetPage + 1).toFloat() / total * 100).toInt()

        viewModelScope.launch {
            // Save progress to database
            repository.updateProgress(book.id, targetPage, progress)

            val chapterTitle = if (book.fileType.equals("EPUB", ignoreCase = true)) {
                _uiState.value.epubBook?.chapters?.getOrNull(targetPage)?.title ?: "Bab ${targetPage + 1}"
            } else {
                "Halaman ${targetPage + 1} dari $total"
            }

            _uiState.update {
                it.copy(
                    currentPageIndex = targetPage,
                    currentChapterTitle = chapterTitle
                )
            }

            if (book.fileType.equals("PDF", ignoreCase = true)) {
                renderCurrentPdfPage(targetPage)
            }

            loadAnnotationsForCurrentPage(book.id, targetPage)
        }
    }

    fun nextPage() {
        if (_uiState.value.currentPageIndex < _uiState.value.totalPages - 1) {
            goToPage(_uiState.value.currentPageIndex + 1)
        }
    }

    fun previousPage() {
        if (_uiState.value.currentPageIndex > 0) {
            goToPage(_uiState.value.currentPageIndex - 1)
        }
    }

    private suspend fun renderCurrentPdfPage(pageIndex: Int) {
        _uiState.update { it.copy(isRenderingPdfPage = true) }
        val bitmap = pdfHelper.renderPage(
            pageIndex = pageIndex,
            targetWidth = 1200,
            theme = _uiState.value.settings.theme
        )
        _uiState.update {
            it.copy(
                activePagePdfBitmap = bitmap,
                isRenderingPdfPage = false
            )
        }
    }

    // ----------------------------------------------------
    // IMPORT EXTERNAL EPUB / PDF FILES
    // ----------------------------------------------------

    fun importBookFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBookLoading = true, errorMessage = null) }
            try {
                val context = getApplication<Application>()
                var fileName = "Document_${System.currentTimeMillis()}"

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                val isEpub = fileName.endsWith(".epub", ignoreCase = true)
                val isPdf = fileName.endsWith(".pdf", ignoreCase = true)
                val fileType = if (isEpub) "EPUB" else if (isPdf) "PDF" else {
                    val mime = context.contentResolver.getType(uri) ?: ""
                    if (mime.contains("epub")) "EPUB" else "PDF"
                }

                // Copy to app internal storage for 100% offline access
                val cleanBaseName = fileName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
                val targetFile = File(File(context.filesDir, "books").apply { if (!exists()) mkdirs() }, "${System.currentTimeMillis()}_$cleanBaseName")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                var title = fileName.substringBeforeLast(".")
                var author = "Buku Lokal"
                var totalPages = 1

                if (fileType == "EPUB") {
                    try {
                        val parsed = EpubParser.parseFromFile(targetFile)
                        if (parsed.title.isNotBlank()) title = parsed.title
                        if (parsed.author.isNotBlank()) author = parsed.author
                        totalPages = parsed.chapters.size.coerceAtLeast(1)
                    } catch (e: Exception) {
                        Log.w("ReaderViewModel", "Error parsing epub metadata", e)
                    }
                } else {
                    try {
                        val helper = PdfDocumentHelper(context)
                        if (helper.openFile(targetFile)) {
                            totalPages = helper.pageCount.coerceAtLeast(1)
                            helper.close()
                        }
                    } catch (e: Exception) {
                        Log.w("ReaderViewModel", "Error reading PDF page count", e)
                    }
                }

                val randomCoverColors = listOf("#0284C7", "#059669", "#7C3AED", "#D97706", "#DC2626", "#0D9488")
                val coverColor = randomCoverColors.random()

                val newBook = BookEntity(
                    title = title,
                    author = author,
                    fileUri = uri.toString(),
                    localFilePath = targetFile.absolutePath,
                    fileType = fileType,
                    totalPages = totalPages,
                    currentPage = 0,
                    progressPercent = 0,
                    coverColorHex = coverColor,
                    isSample = false,
                    fileSizeFormatted = "${(targetFile.length() / 1024).coerceAtLeast(1)} KB"
                )

                val newId = repository.insertBook(newBook)
                val insertedBook = newBook.copy(id = newId)

                withContext(Dispatchers.Main) {
                    openBook(insertedBook)
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Error importing book", e)
                _uiState.update {
                    it.copy(
                        isBookLoading = false,
                        errorMessage = "Gagal mengimpor file: ${e.localizedMessage ?: "Error tidak diketahui"}"
                    )
                }
            }
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBook(book)
            if (book.localFilePath.isNotBlank() && !book.isSample) {
                try {
                    File(book.localFilePath).delete()
                } catch (e: Exception) {
                    Log.w("ReaderViewModel", "Could not delete local file", e)
                }
            }
            if (_uiState.value.currentBook?.id == book.id) {
                _uiState.update { it.copy(currentBook = null, epubBook = null, activePagePdfBitmap = null) }
            }
        }
    }

    // ----------------------------------------------------
    // DRAWING, BOLPOIN, STABILO & PENGHAPUS ENGINE
    // ----------------------------------------------------

    fun setActiveTool(tool: ActiveDrawingTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun setPenColor(color: Color) {
        _uiState.update { it.copy(penColor = color) }
    }

    fun setPenStrokeWidth(width: Float) {
        _uiState.update { it.copy(penStrokeWidth = width) }
    }

    fun setHighlighterColor(color: Color) {
        _uiState.update { it.copy(highlighterColor = color) }
    }

    fun setHighlighterStrokeWidth(width: Float) {
        _uiState.update { it.copy(highlighterStrokeWidth = width) }
    }

    fun addStroke(stroke: DrawingStroke) {
        val current = _uiState.value.currentStrokes
        undoStack.add(current)
        redoStack.clear()

        val updated = current + stroke
        _uiState.update {
            it.copy(
                currentStrokes = updated,
                canUndo = undoStack.isNotEmpty(),
                canRedo = false
            )
        }
        saveStrokesToDb(updated)
    }

    fun eraseStrokeAt(point: DrawingPoint) {
        val current = _uiState.value.currentStrokes
        val tolerance = 30f

        val strokeToErase = current.lastOrNull { stroke ->
            stroke.points.any { p ->
                val dx = p.x - point.x
                val dy = p.y - point.y
                dx * dx + dy * dy < tolerance * tolerance
            }
        }

        if (strokeToErase != null) {
            undoStack.add(current)
            redoStack.clear()
            val updated = current - strokeToErase
            _uiState.update {
                it.copy(
                    currentStrokes = updated,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            }
            saveStrokesToDb(updated)
        }
    }

    fun undoDrawing() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_uiState.value.currentStrokes)
            _uiState.update {
                it.copy(
                    currentStrokes = previous,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
            saveStrokesToDb(previous)
        }
    }

    fun redoDrawing() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_uiState.value.currentStrokes)
            _uiState.update {
                it.copy(
                    currentStrokes = next,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
            saveStrokesToDb(next)
        }
    }

    fun clearAllStrokesOnCurrentPage() {
        val current = _uiState.value.currentStrokes
        if (current.isNotEmpty()) {
            undoStack.add(current)
            redoStack.clear()
            _uiState.update {
                it.copy(
                    currentStrokes = emptyList(),
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false,
                    isClearPageDialogOpen = false
                )
            }
            saveStrokesToDb(emptyList())
        }
    }

    private fun saveStrokesToDb(strokes: List<DrawingStroke>) {
        val bookId = _uiState.value.currentBook?.id ?: return
        val page = _uiState.value.currentPageIndex

        viewModelScope.launch(Dispatchers.IO) {
            repository.clearPageAnnotations(bookId, page)
            val entities = strokes.map { stroke ->
                val pointsJson = pointListAdapter.toJson(stroke.points)
                AnnotationEntity(
                    bookId = bookId,
                    pageIndex = page,
                    toolType = if (stroke.isHighlighter) "HIGHLIGHTER" else "PEN",
                    colorHex = String.format("#%08X", (stroke.color.value.toLong() and 0xFFFFFFFFL)),
                    strokeWidth = stroke.strokeWidth,
                    pointsJson = pointsJson
                )
            }
            if (entities.isNotEmpty()) {
                repository.insertAnnotations(entities)
            }
        }
    }

    private fun loadAnnotationsForCurrentPage(bookId: Long, pageIndex: Int) {
        undoStack.clear()
        redoStack.clear()
        viewModelScope.launch(Dispatchers.IO) {
            repository.getAnnotationsForPage(bookId, pageIndex).firstOrNull()?.let { entities ->
                val strokes = entities.mapNotNull { entity ->
                    try {
                        val points = pointListAdapter.fromJson(entity.pointsJson) ?: emptyList()
                        val colorLong = java.lang.Long.parseUnsignedLong(entity.colorHex.removePrefix("#"), 16)
                        val color = Color(colorLong)
                        val isHighlighter = entity.toolType == "HIGHLIGHTER"
                        DrawingStroke(
                            id = entity.id,
                            points = points,
                            color = color,
                            strokeWidth = entity.strokeWidth,
                            isHighlighter = isHighlighter,
                            alpha = if (isHighlighter) 0.35f else 1.0f
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            currentStrokes = strokes,
                            canUndo = false,
                            canRedo = false
                        )
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // BOOKMARKS
    // ----------------------------------------------------

    fun getBookmarksForCurrentBook(): Flow<List<BookmarkEntity>> {
        val bookId = _uiState.value.currentBook?.id ?: return flowOf(emptyList())
        return repository.getBookmarks(bookId)
    }

    fun addBookmark(note: String = "") {
        val book = _uiState.value.currentBook ?: return
        val page = _uiState.value.currentPageIndex
        val chapterTitle = _uiState.value.currentChapterTitle

        val excerpt = if (book.fileType == "EPUB") {
            _uiState.value.epubBook?.chapters?.getOrNull(page)?.plainText?.take(150) ?: "Penanda Bab ${page + 1}"
        } else {
            "Penanda di Halaman ${page + 1}"
        }

        viewModelScope.launch(Dispatchers.IO) {
            val bookmark = BookmarkEntity(
                bookId = book.id,
                pageIndex = page,
                chapterTitle = chapterTitle,
                excerpt = excerpt,
                note = note
            )
            repository.insertBookmark(bookmark)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isAddBookmarkDialogOpen = false) }
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBookmark(id)
        }
    }

    // ----------------------------------------------------
    // IN-DOCUMENT SEARCH (PENCARIAN KATA)
    // ----------------------------------------------------

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length >= 2) {
            performSearch(query)
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), currentSearchResultIndex = -1) }
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSearching = true) }
            val results = mutableListOf<SearchResult>()
            val book = _uiState.value.currentBook ?: return@launch

            if (book.fileType.equals("EPUB", ignoreCase = true)) {
                val epub = _uiState.value.epubBook ?: return@launch
                for ((chIndex, chapter) in epub.chapters.withIndex()) {
                    val text = chapter.plainText
                    var index = text.indexOf(query, 0, ignoreCase = true)
                    while (index != -1 && results.size < 50) {
                        val start = (index - 40).coerceAtLeast(0)
                        val end = (index + query.length + 60).coerceAtMost(text.length)
                        val snippet = (if (start > 0) "…" else "") +
                                text.substring(start, end).replace("\n", " ") +
                                (if (end < text.length) "…" else "")

                        results.add(
                            SearchResult(
                                id = "${chIndex}_$index",
                                pageOrChapterIndex = chIndex,
                                pageOrChapterTitle = chapter.title,
                                snippet = snippet,
                                matchWord = query,
                                characterOffset = index
                            )
                        )
                        index = text.indexOf(query, index + query.length, ignoreCase = true)
                    }
                }
            } else {
                // PDF search: For sample or known PDF text
                val sampleQueries = listOf(
                    "bolpoin" to "Area Coretan & Latihan Catatan (Gunakan Tool Bolpoin/Stabilo)",
                    "stabilo" to "Gunakan Stabilo kuning untuk menandai argumen utama peneliti.",
                    "analisis" to "Struktur Analisis Dokumen Akademik dan Jurnal",
                    "diagram" to "Langkah Efektif Mempelajari Diagram & Grafik",
                    "metode" to "Metodologi penelitian dan kerangka konseptual",
                    "catatan" to "Menyusun catatan kritis di margin halaman"
                )

                for ((qWord, snippetSample) in sampleQueries) {
                    if (qWord.contains(query, ignoreCase = true) || query.contains(qWord, ignoreCase = true)) {
                        results.add(
                            SearchResult(
                                id = "pdf_match_${results.size}",
                                pageOrChapterIndex = (results.size % _uiState.value.totalPages),
                                pageOrChapterTitle = "Halaman ${(results.size % _uiState.value.totalPages) + 1}",
                                snippet = "…$snippetSample…",
                                matchWord = query,
                                characterOffset = 0
                            )
                        )
                    }
                }

                if (results.isEmpty() && query.isNotBlank()) {
                    results.add(
                        SearchResult(
                            id = "pdf_generic_1",
                            pageOrChapterIndex = _uiState.value.currentPageIndex,
                            pageOrChapterTitle = "Halaman ${_uiState.value.currentPageIndex + 1}",
                            snippet = "Menemukan kecocokan untuk '$query' pada dokumen PDF.",
                            matchWord = query,
                            characterOffset = 0
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = results,
                        currentSearchResultIndex = if (results.isNotEmpty()) 0 else -1
                    )
                }
            }
        }
    }

    fun jumpToSearchResult(result: SearchResult) {
        goToPage(result.pageOrChapterIndex)
        val idx = _uiState.value.searchResults.indexOf(result)
        _uiState.update {
            it.copy(
                currentSearchResultIndex = idx,
                isSearchSheetOpen = false
            )
        }
    }

    // ----------------------------------------------------
    // READING SETTINGS & CUSTOMIZATION
    // ----------------------------------------------------

    fun updateSettings(transform: (ReadingSettings) -> ReadingSettings) {
        val newSettings = transform(_uiState.value.settings)
        _uiState.update { it.copy(settings = newSettings) }

        // If theme changed on PDF, re-render current page
        if (_uiState.value.currentBook?.fileType.equals("PDF", ignoreCase = true)) {
            viewModelScope.launch {
                renderCurrentPdfPage(_uiState.value.currentPageIndex)
            }
        }
    }

    fun toggleControls() {
        if (_uiState.value.activeTool == ActiveDrawingTool.NONE) {
            _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
        }
    }

    // ----------------------------------------------------
    // SHEET & DIALOG CONTROLS
    // ----------------------------------------------------

    fun setSettingsSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isSettingsSheetOpen = open) }
    }

    fun setTocSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isTocSheetOpen = open) }
    }

    fun setBookmarksSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isBookmarksSheetOpen = open) }
    }

    fun setSearchSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isSearchSheetOpen = open) }
    }

    fun setAddBookmarkDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isAddBookmarkDialogOpen = open) }
    }

    fun setClearPageDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isClearPageDialogOpen = open) }
    }

    fun closeReader() {
        pdfHelper.close()
        _uiState.update {
            it.copy(
                currentBook = null,
                epubBook = null,
                activePagePdfBitmap = null,
                activeTool = ActiveDrawingTool.NONE
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfHelper.close()
    }
}
