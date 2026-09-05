package example.nucleus.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.models.MediaMetadata
import example.nucleus.navigation.Route
import example.nucleus.ui.components.formatPlayerTimeValue
import example.nucleus.viewmodels.PlayerUiState
import example.nucleus.viewmodels.QueueSource
import example.nucleus.ui.components.dialogs.ArtistsModal
import example.nucleus.ui.themes.LocalMiniPlayerInset
import com.metrolist.innertube.models.MediaInfo
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

@Composable
fun SongInfoContent(
    song: MediaMetadata,
    state: PlayerUiState,
    mediaInfo: MediaInfo?,
    onNavigate: ((Route) -> Unit)?,
) {
    val scrollState = rememberScrollState()

    val unknownArtistLabel = stringResource(Res.string.unknown_artist)
    val artistName = remember(song.artists, mediaInfo, unknownArtistLabel) {
        if (song.artists.isNotEmpty()) {
            song.artists.joinToString(", ") { it.name }
        } else {
            mediaInfo?.author ?: unknownArtistLabel
        }
    }

    var openSheetModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ArtistAlbumHeader(
            artistName = artistName,
            albumTitle = song.album?.title,
            onClick = { openSheetModal = true }
        )

        StatusChipsRow(
            liked = song.liked,
            isDownloaded = song.isDownloaded,
            isExplicit = song.explicit
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (song.duration > 0) {
                InfoRow(
                    icon = Icons.Rounded.Timer,
                    title = stringResource(Res.string.info_duration),
                    subtitle = formatPlayerTimeValue(song.duration * 1000L)
                )
            }

            state.queueSource?.let { source ->
                val originLabel = when (source) {
                    is QueueSource.Album -> stringResource(Res.string.from_album, source.title)
                    is QueueSource.Playlist -> stringResource(Res.string.from_playlist, source.title)
                    is QueueSource.Single -> stringResource(Res.string.song_radio)
                    QueueSource.Custom -> stringResource(Res.string.custom_queue)
                }
                InfoRow(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = stringResource(Res.string.playing_from),
                    subtitle = originLabel
                )
            }

            mediaInfo?.uploadDate?.let { date ->
                InfoRow(
                    icon = Icons.Rounded.CalendarToday,
                    title = stringResource(Res.string.upload_date),
                    subtitle = date
                )
            }

            mediaInfo?.viewCount?.let { views ->
                if (views > 0) {
                    InfoRow(
                        icon = Icons.Rounded.BarChart,
                        title = stringResource(Res.string.play_count),
                        subtitle = formatViewCount(views)
                    )
                }
            }
        }
        if (openSheetModal) {
            ArtistsModal(
                artists = song.artists,
                onClickArtist = { onNavigate?.invoke(Route.Artist(it)) },
                onDismiss = { openSheetModal = false }
            )
        }
    }
}

@Composable
internal fun ArtistAlbumHeader(
    artistName: String,
    albumTitle: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!albumTitle.isNullOrBlank()) {
                    Text(
                        text = albumTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StatusChipsRow(
    liked: Boolean,
    isDownloaded: Boolean,
    isExplicit: Boolean
) {
    if (!liked && !isDownloaded && !isExplicit) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (liked) {
            AssistChip(
                onClick = { },
                label = { Text(stringResource(Res.string.info_liked)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
        if (isDownloaded) {
            AssistChip(
                onClick = { },
                label = { Text(stringResource(Res.string.info_downloaded)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        if (isExplicit) {
            AssistChip(
                onClick = { },
                label = { Text(stringResource(Res.string.info_explicit)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Explicit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@Composable
internal fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun formatViewCount(views: Int): String {
    return when {
        views >= 1_000_000 -> "${(views / 100_000) / 10.0}M"
        views >= 1_000 -> "${(views / 100) / 10.0}K"
        else -> views.toString()
    }
}
