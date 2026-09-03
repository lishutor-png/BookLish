package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class ReadingTheme(
    val title: String,
    val backgroundColor: Long,
    val textColor: Long,
    val surfaceColor: Long,
    val accentColor: Long,
    val isDark: Boolean
) {
    LIGHT(
        title = "Putih Bersih ☀️",
        backgroundColor = 0xFFFFFFFF,
        textColor = 0xFF0F172A,
        surfaceColor = 0xFFF8FAFC,
        accentColor = 0xFFDC2626,
        isDark = false
    ),
    SEPIA(
        title = "Sepia Nyaman ☕",
        backgroundColor = 0xFFFBF7EE,
        textColor = 0xFF3D3222,
        surfaceColor = 0xFFF5EED9,
        accentColor = 0xFFB45309,
        isDark = false
    ),
    NIGHT(
        title = "Gelap Malam 🌙",
        backgroundColor = 0xFF121417,
        textColor = 0xFFF8FAFC,
        surfaceColor = 0xFF1A1D21,
        accentColor = 0xFFDC2626,
        isDark = true
    ),
    OLED(
        title = "OLED Pure 🖤",
        backgroundColor = 0xFF000000,
        textColor = 0xFFF8FAFC,
        surfaceColor = 0xFF121417,
        accentColor = 0xFFDC2626,
        isDark = true
    )
}

enum class ReaderFontFamily(val displayName: String) {
    SANS_SERIF("Sans-Serif Modern"),
    SERIF("Serif Klasik (Buku)"),
    MONOSPACE("Monospace Rapi"),
    ROUNDED("Rounded Santai")
}

enum class ReaderTextAlign(val displayName: String) {
    JUSTIFY("Rata Kanan-Kiri"),
    LEFT("Rata Kiri"),
    CENTER("Rata Tengah")
}

data class ReadingSettings(
    val fontSizeSp: Float = 16f,
    val lineHeightMultiplier: Float = 1.6f,
    val theme: ReadingTheme = ReadingTheme.LIGHT,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SANS_SERIF,
    val textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
    val horizontalPaddingDp: Int = 16,
    val isAutoScrollEnabled: Boolean = false,
    val autoScrollSpeed: Float = 1f
)

enum class ActiveDrawingTool {
    NONE,
    PEN,
    HIGHLIGHTER,
    ERASER
}

data class DrawingPoint(
    val x: Float,
    val y: Float
)

data class DrawingStroke(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val points: List<DrawingPoint>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false,
    val alpha: Float = if (isHighlighter) 0.35f else 1.0f
)

