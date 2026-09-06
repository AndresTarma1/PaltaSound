package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.data.repository.AudioQuality
import example.nucleus.data.repository.LoudnessLevel
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.viewmodels.AudioSettingsViewModel
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioSettingsGroup(
    onOpenEqualizer: () -> Unit
) {
    val colors = LocalSettingsColors.current
    val viewModel: AudioSettingsViewModel = koinInject()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val loudnessLevel by viewModel.loudnessLevel.collectAsState()
    val sabrEnabled by viewModel.sabrEnabled.collectAsState()
    var showAudioDropdown by remember { mutableStateOf(false) }
    var showLoudnessDropdown by remember { mutableStateOf(false) }

    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.section_audio),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors,
    ) {
        DropdownSelector(
            label = stringResource(Res.string.streaming_quality),
            icon = Icons.Rounded.Tune,
            currentValue = audioQuality.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 4),
            expanded = showAudioDropdown,
            onExpandedChange = { showAudioDropdown = it },
            options = AudioQuality.entries.map { it to it.displayName() },
            isSelected = { it == audioQuality },
            onSelect = { viewModel.setAudioQuality(it); showAudioDropdown = false }
        )
        DropdownSelector(
            label = stringResource(Res.string.loudness_level),
            icon = Icons.Rounded.VolumeUp,
            currentValue = loudnessLevel.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 4),
            expanded = showLoudnessDropdown,
            onExpandedChange = { showLoudnessDropdown = it },
            options = LoudnessLevel.entries.map { it to it.displayName() },
            isSelected = { it == loudnessLevel },
            onSelect = { viewModel.setLoudnessLevel(it); showLoudnessDropdown = false }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Bolt, null) },
            title = { Text(stringResource(Res.string.sabr_streaming)) },
            subtitle = { Text(stringResource(Res.string.sabr_streaming_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
            colors = colors,
            state = sabrEnabled,
            onCheckedChange = { viewModel.setSabrEnabled(it) }
        )
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.GraphicEq, null) },
            colors = colors,
            shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
            title = { Text(stringResource(Res.string.equalizer)) },
            action = {
                IconButton(onClick = onOpenEqualizer) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            },
            subtitle = { Text(stringResource(Res.string.ten_bands)) },
            onClick = onOpenEqualizer
        )
    }
}
