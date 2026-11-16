package example.nucleus.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import example.nucleus.navigation.ScreenConfig
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import example.nucleus.ui.screens.shared.SectionListItem
import example.nucleus.utils.LocalPlayerViewModel
import com.metrolist.innertube.models.PlaylistItem
import example.nucleus.ui.components.ItemContentSource
import example.nucleus.ui.components.MediaGridItem
import example.nucleus.ui.components.exportPlaylistWithFeedback
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.screens.library.LibraryScreenState
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.utils.LocalPlaylistsViewModel
import example.nucleus.utils.LocalSnackbarHostState
import example.nucleus.utils.LocalSnackbarScope
import example.nucleus.viewmodels.YtmLibraryState
import example.nucleus.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import example.nucleus.ui.utils.circleAwareShape
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryMixedTab(
    state: LibraryScreenState,
    onNavigate: (ScreenConfig) -> Unit,
    playerViewModel: PlayerViewModel? = null,
    onRemovePlaylist: (String) -> Unit = {},
    onQuickPlayAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickShuffleAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickPlayPlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickShufflePlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
    onTabSelected: (example.nucleus.viewmodels.LibraryTab) -> Unit = {},
    viewMode: example.nucleus.data.repository.LibraryViewMode = example.nucleus.data.repository.LibraryViewMode.GRID,
    header: @Composable () -> Unit = {},
    searchQuery: String = "",
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


    val albumLabel = stringResource(Res.string.item_album)
    val artistLabel = stringResource(Res.string.item_artist)
    val playlistLabel = stringResource(Res.string.item_playlist)
    val podcastLabel = stringResource(Res.string.item_podcast)
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
        state.podcasts,
        ytm,
        downloadedSongs,
        downloadedCount,
        fullyDownloadedAlbums,
        fullyDownloadedPlaylists,
    ) {
        buildList {

            val mergedAlbums = (state.albums + ytm?.albums.orEmpty()).distinctBy { it.id }
            // Perfiles fuera: no son artistas abribles (sin página ni avatar).
            val mergedArtists = (state.artists.filterNot { it.isProfile } + ytm?.artists.orEmpty().filterNot { it.isProfile }).distinctBy { it.id }
            val mergedPlaylists = (state.playlists + ytm?.playlists.orEmpty()).distinctBy { it.id }

            val seenAlbumIds = mutableSetOf<String>()
            val seenPlaylistIds = mutableSetOf<String>()

            mergedAlbums.forEach { albumInfo ->
                if (albumInfo.id !in seenAlbumIds) {
                    seenAlbumIds.add(albumInfo.id)
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
                            onClick = { onNavigate(ScreenConfig.collection(albumInfo.browseId)) },
                            onPlay = {
                                onQuickPlayAlbum(albumInfo.browseId, albumInfo.playlistId, albumInfo.title) {
                                    onNavigate(ScreenConfig.collection(albumInfo.browseId))
                                }
                            },
                            onShuffle = {
                                onQuickShuffleAlbum(albumInfo.browseId, albumInfo.playlistId, albumInfo.title) {
                                    onNavigate(ScreenConfig.collection(albumInfo.browseId))
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
                        onClick = { if (!artist.isProfile) onNavigate(ScreenConfig.Artist(artist.id)) },
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
                        onClick = { onNavigate(ScreenConfig.Playlist(playlist.id)) },
                        onPlay = {
                            onQuickPlayPlaylist(playlist.id, playlist.playEndpoint, playlist.title) {
                                onNavigate(ScreenConfig.Playlist(playlist.id))
                            }
                        },
                        onShuffle = {
                            onQuickShufflePlaylist(playlist.id, playlist.shuffleEndpoint ?: playlist.playEndpoint, playlist.title) {
                                onNavigate(ScreenConfig.Playlist(playlist.id))
                            }
                        },
                        isRemovable = !isYtm,
                        onRemove = { onRemovePlaylist(playlist.id) },
                        onExport = {
                            exportPlaylistWithFeedback(scope, snackbar) { onResult ->
                                playlistsViewModel.exportPlaylist(playlist.id, onResult)
                            }
                        },
                    )
                )
            }

            // Podcasts guardados. Van aqui ademas de en su propia pestana porque esta es la que se
            // abre por defecto: si solo estuvieran en la pestana, parecerian no existir.
            // `state.podcasts` ya viene fusionado local+remoto y sin duplicar.
            state.podcasts.forEach { podcast ->
                add(
                    MixedGridEntry(
                        key = "pod_${podcast.id}",
                        item = podcast,
                        title = podcast.title,
                        subtitle = podcast.author?.name ?: podcast.episodeCountText ?: podcastLabel,
                        thumbnailUrl = podcast.thumbnail,
                        placeholderType = PlaceholderType.PLAYLIST,
                        shape = RoundedCornerShape(12.dp),
                        source = if (ytm?.podcasts?.any { it.id == podcast.id } == true) {
                            ItemContentSource.YOUTUBE
                        } else {
                            ItemContentSource.LOCAL
                        },
                        onClick = { onNavigate(ScreenConfig.Playlist(podcast.id)) },
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
                        onClick = { onNavigate(ScreenConfig.Playlist("LOCAL_DOWNLOADS")) },
                        onPlay = {
                            if (downloadedSongs.isNotEmpty()) {
                                playerViewModel?.playCustom(downloadedSongs, 0)
                            } else {
                                onNavigate(ScreenConfig.Playlist("LOCAL_DOWNLOADS"))
                            }
                        },
                        onShuffle = {
                            if (downloadedSongs.isNotEmpty()) {
                                playerViewModel?.playCustom(downloadedSongs, 0)
                                playerViewModel?.toggleShuffle()
                            } else {
                                onNavigate(ScreenConfig.Playlist("LOCAL_DOWNLOADS"))
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
                            onClick = { onNavigate(ScreenConfig.Playlist(playlistInfo.playlistId)) },
                            onPlay = {
                                onQuickPlayPlaylist(playlistInfo.playlistId, null, playlistInfo.playlistName) {
                                    onNavigate(ScreenConfig.Playlist(playlistInfo.playlistId))
                                }
                            },
                            onShuffle = {
                                onQuickShufflePlaylist(playlistInfo.playlistId, null, playlistInfo.playlistName) {
                                    onNavigate(ScreenConfig.Playlist(playlistInfo.playlistId))
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
                            onClick = { onNavigate(ScreenConfig.collection(albumInfo.albumId)) },
                            onPlay = {
                                onQuickPlayAlbum(albumInfo.albumId, null, albumInfo.albumName) {
                                    onNavigate(ScreenConfig.collection(albumInfo.albumId))
                                }
                            },
                            onShuffle = {
                                onQuickShuffleAlbum(albumInfo.albumId, null, albumInfo.albumName) {
                                    onNavigate(ScreenConfig.collection(albumInfo.albumId))
                                }
                            },
                        )
                    )
                }
            }
        }
    }

    if (items.isEmpty()) {
        // Con búsqueda activa el header debe seguir visible: si no, no hay
        // forma de limpiar el filtro ni de cambiar de pestaña.
        if (searchQuery.isNotBlank()) {
            LibraryEmptyWithHeader(header, Icons.Default.Search, stringResource(Res.string.no_results), "\"$searchQuery\"")
        } else {
            LibraryEmptyWithHeader(header, Icons.Default.LibraryMusic, stringResource(Res.string.empty_library), stringResource(Res.string.empty_library_hint))
        }
        return
    }

    if (viewMode == example.nucleus.data.repository.LibraryViewMode.LIST) {
            val pvm = playerViewModel ?: LocalPlayerViewModel.current
            val localPlaylistIds = remember(state.playlists) { state.playlists.map { it.id }.toSet() }
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = libraryGridContentPadding(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "library_header") { header() }
                    items(items = items, key = { it.key }) { entry ->
                            val rowItem = entry.item ?: PlaylistItem(
                                id = "LOCAL_DOWNLOADS",
                                title = entry.title,
                                author = null,
                                songCountText = entry.subtitle,
                                thumbnail = entry.thumbnailUrl,
                                playEndpoint = null,
                                shuffleEndpoint = null,
                                radioEndpoint = null,
                            )
                            SectionListItem(
                                item = rowItem,
                                onNavigate = onNavigate,
                                playerViewModel = pvm,
                                onRemoveCollection = { item ->
                                    if (item is PlaylistItem && item.id in localPlaylistIds) {
                                        onRemovePlaylist(item.id)
                                    }
                                },
                            )
                        }
                }
                AppVerticalScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
        } else {
            val gridState = remember { LazyGridState(0, 0) }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = LibraryGridMinCellWidth),
                    contentPadding = libraryGridContentPadding(),
                    horizontalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
                    verticalArrangement = Arrangement.spacedBy(LibraryGridSpacing)
                ) {
                                        item(span = { GridItemSpan(maxLineSpan) }, key = "library_header") { header() }
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
