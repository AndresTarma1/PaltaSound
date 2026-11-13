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
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.material3.material3

/**
 * Interruptor BETA de Liquid Glass. Se provee una vez en [example.nucleus.navigation.NavigationDesktop]
 * desde la preferencia persistida; los componentes glass lo consumen sin cablear parámetros
 * por todas las pantallas. Por defecto, todo se renderiza clásico.
 */
val LocalLiquidGlass = compositionLocalOf { false }

/**
 * Indica si este proceso arrancó en cristal. En ese caso el interruptor puede
 * alternar en caliente; si no, activar cristal requiere reiniciar porque el
 * primer frame compila los shaders de Haze y bloquea la UI varios segundos.
 */
val LocalLiquidGlassCanToggleLive = compositionLocalOf { false }

/**
 * Ajustes del material Liquid Glass configurables desde Ajustes. Se provee en
 * App desde las preferencias; los estilos lo leen, así que mover un slider
 * actualiza cristal, lentes y menús en vivo. La refracción se aplica a ambos
 * estilos por igual (barra Regular y lentes Clear).
 */
data class LiquidGlassTuning(
    val blurDp: Float = 28f,
    val baseAlpha: Float = 0.9f,
    val specular: Float = 0.55f,
    val refraction: Float = 0.8f,
)

val LocalLiquidGlassTuning = compositionLocalOf { LiquidGlassTuning() }

/**
 * Fuente Haze compartida para los materiales Glass (fondo real de la ventana).
 * La provee NavigationDesktop con el estado que muestrea la capa de fondo.
 */
val LocalGlassHaze = compositionLocalOf<HazeState?> { null }

/**
 * Fuente del cristal del MiniPlayer y de los menús que flotan sobre el
 * contenido (p. ej. el dropdown del title bar). La provee App y Navigation le
 * ata la fuente a la caja del contenido (hermana-anterior de los overlays:
 * patrón sibling de la guía). No reutilizar [LocalGlassHaze]: aquel vive en un
 * hazeSource ancestro de toda la ventana y Behind ahí selecciona vacío.
 */
val LocalContentHaze = compositionLocalOf<HazeState?> { null }

/**
 * Fuente del cristal de la barra de navegación. La provee App con el
 * railHazeState, atado a la capa de fondo ambiental (degradado/cover
 * desenfocado). La rail va en flujo sin nada desplazándose detrás, así que
 * muestrea el fondo de ventana completa en vez del contenido.
 */
val LocalRailHaze = compositionLocalOf<HazeState?> { null }

/**
 * Estilo Glass para superficies GRANDES con texto (barra del MiniPlayer, rails,
 * tarjetas). Basado en `GlassStyle.regular`; blur, base, specular y refracción
 * salen del ajuste de usuario ([LocalLiquidGlassTuning]).
 *
 * El color vive DENTRO del estilo (material3 + tint): no añadir
 * `Modifier.background` encima, que aplana el cristal en una capa opaca.
 */
@Composable
fun rememberLiquidGlassStyle(
    shape: RoundedCornerShape = RoundedCornerShape(50),
    selected: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    // Overrides explícitos (los usa el preview de Ajustes con sus sliders; si
    // son null se lee LocalLiquidGlassTuning). Separan "llega el valor" de
    // "Haze lo aplica" a la hora de depurar.
    blurDp: Float? = null,
    baseAlpha: Float? = null,
    specular: Float? = null,
    refraction: Float? = null,
): GlassStyle {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f
    val tuning = LocalLiquidGlassTuning.current
    val blur = blurDp ?: tuning.blurDp
    val alpha = baseAlpha ?: tuning.baseAlpha
    val spec = specular ?: tuning.specular
    val refr = refraction ?: tuning.refraction
    // Base translúcida REAL dentro del estilo: sobre fondos planos y oscuros la
    // refracción no aporta contraste, así que la presencia del cristal la dan la
    // base + el tint. Sin esto el cristal es invisible. En claro la base es
    // BLANCA a propósito (no del contenedor): garantiza cristal legible sobre
    // cualquier contenido, venga la respuesta de Haze clara u oscura.
    val base = if (isDark) {
        containerColor.copy(alpha = alpha)
    } else {
        Color.White.copy(alpha = (alpha + 0.02f).coerceAtMost(1f))
    }
    // Tint sutil estilo Apple: apenas tiñe en oscuro, algo más en claro para
    // que el texto siga legible sin volver opaco el cristal.
    val tint = if (selected) {
        colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.35f)
    } else if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.30f)
    }

    val baseStyle = GlassStyle.regular.material3(
        containerColor = containerColor,
        tint = tint,
    )
    return remember(baseStyle, shape, selected, base, isDark, blur, spec, refr) {
        baseStyle.then {
            backgroundColor(base)
            shape(shape)
            // Respuesta de luz de la guía: specular visible + elevación suave,
            // sin sombra dura que recorte el cristal como un recuadro. En tema
            // claro el ribete negro ensucia el borde: se quita.
            specularIntensity(spec)
            ambientResponse(0.3f)
            edgeShadow(if (isDark) Color.Black.copy(alpha = 0.14f) else Color.Transparent)
            // Dispersión contenida: iridiscencia en bordes sin artefactos.
            chromaticAberrationStrength(0.12f)
            // Esmerilado del ajuste de usuario: a estos radios nada detrás se
            // distingue, en superficies grandes y pequeñas por igual.
            optics {
                copy(
                    blurRadius = OpticalSizeValue.Fixed(blur.dp),
                    depth = OpticalSizeValue.Fixed(1f),
                    refractionStrength = refr,
                )
            }
        }
    }
}

/**
 * Estilo Glass para CONTROLES pequeños (IconButtons de transporte, cola,
 * volumen, like). Basado en `GlassStyle.clear`: refracción concentrada en una
 * banda del borde que forma loops en las esquinas — la "lente" se lee como
 * cristal sobre la barra, en vez de fundirse con ella como un círculo
 * translúcido plano. Blur y refracción salen del ajuste de usuario.
 *
 * Incluye la respuesta de interacción de la guía (pressed > hovered >
 * focused): luz + refracción + escala. El [interactionSource] se pasa al
 * modificador `hazeGlass`, no al estilo, así que un mismo estilo sirve para
 * varios botones sin compartir estado.
 */
@Composable
fun rememberLiquidGlassControlStyle(
    shape: RoundedCornerShape = RoundedCornerShape(50),
    selected: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    blurDp: Float? = null,
    baseAlpha: Float? = null,
    specular: Float? = null,
    refraction: Float? = null,
): GlassStyle {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f
    val tuning = LocalLiquidGlassTuning.current
    val alpha = baseAlpha ?: tuning.baseAlpha
    val blur = blurDp ?: tuning.blurDp
    val spec = (specular ?: tuning.specular) + 0.05f
    val refr = refraction ?: tuning.refraction
    // Base con presencia: en oscuro sale del contenedor; en claro es BLANCA a
    // propósito para que la lente nunca salga oscura sobre portadas, venga la
    // respuesta de Haze clara u oscura. Un punto por debajo de la barra para
    // jerarquía, salvo seleccionada.
    val lightBase = (if (selected) alpha + 0.02f else alpha - 0.08f).coerceIn(0.3f, 1f)
    val base = if (isDark) {
        containerColor.copy(
            alpha = (if (selected) alpha + 0.02f else alpha - 0.1f)
                .coerceIn(0.3f, 1f),
        )
    } else {
        Color.White.copy(alpha = lightBase)
    }
    val tint = if (selected) {
        colorScheme.primary.copy(alpha = if (isDark) 0.25f else 0.35f)
    } else if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.30f)
    }

    val baseStyle = GlassStyle.clear.material3(
        containerColor = containerColor,
        tint = tint,
    )
    return remember(baseStyle, shape, selected, base, isDark, blur, spec, refr) {
        baseStyle.then {
            backgroundColor(base)
            shape(shape)
            specularIntensity(spec.coerceAtMost(1f))
            ambientResponse(0.35f)
            edgeShadow(if (isDark) Color.Black.copy(alpha = 0.14f) else Color.Transparent)
            chromaticAberrationStrength(0.15f)
            // Mismo esmerilado que la barra: las lentes no dejan distinguir el
            // fondo, pero conservan la refracción de borde de Clear.
            optics {
                copy(
                    blurRadius = OpticalSizeValue.Fixed(blur.dp),
                    depth = OpticalSizeValue.Fixed(1f),
                    refractionStrength = refr,
                )
            }
            pressed {
                lightingIntensity(1f)
                refractionMultiplier(1.08f)
                whitePointDelta(0.04f)
                scale(0.98f)
            }
            hovered {
                lightingIntensity(0.35f)
            }
            focused {
                lightingIntensity(0.35f)
            }
        }
    }
}

/**
 * Tinte de base que asegura que el cristal se lea incluso sobre fondos
 * degradados o ya desenfocados (donde el blur no aporta diferencia visible).
 *
 * @deprecated El fondo debe vivir DENTRO del GlassStyle (backgroundColor/tint
 * de material3). Mantener un `Modifier.background` extra encima del cristal lo
 * aplana. Solo se conserva por compatibilidad con llamadas antiguas.
 */
@Deprecated("El fondo vive dentro del GlassStyle; no añadir Modifier.background sobre hazeGlass.")
@Composable
fun liquidGlassFillColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    return if (colorScheme.surface.luminance() < 0.5f) {
        colorScheme.surfaceContainer.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.65f)
    }
}
