@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.models.MediaMetadata
import example.nucleus.navigation.Route
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.nowPlayingTitle
import example.nucleus.ui.themes.songTitle
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.videoThumbnailUrl
import example.nucleus.viewmodels.PlayerUiState
import example.nucleus.viewmodels.QueueSource
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

/**
 * Carátula del reproductor. Cuadrada siempre (`aspectRatio(1f)`).
 *
 * CON VIDEOID, USA EL FRAME DEL VÍDEO EN BRUTO
 * ---------------------------------------------
 * La miniatura que da la API de YouTube Music para una canción normal es un **cuadrado de 120x120**
 * (`yt3.googleusercontent.com/...=w120-h120-l90-rj`). Cuando el vídeo es 16:9, ese cuadrado lleva
 * el frame **encajado con barras negras ya grabadas en el JPEG**: son píxeles de la imagen, no un
 * letterbox de Compose, así que ni `ContentScale.Crop` ni el aspect ratio del marco las quitan. El
 * cover se ve como un cuadro negro con una foto en medio y el recorte hecho por el servidor.
 *
 * La única fuente limpia es `i.ytimg.com/vi/<id>/…jpg`, que da el frame 16:9 entero; al recortarlo
 * al cuadrado `Crop` llena el marco y solo se pierden los lados del frame.
 *
 * Se pide `maxresdefault` (1280x720) y, si falla —no existe salvo que el uploader subiera un
 * thumbnail de 720p o más—, cae a `mqdefault` (320x180), que está en todos los vídeos. Solo si
 * fallan las dos se vuelve al artwork original de la API.
 *
 * El precio, a conciencia: una canción cuya portada sea artwork de verdad enseñará un fotograma
 * del vídeo en lugar de la portada.
 *
 * @param videoId id del vídeo de YouTube. Si es null se usa tal cual [url].
 */
@Composable
fun CoverArt(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    videoId: String? = null,
) {

    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = AppShapes.small,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        MusicPlayerImage(
            url = url,
            contentDescription = title,
            modifier = Modifier
                .fillMaxSize(),
            placeholderType = PlaceholderType.SONG,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun SongHeader(
    state: PlayerUiState,
    song: MediaMetadata,
    textAlign: TextAlign,
    onNavigate: ((Route) -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = when (textAlign) {
            TextAlign.Start -> Alignment.Start
            else -> Alignment.CenterHorizontally
        },
        modifier = Modifier.then(modifier)
    ) {
        state.queueSource?.let { source ->
            val label = when (source) {
                is QueueSource.Album -> stringResource(Res.string.from_album, source.title)
                is QueueSource.Playlist -> stringResource(Res.string.from_playlist, source.title)
                is QueueSource.Single -> stringResource(Res.string.song_radio)
                QueueSource.Custom -> stringResource(Res.string.custom_queue)
            }
            Surface(
                shape = AppShapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = if (compact) 10.dp else 14.dp),
            ) {
                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .widthIn(max = if (compact) 220.dp else 320.dp)
                        .basicMarquee(),
                )
            }
        }

        Text(
            text = song.title,
            style = if (compact) MaterialTheme.typography.songTitle else MaterialTheme.typography.nowPlayingTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
        )

        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (textAlign == TextAlign.Start) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            song.artists.forEachIndexed { i, artist ->
                val hasId = artist.id != null
                Text(
                    text = artist.name,
                    style = if (compact) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (hasId) {
                        Modifier
                            .clip(AppShapes.small)
                            .clickable {
                                onCollapse?.invoke()
                                onNavigate?.invoke(Route.Artist(artist.id!!))
                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    } else {
                        Modifier.padding(horizontal = 4.dp)
                    },
                )
                if (i < song.artists.size - 1) {
                    Text(
                        text = " · ",
                        style = if (compact) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}
