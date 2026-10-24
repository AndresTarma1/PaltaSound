package example.nucleus.ui.themes

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.roboto_bold
import example.nucleus.generated.resources.roboto_medium
import example.nucleus.generated.resources.roboto_regular
import org.jetbrains.compose.resources.Font

/**
 * Nombres de las familias de fuentes instaladas en el sistema.
 *
 * NOTA (migración a Tao / nucleus-3.0): el backend Tao no expone `java.awt.GraphicsEnvironment`,
 * por lo que esta función devuelve una lista vacía. Las fuentes empaquetadas con la app
 * (Roboto) siguen funcionando normalmente. Si se necesita enumerar fuentes del sistema en el
 * futuro, habría que buscar una alternativa sin AWT.
 */
fun systemFontNames(): List<String> = emptyList()

/** [FontFamily] que apunta a una familia instalada en el sistema. Nombre vacío → la predeterminada. */
@OptIn(ExperimentalTextApi::class)
fun systemFontFamily(name: String): FontFamily {
    if (name.isBlank()) return FontFamily.Default
    return FontFamily(
        SystemFont(name, FontWeight.Normal),
        SystemFont(name, FontWeight.Medium),
        SystemFont(name, FontWeight.SemiBold),
        SystemFont(name, FontWeight.Bold),
    )
}

/**
 * Roboto empaquetado con la app (regular/medium/bold). Es la fuente por defecto de la
 * aplicación; el selector de fuentes solo la reemplaza cuando el usuario elige otra.
 */
@Composable
fun robotoFamily(): FontFamily = FontFamily(
    Font(Res.font.roboto_regular, FontWeight.Normal),
    Font(Res.font.roboto_medium, FontWeight.Medium),
    Font(Res.font.roboto_bold, FontWeight.Bold),
)
