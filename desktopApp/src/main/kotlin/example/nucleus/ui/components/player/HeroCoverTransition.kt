package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import example.nucleus.ui.themes.expressiveEasing
import example.nucleus.ui.themes.expressiveLayoutDuration
import example.nucleus.utils.LocalAnimationsEnabled

/**
 * Hero cover element para la transición de la carátula entre el MiniPlayer y NowPlaying.
 *
 * Usa `sharedBounds`: el overlay interpola posición/tamaño y el contenido se
 * re-mide en destino (sin distorsión de aspecto, aunque la portada del mini
 * player sea 16:9). Solo se aplica con animaciones activadas; con reduce-motion
 * la carátula aparece sin morph.
 *
 * Nota Tao: los bounds del elemento que sale deben quedarse quietos durante la
 * transición — por eso el cover del MiniPlayer entra/sale solo con fade (sin
 * expand/shrink, que pelearían con el overlay y producían el tembleque).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.heroCoverElement(
    songId: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this
    if (!LocalAnimationsEnabled.current) return this
    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key = "now_playing_cover_$songId"),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            boundsTransform = { _, _ ->
                tween<Rect>(durationMillis = expressiveLayoutDuration, easing = expressiveEasing)
            },
        )
    }
}
