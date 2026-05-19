package com.example.baropod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baropod.BuildConfig
import com.example.baropod.bluetooth.BluetoothManager
import com.example.baropod.ui.theme.appColors
import com.example.baropod.viewmodel.ConnectionState
import com.example.baropod.viewmodel.DeviceItem
import com.example.baropod.viewmodel.SensorUiState

/**
 * Pantalla 1: lista de dispositivos emparejados, selección y conexión.
 */
@Composable
fun ConnectionScreen(
    state: SensorUiState,
    hasPermissions: Boolean,
    bluetoothEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onRefreshDevices: () -> Unit,
    onConnect: (DeviceItem) -> Unit,
    onOpenSettings: () -> Unit,
    onEnterDevMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf<DeviceItem?>(null) }
    val colors = MaterialTheme.appColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Baropod",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Selecciona el dispositivo para iniciar la captura",
            fontSize = 13.sp,
            color = colors.secondaryText
        )
        Spacer(Modifier.height(16.dp))

        ConnectionStatusRow(state.connection)
        Spacer(Modifier.height(12.dp))

        when {
            !bluetoothEnabled -> {
                InfoCard(
                    title = "Bluetooth desactivado",
                    body = "Activa el Bluetooth en los ajustes del sistema para continuar."
                ) {
                    OutlinedButton(onClick = onOpenSettings) {
                        Text("Abrir ajustes")
                    }
                }
            }
            !hasPermissions -> {
                InfoCard(
                    title = "Permisos necesarios",
                    body = "Esta app necesita permisos de Bluetooth para conectarse al ESP32."
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRequestPermissions) {
                            Text("Conceder permisos")
                        }
                        OutlinedButton(onClick = onOpenSettings) {
                            Text("Abrir ajustes")
                        }
                    }
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dispositivos emparejados",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryText,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onRefreshDevices) {
                        Text("Actualizar")
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (state.pairedDevices.isEmpty()) {
                    InfoCard(
                        title = "Sin dispositivos emparejados",
                        body = "Empareja tu ESP32 (`${BluetoothManager.DEFAULT_DEVICE_NAME}`) " +
                                "desde los ajustes de Bluetooth del sistema y vuelve a esta pantalla."
                    ) {
                        OutlinedButton(onClick = onOpenSettings) {
                            Text("Abrir ajustes Bluetooth")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(state.pairedDevices, key = { it.address }) { device ->
                            DeviceRow(
                                device = device,
                                isSelected = selected?.address == device.address,
                                onClick = { selected = device }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    val canConnect = selected != null &&
                            state.connection !is ConnectionState.Connecting
                    Button(
                        onClick = { selected?.let(onConnect) },
                        enabled = canConnect,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when (state.connection) {
                                is ConnectionState.Connecting -> "Conectando…"
                                else -> "Conectar"
                            }
                        )
                    }
                }
            }
        }

        // ----- Modo desarrollo (sólo builds debug) -----
        // En el flujo normal, la lista de dispositivos consume el peso
        // vertical disponible y este botón queda anclado al fondo. En las
        // ramas con InfoCard (sin BT / sin permisos / sin dispositivos) cae
        // justo debajo del card — sigue siendo discreto y accesible.
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onEnterDevMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Modo desarrollo (sin Bluetooth)",
                    fontSize = 12.sp,
                    color = colors.tertiaryText
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusRow(connection: ConnectionState) {
    val colors = MaterialTheme.appColors
    val (color, label) = when (connection) {
        ConnectionState.Disconnected -> colors.statusWarn to "Desconectado"
        ConnectionState.Connecting -> colors.statusWarn to "Conectando…"
        is ConnectionState.Connected -> colors.statusOk to "Conectado · ${connection.deviceName}"
        is ConnectionState.Error -> colors.statusError to "Error: ${connection.message}"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, fontSize = 13.sp, color = colors.strongText)
    }
}

@Composable
private fun DeviceRow(
    device: DeviceItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.appColors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colors.selectedCardBackground else colors.cardBackground
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = device.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = device.address,
                fontSize = 12.sp,
                color = colors.secondaryText
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    action: @Composable (() -> Unit)? = null
) {
    val colors = MaterialTheme.appColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.infoCardBackground)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                fontSize = 13.sp,
                color = colors.secondaryText
            )
            if (action != null) {
                Spacer(Modifier.height(10.dp))
                action()
            }
        }
    }
}
