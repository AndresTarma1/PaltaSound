package example.nucleus.ui.screens.playlist

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.LoadingMoreSongsItem
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.dialogs.DownloadConfirmationDialog
import example.nucleus.ui.components.layout.AppScrollbarGutter
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.layout.appScrollContentPadding
import example.nucleus.ui.screens.PlaylistActions
import example.nucleus.ui.screens.PlaylistScreenState
import example.nucleus.ui.screens.shared.CollectionCompactCoverSize
import example.nucleus.ui.screens.shared.CollectionHeroActionRow
import example.nucleus.ui.screens.shared.CollectionHeroIconButton
import example.nucleus.ui.screens.shared.CollectionStickyHeaderBar
import example.nucleus.ui.screens.shared.CollectionWideCoverSize
import example.nucleus.ui.screens.shared.CollectionWideGap
import example.nucleus.ui.screens.shared.CollectionWideHeroWidth
import example.nucleus.ui.screens.shared.CollectionWidePaddingBottom
import example.nucleus.ui.screens.shared.CollectionWidePaddingStart
import example.nucleus.ui.screens.shared.CollectionWidePaddingTop
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.collectionTitle
import example.nucleus.ui.themes.ctaLabel
import example.nucleus.utils.LocalDownloadViewModel
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.PlaylistPage
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlaylistLayout(
    playlistPage: PlaylistPage,
    state: PlaylistScreenState,
    actions: PlaylistActions
) {
    val downloadViewModel = LocalDownloadViewModel.current

    val songIds = remember(state.songs) { state.songs.map { it.id } }

    val isAnyDownloading by remember(songIds, downloadViewModel) {
        downloadViewModel.isAnyDownloadingFlow(songIds)
    }.collectAsState(initial = false)

    val isFullyDownloaded by remember(songIds, downloadViewModel) {
        downloadViewModel.isFullyDownloadedFlow(songIds)
    }.collectAsState(initial = false)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedSongIds by remember(state.songs) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedSongs = remember(state.songs, selectedSongIds) {
        state.songs.filter { it.id in selectedSongIds }
    }

    if (showDeleteDialog) {
        DownloadConfirmationDialog(
            onConfirm = { downloadViewModel.removeDownloads(songIds) },
            onDismiss = { showDeleteDialog = false }
        )
    }

    val onDownloadClick = {
        val isDownloadsPlaylist = playlistPage.playlist.id == "LOCAL_DOWNLOADS"
        if (isFullyDownloaded && !isDownloadsPlaylist) showDeleteDialog = true
        else if (!isAnyDownloading) actions.onDownloadPlaylist()
    }

    val controls = PlaylistInfoPanelControls(
        isSaved = state.isSaved,
        isSaving = state.isSaving,
        isLoadingForPlay = state.isLoadingForPlay,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < 980.dp) {
            PlaylistCompactLayout(
                playlistPage = playlistPage,
                state = state,
                actions = actions,
                controls = controls,
                selectedSongIds = selectedSongIds,
                selectedSongs = selectedSongs,
                isAnyDownloading = { isAnyDownloading },
                isFullyDownloaded = { isFullyDownloaded },
                onDownloadClick = onDownloadClick,
                onSelectionChange = { id, selected ->
                    selectedSongIds = if (selected) selectedSongIds + id else selectedSongIds - id
                },
                onClearSelection = { selectedSongIds = emptySet() },
                onSongPlay = { index -> actions.onPlaySong(index) }
            )
        } else {
            PlaylistWideLayout(
                playlistPage = playlistPage,
                state = state,
                actions = actions,
                controls = controls,
                selectedSongIds = selectedSongIds,
                selectedSongs = selectedSongs,
                isAnyDownloading = { isAnyDownloading },
                isFullyDownloaded = { isFullyDownloaded },
                onDownloadClick = onDownloadClick,
                onSelectionChange = { id, selected ->
                    selectedSongIds = if (selected) selectedSongIds + id else selectedSongIds - id
                },
                onClearSelection = { selectedSongIds = emptySet() },
                onSongPlay = { index -> actions.onPlaySong(index) }
            )
        }
    }
}

@Composable
internal fun PlaylistWideLayout(
    playlistPage: PlaylistPage,
    state: PlaylistScreenState,
    actions: PlaylistActions,
    controls: PlaylistInfoPanelControls,
    selectedSongIds: Set<String>,
    selectedSongs: List<SongItem>,
    isAnyDownloading: () -> Boolean,
    isFullyDownloaded: () -> Boolean,
    onDownloadClick: () -> Unit,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSongPlay: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // Scrollbar anclado al borde derecho de la pantalla (no al final de la columna de la lista).
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
                PlaylistInfoPanel(
                    playlistPage = playlistPage,
                    coverSize = CollectionWideCoverSize,
                    controls = controls,
                    actions = actions,
                    isDownloadingAny = isAnyDownloading,
                    isFullyDownloaded = isFullyDownloaded,
                    onDownloadClick = onDownloadClick,
                )
            }

            PlaylistSongList(
                modifier = Modifier.weight(1f),
                listState = listState,
                state = state,
                selectedSongIds = selectedSongIds,
                selectedSongs = selectedSongs,
                actions = actions,
                onSelectionChange = onSelectionChange,
                onClearSelection = onClearSelection,
                onSongPlay = onSongPlay,
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
internal fun PlaylistCompactLayout(
    playlistPage: PlaylistPage,
    state: PlaylistScreenState,
    actions: PlaylistActions,
    controls: PlaylistInfoPanelControls,
    selectedSongIds: Set<String>,
    selectedSongs: List<SongItem>,
    isAnyDownloading: () -> Boolean,
    isFullyDownloaded: () -> Boolean,
    onDownloadClick: () -> Unit,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSongPlay: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        val lazyColumnState = rememberLazyListState()

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
                        title = playlistPage.playlist.title,
                        isDownloading = isAnyDownloading(),
                        isFullyDownloaded = isFullyDownloaded(),
                        onDownloadClick = onDownloadClick,
                        onPlayClick = actions.onPlay,
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
                    PlaylistInfoPanel(
                        playlistPage = playlistPage,
                        coverSize = CollectionCompactCoverSize,
                        controls = controls,
                        actions = actions,
                        isDownloadingAny = isAnyDownloading,
                        isFullyDownloaded = isFullyDownloaded,
                        onDownloadClick = onDownloadClick,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            itemsIndexed(
                items = state.songs,
                key = { index, song -> "${song.id}_$index" }
            ) { index, song ->
                SongListItem(
                    song = song,
                    onPlay = {
                        if (selectedSongIds.isNotEmpty()) {
                            onSelectionChange(song.id, song.id !in selectedSongIds)
                        } else {
                            onSongPlay(index)
                        }
                    },
                    isSelected = song.id in selectedSongIds,
                    selectionMode = selectedSongIds.isNotEmpty(),
                    onSelectionChange = { selected -> onSelectionChange(song.id, selected) },
                    isLocalPlaylist = actions.isLocalPlaylist,
                    onRemoveFromPlaylist = actions.onRemoveSongFromPlaylist,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
            }

            if (state.hasMore) {
                item { LoadingMoreSongsItem(onLoadMore = actions.onLoadMore) }
            }

        }

        AppVerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            state = lazyColumnState,
        )

        MultiSongSelectionBar(
            selectedSongs = selectedSongs,
            allSongIds = state.songs.map { it.id },
            isLocalPlaylist = actions.isLocalPlaylist,
            onClearSelection = onClearSelection,
            onSelectAll = { state.songs.forEach { onSelectionChange(it.id, true) } },
            onRemoveFromPlaylist = actions.onRemoveSongFromPlaylist,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PlaylistSongList(
    modifier: Modifier,
    listState: LazyListState,
    state: PlaylistScreenState,
    selectedSongIds: Set<String>,
    selectedSongs: List<SongItem>,
    actions: PlaylistActions,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSongPlay: (Int) -> Unit,
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
            itemsIndexed(
                items = state.songs,
                key = { index, song -> "${song.id}_$index" },
            ) { index, song ->
                SongListItem(
                    song = song,
                    onPlay = {
                        if (selectedSongIds.isNotEmpty()) {
                            onSelectionChange(song.id, song.id !in selectedSongIds)
                        } else {
                            onSongPlay(index)
                        }
                    },
                    isSelected = song.id in selectedSongIds,
                    selectionMode = selectedSongIds.isNotEmpty(),
                    onSelectionChange = { selected -> onSelectionChange(song.id, selected) },
                    isLocalPlaylist = actions.isLocalPlaylist,
                    onRemoveFromPlaylist = actions.onRemoveSongFromPlaylist,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                )
            }

            if (state.hasMore) {
                item { LoadingMoreSongsItem(onLoadMore = actions.onLoadMore) }
            }
        }

        MultiSongSelectionBar(
            selectedSongs = selectedSongs,
            allSongIds = state.songs.map { it.id },
            isLocalPlaylist = actions.isLocalPlaylist,
            onClearSelection = onClearSelection,
            onSelectAll = { state.songs.forEach { onSelectionChange(it.id, true) } },
            onRemoveFromPlaylist = actions.onRemoveSongFromPlaylist,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

internal data class PlaylistInfoPanelControls(
    val isSaved: Boolean,
    val isSaving: Boolean,
    val isLoadingForPlay: Boolean,
)

@Composable
internal fun PlaylistInfoPanel(
    playlistPage: PlaylistPage,
    coverSize: Dp,
    controls: PlaylistInfoPanelControls,
    actions: PlaylistActions,
    isDownloadingAny: () -> Boolean,
    isFullyDownloaded: () -> Boolean,
    onDownloadClick: () -> Unit
) {
    // Author chip
    playlistPage.playlist.author?.let { author ->
        AssistChip(
            onClick = {},
            label = {
                Text(
                    text = author.name,
                    style = MaterialTheme.typography.ctaLabel,
                )
            },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(circleAwareShape())
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    MusicPlayerImage(
                        url = null,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        shape = circleAwareShape(),
                        contentScale = ContentScale.Crop,
                        placeholderType = PlaceholderType.ARTIST,
                        iconSize = 12.dp
                    )
                }
            },
            shape = AppShapes.large,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        )
        Spacer(Modifier.height(16.dp))
    }

    // Cover — M3E soft radius + tonal elevation
    Card(
        modifier = Modifier
            .size(coverSize)
            .shadow(elevation = 16.dp, shape = AppShapes.xLarge, clip = false),
        shape = AppShapes.xLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MusicPlayerImage(
                url = playlistPage.playlist.thumbnail,
                contentDescription = playlistPage.playlist.title,
                modifier = Modifier.fillMaxSize(),
                placeholderType = PlaceholderType.PLAYLIST,
                contentScale = ContentScale.Crop,
                iconSize = coverSize * 0.35f,
            )
        }
    }

    Spacer(Modifier.height(22.dp))

    Text(
        text = playlistPage.playlist.title,
        style = MaterialTheme.typography.collectionTitle,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(Modifier.height(6.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.item_playlist),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val songCountText = playlistPage.playlist.songCountText
        if (!songCountText.isNullOrBlank()) {
            Text(
                text = " • $songCountText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    CollectionHeroActionRow(
        isSaved = controls.isSaved,
        isSaving = controls.isSaving,
        onToggleSave = actions.onToggleSave,
        isLoadingForPlay = controls.isLoadingForPlay,
        onPlay = actions.onPlay,
        onShuffle = actions.onShuffle,
        isDownloading = isDownloadingAny(),
        isFullyDownloaded = isFullyDownloaded(),
        onDownloadClick = onDownloadClick,
    )
}

@Composable
fun PlaylistActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    isActive: Boolean,
    isLoading: Boolean,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    CollectionHeroIconButton(
        onClick = onClick,
        icon = icon,
        active = isActive,
        isLoading = isLoading,
        modifier = modifier,
    )
}

@Composable
internal fun DownloadAllButton(
    isDownloadingAny: () -> Boolean,
    isFullyDownloaded: () -> Boolean,
    onClick: () -> Unit,
) {
    CollectionHeroIconButton(
        onClick = onClick,
        icon = if (isFullyDownloaded()) Icons.Default.DownloadDone else Icons.Default.Download,
        active = isFullyDownloaded(),
        isLoading = isDownloadingAny(),
        useWavyProgress = true,
    )
}