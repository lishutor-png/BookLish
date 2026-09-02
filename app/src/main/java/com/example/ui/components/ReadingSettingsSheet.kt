package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReaderFontFamily
import com.example.data.model.ReaderTextAlign
import com.example.data.model.ReadingSettings
import com.example.data.model.ReadingTheme
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSettingsSheet(
    settings: ReadingSettings,
    onUpdateSettings: ((ReadingSettings) -> ReadingSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MinimalDarkSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(48.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MinimalBorder)
            )
        },
        modifier = Modifier.testTag("reading_settings_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Tampilan & Tipografi",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                ),
                color = MinimalTextPrimary
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 1. THEME SELECTION (Minimal Dark, Minimal Light, Sepia, OLED)
            Text(
                text = "TEMA WARNA LAYAR",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                ),
                color = MinimalPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (theme in ReadingTheme.values()) {
                    val isSelected = settings.theme == theme
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MinimalPrimary else MinimalBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onUpdateSettings { it.copy(theme = theme) }
                            },
                        color = Color(theme.backgroundColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = when (theme) {
                                    ReadingTheme.NIGHT -> "Dark 🌙"
                                    ReadingTheme.LIGHT -> "Light ☀️"
                                    ReadingTheme.SEPIA -> "Sepia ☕"
                                    ReadingTheme.OLED -> "OLED 🖤"
                                },
                                color = Color(theme.textColor),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. FONT SIZE CONTROLS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UKURAN TEKS (${settings.fontSizeSp.toInt()} SP)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    ),
                    color = MinimalPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MinimalDarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalBorder),
                        modifier = Modifier
                            .clickable {
                                if (settings.fontSizeSp > 12f) {
                                    onUpdateSettings { it.copy(fontSizeSp = it.fontSizeSp - 1f) }
                                }
                            }
                    ) {
                        Text(
                            text = "A-",
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MinimalDarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalBorder),
                        modifier = Modifier
                            .clickable {
                                if (settings.fontSizeSp < 36f) {
                                    onUpdateSettings { it.copy(fontSizeSp = it.fontSizeSp + 1f) }
                                }
                            }
                    ) {
                        Text(
                            text = "A+",
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Slider(
                value = settings.fontSizeSp,
                onValueChange = { newSize ->
                    onUpdateSettings { it.copy(fontSizeSp = newSize) }
                },
                valueRange = 12f..36f,
                steps = 23,
                colors = SliderDefaults.colors(
                    thumbColor = MinimalPrimary,
                    activeTrackColor = MinimalPrimary,
                    inactiveTrackColor = MinimalBorder
                ),
                modifier = Modifier.fillMaxWidth().testTag("font_size_slider")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. FONT FAMILY
            Text(
                text = "GAYA TIPOGRAFI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                ),
                color = MinimalPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (font in ReaderFontFamily.values()) {
                    val isSelected = settings.fontFamily == font
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MinimalPrimaryContainer else MinimalDarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MinimalPrimary else MinimalBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onUpdateSettings { it.copy(fontFamily = font) }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = when (font) {
                                    ReaderFontFamily.SANS_SERIF -> "Sans"
                                    ReaderFontFamily.SERIF -> "Serif"
                                    ReaderFontFamily.MONOSPACE -> "Mono"
                                    ReaderFontFamily.ROUNDED -> "Round"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MinimalPrimary else MinimalTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. TEXT ALIGNMENT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PERATAAN PARAGRAF",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    ),
                    color = MinimalPrimary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val alignments = listOf(
                        ReaderTextAlign.LEFT to Icons.Default.FormatAlignLeft,
                        ReaderTextAlign.CENTER to Icons.Default.FormatAlignCenter,
                        ReaderTextAlign.JUSTIFY to Icons.Default.FormatAlignJustify
                    )

                    for ((align, icon) in alignments) {
                        val isSelected = settings.textAlign == align
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MinimalPrimaryContainer else MinimalDarkSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MinimalPrimary else MinimalBorder
                            ),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onUpdateSettings { it.copy(textAlign = align) } }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MinimalPrimary else MinimalTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
