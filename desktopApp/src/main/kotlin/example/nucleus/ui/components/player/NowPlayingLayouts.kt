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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import example.nucleus.windows.HiddenCursor
import example.nucleus.viewmodels.PlayerUiState
import example.nucleus.viewmodels.QueueSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

/**
 * Vistas del reproductor a pantalla completa, al estilo de Sonora: la primera no es una
 * pestaña sino la ausencia de panel — la portada a pantalla completa. Las otras dos
 * abren el panel lateral (letra o cola) cuando hay ancho, y ocupan toda la pantalla
 * cuando no.
 */
enum class NowPlayingTab {
    COVER,
    LYRICS,
    QUEUE;

    /** Si este estado muestra un panel junto a la portada. */
    val hasPanel: Boolean get() = this != COVER
}

/** Espera (ms) sin actividad del puntero antes de que el cromo de Now Playing se oculte. */
private const val AUTO_HIDE_REST_MS = 1_500L

/**
 * Desplazamiento (px) a partir del cual se considera que el puntero se ha movido de
 * verdad. Filtra los eventos sinteticos que Windows emite al cambiar el cursor de la
 * ventana, que llegan siempre con la posicion intacta.
 */
private const val MIN_POINTER_MOVE_PX = 2f

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

    /**
     * Ultima posicion conocida del puntero, en pixeles de la ventana.
     *
     * Windows reenvia eventos de movimiento al fijar el cursor de una ventana
     * (`WM_SETCURSOR`), y al ocultar el cromo se cambia el cursor en cada tick. Esos
     * eventos llegan con la posicion intacta, asi que sin esta comprobacion la rafaga
     * artificial reiniciaba el temporizador de inactividad y la pantalla nunca terminaba
     * de dormirse. Comparar la posicion separa un movimiento real de uno sintetico.
     */
    private var lastPointer = Offset.Unspecified

    fun wake() {
        lastActiveNanos = System.nanoTime()
        interacted = true
    }

    /**
     * El cursor se va con el cromo y vuelve con el primer movimiento real. Se recupera
     * aqui y no al carretar la animación, porque cualquier movimiento lo devuelve.
     */
    fun wakeWithCursor() {
        wake()
        HiddenCursor.show()
    }

    /** Solo despierta si el puntero se ha desplazado de verdad. */
    fun onPointerMoved(position: Offset) {
        val previous = lastPointer
        lastPointer = position
        // Sin posicion valida no se puede comparar, asi que se despierta: es preferible
        // un cromo que reaparece de mas a un cursor invisible que no se recupera.
        val moved = !position.isValid() || previous == Offset.Unspecified ||
            abs(position.x - previous.x) > MIN_POINTER_MOVE_PX ||
            abs(position.y - previous.y) > MIN_POINTER_MOVE_PX
        if (moved) wakeWithCursor()
    }

    /**
     * Avisa de una interacción deliberada (clic, rueda, tecla) que sí debe despertar,
     * aunque el puntero no se haya desplazado.
     */
    fun wakeFromIntent() = wakeWithCursor()
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

    // Si Now Playing se cierra con el cromo hundido, el cursor se quedaria invisible en
    // el resto de la app. Se devuelve siempre al salir de la pantalla.
    DisposableEffect(Unit) {
        onDispose { HiddenCursor.show() }
    }

    LaunchedEffect(enabled, animationsEnabled) {
        var target = 0f
        // Ultima posicion real del raton en pantalla. Se guarda aqui, y no en la clase,
        // porque el cursor se recupera desde el propio motor.
        var lastScreenPosition: HiddenCursor.Point? = null

        while (true) {
            // Al ocultar el cromo se cambia el cursor y Windows reenvia eventos de
            // movimiento con la posicion intacta. Comprobar la posicion real del raton
            // distingue esa rafaga sintetica de un movimiento de verdad, y de paso es la
            // red de seguridad que devuelve el cursor aunque el evento se perdiera.
            val realPosition = HiddenCursor.cursorPosition()
            if (realPosition != null) {
                val previousScreen = lastScreenPosition
                if (previousScreen != null &&
                    abs(realPosition.x - previousScreen.x) > MIN_POINTER_MOVE_PX
                ) {
                    // El raton se ha movido de verdad: vuelve el cromo y el cursor.
                    autoHide.wakeWithCursor()
                }
                lastScreenPosition = realPosition
            }

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
            // El cursor se oculta con el cromo y solo se recupera con el primer movimiento
            // del puntero (Sonora: cx.hide_cursor() al dormir el cromo). Se refresca cada
            // tick porque cualquier WM_SETCURSOR de la ventana repone la flecha.
            if (next >= 1f) {
                HiddenCursor.hide()
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
 * por lado) para que el hueco visual entre carátula y detalles no se cierre.
 */
private fun Modifier.autoHideCoverLift(
    autoHide: NowPlayingAutoHide,
    lift: Dp,
): Modifier = graphicsLayer {
    translationY = lift.toPx() * autoHide.hidden.value
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
// El movimiento solo despierta si el puntero se desplaza de verdad: al ocultar
            // el cromo se cambia el cursor y Windows reenvia eventos de posicion sin
            // que el raton se mueva, lo que reiniciaba el temporizador en bucle.
            .onPointerEvent(PointerEventType.Move) { event ->
                event.changes.firstOrNull()?.let { autoHide.onPointerMoved(it.position) }
            }
            .onPointerEvent(PointerEventType.Press) {
                autoHide.wakeFromIntent()
                autoHide.pointerDown = true
            }
            .onPointerEvent(PointerEventType.Release) {
                autoHide.pointerDown = false
                autoHide.wakeFromIntent()
            }
            .onPointerEvent(PointerEventType.Exit) {
                // El release puede caer fuera de la ventana (popup encima):
                // no quedarse con el botón "abajo" y el cromo visible para siempre.
                autoHide.pointerDown = false
            }
            .onPointerEvent(PointerEventType.Scroll) { autoHide.wakeFromIntent() },
    ) {
        // Ancho minimo para poner portada y panel en paralelo. 740dp es el breakpoint `Wide`
        // de Sonora: por debajo, el panel ocupa toda la pantalla y la portada sale del layout.
        val isCompact = maxWidth < 740.dp || maxHeight < 400.dp

        // Fondo ambiental animado detras de todo: la base del tema con resplandores de la
        // paleta a la deriva. No intercepta puntero y el panel es transparente, asi que el
        // resplandor se ve tambien tras la letra y la cola.
        NowPlayingAmbientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset),
        ) {
            // En compacto las pestañas no ocupan la barra: flotan sobre el contenido, de
            // modo que aquí solo queda el menú y el ecualizador.
            if (isCompact) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(end = 8.dp)
                        .autoHideChrome(autoHide, sink = (-14).dp)
                        .onHover { autoHide.topBarHover = it },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    NowPlayingTopActions(
                        showMenu = showMenu,
                        onMenuToggle = { showMenu = it },
                        onOpenEqualizer = { showEqualizer = true },
                    )
                }
            } else {
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
            }

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
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    autoHide = autoHide,
                )

                // Píldoras de pestañas flotando sobre el contenido.
                if (isCompact) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .autoHideChrome(autoHide, sink = 18.dp)
                            .onHover { autoHide.chromeHover = it },
                    ) {
                        NowPlayingIconTabs(
                            selectedTab = selectedTab,
                            onTabSelected = onTabSelected,
                            queueCount = queueCount,
                            showLabels = false,
                        )
                    }
                }

                // Colapsar (volver) — solo en el layout ancho; en compacto va al final de la
                // fila de transporte. El mini player está oculto en Now Playing.
                if (!isCompact) {
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
    val coverLabel = stringResource(Res.string.tab_cover)
    val lyricsLabel = stringResource(Res.string.tab_lyrics)
    val queueLabel = stringResource(Res.string.tab_queue)
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
                    NowPlayingTab.COVER -> coverLabel
                    NowPlayingTab.LYRICS -> lyricsLabel
                    NowPlayingTab.QUEUE -> if (queueCount > 0) "$queueLabel · $queueCount" else queueLabel
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
 * Cuerpo principal del reproductor a pantalla completa, con la misma estructura que
 * Sonora (`fullscreen.rs:1015-1223`):
 *
 * - **Portada** ([NowPlayingTab.COVER]): la carátula manda, sin panel. En escritorio se
 *   centra con los metadatos debajo; en vertical, arriba y con el chrome abajo.
 * - **Con panel** (letra o cola): en ancho suficiente la portada se queda a la izquierda y
 *   el panel ocupa la derecha; si no cabe, el panel ocupa toda la pantalla y la carátula
 *   desaparece del layout sustituida por la fila compacta de arriba (Sonora llama a esto
 *   `staged`/`split`).
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
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    autoHide: NowPlayingAutoHide,
) {
    val (coverScale, coverAlpha) = rememberCoverEnter()
    val hasPanel = selectedTab.hasPanel
    // Split: panel y portada en paralelo. Solo con ancho, igual que Sonora exige Wide.
    val split = hasPanel && !compact

    if (compact) {
        // Layout vertical tipo móvil: el contenido scrolleable (letra, cola) manda y ocupa
        // todo el espacio; el chrome se ancla abajo.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (hasPanel) {
                    NowPlayingTabContent(
                        tab = selectedTab,
                        song = song,
                        state = state,
                        lyrics = lyrics,
                        mediaInfo = mediaInfo,
                        lyricsTextStyle = MaterialTheme.typography.bodyLarge,
                        onNavigate = onNavigate,
                    )
                } else {
                    // Vista de portada en vertical: carátula grande arriba, metadatos debajo.
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val coverSize = minOf(maxWidth * 0.86f, maxHeight - 132.dp, 320.dp)
                            .coerceAtLeast(96.dp)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CoverArt(
                                url = song.thumbnailUrl,
                                title = song.title,
                                modifier = Modifier
                                    .size(coverSize)
                                    .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                                    .coverEnter(coverScale, coverAlpha)
                                    .autoHideCoverGrow(autoHide, restScale = 1.1f),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            NowPlayingSongDetails(
                                state = state,
                                song = song,
                                textAlign = TextAlign.Center,
                                onNavigate = onNavigate,
                                onCollapse = onCollapse,
                                compact = true,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chrome anclado abajo: pista, progreso y transporte. La carátula aquí solo
            // aparece cuando hay panel — con la vista de portada ya está arriba.
            // Acotado y centrado: en ventanas alargadas la barra no debe ir de borde a borde.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .align(Alignment.CenterHorizontally)
                    .autoHideChrome(autoHide, sink = 18.dp)
                    .onHover { autoHide.chromeHover = it },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (hasPanel) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverArt(
                            url = song.thumbnailUrl,
                            title = song.title,
                            modifier = Modifier
                                .size(48.dp)
                                .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                                .coverEnter(coverScale, coverAlpha),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        NowPlayingSongDetails(
                            state = state,
                            song = song,
                            textAlign = TextAlign.Start,
                            onNavigate = onNavigate,
                            onCollapse = onCollapse,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                PlayerProgressRow()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PlayerTransportRow()
                    // Se usa el botón que despliega el slider en un popup, que en el
                    // layout ancho ya está en uso.
                    Spacer(Modifier.width(4.dp))
                    PlayerVolumeVertical(
                        trackHeight = 88.dp,
                        onBusyChange = { autoHide.volumePopup = it },
                    )
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .size(40.dp)
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
    } else {
        // Diseño de escritorio tipo Apple Music / Spotify:
        // izquierda = carátula + metadatos + progreso + transporte; derecha = panel de
        // letra/cola. Sin panel (vista de portada) la izquierda ocupa todo el ancho.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Row(
            modifier = Modifier
                .weight(if (split) 0.45f else 1f)
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

                // Progreso + transporte (el mini player está oculto en Now Playing).
                // Acotados al ancho de la columna de metadatos: la barra no debe estirarse
                // mas que la portada que acompaña.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
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

            // Volumen vertical estilo pista al borde inferior derecho del panel.
            // Sin panel la columna izquierda ocupa todo el ancho, asi que el volumen cae en
            // la misma esquina que el boton de colapsar y lo pisa. Se sube lo justo para
            // quedar encima: el colapsar ocupa de 8 a 48dp del borde, el volumen empieza
            // en 56dp y quedan 8dp de aire entre ambos.
            PlayerVolumeVertical(
                modifier = Modifier
                    .padding(end = 12.dp, bottom = if (split) 12.dp else 40.dp)
                    .autoHideChrome(autoHide, sink = 18.dp)
                    .onHover { autoHide.chromeHover = it },
                onBusyChange = { autoHide.volumePopup = it },
            )
        }

        // Columna derecha: solo con panel abierto. Sin panel la columna izquierda ya
        // ocupa el ancho completo, asi que la portada queda centrada en la ventana.
        if (split) {
            TransparentPanel(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, top = 8.dp, bottom = 12.dp),
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
    NowPlayingTab.COVER -> Icons.Rounded.Album
    NowPlayingTab.LYRICS -> Icons.Rounded.Lyrics
    NowPlayingTab.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
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
            // La vista de portada no tiene panel: la compone el layout, no este contenido.
            NowPlayingTab.COVER -> Unit
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
        }
    }
}