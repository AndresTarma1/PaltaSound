package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.sp
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
import example.nucleus.ui.themes.mediaItemTitle
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
    var showMenu by remember { mutableStateOf(false) }

    val itemShape = RoundedCornerShape(12.dp)
    val colorScheme = MaterialTheme.colorScheme

    val containerBg = when {
        isDragging -> colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
        isCurrent -> colorScheme.primaryContainer.copy(alpha = 0.28f)
        isHovered -> colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val itemBorder = when {
        isDragging -> BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f))
        isCurrent -> BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
        isHovered -> BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.25f))
        else -> null
    }

    Surface(
        color = containerBg,
        shape = itemShape,
        border = itemBorder,
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
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
            modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Franja de acento de reproducción activa
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(26.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary),
                )
            }

            // Indicador de arrastre: espacio reservado, solo visible en hover o arrastrando
            if (!queueLocked) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(40.dp)
                        .then(dragModifier)
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = null,
                        tint = if (isDragging) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .alpha(if (isHovered || isDragging) 1f else 0.15f),
                    )
                }
            }

            // Portada de la canción con indicador de reproducción / hover
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                MusicPlayerImage(
                    url = song.thumbnailUrl,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(10.dp),
                    isLowRes = true,
                    placeholderType = PlaceholderType.SONG,
                    iconSize = 20.dp,
                    contentScale = ContentScale.Crop,
                )

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(10.dp)),
                    ) {
                        AnimatedEqualizer(
                            isPlaying = isPlaying,
                            modifier = Modifier.size(18.dp).align(Alignment.Center),
                        )
                    }
                } else if (isHovered && !isDragging) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(10.dp)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(Res.string.play_item),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp).align(Alignment.Center),
                        )
                    }
                }
            }

            // Información: Título y Artistas
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = song.title,
                    style = if (isCurrent) {
                        MaterialTheme.typography.titleMediumEmphasized.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.15).sp,
                        )
                    } else {
                        MaterialTheme.typography.mediaItemTitle.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.05).sp,
                        )
                    },
                    color = if (isCurrent) colorScheme.primary else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artists.joinToString(", ") { it.name }.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.05.sp,
                    ),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Estado de descarga
            DownloadIndicator(state = downloadState)

            // Duración + botón de eliminar en hover
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (song.duration > 0) {
                    Text(
                        text = formatPlayerTimeValue(song.duration * 1000L),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum",
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 0.1.sp,
                        ),
                        color = if (isCurrent) colorScheme.primary.copy(alpha = 0.9f) else colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }

                Box(
                    modifier = Modifier.size(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isHovered && !isDragging) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(28.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = colorScheme.errorContainer.copy(alpha = 0.35f),
                                contentColor = colorScheme.error,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistRemove,
                                contentDescription = stringResource(Res.string.remove_from_queue),
                                modifier = Modifier.size(16.dp),
                            )
                        }
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
