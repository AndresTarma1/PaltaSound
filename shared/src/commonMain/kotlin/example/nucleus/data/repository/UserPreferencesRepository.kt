package example.nucleus.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import example.nucleus.utils.PendingAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/** Valor sentinela para la posición del overlay que nunca ha sido configurada por el usuario. */
const val OVERLAY_POS_UNSET = Int.MIN_VALUE

enum class AudioQuality {
    LOW, NORMAL, HIGH
}

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

/** Qué tan oscuras son las superficies del tema oscuro. DIM = gris oscuro teñido, BLACK = negro puro (AMOLED). */
enum class DarkLevel {
    DIM, BLACK
}

/** Estilo de diseño general. ISLANDS = tarjetas redondeadas y espaciadas (actual). ATTACHED = de borde a borde, compacto. SQUARE = armazón cuadrado con bordes separadores entre regiones. */
enum class LayoutMode {
    ISLANDS, ATTACHED, SQUARE
}

/** Estilo visual del mini reproductor. BAR = barra pegada al borde inferior de la ventana. FLOATING = tarjeta flotante sobre el contenido. DOCKED = integrado al final de las pantallas. */
enum class MiniPlayerStyle {
    BAR, FLOATING, DOCKED
}

/** Fondo de la tarjeta del mini reproductor flotante. SOLID = color sólido del chrome. COVER = carátula desenfocada con gradiente de sus colores. TRANSLUCENT = semitransparente. */
enum class MiniPlayerBackgroundStyle {
    SOLID, COVER, TRANSLUCENT
}

/** Estilos del navigationRail **/
enum class NavigationRailStyle{
    DEFAULT, WIDE
}

/**
 * Qué tan pronunciadas están las "islas" — redondez de esquinas + el espaciado alrededor de cada superficie —
 * solo tiene sentido cuando [LayoutMode.ISLANDS] está activo. Los valores (dp) son consumidos por `dimensFor`
 * en el módulo de UI (se mantienen como Int aquí para que esto permanezca en commonMain, libre de tipos de Compose).
 */
enum class IslandStyle(val cornerDp: Int, val gapDp: Int) {
    COMPACT(12, 8),
    COMFORTABLE(18, 12),
    SPACIOUS(26, 18),
}

/**
 * Estilo visual de la barra de progreso de reproducción, elegido desde un diálogo de vista previa.
 *  - WAVY: garabato animado (estilo Metrolist).
 *  - LINEAR: barra plana delgada con un pulgar de punto redondo (estilo Spotify).
 *  - MATERIAL: el control deslizante grueso estándar de Material 3 con un amplio mango.
 *  - MINIMAL: una línea delgada de seguimiento sin pulgar hasta que se arrastra.
 */
enum class SeekBarStyle {
    WAVY, LINEAR, MATERIAL, MINIMAL
}

/** Normalización de volumen (LUFS) vía filtro loudnorm de mpv, como Metrolist. */
enum class LoudnessLevel(val lufs: Int) {
    OFF(-1), AGGRESSIVE(-7), LOUD(-11), BALANCED(-14), QUIET(-19)
}

/** Estilo de animación de la línea activa en las letras sincronizadas. */
enum class LyricsAnimationStyle {
    NONE, FADE, KARAOKE, GLOW
}

enum class AppLocale(val tag: String?) {
    SYSTEM(null), ES("es"), EN("en")
}

enum class YouTubeRegion(val gl: String, val hl: String) {
    SYSTEM("", ""),
    US("US", "en"),
    CO("CO", "es"),
    MX("MX", "es"),
    AR("AR", "es"),
    BR("BR", "pt"),
    CL("CL", "es"),
    PE("PE", "es"),
    EC("EC", "es"),
    VE("VE", "es"),
    CA("CA", "en"),
}

enum class ThemePalette(val primary: Long, val secondary: Long) {
    DEFAULT(0xFF687988, 0xFF72787E),
    OCEANO(0xFF0288D1, 0xFF0277BD),
    BOSQUE(0xFF2E7D32, 0xFF388E3C),
    ATARDECER(0xFFC62828, 0xFFEF6C00),
    PURPURA(0xFF6A1B9A, 0xFF8E24AA),
    TEAL(0xFF00695C, 0xFF00897B),
    AMBAR(0xFFFF8F00, 0xFFFFA000),
    INDIGO(0xFF283593, 0xFF3949AB),
    YTMUSIC(0xFFFF0033, 0xFFB00020)
}

enum class BackgroundStyle {
    GRADIENT, BLURRED_COVER, SOLID_COLOR
}

/** Diseño visual de la pantalla Now Playing, inspirado en grandes reproductores.
 *  YOUTUBE_MUSIC = carátula centrada con fondo borroso ambiental.
 *  APPLE_MUSIC   = carátula grande a la izquierda y panel frosted a la derecha.
 *  SPOTIFY       = fondo degradado oscuro desde la carátula y tipografía grande. */
enum class NowPlayingDesign {
    YOUTUBE_MUSIC, APPLE_MUSIC, SPOTIFY
}

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private object PreferencesKeys {
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HIGH_RES_COVER = booleanPreferencesKey("high_res_cover")
        val CROSSFADE = booleanPreferencesKey("crossfade")
        val CACHE_IMAGES = booleanPreferencesKey("cache_images")
        val IMAGES_ENABLED = booleanPreferencesKey("images_enabled")
        val MINIMIZE_TO_TRAY = booleanPreferencesKey("minimize_to_tray")
        val TRIM_MEMORY_ON_TRAY = booleanPreferencesKey("trim_memory_on_tray")
        val LAUNCH_AT_STARTUP = booleanPreferencesKey("launch_at_startup")
        val WINDOW_WIDTH = intPreferencesKey("window_width")
        val WINDOW_HEIGHT = intPreferencesKey("window_height")
        val WINDOW_MAXIMIZED = booleanPreferencesKey("window_maximized")
        val QUEUE_LOCKED = booleanPreferencesKey("queue_locked")
        val EQUALIZER_BANDS = stringPreferencesKey("equalizer_bands")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val LOCALE = stringPreferencesKey("locale")
        val YOUTUBE_REGION = stringPreferencesKey("youtube_region")
        val DARK_LEVEL = stringPreferencesKey("dark_level")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val ISLAND_STYLE = stringPreferencesKey("island_style")
        val YTM_SYNC = booleanPreferencesKey("ytm_sync_enabled")
        val LAST_FULL_SYNC_AT = longPreferencesKey("last_full_sync_at")
        val OVERLAY_HOTKEY_ENABLED = booleanPreferencesKey("overlay_hotkey_enabled")
        val OVERLAY_HOTKEY_CODE = intPreferencesKey("overlay_hotkey_code")
        val OVERLAY_HOTKEY_MODS = intPreferencesKey("overlay_hotkey_mods")
        val OVERLAY_HOTKEY_LABEL = stringPreferencesKey("overlay_hotkey_label")
        val OVERLAY_POS_X = intPreferencesKey("overlay_pos_x")
        val OVERLAY_POS_Y = intPreferencesKey("overlay_pos_y")
        val LT_USERNAME = stringPreferencesKey("listen_together_username")
        val LAST_ACCOUNT_ID = stringPreferencesKey("last_account_id")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode_enabled")
        val PENDING_ACTIONS = stringPreferencesKey("pending_sync_actions")
        val SEEK_BAR_STYLE = stringPreferencesKey("seek_bar_style")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val APP_BACKGROUND = stringPreferencesKey("app_background_style")

        val SCALE_UI = floatPreferencesKey("scale_ui")

        val VOLUMEN = intPreferencesKey("volumen")
        val NAVIGATION_RAIL_STYLE = stringPreferencesKey("navigation_rail_style")
        val MINI_PLAYER_STYLE = stringPreferencesKey("mini_player_style")
        val MINI_PLAYER_BG_STYLE = stringPreferencesKey("mini_player_bg_style")
        val NOW_PLAYING_BACKGROUND = stringPreferencesKey("now_playing_background")
        val NOW_PLAYING_DESIGN = stringPreferencesKey("now_playing_design")
        val SELECTED_FONT = stringPreferencesKey("selected_font")
        val PREFERENCES_MIGRATION_VERSION = intPreferencesKey("preferences_migration_version")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val NOW_PLAYING_AUTO_HIDE = booleanPreferencesKey("now_playing_auto_hide")
        val TASKBAR_WIDGET_ENABLED = booleanPreferencesKey("taskbar_widget_enabled")
        val LOUDNESS_LEVEL = stringPreferencesKey("loudness_level")
        val SAVED_QUEUE = stringPreferencesKey("saved_queue")
        val SAVED_QUEUE_SHUFFLE = booleanPreferencesKey("saved_queue_shuffle")
        val SAVED_QUEUE_REPEAT = stringPreferencesKey("saved_queue_repeat")
        val QUEUE_PERSISTENCE_ENABLED = booleanPreferencesKey("queue_persistence_enabled")
        val LYRICS_TEXT_SIZE = floatPreferencesKey("lyrics_text_size")
        val LYRICS_LINE_SPACING = floatPreferencesKey("lyrics_line_spacing")
        val LYRICS_ANIMATION_STYLE = stringPreferencesKey("lyrics_animation_style")
        val LYRICS_ROMANIZE = booleanPreferencesKey("lyrics_romanize")
        val LYRICS_OFFSET_MS = intPreferencesKey("lyrics_offset_ms")
        val LOG_TO_FILE = booleanPreferencesKey("log_to_file")
        val LOG_VERBOSE = booleanPreferencesKey("log_verbose")
        val SABR_ENABLED = booleanPreferencesKey("sabr_enabled")
    }


    /** Factor de escala de la UI (zoom de interfaz), independiente del DPI real del sistema.
     *  1.0f = 100% (normal). Se aplica como multiplicador sobre LocalDensity.current.density. */
    val uiScale: Flow<Float> = dataStore.data.map { it[PreferencesKeys.SCALE_UI] ?: 1.0f }

    suspend fun setUiScale(scale: Float) {
        dataStore.edit { it[PreferencesKeys.SCALE_UI] = scale.coerceIn(0.7f, 2.0f) }
    }

    /** Nombre de la familia de fuentes instalada en el sistema elegida por el usuario. Vacío = la predeterminada de Compose. */
    val selectedFont: Flow<String> = dataStore.data.map { it[PreferencesKeys.SELECTED_FONT] ?: "" }

    suspend fun setSelectedFont(name: String) {
        dataStore.edit { it[PreferencesKeys.SELECTED_FONT] = name }
    }

    /** Habilitar/deshabilitar animaciones de la interfaz. */
    val animationsEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.ANIMATIONS_ENABLED] ?: true }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.ANIMATIONS_ENABLED] = enabled }
    }

    /** Ocultar automáticamente el cromo de Now Playing (barra, progreso, transporte,
     *  volumen) al dejar de mover el ratón; reaparece con el primer movimiento. */
    val nowPlayingAutoHide: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.NOW_PLAYING_AUTO_HIDE] ?: true }

    suspend fun setNowPlayingAutoHide(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.NOW_PLAYING_AUTO_HIDE] = enabled }
    }

    /** Widget del reproductor en la barra de tareas de Windows (SMTC). */
    val taskbarWidgetEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.TASKBAR_WIDGET_ENABLED] ?: true }

    suspend fun setTaskbarWidgetEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.TASKBAR_WIDGET_ENABLED] = enabled }
    }

    /** Nivel de normalización de volumen (LUFS). OFF = desactivado. */
    val loudnessLevel: Flow<LoudnessLevel> = dataStore.data.map { pref ->
        try {
            LoudnessLevel.valueOf(pref[PreferencesKeys.LOUDNESS_LEVEL] ?: LoudnessLevel.OFF.name)
        } catch (_: Exception) { LoudnessLevel.OFF }
    }

    suspend fun setLoudnessLevel(level: LoudnessLevel) {
        dataStore.edit { it[PreferencesKeys.LOUDNESS_LEVEL] = level.name }
    }

    // ── Cola persistente (JSON serializado de QueueSession + shuffle/repeat) ──

    val savedQueueJson: Flow<String?> = dataStore.data.map { it[PreferencesKeys.SAVED_QUEUE] }

    suspend fun saveQueue(json: String?) {
        dataStore.edit {
            if (json == null) it.remove(PreferencesKeys.SAVED_QUEUE)
            else it[PreferencesKeys.SAVED_QUEUE] = json
        }
    }

    val savedQueueShuffle: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.SAVED_QUEUE_SHUFFLE] ?: false }

    suspend fun saveQueueShuffle(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SAVED_QUEUE_SHUFFLE] = enabled }
    }

    val savedQueueRepeat: Flow<String> = dataStore.data.map { it[PreferencesKeys.SAVED_QUEUE_REPEAT] ?: "OFF" }

    suspend fun saveQueueRepeat(mode: String) {
        dataStore.edit { it[PreferencesKeys.SAVED_QUEUE_REPEAT] = mode }
    }

    /** ¿Persistir la cola de reproducción entre sesiones? (por defecto sí). */
    val queuePersistenceEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.QUEUE_PERSISTENCE_ENABLED] ?: true }

    suspend fun setQueuePersistenceEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.QUEUE_PERSISTENCE_ENABLED] = enabled }
    }

    // ── Letras ──

    /** Tamaño (sp) de la línea activa de letras sincronizadas. */
    val lyricsTextSize: Flow<Float> = dataStore.data.map { it[PreferencesKeys.LYRICS_TEXT_SIZE] ?: 40f }

    suspend fun setLyricsTextSize(size: Float) {
        dataStore.edit { it[PreferencesKeys.LYRICS_TEXT_SIZE] = size.coerceIn(20f, 64f) }
    }

    /** Espaciado de línea (sp) de las letras sincronizadas. */
    val lyricsLineSpacing: Flow<Float> = dataStore.data.map { it[PreferencesKeys.LYRICS_LINE_SPACING] ?: 54f }

    suspend fun setLyricsLineSpacing(spacing: Float) {
        dataStore.edit { it[PreferencesKeys.LYRICS_LINE_SPACING] = spacing.coerceIn(30f, 80f) }
    }

    /** Estilo de animación de la línea activa. */
    val lyricsAnimationStyle: Flow<LyricsAnimationStyle> = dataStore.data.map { pref ->
        try {
            LyricsAnimationStyle.valueOf(pref[PreferencesKeys.LYRICS_ANIMATION_STYLE] ?: LyricsAnimationStyle.KARAOKE.name)
        } catch (_: Exception) { LyricsAnimationStyle.KARAOKE }
    }

    suspend fun setLyricsAnimationStyle(style: LyricsAnimationStyle) {
        dataStore.edit { it[PreferencesKeys.LYRICS_ANIMATION_STYLE] = style.name }
    }

    /** Romanizar letras en scripts no latinos (JA/KO/RU, estilo Metrolist). */
    val lyricsRomanize: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.LYRICS_ROMANIZE] ?: false }

    suspend fun setLyricsRomanize(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.LYRICS_ROMANIZE] = enabled }
    }

    /**
     * Offset global de sincronización de letras en milisegundos.
     * Positivo = letras más tarde respecto al audio; negativo = más temprano.
     */
    val lyricsOffsetMs: Flow<Int> = dataStore.data.map { it[PreferencesKeys.LYRICS_OFFSET_MS] ?: 0 }

    suspend fun setLyricsOffsetMs(offsetMs: Int) {
        dataStore.edit { it[PreferencesKeys.LYRICS_OFFSET_MS] = offsetMs.coerceIn(-10_000, 10_000) }
    }


    val appBackgroundStyle: Flow<BackgroundStyle> = dataStore.data.map { pref ->
        try { BackgroundStyle.valueOf(pref[PreferencesKeys.APP_BACKGROUND] ?: BackgroundStyle.SOLID_COLOR.name) }
        catch (_: Exception) { BackgroundStyle.BLURRED_COVER }
    }

    suspend fun setAppBackgroundStyle(style: BackgroundStyle) {
        dataStore.edit { it[PreferencesKeys.APP_BACKGROUND] = style.name }
    }

    val navigationRailStyle: Flow<NavigationRailStyle> = dataStore.data.map{ pref ->
        try { NavigationRailStyle.valueOf(pref[PreferencesKeys.NAVIGATION_RAIL_STYLE] ?: NavigationRailStyle.DEFAULT.name) }
        catch (_: Exception) { NavigationRailStyle.DEFAULT }
    }

    suspend fun setNavigationRailStyle(style: NavigationRailStyle) {
        dataStore.edit { it[PreferencesKeys.NAVIGATION_RAIL_STYLE] = style.name }
    }

    /** Última posición de la ventana de overlay en dp, o [OVERLAY_POS_UNSET] cuando nunca se ha movido (→ esquina predeterminada). */
    val overlayPosX: Flow<Int> = dataStore.data.map { it[PreferencesKeys.OVERLAY_POS_X] ?: OVERLAY_POS_UNSET }
    val overlayPosY: Flow<Int> = dataStore.data.map { it[PreferencesKeys.OVERLAY_POS_Y] ?: OVERLAY_POS_UNSET }

    suspend fun setOverlayPosition(x: Int, y: Int) {
        dataStore.edit {
            it[PreferencesKeys.OVERLAY_POS_X] = x
            it[PreferencesKeys.OVERLAY_POS_Y] = y
        }
    }

    /** Nombre de usuario recordado para unirse/crear salas de Escuchar Juntos. */
    val listenTogetherUsername: Flow<String> = dataStore.data.map { it[PreferencesKeys.LT_USERNAME] ?: "" }

    suspend fun setListenTogetherUsername(name: String) {
        dataStore.edit { it[PreferencesKeys.LT_USERNAME] = name }
    }

    val volume: Flow<Int> = dataStore.data.map { it[PreferencesKeys.VOLUMEN] ?: 100 }

    suspend fun readSavedVolume(): Int = volume.first()

    suspend fun setVolumen(volumen: Int) {
        dataStore.edit { it[PreferencesKeys.VOLUMEN] = volumen.coerceIn(0, 100) }
    }


    /** Identidad (email) de la última cuenta de YouTube iniciada — se usa para detectar cambios de cuenta. */
    val lastAccountId: Flow<String> = dataStore.data.map { it[PreferencesKeys.LAST_ACCOUNT_ID] ?: "" }

    suspend fun setLastAccountId(id: String) {
        dataStore.edit { it[PreferencesKeys.LAST_ACCOUNT_ID] = id }
    }

    // ── Atajos de teclado del overlay del juego ─────────────────────────────────────────────────
    // Un atajo global que activa/desactiva un overlay estilo Steam siempre visible para controlar la música
    // sin salir del juego/aplicación actual. La combinación se almacena como un código de tecla de jnativehook
    // más una máscara de modificadores empaquetada (bit0 ctrl, bit1 alt, bit2 shift, bit3 meta); código 0
    // significa "sin configurar, usar el predeterminado del gestor". [label] es la combinación legible preformateada.

    val overlayHotkeyEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.OVERLAY_HOTKEY_ENABLED] ?: true }
    val overlayHotkeyCode: Flow<Int> = dataStore.data.map { it[PreferencesKeys.OVERLAY_HOTKEY_CODE] ?: 0 }
    val overlayHotkeyMods: Flow<Int> = dataStore.data.map { it[PreferencesKeys.OVERLAY_HOTKEY_MODS] ?: 0 }
    val overlayHotkeyLabel: Flow<String> = dataStore.data.map { it[PreferencesKeys.OVERLAY_HOTKEY_LABEL] ?: "" }

    suspend fun setOverlayHotkeyEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.OVERLAY_HOTKEY_ENABLED] = enabled }
    }

    suspend fun setOverlayHotkey(code: Int, mods: Int, label: String) {
        dataStore.edit {
            it[PreferencesKeys.OVERLAY_HOTKEY_CODE] = code
            it[PreferencesKeys.OVERLAY_HOTKEY_MODS] = mods
            it[PreferencesKeys.OVERLAY_HOTKEY_LABEL] = label
        }
    }

    // ── Sincronización con YouTube Music (experimental) ───────────────────────────────────
    // Cuando está habilitado, agregar/eliminar una canción en una playlist local se refleja en una
    // playlist de YouTube Music en la cuenta iniciada. Los IDs de playlist local→remoto se almacenan
    // aquí (no en el esquema SQLite) para que la función no necesite una migración de BD destructiva.

    val ytmSyncEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.YTM_SYNC] ?: false }

    suspend fun setYtmSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.YTM_SYNC] = enabled }
    }

    /**
     * Milisegundos de época de la última sincronización completa de la biblioteca (canciones/álbumes/artistas/playlists
     * marcados como favoritos). Se usa para limitar la resincronización: AccountViewModel se recrea cada vez que el
     * usuario navega a la pantalla de Cuenta (es una fábrica de Koin), por lo que sin esto, una sincronización completa
     — incluyendo una recarga completa de las canciones de cada playlist vinculada a YouTube cuando ytmSyncEnabled está
     * activo — se ejecutaría en cada visita en lugar de solo periódicamente.
     */
    val lastFullSyncAt: Flow<Long> = dataStore.data.map { it[PreferencesKeys.LAST_FULL_SYNC_AT] ?: 0L }

    suspend fun setLastFullSyncAt(epochMillis: Long) {
        dataStore.edit { it[PreferencesKeys.LAST_FULL_SYNC_AT] = epochMillis }
    }

    private fun remotePlaylistKey(localId: String) = stringPreferencesKey("ytm_remote_$localId")

    /** ID remoto de playlist YTM que refleja una playlist local dada, o null si aún no existe. */
    suspend fun getRemotePlaylistId(localId: String): String? =
        dataStore.data.first()[remotePlaylistKey(localId)]

    suspend fun setRemotePlaylistId(localId: String, remoteId: String) {
        dataStore.edit { it[remotePlaylistKey(localId)] = remoteId }
    }

    suspend fun clearRemotePlaylistId(localId: String) {
        dataStore.edit { it.remove(remotePlaylistKey(localId)) }
    }

    val darkLevel: Flow<DarkLevel> = dataStore.data.map { pref ->
        try { DarkLevel.valueOf(pref[PreferencesKeys.DARK_LEVEL] ?: DarkLevel.DIM.name) }
        catch (_: Exception) { DarkLevel.DIM }
    }

    suspend fun setDarkLevel(level: DarkLevel) {
        dataStore.edit { it[PreferencesKeys.DARK_LEVEL] = level.name }
    }

    val layoutMode: Flow<LayoutMode> = dataStore.data.map { pref ->
        try { LayoutMode.valueOf(pref[PreferencesKeys.LAYOUT_MODE] ?: LayoutMode.ATTACHED.name) }
        catch (_: Exception) { LayoutMode.ATTACHED }
    }

    suspend fun setLayoutMode(mode: LayoutMode) {
        // Islands are temporarily disabled; keep this guard even if another caller
        // tries to select it programmatically.
        if (mode == LayoutMode.ISLANDS) return
        dataStore.edit { it[PreferencesKeys.LAYOUT_MODE] = mode.name }
    }

    /**
     * One-shot migration for the release where Islands is temporarily disabled.
     * Existing users are moved to Attached without resetting any other preference.
     */
    suspend fun migrateDisabledIslandsLayout() {
        dataStore.edit { preferences ->
            val version = preferences[PreferencesKeys.PREFERENCES_MIGRATION_VERSION] ?: 0
            if (version < 1) {
                preferences[PreferencesKeys.LAYOUT_MODE] = LayoutMode.ATTACHED.name
                preferences[PreferencesKeys.PREFERENCES_MIGRATION_VERSION] = 1
            }
        }
    }

    val islandStyle: Flow<IslandStyle> = dataStore.data.map { pref ->
        try { IslandStyle.valueOf(pref[PreferencesKeys.ISLAND_STYLE] ?: IslandStyle.COMFORTABLE.name) }
        catch (_: Exception) { IslandStyle.COMFORTABLE }
    }

    suspend fun setIslandStyle(style: IslandStyle) {
        dataStore.edit { it[PreferencesKeys.ISLAND_STYLE] = style.name }
    }

    val miniPlayerStyle: Flow<MiniPlayerStyle> = dataStore.data.map { pref ->
        try { MiniPlayerStyle.valueOf(pref[PreferencesKeys.MINI_PLAYER_STYLE] ?: MiniPlayerStyle.BAR.name) }
        catch (_: Exception) { MiniPlayerStyle.BAR }
    }

    suspend fun setMiniPlayerStyle(style: MiniPlayerStyle) {
        dataStore.edit { it[PreferencesKeys.MINI_PLAYER_STYLE] = style.name }
    }

    val miniPlayerBackgroundStyle: Flow<MiniPlayerBackgroundStyle> = dataStore.data.map { pref ->
        try { MiniPlayerBackgroundStyle.valueOf(pref[PreferencesKeys.MINI_PLAYER_BG_STYLE] ?: MiniPlayerBackgroundStyle.TRANSLUCENT.name) }
        catch (_: Exception) { MiniPlayerBackgroundStyle.TRANSLUCENT }
    }

    suspend fun setMiniPlayerBackgroundStyle(style: MiniPlayerBackgroundStyle) {
        dataStore.edit { it[PreferencesKeys.MINI_PLAYER_BG_STYLE] = style.name }
    }

    suspend fun setNowPlayingBackground(backgroundStyle: BackgroundStyle) {
        dataStore.edit { it[PreferencesKeys.NOW_PLAYING_BACKGROUND] = backgroundStyle.name }
    }

    val nowPlayingBackground: Flow<BackgroundStyle> = dataStore.data.map { pref ->
        try { BackgroundStyle.valueOf(pref[PreferencesKeys.NOW_PLAYING_BACKGROUND] ?: BackgroundStyle.SOLID_COLOR.name) }
        catch (_: Exception) { BackgroundStyle.SOLID_COLOR }
    }

    val nowPlayingDesign: Flow<NowPlayingDesign> = dataStore.data.map { pref ->
        try { NowPlayingDesign.valueOf(pref[PreferencesKeys.NOW_PLAYING_DESIGN] ?: NowPlayingDesign.YOUTUBE_MUSIC.name) }
        catch (_: Exception) { NowPlayingDesign.YOUTUBE_MUSIC }
    }

    suspend fun setNowPlayingDesign(design: NowPlayingDesign) {
        dataStore.edit { it[PreferencesKeys.NOW_PLAYING_DESIGN] = design.name }
    }

    val audioQuality: Flow<AudioQuality> = dataStore.data.map { preferences ->
        try {
            AudioQuality.valueOf(preferences[PreferencesKeys.AUDIO_QUALITY] ?: AudioQuality.NORMAL.name)
        } catch (e: Exception) {
            AudioQuality.NORMAL
        }
    }

    /** Streaming segmentado SABR/UMP en-proceso (proxy local) antes de recurrir a yt-dlp. Default on. */
    val sabrEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.SABR_ENABLED] ?: true }

    suspend fun setSabrEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SABR_ENABLED] = enabled }
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        try {
            ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
    }

    val dynamicColorFromArtwork: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.DYNAMIC_COLOR] ?: false }
    val highResCoverArt: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.HIGH_RES_COVER] ?: true }
    val crossfadeEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.CROSSFADE] ?: false }

    val seekBarStyle: Flow<SeekBarStyle> = dataStore.data.map { pref ->
        try { SeekBarStyle.valueOf(pref[PreferencesKeys.SEEK_BAR_STYLE] ?: SeekBarStyle.WAVY.name) }
        catch (_: Exception) { SeekBarStyle.WAVY }
    }

    suspend fun setSeekBarStyle(style: SeekBarStyle) {
        dataStore.edit { it[PreferencesKeys.SEEK_BAR_STYLE] = style.name }
    }

    /** Multiplicador de velocidad de reproducción que se pasa directamente a la propiedad `speed` de mpv. */
    val playbackSpeed: Flow<Float> = dataStore.data.map { it[PreferencesKeys.PLAYBACK_SPEED] ?: 1f }

    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { it[PreferencesKeys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2f) }
    }
    val cacheImages: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.CACHE_IMAGES] ?: true }

    /** Guardar logs de la app en disco (carpeta logs local). Default off. */
    val logToFile: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.LOG_TO_FILE] ?: false }

    /** Incluir DEBUG/VERBOSE en el log diario (solo si [logToFile]). */
    val logVerbose: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.LOG_VERBOSE] ?: false }

    val imagesEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.IMAGES_ENABLED] ?: true }
    val minimizeToTray: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.MINIMIZE_TO_TRAY] ?: true }
    val trimMemoryOnTray: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.TRIM_MEMORY_ON_TRAY] ?: true }

    /** Si MusicPlayer se registra para iniciarse cuando Windows arranca (sincronizado con la clave de registro Run). */
    val launchAtStartup: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.LAUNCH_AT_STARTUP] ?: false }

    suspend fun setLaunchAtStartup(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.LAUNCH_AT_STARTUP] = enabled }
    }

    val windowWidth: Flow<Int> = dataStore.data.map { it[PreferencesKeys.WINDOW_WIDTH] ?: 1200 }
    val windowHeight: Flow<Int> = dataStore.data.map { it[PreferencesKeys.WINDOW_HEIGHT] ?: 800 }
    val windowMaximized: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.WINDOW_MAXIMIZED] ?: false }
    val queueLocked: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.QUEUE_LOCKED] ?: false }

    // Almacenamos 5 bandas separadas por coma, default a 0.0
    val equalizerBands: Flow<List<Float>> = dataStore.data.map { pref ->
        val str = pref[PreferencesKeys.EQUALIZER_BANDS] ?: "0,0,0,0,0"
        try {
            str.split(",").map { it.toFloat() }
        } catch(e: Exception) {
            List(5) { 0f }
        }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { it[PreferencesKeys.AUDIO_QUALITY] = quality.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColorFromArtwork(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setHighResCoverArt(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HIGH_RES_COVER] = enabled }
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CROSSFADE] = enabled }
    }

    suspend fun setCacheImages(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CACHE_IMAGES] = enabled }
    }

    suspend fun setLogToFile(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.LOG_TO_FILE] = enabled }
    }

    suspend fun setLogVerbose(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.LOG_VERBOSE] = enabled }
    }

    suspend fun setImagesEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.IMAGES_ENABLED] = enabled }
    }

    suspend fun setMinimizeToTray(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.MINIMIZE_TO_TRAY] = enabled }
    }

    suspend fun setTrimMemoryOnTray(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.TRIM_MEMORY_ON_TRAY] = enabled }
    }

    suspend fun setWindowSize(width: Int, height: Int) {
        dataStore.edit {
            it[PreferencesKeys.WINDOW_WIDTH] = width
            it[PreferencesKeys.WINDOW_HEIGHT] = height
        }
    }

    suspend fun setWindowMaximized(maximized: Boolean) {
        dataStore.edit { it[PreferencesKeys.WINDOW_MAXIMIZED] = maximized }
    }

    /**
     * Persiste la posición y el tamaño en una sola escritura atómica. La función `edit` de DataStore serializa
     * y reemplaza atómicamente todo el archivo de preferencias en cada llamada, por lo que llamarla dos veces
     * seguidas (como hacía la combinación anterior de setWindowMaximized + setWindowSize) duplica ese viaje de
     * disco justo antes de cerrar la aplicación — noticeable como un breve titubeo al cerrar. Una edición
     * combinada lo reduce a la mitad.
     */
    suspend fun setWindowState(maximized: Boolean, width: Int, height: Int) {
        dataStore.edit {
            it[PreferencesKeys.WINDOW_MAXIMIZED] = maximized
            if (!maximized) {
                it[PreferencesKeys.WINDOW_WIDTH] = width
                it[PreferencesKeys.WINDOW_HEIGHT] = height
            }
        }
    }

    suspend fun setQueueLocked(locked: Boolean) {
        dataStore.edit { it[PreferencesKeys.QUEUE_LOCKED] = locked }
    }

    suspend fun setEqualizerBands(bands: List<Float>) {
        dataStore.edit { it[PreferencesKeys.EQUALIZER_BANDS] = bands.joinToString(",") }
    }

    val themePalette: Flow<ThemePalette> = dataStore.data.map { pref ->
        val name = pref[PreferencesKeys.THEME_PALETTE] ?: ThemePalette.DEFAULT.name
        try { ThemePalette.valueOf(name) } catch (_: Exception) { ThemePalette.DEFAULT }
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        dataStore.edit { it[PreferencesKeys.THEME_PALETTE] = palette.name }
    }

    val locale: Flow<AppLocale> = dataStore.data.map { pref ->
        val name = pref[PreferencesKeys.LOCALE] ?: AppLocale.SYSTEM.name
        try { AppLocale.valueOf(name) } catch (_: Exception) { AppLocale.SYSTEM }
    }

    suspend fun setLocale(locale: AppLocale) {
        dataStore.edit { it[PreferencesKeys.LOCALE] = locale.name }
    }

    val youtubeRegion: Flow<YouTubeRegion> = dataStore.data.map { pref ->
        val name = pref[PreferencesKeys.YOUTUBE_REGION] ?: YouTubeRegion.SYSTEM.name
        try { YouTubeRegion.valueOf(name) } catch (_: Exception) { YouTubeRegion.SYSTEM }
    }

    suspend fun setYoutubeRegion(region: YouTubeRegion) {
        dataStore.edit { it[PreferencesKeys.YOUTUBE_REGION] = region.name }
    }

    /**
     * "Modo offline" manual — cuando está activo, la aplicación nunca intenta llamadas de red (búsqueda,
     * feed principal, obtención de letras, sincronización de biblioteca) y solo reproduce canciones
     * descargadas/en caché, independientemente de si una conexión está realmente disponible. Independiente
     * de la DETECCIÓN automática de offline ([example.nucleus.utils.NetworkMonitor]), que reacciona
     * cuando una conexión realmente cae; esto es cuando el usuario opta proactivamente por no usar la red.
     */
    val offlineModeEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.OFFLINE_MODE] ?: false }

    suspend fun setOfflineModeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.OFFLINE_MODE] = enabled }
    }

    // ── Acciones de sincronización remota diferidas (cola offline) ────────────────────────
    // Los envíos remotos a YouTube (like, suscribirse, ...) que fallaron mientras estaba offline se
    // encolan aquí como JSON y se reintenta con PendingSyncQueue una vez que la conectividad vuelve.
    // Ver [PendingAction].

    private val pendingActionsJson = Json { ignoreUnknownKeys = true }

    val pendingActions: Flow<List<PendingAction>> = dataStore.data.map { pref ->
        val raw = pref[PreferencesKeys.PENDING_ACTIONS] ?: return@map emptyList()
        try {
            pendingActionsJson.decodeFromString<List<PendingAction>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addPendingAction(action: PendingAction) {
        dataStore.edit { pref ->
            val current = pref[PreferencesKeys.PENDING_ACTIONS]?.let {
                try { pendingActionsJson.decodeFromString<List<PendingAction>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            // Sustituye acción previa del mismo objetivo (p. ej. like/unlike del mismo id).
            val deduped = when (action) {
                is PendingAction.LikeSong ->
                    current.filterNot { it is PendingAction.LikeSong && it.songId == action.songId }
                is PendingAction.SubscribeArtist ->
                    current.filterNot {
                        it is PendingAction.SubscribeArtist && it.channelId == action.channelId
                    }
            }
            pref[PreferencesKeys.PENDING_ACTIONS] =
                pendingActionsJson.encodeToString(deduped + action)
        }
    }

    suspend fun removePendingAction(action: PendingAction) {
        dataStore.edit { pref ->
            val current = pref[PreferencesKeys.PENDING_ACTIONS]?.let {
                try { pendingActionsJson.decodeFromString<List<PendingAction>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
            pref[PreferencesKeys.PENDING_ACTIONS] = pendingActionsJson.encodeToString(current - action)
        }
    }

}
