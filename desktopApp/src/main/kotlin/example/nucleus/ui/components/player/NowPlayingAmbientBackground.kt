package example.nucleus.ui.components.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import example.nucleus.utils.LocalAnimationsEnabled
import kotlin.math.roundToInt

/**
 * Fondo ambiental de Now Playing: la base del tema con resplandores de la paleta
 * (primario, terciario, secundario) a la deriva, como el fullscreen de Sonora.
 *
 * No es un blur de la carátula sino orbes radiales que se mueven despacio: cuesta una
 * fracción del blur real y nunca toca layout. Con animaciones desactivadas queda fijo.
 * El velo de fondo apaga los orbes por igual en tema claro y oscuro para que el texto
 * siempre se lea.
 */
@Composable
fun NowPlayingAmbientBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animationsEnabled = LocalAnimationsEnabled.current
    val dark = scheme.background.luminance() < 0.5f
    // En claro los orbes van mas apagados: el fondo ya es luminoso y el contraste lo
    // da el texto, no el velo. Valores contenidos a proposito: esto es un lavado de
    // fondo, no focos; el velo los apaga otro tanto por encima.
    val glowAlpha = if (dark) 0.24f else 0.15f
    val veilAlpha = if (dark) 0.42f else 0.48f

    // Tres fases desincronizadas para que el movimiento no se lea como un bucle.
    val phases = if (animationsEnabled) ambientPhases() else Triple(0.5f, 0.5f, 0.5f)
    val (pa, pb, pc) = phases

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val unit = minOf(widthPx, heightPx).coerceAtLeast(1f)

        Box(modifier = Modifier.fillMaxSize().background(scheme.background))

        // Violeta arriba-izquierda, magenta a la derecha, azul abajo-derecha: la misma
        // composición de resplandores de la referencia. Diametros contenidos para que el
        // centro quede oscuro y el resplandor viva en los bordes.
        AmbientOrb(
            color = scheme.tertiary,
            alpha = glowAlpha,
            diameterPx = unit * 0.95f,
            centerX = widthPx * (0.08f + (pa - 0.5f) * 0.12f),
            centerY = heightPx * (0.10f + (pb - 0.5f) * 0.10f),
        )
        AmbientOrb(
            color = scheme.primary,
            alpha = glowAlpha * 0.85f,
            diameterPx = unit * 1.05f,
            centerX = widthPx * (0.88f + (pb - 0.5f) * 0.12f),
            centerY = heightPx * (0.32f + (pc - 0.5f) * 0.12f),
        )
        AmbientOrb(
            color = scheme.secondary,
            alpha = glowAlpha,
            diameterPx = unit * 0.9f,
            centerX = widthPx * (0.82f + (pc - 0.5f) * 0.12f),
            centerY = heightPx * (0.92f + (pa - 0.5f) * 0.10f),
        )
        AmbientOrb(
            color = scheme.primary,
            alpha = glowAlpha * 0.45f,
            diameterPx = unit * 0.75f,
            centerX = widthPx * (0.10f + (pc - 0.5f) * 0.10f),
            centerY = heightPx * (0.90f + (pb - 0.5f) * 0.08f),
        )

        // Velo plano del fondo: apaga los orbes sin teñir, en ambos temas.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background.copy(alpha = veilAlpha)),
        )
    }
}

@Composable
private fun ambientPhases(): Triple<Float, Float, Float> {
    val transition = rememberInfiniteTransition(label = "now_playing_ambient")
    val a by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambient_a",
    )
    val b by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(21000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambient_b",
    )
    val c by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambient_c",
    )
    return Triple(a, b, c)
}

@Composable
private fun AmbientOrb(
    color: Color,
    alpha: Float,
    diameterPx: Float,
    centerX: Float,
    centerY: Float,
) {
    val density = LocalDensity.current
    val diameter: Dp = with(density) { diameterPx.toDp() }
    Box(
        modifier = Modifier
            .size(diameter)
            .offset {
                IntOffset(
                    (centerX - diameterPx / 2f).roundToInt(),
                    (centerY - diameterPx / 2f).roundToInt(),
                )
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                ),
            ),
    )
}
