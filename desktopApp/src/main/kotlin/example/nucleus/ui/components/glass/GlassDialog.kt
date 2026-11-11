package example.nucleus.ui.components.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * AlertDialog con material Liquid Glass (BETA).
 *
 * Paridad total con el `AlertDialog` de M3: con el interruptor apagado es
 * idéntico; encendido, el panel va transparente y el cristal lo pinta con el
 * estilo compartido (misma base, specular y esmerilado que barra y menús).
 * Funciona también en ventana aparte: los diálogos muestrean la caja del
 * contenido vía Sources (alineación cross-window según la guía de Haze).
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconContentColor: Color = MaterialTheme.colorScheme.secondary,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
    hazeState: HazeState? = LocalContentHaze.current ?: LocalGlassHaze.current,
) {
    val liquidGlass = LocalLiquidGlass.current
    val useGlass = liquidGlass && hazeState != null
    val glassShape = (shape as? RoundedCornerShape) ?: RoundedCornerShape(28.dp)

    val style = if (useGlass) {
        rememberLiquidGlassStyle(
            shape = glassShape,
            containerColor = containerColor,
        )
    } else null

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.then(
            if (useGlass) {
                Modifier.hazeGlass(
                    input = HazeInput.Sources(hazeState),
                    style = style!!,
                )
            } else Modifier,
        ),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = if (useGlass) Color.Transparent else containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = if (useGlass) 0.dp else tonalElevation,
        properties = properties,
    )
}

/**
 * Surface de diálogo personalizado con cristal (para los `Dialog` hechos a
 * mano que no son AlertDialog). Mismos parámetros que el Surface que
 * sustituye; con el interruptor apagado pinta idéntico.
 */
@Composable
fun GlassDialogSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation: Dp = 0.dp,
    hazeState: HazeState? = LocalContentHaze.current ?: LocalGlassHaze.current,
    content: @Composable () -> Unit,
) {
    val liquidGlass = LocalLiquidGlass.current
    val useGlass = liquidGlass && hazeState != null
    val glassShape = (shape as? RoundedCornerShape) ?: RoundedCornerShape(28.dp)

    val style = if (useGlass) {
        rememberLiquidGlassStyle(
            shape = glassShape,
            containerColor = containerColor,
        )
    } else null

    Surface(
        modifier = modifier.then(
            if (useGlass) {
                Modifier.hazeGlass(
                    input = HazeInput.Sources(hazeState),
                    style = style!!,
                )
            } else Modifier,
        ),
        shape = shape,
        color = if (useGlass) Color.Transparent else containerColor,
        tonalElevation = if (useGlass) 0.dp else tonalElevation,
        content = content,
    )
}
