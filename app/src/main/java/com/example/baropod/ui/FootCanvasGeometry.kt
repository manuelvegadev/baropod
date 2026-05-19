package com.example.baropod.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bounding-box del pie centrado dentro del canvas preservando el aspect ratio
 * de la imagen de origen (`foot_outline`). Compartido entre [FootprintHeatmap]
 * y la canvas de calibración: ambas necesitan mapear las coordenadas
 * normalizadas (`nx`, `ny`) a píxeles absolutos y deben coincidir píxel a
 * píxel para que el drag de calibración alinee con los blobs de calor.
 */
internal data class FootBox(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float
) {
    fun toCanvasX(nx: Float): Float = leftPx + nx * widthPx
    fun toCanvasY(ny: Float): Float = topPx + ny * heightPx
}

@Composable
internal fun rememberFootBox(
    image: ImageBitmap,
    canvasWidth: Dp,
    canvasHeight: Dp
): FootBox {
    val density = LocalDensity.current
    val aspect = remember(image) { image.width.toFloat() / image.height.toFloat() }
    return remember(canvasWidth, canvasHeight, aspect, density) {
        val (footWDp, footHDp) = aspectFit(canvasWidth, canvasHeight, aspect)
        with(density) {
            val w = footWDp.toPx()
            val h = footHDp.toPx()
            val canvasWPx = canvasWidth.toPx()
            val canvasHPx = canvasHeight.toPx()
            FootBox(
                leftPx = (canvasWPx - w) / 2f,
                topPx = (canvasHPx - h) / 2f,
                widthPx = w,
                heightPx = h
            )
        }
    }
}

private fun aspectFit(maxW: Dp, maxH: Dp, aspect: Float): Pair<Dp, Dp> {
    val targetWByH = maxH.value * aspect
    return if (targetWByH <= maxW.value) Pair(targetWByH.dp, maxH)
    else Pair(maxW, (maxW.value / aspect).dp)
}
