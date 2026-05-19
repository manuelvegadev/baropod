package com.example.baropod.data

import android.content.Context
import android.net.Uri
import com.example.baropod.model.PatientData
import java.io.File
import java.util.UUID

/**
 * Persistencia del paciente activo: campos clínicos en `SharedPreferences`,
 * fotos como archivos en `filesDir/patient_photos/`. La URI elegida en el
 * Photo Picker se copia a almacenamiento interno para que la foto sobreviva
 * a borrados externos y la app no requiera permisos persistentes.
 */
class PatientStorage(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.baropodPrefs()

    val photosDir: File by lazy {
        File(appContext.filesDir, PHOTOS_SUBDIR).also { it.mkdirs() }
    }

    fun load(): PatientData = PatientData(
        name = prefs.getString(KEY_NAME, "") ?: "",
        ageYears = prefs.getNullableInt(KEY_AGE),
        heightCm = prefs.getNullableFloat(KEY_HEIGHT_CM),
        weightKg = prefs.getNullableFloat(KEY_WEIGHT_KG),
        photoFileNames = prefs.getString(KEY_PHOTOS, "")
            ?.split('|')?.filter { it.isNotBlank() }
            ?: emptyList()
    )

    fun save(data: PatientData) {
        prefs.edit()
            .putString(KEY_NAME, data.name)
            .putInt(KEY_AGE, data.ageYears ?: -1)
            .putFloat(KEY_HEIGHT_CM, data.heightCm ?: Float.NaN)
            .putFloat(KEY_WEIGHT_KG, data.weightKg ?: Float.NaN)
            .putString(KEY_PHOTOS, data.photoFileNames.joinToString("|"))
            .apply()
    }

    /**
     * Copia el contenido apuntado por [sourceUri] a un archivo nuevo en
     * [photosDir] y devuelve el nombre del archivo. Lanza si el
     * `ContentResolver` no puede abrir la URI.
     */
    fun importPhoto(sourceUri: Uri): String {
        val fileName = "patient_${UUID.randomUUID()}.jpg"
        val dest = File(photosDir, fileName)
        appContext.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "No se pudo abrir la URI: $sourceUri" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return fileName
    }

    fun deletePhotoFile(fileName: String) {
        File(photosDir, fileName).delete()
    }

    private companion object {
        const val KEY_NAME = "patient_name"
        const val KEY_AGE = "patient_age_years"
        const val KEY_HEIGHT_CM = "patient_height_cm"
        const val KEY_WEIGHT_KG = "patient_weight_kg"
        const val KEY_PHOTOS = "patient_photo_files"
        const val PHOTOS_SUBDIR = "patient_photos"
    }
}
