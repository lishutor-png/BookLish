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
import com.example.data.parser.PdfTextExtractor
import com.example.data.parser.SamplePdfProvider
import com.example.data.parser.SearchHighlightBox
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val characterOffset: Int,
    val normalizedTopFraction: Float = 0.5f
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
    val searchHighlightBoxes: Map<Int, List<com.example.data.parser.SearchHighlightBox>> = emptyMap(),

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
    private var searchJob: Job? = null
    private var extractedDocumentPages: List<String> = emptyList()

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
        searchJob?.cancel()
        extractedDocumentPages = emptyList()
        clearSearch()
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
                currentChapterTitle = "Halaman ${initialPage + 1} dari $totalPages",
                settings = it.settings.copy(viewMode = ReaderViewMode.CONTINUOUS_VERTICAL)
            )
        }

        renderCurrentPdfPage(initialPage)
        loadAnnotationsForCurrentPage(book.id, initialPage)

        // Asynchronously extract and cache all text across document pages for instantaneous full-document search
        viewModelScope.launch(Dispatchers.IO) {
            val samplePages = SamplePdfProvider.getSampleBookPagesText(book)
            if (!samplePages.isNullOrEmpty()) {
                extractedDocumentPages = samplePages
                return@launch
            }
            val nativePages = pdfHelper.extractAllPagesText()
            if (!nativePages.isNullOrEmpty() && nativePages.any { it.isNotBlank() }) {
                extractedDocumentPages = nativePages
                return@launch
            }
            extractedDocumentPages = PdfTextExtractor.extractPagesText(
                context = getApplication(),
                book = book,
                totalPages = totalPages
            )
        }
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
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    currentSearchResultIndex = -1,
                    isSearching = false,
                    searchHighlightBoxes = emptyMap()
                )
            }
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150L) // Debounce keystrokes so typing full words is smooth and doesn't lag
            performSearch(trimmed)
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isSearching = true) }
        val results = mutableListOf<SearchResult>()
        val currentBook = _uiState.value.currentBook
        val totalPages = _uiState.value.totalPages

        // 1. Ensure document text pages are extracted and cached in-memory
        if (extractedDocumentPages.isEmpty() && currentBook != null) {
            val samplePages = SamplePdfProvider.getSampleBookPagesText(currentBook)
            if (!samplePages.isNullOrEmpty()) {
                extractedDocumentPages = samplePages
            } else {
                val nativePages = pdfHelper.extractAllPagesText()
                if (!nativePages.isNullOrEmpty() && nativePages.any { it.isNotBlank() }) {
                    extractedDocumentPages = nativePages
                } else {
                    extractedDocumentPages = PdfTextExtractor.extractPagesText(
                        context = getApplication(),
                        book = currentBook,
                        totalPages = totalPages
                    )
                }
            }
        }

        var matchCount = 0
        val maxMatches = 300

        // Prepare flexible regex in case PDF has kerning spaces between characters (e.g. "p a n d u a n")
        val flexibleRegex = try {
            if (query.length > 1) {
                val escapedLetters = query.map { Regex.escape(it.toString()) }
                Regex(escapedLetters.joinToString("""[\s\u00A0]*"""), RegexOption.IGNORE_CASE)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        // 2. Scan through ALL pages of the entire document
        // 100% case-insensitive: huruf besar dan kecil (a dan A) terbaca sama
        for (pageIdx in 0 until totalPages) {
            if (matchCount >= maxMatches) break

            val pageText = if (pageIdx < extractedDocumentPages.size) {
                extractedDocumentPages[pageIdx]
            } else {
                ""
            }

            if (pageText.isNotBlank()) {
                val pageMatchIndices = mutableListOf<Pair<Int, Int>>() // foundIndex to matchLength

                // Strategy A: Direct case-insensitive search
                var startIndex = 0
                while (startIndex < pageText.length && matchCount + pageMatchIndices.size < maxMatches) {
                    val foundIndex = pageText.indexOf(query, startIndex, ignoreCase = true)
                    if (foundIndex == -1) break
                    pageMatchIndices.add(Pair(foundIndex, query.length))
                    startIndex = foundIndex + maxOf(1, query.length)
                }

                // Strategy B: Flexible kerning/spaced matching if direct match found nothing
                if (pageMatchIndices.isEmpty() && flexibleRegex != null) {
                    val regexMatches = flexibleRegex.findAll(pageText).take(maxMatches - matchCount)
                    for (m in regexMatches) {
                        pageMatchIndices.add(Pair(m.range.first, m.value.length))
                    }
                }

                for ((foundIndex, matchLen) in pageMatchIndices) {
                    val snippetStart = (foundIndex - 35).coerceAtLeast(0)
                    val snippetEnd = (foundIndex + matchLen + 45).coerceAtMost(pageText.length)
                    val rawSnippet = pageText.substring(snippetStart, snippetEnd).trim()
                    val prefix = if (snippetStart > 0) "… " else ""
                    val suffix = if (snippetEnd < pageText.length) " …" else ""
                    val formattedSnippet = "$prefix$rawSnippet$suffix"
                    val topFraction = (foundIndex.toFloat() / maxOf(1, pageText.length)).coerceIn(0.08f, 0.92f)

                    matchCount++
                    results.add(
                        SearchResult(
                            id = "pdf_match_${pageIdx}_${foundIndex}_$matchCount",
                            pageOrChapterIndex = pageIdx,
                            pageOrChapterTitle = "Halaman ${pageIdx + 1}",
                            snippet = formattedSnippet,
                            matchWord = query,
                            characterOffset = foundIndex,
                            normalizedTopFraction = topFraction
                        )
                    )
                }
            }
        }

        // 3. Fallback for title/author match if content has no matches
        if (results.isEmpty() && currentBook != null) {
            val bookTitle = currentBook.title
            if (bookTitle.contains(query, ignoreCase = true)) {
                results.add(
                    SearchResult(
                        id = "pdf_title_match",
                        pageOrChapterIndex = 0,
                        pageOrChapterTitle = "Halaman 1 (Judul Dokumen)",
                        snippet = "Kecocokan pada judul dokumen: \"$bookTitle\"",
                        matchWord = query,
                        characterOffset = 0,
                        normalizedTopFraction = 0.15f
                    )
                )
            }
            val bookAuthor = currentBook.author
            if (bookAuthor.contains(query, ignoreCase = true)) {
                results.add(
                    SearchResult(
                        id = "pdf_author_match",
                        pageOrChapterIndex = 0,
                        pageOrChapterTitle = "Halaman 1 (Penulis)",
                        snippet = "Kecocokan pada nama penulis: \"$bookAuthor\"",
                        matchWord = query,
                        characterOffset = 0,
                        normalizedTopFraction = 0.25f
                    )
                )
            }
        }

        // 4. Retrieve exact bounding boxes for highlight rendering if supported (Android 35+)
        val highlightBoxesMap = mutableMapOf<Int, List<SearchHighlightBox>>()
        val distinctPagesWithMatches = results.map { it.pageOrChapterIndex }.distinct()
        for (pageIdx in distinctPagesWithMatches) {
            val boxes = pdfHelper.getSearchHighlightsForPage(pageIdx, query)
            if (boxes.isNotEmpty()) {
                highlightBoxesMap[pageIdx] = boxes
            }
        }

        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = results,
                    currentSearchResultIndex = if (results.isNotEmpty()) 0 else -1,
                    searchHighlightBoxes = highlightBoxesMap
                )
            }
        }
    }

    fun jumpToSearchResult(result: SearchResult) {
        goToPage(result.pageOrChapterIndex)
        val idx = _uiState.value.searchResults.indexOf(result)
        _uiState.update {
            it.copy(
                currentSearchResultIndex = if (idx >= 0) idx else 0,
                isSearchSheetOpen = false
            )
        }
    }

    fun nextSearchResult() {
        val results = _uiState.value.searchResults
        if (results.isEmpty()) return
        val currentIdx = _uiState.value.currentSearchResultIndex
        val nextIdx = (currentIdx + 1) % results.size
        val result = results[nextIdx]
        goToPage(result.pageOrChapterIndex)
        _uiState.update { it.copy(currentSearchResultIndex = nextIdx) }
    }

    fun previousSearchResult() {
        val results = _uiState.value.searchResults
        if (results.isEmpty()) return
        val currentIdx = _uiState.value.currentSearchResultIndex
        val prevIdx = if (currentIdx <= 0) results.size - 1 else currentIdx - 1
        val result = results[prevIdx]
        goToPage(result.pageOrChapterIndex)
        _uiState.update { it.copy(currentSearchResultIndex = prevIdx) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                currentSearchResultIndex = -1,
                isSearching = false,
                searchHighlightBoxes = emptyMap()
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

