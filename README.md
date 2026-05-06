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

- **Sensores resistivos de fuerza RFP-602 (rango 0 – 5 Kg)** distribuidos en posiciones anatómicas relevantes para la evaluación de pie plano. La configuración objetivo, por pie, es:
  - 1 en el talón
  - 1 en el arco medial (zona crítica para detectar caída del arco)
  - 1 en el arco lateral
  - 4 en cabezas metatarsales
  - 1 en el hallux
- Cada sensor se lee mediante un **divisor de tensión con resistencia fija de 10 kΩ**, convirtiendo la variación de resistencia del sensor en un voltaje proporcional a la fuerza aplicada.

### Procesamiento

- **Microcontrolador ESP32 DevKit V1** con doble núcleo a 240 MHz, WiFi y Bluetooth integrados. Encargado de leer las entradas analógicas y transmitir los datos al exterior.
- Se utilizan los pines del **bloque ADC1** (GPIO 32, 33, 34, 35, 36, 39) para garantizar lecturas estables cuando la radio Bluetooth está activa. Los pines de ADC2 comparten hardware con la radio y producen picos espurios de lectura.

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
- **Tara ("calibrar a 0") persistente por dispositivo**: SharedPreferences con clave por MAC, así varias plantillas pueden coexistir sin que la calibración de una contamine a la otra. Long-press para descartar.
- **Soporte de modo claro / oscuro** con paleta de colores y composición offscreen idénticas en ambos.
- **Panel de diagnóstico de stream BT** (long-press en el indicador de Hz): Δt instantáneo, media, σ, min/max, conteo de líneas y errores de parseo, última línea cruda recibida. Útil para detectar problemas de buffering o ruido en el ADC.

### Comunicación

La plantilla y la aplicación se comunican vía **Bluetooth Classic (SPP)** con un protocolo basado en líneas de texto:

```
1716,1820\n
1721,1817\n
...
```

Cada línea es una muestra ADC cruda separada por comas. La app la convierte a Kg usando la tara guardada para ese dispositivo más una pendiente fija (calibración de span). El protocolo es trivialmente depurable con cualquier terminal serial.

## Fundamento académico

El diseño se apoya en la metodología de [Hsu et al. 2018](https://www.mdpi.com/1424-8220/18/11/3617), que valida el uso de sensores resistivos de fuerza para el monitoreo de pie plano y establece criterios para la ubicación anatómica de los puntos de medición. El sistema implementa estas referencias adaptadas al contexto de fabricación local en Colombia.

## Estado del proyecto

**En desarrollo.** Prototipo funcional con **2 sensores en el pie derecho** (talón y metatarsal) y la app Android operativa.

### Implementado

- Adquisición y transmisión BT a 20 Hz desde el ESP32.
- App Android con conexión / reconexión automática y manejo de permisos en runtime.
- Modelo de zonas escalable a 8 sensores por pie (placeholders ya en código).
- Renderizado *reveal-mask* sobre imagen real de huella, marcadores tipo "diana" del tamaño aproximado del sensor real.
- Gráfica de fuerza vs. tiempo y total por pie en kilogramos.
- Tara persistente por MAC, con descarte por gesto.
- Tema claro / oscuro.
- Panel de diagnóstico para depurar buffering / jitter del stream BT.

### Próximos pasos

- Escalado a la matriz completa de 8 sensores por pie.
- Calibración formal con pesos de referencia y curva no lineal del FSR.
- Diseño de la plantilla física flexible (PCB / cableado plano).
- Registro de sesiones vinculado a un perfil de paciente.
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
│       │   ├── data/                   # Persistencia (tara + último device)
│       │   ├── model/                  # SensorZone, FootSide, SensorReading
│       │   ├── ui/                     # Composables (heatmap, chart, screens)
│       │   ├── util/                   # Calibración fuerza, paleta
│       │   └── viewmodel/
│       └── res/
│           ├── drawable-nodpi/         # foot_outline.png, foot_print.png
│           ├── values{,-night}/        # colores, temas, strings
│           └── ...
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

El firmware del ESP32 vive aparte; un sketch de Arduino mínimo lo encuentras en la sección de configuración más abajo.

## Configuración

### App Android

1. Clonar y abrir en **Android Studio** (Ladybug 2024.2 o superior recomendado).
2. Esperar Gradle sync. Min SDK 26 (Android 8.0+), target SDK 34. JDK 17.
3. Conectar un celular Android con depuración USB activada.
4. Ejecutar la configuración `app` (Run / Shift+F10).

Stack: AGP 8.5.2 · Kotlin 2.0.20 · Gradle 8.9 · Compose BOM 2024.09.02 · Material 3.

### Firmware ESP32 (referencia)

Sketch mínimo de Arduino para 2 sensores. Los pines son GPIO 32 y 33 (ADC1, sin conflicto con BT):

```cpp
#include "BluetoothSerial.h"

BluetoothSerial SerialBT;
const int NUM_SENSORES = 2;
const int PINES[NUM_SENSORES] = { 32, 33 };
const char* NOMBRE_BT = "Baropod_ESP32";
const int INTERVALO_MS = 50;  // 20 Hz
unsigned long ultimoEnvio = 0;

void setup() {
  Serial.begin(115200);
  SerialBT.begin(NOMBRE_BT);
  analogReadResolution(12);
}

int leerPromedio(int pin, int n) {
  long suma = 0;
  for (int i = 0; i < n; i++) { suma += analogRead(pin); delayMicroseconds(500); }
  return suma / n;
}

void loop() {
  if (millis() - ultimoEnvio < INTERVALO_MS) return;
  ultimoEnvio = millis();
  String msg = "";
  for (int s = 0; s < NUM_SENSORES; s++) {
    msg += String(leerPromedio(PINES[s], 10));
    if (s < NUM_SENSORES - 1) msg += ",";
  }
  msg += "\n";
  Serial.print(msg);
  if (SerialBT.hasClient()) SerialBT.print(msg);
}
```

Tras flashear, emparejar `Baropod_ESP32` desde los ajustes Bluetooth del celular y abrir la app: hará auto-conexión.

## Equipo

Proyecto académico interdisciplinario desarrollado por estudiantes de fisioterapia e ingeniería.

## Licencia

Distribuido bajo la licencia **MIT**. Ver el archivo [LICENSE](LICENSE) para los términos completos.
