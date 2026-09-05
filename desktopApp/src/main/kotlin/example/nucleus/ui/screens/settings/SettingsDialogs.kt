package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import example.nucleus.overlay.GlobalHotkeyManager
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.components.EqualizerDialog
import example.nucleus.ui.screens.AdvancedJvmSettingsScreen
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.viewmodels.AudioSettingsViewModel
import example.nucleus.viewmodels.JvmSettingsViewModel
import example.nucleus.viewmodels.MiniPlayerSettingsViewModel
import example.nucleus.viewmodels.OverlaySettingsViewModel
import example.nucleus.viewmodels.SyncSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Diálogos que puede abrir la pantalla de Ajustes. */
enum class SettingsDialog {
    OVERLAY_CAPTURE,
    YTM_SYNC_WARNING,
    CLEAR_DOWNLOADS,
    EQUALIZER,
    SEEK_BAR_STYLE,
    JVM_SETTINGS,
}

@Composable
fun SettingsDialogsHost(
    dialog: SettingsDialog?,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        SettingsDialog.OVERLAY_CAPTURE -> OverlayCaptureDialog(onDismiss = onDismiss)
        SettingsDialog.YTM_SYNC_WARNING -> YtmSyncWarningDialog(onDismiss = onDismiss)
        SettingsDialog.CLEAR_DOWNLOADS -> ClearDownloadsDialog(onDismiss = onDismiss)
        SettingsDialog.EQUALIZER -> EqualizerDialogHost(onDismiss = onDismiss)
        SettingsDialog.SEEK_BAR_STYLE -> SeekBarStyleDialog(onDismiss = onDismiss)
        SettingsDialog.JVM_SETTINGS -> JvmSettingsDialog(onDismiss = onDismiss)
        null -> Unit
    }
}

@Composable
private fun OverlayCaptureDialog(onDismiss: () -> Unit) {
    val viewModel: OverlaySettingsViewModel = koinInject()
    val hotkeyManager: GlobalHotkeyManager = koinInject()
    DisposableEffect(Unit) {
        hotkeyManager.beginCapture { combo ->
            viewModel.setOverlayHotkey(combo.keyCode, combo.modsMask, combo.label())
            onDismiss()
        }
        onDispose { hotkeyManager.cancelCapture() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        icon = { Icon(Icons.Rounded.Keyboard, null) },
        title = { Text(stringResource(Res.string.overlay_capture_title)) },
        text = { Text(stringResource(Res.string.overlay_capture_message)) },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun YtmSyncWarningDialog(onDismiss: () -> Unit) {
    val viewModel: SyncSettingsViewModel = koinInject()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        icon = { Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Res.string.ytm_sync_warning_title)) },
        text = { Text(stringResource(Res.string.ytm_sync_warning_message)) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.setYtmSyncEnabled(true)
                onDismiss()
            }) { Text(stringResource(Res.string.ytm_sync_warning_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun ClearDownloadsDialog(onDismiss: () -> Unit) {
    val downloadViewModel = LocalDownloadViewModel.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        title = { Text(stringResource(Res.string.clear_downloads_title)) },
        text = { Text(stringResource(Res.string.clear_downloads_message)) },
        confirmButton = {
            TextButton(onClick = { downloadViewModel.clearCache(); onDismiss() }) {
                Text(stringResource(Res.string.btn_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun EqualizerDialogHost(onDismiss: () -> Unit) {
    val viewModel: AudioSettingsViewModel = koinInject()
    val equalizerBands by viewModel.equalizerBands.collectAsState()
    EqualizerDialog(
        onDismiss = onDismiss,
        bands = equalizerBands,
        onBandsChange = { viewModel.setEqualizerBands(it) }
    )
}

@Composable
private fun SeekBarStyleDialog(onDismiss: () -> Unit) {
    val viewModel: MiniPlayerSettingsViewModel = koinInject()
    val seekBarStyle by viewModel.seekBarStyle.collectAsState()
    ResponsiveSettingsDialog(
        onDismiss = onDismiss,
        icon = Icons.AutoMirrored.Rounded.ShowChart,
        title = stringResource(Res.string.seek_bar_style_title),
    ) {
        SeekBarStylePickerContent(
            current = seekBarStyle,
            onSelect = { viewModel.setSeekBarStyle(it) },
        )
    }
}

@Composable
private fun JvmSettingsDialog(onDismiss: () -> Unit) {
    val jvmSettingsViewModel: JvmSettingsViewModel = koinInject()
    AdvancedJvmSettingsScreen(
        viewModel = jvmSettingsViewModel,
        onDismiss = onDismiss,
    )
}
