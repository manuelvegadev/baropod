package com.example.baropod.model

/**
 * Una muestra recibida desde el ESP32.
 *
 * @param timestampMs Marca de tiempo (System.currentTimeMillis) cuando se recibió.
 * @param values Lista de valores ADC crudos (0..4095). Tamaño variable: hoy 2,
 *               en el futuro hasta 8 según la cantidad de sensores activos.
 */
data class SensorReading(
    val timestampMs: Long,
    val values: List<Int>
) {
    companion object {
        val EMPTY = SensorReading(0L, emptyList())
    }
}
