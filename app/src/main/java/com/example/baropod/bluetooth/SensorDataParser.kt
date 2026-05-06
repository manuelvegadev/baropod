package com.example.baropod.bluetooth

import com.example.baropod.model.SensorReading

/**
 * Parser de las líneas que envía el ESP32.
 * Formato: "1716,1721\n" (n valores enteros separados por coma, terminados en \n).
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
