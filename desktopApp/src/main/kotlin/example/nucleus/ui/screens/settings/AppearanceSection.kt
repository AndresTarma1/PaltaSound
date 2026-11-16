package example.nucleus.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.components.glass.GlassAlertDialog
import example.nucleus.ui.components.glass.LocalLiquidGlass
import example.nucleus.ui.components.glass.LocalLiquidGlassCanToggleLive
import example.nucleus.ui.components.player.GlassTextPillButton
import example.nucleus.data.repository.*
import example.nucleus.viewmodels.AppearanceSettingsViewModel
import example.nucleus.generated.resources.*
import example.nucleus.ui.components.context.SongMenuAction
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt


private data class Preset(
    val label: String,
    val layout: LayoutMode,
    val scale: Float,
    val animations: Boolean,
)
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
    val themePaletteStyle by viewModel.themePaletteStyle.collectAsState()
    val customSeed by viewModel.customSeedColor.collectAsState()
    val dynamicColor by viewModel.dynamicColorFromArtwork.collectAsState()
    val navigationRailStyle by viewModel.navigationRailStyle.collectAsState()
    val appBackgroundStyle by viewModel.appBackgroundStyle.collectAsState()
    val liquidGlassEnabled by viewModel.liquidGlassEnabled.collectAsState()
    val canToggleLiquidGlassLive = LocalLiquidGlassCanToggleLive.current
    var showCustomColor by remember { mutableStateOf(false) }
    var showGlassRestartNotice by remember { mutableStateOf(false) }

    val glass = LocalLiquidGlass.current
    val layout by viewModel.layoutMode.collectAsState()
    val scale by viewModel.uiScale.collectAsState()
    val animations by viewModel.animationsEnabled.collectAsState()

    val presets = listOf(
        Preset(stringResource(Res.string.preset_compact), LayoutMode.SQUARE, 0.90f, false),
        Preset(stringResource(Res.string.preset_comfortable), LayoutMode.ATTACHED, 1.00f, true),
        Preset(stringResource(Res.string.preset_accessible), LayoutMode.ATTACHED, 1.20f, true),
    )

    Row(
        modifier = Modifier.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(
            if (glass) 8.dp else ButtonGroupDefaults.ConnectedSpaceBetween
        ),
    ) {
        presets.forEachIndexed { index, p ->
            val selected = layout == p.layout && scale == p.scale && animations == p.animations
            val apply = {
                viewModel.setLayoutMode(p.layout)
                viewModel.setUiScale(p.scale)
                viewModel.setAnimationsEnabled(p.animations)
            }

            if (glass) {
                GlassTextPillButton(
                    onClick = apply,
                    text = p.label,
                    modifier = Modifier.weight(1f),
                    fallbackContainer = MaterialTheme.colorScheme.primaryContainer,
                    fallbackContent = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                ToggleButton(
                    checked = selected,
                    onCheckedChange = { apply() },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        presets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                    modifier = Modifier.semantics { role = Role.RadioButton },
                ) {
                    if (selected) {
                        Icon(Icons.Filled.Check, null, Modifier.size(ToggleButtonDefaults.IconSize))
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    }
                    Text(p.label, maxLines = 1)
                }
            }
        }
    }

    var showThemeDropdown by remember { mutableStateOf(false) }
    var showDarkLevelDropdown by remember { mutableStateOf(false) }
    var showLayoutDropdown by remember { mutableStateOf(false) }
    var showCornerStyleDropdown by remember { mutableStateOf(false) }
    var showAppBackgroundDropdown by remember { mutableStateOf(false) }
    var showNavigationRailStyle by remember { mutableStateOf(false) }
    SettingsGroup(
        title = { Text(stringResource(Res.string.appearance_theme), style = MaterialTheme.typography.titleMediumEmphasized) },
        colors = colors,
    ) {
        DropdownSelector(
            label = stringResource(Res.string.theme),
            icon = Icons.Rounded.DarkMode,
            currentValue = themeMode.displayName(),
            expanded = showThemeDropdown,
            onExpandedChange = { showThemeDropdown = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 5),
            options = ThemeMode.entries.map { it to it.displayName() },
            isSelected = { it == themeMode },
            onSelect = { viewModel.setThemeMode(it); showThemeDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.settings_dark_level),
            icon = Icons.Rounded.Contrast,
            currentValue = darkLevel.displayName(),
            expanded = showDarkLevelDropdown,
            onExpandedChange = { showDarkLevelDropdown = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 5),
            options = DarkLevel.entries.map { it to it.displayName() },
            isSelected = { it == darkLevel },
            onSelect = { viewModel.setDarkLevel(it); showDarkLevelDropdown = false },
        )
        SingleChoiceRow(
            label = stringResource(Res.string.color_palette),
            icon = Icons.Rounded.Palette,
            currentValue = if (customSeed != 0) {
                "#" + "%06X".format(0xFFFFFF and customSeed)
            } else {
                themePalette.displayName()
            },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 2, count = 6),
            options = ThemePalette.entries.map { it to it.displayName() },
            isSelected = { it == themePalette },
            onSelect = { viewModel.setThemePalette(it) },
            swatchFor = { Color(it.primary) },
            enabled = customSeed == 0,
        )
        SingleChoiceRow(
            label = stringResource(Res.string.palette_style),
            icon = Icons.Rounded.Style,
            currentValue = themePaletteStyle.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 3, count = 6),
            options = ThemePaletteStyle.entries.map { it to it.displayName() },
            isSelected = { it == themePaletteStyle },
            onSelect = { viewModel.setThemePaletteStyle(it) },
        )
        // Color libre: abre el picker (presets + RGB + hex). Si hay custom,
        // manda sobre la paleta de arriba.
        CustomColorRow(
            currentSeed = customSeed,
            fallbackColor = Color(themePalette.primary),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 4, count = 6),
            onOpen = { showCustomColor = true },
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.ColorLens, null) },
            title = { Text(stringResource(Res.string.dynamic_colors)) },
            shapes = ListItemDefaults.segmentedShapes(index = 5, count = 6),
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
            onExpandedChange = { showLayoutDropdown = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 5),
            options = listOf(LayoutMode.ATTACHED, LayoutMode.SQUARE).map { it to it.displayName() },
            isSelected = { it == layoutMode },
            onSelect = { viewModel.setLayoutMode(it); showLayoutDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.settings_corner_style),
            icon = Icons.Rounded.RoundedCorner,
            currentValue = cornerStyle.displayName(),
            expanded = showCornerStyleDropdown,
            onExpandedChange = { showCornerStyleDropdown = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 5),
            options = CornerStyle.entries.map { it to it.displayName() },
            isSelected = { it == cornerStyle },
            onSelect = { viewModel.setCornerStyle(it); showCornerStyleDropdown = false },
        )
        DropdownSelector(
            label = stringResource(Res.string.app_background_style),
            icon = Icons.Rounded.Wallpaper,
            currentValue = if (liquidGlassEnabled) {
                stringResource(Res.string.background_locked_by_glass)
            } else {
                appBackgroundStyle.displayName()
            },
            expanded = showAppBackgroundDropdown,
            onExpandedChange = { showAppBackgroundDropdown = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 2, count = 5),
            options = BackgroundStyle.entries.map { it to it.displayName() },
            isSelected = { it == appBackgroundStyle },
            onSelect = { viewModel.setAppBackgroundStyle(it); showAppBackgroundDropdown = false },
            enabled = !liquidGlassEnabled,
        )
        DropdownSelector(
            label = stringResource(Res.string.navigation_rail_style),
            icon = Icons.Rounded.Menu,
            currentValue = if (liquidGlassEnabled) {
                stringResource(Res.string.navigation_rail_fab)
            } else {
                navigationRailStyle.displayName()
            },
            expanded = showNavigationRailStyle,
            onExpandedChange = { showNavigationRailStyle = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 3, count = 5),
            options = NavigationRailStyle.entries.map { it to it.displayName() },
            isSelected = { it == navigationRailStyle },
            onSelect = { viewModel.setNavigationRailStyle(it); showNavigationRailStyle = false },
            enabled = !liquidGlassEnabled,
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.BlurOn, null) },
            title = { Text(stringResource(Res.string.liquid_glass_beta)) },
            subtitle = {
                Text(
                    if (liquidGlassEnabled && !canToggleLiquidGlassLive) {
                        stringResource(Res.string.liquid_glass_restart_required)
                    } else {
                        stringResource(Res.string.liquid_glass_beta_subtitle)
                    }
                )
            },
            shapes = ListItemDefaults.segmentedShapes(index = 4, count = 5),
            colors = colors,
            state = liquidGlassEnabled,
            onCheckedChange = { enabled ->
                // El cristal solo rinde sobre fondo sólido: al activarlo se fija
                // y el selector de fondo queda bloqueado (ver DropdownSelector).
                if (enabled) viewModel.setAppBackgroundStyle(BackgroundStyle.SOLID_COLOR)
                viewModel.setLiquidGlassEnabled(enabled)
                // Haze Glass compila sus shaders en el primer frame que dibuja
                // cristal. Si este proceso arrancó en material, activarlo en
                // caliente congelaría la ventana: se aplica al reiniciar.
                if (enabled && !canToggleLiquidGlassLive) {
                    showGlassRestartNotice = true
                }
            }
        )

        AnimatedVisibility(
            visible = liquidGlassEnabled
        ){
            LiquidGlassSettings()
        }
    }

    if (showCustomColor) {
        val initial = if (customSeed != 0) Color(customSeed) else Color(themePalette.primary)
        CustomColorDialog(
            initial = initial,
            hasCustom = customSeed != 0,
            onSave = { viewModel.setCustomSeedColor(it.toArgb()) },
            onReset = { viewModel.clearCustomSeedColor() },
            onDismiss = { showCustomColor = false },
        )
    }

    if (showGlassRestartNotice) {
        GlassAlertDialog(
            onDismissRequest = { showGlassRestartNotice = false },
            title = { Text(stringResource(Res.string.liquid_glass_restart_title)) },
            text = { Text(stringResource(Res.string.liquid_glass_restart_message)) },
            confirmButton = {
                TextButton(onClick = { showGlassRestartNotice = false }) {
                    Text(stringResource(Res.string.liquid_glass_restart_confirm))
                }
            },
        )
    }
}

/** Fila del color libre: muestra el custom o el de la paleta, abre el picker. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomColorRow(
    currentSeed: Int,
    fallbackColor: Color,
    segmentedShape: ListItemShapes,
    onOpen: () -> Unit,
) {
    val colors = LocalSettingsColors.current
    val current = if (currentSeed != 0) Color(currentSeed) else fallbackColor
    SettingsMenuLink(
        icon = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(current)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
        title = { Text(stringResource(Res.string.custom_color)) },
        subtitle = {
            Text(
                if (currentSeed != 0) "#" + "%06X".format(0xFFFFFF and currentSeed)
                else stringResource(Res.string.palette_default)
            )
        },
        shapes = segmentedShape,
        colors = colors,
        action = {
            IconButton(onClick = onOpen) {
                Icon(Icons.Rounded.ChevronRight, null)
            }
        },
        onClick = onOpen,
    )
}

/** Picker: presets + sliders RGB + hex. Los presets aplican al acto; RGB/hex con Guardar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomColorDialog(
    initial: Color,
    hasCustom: Boolean,
    onSave: (Color) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var hex by remember { mutableStateOf("#" + "%06X".format(0xFFFFFF and initial.toArgb())) }
    var r by remember { mutableFloatStateOf(((initial.toArgb() shr 16) and 0xFF).toFloat()) }
    var g by remember { mutableFloatStateOf(((initial.toArgb() shr 8) and 0xFF).toFloat()) }
    var b by remember { mutableFloatStateOf((initial.toArgb() and 0xFF).toFloat()) }

    fun push(c: Color) {
        draft = c
        val argb = c.toArgb()
        r = ((argb shr 16) and 0xFF).toFloat()
        g = ((argb shr 8) and 0xFF).toFloat()
        b = (argb and 0xFF).toFloat()
        hex = "#" + "%06X".format(0xFFFFFF and argb)
    }

    ResponsiveSettingsDialog(
        onDismiss = onDismiss,
        icon = Icons.Rounded.Palette,
        title = stringResource(Res.string.custom_color),
    ) {
        // Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(AppShapes.large)
                .background(draft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "#" + "%06X".format(0xFFFFFF and draft.toArgb()),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        // Presets como punto de partida
        Text(
            stringResource(Res.string.color_palette),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ThemePalette.entries.chunked(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { preset ->
                    val c = Color(preset.primary)
                    val selected = draft.toArgb() == c.toArgb()
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable { push(c) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        // RGB
        @Composable
        fun ChannelRow(label: String, value: Float, tint: Color, onChange: (Float) -> Unit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp),
                )
                Slider(
                    value = value,
                    onValueChange = {
                        onChange(it)
                        push(Color(red = r.toInt(), green = g.toInt(), blue = b.toInt()))
                    },
                    valueRange = 0f..255f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
                )
                Text(
                    value.toInt().toString(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(36.dp),
                )
            }
        }
        ChannelRow("R", r, Color(0xFFE53935)) { r = it }
        ChannelRow("G", g, Color(0xFF43A047)) { g = it }
        ChannelRow("B", b, Color(0xFF1E88E5)) { b = it }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = {
                hex = it
                parseHexColor(it)?.let { push(it) }
            },
            label = { Text(stringResource(Res.string.custom_color_hex)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (hasCustom) {
                TextButton(onClick = onReset) { Text(stringResource(Res.string.reset)) }
            }
            Button(onClick = { onSave(draft) }) { Text(stringResource(Res.string.btn_save)) }
        }
    }
}

private fun parseHexColor(text: String): Color? {
    val clean = text.trim().removePrefix("#").removePrefix("0x")
    if (clean.length != 6 && clean.length != 8) return null
    val value = clean.toLongOrNull(16) ?: return null
    val argb = if (clean.length == 6) (0xFF000000L or value).toInt() else value.toInt()
    return Color(argb)
}


