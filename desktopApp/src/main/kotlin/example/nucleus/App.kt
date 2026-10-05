package example.nucleus


import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.YouTubeLocale
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.NucleusDecoratedWindowScope
import dev.nucleusframework.aot.runtime.AotRuntime
import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.autolaunch.AutoLaunchResult
import dev.nucleusframework.window.NucleusDecoratedWindowTheme
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.macOSLargeCornerRadius
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import dev.nucleusframework.window.styling.TitleBarColors
import dev.nucleusframework.window.styling.TitleBarMetrics
import dev.nucleusframework.window.styling.TitleBarStyle
import example.nucleus.bootstrap.CrashReportDialog
import example.nucleus.data.account.AccountManager
import example.nucleus.data.repository.AppLocale
import example.nucleus.data.repository.BackgroundStyle
import example.nucleus.data.repository.CrashReport
import example.nucleus.data.repository.CrashReportRepository
import example.nucleus.data.repository.LayoutMode
import example.nucleus.data.repository.ThemeMode
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.data.repository.YouTubeRegion
import example.nucleus.navigation.NavigationDesktop
import example.nucleus.navigation.RootComponent
import example.nucleus.overlay.GlobalHotkeyManager
import example.nucleus.overlay.HotkeyCombo
import example.nucleus.player.PlaybackState
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.components.artwork.ArtworkColors
import example.nucleus.ui.components.artwork.LocalArtworkColors
import example.nucleus.ui.components.artwork.rememberArtworkColors
import example.nucleus.ui.components.background.BackgroundStyle
import example.nucleus.ui.themes.AppTheme
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalPlaylistsViewModel
import example.nucleus.utils.LocalSnackbarHostState
import example.nucleus.utils.LocalSnackbarScope
import example.nucleus.utils.LocalUserPreferences
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.viewmodels.AppUpdateInfo
import example.nucleus.viewmodels.AppViewModel
import example.nucleus.viewmodels.DownloadViewModel
import example.nucleus.viewmodels.LibraryPlaylistsViewModel
import example.nucleus.viewmodels.PlayerViewModel
import example.nucleus.viewmodels.UpdateStatus
import example.nucleus.windows.TaskBarMediaWidget
import example.nucleus.windows.WindowsThumbBar
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.newFullscreenControls
import org.koin.compose.koinInject
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import example.nucleus.ui.themes.LocalChromeSurface
import example.nucleus.ui.themes.LocalLayoutMode

@Composable
fun NucleusApplicationScope.App(
    rootComponent: RootComponent,
    appViewModel: AppViewModel,
    playerViewModel: PlayerViewModel,
    downloadViewModel: DownloadViewModel,
    userPreferences: UserPreferencesRepository,
    onExit: () -> Unit,
    windowState: WindowState,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    var isVisible by remember { mutableStateOf(true) }
    val minimizeToTray by remember { userPreferences.minimizeToTray }.collectAsState(false)
    val trimMemoryOnTray by remember(userPreferences) { userPreferences.trimMemoryOnTray }.collectAsState(true)

    LaunchedEffect(isVisible, trimMemoryOnTray) {
        if (!isVisible && trimMemoryOnTray) {
            delay(2000.milliseconds)
            // Liberar los bitmaps decodificados (Coil) — el disk cache de 256 MB los conserva, así
            // que solo se pierde la copia en RAM. Luego se recorta el working set de Windows.
            runCatching { example.nucleus.ui.components.CoilSetup.evictMemoryCache() }
            example.nucleus.utils.WorkingSetTrimmer.trim()
        }
    }

    LaunchedEffect(Unit) {
        userPreferences.launchAtStartup.distinctUntilChanged().collect { enabled ->
            val result = if (enabled) AutoLaunch.enable() else AutoLaunch.disable()
            if (result != AutoLaunchResult.OK && result != AutoLaunchResult.UNCHANGED) {
                Napier.w("AutoLaunch sync result: $result")
            }
        }
    }

    // Precalienta el pipeline WEB (solucionador EJS + PoToken sidecar) en background para que
    // la primera reproducción web no pague el cold-start (~20-25s de prepare). Nunca bloquea la UI.
    // En training AOT se omite: GraalJS/Truffle cargan una enormidad de clases, usan red y
    // lanzan procesos, las tres cosas que un training no quiere (tamaño, determinismo, efectos).
    LaunchedEffect(Unit) {
        if (!AotRuntime.isTraining()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                example.nucleus.utils.cipher.PlayerPipelineWarmup.warmup()
            }
        }
    }

    // Estado del overlay
    val hotkeyManager: GlobalHotkeyManager = koinInject()
    val overlayEnabled by remember(userPreferences) { userPreferences.overlayHotkeyEnabled }.collectAsState(true)
    val overlayCode by remember(userPreferences) { userPreferences.overlayHotkeyCode }.collectAsState(0)
    val overlayMods by remember(userPreferences) { userPreferences.overlayHotkeyMods }.collectAsState(0)

    LaunchedEffect(overlayEnabled, overlayCode, overlayMods) {
        // jnativehook (hook nativo global) solo se registra cuando el overlay está activado:
        // si está desactivado, no cargamos su DLL ni el hook del sistema. En training tampoco:
        // un hook global de teclado en CI no aporta nada al perfilado y mete clases nativas.
        val hotkeysLive = overlayEnabled && !AotRuntime.isTraining()
        if (hotkeysLive) hotkeyManager.start() else hotkeyManager.stop()
        hotkeyManager.setEnabled(hotkeysLive)
        hotkeyManager.updateCombo(HotkeyCombo.fromPrefs(overlayCode, overlayMods))
    }

    // Estado de sincronización YTM
    val syncUtils: example.nucleus.utils.SyncUtils = koinInject()
    val ytmSyncEnabled by remember(userPreferences) { userPreferences.ytmSyncEnabled }.collectAsState(false)
    val offlineMode by remember(userPreferences) { userPreferences.offlineModeEnabled }.collectAsState(false)
    val syncState by syncUtils.syncState.collectAsState()
    var showYtmSyncWarningFromMenu by remember { mutableStateOf(false) }

    val isLoggedIn by remember { AccountManager.loginState }.collectAsState(false)
    val accountInfo by produceState<AccountInfo?>(initialValue = null, isLoggedIn) {
        // En training no se toca red: la cuenta (y sus serializadores/modelos de API) es la
        // mayor fuente de no determinismo entre sesiones de training.
        value = if (isLoggedIn && !AotRuntime.isTraining()) YouTube.accountInfo().getOrNull() else null
    }

    fun handleExit() {
        scope.launch {
            userPreferences.setWindowState(
                maximized = windowState.placement == WindowPlacement.Maximized,
                width = windowState.size.width.value.toInt(),
                height = windowState.size.height.value.toInt(),
            )
            onExit()
        }
    }

    val playerUiState by playerViewModel.uiState.collectAsState()
    // BUFFERING/LOADING con intención de play: no flipar a "pausado" (thumbbar spam + UI mentirosa).
    val isPlaying = when (playerUiState.playbackState) {
        PlaybackState.PLAYING,
        PlaybackState.BUFFERING,
        -> true
        PlaybackState.LOADING -> playerUiState.currentSong != null
        else -> false
    }
    val currentSong = playerUiState.currentSong

    val appLocale by remember(userPreferences) { userPreferences.locale }.collectAsState(AppLocale.SYSTEM)
    LaunchedEffect(appLocale) {
        val newLocale = appLocale.tag?.let { Locale.forLanguageTag(it) }
        if (newLocale != null) Locale.setDefault(newLocale)
    }

    val dynamicColorEnabled by remember(userPreferences) { userPreferences.dynamicColorFromArtwork }.collectAsState(false)
    val artworkColors = if (dynamicColorEnabled) {
        rememberArtworkColors(currentSong?.thumbnailUrl)
    } else {
        ArtworkColors.Default
    }
    val themeMode by remember(userPreferences) { userPreferences.themeMode }.collectAsState(ThemeMode.SYSTEM)
    val youtubeRegion by remember(userPreferences) { userPreferences.youtubeRegion }.collectAsState(YouTubeRegion.SYSTEM)
    val animationsEnabled by remember(userPreferences) { userPreferences.animationsEnabled }.collectAsState(true)

    if (!isVisible || minimizeToTray) {
        TrayCustom(
            playerViewModel = playerViewModel,
            onToggleVisibility = { isVisible = !isVisible },
            onShow = { isVisible = true },
            handleExit = ::handleExit
        )
    }

    LaunchedEffect(youtubeRegion) {
        if (youtubeRegion == YouTubeRegion.SYSTEM) {
            val sysLocale = Locale.getDefault()
            val rawCountry = sysLocale.country
            val rawLang = sysLocale.toLanguageTag()
            val safeGl = if (rawCountry.matches(Regex("^[a-zA-Z]{2}$"))) rawCountry.uppercase() else "US"
            val safeHl = if (rawLang.matches(Regex("^[a-zA-Z]{2}(-[a-zA-Z]{2})?$"))) rawLang else "en-US"
            YouTube.locale = YouTubeLocale(safeGl, safeHl)
        } else {
            YouTube.locale = YouTubeLocale(youtubeRegion.gl, youtubeRegion.hl)
        }
    }

    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val uiScaleFactor by userPreferences.uiScale.collectAsState(1f)
    val backgroundAppStyle by userPreferences.appBackgroundStyle.collectAsState(BackgroundStyle.SOLID_COLOR)

    AppTheme(artworkColors = artworkColors, userPreferences = userPreferences) {

        IntUiTheme(
            theme = if (isDark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition(),
            styling = ComponentStyling.decoratedWindow(),
            swingCompatMode = true
        ) {
            val playlistsViewModel: LibraryPlaylistsViewModel = koinInject()
            CompositionLocalProvider(
                LocalArtworkColors provides artworkColors,
                LocalSnackbarHostState provides snackbarHostState,
                LocalSnackbarScope provides scope,
                LocalPlayerViewModel provides playerViewModel,
                LocalDownloadViewModel provides downloadViewModel,
                LocalPlaylistsViewModel provides playlistsViewModel,
                LocalUserPreferences provides userPreferences,
                LocalAnimationsEnabled provides animationsEnabled,
            ) {
                // Widget de medios dentro de la barra de tareas de Windows (antes de la bandeja).
                // Se compone en su propia ventana pero hereda estos CompositionLocals y el tema.
                // Opcional: se activa desde Ajustes → Aplicación.
                val taskbarWidgetEnabled by remember(userPreferences) { userPreferences.taskbarWidgetEnabled }
                    .collectAsState(true)
                if (taskbarWidgetEnabled) {
                    TaskBarMediaWidget(
                        playerViewModel = playerViewModel,
                        userPreferences = userPreferences,
                        animationsEnabled = animationsEnabled,
                        onExit = { handleExit() },
                        onShowWindow = { isVisible = true },
                    )
                }

                val titleBarStyle = TitleBarStyle(
                    colors = TitleBarColors(
                        background = LocalChromeSurface.current,
                        inactiveBackground = LocalChromeSurface.current,
                        border = Color.Transparent,
                        content = MaterialTheme.colorScheme.onSurface,
                    ),
                    metrics = TitleBarMetrics(height = 36.dp),
                )

                NucleusDecoratedWindowTheme(isDark = isDark, titleBarStyle = titleBarStyle) {

                    DecoratedWindow(
                        onCloseRequest = { if (minimizeToTray) isVisible = false else handleExit() },
                        state = windowState,
                        visible = isVisible,
                        transparent = true,
                        title = stringResource(Res.string.app_name),
                        icon = painterResource(Res.drawable.PaltaSound),
                        minimumSize = DpSize(900.dp, 600.dp),
                    ) {

                        // Estado de actualizaciones. En training no se comprueba: es red (no determinista
                        // para la cache) y el resultado no se usa para nada en un training.
                        val updateStatus by appViewModel.updateStatus.collectAsState()
                        val showInstallPrompt by appViewModel.showInstallPrompt.collectAsState()
                        LaunchedEffect(Unit) {
                            if (!AotRuntime.isTraining()) appViewModel.checkForUpdates()
                        }

                        WindowsTaskbarIntegration(
                            isVisible = isVisible,
                            isPlaying = isPlaying,
                            onPrevious = { playerViewModel.previous() },
                            onPlayPause = { playerViewModel.togglePlayPause() },
                            onNext = { playerViewModel.next() },
                        )

                        UpdateReadyDialog(
                            updateStatus = updateStatus,
                            showInstallPrompt = showInstallPrompt,
                            onInstall = { appViewModel.installUpdate { handleExit() } },
                            onPostpone = { appViewModel.postponeInstall() },
                        )

                        YtmSyncWarningDialog(
                            show = showYtmSyncWarningFromMenu,
                            onConfirm = {
                                scope.launch { userPreferences.setYtmSyncEnabled(true) }
                                showYtmSyncWarningFromMenu = false
                            },
                            onDismiss = { showYtmSyncWarningFromMenu = false },
                        )

                        UnsentCrashReportsDialog()

                        val mpvError by playerViewModel.mpvError.collectAsState()
                        if (mpvError != null) {
                            val scheme = MaterialTheme.colorScheme
                            AlertDialog(
                                onDismissRequest = { playerViewModel.clearMpvError() },
                                containerColor = scheme.surfaceContainerHigh,
                                titleContentColor = scheme.onSurface,
                                textContentColor = scheme.onSurfaceVariant,
                                title = { Text("Error de audio (libmpv)", color = scheme.onSurface) },
                                text = { Text(mpvError ?: "", color = scheme.onSurfaceVariant) },
                                confirmButton = {
                                    TextButton(onClick = { playerViewModel.clearMpvError() }) {
                                        Text("Entendido", color = scheme.primary)
                                    }
                                },
                            )
                        }

                        BackgroundStyle(
                            imageUrl = playerUiState.currentSong?.thumbnailUrl,
                            backgroundStyle = backgroundAppStyle
                        ) {

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(LocalChromeSurface.current)
                            ) {
                                Column {

                                    val isSquareLayout = LocalLayoutMode.current == LayoutMode.SQUARE
                                    val navStack by rootComponent.childStack.subscribeAsState()
                                    val canGoBack = navStack.items.size > 1

                                    TitleBar(
                                        modifier = if (isSquareLayout) Modifier else Modifier.macOSLargeCornerRadius(),
                                        style = titleBarStyle
                                    ) {

                                        DesktopTitleBar(
                                            currentSong = currentSong?.title,
                                            isPlaying = isPlaying,
                                            isLoggedIn = isLoggedIn,
                                            accountInfo = accountInfo,
                                            ytmSyncEnabled = ytmSyncEnabled,
                                            isSyncing = syncState.overallStatus is example.nucleus.utils.SyncStatus.Syncing,
                                            isOfflineMode = offlineMode,
                                            canGoBack = canGoBack,
                                            onBack = { if (canGoBack) rootComponent.onBack() },
                                            onRefresh = { rootComponent.refresh() },
                                            onToggleOfflineMode = { enabled ->
                                                scope.launch { userPreferences.setOfflineModeEnabled(enabled) }
                                            },
                                            onToggleSync = { enabled ->
                                                scope.launch { userPreferences.setYtmSyncEnabled(enabled) }
                                            },
                                            onSyncNow = { syncUtils.performFullSync() },
                                        )

                                    }
                                    if (isSquareLayout) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            thickness = 1.dp
                                        )
                                    }
                                    CompositionLocalProvider(
                                        LocalDensity provides Density(
                                            density = LocalDensity.current.density * uiScaleFactor,
                                            fontScale = LocalDensity.current.fontScale,
                                        )
                                    )
                                    {
                                        key(appLocale) {
                                            NavigationDesktop(
                                                rootComponent = rootComponent,
                                                userPreferences = userPreferences
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Diálogos del ciclo de vida de la ventana ────────────────────────────────

@Composable
private fun UpdateReadyDialog(
    updateStatus: UpdateStatus,
    showInstallPrompt: Boolean,
    onInstall: () -> Unit,
    onPostpone: () -> Unit,
) {
    val readyStatus = updateStatus as? UpdateStatus.Ready
    if (showInstallPrompt && readyStatus != null) {
        val info = readyStatus.info
        // Resolver los colores en esta composición (tema real de la app, incl. modo oscuro),
        // no dentro del content del diálogo, que se compone en su propia ventana y puede
        // caer al tema por defecto (claro) dejando texto negro sobre fondo oscuro.
        val scheme = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onPostpone,
            containerColor = scheme.surfaceContainerHigh,
            titleContentColor = scheme.onSurface,
            textContentColor = scheme.onSurfaceVariant,
            tonalElevation = 0.dp,
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text(stringResource(Res.string.update_ready_title), color = scheme.onSurface) },
            text = { Text(stringResource(Res.string.update_ready_message, info.latestVersion), color = scheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = onInstall) { Text(stringResource(Res.string.update_install_now), color = scheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = onPostpone) { Text(stringResource(Res.string.update_install_later), color = scheme.primary) }
            },
        )
    }
}

@Composable
private fun YtmSyncWarningDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (show) {
        val scheme = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = scheme.surfaceContainerHigh,
            titleContentColor = scheme.onSurface,
            textContentColor = scheme.onSurfaceVariant,
            tonalElevation = 0.dp,
            title = { Text(stringResource(Res.string.ytm_sync_warning_title), color = scheme.onSurface) },
            text = { Text(stringResource(Res.string.ytm_sync_warning_message), color = scheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(stringResource(Res.string.ytm_sync_warning_confirm), color = scheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel), color = scheme.primary) }
            },
        )
    }
}

@Composable
private fun UnsentCrashReportsDialog() {
    var unsentCrashReports by remember { mutableStateOf<List<Pair<java.io.File, CrashReport>>>(emptyList()) }
    var showCrashDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val reports = CrashReportRepository.getUnsentReports()
        if (reports.isNotEmpty()) {
            unsentCrashReports = reports
            showCrashDialog = true
        }
    }
    if (showCrashDialog && unsentCrashReports.isNotEmpty()) {
        CrashReportDialog(
            reports = unsentCrashReports,
            onSend = {
                unsentCrashReports.forEach { (_, report) -> CrashReportRepository.openCrashAsGitHubIssue(report) }
                CrashReportRepository.markAllAsSent()
                showCrashDialog = false
            },
            onDismiss = {
                CrashReportRepository.markAllAsSent()
                showCrashDialog = false
            },
        )
    }
}

// ── Integración con Windows (taskbar / thumb bar) ────────────────────────────

/**
 * Espera el HWND real de la ventana Tao (getNativeHandle, no getHandle — el primero traduce el
 * handle interno de Tao al HWND real vía NativeTaoBridge.nativeHwndHandle; `taoHandle` devuelve el
 * handle interno, que en dev JVM es 1, un placeholder inválido para IsWindow) y subclasea la ventana
 * para los botones de miniaturas de la taskbar.
 */
@Composable
private fun NucleusDecoratedWindowScope.WindowsTaskbarIntegration(
    isVisible: Boolean,
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    if (!isWindows()) return
    val thumbBar = remember { WindowsThumbBar(onPrevious, onPlayPause, onNext) }

    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        var attempts = 0
        while ((nucleusWindow.unsafe.taoWindow?.nativeHandle ?: 0L) == 0L && attempts++ < 100) {
            delay(50.milliseconds)
        }
        val hwnd = nucleusWindow.unsafe.taoWindow?.nativeHandle
        if (hwnd != null && hwnd != 0L) {
            Napier.i("[thumbbar] hwnd=0x${hwnd.toString(16)}")
            thumbBar.init(hwnd)
        }
    }

    LaunchedEffect(isPlaying) {
        thumbBar.setPlaying(isPlaying)
    }
}

private fun isWindows(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("win")
