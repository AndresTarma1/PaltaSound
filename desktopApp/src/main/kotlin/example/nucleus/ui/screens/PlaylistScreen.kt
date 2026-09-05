package example.nucleus.ui.screens

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
import example.nucleus.ui.components.PlaylistScreenSkeleton
import example.nucleus.ui.screens.playlist.PlaylistLayout
import example.nucleus.viewmodels.PlaylistState
import example.nucleus.viewmodels.PlaylistManagerViewModel
import com.metrolist.innertube.models.SongItem
import example.nucleus.ui.components.artwork.ArtworkColors
import example.nucleus.ui.components.artwork.rememberArtworkColors
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource

data class PlaylistScreenState(
    val songs: List<SongItem> = emptyList(),
    val hasMore: Boolean = false,
    val isSaved: Boolean = false,
    val isSaving: Boolean = false,
    val isLoadingForPlay: Boolean = false,
)


data class PlaylistActions(
    val onBack: () -> Unit,
    val onNavigate: (Route) -> Unit,
    val onToggleSave: () -> Unit,
    val onPlay: () -> Unit,
    val onShuffle: () -> Unit,
    val onLoadMore: () -> Unit,
    val onDownloadPlaylist: () -> Unit,
    val onPlaySong: (Int) -> Unit,
    val onRemoveSongFromPlaylist: ((String) -> Unit)? = null,
    val onEditCover: (() -> Unit)? = null,
    val isLocalPlaylist: Boolean = false
)

@Composable
fun PlaylistScreenRoute(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistManagerViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    val successState = uiState as? PlaylistState.Success

    LaunchedEffect(successState?.playlistPage?.playlist?.id) {
        if (successState?.playlistPage?.playlist?.id == "LOCAL_DOWNLOADS") {
            viewModel.refreshLocalDownloadsPlaylist()
        }
    }

    // Las listas de reproducción locales siempre son editables; una lista de YouTube guardada
    // (marcada con favorito) también se vuelve editable una vez que sus canciones se almacenan
    // localmente en caché. isSaved puede cambiar mientras esta pantalla está abierta (el botón
    // de favorito), por lo que debe estar en la clave del remember, a diferencia del id inmutable
    // de la lista. Los estantes generados automáticamente (Mixes/Radios, id comienza con "RD";
    // "SE" episodios guardados) nunca son realmente editables del lado de YouTube — no ofrecer
    // eliminación para esos, incluso si están marcados. "LM" (Música que me gusta) es la única
    // excepción: PlaylistViewModel.removeSongFromPlaylist mapea la eliminación de ahí a un
    // dislike real, por lo que sigue siendo editable.
    val currentPlaylistId = successState?.playlistPage?.playlist?.id
    val isNonEditableAutoPlaylist = currentPlaylistId != null &&
        (currentPlaylistId == "SE" || currentPlaylistId.startsWith("RD"))
    val canEditPlaylist = !isNonEditableAutoPlaylist &&
        (currentPlaylistId?.startsWith("LOCAL_") == true || successState?.isSaved == true)

    val actions = remember(viewModel, successState != null, canEditPlaylist) {
        PlaylistActions(
            onBack = onBack,
            onNavigate = onNavigate,
            onToggleSave = { viewModel.toggleSave() },
            onLoadMore = { viewModel.loadMoreSongs() },
            onPlay = { viewModel.playAllSongs(shuffle = false) },
            onShuffle = { viewModel.playAllSongs(shuffle = true) },
            onDownloadPlaylist = { viewModel.downloadPlaylist() },
            onPlaySong = { index -> viewModel.playSongFromPlaylist(index) },
            onRemoveSongFromPlaylist = if (canEditPlaylist) {
                { songId -> viewModel.removeSongFromPlaylist(songId) }
            } else null,
            onEditCover = { viewModel.pickAndSetCustomThumbnail() },
            // También es cierto para una lista de YouTube guardada (marcada): sus canciones se
            // almacenan localmente en caché también, y la eliminación se envía a la lista real
            // (ver PlaylistViewModel.removeSongFromPlaylist).
            isLocalPlaylist = canEditPlaylist
        )
    }

    val songs by viewModel.songs.collectAsState()
    val hasMoreSongs by viewModel.hasMoreSongs.collectAsState()


    val state = PlaylistScreenState(
        songs = songs,
        hasMore = hasMoreSongs,
        isSaved = successState?.isSaved ?: false,
        isSaving = successState?.isSaving ?: false,
        isLoadingForPlay = successState?.isLoadingForPlay ?: false,
    )


    PlaylistScreen(
        uiState = uiState,
        state = state,
        actions = actions
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistScreen(
    uiState: PlaylistState,
    state: PlaylistScreenState,
    actions: PlaylistActions
) {
    val thumbnailUrl = (uiState as? PlaylistState.Success)?.playlistPage?.playlist?.thumbnail
    val artworkColors = rememberArtworkColors(thumbnailUrl)
    val isReady = uiState is PlaylistState.Success && (thumbnailUrl == null || artworkColors != ArtworkColors.Default)
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
        if (!isReady && uiState !is PlaylistState.Error) {
             PlaylistScreenSkeleton()
        } else {
            when (uiState) {
                is PlaylistState.Success -> PlaylistLayout(
                    playlistPage = uiState.playlistPage,
                    state = state,
                    actions = actions
                )
                is PlaylistState.Error -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
