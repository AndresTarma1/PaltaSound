package example.nucleus.ui.themes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import example.nucleus.data.repository.DarkLevel
import example.nucleus.data.repository.BackgroundStyle
import example.nucleus.data.repository.IslandStyle
import example.nucleus.data.repository.LayoutMode
import example.nucleus.data.repository.ThemeMode
import example.nucleus.data.repository.ThemePalette
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.ui.components.artwork.ArtworkColors
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicMaterialThemeState

val LocalChromeSurface = staticCompositionLocalOf { Color.Transparent }
val LocalIsSolidBackground = staticCompositionLocalOf { true }

/**
 * Escala de formas Material 3 Expressive: superficies más suaves que el M3 clásico
 * (xLarge en 20dp en vez de 24) y extraLarge para diálogos y tarjetas hero.
 * Se usa directamente como [AppShapes] porque `xLarge` no tiene equivalente en
 * [MaterialTheme.shapes].
 */
object AppShapes {
    val extraSmall: RoundedCornerShape = RoundedCornerShape(4.dp)
    val small: RoundedCornerShape = RoundedCornerShape(8.dp)
    val medium: RoundedCornerShape = RoundedCornerShape(12.dp)
    val large: RoundedCornerShape = RoundedCornerShape(16.dp)
    val xLarge: RoundedCornerShape = RoundedCornerShape(20.dp)
    val extraLarge: RoundedCornerShape = RoundedCornerShape(28.dp)

    // Roles "increased" de M3 Expressive: un punto más de redondeo sobre large/extraLarge.
    // Los usan contenedores que alojan contenido (tarjetas de rejilla, bloques de sección).
    val largeIncreased: RoundedCornerShape = RoundedCornerShape(20.dp)
    val extraLargeIncreased: RoundedCornerShape = RoundedCornerShape(32.dp)
    val extraExtraLarge: RoundedCornerShape = RoundedCornerShape(40.dp)
}

/**
 * [MaterialTheme.shapes] con los ocho roles de la alpha actual. Los tres primeros son los
 * increased, que antes se emulaban pasando [AppShapes.large] a mano y quedaban fuera del
 * tema: cualquier componente de M3 que los pida por defecto los toma ya de aqui.
 */
private val MaterialShapes = Shapes(
    extraSmall = AppShapes.extraSmall,
    small = AppShapes.small,
    medium = AppShapes.medium,
    large = AppShapes.large,
    extraLarge = AppShapes.extraLarge,
    largeIncreased = AppShapes.largeIncreased,
    extraLargeIncreased = AppShapes.extraLargeIncreased,
    extraExtraLarge = AppShapes.extraExtraLarge,
)

@Composable
fun AppTheme(
    artworkColors: ArtworkColors? = null,
    userPreferences: UserPreferencesRepository,
    content: @Composable () -> Unit,
) {
    val themeMode by userPreferences.themeMode.collectAsState(ThemeMode.SYSTEM)
    val dynamicEnabled by userPreferences.dynamicColorFromArtwork.collectAsState(false)
    val palette by userPreferences.themePalette.collectAsState(ThemePalette.DEFAULT)
    val darkLevel by userPreferences.darkLevel.collectAsState(DarkLevel.DIM)
    val backgroundStyle by userPreferences.appBackgroundStyle.collectAsState(BackgroundStyle.SOLID_COLOR)
    val layoutMode by userPreferences.layoutMode.collectAsState(LayoutMode.ATTACHED)
    val islandStyle by userPreferences.islandStyle.collectAsState(IslandStyle.COMFORTABLE)
    val selectedFont by userPreferences.selectedFont.collectAsState("")
    val animationsEnabled by userPreferences.animationsEnabled.collectAsState(true)

    val isDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    val targetSeeds = remember(artworkColors, dynamicEnabled, palette) {
        val basePrimary = if (dynamicEnabled && artworkColors != null && artworkColors != ArtworkColors.Default) {
            artworkColors.vibrant
        } else {
            Color(palette.primary)
        }
        val baseSecondary = if (dynamicEnabled && artworkColors != null && artworkColors != ArtworkColors.Default) {
            artworkColors.muted
        } else {
            Color(palette.secondary)
        }
        basePrimary to baseSecondary
    }

    // Crossfade seed colors when artwork / palette changes (bloom) — snap if reduce-motion.
    val seedSpec = if (animationsEnabled) {
        expressiveTween<Color>(durationMillis = expressiveColorDuration)
    } else {
        snap()
    }
    val animatedPrimary by animateColorAsState(
        targetValue = targetSeeds.first,
        animationSpec = seedSpec,
        label = "themePrimarySeed",
    )
    val animatedSecondary by animateColorAsState(
        targetValue = targetSeeds.second,
        animationSpec = seedSpec,
        label = "themeSecondarySeed",
    )
    val seeds = animatedPrimary to animatedSecondary

    val dynamicThemeState = rememberDynamicMaterialThemeState(
        isDark = isDarkTheme,
        style = PaletteStyle.Content,
        primary = seeds.first,
        secondary = seeds.second,
    )

    val baseScheme = dynamicThemeState.colorScheme
    val colorScheme = remember(baseScheme, isDarkTheme, darkLevel, seeds.first) {
        if (isDarkTheme) baseScheme.darkened(darkLevel, seeds.first) else baseScheme
    }

    // Roboto (empaquetada) es la fuente por defecto; el selector solo la reemplaza
    // cuando el usuario elige otra fuente del sistema.
    val defaultFamily = robotoFamily()
    val customFamily = remember(selectedFont) {
        if (selectedFont.isBlank()) null else systemFontFamily(selectedFont)
    }
    val fontFamily = customFamily ?: defaultFamily

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MaterialShapes,
        typography = remember(fontFamily) { paltaTypography(fontFamily) },
        // Scheme oficial de M3 Expressive: los componentes M3 que lo consulten (menús,
        // chips, indicators) toman ya sus curvas. El chrome propio sigue usando Motion.kt,
        // que mantiene el carácter "sin rebote" de escritorio.
        motionScheme = MotionScheme.expressive(),
    ) {
        CompositionLocalProvider(
            LocalDimens provides dimensFor(layoutMode, islandStyle),
            LocalLayoutMode provides layoutMode,
            LocalIsSolidBackground provides (backgroundStyle == BackgroundStyle.SOLID_COLOR),
            LocalChromeSurface provides if (darkLevel == DarkLevel.BLACK || backgroundStyle != BackgroundStyle.SOLID_COLOR) {
                Color.Transparent
            } else {
                colorScheme.surfaceContainer
            },
            content = content,
        )
    }
}

/** Re-derives the dark scheme's surfaces for the chosen [level], tinting them with [accent]. */
private fun ColorScheme.darkened(level: DarkLevel, accent: Color): ColorScheme = when (level) {
    DarkLevel.BLACK -> copy(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceDim = Color(0xFF000000),
        surfaceBright = Color(0xFF1C1C1C),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF202020),
        surfaceVariant = Color(0xFF1A1A1A),
    )
    DarkLevel.DIM -> {
        fun t(baseHex: Long, amount: Float) = lerp(Color(baseHex), accent, amount)
        copy(
            background = t(0xFF0A0A0C, 0.04f),
            surface = t(0xFF121214, 0.05f),
            surfaceDim = t(0xFF0A0A0C, 0.04f),
            surfaceBright = t(0xFF242428, 0.06f),
            surfaceContainerLowest = t(0xFF0D0D0F, 0.04f),
            surfaceContainerLow = t(0xFF161618, 0.05f),
            surfaceContainer = t(0xFF1A1A1D, 0.06f),
            surfaceContainerHigh = t(0xFF222226, 0.06f),
            surfaceContainerHighest = t(0xFF2A2A2E, 0.06f),
            surfaceVariant = t(0xFF26262A, 0.05f),
        )
    }
}
