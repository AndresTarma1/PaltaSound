package example.nucleus.ui.components.artwork

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kmpalette.color
import com.kmpalette.loader.NetworkLoader
import com.kmpalette.rememberPaletteState
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Extracted color palette from a song's artwork.
 */
data class ArtworkColors(
    val dominant: Color,
    val vibrant: Color,
    val muted: Color,
    val darkMuted: Color,
    val isLight: Boolean,
    /** False while the palette is still being extracted (or after a failure). */
    val resolved: Boolean = true,
) {
    companion object {
        val Default = ArtworkColors(
            dominant = Color(0xFF1A1A2E),
            vibrant = Color(0xFF6C63FF),
            muted = Color(0xFF2D2D44),
            darkMuted = Color(0xFF121225),
            isLight = false,
            resolved = false,
        )
    }
}

val LocalArtworkColors = staticCompositionLocalOf { ArtworkColors.Default }

// Opciones de caché optimizadas para lectura directa
private class ArtworkPaletteCache(private val maxSize: Int = 64) {
    private val map = LinkedHashMap<String, ArtworkColors>(maxSize, 0.75f, true)

    // Usamos funciones con Intrínsecos de sincronización simples o accesos directos
    fun get(key: String): ArtworkColors? = synchronized(map) { map[key] }

    fun put(key: String, value: ArtworkColors) = synchronized(map) {
        map[key] = value
        if (map.size > maxSize) {
            val eldest = map.keys.iterator().next()
            map.remove(eldest)
        }
    }
}

private val artworkPaletteCache = ArtworkPaletteCache()

@Composable
fun rememberArtworkColors(url: String?, enabled: Boolean = true): ArtworkColors {
    val loader = remember { NetworkLoader.Default }
    val paletteState = rememberPaletteState(loader = loader)

    // 1. SOLUCIÓN AL PARPADEO: Inicializamos el estado directamente buscando en la caché.
    // Si ya existe, se renderiza instantáneamente con los colores correctos sin esperar al LaunchedEffect.
    var colors by remember(url) {
        mutableStateOf(
            if (!url.isNullOrBlank()) artworkPaletteCache.get(url) ?: ArtworkColors.Default
            else ArtworkColors.Default
        )
    }

    LaunchedEffect(url, enabled) {
        if (url.isNullOrBlank()) {
            colors = ArtworkColors.Default
            return@LaunchedEffect
        }

        // La caché puede haberse llenado después de componer esta tarjeta.
        artworkPaletteCache.get(url)?.let {
            colors = it
            return@LaunchedEffect
        }
        if (!enabled) return@LaunchedEffect

        try {
            delay(120.milliseconds)
            withContext(Dispatchers.IO) {
                paletteState.generate(Url(url))
            }

            val dominant = paletteState.palette?.dominantSwatch?.color
            val vibrant = paletteState.palette?.vibrantSwatch?.color
            val muted = paletteState.palette?.mutedSwatch?.color
            val darkMuted = paletteState.palette?.darkMutedSwatch?.color

            val finalDominant = dominant ?: Color(0xFF1A1A2E)

            val resolved = ArtworkColors(
                dominant = finalDominant,
                vibrant = vibrant ?: finalDominant,
                muted = muted ?: finalDominant,
                darkMuted = darkMuted ?: finalDominant,
                isLight = finalDominant.luminance() > 0.5f,
                resolved = true,
            )

            artworkPaletteCache.put(url, resolved)
            colors = resolved

        } catch (e: Exception) {
            // 2. CORRECCIÓN DE CORRUTINAS: Si la excepción es por cancelación, la dejamos pasar.
            // Si no, destruimos el ciclo cooperativo de Compose.
            if (e is CancellationException) throw e
            // Conserva el último color válido en caso de error de red
        }
    }

    return colors
}