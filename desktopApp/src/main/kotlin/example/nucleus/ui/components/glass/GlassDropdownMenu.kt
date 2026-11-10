package example.nucleus.ui.components.glass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * DropdownMenu con material Liquid Glass (BETA).
 *
 * Con el interruptor apagado es un `DropdownMenu` de M3 idéntico al original.
 * Con el interruptor encendido usa el estilo compartido de la app (base real +
 * specular, igual que MiniPlayer y rail) en vez de su antiguo estilo débil a
 * medida, y muestrea [LocalContentHaze] (fuente del contenido, válida también
 * desde popups/ventanas separadas vía Sources) en vez de [LocalGlassHaze],
 * que vive en un ancestro y ahí Behind selecciona vacío.
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = androidx.compose.foundation.rememberScrollState(),
    shape: Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation: Dp = 2.dp,
    shadowElevation: Dp = 8.dp,
    border: androidx.compose.foundation.BorderStroke? = null,
    hazeState: HazeState? = LocalContentHaze.current ?: LocalGlassHaze.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    val liquidGlass = LocalLiquidGlass.current
    val useGlass = liquidGlass && hazeState != null
    val glassShape = (shape as? RoundedCornerShape) ?: RoundedCornerShape(16.dp)

    val style = if (useGlass) {
        rememberLiquidGlassStyle(
            shape = glassShape,
            containerColor = containerColor,
        )
    } else null

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.then(
            if (useGlass) {
                // Sin fondo extra: la base vive dentro del estilo.
                Modifier.hazeGlass(
                    input = HazeInput.Sources(hazeState),
                    style = style!!,
                )
            } else {
                Modifier
            },
        ),
        offset = offset,
        scrollState = scrollState,
        shape = shape,
        containerColor = if (useGlass) Color.Transparent else containerColor,
        tonalElevation = if (useGlass) 0.dp else tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}
