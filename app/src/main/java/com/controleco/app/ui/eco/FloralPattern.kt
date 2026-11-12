package com.controleco.app.ui.eco

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform

/** Fine line flowers, drawn as vectors so the motif stays crisp at every screen density. */
internal fun DrawScope.drawFloralBranch(color: Color, origin: Offset = Offset.Zero, width: Float = size.width, height: Float = size.height) {
    withTransform({ translate(origin.x, origin.y); scale(width / 180f, height / 65f, pivot = Offset.Zero) }) {
        val stroke = Stroke(width = 0.85f, cap = StrokeCap.Round)
        val stem = Path().apply {
            moveTo(3f, 53f)
            cubicTo(40f, 22f, 85f, 64f, 132f, 30f)
            cubicTo(148f, 17f, 163f, 6f, 178f, 7f)
        }
        drawPath(stem, color, style = stroke)
        fun petal(x: Float, y: Float, angle: Float, length: Float = 14f, breadth: Float = 7f) {
            rotate(angle, Offset(x, y)) {
                val path = Path().apply {
                    moveTo(x, y)
                    cubicTo(x - breadth, y - length * .42f, x - breadth * .65f, y - length, x, y - length)
                    cubicTo(x + breadth * .65f, y - length, x + breadth, y - length * .42f, x, y)
                    close()
                }
                drawPath(path, color.copy(alpha = color.alpha * .08f))
                drawPath(path, color, style = stroke)
            }
        }
        repeat(5) { petal(143f, 25f, it * 72f) }
        drawCircle(color, 1.5f, Offset(143f, 25f))
        petal(121f, 40f, 205f, 10f, 4f)
        petal(80f, 32f, 40f, 12f, 4.5f)
        petal(103f, 52f, 210f, 9f, 3.5f)
    }
}
