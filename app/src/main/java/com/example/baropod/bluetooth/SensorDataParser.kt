package com.example.baropod.bluetooth

import com.example.baropod.model.SensorReading

/**
 * Parser de las líneas que envía el ESP32.
 * Formato: "1820,1750,1900,1830,2100,2250,1780,1820\n"
 *   - n valores enteros separados por coma, terminados en `\n`.
 *   - n es variable (la app no asume un número fijo de sensores). Hoy 8;
 *     en el futuro hasta 16 si se instrumentan ambos pies.
 */
object SensorDataParser {

    /**
     * Convierte una línea cruda en [SensorReading].
     * Si la línea está corrupta, vacía o no contiene enteros válidos, retorna null
     * (el llamador debe descartarla sin crashear).
     */
    fun parseLine(rawLine: String, timestampMs: Long = System.currentTimeMillis()): SensorReading? {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split(',')
        if (parts.isEmpty()) return null

        val values = ArrayList<Int>(parts.size)
        for (part in parts) {
            val n = part.trim().toIntOrNull() ?: return null
            values.add(n)
        }
        if (values.isEmpty()) return null
        return SensorReading(timestampMs, values)
    }
}
