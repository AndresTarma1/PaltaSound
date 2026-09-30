@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.db.entities.SearchHistoryEntry
import example.nucleus.navigation.Route
import example.nucleus.ui.components.ExpressiveEmptyState
import example.nucleus.ui.components.layout.AppScrollbarGutter
import example.nucleus.ui.components.layout.AppScreenContentHorizontal
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.ChipRowSkeleton
import example.nucleus.ui.components.layout.HorizontalScrollableRow
import example.nucleus.ui.components.layout.appScrollContentPadding
import example.nucleus.ui.components.SongSkeleton
import example.nucleus.ui.screens.shared.SectionGridItem
import example.nucleus.ui.screens.shared.SectionListItem
import example.nucleus.ui.screens.shared.onYTItemClick
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.viewmodels.PlayerViewModel
import example.nucleus.viewmodels.SearchState
import example.nucleus.viewmodels.SearchViewModel
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.MoodAndGenres
import example.nucleus.generated.resources.*
import example.nucleus.ui.components.CustomLabeledCard
import example.nucleus.ui.components.HorizontalGridLikeRow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.modifier.thenIf

// NOTE: `onHover` comes from org.jetbrains.jewel, which targets desktop only.
// If this file is compiled for Android/iOS in a shared source set, this import
// will fail there. If that's the case, move SuggestionListItem/HistoryListItem's
// hover behavior behind an expect/actual, or relocate this file to desktopMain.

data class SearchScreenState(
    val uiState: SearchState = SearchState.Idle,
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val filter: YouTube.SearchFilter? = null,
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val charts: ChartsPage? = null,
    val explore: ExplorePage? = null,
    val moodAndGenres: List<MoodAndGenres> = emptyList(),
)

data class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onFilterChange: (YouTube.SearchFilter?) -> Unit,
    val onLoadMore: () -> Unit,
    val onNavigate: (Route) -> Unit,
    val onDeleteHistoryEntry: (String) -> Unit,
    val onClearHistory: () -> Unit,
)

@Composable
fun SearchScreenRoute(
    viewModel: SearchViewModel,
    onNavigate: (Route) -> Unit,
) {
    val playerViewModel = LocalPlayerViewModel.current

    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val charts by viewModel.charts.collectAsState()
    val explore by viewModel.explore.collectAsState()
    val moodAndGenres by viewModel.moodAndGenres.collectAsState()

    val state = SearchScreenState(
        uiState = uiState,
        query = query,
        suggestions = suggestions,
        filter = filter,
        searchHistory = searchHistory,
        charts = charts,
        explore = explore,
        moodAndGenres = moodAndGenres,
    )

    val actions = remember(viewModel, onNavigate) {
        SearchActions(
            onQueryChange = { viewModel.onQueryChange(it) },
            onSearch = { viewModel.search() },
            onFilterChange = { viewModel.onFilterChange(it) },
            onLoadMore = { viewModel.searchContinuation() },
            onNavigate = onNavigate,
            onDeleteHistoryEntry = { viewModel.deleteHistoryEntry(it) },
            onClearHistory = { viewModel.clearHistory() },
        )
    }

    SearchScreen(
        state = state,
        actions = actions,
        playerViewModel = playerViewModel,
    )
}

@Composable
fun SearchScreen(
    state: SearchScreenState,
    actions: SearchActions,
    playerViewModel: PlayerViewModel,
) {
    var active by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchSection(
                query = state.query,
                active = active,
                suggestions = state.suggestions,
                searchHistory = state.searchHistory,
                onActiveChange = { active = it },
                onQueryChange = actions.onQueryChange,
                onSearch = {
                    actions.onSearch()
                    active = false
                },
                onDeleteHistoryEntry = actions.onDeleteHistoryEntry,
                onClearHistory = actions.onClearHistory,
            )


            ResultsList(
                uiState = state.uiState,
                charts = state.charts,
                explore = state.explore,
                moodAndGenres = state.moodAndGenres,
                showFilterRow = true,
                filter = state.filter,
                playerViewModel = playerViewModel,
                onItemClick = { item -> onYTItemClick(item, actions.onNavigate, playerViewModel) },
                onMoodClick = { browseId, params -> actions.onNavigate(Route.YouTubeBrowse(browseId, params)) },
                onFilterChange = actions.onFilterChange,
                onLoadMore = actions.onLoadMore,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSection(
    query: String,
    active: Boolean,
    suggestions: List<String>,
    searchHistory: List<SearchHistoryEntry>,
    onActiveChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDeleteHistoryEntry: (String) -> Unit,
    onClearHistory: () -> Unit,
)  {
    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = if (active) 0.dp else 16.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onActiveChange(false)
                    true
                } else {
                    false
                }
            },
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onSearch(query) },
                expanded = active,
                onExpandedChange = onActiveChange,
                placeholder = {
                    Text(
                        stringResource(Res.string.search_placeholder),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cd_clear))
                        }
                    }
                }
            )
        },
        expanded = active,
        onExpandedChange = onActiveChange,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp + LocalMiniPlayerInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (query.isEmpty()) {
                if (searchHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionHeader(stringResource(Res.string.recent_searches))
                            Text(
                                text = stringResource(Res.string.clear_all),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onClearHistory() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    items(searchHistory, key = { it.query }) { entry ->
                        HistoryListItem(
                            query = entry.query,
                            onClick = {
                                onQueryChange(entry.query)
                                onSearch(entry.query)
                            },
                            onDelete = { onDeleteHistoryEntry(entry.query) },
                        )
                    }
                } else {
                    item { SectionHeader(stringResource(Res.string.recent_searches)) }
                    item { EmptyStateText() }
                }
            } else {
                item { SectionHeader(stringResource(Res.string.suggestions_title)) }

                items(suggestions.take(8), key = { it }) { suggestion ->
                    SuggestionListItem(
                        suggestion = suggestion,
                        onClick = {
                            onQueryChange(suggestion)
                            onSearch(suggestion)
                        },
                    )
                }

                if (suggestions.isEmpty() && query.length >= 2) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLargeEmphasized.copy(
            letterSpacing = 1.2.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = AppScreenContentHorizontal,
            top = 8.dp,
            bottom = 12.dp,
        ),
    )
}

@Composable
private fun SuggestionListItem(suggestion: String, onClick: () -> Unit) {
    var isHover by remember { mutableStateOf(false) }
    val bgColor = if (isHover) MaterialTheme.colorScheme.onSurface.copy(0.12f) else Color.Transparent
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .onHover { isHover = it }
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.NorthWest,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun HistoryListItem(query: String, onClick: () -> Unit, onDelete: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val bgColor = if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent

    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .onHover { isHovered = it }
            .clickable(onClick = onClick)
            .background(bgColor),
        headlineContent = {
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.cd_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun EmptyStateText() {
    Text(
        text = stringResource(Res.string.no_recent_searches),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun FilterRow(
    selectedFilter: YouTube.SearchFilter?,
    onFilterSelected: (YouTube.SearchFilter?) -> Unit,
) {
    val filters = listOf(
        stringResource(Res.string.filter_all) to null,
        stringResource(Res.string.filter_videos) to YouTube.SearchFilter.FILTER_VIDEO,
        stringResource(Res.string.filter_songs) to YouTube.SearchFilter.FILTER_SONG,
        stringResource(Res.string.filter_albums) to YouTube.SearchFilter.FILTER_ALBUM,
        stringResource(Res.string.filter_artists) to YouTube.SearchFilter.FILTER_ARTIST,
        stringResource(Res.string.filter_playlists) to YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST,
        stringResource(Res.string.filter_podcasts) to YouTube.SearchFilter.FILTER_PODCAST,
        stringResource(Res.string.filter_episodes) to YouTube.SearchFilter.FILTER_EPISODE,
    )

    HorizontalScrollableRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = AppScreenContentHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters) { (label, f) ->
            val isSelected = selectedFilter == f
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(f) },
                label = {
                    Text(
                        label,
                        style = if (isSelected) {
                            MaterialTheme.typography.labelLargeEmphasized
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                    )
                },
                shape = AppShapes.extraLarge,
            )
        }
    }
}

@Composable
fun ResultsList(
    uiState: SearchState,
    charts: ChartsPage?,
    explore: ExplorePage? = null,
    moodAndGenres: List<MoodAndGenres> = emptyList(),
    playerViewModel: PlayerViewModel,
    onItemClick: (YTItem) -> Unit,
    onMoodClick: (String, String?) -> Unit = { _, _ -> },
    showFilterRow: Boolean = true,
    filter: YouTube.SearchFilter?,
    onFilterChange: (YouTube.SearchFilter?) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    // ---- Datos derivados (una sola vez por cambio de estado) ----
    val items = remember(uiState) {
        (uiState as? SearchState.Success)?.items.orEmpty().distinctBy { it.id }
    }
    val summaries = remember(uiState) {
        (uiState as? SearchState.SummarySuccess)?.summary?.summaries.orEmpty()
    }
    val hasItems = items.isNotEmpty() || summaries.isNotEmpty()

    // ---- Volver arriba al cambiar de filtro o al llegar una búsqueda nueva ----
    // Se clavea por el primer id para no resetear al paginar.
    val contentKey = items.firstOrNull()?.id
        ?: summaries.firstOrNull()?.items?.firstOrNull()?.id
    LaunchedEffect(filter, contentKey) {
        listState.scrollToItem(0)
    }

    // ---- Paginación ----
    val currentState by rememberUpdatedState(uiState)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val shouldLoadMore by remember {
        derivedStateOf {
            val state = currentState
            if (state !is SearchState.Success) return@derivedStateOf false

            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0

            lastVisible >= info.totalItemsCount - 3 &&
                    !state.isLoadingMore &&
                    state.continuation != null
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    // ---- UI ----
    Column(Modifier.fillMaxSize()) {
        // La fila de filtros queda fija: siempre se puede cambiar de filtro,
        // incluso con "sin resultados" o con error.
        if (showFilterRow && uiState !is SearchState.Idle) {
            FilterRow(selectedFilter = filter, onFilterSelected = onFilterChange)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState is SearchState.Idle -> {
                    SearchChartsContent(
                        charts = charts,
                        explore = explore,
                        moodAndGenres = moodAndGenres,
                        playerViewModel = playerViewModel,
                        onItemClick = onItemClick,
                        onMoodClick = onMoodClick,
                    )
                }

                uiState is SearchState.Error -> {
                    EmptyStateView(
                        icon = Icons.Default.ErrorOutline,
                        message = stringResource(Res.string.something_went_wrong),
                    )
                }

                uiState !is SearchState.Loading && !hasItems -> {
                    EmptyStateView(
                        icon = Icons.Default.Search,
                        message = stringResource(Res.string.no_results),
                    )
                }

                else -> {
                    val isLoading = uiState is SearchState.Loading

                    // Un solo LazyColumn para Loading y resultados: mismo padding,
                    // sin saltos visuales al pasar de uno a otro.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = appScrollContentPadding(
                            end = AppScrollbarGutter + 4.dp,
                            bottom = LocalMiniPlayerInset.current,
                        ),
                    ) {
                        if (isLoading) {
                            items(10, key = { "skeleton_$it" }) { SongSkeleton() }
                        } else {
                            summaries.forEachIndexed { index, summary ->
                                item(key = "header_$index") {
                                    SectionHeader(summary.title)
                                }
                                items(
                                    items = summary.items,
                                    // La misma canción puede salir en dos secciones,
                                    // por eso la key incluye la sección.
                                    key = { "${index}_${it.id}" },
                                ) { item ->
                                    ResultRow(item, playerViewModel, onItemClick)
                                }
                            }

                            items(items = items, key = { it.id }) { item ->
                                ResultRow(item, playerViewModel, onItemClick)
                            }

                            if (uiState is SearchState.Success && uiState.isLoadingMore) {
                                item(key = "loading_more") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isLoading) {
                        AppVerticalScrollbar(
                            state = listState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    item: YTItem,
    playerViewModel: PlayerViewModel,
    onItemClick: (YTItem) -> Unit,
) {
    SectionListItem(
        item = item,
        onNavigate = { onItemClick(item) },
        playerViewModel = playerViewModel,
        modifier = Modifier.padding(horizontal = AppScreenContentHorizontal - 8.dp),
        dividerBelow = true,
    )
}

@Composable
private fun SearchChartsContent(
    charts: ChartsPage?,
    explore: ExplorePage? = null,
    moodAndGenres: List<MoodAndGenres> = emptyList(),
    playerViewModel: PlayerViewModel,
    onItemClick: (YTItem) -> Unit,
    onMoodClick: (String, String?) -> Unit = { _, _ -> },
) {
    if (charts == null && explore == null && moodAndGenres.isEmpty()) {
        EmptyStateView(Icons.AutoMirrored.Filled.TrendingUp, stringResource(Res.string.explore_trends))
        return
    }

    Box(Modifier.fillMaxSize()) {
    val chartsScroll = rememberLazyListState()
    LazyColumn(
        state = chartsScroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = appScrollContentPadding(
            start = AppScreenContentHorizontal,
            end = AppScrollbarGutter + AppScreenContentHorizontal,
            top = 16.dp,
            bottom = LocalMiniPlayerInset.current + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        explore?.newReleaseAlbums?.let { albums ->
            if (albums.isNotEmpty()) {
                item {
                    SearchAlbumGrid(
                        title = stringResource(Res.string.new_releases),
                        albums = albums,
                        playerViewModel = playerViewModel,
                        onItemClick = onItemClick,
                    )
                }
            }
        }

        explore?.moodAndGenres?.let { moods ->
            if (moods.isNotEmpty()) {
                item { SearchMoodGrid(moods = moods, onMoodClick = onMoodClick) }
            }
        }
    }

        AppVerticalScrollbar(
            state = chartsScroll,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun SearchAlbumGrid(
    title: String,
    albums: List<AlbumItem>,
    playerViewModel: PlayerViewModel,
    onItemClick: (YTItem) -> Unit,
) {

    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Album, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalScrollableRow(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) {

            items(albums, key = { it.id }) { album ->
                SectionGridItem(
                    item = album,
                    onNavigate = { onItemClick(album) },
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}


@Composable
private fun SearchMoodGrid(
    moods: List<MoodAndGenres.Item>,
    onMoodClick: (String, String?) -> Unit = { _, _ -> },
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp) // Alineación opcional para el título
        ) {
            Icon(
                Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(Res.string.moods_genres),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Misma mecánica que Quick Picks del Home: LazyRow con columnas agrupadas +
        // scrollbar nativo estilizado (HorizontalScrollableRow). Los CustomLabeledCard
        // miden 260x80dp, así que 4 filas dan la misma altura que antes sin recortes.
        HorizontalGridLikeRow(
            items = moods,
            rows = 4,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            columnWidth = 260.dp,
            rowSpacing = 12.dp,
            columnSpacing = 16.dp,
            itemKey = { it.title },
        ) { mood ->
            CustomLabeledCard(
                text = mood.title,
                borderColor = Color(mood.stripeColor),
                onClick = { onMoodClick(mood.endpoint.browseId, mood.endpoint.params) },
            )
        }
    }
}

@Composable
fun EmptyStateView(icon: ImageVector, message: String) {
    ExpressiveEmptyState(
        icon = icon,
        title = message,
    )
}