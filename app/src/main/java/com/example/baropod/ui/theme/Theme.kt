package com.example.baropod.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta extendida del dominio (más allá de la `ColorScheme` de Material 3).
 * Incluye colores específicos de la huella, las tarjetas y los estados.
 *
 * Las variantes light/dark se resuelven en [BaropodTheme] y se exponen vía
 * [LocalAppColors]. Acceder con `MaterialTheme.appColors`.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val cardBackground: Color,
    val selectedCardBackground: Color,
    val infoCardBackground: Color,
    val primaryText: Color,
    val strongText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val divider: Color,
    val footFill: Color,
    val footStroke: Color,
    val footOutline: Color,
    val footPrint: Color,
    val sensorMarker: Color,
    val dashedConnector: Color,
    val statusOk: Color,
    val statusWarn: Color,
    val statusError: Color,
    val sensorTraces: List<Color>
)

private val LightAppColors = AppColors(
    isDark = false,
    background = LightBackground,
    surface = LightSurface,
    cardBackground = LightCardBackground,
    selectedCardBackground = LightSelectedCardBackground,
    infoCardBackground = LightInfoCardBackground,
    primaryText = LightPrimaryText,
    strongText = LightStrongText,
    secondaryText = LightSecondaryText,
    tertiaryText = LightTertiaryText,
    divider = LightDivider,
    footFill = LightFootFill,
    footStroke = LightFootStroke,
    footOutline = LightFootOutline,
    footPrint = LightFootPrint,
    sensorMarker = LightSensorMarker,
    dashedConnector = LightDashedConnector,
    statusOk = StatusOk,
    statusWarn = StatusWarn,
    statusError = StatusError,
    sensorTraces = LightSensorTraces
)

private val DarkAppColors = AppColors(
    isDark = true,
    background = DarkBackground,
    surface = DarkSurface,
    cardBackground = DarkCardBackground,
    selectedCardBackground = DarkSelectedCardBackground,
    infoCardBackground = DarkInfoCardBackground,
    primaryText = DarkPrimaryText,
    strongText = DarkStrongText,
    secondaryText = DarkSecondaryText,
    tertiaryText = DarkTertiaryText,
    divider = DarkDivider,
    footFill = DarkFootFill,
    footStroke = DarkFootStroke,
    footOutline = DarkFootOutline,
    footPrint = DarkFootPrint,
    sensorMarker = DarkSensorMarker,
    dashedConnector = DarkDashedConnector,
    statusOk = StatusOk,
    statusWarn = StatusWarn,
    statusError = StatusError,
    sensorTraces = DarkSensorTraces
)

private val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("AppColors no provistos. Envuelve la UI con BaropodTheme.")
}

/** Acceso ergonómico: `MaterialTheme.appColors`. */
val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    onBackground = LightPrimaryText,
    surface = LightSurface,
    onSurface = LightPrimaryText,
    surfaceVariant = LightSelectedCardBackground,
    onSurfaceVariant = LightSecondaryText
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSelectedCardBackground,
    onSurfaceVariant = DarkSecondaryText
)

@Composable
fun BaropodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}
