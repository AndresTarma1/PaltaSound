package example.nucleus.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * FilterChip con material Liquid Glass (BETA).
 *
 * Lente Clear: la refracción vive en la banda del borde y el cuerpo deja pasar
 * el fondo, en vez de la píldora translúcida plana con borde blanco genérico de
 * antes (sin relleno ni borde M3: fondo y tint van dentro del estilo). La
 * respuesta pressed/hovered del estilo le da el tacto líquido; aquí solo se
 * conecta el [interactionSource] compartido.
 *
 * Muestrea el fondo ambiental ([LocalRailHaze], ventana completa) en vez del
 * contenido: los chips viven dentro de las pantallas y cualquier fuente del
 * contenido sería ancestro (Behind → vacío). El fondo ambiental no tiene
 * ancestro con ese estado, así que selecciona siempre.
 */
@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(50),
    showBorder: Boolean = false,
    colors: Any? = null,
    hazeState: HazeState? = LocalRailHaze.current ?: LocalGlassHaze.current,
) {
    val liquidGlass = LocalLiquidGlass.current
    val useGlass = liquidGlass && hazeState != null
    val colorScheme = MaterialTheme.colorScheme
    val glassShape = (shape as? RoundedCornerShape) ?: RoundedCornerShape(50)

    val glassStyle = if (useGlass) {
        rememberLiquidGlassControlStyle(
            shape = glassShape,
            selected = selected,
            containerColor = colorScheme.surfaceContainer,
        )
    } else null

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .then(
                if (useGlass) {
                    Modifier.hazeGlass(
                        input = HazeInput.Sources(hazeState),
                        style = glassStyle!!,
                        interactionSource = interactionSource,
                    )
                } else {
                    Modifier.background(
                        if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                        shape,
                    )
                },
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leadingIcon?.invoke()
            label()
        }
    }
}
