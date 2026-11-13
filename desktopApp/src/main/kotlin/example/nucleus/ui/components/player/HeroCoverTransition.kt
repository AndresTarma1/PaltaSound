package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import example.nucleus.ui.themes.expressiveEasing
import example.nucleus.utils.LocalAnimationsEnabled

/**
 * Duración del morph de la carátula y del fade del MiniPlayer/stack. Deben
 * coincidir: si el MiniPlayer desaparece antes, la fuente del shared element
 * muere a mitad de vuelo y el morph se corta.
 */
internal const val HeroCoverMorphMs = 420

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
    if (sharedTransitionScope == null || animatedVisibilityScope == null) {
        println("[HeroCover] NO-SCOPE key=now_playing_cover_$songId")
        return this
    }
    if (!LocalAnimationsEnabled.current) {
        println("[HeroCover] NO-ANIM key=now_playing_cover_$songId")
        return this
    }
    return with(sharedTransitionScope) {
        val contentState = rememberSharedContentState(key = "now_playing_cover_$songId")
        // Diagnóstico temporal: sin match no hay animación compartida.
        androidx.compose.runtime.SideEffect {
            println("[HeroCover] key=now_playing_cover_$songId match=${contentState.isMatchFound}")
        }
        sharedBounds(
            sharedContentState = contentState,
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            boundsTransform = { _, _ ->
                tween<Rect>(durationMillis = HeroCoverMorphMs, easing = expressiveEasing)
            },
            // Sin el clip del padre (la Surface redondeada del contenido): si
            // hereda ese clip, el overlay se corta al cruzar del MiniPlayer al
            // contenido y el vuelo "desaparece" a mitad de camino.
            clipInOverlayDuringTransition = OverlayClip(RectangleShape),
        )
    }
}
