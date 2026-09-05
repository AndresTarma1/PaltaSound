package example.nucleus.ui.components.song

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import example.nucleus.download.DownloadState
import example.nucleus.utils.LocalSnackbarHostState
import example.nucleus.utils.LocalSnackbarScope
import example.nucleus.viewmodels.LibraryPlaylistsViewModel
import com.metrolist.innertube.models.SongItem
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadIndicator(
    state: DownloadState?,
    modifier: Modifier = Modifier
) {
    val indicatorColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier.size(18.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is DownloadState.Queued -> {
                // Pulso sutil para indicar espera
                LoadingIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is DownloadState.Downloading -> {
                CircularWavyProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = indicatorColor,
                    trackColor = trackColor,
                )
            }
            is DownloadState.Completed -> {
                Icon(
                    imageVector = Icons.Rounded.ArrowCircleDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = indicatorColor
                )
            }
            is DownloadState.Failed -> {
                Icon(
                    imageVector = Icons.Rounded.PriorityHigh, // Más minimalista que el error estándar
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            else -> Unit
        }
    }
}

@Composable
fun rememberContextMenuPositionProvider(clickOffset: DpOffset): PopupPositionProvider {
    val density = LocalDensity.current
    val clickXPx = with(density) { clickOffset.x.toPx() }
    val clickYPx = with(density) { clickOffset.y.toPx() }
    val marginPx = with(density) { 6.dp.roundToPx() }

    return remember(clickXPx, clickYPx, marginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                var x = anchorBounds.left + clickXPx.roundToInt()
                var y = anchorBounds.top + clickYPx.roundToInt()

                if (x + popupContentSize.width > windowSize.width - marginPx) {
                    x = windowSize.width - popupContentSize.width - marginPx
                }
                if (y + popupContentSize.height > windowSize.height - marginPx) {
                    y -= popupContentSize.height
                }

                x = x.coerceAtLeast(marginPx)
                y = y.coerceAtLeast(marginPx)

                return IntOffset(x, y)
            }
        }
    }
}





@Composable
fun AddToPlaylistDialog(
    song: SongItem,
    playlistsViewModel: LibraryPlaylistsViewModel,
    onDismiss: () -> Unit
) = AddToPlaylistDialog(listOf(song), playlistsViewModel, onDismiss)

@Composable
fun AddToPlaylistDialog(
    songs: List<SongItem>,
    playlistsViewModel: LibraryPlaylistsViewModel,
    onDismiss: () -> Unit
) {
    // Todas las playlists guardadas (locales + guardadas de YouTube) — añadir a una playlist de
    // YouTube envía el cambio a la playlist real de la cuenta (PlaylistRepository.addSongToPlaylist).
    val savedPlaylists by playlistsViewModel.savedPlaylists.collectAsState()
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val scope = LocalSnackbarScope.current
    val addSuffix = if (songs.size > 1) "s" else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        title = {
            Text(
                if (isCreatingNew) stringResource(Res.string.create_new_playlist) else stringResource(Res.string.add_n_to_playlist, songs.size),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            if (isCreatingNew) {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(Res.string.playlist_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (savedPlaylists.isEmpty()) {
                Text(
                    stringResource(Res.string.no_playlists_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp, max = 260.dp)
                ) {
                    items(savedPlaylists) { playlist ->
                        var isHovered by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if(isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .onHover{ isHovered = it}
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    songs.forEach { playlistsViewModel.addSongToLocalPlaylist(playlist.id, it) }
                                    scope.launch {
                                        val addedMsg = getString(Res.string.added_to_playlist, addSuffix, playlist.title)
                                        snackbar.showSnackbar(addedMsg)
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = playlist.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreatingNew) {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            playlistsViewModel.createLocalPlaylist(newPlaylistName.trim(), songs)
                            scope.launch {
                                val createdMsg = getString(Res.string.playlist_created_with, newPlaylistName.trim(), songs.size)
                                snackbar.showSnackbar(createdMsg)
                            }
                            onDismiss()
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text(stringResource(Res.string.create_and_add))
                }
            } else {
                Button(
                    onClick = { isCreatingNew = true }
                ) {
                    Text(stringResource(Res.string.new_playlist))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isCreatingNew) {
                        isCreatingNew = false
                        newPlaylistName = ""
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (isCreatingNew) stringResource(Res.string.back) else stringResource(Res.string.btn_cancel))
            }
        }
    )
}
