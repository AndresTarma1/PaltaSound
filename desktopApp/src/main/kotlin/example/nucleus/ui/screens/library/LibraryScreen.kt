@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.navigation.Route
import example.nucleus.ui.components.layout.HorizontalScrollableRow
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.ui.screens.library.tabs.AlbumsTab
import example.nucleus.ui.screens.library.tabs.ArtistsTab
import example.nucleus.ui.screens.library.tabs.LibraryMixedTab
import example.nucleus.ui.screens.library.tabs.PlaylistsTab
import example.nucleus.viewmodels.CsvImportState
import example.nucleus.viewmodels.LibraryPlaylistsViewModel
import example.nucleus.viewmodels.LibrarySortOrder
import example.nucleus.viewmodels.LibraryTab
import example.nucleus.viewmodels.LibraryViewModel
import example.nucleus.viewmodels.PlayerViewModel
import example.nucleus.viewmodels.YtmLibraryFilter
import example.nucleus.viewmodels.YtmLibraryState
import example.nucleus.utils.LocalPlayerViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import example.nucleus.platform.NativeDesktop
import java.net.URI

data class LibraryScreenState(
    val selectedTab: LibraryTab? = null,
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.NAME_ASC,
    val selectedYtmFilter: YtmLibraryFilter? = null,
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val ytmState: YtmLibraryState = YtmLibraryState.Idle,
)

data class LibraryActions(
    val onTabSelected: (LibraryTab) -> Unit,
    val onNavigate: (Route) -> Unit,
    val onOpenStats: () -> Unit,
    val onRemoveAlbum: (String) -> Unit,
    val onRemoveArtist: (String) -> Unit,
    val onRemovePlaylist: (String) -> Unit,
    val onQuickPlayAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    val onQuickShuffleAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    val onQuickPlayPlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
    val onQuickShufflePlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
    val onRefreshYtm: () -> Unit,
    val onCreatePlaylist: (String) -> Unit,
    val onImportCsv: () -> Unit,
    val onExportPlaylist: (String) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onSortOrderChange: (LibrarySortOrder) -> Unit,
    val onYtmFilterChange: (YtmLibraryFilter?) -> Unit,
)

@Composable
fun LibraryScreenRoute(
    viewModel: LibraryViewModel,
    onNavigate: (Route) -> Unit,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val playlistsViewModel = koinInject<LibraryPlaylistsViewModel>()

    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val selectedYtmFilter by viewModel.selectedYtmFilter.collectAsState()
    val albums by viewModel.sortedFilteredAlbums.collectAsState()
    val artists by viewModel.sortedFilteredArtists.collectAsState()
    val playlists by viewModel.sortedFilteredPlaylists.collectAsState()
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
        albums = albums,
        artists = artists,
        playlists = playlists,
        ytmState = ytmState,
    )

    val actions = remember(viewModel, onNavigate, playerViewModel) {
        LibraryActions(
            onTabSelected = viewModel::selectTab,
            onNavigate = onNavigate,
            onOpenStats = { onNavigate(Route.Stats) },
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
            onExportPlaylist = { playlistId ->
                playlistsViewModel.exportPlaylist(playlistId) { success, msg ->
                    // Manejo simple: log o snackbar (por ahora solo log)
                    println(if (success) "Exportado: $msg" else "Error export: $msg")
                }
            },
            onSearchQueryChange = viewModel::setSearchQuery,
            onClearSearch = viewModel::clearSearch,
            onSortOrderChange = viewModel::setSortOrder,
            onYtmFilterChange = viewModel::setYtmFilter,
        )
    }

    LibraryScreen(
        state = state,
        actions = actions,
        playerViewModel = playerViewModel,
    )

    var openExtractPage by remember { mutableStateOf(true) }
    if (showCsvTutorial) {
        AlertDialog(
            onDismissRequest = { showCsvTutorial = false },
            icon = { CsvImportTutorialIcon() },
            title = { Text(stringResource(Res.string.csv_tutorial_title)) },
            text = {
                Column {
                    CsvImportTutorialBody()
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { openExtractPage = !openExtractPage }
                            .padding(4.dp)
                    ) {
                        Checkbox(checked = openExtractPage, onCheckedChange = { openExtractPage = it })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(Res.string.csv_tutorial_open_page),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCsvTutorial = false
                    if (openExtractPage) {
                        NativeDesktop.browse(URI("https://www.tunemymusic.com/"))
                    }
                    playlistsViewModel.importCsvFile()
                }) { Text(if (openExtractPage) stringResource(Res.string.csv_tutorial_btn) else stringResource(Res.string.csv_import_open_picker)) }
            },
            dismissButton = {
                TextButton(onClick = { showCsvTutorial = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    (csvImportState as? CsvImportState.Ready)?.let { csvState ->
        if (importNameField.isBlank()) {
            importNameField = csvState.suggestedName
        }
        AlertDialog(
            onDismissRequest = { playlistsViewModel.cancelCsvImport() },
            title = { Text(stringResource(Res.string.csv_import_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.csv_songs_found, csvState.totalCount))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importNameField,
                        onValueChange = { importNameField = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.playlist_name_label)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = importNameField.trim().ifBlank { csvState.suggestedName }
                        playlistsViewModel.confirmCsvImport(name)
                    },
                ) { Text(stringResource(Res.string.csv_btn_import)) }
            },
            dismissButton = {
                TextButton(onClick = { playlistsViewModel.cancelCsvImport() }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (showDeletePlaylistDialog && playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeletePlaylistDialog = false
                playlistToDelete = null
            },
            title = { Text(stringResource(Res.string.delete_playlist_title)) },
            text = { Text(stringResource(Res.string.delete_playlist_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistToDelete?.let(viewModel::removePlaylist)
                        showDeletePlaylistDialog = false
                        playlistToDelete = null
                    },
                ) { Text(stringResource(Res.string.delete_playlist_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        playlistToDelete = null
                    },
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryScreenState,
    actions: LibraryActions,
    playerViewModel: PlayerViewModel? = null,
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showFilterMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.library_title),
                        style = MaterialTheme.typography.displaySmallEmphasized,
                    )
                },
                actions = {
                    IconButton(
                        onClick = actions.onOpenStats,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = stringResource(Res.string.stats_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = actions.onImportCsv,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = stringResource(Res.string.cd_import_csv),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = { showCreatePlaylistDialog = true },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = stringResource(Res.string.cd_create_playlist),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.ytmState !is YtmLibraryState.Idle) {
                        IconButton(
                            onClick = actions.onRefreshYtm,
                            enabled = state.ytmState !is YtmLibraryState.Loading,
                        ) {
                            if (state.ytmState is YtmLibraryState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(Res.string.refresh_ytm),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),

            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryTabRow(
                    selectedTab = state.selectedTab,
                    onTabSelected = actions.onTabSelected,
                    modifier = Modifier.weight(1f),
                )

                Box {
                    IconButton(
                        onClick = { showFilterMenu = true },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(Res.string.library_filter_sort),
                            tint = if (state.selectedYtmFilter != null || state.sortOrder != LibrarySortOrder.NAME_ASC)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                    ) {
                        // ── Sección de ordenamiento ──
                        MenuSectionHeader(stringResource(Res.string.library_section_sort))
                        LibrarySortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        order.displayName(),
                                        fontWeight = if (state.sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = { actions.onSortOrderChange(order); showFilterMenu = false },
                                leadingIcon = {
                                    if (state.sortOrder == order) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                },
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )

                        // ── Sección de biblioteca de YouTube Music ──
                        MenuSectionHeader(stringResource(Res.string.library_section_ytm))
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.filter_library), fontWeight = if (state.selectedYtmFilter == null) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { actions.onYtmFilterChange(null); showFilterMenu = false },
                            leadingIcon = {
                                if (state.selectedYtmFilter == null) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        )
                        YtmLibraryFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.displayName(), fontWeight = if (state.selectedYtmFilter == filter) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { actions.onYtmFilterChange(filter); showFilterMenu = false },
                                leadingIcon = {
                                    if (state.selectedYtmFilter == filter) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            when (state.selectedTab ?: LibraryTab.LIBRARY) {
                LibraryTab.ALBUMS -> AlbumsTab(
                    albums = state.albums,
                    ytmAlbums = (state.ytmState as? YtmLibraryState.Success)?.albums.orEmpty(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemoveAlbum,
                    onQuickPlayAlbum = actions.onQuickPlayAlbum,
                    onQuickShuffleAlbum = actions.onQuickShuffleAlbum,
                )

                LibraryTab.ARTISTS -> ArtistsTab(
                    artists = state.artists,
                    ytmArtists = (state.ytmState as? YtmLibraryState.Success)?.artists.orEmpty(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemoveArtist,
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
                )

                LibraryTab.LIBRARY -> LibraryMixedTab(
                    state = state,
                    onNavigate = actions.onNavigate,
                    playerViewModel = playerViewModel,
                    onRemovePlaylist = actions.onRemovePlaylist,
                    onQuickPlayAlbum = actions.onQuickPlayAlbum,
                    onQuickShuffleAlbum = actions.onQuickShuffleAlbum,
                    onQuickPlayPlaylist = actions.onQuickPlayPlaylist,
                    onQuickShufflePlaylist = actions.onQuickShufflePlaylist,
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(stringResource(Res.string.create_playlist_dialog)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.playlist_name_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            actions.onCreatePlaylist(name)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                ) { Text(stringResource(Res.string.btn_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

@Composable
private fun MenuSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun LibraryTabRow(
    selectedTab: LibraryTab?,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        LibraryTab.ALBUMS to stringResource(Res.string.tab_albums),
        LibraryTab.ARTISTS to stringResource(Res.string.tab_artists),
        LibraryTab.PLAYLISTS to stringResource(Res.string.tab_playlists),
    )

    HorizontalScrollableRow(
        modifier = modifier
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        tabs.forEach { (tab, label) ->
            val isSelected = selectedTab == tab
            item {
                FilterChip(
                    selected = isSelected,
                    leadingIcon = { if(isSelected)Icon(Icons.Default.Check,  contentDescription = null) },
                    onClick = { onTabSelected(if (isSelected) LibraryTab.LIBRARY else tab) },
                    label = {
                        Text(
                            label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    shape = RoundedCornerShape(50.dp),
                    border = null,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}
