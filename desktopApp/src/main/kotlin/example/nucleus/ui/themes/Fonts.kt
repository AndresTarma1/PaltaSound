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
import java.awt.GraphicsEnvironment
import org.jetbrains.compose.resources.Font

/**
 * Nombres de las familias de fuentes instaladas en el sistema, ordenados alfabéticamente.
 * AWT se usa solo para enumerarlas; el renderizado lo sigue haciendo Compose/Skia, así que
 * funciona igual con el backend Tao.
 */
fun systemFontNames(): List<String> =
    GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.sorted()

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
