package example.nucleus.ui.screens.album

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.dp
import example.nucleus.navigation.Route
import example.nucleus.ui.components.AlbumScreenSkeleton
import example.nucleus.viewmodels.AlbumState
import example.nucleus.viewmodels.AlbumManagerViewModel
import com.metrolist.innertube.models.SongItem
import example.nucleus.ui.components.artwork.ArtworkColors
import example.nucleus.ui.components.artwork.rememberArtworkColors
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource

data class AlbumScreenState(
    val songs: List<SongItem> = emptyList(),
    val hasMore: Boolean = false,
    val isSaved: Boolean = false,
    val isSaving: Boolean = false,
    val isLoadingForPlay: Boolean = false,
)

data class AlbumScreenActions(
    val onBack: () -> Unit,
    val onLoadMore: () -> Unit,
    val onNavigate: (Route) -> Unit,
    val onToggleSave: () -> Unit,
    val onPlayAll: () -> Unit,
    val onShuffle: () -> Unit,
)

@Composable
fun AlbumScreenRoute(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumManagerViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val hasMoreSongs by viewModel.hasMoreSongs.collectAsState()

    val successState = uiState as? AlbumState.Success

    val state = AlbumScreenState(
        songs = songs,
        hasMore = hasMoreSongs,
        isSaved = successState?.isSaved ?: false,
        isSaving = successState?.isSaving ?: false,
        isLoadingForPlay = successState?.isLoadingForPlay ?: false,
    )

    val actions = AlbumScreenActions(
        onBack = onBack,
        onLoadMore = { viewModel.loadMoreSongs() },
        onNavigate = onNavigate,
        onToggleSave = { viewModel.toggleSave() },
        onPlayAll = { viewModel.playAllSongs(shuffle = false) },
        onShuffle = { viewModel.playAllSongs(shuffle = true) },
    )

    AlbumScreen(
        uiState = uiState,
        state = state,
        actions = actions,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AlbumScreen(
    uiState: AlbumState,
    state: AlbumScreenState,
    actions: AlbumScreenActions,
) {
    val thumbnailUrl = (uiState as? AlbumState.Success)?.albumPage?.album?.thumbnail
    val artworkColors = rememberArtworkColors(thumbnailUrl)
    val isReady = uiState is AlbumState.Success && (thumbnailUrl == null || artworkColors != ArtworkColors.Default)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                    actions.onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        if (!isReady && uiState !is AlbumState.Error) {
            AlbumScreenSkeleton()
        } else {
            when (uiState) {
                is AlbumState.Success -> AlbumScreenLayout(
                    albumPage = uiState.albumPage,
                    state = state,
                    actions = actions,
                )
                is AlbumState.Error -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
