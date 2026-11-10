package example.nucleus.ui.components.player

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass
import example.nucleus.ui.components.glass.rememberLiquidGlassControlStyle

/**
 * Botón circular con material Liquid Glass (BETA).
 *
 * Lente de cristal Clear sobre lo que haya detrás (la barra + el contenido):
 * usa `HazeInput.Sources` con el miniPlayerHazeState, cuya fuente es la caja del
 * contenido (hermana-anterior del overlay): patrón sibling de la guía. Sin
 * `Modifier.background` extra: el fondo y el tint viven dentro del estilo (una
 * sola capa, sin recuadros).
 *
 * La respuesta visual (pressed/hovered/focused) vive en el estilo; aquí solo se
 * conecta el [interactionSource] compartido entre el clickable y el `hazeGlass`,
 * como pide la guía de interacción.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectedTint: Color = MaterialTheme.colorScheme.primary,
    contentTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    val colorScheme = MaterialTheme.colorScheme
    val style = rememberLiquidGlassControlStyle(
        selected = selected,
        containerColor = colorScheme.surfaceContainer,
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = style,
                interactionSource = interactionSource,
            )
            // Filete claro sobre el cristal: garantiza que la lente se recorte
            // incluso sobre fondos planos donde la refracción no da contraste.
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.35f),
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (selected) selectedTint else contentTint,
            modifier = Modifier.size(iconSize),
        )
    }
}
