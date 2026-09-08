package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Floating Canvas Navigation Controller providing:
 * 1. Zoom In (+) and Zoom Out (-) buttons
 * 2. 4-Directional Arrow Keys (Up, Down, Left, Right) to pan canvas with single taps
 * 3. Center Reset button to quickly fit and re-center the document
 * 4. Expandable / Collapsible interface to keep reading unobtrusive
 */
@Composable
fun CanvasPanZoomController(
    scale: Float,
    offset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onReset: () -> Unit,
    isDrawingActive: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val panStep = 100f // Distance in pixels to shift per arrow press

    // Automatically expand or show if zoom is active and user wants navigation
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp,
        modifier = modifier.testTag("canvas_pan_zoom_controller")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(6.dp)
        ) {
            if (isExpanded) {
                // Header: Title & Minimize Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.OpenWith,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Geser & Zoom",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { isExpanded = false },
                        modifier = Modifier.size(28.dp).testTag("collapse_nav_pad_button")
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Kecilkan Kontrol",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )

                // 1. Zoom Controls Row (+ / - / Reset)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    // Zoom Out (-)
                    FilledTonalIconButton(
                        onClick = {
                            val newScale = (scale - 0.25f).coerceAtLeast(1.0f)
                            onScaleChange(newScale)
                            if (newScale <= 1.02f) onReset()
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(36.dp).testTag("zoom_out_button")
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Perkecil Zoom",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Current Zoom Level Badge (Click to reset)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable { onReset() }
                            .padding(horizontal = 2.dp)
                            .testTag("zoom_level_label")
                    ) {
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Zoom In (+)
                    FilledTonalIconButton(
                        onClick = {
                            val newScale = (scale + 0.25f).coerceAtMost(4.5f)
                            onScaleChange(newScale)
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(36.dp).testTag("zoom_in_button")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Perbesar Zoom",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. 4-Directional Arrow Keys (D-Pad Cross)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    // ATAS (Up Arrow) - Moves view UP by increasing offset.y
                    ArrowButton(
                        icon = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Geser ke Atas",
                        onClick = { onOffsetChange(offset + Offset(0f, panStep)) },
                        testTag = "pan_arrow_up"
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // ROW: KIRI (Left), PUSAT/RESET, KANAN (Right)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // KIRI (Left Arrow) - Moves view LEFT by increasing offset.x
                        ArrowButton(
                            icon = Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Geser ke Kiri",
                            onClick = { onOffsetChange(offset + Offset(panStep, 0f)) },
                            testTag = "pan_arrow_left"
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // CENTER: Fit Screen / Reset
                        FilledIconButton(
                            onClick = onReset,
                            shape = RoundedCornerShape(10.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (scale > 1.05f || offset != Offset.Zero) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (scale > 1.05f || offset != Offset.Zero) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            modifier = Modifier.size(40.dp).testTag("pan_center_fit")
                        ) {
                            Icon(
                                Icons.Default.FitScreen,
                                contentDescription = "Pusatkan Dokumen",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // KANAN (Right Arrow) - Moves view RIGHT by decreasing offset.x
                        ArrowButton(
                            icon = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Geser ke Kanan",
                            onClick = { onOffsetChange(offset - Offset(panStep, 0f)) },
                            testTag = "pan_arrow_right"
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // BAWAH (Down Arrow) - Moves view DOWN by decreasing offset.y
                    ArrowButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Geser ke Bawah",
                        onClick = { onOffsetChange(offset - Offset(0f, panStep)) },
                        testTag = "pan_arrow_down"
                    )
                }
            } else {
                // Collapsed Compact View: Zoom buttons + D-pad toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    // Zoom Out
                    IconButton(
                        onClick = {
                            val newScale = (scale - 0.25f).coerceAtLeast(1.0f)
                            onScaleChange(newScale)
                            if (newScale <= 1.02f) onReset()
                        },
                        modifier = Modifier.size(34.dp).testTag("zoom_out_button_compact")
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Perkecil",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Zoom percentage label
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onReset() }
                            .padding(horizontal = 6.dp)
                            .testTag("zoom_level_label_compact")
                    )

                    // Zoom In
                    IconButton(
                        onClick = {
                            val newScale = (scale + 0.25f).coerceAtMost(4.5f)
                            onScaleChange(newScale)
                        },
                        modifier = Modifier.size(34.dp).testTag("zoom_in_button_compact")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Perbesar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .height(20.dp)
                            .padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Directional D-pad expand button with label
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clickable { isExpanded = true }
                            .testTag("expand_nav_pad_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.OpenWith,
                                contentDescription = "Buka Panah Geser",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Geser",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    FilledTonalIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .size(40.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}
