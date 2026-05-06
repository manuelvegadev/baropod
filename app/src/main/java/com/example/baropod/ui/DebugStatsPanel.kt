package com.example.baropod.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baropod.ui.theme.appColors
import com.example.baropod.viewmodel.DebugStats

/**
 * Panel compacto con métricas del stream BT para diagnosticar buffering / jitter.
 * Se muestra al hacer long-press sobre el indicador de Hz en la barra superior.
 */
@Composable
fun DebugStatsPanel(
    stats: DebugStats,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.appColors

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = "Diagnóstico de stream",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )
            Spacer(Modifier.height(4.dp))

            // Línea 1: contadores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatChip("líneas", stats.totalLines.toString())
                StatChip(
                    "errores",
                    stats.parseErrors.toString(),
                    accent = stats.parseErrors > 0
                )
            }

            Spacer(Modifier.height(4.dp))

            // Línea 2: inter-arrival
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatChip("Δt", "${stats.lastInterArrivalMs} ms")
                StatChip("media", "${stats.avgInterArrivalMs.toInt()} ms")
                StatChip("σ", "${stats.stdDevInterArrivalMs.toInt()} ms")
            }

            Spacer(Modifier.height(4.dp))

            // Línea 3: extremos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatChip("min", "${stats.minInterArrivalMs} ms")
                StatChip("max", "${stats.maxInterArrivalMs} ms")
            }

            Spacer(Modifier.height(4.dp))

            // Línea 4: última línea cruda
            Text(
                text = "última: \"${stats.lastRawLine}\"",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.secondaryText
            )
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    accent: Boolean = false
) {
    val colors = MaterialTheme.appColors
    Row {
        Text(
            text = "$label: ",
            fontSize = 10.sp,
            color = colors.secondaryText
        )
        Text(
            text = value,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) colors.statusError else colors.primaryText
        )
    }
}
