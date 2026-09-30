package example.nucleus.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Horizontal
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.layout.HorizontalScrollableRow
import example.nucleus.ui.components.context.SongContextMenuPopup
import example.nucleus.ui.components.context.CollectionContextMenuPopup
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.helpers.animateDesktopPressScale
import example.nucleus.ui.helpers.contextMenuArea
import example.nucleus.ui.helpers.desktopInteractiveSurface
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalDimens
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.mediaItemTitle
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.ui.utils.isCircleLikeShape
import example.nucleus.utils.thumbnailAspectRatio
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.utils.LocalPlaylistsViewModel
import example.nucleus.utils.LocalSnackbarHostState
import example.nucleus.utils.LocalSnackbarScope
import kotlinx.coroutines.launch
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

enum class ItemContentSource {
    LOCAL,
    YOUTUBE,
}

data class CornerQuickPlayConfig(
    val size: Dp,
    val iconSize: Dp,
    val onClick: () -> Unit,
)

@Composable
private fun YTItem.mediaGridSubtitle(): String = when (this) {
    is AlbumItem -> artists?.firstOrNull()?.name ?: year?.toString() ?: stringResource(Res.string.item_album)
    is ArtistItem -> stringResource(Res.string.item_artist)
    is PlaylistItem -> author?.name ?: songCountText ?: stringResource(Res.string.item_playlist)
    is SongItem -> artists.firstOrNull()?.name ?: stringResource(Res.string.item_song)
    is PodcastItem -> author?.name ?: episodeCountText ?: stringResource(Res.string.item_podcast)
    is EpisodeItem -> podcast?.name ?: author?.name ?: stringResource(Res.string.item_episode)
}

private fun YTItem.mediaGridPlaceholderType(): PlaceholderType = when (this) {
    is AlbumItem -> PlaceholderType.ALBUM
    is ArtistItem -> PlaceholderType.ARTIST
    is PlaylistItem -> PlaceholderType.PLAYLIST
    is PodcastItem -> PlaceholderType.PLAYLIST
    is SongItem -> PlaceholderType.SONG
    is EpisodeItem -> PlaceholderType.SONG
}

@Composable
private fun YTItem.mediaGridShape(): Shape = when (this) {
    is ArtistItem -> circleAwareShape()
    else -> MaterialTheme.shapes.medium
}

@Composable
fun YouTubeGridItem(
    item: YTItem,
    onClick: (YTItem) -> Unit,
    imageShape: Shape,
    alignment: Horizontal,
    titleAlign: TextAlign,
    placeholderType: PlaceholderType,
    centerPlayVisible: Boolean,
    contextMenuEnabled: Boolean,
    onContextMenuAction: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    quickPlay: CornerQuickPlayConfig? = null,
    onClickSubtitle: (() -> Unit)? = null,
    subtitle: String,
    topStartOverlay: (@Composable BoxScope.() -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isArtist = item is ArtistItem
    val cardHeight = 180.dp
    val aspectRatio = item.thumbnailAspectRatio()
    val cardWidth = cardHeight * aspectRatio
    val contentPadding = 10.dp

    // Fuente propia para la pulsacion: las tarjetas de rejilla no tenian feedback de
    // hover/press de escritorio (escala suave), solo el salto de opacidad del scrim.
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()
    var isHovered by remember { mutableStateOf(false) }

    val coverScale by animateDesktopPressScale(pressed = isPressed, hovered = isHovered)

    // Scrim y acciones de portada se abren con fade; el boton de opciones no (ver mas abajo).
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.30f else 0f,
        animationSpec = expressiveFadeTween(),
        label = "gridScrim",
    )
    val playAlpha by animateFloatAsState(
        targetValue = if (isHovered && centerPlayVisible) 1f else 0f,
        animationSpec = expressiveFadeTween(),
        label = "gridPlay",
    )
    val quickPlayAlpha by animateFloatAsState(
        targetValue = if (isHovered && quickPlay != null) 1f else 0f,
        animationSpec = expressiveFadeTween(),
        label = "gridQuickPlay",
    )
    // El titulo se atenua con el raton encima: refuerza que la imagen es el blanco del
    // click y evita que el texto compita con los iconos de accion.
    val titleAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.7f else 1f,
        animationSpec = expressiveFadeTween(),
        label = "gridTitleAlpha",
    )

    Box(modifier = modifier.width(cardWidth + contentPadding * 2).padding(contentPadding)) {
        Column(horizontalAlignment = alignment) {
            BoxForContainerContextMenuItem(
                modifier = Modifier
                    .aspectRatio(aspectRatio)
                    .graphicsLayer {
                        scaleX = coverScale
                        scaleY = coverScale
                    }
                    .clip(imageShape)
                    .onHover { isHovered = it }
                    .clickable(
                        interactionSource = pressSource,
                        indication = null,
                    ) { onClick(item) }
                    .pointerHoverIcon(PointerIcon.Hand),
                enabled = contextMenuEnabled,
                onMenuAction = onContextMenuAction.let { { it?.invoke() } }
            ) { menuButtonModifier, openMenuFromButton ->
                MusicPlayerImage(
                    url = item.thumbnail,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = imageShape,
                    placeholderType = placeholderType,
                    iconSize = if (isArtist) 56.dp else 40.dp,
                    contentScale = ContentScale.Crop,
                    alignment = if (isArtist) Alignment.TopCenter else Alignment.Center,
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(scrimAlpha)
                        .background(Color.Black)
                )

                if (centerPlayVisible) {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            stringResource(Res.string.play_item),
                            tint = Color.White,
                            modifier = Modifier
                                .size(56.dp)
                                .alpha(playAlpha)
                        )
                    }
                }

                if (onContextMenuAction != null) {
                    // Siempre visible: el menu de opciones de un item no debe depender de
                    // acertar el raton encima para existir (igual que en las filas de lista).
                    HoverCornerActionButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(Res.string.options),
                        onClick = { onMoreClick?.invoke() ?: openMenuFromButton() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        buttonModifier = menuButtonModifier.pointerHoverIcon(PointerIcon.Hand),
                        visible = true,
                        onButtonHoverChange = { if (it) isHovered = true }
                    )
                }


                if (quickPlay != null) {
                    HoverCornerActionButton(
                        icon = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(Res.string.play_item),
                        onClick = quickPlay.onClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .alpha(quickPlayAlpha),
                        buttonModifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        visible = isHovered,
                        size = quickPlay.size,
                        iconSize = quickPlay.iconSize,
                        onButtonHoverChange = { if (it) isHovered = true }
                    )
                }

                topStartOverlay?.invoke(this)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.mediaItemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(titleAlpha),
                textAlign = titleAlign
            )

            // TODO: Cuando el usuario pase el mouse sobre los artistas, que pueda seleccionar alguno y llevarlo a dicha ArtistPage

            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                var subtitleHover by remember { mutableStateOf(false) }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = if (subtitleHover) TextDecoration.Underline else TextDecoration.None,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerHoverIcon(PointerIcon.Hand)
                        .onHover { subtitleHover = it }
                        .clickable { onClickSubtitle?.invoke() },
                    textAlign = titleAlign
                )
            }
        }

        overlayContent()
    }
}


@Composable
fun MediaGridItem(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    placeholderType: PlaceholderType,
    shape: Shape,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onRemove: () -> Unit = {},
    isRemovable: Boolean = true,
    source: ItemContentSource = ItemContentSource.LOCAL,
    onExport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isCircle = isCircleLikeShape(shape)
    val alignment = if (isCircle) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (isCircle) TextAlign.Center else TextAlign.Start
    var isImageHovered by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val showImageActions = placeholderType == PlaceholderType.ALBUM || placeholderType == PlaceholderType.PLAYLIST

    // Mismo tratamiento que YouTubeGridItem: scrim y acciones con fade, y escala de
    // hover/press en la portada.
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()
    val coverScale by animateDesktopPressScale(pressed = isPressed, hovered = isImageHovered)

    val actionsVisible = isImageHovered && showImageActions
    val overlayAlpha by animateFloatAsState(
        targetValue = if (actionsVisible) 0.30f else 0f,
        animationSpec = expressiveFadeTween(),
        label = "mediaScrim",
    )
    val playAlpha by animateFloatAsState(
        targetValue = if (actionsVisible && onPlay != null) 1f else 0f,
        animationSpec = expressiveFadeTween(),
        label = "mediaPlay",
    )

    val sourceIcon = if (source == ItemContentSource.LOCAL) Icons.Default.LibraryMusic else null

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .padding(8.dp),
            horizontalAlignment = alignment
        ) {
            BoxForContainerContextMenuItem(
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Hand),
                enabled = showImageActions,
                onHoverChange = { isImageHovered = it },
                onMenuAction = {
                    showMenu = true
                }
            ) { menuButtonModifier, openMenuFromButton ->
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        }
                        .clickable(
                            interactionSource = pressSource,
                            indication = null,
                            onClick = onClick,
                        )
                        .onHover { isImageHovered = it }
                ) {
                    MusicPlayerImage(
                        url = thumbnailUrl,
                        contentDescription = title,
                        modifier = Modifier.aspectRatio(1f).fillMaxWidth(),
                        shape = shape,
                        placeholderType = placeholderType,
                        iconSize = 40.dp,
                        contentScale = ContentScale.Crop,
                        alignment = if (isCircle) Alignment.TopCenter else Alignment.Center,
                        // Cards de grid se pintan a ~200-260dp: 192px basta y reduce el bitmap
                        // nativo (192^2x4 = 144 KB vs 256 KB a 256px) en pantallas con muchos covers.
                        coilSizeOverride = 192,
                    )

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Color.Black.copy(alpha = overlayAlpha),
                                MaterialTheme.shapes.medium
                            )
                            .clip(MaterialTheme.shapes.medium)
                    )

                    sourceIcon?.let {
                        Surface(
                            shape = circleAwareShape(),
                            color = Color.Black.copy(alpha = 0.50f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = sourceIcon,
                                    contentDescription = if (source == ItemContentSource.LOCAL) stringResource(Res.string.cd_local) else stringResource(
                                        Res.string.cd_youtube
                                    ),
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (showImageActions) {
                        // Scrim circular detras del icono: sin el, el "mas" desaparece
                        // sobre portadas claras. Siempre visible, igual que en las filas.
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.40f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = openMenuFromButton,
                                modifier = menuButtonModifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    stringResource(Res.string.options),
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    if (onPlay != null && showImageActions) {
                        FilledIconButton(
                            onClick = onPlay,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(34.dp)
                                .alpha(playAlpha),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.55f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = stringResource(Res.string.play_item),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            CollectionContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                title = title,
                isPlaylist = placeholderType == PlaceholderType.PLAYLIST,
                onOpen = onClick,
                onPlay = onPlay,
                onShuffle = onShuffle,
                onRemoveFromLibrary = if (isRemovable) onRemove else null,
                onExport = onExport,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.mediaItemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = textAlign
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = textAlign
            )
        }
    }
}

@Composable
fun MediaGridItem(
    item: YTItem,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onRemove: () -> Unit = {},
    isRemovable: Boolean = true,
    source: ItemContentSource = ItemContentSource.LOCAL,
    onExport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    MediaGridItem(
        title = item.title,
        subtitle = item.mediaGridSubtitle(),
        thumbnailUrl = item.thumbnail,
        placeholderType = item.mediaGridPlaceholderType(),
        shape = item.mediaGridShape(),
        onClick = onClick,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onRemove = onRemove,
        isRemovable = isRemovable,
        onExport = onExport,
        source = source,
        modifier = modifier,
    )
}

internal fun String?.resizeThumbnailUrl(size: Int): String? {
    if (this.isNullOrEmpty()) return this
    val replaced = Regex("w\\d+-h\\d+").replace(this, "w$size-h$size")
    if (replaced != this) return replaced
    return if (startsWith("https://lh3.googleusercontent.com") ||
        startsWith("https://yt3.ggpht.com") ||
        startsWith("https://yt3.googleusercontent.com")
    ) "$this=w$size-h$size-l90-rj" else this
}


@Composable
fun YoutubeListItem(
    item: YTItem,
    source: ItemContentSource,
    onClick: (YTItem) -> Unit,
    onPlay: (YTItem) -> Unit,
    onShuffle: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Linea divisoria bajo la fila. Solo tiene sentido en listas de una columna; en el
     * grid de dos filas de Home las lineas cairian dentro de cada columna.
     */
    dividerBelow: Boolean = false,
) {
    val dimens = LocalDimens.current
    val shape = when (item) {
        is ArtistItem -> circleAwareShape()
        is PlaylistItem -> RoundedCornerShape(dimens.itemCorner)
        is PodcastItem -> RoundedCornerShape(dimens.itemCorner)
        else -> RoundedCornerShape(dimens.itemCorner - 2.dp)
    }

    val imageSize = 56.dp

    var showMenu by remember { mutableStateOf(false) }
    var isHovered by remember { mutableStateOf(false) }

    val sourceIcon = if (source == ItemContentSource.LOCAL) Icons.Default.PhoneAndroid else null
    val isCollectionItem = item is AlbumItem || item is PlaylistItem || item is PodcastItem
    val isEpisodeItem = item is EpisodeItem
    val rowShape = AppShapes.large

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .desktopInteractiveSurface(shape = rowShape, showHandCursor = true)
            .clickable { onClick(item) }
            .contextMenuArea(
                enabled = item is SongItem || isEpisodeItem || isCollectionItem,
                onHoverChange = { isHovered = it },
                onMenuAction = {
                    showMenu = true
                },
            )
            // Evita que hover/menú se dibujen por encima del gutter del scrollbar padre.
            .padding(end = 2.dp)
    ) {
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.mediaItemTitle,
                )
            },
            supportingContent = {
                val subtitle = when (item) {
                    is SongItem -> {
                        val artists = item.artists.joinToString { it.name }
                        val album = item.album?.name?.let { " • $it" } ?: ""
                        "$artists$album"
                    }

                    is AlbumItem -> {
                        val artists = item.artists?.joinToString { it.name } ?: stringResource(Res.string.item_album)
                        "${stringResource(Res.string.item_album)} • $artists"
                    }

                    is ArtistItem -> stringResource(Res.string.item_artist)
                    is PlaylistItem -> {
                        val author = item.author?.name?.let { " • $it" } ?: ""
                        "${stringResource(Res.string.item_playlist)}$author"
                    }

                    is PodcastItem -> {
                        val author = item.author?.name?.let { " • $it" } ?: ""
                        val count = item.episodeCountText?.let { " • $it" } ?: ""
                        "${stringResource(Res.string.item_podcast)}$author$count"
                    }

                    is EpisodeItem -> {
                        val podcast = item.podcast?.name ?: item.author?.name ?: ""
                        val date = item.publishDateText?.let { " • $it" } ?: ""
                        "${stringResource(Res.string.item_episode)} • $podcast$date"
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    sourceIcon?.let {
                        Surface(
                            shape = circleAwareShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    sourceIcon,
                                    null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            leadingContent = {
                MusicPlayerImage(
                    url = item.thumbnail.resizeThumbnailUrl(160),
                    contentDescription = item.title,
                    modifier = Modifier.size(imageSize),
                    shape = shape,
                    placeholderType = when (item) {
                        is ArtistItem -> PlaceholderType.ARTIST
                        is AlbumItem -> PlaceholderType.ALBUM
                        is PlaylistItem -> PlaceholderType.PLAYLIST
                        is PodcastItem -> PlaceholderType.PLAYLIST
                        else -> PlaceholderType.SONG
                    },
                    contentScale = ContentScale.Crop,
                    iconSize = if (item is PlaylistItem || item is PodcastItem) 28.dp else 24.dp,
                )
            },
            trailingContent = {
                if (item is SongItem || isEpisodeItem || isCollectionItem) {
                    // Tonal en vez de IconButton pelado: el "mas" deja de perderse al final
                    // de la fila. Aparece con el hover del item, no de forma permanente, para
                    // no ensuciar la columna en reposo.
                    val menuAlpha by animateFloatAsState(
                        targetValue = if (isHovered) 1f else 0f,
                        animationSpec = expressiveFadeTween(),
                        label = "rowMenuAlpha",
                    )
                    Box(modifier = Modifier.alpha(menuAlpha)) {
                        FilledTonalIconButton(
                            onClick = {
                                showMenu = true
                            },
                            // Con alpha 0 el boton seguiria siendo pulsable (alpha no afecta
                            // al hit-test), lo que dejaria una trampa invisible.
                            enabled = menuAlpha > 0.01f,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(Res.string.more_options),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        )

        if (dividerBelow) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 8.dp, end = 8.dp),
            )
        }

        if (item is SongItem) {
            SongContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                song = item
            )
        } else if (item is EpisodeItem) {
            SongContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                song = item.asSongItem()
            )
        } else if (item is AlbumItem || item is PlaylistItem || item is PodcastItem) {
            val playlistsViewModel = LocalPlaylistsViewModel.current
            val scope = LocalSnackbarScope.current
            val snackbar = LocalSnackbarHostState.current
            CollectionContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                title = item.title,
                isPlaylist = item is PlaylistItem,
                onOpen = { onClick(item) },
                onPlay = {
                    onPlay(item)
                },
                onShuffle = {
                    onShuffle(item)
                },
                onExport = if (item is PlaylistItem) ({
                    playlistsViewModel.exportPlaylist(item.id) { success, msg ->
                        scope.launch {
                            if (success) {
                                val result = snackbar.showSnackbar(
                                    message = "Exportada a $msg",
                                    actionLabel = "Abrir carpeta",
                                    duration = androidx.compose.material3.SnackbarDuration.Long
                                )
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    try { example.nucleus.platform.NativeDesktop.openFolder(java.io.File(msg).parentFile ?: java.io.File(msg)) } catch (_: Exception) {}
                                }
                            } else {
                                snackbar.showSnackbar("Error: $msg")
                            }
                        }
                    }
                }) else null
            )
        }
    }
}

@Composable
fun <T> HorizontalGridLikeRow(
    items: List<T>,
    rows: Int,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    columnWidth: Dp,
    rowSpacing: Dp = 8.dp,
    columnSpacing: Dp = 12.dp,
    itemKey: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val safeRows = rows.coerceAtLeast(1)
    val columns = remember(items, safeRows) { items.chunked(safeRows) }

    HorizontalScrollableRow(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(columnSpacing),
    ) {
        items(
            count = columns.size,
            key = { index ->
                val firstItem = columns[index].firstOrNull()
                if (firstItem != null && itemKey != null) itemKey(firstItem) else "col_$index"
            }
        ) { colIndex ->
            val columnItems = columns[colIndex]
            Column(modifier = Modifier.width(columnWidth)) {
                columnItems.forEachIndexed { rowIndex, item ->
                    itemContent(item)
                    if (rowIndex != columnItems.lastIndex) {
                        Spacer(modifier = Modifier.height(rowSpacing))
                    }
                }
            }
        }
    }
}
