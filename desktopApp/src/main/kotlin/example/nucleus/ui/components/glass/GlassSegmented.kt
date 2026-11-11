package example.nucleus.ui.components.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Pista esmerilada para tabs segmentados (los de Ajustes, como los de Now
 * Playing). El contenido lo aporta el llamador: una fila (con su contenedor
 * para fusionar) de [GlassSegmentPill].
 *
 * Sin cristal no pinta nada: cada llamador conserva su clásico en la rama
 * else, porque el aspecto clásico varía por sitio.
 */
@Composable
fun GlassSegmentTrack(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(50),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    val style = rememberLiquidGlassStyle(
        shape = shape,
        containerColor = containerColor,
    )
    Box(
        modifier = modifier.hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = style,
        ),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/**
 * Una píldora del control segmentado: lente Clear con interacción. El gap con
 * las vecinas debe superar al mergeDistance del contenedor para que cada una
 * conserve su lente (ver los call sites: gap 8dp frente a merge 6dp… 3dp).
 */
@Composable
fun GlassSegmentPill(
    selected: Boolean,
    onClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(50),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val style = rememberLiquidGlassControlStyle(
        shape = shape,
        selected = selected,
        containerColor = containerColor,
    )
    Row(
        modifier = modifier
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = style,
                interactionSource = interactionSource,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
