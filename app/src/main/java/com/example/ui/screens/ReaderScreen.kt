package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.viewmodel.ReaderUiState
import com.example.ui.viewmodel.ReaderViewModel
import com.example.ui.viewmodel.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Clamps pan offset so the zoomed PDF page stays within the viewport boundaries
 * and never flies off-screen into empty space.
 */
fun clampPanOffset(rawOffset: Offset, currentScale: Float, containerSize: IntSize): Offset {
    if (currentScale <= 1.01f) return Offset.Zero
    if (containerSize.width <= 0 || containerSize.height <= 0) return rawOffset

    val maxX = (containerSize.width * (currentScale - 1f) / 2f).coerceAtLeast(0f)
    val maxY = (containerSize.height * (currentScale - 1f) / 2f).coerceAtLeast(0f)

    return Offset(
        x = rawOffset.x.coerceIn(-maxX, maxX),
        y = rawOffset.y.coerceIn(-maxY, maxY)
    )
}

/**
 * Computes the exact rendered page dimensions inside a container using ContentScale.Fit logic,
 * ensuring that the highlight Canvas has identical origin and pixel bounds as the PDF Image.
 */
fun computeFittedPageSize(
    containerWidth: Float,
    containerHeight: Float,
    bitmapWidth: Float,
    bitmapHeight: Float
): Size {
    if (containerWidth <= 0f || containerHeight <= 0f || bitmapWidth <= 0f || bitmapHeight <= 0f) {
        return Size(containerWidth.coerceAtLeast(0f), containerHeight.coerceAtLeast(0f))
    }
    val containerAspect = containerWidth / containerHeight
    val bitmapAspect = (bitmapWidth / bitmapHeight).coerceAtLeast(0.05f)
    return if (containerAspect > bitmapAspect) {
        Size(width = containerHeight * bitmapAspect, height = containerHeight)
    } else {
        Size(width = containerWidth, height = containerWidth / bitmapAspect)
    }
}

/**
 * Computes clamped pan offset needed to center a normalized [0..1] point on a centered page
 * inside the zoomed viewport (TransformOrigin.Center).
 */
fun computePanOffsetForNormalizedPoint(
    normX: Float,
    normY: Float,
    pageWidth: Float,
    pageHeight: Float,
    containerSize: IntSize,
    scale: Float
): Offset {
    if (scale <= 1.01f) return Offset.Zero
    val dx = (normX.coerceIn(0f, 1f) - 0.5f) * pageWidth
    val dy = (normY.coerceIn(0f, 1f) - 0.5f) * pageHeight
    return clampPanOffset(
        rawOffset = Offset(x = -dx * scale, y = -dy * scale),
        currentScale = scale,
        containerSize = containerSize
    )
}

data class VisiblePageSpan(
    val index: Int,
    val offset: Int,
    val size: Int
)

/**
 * Selects the page occupying the largest visible vertical span within [viewportStart, viewportEnd],
 * breaking ties by proximity of the page center to the viewport center.
 */
fun resolveDominantVisiblePage(
    visibleItems: List<VisiblePageSpan>,
    viewportStart: Int,
    viewportEnd: Int,
    fallbackIndex: Int = 0
): Int {
    if (visibleItems.isEmpty()) return fallbackIndex
    val viewportCenter = (viewportStart + viewportEnd) / 2
    val best = visibleItems.maxWithOrNull(
        compareBy<VisiblePageSpan>(
            { item ->
                val itemStart = item.offset
                val itemEnd = item.offset + item.size
                (minOf(itemEnd, viewportEnd) - maxOf(itemStart, viewportStart)).coerceAtLeast(0)
            },
            { item ->
                -abs((item.offset + item.size / 2) - viewportCenter)
            }
        )
    )
    return best?.index ?: fallbackIndex
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPdfSearchHighlights(
    boxes: List<com.example.data.parser.SearchHighlightBox>,
    fallbackResults: List<SearchResult>,
    activeMatchIndexInPage: Int?,
    activeResultId: String?
) {
    if (size.width <= 0f || size.height <= 0f) return
    if (boxes.isNotEmpty()) {
        val padX = 1.5.dp.toPx()
        val padY = 1.dp.toPx()
        val minSize = 4.dp.toPx()

        for ((idx, box) in boxes.withIndex()) {
            val isActiveMatch = activeMatchIndexInPage != null && (
                box.matchIndexInPage == activeMatchIndexInPage ||
                    (box.matchIndexInPage < 0 && idx == activeMatchIndexInPage)
                )

            val rawLeft = (minOf(box.normalizedLeft, box.normalizedRight) * size.width).coerceIn(0f, size.width)
            val rawRight = (maxOf(box.normalizedLeft, box.normalizedRight) * size.width).coerceIn(0f, size.width)
            val rawTop = (minOf(box.normalizedTop, box.normalizedBottom) * size.height).coerceIn(0f, size.height)
            val rawBottom = (maxOf(box.normalizedTop, box.normalizedBottom) * size.height).coerceIn(0f, size.height)

            val rawW = (rawRight - rawLeft).coerceAtLeast(0f)
            val rawH = (rawBottom - rawTop).coerceAtLeast(0f)
            val drawW = (rawW + padX * 2f).coerceAtLeast(minSize).coerceAtMost(size.width)
            val drawH = (rawH + padY * 2f).coerceAtLeast(minSize).coerceAtMost(size.height)

            // Expand symmetrically around the true glyph box center so text is covered cleanly without vertical/horizontal shift
            val centerX = (rawLeft + rawRight) / 2f
            val centerY = (rawTop + rawBottom) / 2f
            val drawLeft = (centerX - drawW / 2f).coerceIn(0f, (size.width - drawW).coerceAtLeast(0f))
            val drawTop = (centerY - drawH / 2f).coerceIn(0f, (size.height - drawH).coerceAtLeast(0f))

            val fillColor = if (isActiveMatch) {
                Color(0xFFFFB300).copy(alpha = 0.52f)
            } else {
                Color(0xFFFFEB3B).copy(alpha = 0.40f)
            }
            val strokeColor = if (isActiveMatch) Color(0xFFE65100) else Color(0xFFF57F17)
            val strokeWidth = if (isActiveMatch) 2.dp.toPx() else 1.dp.toPx()

            drawRect(
                color = fillColor,
                topLeft = Offset(drawLeft, drawTop),
                size = Size(drawW, drawH)
            )
            drawRect(
                color = strokeColor,
                topLeft = Offset(drawLeft, drawTop),
                size = Size(drawW, drawH),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }
    } else {
        for (res in fallbackResults) {
            val isActiveMatch = activeResultId != null && res.id == activeResultId
            val yPos = res.normalizedTopFraction * size.height
            val barHeight = 22.dp.toPx()
            val top = (yPos - barHeight / 2f).coerceIn(0f, (size.height - barHeight).coerceAtLeast(0f))
            drawRect(
                color = if (isActiveMatch) Color(0xFFFFB300).copy(alpha = 0.45f) else Color(0xFFFFEB3B).copy(alpha = 0.35f),
                topLeft = Offset(6.dp.toPx(), top),
                size = Size((size.width - 12.dp.toPx()).coerceAtLeast(0f), barHeight)
            )
            drawRect(
                color = if (isActiveMatch) Color(0xFFE65100) else Color(0xFFFFA000).copy(alpha = 0.85f),
                topLeft = Offset(6.dp.toPx(), top),
                size = Size((size.width - 12.dp.toPx()).coerceAtLeast(0f), barHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isActiveMatch) 1.5.dp.toPx() else 1.dp.toPx())
            )
        }
    }
}

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
    val viewMode = uiState.settings.viewMode
    val coroutineScope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var swipeDragOffsetX by remember { mutableFloatStateOf(0f) }
    val livePoints = remember { mutableStateListOf<DrawingPoint>() }

    // Keep latest state references inside pointerInput without restarting the gesture coroutine
    val currentUiState by rememberUpdatedState(uiState)
    val currentContainerSize by rememberUpdatedState(containerSize)

    // Smooth animated swipe feedback when releasing horizontal page drag
    val animatedSwipeOffsetX by animateFloatAsState(
        targetValue = swipeDragOffsetX,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "swipeOffsetX"
    )

    // Reset zoom and pan on page switch in single-page mode
    LaunchedEffect(uiState.currentPageIndex) {
        if (viewMode == ReaderViewMode.SINGLE_PAGE_SWIPE) {
            scale = 1f
            offset = Offset.Zero
        }
        swipeDragOffsetX = 0f
        livePoints.clear()
    }

    // Re-clamp pan offset whenever screen size or orientation changes
    LaunchedEffect(containerSize) {
        offset = clampPanOffset(offset, scale, containerSize)
    }

    // Pan directly to the active search highlight in Single Page Mode if already zoomed in
    LaunchedEffect(uiState.searchNavigationTrigger) {
        if (viewMode == ReaderViewMode.SINGLE_PAGE_SWIPE &&
            uiState.searchNavigationTrigger > 0L &&
            scale > 1.03f
        ) {
            val activeRes = uiState.searchResults.getOrNull(uiState.currentSearchResultIndex)
            val bmp = uiState.activePagePdfBitmap
            if (activeRes != null && activeRes.pageOrChapterIndex == uiState.currentPageIndex &&
                bmp != null && !bmp.isRecycled && containerSize.width > 0 && containerSize.height > 0
            ) {
                val pageBoxes = uiState.searchHighlightBoxes[activeRes.pageOrChapterIndex]
                val targetBox = pageBoxes?.firstOrNull { it.matchIndexInPage == activeRes.matchIndexInPage }
                    ?: pageBoxes?.getOrNull(activeRes.matchIndexInPage)
                val targetNormX = targetBox?.let { (it.normalizedLeft + it.normalizedRight) / 2f } ?: 0.5f
                val targetNormY = targetBox?.let { (it.normalizedTop + it.normalizedBottom) / 2f } ?: activeRes.normalizedTopFraction
                val fitted = computeFittedPageSize(
                    containerWidth = containerSize.width.toFloat(),
                    containerHeight = containerSize.height.toFloat(),
                    bitmapWidth = bmp.width.toFloat(),
                    bitmapHeight = bmp.height.toFloat()
                )
                offset = computePanOffsetForNormalizedPoint(
                    normX = targetNormX,
                    normY = targetNormY,
                    pageWidth = fitted.width,
                    pageHeight = fitted.height,
                    containerSize = containerSize,
                    scale = scale
                )
            }
        }
    }

    val initialResumePage = remember(book.id) { book.currentPage }
    var showResumeToast by remember(book.id) { mutableStateOf(initialResumePage > 0) }
    LaunchedEffect(book.id) {
        if (initialResumePage > 0) {
            delay(3200L)
            showResumeToast = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(theme.backgroundColor))
            .testTag("reader_screen")
    ) {
        // Melanjutkan membaca dari halaman terakhir banner
        AnimatedVisibility(
            visible = showResumeToast && !uiState.isBookLoading,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp)
                .zIndex(20f)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Melanjutkan membaca dari Halaman ${initialResumePage + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // =========================================================================
        // MODE 1: CONTINUOUS VERTICAL SCROLL (GULIR VERTIKAL ATAS-BAWAH MULUS)
        // =========================================================================
        if (viewMode == ReaderViewMode.CONTINUOUS_VERTICAL && uiState.activeTool == ActiveDrawingTool.NONE) {
            val initialSavedPage = remember(book.id) {
                uiState.currentPageIndex.coerceIn(0, (uiState.totalPages - 1).coerceAtLeast(0))
            }
            val lazyListState = remember(book.id) {
                LazyListState(firstVisibleItemIndex = initialSavedPage)
            }
            var hasRestoredScrollPosition by remember(book.id) { mutableStateOf(false) }

            // Restore last read page when book finishes loading
            LaunchedEffect(book.id, uiState.isBookLoading) {
                if (uiState.isBookLoading) {
                    hasRestoredScrollPosition = false
                } else if (!hasRestoredScrollPosition) {
                    val target = currentUiState.currentPageIndex.coerceIn(0, (currentUiState.totalPages - 1).coerceAtLeast(0))
                    if (target > 0) {
                        lazyListState.scrollToItem(target)
                    }
                    hasRestoredScrollPosition = true
                }
            }

            // Sync visible page as user scrolls vertically (guarded so initial page is preserved)
            LaunchedEffect(lazyListState, book.id, uiState.totalPages, uiState.isBookLoading) {
                if (uiState.isBookLoading) return@LaunchedEffect
                snapshotFlow {
                    val state = currentUiState
                    if (!state.isBookLoading && hasRestoredScrollPosition) {
                        val layoutInfo = lazyListState.layoutInfo
                        val spans = layoutInfo.visibleItemsInfo.map { item ->
                            VisiblePageSpan(index = item.index, offset = item.offset, size = item.size)
                        }
                        resolveDominantVisiblePage(
                            visibleItems = spans,
                            viewportStart = layoutInfo.viewportStartOffset,
                            viewportEnd = layoutInfo.viewportEndOffset,
                            fallbackIndex = lazyListState.firstVisibleItemIndex
                        )
                    } else null
                }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { visiblePage ->
                        viewModel.syncVisiblePageInContinuousMode(visiblePage)
                    }
            }

            // Scroll to page if user explicitly jumped via Slider, Next/Prev, TOC, or Bookmark
            LaunchedEffect(uiState.pageJumpTrigger, uiState.isBookLoading) {
                if (!uiState.isBookLoading && hasRestoredScrollPosition && uiState.pageJumpTrigger > 0L) {
                    lazyListState.scrollToItem(currentUiState.currentPageIndex)
                }
            }

            // Scroll precisely to active search result inside target page (handles same-page jumps & tall/zoomed pages)
            LaunchedEffect(uiState.searchNavigationTrigger, uiState.isBookLoading) {
                if (!uiState.isBookLoading && hasRestoredScrollPosition && uiState.searchNavigationTrigger > 0L) {
                    val activeResult = uiState.searchResults.getOrNull(uiState.currentSearchResultIndex) ?: return@LaunchedEffect
                    val targetPage = activeResult.pageOrChapterIndex.coerceIn(0, (uiState.totalPages - 1).coerceAtLeast(0))
                    val pageBoxes = uiState.searchHighlightBoxes[targetPage]
                    val targetBox = pageBoxes?.firstOrNull { it.matchIndexInPage == activeResult.matchIndexInPage }
                        ?: pageBoxes?.getOrNull(activeResult.matchIndexInPage)
                    val targetNormX = targetBox?.let { (it.normalizedLeft + it.normalizedRight) / 2f } ?: 0.5f
                    val targetNormY = targetBox?.let { (it.normalizedTop + it.normalizedBottom) / 2f } ?: activeResult.normalizedTopFraction

                    lazyListState.scrollToItem(targetPage)
                    withFrameNanos { }

                    val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetPage }
                    val viewportHeight = (lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset).coerceAtLeast(1)
                    if (itemInfo != null && itemInfo.size > 0) {
                        val matchYInItem = (targetNormY * itemInfo.size).toInt()
                        val desiredScrollOffset = (matchYInItem - (viewportHeight * 0.35f).toInt())
                            .coerceIn(0, (itemInfo.size - viewportHeight / 2).coerceAtLeast(0))
                        if (desiredScrollOffset > 0) {
                            lazyListState.scrollToItem(targetPage, desiredScrollOffset)
                        }
                    }

                    if (scale > 1.03f && containerSize.width > 0) {
                        offset = computePanOffsetForNormalizedPoint(
                            normX = targetNormX,
                            normY = 0.5f,
                            pageWidth = containerSize.width.toFloat(),
                            pageHeight = containerSize.height.toFloat(),
                            containerSize = containerSize,
                            scale = scale
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (uiState.isControlsVisible) 56.dp else 8.dp,
                        bottom = if (uiState.isControlsVisible) 68.dp else 8.dp
                    )
                    .onSizeChanged { containerSize = it }
                    .pointerInput(Unit) {
                        var lastTapTime = 0L
                        var singleTapJob: Job? = null

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            val startPos = down.position
                            var moved = false
                            var twoFinger = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                if (pressed.size >= 2) {
                                    twoFinger = true
                                    singleTapJob?.cancel()
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val oldScale = scale
                                    val newScale = (oldScale * zoom).coerceIn(1.0f, 4.5f)
                                    val effectiveZoom = newScale / oldScale
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val size = currentContainerSize
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val focusOffset = if (centroid != Offset.Unspecified) {
                                        centroid - center - offset
                                    } else {
                                        Offset.Zero
                                    }
                                    val rawOffset = offset + pan - focusOffset * (effectiveZoom - 1f)
                                    scale = newScale
                                    offset = clampPanOffset(rawOffset, newScale, size)
                                    event.changes.forEach { it.consume() }
                                } else if (pressed.size == 1 && !twoFinger) {
                                    val pointer = pressed.first()
                                    if (abs(pointer.position.x - startPos.x) > 10f ||
                                        abs(pointer.position.y - startPos.y) > 10f
                                    ) {
                                        moved = true
                                    }
                                    // When zoomed in, allow horizontal panning while LazyColumn scrolls vertically
                                    if (scale > 1.03f) {
                                        val delta = pointer.positionChange()
                                        if (abs(delta.x) > abs(delta.y) * 0.5f) {
                                            offset = clampPanOffset(
                                                offset + Offset(delta.x, 0f),
                                                scale,
                                                currentContainerSize
                                            )
                                        }
                                    }
                                }
                            }

                            if (!twoFinger && !moved) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 280L) {
                                    // Double-tap to zoom in/out!
                                    singleTapJob?.cancel()
                                    lastTapTime = 0L
                                    val size = currentContainerSize
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val nextScale = when {
                                        scale < 1.6f -> 2.0f
                                        scale < 2.6f -> 3.0f
                                        else -> 1.0f
                                    }
                                    val rawOffset = if (nextScale <= 1.02f) {
                                        Offset.Zero
                                    } else {
                                        (center - startPos) * (nextScale - 1f)
                                    }
                                    scale = nextScale
                                    offset = clampPanOffset(rawOffset, nextScale, size)
                                } else {
                                    lastTapTime = now
                                    singleTapJob?.cancel()
                                    singleTapJob = coroutineScope.launch {
                                        delay(250L)
                                        viewModel.toggleControls()
                                    }
                                }
                            }
                        }
                    }
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                            transformOrigin = TransformOrigin.Center
                        },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(
                        count = uiState.totalPages,
                        key = { pageIdx -> "pdf_page_$pageIdx" }
                    ) { pageIdx ->
                        val pageMatches = uiState.searchResults.filter { it.pageOrChapterIndex == pageIdx }
                        val activeResult = if (uiState.currentSearchResultIndex in uiState.searchResults.indices) {
                            uiState.searchResults[uiState.currentSearchResultIndex]
                        } else null
                        val isCurrentMatchPage = activeResult?.pageOrChapterIndex == pageIdx

                        ContinuousPdfPageCard(
                            pageIndex = pageIdx,
                            totalPages = uiState.totalPages,
                            isCurrentPage = pageIdx == uiState.currentPageIndex,
                            theme = theme,
                            viewModel = viewModel,
                            searchQuery = uiState.searchQuery,
                            searchHighlightBoxes = uiState.searchHighlightBoxes[pageIdx] ?: emptyList(),
                            pageSearchResults = pageMatches,
                            pageLinks = uiState.documentLinks[pageIdx] ?: emptyList(),
                            isCurrentSearchMatchPage = isCurrentMatchPage,
                            activeMatchSnippet = if (isCurrentMatchPage) activeResult?.snippet else null,
                            activeMatchIndexInPage = if (isCurrentMatchPage) activeResult?.matchIndexInPage else null,
                            activeResultId = activeResult?.id,
                            onOpenLink = { link -> viewModel.setActiveLinkForDialog(link) },
                            onAnnotateThisPage = {
                                viewModel.goToPage(pageIdx)
                                viewModel.setActiveTool(ActiveDrawingTool.PEN)
                            }
                        )
                    }
                }
            }
        } else {
            // =========================================================================
            // MODE 2: SINGLE PAGE SWIPE & PRECISION DRAWING CANVAS
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (uiState.isControlsVisible || uiState.activeTool != ActiveDrawingTool.NONE) 56.dp else 4.dp,
                        bottom = if (uiState.isControlsVisible && uiState.activeTool == ActiveDrawingTool.NONE) 64.dp else 4.dp
                    )
                    .onSizeChanged { containerSize = it }
                    .pointerInput(uiState.activeTool) {
                        var lastTapTime = 0L
                        var singleTapJob: Job? = null

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var isTwoFingerGesture = false
                            var touchMoved = false
                            val startPos = down.position
                            var totalPanX = 0f
                            var totalPanY = 0f

                            val size = currentContainerSize
                            val center = Offset(size.width / 2f, size.height / 2f)

                            if (uiState.activeTool == ActiveDrawingTool.PEN || uiState.activeTool == ActiveDrawingTool.HIGHLIGHTER) {
                                val localX = if (size.width > 0) {
                                    center.x + (down.position.x - center.x - offset.x) / scale
                                } else {
                                    (down.position.x - offset.x) / scale
                                }
                                val localY = if (size.height > 0) {
                                    center.y + (down.position.y - center.y - offset.y) / scale
                                } else {
                                    (down.position.y - offset.y) / scale
                                }
                                livePoints.add(DrawingPoint(localX, localY))
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val pointers = event.changes.filter { it.pressed }
                                if (pointers.isEmpty()) break

                                if (pointers.size >= 2) {
                                    // 2-finger gesture: Smooth Pinch Zoom & Pan around centroid!
                                    if (!isTwoFingerGesture) {
                                        isTwoFingerGesture = true
                                        singleTapJob?.cancel()
                                        swipeDragOffsetX = 0f
                                        livePoints.clear()
                                    }

                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val oldScale = scale
                                    val newScale = (oldScale * zoom).coerceIn(1.0f, 4.5f)
                                    val effectiveZoom = newScale / oldScale

                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val focusOffset = if (centroid != Offset.Unspecified && size.width > 0) {
                                        centroid - center - offset
                                    } else {
                                        Offset.Zero
                                    }

                                    val rawNewOffset = offset + pan - focusOffset * (effectiveZoom - 1f)
                                    scale = newScale
                                    offset = clampPanOffset(rawNewOffset, newScale, size)

                                    event.changes.forEach { it.consume() }
                                } else if (pointers.size == 1 && !isTwoFingerGesture) {
                                    val pointer = pointers.first()
                                    val currentPos = pointer.position
                                    val delta = pointer.positionChange()

                                    if (abs(currentPos.x - startPos.x) > 8f || abs(currentPos.y - startPos.y) > 8f) {
                                        touchMoved = true
                                    }

                                    if (uiState.activeTool == ActiveDrawingTool.PEN || uiState.activeTool == ActiveDrawingTool.HIGHLIGHTER) {
                                        val localX = if (size.width > 0) {
                                            center.x + (currentPos.x - center.x - offset.x) / scale
                                        } else {
                                            (currentPos.x - offset.x) / scale
                                        }
                                        val localY = if (size.height > 0) {
                                            center.y + (currentPos.y - center.y - offset.y) / scale
                                        } else {
                                            (currentPos.y - offset.y) / scale
                                        }
                                        livePoints.add(DrawingPoint(localX, localY))
                                        pointer.consume()
                                    } else if (uiState.activeTool == ActiveDrawingTool.ERASER) {
                                        val localX = if (size.width > 0) {
                                            center.x + (currentPos.x - center.x - offset.x) / scale
                                        } else {
                                            (currentPos.x - offset.x) / scale
                                        }
                                        val localY = if (size.height > 0) {
                                            center.y + (currentPos.y - center.y - offset.y) / scale
                                        } else {
                                            (currentPos.y - offset.y) / scale
                                        }
                                        viewModel.eraseStrokeAt(DrawingPoint(localX, localY))
                                        pointer.consume()
                                    } else {
                                        // ActiveTool == NONE (Reading & Navigation Mode)
                                        if (scale > 1.03f) {
                                            // 1-Finger Pan when zoomed in
                                            val newOffset = clampPanOffset(offset + delta, scale, size)
                                            offset = newOffset
                                            pointer.consume()
                                        } else {
                                            // 1-Finger Interactive Page Slide when at 1x zoom!
                                            totalPanX += delta.x
                                            totalPanY += delta.y
                                            if (abs(totalPanX) > 6f) {
                                                val state = currentUiState
                                                val canSlideLeft = state.currentPageIndex < state.totalPages - 1
                                                val canSlideRight = state.currentPageIndex > 0
                                                val resistance = when {
                                                    totalPanX < 0 && !canSlideLeft -> 0.2f
                                                    totalPanX > 0 && !canSlideRight -> 0.2f
                                                    else -> 0.85f
                                                }
                                                swipeDragOffsetX = (totalPanX * resistance).coerceIn(-360f, 360f)
                                                pointer.consume()
                                            }
                                        }
                                    }
                                }
                            }

                            // Pointer Up / Gesture Ended
                            if (!isTwoFingerGesture) {
                                if (uiState.activeTool == ActiveDrawingTool.PEN || uiState.activeTool == ActiveDrawingTool.HIGHLIGHTER) {
                                    if (livePoints.isNotEmpty()) {
                                        val state = currentUiState
                                        val isHighlighter = state.activeTool == ActiveDrawingTool.HIGHLIGHTER
                                        val color = if (isHighlighter) state.highlighterColor else state.penColor
                                        val strokeWidth = if (isHighlighter) state.highlighterStrokeWidth else state.penStrokeWidth
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
                                } else if (uiState.activeTool == ActiveDrawingTool.NONE) {
                                    val state = currentUiState
                                    if (!touchMoved) {
                                        // Single Tap vs Double Tap Detection
                                        val now = System.currentTimeMillis()
                                        if (now - lastTapTime < 280L) {
                                            // Double-Tap: Smoothly cycle Zoom 1x -> 2x -> 3x -> 1x centered on tap!
                                            singleTapJob?.cancel()
                                            lastTapTime = 0L
                                            val nextScale = when {
                                                scale < 1.6f -> 2.0f
                                                scale < 2.6f -> 3.0f
                                                else -> 1.0f
                                            }
                                            val rawTargetOffset = if (nextScale <= 1.02f) {
                                                Offset.Zero
                                            } else {
                                                (center - startPos) * (nextScale - 1f)
                                            }
                                            scale = nextScale
                                            offset = clampPanOffset(rawTargetOffset, nextScale, size)
                                        } else {
                                            lastTapTime = now
                                            singleTapJob?.cancel()
                                            singleTapJob = coroutineScope.launch {
                                                delay(240L)
                                                viewModel.toggleControls()
                                            }
                                        }
                                    } else if (scale <= 1.03f) {
                                        // Evaluate 1-finger page swipe (horizontal or vertical)
                                        val swipeThreshold = 60f
                                        if (totalPanX < -swipeThreshold && state.currentPageIndex < state.totalPages - 1) {
                                            swipeDragOffsetX = 0f
                                            viewModel.nextPage()
                                        } else if (totalPanX > swipeThreshold && state.currentPageIndex > 0) {
                                            swipeDragOffsetX = 0f
                                            viewModel.previousPage()
                                        } else if (abs(totalPanY) > abs(totalPanX) * 1.4f && abs(totalPanY) > 85f) {
                                            swipeDragOffsetX = 0f
                                            if (totalPanY < 0 && state.currentPageIndex < state.totalPages - 1) {
                                                viewModel.nextPage()
                                            } else if (totalPanY > 0 && state.currentPageIndex > 0) {
                                                viewModel.previousPage()
                                            }
                                        } else {
                                            swipeDragOffsetX = 0f
                                        }
                                    }
                                }
                            }
                            livePoints.clear()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Container with graphicsLayer transformation for 100% synchronized zoom, pan & interactive page slide
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x + animatedSwipeOffsetX
                            translationY = offset.y
                            transformOrigin = TransformOrigin.Center
                        }
                ) {
                    // 1. PDF Page Rendered Bitmap + 1:1 Fitted Search Highlight Overlay
                    val bmp = uiState.activePagePdfBitmap
                    if (bmp != null && !bmp.isRecycled) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val fittedSize = computeFittedPageSize(
                                containerWidth = constraints.maxWidth.toFloat(),
                                containerHeight = constraints.maxHeight.toFloat(),
                                bitmapWidth = bmp.width.toFloat(),
                                bitmapHeight = bmp.height.toFloat()
                            )
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val fittedWidthDp = with(density) { fittedSize.width.toDp() }
                            val fittedHeightDp = with(density) { fittedSize.height.toDp() }

                            Box(
                                modifier = Modifier.size(width = fittedWidthDp, height = fittedHeightDp)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Halaman Dokumen PDF",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )

                                val activePageHighlights = uiState.searchHighlightBoxes[uiState.currentPageIndex] ?: emptyList()
                                val activePageSearchResults = uiState.searchResults.filter { it.pageOrChapterIndex == uiState.currentPageIndex }
                                if (activePageHighlights.isNotEmpty() || activePageSearchResults.isNotEmpty()) {
                                    val activeResult = uiState.searchResults.getOrNull(uiState.currentSearchResultIndex)
                                    val activeMatchIdx = if (activeResult?.pageOrChapterIndex == uiState.currentPageIndex) {
                                        activeResult.matchIndexInPage
                                    } else {
                                        null
                                    }
                                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawPdfSearchHighlights(
                                            boxes = activePageHighlights,
                                            fallbackResults = activePageSearchResults,
                                            activeMatchIndexInPage = activeMatchIdx,
                                            activeResultId = activeResult?.id
                                        )
                                    }
                                }
                            }
                        }
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

                // Visual Page Turn Indicator Pill when dragging horizontally to slide pages
                if (abs(swipeDragOffsetX) > 25f) {
                    val isGoingNext = swipeDragOffsetX < 0
                    val targetPageNum = if (isGoingNext) {
                        (uiState.currentPageIndex + 2).coerceAtMost(uiState.totalPages)
                    } else {
                        uiState.currentPageIndex.coerceAtLeast(1)
                    }
                    val canTurn = (isGoingNext && uiState.currentPageIndex < uiState.totalPages - 1) ||
                        (!isGoingNext && uiState.currentPageIndex > 0)

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(if (isGoingNext) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (!isGoingNext) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (canTurn) "Lepas ke Hal. $targetPageNum" else "Batas Halaman",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (isGoingNext) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Current Page Web Links floating pill in Single Page Mode
                val singlePageLinks = uiState.documentLinks[uiState.currentPageIndex] ?: emptyList()
                if (singlePageLinks.isNotEmpty() && uiState.activeTool == ActiveDrawingTool.NONE) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp, start = 14.dp, end = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tautan:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (link in singlePageLinks) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        modifier = Modifier.clickable { viewModel.setActiveLinkForDialog(link) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = link.displayText.take(28) + (if (link.displayText.length > 28) "…" else ""),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.OpenInNew,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // PDF Page Rendering Spinner Indicator (Only shown on initial cold load)
                if (uiState.isRenderingPdfPage && uiState.activePagePdfBitmap == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(theme.backgroundColor).copy(alpha = 0.6f)),
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
        }

        // Floating Zoom Controls & 4-Directional Arrow Pan Controller (When not in drawing mode)
        val isControllerVisible = (scale > 1.02f || uiState.isControlsVisible) && uiState.activeTool == ActiveDrawingTool.NONE
        val controllerBottomPadding = if (uiState.isControlsVisible) 118.dp else 24.dp

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
                    offset = clampPanOffset(offset, newScale, containerSize)
                },
                onOffsetChange = { newOffset ->
                    offset = clampPanOffset(newOffset, scale, containerSize)
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
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
            visible = uiState.isControlsVisible || uiState.activeTool != ActiveDrawingTool.NONE || uiState.searchResults.isNotEmpty(),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("back_to_library_button")
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
                                    text = "Halaman ${uiState.currentPageIndex + 1} dari ${uiState.totalPages} • ${if (viewMode == ReaderViewMode.CONTINUOUS_VERTICAL) "Gulir ↕" else "Geser ↔"}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 0.2.sp
                                    ),
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Right: Actions Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Quick Exit button if reading a vault document
                            if (book.isVault || uiState.isVaultUnlocked) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .clickable { viewModel.quickExitVault() }
                                        .padding(end = 4.dp)
                                        .testTag("reader_vault_quick_exit_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ExitToApp,
                                            contentDescription = "Keluar Cepat",
                                            tint = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Keluar Cepat",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onError
                                        )
                                    }
                                }
                            }

                            // 1-Tap Toggle between Horizontal Page Swipe & Continuous Vertical Scroll
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .clickable { viewModel.toggleViewMode() }
                                    .padding(horizontal = 2.dp)
                                    .testTag("toggle_view_mode_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        if (viewMode == ReaderViewMode.SINGLE_PAGE_SWIPE) Icons.Default.SwapVert else Icons.Default.SwapHoriz,
                                        contentDescription = "Ubah Mode Slide/Gulir",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (viewMode == ReaderViewMode.SINGLE_PAGE_SWIPE) "Gulir ↕" else "Geser ↔",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Search inside document
                            IconButton(
                                onClick = { viewModel.setSearchSheetOpen(true) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("search_in_doc_button")
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Cari Kata",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Document Links (Buka di browser luar HP)
                            val totalLinksCount = uiState.documentLinks.values.sumOf { it.size }
                            IconButton(
                                onClick = { viewModel.setLinksSheetOpen(true) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("document_links_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (totalLinksCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Text("$totalLinksCount", fontSize = 9.sp)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = "Tautan Dokumen",
                                        tint = if (totalLinksCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Table of Contents
                            IconButton(
                                onClick = { viewModel.setTocSheetOpen(true) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("toc_button")
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
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("bookmarks_button")
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
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("add_bookmark_quick_button")
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

                    // In-Reader Search Navigation Bar HUD (Multi-page jump & result browsing)
                    AnimatedVisibility(
                        visible = uiState.searchResults.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp, top = 2.dp)
                                .testTag("in_reader_search_hud"),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left: Search keyword & matches counter
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.setSearchSheetOpen(true) }
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "\"${uiState.searchQuery}\"",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val currentMatchNumber = uiState.currentSearchResultIndex + 1
                                        val currentMatchPage = if (uiState.currentSearchResultIndex in uiState.searchResults.indices) {
                                            uiState.searchResults[uiState.currentSearchResultIndex].pageOrChapterIndex + 1
                                        } else {
                                            uiState.currentPageIndex + 1
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "$currentMatchNumber/${uiState.searchResults.size} • Hal. $currentMatchPage",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // Right: Prev, Next, List, Close buttons
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.previousSearchResult() },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .testTag("search_prev_button")
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Hasil Sebelumnya",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.nextSearchResult() },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .testTag("search_next_button")
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Hasil Berikutnya",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.setSearchSheetOpen(true) },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .testTag("search_list_button")
                                        ) {
                                            Icon(
                                                Icons.Default.FormatListBulleted,
                                                contentDescription = "Daftar Hasil",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.clearSearch() },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .testTag("search_close_button")
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Tutup Pencarian",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                if (uiState.currentSearchResultIndex in uiState.searchResults.indices) {
                                    val activeRes = uiState.searchResults[uiState.currentSearchResultIndex]
                                    val trimmedQ = uiState.searchQuery.trim()
                                    val qTarget = if (trimmedQ.isNotEmpty()) trimmedQ else activeRes.matchWord
                                    val snippetAnnotated = buildAnnotatedString {
                                        val text = activeRes.snippet
                                        var lastIdx = 0
                                        val pattern = try {
                                            Regex(Regex.escape(qTarget), RegexOption.IGNORE_CASE)
                                        } catch (e: Exception) {
                                            null
                                        }
                                        val matches = pattern?.findAll(text)?.toList() ?: emptyList()
                                        if (matches.isNotEmpty()) {
                                            matches.forEach { match ->
                                                if (match.range.first > lastIdx) {
                                                    append(text.substring(lastIdx, match.range.first))
                                                }
                                                withStyle(
                                                    style = SpanStyle(
                                                        background = Color(0xFFFFEB3B),
                                                        color = Color(0xFF1B1B1F),
                                                        fontWeight = FontWeight.Black
                                                    )
                                                ) {
                                                    append(match.value)
                                                }
                                                lastIdx = match.range.last + 1
                                            }
                                            if (lastIdx < text.length) append(text.substring(lastIdx))
                                        } else {
                                            append(text)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp, bottom = 2.dp)
                                            .clickable { viewModel.setSearchSheetOpen(true) }
                                    ) {
                                        Text(
                                            text = snippetAnnotated,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    if (scale > 1.03f) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${(scale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
                        offset = clampPanOffset(offset, newScale, containerSize)
                    },
                    onOffsetChange = { newOffset ->
                        offset = clampPanOffset(newOffset, scale, containerSize)
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
                var sliderPosition by remember(uiState.currentPageIndex) {
                    mutableFloatStateOf(uiState.currentPageIndex.toFloat())
                }
                var isDraggingPageSlider by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Page slider & count row (smooth scrubbing without stutter)
                        val displayedPage = if (isDraggingPageSlider) {
                            sliderPosition.roundToInt().coerceIn(0, (uiState.totalPages - 1).coerceAtLeast(0))
                        } else {
                            uiState.currentPageIndex
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Halaman ${displayedPage + 1} / ${uiState.totalPages}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Slider(
                                value = if (isDraggingPageSlider) sliderPosition else uiState.currentPageIndex.toFloat(),
                                onValueChange = { pageFloat ->
                                    isDraggingPageSlider = true
                                    sliderPosition = pageFloat
                                    val targetIdx = pageFloat.roundToInt().coerceIn(0, (uiState.totalPages - 1).coerceAtLeast(0))
                                    if (targetIdx != uiState.currentPageIndex) {
                                        viewModel.goToPage(targetIdx)
                                    }
                                },
                                onValueChangeFinished = {
                                    isDraggingPageSlider = false
                                    val targetIdx = sliderPosition.roundToInt().coerceIn(0, (uiState.totalPages - 1).coerceAtLeast(0))
                                    viewModel.goToPage(targetIdx)
                                },
                                valueRange = 0f..(uiState.totalPages - 1).coerceAtLeast(1).toFloat(),
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
                                text = "${((displayedPage + 1).toFloat() / uiState.totalPages.coerceAtLeast(1) * 100).toInt()}%",
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
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("previous_page_button")
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

                            // 4. PENGATURAN TAMPILAN & MODE SLIDE
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
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("next_page_button")
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

        if (uiState.isLinksSheetOpen) {
            DocumentLinksSheet(
                currentPageIndex = uiState.currentPageIndex,
                allLinks = uiState.documentLinks,
                onSelectPage = { pageIdx -> viewModel.goToPage(pageIdx) },
                onDismiss = { viewModel.setLinksSheetOpen(false) }
            )
        }

        uiState.activeLinkForDialog?.let { link ->
            OpenLinkDialog(
                link = link,
                onDismiss = { viewModel.setActiveLinkForDialog(null) }
            )
        }
    }
}

/**
 * Renders an individual PDF page card inside the Continuous Vertical Scroll list
 * with automatic LRU cache lookup and background rendering.
 */
@Composable
private fun ContinuousPdfPageCard(
    pageIndex: Int,
    totalPages: Int,
    isCurrentPage: Boolean,
    theme: ReadingTheme,
    viewModel: ReaderViewModel,
    searchQuery: String = "",
    searchHighlightBoxes: List<com.example.data.parser.SearchHighlightBox> = emptyList(),
    pageSearchResults: List<SearchResult> = emptyList(),
    pageLinks: List<PdfLinkItem> = emptyList(),
    isCurrentSearchMatchPage: Boolean = false,
    activeMatchSnippet: String? = null,
    activeMatchIndexInPage: Int? = null,
    activeResultId: String? = null,
    onOpenLink: (PdfLinkItem) -> Unit = {},
    onAnnotateThisPage: () -> Unit
) {
    var pageBitmap by remember(pageIndex, theme) {
        mutableStateOf(viewModel.getCachedPageForIndex(pageIndex))
    }

    LaunchedEffect(pageIndex, theme) {
        if (pageBitmap == null || pageBitmap?.isRecycled == true) {
            pageBitmap = viewModel.renderPageForIndex(pageIndex)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(theme.surfaceColor),
        border = BorderStroke(
            width = if (isCurrentSearchMatchPage) 2.5.dp else if (isCurrentPage) 1.5.dp else 1.dp,
            color = if (isCurrentSearchMatchPage) Color(0xFFFFB300) else if (isCurrentPage) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = if (isCurrentSearchMatchPage) 6.dp else 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("continuous_page_card_$pageIndex")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Page Header Bar with Page Number & Quick Annotate Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Halaman ${pageIndex + 1} dari $totalPages",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isCurrentSearchMatchPage) Color(0xFFE65100) else if (isCurrentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { onAnnotateThisPage() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Coret Halaman Ini",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Coret",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Search Matches Banner on this page
            if (pageSearchResults.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF9C4),
                    border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("continuous_page_highlight_banner_$pageIndex")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${pageSearchResults.size} kata \"${searchQuery.trim()}\" disorot pada halaman ini",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5D4037)
                            )
                        )
                    }
                }
            }

            // Active Search Match Snippet Callout
            if (isCurrentSearchMatchPage && !activeMatchSnippet.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF176).copy(alpha = 0.95f),
                    border = BorderStroke(1.5.dp, Color(0xFFFFA000)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⭐ Sorotan: ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFBF360C)
                            )
                        )
                        Text(
                            text = activeMatchSnippet,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF212121)
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Web Links on this page (buka di browser HP)
            if (pageLinks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .horizontalScroll(rememberScrollState())
                        .testTag("continuous_page_links_banner_$pageIndex"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (link in pageLinks) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clickable { onOpenLink(link) }
                                .testTag("in_page_link_chip_${link.url}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = link.displayText.take(30) + (if (link.displayText.length > 30) "…" else ""),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Buka di Browser",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Rendered PDF Page Content with In-Page Search Highlights
            val bmp = pageBitmap
            if (bmp != null && !bmp.isRecycled) {
                val aspectRatio = (bmp.width.toFloat() / bmp.height.toFloat()).coerceAtLeast(0.05f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Halaman ${pageIndex + 1}",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Draw highlight rectangles directly over the page matching 1:1 with PDF image bounds
                    if (searchHighlightBoxes.isNotEmpty() || pageSearchResults.isNotEmpty()) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawPdfSearchHighlights(
                                boxes = searchHighlightBoxes,
                                fallbackResults = pageSearchResults,
                                activeMatchIndexInPage = activeMatchIndexInPage,
                                activeResultId = activeResultId
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.707f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp
                    )
                }
            }
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
            border = if (isActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
