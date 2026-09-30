@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.MediaInfo
import example.nucleus.models.MediaMetadata
import example.nucleus.navigation.Route
import example.nucleus.generated.resources.*
import example.nucleus.generated.resources.Res
import example.nucleus.ui.components.EqualizerDialog
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.expressiveLayoutTween
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalUserPreferences
import example.nucleus.viewmodels.PlayerUiState
import example.nucleus.viewmodels.QueueSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

enum class NowPlayingTab { LYRICS, QUEUE, INFO }

/** Espera (ms) sin actividad del puntero antes de que el cromo de Now Playing se oculte. */
private const val AUTO_HIDE_REST_MS = 1_500L

/**
 * Estado del auto-hide del cromo de Now Playing (barra superior, progreso, transporte,
 * volumen y botón de colapso), inspirado en Sonora.
 *
 * `hidden` va de 0 (visible) a 1 (oculto). El cromo permanece despierto mientras el
 * puntero esté sobre alguno de sus elementos (`topBarHover`/`chromeHover`), mientras el
 * popup de volumen esté abierto (`volumePopup`), mientras haya un botón del ratón
 * presionado (`pointerDown`, cubre seeks y reordenados lentos) o mientras corran menús
 * locales. Cualquier movimiento/press/scroll hace `wake()`; hasta que eso no ocurre
 * una vez, el cromo nunca se oculta (al abrir Now Playing permanece visible).
 */
@Stable
internal class NowPlayingAutoHide {
    var lastActiveNanos by mutableStateOf(System.nanoTime())
        private set
    var interacted by mutableStateOf(false)
        private set
    var topBarHover by mutableStateOf(false)
    var chromeHover by mutableStateOf(false)
    var volumePopup by mutableStateOf(false)
    var pointerDown by mutableStateOf(false)

    val busy: Boolean
        get() = topBarHover || chromeHover || volumePopup || pointerDown

    val hidden = Animatable(0f)

    fun wake() {
        lastActiveNanos = System.nanoTime()
        interacted = true
    }
}

/**
 * Motor del auto-hide: cada 100 ms decide si el cromo debe ocultarse y anima `hidden`
 * con un muelle (o snap si las animaciones están desactivadas). `extraBusy` cubre
 * overlays locales (menú de la barra, ecualizador).
 */
@Composable
private fun NowPlayingAutoHideDriver(
    autoHide: NowPlayingAutoHide,
    enabled: Boolean,
    extraBusy: () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val animationsEnabled = LocalAnimationsEnabled.current

    LaunchedEffect(enabled, animationsEnabled) {
        var target = 0f
        while (true) {
            val idleMs = (System.nanoTime() - autoHide.lastActiveNanos) / 1_000_000
            val next =
                if (enabled && autoHide.interacted && !autoHide.busy && !extraBusy() &&
                    idleMs >= AUTO_HIDE_REST_MS
                ) {
                    1f
                } else {
                    0f
                }
            if (next != target) {
                target = next
                if (animationsEnabled) {
                    // En un scope aparte para no bloquear el tick: cada animateTo sobre el
                    // mismo Animatable cancela al anterior, así el wake corta el hundimiento.
                    scope.launch {
                        autoHide.hidden.animateTo(
                            targetValue = next,
                            animationSpec = spring(
                                dampingRatio = 0.86f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                } else {
                    autoHide.hidden.snapTo(next)
                }
            }
            delay(100)
        }
    }
}

/**
 * Croma auto-ocultable: se desvanece y se hunde (o sube, con `sink` negativo) según
 * `autoHide.hidden`. Mientras está oculto bloquea los punteros con botón presionado para
 * que no se pulse a ciegas; mover el ratón lo devuelve en ≤100 ms.
 */
private fun Modifier.autoHideChrome(
    autoHide: NowPlayingAutoHide,
    sink: Dp,
): Modifier = this
    .graphicsLayer {
        val h = autoHide.hidden.value
        alpha = 1f - h
        translationY = h * sink.toPx()
    }
    .pointerInput(autoHide) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (autoHide.hidden.value > 0.5f) {
                    event.changes.forEach { change ->
                        if (change.pressed) change.consume()
                    }
                }
            }
        }
    }

/**
 * Re-escala la carátula solo en el compositor mientras el cromo se oculta (estilo Sonora,
 * fullscreen.rs:1082): el layout en Dp queda fijo y `graphicsLayer` crece el raster hasta
 * `restScale`, centrado. Cero reflow, y la capa solo se invalida al cambiar `hidden`.
 */
private fun Modifier.autoHideCoverGrow(
    autoHide: NowPlayingAutoHide,
    restScale: Float,
): Modifier = graphicsLayer {
    val scale = 1f + (restScale - 1f) * autoHide.hidden.value
    scaleX = scale
    scaleY = scale
}

/**
 * Empuja los metadatos lo que la carátula desborda al crecer (crece centrada: overflow/2
 * por lado) para que el hueco visual entre carátula y detalles no se cierre. En modo
 * compacto los detalles están a la derecha, así que el empuje es horizontal.
 */
private fun Modifier.autoHideCoverLift(
    autoHide: NowPlayingAutoHide,
    lift: Dp,
    horizontal: Boolean = false,
): Modifier = graphicsLayer {
    val offset = lift.toPx() * autoHide.hidden.value
    if (horizontal) {
        translationX = offset
    } else {
        translationY = offset
    }
}

/**
 * Animación de entrada para la carátula en Now Playing: escala y desvanecimiento suaves.
 */
@Composable
private fun rememberCoverEnter(): Pair<Animatable<Float, *>, Animatable<Float, *>> {
    val animationsEnabled = LocalAnimationsEnabled.current
    val scale = remember { Animatable(if (animationsEnabled) 0.82f else 1f) }
    val alpha = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            launch { scale.animateTo(1f, expressiveTween(340)) }
            launch { alpha.animateTo(1f, expressiveTween(280)) }
        }
    }
    return scale to alpha
}

@Composable
private fun Modifier.coverEnter(scaleAnim: Animatable<Float, *>, alphaAnim: Animatable<Float, *>): Modifier =
    this.graphicsLayer {
        scaleX = scaleAnim.value
        scaleY = scaleAnim.value
        alpha = alphaAnim.value
    }

/**
 * Diseño único, moderno y espacioso para la pantalla Now Playing de Melodist.
 */
@Composable
fun NowPlayingLayout(
    state: PlayerUiState,
    song: MediaMetadata,
    onCollapse: () -> Unit,
    onNavigate: ((Route) -> Unit)? = null,
    selectedTab: NowPlayingTab = NowPlayingTab.QUEUE,
    onTabSelected: (NowPlayingTab) -> Unit = {},
    lyrics: String? = null,
    mediaInfo: MediaInfo? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    val preferencesRepo = LocalUserPreferences.current
    val equalizerBands by preferencesRepo.equalizerBands.collectAsState(initial = List(5) { 0f })
    val bottomInset = LocalMiniPlayerInset.current
    val queueCount = state.queue.size

    // Auto-hide del cromo (barra, progreso, transporte, volumen, colapso)
    val autoHideEnabled by preferencesRepo.nowPlayingAutoHide.collectAsState(initial = true)
    val autoHide = remember { NowPlayingAutoHide() }
    NowPlayingAutoHideDriver(
        autoHide = autoHide,
        enabled = autoHideEnabled,
        extraBusy = { showMenu || showEqualizer },
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Cualquier actividad del puntero mantiene/repierte el cromo.
            .onPointerEvent(PointerEventType.Move) { autoHide.wake() }
            .onPointerEvent(PointerEventType.Press) {
                autoHide.wake()
                autoHide.pointerDown = true
            }
            .onPointerEvent(PointerEventType.Release) {
                autoHide.pointerDown = false
                autoHide.wake()
            }
            .onPointerEvent(PointerEventType.Exit) {
                // El release puede caer fuera de la ventana (popup encima):
                // no quedarse con el botón "abajo" y el cromo visible para siempre.
                autoHide.pointerDown = false
            }
            .onPointerEvent(PointerEventType.Scroll) { autoHide.wake() },
    ) {
        val isCompact = maxWidth < 640.dp || maxHeight < 400.dp
        val screenWidth = maxWidth

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset),
        ) {
            NowPlayingTopBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                queueCount = queueCount,
                showMenu = showMenu,
                onMenuToggle = { showMenu = it },
                onOpenEqualizer = { showEqualizer = true },
                compact = isCompact,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)
                    .autoHideChrome(autoHide, sink = (-14).dp)
                    .onHover { autoHide.topBarHover = it },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                SpaciousNowPlayingBody(
                    state = state,
                    song = song,
                    lyrics = lyrics,
                    mediaInfo = mediaInfo,
                    selectedTab = selectedTab,
                    onNavigate = onNavigate,
                    onCollapse = onCollapse,
                    compact = isCompact,
                    screenWidth = screenWidth,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    autoHide = autoHide,
                )

                // Colapsar (volver) — el mini player está oculto en Now Playing
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 8.dp)
                        .size(40.dp)
                        .autoHideChrome(autoHide, sink = 16.dp)
                        .onHover { autoHide.chromeHover = it }
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(Res.string.mp_collapse),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }

    if (showEqualizer) {
        EqualizerDialog(
            bands = equalizerBands,
            onBandsChange = { scope.launch { preferencesRepo.setEqualizerBands(it) } },
            onDismiss = { showEqualizer = false },
        )
    }
}

/**
 * Barra superior con selector de pestañas centrado y acciones rápidas.
 * El retroceso de navegación se gestiona globalmente desde la TitleBar.
 */
@Composable
private fun NowPlayingTopBar(
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    queueCount: Int,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            Spacer(Modifier.width(88.dp))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            NowPlayingIconTabs(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                queueCount = queueCount,
                showLabels = !compact,
            )
        }

        NowPlayingTopActions(
            showMenu = showMenu,
            onMenuToggle = onMenuToggle,
            onOpenEqualizer = onOpenEqualizer,
        )
    }
}

/**
 * Píldoras de navegación entre pestañas (Letras, Cola, Información).
 */
@Composable
private fun NowPlayingIconTabs(
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    queueCount: Int,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val lyricsLabel = stringResource(Res.string.tab_lyrics)
    val queueLabel = stringResource(Res.string.tab_queue)
    val infoLabel = stringResource(Res.string.tab_info)
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        shape = AppShapes.extraLarge,
        color = colorScheme.surfaceContainer.copy(alpha = 0.5f),
        tonalElevation = 0.dp,
        modifier = modifier.border(
            width = 0.5.dp,
            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
            shape = AppShapes.extraLarge,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NowPlayingTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val label = when (tab) {
                    NowPlayingTab.LYRICS -> lyricsLabel
                    NowPlayingTab.QUEUE -> if (queueCount > 0) "$queueLabel · $queueCount" else queueLabel
                    NowPlayingTab.INFO -> infoLabel
                }
                val contentColor = if (selected) {
                    colorScheme.onPrimaryContainer
                } else {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                }
                val containerColor = if (selected) {
                    colorScheme.primaryContainer.copy(alpha = 0.65f)
                } else {
                    Color.Transparent
                }

                Surface(
                    onClick = { onTabSelected(tab) },
                    shape = AppShapes.large,
                    color = containerColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .height(36.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (showLabels) 14.dp else 10.dp,
                            vertical = 6.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = tabIcon(tab),
                            contentDescription = if (showLabels) null else label,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor,
                        )
                        if (showLabels) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contenedor transparente para alojar el contenido de la pestaña activa derivando del AppBackground.
 */
@Composable
private fun TransparentPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        content()
    }
}

/**
 * Cuerpo principal espacioso: en escritorio divide la pantalla con proporción aireada,
 * y en pantallas muy estrechas (< 640dp) organiza la carátula y el contenido en una columna fluida.
 */
@Composable
private fun SpaciousNowPlayingBody(
    state: PlayerUiState,
    song: MediaMetadata,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    selectedTab: NowPlayingTab,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    compact: Boolean,
    screenWidth: Dp,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    autoHide: NowPlayingAutoHide,
) {
    val (coverScale, coverAlpha) = rememberCoverEnter()
    // En modo compacto el volumen va horizontal y solo cabe si hay ancho suficiente
    // (no se usa BoxWithConstraints anidado: colisiona con el maxWidth raíz del layout).
    val showVolume = screenWidth >= 420.dp

    if (compact) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(
                    url = song.thumbnailUrl,
                    title = song.title,
                    modifier = Modifier
                        .size(104.dp)
                        .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                        .coverEnter(coverScale, coverAlpha)
                        .autoHideCoverGrow(autoHide, restScale = 1.12f),
                )
                NowPlayingSongDetails(
                    state = state,
                    song = song,
                    textAlign = TextAlign.Start,
                    onNavigate = onNavigate,
                    onCollapse = onCollapse,
                    compact = true,
                    modifier = Modifier
                        .weight(1f)
                        .autoHideCoverLift(autoHide, lift = 6.24.dp, horizontal = true),
                )
            }

            // Controles (el mini player está oculto en Now Playing)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .autoHideChrome(autoHide, sink = 18.dp)
                    .onHover { autoHide.chromeHover = it },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PlayerProgressRow()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PlayerTransportRow()
                    if (showVolume) {
                        Spacer(Modifier.width(10.dp))
                        PlayerVolumeControl()
                    }
                }
            }

            TransparentPanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                ) {
                    NowPlayingTabContent(
                        tab = selectedTab,
                        song = song,
                        state = state,
                        lyrics = lyrics,
                        lyricsTextStyle = MaterialTheme.typography.bodyLarge,
                        onNavigate = onNavigate,
                        mediaInfo = mediaInfo,
                    )
                }
            }
        }
    } else {
        // Diseño de escritorio tipo Apple Music / Spotify:
        // izquierda = carátula + metadatos + progreso + transporte; derecha = panel de pestañas.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Row(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // La carátula cede espacio a los metadatos para no desbordar la columna
                    val coverDimension = minOf(
                        maxWidth * 0.8f,
                        maxHeight - 170.dp,
                        360.dp,
                    ).coerceAtLeast(96.dp)

                    // Carátula en reposo: con los controles ocultos el reserve baja de 170
                    // a ~96dp, así que puede crecer (Sonora fullscreen.rs:1082) sin reflow —
                    // solo se re-escala el raster y se compensa el desbordamiento en los detalles.
                    val coverRest = minOf(
                        maxWidth * 0.92f,
                        maxHeight - 96.dp,
                        440.dp,
                    ).coerceAtLeast(coverDimension)
                    val coverRestScale = (coverRest / coverDimension).coerceAtMost(1.25f)
                    val coverLift = coverDimension * (coverRestScale - 1f) / 2f

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CoverArt(
                            url = song.thumbnailUrl,
                            title = song.title,
                            modifier = Modifier
                                .size(coverDimension)
                                .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                                .coverEnter(coverScale, coverAlpha)
                                .autoHideCoverGrow(autoHide, coverRestScale),
                        )

                        NowPlayingSongDetails(
                            state = state,
                            song = song,
                            textAlign = TextAlign.Center,
                            onNavigate = onNavigate,
                            onCollapse = onCollapse,
                            compact = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 440.dp)
                                .padding(horizontal = 8.dp)
                                .autoHideCoverLift(autoHide, coverLift),
                        )
                    }
                }

                // Progreso + transporte (el mini player está oculto en Now Playing)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .padding(top = 8.dp)
                        .autoHideChrome(autoHide, sink = 18.dp)
                        .onHover { autoHide.chromeHover = it },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PlayerProgressRow()
                    PlayerTransportRow()
                }
            }

            // Volumen vertical estilo pista al borde inferior derecho del panel
            PlayerVolumeVertical(
                modifier = Modifier
                    .padding(end = 4.dp, bottom = 12.dp)
                    .autoHideChrome(autoHide, sink = 18.dp)
                    .onHover { autoHide.chromeHover = it },
                onBusyChange = { autoHide.volumePopup = it },
            )
        }

        // Columna derecha: panel transparente con el contenido de la pestaña activa
        TransparentPanel(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            ) {
                NowPlayingTabContent(
                    tab = selectedTab,
                    song = song,
                    state = state,
                    lyrics = lyrics,
                    lyricsTextStyle = MaterialTheme.typography.headlineMedium,
                    onNavigate = onNavigate,
                    mediaInfo = mediaInfo,
                )
            }
        }
        }
    }
}

/**
 * Detalles y metadatos de la canción con tipografía destacada y enlaces a artistas y álbum.
 */
@Composable
private fun NowPlayingSongDetails(
    state: PlayerUiState,
    song: MediaMetadata,
    textAlign: TextAlign,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val playerViewModel = LocalPlayerViewModel.current
    Column(
        horizontalAlignment = when (textAlign) {
            TextAlign.Start -> Alignment.Start
            else -> Alignment.CenterHorizontally
        },
        modifier = modifier,
    ) {
        // Chip de origen / procedencia
        state.queueSource?.let { source ->
            val label = when (source) {
                is QueueSource.Album -> stringResource(Res.string.from_album, source.title)
                is QueueSource.Playlist -> stringResource(Res.string.from_playlist, source.title)
                is QueueSource.Single -> stringResource(Res.string.song_radio)
                QueueSource.Custom -> stringResource(Res.string.custom_queue)
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = if (compact) 8.dp else 12.dp),
            ) {
                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .widthIn(max = if (compact) 200.dp else 320.dp)
                        .basicMarquee(),
                )
            }
        }

        // Título de la pista + like (estilo referencia: título centrado con corazón al final)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (textAlign == TextAlign.Start) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = song.title,
                style = if (compact) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .basicMarquee(),
            )
            LikeToggle(
                liked = song.liked,
                onToggle = { playerViewModel.toggleLike() },
            )
        }

        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))

        // Artistas interactivos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (textAlign == TextAlign.Start) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            song.artists.forEachIndexed { i, artist ->
                val hasId = artist.id != null
                Text(
                    text = artist.name,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (hasId) {
                        Modifier
                            .clip(AppShapes.small)
                            .clickable {
                                onCollapse?.invoke()
                                onNavigate?.invoke(Route.Artist(artist.id!!))
                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    } else {
                        Modifier.padding(horizontal = 4.dp)
                    },
                )
                if (i < song.artists.size - 1) {
                    Text(
                        text = " · ",
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }

        // Álbum si está presente
        song.album?.let { album ->
            if (!compact) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier
                        .clip(AppShapes.small)
                        .clickable {
                            onCollapse?.invoke()
                            onNavigate?.invoke(Route.Album(album.id))
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun tabIcon(tab: NowPlayingTab) = when (tab) {
    NowPlayingTab.LYRICS -> Icons.Rounded.Lyrics
    NowPlayingTab.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
    NowPlayingTab.INFO -> Icons.Rounded.Info
}

/**
 * Contenido animado de cada pestaña dentro de Now Playing.
 */
@Composable
private fun NowPlayingTabContent(
    tab: NowPlayingTab,
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    lyricsTextStyle: androidx.compose.ui.text.TextStyle,
    onNavigate: ((Route) -> Unit)?,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            if (animationsEnabled) {
                (fadeIn(expressiveFadeTween()) +
                    slideInVertically(animationSpec = expressiveLayoutTween()) { it / 24 })
                    .togetherWith(fadeOut(expressiveTween(140)))
            } else {
                fadeIn(expressiveTween(0)) togetherWith fadeOut(expressiveTween(0))
            }
        },
        label = "now_playing_tab_content",
        modifier = Modifier.fillMaxSize(),
    ) { targetTab ->
        when (targetTab) {
            NowPlayingTab.LYRICS -> LyricsContent(
                lyrics = lyrics,
                textAlign = TextAlign.Start,
                style = lyricsTextStyle,
            )
            NowPlayingTab.QUEUE -> NowPlayingQueuePanel(
                state = state,
                modifier = Modifier.fillMaxSize(),
                bottomInset = 0.dp,
            )
            NowPlayingTab.INFO -> SongInfoContent(
                song = song,
                state = state,
                mediaInfo = mediaInfo,
                onNavigate = onNavigate,
            )
        }
    }
}