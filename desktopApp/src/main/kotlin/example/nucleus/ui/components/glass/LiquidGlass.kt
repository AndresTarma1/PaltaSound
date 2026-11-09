package example.nucleus.ui.components.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue

/**
 * Interruptor BETA de Liquid Glass. Se provee una vez en [example.nucleus.navigation.NavigationDesktop]
 * desde la preferencia persistida; los componentes glass lo consumen sin cablear parámetros
 * por todas las pantallas. Por defecto, todo se renderiza clásico.
 */
val LocalLiquidGlass = compositionLocalOf { false }

/**
 * Fuente Haze compartida para los materiales Glass (fondo real de la ventana).
 * La provee NavigationDesktop con el estado que muestrea la capa de fondo.
 */
val LocalGlassHaze = compositionLocalOf<HazeState?> { null }

/**
 * Configuración única de Liquid Glass para todos los componentes.
 *
 * En tema claro usa una base clara y más opaca para que el cristal no se vea
 * oscuro; en tema oscuro baja la opacidad para dejar pasar el fondo. La óptica
 * (blur/refracción) es la misma que usa el MiniPlayer.
 */
@Composable
fun rememberLiquidGlassStyle(
    shape: RoundedCornerShape = RoundedCornerShape(50),
    selected: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
): GlassStyle {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f
    val base = if (isDark) containerColor else Color.White
    val backgroundAlpha = when {
        isDark && selected -> 0.48f
        isDark -> 0.36f
        selected -> 0.92f
        else -> 0.85f
    }
    val tintColor = if (isDark) containerColor else Color.White
    val tintAlpha = if (isDark) 0.08f else 0.28f

    return remember(base, tintColor, shape, selected, isDark) {
        GlassStyle.regular.then {
            backgroundColor(base.copy(alpha = backgroundAlpha))
            tint(tintColor.copy(alpha = tintAlpha))
            shape(shape)
            edgeShadow(Color.Transparent)
            specularIntensity(0.28f)
            ambientResponse(0.12f)
            optics {
                copy(
                    blurRadius = OpticalSizeValue.Fixed(34.dp),
                    refractionStrength = 1f,
                    refractionDisplacement = 64.dp,
                    refractionHeightFraction = 0.28f,
                    depth = OpticalSizeValue.Fixed(1f),
                )
            }
        }
    }
}
