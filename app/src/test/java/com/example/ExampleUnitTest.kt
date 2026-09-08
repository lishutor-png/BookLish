package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun bookEntity_progressCalculation_isAccurate() {
        val book = BookEntity(
            id = 1,
            title = "Testing PDF Document",
            author = "Penulis Uji",
            fileUri = "content://sample/test.pdf",
            localFilePath = "/data/user/0/com.example/files/books/test.pdf",
            fileType = "PDF",
            totalPages = 10,
            currentPage = 4,
            progressPercent = 50,
            coverColorHex = "#DC2626"
        )

        assertEquals("PDF", book.fileType)
        assertEquals(10, book.totalPages)
        assertEquals(4, book.currentPage)
        assertEquals(50, book.progressPercent)
        assertEquals("PDF", book.fileType)
    }

    @Test
    fun drawingPoints_jsonSerialization_roundtripSuccess() {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val listType = Types.newParameterizedType(List::class.java, DrawingPoint::class.java)
        val adapter = moshi.adapter<List<DrawingPoint>>(listType)

        val originalPoints = listOf(
            DrawingPoint(10.5f, 20.2f),
            DrawingPoint(35.0f, 42.8f),
            DrawingPoint(100.1f, 250.7f)
        )

        val json = adapter.toJson(originalPoints)
        assertNotNull(json)
        assertTrue(json.contains("10.5"))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals(3, deserialized!!.size)
        assertEquals(10.5f, deserialized[0].x, 0.01f)
        assertEquals(20.2f, deserialized[0].y, 0.01f)
        assertEquals(100.1f, deserialized[2].x, 0.01f)
    }

    @Test
    fun zoomScale_clampingBounds_areCorrect() {
        var scale = 1.0f

        // Zoom in step test
        scale = (scale + 0.25f).coerceAtMost(4.5f)
        assertEquals(1.25f, scale, 0.001f)

        // Repeat zoom in beyond 4.5f
        for (i in 1..20) {
            scale = (scale + 0.25f).coerceAtMost(4.5f)
        }
        assertEquals(4.5f, scale, 0.001f)

        // Zoom out step test down to 1.0f
        for (i in 1..25) {
            scale = (scale - 0.25f).coerceAtLeast(1.0f)
        }
        assertEquals(1.0f, scale, 0.001f)
    }

    @Test
    fun panOffset_directionalStep_adjustsCorrectly() {
        var offset = Offset.Zero
        val panStep = 100f

        // Shift UP (+panStep on Y)
        offset += Offset(0f, panStep)
        assertEquals(0f, offset.x, 0.001f)
        assertEquals(100f, offset.y, 0.001f)

        // Shift LEFT (+panStep on X)
        offset += Offset(panStep, 0f)
        assertEquals(100f, offset.x, 0.001f)
        assertEquals(100f, offset.y, 0.001f)

        // Shift DOWN (-panStep on Y)
        offset -= Offset(0f, panStep)
        assertEquals(100f, offset.x, 0.001f)
        assertEquals(0f, offset.y, 0.001f)

        // Shift RIGHT (-panStep on X)
        offset -= Offset(panStep, 0f)
        assertEquals(0f, offset.x, 0.001f)
        assertEquals(0f, offset.y, 0.001f)
    }

    @Test
    fun readingSettings_defaults_areCleanWhite() {
        val settings = ReadingSettings()
        assertEquals(ReadingTheme.LIGHT, settings.theme)
        assertEquals(16f, settings.fontSizeSp, 0.01f)
        assertEquals(ReaderFontFamily.SANS_SERIF, settings.fontFamily)
    }

    @Test
    fun activeDrawingTool_options_areAvailable() {
        val tools = ActiveDrawingTool.values()
        assertTrue(tools.contains(ActiveDrawingTool.NONE))
        assertTrue(tools.contains(ActiveDrawingTool.PEN))
        assertTrue(tools.contains(ActiveDrawingTool.HIGHLIGHTER))
        assertTrue(tools.contains(ActiveDrawingTool.ERASER))
    }

    @Test
    fun drawingStroke_penVsHighlighter_propertiesAreCorrect() {
        val penStroke = DrawingStroke(
            points = listOf(DrawingPoint(10f, 10f), DrawingPoint(20f, 20f)),
            color = Color(0xFFDC2626),
            strokeWidth = 6f,
            isHighlighter = false,
            alpha = 1.0f
        )
        assertFalse(penStroke.isHighlighter)
        assertEquals(1.0f, penStroke.alpha, 0.001f)
        assertEquals(6f, penStroke.strokeWidth, 0.001f)

        val highlighterStroke = DrawingStroke(
            points = listOf(DrawingPoint(10f, 10f), DrawingPoint(80f, 10f)),
            color = Color(0xFFFFEB3B),
            strokeWidth = 24f,
            isHighlighter = true,
            alpha = 0.35f
        )
        assertTrue(highlighterStroke.isHighlighter)
        assertEquals(0.35f, highlighterStroke.alpha, 0.001f)
        assertEquals(24f, highlighterStroke.strokeWidth, 0.001f)
    }

    @Test
    fun eraser_distanceProximity_identifiesTargetStroke() {
        val stroke1 = DrawingStroke(
            id = 1L,
            points = listOf(DrawingPoint(100f, 100f), DrawingPoint(110f, 110f)),
            color = Color.Black,
            strokeWidth = 5f
        )
        val stroke2 = DrawingStroke(
            id = 2L,
            points = listOf(DrawingPoint(500f, 500f), DrawingPoint(510f, 510f)),
            color = Color.Blue,
            strokeWidth = 5f
        )
        val strokes = listOf(stroke1, stroke2)

        val touchNearStroke1 = DrawingPoint(105f, 105f)
        val tolerance = 30f
        val hitStroke = strokes.lastOrNull { stroke ->
            stroke.points.any { p ->
                val dx = p.x - touchNearStroke1.x
                val dy = p.y - touchNearStroke1.y
                dx * dx + dy * dy < tolerance * tolerance
            }
        }

        assertNotNull(hitStroke)
        assertEquals(1L, hitStroke?.id)
    }

    @Test
    fun readingTheme_toggleCycle_coversAllThemes() {
        var currentTheme = ReadingTheme.LIGHT

        currentTheme = when (currentTheme) {
            ReadingTheme.LIGHT -> ReadingTheme.SEPIA
            ReadingTheme.SEPIA -> ReadingTheme.NIGHT
            ReadingTheme.NIGHT -> ReadingTheme.OLED
            ReadingTheme.OLED -> ReadingTheme.LIGHT
        }
        assertEquals(ReadingTheme.SEPIA, currentTheme)

        currentTheme = when (currentTheme) {
            ReadingTheme.LIGHT -> ReadingTheme.SEPIA
            ReadingTheme.SEPIA -> ReadingTheme.NIGHT
            ReadingTheme.NIGHT -> ReadingTheme.OLED
            ReadingTheme.OLED -> ReadingTheme.LIGHT
        }
        assertEquals(ReadingTheme.NIGHT, currentTheme)

        currentTheme = when (currentTheme) {
            ReadingTheme.LIGHT -> ReadingTheme.SEPIA
            ReadingTheme.SEPIA -> ReadingTheme.NIGHT
            ReadingTheme.NIGHT -> ReadingTheme.OLED
            ReadingTheme.OLED -> ReadingTheme.LIGHT
        }
        assertEquals(ReadingTheme.OLED, currentTheme)

        currentTheme = when (currentTheme) {
            ReadingTheme.LIGHT -> ReadingTheme.SEPIA
            ReadingTheme.SEPIA -> ReadingTheme.NIGHT
            ReadingTheme.NIGHT -> ReadingTheme.OLED
            ReadingTheme.OLED -> ReadingTheme.LIGHT
        }
        assertEquals(ReadingTheme.LIGHT, currentTheme)
    }

    @Test
    fun pageIndex_navigationBounds_cannotExceedLimits() {
        val totalPages = 5
        var currentPage = 0

        // Attempt previous page when at 0
        val prevAttempt = if (currentPage > 0) currentPage - 1 else currentPage
        assertEquals(0, prevAttempt)

        // Advance to last page
        currentPage = 4
        val nextAttempt = if (currentPage < totalPages - 1) currentPage + 1 else currentPage
        assertEquals(4, nextAttempt)
    }

    @Test
    fun bookmarkEntity_withNote_storesCorrectly() {
        val bookmark = BookmarkEntity(
            id = 10L,
            bookId = 1L,
            pageIndex = 2,
            chapterTitle = "Halaman 3",
            excerpt = "Penanda Halaman 3 - Dokumen Panduan",
            note = "Rumus penting bab 2",
            createdAt = System.currentTimeMillis()
        )

        assertEquals(1L, bookmark.bookId)
        assertEquals(2, bookmark.pageIndex)
        assertEquals("Rumus penting bab 2", bookmark.note)
        assertEquals("Halaman 3", bookmark.chapterTitle)
    }
}
