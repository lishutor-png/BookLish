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
    NIGHT(
        title = "Minimal Dark 🌙",
        backgroundColor = 0xFF1A1C1E,
        textColor = 0xFFE2E2E6,
        surfaceColor = 0xFF212327,
        accentColor = 0xFFD0BCFF,
        isDark = true
    ),
    LIGHT(
        title = "Minimal Light ☀️",
        backgroundColor = 0xFFF9FAFB,
        textColor = 0xFF1A1C1E,
        surfaceColor = 0xFFFFFFFF,
        accentColor = 0xFF4F378B,
        isDark = false
    ),
    SEPIA(
        title = "Sepia Hangat ☕",
        backgroundColor = 0xFFF4ECD8,
        textColor = 0xFF3D3222,
        surfaceColor = 0xFFEFE4CB,
        accentColor = 0xFF8C5000,
        isDark = false
    ),
    OLED(
        title = "OLED Pure 🖤",
        backgroundColor = 0xFF000000,
        textColor = 0xFFE2E2E6,
        surfaceColor = 0xFF141618,
        accentColor = 0xFFD0BCFF,
        isDark = true
    )
}

enum class ReaderFontFamily(val displayName: String) {
    SANS_SERIF("Sans-Serif Minimal"),
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
    val fontSizeSp: Float = 17f,
    val lineHeightMultiplier: Float = 1.65f,
    val theme: ReadingTheme = ReadingTheme.NIGHT,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SANS_SERIF,
    val textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
    val horizontalPaddingDp: Int = 20,
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
