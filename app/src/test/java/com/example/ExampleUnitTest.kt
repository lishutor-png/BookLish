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
        assertTrue(book.isPdf)
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
        assertTrue(settings.isLightStatusBar)
        assertTrue(settings.keepScreenOn)
    }

    @Test
    fun activeDrawingTool_options_areAvailable() {
        val tools = ActiveDrawingTool.values()
        assertTrue(tools.contains(ActiveDrawingTool.NONE))
        assertTrue(tools.contains(ActiveDrawingTool.PEN))
        assertTrue(tools.contains(ActiveDrawingTool.HIGHLIGHTER))
        assertTrue(tools.contains(ActiveDrawingTool.ERASER))
    }
}
