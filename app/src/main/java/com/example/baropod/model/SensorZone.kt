package com.example.baropod.model

/**
 * Zona/sensor sobre la huella plantar.
 *
 * Las posiciones se expresan en coordenadas normalizadas dentro del bounding box
 * del pie: (0,0) = arriba-izquierda, (1,1) = abajo-derecha. Se asumen siempre
 * en la orientación natural del pie derecho; al renderizar el pie izquierdo
 * el composable las espeja automáticamente.
 *
 * @param side Pie al que pertenece el sensor. Se usa para distribuirlo en la
 *             pantalla y para etiquetar la traza correspondiente en la gráfica.
 */
data class SensorZone(
    val name: String,
    val shortLabel: String,
    val nx: Float,
    val ny: Float,
    val side: FootSide
) {
    companion object {
        /**
         * Sensores actuales: 2 en el pie derecho (Talón y Metatarsal).
         * El orden de esta lista debe coincidir con el orden de valores que
         * envía el ESP32 — el primer índice mapea al primer ADC, etc.
         *
         * Cuando agregues hardware al pie izquierdo, añade aquí las zonas con
         * `side = FootSide.LEFT` en el mismo orden en que aparecen en la trama.
         * Las coordenadas siguen siendo en orientación natural (pie derecho);
         * el espejo lo hace `FootprintHeatmap`.
         */
        val DEFAULT_ZONES: List<SensorZone> = listOf(
            // ----- Pie derecho -----
            SensorZone("Talón D",      "RS1", 0.50f, 0.85f, FootSide.RIGHT),
            SensorZone("Metatarsal D", "RS2", 0.50f, 0.30f, FootSide.RIGHT)

            // ----- Pie izquierdo (placeholder para cuando exista hardware) -----
            // SensorZone("Talón I",      "LS1", 0.50f, 0.85f, FootSide.LEFT),
            // SensorZone("Metatarsal I", "LS2", 0.50f, 0.30f, FootSide.LEFT),
            // ----- Futuras zonas asimétricas (8 sensores por pie) -----
            // SensorZone("Arco medial D",   "RS3", 0.40f, 0.60f, FootSide.RIGHT),
            // SensorZone("Arco lateral D",  "RS4", 0.62f, 0.62f, FootSide.RIGHT),
            // SensorZone("Hallux D",        "RS5", 0.38f, 0.10f, FootSide.RIGHT),
        )
    }
}
