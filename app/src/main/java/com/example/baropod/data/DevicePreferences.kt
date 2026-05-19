package com.example.baropod.data

import android.content.Context

/**
 * Última dirección Bluetooth a la que la app se conectó con éxito; usada
 * para intentar reconectar automáticamente al abrir la app.
 */
class DevicePreferences(context: Context) {

    private val prefs = context.baropodPrefs()

    fun getLastDeviceAddress(): String? = prefs.getString(KEY_LAST_DEVICE, null)

    fun setLastDeviceAddress(address: String) {
        prefs.edit().putString(KEY_LAST_DEVICE, address).apply()
    }

    fun clearLastDeviceAddress() {
        prefs.edit().remove(KEY_LAST_DEVICE).apply()
    }

    private companion object {
        const val KEY_LAST_DEVICE = "last_device_address"
    }
}
