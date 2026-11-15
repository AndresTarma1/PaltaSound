package example.nucleus.ui.screens.library.tabs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import example.nucleus.navigation.ScreenConfig
import example.nucleus.ui.components.ItemContentSource
import example.nucleus.ui.components.MediaGridItem
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import com.metrolist.innertube.models.AlbumItem

@Composable
fun AlbumsTab(
    albums: List<AlbumItem>,
    ytmAlbums: List<AlbumItem> = emptyList(),
    isLoadingYtm: Boolean = false,
    onNavigate: (ScreenConfig) -> Unit,
    onRemove: (String) -> Unit,
    onQuickPlayAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickShuffleAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    viewMode: example.nucleus.data.repository.LibraryViewMode = example.nucleus.data.repository.LibraryViewMode.GRID,
    playerViewModel: example.nucleus.viewmodels.PlayerViewModel? = null,
    header: @Composable () -> Unit = {},
) {
    val isEmpty = albums.isEmpty() && ytmAlbums.isEmpty() && !isLoadingYtm
    if (isEmpty) {
        LibraryEmptyState(Icons.Default.Album, stringResource(Res.string.no_saved_albums), stringResource(Res.string.save_albums_hint))
        return
    }
    if (isLoadingYtm && ytmAlbums.isEmpty()) {
        YtmSectionHeader(stringResource(Res.string.ytm_albums_section), isLoading = true)
        LibraryGridSkeleton(count = 4)
        return
    }

    val mergedAlbums = remember(albums, ytmAlbums) {
        buildList {
            albums.forEach { add(it to ItemContentSource.LOCAL) }
            ytmAlbums.forEach { add(it to ItemContentSource.YOUTUBE) }
        }.distinctBy { (album, _) -> album.id }
    }

    if (viewMode == example.nucleus.data.repository.LibraryViewMode.LIST && playerViewModel != null) {
        val sourcesById = remember(mergedAlbums) { mergedAlbums.associate { (a, s) -> a.id to s } }
        LibraryListRows(
            header = header,
            items = mergedAlbums.map { it.first },
            onNavigate = onNavigate,
            playerViewModel = playerViewModel,
            sourceFor = { sourcesById[it.id] ?: ItemContentSource.YOUTUBE },
            onRemoveCollection = { item ->
                (item as? com.metrolist.innertube.models.AlbumItem)?.let { onRemove(it.browseId) }
            },
        )
        return
    }

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
            items(
                items = mergedAlbums,
                key = { (album, source) -> "${source.name.lowercase()}_${album.browseId}" }
            ) { (album, source) ->
                MediaGridItem(
                    item = album,
                    onClick = { onNavigate(ScreenConfig.collection(album.browseId)) },
                    onPlay = {
                        onQuickPlayAlbum(album.browseId, album.playlistId, album.title) {
                            onNavigate(ScreenConfig.collection(album.browseId))
                        }
                    },
                    onShuffle = {
                        onQuickShuffleAlbum(album.browseId, album.playlistId, album.title) {
                            onNavigate(ScreenConfig.collection(album.browseId))
                        }
                    },
                    onRemove = { onRemove(album.browseId) },
                    isRemovable = source == ItemContentSource.LOCAL,
                    source = source,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
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


