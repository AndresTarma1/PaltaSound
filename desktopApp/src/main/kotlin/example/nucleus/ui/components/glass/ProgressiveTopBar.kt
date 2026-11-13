package example.nucleus.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Altura de las barras fijas que flotan sobre el contenido (tabs de Ajustes,
 * Biblioteca, filtros de Búsqueda...). Los listados la consumen como padding
 * superior para que su contenido pase por debajo y el difuminado tenga algo
 * que difuminar.
 */
val LocalProgressiveTopInset = compositionLocalOf { 0.dp }

/**
 * Blur progresivo para barras fijas: máxima difusión arriba y se disuelve
 * hacia abajo. `noiseFactor(0f)` evita el grano que Haze añade por defecto y
 * que en escritorio se ve como "grumoso".
 *
 * La transición se hace con `mask` (gradiente alfa) en vez de
 * `HazeProgressive`: este último usa un RuntimeEffect que en Windows puede
 * bloquear el primer render 30 s o más (haze#1260 / SKIKO-1179). El mask es
 * un gradiente normal de Skia y no compila shaders.
 */
@Composable
fun rememberProgressiveBlurBarStyle(radius: Dp = 22.dp): HazeBlurStyle = remember(radius) {
    HazeBlurStyle {
        blurRadius(radius)
        noiseFactor(0f)
        mask(
            Brush.verticalGradient(
                0f to Color.White,
                0.65f to Color.White.copy(alpha = 0.7f),
                1f to Color.Transparent,
            ),
        )
    }
}

/**
 * Velo sutil sobre el difuminado: sin él, los controles flotantes pierden
 * contraste porque detrás solo hay contenido borroso.
 */
@Composable
fun rememberProgressiveBarScrim(): Brush {
    val surface = MaterialTheme.colorScheme.surface
    return remember(surface) {
        Brush.verticalGradient(
            0.0f to surface.copy(alpha = 0.55f),
            0.65f to surface.copy(alpha = 0.22f),
            1.0f to Color.Transparent,
        )
    }
}

/**
 * Franja difuminada que queda detrás de una barra fija. Debe componerse
 * DESPUÉS de la fuente Haze (el contenido) y ANTES de la barra con los
 * controles, para no taparlos.
 */
@Composable
fun ProgressiveBlurBackdrop(
    hazeState: HazeState?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    if (hazeState == null || height <= 0.dp) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = rememberProgressiveBlurBarStyle(),
            )
            .background(rememberProgressiveBarScrim()),
    )
}
