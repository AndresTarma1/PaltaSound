package example.nucleus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import example.nucleus.ui.components.layout.AppScreenContentHorizontal
import example.nucleus.ui.components.layout.AppScrollbarGutter
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.layout.appScrollContentPadding
import example.nucleus.ui.components.glass.GlassSegmentPill
import example.nucleus.ui.components.glass.GlassSegmentTrack
import example.nucleus.ui.components.glass.LocalContentHaze
import example.nucleus.ui.components.glass.LocalLiquidGlass
import example.nucleus.ui.components.glass.LocalRailHaze
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
) {
    APPEARANCE(Icons.Rounded.Palette, Res.string.section_appearance),
    ACCESSIBILITY(Icons.Rounded.Accessibility, Res.string.section_accessibility),
    AUDIO(Icons.Rounded.GraphicEq, Res.string.section_audio),
    PLAYBACK(Icons.Rounded.PlayCircle, Res.string.section_playback),
    LIBRARY(Icons.Rounded.LibraryMusic, Res.string.section_library),
    SYSTEM(Icons.Rounded.Settings, Res.string.section_system),
    ABOUT(Icons.Rounded.Info, Res.string.section_about),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen() {
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var selected by remember { mutableStateOf(SettingsCategory.APPEARANCE) }

    CompositionLocalProvider(LocalSettingsColors provides rememberSettingsListItemColors()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isNarrow = maxWidth < 900.dp

            if (isNarrow) {
                NarrowSettingsLayout(
                    selected = selected,
                    onSelect = { selected = it },
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

@Composable
private fun WideSettingsLayout(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    onDialog: (SettingsDialog?) -> Unit,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    // El contenido es la fuente; la barra de tabs flota encima con blur
    // progresivo (fuerte arriba, desaparece hacia abajo) mientras se scrollea
    // por debajo.
    val contentHaze = rememberHazeState()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(contentHaze),
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { settingsCategoryTransition(animationsEnabled) },
                label = "settings_category",
            ) { cat ->
                SettingsCategoryDetail(
                    category = cat,
                    onDialog = onDialog,
                    contentTopPadding = SettingsTabsBarHeight,
                )
            }
        }

        // Difuminado progresivo detrás de los tabs.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(SettingsTabsBarHeight)
                .hazeBlur(
                    input = HazeInput.Sources(contentHaze),
                    style = rememberProgressiveBarStyle(),
                )
                .background(rememberBarScrim()),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            SettingsCategoryTabs(
                selected = selected,
                onSelect = onSelect,
                hazeState = contentHaze,
            )
        }
    }
}

/** Altura de la franja difuminada que queda fija sobre los ajustes. */
private val SettingsTabsBarHeight = 56.dp

/**
 * Blur progresivo para barras fijas: máxima intensidad arriba y se disuelve
 * hacia abajo, para que el contenido que pasa por debajo no se vea nítido.
 *
 * `noiseFactor(0f)`: el ruido por defecto de Haze se ve como grano/"grumoso"
 * sobre superficies planas de escritorio.
 */
@Composable
private fun rememberProgressiveBarStyle(): HazeBlurStyle = remember {
    HazeBlurStyle {
        blurRadius(22.dp)
        noiseFactor(0f)
        // Mask en vez de HazeProgressive: evita el RuntimeEffect que congela
        // el primer render en Windows (haze#1260 / SKIKO-1179).
        mask(
            Brush.verticalGradient(
                0f to Color.White,
                0.65f to Color.White.copy(alpha = 0.7f),
                1f to Color.Transparent,
            ),
        )
    }
}

/**
 * Velo sutil sobre el difuminado: sin él, los tabs quedan medio invisibles
 * porque detrás solo hay contenido borroso. Se funde a transparente hacia
 * abajo para no tapar el efecto progresivo.
 */
@Composable
private fun rememberBarScrim(): Brush {
    val surface = MaterialTheme.colorScheme.surface
    return remember(surface) {
        Brush.verticalGradient(
            0.0f to surface.copy(alpha = 0.55f),
            0.65f to surface.copy(alpha = 0.22f),
            1.0f to Color.Transparent,
        )
    }
}

@Composable
private fun SettingsCategoryTabs(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val segHaze = hazeState ?: LocalRailHaze.current ?: LocalContentHaze.current
    val segGlass = LocalLiquidGlass.current && segHaze != null
    if (segGlass) {
        GlassSegmentTrack(hazeState = segHaze, modifier = modifier) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .hazeGlassContainer(mergeDistance = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsCategory.entries.forEach { cat ->
                    val isSelected = cat == selected
                    GlassSegmentPill(
                        selected = isSelected,
                        onClick = { onSelect(cat) },
                        hazeState = segHaze,
                    ) {
                        Icon(
                            cat.icon, null,
                            tint = if (isSelected) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(cat.titleRes),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = if (isSelected) {
                                colorScheme.onSurface
                            } else {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                            },
                        )
                    }
                }
            }
        }
        return
    }
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
                shape = AppShapes.xLarge,
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
    onDialog: (SettingsDialog?) -> Unit,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val selectedTabIndex = SettingsCategory.entries.indexOf(selected)
    val contentHaze = rememberHazeState()
    val glassTabs = LocalLiquidGlass.current

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(contentHaze),
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { settingsCategoryTransition(animationsEnabled) },
                label = "settings_category_narrow",
            ) { cat ->
                SettingsCategoryDetail(
                    category = cat,
                    onDialog = onDialog,
                    contentTopPadding = SettingsTabsBarHeight,
                )
            }
        }

        // Difuminado progresivo detrás de los tabs: el contenido se ve pasar
        // por debajo, difuminado fuerte arriba y disuelto hacia abajo.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(SettingsTabsBarHeight)
                .hazeBlur(
                    input = HazeInput.Sources(contentHaze),
                    style = rememberProgressiveBarStyle(),
                )
                .background(rememberBarScrim()),
        )

        if (glassTabs) {
            // Pista esmerilada con las píldoras deslizables dentro.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlassSegmentTrack(
                    hazeState = contentHaze,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .hazeGlassContainer(mergeDistance = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsCategory.entries.forEach { cat ->
                            val isSelected = cat == selected
                            GlassSegmentPill(
                                selected = isSelected,
                                onClick = { onSelect(cat) },
                                hazeState = contentHaze,
                            ) {
                                Icon(cat.icon, null, modifier = Modifier.size(18.dp))
                                Text(
                                    stringResource(cat.titleRes),
                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Tabs clásicos, con contenedor transparente para que se vea el
            // difuminado progresivo de detrás.
            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                divider = {},
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = false),
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                SettingsCategory.entries.forEach { cat ->
                    Tab(
                        selected = cat == selected,
                        onClick = { onSelect(cat) },
                        text = {
                            Text(
                                stringResource(cat.titleRes),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        icon = { Icon(cat.icon, null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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

