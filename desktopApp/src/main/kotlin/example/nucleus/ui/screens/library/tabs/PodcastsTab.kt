package example.nucleus.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.PodcastItem
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.no_saved_podcasts
import example.nucleus.generated.resources.save_podcasts_hint
import example.nucleus.navigation.Route
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.screens.shared.PodcastGridItem
import example.nucleus.ui.themes.LocalMiniPlayerInset
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
    onNavigate: (Route) -> Unit,
) {
    if (podcasts.isEmpty() && !isLoading) {
        LibraryEmptyState(
            Icons.Rounded.Podcasts,
            stringResource(Res.string.no_saved_podcasts),
            stringResource(Res.string.save_podcasts_hint),
        )
        return
    }
    if (podcasts.isEmpty() && isLoading) {
        LibraryGridSkeleton(count = 4)
        return
    }

    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = maxOf(80.dp, LocalMiniPlayerInset.current),
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = podcasts, key = { it.id }) { podcast ->
                PodcastGridItem(
                    item = podcast,
                    onClick = { onNavigate(Route.Playlist(podcast.id)) },
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