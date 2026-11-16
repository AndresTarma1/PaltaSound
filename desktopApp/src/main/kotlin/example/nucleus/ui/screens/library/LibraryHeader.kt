@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import example.nucleus.data.repository.LibraryViewMode
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.cd_create_playlist
import example.nucleus.generated.resources.cd_import_csv
import example.nucleus.generated.resources.filter_all
import example.nucleus.generated.resources.filter_library
import example.nucleus.generated.resources.library_clear_search
import example.nucleus.generated.resources.library_filter_sort
import example.nucleus.generated.resources.library_search_hint
import example.nucleus.generated.resources.library_section_sort
import example.nucleus.generated.resources.library_section_ytm
import example.nucleus.generated.resources.library_view_grid
import example.nucleus.generated.resources.library_view_list
import example.nucleus.generated.resources.refresh_ytm
import example.nucleus.generated.resources.stats_title
import example.nucleus.generated.resources.tab_albums
import example.nucleus.generated.resources.tab_artists
import example.nucleus.generated.resources.tab_playlists
import example.nucleus.generated.resources.tab_podcasts
import example.nucleus.generated.resources.tab_songs
import example.nucleus.ui.components.glass.GlassDropdownMenu
import example.nucleus.ui.components.glass.GlassFilterChip
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.viewmodels.LibrarySortOrder
import example.nucleus.viewmodels.LibraryTab
import example.nucleus.viewmodels.YtmLibraryFilter
import example.nucleus.viewmodels.YtmLibraryState
import org.jetbrains.compose.resources.stringResource

/**
 * Cabecera de la biblioteca: pestañas, orden, búsqueda, filtros y vista.
 * Va como primer elemento del scroll de cada pestaña (no fija arriba).
 */
@Composable
internal fun LibraryHeader(
    state: LibraryScreenState,
    actions: LibraryActions,
    onCreatePlaylistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSearch by remember(state.searchQuery) { mutableStateOf(state.searchQuery.isNotBlank()) }

    Column(modifier = modifier.fillMaxWidth()) {
        LibraryTabRow(
            selectedTab = state.selectedTab,
            onTabSelected = actions.onTabSelected,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Orden: chip compacto con su menú.
            Box {
                GlassFilterChip(
                    selected = false,
                    onClick = { showSortMenu = true },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.sortOrder.displayName())
                            Icon(Icons.Default.ExpandMore, contentDescription = null)
                        }
                    },
                    shape = RoundedCornerShape(50.dp),
                    showBorder = false,
                )
                GlassDropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        stringResource(Res.string.library_section_sort),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LibrarySortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    order.displayName(),
                                    fontWeight = if (state.sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = { actions.onSortOrderChange(order); showSortMenu = false },
                            leadingIcon = {
                                if (state.sortOrder == order) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            },
                        )
                    }
                }
            }
            IconButton(
                onClick = { showSearch = !showSearch },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(Res.string.library_search_hint),
                    tint = if (showSearch || state.searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            // Filtros YTM.
            Box {
                IconButton(
                    onClick = { showFilterMenu = true },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = stringResource(Res.string.library_filter_sort),
                        tint = if (state.selectedYtmFilter != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GlassDropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        stringResource(Res.string.library_section_ytm),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
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
            // Más acciones (antes en el TopBar).
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                GlassDropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.stats_title)) },
                        leadingIcon = { Icon(Icons.Default.BarChart, null) },
                        onClick = { actions.onOpenStats(); showMoreMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.cd_import_csv)) },
                        leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                        onClick = { actions.onImportCsv(); showMoreMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.cd_create_playlist)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        onClick = { onCreatePlaylistClick(); showMoreMenu = false },
                    )
                    if (state.ytmState !is YtmLibraryState.Idle) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.refresh_ytm)) },
                            leadingIcon = {
                                if (state.ytmState is YtmLibraryState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, null)
                                }
                            },
                            enabled = state.ytmState !is YtmLibraryState.Loading,
                            onClick = { actions.onRefreshYtm(); showMoreMenu = false },
                        )
                    }
                }
            }
            // Vista lista / parrilla como grupo conectado. Los textos se izan
            // porque el content de ButtonGroup no es @Composable.
            val listActive = state.viewMode == LibraryViewMode.LIST
            val listLabel = stringResource(Res.string.library_view_list)
            val gridLabel = stringResource(Res.string.library_view_grid)
            ButtonGroup(
                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(menuState = it) },
                expandedRatio = 0f,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                toggleableItem(
                    checked = listActive,
                    onCheckedChange = { _ -> actions.onViewModeChange(LibraryViewMode.LIST) },
                    label = listLabel,
                    icon = {
                        Icon(Icons.Default.ViewList, listLabel)
                    },
                )
                toggleableItem(
                    checked = !listActive,
                    onCheckedChange = { _ -> actions.onViewModeChange(LibraryViewMode.GRID) },
                    label = gridLabel,
                    icon = {
                        Icon(Icons.Default.GridView, gridLabel)
                    },
                )
            }
        }
        AnimatedVisibility(visible = showSearch || state.searchQuery.isNotBlank()) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = actions.onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text(stringResource(Res.string.library_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = actions.onClearSearch) {
                            Icon(Icons.Default.Close, stringResource(Res.string.library_clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50.dp),
            )
        }
        state.selectedYtmFilter?.let { filter ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassFilterChip(
                    selected = true,
                    onClick = { actions.onYtmFilterChange(null) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(filter.displayName())
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(Res.string.library_clear_search),
                            )
                        }
                    },
                    shape = RoundedCornerShape(50.dp),
                    showBorder = false,
                )
            }
        }
    }
}
