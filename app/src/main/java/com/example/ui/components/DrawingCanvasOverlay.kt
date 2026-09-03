package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
    livePoints: List<DrawingPoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("drawing_canvas_overlay")
    ) {
        // 1. Draw existing committed strokes
        for (stroke in strokes) {
            if (stroke.points.size < 2) {
                if (stroke.points.size == 1) {
                    val p = stroke.points[0]
                    drawCircle(
                        color = stroke.color.copy(alpha = stroke.alpha),
                        radius = stroke.strokeWidth / 2f,
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

        // 2. Draw live interactive drawing stroke in real-time
        if (livePoints.isNotEmpty()) {
            val isHighlighter = activeTool == ActiveDrawingTool.HIGHLIGHTER
            val color = if (isHighlighter) highlighterColor.copy(alpha = 0.35f) else penColor
            val strokeWidth = if (isHighlighter) highlighterStrokeWidth else penStrokeWidth

            if (livePoints.size < 2) {
                val p = livePoints[0]
                drawCircle(
                    color = color,
                    radius = strokeWidth / 2f,
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

