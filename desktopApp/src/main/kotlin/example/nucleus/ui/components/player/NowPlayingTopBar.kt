@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.generated.resources.*
import example.nucleus.generated.resources.Res
import example.nucleus.ui.themes.AppShapes
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
