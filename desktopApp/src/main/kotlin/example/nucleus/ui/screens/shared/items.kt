package example.nucleus.ui.screens.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import example.nucleus.navigation.Route
import example.nucleus.ui.components.CornerQuickPlayConfig
import example.nucleus.ui.components.ItemContentSource
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.YoutubeListItem
import example.nucleus.ui.components.YouTubeGridItem
import example.nucleus.ui.components.context.CollectionContextMenuPopup
import example.nucleus.ui.components.context.SongContextMenuPopup
import example.nucleus.ui.components.dialogs.ArtistsModal
import example.nucleus.ui.components.song.DownloadIndicator
import example.nucleus.ui.helpers.rememberSongDownloadState
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.item_album
import example.nucleus.generated.resources.item_artist
import example.nucleus.generated.resources.item_list
import example.nucleus.generated.resources.item_podcast
import org.jetbrains.compose.resources.*

fun onYTItemClick(
    item: YTItem,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel?,
) {
    when (item) {
        is SongItem -> playerViewModel?.playSingle(item)
        is AlbumItem -> onNavigate(Route.Album(item.browseId))
        is PlaylistItem -> onNavigate(Route.Playlist(item.id))
        is ArtistItem -> onNavigate(Route.Artist(item.id))
        is PodcastItem -> onNavigate(Route.Playlist(item.id))
        is EpisodeItem -> playerViewModel?.playSingle(item.asSongItem())
    }
}

private fun playYTItem(
    item: YTItem,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel,
    onClick: (YTItem) -> Unit,
) {
    when (item) {
        is SongItem -> playerViewModel.playSingle(item)
        is EpisodeItem -> playerViewModel.playSingle(item.asSongItem())
        is AlbumItem -> playerViewModel.playAlbumFromBrowseId(
            browseId = item.browseId,
            playlistId = item.playlistId,
            title = item.title,
            onEmpty = { onClick(item) }
        )
        is PlaylistItem -> playerViewModel.playPlaylistFromId(
            playlistId = item.id,
            endpoint = item.playEndpoint,
            title = item.title,
            onEmpty = { onClick(item) }
        )
        is PodcastItem -> onNavigate(Route.Playlist(item.id))
        is ArtistItem -> onNavigate(Route.Artist(item.id))
    }
}

private fun shuffleYTItem(
    item: YTItem,
    playerViewModel: PlayerViewModel,
    onClick: (YTItem) -> Unit,
) {
    when (item) {
        is AlbumItem -> playerViewModel.playAlbumFromBrowseId(
            browseId = item.browseId,
            playlistId = item.playlistId,
            title = item.title,
            shuffle = true,
            onEmpty = { onClick(item) }
        )
        is PlaylistItem -> playerViewModel.playPlaylistFromId(
            playlistId = item.id,
            endpoint = item.shuffleEndpoint ?: item.playEndpoint,
            title = item.title,
            shuffle = true,
            onEmpty = { onClick(item) }
        )
        is PodcastItem -> onClick(item)
        else -> onClick(item)
    }
}


@Composable
fun SongGridItem(
    item: SongItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit,
    onClickSubtitle: (String) -> Unit,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(item.id, downloadViewModel)

    var showMenu by remember { mutableStateOf(false) }
    var showModalSheet by remember { mutableStateOf(false) }

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = MaterialTheme.shapes.small,
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.SONG,
        centerPlayVisible = true,
        onClickSubtitle = { showModalSheet = true },
        contextMenuEnabled = true,
        onContextMenuAction = { showMenu = true },
        subtitle = item.artists.firstOrNull()?.name.orEmpty(),
        modifier = modifier,
        topStartOverlay = {
            if (downloadState != null) {
                DownloadIndicator(
                    state = downloadState,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), circleAwareShape())
                        .padding(4.dp)
                )
            }
        },
        overlayContent = {
            SongContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                song = item
            )
        }
    )

    if (showModalSheet) {
        ArtistsModal(
            artists = item.artists,
            onClickArtist = { artistId ->
                showModalSheet = false
                onClickSubtitle(artistId)
            },
            onDismiss = { showModalSheet = false }
        )
    }
}

@Composable
fun AlbumGridItem(
    item: AlbumItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit,
    onPlay: (YTItem) -> Unit,
    onShuffle: (YTItem) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = MaterialTheme.shapes.small,
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.ALBUM,
        centerPlayVisible = false,
        contextMenuEnabled = true,
        onContextMenuAction = { showMenu = true },
        quickPlay = CornerQuickPlayConfig(
            size = 42.dp,
            iconSize = 20.dp,
            onClick = {
                onPlay(item)
            }
        ),
        subtitle = item.artists?.firstOrNull()?.name ?: stringResource(Res.string.item_album),
        modifier = modifier,
        overlayContent = {
            CollectionContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                title = item.title,
                isPlaylist = false,
                onOpen = { onClick(item) },
                onPlay = {
                    onPlay(item)
                },
                onShuffle = {
                    onShuffle(item)
                }
            )
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistGridItem(
    item: PlaylistItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit,
    onPlay: (YTItem) -> Unit,
    onShuffle: (YTItem) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = MaterialTheme.shapes.small,
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.PLAYLIST,
        centerPlayVisible = false,
        contextMenuEnabled = true,
        onContextMenuAction = { showMenu = true },
        quickPlay = CornerQuickPlayConfig(
            size = 42.dp,
            iconSize = 20.dp,
            onClick = {
                onPlay(item)
            }
        ),
        subtitle = item.author?.name ?: stringResource(Res.string.item_list),
        modifier = modifier,
        overlayContent = {
            CollectionContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                title = item.title,
                isPlaylist = true,
                onOpen = { onClick(item) },
                onPlay = {
                    onPlay(item)
                },
                onShuffle = {
                    onShuffle(item)
                }
            )
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ArtistGridItem(
    item: ArtistItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit,
) {
    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = circleAwareShape(),
        alignment = Alignment.CenterHorizontally,
        titleAlign = TextAlign.Center,
        placeholderType = PlaceholderType.ARTIST,
        centerPlayVisible = false,
        contextMenuEnabled = false,
        subtitle = stringResource(Res.string.item_artist),
        modifier = modifier
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PodcastGridItem(
    item: PodcastItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = MaterialTheme.shapes.small,
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.PLAYLIST,
        centerPlayVisible = false,
        contextMenuEnabled = true,
        onContextMenuAction = { showMenu = true },
        subtitle = item.author?.name ?: item.episodeCountText ?: stringResource(Res.string.item_podcast),
        modifier = modifier,
        overlayContent = {
            CollectionContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                title = item.title,
                isPlaylist = true,
                onOpen = { onClick(item) },
                onPlay = { onClick(item) },
                onShuffle = { onClick(item) }
            )
        }
    )
}

@Composable
fun EpisodeGridItem(
    item: EpisodeItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit,
    onClickSubtitle: (String) -> Unit,
) {
    SongGridItem(
        item = item.asSongItem(),
        modifier = modifier,
        onClick = { onClick(item) },
        onClickSubtitle = onClickSubtitle,
    )
}


@Composable
fun SectionListItem(
    item: YTItem,
    onNavigate: (Route) -> Unit = {},
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel
) {
    val onClick = { it: YTItem -> onYTItemClick(it, onNavigate, playerViewModel) }

    YoutubeListItem(
        item = item,
        onClick = onClick,
        onPlay = { playYTItem(it, onNavigate, playerViewModel, onClick) },
        onShuffle = { shuffleYTItem(it, playerViewModel, onClick) },
        modifier = modifier,
        source = ItemContentSource.YOUTUBE,
    )
}

@Composable
fun SectionGridItem(
    item: YTItem,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val onClick = { it: YTItem -> onYTItemClick(it, onNavigate, playerViewModel) }
    val onPlay = { it: YTItem -> playYTItem(it, onNavigate, playerViewModel, onClick) }
    val onShuffle = { it: YTItem -> shuffleYTItem(it, playerViewModel, onClick) }

    when (item) {
        is SongItem -> SongGridItem(
            item = item,
            onClick = onClick,
            onClickSubtitle = { onNavigate(Route.Artist(it)) },
            modifier = modifier,
        )
        is AlbumItem -> AlbumGridItem(item = item, onClick = onClick, modifier = modifier, onPlay = onPlay, onShuffle = onShuffle)
        is PlaylistItem -> PlaylistGridItem(item = item, onClick = onClick, modifier = modifier, onPlay = onPlay, onShuffle = onShuffle)
        is ArtistItem -> ArtistGridItem(item = item, onClick = onClick, modifier = modifier)
        is PodcastItem -> PodcastGridItem(item = item, onClick = onClick, modifier = modifier)
        is EpisodeItem -> EpisodeGridItem(
            item = item,
            onClick = onClick,
            onClickSubtitle = { onNavigate(Route.Artist(it)) },
            modifier = modifier,
        )
    }
}