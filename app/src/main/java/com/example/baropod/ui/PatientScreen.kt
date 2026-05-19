package com.example.baropod.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baropod.model.PatientData
import com.example.baropod.ui.theme.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pantalla "Datos del paciente": formulario clínico + galería de fotos
 * importadas desde el Photo Picker. Las ediciones se persisten en cada
 * cambio (sin botón Guardar) para que volver atrás no pierda nada.
 */
@Composable
fun PatientScreen(
    patient: PatientData,
    photosDir: File,
    onUpdateName: (String) -> Unit,
    onUpdateAge: (Int?) -> Unit,
    onUpdateHeight: (Float?) -> Unit,
    onUpdateWeight: (Float?) -> Unit,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.appColors

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAddPhoto(uri)
    }

    var photoToDelete by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Volver",
                    tint = colors.primaryText
                )
            }
            Text(
                text = "Datos del paciente",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = patient.name,
                onValueChange = onUpdateName,
                label = { Text("Nombre") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            IntPatientField(
                value = patient.ageYears,
                onValueChange = onUpdateAge,
                label = "Edad (años)"
            )
            Spacer(Modifier.height(8.dp))

            FloatPatientField(
                value = patient.heightCm,
                onValueChange = onUpdateHeight,
                label = "Altura (cm)"
            )
            Spacer(Modifier.height(8.dp))

            FloatPatientField(
                value = patient.weightKg,
                onValueChange = onUpdateWeight,
                label = "Peso (kg)"
            )
            Spacer(Modifier.height(16.dp))

            BmiCard(bmi = patient.bmi)
            Spacer(Modifier.height(20.dp))

            Text(
                text = "Fotos",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText
            )
            Spacer(Modifier.height(8.dp))

            PhotoGrid(
                fileNames = patient.photoFileNames,
                photosDir = photosDir,
                onAdd = {
                    pickPhotoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onLongPress = { photoToDelete = it }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Mantén pulsada una foto para eliminarla.",
                fontSize = 11.sp,
                color = colors.secondaryText
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    photoToDelete?.let { fileName ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Eliminar foto") },
            text = { Text("¿Eliminar esta foto del paciente? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemovePhoto(fileName)
                    photoToDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Campo Float que NO descarta input mid-edición. Mantenemos el `raw` local
 * y sólo reconciliamos cuando el valor upstream representa un número
 * distinto al que el usuario tiene escrito. Así "12." (mid-typing) no se
 * trunca a "12" cuando el VM round-trip emite 12.0f → trimFloat → "12".
 */
@Composable
private fun FloatPatientField(
    value: Float?,
    onValueChange: (Float?) -> Unit,
    label: String
) {
    var raw by remember { mutableStateOf(value?.let(::trimFloat).orEmpty()) }
    LaunchedEffect(value) {
        if (raw.trim().toFloatOrNull() != value) {
            raw = value?.let(::trimFloat).orEmpty()
        }
    }
    OutlinedTextField(
        value = raw,
        onValueChange = { new ->
            raw = new
            val trimmed = new.trim()
            if (trimmed.isEmpty()) onValueChange(null)
            else trimmed.toFloatOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun IntPatientField(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    label: String
) {
    var raw by remember { mutableStateOf(value?.toString().orEmpty()) }
    LaunchedEffect(value) {
        if (raw.trim().toIntOrNull() != value) {
            raw = value?.toString().orEmpty()
        }
    }
    OutlinedTextField(
        value = raw,
        onValueChange = { new ->
            raw = new
            val trimmed = new.trim()
            if (trimmed.isEmpty()) onValueChange(null)
            else trimmed.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BmiCard(bmi: Float?) {
    val colors = MaterialTheme.appColors
    val bmiText = if (bmi != null) String.format("%.1f", bmi) else "—"
    val subtitle = if (bmi != null) "kg/m²" else "Ingresa altura y peso"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "IMC",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.secondaryText
            )
            Text(
                text = "Calculado de altura y peso",
                fontSize = 11.sp,
                color = colors.tertiaryText
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = bmiText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.tertiaryText
            )
        }
    }
}

/**
 * Grid de 3 columnas hecho con Column + Row.chunked(3): evita embeber un
 * `LazyVerticalGrid` dentro del `verticalScroll` exterior (constraint
 * conflicts). Para ≤ docenas de fotos la diferencia de costo es nula.
 */
@Composable
private fun PhotoGrid(
    fileNames: List<String>,
    photosDir: File,
    onAdd: () -> Unit,
    onLongPress: (String) -> Unit
) {
    val cells: List<PhotoCellModel> = buildList {
        add(PhotoCellModel.Add)
        fileNames.forEach { add(PhotoCellModel.Photo(it)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    val cellModifier = Modifier.weight(1f)
                    when (cell) {
                        is PhotoCellModel.Add -> AddPhotoTile(
                            onAdd = onAdd,
                            modifier = cellModifier
                        )
                        is PhotoCellModel.Photo -> PhotoCell(
                            file = File(photosDir, cell.fileName),
                            onLongPress = { onLongPress(cell.fileName) },
                            modifier = cellModifier
                        )
                    }
                }
                // Mantiene ancho de columna constante en la última fila parcial.
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private sealed interface PhotoCellModel {
    data object Add : PhotoCellModel
    data class Photo(val fileName: String) : PhotoCellModel
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddPhotoTile(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .combinedClickable(onClick = onAdd),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Agregar foto",
                tint = colors.secondaryText
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Agregar",
                fontSize = 11.sp,
                color = colors.secondaryText
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(
    file: File,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.appColors
    var bitmap by remember(file.path) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file.path) {
        bitmap = withContext(Dispatchers.IO) { decodeSampledBitmap(file, targetSize = 320) }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { bm ->
            Image(
                bitmap = bm,
                contentDescription = "Foto del paciente",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** "12.0" → "12", "12.5" → "12.5" — display sin trailing ".0" redundante. */
private fun trimFloat(value: Float): String = if (value == value.toInt().toFloat()) {
    value.toInt().toString()
} else {
    value.toString()
}

/** Submuestreo para no cargar 12 MP en memoria por una miniatura de ~320 px. */
private fun decodeSampledBitmap(file: File, targetSize: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
    while ((maxSide / sample) > targetSize * 2) sample *= 2

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.path, decodeOptions)?.asImageBitmap()
}
