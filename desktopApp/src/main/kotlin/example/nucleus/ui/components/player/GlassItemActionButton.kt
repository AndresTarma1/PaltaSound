package example.nucleus.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.glass.LocalContentHaze
import example.nucleus.ui.components.glass.LocalGlassHaze
import example.nucleus.ui.components.glass.LocalLiquidGlass

/**
 * Botón de acción de item (⋮ de filas y tarjetas, play rápido...) con cristal
 * cuando el modo está activo y clásico idéntico si no.
 *
 * Resuelve el HazeState solo: dentro del contenido el ancestro con el mismo
 * estado deja Behind vacío, así que cae al fondo ambiental (única fuente
 * válida ahí), igual que el resto de lentes sobre tarjetas.
 */
@Composable
fun GlassItemActionButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    contentTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selected: Boolean = false,
    // Fondo del modo clásico (p. ej. scrim negro sobre portadas). En cristal
    // no se usa: la base vive dentro del estilo.
    fallbackContainer: Color? = null,
) {
    val useGlass = LocalLiquidGlass.current
    val hazeState = LocalContentHaze.current ?: LocalGlassHaze.current
    if (useGlass && hazeState != null) {
        GlassIconButton(
            onClick = onClick,
            imageVector = imageVector,
            contentDescription = contentDescription,
            hazeState = hazeState,
            modifier = modifier,
            selected = selected,
            contentTint = contentTint,
            size = size,
            iconSize = iconSize,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .then(
                    if (fallbackContainer != null) {
                        Modifier
                            .clip(CircleShape)
                            .background(fallbackContainer)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(size)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = contentTint,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
