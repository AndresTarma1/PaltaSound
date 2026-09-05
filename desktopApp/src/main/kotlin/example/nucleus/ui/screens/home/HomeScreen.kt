@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import example.nucleus.navigation.Route
import example.nucleus.ui.components.ChipRowSkeleton
import example.nucleus.ui.components.ExpressiveEmptyState
import example.nucleus.ui.components.HorizontalGridLikeRow
import example.nucleus.ui.components.SectionSkeleton
import example.nucleus.ui.components.layout.AppScreenContentHorizontal
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.layout.HorizontalScrollableRow
import example.nucleus.ui.components.layout.appScrollContentPadding
import example.nucleus.ui.helpers.rememberSongDownloadState
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.ctaLabel
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.viewmodels.HomeState
import example.nucleus.viewmodels.HomeUiEvent
import example.nucleus.viewmodels.HomeViewModel
import example.nucleus.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.HomePage
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import example.nucleus.ui.screens.shared.SectionGridItem
import example.nucleus.ui.screens.shared.SectionListItem
import androidx.compose.foundation.lazy.itemsIndexed

@Composable
fun HomeScreenRoute(
    viewModel: HomeViewModel,
    onNavigate: (Route) -> Unit,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val uiState by viewModel.uiState.collectAsState()
    val recentSongs by viewModel.recentSongs.collectAsState()

    HomeScreen(
        uiState = uiState,
        recentSongs = recentSongs,
        onEvent = viewModel::onEvent,
        onNavigate = onNavigate,
        playerViewModel = playerViewModel,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeState,
    recentSongs: List<SongItem> = emptyList(),
    onEvent: (HomeUiEvent) -> Unit,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        when (uiState) {

            is HomeState.Loading -> HomeScreenLoading(
                modifier = Modifier.padding(top = 16.dp)
            )

            is HomeState.Success -> HomeScreenContent(
                page = uiState.page,
                recentSongs = recentSongs,
                selectedParams = uiState.selectedParams,
                isLoadingMore = uiState.isLoadingMore,
                onChipClick = { params -> onEvent(HomeUiEvent.ChipSelected(params)) },
                onScrollNearEnd = { onEvent(HomeUiEvent.LoadMore) },
                onNavigate = onNavigate,
                playerViewModel = playerViewModel,
                contentPadding = appScrollContentPadding(
                    top = 16.dp,
                    bottom = LocalMiniPlayerInset.current,
                ),
            )

            is HomeState.Error -> HomeScreenError(
                message = uiState.message,
                isOffline = uiState.isOffline,
                onRetry = { onEvent(HomeUiEvent.Retry) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    page: HomePage,
    recentSongs: List<SongItem> = emptyList(),
    selectedParams: String?,
    isLoadingMore: Boolean,
    onChipClick: (String?) -> Unit,
    onScrollNearEnd: () -> Unit,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, page.sections.size, page.continuation, isLoadingMore) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(lastVisibleIndex, layoutInfo.totalItemsCount, listState.canScrollForward)
        }.collect { (lastVisibleIndex, totalItemsCount, canScrollForward) ->
            val isNearEnd = totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 2
            // If the first response does not fill the viewport, there is no user scroll
            // event to trigger pagination. Request the next page until the list can scroll.
            val viewportNeedsMore = totalItemsCount > 0 && !canScrollForward
            if ((isNearEnd || viewportNeedsMore) && !isLoadingMore && page.continuation != null) {
                onScrollNearEnd()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {


        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            // 4. Una sola fila para todos los chips
            if (!page.chips.isNullOrEmpty()) {
                item {
                    ChipFilterRow(
                        chips = page.chips!!,
                        selectedParams = selectedParams,
                        onChipClick = onChipClick,
                    )
                }
            }

            if (recentSongs.isNotEmpty()) {
                item {
                    QuickPicksSection(
                        songs = recentSongs,
                        playerViewModel = playerViewModel,
                    )
                }
            }

            itemsIndexed(
                page.sections,
                // Continuation pages may contain shelves with the same title as an
                // earlier page. The title alone is not a stable unique key.
                key = { index, section -> "$index:${section.title}" }) { _, section ->
                HomeSectionRow(
                    section = section,
                    onNavigate = onNavigate,
                    playerViewModel = playerViewModel,
                )

            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        AppVerticalScrollbar(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

// Sub-composables tontos — solo renderizan, no deciden
@Composable
private fun ChipFilterRow(
    chips: List<HomePage.Chip>,
    selectedParams: String?,
    onChipClick: (String?) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Column {
        HorizontalScrollableRow(
            modifier = Modifier.padding(vertical = 10.dp),
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = AppScreenContentHorizontal),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips.size) { index ->
                val chip = chips[index]
                val isSelected = chip.endpoint?.params == selectedParams
                FilterChip(
                    selected = isSelected,
                    onClick = { onChipClick(chip.endpoint?.params) },
                    label = {
                        Text(
                            chip.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(Res.string.selected),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionRow(
    section: HomePage.Section,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel,
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.padding(horizontal = AppScreenContentHorizontal, vertical = 4.dp),
        )

        val rows = section.numItemsPerColumn ?: 1
        if (rows > 1) {
            // Por si YouTube nos da una sección de 2 filas, renderizamos un grid-like horizontal con 2 o mas filas
            HorizontalGridLikeRow(
                items = section.items,
                rows = rows,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = AppScreenContentHorizontal, vertical = 4.dp),
                columnWidth = 320.dp,
                rowSpacing = 8.dp,
                columnSpacing = 12.dp,
                itemKey = { it.id },
            ) { item ->
                SectionListItem(
                    item = item,
                    onNavigate = onNavigate,
                    playerViewModel = playerViewModel,
                )
            }
        } else {
            val sectionScrollState = rememberLazyListState()
            HorizontalScrollableRow(
                modifier = Modifier.fillMaxWidth(),
                state = sectionScrollState,
                contentPadding = PaddingValues(horizontal = AppScreenContentHorizontal, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = section.items.size,
                    key = { index -> section.items[index].id }
                ) { index ->
                    SectionGridItem(
                        item = section.items[index],
                        onNavigate = onNavigate,
                        playerViewModel = playerViewModel,
                    )
                }
            }
        }
    }
}


/**
 * Renderizar la sección de "Quick Picks" (canciones recientes) en la pantalla de inicio.
 *
 * @param songs Los sonidos que nos devuelven.
 */
@Composable
private fun QuickPicksSection(
    songs: List<SongItem>,
    playerViewModel: PlayerViewModel,
) {
    val rowCount = 2
    val itemHeight = 64.dp
    val rowSpacing = 8.dp
    val columnWidth = 320.dp

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = stringResource(Res.string.recently_played),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.padding(horizontal = AppScreenContentHorizontal, vertical = 4.dp),
        )
        HorizontalGridLikeRow(
            items = songs,
            rows = rowCount,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AppScreenContentHorizontal, vertical = 8.dp),
            columnWidth = columnWidth,
            rowSpacing = rowSpacing,
            columnSpacing = 12.dp,
            itemKey = { it.id }
        ) { song ->

            SectionListItem(
                item = song,
                playerViewModel = playerViewModel,
                modifier = Modifier.fillMaxWidth().height(itemHeight)
            )
        }
    }
}

// Loading / Error — sin cambios de lógica
@Composable
fun HomeScreenLoading(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ChipRowSkeleton()
        repeat(3) { SectionSkeleton() }
    }
}

@Composable
fun HomeScreenError(message: String, isOffline: Boolean = false, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExpressiveEmptyState(
            icon = if (isOffline) Icons.Default.WifiOff else Icons.Default.ErrorOutline,
            title = stringResource(if (isOffline) Res.string.home_offline_title else Res.string.home_error),
            subtitle = if (isOffline) stringResource(Res.string.home_offline_message) else message,
            modifier = Modifier.weight(1f, fill = false).heightIn(max = 280.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRetry,
            shape = AppShapes.extraLarge,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            Text(
                stringResource(Res.string.home_retry),
                style = MaterialTheme.typography.ctaLabel,
            )
        }
    }
}
