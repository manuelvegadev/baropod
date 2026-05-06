package com.example.baropod.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Calibración ADC -> fuerza en kilogramos.
 *
 * Editar estas constantes según el sensor / la calibración real. La unidad
 * elegida en la app es kilogramos porque coincide con el rango nominal del
 * RFP-602 (0 – 5 Kg).
 */
object ForceCalibration {
    /** ADC en reposo, sin presión sobre el sensor. Mapea a 0 Kg. */
    const val ADC_AT_0_KG: Int = 1716

    /**
     * ADC con 1 Kg de fuerza aplicada (calibración con un dedo). El rango
     * 1716 → 2200 = 484 unidades ≈ 1 Kg. Asumiendo respuesta lineal, los
     * 5 Kg del fondo de escala caen en ~ADC 4136, justo más allá de la
     * saturación del ADC de 12 bits (4095): conveniente, porque significa
     * que la app puede mostrar el rango entero del sensor.
     */
    const val ADC_AT_1_KG: Int = 2200

    /** Tope nominal del sensor RFP-602 en kilogramos. */
    const val FULL_SCALE_KG: Float = 5.0f

    /** Pendiente: unidades ADC por cada kilogramo. */
    val ADC_PER_KG: Int get() = ADC_AT_1_KG - ADC_AT_0_KG

    /**
     * Convierte un ADC crudo en kilogramos usando la línea base ([baseline])
     * que pase el llamador (la tara). El span es fijo porque la sensibilidad
     * del sensor no deriva con la temperatura, sólo el cero.
     *
     * No se aplica clamp en el extremo superior: cuando el sensor satura,
     * el llamador puede mostrarlo como "fuera de rango".
     */
    fun adcToKg(adc: Int, baseline: Int = ADC_AT_0_KG): Float {
        val perKg = ADC_PER_KG.toFloat()
        if (perKg <= 0f) return 0f
        val kg = (adc - baseline) / perKg
        return if (kg < 0f) 0f else kg
    }
}

/** Paleta de la escala de fuerza. */
object PressurePalette {
    val GREEN_LOW = Color(0xFF88CCAA)
    val YELLOW_GREEN = Color(0xFFD5E84A)
    val ORANGE = Color(0xFFFFA830)
    val RED_HIGH = Color(0xFFA32D2D)
    val LEGEND_MID = Color(0xFFFFD030)
}

/**
 * Color por intensidad de fuerza, en kilogramos. Las transiciones están
 * proporcionalmente alineadas con el fondo de escala de 5 Kg:
 *
 *   0 – 1.25 Kg  : verde claro      → amarillo verdoso
 *   1.25 – 3 Kg  : amarillo verdoso → naranja
 *   3 – 5 Kg     : naranja          → rojo intenso
 *   > 5 Kg       : rojo intenso fijo (saturación del sensor)
 */
fun colorFromKg(kg: Float): Color {
    return when {
        kg <= 0f -> PressurePalette.GREEN_LOW
        kg < 1.25f -> {
            val t = kg / 1.25f
            lerp(PressurePalette.GREEN_LOW, PressurePalette.YELLOW_GREEN, t)
        }
        kg < 3f -> {
            val t = (kg - 1.25f) / (3f - 1.25f)
            lerp(PressurePalette.YELLOW_GREEN, PressurePalette.ORANGE, t)
        }
        kg < 5f -> {
            val t = (kg - 3f) / (5f - 3f)
            lerp(PressurePalette.ORANGE, PressurePalette.RED_HIGH, t)
        }
        else -> PressurePalette.RED_HIGH
    }
}
