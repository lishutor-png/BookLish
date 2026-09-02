package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ReaderUiState
import com.example.ui.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    uiState: ReaderUiState,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val book = uiState.currentBook ?: return
    val isPdf = book.fileType.equals("PDF", ignoreCase = true)
    val theme = uiState.settings.theme
    val bookmarks by viewModel.getBookmarksForCurrentBook().collectAsState(initial = emptyList())

    val backgroundColor = Color(theme.backgroundColor)
    val textColor = Color(theme.textColor)

    val fontFamily = remember(uiState.settings.fontFamily) {
        when (uiState.settings.fontFamily) {
            ReaderFontFamily.SERIF -> FontFamily.Serif
            ReaderFontFamily.SANS_SERIF -> FontFamily.SansSerif
            ReaderFontFamily.MONOSPACE -> FontFamily.Monospace
            ReaderFontFamily.ROUNDED -> FontFamily.Default
        }
    }

    val textAlign = remember(uiState.settings.textAlign) {
        when (uiState.settings.textAlign) {
            ReaderTextAlign.JUSTIFY -> TextAlign.Justify
            ReaderTextAlign.LEFT -> TextAlign.Start
            ReaderTextAlign.CENTER -> TextAlign.Center
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .testTag("reader_screen")
    ) {
        // Document Content View (EPUB Text or PDF Bitmap)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (uiState.activeTool == ActiveDrawingTool.NONE) {
                        viewModel.toggleControls()
                    }
                }
        ) {
            if (isPdf) {
                PdfViewerContent(
                    bitmap = uiState.activePagePdfBitmap,
                    isRendering = uiState.isRenderingPdfPage,
                    theme = theme
                )
            } else {
                EpubViewerContent(
                    uiState = uiState,
                    fontFamily = fontFamily,
                    textColor = textColor,
                    textAlign = textAlign
                )
            }

            // Interactive Drawing Canvas Overlay (Bolpoin & Stabilo & Eraser)
            DrawingCanvasOverlay(
                activeTool = uiState.activeTool,
                penColor = uiState.penColor,
                penStrokeWidth = uiState.penStrokeWidth,
                highlighterColor = uiState.highlighterColor,
                highlighterStrokeWidth = uiState.highlighterStrokeWidth,
                strokes = uiState.currentStrokes,
                onAddStroke = { stroke -> viewModel.addStroke(stroke) },
                onEraseAt = { point -> viewModel.eraseStrokeAt(point) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Navigation Bar - Clean Minimalist UI
        AnimatedVisibility(
            visible = uiState.isControlsVisible || uiState.activeTool != ActiveDrawingTool.NONE,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MinimalDarkBackground.copy(alpha = 0.96f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MinimalBorder.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Back button & Book Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onBackToLibrary,
                                modifier = Modifier.size(40.dp).testTag("back_to_library_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kembali ke Perpustakaan",
                                    tint = MinimalTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Column {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = (-0.1).sp
                                    ),
                                    maxLines = 1,
                                    color = MinimalTextPrimary
                                )
                                Text(
                                    text = uiState.currentChapterTitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 0.3.sp
                                    ),
                                    maxLines = 1,
                                    color = MinimalPrimary
                                )
                            }
                        }

                        // Right: Actions Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Search inside document
                            IconButton(
                                onClick = { viewModel.setSearchSheetOpen(true) },
                                modifier = Modifier.size(40.dp).testTag("search_in_doc_button")
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Cari Kata",
                                    tint = MinimalTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Table of Contents
                            IconButton(
                                onClick = { viewModel.setTocSheetOpen(true) },
                                modifier = Modifier.size(40.dp).testTag("toc_button")
                            ) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = "Daftar Isi",
                                    tint = MinimalTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Bookmarks List
                            val isBookmarked = bookmarks.any { it.pageIndex == uiState.currentPageIndex }
                            IconButton(
                                onClick = { viewModel.setBookmarksSheetOpen(true) },
                                modifier = Modifier.size(40.dp).testTag("bookmarks_button")
                            ) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Penanda Halaman",
                                    tint = if (isBookmarked) MinimalPrimary else MinimalTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Quick Add Bookmark with Note
                            IconButton(
                                onClick = { viewModel.setAddBookmarkDialogOpen(true) },
                                modifier = Modifier.size(40.dp).testTag("add_bookmark_quick_button")
                            ) {
                                Icon(
                                    Icons.Default.BookmarkAdd,
                                    contentDescription = "Tambah Penanda",
                                    tint = MinimalPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Minimal Floating Status Pill when controls hidden
        AnimatedVisibility(
            visible = !uiState.isControlsVisible && uiState.activeTool == ActiveDrawingTool.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MinimalDarkSurfaceVariant.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MinimalBorder.copy(alpha = 0.8f)),
                modifier = Modifier.clickable { viewModel.toggleControls() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4ADE80))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Halaman ${uiState.currentPageIndex + 1} dari ${uiState.totalPages}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MinimalTextPrimary
                    )
                }
            }
        }

        // Bottom Reader Navigation Bar & Floating Drawing Toolbar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Floating Drawing Toolbar if drawing tool is active (with adjustable pen & stabilo size)
            AnimatedVisibility(
                visible = uiState.activeTool != ActiveDrawingTool.NONE,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                DrawingToolbar(
                    activeTool = uiState.activeTool,
                    penColor = uiState.penColor,
                    penStrokeWidth = uiState.penStrokeWidth,
                    highlighterColor = uiState.highlighterColor,
                    highlighterStrokeWidth = uiState.highlighterStrokeWidth,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onSelectTool = { tool -> viewModel.setActiveTool(tool) },
                    onSelectPenColor = { color -> viewModel.setPenColor(color) },
                    onSelectPenStrokeWidth = { width -> viewModel.setPenStrokeWidth(width) },
                    onSelectHighlighterColor = { color -> viewModel.setHighlighterColor(color) },
                    onSelectHighlighterStrokeWidth = { width -> viewModel.setHighlighterStrokeWidth(width) },
                    onUndo = { viewModel.undoDrawing() },
                    onRedo = { viewModel.redoDrawing() },
                    onClearPage = { viewModel.setClearPageDialogOpen(true) }
                )
            }

            // Bottom Navigation & Controls Bar - Clean Minimalism Design
            AnimatedVisibility(
                visible = uiState.isControlsVisible && uiState.activeTool == ActiveDrawingTool.NONE,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MinimalDarkSurface.copy(alpha = 0.98f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Page slider & count row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Halaman ${uiState.currentPageIndex + 1} / ${uiState.totalPages}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MinimalPrimary
                            )

                            Slider(
                                value = uiState.currentPageIndex.toFloat(),
                                onValueChange = { pageFloat ->
                                    viewModel.goToPage(pageFloat.toInt())
                                },
                                valueRange = 0f..(uiState.totalPages - 1).coerceAtLeast(1).toFloat(),
                                steps = (uiState.totalPages - 2).coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = MinimalPrimary,
                                    activeTrackColor = MinimalPrimary,
                                    inactiveTrackColor = MinimalBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                                    .testTag("page_navigation_slider")
                            )

                            Text(
                                text = "${((uiState.currentPageIndex + 1).toFloat() / uiState.totalPages * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MinimalTextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Bottom Minimalist Tool Items Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Prev page
                            IconButton(
                                onClick = { viewModel.previousPage() },
                                enabled = uiState.currentPageIndex > 0,
                                modifier = Modifier.size(36.dp).testTag("previous_page_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Sebelumnya",
                                    tint = if (uiState.currentPageIndex > 0) MinimalTextPrimary else MinimalTextMuted.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 1. BOLPOIN (Coret)
                            MinimalReaderToolItem(
                                icon = Icons.Default.Edit,
                                label = "Bolpoin",
                                isActive = uiState.activeTool == ActiveDrawingTool.PEN,
                                onClick = { viewModel.setActiveTool(ActiveDrawingTool.PEN) },
                                testTag = "activate_pen_chip"
                            )

                            // 2. STABILO (Highlighter)
                            MinimalReaderToolItem(
                                icon = Icons.Default.Brush,
                                label = "Stabilo",
                                isActive = uiState.activeTool == ActiveDrawingTool.HIGHLIGHTER,
                                onClick = { viewModel.setActiveTool(ActiveDrawingTool.HIGHLIGHTER) },
                                testTag = "activate_highlighter_chip"
                            )

                            // 3. DAFTAR ISI
                            MinimalReaderToolItem(
                                icon = Icons.Default.FormatListBulleted,
                                label = "Daftar Isi",
                                isActive = false,
                                onClick = { viewModel.setTocSheetOpen(true) },
                                testTag = "reader_toc_tab"
                            )

                            // 4. PENGATURAN FONT & TAMPILAN
                            MinimalReaderToolItem(
                                icon = Icons.Default.FormatSize,
                                label = "Tampilan",
                                isActive = false,
                                onClick = { viewModel.setSettingsSheetOpen(true) },
                                testTag = "reading_settings_button"
                            )

                            // 5. TEMA (Malam / Siang / Sepia / OLED)
                            MinimalReaderToolItem(
                                icon = if (theme.isDark) Icons.Default.NightsStay else Icons.Default.WbSunny,
                                label = if (theme.isDark) "Malam" else "Siang",
                                isActive = false,
                                onClick = {
                                    val nextTheme = when (theme) {
                                        ReadingTheme.NIGHT -> ReadingTheme.LIGHT
                                        ReadingTheme.LIGHT -> ReadingTheme.SEPIA
                                        ReadingTheme.SEPIA -> ReadingTheme.OLED
                                        ReadingTheme.OLED -> ReadingTheme.NIGHT
                                    }
                                    viewModel.updateSettings { it.copy(theme = nextTheme) }
                                },
                                testTag = "reader_theme_toggle"
                            )

                            // Next page
                            IconButton(
                                onClick = { viewModel.nextPage() },
                                enabled = uiState.currentPageIndex < uiState.totalPages - 1,
                                modifier = Modifier.size(36.dp).testTag("next_page_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Berikutnya",
                                    tint = if (uiState.currentPageIndex < uiState.totalPages - 1) MinimalTextPrimary else MinimalTextMuted.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Home bar indicator grabber
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(MinimalBorder)
                        )
                    }
                }
            }
        }

        // SHEETS & DIALOGS
        if (uiState.isSettingsSheetOpen) {
            ReadingSettingsSheet(
                settings = uiState.settings,
                onUpdateSettings = { transform -> viewModel.updateSettings(transform) },
                onDismiss = { viewModel.setSettingsSheetOpen(false) }
            )
        }

        if (uiState.isTocSheetOpen) {
            TableOfContentsSheet(
                bookTitle = book.title,
                chapters = uiState.epubBook?.chapters ?: emptyList(),
                currentPageIndex = uiState.currentPageIndex,
                totalPages = uiState.totalPages,
                isPdf = isPdf,
                onSelectPageOrChapter = { index ->
                    viewModel.goToPage(index)
                    viewModel.setTocSheetOpen(false)
                },
                onDismiss = { viewModel.setTocSheetOpen(false) }
            )
        }

        if (uiState.isBookmarksSheetOpen) {
            BookmarksSheet(
                bookmarks = bookmarks,
                onSelectBookmark = { bm ->
                    viewModel.goToPage(bm.pageIndex)
                    viewModel.setBookmarksSheetOpen(false)
                },
                onDeleteBookmark = { id -> viewModel.deleteBookmark(id) },
                onOpenAddBookmarkDialog = {
                    viewModel.setBookmarksSheetOpen(false)
                    viewModel.setAddBookmarkDialogOpen(true)
                },
                onDismiss = { viewModel.setBookmarksSheetOpen(false) }
            )
        }

        if (uiState.isSearchSheetOpen) {
            SearchSheet(
                searchQuery = uiState.searchQuery,
                isSearching = uiState.isSearching,
                searchResults = uiState.searchResults,
                onQueryChange = { q -> viewModel.setSearchQuery(q) },
                onResultClick = { result -> viewModel.jumpToSearchResult(result) },
                onDismiss = { viewModel.setSearchSheetOpen(false) }
            )
        }

        if (uiState.isAddBookmarkDialogOpen) {
            AddBookmarkDialog(
                chapterTitle = uiState.currentChapterTitle,
                onConfirm = { note -> viewModel.addBookmark(note) },
                onDismiss = { viewModel.setAddBookmarkDialogOpen(false) }
            )
        }

        if (uiState.isClearPageDialogOpen) {
            ClearPageDialog(
                onConfirm = { viewModel.clearAllStrokesOnCurrentPage() },
                onDismiss = { viewModel.setClearPageDialogOpen(false) }
            )
        }
    }
}

@Composable
fun MinimalReaderToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isActive) MinimalPrimaryContainer else Color.Transparent,
            border = if (isActive) null else androidx.compose.foundation.BorderStroke(1.dp, MinimalBorder.copy(alpha = 0.4f)),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) MinimalPrimary else MinimalTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.5.sp
            ),
            color = if (isActive) MinimalPrimary else MinimalTextMuted
        )
    }
}

@Composable
fun EpubViewerContent(
    uiState: ReaderUiState,
    fontFamily: FontFamily,
    textColor: Color,
    textAlign: TextAlign
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.currentPageIndex) {
        scrollState.scrollTo(0)
    }

    val currentChapter = uiState.epubBook?.chapters?.getOrNull(uiState.currentPageIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = 64.dp,
                bottom = 72.dp,
                start = uiState.settings.horizontalPaddingDp.dp,
                end = uiState.settings.horizontalPaddingDp.dp
            )
    ) {
        if (currentChapter != null) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Minimal Breadcrumb header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentChapter.title.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MinimalTextMuted,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Halaman ${uiState.currentPageIndex + 1} dari ${uiState.totalPages}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.3.sp
                            ),
                            color = MinimalTextMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MinimalBorder.copy(alpha = 0.4f))
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = currentChapter.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily,
                            letterSpacing = (-0.2).sp
                        ),
                        color = Color(uiState.settings.theme.accentColor),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    val paragraphs = remember(currentChapter.plainText) {
                        currentChapter.plainText.split("\n\n").filter { it.isNotBlank() }
                    }

                    for (p in paragraphs) {
                        Text(
                            text = p.trim(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = uiState.settings.fontSizeSp.sp,
                                lineHeight = (uiState.settings.fontSizeSp * uiState.settings.lineHeightMultiplier).sp,
                                fontFamily = fontFamily,
                                textAlign = textAlign,
                                letterSpacing = 0.2.sp
                            ),
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = (uiState.settings.fontSizeSp * 0.75f).dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Memuat bab bacaan...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun PdfViewerContent(
    bitmap: Bitmap?,
    isRendering: Boolean,
    theme: ReadingTheme
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 3.5f)
        offset += offsetChange
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 56.dp, bottom = 64.dp)
            .transformable(state = transformableState),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Halaman Dokumen PDF",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        }

        if (isRendering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MinimalPrimary,
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}
