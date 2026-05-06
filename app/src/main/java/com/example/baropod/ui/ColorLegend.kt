package com.example.baropod.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baropod.ui.theme.appColors
import com.example.baropod.util.PressurePalette

/**
 * Leyenda inferior: barra horizontal con gradiente y etiquetas 0% / 100%+.
 */
@Composable
fun ColorLegend(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(240.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            Canvas(modifier = Modifier.size(width = 240.dp, height = 10.dp)) {
                val brush = Brush.linearGradient(
                    colors = listOf(
                        PressurePalette.GREEN_LOW,
                        PressurePalette.LEGEND_MID,
                        PressurePalette.RED_HIGH
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f)
                )
                drawRect(brush = brush)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.width(240.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0%", fontSize = 11.sp, color = colors.secondaryText)
            Text("100%+", fontSize = 11.sp, color = colors.secondaryText)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Escala de presión",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.secondaryText
        )
    }
}
