package com.ironlog.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironlog.app.theme.accent

/** Weekly bars: past weeks in muted grey, the last bar ("now") in the accent. Rounded tops, square baseline. */
@Composable
fun BarChart(values: List<Double>, labels: List<String>, formatMax: (Double) -> String, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val past = colors.onSurfaceVariant.copy(alpha = 0.65f) // ≥3:1 on the card in both themes
  val now = colors.accent
  val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
  Column(modifier) {
    Text(formatMax(values.maxOrNull() ?: 0.0), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
      val n = values.size.coerceAtLeast(1)
      val slot = size.width / n
      val barW = (slot - 2.dp.toPx()).coerceAtMost(slot * 0.6f)
      val r = 4.dp.toPx()
      drawLine(colors.outlineVariant, Offset(0f, 0f), Offset(size.width, 0f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
      values.forEachIndexed { i, v ->
        val h = (v / max).toFloat() * size.height
        if (h > 0) {
          val left = i * slot + (slot - barW) / 2
          val bar = RoundRect(Rect(left, size.height - h, left + barW, size.height), topLeft = CornerRadius(r), topRight = CornerRadius(r))
          drawPath(Path().apply { addRoundRect(bar) }, if (i == values.lastIndex) now else past)
        }
      }
      drawLine(colors.outline, Offset(0f, size.height), Offset(size.width, size.height))
    }
    AxisLabels(labels)
  }
}

/** Line over evenly spaced points, drawn in ink; only the latest point is marked, in the accent. */
@Composable
fun LineChart(values: List<Double>, labels: List<String>, formatValue: (Double) -> String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
  val colors = MaterialTheme.colorScheme
  if (values.isEmpty()) return
  val max = values.max()
  val min = values.min()
  val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
  Column(modifier) {
    Row {
      Text("${formatValue(min)} – ${formatValue(max)}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
      val pad = 8.dp.toPx()
      val w = size.width - 2 * pad
      val h = size.height - 2 * pad
      fun pt(i: Int): Offset {
        val x = if (values.size == 1) size.width / 2 else pad + w * i / (values.size - 1)
        val y = pad + h - ((values[i] - min) / span).toFloat() * h
        return Offset(x, y)
      }
      val path = Path().apply { values.indices.forEach { i -> pt(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
      drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
      val last = pt(values.lastIndex)
      drawCircle(colors.surfaceContainerLow, radius = 6.dp.toPx(), center = last) // card-coloured ring
      drawCircle(colors.accent, radius = 4.dp.toPx(), center = last)
    }
    AxisLabels(labels)
  }
}

@Composable
private fun AxisLabels(labels: List<String>) {
  if (labels.isEmpty()) return
  Row(Modifier.fillMaxWidth()) {
    labels.forEach {
      Text(
        it,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.weight(1f),
      )
    }
  }
}
