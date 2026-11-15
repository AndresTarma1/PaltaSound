package example.nucleus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.layout.AppScreenContentHorizontal
import example.nucleus.ui.components.layout.AppScrollbarGutter
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.layout.appScrollContentPadding
import example.nucleus.ui.screens.settings.*
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private enum class SettingsCategory(
    val icon: ImageVector,
    val titleRes: org.jetbrains.compose.resources.StringResource,
    val descRes: org.jetbrains.compose.resources.StringResource,
) {
    APPEARANCE(Icons.Rounded.Palette, Res.string.section_appearance, Res.string.section_appearance_subtitle),
    ACCESSIBILITY(Icons.Rounded.Accessibility, Res.string.section_accessibility, Res.string.section_accessibility_subtitle),
    AUDIO(Icons.Rounded.GraphicEq, Res.string.section_audio, Res.string.section_audio_subtitle),
    PLAYBACK(Icons.Rounded.PlayCircle, Res.string.section_playback, Res.string.section_playback_subtitle),
    LIBRARY(Icons.Rounded.LibraryMusic, Res.string.section_library, Res.string.section_library_subtitle),
    SYSTEM(Icons.Rounded.Settings, Res.string.section_system, Res.string.section_system_subtitle),
    ABOUT(Icons.Rounded.Info, Res.string.section_about, Res.string.section_about_subtitle),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen() {
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    // Sin selección solo se ve la lista; al elegir aparece el detalle.
    var selected by remember { mutableStateOf<SettingsCategory?>(null) }

    CompositionLocalProvider(LocalSettingsColors provides rememberSettingsListItemColors()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth < 900.dp) {
                NarrowSettingsLayout(
                    selected = selected,
                    onSelect = { selected = it },
                    onBack = { selected = null },
                    onDialog = { activeDialog = it },
                )
            } else {
                WideSettingsLayout(
                    selected = selected,
                    onSelect = { selected = it },
                    onDialog = { activeDialog = it },
                )
            }
        }

        SettingsDialogsHost(
            dialog = activeDialog,
            onDismiss = { activeDialog = null },
        )
    }
}

/**
 * Transición al cambiar de categoría de Ajustes.
 *
 * [AnimatedContent] trae `fadeIn togetherWith fadeOut` por defecto, así que quitar el
 * `transitionSpec` no elimina la animación: hay que pasar una explícita que respete
 * la preferencia. Compartida por el layout ancho y el estrecho, que no deben divergir.
 */
@OptIn(ExperimentalAnimationApi::class)
private fun settingsCategoryTransition(animationsEnabled: Boolean): ContentTransform =
    if (animationsEnabled) {
        fadeIn(expressiveFadeTween()) togetherWith fadeOut(expressiveFadeTween())
    } else {
        EnterTransition.None togetherWith ExitTransition.None
    }

/** Ancho de la columna de secciones en el layout ancho. */
private val SettingsListPaneWidth = 320.dp

@Composable
private fun WideSettingsLayout(
    selected: SettingsCategory?,
    onSelect: (SettingsCategory?) -> Unit,
    onDialog: (SettingsDialog?) -> Unit,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    Row(modifier = Modifier.fillMaxSize()) {
        SettingsSectionList(
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier
                .width(SettingsListPaneWidth)
                .fillMaxHeight(),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { settingsCategoryTransition(animationsEnabled) },
                label = "settings_detail",
            ) { cat ->
                if (cat != null) {
                    SettingsCategoryDetail(category = cat, onDialog = onDialog)
                } else {
                    SettingsEmptyState()
                }
            }
        }
    }
}

@Composable
private fun NarrowSettingsLayout(
    selected: SettingsCategory?,
    onSelect: (SettingsCategory?) -> Unit,
    onBack: () -> Unit,
    onDialog: (SettingsDialog?) -> Unit,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    AnimatedContent(
        targetState = selected,
        transitionSpec = { settingsCategoryTransition(animationsEnabled) },
        label = "settings_narrow",
        modifier = Modifier.fillMaxSize(),
    ) { cat ->
        if (cat == null) {
            SettingsSectionList(
                selected = null,
                onSelect = onSelect,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                    Text(
                        text = stringResource(cat.titleRes),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SettingsCategoryDetail(category = cat, onDialog = onDialog)
                }
            }
        }
    }
}

/** Lista de secciones con su descripción: primera columna / pantalla inicial. */
@Composable
private fun SettingsSectionList(
    selected: SettingsCategory?,
    onSelect: (SettingsCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = appScrollContentPadding(
                start = AppScreenContentHorizontal,
                end = AppScreenContentHorizontal,
                top = 20.dp,
                bottom = 24.dp + LocalMiniPlayerInset.current,
            ),
            verticalArrangement = Arrangement.Center,
        ) {
            items(
                count = SettingsCategory.entries.size,
                key = { SettingsCategory.entries[it] },
            ) { index ->
                val cat = SettingsCategory.entries[index]
                SettingsSectionRow(
                    cat = cat,
                    isSelected = cat == selected,
                    onClick = { onSelect(cat) },
                )
            }
        }
        AppVerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun SettingsSectionRow(
    cat: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = AppShapes.large,
        color = if (isSelected) colorScheme.secondaryContainer else Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                cat.icon, null,
                tint = if (isSelected) colorScheme.onSecondaryContainer
                else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(cat.titleRes),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = if (isSelected) colorScheme.onSecondaryContainer
                    else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(cat.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    else colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Rounded.Settings, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(Res.string.settings_select_section),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCategoryDetail(
    category: SettingsCategory,
    onDialog: (SettingsDialog?) -> Unit,
    contentTopPadding: Dp = 20.dp,
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        // La guía pide no estirar los ajustes a todo el ancho: columna
        // centrada de 760dp como máximo (el scrollbar queda al borde).
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            contentPadding = appScrollContentPadding(
                start = AppScreenContentHorizontal + 4.dp,
                end = AppScrollbarGutter + AppScreenContentHorizontal,
                top = contentTopPadding,
                bottom = 24.dp + LocalMiniPlayerInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Contenido desacoplado por categoría — solo se compone la activa
            when (category) {
                SettingsCategory.APPEARANCE -> item {
                    AppearanceSettingsGroup()
                }
                SettingsCategory.ACCESSIBILITY -> item {
                    AccessibilitySettingsGroup()
                }
                SettingsCategory.AUDIO -> item {
                    AudioSettingsGroup(onOpenEqualizer = { onDialog(SettingsDialog.EQUALIZER) })
                }
                SettingsCategory.PLAYBACK -> {
                    item { MiniPlayerSettingsGroup(onOpenSeekBarStyleDialog = { onDialog(SettingsDialog.SEEK_BAR_STYLE) }) }
                    item { NowPlayingSettingsGroup() }
                }
                SettingsCategory.LIBRARY -> item {
                    SyncSettingsGroup(onShowYtmSyncWarning = { onDialog(SettingsDialog.YTM_SYNC_WARNING) })
                }
                SettingsCategory.SYSTEM -> {
                    item { ApplicationSettingsGroup() }
                    item {
                        AdvancedSettingsGroup(
                            onShowClearDownloadsDialog = { onDialog(SettingsDialog.CLEAR_DOWNLOADS) },
                            onOpenJvmSettings = { onDialog(SettingsDialog.JVM_SETTINGS) },
                        )
                    }
                }
                SettingsCategory.ABOUT -> {
                    item { SupportSettingsGroup() }
                    item {
                        AboutCard()
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

        AppVerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

