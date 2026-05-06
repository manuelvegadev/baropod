package com.example.baropod.ui.theme

import androidx.compose.ui.graphics.Color

// ---------- Light tokens ----------

val LightBackground = Color(0xFFF5F2ED)
val LightSurface = Color(0xFFFFFFFF)
val LightCardBackground = Color(0xFFFFFFFF)
val LightSelectedCardBackground = Color(0xFFE9E2D2)
val LightInfoCardBackground = Color(0xFFFFF6E6)

val LightPrimary = Color(0xFF5D4F3A)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightSecondary = Color(0xFFA0876A)

val LightPrimaryText = Color(0xFF2C2C2A)
val LightStrongText = Color(0xFF333333)
val LightSecondaryText = Color(0xFF666666)
val LightTertiaryText = Color(0xFF888888)
val LightDivider = Color(0xFFDDDDDD)

val LightFootFill = Color(0xFFF5DCC4)
val LightFootStroke = Color(0xFF5D4F3A)
val LightFootOutline = Color(0xFF3A2F22)
val LightFootPrint = Color(0xFF1F140A)
val LightSensorMarker = Color(0xFF2C2C2A)
val LightDashedConnector = Color(0xFF888888)

// ---------- Dark tokens ----------

val DarkBackground = Color(0xFF141414)
val DarkSurface = Color(0xFF1E1E1E)
val DarkCardBackground = Color(0xFF252525)
val DarkSelectedCardBackground = Color(0xFF3A3528)
val DarkInfoCardBackground = Color(0xFF332B1A)

val DarkPrimary = Color(0xFFD4BC97)
val DarkOnPrimary = Color(0xFF2C2418)
val DarkSecondary = Color(0xFFCDB594)

val DarkPrimaryText = Color(0xFFEDEDED)
val DarkStrongText = Color(0xFFEDEDED)
val DarkSecondaryText = Color(0xFFB0B0B0)
val DarkTertiaryText = Color(0xFF8A8A8A)
val DarkDivider = Color(0xFF333333)

// La silueta del pie en dark se atenúa para no encandilar y mantener
// contraste con los blobs de calor (que siguen siendo brillantes).
val DarkFootFill = Color(0xFF7A6750)
val DarkFootStroke = Color(0xFFCDB594)
val DarkFootOutline = Color(0xFFD7C4A4)
val DarkFootPrint = Color(0xFFE8D5B5)
val DarkSensorMarker = Color(0xFFEDEDED)
val DarkDashedConnector = Color(0xFF8A8A8A)

// ---------- Estados (semánticos, comparten valor en ambos temas) ----------

val StatusOk = Color(0xFF2E8B57)
val StatusWarn = Color(0xFFE0A030)
val StatusError = Color(0xFFA32D2D)

// ---------- Paleta de trazas para la gráfica vs tiempo ----------
// 8 hues bien diferenciados. La variante light usa tonos saturados (Tailwind 600);
// la variante dark usa tonos un poco más claros (Tailwind 400) para resaltar
// sobre el fondo oscuro.

val LightSensorTraces: List<Color> = listOf(
    Color(0xFF2563EB), // azul
    Color(0xFFEA580C), // naranja
    Color(0xFF16A34A), // verde
    Color(0xFFDB2777), // rosa
    Color(0xFF9333EA), // morado
    Color(0xFF0891B2), // cian
    Color(0xFFCA8A04), // amarillo
    Color(0xFFDC2626)  // rojo
)

val DarkSensorTraces: List<Color> = listOf(
    Color(0xFF60A5FA), // azul
    Color(0xFFFB923C), // naranja
    Color(0xFF4ADE80), // verde
    Color(0xFFF472B6), // rosa
    Color(0xFFC084FC), // morado
    Color(0xFF22D3EE), // cian
    Color(0xFFFACC15), // amarillo
    Color(0xFFF87171)  // rojo
)
