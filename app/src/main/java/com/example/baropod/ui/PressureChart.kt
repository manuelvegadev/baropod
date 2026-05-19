package com.example.baropod.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baropod.model.SensorZone
import com.example.baropod.ui.theme.appColors
import com.example.baropod.util.ForceCalibration
import com.example.baropod.util.PressurePalette
import com.example.baropod.util.colorFromKg

private val Y_TICKS_KG = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f)

/**
 * Gráfica de fuerza (Kg) vs. tiempo. Una traza por sensor.
 *
 * Optimizaciones para no sufrir a 20 Hz:
 *  - `history` es `List<FloatArray>`: muestras primitivas, sin boxing.
 *  - Las etiquetas estáticas (eje Y, marca de tiempo) se pre-miden una sola
 *    vez con `remember`; sólo las etiquetas de valor (que cambian con la
 *    fuerza) se miden por frame.
 *  - El `PathEffect` punteado se cachea con `remember`.
 *
 * @param yMaxKg Tope visual del eje Y. Por defecto el rango nominal del sensor
 *               (5 Kg). Cualquier valor por encima se ve "saturado" y la
 *               línea se dibuja al borde superior.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PressureChart(
    history: List<FloatArray>,
    zones: List<SensorZone>,
    modifier: Modifier = Modifier,
    yMaxKg: Float = ForceCalibration.FULL_SCALE_KG
) {
    val colors = MaterialTheme.appColors
    val gridColor = colors.divider
    val axisLabelColor = colors.secondaryText
    val refLineColor = PressurePalette.RED_HIGH.copy(alpha = 0.45f)
    val sensorColors = colors.sensorTraces
    val textMeasurer = rememberTextMeasurer()

    // Estilos / etiquetas estáticas — caché entre frames.
    val axisStyle = remember(axisLabelColor) {
        TextStyle(color = axisLabelColor, fontSize = 10.sp)
    }
    val timeStyle = remember(axisLabelColor) {
        TextStyle(color = axisLabelColor, fontSize = 9.sp)
    }
    val valueLabelBaseStyle = remember {
        TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
    val axisLabelLayouts: Map<Int, TextLayoutResult> = remember(textMeasurer, axisStyle) {
        Y_TICKS_KG.associate { kg ->
            val key = kg.toInt()
            key to textMeasurer.measure(
                text = AnnotatedString("$key Kg"),
                style = axisStyle
            )
        }
    }
    val timeLabelLayout: TextLayoutResult = remember(textMeasurer, timeStyle) {
        textMeasurer.measure(
            text = AnnotatedString("últimos 10 s"),
            style = timeStyle
        )
    }
    val refDashEffect: PathEffect = remember {
        PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    val gridLineColor = remember(gridColor) { gridColor.copy(alpha = 0.6f) }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val padLeft = 42.dp.toPx()
                val padRight = 72.dp.toPx()
                val padTop = 12.dp.toPx()
                val padBottom = 16.dp.toPx()

                val plotL = padLeft
                val plotT = padTop
                val plotR = w - padRight
                val plotB = h - padBottom
                val plotW = (plotR - plotL).coerceAtLeast(1f)
                val plotH = (plotB - plotT).coerceAtLeast(1f)

                val refLineWidth = 1.2.dp.toPx()
                val gridLineWidth = 0.8.dp.toPx()
                val traceStrokeWidth = 2.dp.toPx()

                // 1) Cuadrícula horizontal + etiquetas Y (todas pre-medidas).
                //    La línea de referencia es el fondo de escala del sensor (5 Kg).
                for (kg in Y_TICKS_KG) {
                    val y = plotB - (kg / yMaxKg) * plotH
                    val isRef = kg == ForceCalibration.FULL_SCALE_KG
                    drawLine(
                        color = if (isRef) refLineColor else gridLineColor,
                        start = Offset(plotL, y),
                        end = Offset(plotR, y),
                        strokeWidth = if (isRef) refLineWidth else gridLineWidth,
                        pathEffect = if (isRef) refDashEffect else null
                    )
                    val layout = axisLabelLayouts[kg.toInt()] ?: continue
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x = plotL - layout.size.width - 4.dp.toPx(),
                            y = y - layout.size.height / 2f
                        )
                    )
                }

                // Color por posición de la zona (no por inputIndex): aunque el
                // usuario remapee, "Talón" sigue siendo azul.
                zones.forEachIndexed { zIdx, zone ->
                    val samples = history.getOrNull(zone.inputIndex) ?: return@forEachIndexed
                    if (samples.isEmpty()) return@forEachIndexed
                    val traceColor = sensorColors.getOrElse(zIdx) { Color.Gray }
                    val n = samples.size

                    if (n == 1) {
                        val y = plotB - (samples[0].coerceIn(0f, yMaxKg) / yMaxKg) * plotH
                        drawCircle(
                            color = traceColor,
                            radius = 2.dp.toPx(),
                            center = Offset(plotR, y)
                        )
                    } else {
                        val path = Path()
                        var i = 0
                        val invDenom = 1f / (n - 1)
                        while (i < n) {
                            val x = plotL + i * invDenom * plotW
                            val kg = samples[i]
                            val clamped = if (kg < 0f) 0f else if (kg > yMaxKg) yMaxKg else kg
                            val y = plotB - (clamped / yMaxKg) * plotH
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            i++
                        }
                        drawPath(
                            path = path,
                            color = traceColor,
                            style = Stroke(width = traceStrokeWidth)
                        )
                    }

                    // Etiqueta del valor actual: nombre corto + Kg con color de intensidad.
                    val lastKg = samples[n - 1]
                    val lastY = plotB - (lastKg.coerceIn(0f, yMaxKg) / yMaxKg) * plotH
                    val kgText = String.format("%.1f Kg", lastKg)
                    val intensityColor = colorFromKg(lastKg)
                    val annotated = buildAnnotatedString {
                        withStyle(SpanStyle(color = traceColor)) { append(zone.shortLabel) }
                        append("  ")
                        withStyle(SpanStyle(color = intensityColor)) { append(kgText) }
                    }
                    val labelLayout = textMeasurer.measure(
                        text = annotated,
                        style = valueLabelBaseStyle
                    )
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(
                            x = plotR + 4.dp.toPx(),
                            y = lastY - labelLayout.size.height / 2f
                        )
                    )
                }

                // 3) Marca de tiempo pre-medida.
                drawText(
                    textLayoutResult = timeLabelLayout,
                    topLeft = Offset(plotL + 2.dp.toPx(), plotT)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Leyenda compacta: con 8 sensores no caben en una sola fila en
        // pantallas pequeñas; `FlowRow` envuelve a la siguiente línea sin
        // recortar etiquetas.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            zones.forEachIndexed { i, zone ->
                val traceColor = sensorColors.getOrElse(i) { Color.Gray }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(traceColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${zone.shortLabel} · ${zone.name}",
                        fontSize = 10.sp,
                        color = colors.secondaryText
                    )
                }
            }
        }
    }
}
