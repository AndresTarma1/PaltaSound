package example.nucleus.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import example.nucleus.ui.themes.rememberInteractionSpring
import example.nucleus.utils.LocalAnimationsEnabled
import kotlin.math.roundToInt

@Composable
fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    trackHeight: Dp = 3.dp,
    thumbSize: Dp = 8.dp,
    draggedThumbSize: Dp = 12.dp,
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    val thumbDiameter by animateDpAsState(
        targetValue = if (isDragging) draggedThumbSize else thumbSize,
        animationSpec = rememberInteractionSpring(),
        label = "slimThumb",
    )
    val fraction = value.coerceIn(0f, 1f)

    // Mantener siempre la referencia más reciente de los callbacks
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val interaction = if (enabled) {
        Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.takeIf { it > 0 } ?: 1
                    currentOnValueChange((offset.x / width).coerceIn(0f, 1f))
                    currentOnValueChangeFinished?.invoke()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false; currentOnValueChangeFinished?.invoke() },
                    onDragCancel = { isDragging = false },
                ) { change, _ ->
                    change.consume()
                    val width = size.width.takeIf { it > 0 } ?: 1
                    currentOnValueChange((change.position.x / width).coerceIn(0f, 1f))
                }
            }
    } else {
        Modifier
    }

    BoxWithConstraints(modifier = modifier.height(20.dp).then(interaction)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val thumbRadiusPx = with(density) { (thumbDiameter / 2).toPx() }

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(inactiveColor)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(activeColor)
        )
        if (thumbDiameter > 0.dp) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((widthPx * fraction - thumbRadiusPx).roundToInt(), 0) }
                    .size(thumbDiameter)
                    .clip(CircleShape)
                    .background(activeColor)
            )
        }
    }
}
