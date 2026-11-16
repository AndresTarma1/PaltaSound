package example.nucleus.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import example.nucleus.data.repository.LibraryViewMode
import example.nucleus.navigation.ScreenConfig
import example.nucleus.ui.components.ItemContentSource
import example.nucleus.ui.components.MediaGridItem
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.screens.shared.SectionListItem
import example.nucleus.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.SongItem
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.no_results
import example.nucleus.generated.resources.no_saved_songs
import example.nucleus.generated.resources.save_songs_hint
import org.jetbrains.compose.resources.stringResource

@Composable
fun SongsTab(
    songs: List<SongItem>,
    onNavigate: (ScreenConfig) -> Unit,
    viewMode: LibraryViewMode = LibraryViewMode.GRID,
    playerViewModel: PlayerViewModel? = null,
    header: @Composable () -> Unit = {},
    searchQuery: String = "",
) {
    if (songs.isEmpty()) {
        // Con búsqueda activa el header debe seguir visible: si no, no hay
        // forma de limpiar el filtro ni de cambiar de pestaña.
        if (searchQuery.isNotBlank()) {
            LibraryEmptyWithHeader(header, Icons.Default.Search, stringResource(Res.string.no_results), "\"$searchQuery\"")
        } else {
            LibraryEmptyWithHeader(header, Icons.Default.MusicNote, stringResource(Res.string.no_saved_songs), stringResource(Res.string.save_songs_hint))
        }
        return
    }

    if (viewMode == LibraryViewMode.LIST && playerViewModel != null) {
        LibraryListRows(
            header = header,
            items = songs,
            onNavigate = onNavigate,
            playerViewModel = playerViewModel,
            sourceFor = { ItemContentSource.LOCAL },
        )
        return
    }

    val pvm = playerViewModel ?: return
    val gridState = rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = LibraryGridMinCellWidth),
            contentPadding = libraryGridContentPadding(),
            horizontalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
            verticalArrangement = Arrangement.spacedBy(LibraryGridSpacing)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "library_header") { header() }
            items(items = songs, key = { it.id }) { song ->
                MediaGridItem(
                    item = song,
                    onClick = { pvm.playSingle(song) },
                    onPlay = { pvm.playSingle(song) },
                    onShuffle = {
                        pvm.playSingle(song)
                        pvm.toggleShuffle()
                    },
                    isRemovable = false,
                    source = ItemContentSource.LOCAL,
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
