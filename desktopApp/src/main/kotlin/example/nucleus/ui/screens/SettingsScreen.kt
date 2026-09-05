package example.nucleus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.layout.AppScreenContentHorizontal
import example.nucleus.ui.components.layout.AppScrollbarGutter
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.layout.appScrollContentPadding
import example.nucleus.ui.screens.settings.*
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.screenTitle
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private enum class SettingsCategory(
    val icon: ImageVector,
    val titleRes: org.jetbrains.compose.resources.StringResource,
    val subtitleRes: org.jetbrains.compose.resources.StringResource,
) {
    AUDIO(Icons.Rounded.GraphicEq, Res.string.section_audio, Res.string.section_audio_subtitle),
    APPEARANCE(Icons.Rounded.Palette, Res.string.section_appearance, Res.string.section_appearance_subtitle),
    PLAYBACK(Icons.Rounded.PlayCircle, Res.string.section_playback, Res.string.section_playback_subtitle),
    LIBRARY(Icons.Rounded.LibraryMusic, Res.string.section_library, Res.string.section_library_subtitle),
    SYSTEM(Icons.Rounded.Settings, Res.string.section_system, Res.string.section_system_subtitle),
    ABOUT(Icons.Rounded.Info, Res.string.section_about, Res.string.section_about_subtitle),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen() {
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var selected by remember { mutableStateOf(SettingsCategory.AUDIO) }

    CompositionLocalProvider(LocalSettingsColors provides rememberSettingsListItemColors()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isNarrow = maxWidth < 720.dp

            if (isNarrow) {
                NarrowSettingsLayout(
                    selected = selected,
                    onSelect = { selected = it },
                    activeDialog = activeDialog,
                    onDialog = { activeDialog = it },
                )
            } else {
                WideSettingsLayout(
                    selected = selected,
                    onSelect = { selected = it },
                    activeDialog = activeDialog,
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

@Composable
private fun WideSettingsLayout(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    activeDialog: SettingsDialog?,
    onDialog: (SettingsDialog?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Tabs superiores estilo NowPlaying — pill, centrados, con icono + label
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            SettingsCategoryTabs(
                selected = selected,
                onSelect = onSelect,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Detail — solo la categoría seleccionada, desacoplado
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "settings_category",
            ) { cat ->
                SettingsCategoryDetail(
                    category = cat,
                    onDialog = onDialog,
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryTabs(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsCategory.entries.forEach { cat ->
            val isSelected = cat == selected
            val containerColor = if (isSelected) colorScheme.secondaryContainer.copy(alpha = 0.85f)
            else androidx.compose.ui.graphics.Color.Transparent
            val contentColor = if (isSelected) colorScheme.onSecondaryContainer
            else colorScheme.onSurfaceVariant.copy(alpha = 0.9f)

            Surface(
                onClick = { onSelect(cat) },
                shape = RoundedCornerShape(20.dp),
                color = containerColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.height(38.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        cat.icon, null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(cat.titleRes),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = contentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun NarrowSettingsLayout(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    activeDialog: SettingsDialog?,
    onDialog: (SettingsDialog?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top tabs para narrow
        ScrollableTabRow(
            selectedTabIndex = SettingsCategory.entries.indexOf(selected),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = {},
            edgePadding = 8.dp,
        ) {
            SettingsCategory.entries.forEach { cat ->
                Tab(
                    selected = cat == selected,
                    onClick = { onSelect(cat) },
                    text = { Text(stringResource(cat.titleRes), style = MaterialTheme.typography.labelLargeEmphasized) },
                    icon = { Icon(cat.icon, null, modifier = Modifier.size(18.dp)) },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "settings_category_narrow",
            ) { cat ->
                SettingsCategoryDetail(category = cat, onDialog = onDialog)
            }
        }
    }
}

@Composable
private fun BoxScope.SettingsCategoryDetail(
    category: SettingsCategory,
    onDialog: (SettingsDialog?) -> Unit,
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = appScrollContentPadding(
                start = AppScreenContentHorizontal + 4.dp,
                end = AppScrollbarGutter + AppScreenContentHorizontal,
                top = 20.dp,
                bottom = 24.dp + LocalMiniPlayerInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Icon(
                                category.icon, null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp).size(22.dp),
                            )
                        }
                        Column {
                            Text(
                                stringResource(category.titleRes),
                                style = MaterialTheme.typography.screenTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(category.subtitleRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Contenido desacoplado por categoría — solo se compone la activa
            when (category) {
                SettingsCategory.AUDIO -> item {
                    AudioSettingsGroup(onOpenEqualizer = { onDialog(SettingsDialog.EQUALIZER) })
                }
                SettingsCategory.APPEARANCE -> item {
                    AppearanceSettingsGroup()
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
