package example.nucleus.ui.components.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import example.nucleus.data.repository.BackgroundStyle
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.artwork.ArtworkColors
import example.nucleus.ui.components.artwork.rememberArtworkColors
import example.nucleus.utils.LocalUserPreferences


@Composable
fun BackgroundStyle(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    backgroundStyle: BackgroundStyle?,
    backgroundModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Solo extrae paleta cuando realmente se necesita (GRADIENT). Evita decode+quantization
    // en cada cambio de cancion cuando el fondo es SOLID/BLURRED (SOLID es el default tras el fix).
    val artworkColors = if (backgroundStyle == BackgroundStyle.GRADIENT) {
        rememberArtworkColors(imageUrl)
    } else {
        ArtworkColors.Default
    }

    when(backgroundStyle){
        BackgroundStyle.GRADIENT -> BackgroundWithGradient(
            artworkColors = artworkColors,
            modifier = modifier,
            backgroundModifier = backgroundModifier,
            contentModifier = contentModifier,
            content = content
        )
        BackgroundStyle.BLURRED_COVER -> BackgroundWithBlur(
            imageUrl = imageUrl,
            modifier = modifier,
            backgroundModifier = backgroundModifier,
            contentModifier = contentModifier,
            content = content
        )
        BackgroundStyle.SOLID_COLOR -> BackgroundWithSolidColor(
            modifier = modifier,
            backgroundModifier = backgroundModifier,
            contentModifier = contentModifier,
            content = content
        )
        else -> content
    }
}

@Composable
fun BackgroundWithSolidColor(
    modifier: Modifier,
    backgroundModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable (BoxScope.() -> Unit)
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Capa de fondo aislada: es la fuente que consume el Glass del rail (BETA).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.background)
                .then(backgroundModifier),
        )
        val rememberedContent = remember(content) { content }
        Box(modifier = contentModifier.fillMaxSize()) { rememberedContent() }
    }
}


@JvmName("NowPlayingBackgroundWithGradient")
@Composable
fun BackgroundWithGradient(
    artworkColors: ArtworkColors,
    modifier: Modifier = Modifier,
    backgroundModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Creamos un degradado diagonal usando los colores extraídos.
    // Esto simula las esquinas iluminadas de una portada real sin cargar archivos.
    val ambientGradient = remember(artworkColors, surfaceColor) {
        val topColor = if (artworkColors == ArtworkColors.Default) surfaceColor else artworkColors.vibrant
        val bottomColor = if (artworkColors == ArtworkColors.Default) surfaceColor else artworkColors.darkMuted

        Brush.linearGradient(
            colors = listOf(
                topColor.copy(alpha = 0.35f),
                bottomColor.copy(alpha = 0.15f),
                surfaceColor
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(surfaceColor)
                .background(ambientGradient) // Una sola pasada de dibujo nativo
                .then(backgroundModifier),
        )
        val rememberedContent = remember(content) { content }
        Box(modifier = contentModifier.fillMaxSize()) { rememberedContent() }
    }
}


@JvmName("NowPlayingBackgroundFromImageUrlWithBlur")
@Composable
fun BackgroundWithBlur(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    backgroundModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val background = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize()) {
        // Capa de fondo aislada: color base + cover desenfocado. Es la fuente
        // que consume el Glass del rail (BETA).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(background)
                .then(backgroundModifier),
        ) {
            if (!imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Rectangle acota el layer; 32.dp mantiene el ambiente sin el costo de 99.dp
                        // (offscreen render-target del tamaño de la ventana por frame).
                        .blur(radius = 32.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                        .alpha(0.25f)
                ) {
                    MusicPlayerImage(
                        url = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        // El blur de 99dp suaviza todo detalle: decodificar a 192px ahorra memoria
                        // nativa (192^2x4 = 144 KB) sin pérdida visual apreciable en el fondo.
                        coilSizeOverride = 192,
                    )
                }
            }
        }

        // Slot de contenido estable
        val rememberedContent = remember(content) { content }
        Box(modifier = contentModifier.fillMaxSize()) { rememberedContent() }
    }
}