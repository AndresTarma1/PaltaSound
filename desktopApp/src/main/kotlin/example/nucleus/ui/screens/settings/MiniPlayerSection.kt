package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import example.nucleus.data.repository.MiniPlayerBackgroundStyle
import example.nucleus.data.repository.MiniPlayerStyle
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.viewmodels.MiniPlayerSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayerSettingsGroup(
    onOpenSeekBarStyleDialog: () -> Unit
) {
    val colors = LocalSettingsColors.current
    val viewModel: MiniPlayerSettingsViewModel = koinInject()
    val miniPlayerStyle by viewModel.miniPlayerStyle.collectAsState()
    val miniPlayerBackgroundStyle by viewModel.miniPlayerBackgroundStyle.collectAsState()
    val seekBarStyle by viewModel.seekBarStyle.collectAsState()
    var showMiniPlayerStyleDropdown by remember { mutableStateOf(false) }
    var showMiniPlayerBgStyleDropdown by remember { mutableStateOf(false) }

    val itemCount = if (miniPlayerStyle == MiniPlayerStyle.FLOATING) 3 else 2
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
            segmentedShape = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
            onExpandedChange = { showMiniPlayerStyleDropdown = it },
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
                segmentedShape = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
                onExpandedChange = { showMiniPlayerBgStyleDropdown = it },
                options = MiniPlayerBackgroundStyle.entries.map { it to it.displayName() },
                isSelected = { it == miniPlayerBackgroundStyle },
                onSelect = { viewModel.setMiniPlayerBackgroundStyle(it); showMiniPlayerBgStyleDropdown = false },
            )
        }
        SettingsMenuLink(
            icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, null) },
            shapes = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
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
