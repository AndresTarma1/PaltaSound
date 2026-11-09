package example.nucleus.ui.components.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * DropdownMenu con material Liquid Glass (BETA).
 *
 * Con el interruptor apagado es un `DropdownMenu` de M3 idéntico al original.
 * Con el interruptor encendido, el contenedor va transparente y se pinta con
 * una sola píldora Glass (sin bordes duros ni fondos extra).
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    shape: Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation: Dp = 2.dp,
    shadowElevation: Dp = 8.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val liquidGlass = LocalLiquidGlass.current
    val hazeState = LocalGlassHaze.current
    val useGlass = liquidGlass && hazeState != null
    val colorScheme = MaterialTheme.colorScheme
    val glassShape = (shape as? RoundedCornerShape) ?: RoundedCornerShape(16.dp)

    val style = if (useGlass) {
        remember(colorScheme.surfaceContainer, glassShape) {
            GlassStyle.regular.then {
                backgroundColor(colorScheme.surfaceContainer.copy(alpha = 0.4f))
                tint(colorScheme.surfaceContainer.copy(alpha = 0.1f))
                shape(glassShape)
                edgeShadow(Color.Transparent)
                specularIntensity(0.18f)
                ambientResponse(0.06f)
            }
        }
    } else null

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.then(
            if (useGlass) {
                Modifier.hazeGlass(
                    input = HazeInput.Sources(hazeState!!),
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
