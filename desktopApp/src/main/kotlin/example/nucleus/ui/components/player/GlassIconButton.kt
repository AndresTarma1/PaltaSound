package example.nucleus.ui.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Botón circular con material Liquid Glass (BETA).
 *
 * Píldora de cristal sobre la fuente Haze dada: sin fondo sólido propio, el
 * tint y el fondo viven DENTRO del estilo Glass (una sola capa, sin recuadros).
 * En superficies pequeñas el Regular de Haze conserva detalle refractado, así
 * que no se toca la óptica: solo tinte, forma y specular suave.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectedTint: Color = MaterialTheme.colorScheme.onSurface,
    contentTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    val colorScheme = MaterialTheme.colorScheme
    // GlassStyle compara por identidad: recordar por (color, selected).
    val style = remember(colorScheme.surfaceContainer, selected) {
        GlassStyle.regular.then {
            backgroundColor(colorScheme.surfaceContainer.copy(alpha = if (selected) 0.45f else 0.28f))
            tint(colorScheme.surfaceContainer.copy(alpha = 0.08f))
            shape(RoundedCornerShape(50))
            specularIntensity(0.2f)
            ambientResponse(0.08f)
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .shadow(2.dp, CircleShape, clip = false)
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = style,
            ),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = if (selected) selectedTint else contentTint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
