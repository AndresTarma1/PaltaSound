package example.nucleus.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.overlay.HotkeyCombo.Companion.DEFAULT
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.viewmodels.OverlaySettingsViewModel
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverlaySettingsGroup(
    onOpenCapture: () -> Unit
) {
    val colors = LocalSettingsColors.current
    val viewModel: OverlaySettingsViewModel = koinInject()
    val overlayHotkeyEnabled by viewModel.overlayHotkeyEnabled.collectAsState()
    val overlayHotkeyLabel by viewModel.overlayHotkeyLabel.collectAsState()
    val defaultHotkeyLabel = remember { DEFAULT.label() }

    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.section_overlay),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors,
    ) {
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.VideogameAsset, null) },
            title = { Text(stringResource(Res.string.overlay_enable)) },
            subtitle = { Text(stringResource(Res.string.overlay_enable_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
            enabled = false,
            colors = colors,
            state = overlayHotkeyEnabled,
            onCheckedChange = { viewModel.setOverlayHotkeyEnabled(it) }
        )

        AnimatedVisibility(
            visible = overlayHotkeyEnabled,
            enter = if (LocalAnimationsEnabled.current) fadeIn() else EnterTransition.None,
            exit = if (LocalAnimationsEnabled.current) fadeOut() else ExitTransition.None,
        ){
            SettingsMenuLink(
                icon = { Icon(Icons.Rounded.Keyboard, null) },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                title = { Text(stringResource(Res.string.overlay_shortcut)) },
                subtitle = { Text(overlayHotkeyLabel.ifBlank { defaultHotkeyLabel }) },
                colors = colors,
                action = {
                    FilledTonalButton(onClick = onOpenCapture) {
                        Text(stringResource(Res.string.overlay_shortcut_set))
                    }
                },
                onClick = onOpenCapture
            )

        }
    }
}
