package example.nucleus.ui.themes

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.data.repository.IslandStyle
import example.nucleus.data.repository.LayoutMode

/**
 * Spacing / sizing design tokens. A single consistent scale (replaces ad-hoc hardcoded dp) plus
 * a few values derived from the [LayoutMode] so "islands" (rounded, spaced cards) and "attached"
 * (edge-to-edge, compact) share one source of truth.
 */
data class Dimens(
    // Base spacing scale (4dp rhythm)
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,

    // Layout-mode derived
    /** Corner radius of top-level "surfaces" (cards, miniplayer, panels). 0 in attached mode. */
    val surfaceCorner: Dp,
    /** Gap between top-level surfaces. 0 in attached mode. */
    val surfaceGap: Dp,
    /** Outer padding of the window content around surfaces. */
    val windowPadding: Dp,
    /** Inner padding inside a surface/card. */
    val surfacePadding: Dp,
    /** Corner radius for inner items (list rows, thumbnails, chips). */
    val itemCorner: Dp,
    /** Drop-shadow elevation that lifts islands off the background. 0 in attached mode (flat). */
    val surfaceElevation: Dp,
    /** Width of the chrome borders separating top-level regions (rail, content, queue, miniplayer). */
    val chromeBorderWidth: Dp,

    // Mini player
    /** Height of the mini player surface (bar or floating card). */
    val miniPlayerHeight: Dp = 88.dp,
    /** Outer margin of the floating mini player card (bottom inset and gap above content). */
    val miniPlayerFloatingMargin: Dp = 8.dp,
)

fun dimensFor(
    layoutMode: LayoutMode,
    islandStyle: IslandStyle = IslandStyle.COMFORTABLE,
): Dimens = when (layoutMode) {
    LayoutMode.ISLANDS -> Dimens(
        surfaceCorner = islandStyle.cornerDp.dp,
        surfaceGap = islandStyle.gapDp.dp,
        windowPadding = islandStyle.gapDp.dp,
        surfacePadding = 14.dp,
        itemCorner = 10.dp,
        surfaceElevation = 10.dp,
        chromeBorderWidth = 0.5.dp,
    )
    LayoutMode.ATTACHED -> Dimens(
        // Pegado/compacto NO es un modo sin esquinas: es sin margenes ni sombras. El armazón se
        // apoya en el borde de la ventana pero conserva el redondeo, asi que surfaceCorner
        // necesita un radio real. Antes valia 0.dp y eso dejaba el contenido de pegado con
        // esquinas rectas, que es justo lo que se quejaba de "cuadrado/bordes".
        surfaceCorner = 20.dp,
        surfaceGap = 0.dp,
        windowPadding = 0.dp,
        surfacePadding = 12.dp,
        itemCorner = 8.dp,
        surfaceElevation = 0.dp,
        chromeBorderWidth = 0.dp,
    )
    LayoutMode.SQUARE -> Dimens(
        surfaceCorner = 0.dp,
        surfaceGap = 0.dp,
        windowPadding = 0.dp,
        surfacePadding = 12.dp,
        itemCorner = 8.dp,
        surfaceElevation = 0.dp,
        chromeBorderWidth = 1.dp,
    )
}

val LocalDimens = staticCompositionLocalOf { dimensFor(LayoutMode.ATTACHED) }
val LocalLayoutMode = staticCompositionLocalOf { LayoutMode.ATTACHED }

/**
 * Espacio inferior que el mini reproductor flotante ocupa sobre las screens (0 cuando el
 * mini reproductor es una barra pegada). Las screens lo suman al `contentPadding` de sus
 * listas para que el contenido pueda pasar por debajo de la tarjeta sin quedar oculto.
 */
val LocalMiniPlayerInset = staticCompositionLocalOf { 0.dp }

/**
 * Alto que el contenido de la pantalla debe dejar libre ARRIBA porque la barra de título se
 * dibuja **encima** en lugar de apilada encima del contenido.
 *
 * Es 0 en todas las pantallas salvo en Now Playing. Allí la barra se superpone para que el fondo
 * ambiental de la pantalla llegue hasta el borde superior de la ventana, en vez de quedar cortado
 * por una franja opaca de 36dp.
 *
 * El fondo se pinta en el contenedor que llena la ventana entera, así que el padding va en la
 * columna de contenido de dentro. Si se aplicara al fondo, la franja de la barra volvería a
 * verse del color del chrome y esto no serviría para nada.
 */
val LocalTitleBarOverlayInset = staticCompositionLocalOf { 0.dp }
