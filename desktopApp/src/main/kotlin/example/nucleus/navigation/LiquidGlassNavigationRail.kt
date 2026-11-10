package example.nucleus.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.nav_account
import example.nucleus.generated.resources.nav_home
import example.nucleus.generated.resources.nav_library
import example.nucleus.generated.resources.nav_listen_together
import example.nucleus.generated.resources.nav_search
import example.nucleus.generated.resources.nav_settings
import example.nucleus.ui.components.glass.LocalRailHaze
import example.nucleus.ui.components.glass.LocalLiquidGlass
import example.nucleus.ui.components.glass.rememberLiquidGlassStyle
import org.jetbrains.compose.resources.stringResource

/**
 * Navigation rail vertical estilo Apple Music Liquid Glass.
 *
 * Sustituye al NavigationRail cuando Liquid Glass está activo. Toda la rail es
 * una píldora Glass; el ítem activo se marca con un fondo que cubre icono y
 * nombre.
 */
@Composable
internal fun LiquidGlassNavigationRail(
    tabs: List<TabInfo>,
    activeConfig: ScreenConfig,
    onTabClick: (ScreenConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassEnabled = LocalLiquidGlass.current
    // Fondo ambiental de ventana completa (degradado/cover), no el contenido:
    // la rail va en flujo sin nada desplazándose detrás. railHazeState no tiene
    // ancestro con el mismo estado (su fuente es hermana-anterior), así que
    // Behind selecciona siempre. LocalGlassHaze aquí seleccionaría vacío.
    val hazeState = LocalRailHaze.current
    val railShape = RoundedCornerShape(30.dp)
    val useGlass = glassEnabled && hazeState != null
    val colorScheme = MaterialTheme.colorScheme

    val railStyle = if (useGlass) {
        rememberLiquidGlassStyle(
            shape = railShape,
            containerColor = colorScheme.surfaceContainer,
        )
    } else null

    Column(
        modifier = modifier
            .width(96.dp)
            .then(
                if (useGlass) {
                    // Sin fondo extra: la base vive dentro del estilo (una sola
                    // capa). La fuente es el fondo ambiental de ventana completa.
                    Modifier.hazeGlass(
                        input = HazeInput.Sources(hazeState),
                        style = railStyle!!,
                    )
                } else {
                    Modifier.background(colorScheme.surfaceContainer, railShape)
                },
            )
            .clip(railShape)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tabs.forEach { tab ->
            val selected = tab.config == activeConfig
            val itemShape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .then(
                        if (selected) {
                            Modifier.hazeGlass(
                                input = HazeInput.Sources(hazeState!!),
                                style = railStyle!!,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onTabClick(tab.config) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = navLabel(tab.config),
                    tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = navLabel(tab.config),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun navLabel(config: ScreenConfig): String = when (config) {
    ScreenConfig.Home -> stringResource(Res.string.nav_home)
    ScreenConfig.Search -> stringResource(Res.string.nav_search)
    ScreenConfig.Library -> stringResource(Res.string.nav_library)
    ScreenConfig.ListenTogether -> stringResource(Res.string.nav_listen_together)
    ScreenConfig.Account -> stringResource(Res.string.nav_account)
    ScreenConfig.Settings -> stringResource(Res.string.nav_settings)
    else -> ""
}
