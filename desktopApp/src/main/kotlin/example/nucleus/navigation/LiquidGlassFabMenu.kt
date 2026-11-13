package example.nucleus.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.nav_account
import example.nucleus.generated.resources.nav_home
import example.nucleus.generated.resources.nav_library
import example.nucleus.generated.resources.nav_listen_together
import example.nucleus.generated.resources.nav_search
import example.nucleus.generated.resources.nav_settings
import example.nucleus.generated.resources.navigation_rail
import example.nucleus.ui.components.glass.LocalContentHaze
import example.nucleus.ui.components.glass.LocalLiquidGlass
import example.nucleus.ui.components.glass.rememberLiquidGlassControlStyle
import example.nucleus.ui.components.player.GlassIconButton
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.ui.helpers.blockPointerEventsBehind
import org.jetbrains.compose.resources.stringResource

/**
 * Alternativa a la rail: un FAB flotante que despliega píldoras separadas
 * (speed-dial), no una lista dropdown.
 *
 * Cada destino es su propia lente de cristal (o píldora clásica sin glass),
 * con gaps amplios para que se lean como piezas sueltas. El popup cubre esta
 * misma caja, así que el input es la caja del contenido vía Sources (válido
 * también en ventanas separadas según la guía de Haze).
 */
@Composable
internal fun NavigationFabMenu(
    mainTabs: List<TabInfo>,
    bottomTabs: List<TabInfo>,
    activeConfig: ScreenConfig,
    onTabClick: (ScreenConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassEnabled = LocalLiquidGlass.current
    val contentHaze = LocalContentHaze.current
    val useGlass = glassEnabled && contentHaze != null
    val animationsEnabled = LocalAnimationsEnabled.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.blockPointerEventsBehind(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = if (animationsEnabled) {
                fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
            } else {
                fadeIn()
            },
            exit = if (animationsEnabled) {
                fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            } else {
                fadeOut()
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                (mainTabs + bottomTabs).forEach { tab ->
                    FabMenuItem(
                        tab = tab,
                        selected = tab.config == activeConfig,
                        useGlass = useGlass,
                        hazeState = contentHaze,
                        onClick = {
                            expanded = false
                            onTabClick(tab.config)
                        },
                    )
                }
            }
        }

        Box {
            if (useGlass) {
                GlassIconButton(
                    onClick = { expanded = !expanded },
                    imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Menu,
                    contentDescription = stringResource(Res.string.navigation_rail),
                    hazeState = contentHaze,
                    selected = expanded,
                    size = 56.dp,
                    iconSize = 26.dp,
                )
            } else {
                FloatingActionButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Menu,
                        contentDescription = stringResource(Res.string.navigation_rail),
                    )
                }
            }
        }
    }
}

/**
 * Una píldora suelta del speed-dial: lente Clear con icono + etiqueta (o
 * píldora clásica sin glass). Sin contenedor a propósito: cada pieza dibuja su
 * propio cristal para que no se fusionen.
 */@Composable
private fun FabMenuItem(
    tab: TabInfo,
    selected: Boolean,
    useGlass: Boolean,
    hazeState: HazeState?,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val pillShape = RoundedCornerShape(50)
    val interactionSource = remember { MutableInteractionSource() }
    val style = if (useGlass && hazeState != null) {
        rememberLiquidGlassControlStyle(
            shape = pillShape,
            selected = selected,
            containerColor = colorScheme.surfaceContainer,
        )
    } else null

    Row(
        modifier = Modifier
            .then(
                if (useGlass && hazeState != null) {
                    Modifier.hazeGlass(
                        input = HazeInput.Sources(hazeState),
                        style = style!!,
                        interactionSource = interactionSource,
                    )
                } else {
                    Modifier
                        .shadow(4.dp, pillShape, clip = false)
                        .background(
                            if (selected) {
                                colorScheme.primaryContainer
                            } else {
                                colorScheme.surfaceContainerHigh
                            },
                            pillShape,
                        )
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = if (selected) {
                colorScheme.primary
            } else {
                colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = navLabel(tab.config),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                colorScheme.onSurface
            } else {
                colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
internal fun navLabel(config: ScreenConfig): String = when (config) {
    ScreenConfig.Home -> stringResource(Res.string.nav_home)
    ScreenConfig.Search -> stringResource(Res.string.nav_search)
    ScreenConfig.Library -> stringResource(Res.string.nav_library)
    ScreenConfig.ListenTogether -> stringResource(Res.string.nav_listen_together)
    ScreenConfig.Account -> stringResource(Res.string.nav_account)
    ScreenConfig.Settings -> stringResource(Res.string.nav_settings)
    else -> ""
}
