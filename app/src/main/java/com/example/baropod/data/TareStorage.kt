package com.example.baropod.data

import android.content.Context

/**
 * Tara (offsets de cero por sensor), una entrada por dirección MAC para que
 * varios dispositivos coexistan sin contaminarse. Payload: CSV de enteros.
 */
class TareStorage(context: Context) {

    private val prefs = context.baropodPrefs()

    /** Devuelve los offsets guardados, o lista vacía si nunca se ha tarado. */
    fun load(deviceAddress: String): List<Int> {
        val raw = prefs.getString(keyFor(deviceAddress), null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    /** Guarda los offsets. Una lista vacía se trata como `clear`. */
    fun save(deviceAddress: String, offsets: List<Int>) {
        if (offsets.isEmpty()) {
            clear(deviceAddress)
            return
        }
        prefs.edit()
            .putString(keyFor(deviceAddress), offsets.joinToString(","))
            .apply()
    }

    fun clear(deviceAddress: String) {
        prefs.edit().remove(keyFor(deviceAddress)).apply()
    }

    private fun keyFor(address: String) = "tare_$address"
}
