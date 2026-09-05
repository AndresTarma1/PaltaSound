@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.models.MediaMetadata
import example.nucleus.generated.resources.*
import example.nucleus.generated.resources.Res
import example.nucleus.ui.components.ExpressiveEmptyState
import example.nucleus.ui.components.context.SongContextMenuPopup
import example.nucleus.ui.components.formatPlayerTimeValue
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.skeletons.AnimatedEqualizer
import example.nucleus.ui.components.song.AddToPlaylistDialog
import example.nucleus.ui.components.song.DownloadIndicator
import example.nucleus.ui.helpers.rememberSongDownloadState
import example.nucleus.ui.themes.AppShapes
import example.nucleus.utils.*
import example.nucleus.viewmodels.PlayerUiState
import example.nucleus.viewmodels.QueueSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.milliseconds

/**
 * Panel de cola de reproducción exclusivo y espacioso para la pantalla Now Playing.
 */
@Composable
fun NowPlayingQueuePanel(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current
    val playlistsViewModel = LocalPlaylistsViewModel.current
    val preferencesRepo = LocalUserPreferences.current
    val coroutineScope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHostState.current
    val scope = LocalSnackbarScope.current

    val listState = rememberLazyListState()
    val queueLocked by preferencesRepo.queueLocked.collectAsState(initial = false)
    val queueSongs = remember(state.queue) { state.queue.map { it.toSongItem() } }

    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var showAddQueueDialog by remember { mutableStateOf(false) }

    val defaultQueueName = stringResource(Res.string.queue_title)
    var queuePlaylistName by remember(state.queueSource, state.queue, defaultQueueName) {
        mutableStateOf(
            when (val source = state.queueSource) {
                is QueueSource.Album -> source.title
                is QueueSource.Playlist -> source.title
                else -> defaultQueueName
            }
        )
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        playerViewModel.moveQueueItem(from.index, to.index)
    }

    // Desplazamiento al elemento en reproducción — instantáneo si el salto es grande para no parecer recarga
    var previousIndex by remember { mutableStateOf(state.currentIndex) }
    LaunchedEffect(state.currentIndex, state.isShuffled) {
        if (state.queue.isNotEmpty() && state.currentIndex in state.queue.indices) {
            val distance = kotlin.math.abs(state.currentIndex - previousIndex)
            previousIndex = state.currentIndex
            val targetScroll = (state.currentIndex - 1).coerceAtLeast(0)
            if (distance > 3) {
                listState.scrollToItem(targetScroll)
            } else {
                delay(120.milliseconds)
                listState.animateScrollToItem(targetScroll)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
    ) {
        NowPlayingQueueHeader(
            state = state,
            queueLocked = queueLocked,
            onToggleLock = {
                coroutineScope.launch { preferencesRepo.setQueueLocked(!queueLocked) }
            },
            onSaveAsPlaylist = { showSaveQueueDialog = true },
            onAddToPlaylist = { showAddQueueDialog = true },
            onDownloadAll = {
                downloadViewModel.downloadAll(queueSongs)
                scope.launch {
                    snackbar.showSnackbar(getString(Res.string.queue_download_added, queueSongs.size))
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (state.queue.isEmpty()) {
                ExpressiveEmptyState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = stringResource(Res.string.queue_empty),
                    subtitle = stringResource(Res.string.queue_empty_hint),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 4.dp,
                        end = 12.dp,
                        top = 4.dp,
                        bottom = 16.dp + bottomInset,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        items = state.queue,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        val isCurrent = index == state.currentIndex

                        ReorderableItem(reorderableState, key = song.id) { isDragging ->
                            val dragModifier = if (!queueLocked) Modifier.draggableHandle() else Modifier
                            NowPlayingQueueRowItem(
                                song = song,
                                index = index,
                                isCurrent = isCurrent,
                                isDragging = isDragging,
                                dragModifier = dragModifier,
                                isLocked = queueLocked,
                                onClick = { playerViewModel.playAtIndex(index) },
                                onRemove = { playerViewModel.removeFromQueue(index) },
                            )
                        }
                    }
                }

                AppVerticalScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 4.dp),
                )
            }
        }
    }

    if (showAddQueueDialog) {
        AddToPlaylistDialog(
            songs = queueSongs,
            playlistsViewModel = playlistsViewModel,
            onDismiss = { showAddQueueDialog = false },
        )
    }

    if (showSaveQueueDialog) {
        AlertDialog(
            onDismissRequest = { showSaveQueueDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = { Text(stringResource(Res.string.save_queue_title), style = MaterialTheme.typography.titleMediumEmphasized) },
            text = {
                OutlinedTextField(
                    value = queuePlaylistName,
                    onValueChange = { queuePlaylistName = it },
                    label = { Text(stringResource(Res.string.playlist_name_label)) },
                    singleLine = true,
                    shape = AppShapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = queuePlaylistName.isNotBlank(),
                    shape = AppShapes.large,
                    onClick = {
                        playlistsViewModel.createLocalPlaylist(queuePlaylistName.trim(), queueSongs)
                        scope.launch { snackbar.showSnackbar(getString(Res.string.queue_saved_as, queuePlaylistName.trim())) }
                        showSaveQueueDialog = false
                    },
                ) { Text(stringResource(Res.string.btn_save)) }
            },
            dismissButton = {
                TextButton(
                    shape = AppShapes.large,
                    onClick = { showSaveQueueDialog = false },
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

/**
 * Cabecera limpia y moderna del panel de cola en Now Playing.
 */
@Composable
private fun NowPlayingQueueHeader(
    state: PlayerUiState,
    queueLocked: Boolean,
    onToggleLock: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownloadAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.queue_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        text = "${state.queue.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            state.queueSource?.let { source ->
                val originLabel = when (source) {
                    is QueueSource.Album -> source.title
                    is QueueSource.Playlist -> source.title
                    is QueueSource.Single -> stringResource(Res.string.song_radio)
                    QueueSource.Custom -> stringResource(Res.string.custom_queue)
                }
                Text(
                    text = originLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .size(36.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    imageVector = if (queueLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = if (queueLocked) stringResource(Res.string.unlock_queue) else stringResource(Res.string.lock_queue),
                    modifier = Modifier.size(18.dp),
                    tint = if (queueLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(36.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.options),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    shape = AppShapes.large,
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp,
                ) {
                    DropdownMenuItem(
                        onClick = { showMenu = false; onAddToPlaylist() },
                        text = { Text(stringResource(Res.string.add_to_playlist), style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, modifier = Modifier.size(18.dp))
                        },
                    )
                    DropdownMenuItem(
                        onClick = { showMenu = false; onSaveAsPlaylist() },
                        text = { Text(stringResource(Res.string.save_as_playlist), style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        },
                    )
                    DropdownMenuItem(
                        onClick = { showMenu = false; onDownloadAll() },
                        text = { Text(stringResource(Res.string.download_queue), style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
    }
}

/**
 * Fila individual con espaciado amplio, carátula redondeada y detalles visuales refinados.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NowPlayingQueueRowItem(
    song: MediaMetadata,
    index: Int,
    isCurrent: Boolean,
    isDragging: Boolean,
    dragModifier: Modifier,
    isLocked: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)
    var isHovered by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val itemShape = RoundedCornerShape(14.dp)
    val colorScheme = MaterialTheme.colorScheme

    val containerColor = when {
        isDragging -> colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
        isCurrent -> colorScheme.primaryContainer.copy(alpha = 0.28f)
        isHovered -> colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
        else -> Color.Transparent
    }

    Surface(
        color = containerColor,
        shape = itemShape,
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
            .then(
                if (isCurrent) Modifier.border(
                    width = 1.dp,
                    color = colorScheme.primary.copy(alpha = 0.4f),
                    shape = itemShape,
                ) else Modifier
            )
            .then(dragModifier)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Indicador de reproducción o número de índice / handle
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .background(colorScheme.primary, CircleShape),
                )
            } else if (!isLocked && isHovered) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(18.dp),
                )
            }

            // Miniatura con carátula
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
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedEqualizer(
                            isPlaying = true,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else if (isHovered && !isDragging) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(Res.string.play_item),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // Metadatos de la canción
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (isCurrent) colorScheme.primary else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val artistText = song.artists.joinToString(", ") { it.name }
                val albumText = song.album?.title
                val subtitleText = when {
                    albumText.isNullOrBlank() -> artistText
                    artistText.isBlank() -> albumText
                    else -> "$artistText · $albumText"
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            DownloadIndicator(state = downloadState)

            // Acciones al pasar el ratón o duración
            if (isHovered && !isDragging) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistRemove,
                        contentDescription = stringResource(Res.string.remove_from_queue),
                        tint = colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                if (song.duration > 0) {
                    Text(
                        text = formatPlayerTimeValue(song.duration * 1000L),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
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
