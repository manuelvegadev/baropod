package com.example.baropod.data

import android.content.Context
import android.content.SharedPreferences

internal const val BAROPOD_PREFS = "baropod_prefs"

internal fun Context.baropodPrefs(): SharedPreferences =
    getSharedPreferences(BAROPOD_PREFS, Context.MODE_PRIVATE)

/**
 * `getFloat` lanza `ClassCastException` cuando la entrada existe pero está
 * guardada como otro tipo. Esto puede pasar tras una actualización con
 * cambio de esquema o un archivo de Prefs corrupto.
 */
internal fun SharedPreferences.getFloatSafe(key: String, fallback: Float): Float = try {
    getFloat(key, fallback)
} catch (e: ClassCastException) {
    fallback
}

internal fun SharedPreferences.getNullableInt(key: String, sentinel: Int = -1): Int? =
    getInt(key, sentinel).takeIf { it != sentinel }

internal fun SharedPreferences.getNullableFloat(key: String): Float? =
    getFloatSafe(key, Float.NaN).takeUnless { it.isNaN() }
