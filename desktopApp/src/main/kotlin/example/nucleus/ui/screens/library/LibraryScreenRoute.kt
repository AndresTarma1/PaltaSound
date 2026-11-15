@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.library

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import example.nucleus.navigation.ScreenConfig
import example.nucleus.platform.NativeDesktop
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.viewmodels.CsvImportState
import example.nucleus.viewmodels.LibraryPlaylistsViewModel
import example.nucleus.viewmodels.LibraryViewModel
import java.net.URI
import org.koin.compose.koinInject

@Composable
fun LibraryScreenRoute(
    viewModel: LibraryViewModel,
    onNavigate: (ScreenConfig) -> Unit,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val playlistsViewModel = koinInject<LibraryPlaylistsViewModel>()

    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val selectedYtmFilter by viewModel.selectedYtmFilter.collectAsState()
    val viewMode by viewModel.libraryViewMode.collectAsState()
    val songs by viewModel.sortedFilteredSongs.collectAsState()
    val albums by viewModel.sortedFilteredAlbums.collectAsState()
    val artists by viewModel.sortedFilteredArtists.collectAsState()
    val playlists by viewModel.sortedFilteredPlaylists.collectAsState()
    val podcasts by viewModel.sortedFilteredPodcasts.collectAsState()
    val ytmState by viewModel.ytmState.collectAsState()
    val csvImportState by playlistsViewModel.csvImportState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.ensureYtmLibraryLoaded()
    }

    var importNameField by remember { mutableStateOf("") }
    LaunchedEffect(csvImportState) {
        if (csvImportState !is CsvImportState.Ready) importNameField = ""
    }
    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<String?>(null) }
    var showCsvTutorial by remember { mutableStateOf(false) }

    val state = LibraryScreenState(
        selectedTab = selectedTab,
        searchQuery = searchQuery,
        sortOrder = sortOrder,
        selectedYtmFilter = selectedYtmFilter,
        viewMode = viewMode,
        songs = songs,
        albums = albums,
        artists = artists,
        playlists = playlists,
        podcasts = podcasts,
        ytmState = ytmState,
    )

    val actions = remember(viewModel, onNavigate, playerViewModel) {
        LibraryActions(
            onTabSelected = viewModel::selectTab,
            onNavigate = onNavigate,
            onOpenStats = { onNavigate(ScreenConfig.Stats) },
            onRemoveAlbum = viewModel::removeAlbum,
            onRemoveArtist = viewModel::removeArtist,
            onRemovePlaylist = { id ->
                playlistToDelete = id
                showDeletePlaylistDialog = true
            },
            onQuickPlayAlbum = { browseId, playlistId, title, onFallback ->
                viewModel.resolveAlbumSongsForPlayback(
                    browseId = browseId,
                    onResolved = { songs ->
                        playerViewModel.playAlbum(
                            songs = songs, startIndex = 0,
                            browseId = browseId, title = title,
                        )
                    },
                    onFallback = onFallback,
                )
            },
            onQuickShuffleAlbum = { browseId, playlistId, title, onFallback ->
                viewModel.resolveAlbumSongsForPlayback(
                    browseId = browseId,
                    onResolved = { songs ->
                        playerViewModel.playAlbum(
                            songs = songs, startIndex = 0,
                            browseId = browseId, title = title,
                        )
                        playerViewModel.toggleShuffle()
                    },
                    onFallback = onFallback,
                )
            },
            onQuickPlayPlaylist = { playlistId, endpoint, title, onFallback ->
                playerViewModel.playPlaylistFromId(
                    playlistId = playlistId,
                    endpoint = endpoint,
                    title = title,
                    onEmpty = onFallback,
                )
            },
            onQuickShufflePlaylist = { playlistId, endpoint, title, onFallback ->
                playerViewModel.playPlaylistFromId(
                    playlistId = playlistId,
                    endpoint = endpoint,
                    title = title,
                    shuffle = true,
                    onEmpty = onFallback,
                )
            },
            onRefreshYtm = viewModel::refreshYtmLibrary,
            onCreatePlaylist = viewModel::createLocalPlaylist,
            onImportCsv = { showCsvTutorial = true },
            onSearchQueryChange = viewModel::setSearchQuery,
            onClearSearch = viewModel::clearSearch,
            onSortOrderChange = viewModel::setSortOrder,
            onYtmFilterChange = viewModel::setYtmFilter,
            onViewModeChange = viewModel::setLibraryViewMode,
        )
    }

    LibraryScreen(
        state = state,
        actions = actions,
        playerViewModel = playerViewModel,
    )

    var openExtractPage by remember { mutableStateOf(true) }
    if (showCsvTutorial) {
        CsvTutorialDialog(
            openExtractPage = openExtractPage,
            onOpenExtractPageChange = { openExtractPage = it },
            onConfirm = { open ->
                showCsvTutorial = false
                if (open) NativeDesktop.browse(URI("https://www.tunemymusic.com/"))
                playlistsViewModel.importCsvFile()
            },
            onDismiss = { showCsvTutorial = false },
        )
    }

    (csvImportState as? CsvImportState.Ready)?.let { csvState ->
        if (importNameField.isBlank()) {
            importNameField = csvState.suggestedName
        }
        CsvImportNameDialog(
            name = importNameField,
            onNameChange = { importNameField = it },
            totalCount = csvState.totalCount,
            suggestedName = csvState.suggestedName,
            onConfirm = { playlistsViewModel.confirmCsvImport(it) },
            onDismiss = { playlistsViewModel.cancelCsvImport() },
        )
    }

    if (showDeletePlaylistDialog && playlistToDelete != null) {
        DeletePlaylistDialog(
            onConfirm = {
                playlistToDelete?.let(viewModel::removePlaylist)
                playlistToDelete = null
                showDeletePlaylistDialog = false
            },
            onDismiss = {
                showDeletePlaylistDialog = false
                playlistToDelete = null
            },
        )
    }
}
