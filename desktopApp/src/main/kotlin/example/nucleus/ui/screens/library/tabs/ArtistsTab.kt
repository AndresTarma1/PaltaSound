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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import example.nucleus.navigation.ScreenConfig
import example.nucleus.ui.components.ItemContentSource
import example.nucleus.ui.components.MediaGridItem
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import com.metrolist.innertube.models.ArtistItem

@Composable
fun ArtistsTab(
    artists: List<ArtistItem>,
    ytmArtists: List<ArtistItem> = emptyList(),
    isLoadingYtm: Boolean = false,
    onNavigate: (ScreenConfig) -> Unit,
    onRemove: (String) -> Unit,
) {
    val isEmpty = artists.isEmpty() && ytmArtists.isEmpty() && !isLoadingYtm
    if (isEmpty) {
        LibraryEmptyState(Icons.Default.Person, stringResource(Res.string.no_saved_artists), stringResource(Res.string.save_artists_hint))
        return
    }
    if (isLoadingYtm && ytmArtists.isEmpty()) {
        LibraryGridSkeleton(count = 4, isCircle = true)
        return
    }

    val mergedArtists = remember(artists, ytmArtists) {
        buildList {
            artists.forEach { add(it to ItemContentSource.LOCAL) }
            ytmArtists.forEach { add(it to ItemContentSource.YOUTUBE) }
        }.distinctBy { (artist, _) -> artist.id }
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
            items(
                items = mergedArtists,
                key = { (artist, source) -> "${source.name.lowercase()}_${artist.id}" }
            ) { (artist, source) ->
                MediaGridItem(
                    item = artist,
                    onClick = { onNavigate(ScreenConfig.Artist(artist.id)) },
                    onRemove = { onRemove(artist.id) },
                    isRemovable = source == ItemContentSource.LOCAL,
                    source = source,
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
