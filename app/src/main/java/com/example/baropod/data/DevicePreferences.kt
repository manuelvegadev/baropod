package com.example.baropod.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistencia ligera de la dirección Bluetooth del último dispositivo al que
 * la app se conectó con éxito. Se usa para intentar reconectar automáticamente
 * la próxima vez que se abre la app.
 *
 * Comparte el mismo archivo de `SharedPreferences` que [TareStorage]
 * (`baropod_prefs`) bajo una clave distinta, para no duplicar archivos
 * y mantener todo el estado persistido en un único lugar.
 */
class DevicePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Última MAC conocida o `null` si nunca se ha conectado. */
    fun getLastDeviceAddress(): String? = prefs.getString(KEY_LAST_DEVICE, null)

    fun setLastDeviceAddress(address: String) {
        prefs.edit().putString(KEY_LAST_DEVICE, address).apply()
    }

    fun clearLastDeviceAddress() {
        prefs.edit().remove(KEY_LAST_DEVICE).apply()
    }

    companion object {
        private const val PREFS_NAME = "baropod_prefs"
        private const val KEY_LAST_DEVICE = "last_device_address"
    }
}
