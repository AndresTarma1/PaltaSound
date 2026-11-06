package example.nucleus.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.LocalNucleusWindow
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.WindowBackground
import example.nucleus.player.YouTubePlayerService
import example.nucleus.ui.screens.youtube.PlayerCard
import example.nucleus.viewmodels.YouTubeWatchViewModel
import example.nucleus.windows.HiddenCursor
import kotlinx.coroutines.delay

/**
 * Ventana dedicada al vídeo fullscreen.
 *
 * La ventana principal NO entra en fullscreen ni cambia de tamaño/layout: esta
 * segunda ventana borderless se crea oculta al arrancar y se muestra/oculta con
 * el estado `fullscreen`. Así no hay una transición de swapchain sobre la
 * ventana principal que pueda dejar el frame viejo (el "fantasma") ni un
 * relayout de la app al salir.
 *
 * La textura D3D11 del vídeo es compartida: el `TextureView` de esta ventana
 * importa el mismo handle que el de la ventana principal.
 */
@Composable
fun YouTubeFullscreenWindow(
    scope: NucleusApplicationScope,
    playerService: YouTubePlayerService,
    viewModel: YouTubeWatchViewModel,
    visible: Boolean,
) {
    val mainWindow = LocalNucleusWindow.current
    val windowState =
        rememberWindowState(
            size = DpSize(1280.dp, 720.dp),
            position = WindowPosition(0.dp, 0.dp),
        )
    var shown by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            // Colocarla en el monitor de la ventana principal antes de pedir
            // el fullscreen nativo. Todo ocurre con la ventana oculta, así que
            // no se ve ningún cambio de geometría.
            windowState.placement = WindowPlacement.Floating
            mainWindow.boundsOnScreen()?.let { bounds ->
                windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
            }
            // Dejar que el efecto de posición (solo se aplica en Floating)
            // mueva la ventana al monitor correcto.
            delay(32)
            windowState.placement = WindowPlacement.Fullscreen
            // Y que Tao aplique el placement mientras sigue oculta.
            delay(80)
            focused = true
            shown = true
        } else {
            shown = false
            focused = false
            mainWindow.requestFocus()
        }
    }

    scope.DecoratedWindow(
        onCloseRequest = { playerService.setFullscreen(false) },
        state = windowState,
        visible = shown,
        title = "",
        undecorated = true,
        resizable = false,
        minimizable = false,
        maximizable = false,
        alwaysOnTop = shown && focused,
        hiddenFromDock = true,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown &&
                (event.key == Key.Escape || event.key == Key.Back)
            ) {
                playerService.setFullscreen(false)
                true
            } else {
                false
            }
        },
    ) {
        WindowBackground(Color.Black)
        val fullscreenWindow = LocalNucleusWindow.current
        LaunchedEffect(fullscreenWindow) {
            fullscreenWindow.focusFlow.collect { fsFocused -> focused = fsFocused }
        }
        LaunchedEffect(shown) {
            if (shown) fullscreenWindow.requestFocus()
        }
        FullscreenVideoContent(playerService, viewModel)
    }
}

@Composable
private fun FullscreenVideoContent(
    playerService: YouTubePlayerService,
    viewModel: YouTubeWatchViewModel,
) {
    val state by viewModel.state.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.isPlaying, interactionTick) {
        if (state.isPlaying) {
            controlsVisible = true
            delay(3_000)
            controlsVisible = false
        } else {
            controlsVisible = true
        }
    }

    // En el fallback HWND el vídeo se come los eventos de ratón; leer la
    // posición real del cursor mantiene el auto-hide funcionando en ambas rutas.
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) return@LaunchedEffect
        var last = HiddenCursor.cursorPosition()
        while (true) {
            delay(150)
            val current = HiddenCursor.cursorPosition()
            if (last != null && current != null &&
                (current.x != last.x || current.y != last.y)
            ) {
                interactionTick++
            }
            last = current
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(playerService) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when {
                            event.type == PointerEventType.Press &&
                                event.buttons == PointerButtons(8) ->
                                playerService.setFullscreen(false)
                            event.type == PointerEventType.Move -> interactionTick++
                        }
                    }
                }
            },
    ) {
        PlayerCard(
            state = state,
            playerService = playerService,
            onTogglePlayPause = viewModel::togglePlayPause,
            onSeek = viewModel::seekTo,
            onVolume = viewModel::setVolume,
            onMinimize = { playerService.setFullscreen(false) },
            onToggleFullscreen = { playerService.setFullscreen(false) },
            fullscreen = true,
            controlsVisible = controlsVisible,
        )
    }
}
