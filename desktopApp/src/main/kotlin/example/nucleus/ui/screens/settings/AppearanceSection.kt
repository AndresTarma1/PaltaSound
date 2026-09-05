package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.data.repository.*
import example.nucleus.ui.screens.shared.displayName
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
    val themePalette by viewModel.themePalette.collectAsState()
    val dynamicColor by viewModel.dynamicColorFromArtwork.collectAsState()
    val navigationRailStyle by viewModel.navigationRailStyle.collectAsState()
    val appBackgroundStyle by viewModel.appBackgroundStyle.collectAsState()
    val uiScale by viewModel.uiScale.collectAsState()
    val selectedFont by viewModel.selectedFont.collectAsState()
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()

    var showThemeDropdown by remember { mutableStateOf(false) }
    var showDarkLevelDropdown by remember { mutableStateOf(false) }
    var showLayoutDropdown by remember { mutableStateOf(false) }
    var showPaletteDropdown by remember { mutableStateOf(false) }
    var showNavigationRailStyle by remember { mutableStateOf(false) }
    var showAppBackgroundDropdown by remember { mutableStateOf(false) }
    var showUiScaleDropdown by remember { mutableStateOf(false) }

    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.section_appearance),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors,
    ) {
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Animation, null) },
            title = { Text(stringResource(Res.string.animations_enabled)) },
            subtitle = { Text(stringResource(Res.string.animations_enabled_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 10),
            colors = colors,
            state = animationsEnabled,
            onCheckedChange = { viewModel.setAnimationsEnabled(it) }
        )
        DropdownSelector(
            label = stringResource(Res.string.theme),
            icon = Icons.Rounded.DarkMode,
            currentValue = themeMode.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 10),
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
            segmentedShape = ListItemDefaults.segmentedShapes(index = 2, count = 10),
            onExpandedChange = { showDarkLevelDropdown = it },
            options = DarkLevel.entries.map { it to it.displayName() },
            isSelected = { it == darkLevel },
            onSelect = { viewModel.setDarkLevel(it); showDarkLevelDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.settings_layout),
            icon = Icons.Rounded.Dashboard,
            currentValue = layoutMode.displayName(),
            expanded = showLayoutDropdown,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 3, count = 10),
            onExpandedChange = { showLayoutDropdown = it },
            options = listOf(LayoutMode.ATTACHED, LayoutMode.SQUARE)
                .map { it to it.displayName() },
            isSelected = { it == layoutMode },
            onSelect = { viewModel.setLayoutMode(it); showLayoutDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.navigation_rail_style),
            icon = Icons.Rounded.Menu,
            currentValue = navigationRailStyle.displayName(),
            expanded = showNavigationRailStyle,
            onExpandedChange = { showNavigationRailStyle = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 4, count = 10),
            options = NavigationRailStyle.entries.map { it to it.displayName() },
            isSelected = { it == navigationRailStyle },
            onSelect = { viewModel.setNavigationRailStyle(it); showNavigationRailStyle = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.color_palette),
            icon = Icons.Rounded.Palette,
            currentValue = themePalette.displayName(),
            expanded = showPaletteDropdown,
            onExpandedChange = { showPaletteDropdown = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 5, count = 10),
            options = ThemePalette.entries.map { it to it.displayName() },
            isSelected = { it == themePalette },
            onSelect = { viewModel.setThemePalette(it); showPaletteDropdown = false },
            paletteItem = true,
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.ColorLens, null) },
            title = { Text(stringResource(Res.string.dynamic_colors)) },
            shapes = ListItemDefaults.segmentedShapes(index = 6, count = 10),
            colors = colors,
            state = dynamicColor,
            onCheckedChange = { viewModel.setDynamicColorFromArtwork(it) }
        )
        DropdownSelector(
            label = stringResource(Res.string.app_background_style),
            icon = Icons.Rounded.Wallpaper,
            currentValue = appBackgroundStyle.displayName(),
            expanded = showAppBackgroundDropdown,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 7, count = 10),
            onExpandedChange = { showAppBackgroundDropdown = it },
            options = BackgroundStyle.entries.map { it to it.displayName() },
            isSelected = { it == appBackgroundStyle },
            onSelect = { viewModel.setAppBackgroundStyle(it); showAppBackgroundDropdown = false },
        )
        val closestScale = scalePresets.minByOrNull { kotlin.math.abs(it - uiScale) } ?: 1.00f
        DropdownSelector(
            label = stringResource(Res.string.ui_scale),
            icon = Icons.Rounded.ZoomIn,
            currentValue = "${(closestScale * 100).roundToInt()}%",
            expanded = showUiScaleDropdown,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 8, count = 10),
            onExpandedChange = { showUiScaleDropdown = it },
            options = scalePresets.map { it to "${(it * 100).roundToInt()}%" },
            isSelected = { it == closestScale },
            onSelect = { viewModel.setUiScale(it); showUiScaleDropdown = false },
        )
        SystemFontSelector(
            selectedFont = selectedFont,
            onSelect = { viewModel.setSelectedFont(it) },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 9, count = 10),
        )
    }
}

/** Presets de escala de UI disponibles en el selector. */
private val scalePresets = listOf(0.75f, 0.80f, 0.90f, 1.00f, 1.10f, 1.20f, 1.30f, 1.50f)
