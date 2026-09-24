package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.viewmodels.NowPlayingSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.data.repository.LyricsAnimationStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingSettingsGroup() {
    val colors = LocalSettingsColors.current
    val viewModel: NowPlayingSettingsViewModel = koinInject()
    val highResCover by viewModel.highResCoverArt.collectAsState()
    val imagesEnabled by viewModel.imagesEnabled.collectAsState()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsState()

    val lyricsTextSize by viewModel.lyricsTextSize.collectAsState()
    val lyricsLineSpacing by viewModel.lyricsLineSpacing.collectAsState()
    val lyricsAnimationStyle by viewModel.lyricsAnimationStyle.collectAsState()
    val lyricsRomanize by viewModel.lyricsRomanize.collectAsState()
    val lyricsOffsetMs by viewModel.lyricsOffsetMs.collectAsState()
    val queuePersistenceEnabled by viewModel.queuePersistenceEnabled.collectAsState()
    var showAnimationDropdown by remember { mutableStateOf(false) }
    var showSizeDropdown by remember { mutableStateOf(false) }
    var showSpacingDropdown by remember { mutableStateOf(false) }
    var showOffsetDropdown by remember { mutableStateOf(false) }

    val itemCount = 7
    var idx = 0

    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.section_now_playing),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors,
    ) {
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.HighQuality, null) },
            title = { Text(stringResource(Res.string.high_res_artwork)) },
            subtitle = { Text(stringResource(Res.string.high_res_artwork_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
            colors = colors,
            state = highResCover,
            onCheckedChange = { viewModel.setHighResCoverArt(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Image, null) },
            title = { Text(stringResource(Res.string.show_images)) },
            subtitle = { Text(stringResource(Res.string.show_images_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
            colors = colors,
            state = imagesEnabled,
            onCheckedChange = { viewModel.setImagesEnabled(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Shuffle, null) },
            subtitle = { Text(stringResource(Res.string.crossfade_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
            title = { Text(stringResource(Res.string.crossfade)) },
            colors = colors,
            state = crossfadeEnabled,
            onCheckedChange = { viewModel.setCrossfadeEnabled(it) }
        )
        DropdownSelector(
            label = stringResource(Res.string.lyrics_animation_style),
            icon = Icons.Rounded.AutoAwesome,
            currentValue = lyricsAnimationStyle.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
            expanded = showAnimationDropdown,
            onExpandedChange = { showAnimationDropdown = it },
            options = LyricsAnimationStyle.entries.map { it to it.displayName() },
            isSelected = { it == lyricsAnimationStyle },
            onSelect = { viewModel.setLyricsAnimationStyle(it); showAnimationDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.lyrics_text_size),
            icon = Icons.Rounded.FormatSize,
            currentValue = "${lyricsTextSize.roundToInt()} sp",
            segmentedShape = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
            expanded = showSizeDropdown,
            onExpandedChange = { showSizeDropdown = it },
            options = listOf(28f, 32f, 36f, 40f, 44f, 48f, 52f).map { it to "${it.roundToInt()} sp" },
            isSelected = { it == lyricsTextSize },
            onSelect = { viewModel.setLyricsTextSize(it); showSizeDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.lyrics_line_spacing),
            icon = Icons.Rounded.TextFields,
            currentValue = "${lyricsLineSpacing.roundToInt()} sp",
            segmentedShape = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
            expanded = showSpacingDropdown,
            onExpandedChange = { showSpacingDropdown = it },
            options = listOf(40f, 46f, 54f, 60f, 68f).map { it to "${it.roundToInt()} sp" },
            isSelected = { it == lyricsLineSpacing },
            onSelect = { viewModel.setLyricsLineSpacing(it); showSpacingDropdown = false },
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Translate, null) },
            title = { Text(stringResource(Res.string.lyrics_romanize)) },
            subtitle = { Text(stringResource(Res.string.lyrics_romanize_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
            colors = colors,
            state = lyricsRomanize,
            onCheckedChange = { viewModel.setLyricsRomanize(it) }
        )
        val offsetOptions = listOf(
            -2000, -1500, -1000, -500, -250, 0, 250, 500, 1000, 1500, 2000
        )
        DropdownSelector(
            label = stringResource(Res.string.lyrics_offset_settings),
            icon = Icons.Rounded.Timer,
            currentValue = stringResource(Res.string.lyrics_offset_value, lyricsOffsetMs),
            segmentedShape = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
            expanded = showOffsetDropdown,
            onExpandedChange = { showOffsetDropdown = it },
            options = offsetOptions.map { it to stringResource(Res.string.lyrics_offset_value, it) },
            isSelected = { it == lyricsOffsetMs },
            onSelect = { viewModel.setLyricsOffsetMs(it); showOffsetDropdown = false },
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Save, null) },
            title = { Text(stringResource(Res.string.queue_persistence)) },
            subtitle = { Text(stringResource(Res.string.queue_persistence_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = idx++, count = itemCount),
            colors = colors,
            state = queuePersistenceEnabled,
            onCheckedChange = { viewModel.setQueuePersistenceEnabled(it) }
        )
    }
}
