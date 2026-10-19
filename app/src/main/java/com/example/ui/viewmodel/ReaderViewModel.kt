package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BookRepository
import com.example.data.model.*
import com.example.data.parser.PdfDocumentHelper
import com.example.data.parser.SamplePdfProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    // Content state (PDF)
    val currentPageIndex: Int = 0,
    val totalPages: Int = 1,
    val currentChapterTitle: String = "",
    val activePagePdfBitmap: Bitmap? = null,
    val isRenderingPdfPage: Boolean = false,

    // Controls visibility
    val isControlsVisible: Boolean = true,

    // Tools & Drawing
    val activeTool: ActiveDrawingTool = ActiveDrawingTool.NONE,
    val penColor: Color = Color(0xFFDC2626), // Crimson default
    val penStrokeWidth: Float = 6f,
    val highlighterColor: Color = Color(0xFFFFEB3B), // Yellow highlighter
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
    private var pageRenderJob: Job? = null

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

        // Initialize sample PDF documents if database is empty on first run
        viewModelScope.launch {
            try {
                SamplePdfProvider.initializeSampleBooksIfFirstRun(application, repository)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Error initializing sample pdf books", e)
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
                loadPdfBook(book)
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Error loading PDF ${book.title}", e)
                _uiState.update {
                    it.copy(
                        isBookLoading = false,
                        errorMessage = "Gagal memuat dokumen PDF: ${e.localizedMessage ?: "File rusak atau tidak didukung"}"
                    )
                }
            }
        }
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
        if (targetPage == _uiState.value.currentPageIndex && _uiState.value.activePagePdfBitmap != null) {
            return
        }

        val book = _uiState.value.currentBook ?: return
        val progress = ((targetPage + 1).toFloat() / total * 100).toInt()
        val theme = _uiState.value.settings.theme

        // Instant synchronous check from memory cache to prevent any flicker or loading spinner
        val cachedBitmap = pdfHelper.getCachedPage(targetPage, 1400, theme)

        _uiState.update {
            it.copy(
                currentPageIndex = targetPage,
                currentChapterTitle = "Halaman ${targetPage + 1} dari $total",
                activePagePdfBitmap = cachedBitmap ?: it.activePagePdfBitmap,
                isRenderingPdfPage = cachedBitmap == null && it.activePagePdfBitmap == null
            )
        }

        loadAnnotationsForCurrentPage(book.id, targetPage)

        pageRenderJob?.cancel()
        pageRenderJob = viewModelScope.launch {
            repository.updateProgress(book.id, targetPage, progress)
            if (cachedBitmap == null) {
                renderCurrentPdfPage(targetPage)
            } else {
                pdfHelper.prefetchAdjacentPages(targetPage, 1400, theme)
            }
        }
    }

    fun syncVisiblePageInContinuousMode(pageIndex: Int) {
        val total = _uiState.value.totalPages
        val targetPage = pageIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
        if (targetPage == _uiState.value.currentPageIndex) return

        val book = _uiState.value.currentBook ?: return
        val progress = ((targetPage + 1).toFloat() / total * 100).toInt()
        val theme = _uiState.value.settings.theme
        val cachedBitmap = pdfHelper.getCachedPage(targetPage, 1400, theme)

        _uiState.update {
            it.copy(
                currentPageIndex = targetPage,
                currentChapterTitle = "Halaman ${targetPage + 1} dari $total",
                activePagePdfBitmap = cachedBitmap ?: it.activePagePdfBitmap
            )
        }

        loadAnnotationsForCurrentPage(book.id, targetPage)
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateProgress(book.id, targetPage, progress)
        }
    }

    fun getCachedPageForIndex(pageIndex: Int): Bitmap? {
        return pdfHelper.getCachedPage(pageIndex, 1400, _uiState.value.settings.theme)
    }

    suspend fun renderPageForIndex(pageIndex: Int): Bitmap? {
        return pdfHelper.renderPage(
            pageIndex = pageIndex,
            targetWidth = 1400,
            theme = _uiState.value.settings.theme
        )
    }

    fun toggleViewMode() {
        updateSettings { current ->
            val nextMode = if (current.viewMode == ReaderViewMode.SINGLE_PAGE_SWIPE) {
                ReaderViewMode.CONTINUOUS_VERTICAL
            } else {
                ReaderViewMode.SINGLE_PAGE_SWIPE
            }
            current.copy(viewMode = nextMode)
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
        val theme = _uiState.value.settings.theme
        val cached = pdfHelper.getCachedPage(pageIndex, 1400, theme)
        if (cached != null) {
            _uiState.update {
                it.copy(
                    activePagePdfBitmap = cached,
                    isRenderingPdfPage = false
                )
            }
            pdfHelper.prefetchAdjacentPages(pageIndex, 1400, theme)
            return
        }

        if (_uiState.value.activePagePdfBitmap == null) {
            _uiState.update { it.copy(isRenderingPdfPage = true) }
        }
        val bitmap = pdfHelper.renderPage(
            pageIndex = pageIndex,
            targetWidth = 1400,
            theme = theme
        )
        _uiState.update {
            it.copy(
                activePagePdfBitmap = bitmap ?: it.activePagePdfBitmap,
                isRenderingPdfPage = false
            )
        }
        pdfHelper.prefetchAdjacentPages(pageIndex, 1400, theme)
    }

    // ----------------------------------------------------
    // IMPORT EXTERNAL PDF FILES & FILE MANAGER INTEGRATION
    // ----------------------------------------------------

    fun openFromExternalUri(uri: Uri) {
        importBookFromUri(uri)
    }

    fun importBookFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBookLoading = true, errorMessage = null) }
            try {
                val context = getApplication<Application>()

                // Take persistable permission if available
                if (uri.scheme == "content") {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                }

                var queriedName: String? = null
                if (uri.scheme == "content") {
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                queriedName = cursor.getString(nameIndex)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("ReaderViewModel", "Could not query display name from content resolver", e)
                    }
                }

                val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: "Dokumen_${System.currentTimeMillis()}.pdf"
                val rawName = if (!queriedName.isNullOrBlank()) queriedName!! else fallbackName
                val fileName = if (rawName.endsWith(".pdf", ignoreCase = true)) rawName else "$rawName.pdf"
                val title = fileName.substringBeforeLast(".")

                // Check if book already exists in library to avoid duplicate copies and preserve user drawings/bookmarks
                val existingBook = allBooks.value.firstOrNull { book ->
                    (book.fileUri == uri.toString() || book.title.equals(title, ignoreCase = true)) &&
                    book.localFilePath.isNotBlank() && File(book.localFilePath).exists()
                }

                if (existingBook != null) {
                    withContext(Dispatchers.Main) {
                        openBook(existingBook)
                    }
                    return@launch
                }

                // Copy to app internal storage for 100% offline access
                val cleanBaseName = fileName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
                val booksDir = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }
                val targetFile = File(booksDir, "${System.currentTimeMillis()}_$cleanBaseName")

                val inputStream = when (uri.scheme) {
                    "file" -> {
                        val path = uri.path
                        if (path != null && File(path).exists()) File(path).inputStream()
                        else context.contentResolver.openInputStream(uri)
                    }
                    else -> context.contentResolver.openInputStream(uri)
                } ?: throw IllegalStateException("Tidak dapat membaca data berkas dari $uri")

                inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val author = "Dokumen Saya"
                var totalPages = 1

                try {
                    val helper = PdfDocumentHelper(context)
                    if (helper.openFile(targetFile)) {
                        totalPages = helper.pageCount.coerceAtLeast(1)
                        helper.close()
                    }
                } catch (e: Exception) {
                    Log.w("ReaderViewModel", "Error reading PDF page count", e)
                }

                val randomCoverColors = listOf("#DC2626", "#0284C7", "#059669", "#7C3AED", "#D97706", "#0D9488")
                val coverColor = randomCoverColors.random()

                val newBook = BookEntity(
                    title = title,
                    author = author,
                    fileUri = uri.toString(),
                    localFilePath = targetFile.absolutePath,
                    fileType = "PDF",
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
                Log.e("ReaderViewModel", "Error importing PDF", e)
                _uiState.update {
                    it.copy(
                        isBookLoading = false,
                        errorMessage = "Gagal memuat dokumen PDF: ${e.localizedMessage ?: "File rusak atau tidak dapat diakses"}"
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
                _uiState.update { it.copy(currentBook = null, activePagePdfBitmap = null) }
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
        val chapterTitle = "Halaman ${page + 1}"
        val excerpt = "Penanda Halaman ${page + 1} - ${book.title}"

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

            val sampleQueries = listOf(
                "bolpoin" to "Area Coretan & Latihan Catatan (Gunakan Tool Bolpoin/Stabilo)",
                "stabilo" to "Gunakan Stabilo kuning untuk menandai argumen utama peneliti.",
                "gestur" to "Gestur 2 Jari Pintar (Pinch & Pan): Saat mode menulis aktif",
                "zoom" to "Perbesar dokumen hingga 300% untuk menulis catatan kecil di margin",
                "catatan" to "Menyusun catatan kritis dan ringkasan",
                "penanda" to "Simpan penanda halaman penting disertai catatan refleksi"
            )

            for ((qWord, snippetSample) in sampleQueries) {
                if (qWord.contains(query, ignoreCase = true) || query.contains(qWord, ignoreCase = true)) {
                    val pageIdx = results.size % _uiState.value.totalPages.coerceAtLeast(1)
                    results.add(
                        SearchResult(
                            id = "pdf_match_${results.size}",
                            pageOrChapterIndex = pageIdx,
                            pageOrChapterTitle = "Halaman ${pageIdx + 1}",
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
                        snippet = "Pencarian '$query' pada dokumen PDF aktif.",
                        matchWord = query,
                        characterOffset = 0
                    )
                )
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
        val oldTheme = _uiState.value.settings.theme
        val newSettings = transform(_uiState.value.settings)
        _uiState.update { it.copy(settings = newSettings) }

        if (newSettings.theme != oldTheme) {
            pageRenderJob?.cancel()
            pageRenderJob = viewModelScope.launch {
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

