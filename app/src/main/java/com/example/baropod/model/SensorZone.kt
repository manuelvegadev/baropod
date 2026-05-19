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
 * @param inputIndex Índice (0-based) del valor en la trama del ESP32 que
 *             alimenta esta zona. Por defecto = la posición de la zona en
 *             [DEFAULT_ZONES], pero el usuario puede reasignarlo desde la
 *             pantalla de Calibración cuando el cableado físico no coincide
 *             con la asignación nominal.
 */
data class SensorZone(
    val name: String,
    val shortLabel: String,
    val nx: Float,
    val ny: Float,
    val side: FootSide,
    val inputIndex: Int
) {
    companion object {
        /**
         * Sensores actuales: 8 en el pie derecho. El orden de esta lista define
         * el orden de presentación de las zonas en la UI; el campo `inputIndex`
         * controla con qué valor de la trama se alimenta cada zona.
         *
         * Mapeo por defecto (zona → entrada de trama → GPIO físico):
         *   S1 Halux            → input 1 → GPIO36 (VP)
         *   S2 1er Metatarsiano → input 2 → GPIO39 (VN)
         *   S3 3er Metatarsiano → input 5 → GPIO32 (D32)
         *   S4 5to Metatarsiano → input 4 → GPIO35 (D35)
         *   S5 Arco Medial      → input 6 → GPIO33 (D33)
         *   S6 Mediopié Lateral → input 3 → GPIO34 (D34)
         *   S7 Talón Medial     → input 8 → GPIO26 (D26)
         *   S8 Talón Lateral    → input 7 → GPIO25 (D25)
         *
         * Esta permutación (no identidad) refleja el cableado físico real de
         * la plantilla. Si más adelante el cableado cambia, no hace falta
         * tocar este archivo: la pantalla de Calibración permite reasignar
         * `inputIndex` y persiste el cambio.
         *
         * Convención de coordenadas (silueta del pie derecho, sole vista desde
         * abajo): x=0 = lado medial (interior, hacia el dedo gordo); x=1 =
         * lado lateral (exterior, hacia el meñique); y=0 = punta de los dedos;
         * y=1 = base del talón.
         */
        val DEFAULT_ZONES: List<SensorZone> = listOf(
            SensorZone("Halux",             "S1", 0.38f, 0.10f, FootSide.RIGHT, 0),
            SensorZone("1er Metatarsiano",  "S2", 0.40f, 0.32f, FootSide.RIGHT, 1),
            SensorZone("3er Metatarsiano",  "S3", 0.50f, 0.30f, FootSide.RIGHT, 4),
            SensorZone("5to Metatarsiano",  "S4", 0.62f, 0.34f, FootSide.RIGHT, 3),
            SensorZone("Arco Medial",       "S5", 0.40f, 0.60f, FootSide.RIGHT, 5),
            SensorZone("Mediopié Lateral",  "S6", 0.62f, 0.62f, FootSide.RIGHT, 2),
            SensorZone("Talón Medial",      "S7", 0.45f, 0.85f, FootSide.RIGHT, 7),
            SensorZone("Talón Lateral",     "S8", 0.58f, 0.85f, FootSide.RIGHT, 6),
        )
    }
}
