package com.example.baropod.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistencia de la tara (offsets de cero por sensor) entre sesiones.
 *
 * Se guarda **una entrada por dirección MAC del dispositivo Bluetooth**, así
 * varios dispositivos pueden coexistir sin que la calibración de uno contamine
 * a la del otro. El payload es una cadena CSV de enteros: `"1776,1820"`.
 *
 * Implementado con `SharedPreferences` por simplicidad — no añade dependencias
 * extra y para 2-8 enteros la latencia es despreciable. Si en el futuro hace
 * falta migrar a DataStore (p. ej. para reactividad o tipado fuerte), la API
 * pública de esta clase se mantiene igual.
 */
class TareStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    companion object {
        private const val PREFS_NAME = "baropod_prefs"
    }
}
