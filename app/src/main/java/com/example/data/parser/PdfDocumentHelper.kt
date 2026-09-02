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
import androidx.compose.ui.graphics.Color
import com.example.data.model.ReadingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfDocumentHelper(private val context: Context) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    var pageCount: Int = 0
        private set

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

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1080, theme: ReadingTheme = ReadingTheme.NIGHT): Bitmap? =
        withContext(Dispatchers.IO) {
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
                if (theme == ReadingTheme.NIGHT || theme == ReadingTheme.OLED) {
                    val invertedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val filterCanvas = android.graphics.Canvas(invertedBitmap)
                    // Invert colors and dim slightly for eye comfort
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
            } catch (e: Exception) {
                Log.e("PdfDocumentHelper", "Error rendering page $pageIndex", e)
                null
            }
        }

    fun close() {
        try {
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
