package example.nucleus.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import example.nucleus.navigation.Route
import example.nucleus.ui.components.ItemContentSource
import example.nucleus.ui.components.MediaGridItem
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.screens.library.LibraryScreenState
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.utils.LocalPlaylistsViewModel
import example.nucleus.utils.LocalSnackbarHostState
import example.nucleus.utils.LocalSnackbarScope
import example.nucleus.viewmodels.YtmLibraryState
import example.nucleus.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import example.nucleus.ui.utils.circleAwareShape
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryMixedTab(
    state: LibraryScreenState,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel? = null,
    onRemovePlaylist: (String) -> Unit = {},
    onQuickPlayAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickShuffleAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickPlayPlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickShufflePlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
) {
    val ytm = state.ytmState as? YtmLibraryState.Success
    val downloadViewModel = LocalDownloadViewModel.current
    val playlistsViewModel = koinInject<example.nucleus.viewmodels.LibraryPlaylistsViewModel>()
    val snackbar = LocalSnackbarHostState.current
    val scope = LocalSnackbarScope.current
    val downloadedSongs by downloadViewModel.downloadedSongs.collectAsState()
    val downloadedCount by downloadViewModel.downloadedCount.collectAsState()
    val fullyDownloadedAlbums by downloadViewModel.fullyDownloadedAlbums.collectAsState()
    val fullyDownloadedPlaylists by downloadViewModel.fullyDownloadedPlaylists.collectAsState()


    val ytmSettled = true
    if (!ytmSettled) {
        CircularWavyProgressIndicator() // spinner o skeleton simple
        return
    }

    val albumLabel = stringResource(Res.string.item_album)
    val artistLabel = stringResource(Res.string.item_artist)
    val playlistLabel = stringResource(Res.string.item_playlist)
    val downloadsLabel = stringResource(Res.string.downloads)
    val nSongsTemplate = stringResource(Res.string.n_songs)

    data class MixedGridEntry(
        val key: String,
        val item: YTItem? = null,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String?,
        val placeholderType: PlaceholderType,
        val shape: Shape,
        val source: ItemContentSource,
        val onClick: () -> Unit,
        val onPlay: (() -> Unit)? = null,
        val onShuffle: (() -> Unit)? = null,
        val isRemovable: Boolean = false,
        val onRemove: () -> Unit = {},
        val onExport: (() -> Unit)? = null,
    )

    val items = remember(
        state.albums,
        state.artists,
        state.playlists,
        ytm,
        downloadedSongs,
        downloadedCount,
        fullyDownloadedAlbums,
        fullyDownloadedPlaylists,
    ) {
        buildList {

            val mergedAlbums = (state.albums + ytm?.albums.orEmpty()).distinctBy { it.id }
            val mergedArtists = (state.artists + ytm?.artists.orEmpty()).distinctBy { it.id }
            val mergedPlaylists = (state.playlists + ytm?.playlists.orEmpty()).distinctBy { it.id }

            val seenAlbumIds = mutableSetOf<String>()
            val seenPlaylistIds = mutableSetOf<String>()

            mergedAlbums.forEach { albumInfo ->

                if (albumInfo.id !in seenAlbumIds) {
                    add(
                        MixedGridEntry(
                            key = "alb_${albumInfo.id}",
                            item = albumInfo,
                            title = albumInfo.title,
                            subtitle = albumInfo.artists?.firstOrNull()?.name ?: albumLabel,
                            thumbnailUrl = albumInfo.thumbnail,
                            placeholderType = PlaceholderType.ALBUM,
                            shape = RoundedCornerShape(12.dp),
                            source = if (ytm?.albums?.any { it.id == albumInfo.id } == true) ItemContentSource.YOUTUBE else ItemContentSource.LOCAL,
                            onClick = { onNavigate(Route.Album(albumInfo.browseId)) },
                            onPlay = {
                                onQuickPlayAlbum(albumInfo.browseId, albumInfo.playlistId, albumInfo.title) {
                                    onNavigate(Route.Album(albumInfo.browseId))
                                }
                            },
                            onShuffle = {
                                onQuickShuffleAlbum(albumInfo.browseId, albumInfo.playlistId, albumInfo.title) {
                                    onNavigate(Route.Album(albumInfo.browseId))
                                }
                            },
                        )

                    )
                }
            }

            mergedArtists.forEach { artist ->
                add(
                    MixedGridEntry(
                        key = "art_${artist.id}",
                        item = artist,
                        title = artist.title,
                        subtitle = artistLabel,
                        thumbnailUrl = artist.thumbnail,
                        placeholderType = PlaceholderType.ARTIST,
                        shape = circleAwareShape(),
                        source = if (ytm?.artists?.any { it.id == artist.id } == true) ItemContentSource.YOUTUBE else ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Artist(artist.id)) },
                    )
                )
            }

            mergedPlaylists.forEach { playlist ->
                val isYtm = ytm?.playlists?.any { it.id == playlist.id } == true
                seenPlaylistIds.add(playlist.id)
                add(
                    MixedGridEntry(
                        key = "pl_${playlist.id}",
                        item = playlist,
                        title = playlist.title,
                        subtitle = playlist.songCountText ?: playlist.author?.name ?: playlistLabel,
                        thumbnailUrl = playlist.thumbnail,
                        placeholderType = PlaceholderType.PLAYLIST,
                        shape = RoundedCornerShape(12.dp),
                        source = if (isYtm) ItemContentSource.YOUTUBE else ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Playlist(playlist.id)) },
                        onPlay = {
                            onQuickPlayPlaylist(playlist.id, playlist.playEndpoint, playlist.title) {
                                onNavigate(Route.Playlist(playlist.id))
                            }
                        },
                        onShuffle = {
                            onQuickShufflePlaylist(playlist.id, playlist.shuffleEndpoint ?: playlist.playEndpoint, playlist.title) {
                                onNavigate(Route.Playlist(playlist.id))
                            }
                        },
                        isRemovable = !isYtm,
                        onRemove = { onRemovePlaylist(playlist.id) },
                        onExport = {
                            playlistsViewModel.exportPlaylist(playlist.id) { success, msg ->
                                scope.launch {
                                    if (success) {
                                        val result = snackbar.showSnackbar(
                                            message = getString(Res.string.export_playlist_success, msg),
                                            actionLabel = "Abrir carpeta",
                                            duration = androidx.compose.material3.SnackbarDuration.Long
                                        )
                                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                            try {
                                                val file = java.io.File(msg)
                                                example.nucleus.platform.NativeDesktop.openFolder(file.parentFile ?: file)
                                            } catch (_: Exception) {}
                                        }
                                    } else {
                                        snackbar.showSnackbar(getString(Res.string.export_playlist_error, msg))
                                    }
                                }
                            }
                        },
                    )
                )
            }

            if (downloadedCount > 0) {
                add(
                    MixedGridEntry(
                        key = "downloads_all",
                        title = downloadsLabel,
                        subtitle = String.format(nSongsTemplate, downloadedCount),
                        thumbnailUrl = downloadedSongs.firstOrNull()?.thumbnail,
                        placeholderType = PlaceholderType.DOWNLOADS,
                        shape = RoundedCornerShape(12.dp),
                        source = ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Playlist("LOCAL_DOWNLOADS")) },
                        onPlay = {
                            if (downloadedSongs.isNotEmpty()) {
                                playerViewModel?.playCustom(downloadedSongs, 0)
                            } else {
                                onNavigate(Route.Playlist("LOCAL_DOWNLOADS"))
                            }
                        },
                        onShuffle = {
                            if (downloadedSongs.isNotEmpty()) {
                                playerViewModel?.playCustom(downloadedSongs, 0)
                                playerViewModel?.toggleShuffle()
                            } else {
                                onNavigate(Route.Playlist("LOCAL_DOWNLOADS"))
                            }
                        },
                    )
                )
            }

            fullyDownloadedPlaylists.forEach { playlistInfo ->
                if (playlistInfo.playlistId !in seenPlaylistIds) {
                    add(
                        MixedGridEntry(
                            key = "dlpl_${playlistInfo.playlistId}",
                            title = playlistInfo.playlistName,
                            subtitle = String.format(nSongsTemplate, playlistInfo.downloadedSongCount),
                            thumbnailUrl = playlistInfo.thumbnail,
                            placeholderType = PlaceholderType.PLAYLIST,
                            shape = RoundedCornerShape(12.dp),
                            source = ItemContentSource.LOCAL,
                            onClick = { onNavigate(Route.Playlist(playlistInfo.playlistId)) },
                            onPlay = {
                                onQuickPlayPlaylist(playlistInfo.playlistId, null, playlistInfo.playlistName) {
                                    onNavigate(Route.Playlist(playlistInfo.playlistId))
                                }
                            },
                            onShuffle = {
                                onQuickShufflePlaylist(playlistInfo.playlistId, null, playlistInfo.playlistName) {
                                    onNavigate(Route.Playlist(playlistInfo.playlistId))
                                }
                            },
                        )
                    )
                }
            }

            fullyDownloadedAlbums.forEach { albumInfo ->
                if (albumInfo.albumId !in seenAlbumIds) {
                    add(
                        MixedGridEntry(
                            key = "dlal_${albumInfo.albumId}",
                            title = albumInfo.albumName,
                            subtitle = String.format(nSongsTemplate, albumInfo.songs.size),
                            thumbnailUrl = albumInfo.thumbnail,
                            placeholderType = PlaceholderType.ALBUM,
                            shape = RoundedCornerShape(12.dp),
                            source = ItemContentSource.LOCAL,
                            onClick = { onNavigate(Route.Album(albumInfo.albumId)) },
                            onPlay = {
                                onQuickPlayAlbum(albumInfo.albumId, null, albumInfo.albumName) {
                                    onNavigate(Route.Album(albumInfo.albumId))
                                }
                            },
                            onShuffle = {
                                onQuickShuffleAlbum(albumInfo.albumId, null, albumInfo.albumName) {
                                    onNavigate(Route.Album(albumInfo.albumId))
                                }
                            },
                        )
                    )
                }
            }
        }
    }

    if (items.isEmpty()) {
        LibraryEmptyState(Icons.Default.LibraryMusic, stringResource(Res.string.empty_library), stringResource(Res.string.empty_library_hint))
        return
    }

    val hasNonDownloadItems = items.any {
        !it.key.startsWith("downloads_") && !it.key.startsWith("dlpl_") && !it.key.startsWith("dlal_")
    }

    key(hasNonDownloadItems) {
        val gridState = remember { LazyGridState(0, 0) }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = maxOf(80.dp, LocalMiniPlayerInset.current)
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = items, key = { it.key }) { entry ->
                    if (entry.item != null) {
                        MediaGridItem(
                            item = entry.item,
                            onClick = entry.onClick,
                            onPlay = entry.onPlay,
                            onShuffle = entry.onShuffle,
                            onRemove = entry.onRemove,
                            isRemovable = entry.isRemovable,
                            source = entry.source,
                            onExport = entry.onExport
                        )
                    } else {
                        MediaGridItem(
                            title = entry.title,
                            subtitle = entry.subtitle,
                            thumbnailUrl = entry.thumbnailUrl,
                            placeholderType = entry.placeholderType,
                            shape = entry.shape,
                            onClick = entry.onClick,
                            onPlay = entry.onPlay,
                            onShuffle = entry.onShuffle,
                            onRemove = entry.onRemove,
                            isRemovable = entry.isRemovable,
                            source = entry.source,
                            onExport = entry.onExport
                        )
                    }
                }
            }

            AppVerticalScrollbar(
                state = gridState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}
