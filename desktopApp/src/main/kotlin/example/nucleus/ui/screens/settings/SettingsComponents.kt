@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import example.nucleus.data.repository.SeekBarStyle
import example.nucleus.data.repository.ThemePalette
import example.nucleus.ui.components.PlayerSeekBar
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.systemFontFamily
import example.nucleus.ui.themes.systemFontNames
import example.nucleus.generated.resources.*
import example.nucleus.viewmodels.AppViewModel
import org.jetbrains.compose.resources.stringResource

/** Paleta de colores compartida por todas las secciones de Ajustes. */
val LocalSettingsColors = staticCompositionLocalOf<ListItemColors> {
    error("LocalSettingsColors no proporcionado")
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberSettingsListItemColors(): ListItemColors {
    val scheme = MaterialTheme.colorScheme
    // Contenedor activo sólido (sin alpha): el tinte al 55% sobre surface hacía
    // que onPrimaryContainer quedara oscuro/apagado y el subtítulo ilegible.
    val selectedContainer = scheme.secondaryContainer
    val onSelected = scheme.onSecondaryContainer
    val selectedAccent = scheme.secondary

    return ListItemDefaults.segmentedColors(
        // Estado base
        containerColor = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        leadingContentColor = scheme.onSurfaceVariant,
        trailingContentColor = scheme.onSurfaceVariant,
        overlineContentColor = scheme.onSurfaceVariant,
        supportingContentColor = scheme.onSurfaceVariant,

        // Activo / checked — secondaryContainer M3 sólido + onSecondaryContainer opaco
        selectedContainerColor = selectedContainer,
        selectedContentColor = onSelected,
        selectedLeadingContentColor = selectedAccent,
        selectedTrailingContentColor = selectedAccent,
        selectedOverlineContentColor = onSelected,
        selectedSupportingContentColor = onSelected,

        // Dragged — tertiary, distinto del seleccionado
        draggedContainerColor = scheme.tertiaryContainer,
        draggedContentColor = scheme.onTertiaryContainer,
        draggedLeadingContentColor = scheme.tertiary,
        draggedTrailingContentColor = scheme.tertiary,
        draggedOverlineContentColor = scheme.onTertiaryContainer,
        draggedSupportingContentColor = scheme.onTertiaryContainer,

        // Deshabilitado — contenedor opaco; texto al 38% (estándar M3)
        disabledContainerColor = scheme.surfaceContainerLow,
        disabledContentColor = scheme.onSurface.copy(alpha = 0.38f),
        disabledLeadingContentColor = scheme.onSurface.copy(alpha = 0.38f),
        disabledTrailingContentColor = scheme.onSurface.copy(alpha = 0.38f),
        disabledOverlineContentColor = scheme.onSurface.copy(alpha = 0.38f),
        disabledSupportingContentColor = scheme.onSurface.copy(alpha = 0.38f),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> DropdownSelector(
    label: String,
    icon: ImageVector,
    currentValue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<T, String>>,
    segmentedShape: ListItemShapes = ListItemDefaults.shapes(),
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    paletteItem: Boolean = false,
) {
    val colors = LocalSettingsColors.current
    Box {
        SettingsMenuLink(
            icon = { Icon(icon, null) },
            title = { Text(label) },
            subtitle = { Text(currentValue) },
            shapes = segmentedShape,
            colors = colors,
            action = {
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(Icons.Rounded.ChevronRight, null)
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { onExpandedChange(false) },
                        offset = DpOffset(x = 16.dp, y = 0.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                    ) {
                        options.forEach { (value, displayName) ->
                            DropdownMenuItem(
                                text = { Text(displayName) },
                                onClick = {
                                    onSelect(value)
                                    onExpandedChange(false)
                                },
                                leadingIcon = {
                                    if (paletteItem && value is ThemePalette) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(value.primary))
                                        )
                                    } else if (isSelected(value)) {
                                        Icon(
                                            Icons.Rounded.Check, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            },
            onClick = { onExpandedChange(!expanded) }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SystemFontSelector(
    selectedFont: String,
    onSelect: (String) -> Unit,
    segmentedShape: ListItemShapes = ListItemDefaults.shapes(),
) {
    val colors = LocalSettingsColors.current
    val systemFonts = remember { systemFontNames() }
    val currentLabel = selectedFont.ifBlank {
        stringResource(Res.string.font_system_default)
    }
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.FontDownload, null) },
            title = { Text(stringResource(Res.string.font)) },
            subtitle = { Text(currentLabel) },
            shapes = segmentedShape,
            colors = colors,
            action = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Rounded.ChevronRight, null)
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        offset = DpOffset(x = 16.dp, y = 0.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.font_system_default)) },
                            onClick = { onSelect(""); expanded = false },
                            leadingIcon = {
                                if (selectedFont.isBlank()) {
                                    Icon(
                                        Icons.Rounded.Check, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            systemFonts.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name, fontFamily = systemFontFamily(name)) },
                                    onClick = { onSelect(name); expanded = false },
                                    leadingIcon = {
                                        if (name == selectedFont) {
                                            Icon(
                                                Icons.Rounded.Check, null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            },
            onClick = { expanded = !expanded }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ActionRow(
    label: String,
    icon: ImageVector,
    btnLabel: String,
    subtitle: String? = null,
    segmentedShape: ListItemShapes = ListItemDefaults.shapes(),
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalSettingsColors.current
    SettingsMenuLink(
        icon = { Icon(icon, null, tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        title = { Text(label) },
        subtitle = subtitle?.let { { Text(it) } },
        shapes = segmentedShape,
        colors = colors,
        action = {
            TextButton(onClick = onClick) {
                Text(btnLabel)
            }
        },
        onClick = onClick
    )
}

@Composable
internal fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.xLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(AppShapes.large)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MusicNote, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.about_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.about_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = AppShapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 1.dp,
            ) {
                Text(
                    text = stringResource(Res.string.version_prefix) + AppViewModel.CURRENT_VERSION,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun SeekBarStylePickerContent(
    current: SeekBarStyle,
    onSelect: (SeekBarStyle) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SeekBarStyle.entries.forEach { style ->
            val selected = style == current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelect(style) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        style.displayName(),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                PlayerSeekBar(
                    style = style,
                    value = 0.42f,
                    onValueChange = {},
                    onValueChangeFinished = {},
                    modifier = Modifier.fillMaxWidth(),
                    isPlaying = true,
                    enabled = false,
                )
            }
        }
    }
}

@Composable
internal fun ResponsiveSettingsDialog(
    onDismiss: () -> Unit,
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints {
            val maxWidth = maxWidth
            val maxHeight = maxHeight
            val dialogWidth = (maxWidth * 0.9f).coerceAtMost(480.dp)
            val maxDialogHeight = maxHeight * 0.85f

            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = maxDialogHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, stringResource(Res.string.close_label))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        content = content,
                    )
                }
            }
        }
    }
}
