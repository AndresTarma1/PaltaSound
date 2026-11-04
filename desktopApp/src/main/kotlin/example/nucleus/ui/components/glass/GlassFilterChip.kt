package example.nucleus.ui.components.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * FilterChip con material Liquid Glass (BETA).
 *
 * Con el interruptor apagado es un `FilterChip` de M3 idéntico al original
 * (mismo borde por defecto salvo `showBorder = false`). Con el interruptor
 * encendido, el chip de M3 va transparente dentro de una sola píldora Glass:
 * sin bordes duros ni fondos extra que se verían como recuadros.
 */
@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    showBorder: Boolean = true,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
) {
    val liquidGlass = LocalLiquidGlass.current
    val hazeState = LocalGlassHaze.current
    if (liquidGlass && hazeState != null) {
        val colorScheme = MaterialTheme.colorScheme
        val glassShape = (shape as? RoundedCornerShape) ?: RoundedCornerShape(50)
        // GlassStyle compara por identidad: recordar por (color, selección, forma).
        val style = remember(colorScheme.surfaceContainer, selected, glassShape) {
            GlassStyle.regular.then {
                backgroundColor(colorScheme.surfaceContainer.copy(alpha = if (selected) 0.5f else 0.3f))
                tint(colorScheme.surfaceContainer.copy(alpha = 0.08f))
                shape(glassShape)
                edgeShadow(Color.Transparent)
                specularIntensity(0.18f)
                ambientResponse(0.06f)
            }
        }
        Box(
            modifier = modifier
                .shadow(2.dp, shape, clip = false)
                .hazeGlass(
                    input = HazeInput.Sources(hazeState),
                    style = style,
                ),
        ) {
            FilterChip(
                selected = selected,
                onClick = onClick,
                label = label,
                modifier = Modifier,
                enabled = enabled,
                leadingIcon = leadingIcon,
                shape = shape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    selectedContainerColor = Color.Transparent,
                ),
                border = null,
            )
        }
    } else if (!showBorder) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            shape = shape,
            colors = colors,
            border = null,
        )
    } else {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            shape = shape,
            colors = colors,
        )
    }
}
