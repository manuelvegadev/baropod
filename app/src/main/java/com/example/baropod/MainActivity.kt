package com.example.baropod

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.baropod.ui.ConnectionScreen
import com.example.baropod.ui.PatientScreen
import com.example.baropod.ui.SettingsScreen
import com.example.baropod.ui.VisualizationScreen
import com.example.baropod.ui.theme.BaropodTheme
import com.example.baropod.viewmodel.ConnectionState
import com.example.baropod.viewmodel.SensorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SensorViewModel by viewModels { SensorViewModel.Factory }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Tras la respuesta del usuario, refrescamos la lista de dispositivos.
        viewModel.refreshPairedDevices()
        permissionsTick++
    }

    // Estado simple para forzar recomposición cuando cambian permisos.
    private var permissionsTick: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BaropodTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        viewModel = viewModel,
                        onRequestPermissions = {
                            permissionsLauncher.launch(viewModel.requiredRuntimePermissions())
                        },
                        onOpenAppSettings = ::openAppSettings,
                        onOpenBluetoothSettings = ::openBluetoothSettings
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPairedDevices()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}

@Composable
private fun AppRoot(
    viewModel: SensorViewModel,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Recomponer permisos al volver de ajustes: usamos un tick que cambia en onResume.
    var refreshTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        // Refrescar al iniciar
        viewModel.refreshPairedDevices()
        refreshTrigger++
    }

    val hasPermissions = viewModel.hasAllRequiredPermissions()
    val btEnabled = viewModel.isBluetoothEnabled()
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val patient by viewModel.patientData.collectAsStateWithLifecycle()

    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.Main) }

    // Auto-conexión al último dispositivo recordado. El VM tiene un flag
    // idempotente, así que esto sólo dispara la primera vez por proceso.
    LaunchedEffect(hasPermissions, btEnabled) {
        if (hasPermissions && btEnabled) {
            viewModel.tryAutoConnect()
        }
    }

    // Si la conexión se pierde mientras una subpantalla estaba abierta,
    // volvemos al Main para que la próxima reconexión arranque limpio.
    LaunchedEffect(state.connection) {
        if (state.connection !is ConnectionState.Connected) subScreen = SubScreen.Main
    }

    when (state.connection) {
        is ConnectionState.Connected -> {
            when (subScreen) {
                SubScreen.Settings -> SettingsScreen(
                    zones = zones,
                    lastReading = state.lastReading,
                    zeroOffsets = state.zeroOffsets,
                    onUpdateZonePosition = viewModel::updateZonePosition,
                    onUpdateZoneInputIndex = viewModel::updateZoneInputIndex,
                    onReset = viewModel::resetZones,
                    onBack = { subScreen = SubScreen.Main }
                )
                SubScreen.Patient -> PatientScreen(
                    patient = patient,
                    photosDir = viewModel.patientPhotosDir,
                    onUpdateName = viewModel::updatePatientName,
                    onUpdateAge = viewModel::updatePatientAge,
                    onUpdateHeight = viewModel::updatePatientHeight,
                    onUpdateWeight = viewModel::updatePatientWeight,
                    onAddPhoto = viewModel::addPatientPhoto,
                    onRemovePhoto = viewModel::removePatientPhoto,
                    onBack = { subScreen = SubScreen.Main }
                )
                SubScreen.Main -> VisualizationScreen(
                    state = state,
                    zones = zones,
                    onTogglePause = viewModel::togglePaused,
                    onTare = viewModel::tare,
                    onResetTare = viewModel::resetTare,
                    onDisconnect = viewModel::disconnect,
                    onOpenSettings = { subScreen = SubScreen.Settings },
                    onOpenPatient = { subScreen = SubScreen.Patient },
                    onSetDebugLogging = viewModel::setDebugLogging
                )
            }
        }
        else -> {
            ConnectionScreen(
                state = state,
                hasPermissions = hasPermissions,
                bluetoothEnabled = btEnabled,
                onRequestPermissions = onRequestPermissions,
                onRefreshDevices = viewModel::refreshPairedDevices,
                onConnect = { device -> viewModel.connectTo(device.address) },
                onOpenSettings = if (!btEnabled) onOpenBluetoothSettings else onOpenAppSettings,
                onEnterDevMode = viewModel::enterDevMode
            )
        }
    }
}

private sealed interface SubScreen {
    data object Main : SubScreen
    data object Settings : SubScreen
    data object Patient : SubScreen
}
