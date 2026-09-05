package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.data.AppDirs
import example.nucleus.logging.AppFileLogger
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.screens.shared.openFolder
import example.nucleus.viewmodels.AdvancedSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdvancedSettingsGroup(
    onShowClearDownloadsDialog: () -> Unit,
    onOpenJvmSettings: () -> Unit
) {
    val colors = LocalSettingsColors.current
    val viewModel: AdvancedSettingsViewModel = koinInject()
    val cacheImages by viewModel.cacheImages.collectAsState()
    val logToFile by viewModel.logToFile.collectAsState()
    val logVerbose by viewModel.logVerbose.collectAsState()

    LaunchedEffect(logToFile, logVerbose) {
        AppFileLogger.applyPreferences(logToFile = logToFile, verbose = logVerbose)
    }

    val itemCount = 8
    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.section_advanced),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors
    ) {
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Image, null) },
            title = { Text(stringResource(Res.string.cache_images)) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = itemCount),
            colors = colors,
            state = cacheImages,
            onCheckedChange = { viewModel.setCacheImages(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Description, null) },
            title = { Text(stringResource(Res.string.log_to_file)) },
            subtitle = { Text(stringResource(Res.string.log_to_file_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = itemCount),
            colors = colors,
            state = logToFile,
            onCheckedChange = { viewModel.setLogToFile(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.BugReport, null) },
            title = { Text(stringResource(Res.string.log_verbose)) },
            subtitle = { Text(stringResource(Res.string.log_verbose_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = itemCount),
            colors = colors,
            state = logVerbose,
            enabled = logToFile,
            onCheckedChange = { viewModel.setLogVerbose(it) }
        )
        ActionRow(
            label = stringResource(Res.string.open_logs_folder),
            icon = Icons.Rounded.FolderOpen,
            btnLabel = stringResource(Res.string.btn_open),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 3, count = itemCount),
            onClick = { openFolder(AppFileLogger.logsDirectory) }
        )
        ActionRow(
            label = stringResource(Res.string.clear_app_logs),
            icon = Icons.Rounded.DeleteSweep,
            btnLabel = stringResource(Res.string.btn_clear),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 4, count = itemCount),
            isDestructive = true,
            onClick = { AppFileLogger.clearLogs() }
        )
        ActionRow(
            label = stringResource(Res.string.open_data_folder),
            icon = Icons.Rounded.FolderOpen,
            btnLabel = stringResource(Res.string.btn_open),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 5, count = itemCount),
            onClick = { openFolder(AppDirs.dataRoot) }
        )
        ActionRow(
            label = stringResource(Res.string.clear_download_cache),
            icon = Icons.Rounded.DeleteSweep,
            btnLabel = stringResource(Res.string.btn_clear),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 6, count = itemCount),
            isDestructive = true,
            onClick = onShowClearDownloadsDialog
        )
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.AutoFixHigh, null) },
            enabled = false,
            shapes = ListItemDefaults.segmentedShapes(index = 7, count = itemCount),
            title = { Text(stringResource(Res.string.skiko_rendering)) },
            colors = colors,
            subtitle = { Text(stringResource(Res.string.render_api_restart)) },
            action = {
                IconButton(onClick = onOpenJvmSettings) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            },
            onClick = onOpenJvmSettings
        )
    }
}
