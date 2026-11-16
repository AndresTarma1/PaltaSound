package example.nucleus.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import example.nucleus.data.repository.MiniPlayerBackgroundStyle
import example.nucleus.data.repository.MiniPlayerDesign
import example.nucleus.data.repository.MiniPlayerStyle
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.components.MiniPlayerCompactContent
import example.nucleus.ui.components.MiniPlayerPreviewSong
import example.nucleus.ui.components.MiniPlayerVinylContent
import example.nucleus.ui.components.MiniPlayerWideRow
import example.nucleus.ui.screens.shared.description
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.viewmodels.MiniPlayerSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayerSettingsGroup(
    onOpenSeekBarStyleDialog: () -> Unit
) {
    val colors = LocalSettingsColors.current
    val viewModel: MiniPlayerSettingsViewModel = koinInject()
    val miniPlayerStyle by viewModel.miniPlayerStyle.collectAsState()
    val miniPlayerDesign by viewModel.miniPlayerDesign.collectAsState()
    val miniPlayerBackgroundStyle by viewModel.miniPlayerBackgroundStyle.collectAsState()
    val seekBarStyle by viewModel.seekBarStyle.collectAsState()

    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.section_mini_player),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors,
    ) {
        Text(
            text = stringResource(Res.string.mini_player_design),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        MiniPlayerDesign.entries.forEach { design ->
            MiniPlayerDesignCard(
                design = design,
                selected = design == miniPlayerDesign,
                onSelect = { viewModel.setMiniPlayerDesign(design) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }

    var showMiniPlayerStyleDropdown by remember { mutableStateOf(false) }
    var showMiniPlayerBgStyleDropdown by remember { mutableStateOf(false) }
    val itemCount = if (miniPlayerStyle == MiniPlayerStyle.FLOATING) 2 else 1
    var idx = 0

    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.section_mini_player),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors,
    ) {
        DropdownSelector(
            label = stringResource(Res.string.mini_player_style),
            icon = Icons.Rounded.Album,
            currentValue = miniPlayerStyle.displayName(),
            expanded = showMiniPlayerStyleDropdown,
            onExpandedChange = { showMiniPlayerStyleDropdown = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount + 1),
            options = MiniPlayerStyle.entries.map { it to it.displayName() },
            isSelected = { it == miniPlayerStyle },
            onSelect = { viewModel.setMiniPlayerStyle(it); showMiniPlayerStyleDropdown = false },
        )
        if (miniPlayerStyle == MiniPlayerStyle.FLOATING) {
            DropdownSelector(
                label = stringResource(Res.string.mini_player_background_style),
                icon = Icons.Rounded.BlurOn,
                currentValue = miniPlayerBackgroundStyle.displayName(),
                expanded = showMiniPlayerBgStyleDropdown,
                onExpandedChange = { showMiniPlayerBgStyleDropdown = it },
                segmentedShape = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount + 1),
                options = MiniPlayerBackgroundStyle.entries.map { it to it.displayName() },
                isSelected = { it == miniPlayerBackgroundStyle },
                onSelect = { viewModel.setMiniPlayerBackgroundStyle(it); showMiniPlayerBgStyleDropdown = false },
            )
        }
        SettingsMenuLink(
            icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, null) },
            shapes = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount + 1),
            title = { Text(stringResource(Res.string.seek_bar_style)) },
            subtitle = { Text(seekBarStyle.displayName()) },
            colors = colors,
            action = {
                IconButton(onClick = onOpenSeekBarStyleDialog) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            },
            onClick = onOpenSeekBarStyleDialog
        )
    }
}

/**
 * Tarjeta de selección de diseño con vista previa en vivo (no interactiva:
 * un velo transparente consume los toques y pulsar selecciona).
 *
 * Jerarquía tonal M3 (como la referencia): la tarjeta va en
 * [surfaceContainerHigh] igual que las demás filas de ajustes, y el pozo de
 * preview usa [surfaceContainer], el fondo propio del chrome del MiniPlayer
 * en modo sólido. Así el diseño se ve sobre su fondo real, no sobre el de ajustes.
 */
@Composable
private fun MiniPlayerDesignCard(
    design: MiniPlayerDesign,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(16.dp)
    val previewShape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Pozo de preview: fondo propio del MiniPlayer (chrome sólido =
            // surfaceContainer), no el de ajustes.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                shape = previewShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MiniPlayerDesignPreview(design = design)
                    // Velo: bloquea la interacción interna; pulsar selecciona.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSelect,
                            ),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = design.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = design.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RadioButton(selected = selected, onClick = onSelect)
            }
        }
    }
}

/**
 * Vista previa de cada diseño con sus propios composables reales:
 * EXPRESSIVE usa la fila ancha (layout wide) con el estado actual del
 * reproductor; COMPACT y VINYL usan datos de ejemplo estáticos.
 */
@Composable
private fun MiniPlayerDesignPreview(design: MiniPlayerDesign) {
    val playerViewModel = LocalPlayerViewModel.current
    val uiState by playerViewModel.uiState.collectAsState()
    when (design) {
        MiniPlayerDesign.EXPRESSIVE -> MiniPlayerWideRow(
            song = uiState.currentSong ?: MiniPlayerPreviewSong,
            state = uiState,
            isError = false,
            isOnNowPlaying = false,
            isQueueVisible = false,
            onNowPlaying = {},
            onToggleQueue = {},
            ratio = 1f,
            sharedTransitionScope = null,
            sharedVisibilityScope = null,
            playerViewModel = playerViewModel,
            colorScheme = MaterialTheme.colorScheme,
            liquidGlass = false,
            hazeState = null,
        )
        MiniPlayerDesign.COMPACT -> MiniPlayerCompactContent(
            song = MiniPlayerPreviewSong,
            isPlaying = true,
            isLoading = false,
            isError = false,
            errorText = null,
            progress = 0.35f,
            liked = true,
            onTogglePlay = {},
            onPrevious = {},
            onNext = {},
            onToggleLike = {},
            onToggleQueue = {},
            onExpand = {},
            interactive = false,
        )
        MiniPlayerDesign.VINYL -> MiniPlayerVinylContent(
            song = MiniPlayerPreviewSong,
            isPlaying = true,
            isLoading = false,
            isError = false,
            errorText = null,
            progress = 0.35f,
            liked = false,
            onTogglePlay = {},
            onPrevious = {},
            onNext = {},
            onToggleLike = {},
            onToggleQueue = {},
            onExpand = {},
            interactive = false,
        )
    }
}
