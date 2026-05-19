/* ============================================================================
 * Baropod — Plantilla instrumentada para fisioterapia pediátrica de pie plano.
 * Firmware ESP32: 8 sensores FSR RFP-602 con divisor a 10 kΩ contra GND y
 * 3,3 V en el otro extremo. Envío por Bluetooth Classic (SPP) a la app
 * Android compañera.
 *
 *   Placa Arduino IDE : "DOIT ESP32 DEVKIT V1"
 *   Nombre BT         : "Plantilla_ESP32"
 *   Tasa de envío     : 20 Hz (1 trama cada 50 ms)
 *   Formato de trama  : "v1,v2,v3,v4,v5,v6,v7,v8\n"  (ADC crudo 0–4095, ASCII)
 *
 * Cableado (todos los pines en el costado izquierdo del ESP32):
 *
 *   Sensor  GPIO  Etiqueta  Zona anatómica
 *   ------  ----  --------  --------------
 *     S1     36     VP      Halux
 *     S2     39     VN      1er Metatarsiano
 *     S3     34     D34     3er Metatarsiano
 *     S4     35     D35     5to Metatarsiano
 *     S5     32     D32     Arco Medial
 *     S6     33     D33     Mediopie Lateral
 *     S7     25     D25     Talon Medial
 *     S8     26     D26     Talon Lateral
 *
 * NOTA TÉCNICA: GPIO25 y GPIO26 están en ADC2. Funcionan bien con Bluetooth
 * Classic activo, pero el bloque ADC2 queda inutilizable si se enciende WiFi.
 * No habilitar WiFi en este firmware.
 *
 * Fecha: 2026-05-18
 * ============================================================================
 */

#include "BluetoothSerial.h"

// ============================ Configuración ===============================

// Pines de los 8 sensores. El orden de este arreglo debe coincidir con el
// orden de las zonas en SensorZone.DEFAULT_ZONES del Android: la app asume
// que el i-ésimo valor de la trama corresponde a la i-ésima zona.
const int   PINS[8]  = {  36,   39,   34,   35,   32,   33,   25,   26  };
const char* NAMES[8] = { "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8" };
const char* ZONES[8] = {
  "Halux",             // S1
  "1er Metatarsiano",  // S2
  "3er Metatarsiano",  // S3
  "5to Metatarsiano",  // S4
  "Arco Medial",       // S5
  "Mediopie Lateral",  // S6
  "Talon Medial",      // S7
  "Talon Lateral"      // S8
};

// Constantes de calibración (informativas — el firmware NO las aplica; envía
// el ADC crudo y la app Android hace la conversión a porcentaje / Kg).
//
//   ADC_REST       — lectura típica sin presión, ya corregida por la deriva
//                    observada en los sensores.
//   ADC_MAX_FINGER — lectura aproximada al apretar con un dedo a fondo.
const int ADC_REST       = 1800;
const int ADC_MAX_FINGER = 2200;

// Nombre que ve el celular al emparejarse. NO cambiar: la app Android ya está
// emparejada con este nombre.
const char* BT_NAME = "Plantilla_ESP32";

// Pacing del bucle principal y promediado por sensor.
const unsigned long SEND_INTERVAL_MS  = 50;    // 20 Hz
const int           OVERSAMPLES       = 10;    // muestras por sensor
const int           OVERSAMPLE_DELAY_US = 500; // pausa entre muestras

// ============================== Estado ====================================

BluetoothSerial SerialBT;

// Timestamp (millis) del último envío. Se usa con un patrón no bloqueante
// para que el loop quede libre para futuras extensiones (filtros, sleep
// entre muestras, etc.) sin tener que romper el ritmo de 20 Hz.
unsigned long lastSend = 0;

// ============================== Helpers ===================================

// Lee `pin` OVERSAMPLES veces con pausa fija entre cada lectura y devuelve
// el promedio. Reduce el ruido del FSR sin agregar latencia perceptible
// (10 × 500 µs ≈ 5 ms por sensor; con 8 sensores < 50 ms del intervalo).
int readAveraged(int pin) {
  long acc = 0;
  for (int i = 0; i < OVERSAMPLES; i++) {
    acc += analogRead(pin);
    delayMicroseconds(OVERSAMPLE_DELAY_US);
  }
  return (int)(acc / OVERSAMPLES);
}

// Construye la trama ASCII y la envía por BT (si hay cliente) y por USB Serial.
// La copia por USB es útil para depurar sin teléfono.
void sendSample(const int values[8]) {
  char buf[80];
  int n = snprintf(
    buf, sizeof(buf),
    "%d,%d,%d,%d,%d,%d,%d,%d\n",
    values[0], values[1], values[2], values[3],
    values[4], values[5], values[6], values[7]
  );
  if (n <= 0) return;

  // USB siempre (ayuda durante pruebas en mesa).
  Serial.write((const uint8_t*)buf, n);

  // BT sólo si hay cliente: escribir sin cliente puede bloquear el stack.
  if (SerialBT.hasClient()) {
    SerialBT.write((const uint8_t*)buf, n);
  }
}

// ============================== Setup =====================================

void setup() {
  Serial.begin(115200);

  // ADC: 12 bits (0–4095) y atenuación de 11 dB en los 8 pines para que el
  // rango de entrada llegue a ~3,3 V (compatible con el divisor del FSR).
  analogReadResolution(12);
  for (int i = 0; i < 8; i++) {
    analogSetPinAttenuation(PINS[i], ADC_11db);
  }

  SerialBT.begin(BT_NAME);

  Serial.println();
  Serial.println(F("============================================="));
  Serial.println(F(" Baropod firmware ESP32  -  8 sensores FSR"));
  Serial.println(F("============================================="));
  Serial.print(F("Nombre Bluetooth: "));
  Serial.println(BT_NAME);
  Serial.print(F("Tasa de envio:    "));
  Serial.print(1000UL / SEND_INTERVAL_MS);
  Serial.println(F(" Hz"));
  Serial.println(F("Pines configurados:"));
  for (int i = 0; i < 8; i++) {
    Serial.print(F("  "));
    Serial.print(NAMES[i]);
    Serial.print(F("  GPIO"));
    if (PINS[i] < 10) Serial.print(' ');
    Serial.print(PINS[i]);
    Serial.print(F("   "));
    Serial.println(ZONES[i]);
  }
  Serial.println(F("---------------------------------------------"));
  Serial.println(F("Listo para emparejar. Esperando cliente BT..."));
}

// =============================== Loop =====================================

void loop() {
  // Pacing no bloqueante con millis(). Si aún no toca enviar, salimos del
  // tick para no monopolizar la CPU (en el futuro pueden agregarse aquí
  // tareas ligeras, p. ej. filtrado de los buffers).
  const unsigned long now = millis();
  if (now - lastSend < SEND_INTERVAL_MS) return;
  lastSend = now;

  // Lectura promediada de los 8 canales.
  int values[8];
  for (int i = 0; i < 8; i++) {
    values[i] = readAveraged(PINS[i]);
  }

  sendSample(values);
}
