package fr.kalori.app.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Graphiques du tableau de bord, dessinés à la main pour garder la maîtrise des règles de lisibilité :
 * un seul axe Y, traits de 2 px, marqueurs de 8 px avec anneau de fond, grille discrète, objectif en
 * pointillés étiqueté directement, étiquette directe sur le dernier point seulement, sélection au
 * toucher (repère vertical + infobulle). Textes en couleurs de texte, jamais en couleur de série.
 */

/** Séries de macronutriments : 3 premières teintes de la palette catégorielle de référence (validées toutes paires). */
object ChartPalette {
    @Composable
    fun macros(): List<Color> = if (isSystemInDarkTheme()) {
        listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70))
    } else {
        listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A))
    }
}

private val ChartHeight = 200.dp
private val LeftAxisWidth = 44.dp
private val RightPadding = 8.dp
private val BottomAxisHeight = 22.dp
private val TopPadding = 28.dp // place réservée à l'infobulle

private class ChartColors(
    val text: Color,
    val grid: Color,
    val target: Color,
    val surface: Color,
    val tooltip: Color,
    val onTooltip: Color,
)

@Composable
private fun chartColors() = ChartColors(
    text = MaterialTheme.colorScheme.onSurfaceVariant,
    grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    target = MaterialTheme.colorScheme.outline,
    surface = MaterialTheme.colorScheme.surface,
    tooltip = MaterialTheme.colorScheme.inverseSurface,
    onTooltip = MaterialTheme.colorScheme.inverseOnSurface,
)

/** Courbe datée (ex. poids), avec ligne d'objectif facultative. */
@Composable
fun LineChart(
    points: List<Pair<LocalDate, Double>>,
    xRange: ClosedRange<LocalDate>,
    color: Color,
    formatValue: (Double) -> String,
    formatDate: (LocalDate) -> String,
    description: String,
    modifier: Modifier = Modifier,
    target: Double? = null,
    targetLabel: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val colors = chartColors()
    val labelStyle = MaterialTheme.typography.labelSmall
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val scale = remember(points, target) { niceScale(points.map { it.second } + listOfNotNull(target)) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = description }
            .selectable(points, { width -> points.map { dateX(it.first, xRange, width) } }) { selected = it },
    ) {
        val plot = plotArea()
        drawGrid(scale, plot, measurer, labelStyle, colors, formatValue)
        drawDateLabels(listOf(xRange.start, midDate(xRange), xRange.endInclusive).distinct(), { dateX(it, xRange, size.width) }, plot, measurer, labelStyle, colors, formatDate)
        if (target != null) drawTarget(target, scale, plot, measurer, labelStyle, colors, targetLabel)
        if (points.isEmpty()) return@Canvas

        val offsets = points.map { (d, v) -> Offset(dateX(d, xRange, size.width), yOf(v, scale, plot)) }
        if (offsets.size > 1) {
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        offsets.forEachIndexed { i, o ->
            val r = if (i == selected) 6.dp.toPx() else 4.dp.toPx()
            drawCircle(colors.surface, radius = r + 2.dp.toPx(), center = o)
            drawCircle(color, radius = r, center = o)
        }
        if (selected == null) {
            val last = offsets.last()
            drawLabelAbove(measurer, formatValue(points.last().second), labelStyle.copy(color = colors.text), last)
        }
        selected?.let { i ->
            val o = offsets.getOrNull(i) ?: return@let
            drawLine(colors.text.copy(alpha = 0.5f), Offset(o.x, plot.top), Offset(o.x, plot.bottom), strokeWidth = 1.dp.toPx())
            drawTooltip(measurer, "${formatDate(points[i].first)} : ${formatValue(points[i].second)}", o.x, colors, labelStyle)
        }
    }
}

/** Une barre (simple ou empilée) à une date donnée. */
data class BarValue(val date: LocalDate, val segments: List<Double>)

/**
 * Histogramme. [slots] : toutes les positions de l'axe X (jours ou semaines), y compris sans donnée.
 * Segments empilés dans l'ordre de [colors], séparés par 2 px ; coins arrondis en haut de la pile.
 */
@Composable
fun BarChart(
    bars: List<BarValue>,
    slots: List<LocalDate>,
    colors: List<Color>,
    formatValue: (Double) -> String,
    formatDate: (LocalDate) -> String,
    description: String,
    modifier: Modifier = Modifier,
    target: Double? = null,
    targetLabel: String? = null,
    tooltipText: (BarValue) -> String = { "${formatDate(it.date)} : ${formatValue(it.segments.sum())}" },
) {
    val measurer = rememberTextMeasurer()
    val chartColors = chartColors()
    val labelStyle = MaterialTheme.typography.labelSmall
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val scale = remember(bars, target) { niceScale(bars.map { it.segments.sum() } + listOfNotNull(target) + 0.0) }
    val slotIndex = remember(slots) { slots.withIndex().associate { it.value to it.index } }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = description }
            .selectable(bars, { width -> bars.map { slotX(slotIndex[it.date] ?: 0, slots.size, width) } }) { selected = it },
    ) {
        val plot = plotArea()
        drawGrid(scale, plot, measurer, labelStyle, chartColors, formatValue)
        if (slots.isNotEmpty()) {
            val labelled = listOf(slots.first(), slots[slots.size / 2], slots.last()).distinct()
            drawDateLabels(labelled, { slotX(slotIndex.getValue(it), slots.size, size.width) }, plot, measurer, labelStyle, chartColors, formatDate)
        }

        val slotWidth = plot.width / max(1, slots.size)
        val barWidth = min(slotWidth * 0.6f, 24.dp.toPx())
        val gap = 2.dp.toPx()
        bars.forEachIndexed { i, bar ->
            val index = slotIndex[bar.date] ?: return@forEachIndexed
            val cx = slotX(index, slots.size, size.width)
            val topSegment = bar.segments.indexOfLast { it > 0 }
            var cumulative = 0.0
            bar.segments.forEachIndexed { s, value ->
                if (value <= 0) return@forEachIndexed
                val yBottom = yOf(cumulative, scale, plot) - if (cumulative > 0) gap else 0f
                val yTop = yOf(cumulative + value, scale, plot)
                cumulative += value
                val color = colors[s % colors.size].let { if (selected != null && selected != i) it.copy(alpha = 0.45f) else it }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - barWidth / 2, yTop),
                    size = Size(barWidth, (yBottom - yTop).coerceAtLeast(1f)),
                    cornerRadius = if (s == topSegment) CornerRadius(4.dp.toPx()) else CornerRadius.Zero,
                )
            }
        }
        if (target != null) drawTarget(target, scale, plot, measurer, labelStyle, chartColors, targetLabel)
        selected?.let { i ->
            val bar = bars.getOrNull(i) ?: return@let
            drawTooltip(measurer, tooltipText(bar), slotX(slotIndex[bar.date] ?: 0, slots.size, size.width), chartColors, labelStyle)
        }
    }
}

// ── Géométrie ──

private data class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

private fun DrawScope.plotArea() = Plot(
    left = LeftAxisWidth.toPx(),
    top = TopPadding.toPx(),
    right = size.width - RightPadding.toPx(),
    bottom = size.height - BottomAxisHeight.toPx(),
)

private data class Scale(val min: Double, val max: Double, val step: Double) {
    val ticks: List<Double> get() = generateSequence(min) { it + step }.takeWhile { it <= max + step / 1000 }.toList()
}

/** Échelle « ronde » (pas de 1, 2 ou 5 × 10^n), environ 4 graduations. */
private fun niceScale(values: List<Double>): Scale {
    if (values.isEmpty()) return Scale(0.0, 1.0, 0.5)
    var lo = values.min()
    var hi = values.max()
    if (hi - lo < 1e-9) {
        lo -= 1.0
        hi += 1.0
    }
    val rough = (hi - lo) / 4
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
    return Scale(floor(lo / step) * step, ceil(hi / step) * step, step)
}

private fun yOf(value: Double, scale: Scale, plot: Plot): Float =
    (plot.bottom - (value - scale.min) / (scale.max - scale.min) * plot.height).toFloat()

private fun midDate(range: ClosedRange<LocalDate>): LocalDate =
    range.start.plusDays(ChronoUnit.DAYS.between(range.start, range.endInclusive) / 2)

/** Abscisse d'une date sur une échelle de temps (largeur totale du composant en px). */
private fun Density.dateX(date: LocalDate, range: ClosedRange<LocalDate>, width: Float): Float {
    val left = LeftAxisWidth.toPx() + 6.dp.toPx()
    val usable = width - left - RightPadding.toPx() - 6.dp.toPx()
    val days = ChronoUnit.DAYS.between(range.start, range.endInclusive).coerceAtLeast(1)
    return left + usable * ChronoUnit.DAYS.between(range.start, date) / days.toFloat()
}

/** Abscisse du centre d'un créneau de barre. */
private fun Density.slotX(index: Int, count: Int, width: Float): Float {
    val left = LeftAxisWidth.toPx()
    val slot = (width - left - RightPadding.toPx()) / max(1, count)
    return left + slot * (index + 0.5f)
}

// ── Sélection au toucher ou au glissé : élément le plus proche ──

private fun Modifier.selectable(
    key: Any,
    positions: Density.(width: Float) -> List<Float>,
    onSelect: (Int?) -> Unit,
): Modifier {
    fun Density.nearest(x: Float, width: Float): Int? =
        positions(width).withIndex().minByOrNull { abs(it.value - x) }?.index
    return pointerInput(key) {
        detectTapGestures { onSelect(nearest(it.x, size.width.toFloat())) }
    }.pointerInput(key) {
        detectHorizontalDragGestures { change, _ -> onSelect(nearest(change.position.x, size.width.toFloat())) }
    }
}

// ── Éléments communs ──

private fun DrawScope.drawGrid(
    scale: Scale,
    plot: Plot,
    measurer: TextMeasurer,
    style: TextStyle,
    colors: ChartColors,
    format: (Double) -> String,
) {
    scale.ticks.forEach { tick ->
        val y = yOf(tick, scale, plot)
        drawLine(colors.grid, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
        val layout = measurer.measure(format(tick), style.copy(color = colors.text))
        drawText(layout, topLeft = Offset((plot.left - layout.size.width - 6.dp.toPx()).coerceAtLeast(0f), y - layout.size.height / 2f))
    }
}

/** Au plus trois dates sur l'axe X (début, milieu, fin), sans chevauchement des bords. */
private fun DrawScope.drawDateLabels(
    dates: List<LocalDate>,
    xFor: (LocalDate) -> Float,
    plot: Plot,
    measurer: TextMeasurer,
    style: TextStyle,
    colors: ChartColors,
    format: (LocalDate) -> String,
) {
    dates.forEachIndexed { i, date ->
        val layout = measurer.measure(format(date), style.copy(color = colors.text))
        val x = xFor(date)
        val left = when (i) {
            0 -> x - layout.size.width / 4f
            dates.lastIndex -> x - layout.size.width * 3 / 4f
            else -> x - layout.size.width / 2f
        }.coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(left, plot.bottom + 4.dp.toPx()))
    }
}

private fun DrawScope.drawTarget(
    target: Double,
    scale: Scale,
    plot: Plot,
    measurer: TextMeasurer,
    style: TextStyle,
    colors: ChartColors,
    label: String?,
) {
    val y = yOf(target, scale, plot)
    drawLine(
        colors.target,
        Offset(plot.left, y),
        Offset(plot.right, y),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
    if (label != null) {
        val layout = measurer.measure(label, style.copy(color = colors.text))
        drawText(layout, topLeft = Offset(plot.left + 4.dp.toPx(), (y - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
    }
}

private fun DrawScope.drawLabelAbove(measurer: TextMeasurer, text: String, style: TextStyle, anchor: Offset) {
    val layout = measurer.measure(text, style)
    val x = (anchor.x - layout.size.width).coerceIn(0f, size.width - layout.size.width)
    drawText(layout, topLeft = Offset(x, (anchor.y - layout.size.height - 8.dp.toPx()).coerceAtLeast(0f)))
}

private fun DrawScope.drawTooltip(measurer: TextMeasurer, text: String, x: Float, colors: ChartColors, style: TextStyle) {
    val layout = measurer.measure(text, style.copy(color = colors.onTooltip))
    val padH = 8.dp.toPx()
    val padV = 4.dp.toPx()
    val w = layout.size.width + 2 * padH
    val h = layout.size.height + 2 * padV
    val left = (x - w / 2).coerceIn(0f, max(0f, size.width - w))
    drawRoundRect(colors.tooltip, topLeft = Offset(left, 0f), size = Size(w, h), cornerRadius = CornerRadius(6.dp.toPx()))
    drawText(layout, topLeft = Offset(left + padH, padV))
}
