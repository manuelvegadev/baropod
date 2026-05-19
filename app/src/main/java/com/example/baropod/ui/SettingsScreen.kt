package com.example.baropod.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baropod.R
import com.example.baropod.model.SensorReading
import com.example.baropod.model.SensorZone
import com.example.baropod.ui.theme.appColors
import com.example.baropod.util.ForceCalibration
import com.example.baropod.util.colorFromKg

/**
 * Pantalla de Calibración: arrastrar los dots reposiciona la zona en tiempo
 * real y se persiste al soltar. El botón "Entrada N" intercambia el
 * `inputIndex` con el dot que ya tuviera esa entrada, manteniendo siempre
 * una permutación válida (cada entrada se usa exactamente una vez).
 */
@Composable
fun SettingsScreen(
    zones: List<SensorZone>,
    lastReading: SensorReading,
    zeroOffsets: List<Int>,
    onUpdateZonePosition: (String, Float, Float) -> Unit,
    onUpdateZoneInputIndex: (String, Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.appColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Volver",
                    tint = colors.primaryText
                )
            }
            Text(
                text = "Calibración",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onReset) {
                Text("Restablecer")
            }
        }

        FootCalibrationCanvas(
            zones = zones,
            onPositionChanged = onUpdateZonePosition,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(horizontal = 16.dp)
        )

        Text(
            text = "Arrastra los círculos para reposicionar las zonas. " +
                    "Usa el botón \"Entrada\" para corregir el mapeo si un " +
                    "sensor físico no coincide con su dot.",
            fontSize = 11.sp,
            color = colors.secondaryText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(zones, key = { _, z -> z.shortLabel }) { idx, zone ->
                val traceColor = colors.sensorTraces.getOrElse(idx) { Color.Gray }
                val adc = lastReading.values.getOrNull(zone.inputIndex)
                val baseline = zeroOffsets.getOrElse(zone.inputIndex) { ForceCalibration.ADC_AT_0_KG }
                ZoneRow(
                    zone = zone,
                    traceColor = traceColor,
                    adc = adc,
                    baseline = baseline,
                    totalInputs = zones.size,
                    onSelectInputIndex = { newIdx ->
                        onUpdateZoneInputIndex(zone.shortLabel, newIdx)
                    }
                )
            }
        }
    }
}

@Composable
private fun FootCalibrationCanvas(
    zones: List<SensorZone>,
    onPositionChanged: (shortLabel: String, nx: Float, ny: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.appColors
    val outlineImage = ImageBitmap.imageResource(R.drawable.foot_outline)

    // Posición en curso por zona. Una entrada presente reemplaza el `nx/ny`
    // de la zona durante el drag; al soltar se persiste y se quita.
    var dragPositions by remember { mutableStateOf<Map<String, Pair<Float, Float>>>(emptyMap()) }

    // contentAlignment se queda en TopStart: los offsets en píxeles se
    // interpretan desde la esquina superior izquierda. Si centramos los
    // hijos, el offset se suma al desplazamiento de centrado y los dots
    // aparecen fuera de la silueta.
    BoxWithConstraints(modifier = modifier) {
        val box = rememberFootBox(outlineImage, maxWidth, maxHeight)
        val density = LocalDensity.current

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = outlineImage,
                dstOffset = IntOffset(box.leftPx.toInt(), box.topPx.toInt()),
                dstSize = IntSize(box.widthPx.toInt(), box.heightPx.toInt()),
                colorFilter = ColorFilter.tint(colors.footOutline)
            )
        }

        val dotSizeDp = 36.dp
        val dotSizePx = with(density) { dotSizeDp.toPx() }
        zones.forEach { zone ->
            // `pointerInput` mantiene su coroutine viva entre recomposiciones
            // mientras las keys no cambien — así que captura el `zone` de la
            // primera composición y nunca lo actualiza. Sin
            // `rememberUpdatedState`, al iniciar un drag después de commitear
            // uno previo el handler lee la posición original y el dot salta
            // al punto de partida.
            val zoneState by rememberUpdatedState(zone)
            val boxState by rememberUpdatedState(box)

            val current = dragPositions[zone.shortLabel]
            val nx = current?.first ?: zone.nx
            val ny = current?.second ?: zone.ny
            val cx = box.toCanvasX(nx)
            val cy = box.toCanvasY(ny)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((cx - dotSizePx / 2f).toInt(), (cy - dotSizePx / 2f).toInt())
                    }
                    .size(dotSizeDp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(zone.shortLabel) {
                        detectDragGestures(
                            onDragEnd = {
                                val label = zoneState.shortLabel
                                val finalPos = dragPositions[label]
                                if (finalPos != null) {
                                    onPositionChanged(label, finalPos.first, finalPos.second)
                                    dragPositions = dragPositions - label
                                }
                            },
                            onDragCancel = {
                                dragPositions = dragPositions - zoneState.shortLabel
                            }
                        ) { _, dragAmount ->
                            val z = zoneState
                            val b = boxState
                            val prev = dragPositions[z.shortLabel] ?: (z.nx to z.ny)
                            val newNx = (prev.first + dragAmount.x / b.widthPx).coerceIn(0f, 1f)
                            val newNy = (prev.second + dragAmount.y / b.heightPx).coerceIn(0f, 1f)
                            dragPositions = dragPositions + (z.shortLabel to (newNx to newNy))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = zone.shortLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun ZoneRow(
    zone: SensorZone,
    traceColor: Color,
    adc: Int?,
    baseline: Int,
    totalInputs: Int,
    onSelectInputIndex: (Int) -> Unit
) {
    val colors = MaterialTheme.appColors
    val kg = adc?.let { ForceCalibration.adcToKg(it, baseline) }
    val kgColor = if (kg != null) colorFromKg(kg) else colors.tertiaryText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(traceColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${zone.shortLabel} · ${zone.name}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText
            )
            Text(
                text = if (adc != null && kg != null) {
                    String.format("ADC %d  ·  %.1f Kg", adc, kg)
                } else {
                    "Sin datos aún"
                },
                fontSize = 11.sp,
                color = kgColor
            )
        }
        Spacer(Modifier.width(8.dp))
        InputIndexSelector(
            currentIndex = zone.inputIndex,
            totalInputs = totalInputs,
            onSelect = onSelectInputIndex
        )
    }
}

@Composable
private fun InputIndexSelector(
    currentIndex: Int,
    totalInputs: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Entrada ${currentIndex + 1}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (i in 0 until totalInputs) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Entrada ${i + 1}" +
                                    if (i == currentIndex) "  (actual)" else "",
                            fontSize = 13.sp,
                            fontWeight = if (i == currentIndex) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        if (i != currentIndex) onSelect(i)
                    }
                )
            }
        }
    }
}
