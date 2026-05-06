package com.example.baropod.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.baropod.bluetooth.BluetoothManager
import com.example.baropod.bluetooth.readLines
import com.example.baropod.bluetooth.SensorDataParser
import com.example.baropod.data.DevicePreferences
import com.example.baropod.data.TareStorage
import com.example.baropod.model.SensorReading
import com.example.baropod.util.ForceCalibration
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Estado de la conexión Bluetooth.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val deviceName: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

/**
 * Estadísticas del stream Bluetooth para diagnosticar problemas de buffering /
 * jitter. Se calculan sobre una ventana móvil de las últimas 100 muestras.
 */
data class DebugStats(
    val totalLines: Long = 0,
    val parseErrors: Long = 0,
    /** Δt entre la última línea recibida y la anterior. */
    val lastInterArrivalMs: Long = 0,
    val avgInterArrivalMs: Float = 0f,
    val minInterArrivalMs: Long = 0,
    val maxInterArrivalMs: Long = 0,
    /** Desviación estándar de los Δt — alto = jitter, bajo = stream estable. */
    val stdDevInterArrivalMs: Float = 0f,
    /** Última línea cruda recibida del ESP32 (truncada para no inflar el state). */
    val lastRawLine: String = ""
)

/**
 * Estado UI consolidado para las pantallas.
 */
data class SensorUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val lastReading: SensorReading = SensorReading.EMPTY,
    val sampleRateHz: Float = 0f,
    val paused: Boolean = false,
    val pairedDevices: List<DeviceItem> = emptyList(),
    /**
     * Línea base ("cero") por sensor, en unidades ADC. Se aplica como offset
     * en la conversión a porcentaje. Lista vacía = usar el cero por defecto
     * de [com.example.baropod.util.ForceCalibration]. Tras presionar la
     * tara, contiene el ADC actual de cada sensor en ese instante.
     */
    val zeroOffsets: List<Int> = emptyList(),
    /**
     * Historial reciente de presión (%) por sensor para la gráfica vs tiempo.
     * Externo: una lista por sensor. Interno: `FloatArray` con muestras
     * ordenadas oldest→newest. Se usa `FloatArray` (primitivo) en vez de
     * `List<Float>` para evitar el boxing de 200 floats por sensor cada 50 ms.
     */
    val pressureHistory: List<FloatArray> = emptyList(),
    /** Estadísticas para el panel de diagnóstico (long-press en el Hz). */
    val debug: DebugStats = DebugStats()
)

data class DeviceItem(
    val name: String,
    val address: String
)

class SensorViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val btManager = BluetoothManager(application.applicationContext)
    private val tareStorage = TareStorage(application.applicationContext)
    private val devicePrefs = DevicePreferences(application.applicationContext)

    private val _state = MutableStateFlow(SensorUiState())
    val state: StateFlow<SensorUiState> = _state.asStateFlow()

    private var readJob: Job? = null

    /** Dirección MAC del dispositivo conectado: clave para la tara persistida. */
    private var currentDeviceAddress: String? = null

    /**
     * Indica si ya se intentó la auto-reconexión al último dispositivo en este
     * ciclo de vida del proceso. Evita que un disconnect manual del usuario
     * dispare un nuevo auto-connect sin acción explícita.
     */
    private var attemptedAutoConnect: Boolean = false

    // Para el cálculo de tasa de muestras
    private var rateWindowStartMs: Long = 0L
    private var rateWindowCount: Int = 0

    // ---------- Diagnóstico de stream ----------
    private val recentInterArrivals = ArrayDeque<Long>()
    private var totalLines: Long = 0
    private var parseErrors: Long = 0
    private var lastLineTsMs: Long = 0L
    private var lastRawLine: String = ""

    // ---------- Buffer de historia (ring buffer primitivo) ----------
    /**
     * Buffers circulares por sensor. Se mantienen mutables y se reutilizan
     * entre lecturas para no allocar 200 floats boxeados a 20 Hz.
     */
    private var historyBuffers: List<HistoryRingBuffer> = emptyList()

    // ---------- Permisos / dispositivos ----------

    fun requiredRuntimePermissions(): Array<String> = btManager.requiredRuntimePermissions()
    fun hasAllRequiredPermissions(): Boolean = btManager.hasAllRequiredPermissions()
    fun isBluetoothSupported(): Boolean = btManager.isBluetoothSupported()
    fun isBluetoothEnabled(): Boolean = btManager.isBluetoothEnabled()

    /** Refresca la lista de dispositivos emparejados. Llamar después de aceptar permisos. */
    fun refreshPairedDevices() {
        val items = btManager.bondedDevices().map { d ->
            DeviceItem(name = btManager.deviceName(d), address = d.address)
        }
        _state.update { it.copy(pairedDevices = items) }
    }

    // ---------- Conexión ----------

    fun connectTo(address: String) {
        // Evitar conexiones simultáneas
        if (_state.value.connection is ConnectionState.Connecting) return
        readJob?.cancel()

        _state.update { it.copy(connection = ConnectionState.Connecting) }

        readJob = viewModelScope.launch {
            try {
                val device = findDevice(address)
                    ?: throw IllegalStateException("Dispositivo no encontrado")
                val name = btManager.deviceName(device)

                btManager.connect(device)
                currentDeviceAddress = address
                // Recordar este dispositivo como el último exitoso, para que la
                // próxima vez que abramos la app intentemos reconectar solos.
                devicePrefs.setLastDeviceAddress(address)
                // Restaurar tara persistida (si existe) para este dispositivo.
                val savedTare = tareStorage.load(address)
                _state.update {
                    it.copy(
                        connection = ConnectionState.Connected(name),
                        zeroOffsets = savedTare
                    )
                }

                // Ciclo de lectura. Cuando termina con error, lo capturamos abajo.
                btManager.readLines { line, tsMs ->
                    if (_state.value.paused) return@readLines
                    val parsed = SensorDataParser.parseLine(line, tsMs)
                    onLineReceived(line, parsed, tsMs)
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Throwable) {
                _state.update {
                    it.copy(connection = ConnectionState.Error(e.message ?: "Error desconocido"))
                }
            } finally {
                btManager.closeQuietly()
            }
        }
    }

    private suspend fun findDevice(address: String): BluetoothDevice? = withContext(Dispatchers.IO) {
        btManager.bondedDevices().firstOrNull { it.address == address }
    }

    /**
     * Intenta reconectar automáticamente al último dispositivo recordado.
     *
     * Idempotente: sólo dispara la primera vez que se invoca con condiciones
     * válidas (estado Disconnected, hay un device guardado). Si el usuario
     * desconecta manualmente más tarde, no volverá a intentar — debe pulsar
     * Conectar.
     *
     * El llamador (UI) es responsable de garantizar que ya hay permisos y
     * Bluetooth encendido antes de invocar.
     */
    fun tryAutoConnect() {
        if (attemptedAutoConnect) return
        if (_state.value.connection !is ConnectionState.Disconnected) return
        val savedAddress = devicePrefs.getLastDeviceAddress() ?: return
        // Si no está en la lista de emparejados, no tiene sentido intentar.
        val paired = btManager.bondedDevices().any { it.address == savedAddress }
        if (!paired) return
        attemptedAutoConnect = true
        connectTo(savedAddress)
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        btManager.closeQuietly()
        // Reset diagnóstico
        recentInterArrivals.clear()
        totalLines = 0
        parseErrors = 0
        lastLineTsMs = 0L
        lastRawLine = ""
        historyBuffers = emptyList()
        BluetoothManager.verboseLogging = false
        // La dirección y los offsets en runtime se limpian, pero la tara
        // persistida en SharedPreferences sigue intacta para la próxima conexión.
        currentDeviceAddress = null
        _state.update {
            it.copy(
                connection = ConnectionState.Disconnected,
                lastReading = SensorReading.EMPTY,
                sampleRateHz = 0f,
                paused = false,
                zeroOffsets = emptyList(),
                pressureHistory = emptyList(),
                debug = DebugStats()
            )
        }
    }

    fun setPaused(paused: Boolean) {
        _state.update { it.copy(paused = paused) }
    }

    fun togglePaused() {
        _state.update { it.copy(paused = !it.paused) }
    }

    /**
     * Captura el ADC actual de cada sensor como nueva línea base (tara).
     * Equivalente al botón "0" / "Tarar" de una balanza: corrige la deriva
     * por temperatura sin tocar el span.
     *
     * Si aún no se ha recibido ninguna lectura, no hace nada.
     */
    fun tare() {
        val current = _state.value.lastReading.values
        if (current.isEmpty()) return
        val offsets = current.toList()
        // Limpiamos la historia para no dibujar una discontinuidad cuando el cero
        // cambia: la curva post-tara empieza limpia desde 0 Kg.
        historyBuffers.forEach { it.clear() }
        // Persistir la tara para que sobreviva al reinicio de la app.
        currentDeviceAddress?.let { tareStorage.save(it, offsets) }
        _state.update {
            it.copy(zeroOffsets = offsets, pressureHistory = emptyList())
        }
    }

    /**
     * Descarta la tara: vuelve al cero por defecto y borra el valor persistido
     * para el dispositivo conectado, así no se restaurará en próximas conexiones.
     */
    fun resetTare() {
        historyBuffers.forEach { it.clear() }
        currentDeviceAddress?.let { tareStorage.clear(it) }
        _state.update {
            it.copy(zeroOffsets = emptyList(), pressureHistory = emptyList())
        }
    }

    /**
     * Activa o desactiva el log verboso del stream BT (cada línea recibida).
     * Lo controla la pantalla cuando se muestra el panel de debug.
     */
    fun setDebugLogging(enabled: Boolean) {
        BluetoothManager.verboseLogging = enabled
    }

    // ---------- Internos ----------

    /**
     * Punto de entrada para cada línea cruda recibida. Actualiza tracking de
     * diagnóstico y, si la línea parseó correctamente, llama a [publishReading].
     */
    private fun onLineReceived(rawLine: String, parsed: SensorReading?, tsMs: Long) {
        totalLines++
        lastRawLine = rawLine.take(64)
        if (lastLineTsMs > 0L) {
            val delta = tsMs - lastLineTsMs
            recentInterArrivals.addLast(delta)
            while (recentInterArrivals.size > STATS_WINDOW) {
                recentInterArrivals.removeFirst()
            }
        }
        lastLineTsMs = tsMs

        if (parsed == null) {
            parseErrors++
            // Sólo refrescar stats: no hay nueva muestra que graficar.
            _state.update { it.copy(debug = computeDebugStats()) }
        } else {
            publishReading(parsed)
        }
    }

    private fun computeDebugStats(): DebugStats {
        val deltas = recentInterArrivals
        val avg = if (deltas.isNotEmpty()) deltas.sum().toDouble() / deltas.size else 0.0
        val min = deltas.minOrNull() ?: 0L
        val max = deltas.maxOrNull() ?: 0L
        val variance = if (deltas.size > 1) {
            deltas.sumOf { val d = it - avg; d * d } / (deltas.size - 1)
        } else 0.0
        return DebugStats(
            totalLines = totalLines,
            parseErrors = parseErrors,
            lastInterArrivalMs = if (deltas.isNotEmpty()) deltas.last() else 0L,
            avgInterArrivalMs = avg.toFloat(),
            minInterArrivalMs = min,
            maxInterArrivalMs = max,
            stdDevInterArrivalMs = sqrt(variance).toFloat(),
            lastRawLine = lastRawLine
        )
    }

    private fun publishReading(reading: SensorReading) {
        // Cálculo de tasa de muestras: contamos muestras en ventana de 1s.
        val now = reading.timestampMs
        if (rateWindowStartMs == 0L) {
            rateWindowStartMs = now
            rateWindowCount = 0
        }
        rateWindowCount++
        val elapsed = now - rateWindowStartMs
        val newRate = if (elapsed >= 1000L) {
            val hz = (rateWindowCount * 1000f / elapsed)
            rateWindowStartMs = now
            rateWindowCount = 0
            hz
        } else {
            _state.value.sampleRateHz
        }

        // Calcular Kg por sensor con la tara actual y actualizar la historia.
        val zeroOffsets = _state.value.zeroOffsets
        val kgValues = reading.values.mapIndexed { i, adc ->
            val baseline = zeroOffsets.getOrElse(i) { ForceCalibration.ADC_AT_0_KG }
            ForceCalibration.adcToKg(adc, baseline)
        }

        // Append a los ring buffers reutilizables y emitir snapshots inmutables.
        if (historyBuffers.size != kgValues.size) {
            historyBuffers = List(kgValues.size) { HistoryRingBuffer(MAX_HISTORY_SAMPLES) }
        }
        historyBuffers.forEachIndexed { i, buf -> buf.add(kgValues[i]) }
        val updatedHistory: List<FloatArray> = historyBuffers.map { it.snapshot() }

        _state.update {
            it.copy(
                lastReading = reading,
                sampleRateHz = newRate,
                pressureHistory = updatedHistory,
                debug = computeDebugStats()
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        readJob?.cancel()
        btManager.closeQuietly()
    }

    companion object {
        /** Tamaño máximo del buffer de historia (≈ 10 s a 20 Hz). */
        const val MAX_HISTORY_SAMPLES: Int = 200

        /** Ventana móvil de Δt para calcular avg/min/max/σ del stream BT. */
        const val STATS_WINDOW: Int = 100

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                SensorViewModel(app)
            }
        }
    }
}

/**
 * Buffer circular de muestras `Float` con allocations mínimas. El buffer interno
 * se reutiliza entre lecturas; cada [snapshot] devuelve una copia ordenada
 * oldest→newest, único FloatArray que se aloja por emisión (~800 bytes para
 * 200 muestras vs ~3 KB de un `List<Float>` boxeado equivalente).
 */
private class HistoryRingBuffer(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var head = 0
    private var size_ = 0

    val size: Int get() = size_

    fun add(value: Float) {
        data[head] = value
        head = (head + 1) % capacity
        if (size_ < capacity) size_++
    }

    fun clear() {
        head = 0
        size_ = 0
    }

    /** Snapshot inmutable, oldest primero. */
    fun snapshot(): FloatArray {
        val out = FloatArray(size_)
        if (size_ < capacity) {
            System.arraycopy(data, 0, out, 0, size_)
        } else {
            // Buffer lleno: el más viejo está en `head`, el más nuevo en head-1.
            val firstChunk = capacity - head
            System.arraycopy(data, head, out, 0, firstChunk)
            System.arraycopy(data, 0, out, firstChunk, head)
        }
        return out
    }
}
