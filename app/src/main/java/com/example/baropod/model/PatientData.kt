package com.example.baropod.model

/**
 * Datos clínicos del paciente activo. La app soporta un único paciente a la
 * vez (el modelo se simplifica deliberadamente para la fase de prototipo);
 * cuando haga falta soportar varios, este data class pasará a ser una
 * entrada en una lista persistida.
 *
 * Los campos numéricos son `null` cuando aún no se han ingresado, en vez de
 * usar centinelas (-1, 0) que se confunden con valores válidos para algunos
 * rangos pediátricos.
 *
 * @param photoFileNames nombres de archivo (sólo el `name`, sin path) de las
 *        fotos copiadas a `filesDir/patient_photos/`. La resolución a `File`
 *        se hace en el lado UI para no acoplar este modelo al `Context`.
 */
data class PatientData(
    val name: String = "",
    val ageYears: Int? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val photoFileNames: List<String> = emptyList()
) {
    /**
     * IMC en kg/m². Devuelve `null` si falta alguno de los dos datos o la
     * altura es inválida; el lado UI lo muestra como "—" en ese caso.
     *
     * Nota clínica: en pacientes pediátricos el IMC absoluto se interpreta
     * vía percentiles (CDC/WHO) según edad y sexo. Esta app sólo expone el
     * número crudo y deja la interpretación al profesional.
     */
    val bmi: Float?
        get() {
            val h = heightCm ?: return null
            val w = weightKg ?: return null
            if (h <= 0f) return null
            val hm = h / 100f
            return w / (hm * hm)
        }

    companion object {
        val EMPTY = PatientData()
    }
}
