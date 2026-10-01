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
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

// Ahora (ojo: paquete "experimental", no se pueden mezclar con los anteriores)
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation

import com.arkivanov.decompose.extensions.compose.subscribeAsState
import example.nucleus.data.repository.LayoutMode
import example.nucleus.data.repository.MiniPlayerBackgroundStyle
import example.nucleus.data.repository.MiniPlayerStyle
import example.nucleus.data.repository.NavigationRailStyle
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.ui.components.MiniPlayer
import example.nucleus.ui.components.dialogs.SnackBar
import example.nucleus.ui.components.player.PlaybackQueuePanel
import example.nucleus.ui.screens.library.CsvImportProgressOverlay
import example.nucleus.ui.screens.library.StatsScreen
import example.nucleus.viewmodels.LibraryPlaylistsViewModel
import example.nucleus.viewmodels.PlayerViewModel
import org.koin.compose.koinInject
import example.nucleus.ui.screens.*
import example.nucleus.ui.screens.album.AlbumScreenRoute
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

val mainTabs = listOf(
    TabInfo(ScreenConfig.Home, Icons.Filled.Home),
    TabInfo(ScreenConfig.Search, Icons.Filled.Search),
    TabInfo(ScreenConfig.Library, Icons.Filled.LibraryMusic),
)

val bottomTabs = listOf(
    TabInfo(ScreenConfig.ListenTogether, Icons.Filled.Groups),
    TabInfo(ScreenConfig.Account, Icons.Filled.Person),
    TabInfo(ScreenConfig.Settings, Icons.Filled.Settings),
)


@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun NavigationDesktop(rootComponent: RootComponent, userPreferences: UserPreferencesRepository) {
    val childStack by rootComponent.childStack.subscribeAsState()
    val activeConfig = childStack.active.configuration

    val playerViewModel: PlayerViewModel = LocalPlayerViewModel.current
    val snackbarHostState = LocalSnackbarHostState.current
    val playlistsViewModel = koinInject<LibraryPlaylistsViewModel>()
    val csvImportState by playlistsViewModel.csvImportState.collectAsState()

    val navigationRailStyle by userPreferences.navigationRailStyle.collectAsState(NavigationRailStyle.DEFAULT)
    val miniPlayerStyle by userPreferences.miniPlayerStyle.collectAsState(MiniPlayerStyle.BAR)
    val miniPlayerBackgroundStyle by userPreferences.miniPlayerBackgroundStyle.collectAsState(MiniPlayerBackgroundStyle.TRANSLUCENT)

    val playerState by playerViewModel.uiState.collectAsState()
    var isQueueVisible by remember { mutableStateOf(false) }

    val isOnNowPlaying = activeConfig is ScreenConfig.NowPlaying
    val animationsEnabled = LocalAnimationsEnabled.current

    val currentSong = playerState.currentSong
    val queueWidth = 420.dp
    val floatingMiniPlayer = miniPlayerStyle == MiniPlayerStyle.FLOATING && currentSong != null
    val dockedMiniPlayer = miniPlayerStyle == MiniPlayerStyle.DOCKED && currentSong != null
    val barMiniPlayer = miniPlayerStyle == MiniPlayerStyle.BAR && currentSong != null

    val dimens = LocalDimens.current
    // En Now Playing el mini player está oculto (pantalla completa): no hay inset que reservar.
    val floatingBottomInset = if (isOnNowPlaying) {
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


    CompositionLocalProvider(LocalMiniPlayerInset provides floatingBottomInset) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedTransitionScope = this
            val hazeState = rememberHazeState()
            val miniPlayerSlot: @Composable (Modifier) -> Unit = { m ->
                MiniPlayerHost(
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
                    hazeState = hazeState,
                )
            }
        Surface(
            modifier = Modifier.fillMaxSize(),
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
                        AnimatedVisibility(
                            visible = !isOnNowPlaying,
                            enter = if (animationsEnabled) fadeIn(expressiveFadeTween()) + expandHorizontally(expressiveLayoutTween()) else EnterTransition.None,
                            exit = if (animationsEnabled) fadeOut(expressiveFadeTween()) + shrinkHorizontally(expressiveLayoutTween()) else ExitTransition.None,
                        ) {
                            when (navigationRailStyle) {
                                NavigationRailStyle.DEFAULT -> {
                                    NavigationRailDefault(
                                        activeConfig = activeConfig,
                                        changeQueueVisible = { isQueueVisible = it },
                                        rootComponent = rootComponent,
                                    )
                                }

                                NavigationRailStyle.WIDE -> {
                                    WideNavigationRail(
                                        activeConfig = activeConfig,
                                        changeQueueVisible = { isQueueVisible = it },
                                        rootComponent = rootComponent,
                                    )
                                }
                            }
                        }


                        val islands = LocalLayoutMode.current == LayoutMode.ISLANDS
                        val square = LocalLayoutMode.current == LayoutMode.SQUARE
                        // Formas M3E del armazón: el marco de contenido usa la escala expresiva;
                        // en Square se suavizan las esquinas (small) manteniendo los bordes separadores.
                        val contentShape: Shape = when {
                            islands -> RoundedCornerShape(dimens.surfaceCorner)
                            square -> AppShapes.small
                            else -> AppShapes.xLarge
                        }
                        val bottomPadding = if (currentSong != null) 0.dp else dimens.windowPadding

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = dimens.windowPadding, bottom = bottomPadding)
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
                                            // El contenido de las rutas es la fuente del desenfoque
                                            // de fondo del mini reproductor flotante (Haze). Solo se
                                            // captura cuando la tarjeta flotante lo necesita: capturar
                                            // una capa cada frame tiene coste (GPU), así que en modo
                                            // barra u otros fondos se omite.
                                            .then(
                                                if ((floatingMiniPlayer || dockedMiniPlayer) && !isOnNowPlaying && miniPlayerBackgroundStyle == MiniPlayerBackgroundStyle.TRANSLUCENT) {
                                                    Modifier.hazeSource(hazeState)
                                                } else Modifier
                                            )
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = contentShape,
                                            color = if (LocalIsSolidBackground.current) {
                                                MaterialTheme.colorScheme.background
                                            } else {
                                                Color.Transparent
                                            },
                                            contentColor = MaterialTheme.colorScheme.onBackground,
                                        ) {
                                            // Las rutas se renderizan a pantalla completa: su contenido pasa por
                                            // debajo de la tarjeta flotante y cada screen suma LocalMiniPlayerInset
                                            // a su contentPadding para que el scroll nunca quede oculto.
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
                                            .align(Alignment.BottomCenter)
                                    ) {
                                        AnimatedVisibility(
                                            visible = (dockedMiniPlayer || floatingMiniPlayer) && !isOnNowPlaying,
                                            enter = if (animationsEnabled) fadeIn(expressiveFadeTween()) + slideInVertically(animationSpec = expressiveLayoutTween(), initialOffsetY = { it / 4 }) else EnterTransition.None,
                                            exit = if (animationsEnabled) fadeOut(expressiveFadeTween()) + slideOutVertically(animationSpec = expressiveLayoutTween(), targetOffsetY = { it / 4 }) else ExitTransition.None,
                                        ) {
                                            miniPlayerSlot(Modifier.fillMaxWidth())
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
                                            // La lista de la cola no debe quedar bajo el mini
                                            // reproductor cuando este flota o está acoplado.
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
            }
        }
    }
    }
}

@Composable
private fun MiniPlayerHost(
    isOnNowPlaying: Boolean,
    onNowPlaying: () -> Unit,
    onToggleQueue: () -> Unit,
    isQueueVisible: Boolean,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    floating: Boolean = false,
    isDocked: Boolean = false,
    backgroundStyle: MiniPlayerBackgroundStyle = MiniPlayerBackgroundStyle.TRANSLUCENT,
    hazeState: HazeState? = null,
) {
    MiniPlayer(
        isOnNowPlaying = isOnNowPlaying,
        onNowPlaying = onNowPlaying,
        onToggleQueue = onToggleQueue,
        isQueueVisible = isQueueVisible,
        modifier = modifier,
        sharedTransitionScope = sharedTransitionScope,
        floating = floating,
        isDocked = isDocked,
        backgroundStyle = backgroundStyle,
        hazeState = hazeState,
    )
}


fun Route.toConfig(): ScreenConfig = when (this) {
    Route.Home -> ScreenConfig.Home
    Route.Search -> ScreenConfig.Search
    Route.Library -> ScreenConfig.Library
    Route.Account -> ScreenConfig.Account
    Route.Settings -> ScreenConfig.Settings
    Route.ListenTogether -> ScreenConfig.ListenTogether
    Route.NowPlaying -> ScreenConfig.NowPlaying
    Route.Stats -> ScreenConfig.Stats
    // Los episodios de pódcast usan un álbum con id MPSP... (el id del pódcast);
    // ese id solo es válido en la ruta de Playlist (PlaylistScreen reutilizado).
    is Route.Album ->
        if (browseId.startsWith("MPSP")) ScreenConfig.Playlist(browseId)
        else ScreenConfig.Album(browseId)
    is Route.Playlist -> ScreenConfig.Playlist(playlistId)
    is Route.Artist -> ScreenConfig.Artist(artistId)
    is Route.YouTubeBrowse -> ScreenConfig.YouTubeBrowse(browseId, params)
}

@Composable
fun ScreenRouter(
    instance: RootComponent.Child,
    rootComponent: RootComponent,
    sharedAnimatedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val navigator = createNavigator(rootComponent)
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


// Helper para simplificar las llamadas
@Composable
fun createNavigator(rootComponent: RootComponent): (Route) -> Unit = { route ->
    rootComponent.navigateTo(route.toConfig())
}

