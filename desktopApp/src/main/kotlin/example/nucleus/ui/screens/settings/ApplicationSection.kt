package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.data.repository.AppLocale
import example.nucleus.data.repository.YouTubeRegion
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.viewmodels.ApplicationSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ApplicationSettingsGroup() {
    val colors = LocalSettingsColors.current
    val viewModel: ApplicationSettingsViewModel = koinInject()
    val currentLocale by viewModel.locale.collectAsState()
    val youtubeRegion by viewModel.youtubeRegion.collectAsState()
    val minimizeToTray by viewModel.minimizeToTray.collectAsState()
    val trimMemoryOnTray by viewModel.trimMemoryOnTray.collectAsState()
    val launchAtStartup by viewModel.launchAtStartup.collectAsState()
    val taskbarWidgetEnabled by viewModel.taskbarWidgetEnabled.collectAsState()

    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showRegionDropdown by remember { mutableStateOf(false) }

    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.section_application),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors
    ) {
        DropdownSelector(
            label = stringResource(Res.string.language),
            icon = Icons.Rounded.Language,
            currentValue = currentLocale.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 5),
            expanded = showLanguageDropdown,
            onExpandedChange = { showLanguageDropdown = it },
            options = AppLocale.entries.map { it to it.displayName() },
            isSelected = { it == currentLocale },
            onSelect = { viewModel.setLocale(it); showLanguageDropdown = false }
        )
        DropdownSelector(
            label = stringResource(Res.string.youtube_region),
            icon = Icons.Rounded.Public,
            currentValue = youtubeRegion.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 5),
            expanded = showRegionDropdown,
            onExpandedChange = { showRegionDropdown = it },
            options = YouTubeRegion.entries.map { it to it.displayName() },
            isSelected = { it == youtubeRegion },
            onSelect = { viewModel.setYoutubeRegion(it); showRegionDropdown = false }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.NotificationsActive, null) },
            title = { Text(stringResource(Res.string.minimize_to_tray)) },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 5),
            colors = colors,
            state = minimizeToTray,
            onCheckedChange = { viewModel.setMinimizeToTray(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.CleaningServices, null) },
            title = { Text(stringResource(Res.string.trim_memory_on_tray)) },
            subtitle = { Text(stringResource(Res.string.trim_memory_on_tray_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 3, count = 5),
            colors = colors,
            state = trimMemoryOnTray,
            onCheckedChange = { viewModel.setTrimMemoryOnTray(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.RocketLaunch, null) },
            title = { Text(stringResource(Res.string.launch_at_startup)) },
            subtitle = { Text(stringResource(Res.string.launch_at_startup_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 4, count = 6),
            colors = colors,
            state = launchAtStartup,
            onCheckedChange = { viewModel.setLaunchAtStartup(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.MusicNote, null) },
            title = { Text(stringResource(Res.string.taskbar_widget)) },
            subtitle = { Text(stringResource(Res.string.taskbar_widget_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 5, count = 6),
            colors = colors,
            state = taskbarWidgetEnabled,
            onCheckedChange = { viewModel.setTaskbarWidgetEnabled(it) }
        )
    }
}
