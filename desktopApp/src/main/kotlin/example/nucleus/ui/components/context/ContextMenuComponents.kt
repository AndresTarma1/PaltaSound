package example.nucleus.ui.components.context

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberCursorPositionProvider
import example.nucleus.models.toMediaMetadata
import example.nucleus.ui.components.song.AddToPlaylistDialog
import example.nucleus.ui.helpers.rememberSongDownloadState
import example.nucleus.ui.helpers.rememberSongLikedState
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalPlaylistsViewModel
import example.nucleus.utils.LocalSnackbarHostState
import example.nucleus.utils.LocalSnackbarScope
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.launch
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Wrapper compartido: posiciona un context menu en la posición del cursor.
 * Evita repetir el boilerplate de Popup/PopupProperties en cada menú.
 */
@Composable
private fun ContextMenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!expanded) return

    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = rememberCursorPositionProvider(),
        properties = PopupProperties(focusable = true),
    ) {
        content()
    }
}

/** Envuelve una acción opcional para que, además de ejecutarse, cierre el menú. */
private fun (() -> Unit)?.withDismiss(onDismiss: () -> Unit): (() -> Unit)? =
    this?.let { action -> { action(); onDismiss() } }

@Composable
fun SongContextMenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    song: SongItem,
    showQueueActions: Boolean = true,
    onRemoveFromLibrary: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current
    val playlistsViewModel = LocalPlaylistsViewModel.current
    val liked = rememberSongLikedState(song.id, playerViewModel)
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)
    val snackbar = LocalSnackbarHostState.current
    val scope = LocalSnackbarScope.current

    // getString es suspend: la resolución del string debe pasar por scope.launch.
    val showSnackbar: (StringResource, List<Any>) -> Unit = { resource, args ->
        scope.launch {
            snackbar.showSnackbar(getString(resource, *args.toTypedArray()))
        }
    }

    var showPlaylistDialog by remember { mutableStateOf(false) }

    ContextMenuPopup(expanded = expanded, onDismiss = onDismiss) {
        SongContextMenuContent(
            song = song,
            liked = liked,
            downloadState = downloadState,
            showQueueActions = showQueueActions,
            showRemoveFromLibrary = onRemoveFromLibrary != null,
            showRemoveFromPlaylist = onRemoveFromPlaylist != null,
            onAction = { action ->
                when (action) {
                    SongMenuAction.StartRadio -> {
                        val endpoint = song.endpoint ?: WatchEndpoint(videoId = song.id)
                        playerViewModel.playEndpoint(endpoint, previewSong = song.toMediaMetadata())
                        onDismiss()
                    }
                    SongMenuAction.ToggleLike -> {
                        playerViewModel.toggleLikeForSong(song)
                        onDismiss()
                    }
                    SongMenuAction.Download -> {
                        downloadViewModel.downloadSong(song)
                        showSnackbar(Res.string.snackbar_added_to_downloads, listOf(song.title))
                        onDismiss()
                    }
                    SongMenuAction.CancelDownload -> {
                        downloadViewModel.cancelDownload(song.id)
                        showSnackbar(Res.string.snackbar_download_cancelled, emptyList())
                        onDismiss()
                    }
                    SongMenuAction.RemoveDownload -> {
                        downloadViewModel.removeDownload(song.id)
                        showSnackbar(Res.string.snackbar_download_removed, emptyList())
                        onDismiss()
                    }
                    SongMenuAction.PlayNext -> {
                        playerViewModel.playNextResolved(song)
                        showSnackbar(Res.string.snackbar_play_next, listOf(song.title))
                        onDismiss()
                    }
                    SongMenuAction.AddToQueue -> {
                        playerViewModel.addToQueueResolved(song)
                        showSnackbar(Res.string.snackbar_added_to_queue, listOf(song.title))
                        onDismiss()
                    }
                    SongMenuAction.AddToPlaylist -> {
                        showPlaylistDialog = true
                        onDismiss()
                    }
                    SongMenuAction.RemoveFromLibrary -> {
                        onRemoveFromLibrary?.invoke()
                        onDismiss()
                    }
                    SongMenuAction.RemoveFromPlaylist -> {
                        onRemoveFromPlaylist?.invoke()
                        showSnackbar(Res.string.snackbar_removed_from_playlist, listOf(song.title))
                        onDismiss()
                    }
                }
            }
        )
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            song = song,
            playlistsViewModel = playlistsViewModel,
            onDismiss = { showPlaylistDialog = false }
        )
    }
}

@Composable
fun CollectionContextMenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String,
    isPlaylist: Boolean,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    ContextMenuPopup(expanded = expanded, onDismiss = onDismiss) {
        CollectionContextMenuContent(
            title = title,
            isPlaylist = isPlaylist,
            onOpen = { onOpen(); onDismiss() },
            onPlay = onPlay.withDismiss(onDismiss),
            onShuffle = onShuffle.withDismiss(onDismiss),
            onRemoveFromLibrary = onRemoveFromLibrary.withDismiss(onDismiss),
            onExport = onExport?.withDismiss(onDismiss),
        )
    }
}