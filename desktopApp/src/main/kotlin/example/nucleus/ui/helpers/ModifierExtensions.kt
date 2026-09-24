package example.nucleus.ui.helpers

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

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

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onHover(
    onHoverChange: ((Boolean) -> Unit)? = null
): Modifier = composed {
    this
        .onPointerEvent(PointerEventType.Enter) { onHoverChange?.invoke(true) }
        .onPointerEvent(PointerEventType.Exit) { onHoverChange?.invoke(false) }
}
