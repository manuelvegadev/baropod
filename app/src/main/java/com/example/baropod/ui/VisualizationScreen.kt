package com.example.baropod.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baropod.model.FootSide
import com.example.baropod.model.SensorZone
import com.example.baropod.ui.theme.appColors
import com.example.baropod.util.ForceCalibration
import com.example.baropod.util.colorFromKg
import com.example.baropod.viewmodel.ConnectionState
import com.example.baropod.viewmodel.SensorUiState

/**
 * Pantalla 2: visualización principal cuando la conexión está activa.
 *
 * Layout:
 *   - Barra superior: estado, tasa de muestreo y botón compacto de Tarar.
 *   - Pie con huella revelada (~40 % del alto).
 *   - Gráfica de presión vs. tiempo + leyenda (~60 % del alto).
 *   - Botones inferiores: Pausar/Reanudar y Desconectar.
 */
@Composable
fun VisualizationScreen(
    state: SensorUiState,
    zones: List<SensorZone>,
    onTogglePause: () -> Unit,
    onTare: () -> Unit,
    onResetTare: () -> Unit,
    onDisconnect: () -> Unit,
    onSetDebugLogging: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.appColors
    var showDebug by remember { mutableStateOf(false) }

    // El logging verboso del stream BT solo se activa cuando el panel de debug
    // está visible: 20 logs por segundo a logd lagean al sistema entero.
    LaunchedEffect(showDebug) {
        onSetDebugLogging(showDebug)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        TopStatusBar(
            state = state,
            onTare = onTare,
            onResetTare = onResetTare,
            onToggleDebug = { showDebug = !showDebug }
        )
        if (showDebug) {
            Spacer(Modifier.height(8.dp))
            DebugStatsPanel(stats = state.debug)
        }
        Spacer(Modifier.height(12.dp))

        // Pies (izquierdo + derecho), uno al lado del otro.
        val leftSlice = sliceForSide(zones, state.lastReading.values, state.zeroOffsets, FootSide.LEFT)
        val rightSlice = sliceForSide(zones, state.lastReading.values, state.zeroOffsets, FootSide.RIGHT)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FootColumn(
                title = "Pie izquierdo",
                slice = leftSlice,
                mirrored = true,
                modifier = Modifier.weight(1f)
            )
            FootColumn(
                title = "Pie derecho",
                slice = rightSlice,
                mirrored = false,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Gráfica vs. tiempo (zona inferior, más compacta).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
        ) {
            PressureChart(
                history = state.pressureHistory,
                zones = zones,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onTogglePause,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.paused) colors.statusOk else colors.statusWarn
                )
            ) {
                Text(if (state.paused) "Reanudar" else "Pausar")
            }
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.weight(1f)
            ) {
                Text("Desconectar")
            }
        }
    }
}

/**
 * Slice de zonas + ADC + offsets que pertenecen a un lado, preservando la
 * alineación posicional con la trama recibida del ESP32.
 */
private data class FootSlice(
    val zones: List<SensorZone>,
    val adcValues: List<Int>,
    val zeroOffsets: List<Int>
)

private fun sliceForSide(
    zones: List<SensorZone>,
    adcValues: List<Int>,
    zeroOffsets: List<Int>,
    side: FootSide
): FootSlice {
    val indexed = zones.withIndex().filter { it.value.side == side }
    val zs = indexed.map { it.value }
    val adcs = if (adcValues.isEmpty()) emptyList()
    else indexed.map { adcValues.getOrNull(it.index) ?: 0 }
    val offs = if (zeroOffsets.isEmpty()) emptyList()
    else indexed.map { zeroOffsets.getOrNull(it.index) ?: 0 }
    return FootSlice(zs, adcs, offs)
}

@Composable
private fun FootColumn(
    title: String,
    slice: FootSlice,
    mirrored: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.appColors

    // Suma total de fuerza de los sensores del pie. Si no hay sensores,
    // mostramos un guion para que se vea que la columna existe pero aún no
    // tiene datos. El color usa colorFromKg(kg) — saturará en rojo cuando
    // el total pase los 5 Kg, lo cual es esperable con varios sensores.
    val totalKg: Float? = if (slice.zones.isEmpty() || slice.adcValues.isEmpty()) {
        null
    } else {
        var sum = 0f
        slice.zones.forEachIndexed { i, _ ->
            val baseline = slice.zeroOffsets.getOrElse(i) { ForceCalibration.ADC_AT_0_KG }
            val adc = slice.adcValues.getOrNull(i) ?: baseline
            sum += ForceCalibration.adcToKg(adc, baseline)
        }
        sum
    }
    val totalText = if (totalKg != null) String.format("%.1f Kg", totalKg) else "—"
    val totalColor = if (totalKg != null) colorFromKg(totalKg) else colors.tertiaryText

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.secondaryText
        )
        Text(
            text = totalText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = totalColor
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            FootprintHeatmap(
                zones = slice.zones,
                adcValues = slice.adcValues,
                zeroOffsets = slice.zeroOffsets,
                mirrored = mirrored,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopStatusBar(
    state: SensorUiState,
    onTare: () -> Unit,
    onResetTare: () -> Unit,
    onToggleDebug: () -> Unit
) {
    val colors = MaterialTheme.appColors
    val connection = state.connection
    val (color, label) = when (connection) {
        ConnectionState.Disconnected -> colors.statusWarn to "Desconectado"
        ConnectionState.Connecting -> colors.statusWarn to "Conectando…"
        is ConnectionState.Connected -> colors.statusOk to "Conectado · ${connection.deviceName}"
        is ConnectionState.Error -> colors.statusError to "Error: ${connection.message}"
    }

    val hz = state.sampleRateHz
    val periodMs = if (hz > 0f) (1000f / hz).toInt() else 0
    val rateText = if (hz > 0f) {
        "${hz.toInt()} Hz · ${periodMs} ms"
    } else {
        "— Hz"
    }

    val tareEnabled = state.lastReading.values.isNotEmpty()
    val tared = state.zeroOffsets.isNotEmpty()
    val tareTint = if (tared) MaterialTheme.colorScheme.primary else colors.secondaryText

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.strongText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = rateText,
            fontSize = 12.sp,
            color = colors.secondaryText,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = { onToggleDebug() })
            }
        )
        Spacer(Modifier.width(8.dp))
        // Click corto: re-tarar. Long-press: descartar la tara persistida.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .combinedClickable(
                    enabled = tareEnabled,
                    onClick = onTare,
                    onLongClick = if (tared) onResetTare else null
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Adjust,
                contentDescription = if (tared) {
                    "Re-tarar (mantén pulsado para descartar)"
                } else {
                    "Tarar"
                },
                tint = if (tareEnabled) tareTint else colors.tertiaryText
            )
        }
    }
}
