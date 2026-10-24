@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.MediaInfo
import example.nucleus.generated.resources.*
import example.nucleus.models.MediaMetadata
import example.nucleus.navigation.Route
import example.nucleus.ui.components.EqualizerDialog
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.LocalTitleBarOverlayInset
import example.nucleus.utils.LocalUserPreferences
import example.nucleus.viewmodels.PlayerUiState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

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
    // La barra de titulo va superpuesta en Now Playing (App.kt), asi que el contenido
    // arranca 36dp mas abajo. El padding va en la columna de contenido, NO en el
    // BoxWithConstraints: el fondo ambiental vive ahi dentro y tiene que seguir
    // pintando hasta el borde superior.
    val topInset = LocalTitleBarOverlayInset.current
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
                .padding(top = topInset, bottom = bottomInset),
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
 * Contenedor transparente para alojar el contenido de la pestaña activa derivando del AppBackground.
 */
@Composable
internal fun TransparentPanel(
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

    if (compact) {
        NowPlayingCompactBody(
            state = state,
            song = song,
            lyrics = lyrics,
            mediaInfo = mediaInfo,
            selectedTab = selectedTab,
            onNavigate = onNavigate,
            onCollapse = onCollapse,
            coverScale = coverScale,
            coverAlpha = coverAlpha,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            autoHide = autoHide,
        )
    } else {
        NowPlayingWideBody(
            state = state,
            song = song,
            lyrics = lyrics,
            mediaInfo = mediaInfo,
            selectedTab = selectedTab,
            onNavigate = onNavigate,
            onCollapse = onCollapse,
            coverScale = coverScale,
            coverAlpha = coverAlpha,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            autoHide = autoHide,
        )
    }
}
