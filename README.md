# Baropod — Plantilla instrumentada para monitoreo de pie plano

Sistema portátil de medición de presión plantar diseñado para acompañar el tratamiento fisioterapéutico de pie plano en pacientes pediátricos. La plantilla, equipada con sensores de fuerza distribuidos en zonas anatómicas clave, permite visualizar en tiempo real cómo se reparte el peso corporal a lo largo del pie durante el apoyo estático y la marcha.

<p align="center">
  <img width="2505" height="2116" alt="Screenshot_20260506_014646_Pie Plano-left" src="https://github.com/user-attachments/assets/c6a0ebd7-5c69-4ddd-89c3-72a3ad4ca4f5" />
</p>

## Objetivo

Desarrollar una herramienta accesible y de bajo costo que permita a los profesionales en fisioterapia obtener mediciones cuantitativas y reproducibles de la distribución de presión plantar en niños con pie plano, con el fin de:

- **Apoyar el diagnóstico** mediante datos objetivos sobre la distribución de carga, complementando la valoración clínica tradicional.
- **Documentar la progresión del tratamiento** sesión a sesión, permitiendo evaluar la efectividad de las rutinas terapéuticas.
- **Mejorar la adherencia del paciente** al ofrecer retroalimentación visual inmediata, comprensible para niños y sus familias.
- **Democratizar el acceso a la baropodometría** en centros de fisioterapia que no pueden adquirir equipos comerciales, cuyos costos superan ampliamente la inversión necesaria para este sistema.

A largo plazo, el proyecto aspira a generar un dataset de mediciones longitudinales que permita estudiar la evolución del pie plano bajo distintas intervenciones terapéuticas.

## Contexto clínico

El pie plano (*pes planus*) es una condición frecuente en la población pediátrica que se caracteriza por la disminución del arco longitudinal medial. Su tratamiento conservador depende de ejercicios fisioterapéuticos cuya efectividad se evalúa, en la práctica clínica colombiana, mediante observación cualitativa o pruebas estáticas como la huella plantar en papel. Los baropodómetros profesionales que ofrecen mediciones cuantitativas tienen costos prohibitivos y muchos no permiten registrar la marcha en condiciones funcionales reales.

## Qué hace

- Mide la fuerza ejercida en distintos puntos de la planta del pie mediante sensores resistivos.
- Transmite las lecturas de forma inalámbrica a un dispositivo Android.
- **Revela una huella plantar** sobre un croquis del pie en tiempo real: a mayor presión, más se descubre la zona correspondiente de la huella.
- Grafica la **fuerza en kilogramos por sensor** a lo largo del tiempo, con suma total por pie.
- Persiste la calibración del cero (tara) entre sesiones y se reconecta automáticamente al último dispositivo usado.

## Hardware

El sistema físico consiste en una plantilla flexible que se inserta en el calzado del paciente, a la cual van adheridos los sensores y el módulo de adquisición.

### Sensado

**8 sensores resistivos de fuerza RFP-602 (rango 0 – 5 Kg)** distribuidos en zonas anatómicas relevantes para la evaluación de pie plano:

| Sensor | GPIO | Zona anatómica |
|---|---|---|
| S1 | 36 (VP) | Halux |
| S2 | 39 (VN) | 1er Metatarsiano |
| S3 | 34 (D34) | 3er Metatarsiano |
| S4 | 35 (D35) | 5to Metatarsiano |
| S5 | 32 (D32) | Arco Medial |
| S6 | 33 (D33) | Mediopié Lateral |
| S7 | 25 (D25) | Talón Medial |
| S8 | 26 (D26) | Talón Lateral |

Cada sensor se lee mediante un **divisor de tensión con resistencia fija de 10 kΩ** a GND, con 3.3 V en el otro extremo. La variación de resistencia del sensor produce un voltaje proporcional a la fuerza aplicada.

> El cableado físico es reasignable desde la pantalla de **Calibración** de la app: si una zona quedó conectada al GPIO equivocado, se intercambia el `inputIndex` sin re‑flashear el firmware.

### Procesamiento

- **Microcontrolador ESP32 DevKit V1** con doble núcleo a 240 MHz, WiFi y Bluetooth integrados. Encargado de leer las entradas analógicas y transmitir los datos al exterior.
- Se usan **6 pines de ADC1** (GPIO 32–36, 39) y **2 de ADC2** (GPIO 25, 26). ADC2 funciona con Bluetooth Classic activo pero queda inutilizable si se enciende WiFi, así que el firmware no habilita WiFi.

### Alimentación

- **Batería LiPo recargable de 2000 mAh, 3.7 V**, con módulo de carga USB integrado.
- **Convertidor elevador MT3608** que suministra al ESP32 los 5 V requeridos.
- Autonomía estimada de varias horas de uso continuo, suficiente para múltiples sesiones de fisioterapia entre cargas.

### Forma física

El conjunto está diseñado para ser **no intrusivo**: la electrónica se aloja en una carcasa compacta sujeta al tobillo o integrada en la lengüeta del calzado, mientras que solo los sensores y los cables planos quedan dentro de la plantilla. El paciente camina con normalidad sin sentir el equipamiento.

## Software

El sistema cuenta con dos componentes de software que trabajan en conjunto.

### Firmware embebido (ESP32)

Programado en C++ sobre el framework Arduino para ESP32. Responsabilidades:

- **Adquisición** de las señales analógicas a una frecuencia mínima de 20 muestras por segundo, suficiente para capturar las fases del ciclo de marcha.
- **Filtrado básico** de las lecturas (promedio de N muestras consecutivas) para reducir el ruido eléctrico sin introducir latencia significativa.
- **Calibración individual** de cada sensor (en proceso): tabla ADC → fuerza calculada con pesos conocidos para compensar la variabilidad de fabricación entre unidades.
- **Transmisión inalámbrica** de los datos vía Bluetooth Classic SPP bajo un protocolo de texto plano, ligero y depurable directamente con cualquier terminal serial.

### Aplicación móvil (Android)

Aplicación nativa desarrollada en **Kotlin** con **Jetpack Compose**, **Material 3** y arquitectura **MVVM** (`StateFlow`, coroutines, `ViewModel`).

Funcionalidades implementadas:

- **Conexión Bluetooth** clásica (SPP) con auto-reconexión al último dispositivo emparejado al abrir la app.
- **Visualización tipo *reveal-mask*** sobre la silueta del pie: el croquis siempre está visible y, conforme aumenta la presión sobre cada sensor, se "estampa" progresivamente una huella plantar real (técnica de offscreen layer + `BlendMode.DstIn`).
- **Soporte para ambos pies**: el pie izquierdo se renderiza espejando la misma imagen y aplicando el mismo sistema de zonas. El layout lateral muestra el total en Kg por pie con color por intensidad.
- **Gráfica de fuerza vs. tiempo** (10 segundos rolling, eje Y 0 – 5 Kg) con una traza por sensor en colores distinguibles, etiqueta del valor actual y línea de referencia en el fondo de escala.
- **Pantalla de Calibración**: arrastra los 8 dots sobre la silueta para reposicionarlos y reasigna a qué entrada de la trama (1–8) alimenta cada zona mediante un menú. La reasignación intercambia, así nunca queda una entrada huérfana. Los cambios se persisten al instante.
- **Pantalla de Datos del Paciente**: nombre, edad, altura, peso, **IMC calculado**, y galería de fotos importadas desde el Photo Picker del sistema (Android 13+ sin permisos extra). Auto‑guardado en cada edición; las fotos se copian a almacenamiento interno para sobrevivir a borrados externos.
- **Tara ("calibrar a 0") persistente por dispositivo**: SharedPreferences con clave por MAC, así varias plantillas pueden coexistir sin que la calibración de una contamine a la otra. Long-press para descartar.
- **Modo desarrollo** (sólo builds debug): atajo en la pantalla de conexión que arranca la visualización con 8 canales sintéticos a 20 Hz, útil para iterar sobre la UI sin el ESP32.
- **Soporte de modo claro / oscuro** con paleta de colores y composición offscreen idénticas en ambos.
- **Panel de diagnóstico de stream BT** (long-press en el indicador de Hz): Δt instantáneo, media, σ, min/max, conteo de líneas y errores de parseo, última línea cruda recibida. Útil para detectar problemas de buffering o ruido en el ADC.

### Comunicación

La plantilla y la aplicación se comunican vía **Bluetooth Classic (SPP)** con un protocolo basado en líneas de texto. Una muestra es una trama ASCII con 8 enteros ADC (0–4095) separados por coma, terminada en `\n`:

```
1820,1750,1900,1830,2100,2250,1780,1820\n
1818,1751,1899,1828,2103,2247,1782,1820\n
...
```

El parser de la app es **tolerante al tamaño** (la trama puede crecer a 16 cuando ambos pies estén instrumentados) y descarta líneas malformadas en silencio. La conversión a Kg usa la tara guardada para ese dispositivo más la pendiente fija de calibración. El protocolo es trivialmente depurable con cualquier terminal serial.

## Fundamento académico

El diseño se apoya en la metodología de [Hsu et al. 2018](https://www.mdpi.com/1424-8220/18/11/3617), que valida el uso de sensores resistivos de fuerza para el monitoreo de pie plano y establece criterios para la ubicación anatómica de los puntos de medición. El sistema implementa estas referencias adaptadas al contexto de fabricación local en Colombia.

## Estado del proyecto

**En desarrollo.** Prototipo funcional con los **8 sensores del pie derecho** cableados y la app Android operativa de extremo a extremo.

### Implementado

- Firmware ESP32 con 8 canales analógicos a 20 Hz, promediado por sensor y transmisión BT Classic SPP. Sketch en [`firmware/baropod_8sensores/`](firmware/baropod_8sensores/baropod_8sensores.ino).
- App Android (Kotlin + Compose, MVVM) con conexión / reconexión automática y manejo de permisos en runtime.
- Renderizado *reveal-mask* sobre imagen real de huella, marcadores tipo "diana" del tamaño aproximado del sensor real.
- Gráfica de fuerza vs. tiempo y total por pie en kilogramos.
- Pantalla de Calibración: drag & drop de zonas, reasignación de `inputIndex` por permutación.
- Pantalla de Datos del Paciente con IMC calculado y galería de fotos.
- Tara persistente por MAC, con descarte por long-press.
- Modo desarrollo (builds debug) con stream sintético de 8 canales.
- Tema claro / oscuro.
- Panel de diagnóstico para depurar buffering / jitter del stream BT.

### Próximos pasos

- Instrumentar el pie izquierdo (escalar la trama a 16 valores; la app ya es trama‑tamaño agnóstica).
- Calibración formal con pesos de referencia y curva no lineal del FSR.
- Diseño de la plantilla física flexible (PCB / cableado plano).
- Registro de sesiones vinculado al perfil de paciente persistido.
- Exportación a CSV para análisis externo.
- Detección automática del ciclo de marcha y métricas (tiempo de apoyo, distribución medial / lateral, simetría L-R).

## Estructura del repositorio

```
baropod/
├── app/                                # App Android
│   └── src/main/
│       ├── java/com/example/baropod/
│       │   ├── MainActivity.kt
│       │   ├── bluetooth/              # BluetoothManager, parser
│       │   ├── data/                   # Persistencia: tara, último device,
│       │   │                           #   zonas calibradas, paciente
│       │   ├── model/                  # SensorZone, FootSide, SensorReading,
│       │   │                           #   PatientData
│       │   ├── ui/                     # Composables: Connection, Visualization,
│       │   │                           #   Settings (calibración), Patient,
│       │   │                           #   FootprintHeatmap, PressureChart
│       │   ├── util/                   # Calibración fuerza, paleta
│       │   └── viewmodel/
│       └── res/
│           ├── drawable-nodpi/         # foot_outline.png, foot_print.png
│           ├── drawable-xxxhdpi/       # ic_launcher_foreground (emoji 🦶)
│           ├── values{,-night}/        # colores, temas, strings
│           └── ...
├── firmware/
│   └── baropod_8sensores/              # Sketch Arduino para ESP32 (8 sensores)
│       └── baropod_8sensores.ino
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Configuración

### App Android

1. Clonar y abrir en **Android Studio** (Ladybug 2024.2 o superior recomendado).
2. Esperar Gradle sync. Min SDK 26 (Android 8.0+), target SDK 34. JDK 17.
3. Conectar un celular Android con depuración USB activada.
4. Ejecutar la configuración `app` (Run / Shift+F10).

Stack: AGP 8.5.2 · Kotlin 2.0.20 · Gradle 8.9 · Compose BOM 2024.09.02 · Material 3.

### Firmware ESP32

El sketch listo para flashear vive en [`firmware/baropod_8sensores/baropod_8sensores.ino`](firmware/baropod_8sensores/baropod_8sensores.ino).

1. Abrir el archivo en **Arduino IDE** con el core *esp32* instalado.
2. Seleccionar la placa **DOIT ESP32 DEVKIT V1** y el puerto serial.
3. Subir. No requiere librerías externas más allá de `BluetoothSerial.h` (incluida en el core).
4. Emparejar **`Plantilla_ESP32`** desde los ajustes Bluetooth del celular.
5. Abrir la app: hará auto-conexión al último dispositivo emparejado en sesiones futuras.

## Equipo

Proyecto académico interdisciplinario desarrollado por estudiantes de fisioterapia e ingeniería.

## Licencia

Distribuido bajo la licencia **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)**. Resumen:

- **Atribución (BY)**: cualquier uso debe acreditar a los autores originales.
- **No comercial (NC)**: no se permite usar el material con fines comerciales.
- **Compartir igual (SA)**: las obras derivadas deben distribuirse bajo la misma licencia.

Ver el archivo [LICENSE](LICENSE) para los términos completos o el [resumen oficial en español](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.es).
