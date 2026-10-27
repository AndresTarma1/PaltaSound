package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.data.repository.*
import example.nucleus.viewmodels.AppearanceSettingsViewModel
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceSettingsGroup() {
    val colors = LocalSettingsColors.current
    val viewModel: AppearanceSettingsViewModel = koinInject()
    val themeMode by viewModel.themeMode.collectAsState()
    val darkLevel by viewModel.darkLevel.collectAsState()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val cornerStyle by viewModel.cornerStyle.collectAsState()
    val themePalette by viewModel.themePalette.collectAsState()
    val dynamicColor by viewModel.dynamicColorFromArtwork.collectAsState()
    val navigationRailStyle by viewModel.navigationRailStyle.collectAsState()
    val appBackgroundStyle by viewModel.appBackgroundStyle.collectAsState()

    var showThemeDropdown by remember { mutableStateOf(false) }
    var showDarkLevelDropdown by remember { mutableStateOf(false) }
    var showLayoutDropdown by remember { mutableStateOf(false) }
    var showCornerStyleDropdown by remember { mutableStateOf(false) }
    var showPaletteDropdown by remember { mutableStateOf(false) }
    var showNavigationRailStyle by remember { mutableStateOf(false) }
    var showAppBackgroundDropdown by remember { mutableStateOf(false) }

    // Presets
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            stringResource(Res.string.preset_compact) to {
                viewModel.setLayoutMode(LayoutMode.SQUARE)
                viewModel.setUiScale(0.90f)
                viewModel.setAnimationsEnabled(false)
            },
            stringResource(Res.string.preset_comfortable) to {
                viewModel.setLayoutMode(LayoutMode.ATTACHED)
                viewModel.setUiScale(1.00f)
                viewModel.setAnimationsEnabled(true)
            },
            stringResource(Res.string.preset_accessible) to {
                viewModel.setLayoutMode(LayoutMode.ATTACHED)
                viewModel.setUiScale(1.20f)
                viewModel.setAnimationsEnabled(true)
            },
        ).forEach { (label, action) ->
            Button(onClick = action, modifier = Modifier.weight(1f)) {
                Text(label)
            }
        }
    }

    SettingsGroup(
        title = { Text(stringResource(Res.string.appearance_theme), style = MaterialTheme.typography.titleMediumEmphasized) },
        colors = colors,
    ) {
        DropdownSelector(
            label = stringResource(Res.string.theme),
            icon = Icons.Rounded.DarkMode,
            currentValue = themeMode.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 4),
            expanded = showThemeDropdown,
            onExpandedChange = { showThemeDropdown = it },
            options = ThemeMode.entries.map { it to it.displayName() },
            isSelected = { it == themeMode },
            onSelect = { viewModel.setThemeMode(it); showThemeDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.settings_dark_level),
            icon = Icons.Rounded.Contrast,
            currentValue = darkLevel.displayName(),
            expanded = showDarkLevelDropdown,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 4),
            onExpandedChange = { showDarkLevelDropdown = it },
            options = DarkLevel.entries.map { it to it.displayName() },
            isSelected = { it == darkLevel },
            onSelect = { viewModel.setDarkLevel(it); showDarkLevelDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.color_palette),
            icon = Icons.Rounded.Palette,
            currentValue = themePalette.displayName(),
            expanded = showPaletteDropdown,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 2, count = 4),
            onExpandedChange = { showPaletteDropdown = it },
            options = ThemePalette.entries.map { it to it.displayName() },
            isSelected = { it == themePalette },
            onSelect = { viewModel.setThemePalette(it); showPaletteDropdown = false },
            paletteItem = true,
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.ColorLens, null) },
            title = { Text(stringResource(Res.string.dynamic_colors)) },
            shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
            colors = colors,
            state = dynamicColor,
            onCheckedChange = { viewModel.setDynamicColorFromArtwork(it) }
        )
    }

    SettingsGroup(
        title = { Text(stringResource(Res.string.appearance_design), style = MaterialTheme.typography.titleMediumEmphasized) },
        colors = colors,
    ) {
        DropdownSelector(
            label = stringResource(Res.string.settings_layout),
            icon = Icons.Rounded.Dashboard,
            currentValue = layoutMode.displayName(),
            expanded = showLayoutDropdown,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 4),
            onExpandedChange = { showLayoutDropdown = it },
            options = listOf(LayoutMode.ATTACHED, LayoutMode.SQUARE).map { it to it.displayName() },
            isSelected = { it == layoutMode },
            onSelect = { viewModel.setLayoutMode(it); showLayoutDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.settings_corner_style),
            icon = Icons.Rounded.RoundedCorner,
            currentValue = cornerStyle.displayName(),
            expanded = showCornerStyleDropdown,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 4),
            onExpandedChange = { showCornerStyleDropdown = it },
            options = CornerStyle.entries.map { it to it.displayName() },
            isSelected = { it == cornerStyle },
            onSelect = { viewModel.setCornerStyle(it); showCornerStyleDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.app_background_style),
            icon = Icons.Rounded.Wallpaper,
            currentValue = appBackgroundStyle.displayName(),
            expanded = showAppBackgroundDropdown,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 2, count = 4),
            onExpandedChange = { showAppBackgroundDropdown = it },
            options = BackgroundStyle.entries.map { it to it.displayName() },
            isSelected = { it == appBackgroundStyle },
            onSelect = { viewModel.setAppBackgroundStyle(it); showAppBackgroundDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.navigation_rail_style),
            icon = Icons.Rounded.Menu,
            currentValue = navigationRailStyle.displayName(),
            expanded = showNavigationRailStyle,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 3, count = 4),
            onExpandedChange = { showNavigationRailStyle = it },
            options = NavigationRailStyle.entries.map { it to it.displayName() },
            isSelected = { it == navigationRailStyle },
            onSelect = { viewModel.setNavigationRailStyle(it); showNavigationRailStyle = false },
        )
    }
}


