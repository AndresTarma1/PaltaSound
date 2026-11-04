package example.nucleus.ui.components.glass

import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.HazeState

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
