@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.MediaInfo
import example.nucleus.generated.resources.*
import example.nucleus.models.MediaMetadata
import example.nucleus.navigation.Route
import example.nucleus.viewmodels.PlayerUiState
import org.jetbrains.jewel.foundation.modifier.onHover

@Composable
internal fun NowPlayingWideBody(
    state: PlayerUiState,
    song: MediaMetadata,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    selectedTab: NowPlayingTab,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    coverScale: Animatable<Float, *>,
    coverAlpha: Animatable<Float, *>,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    autoHide: NowPlayingAutoHide,
) {
    val hasPanel = selectedTab.hasPanel
    // Solo se llega aqui en ancho, asi que el panel siempre va en paralelo.
    val split = hasPanel

    // Diseño de escritorio tipo Apple Music / Spotify:
    // izquierda = carátula + metadatos + progreso + transporte; derecha = panel de
    // letra/cola. Sin panel (vista de portada) la izquierda ocupa todo el ancho.
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    Row(
        modifier = Modifier
            .weight(if (split) 0.45f else 1f)
            .fillMaxHeight(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // La carátula cede espacio a los metadatos para no desbordar la columna
                val coverDimension = minOf(
                    maxWidth * 0.8f,
                    maxHeight - 170.dp,
                    360.dp,
                ).coerceAtLeast(96.dp)

                // Carátula en reposo: con los controles ocultos el reserve baja de 170
                // a ~96dp, así que puede crecer (Sonora fullscreen.rs:1082) sin reflow —
                // solo se re-escala el raster y se compensa el desbordamiento en los detalles.
                val coverRest = minOf(
                    maxWidth * 0.92f,
                    maxHeight - 96.dp,
                    440.dp,
                ).coerceAtLeast(coverDimension)
                val coverRestScale = (coverRest / coverDimension).coerceAtMost(1.25f)
                val coverLift = coverDimension * (coverRestScale - 1f) / 2f

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CoverArt(
                        url = song.thumbnailUrl,
                        title = song.title,
                        videoId = song.id,
                        modifier = Modifier
                            .size(coverDimension)
                            .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                            .coverEnter(coverScale, coverAlpha)
                            .autoHideCoverGrow(autoHide, coverRestScale),
                    )

                    NowPlayingSongDetails(
                        state = state,
                        song = song,
                        textAlign = TextAlign.Center,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        compact = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp)
                            .padding(horizontal = 8.dp)
                            .autoHideCoverLift(autoHide, coverLift),
                    )
                }
            }

            // Progreso + transporte (el mini player está oculto en Now Playing).
            // Acotados al ancho de la columna de metadatos: la barra no debe estirarse
            // mas que la portada que acompaña.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .padding(top = 8.dp)
                    .autoHideChrome(autoHide, sink = 18.dp)
                    .onHover { autoHide.chromeHover = it },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlayerProgressRow()
                PlayerTransportRow()
            }
        }

        // Boton de mute con el porcentaje en tooltip, en la esquina inferior derecha.
        // Sin panel la columna izquierda ocupa todo el ancho, asi que el volumen cae en
        // la misma esquina que el boton de colapsar y lo pisa. Se sube lo justo para
        // quedar encima: el colapsar ocupa de 8 a 48dp del borde y el tooltip emerge
        // hacia arriba desde los 56dp, con 8dp de aire entre ambos.
        PlayerVolumeVertical(
            modifier = Modifier
                .padding(end = 12.dp, bottom = if (split) 12.dp else 40.dp)
                .autoHideChrome(autoHide, sink = 18.dp)
                .onHover { autoHide.chromeHover = it },
            trackHeight = 88.dp,
            onBusyChange = { autoHide.volumePopup = it },
        )
    }

    // Columna derecha: solo con panel abierto. Sin panel la columna izquierda ya
    // ocupa el ancho completo, asi que la portada queda centrada en la ventana.
    if (split) {
        TransparentPanel(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, top = 8.dp, bottom = 12.dp),
            ) {
                NowPlayingTabContent(
                    tab = selectedTab,
                    song = song,
                    state = state,
                    lyrics = lyrics,
                    lyricsTextStyle = MaterialTheme.typography.headlineMedium,
                    onNavigate = onNavigate,
                    mediaInfo = mediaInfo,
                )
            }
        }
    }
    }
}
