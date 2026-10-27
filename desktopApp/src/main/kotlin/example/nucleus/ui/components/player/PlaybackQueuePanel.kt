@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.models.MediaMetadata
import example.nucleus.player.PlaybackState
import example.nucleus.ui.components.ExpressiveEmptyState
import example.nucleus.ui.components.artwork.rememberArtworkColors
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.skeletons.AnimatedEqualizer
import example.nucleus.ui.components.song.AddToPlaylistDialog
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.expressiveColorDuration
import example.nucleus.ui.themes.expressiveFadeTween
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

    // Hero "sonando ahora" + tinte ambiental de la carátula (dirección expresiva A+B).
    val currentSong = state.queue.getOrNull(state.currentIndex)
    val isPlaying = state.playbackState == PlaybackState.PLAYING
    val palette = rememberArtworkColors(currentSong?.thumbnailUrl)
    val animatedTint by animateColorAsState(
        targetValue = palette.vibrant,
        animationSpec = tween(durationMillis = expressiveColorDuration),
        label = "queueTint",
    )

    var previousIndex by remember { mutableStateOf(state.currentIndex) }
    var firstPositioning by remember { mutableStateOf(true) }
    LaunchedEffect(state.currentIndex, state.isShuffled) {
        if (state.queue.isNotEmpty() && state.currentIndex in state.queue.indices) {
            val distance = kotlin.math.abs(state.currentIndex - previousIndex)
            previousIndex = state.currentIndex
            val target = (state.currentIndex - 1).coerceAtLeast(0)
            if (firstPositioning) {
                firstPositioning = false
                listState.scrollToItem(target)
            } else {
                val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
                if (state.currentIndex !in visible) {
                    if (distance > 3) listState.scrollToItem(target)
                    else {
                        delay(120.milliseconds)
                        listState.animateScrollToItem(target)
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Tinte ambiental: degradado sutil del color vibrante de la carátula actual.
            // Funciona sobre contenedor transparente (acoplado) y sólido (overlay).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                animatedTint.copy(alpha = 0.14f),
                                Color.Transparent,
                            )
                        )
                    )
            )
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
                    currentSong = currentSong,
                    isPlaying = isPlaying,
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
                                top = 4.dp,
                                bottom = 12.dp + bottomInset
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(
                                items = state.queue,
                                key = { index, song ->
                                    "q${state.queueSession.order.getOrElse(index) { index }}:${song.id}"
                                }
                            ) { index, queueSong ->
                                val isCurrent = index == state.currentIndex
                                ReorderableItem(
                                    reorderableState,
                                    key = "q${state.queueSession.order.getOrElse(index) { index }}:${queueSong.id}",
                                ) { isDragging ->
                                    val dragModifier = if (!queueLocked) Modifier.draggableHandle() else Modifier
                                    QueueItem(
                                        song = queueSong,
                                        isCurrent = isCurrent,
                                        isPlaying = state.playbackState == PlaybackState.PLAYING,
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
    currentSong: MediaMetadata?,
    isPlaying: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.now_playing),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                QueueActionsMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    queueLocked = queueLocked,
                    queueEmpty = state.queue.isEmpty(),
                    onToggleLock = onToggleLock,
                    onSaveAsPlaylist = onSaveAsPlaylist,
                    onAddToPlaylist = onAddToPlaylist,
                    onDownloadAll = onDownloadAll,
                )
            }

            if (showCloseButton) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.close_queue),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                MusicPlayerImage(
                    url = currentSong?.thumbnailUrl,
                    contentDescription = currentSong?.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    placeholderType = PlaceholderType.SONG,
                    iconSize = 28.dp,
                    contentScale = ContentScale.Crop,
                )
                if (currentSong != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedEqualizer(
                            isPlaying = isPlaying,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = currentSong?.id,
                transitionSpec = {
                    fadeIn(expressiveFadeTween()) togetherWith fadeOut(expressiveFadeTween())
                },
                label = "queueHero",
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = currentSong?.title ?: stringResource(Res.string.queue_empty),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.15).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = currentSong?.artists?.joinToString(", ") { it.name }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = stringResource(Res.string.queue_up_next),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun QueueActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    queueLocked: Boolean,
    queueEmpty: Boolean,
    onToggleLock: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = AppShapes.large,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        DropdownMenuItem(
            onClick = { onDismiss(); onToggleLock() },
            text = {
                Text(
                    stringResource(if (queueLocked) Res.string.unlock_queue else Res.string.lock_queue),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (queueLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        DropdownMenuItem(
            onClick = { onDismiss(); onSaveAsPlaylist() },
            enabled = !queueEmpty,
            text = { Text(stringResource(Res.string.save_as_playlist), style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenuItem(
            onClick = { onDismiss(); onAddToPlaylist() },
            enabled = !queueEmpty,
            text = { Text(stringResource(Res.string.add_to_playlist), style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenuItem(
            onClick = { onDismiss(); onDownloadAll() },
            enabled = !queueEmpty,
            text = { Text(stringResource(Res.string.download_queue), style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp)) },
        )
    }
}

internal fun formatQueueDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "$hours h $minutes min"
    } else {
        "$minutes min"
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
