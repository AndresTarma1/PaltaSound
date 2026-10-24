@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.generated.resources.*
import example.nucleus.generated.resources.Res
import example.nucleus.models.MediaMetadata
import example.nucleus.navigation.Route
import example.nucleus.ui.themes.AppShapes
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.viewmodels.PlayerUiState
import example.nucleus.viewmodels.QueueSource
import org.jetbrains.compose.resources.stringResource

/**
 * Detalles y metadatos de la canción con tipografía destacada y enlaces a artistas y álbum.
 */
@Composable
internal fun NowPlayingSongDetails(
    state: PlayerUiState,
    song: MediaMetadata,
    textAlign: TextAlign,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val playerViewModel = LocalPlayerViewModel.current
    Column(
        horizontalAlignment = when (textAlign) {
            TextAlign.Start -> Alignment.Start
            else -> Alignment.CenterHorizontally
        },
        modifier = modifier,
    ) {
        // Chip de origen / procedencia
        state.queueSource?.let { source ->
            val label = when (source) {
                is QueueSource.Album -> stringResource(Res.string.from_album, source.title)
                is QueueSource.Playlist -> stringResource(Res.string.from_playlist, source.title)
                is QueueSource.Single -> stringResource(Res.string.song_radio)
                QueueSource.Custom -> stringResource(Res.string.custom_queue)
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = if (compact) 8.dp else 12.dp),
            ) {
                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .widthIn(max = if (compact) 200.dp else 320.dp)
                        .basicMarquee(),
                )
            }
        }

        // Título de la pista + like (estilo referencia: título centrado con corazón al final)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (textAlign == TextAlign.Start) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = song.title,
                style = if (compact) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .basicMarquee(),
            )
            LikeToggle(
                liked = song.liked,
                onToggle = { playerViewModel.toggleLike() },
            )
        }

        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))

        // Artistas interactivos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (textAlign == TextAlign.Start) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            song.artists.forEachIndexed { i, artist ->
                val hasId = artist.id != null
                Text(
                    text = artist.name,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
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
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }

        // Álbum si está presente
        song.album?.let { album ->
            if (!compact) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier
                        .clip(AppShapes.small)
                        .clickable {
                            onCollapse?.invoke()
                            onNavigate?.invoke(Route.Album(album.id))
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}
