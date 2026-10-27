package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.viewmodels.AppearanceSettingsViewModel
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccessibilitySettingsGroup() {
    val colors = LocalSettingsColors.current
    val viewModel: AppearanceSettingsViewModel = koinInject()
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()
    val uiScale by viewModel.uiScale.collectAsState()
    val selectedFont by viewModel.selectedFont.collectAsState()

    var showUiScaleDropdown by remember { mutableStateOf(false) }

    SettingsGroup(
        title = { Text(stringResource(Res.string.appearance_motion), style = MaterialTheme.typography.titleMediumEmphasized) },
        colors = colors,
    ) {
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Animation, null) },
            title = { Text(stringResource(Res.string.animations_enabled)) },
            subtitle = { Text(stringResource(Res.string.animations_enabled_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            colors = colors,
            state = animationsEnabled,
            onCheckedChange = { viewModel.setAnimationsEnabled(it) }
        )
    }

    SettingsGroup(
        title = { Text(stringResource(Res.string.appearance_typography), style = MaterialTheme.typography.titleMediumEmphasized) },
        colors = colors,
    ) {
        SystemFontSelector(
            selectedFont = selectedFont,
            onSelect = { viewModel.setSelectedFont(it) },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 2),
        )
        val closestScale = scalePresets.minByOrNull { kotlin.math.abs(it - uiScale) } ?: 1.00f
        var showScale by remember { mutableStateOf(false) }
        DropdownSelector(
            label = stringResource(Res.string.ui_scale),
            icon = Icons.Rounded.ZoomIn,
            currentValue = "${(closestScale * 100).roundToInt()}%",
            expanded = showScale,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 2),
            onExpandedChange = { showScale = it },
            options = scalePresets.map { it to "${(it * 100).roundToInt()}%" },
            isSelected = { it == closestScale },
            onSelect = { viewModel.setUiScale(it); showScale = false },
        )
    }
}

private val scalePresets = listOf(0.75f, 0.80f, 0.90f, 1.00f, 1.10f, 1.20f, 1.30f, 1.50f)
