package example.nucleus.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.metrolist.innertube.models.PodcastItem
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.no_results
import example.nucleus.generated.resources.no_saved_podcasts
import example.nucleus.generated.resources.save_podcasts_hint
import example.nucleus.navigation.ScreenConfig
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.screens.shared.PodcastGridItem
import org.jetbrains.compose.resources.stringResource

/**
 * Podcasts guardados en la cuenta.
 *
 * No hay tabla local para ellos: guardar un podcast es un `likePlaylist` remoto, asi que
 * esta pestana es solo el estante de la cuenta. Todo lo que hay aqui viene de la red.
 */
@Composable
fun PodcastsTab(
    podcasts: List<PodcastItem>,
    isLoading: Boolean = false,
    onNavigate: (ScreenConfig) -> Unit,
    viewMode: example.nucleus.data.repository.LibraryViewMode = example.nucleus.data.repository.LibraryViewMode.GRID,
    playerViewModel: example.nucleus.viewmodels.PlayerViewModel? = null,
    header: @Composable () -> Unit = {},
    searchQuery: String = "",
) {
    if (podcasts.isEmpty() && !isLoading) {
        // Con búsqueda activa el header debe seguir visible: si no, no hay
        // forma de limpiar el filtro ni de cambiar de pestaña.
        if (searchQuery.isNotBlank()) {
            LibraryEmptyWithHeader(
                header,
                Icons.Default.Search,
                stringResource(Res.string.no_results),
                "\"$searchQuery\"",
            )
        } else {
            LibraryEmptyWithHeader(
                header,
                Icons.Rounded.Podcasts,
                stringResource(Res.string.no_saved_podcasts),
                stringResource(Res.string.save_podcasts_hint),
            )
        }
        return
    }
    if (podcasts.isEmpty() && isLoading) {
        LibraryGridSkeleton(count = 4)
        return
    }

    if (viewMode == example.nucleus.data.repository.LibraryViewMode.LIST && playerViewModel != null) {
        LibraryListRows(
            header = header,
            items = podcasts,
            onNavigate = onNavigate,
            playerViewModel = playerViewModel,
        )
        return
    }

    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = LibraryGridMinCellWidth),
            contentPadding = libraryGridContentPadding(),
            horizontalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
            verticalArrangement = Arrangement.spacedBy(LibraryGridSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "library_header") { header() }
            items(items = podcasts, key = { it.id }) { podcast ->
                PodcastGridItem(
                    item = podcast,
                    onClick = { onNavigate(ScreenConfig.Playlist(podcast.id)) },
                )
            }
        }

        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}
