package example.nucleus.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import dev.chrisbanes.haze.hazeSource

// Ahora (ojo: paquete "experimental", no se pueden mezclar con los anteriores)
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation

import com.arkivanov.decompose.extensions.compose.subscribeAsState
import example.nucleus.data.repository.AppMode
import example.nucleus.data.repository.LayoutMode
import example.nucleus.data.repository.MiniPlayerBackgroundStyle
import example.nucleus.data.repository.MiniPlayerStyle
import example.nucleus.data.repository.NavigationRailStyle
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.ui.components.MiniPlayer
import example.nucleus.ui.components.glass.LocalLiquidGlass
import example.nucleus.ui.components.dialogs.SnackBar
import example.nucleus.ui.components.player.PlaybackQueuePanel
import example.nucleus.ui.screens.library.CsvImportProgressOverlay
import example.nucleus.ui.screens.library.StatsScreen
import example.nucleus.viewmodels.LibraryPlaylistsViewModel
import example.nucleus.viewmodels.PlayerViewModel
import example.nucleus.viewmodels.YouTubeWatchViewModel
import org.koin.compose.koinInject
import example.nucleus.player.YouTubePlayerService
import example.nucleus.ui.screens.*
import example.nucleus.ui.screens.album.AlbumScreenRoute
import example.nucleus.ui.screens.youtube.YouTubeHomeScreenRoute
import example.nucleus.ui.screens.youtube.YouTubeWatchScreenRoute
import example.nucleus.ui.youtube.YouTubeFloatingMiniPlayer
import example.nucleus.ui.screens.home.HomeScreenRoute
import example.nucleus.ui.screens.library.LibraryScreenRoute
import example.nucleus.ui.themes.LocalDimens
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.expressiveLayoutTween
import example.nucleus.ui.themes.LocalChromeSurface
import example.nucleus.ui.themes.LocalIsSolidBackground
import example.nucleus.ui.themes.LocalLayoutMode
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalSnackbarHostState
import example.nucleus.utils.LocalAnimationsEnabled


data class TabInfo(
    val config: ScreenConfig,
    val icon: ImageVector
)

val musicMainTabs = listOf(
    TabInfo(ScreenConfig.Home, Icons.Filled.Home),
    TabInfo(ScreenConfig.Search, Icons.Filled.Search),
    TabInfo(ScreenConfig.Library, Icons.Filled.LibraryMusic),
)

val musicBottomTabs = listOf(
    TabInfo(ScreenConfig.ListenTogether, Icons.Filled.Groups),
    TabInfo(ScreenConfig.Account, Icons.Filled.Person),
    TabInfo(ScreenConfig.Settings, Icons.Filled.Settings),
)


@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun NavigationDesktop(
    rootComponent: RootComponent,
    userPreferences: UserPreferencesRepository,
    appMode: AppMode,
) {
    val childStack by rootComponent.childStack.subscribeAsState()

    LaunchedEffect(appMode) {
        val root = if (appMode == AppMode.YOUTUBE) ScreenConfig.YouTubeHome else ScreenConfig.Home
        rootComponent.switchTab(root)
    }

    // Retroceso global centralizado (Compose puro, sin AWT): botón "atrás" del ratón vía
    // pointerInput + teclas Escape/Back vía onPreviewKeyEvent, ambos en el Surface raíz.
    // Las pantallas NO deben poner su propio listener de Escape: el KeyDown lo consume el
    // global y su KeyUp caería en la pantalla nueva produciendo doble back.
    val activeConfig = childStack.active.configuration

    val playerViewModel: PlayerViewModel = LocalPlayerViewModel.current
    val snackbarHostState = LocalSnackbarHostState.current
    val playlistsViewModel = koinInject<LibraryPlaylistsViewModel>()
    val csvImportState by playlistsViewModel.csvImportState.collectAsState()
    val youtubePlayerService: YouTubePlayerService = koinInject()
    val youtubeWatchViewModel: YouTubeWatchViewModel = koinInject()
    val youtubeMiniVisible by youtubePlayerService.minimized.collectAsState()
    val youtubeMiniVideo by youtubePlayerService.currentVideo.collectAsState()
    val youtubeFullscreen by youtubePlayerService.fullscreen.collectAsState()

    val navigationRailStyle by userPreferences.navigationRailStyle.collectAsState(NavigationRailStyle.DEFAULT)
    val miniPlayerStyle by userPreferences.miniPlayerStyle.collectAsState(MiniPlayerStyle.BAR)
    val miniPlayerBackgroundStyle by userPreferences.miniPlayerBackgroundStyle.collectAsState(MiniPlayerBackgroundStyle.TRANSLUCENT)
    val liquidGlassEnabled by userPreferences.liquidGlassEnabled.collectAsState(false)

    val playerState by playerViewModel.uiState.collectAsState()
    var isQueueVisible by remember { mutableStateOf(false) }

    val isOnNowPlaying = activeConfig is ScreenConfig.NowPlaying
    val animationsEnabled = LocalAnimationsEnabled.current
    val musicChrome = appMode == AppMode.MUSIC
    // Fuente del cristal del MiniPlayer: la provee App (contentHazeState) y aquí
    // se le ata la fuente a la caja del contenido (hermana-anterior de los
    // overlays: patrón sibling de la guía). Estado aparte del appContentHazeState
    // porque aquel vive en un hazeSource ancestro y Behind ahí selecciona vacío.
    val miniPlayerHazeState = example.nucleus.ui.components.glass.LocalContentHaze.current

    LaunchedEffect(appMode) {
        if (!musicChrome) isQueueVisible = false
    }

    val currentSong = playerState.currentSong
    val queueWidth = 420.dp
    val floatingMiniPlayer =
        miniPlayerStyle == MiniPlayerStyle.FLOATING && currentSong != null && musicChrome
    val dockedMiniPlayer =
        miniPlayerStyle == MiniPlayerStyle.DOCKED && currentSong != null && musicChrome
    val barMiniPlayer =
        miniPlayerStyle == MiniPlayerStyle.BAR && currentSong != null && musicChrome

    val dimens = LocalDimens.current
    val glassNavVisible = liquidGlassEnabled && appMode == AppMode.MUSIC && !isOnNowPlaying
    val glassNavInset = if (glassNavVisible) 112.dp else 0.dp
    // En Now Playing el mini player está oculto (pantalla completa): no hay inset que reservar.
    val baseBottomInset = if (isOnNowPlaying) {
        0.dp
    } else when {
        floatingMiniPlayer -> dimens.miniPlayerHeight + dimens.miniPlayerFloatingMargin * 2
        dockedMiniPlayer -> dimens.miniPlayerHeight
        else -> 0.dp
    }

    // Nos permite entender o mostrar los errores de reproducción en un Snackbar, sin bloquear la UI principal.
    LaunchedEffect(playerViewModel) {
        playerViewModel.playbackMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }


    // Estado del Liquid Glass BETA para los componentes que lo consumen vía
    // CompositionLocal (transporte de Now Playing, likes, chips, menús).
    CompositionLocalProvider(
        LocalLiquidGlass provides liquidGlassEnabled,
    ) {
    CompositionLocalProvider(LocalMiniPlayerInset provides baseBottomInset) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedTransitionScope = this
            val miniPlayerSlot: @Composable (Modifier) -> Unit = { m ->
                MiniPlayer(
                    isOnNowPlaying = isOnNowPlaying,
                    onNowPlaying = {
                        if (isOnNowPlaying) {
                            rootComponent.onBack()
                        } else {
                            isQueueVisible = false
                            rootComponent.navigateTo(ScreenConfig.NowPlaying)
                        }
                    },
                    onToggleQueue = {
                        isQueueVisible = !isQueueVisible
                    },
                    isQueueVisible = isQueueVisible && !isOnNowPlaying,
                    modifier = m,
                    sharedTransitionScope = sharedTransitionScope,
                    floating = floatingMiniPlayer,
                    isDocked = dockedMiniPlayer,
                    backgroundStyle = miniPlayerBackgroundStyle,
                    liquidGlass = liquidGlassEnabled,
                    hazeState = miniPlayerHazeState,
                )
            }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Back) && when {
                        youtubeFullscreen -> {
                            youtubePlayerService.setFullscreen(false)
                            true
                        }

                        rootComponent.canGoBack() -> {
                            rootComponent.onBack()
                            true
                        }

                        else -> false
                    }
                }
                .pointerInput(youtubeFullscreen) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons == PointerButtons(8)) {
                                if (youtubeFullscreen) {
                                    youtubePlayerService.setFullscreen(false)
                                } else if (rootComponent.canGoBack()) {
                                    rootComponent.onBack()
                                }
                            }
                        }
                    }
                },
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {

                // Ancho real de la ventana: la cola superpuesta nunca debe excederlo.
                val windowWidth = maxWidth
                // La cola solo se acopla al lado si al contenido le queda sitio legible
                // después de ceder los 420dp del panel. Con el rail al lado y una ventana
                // estrecha, compartir la fila dejaba ambas mitades inservibles.
                val queueDocked = maxWidth - queueWidth >= 320.dp

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                        // Now Playing siempre es pantalla completa: el rail se oculta solo.
                        // La ventana principal no cambia de layout al entrar en
                        // fullscreen: el vídeo se muestra en una ventana aparte.
                        AnimatedVisibility(
                            visible = !isOnNowPlaying && !glassNavVisible,
                            enter = if (animationsEnabled) fadeIn(expressiveFadeTween()) + expandHorizontally(expressiveLayoutTween()) else EnterTransition.None,
                            exit = if (animationsEnabled) fadeOut(expressiveFadeTween()) + shrinkHorizontally(expressiveLayoutTween()) else ExitTransition.None,
                        ) {
                            if (appMode == AppMode.YOUTUBE) {
                                YouTubeSidebar(
                                    activeConfig = activeConfig,
                                    rootComponent = rootComponent,
                                )
                            } else {
                                if (glassNavVisible) {
                                    LiquidGlassNavigationRail(
                                        tabs = musicMainTabs + musicBottomTabs,
                                        activeConfig = activeConfig,
                                        onTabClick = { rootComponent.switchTab(it) },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    )
                                } else {
                                    when (navigationRailStyle) {
                                        NavigationRailStyle.DEFAULT -> {
                                            NavigationRailDefault(
                                                activeConfig = activeConfig,
                                                changeQueueVisible = { isQueueVisible = it },
                                                rootComponent = rootComponent,
                                                mainTabs = musicMainTabs,
                                                bottomTabs = musicBottomTabs,
                                            )
                                        }

                                        NavigationRailStyle.WIDE -> {
                                            WideNavigationRail(
                                                activeConfig = activeConfig,
                                                changeQueueVisible = { isQueueVisible = it },
                                                rootComponent = rootComponent,
                                                mainTabs = musicMainTabs,
                                                bottomTabs = musicBottomTabs,
                                            )
                                        }
                                    }
                                }
                            }
                        }


                        val islands = LocalLayoutMode.current == LayoutMode.ISLANDS
                        val square = LocalLayoutMode.current == LayoutMode.SQUARE

                        val contentShape: Shape =
                            if (dimens.surfaceCorner > 0.dp) {
                                val roundedFullContent = if (isQueueVisible) dimens.surfaceCorner else 0.dp
                                RoundedCornerShape(
                                    topStart = dimens.surfaceCorner,
                                    bottomStart = dimens.surfaceCorner,
                                    topEnd = roundedFullContent,
                                    bottomEnd = roundedFullContent
                                )
                            } else {
                                RectangleShape
                            }
                        val bottomPadding = if (currentSong != null) 0.dp else dimens.windowPadding

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    end = dimens.windowPadding,
                                    bottom = bottomPadding,
                                ),
                        ) {

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1F)
                                        .fillMaxHeight()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(LocalChromeSurface.current, contentShape)
                                            .clip(contentShape)
                                            .then(
                                                if (islands) Modifier.border(
                                                    0.5.dp,
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                    contentShape
                                                )
                                                else Modifier
                                            )
                                            // Fuente del cristal del MiniPlayer: el contenido
                                            // (hermano-anterior de sus overlays). Ver el comentario
                                            // de miniPlayerHazeState arriba.
                                            .then(
                                                if (miniPlayerHazeState != null) {
                                                    Modifier.hazeSource(miniPlayerHazeState)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(start = glassNavInset),
                                            shape = contentShape,
                                            color = if (LocalIsSolidBackground.current) {
                                                MaterialTheme.colorScheme.background
                                            } else {
                                                Color.Transparent
                                            },
                                            contentColor = MaterialTheme.colorScheme.onBackground,
                                        ) {
                                            ChildStack(
                                                stack = rootComponent.childStack,
                                                animation = if (animationsEnabled) stackAnimation(fade()) else stackAnimation { _, _, _, _ -> null },
                                            ) { child ->

                                                ScreenRouter(
                                                    instance = child.instance,
                                                    rootComponent = rootComponent,
                                                    sharedAnimatedTransitionScope = this@SharedTransitionLayout,
                                                    animatedVisibilityScope = this,
                                                )

                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        AnimatedVisibility(
                                            visible = (dockedMiniPlayer || floatingMiniPlayer) && !isOnNowPlaying,
                                            enter = if (animationsEnabled) fadeIn(expressiveFadeTween()) + slideInVertically(animationSpec = expressiveLayoutTween(), initialOffsetY = { it / 4 }) else EnterTransition.None,
                                            exit = if (animationsEnabled) fadeOut(expressiveFadeTween()) + slideOutVertically(animationSpec = expressiveLayoutTween(), targetOffsetY = { it / 4 }) else ExitTransition.None,
                                        ) {
                                            miniPlayerSlot(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = glassNavInset),
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(8.dp),
                                    ) {
                                        if (glassNavVisible) {
                                            LiquidGlassNavigationRail(
                                                tabs = musicMainTabs + musicBottomTabs,
                                                activeConfig = activeConfig,
                                                onTabClick = { rootComponent.switchTab(it) },
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isQueueVisible && !isOnNowPlaying,
                                    enter = if (animationsEnabled) fadeIn(expressiveFadeTween()) + expandHorizontally(expressiveLayoutTween()) else EnterTransition.None,
                                    exit = if (animationsEnabled) fadeOut(expressiveFadeTween()) + shrinkHorizontally(expressiveLayoutTween()) else ExitTransition.None,
                                ) {
                                    if (queueDocked) {
                                    Row(modifier = Modifier.fillMaxHeight()) {
                                        if (square) {
                                            Box(
                                                modifier = Modifier
                                                    .width(dimens.chromeBorderWidth)
                                                    .fillMaxHeight()
                                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                            )
                                        } else {
                                            Spacer(Modifier.width(dimens.surfaceGap))
                                        }

                                        PlaybackQueuePanel(
                                            state = playerState,
                                            onDismiss = { isQueueVisible = false },
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(queueWidth)
                                                .clip(contentShape)
                                                .then(
                                                    if (islands) Modifier.border(
                                                        0.5.dp,
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                        contentShape
                                                    )
                                                    else Modifier
                                                ),
                                            containerColor = Color.Transparent,
                                            bottomInset = LocalMiniPlayerInset.current,
                                        )
                                    }
                                    } else {
                                        // Sin sitio para la cola acoplada: se superpone al
                                        // contenido como un panel, en vez de compartir la fila
                                        // y dejar ambas mitades ilegibles. El ancho nunca
                                        // excede el de la ventana.
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.45f))
                                                    .clickable(
                                                        interactionSource = remember {
                                                            MutableInteractionSource()
                                                        },
                                                        indication = null,
                                                        onClick = { isQueueVisible = false },
                                                    ),
                                            )
                                            PlaybackQueuePanel(
                                                state = playerState,
                                                onDismiss = { isQueueVisible = false },
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .fillMaxHeight()
                                                    .width(windowWidth.coerceAtMost(queueWidth))
                                                    .clip(contentShape)
                                                    .then(
                                                        if (islands) Modifier.border(
                                                            0.5.dp,
                                                            MaterialTheme.colorScheme.outlineVariant,
                                                            contentShape
                                                        )
                                                        else Modifier
                                                    ),
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                bottomInset = LocalMiniPlayerInset.current,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = barMiniPlayer && !isOnNowPlaying,
                        enter = if (animationsEnabled) {
                            fadeIn(expressiveFadeTween()) +
                                    expandVertically(expressiveLayoutTween(), expandFrom = Alignment.Top)
                        } else EnterTransition.None,
                        exit = if (animationsEnabled) {
                            fadeOut(expressiveFadeTween()) +
                                    shrinkVertically(expressiveLayoutTween(), shrinkTowards = Alignment.Top)
                        } else ExitTransition.None,
                    ) {
                        miniPlayerSlot(Modifier.fillMaxWidth())
                    }

                }

                SnackBar(
                    currentSong = currentSong,
                    floatingMiniPlayer = floatingMiniPlayer,
                    snackbarHostState = snackbarHostState,
                )

                CsvImportProgressOverlay(
                    state = csvImportState,
                    onCancel = { playlistsViewModel.cancelCsvImport() },
                    onDismiss = { playlistsViewModel.dismissCsvImportResult() },
                )

                val miniVideo = youtubeMiniVideo
                if (youtubeMiniVisible && miniVideo != null) {
                    YouTubeFloatingMiniPlayer(
                        playerService = youtubePlayerService,
                        video = miniVideo,
                        onRestore = {
                            youtubeWatchViewModel.restoreFromMini()
                            rootComponent.navigateTo(
                                ScreenConfig.YouTubeWatch(miniVideo.id),
                            )
                        },
                        onClose = { youtubeWatchViewModel.closeMini() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp),
                    )
                }
            }
    }
    }
}
    }
    }





@Composable
fun ScreenRouter(
    instance: RootComponent.Child,
    rootComponent: RootComponent,
    sharedAnimatedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val navigator: (ScreenConfig) -> Unit = rootComponent::navigateTo
    when (instance) {
        is RootComponent.Child.Home -> {
            HomeScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Search -> {
            SearchScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Album -> {
            AlbumScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
            )
        }

        is RootComponent.Child.Playlist -> {
            PlaylistScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
            )
        }

        is RootComponent.Child.Artist -> {
            ArtistScreenRoute(
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
                viewModel = instance.component.viewModel,
            )
        }

        is RootComponent.Child.Library -> {
            LibraryScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Account -> {
            AccountScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Settings -> {
            SettingsScreen()
        }

        is RootComponent.Child.Stats -> {
            StatsScreen(
                onBack = { rootComponent.onBack() },
            )
        }

        is RootComponent.Child.ListenTogether -> {
            ListenTogetherScreen()
        }

        is RootComponent.Child.YouTubeBrowse -> {
            YouTubeBrowseScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
            )
        }

        is RootComponent.Child.YouTubeHome -> {
            YouTubeHomeScreenRoute(
                viewModel = instance.component.viewModel,
                onOpenVideo = { videoId ->
                    rootComponent.navigateTo(ScreenConfig.YouTubeWatch(videoId))
                },
            )
        }

        is RootComponent.Child.YouTubeShorts -> {
            YouTubeHomeScreenRoute(
                viewModel = instance.component.viewModel,
                onOpenVideo = { videoId ->
                    rootComponent.navigateTo(ScreenConfig.YouTubeWatch(videoId))
                },
                shortsOnly = true,
            )
        }

        is RootComponent.Child.YouTubeWatch -> {
            val playerService = koinInject<YouTubePlayerService>()
            YouTubeWatchScreenRoute(
                viewModel = instance.component.viewModel,
                playerService = playerService,
                onBack = { rootComponent.onBack() },
                onOpenVideo = { videoId ->
                    rootComponent.navigateTo(ScreenConfig.YouTubeWatch(videoId))
                },
            )
        }

        is RootComponent.Child.NowPlaying -> {
            NowPlayingScreen(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
                sharedTransitionScope = sharedAnimatedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}
