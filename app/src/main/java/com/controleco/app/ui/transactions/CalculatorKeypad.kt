package com.controleco.app.ui.transactions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.controleco.app.ui.eco.ecoPressable
import com.controleco.app.ui.theme.eco

@Composable
internal fun CalculatorKeypad(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("C", "⌫", "÷", "×").forEach { key ->
                CalculatorKey(key, onKey, Modifier.weight(1f))
            }
        }
        listOf(listOf("7", "8", "9", "−"), listOf("4", "5", "6", "+")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { key -> CalculatorKey(key, onKey, Modifier.weight(1f)) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val keyWidth = (maxWidth - 15.dp) / 4
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Column(Modifier.width(keyWidth * 3 + 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("1", "2", "3").forEach { key -> CalculatorKey(key, onKey, Modifier.weight(1f)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CalculatorKey("0", onKey, Modifier.width(keyWidth * 2 + 5.dp))
                        CalculatorKey(".", onKey, Modifier.width(keyWidth))
                    }
                }
                CalculatorKey("=", onKey, Modifier.width(keyWidth), tall = true)
            }
        }
    }
}

@Composable
private fun CalculatorKey(key: String, onKey: (String) -> Unit, modifier: Modifier, tall: Boolean = false) {
    val c = MaterialTheme.eco
    val operator = key in listOf("+", "−", "×", "÷")
    val color = when {
        key == "=" -> c.onAccent
        operator || key in listOf("C", "⌫") -> c.blue
        else -> c.ink
    }
    val description = when (key) {
        "C" -> "清空金额"
        "⌫" -> "退格"
        "+" -> "加"
        "−" -> "减"
        "×" -> "乘"
        "÷" -> "除"
        "=" -> "计算结果"
        else -> key
    }
    Box(
        modifier.height(if (tall) 100.dp else 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(when {
                key == "=" -> c.blue
                operator -> c.blueSoft
                else -> c.surface2
            })
            .semantics { contentDescription = description }
            .ecoPressable(onClick = { onKey(key) }),
        contentAlignment = Alignment.Center,
    ) {
        if (operator || key == "=" || key == "⌫") {
            Canvas(Modifier.size(24.dp)) {
                val unit = size.width / 24f
                fun at(x: Float, y: Float) = Offset(size.width / 2 + x * unit, size.height / 2 + y * unit)
                val stroke = 1.8f * unit
                fun segment(x1: Float, y1: Float, x2: Float, y2: Float) =
                    drawLine(color, at(x1, y1), at(x2, y2), stroke, StrokeCap.Round)
                when (key) {
                    "+" -> { segment(-6f, 0f, 6f, 0f); segment(0f, -6f, 0f, 6f) }
                    "−" -> segment(-6f, 0f, 6f, 0f)
                    "×" -> { segment(-4.5f, -4.5f, 4.5f, 4.5f); segment(-4.5f, 4.5f, 4.5f, -4.5f) }
                    "÷" -> {
                        segment(-6f, 0f, 6f, 0f)
                        drawCircle(color, 1.2f * unit, at(0f, -5.5f))
                        drawCircle(color, 1.2f * unit, at(0f, 5.5f))
                    }
                    "=" -> { segment(-6f, -3f, 6f, -3f); segment(-6f, 3f, 6f, 3f) }
                    "⌫" -> {
                        val path = Path().apply {
                            moveTo(at(-10.5f, 0f).x, at(-10.5f, 0f).y)
                            listOf(-4.5f to -7f, 9f to -7f, 10.5f to -5.5f, 10.5f to 5.5f,
                                9f to 7f, -4.5f to 7f).forEach { (x,y) -> lineTo(at(x,y).x, at(x,y).y) }
                            close()
                        }
                        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        segment(-.8f, -2.8f, 4.8f, 2.8f)
                        segment(-.8f, 2.8f, 4.8f, -2.8f)
                    }
                }
            }
        } else Text(key, style = TxKeypadStyle, color = color)
    }
}
