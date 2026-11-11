@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.generated.resources.*
import example.nucleus.generated.resources.Res
import example.nucleus.ui.components.glass.LocalContentHaze
import example.nucleus.ui.components.glass.LocalLiquidGlass
import example.nucleus.ui.components.glass.LocalRailHaze
import example.nucleus.ui.components.glass.rememberLiquidGlassControlStyle
import example.nucleus.ui.components.glass.rememberLiquidGlassStyle
import example.nucleus.ui.themes.AppShapes
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import org.jetbrains.compose.resources.stringResource

/**
 * Barra superior con selector de pestañas centrado y acciones rápidas.
 * El retroceso de navegación se gestiona globalmente desde la TitleBar.
 */
@Composable
internal fun NowPlayingTopBar(
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    queueCount: Int,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            Spacer(Modifier.width(88.dp))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            NowPlayingIconTabs(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                queueCount = queueCount,
                showLabels = !compact,
            )
        }

        NowPlayingTopActions(
            showMenu = showMenu,
            onMenuToggle = onMenuToggle,
            onOpenEqualizer = onOpenEqualizer,
        )
    }
}

/**
 * Píldoras de navegación entre pestañas (Letras, Cola, Información).
 */
@Composable
internal fun NowPlayingIconTabs(
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    queueCount: Int,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val coverLabel = stringResource(Res.string.tab_cover)
    val lyricsLabel = stringResource(Res.string.tab_lyrics)
    val queueLabel = stringResource(Res.string.tab_queue)
    val colorScheme = MaterialTheme.colorScheme
    val useGlass = LocalLiquidGlass.current
    val tabsHaze = LocalRailHaze.current ?: LocalContentHaze.current
    val glassTabs = useGlass && tabsHaze != null
    val trackStyle = if (glassTabs) {
        rememberLiquidGlassStyle(
            shape = RoundedCornerShape(50),
            containerColor = colorScheme.surfaceContainer,
        )
    } else null

    if (glassTabs) {
        // Pista esmerilada + segmentos lente en un contenedor: gap 4dp con
        // merge 3dp para que cada segmento conserve su lente.
        Box(
            modifier = modifier.hazeGlass(
                input = HazeInput.Sources(tabsHaze),
                style = trackStyle!!,
            ),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .hazeGlassContainer(mergeDistance = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NowPlayingTab.entries.forEach { tab ->
                    NowPlayingTabLens(
                        tab = tab,
                        selected = tab == selectedTab,
                        label = when (tab) {
                            NowPlayingTab.COVER -> coverLabel
                            NowPlayingTab.LYRICS -> lyricsLabel
                            NowPlayingTab.QUEUE -> if (queueCount > 0) "$queueLabel · $queueCount" else queueLabel
                        },
                        showLabels = showLabels,
                        hazeState = tabsHaze,
                        onClick = { onTabSelected(tab) },
                    )
                }
            }
        }
        return
    }

    Surface(
        shape = AppShapes.extraLarge,
        color = colorScheme.surfaceContainer.copy(alpha = 0.5f),
        tonalElevation = 0.dp,
        modifier = modifier.border(
            width = 0.5.dp,
            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
            shape = AppShapes.extraLarge,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NowPlayingTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val label = when (tab) {
                    NowPlayingTab.COVER -> coverLabel
                    NowPlayingTab.LYRICS -> lyricsLabel
                    NowPlayingTab.QUEUE -> if (queueCount > 0) "$queueLabel · $queueCount" else queueLabel
                }
                val contentColor = if (selected) {
                    colorScheme.onPrimaryContainer
                } else {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                }
                val containerColor = if (selected) {
                    colorScheme.primaryContainer.copy(alpha = 0.65f)
                } else {
                    Color.Transparent
                }

                Surface(
                    onClick = { onTabSelected(tab) },
                    shape = AppShapes.large,
                    color = containerColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .height(36.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (showLabels) 14.dp else 10.dp,
                            vertical = 6.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = tabIcon(tab),
                            contentDescription = if (showLabels) null else label,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor,
                        )
                        if (showLabels) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Segmento lente del selector de pestañas: solo el seleccionado lleva cristal
 * (miembro del contenedor de la pista); el resto es tinta directa sobre la
 * pista esmerilada. Sin clips: la forma vive en el estilo.
 */
@Composable
private fun NowPlayingTabLens(
    tab: NowPlayingTab,
    selected: Boolean,
    label: String,
    showLabels: Boolean,
    hazeState: dev.chrisbanes.haze.HazeState,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val lensShape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val lensStyle = if (selected) {
        rememberLiquidGlassControlStyle(
            shape = lensShape,
            selected = true,
            containerColor = colorScheme.surfaceContainer,
        )
    } else null
    val contentColor = if (selected) {
        colorScheme.primary
    } else {
        colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    }

    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .height(36.dp)
            .then(
                if (selected) {
                    Modifier.hazeGlass(
                        input = HazeInput.Sources(hazeState),
                        style = lensStyle!!,
                        interactionSource = interaction,
                    )
                } else Modifier,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(
                horizontal = if (showLabels) 14.dp else 10.dp,
                vertical = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = tabIcon(tab),
            contentDescription = if (showLabels) null else label,
            modifier = Modifier.size(18.dp),
            tint = contentColor,
        )
        if (showLabels) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = if (selected) colorScheme.onSurface else contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
