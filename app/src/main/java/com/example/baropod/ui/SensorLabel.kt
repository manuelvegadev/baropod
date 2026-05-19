package com.example.baropod.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baropod.model.SensorZone
import com.example.baropod.ui.theme.appColors
import com.example.baropod.util.ForceCalibration
import com.example.baropod.util.colorFromKg

/**
 * Etiqueta compacta de un sensor para mostrar en un panel lateral con los 8
 * sensores apilados. Diseñada para caber bien en pantallas de 5"–6.5" en
 * portrait: nombre + valor en una sola fila (17 sp), ADC pequeño abajo.
 */
@Composable
fun SensorLabel(
    zone: SensorZone,
    adc: Int,
    baseline: Int = ForceCalibration.ADC_AT_0_KG,
    modifier: Modifier = Modifier
) {
    val kg = ForceCalibration.adcToKg(adc, baseline)
    val animatedColor by animateColorAsState(
        targetValue = colorFromKg(kg),
        animationSpec = tween(durationMillis = 150),
        label = "label_color_${zone.shortLabel}"
    )

    val colors = MaterialTheme.appColors

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${zone.shortLabel} · ${zone.name}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText
            )
            Text(
                text = String.format("%.1f Kg", kg),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = animatedColor
            )
        }
        Text(
            text = "ADC $adc",
            fontSize = 10.sp,
            color = colors.tertiaryText
        )
    }
}

/**
 * Línea punteada delgada entre dos puntos en coordenadas absolutas del padre.
 * Usar dentro de un contenedor que controle el sistema de coordenadas (overlay).
 */
@Composable
fun DashedConnector(
    from: Offset,
    to: Offset,
    color: Color = MaterialTheme.appColors.dashedConnector,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        drawLine(
            color = color,
            start = from,
            end = to,
            strokeWidth = 1.dp.toPx(),
            pathEffect = dash
        )
    }
}
