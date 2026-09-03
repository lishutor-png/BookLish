package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveDrawingTool
import com.example.ui.theme.*

@Composable
fun DrawingToolbar(
    activeTool: ActiveDrawingTool,
    penColor: Color,
    penStrokeWidth: Float,
    highlighterColor: Color,
    highlighterStrokeWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onSelectTool: (ActiveDrawingTool) -> Unit,
    onSelectPenColor: (Color) -> Unit,
    onSelectPenStrokeWidth: (Float) -> Unit,
    onSelectHighlighterColor: (Color) -> Unit,
    onSelectHighlighterStrokeWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val penColors = listOf(
        Color(0xFFDC2626), // LishPDF Crimson Red
        Color(0xFF0F172A), // Deep Charcoal
        Color(0xFF2563EB), // Royal Blue
        Color(0xFF059669), // Emerald Green
        Color(0xFFD97706), // Amber
        Color(0xFF7C3AED), // Violet
        Color(0xFFFFFFFF)  // White
    )

    val highlighterColors = listOf(
        Color(0xFFFFEB3B), // Neon Yellow
        Color(0xFF4ADE80), // Neon Green
        Color(0xFF38BDF8), // Neon Cyan
        Color(0xFFFF7043), // Neon Orange
        Color(0xFFF472B6), // Neon Pink
        Color(0xFFC084FC)  // Soft Purple
    )

    val isExpanded = activeTool != ActiveDrawingTool.NONE

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(10.dp, RoundedCornerShape(22.dp), spotColor = Color(0x20000000))
            .testTag("drawing_toolbar"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Tool Selection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bolpoin (Pen)
                DrawingToolButton(
                    icon = Icons.Default.Edit,
                    label = "Bolpoin",
                    isSelected = activeTool == ActiveDrawingTool.PEN,
                    onClick = {
                        if (activeTool == ActiveDrawingTool.PEN) {
                            onSelectTool(ActiveDrawingTool.NONE)
                        } else {
                            onSelectTool(ActiveDrawingTool.PEN)
                        }
                    },
                    testTag = "tool_pen_button"
                )

                // Stabilo (Highlighter)
                DrawingToolButton(
                    icon = Icons.Default.Brush,
                    label = "Stabilo",
                    isSelected = activeTool == ActiveDrawingTool.HIGHLIGHTER,
                    onClick = {
                        if (activeTool == ActiveDrawingTool.HIGHLIGHTER) {
                            onSelectTool(ActiveDrawingTool.NONE)
                        } else {
                            onSelectTool(ActiveDrawingTool.HIGHLIGHTER)
                        }
                    },
                    testTag = "tool_highlighter_button"
                )

                // Penghapus (Eraser)
                DrawingToolButton(
                    icon = Icons.Default.AutoFixNormal,
                    label = "Hapus",
                    isSelected = activeTool == ActiveDrawingTool.ERASER,
                    onClick = {
                        if (activeTool == ActiveDrawingTool.ERASER) {
                            onSelectTool(ActiveDrawingTool.NONE)
                        } else {
                            onSelectTool(ActiveDrawingTool.ERASER)
                        }
                    },
                    testTag = "tool_eraser_button"
                )

                // Divider
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Undo
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(38.dp).testTag("undo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Redo
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(38.dp).testTag("redo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Clear page strokes
                IconButton(
                    onClick = onClearPage,
                    modifier = Modifier.size(38.dp).testTag("clear_page_strokes_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus Coretan Halaman",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Close / Done Drawing
                if (isExpanded) {
                    IconButton(
                        onClick = { onSelectTool(ActiveDrawingTool.NONE) },
                        modifier = Modifier.size(38.dp).testTag("close_drawing_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selesai",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Expanded Customization Panel for Pen & Highlighter
            AnimatedVisibility(
                visible = activeTool == ActiveDrawingTool.PEN || activeTool == ActiveDrawingTool.HIGHLIGHTER,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val isPen = activeTool == ActiveDrawingTool.PEN
                val currentColor = if (isPen) penColor else highlighterColor
                val currentWidth = if (isPen) penStrokeWidth else highlighterStrokeWidth
                val colorsList = if (isPen) penColors else highlighterColors

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Color Swatches Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WARNA:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (color in colorsList) {
                                val isSelected = currentColor == color
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            if (isPen) {
                                                onSelectPenColor(color)
                                            } else {
                                                onSelectHighlighterColor(color)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (color == Color.White) Color.Black else Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Stroke Width Header with Real-time Live Preview Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isPen) "UKURAN BOLPOIN:" else "UKURAN STABILO:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${currentWidth.toInt()} px",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Live visual preview circle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Pratinjau:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val previewSize = if (isPen) {
                                    currentWidth.coerceIn(3f, 26f).dp
                                } else {
                                    (currentWidth * 0.45f).coerceIn(6f, 26f).dp
                                }
                                Box(
                                    modifier = Modifier
                                        .size(previewSize)
                                        .clip(if (isPen) CircleShape else RoundedCornerShape(2.dp))
                                        .background(currentColor)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 3. Slider with Minus (-) and Plus (+) Fine Adjustment Stepper Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Minus button (Kecilkan)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    if (isPen) {
                                        onSelectPenStrokeWidth((currentWidth - 1f).coerceAtLeast(1f))
                                    } else {
                                        onSelectHighlighterStrokeWidth((currentWidth - 4f).coerceAtLeast(8f))
                                    }
                                }
                                .testTag("decrease_stroke_size_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = "Kecilkan Ukuran",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Slider
                        val minWidth = if (isPen) 1f else 8f
                        val maxWidth = if (isPen) 32f else 64f

                        Slider(
                            value = currentWidth,
                            onValueChange = { newWidth ->
                                if (isPen) {
                                    onSelectPenStrokeWidth(newWidth)
                                } else {
                                    onSelectHighlighterStrokeWidth(newWidth)
                                }
                            },
                            valueRange = minWidth..maxWidth,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .testTag("stroke_width_slider")
                        )

                        // Plus button (Besarkan)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    if (isPen) {
                                        onSelectPenStrokeWidth((currentWidth + 1f).coerceAtMost(32f))
                                    } else {
                                        onSelectHighlighterStrokeWidth((currentWidth + 4f).coerceAtMost(64f))
                                    }
                                }
                                .testTag("increase_stroke_size_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Besarkan Ukuran",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 4. Quick Preset Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val presets = if (isPen) {
                            listOf(
                                2f to "2px Sangat Halus",
                                4f to "4px Halus",
                                8f to "8px Sedang",
                                14f to "14px Tebal",
                                22f to "22px Ekstra"
                            )
                        } else {
                            listOf(
                                14f to "14px Tipis",
                                24f to "24px Standar",
                                38f to "38px Tebal",
                                52f to "52px Lebar"
                            )
                        }

                        for ((sizeVal, label) in presets) {
                            val isSelected = currentWidth.toInt() == sizeVal.toInt()
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (isPen) {
                                            onSelectPenStrokeWidth(sizeVal)
                                        } else {
                                            onSelectHighlighterStrokeWidth(sizeVal)
                                        }
                                    }
                                    .testTag("preset_size_${sizeVal.toInt()}px")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = "${sizeVal.toInt()}px",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Eraser notice
            AnimatedVisibility(
                visible = activeTool == ActiveDrawingTool.ERASER,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Sentuh atau usap garis coretan yang ingin dihapus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DrawingToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .size(40.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

