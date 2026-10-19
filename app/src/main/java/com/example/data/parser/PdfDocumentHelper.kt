package com.example.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import com.example.data.model.ReadingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class SearchHighlightBox(
    val normalizedLeft: Float,
    val normalizedTop: Float,
    val normalizedRight: Float,
    val normalizedBottom: Float
)

class PdfDocumentHelper(private val context: Context) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private val renderMutex = Mutex()

    // Cache up to 12 rendered PDF page bitmaps in memory for instant 0ms page sliding
    private val pageCache = object : LruCache<String, Bitmap>(12) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    var pageCount: Int = 0
        private set

    private fun cacheKey(pageIndex: Int, targetWidth: Int, theme: ReadingTheme): String {
        return "${pageIndex}_${targetWidth}_${theme.name}"
    }

    fun getCachedPage(
        pageIndex: Int,
        targetWidth: Int = 1400,
        theme: ReadingTheme = ReadingTheme.LIGHT
    ): Bitmap? {
        val cached = pageCache.get(cacheKey(pageIndex, targetWidth, theme))
        return if (cached != null && !cached.isRecycled) cached else null
    }

    fun open(uri: Uri): Boolean {
        close()
        return try {
            fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            if (fileDescriptor != null) {
                pdfRenderer = PdfRenderer(fileDescriptor!!)
                pageCount = pdfRenderer?.pageCount ?: 0
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("PdfDocumentHelper", "Error opening PDF URI: $uri", e)
            false
        }
    }

    fun openFile(file: File): Boolean {
        close()
        return try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            pageCount = pdfRenderer?.pageCount ?: 0
            true
        } catch (e: Exception) {
            Log.e("PdfDocumentHelper", "Error opening PDF File: ${file.path}", e)
            false
        }
    }

    suspend fun renderPage(
        pageIndex: Int,
        targetWidth: Int = 1080,
        theme: ReadingTheme = ReadingTheme.NIGHT
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        val key = cacheKey(pageIndex, targetWidth, theme)
        pageCache.get(key)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        renderMutex.withLock {
            // Double-check cache after acquiring mutex
            pageCache.get(key)?.let { cached ->
                if (!cached.isRecycled) return@withContext cached
            }

            val renderer = pdfRenderer ?: return@withContext null
            if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

            try {
                val page = renderer.openPage(pageIndex)
                val width = targetWidth.coerceAtLeast(300)
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(400)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                // Fill background based on theme
                val canvas = android.graphics.Canvas(bitmap)
                val bgPaint = Paint().apply {
                    color = when (theme) {
                        ReadingTheme.LIGHT -> android.graphics.Color.WHITE
                        ReadingTheme.SEPIA -> android.graphics.Color.rgb(244, 236, 216)
                        ReadingTheme.NIGHT -> android.graphics.Color.rgb(26, 28, 30)
                        ReadingTheme.OLED -> android.graphics.Color.BLACK
                    }
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                // Render page to bitmap
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Apply theme color filter if night, oled or sepia
                val finalBitmap = if (theme == ReadingTheme.NIGHT || theme == ReadingTheme.OLED) {
                    val invertedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val filterCanvas = android.graphics.Canvas(invertedBitmap)
                    val colorMatrix = ColorMatrix(
                        floatArrayOf(
                            -0.85f, 0f, 0f, 0f, 230f,
                            0f, -0.85f, 0f, 0f, 230f,
                            0f, 0f, -0.80f, 0f, 240f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    val paint = Paint().apply {
                        colorFilter = ColorMatrixColorFilter(colorMatrix)
                    }
                    filterCanvas.drawBitmap(bitmap, 0f, 0f, paint)
                    bitmap.recycle()
                    invertedBitmap
                } else if (theme == ReadingTheme.SEPIA) {
                    val sepiaBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val filterCanvas = android.graphics.Canvas(sepiaBitmap)
                    val colorMatrix = ColorMatrix(
                        floatArrayOf(
                            0.90f, 0f, 0f, 0f, 20f,
                            0f, 0.85f, 0f, 0f, 10f,
                            0f, 0f, 0.70f, 0f, -10f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    val paint = Paint().apply {
                        colorFilter = ColorMatrixColorFilter(colorMatrix)
                    }
                    filterCanvas.drawBitmap(bitmap, 0f, 0f, paint)
                    bitmap.recycle()
                    sepiaBitmap
                } else {
                    bitmap
                }

                pageCache.put(key, finalBitmap)
                finalBitmap
            } catch (e: Exception) {
                Log.e("PdfDocumentHelper", "Error rendering page $pageIndex", e)
                null
            }
        }
    }

    suspend fun prefetchAdjacentPages(
        centerPageIndex: Int,
        targetWidth: Int = 1400,
        theme: ReadingTheme = ReadingTheme.LIGHT
    ) {
        val neighbors = listOf(centerPageIndex + 1, centerPageIndex - 1)
        for (neighbor in neighbors) {
            if (neighbor in 0 until pageCount) {
                if (getCachedPage(neighbor, targetWidth, theme) == null) {
                    renderPage(neighbor, targetWidth, theme)
                }
            }
        }
    }

    suspend fun extractAllPagesText(): List<String>? = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            renderMutex.withLock {
                val renderer = pdfRenderer ?: return@withContext null
                val count = pageCount
                if (count <= 0) return@withContext null
                val list = mutableListOf<String>()
                try {
                    for (i in 0 until count) {
                        try {
                            val page = renderer.openPage(i)
                            val sb = StringBuilder()
                            val textContents = page.textContents
                            for (content in textContents) {
                                val t = content.text
                                if (!t.isNullOrBlank()) {
                                    sb.append(t).append(" ")
                                }
                            }
                            page.close()
                            list.add(sb.toString().trim())
                        } catch (e: Exception) {
                            Log.e("PdfDocumentHelper", "Error extracting text on page $i", e)
                            list.add("")
                        }
                    }
                    if (list.any { it.isNotBlank() }) list else null
                } catch (e: Exception) {
                    Log.e("PdfDocumentHelper", "Failed native text extraction", e)
                    null
                }
            }
        } else {
            null
        }
    }

    /**
     * Retrieves exact normalized bounding boxes (0.0..1.0) for search matches on Android 35+
     */
    suspend fun getSearchHighlightsForPage(pageIndex: Int, query: String): List<SearchHighlightBox> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (android.os.Build.VERSION.SDK_INT >= 35 && trimmed.isNotEmpty()) {
            renderMutex.withLock {
                val renderer = pdfRenderer ?: return@withContext emptyList()
                if (pageIndex < 0 || pageIndex >= pageCount) return@withContext emptyList()
                try {
                    val page = renderer.openPage(pageIndex)
                    val pw = page.width.toFloat().coerceAtLeast(1f)
                    val ph = page.height.toFloat().coerceAtLeast(1f)
                    val list = mutableListOf<SearchHighlightBox>()
                    val matches = page.searchText(trimmed)
                    for (m in matches) {
                        for (b in m.bounds) {
                            list.add(
                                SearchHighlightBox(
                                    normalizedLeft = (b.left / pw).coerceIn(0f, 1f),
                                    normalizedTop = (b.top / ph).coerceIn(0f, 1f),
                                    normalizedRight = (b.right / pw).coerceIn(0f, 1f),
                                    normalizedBottom = (b.bottom / ph).coerceIn(0f, 1f)
                                )
                            )
                        }
                    }
                    page.close()
                    list
                } catch (e: Throwable) {
                    emptyList()
                }
            }
        } else {
            emptyList()
        }
    }

    fun close() {
        try {
            pageCache.evictAll()
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {
            Log.e("PdfDocumentHelper", "Error closing pdf", e)
        } finally {
            pdfRenderer = null
            fileDescriptor = null
            pageCount = 0
        }
    }
}
