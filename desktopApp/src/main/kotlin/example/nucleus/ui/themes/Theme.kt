package example.nucleus.ui.themes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import example.nucleus.data.repository.CornerStyle
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
    val extraSmall: RoundedCornerShape @Composable get() = LocalAppShapes.current.extraSmall
    val small: RoundedCornerShape @Composable get() = LocalAppShapes.current.small
    val medium: RoundedCornerShape @Composable get() = LocalAppShapes.current.medium
    val large: RoundedCornerShape @Composable get() = LocalAppShapes.current.large
    val xLarge: RoundedCornerShape @Composable get() = LocalAppShapes.current.xLarge
    val extraLarge: RoundedCornerShape @Composable get() = LocalAppShapes.current.extraLarge

    // Roles "increased" de M3 Expressive: un punto más de redondeo sobre large/extraLarge.
    // Los usan contenedores que alojan contenido (tarjetas de rejilla, bloques de sección).
    val largeIncreased: RoundedCornerShape @Composable get() = LocalAppShapes.current.largeIncreased
    val extraLargeIncreased: RoundedCornerShape @Composable get() = LocalAppShapes.current.extraLargeIncreased
    val extraExtraLarge: RoundedCornerShape @Composable get() = LocalAppShapes.current.extraExtraLarge
}

/**
 * Escala de formas completa de la app, ya escalada por la preferencia [CornerStyle].
 *
 * Es una data class y no un simple factor para poder fijar los roles "increased" de M3 Expressive
 * de forma independiente: aun subiendo el radio general, un `extraExtraLarge` tiene que seguir
 * siendo claramente más redondo que un `small`. Con un único multiplicador, `RECTANGULAR` los
 * aplastaría todos a 0 por igual; aquí cada rol tiene su propio peso para que la jerarquía se
 * mantenga incluso en los extremos.
 */
data class AppShapeScale(
    val extraSmall: RoundedCornerShape,
    val small: RoundedCornerShape,
    val medium: RoundedCornerShape,
    val large: RoundedCornerShape,
    val xLarge: RoundedCornerShape,
    val extraLarge: RoundedCornerShape,
    val largeIncreased: RoundedCornerShape,
    val extraLargeIncreased: RoundedCornerShape,
    val extraExtraLarge: RoundedCornerShape,
) {
    companion object {
        /**
         * Radios base (dp) de la escala de la app, antes de aplicar [CornerStyle]. Se guardan
         * como Int porque tienen que poder multiplicarse por un factor antes de convertirse en dp.
         */
        private const val BASE_EXTRA_SMALL = 4
        private const val BASE_SMALL = 8
        private const val BASE_MEDIUM = 12
        private const val BASE_LARGE = 16
        private const val BASE_X_LARGE = 20
        private const val BASE_EXTRA_LARGE = 28
        private const val BASE_LARGE_INCREASED = 20
        private const val BASE_EXTRA_LARGE_INCREASED = 32
        private const val BASE_EXTRA_EXTRA_LARGE = 40

        /**
         * `extraSmall` no se escala igual que el resto: es el radio de campos diminutos y
         * separadores, donde un factor 1.35 lo desproporcionaría. Sigue la preferencia pero con
         * la mitad de fuerza, para que en RECTANGULAR sea 0 y en ROUNDED no se vuelva una píldora.
         */
        fun forCornerStyle(style: CornerStyle): AppShapeScale {
            val f = style.scale
            val fSmall = f * 0.5f
            // coerceAtLeast por construccion: un radio negativo revienta con
            // IllegalArgumentException en CornerBasedShape.createOutline, y eso es un crash de
            // ventana, no un warning. Blindamos el punto por si el factor llegara negativo.
            fun scale(dp: Int, factor: Float = f) =
                RoundedCornerShape((dp * factor).coerceAtLeast(0f).dp)

            return AppShapeScale(
                extraSmall = scale(BASE_EXTRA_SMALL, fSmall),
                small = scale(BASE_SMALL),
                medium = scale(BASE_MEDIUM),
                large = scale(BASE_LARGE),
                xLarge = scale(BASE_X_LARGE),
                extraLarge = scale(BASE_EXTRA_LARGE),
                largeIncreased = scale(BASE_LARGE_INCREASED),
                extraLargeIncreased = scale(BASE_EXTRA_LARGE_INCREASED),
                extraExtraLarge = scale(BASE_EXTRA_EXTRA_LARGE),
            )
        }
    }
}

val LocalAppShapes = staticCompositionLocalOf { AppShapeScale.forCornerStyle(CornerStyle.SOFT) }

/**
 * [MaterialTheme.shapes] alimentado por la escala viva, para que cualquier componente de M3 que
 * pida su forma por defecto (un `Button`, un `Card` sin `shape`) herede la preferencia sin tocar
 * nada. Se construye dentro de la composicion porque `AppShapes` ya es dinamico.
 *
 * Los tres ultimos roles son los increased de M3 Expressive, que antes se emulaban pasando
 * [AppShapes.large] a mano y quedaban fuera del tema.
 */
@Composable
private fun materialShapes(): Shapes {
    val s = LocalAppShapes.current
    return Shapes(
        extraSmall = s.extraSmall,
        small = s.small,
        medium = s.medium,
        large = s.large,
        extraLarge = s.extraLarge,
        largeIncreased = s.largeIncreased,
        extraLargeIncreased = s.extraLargeIncreased,
        extraExtraLarge = s.extraExtraLarge,
    )
}

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
    val cornerStyle by userPreferences.cornerStyle.collectAsState(CornerStyle.SOFT)
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

    // Escala de formas segun la preferencia del usuario. Va en un CompositionLocal aparte porque
    // AppTheme.colorScheme cambia con el tema pero las formas no dependen de el.
    val appShapes = remember(cornerStyle) { AppShapeScale.forCornerStyle(cornerStyle) }

    CompositionLocalProvider(LocalAppShapes provides appShapes) {
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = materialShapes(),
        typography = remember(fontFamily) { paltaTypography(fontFamily) },
        // Scheme oficial de M3 Expressive: los componentes M3 que lo consulten (menús,
        // chips, indicators) toman ya sus curvas. Con las animaciones apagadas se
        // sustituye por uno neutro, porque esos componentes leen el scheme por dentro y
        // no aceptan un spec propio.
        motionScheme = if (animationsEnabled) MotionScheme.expressive() else InstantMotionScheme,
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
