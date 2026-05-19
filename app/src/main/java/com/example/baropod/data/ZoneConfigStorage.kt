package com.example.baropod.data

import android.content.Context
import com.example.baropod.model.SensorZone

/**
 * Persistencia de la calibración por zona (posición y `inputIndex`). Cada
 * zona se persiste bajo claves derivadas de su `shortLabel`; si una clave
 * falta el `getFloatSafe`/`getInt` devuelve el valor por defecto, así que
 * cualquier zona sin override pasa intacta.
 */
class ZoneConfigStorage(context: Context) {

    private val prefs = context.baropodPrefs()

    fun applyOverrides(defaults: List<SensorZone>): List<SensorZone> {
        return defaults.map { z ->
            val nx = prefs.getFloatSafe(nxKey(z.shortLabel), z.nx).coerceIn(0f, 1f)
            val ny = prefs.getFloatSafe(nyKey(z.shortLabel), z.ny).coerceIn(0f, 1f)
            val inputIndex = prefs.getInt(idxKey(z.shortLabel), z.inputIndex)
            if (nx == z.nx && ny == z.ny && inputIndex == z.inputIndex) z
            else z.copy(nx = nx, ny = ny, inputIndex = inputIndex)
        }
    }

    fun saveAll(zones: List<SensorZone>) {
        val edit = prefs.edit()
        zones.forEach { z ->
            edit.putFloat(nxKey(z.shortLabel), z.nx)
            edit.putFloat(nyKey(z.shortLabel), z.ny)
            edit.putInt(idxKey(z.shortLabel), z.inputIndex)
        }
        edit.apply()
    }

    fun clearAll(shortLabels: List<String>) {
        val edit = prefs.edit()
        shortLabels.forEach { short ->
            edit.remove(nxKey(short))
            edit.remove(nyKey(short))
            edit.remove(idxKey(short))
        }
        edit.apply()
    }

    private fun nxKey(short: String) = "zone_${short}_nx"
    private fun nyKey(short: String) = "zone_${short}_ny"
    private fun idxKey(short: String) = "zone_${short}_idx"
}
