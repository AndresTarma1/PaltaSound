package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
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
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 3),
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
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 3),
            expanded = showLoudnessDropdown,
            onExpandedChange = { showLoudnessDropdown = it },
            options = LoudnessLevel.entries.map { it to it.displayName() },
            isSelected = { it == loudnessLevel },
            onSelect = { viewModel.setLoudnessLevel(it); showLoudnessDropdown = false }
        )
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.GraphicEq, null) },
            colors = colors,
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
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
