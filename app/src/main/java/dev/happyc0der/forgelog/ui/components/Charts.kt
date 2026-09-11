package dev.happyc0der.forgelog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import androidx.compose.ui.res.stringResource

/**
 * Charts are drawn by hand with [Canvas].
 *
 * A bar chart and a sparkline do not justify a charting dependency in an app whose whole premise is
 * being small and offline, and hand-drawing keeps the visuals on the app's own palette instead of a
 * library's defaults.
 */
private val CHART_HEIGHT = 140.dp
private val BAR_CORNER = 4.dp

/** One labelled bar. [value] may be zero; a zero bar still occupies its slot. */
data class BarDatum(
    val label: String,
    val value: Double,
    val highlight: Boolean = false,
)

@Composable
fun BarChart(
    bars: List<BarDatum>,
    modifier: Modifier = Modifier,
    valueLabel: (Double) -> String = { it.toInt().toString() },
) {
    if (bars.isEmpty()) {
        EmptyChartMessage(modifier)
        return
    }
    val maxValue = bars.maxOf { it.value }
    val barColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlightColor = MaterialTheme.colorScheme.secondary

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT),
        ) {
            val slotWidth = size.width / bars.size
            val barWidth = slotWidth * 0.6f
            val corner = BAR_CORNER.toPx()
            bars.forEachIndexed { index, bar ->
                // Every bar keeps its slot. An empty training day is information, so it is drawn as a
                // faint baseline rather than left blank.
                val fraction = if (maxValue <= 0.0) 0f else (bar.value / maxValue).toFloat()
                val barHeight = (size.height * fraction).coerceAtLeast(if (bar.value > 0) 2f else 1f)
                val left = index * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(
                    color = when {
                        bar.value <= 0.0 -> mutedColor
                        bar.highlight -> highlightColor
                        else -> barColor
                    },
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    // One line, whatever the font size. A label wide enough to wrap -- "Mon" at the
                    // largest font -- broke onto a second line under its own bar, which pushed the
                    // row's height out and left the axis reading "Mo n".
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = valueLabel(maxValue),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A point on a line chart. [label] is shown under the first and last points only. */
data class LinePoint(
    val value: Double,
    val label: String,
)

/**
 * Sparkline for a trend.
 *
 * A single point draws as a dot rather than a line, because one session is not a trend and drawing a
 * flat line across the chart would imply continuity that is not there.
 */
@Composable
fun LineChart(
    points: List<LinePoint>,
    modifier: Modifier = Modifier,
    valueLabel: (Double) -> String = { it.toInt().toString() },
    /** What to say with no points, when "nothing logged" would not be true. */
    emptyMessage: String? = null,
) {
    if (points.isEmpty()) {
        EmptyChartMessage(modifier, emptyMessage)
        return
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.secondary
    val minValue = points.minOf { it.value }
    val maxValue = points.maxOf { it.value }
    val span = (maxValue - minValue).takeIf { it > 0.0 }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT),
        ) {
            val inset = 6f
            val usableHeight = size.height - inset * 2
            fun yFor(value: Double): Float {
                // A flat trend sits in the middle rather than pinned to an edge.
                val fraction = span?.let { (value - minValue) / it } ?: 0.5
                return inset + usableHeight - (usableHeight * fraction).toFloat()
            }
            if (points.size == 1) {
                drawCircle(
                    color = dotColor,
                    radius = 6f,
                    center = Offset(size.width / 2f, yFor(points.single().value)),
                )
                return@Canvas
            }
            val stepX = size.width / (points.size - 1)
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = index * stepX
                val y = yFor(point.value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round),
            )
            points.forEachIndexed { index, point ->
                drawCircle(
                    color = dotColor,
                    radius = 4f,
                    center = Offset(index * stepX, yFor(point.value)),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = points.first().label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = points.last().label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = valueLabel(minValue) + " – " + valueLabel(maxValue),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Proportional bars with a label each, for set counts by category. */
@Composable
fun ProportionBars(
    entries: List<BarDatum>,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        EmptyChartMessage(modifier)
        return
    }
    val total = entries.sumOf { it.value }.takeIf { it > 0.0 }
    val barColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.sortedByDescending { it.value }.forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = entry.value.toInt().toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                ) {
                    val fraction = total?.let { (entry.value / it).toFloat() } ?: 0f
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.25f),
                        size = Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    )
                    drawRoundRect(
                        color = barColor,
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChartMessage(modifier: Modifier = Modifier, message: String? = null) {
    Text(
        text = message ?: stringResource(R.string.analytics_no_data),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

