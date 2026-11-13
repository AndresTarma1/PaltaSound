package example.nucleus.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Warm-up de los shaders de Haze Glass.
 *
 * Haze compila sus `RuntimeEffect` de forma perezosa y GLOBAL la primera vez
 * que dibuja cristal. Con la app abierta en modo material, ese coste cae en el
 * frame en que el usuario activa Liquid Glass y la ventana se congela un
 * instante. Componer un cristal diminuto al arrancar (con el interruptor
 * apagado) paga ese coste una sola vez, en el startup, y el toggle queda
 * instantáneo.
 *
 * Es 2dp con alpha casi nulo: invisible a efectos prácticos.
 */
@Composable
fun GlassShaderWarmup(modifier: Modifier = Modifier) {
    val source = rememberHazeState()
    val glassStyle = rememberLiquidGlassStyle(
        shape = RoundedCornerShape(2.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    val blurStyle = rememberProgressiveBlurBarStyle()

    Box(modifier = modifier.size(2.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surface)
                .hazeSource(source),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .hazeGlass(
                    input = HazeInput.Sources(source),
                    style = glassStyle,
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .hazeBlur(
                    input = HazeInput.Sources(source),
                    style = blurStyle,
                ),
        )
    }
}
