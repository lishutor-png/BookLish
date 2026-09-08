package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ReaderUiState
import com.example.ui.viewmodel.ReaderViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    uiState: ReaderUiState,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val book = uiState.currentBook ?: return
    val bookmarks by viewModel.getBookmarksForCurrentBook().collectAsState(initial = emptyList())
    val theme = uiState.settings.theme

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val livePoints = remember { mutableStateListOf<DrawingPoint>() }

    // Reset zoom and pan on page switch
    LaunchedEffect(uiState.currentPageIndex) {
        scale = 1f
        offset = Offset.Zero
        livePoints.clear()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(theme.backgroundColor))
            .testTag("reader_screen")
    ) {
        // Main Interactive PDF & Drawing Canvas Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 60.dp)
                .pointerInput(uiState.activeTool, scale, offset) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isTwoFingerGesture = false
                        var touchMoved = false
                        val startPos = down.position

                        if (uiState.activeTool == ActiveDrawingTool.PEN || uiState.activeTool == ActiveDrawingTool.HIGHLIGHTER) {
                            val localX = (down.position.x - offset.x) / scale
                            val localY = (down.position.y - offset.y) / scale
                            livePoints.add(DrawingPoint(localX, localY))
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            if (pointers.isEmpty()) break

                            if (pointers.size >= 2) {
                                // 2-finger gesture: Zoom & Pan canvas simultaneously!
                                if (!isTwoFingerGesture) {
                                    isTwoFingerGesture = true
                                    livePoints.clear() // Cancel live drawing stroke if 2 fingers touched
                                }

                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val newScale = (scale * zoom).coerceIn(1.0f, 4.5f)

                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (centroid != Offset.Unspecified) {
                                    val offsetDiff = (centroid - offset) * (1 - zoom)
                                    var newOffset = offset + pan - offsetDiff
                                    if (newScale <= 1.02f) {
                                        newOffset = Offset.Zero
                                    }
                                    scale = newScale
                                    offset = newOffset
                                } else {
                                    scale = newScale
                                    offset += pan
                                }

                                event.changes.forEach { it.consume() }
                            } else if (pointers.size == 1 && !isTwoFingerGesture) {
                                val pointer = pointers.first()
                                val currentPos = pointer.position
                                if (abs(currentPos.x - startPos.x) > 4f || abs(currentPos.y - startPos.y) > 4f) {
                                    touchMoved = true
                                }

                                if (uiState.activeTool == ActiveDrawingTool.PEN || uiState.activeTool == ActiveDrawingTool.HIGHLIGHTER) {
                                    val localX = (currentPos.x - offset.x) / scale
                                    val localY = (currentPos.y - offset.y) / scale
                                    livePoints.add(DrawingPoint(localX, localY))
                                    pointer.consume()
                                } else if (uiState.activeTool == ActiveDrawingTool.ERASER) {
                                    val localX = (currentPos.x - offset.x) / scale
                                    val localY = (currentPos.y - offset.y) / scale
                                    viewModel.eraseStrokeAt(DrawingPoint(localX, localY))
                                    pointer.consume()
                                } else {
                                    // ActiveTool == NONE
                                    if (scale > 1.05f) {
                                        val panChange = pointer.positionChange()
                                        offset += panChange
                                        pointer.consume()
                                    }
                                }
                            }
                        }

                        // Pointer Up / Gesture Ended
                        if (!isTwoFingerGesture) {
                            if (uiState.activeTool == ActiveDrawingTool.PEN || uiState.activeTool == ActiveDrawingTool.HIGHLIGHTER) {
                                if (livePoints.isNotEmpty()) {
                                    val isHighlighter = uiState.activeTool == ActiveDrawingTool.HIGHLIGHTER
                                    val color = if (isHighlighter) uiState.highlighterColor else uiState.penColor
                                    val strokeWidth = if (isHighlighter) uiState.highlighterStrokeWidth else uiState.penStrokeWidth
                                    val alpha = if (isHighlighter) 0.35f else 1.0f

                                    val newStroke = DrawingStroke(
                                        points = livePoints.toList(),
                                        color = color,
                                        strokeWidth = strokeWidth,
                                        alpha = alpha,
                                        isHighlighter = isHighlighter
                                    )
                                    viewModel.addStroke(newStroke)
                                }
                            } else if (uiState.activeTool == ActiveDrawingTool.NONE && !touchMoved) {
                                // Tap toggles reader controls
                                viewModel.toggleControls()
                            }
                        }
                        livePoints.clear()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Container with graphicsLayer transformation for 100% synchronized zoom & pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                // 1. PDF Page Rendered Bitmap
                if (uiState.activePagePdfBitmap != null) {
                    Image(
                        bitmap = uiState.activePagePdfBitmap.asImageBitmap(),
                        contentDescription = "Halaman Dokumen PDF",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }

                // 2. Vector Drawing Overlay (Committed Strokes & Real-time Live Stroke)
                DrawingCanvasOverlay(
                    activeTool = uiState.activeTool,
                    penColor = uiState.penColor,
                    penStrokeWidth = uiState.penStrokeWidth,
                    highlighterColor = uiState.highlighterColor,
                    highlighterStrokeWidth = uiState.highlighterStrokeWidth,
                    strokes = uiState.currentStrokes,
                    livePoints = livePoints.toList(),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // PDF Page Rendering Spinner Indicator
            if (uiState.isRenderingPdfPage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        // Floating Zoom Controls & 4-Directional Arrow Pan Controller (When not in drawing mode)
        val isControllerVisible = (scale > 1.02f || uiState.isControlsVisible) && uiState.activeTool == ActiveDrawingTool.NONE
        val controllerBottomPadding = if (uiState.isControlsVisible) 108.dp else 24.dp

        AnimatedVisibility(
            visible = isControllerVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = controllerBottomPadding)
        ) {
            CanvasPanZoomController(
                scale = scale,
                offset = offset,
                onScaleChange = { newScale ->
                    scale = newScale
                    if (newScale <= 1.02f) offset = Offset.Zero
                },
                onOffsetChange = { newOffset ->
                    offset = newOffset
                },
                onReset = {
                    scale = 1.0f
                    offset = Offset.Zero
                },
                isDrawingActive = false
            )
        }

        // Gesture & Arrow Controls Hint Pill when in Drawing Mode
        AnimatedVisibility(
            visible = uiState.activeTool == ActiveDrawingTool.PEN || uiState.activeTool == ActiveDrawingTool.HIGHLIGHTER,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 66.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                shadowElevation = 3.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.OpenWith,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Gunakan tombol panah di bilah bawah atau 2 jari untuk geser",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Top Navigation Bar - Clean White & Crimson Theme
        AnimatedVisibility(
            visible = uiState.isControlsVisible || uiState.activeTool != ActiveDrawingTool.NONE,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 3.dp
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
                                    contentDescription = "Kembali ke Beranda",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Column {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.1).sp
                                    ),
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Halaman ${uiState.currentPageIndex + 1} dari ${uiState.totalPages}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 0.3.sp
                                    ),
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.primary
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    contentDescription = "Daftar Halaman",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    tint = MaterialTheme.colorScheme.primary,
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
                .padding(bottom = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 3.dp,
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
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Halaman ${uiState.currentPageIndex + 1} dari ${uiState.totalPages}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
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
            // Floating Drawing Toolbar if drawing tool is active
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
                    onClearPage = { viewModel.setClearPageDialogOpen(true) },
                    scale = scale,
                    offset = offset,
                    onScaleChange = { newScale ->
                        scale = newScale
                        if (newScale <= 1.02f) offset = Offset.Zero
                    },
                    onOffsetChange = { newOffset ->
                        offset = newOffset
                    },
                    onResetZoom = {
                        scale = 1.0f
                        offset = Offset.Zero
                    }
                )
            }

            // Bottom Navigation & Controls Bar - Clean White Design
            AnimatedVisibility(
                visible = uiState.isControlsVisible && uiState.activeTool == ActiveDrawingTool.NONE,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 4.dp
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
                                color = MaterialTheme.colorScheme.primary
                            )

                            Slider(
                                value = uiState.currentPageIndex.toFloat(),
                                onValueChange = { pageFloat ->
                                    viewModel.goToPage(pageFloat.toInt())
                                },
                                valueRange = 0f..(uiState.totalPages - 1).coerceAtLeast(1).toFloat(),
                                steps = (uiState.totalPages - 2).coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                                    .testTag("page_navigation_slider")
                            )

                            Text(
                                text = "${((uiState.currentPageIndex + 1).toFloat() / uiState.totalPages.coerceAtLeast(1) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Bottom Tool Items Row
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
                                    tint = if (uiState.currentPageIndex > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
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

                            // 3. DAFTAR HALAMAN
                            MinimalReaderToolItem(
                                icon = Icons.Default.FormatListBulleted,
                                label = "Halaman",
                                isActive = false,
                                onClick = { viewModel.setTocSheetOpen(true) },
                                testTag = "reader_toc_tab"
                            )

                            // 4. PENGATURAN TAMPILAN
                            MinimalReaderToolItem(
                                icon = Icons.Default.Tune,
                                label = "Tampilan",
                                isActive = false,
                                onClick = { viewModel.setSettingsSheetOpen(true) },
                                testTag = "reading_settings_button"
                            )

                            // 5. TEMA (Putih / Malam / Sepia / OLED)
                            MinimalReaderToolItem(
                                icon = if (theme.isDark) Icons.Default.NightsStay else Icons.Default.WbSunny,
                                label = if (theme.isDark) "Malam" else "Terang",
                                isActive = false,
                                onClick = {
                                    val nextTheme = when (theme) {
                                        ReadingTheme.LIGHT -> ReadingTheme.SEPIA
                                        ReadingTheme.SEPIA -> ReadingTheme.NIGHT
                                        ReadingTheme.NIGHT -> ReadingTheme.OLED
                                        ReadingTheme.OLED -> ReadingTheme.LIGHT
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
                                    tint = if (uiState.currentPageIndex < uiState.totalPages - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
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
                                .background(MaterialTheme.colorScheme.outlineVariant)
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
                currentPageIndex = uiState.currentPageIndex,
                totalPages = uiState.totalPages,
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
                chapterTitle = "Halaman ${uiState.currentPageIndex + 1}",
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
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            border = if (isActive) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

