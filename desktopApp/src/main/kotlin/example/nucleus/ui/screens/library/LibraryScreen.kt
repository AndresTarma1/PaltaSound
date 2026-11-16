@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import example.nucleus.ui.screens.library.tabs.AlbumsTab
import example.nucleus.ui.screens.library.tabs.ArtistsTab
import example.nucleus.ui.screens.library.tabs.LibraryMixedTab
import example.nucleus.ui.screens.library.tabs.PlaylistsTab
import example.nucleus.ui.screens.library.tabs.PodcastsTab
import example.nucleus.ui.screens.library.tabs.SongsTab
import example.nucleus.viewmodels.LibraryTab
import example.nucleus.viewmodels.YtmLibraryState
import example.nucleus.viewmodels.PlayerViewModel

@Composable
fun LibraryScreen(
    state: LibraryScreenState,
    actions: LibraryActions,
    playerViewModel: PlayerViewModel? = null,
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val headerContent: @Composable () -> Unit = {
        LibraryHeader(
            state = state,
            actions = actions,
            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.selectedTab ?: LibraryTab.LIBRARY) {
            LibraryTab.SONGS -> SongsTab(
                songs = state.songs,
                onNavigate = actions.onNavigate,
                viewMode = state.viewMode,
                playerViewModel = playerViewModel,
                header = headerContent,
                searchQuery = state.searchQuery,
            )

            LibraryTab.ALBUMS -> AlbumsTab(
                albums = state.albums,
                ytmAlbums = (state.ytmState as? YtmLibraryState.Success)?.albums.orEmpty(),
                isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                onNavigate = actions.onNavigate,
                onRemove = actions.onRemoveAlbum,
                onQuickPlayAlbum = actions.onQuickPlayAlbum,
                onQuickShuffleAlbum = actions.onQuickShuffleAlbum,
                viewMode = state.viewMode,
                playerViewModel = playerViewModel,
                header = headerContent,
                searchQuery = state.searchQuery,
            )

            LibraryTab.ARTISTS -> ArtistsTab(
                artists = state.artists,
                ytmArtists = (state.ytmState as? YtmLibraryState.Success)?.artists.orEmpty(),
                isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                onNavigate = actions.onNavigate,
                onRemove = actions.onRemoveArtist,
                viewMode = state.viewMode,
                playerViewModel = playerViewModel,
                header = headerContent,
                searchQuery = state.searchQuery,
            )

            LibraryTab.PLAYLISTS -> PlaylistsTab(
                playlists = state.playlists,
                ytmPlaylists = (state.ytmState as? YtmLibraryState.Success)?.playlists.orEmpty(),
                isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                onNavigate = actions.onNavigate,
                onRemove = actions.onRemovePlaylist,
                playerViewModel = playerViewModel,
                onQuickPlayPlaylist = actions.onQuickPlayPlaylist,
                onQuickShufflePlaylist = actions.onQuickShufflePlaylist,
                viewMode = state.viewMode,
                header = headerContent,
                searchQuery = state.searchQuery,
            )

            LibraryTab.PODCASTS -> PodcastsTab(
                podcasts = state.podcasts,
                isLoading = state.ytmState is YtmLibraryState.Loading,
                onNavigate = actions.onNavigate,
                viewMode = state.viewMode,
                playerViewModel = playerViewModel,
                header = headerContent,
                searchQuery = state.searchQuery,
            )

            LibraryTab.LIBRARY -> LibraryMixedTab(
                state = state,
                onNavigate = actions.onNavigate,
                onTabSelected = actions.onTabSelected,
                viewMode = state.viewMode,
                playerViewModel = playerViewModel,
                onRemovePlaylist = actions.onRemovePlaylist,
                onQuickPlayAlbum = actions.onQuickPlayAlbum,
                onQuickShuffleAlbum = actions.onQuickShuffleAlbum,
                onQuickPlayPlaylist = actions.onQuickPlayPlaylist,
                onQuickShufflePlaylist = actions.onQuickShufflePlaylist,
                header = headerContent,
                searchQuery = state.searchQuery,
            )
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            name = newPlaylistName,
            onNameChange = { newPlaylistName = it },
            onConfirm = {
                val name = newPlaylistName.trim()
                if (name.isNotEmpty()) {
                    actions.onCreatePlaylist(name)
                    newPlaylistName = ""
                    showCreatePlaylistDialog = false
                }
            },
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }
}
