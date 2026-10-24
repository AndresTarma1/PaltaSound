@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import example.nucleus.generated.resources.*
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.windows.HiddenCursor
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
internal fun NowPlayingAutoHideDriver(
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
            // tick porque cualquier WM_SETCURSOR de la ventana repone la flecha. Se espera
            // a que la animación termine (`hidden.value >= 1f`): ocultarlo con el resorte a
            // medio camino dejaba el puntero invisible sobre controles todavía visibles.
            if (next >= 1f && autoHide.hidden.value >= 1f) {
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
internal fun Modifier.autoHideChrome(
    autoHide: NowPlayingAutoHide,
    sink: Dp,
): Modifier = this
    .graphicsLayer {
        val h = autoHide.hidden.value
        alpha = 1f - h
        translationY = h * sink.toPx()
    }

/**
 * Re-escala la carátula solo en el compositor mientras el cromo se oculta (estilo Sonora,
 * fullscreen.rs:1082): el layout en Dp queda fijo y `graphicsLayer` crece el raster hasta
 * `restScale`, centrado. Cero reflow, y la capa solo se invalida al cambiar `hidden`.
 */
internal fun Modifier.autoHideCoverGrow(
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
internal fun Modifier.autoHideCoverLift(
    autoHide: NowPlayingAutoHide,
    lift: Dp,
): Modifier = graphicsLayer {
    translationY = lift.toPx() * autoHide.hidden.value
}
