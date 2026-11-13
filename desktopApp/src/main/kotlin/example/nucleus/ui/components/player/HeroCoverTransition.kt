package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import example.nucleus.ui.themes.expressiveEasing
import example.nucleus.utils.LocalAnimationsEnabled

/**
 * Duración del morph de la carátula. Más larga que el layout normal a
 * propósito: el salto miniPlayer ⇄ Now Playing es el gesto más visible de la
 * app y con 320 ms no se apreciaba.
 */
private const val HeroCoverMorphDuration = 560

/**
 * Hero cover element para la transición de la carátula entre el MiniPlayer y NowPlaying.
 *
 * `sharedBounds` + `ScaleToBounds`: el overlay interpola los bounds y ESCALA el
 * contenido (en vez de re-medirlo). `sharedElement` puro no engancha al volver
 * (la salida se quedaba sin morph); `sharedBounds` sí anima en ambas
 * direcciones, y con ScaleToBounds la portada se encoge hacia el thumbnail en
 * lugar de saltar por un re-layout.
 *
 * Solo se aplica con animaciones activadas; con reduce-motion la carátula
 * aparece sin morph.
 *
 * Nota Tao: los bounds del elemento que sale deben quedarse quietos durante la
 * transición — por eso el cover del MiniPlayer entra/sale solo con fade y
 * NowPlaying congela sus insets mientras está compuesto.
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
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            boundsTransform = { _, _ ->
                tween<Rect>(durationMillis = HeroCoverMorphDuration, easing = expressiveEasing)
            },
        )
    }
}
