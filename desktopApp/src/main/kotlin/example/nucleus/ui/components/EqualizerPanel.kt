package example.nucleus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

data class EqPreset(val nameRes: StringResource, val bands: List<Float>)

private val eqPresets = listOf(
    EqPreset(Res.string.eq_preset_flat, List(5) { 0f }),
    EqPreset(Res.string.eq_preset_rock, listOf(5f, 3f, -1f, 2f, 5f)),
    EqPreset(Res.string.eq_preset_pop, listOf(-1f, 2f, 5f, 3f, -2f)),
    EqPreset(Res.string.eq_preset_jazz, listOf(4f, 2f, 1f, 3f, 4f)),
    EqPreset(Res.string.eq_preset_classical, listOf(4f, 2f, 0f, 2f, 3f)),
    EqPreset(Res.string.eq_preset_bass_boost, listOf(8f, 5f, 1f, 0f, 0f)),
    EqPreset(Res.string.eq_preset_dance, listOf(6f, 4f, 0f, 4f, 5f)),
    EqPreset(Res.string.eq_preset_acoustic, listOf(2f, 1f, 3f, 5f, 4f)),
)

// Labels fijos, hardcodeados.
private val eqBandLabels = listOf(
    "60", "250", "1k", "4k", "12k"
)

private const val DEBOUNCE_MS = 1000L

/**
 * Diálogo que envuelve el panel de ecualizador.
 * Incluye selector de presets, botón de reset y cierre.
 */
@Composable
fun EqualizerDialog(
    bands: List<Float>,
    onBandsChange: (List<Float>) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                // --- Encabezado ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.equalizer_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onBandsChange(List(bands.size) { 0f }) }) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = stringResource(Res.string.eq_reset),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Selector de presets ---
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(eqPresets) { preset ->
                        FilterChip(
                            selected = bands == preset.bands,
                            onClick = { onBandsChange(preset.bands) },
                            label = { Text(stringResource(preset.nameRes)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Panel del ecualizador ---
                EqualizerPanel(
                    bands = bands,
                    onBandsChange = onBandsChange,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // --- Acciones ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.close_equalizer))
                    }
                }
            }
        }
    }
}

/**
 * Panel de ecualizador. Trabaja con List<Float>.
 * El movimiento visual es inmediato, pero `onBandsChange` se dispara
 * con un debounce de 1 segundo tras el último cambio.
 * Los colores provienen de MaterialTheme.colorScheme (soporta light/dark/dynamic).
 */
@Composable
fun EqualizerPanel(
    bands: List<Float>,
    onBandsChange: (List<Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // --- Colores Material, leídos aquí porque el bloque Canvas no es @Composable ---
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = colorScheme.primary
    val gridColor = colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textColor = colorScheme.onSurfaceVariant
    val nodeIdleColor = colorScheme.surface

    var draggedBandIndex by remember { mutableStateOf<Int?>(null) }

    // Estado LOCAL: se actualiza en tiempo real para que el dibujo sea fluido.
    var localBands by remember { mutableStateOf(bands) }

    // Si el padre cambia `bands` desde fuera (ej. al aplicar un preset)
    // y no estamos arrastrando, sincronizamos el estado local.
    LaunchedEffect(bands) {
        if (draggedBandIndex == null) {
            localBands = bands
        }
    }

    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    val currentOnBandsChange by rememberUpdatedState(onBandsChange)

    fun scheduleDebouncedUpdate(updated: List<Float>) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS.milliseconds)
            currentOnBandsChange(updated)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 40.dp, vertical = 20.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offsetInitial ->
                            val width = size.width
                            val height = size.height
                            val bandCount = localBands.size
                            val stepX = width / (bandCount - 1)

                            var closestIndex: Int? = null
                            var closestDistance = Float.MAX_VALUE

                            localBands.forEachIndexed { index, value ->
                                val nodeX = index * stepX
                                val normY = (value - (-12f)) / (12f - (-12f))
                                val nodeY = height - (normY * height)

                                val distance = sqrt(
                                    (offsetInitial.x - nodeX).pow(2) +
                                            (offsetInitial.y - nodeY).pow(2)
                                )

                                if (distance < 40f && distance < closestDistance) {
                                    closestDistance = distance
                                    closestIndex = index
                                }
                            }
                            draggedBandIndex = closestIndex
                        },
                        onDrag = { change, _ ->
                            val index = draggedBandIndex ?: return@detectDragGestures
                            change.consume()

                            val height = size.height
                            val clampedY = change.position.y.coerceIn(0f, height.toFloat())

                            val normY = 1f - (clampedY / height)
                            val newValue = -12f + (normY * (12f - (-12f)))

                            val updated = localBands.toMutableList().apply {
                                this[index] = newValue.coerceIn(-12f, 12f)
                            }
                            localBands = updated
                            scheduleDebouncedUpdate(updated)
                        },
                        onDragEnd = { draggedBandIndex = null },
                        onDragCancel = { draggedBandIndex = null }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val bandCount = localBands.size
            val stepX = width / (bandCount - 1)

            // --- 1. GUÍAS Y TEXTOS HORIZONTALES (dB) ---
            val dbLevels = listOf(12 to "+12", 6 to "+6", 0 to "+0", -6 to "-6", -12 to "-12")
            dbLevels.forEach { (db, label) ->
                val normY = (db - (-12f)) / (12f - (-12f))
                val y = height - (normY * height)

                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = TextStyle(color = textColor, fontSize = 11.sp),
                    topLeft = Offset(-35.dp.toPx(), y - 8.dp.toPx())
                )
            }

            // --- 2. LÍNEAS VERTICALES POR BANDA ---
            val nodePoints = ArrayList<Offset>()
            localBands.forEachIndexed { index, value ->
                val x = index * stepX
                val normY = (value - (-12f)) / (12f - (-12f))
                val y = height - (normY * height)
                nodePoints.add(Offset(x, y))

                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // --- 3. LÍNEA QUE UNE LOS NODOS ---
            for (i in 0 until nodePoints.size - 1) {
                drawLine(
                    color = accentColor.copy(alpha = 0.8f),
                    start = nodePoints[i],
                    end = nodePoints[i + 1],
                    strokeWidth = 3.dp.toPx()
                )
            }

            // --- 4. NODOS INTERACTIVOS ---
            nodePoints.forEachIndexed { index, point ->
                val isSelected = draggedBandIndex == index

                drawCircle(
                    color = accentColor.copy(alpha = if (isSelected) 0.3f else 0.15f),
                    radius = if (isSelected) 16.dp.toPx() else 12.dp.toPx(),
                    center = point
                )

                drawCircle(
                    color = accentColor.copy(alpha = 0.4f),
                    radius = if (isSelected) 16.dp.toPx() else 12.dp.toPx(),
                    center = point,
                    style = Stroke(width = 1.dp.toPx())
                )

                drawCircle(
                    color = if (index == 0 || isSelected) accentColor else nodeIdleColor,
                    radius = 6.dp.toPx(),
                    center = point
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 5. TEXTOS INFERIORES DE FRECUENCIA ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            eqBandLabels.take(localBands.size).forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.width(50.dp),
                    maxLines = 1
                )
            }
        }
    }
}