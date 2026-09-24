package example.nucleus.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import example.nucleus.ui.components.BoxForContainerContextMenuItem
import example.nucleus.ui.components.dialogs.ConfirmDestructiveActionDialog
import example.nucleus.ui.components.song.DownloadIndicator
import example.nucleus.ui.components.song.AddToPlaylistDialog
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.context.SongContextMenuPopup
import example.nucleus.download.DownloadState
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.ui.helpers.rememberSongDownloadState
import example.nucleus.ui.helpers.rememberSongLikedState
import example.nucleus.ui.screens.shared.formatDuration
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.mediaItemTitle
import example.nucleus.utils.LocalSnackbarHostState
import example.nucleus.utils.LocalSnackbarScope
import com.metrolist.innertube.models.SongItem
import example.nucleus.utils.LocalPlaylistsViewModel
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.modifier.onHover

val ListItemHeight = 72.dp
val ListThumbnailSize = 56.dp
val ThumbnailCornerRadius = 12.dp

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    isAvailable: Boolean = true,
) {
    val rowShape = AppShapes.medium
    val rowModifier = modifier
        .height(ListItemHeight)
        .padding(horizontal = 8.dp)
        .clip(rowShape)
        .then(
            when {
                isActive && isSelected == true -> Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                )
                isActive -> Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                isSelected == true -> Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                )
                else -> Modifier
            },
        )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier,
    ) {
        Box(
            modifier = Modifier.padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            thumbnailContent()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.mediaItemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isAvailable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                },
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    subtitle()
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            trailingContent()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MultiSongSelectionBar(
    selectedSongs: List<SongItem>,
    allSongIds: List<String> = emptyList(),
    isLocalPlaylist: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: (() -> Unit)? = null,
    onRemoveFromPlaylist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val playlistsViewModel = LocalPlaylistsViewModel.current
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val scope = LocalSnackbarScope.current
    val allSelected = allSongIds.isNotEmpty() && selectedSongs.size == allSongIds.size

    if (selectedSongs.isEmpty()) return

    if (showRemoveConfirm) {
        ConfirmDestructiveActionDialog(
            title = stringResource(Res.string.confirm_delete_songs_title),
            message = stringResource(Res.string.confirm_delete_songs_message, selectedSongs.size),
            confirmText = stringResource(Res.string.confirm_delete_songs_btn),
            onConfirm = {
                selectedSongs.forEach { onRemoveFromPlaylist?.invoke(it.id) }
                scope.launch {
                    snackbar.showSnackbar(getString(Res.string.songs_removed_from_playlist, selectedSongs.size))
                }
                onClearSelection()
            },
            onDismiss = { showRemoveConfirm = false }
        )
    }

    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        shape = AppShapes.xLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(Res.string.selected_count, selectedSongs.size),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            if (onSelectAll != null && allSongIds.isNotEmpty()) {
                if (allSelected) {
                    FilledTonalIconButton(
                        onClick = onClearSelection,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Default.Clear, stringResource(Res.string.cd_deselect_all))
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = onSelectAll,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Default.AddBox, stringResource(Res.string.cd_select_all))
                    }
                }
            }
            FilledTonalIconButton(
                onClick = { showPlaylistDialog = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(Res.string.cd_add_to_playlist))
            }
            FilledTonalIconButton(
                onClick = { downloadViewModel.downloadAll(selectedSongs) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Default.Download, stringResource(Res.string.cd_download))
            }
            if (isLocalPlaylist && onRemoveFromPlaylist != null) {
                FilledTonalIconButton(
                    onClick = { showRemoveConfirm = true },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Default.Delete, stringResource(Res.string.cd_remove_from_playlist))
                }
            }
            FilledTonalIconButton(
                onClick = onClearSelection,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Default.Close, stringResource(Res.string.btn_cancel))
            }
        }
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            songs = selectedSongs,
            playlistsViewModel = playlistsViewModel,
            onDismiss = {
                showPlaylistDialog = false
                onClearSelection()
            }
        )
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
) = ListItem(
    title = title,
    subtitle = {
        badges()

        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SongListItem(
    albumIndex: Int? = null,
    song: SongItem,
    onPlay: () -> Unit,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onSelectionChange: ((Boolean) -> Unit)? = null,
    isLocalPlaylist: Boolean = false,
    onRemoveFromPlaylist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)
    val showDownloadIndicator = downloadState != null && downloadState !is DownloadState.Cancelled
    val playerViewModel = LocalPlayerViewModel.current
    val liked = rememberSongLikedState(song.id, playerViewModel)

    var isHovered by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        BoxForContainerContextMenuItem(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small),
            onHoverChange = { isHovered = it },
            onMenuAction = {
                showContextMenu = true
            }
        ) { menuButtonModifier, openMenuFromButton ->
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = selectionMode) { onSelectionChange?.invoke(!isSelected) }
            ) {
                ListItem(
                    title = song.title,
                    subtitle = song.artists.joinToString(", ") { it.name }.ifEmpty { stringResource(Res.string.unknown_artist) },
                    badges = {
                        if (showDownloadIndicator) {
                            DownloadIndicator(
                                state = downloadState,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }


                        if (song.explicit) {
                            Icon(
                                Icons.Default.Explicit, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    },
                    thumbnailContent = {
                        Box(
                            modifier = Modifier.width(ListThumbnailSize)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onPlay() },
                            contentAlignment = Alignment.Center
                        ) {

                            if (albumIndex != null) {
                                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                    if (isHovered) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            text = albumIndex.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(ListThumbnailSize)
                                        .onHover{ isHovered = it }
                                ) {
                                    MusicPlayerImage(
                                        url = song.thumbnail,
                                        contentDescription = song.title,
                                        modifier = Modifier.matchParentSize(),
                                        contentScale = ContentScale.Crop,
                                        isLowRes = true,
                                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                                        placeholderType = PlaceholderType.SONG,
                                        iconSize = 24.dp,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                Color.Black.copy(alpha = if (isHovered) 0.4f else 0f),
                                                shape = RoundedCornerShape(ThumbnailCornerRadius)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = stringResource(Res.string.play),
                                            tint = Color.White.copy(alpha = if (isHovered) 1f else 0f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isHovered && !selectionMode) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(onClick = { playerViewModel.toggleLikeForSong(song) }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                            null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = openMenuFromButton, modifier = menuButtonModifier.size(36.dp)) {
                                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                                if (selectionMode || isHovered) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onSelectionChange?.invoke(it) }
                                    )
                                } else {
                                    Text(
                                        text = song.duration?.takeIf { it > 0 }?.let { formatDuration(it) }.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(44.dp)
                                    )
                                }

                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isSelected = isSelected
                )
            }
        }

        SongContextMenuPopup(
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            song = song,
            onRemoveFromPlaylist = if (isLocalPlaylist) { { onRemoveFromPlaylist?.invoke(song.id) } } else null,
        )
    }
}

