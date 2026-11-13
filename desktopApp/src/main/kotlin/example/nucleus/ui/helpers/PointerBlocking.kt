package example.nucleus.ui.helpers

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Bloquea los eventos de puntero que NO hayan consumido los hijos.
 *
 * Para superficies superpuestas al contenido (MiniPlayer flotante/docked): sin
 * esto, los huecos entre controles dejan pasar clics y scroll a la lista que
 * hay debajo. Se ejecuta en [PointerEventPass.Main], que va de hijos a padres,
 * así que los botones propios siguen funcionando; solo se consumen los eventos
 * sobrantes, impidiendo que lleguen a los hermanos dibujados por detrás.
 */
fun Modifier.blockPointerEventsBehind(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            event.changes.forEach { change ->
                if (!change.isConsumed) change.consume()
            }
        }
    }
}
