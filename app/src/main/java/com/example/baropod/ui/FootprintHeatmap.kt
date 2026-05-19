package com.example.baropod.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.baropod.R
import com.example.baropod.model.SensorZone
import com.example.baropod.ui.theme.appColors
import com.example.baropod.util.ForceCalibration

/**
 * Composable que dibuja la huella plantar usando una técnica de **reveal-mask**:
 *
 * 1. Siempre se dibuja el croquis (`foot_outline`) — estado inicial sin presión.
 * 2. La huella rellena (`foot_print`) se dibuja en una capa offscreen y se enmascara
 *    con `BlendMode.DstIn` usando gradientes radiales centrados en cada sensor.
 * 3. Cuanta más presión, mayor opacidad central del gradiente → más huella visible
 *    alrededor de ese sensor. A 100 % la zona queda totalmente revelada; >100 %
 *    se mantiene revelada con un pulso sutil.
 *
 * @param zones Sensores con coordenadas normalizadas (0..1) sobre el bbox del pie.
 * @param adcValues Valores ADC actuales en el mismo orden que [zones].
 * @param revealRadiusDp Radio (dp) del gradiente radial de cada sensor. Aumentar
 *                       expande la zona que se "descubre" alrededor de cada sensor.
 */
@Composable
fun FootprintHeatmap(
    zones: List<SensorZone>,
    adcValues: List<Int>,
    zeroOffsets: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
    revealRadiusDp: Float = 95f,
    /**
     * Diámetro del marcador del sensor expresado como fracción del alto del pie.
     * 0.08 ≈ 18 mm sobre un pie real de ~22 cm — tamaño aproximado del RFP-602.
     */
    sensorMarkerDiameterRatio: Float = 0.08f,
    /**
     * Si es `true`, espeja horizontalmente la imagen y los marcadores. Las
     * coordenadas de las zonas (`nx`, `ny`) se definen siempre en orientación
     * de pie derecho; activar este flag las espeja para representar el pie
     * izquierdo sin necesidad de imágenes adicionales ni coordenadas duplicadas.
     */
    mirrored: Boolean = false
) {
    val outlineImage = ImageBitmap.imageResource(R.drawable.foot_outline)
    val printImage = ImageBitmap.imageResource(R.drawable.foot_print)

    val outlineColor = MaterialTheme.appColors.footOutline
    val printColor = MaterialTheme.appColors.footPrint
    val sensorMarker = MaterialTheme.appColors.sensorMarker
    val markerInnerColor = MaterialTheme.appColors.background

    val kgValues = zones.map { zone ->
        val idx = zone.inputIndex
        val baseline = zeroOffsets.getOrElse(idx) { ForceCalibration.ADC_AT_0_KG }
        val adc = adcValues.getOrNull(idx) ?: baseline
        ForceCalibration.adcToKg(adc, baseline)
    }

    // Reveal animado: 0 → 1 conforme la fuerza llega al fondo de escala (5 Kg).
    val revealAmounts = kgValues.map { kg ->
        animateFloatAsState(
            targetValue = (kg / ForceCalibration.FULL_SCALE_KG).coerceIn(0f, 1.0f),
            animationSpec = tween(durationMillis = 150),
            label = "reveal_amount"
        ).value
    }

    // Pulso para los sensores saturados (>100 %): la zona ya está totalmente revelada
    // y oscila ligeramente para dar sensación de "calor" extra.
    val transition = rememberInfiniteTransition(label = "saturation")
    val pulseFactor by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "saturation_pulse"
    )

    BoxWithConstraints(modifier = modifier) {
        val box = rememberFootBox(outlineImage, maxWidth, maxHeight)
        val density = LocalDensity.current

        // Sin zonas (p. ej. pie izquierdo aún sin hardware): saltamos la capa
        // offscreen costosa, sólo dibujamos el croquis.
        val needsOffscreen = zones.isNotEmpty()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (needsOffscreen) {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    if (mirrored) scaleX = -1f
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            val left = box.leftPx
            val top = box.topPx
            val footW = box.widthPx
            val footH = box.heightPx

            val dstOffset = IntOffset(left.toInt(), top.toInt())
            val dstSize = IntSize(footW.toInt(), footH.toInt())

            // 1) Croquis — siempre visible (estado base "sin presión").
            drawImage(
                image = outlineImage,
                dstOffset = dstOffset,
                dstSize = dstSize,
                colorFilter = ColorFilter.tint(outlineColor)
            )

            // Sin zonas: nos quedamos sólo con el croquis (rápido, no hay nada
            // que enmascarar ni marcador que dibujar).
            if (!needsOffscreen) return@Canvas

            // 2) Huella revelada por la presión:
            //    - Capa A (saveLayer): contendrá la huella enmascarada
            //    - Capa B (saveLayer con BlendMode.DstIn): acumula la unión de
            //      gradientes radiales como máscara y, al cerrarse, multiplica
            //      la alfa de la huella por la máscara.
            val baseRadiusPx = revealRadiusDp * density.density

            drawIntoCanvas { canvas ->
                val rect = Rect(0f, 0f, canvasW, canvasH)

                // Capa A
                canvas.saveLayer(rect, Paint())

                drawImage(
                    image = printImage,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    colorFilter = ColorFilter.tint(printColor)
                )

                // Capa B: máscara con DstIn al cerrar.
                val maskPaint = Paint().apply { blendMode = BlendMode.DstIn }
                canvas.saveLayer(rect, maskPaint)

                zones.forEachIndexed { i, zone ->
                    val cx = left + zone.nx * footW
                    val cy = top + zone.ny * footH
                    val kg = kgValues[i]
                    val reveal = revealAmounts[i]

                    // Si supera el fondo de escala, mantenemos reveal=1 con pulso.
                    val effective = if (kg > ForceCalibration.FULL_SCALE_KG) {
                        pulseFactor.coerceIn(0f, 1f)
                    } else reveal

                    if (effective > 0.001f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = effective),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = baseRadiusPx
                            ),
                            radius = baseRadiusPx,
                            center = Offset(cx, cy)
                        )
                    }
                }

                canvas.restore() // cierra Capa B → DstIn aplicado a Capa A
                canvas.restore() // cierra Capa A → composita resultado a la pantalla
            }

            // 3) Marcadores de los sensores: "diana" (cuerpo + anillo claro + centro)
            //    dimensionada al tamaño real del RFP-602 respecto al pie.
            val rOuter = footH * sensorMarkerDiameterRatio / 2f
            val rRing = rOuter * 0.55f
            val rCenter = rOuter * 0.18f
            zones.forEach { zone ->
                val cx = left + zone.nx * footW
                val cy = top + zone.ny * footH
                val center = Offset(cx, cy)
                drawCircle(color = sensorMarker, radius = rOuter, center = center)
                drawCircle(color = markerInnerColor, radius = rRing, center = center)
                drawCircle(color = sensorMarker, radius = rCenter, center = center)
            }
        }
    }
}
