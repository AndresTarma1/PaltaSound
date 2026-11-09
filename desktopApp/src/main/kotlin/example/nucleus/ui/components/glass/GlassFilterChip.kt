package example.nucleus.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * FilterChip con material Liquid Glass (BETA).
 *
 * Reimplementado como una píldora Glass completa (sin el contenedor Material
 * por debajo): el fondo, el tint y el borde viven dentro del estilo Glass y el
 * contenido es un Row clickeable. Con el interruptor apagado cae a un chip
 * Material normal.
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
    showBorder: Boolean = true,
    colors: Any? = null,
) {
    val liquidGlass = LocalLiquidGlass.current
    val hazeState = LocalGlassHaze.current
    val useGlass = liquidGlass && hazeState != null
    val colorScheme = MaterialTheme.colorScheme
    val glassShape = (shape as? RoundedCornerShape) ?: RoundedCornerShape(50)

    val glassStyle = if (useGlass) {
        rememberLiquidGlassStyle(
            shape = glassShape,
            selected = selected,
            containerColor = colorScheme.surfaceContainer,
        )
    } else null

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .shadow(if (useGlass) 1.dp else 0.dp, shape, clip = false)
            .then(
                if (useGlass) {
                    Modifier.hazeGlass(
                        input = HazeInput.Sources(hazeState),
                        style = glassStyle!!,
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
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
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
