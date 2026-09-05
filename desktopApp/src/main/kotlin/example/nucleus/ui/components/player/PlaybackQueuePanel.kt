@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.components.ExpressiveEmptyState
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.song.AddToPlaylistDialog
import example.nucleus.ui.themes.AppShapes
import example.nucleus.utils.*
import example.nucleus.viewmodels.PlayerUiState
import example.nucleus.viewmodels.QueueSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlaybackQueuePanel(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    showCloseButton: Boolean = true,
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

    // Auto-scroll — instantáneo si salto grande
    var previousIndex by remember { mutableStateOf(state.currentIndex) }
    LaunchedEffect(state.currentIndex, state.isShuffled) {
        if (state.queue.isNotEmpty() && state.currentIndex in state.queue.indices) {
            val distance = kotlin.math.abs(state.currentIndex - previousIndex)
            previousIndex = state.currentIndex
            val target = (state.currentIndex - 1).coerceAtLeast(0)
            if (distance > 3) listState.scrollToItem(target)
            else {
                delay(120.milliseconds)
                listState.animateScrollToItem(target)
            }
        }
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            QueueHeader(
                state = state,
                showCloseButton = showCloseButton,
                onDismiss = onDismiss,
                onAddToPlaylist = { showAddQueueDialog = true },
                onDownloadAll = {
                    downloadViewModel.downloadAll(queueSongs)
                    scope.launch {
                        snackbar.showSnackbar(getString(Res.string.queue_download_added, queueSongs.size))
                    }
                },
                onSaveAsPlaylist = { showSaveQueueDialog = true },
                onToggleLock = {
                    coroutineScope.launch { preferencesRepo.setQueueLocked(!queueLocked) }
                },
                queueLocked = queueLocked,
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.5.dp,
            )

            Box(Modifier.fillMaxSize()) {
                if (state.queue.isEmpty()) {
                    EmptyQueuePlaceholder()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 10.dp,
                            bottom = 12.dp + bottomInset
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = state.queue,
                            key = { _, song -> song.id }
                        ) { index, queueSong ->
                            val isCurrent = index == state.currentIndex
                            ReorderableItem(reorderableState, key = queueSong.id) { isDragging ->
                                val dragModifier = if (!queueLocked) Modifier.draggableHandle() else Modifier
                                QueueItem(
                                    song = queueSong,
                                    isCurrent = isCurrent,
                                    isPlaying = state.playbackState == example.nucleus.player.PlaybackState.PLAYING,
                                    queueLocked = queueLocked,
                                    isDragging = isDragging,
                                    dragModifier = dragModifier,
                                    onClick = { playerViewModel.playAtIndex(index) },
                                    onRemove = { playerViewModel.removeFromQueue(index) },
                                )
                            }
                        }
                    }
                }

                AppVerticalScrollbar(
                    state = listState,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }

    if (showAddQueueDialog) {
        AddToPlaylistDialog(
            songs = queueSongs,
            playlistsViewModel = playlistsViewModel,
            onDismiss = { showAddQueueDialog = false }
        )
    }

    if (showSaveQueueDialog) {
        AlertDialog(
            onDismissRequest = { showSaveQueueDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = { Text(stringResource(Res.string.save_queue_title)) },
            text = {
                OutlinedTextField(
                    value = queuePlaylistName,
                    onValueChange = { queuePlaylistName = it },
                    label = { Text(stringResource(Res.string.playlist_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = queuePlaylistName.isNotBlank(),
                    onClick = {
                        playlistsViewModel.createLocalPlaylist(queuePlaylistName.trim(), queueSongs)
                        scope.launch { snackbar.showSnackbar(getString(Res.string.queue_saved_as, queuePlaylistName.trim())) }
                        showSaveQueueDialog = false
                    }
                ) { Text(stringResource(Res.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveQueueDialog = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }
}

@Composable
private fun QueueHeader(
    state: PlayerUiState,
    showCloseButton: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownloadAll: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    onToggleLock: () -> Unit,
    queueLocked: Boolean,
) {
    val totalDurationSeconds = remember(state.queue) {
        state.queue.sumOf { it.duration.toLong() }
    }
    val formattedDuration = remember(totalDurationSeconds) {
        formatQueueDuration(totalDurationSeconds)
    }

    val sourceLabel = when (val source = state.queueSource) {
        is QueueSource.Album -> stringResource(Res.string.from_album, source.title)
        is QueueSource.Playlist -> stringResource(Res.string.from_playlist, source.title)
        is QueueSource.Single -> null
        QueueSource.Custom -> null
        null -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.queue_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.queue_songs_count, state.queue.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    if (formattedDuration.isNotEmpty()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = formattedDuration,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Barra de acciones rápidas
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Bloquear/Desbloquear reordenación
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .size(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = if (queueLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = stringResource(if (queueLocked) Res.string.unlock_queue else Res.string.lock_queue),
                        modifier = Modifier.size(18.dp),
                        tint = if (queueLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Guardar como playlist
                IconButton(
                    onClick = onSaveAsPlaylist,
                    enabled = state.queue.isNotEmpty(),
                    modifier = Modifier
                        .size(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = stringResource(Res.string.save_as_playlist),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Añadir a playlist
                IconButton(
                    onClick = onAddToPlaylist,
                    enabled = state.queue.isNotEmpty(),
                    modifier = Modifier
                        .size(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = stringResource(Res.string.add_to_playlist),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Descargar todo
                IconButton(
                    onClick = onDownloadAll,
                    enabled = state.queue.isNotEmpty(),
                    modifier = Modifier
                        .size(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(Res.string.download_queue),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showCloseButton) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.close_queue),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Chip sutil del origen si está presente
        if (sourceLabel != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private fun formatQueueDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours} h ${minutes} min"
    } else {
        "${minutes} min"
    }
}

@Composable
private fun EmptyQueuePlaceholder() {
    ExpressiveEmptyState(
        icon = Icons.AutoMirrored.Filled.QueueMusic,
        title = stringResource(Res.string.queue_empty),
        subtitle = stringResource(Res.string.queue_empty_hint),
    )
}
