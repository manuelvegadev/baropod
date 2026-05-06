package com.example.baropod.model

/**
 * Lado anatómico al que pertenece un sensor / huella.
 *
 * Convención de coordenadas en este proyecto: las posiciones normalizadas
 * (`nx`, `ny`) en [SensorZone] se definen siempre en el sistema de referencia
 * del pie derecho ("orientación natural" de las imágenes `foot_outline.png` y
 * `foot_print.png`). Cuando se renderiza el pie izquierdo, el composable
 * [com.example.baropod.ui.FootprintHeatmap] aplica un espejo horizontal que
 * cubre tanto la imagen como las posiciones de los sensores.
 */
enum class FootSide { LEFT, RIGHT }
