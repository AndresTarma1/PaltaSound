package example.nucleus.ui.screens.shared

import androidx.compose.runtime.Composable
import example.nucleus.generated.resources.*
import example.nucleus.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import example.nucleus.data.repository.AppLocale
import example.nucleus.data.repository.AudioQuality
import example.nucleus.data.repository.DarkLevel
import example.nucleus.data.repository.IslandStyle
import example.nucleus.data.repository.LayoutMode
import example.nucleus.data.repository.LoudnessLevel
import example.nucleus.data.repository.LyricsAnimationStyle
import example.nucleus.data.repository.MiniPlayerBackgroundStyle
import example.nucleus.data.repository.MiniPlayerStyle
import example.nucleus.data.repository.NavigationRailStyle
import example.nucleus.data.repository.BackgroundStyle
import example.nucleus.data.repository.ThemeMode
import example.nucleus.data.repository.ThemePalette
import example.nucleus.data.repository.RenderApi
import example.nucleus.data.repository.SeekBarStyle
import example.nucleus.data.repository.YouTubeRegion
import example.nucleus.viewmodels.LibrarySortOrder
import example.nucleus.viewmodels.UpdateCheckState
import example.nucleus.viewmodels.UpdateStatus
import example.nucleus.viewmodels.YtmLibraryFilter

@Composable
fun AudioQuality.displayName(): String = when (this) {
    AudioQuality.LOW -> stringResource(Res.string.audio_quality_low)
    AudioQuality.NORMAL -> stringResource(Res.string.audio_quality_normal)
    AudioQuality.HIGH -> stringResource(Res.string.audio_quality_high)
}

@Composable
fun LoudnessLevel.displayName(): String = when (this) {
    LoudnessLevel.OFF -> stringResource(Res.string.loudness_off)
    LoudnessLevel.AGGRESSIVE -> stringResource(Res.string.loudness_aggressive)
    LoudnessLevel.LOUD -> stringResource(Res.string.loudness_loud)
    LoudnessLevel.BALANCED -> stringResource(Res.string.loudness_balanced)
    LoudnessLevel.QUIET -> stringResource(Res.string.loudness_quiet)
}

@Composable
fun LyricsAnimationStyle.displayName(): String = when (this) {
    LyricsAnimationStyle.NONE -> stringResource(Res.string.lyrics_animation_none)
    LyricsAnimationStyle.FADE -> stringResource(Res.string.lyrics_animation_fade)
    LyricsAnimationStyle.KARAOKE -> stringResource(Res.string.lyrics_animation_karaoke)
    LyricsAnimationStyle.GLOW -> stringResource(Res.string.lyrics_animation_glow)
}

@Composable
fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
    ThemeMode.DARK -> stringResource(Res.string.theme_dark)
    ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
}

@Composable
fun DarkLevel.displayName(): String = when (this) {
    DarkLevel.DIM -> stringResource(Res.string.dark_level_dim)
    DarkLevel.BLACK -> stringResource(Res.string.dark_level_black)
}

@Composable
fun NavigationRailStyle.displayName(): String = when (this) {
    NavigationRailStyle.DEFAULT -> stringResource(Res.string.navigation_rail_default)
    NavigationRailStyle.WIDE -> stringResource(Res.string.navigation_rail_wide)
}

@Composable
fun LayoutMode.displayName(): String = when (this) {
    LayoutMode.ISLANDS -> stringResource(Res.string.layout_islands)
    LayoutMode.ATTACHED -> stringResource(Res.string.layout_attached)
    LayoutMode.SQUARE -> stringResource(Res.string.layout_square)
}

@Composable
fun MiniPlayerStyle.displayName(): String = when (this) {
    MiniPlayerStyle.BAR -> stringResource(Res.string.mini_player_style_bar)
    MiniPlayerStyle.FLOATING -> stringResource(Res.string.mini_player_style_floating)
    MiniPlayerStyle.DOCKED -> stringResource(Res.string.mini_player_style_docked)
}

@Composable
fun MiniPlayerBackgroundStyle.displayName(): String = when (this) {
    MiniPlayerBackgroundStyle.SOLID -> stringResource(Res.string.mini_player_bg_solid)
    MiniPlayerBackgroundStyle.COVER -> stringResource(Res.string.mini_player_bg_cover)
    MiniPlayerBackgroundStyle.TRANSLUCENT -> stringResource(Res.string.mini_player_bg_translucent)
}

@Composable
fun IslandStyle.displayName(): String = when (this) {
    IslandStyle.COMPACT -> stringResource(Res.string.island_style_compact)
    IslandStyle.COMFORTABLE -> stringResource(Res.string.island_style_comfortable)
    IslandStyle.SPACIOUS -> stringResource(Res.string.island_style_spacious)
}

@Composable
fun SeekBarStyle.displayName(): String = when (this) {
    SeekBarStyle.WAVY -> stringResource(Res.string.seek_bar_style_wavy)
    SeekBarStyle.LINEAR -> stringResource(Res.string.seek_bar_style_linear)
    SeekBarStyle.MATERIAL -> stringResource(Res.string.seek_bar_style_material)
    SeekBarStyle.MINIMAL -> stringResource(Res.string.seek_bar_style_minimal)
}

@Composable
fun ThemePalette.displayName(): String = when (this) {
    ThemePalette.DEFAULT -> stringResource(Res.string.palette_default)
    ThemePalette.OCEANO -> stringResource(Res.string.palette_ocean)
    ThemePalette.BOSQUE -> stringResource(Res.string.palette_forest)
    ThemePalette.ATARDECER -> stringResource(Res.string.palette_sunset)
    ThemePalette.PURPURA -> stringResource(Res.string.palette_purple)
    ThemePalette.TEAL -> stringResource(Res.string.palette_teal)
    ThemePalette.AMBAR -> stringResource(Res.string.palette_amber)
    ThemePalette.INDIGO -> stringResource(Res.string.palette_indigo)
    ThemePalette.YTMUSIC -> stringResource(Res.string.palette_ytmusic)
}

@Composable
fun BackgroundStyle.displayName(): String = when (this) {
    BackgroundStyle.GRADIENT -> stringResource(Res.string.background_style_gradient)
    BackgroundStyle.BLURRED_COVER -> stringResource(Res.string.background_style_blur)
    BackgroundStyle.SOLID_COLOR -> stringResource(Res.string.background_style_solid)
}

@Composable
fun RenderApi.displayName(): String = when (this) {
    RenderApi.DIRECTX -> stringResource(Res.string.render_directx)
    RenderApi.OPENGL -> stringResource(Res.string.render_opengl)
    RenderApi.SOFTWARE -> stringResource(Res.string.render_software)
    RenderApi.ANGLE -> stringResource(Res.string.render_angle)
}

@Composable
fun RenderApi.displayDescription(): String = when (this) {
    RenderApi.DIRECTX -> stringResource(Res.string.render_directx_desc)
    RenderApi.OPENGL -> stringResource(Res.string.render_opengl_desc)
    RenderApi.SOFTWARE -> stringResource(Res.string.render_software_desc)
    RenderApi.ANGLE -> stringResource(Res.string.render_angle_desc)
}

@Composable
fun LibrarySortOrder.displayName(): String = when (this) {
    LibrarySortOrder.NAME_ASC -> stringResource(Res.string.sort_name_asc_label)
    LibrarySortOrder.NAME_DESC -> stringResource(Res.string.sort_name_desc_label)
    LibrarySortOrder.DATE_ADDED -> stringResource(Res.string.sort_date_added)
}

@Composable
fun YtmLibraryFilter.displayName(): String = when (this) {
    YtmLibraryFilter.RECENT_ACTIVITY -> stringResource(Res.string.ytm_filter_recent_activity)
    YtmLibraryFilter.RECENTLY_PLAYED -> stringResource(Res.string.ytm_filter_recently_played)
    YtmLibraryFilter.PLAYLISTS_AZ -> stringResource(Res.string.ytm_filter_playlists_az)
    YtmLibraryFilter.PLAYLISTS_RECENT -> stringResource(Res.string.ytm_filter_playlists_recent)
}

@Composable
fun AppLocale.displayName(): String = when (this) {
    AppLocale.SYSTEM -> stringResource(Res.string.language_system)
    AppLocale.ES -> stringResource(Res.string.language_spanish)
    AppLocale.EN -> stringResource(Res.string.language_english)
}

@Composable
fun YouTubeRegion.displayName(): String = when (this) {
    YouTubeRegion.SYSTEM -> stringResource(Res.string.region_system)
    YouTubeRegion.US -> stringResource(Res.string.region_us)
    YouTubeRegion.CO -> stringResource(Res.string.region_co)
    YouTubeRegion.MX -> stringResource(Res.string.region_mx)
    YouTubeRegion.AR -> stringResource(Res.string.region_ar)
    YouTubeRegion.BR -> stringResource(Res.string.region_br)
    YouTubeRegion.CL -> stringResource(Res.string.region_cl)
    YouTubeRegion.PE -> stringResource(Res.string.region_pe)
    YouTubeRegion.EC -> stringResource(Res.string.region_ec)
    YouTubeRegion.VE -> stringResource(Res.string.region_ve)
    YouTubeRegion.CA -> stringResource(Res.string.region_ca)
}

/** Subtítulo de la entrada "Buscar actualizaciones" según el estado del updater. */
@Composable
fun updateCheckSubtitle(updateStatus: UpdateStatus, checkState: UpdateCheckState): String {
    val downloading = updateStatus as? UpdateStatus.Downloading
    val manualOnly = updateStatus as? UpdateStatus.ManualOnly
    return when {
        updateStatus is UpdateStatus.Ready -> stringResource(Res.string.check_updates_ready)
        manualOnly != null -> stringResource(Res.string.check_updates_manual_only, manualOnly.info.latestVersion)
        downloading != null -> {
            val pct = downloading.progress
            if (pct >= 0f) {
                "${stringResource(Res.string.check_updates_downloading)} ${(pct * 100).toInt()}%"
            } else {
                stringResource(Res.string.check_updates_downloading)
            }
        }
        checkState is UpdateCheckState.Checking -> stringResource(Res.string.check_updates_checking)
        checkState is UpdateCheckState.UpToDate -> stringResource(Res.string.check_updates_up_to_date)
        checkState is UpdateCheckState.Failed -> stringResource(Res.string.check_updates_failed)
        else -> stringResource(Res.string.check_updates_subtitle)
    }
}

/** Subtítulo de "Sincronizar ahora": operación en curso o el texto por defecto. */
@Composable
fun syncNowSubtitle(isSyncing: Boolean, currentOperation: String): String =
    if (isSyncing) currentOperation.ifBlank { stringResource(Res.string.sync_now_syncing) }
    else stringResource(Res.string.sync_now_subtitle)
