package example.nucleus.ui.helpers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.utils.LocalAnimationsEnabled

/**
 * Desktop hover / focus / press state for M3 state layers.
 *
 * Prefer sharing one [interactionSource] with the clickable / button that owns the control.
 */
@Composable
fun rememberDesktopInteractionState(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
): DesktopInteractionState {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    return DesktopInteractionState(
        interactionSource = interactionSource,
        hovered = hovered,
        focused = focused,
        pressed = pressed,
    )
}

data class DesktopInteractionState(
    val interactionSource: MutableInteractionSource,
    val hovered: Boolean,
    val focused: Boolean,
    val pressed: Boolean,
) {
    val active: Boolean get() = hovered || focused || pressed
}

/**
 * M3-ish state-layer alpha on desktop (hover 0.08, focus 0.10, press 0.12; combined max ~0.16).
 */
fun desktopStateLayerAlpha(
    hovered: Boolean,
    focused: Boolean = false,
    pressed: Boolean = false,
    enabled: Boolean = true,
): Float {
    if (!enabled) return 0f
    var a = 0f
    if (hovered) a += 0.08f
    if (focused) a += 0.10f
    if (pressed) a += 0.12f
    return a.coerceIn(0f, 0.16f)
}

/**
 * Soft scale feedback for interactive chrome (no bounce).
 */
@Composable
fun animateDesktopPressScale(
    pressed: Boolean,
    hovered: Boolean = false,
    enabled: Boolean = true,
): State<Float> {
    val animationsEnabled = LocalAnimationsEnabled.current
    val target = when {
        !enabled -> 1f
        pressed -> 0.97f
        hovered -> 1.015f
        else -> 1f
    }
    return animateFloatAsState(
        targetValue = target,
        animationSpec = if (animationsEnabled) expressiveFadeTween() else androidx.compose.animation.core.snap(),
        label = "desktopPressScale",
    )
}

/**
 * Clip + M3 state layer + hand cursor for clickable surfaces (list rows, cards, chips).
 *
 * Pass the same [interactionSource] into `clickable` / `Card(onClick=…)` when possible.
 */
fun Modifier.desktopInteractiveSurface(
    shape: Shape,
    enabled: Boolean = true,
    showHandCursor: Boolean = true,
    /**
     * Feedback de escala (hover 1.015, press 0.97). Con `false` se conservan el state layer y el
     * cursor de mano, pero la superficie no "se infla" al pasar el ratón.
     */
    scaleOnHover: Boolean = true,
    stateLayerColor: Color? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val animationsEnabled = LocalAnimationsEnabled.current
    val targetAlpha = desktopStateLayerAlpha(hovered, focused, pressed, enabled)
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = if (animationsEnabled) expressiveFadeTween() else androidx.compose.animation.core.snap(),
        label = "desktopStateLayer",
    )
    val scale by animateDesktopPressScale(pressed = pressed, hovered = hovered, enabled = enabled && scaleOnHover)
    val layerColor = stateLayerColor ?: MaterialTheme.colorScheme.onSurface

    this
        .then(
            if (scaleOnHover) {
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            } else {
                Modifier
            },
        )
        .clip(shape)
        .hoverable(interactionSource = source, enabled = enabled)
        .background(layerColor.copy(alpha = alpha))
        .then(
            if (showHandCursor && enabled) Modifier.pointerHoverIcon(PointerIcon.Hand)
            else Modifier,
        )
}

/**
 * Hand cursor + optional hoverable tracking without drawing a layer (for IconButtons, etc.).
 */
fun Modifier.desktopClickableCursor(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val source = interactionSource
    this
        .then(
            if (source != null) Modifier.hoverable(interactionSource = source, enabled = enabled)
            else Modifier,
        )
        .then(
            if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier,
        )
}
