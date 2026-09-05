package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.models.MediaMetadata
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.formatPlayerTimeValue
import example.nucleus.ui.components.song.DownloadIndicator
import example.nucleus.ui.components.skeletons.AnimatedEqualizer
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.ui.components.context.SongContextMenuPopup
import example.nucleus.ui.helpers.rememberSongDownloadState
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QueueItem(
    song: MediaMetadata,
    isCurrent: Boolean,
    isPlaying: Boolean = false,
    queueLocked: Boolean = false,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)

    var isHovered by remember { mutableStateOf(false) }

    val itemShape = RoundedCornerShape(12.dp)
    val currentBg = when {
        isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    val itemBorderModifier = if (isCurrent && !isDragging) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), itemShape)
    } else Modifier

    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = currentBg,
        shape = itemShape,
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
            .then(itemBorderModifier)
            .clickable(onClick = onClick)
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    showMenu = true
                }
            }
            .onHover { isHovered = it }
            .pointerHoverIcon(if (isDragging) PointerIcon.Crosshair else PointerIcon.Hand),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Indicador de arrastre para reordenar (visible cuando la cola no está bloqueada)
            if (!queueLocked) {
                Box(
                    modifier = Modifier
                        .then(dragModifier)
                        .padding(end = 2.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = null,
                        tint = if (isHovered || isDragging) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Portada de la canción con indicador de reproducción / hover
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                MusicPlayerImage(
                    url = song.thumbnailUrl,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp),
                    isLowRes = true,
                    placeholderType = PlaceholderType.SONG,
                    iconSize = 20.dp,
                    contentScale = ContentScale.Crop,
                )

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
                    ) {
                        AnimatedEqualizer(
                            isPlaying = isPlaying,
                            modifier = Modifier.size(18.dp).align(Alignment.Center)
                        )
                    }
                } else if (isHovered && !isDragging) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(Res.string.play_item),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp).align(Alignment.Center)
                        )
                    }
                }
            }

            // Información: Título y Artistas
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = song.artists.joinToString(", ") { it.name }.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Estado de descarga
            DownloadIndicator(state = downloadState)

            // Duración o botón de eliminar al hacer hover
            Box(
                modifier = Modifier.widthIn(min = 36.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isHovered && !isDragging) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistRemove,
                            contentDescription = stringResource(Res.string.remove_from_queue),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    if (song.duration > 0) {
                        Text(
                            text = formatPlayerTimeValue(song.duration * 1000L),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        SongContextMenuPopup(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            song = song.toSongItem(),
        )
    }
}

internal fun MediaMetadata.toSongItem(): SongItem = SongItem(
    id = id,
    title = title,
    artists = artists.map { Artist(name = it.name, id = it.id) },
    album = album?.let { Album(name = it.title, id = it.id) },
    duration = duration.takeIf { it > 0 },
    thumbnail = thumbnailUrl.orEmpty(),
    explicit = explicit
)
