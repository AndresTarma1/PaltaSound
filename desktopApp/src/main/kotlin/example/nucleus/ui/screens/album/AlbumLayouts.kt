package example.nucleus.ui.screens.album

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.dialogs.DownloadConfirmationDialog
import example.nucleus.navigation.Route
import example.nucleus.ui.components.layout.AppScrollbarGutter
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.layout.appScrollContentPadding
import example.nucleus.ui.components.LoadingMoreSongsItem
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.ui.screens.playlist.MultiSongSelectionBar
import example.nucleus.ui.screens.playlist.SongListItem
import example.nucleus.ui.screens.shared.CollectionCompactCoverSize
import example.nucleus.ui.screens.shared.CollectionHeroActionRow
import example.nucleus.ui.screens.shared.CollectionStickyHeaderBar
import example.nucleus.ui.screens.shared.CollectionWideCoverSize
import example.nucleus.ui.screens.shared.CollectionWideGap
import example.nucleus.ui.screens.shared.CollectionWideHeroWidth
import example.nucleus.ui.screens.shared.CollectionWidePaddingBottom
import example.nucleus.ui.screens.shared.CollectionWidePaddingStart
import example.nucleus.ui.screens.shared.CollectionWidePaddingTop
import example.nucleus.ui.screens.shared.calculateTotalDuration
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.collectionTitle
import example.nucleus.ui.themes.ctaLabel
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.utils.LocalPlayerViewModel
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource


@Composable
internal fun AlbumScreenLayout(
    albumPage: AlbumPage,
    state: AlbumScreenState,
    actions: AlbumScreenActions,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    val songIds = remember(state.songs) { state.songs.map { it.id } }
    val isFullyDownloadedState = remember(songIds, downloadViewModel) {
        downloadViewModel.isFullyDownloadedFlow(songIds)
    }.collectAsState(initial = false)
    val isAnyDownloadingState = remember(songIds, downloadViewModel) {
        downloadViewModel.isAnyDownloadingFlow(songIds)
    }.collectAsState(initial = false)

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DownloadConfirmationDialog(
            onConfirm = {
                downloadViewModel.removeDownloads(songIds)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    val controls = AlbumInfoPanelControls(
        isSaved = state.isSaved,
        isSaving = state.isSaving,
        isLoadingForPlay = state.isLoadingForPlay,
    )

    // Estado de selección múltiple
    var selectedSongIds by remember(state.songs) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedSongs = remember(state.songs, selectedSongIds) {
        state.songs.filter { it.id in selectedSongIds }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < 980.dp) {
            AlbumCompactLayout(
                albumPage = albumPage,
                songs = state.songs,
                hasMore = state.hasMore,
                controls = controls,
                isAnyDownloading = isAnyDownloadingState.value,
                isFullyDownloaded = isFullyDownloadedState.value,
                selectedSongIds = selectedSongIds,
                selectedSongs = selectedSongs,
                onSelectionChange = { id, selected ->
                    selectedSongIds = if (selected) selectedSongIds + id else selectedSongIds - id
                },
                onClearSelection = { selectedSongIds = emptySet() },
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariant = onSurfaceVariant,
                onLoadMore = actions.onLoadMore,
                onSongClick = { index ->
                    if (selectedSongIds.isNotEmpty()) {
                        selectedSongIds = if (state.songs[index].id in selectedSongIds) {
                            selectedSongIds - state.songs[index].id
                        } else {
                            selectedSongIds + state.songs[index].id
                        }
                    } else {
                        playerViewModel.playAlbum(state.songs, index, albumPage.album.browseId, albumPage.album.title)
                    }
                },
                onToggleSave = actions.onToggleSave,
                onPlayAll = actions.onPlayAll,
                onShuffle = actions.onShuffle,
                showDeleteDialog = { showDeleteDialog = true },
                onNavigate = actions.onNavigate,
            )
        } else {
            AlbumWideLayout(
                albumPage = albumPage,
                songs = state.songs,
                hasMore = state.hasMore,
                controls = controls,
                isAnyDownloading = isAnyDownloadingState.value,
                isFullyDownloaded = isFullyDownloadedState.value,
                selectedSongIds = selectedSongIds,
                selectedSongs = selectedSongs,
                onSelectionChange = { id, selected ->
                    selectedSongIds = if (selected) selectedSongIds + id else selectedSongIds - id
                },
                onClearSelection = { selectedSongIds = emptySet() },
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariant = onSurfaceVariant,
                onLoadMore = actions.onLoadMore,
                onSongClick = { index ->
                    if (selectedSongIds.isNotEmpty()) {
                        selectedSongIds = if (state.songs[index].id in selectedSongIds) {
                            selectedSongIds - state.songs[index].id
                        } else {
                            selectedSongIds + state.songs[index].id
                        }
                    } else {
                        playerViewModel.playAlbum(state.songs, index, albumPage.album.browseId, albumPage.album.title)
                    }
                },
                onToggleSave = actions.onToggleSave,
                onPlayAll = actions.onPlayAll,
                onShuffle = actions.onShuffle,
                showDeleteDialog = { showDeleteDialog = true },
                onNavigate = actions.onNavigate,
            )
        }
    }
}


@Composable
internal fun AlbumWideLayout(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    hasMore: Boolean,
    controls: AlbumInfoPanelControls,
    isAnyDownloading: Boolean,
    isFullyDownloaded: Boolean,
    selectedSongIds: Set<String>,
    selectedSongs: List<SongItem>,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    onLoadMore: () -> Unit,
    onSongClick: (index: Int) -> Unit,
    onToggleSave: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    showDeleteDialog: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    val listState = rememberLazyListState()

    // Scrollbar al borde derecho de la pantalla (misma geometría que playlist).
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = CollectionWidePaddingStart,
                    end = AppScrollbarGutter,
                    top = CollectionWidePaddingTop,
                    bottom = CollectionWidePaddingBottom,
                ),
            horizontalArrangement = Arrangement.spacedBy(CollectionWideGap),
        ) {
            Column(
                modifier = Modifier
                    .width(CollectionWideHeroWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AlbumInfoPanel(
                    albumPage = albumPage,
                    songs = songs,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariant = onSurfaceVariant,
                    coverSize = CollectionWideCoverSize,
                    controls = controls,
                    isAnyDownloading = isAnyDownloading,
                    isFullyDownloaded = isFullyDownloaded,
                    onToggleSave = onToggleSave,
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                    showDeleteDialog = showDeleteDialog,
                    onNavigate = onNavigate,
                )
            }

            AlbumSongList(
                modifier = Modifier.weight(1f),
                listState = listState,
                songs = songs,
                hasMore = hasMore,
                selectedSongIds = selectedSongIds,
                onSelectionChange = onSelectionChange,
                onClearSelection = onClearSelection,
                selectedSongs = selectedSongs,
                isLocalPlaylist = false,
                onRemoveFromPlaylist = null,
                onLoadMore = onLoadMore,
                onSongClick = onSongClick,
            )
        }

        AppVerticalScrollbar(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}


@Composable
internal fun AlbumCompactLayout(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    hasMore: Boolean,
    controls: AlbumInfoPanelControls,
    isAnyDownloading: Boolean,
    isFullyDownloaded: Boolean,
    selectedSongIds: Set<String>,
    selectedSongs: List<SongItem>,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    onLoadMore: () -> Unit,
    onSongClick: (index: Int) -> Unit,
    onToggleSave: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    showDeleteDialog: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        val lazyColumnState = rememberLazyListState()
        val downloadViewModel = LocalDownloadViewModel.current

        val showStickHeader by remember {
            derivedStateOf { lazyColumnState.firstVisibleItemIndex > 0 }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyColumnState,
            contentPadding = appScrollContentPadding(
                bottom = maxOf(
                    if (selectedSongIds.isNotEmpty()) 72.dp else 0.dp,
                    LocalMiniPlayerInset.current,
                ),
            ),
        ) {
            if (showStickHeader) {
                stickyHeader {
                    CollectionStickyHeaderBar(
                        title = albumPage.album.title,
                        isDownloading = isAnyDownloading,
                        isFullyDownloaded = isFullyDownloaded,
                        onDownloadClick = {
                            if (isFullyDownloaded) {
                                showDeleteDialog()
                            } else if (!isAnyDownloading) {
                                downloadViewModel.downloadAll(songs)
                            }
                        },
                        onPlayClick = onPlayAll,
                        playContentDescription = stringResource(Res.string.cd_play),
                    )
                }
            }

            // Info panel sin scroll propio: se desplaza con la Column
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(16.dp))
                    AlbumInfoPanel(
                        albumPage = albumPage,
                        songs = songs,
                        onSurfaceColor = onSurfaceColor,
                        onSurfaceVariant = onSurfaceVariant,
                        coverSize = CollectionCompactCoverSize,
                        controls = controls,
                        isAnyDownloading = isAnyDownloading,
                        isFullyDownloaded = isFullyDownloaded,
                        onToggleSave = onToggleSave,
                        onPlayAll = onPlayAll,
                        onShuffle = onShuffle,
                        showDeleteDialog = showDeleteDialog,
                        onNavigate = onNavigate,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongListItem(
                    albumIndex = index + 1,
                    song = song,
                    onPlay = { onSongClick(index) },
                    isSelected = song.id in selectedSongIds,
                    selectionMode = selectedSongIds.isNotEmpty(),
                    onSelectionChange = { selected ->
                        onSelectionChange(song.id, selected)
                    },
                    isLocalPlaylist = false,
                    onRemoveFromPlaylist = null,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            }

            if (hasMore) item { LoadingMoreSongsItem(onLoadMore = onLoadMore) }
        }

        AppVerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            state = lazyColumnState,
        )

        MultiSongSelectionBar(
            selectedSongs = selectedSongs,
            allSongIds = songs.map { it.id },
            isLocalPlaylist = false,
            onClearSelection = onClearSelection,
            onSelectAll = { songs.forEach { onSelectionChange(it.id, true) } },
            onRemoveFromPlaylist = null,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}


@Composable
private fun AlbumSongList(
    modifier: Modifier,
    listState: LazyListState,
    songs: List<SongItem>,
    hasMore: Boolean,
    selectedSongIds: Set<String>,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    selectedSongs: List<SongItem>,
    isLocalPlaylist: Boolean,
    onRemoveFromPlaylist: ((String) -> Unit)?,
    onLoadMore: () -> Unit,
    onSongClick: (index: Int) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = maxOf(
                    if (selectedSongIds.isNotEmpty()) 72.dp else 0.dp,
                    LocalMiniPlayerInset.current,
                ),
            ),
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongListItem(
                    albumIndex = index + 1,
                    song = song,
                    onPlay = { onSongClick(index) },
                    isSelected = song.id in selectedSongIds,
                    selectionMode = selectedSongIds.isNotEmpty(),
                    onSelectionChange = { selected ->
                        onSelectionChange(song.id, selected)
                    },
                    isLocalPlaylist = isLocalPlaylist,
                    onRemoveFromPlaylist = onRemoveFromPlaylist,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 48.dp),
                    )
                }
            }

            if (hasMore) item { LoadingMoreSongsItem(onLoadMore = onLoadMore) }
        }

        MultiSongSelectionBar(
            selectedSongs = selectedSongs,
            allSongIds = songs.map { it.id },
            isLocalPlaylist = isLocalPlaylist,
            onClearSelection = onClearSelection,
            onSelectAll = { songs.forEach { onSelectionChange(it.id, true) } },
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}


internal data class AlbumInfoPanelControls(
    val isSaved: Boolean,
    val isSaving: Boolean,
    val isLoadingForPlay: Boolean,
)

@Composable
internal fun AlbumInfoPanel(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    coverSize: Dp,
    controls: AlbumInfoPanelControls,
    isAnyDownloading: Boolean,
    isFullyDownloaded: Boolean,
    onToggleSave: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    showDeleteDialog: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    val downloadViewModel = LocalDownloadViewModel.current

    val songIds = remember(songs) { songs.map { it.id } }
    val isDownloading by remember(songIds, downloadViewModel) {
        downloadViewModel.isAnyDownloadingFlow(songIds)
    }.collectAsState(initial = false)

    val isFullyDownloaded by remember(songIds, downloadViewModel) {
        downloadViewModel.isFullyDownloadedFlow(songIds)
    }.collectAsState(initial = false)

    val firstArtist = albumPage.album.artists?.firstOrNull()

    Surface(
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(AppShapes.large)
            .clickable(enabled = firstArtist?.id != null) {
                firstArtist?.id?.let { onNavigate(Route.Artist(it)) }
            }
            .pointerHoverIcon(if (firstArtist?.id != null) PointerIcon.Hand else PointerIcon.Default)
    ) {
        Text(
            text = "${stringResource(Res.string.author_label)} • ${firstArtist?.name ?: stringResource(Res.string.item_artist)}",
            style = MaterialTheme.typography.ctaLabel,
            color = onSurfaceColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }

    Spacer(Modifier.height(16.dp))

    Card(
        modifier = Modifier
            .size(coverSize)
            .shadow(16.dp, AppShapes.xLarge, clip = false),
        shape = AppShapes.xLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        MusicPlayerImage(
            url = albumPage.album.thumbnail,
            contentDescription = albumPage.album.title,
            modifier = Modifier.fillMaxSize(),
            placeholderType = PlaceholderType.ALBUM,
            contentScale = ContentScale.Crop,
            iconSize = coverSize * 0.33f,
        )
    }

    Spacer(Modifier.height(22.dp))

    Text(
        text = albumPage.album.title,
        style = MaterialTheme.typography.collectionTitle,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = onSurfaceColor
    )

    Spacer(Modifier.height(6.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (albumPage.album.explicit) {
            Icon(Icons.Default.Explicit, null, modifier = Modifier.size(16.dp), tint = onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            "${stringResource(Res.string.album_label)} • ${albumPage.album.year ?: ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariant
        )
    }

    Text(
        stringResource(Res.string.songs_duration, songs.size, calculateTotalDuration(songs)),
        style = MaterialTheme.typography.bodySmall,
        color = onSurfaceVariant.copy(alpha = 0.7f)
    )

    Spacer(Modifier.height(28.dp))

    CollectionHeroActionRow(
        isSaved = controls.isSaved,
        isSaving = controls.isSaving,
        onToggleSave = onToggleSave,
        isLoadingForPlay = controls.isLoadingForPlay,
        onPlay = onPlayAll,
        onShuffle = onShuffle,
        isDownloading = isDownloading,
        isFullyDownloaded = isFullyDownloaded,
        onDownloadClick = {
            if (isFullyDownloaded) {
                showDeleteDialog()
            } else if (!isDownloading) {
                downloadViewModel.downloadAll(songs)
            }
        },
    )
}