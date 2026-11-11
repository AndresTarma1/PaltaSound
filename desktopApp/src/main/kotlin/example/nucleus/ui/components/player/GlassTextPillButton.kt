package example.nucleus.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass
import example.nucleus.ui.components.glass.LocalContentHaze
import example.nucleus.ui.components.glass.LocalLiquidGlass
import example.nucleus.ui.components.glass.LocalRailHaze
import example.nucleus.ui.components.glass.rememberLiquidGlassControlStyle

/**
 * Píldora con texto (y opcional icono) con cristal.
 *
 * Equivalente textual de [GlassIconButton]: lente Clear con interacción en
 * modo cristal; píldora tonal clásica si no. Se usa en acciones con etiqueta
 * (play de artista/colección, ajustes, diálogos). resuelve solo el HazeState:
 * el fondo ambiental es válido en cualquier sitio empotrado.
 */
@Composable
fun GlassTextPillButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    hazeState: HazeState? = LocalRailHaze.current ?: LocalContentHaze.current,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentTint: Color = MaterialTheme.colorScheme.onSurface,
    fallbackContainer: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    fallbackContent: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: Dp = 20.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val useGlass = LocalLiquidGlass.current && hazeState != null
    val interactionSource = remember { MutableInteractionSource() }
    val style = if (useGlass) {
        rememberLiquidGlassControlStyle(
            shape = RoundedCornerShape(50),
            selected = selected,
            containerColor = containerColor,
        )
    } else null

    Box(
        modifier = modifier
            .then(
                if (useGlass) {
                    Modifier.hazeGlass(
                        input = HazeInput.Sources(hazeState!!),
                        style = style!!,
                        interactionSource = interactionSource,
                    )
                } else {
                    Modifier.background(fallbackContainer, CircleShape)
                },
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(enabled = enabled && !loading, interactionSource = interactionSource)
            .pointerHoverIcon(if (enabled && !loading) PointerIcon.Hand else PointerIcon.Default)
            .alpha(if (enabled && !loading) 1f else 0.55f)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = contentTint,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = contentTint,
                        modifier = Modifier.size(iconSize),
                    )
                }
                Text(
                    text = text,
                    style = textStyle,
                    color = contentTint,
                    maxLines = 1,
                )
            }
        }
    }
}
