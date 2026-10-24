package example.nucleus.ui.helpers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventType.Companion
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.contextMenuArea(
    enabled: Boolean = true,
    onHoverChange: ((Boolean) -> Unit)? = null,
    onMenuAction: () -> Unit
): Modifier = composed {
    this
        .onPointerEvent(PointerEventType.Enter) { onHoverChange?.invoke(true) }
        .onPointerEvent(PointerEventType.Exit) { onHoverChange?.invoke(false) }
        .onPointerEvent(PointerEventType.Press) {
            if (enabled && it.button == PointerButton.Secondary) {
                onMenuAction()
            }
        }
}


fun Modifier.onHover(onHover: (Boolean) -> Unit): Modifier =
    pointerInput(onHover) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Enter -> onHover(true)
                    PointerEventType.Exit -> onHover(false)
                }
            }
        }
    }

/**
 * Ajusta un valor entero con la rueda del ratón, en pasos de [step] y recortado a [range].
 *
 * Sustituye a las pistas arrastrables del control de volumen: con el tooltip siendo solo una
 * etiqueta del porcentaje, la rueda es lo que deja mover el volumen sin ocupar hueco en el layout.
 *
 * `scrollDelta` no viene en píxeles sino en "muescas" de plataforma (más o menos ±1 por clic, y
 * valores fraccionarios con trackpad), así que se acumula en [accumulator] y se agrupa por muescas
 * enteras. Sin eso, un trackpad llegaría con deltas de 0.1 y nunca cruzaría el umbral.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.pointerScrollToInt(
    value: Int,
    range: IntRange,
    step: Int = 5,
    onValueChange: (Int) -> Unit,
): Modifier = composed {
    var accumulator by remember { mutableFloatStateOf(0f) }

    this.onPointerEvent(PointerEventType.Scroll) { event ->
        val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        if (delta == 0f) return@onPointerEvent

        accumulator += delta
        if (abs(accumulator) < 1f) return@onPointerEvent

        val notches = accumulator.toInt()
        accumulator -= notches

        onValueChange((value + notches * step).coerceIn(range))
    }
}
