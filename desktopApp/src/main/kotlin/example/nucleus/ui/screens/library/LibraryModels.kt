@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.library

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import example.nucleus.data.repository.LibraryViewMode
import example.nucleus.navigation.ScreenConfig
import example.nucleus.viewmodels.LibrarySortOrder
import example.nucleus.viewmodels.LibraryTab
import example.nucleus.viewmodels.YtmLibraryFilter
import example.nucleus.viewmodels.YtmLibraryState

data class LibraryScreenState(
    val selectedTab: LibraryTab? = null,
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.NAME_ASC,
    val selectedYtmFilter: YtmLibraryFilter? = null,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val podcasts: List<PodcastItem> = emptyList(),
    val ytmState: YtmLibraryState = YtmLibraryState.Idle,
)

data class LibraryActions(
    val onTabSelected: (LibraryTab) -> Unit,
    val onNavigate: (ScreenConfig) -> Unit,
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
    val onSearchQueryChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onSortOrderChange: (LibrarySortOrder) -> Unit,
    val onYtmFilterChange: (YtmLibraryFilter?) -> Unit,
    val onViewModeChange: (LibraryViewMode) -> Unit,
)
