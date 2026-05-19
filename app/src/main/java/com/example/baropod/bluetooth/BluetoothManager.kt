package com.example.baropod.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

/**
 * Encapsula el `BluetoothAdapter` y el `BluetoothSocket` SPP. No mantiene estado
 * reactivo: el [com.example.baropod.viewmodel.SensorViewModel] consume su API
 * desde una coroutine y publica el estado vía StateFlow.
 *
 * Toda operación de red BT corre en `Dispatchers.IO`. Internamente la lectura
 * usa [BufferedReader] para que el OS pueda agrupar bytes en bloques en lugar
 * de hacer un syscall por byte (clave para no perder velocidad cuando el ESP32
 * envía ráfagas de líneas).
 */
class BluetoothManager(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    private val androidBtManager: AndroidBluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBluetoothManager

    private val adapter: BluetoothAdapter? get() = androidBtManager?.adapter

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var input: InputStream? = null
    @Volatile private var reader: BufferedReader? = null

    // ---------- Permisos / disponibilidad ----------

    fun isBluetoothSupported(): Boolean = adapter != null
    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /** Permisos requeridos en runtime para esta app, según versión de Android. */
    fun requiredRuntimePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasAllRequiredPermissions(): Boolean {
        return requiredRuntimePermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ---------- Dispositivos emparejados ----------

    /**
     * Lista de dispositivos ya emparejados. Devuelve lista vacía si no hay permiso
     * o no hay adaptador.
     */
    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> {
        if (!hasAllRequiredPermissions()) return emptyList()
        val a = adapter ?: return emptyList()
        return try {
            a.bondedDevices?.toList() ?: emptyList()
        } catch (se: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun deviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: device.address
        } catch (se: SecurityException) {
            device.address
        }
    }

    // ---------- Conexión SPP ----------

    /**
     * Abre el socket SPP al [device] y deja el [BufferedReader] listo para lectura.
     * Llamar desde una coroutine. Lanza [IOException] si falla.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice) = withContext(io) {
        // Cerrar cualquier socket previo
        closeQuietly()

        // Cancelar descubrimiento (mejora la fiabilidad de la conexión SPP)
        try {
            adapter?.cancelDiscovery()
        } catch (se: SecurityException) {
            // Sin permiso para BLUETOOTH_SCAN: ignorar, no es bloqueante.
        }

        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            s.connect()
        } catch (e: IOException) {
            try { s.close() } catch (_: IOException) {}
            throw e
        }
        val stream = s.inputStream
        socket = s
        input = stream
        reader = BufferedReader(InputStreamReader(stream, Charsets.US_ASCII), 4096)
        Log.i(LOG_TAG, "Conectado a ${deviceName(device)}")
    }

    /** Indica si hay un socket conectado. */
    fun isConnected(): Boolean = socket?.isConnected == true

    /**
     * Lee la siguiente línea (terminada en '\n' o '\r\n'). Devuelve `null` si
     * el stream se cerró. Solo debe llamarse después de [connect], desde un
     * dispatcher de IO (es bloqueante).
     */
    fun readLine(): String? {
        val r = reader ?: throw IOException("Socket no conectado")
        return r.readLine()
    }

    /** Cierra el socket y el stream, ignorando errores. */
    fun closeQuietly() {
        try { reader?.close() } catch (_: IOException) {}
        try { input?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        reader = null
        input = null
        socket = null
    }

    companion object {
        /** UUID estándar SPP. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /**
         * Nombre BT esperado del dispositivo objetivo. Debe coincidir con el
         * nombre que el firmware del ESP32 publica en `SerialBT.begin(...)`.
         */
        const val DEFAULT_DEVICE_NAME = "Plantilla_ESP32"

        /** Tag para logs de stream BT. Filtrar con `adb logcat -s Baropod-BT`. */
        const val LOG_TAG = "Baropod-BT"

        /**
         * Cuando es `true`, se loguea cada línea recibida a logcat. Apagado por
         * defecto: logear 20 veces por segundo pone presión sobre `logd` y
         * notoriamente ralentiza al dispositivo entero (incluido logcat). El
         * panel de debug (long-press en el Hz) lo enciende automáticamente
         * mientras esté visible.
         */
        @Volatile
        var verboseLogging: Boolean = false
    }
}

/**
 * Lee líneas crudas del stream BT y entrega cada una junto a su timestamp
 * de recepción al callback. La conversión a [com.example.baropod.model.SensorReading]
 * la hace el llamador (para que pueda contar errores de parseo y mantener
 * estadísticas de inter-llegada).
 *
 * Cada línea recibida se loguea a logcat con tag [BluetoothManager.LOG_TAG]:
 *   `adb logcat -s Baropod-BT`
 *
 * Bucle bloqueante: ejecutar en [Dispatchers.IO] y cancelar la coroutine
 * para detenerlo. Lanza [IOException] cuando el stream se cierra.
 */
suspend fun BluetoothManager.readLines(
    onLine: suspend (line: String, timestampMs: Long) -> Unit
) = withContext(Dispatchers.IO) {
    var lastTsMs: Long = 0L
    while (true) {
        val line = readLine() ?: throw IOException("Stream BT cerrado por el dispositivo remoto")
        val tsMs = System.currentTimeMillis()
        val deltaMs = if (lastTsMs == 0L) 0L else (tsMs - lastTsMs)
        lastTsMs = tsMs
        if (BluetoothManager.verboseLogging) {
            Log.v(BluetoothManager.LOG_TAG, "Δt=${deltaMs}ms  line=\"$line\"")
        }
        onLine(line, tsMs)
    }
}
