package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.example.data.model.ActiveDrawingTool
import com.example.data.model.DrawingPoint
import com.example.data.model.DrawingStroke

@Composable
fun DrawingCanvasOverlay(
    activeTool: ActiveDrawingTool,
    penColor: Color,
    penStrokeWidth: Float,
    highlighterColor: Color,
    highlighterStrokeWidth: Float,
    strokes: List<DrawingStroke>,
    onAddStroke: (DrawingStroke) -> Unit,
    onEraseAt: (DrawingPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    var livePoints by remember { mutableStateOf<List<DrawingPoint>>(emptyList()) }

    val isDrawingActive = activeTool != ActiveDrawingTool.NONE

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("drawing_canvas_overlay")
            .then(
                if (isDrawingActive) {
                    Modifier.pointerInput(activeTool, penColor, highlighterColor, penStrokeWidth, highlighterStrokeWidth) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val point = DrawingPoint(offset.x, offset.y)
                                if (activeTool == ActiveDrawingTool.ERASER) {
                                    onEraseAt(point)
                                } else {
                                    livePoints = listOf(point)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val point = DrawingPoint(change.position.x, change.position.y)
                                if (activeTool == ActiveDrawingTool.ERASER) {
                                    onEraseAt(point)
                                } else {
                                    livePoints = livePoints + point
                                }
                            },
                            onDragEnd = {
                                if (livePoints.isNotEmpty()) {
                                    val isHighlighter = activeTool == ActiveDrawingTool.HIGHLIGHTER
                                    val stroke = DrawingStroke(
                                        points = livePoints,
                                        color = if (isHighlighter) highlighterColor else penColor,
                                        strokeWidth = if (isHighlighter) highlighterStrokeWidth else penStrokeWidth,
                                        isHighlighter = isHighlighter,
                                        alpha = if (isHighlighter) 0.35f else 1.0f
                                    )
                                    onAddStroke(stroke)
                                    livePoints = emptyList()
                                }
                            },
                            onDragCancel = {
                                livePoints = emptyList()
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // Draw existing strokes
        for (stroke in strokes) {
            if (stroke.points.size < 2) {
                if (stroke.points.size == 1) {
                    val p = stroke.points[0]
                    drawCircle(
                        color = stroke.color.copy(alpha = stroke.alpha),
                        radius = stroke.strokeWidth / 2,
                        center = Offset(p.x, p.y)
                    )
                }
                continue
            }

            val path = Path().apply {
                moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    val prev = stroke.points[i - 1]
                    val curr = stroke.points[i]
                    val midX = (prev.x + curr.x) / 2f
                    val midY = (prev.y + curr.y) / 2f
                    quadraticTo(prev.x, prev.y, midX, midY)
                }
                lineTo(stroke.points.last().x, stroke.points.last().y)
            }

            drawPath(
                path = path,
                color = stroke.color.copy(alpha = stroke.alpha),
                style = Stroke(
                    width = stroke.strokeWidth,
                    cap = if (stroke.isHighlighter) StrokeCap.Square else StrokeCap.Round,
                    join = if (stroke.isHighlighter) StrokeJoin.Bevel else StrokeJoin.Round
                )
            )
        }

        // Draw live drawing stroke
        if (livePoints.isNotEmpty()) {
            val isHighlighter = activeTool == ActiveDrawingTool.HIGHLIGHTER
            val color = if (isHighlighter) highlighterColor.copy(alpha = 0.35f) else penColor
            val strokeWidth = if (isHighlighter) highlighterStrokeWidth else penStrokeWidth

            if (livePoints.size < 2) {
                val p = livePoints[0]
                drawCircle(
                    color = color,
                    radius = strokeWidth / 2,
                    center = Offset(p.x, p.y)
                )
            } else {
                val path = Path().apply {
                    moveTo(livePoints[0].x, livePoints[0].y)
                    for (i in 1 until livePoints.size) {
                        val prev = livePoints[i - 1]
                        val curr = livePoints[i]
                        val midX = (prev.x + curr.x) / 2f
                        val midY = (prev.y + curr.y) / 2f
                        quadraticTo(prev.x, prev.y, midX, midY)
                    }
                    lineTo(livePoints.last().x, livePoints.last().y)
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidth,
                        cap = if (isHighlighter) StrokeCap.Square else StrokeCap.Round,
                        join = if (isHighlighter) StrokeJoin.Bevel else StrokeJoin.Round
                    )
                )
            }
        }
    }
}
