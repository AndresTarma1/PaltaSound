@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package example.nucleus.ui.screens.youtube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.ui.components.artwork.rememberArtworkColors
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.layout.appScrollContentPadding
import example.nucleus.viewmodels.YouTubeHomeUiState
import example.nucleus.viewmodels.YouTubeHomeViewModel
import example.youtube.model.YouTubeChannel
import example.youtube.model.YouTubePlaylist
import example.youtube.model.YouTubeSearchItem
import example.youtube.model.YouTubeVideo

private val categories = listOf(
    "Todo",
    "Videojuegos",
    "Música",
    "Valorant",
    "Mixes",
    "En directo",
    "Pop indie",
    "Hip hop latino",
    "Reggaeton",
    "Listas de reproducción",
    "Hip hop",
    "J-Pop",
    "Lo-fi",
)

@Composable
fun YouTubeHomeScreenRoute(
    viewModel: YouTubeHomeViewModel,
    onOpenVideo: (String) -> Unit,
    shortsOnly: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.refreshHome()
    }
    YouTubeHomeScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = { viewModel.search() },
        onOpenVideo = onOpenVideo,
        onRefreshHome = viewModel::refreshHome,
        onLoadMoreHome = viewModel::loadMoreHome,
        shortsOnly = shortsOnly,
    )
}

@Composable
fun YouTubeHomeScreen(
    state: YouTubeHomeUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onRefreshHome: () -> Unit,
    onLoadMoreHome: () -> Unit,
    shortsOnly: Boolean = false,
) {
    var selectedCategory by remember { mutableStateOf("Todo") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        SearchRow(
            query = state.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
        )

        if (!state.hasSearched && !shortsOnly) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.error != null -> ErrorState(
                message = state.error.orEmpty(),
                onRetry = onSearch,
            )

            state.hasSearched -> SearchResults(
                state = state,
                onOpenVideo = onOpenVideo,
            )

            shortsOnly -> ShortsGrid(
                state = state,
                onOpenVideo = onOpenVideo,
                onRefreshHome = onRefreshHome,
            )

            else -> HomeGrid(
                state = state,
                onOpenVideo = onOpenVideo,
                onRefreshHome = onRefreshHome,
                onLoadMoreHome = onLoadMoreHome,
            )
        }
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Buscar") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Buscar")
                }
            },
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun HomeGrid(
    state: YouTubeHomeUiState,
    onOpenVideo: (String) -> Unit,
    onRefreshHome: () -> Unit,
    onLoadMoreHome: () -> Unit,
) {
    if (state.homeLoading && state.home.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.homeError != null) {
        ErrorState(state.homeError.orEmpty(), onRefreshHome)
        return
    }
    if (state.home.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No hay recomendaciones. Iniciá sesión para ver tu inicio personalizado.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val gridState = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(responsiveColumns(windowWidthDp())),
            contentPadding = appScrollContentPadding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            gridItems(state.home, key = { it.id }) { video ->
                VideoCard(video, onOpenVideo)
            }
            if (state.homeLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Cargando más…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            if (state.homeContinuation != null && !state.homeLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = onLoadMoreHome) { Text("Mostrar más") }
                }
            }
        }
        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun ShortsGrid(
    state: YouTubeHomeUiState,
    onOpenVideo: (String) -> Unit,
    onRefreshHome: () -> Unit,
) {
    if (state.homeLoading && state.home.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val shorts = state.home.filter { it.url.contains("/shorts/") }
    if (shorts.isEmpty()) {
        ErrorState("No hay Shorts disponibles por ahora.", onRefreshHome)
        return
    }
    val gridState = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(shortsColumns(windowWidthDp())),
            contentPadding = appScrollContentPadding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            gridItems(shorts, key = { it.id }) { video ->
                VideoCard(video, onOpenVideo, thumbnailAspectRatio = 9f / 16f)
            }
        }
        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun SearchResults(
    state: YouTubeHomeUiState,
    onOpenVideo: (String) -> Unit,
) {
    if (state.items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sin resultados", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val gridState = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(responsiveColumns(windowWidthDp())),
            contentPadding = appScrollContentPadding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            gridItems(
                items = state.items,
                key = { it.key() },
                span = { item ->
                    if (item is YouTubeSearchItem.Video) GridItemSpan(1) else GridItemSpan(maxLineSpan)
                },
            ) { item ->
                when (item) {
                    is YouTubeSearchItem.Video -> VideoCard(item.video, onOpenVideo)
                    is YouTubeSearchItem.Channel -> ChannelRow(item.channel)
                    is YouTubeSearchItem.Playlist -> PlaylistRow(item.playlist)
                }
            }
        }
        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun windowWidthDp(): Dp {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    return with(density) { window.containerSize.width.toDp() }
}

private fun responsiveColumns(windowWidth: Dp): Int = when {
    windowWidth < 620.dp -> 1
    windowWidth < 900.dp -> 2
    else -> 3
}

private fun shortsColumns(windowWidth: Dp): Int = when {
    windowWidth < 620.dp -> 2
    windowWidth < 900.dp -> 3
    windowWidth < 1200.dp -> 4
    windowWidth < 1600.dp -> 5
    else -> 6
}

@Composable
private fun VideoCard(
    video: YouTubeVideo,
    onOpenVideo: (String) -> Unit,
    thumbnailAspectRatio: Float = 16f / 9f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    // La paleta se extrae solo al hacer hover: en una parrilla de 20+ tarjetas
    // no tiene sentido descargar y analizar todas las miniaturas por adelantado.
    val artwork = rememberArtworkColors(video.thumbnailUrl, enabled = hovered)
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    // Mientras se extrae la paleta se usa un gris neutro; al llegar el color
    // dominante la tarjeta transiciona a él sin saltos.
    val hoverBackdrop by animateColorAsState(
        targetValue = if (artwork.resolved) {
            lerp(
                MaterialTheme.colorScheme.surfaceContainerLow,
                artwork.dominant,
                if (lightSurface) 0.22f else 0.45f,
            )
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(durationMillis = 220),
        label = "youtube-video-hover-color",
    )
    val hoverReveal by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "youtube-video-hover",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable { onOpenVideo(video.id) }
            .pointerHoverIcon(PointerIcon.Hand)
            .drawBehind {
                if (hoverReveal <= 0.001f) return@drawBehind
                val expand = 8.dp.toPx()
                val radius = CornerRadius(24.dp.toPx())
                val topLeft = Offset(-expand, -expand)
                val backdropSize = Size(
                    width = size.width + expand * 2,
                    height = size.height + expand * 2,
                )
                drawRoundRect(
                    color = hoverBackdrop.copy(alpha = hoverReveal),
                    topLeft = topLeft,
                    size = backdropSize,
                    cornerRadius = radius,
                )
                if (artwork.resolved) {
                    drawRoundRect(
                        color = artwork.dominant.copy(
                            alpha = hoverReveal * if (lightSurface) 0.35f else 0.5f,
                        ),
                        topLeft = topLeft,
                        size = backdropSize,
                        cornerRadius = radius,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(thumbnailAspectRatio)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            MusicPlayerImage(
                url = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                placeholderType = PlaceholderType.SONG,
                contentScale = ContentScale.Crop,
                isLowRes = false,
            )
            if (video.durationSeconds > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = formatDuration(video.durationSeconds),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (video.isLive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFF0033))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "EN VIVO",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            ChannelAvatar(video.channelName, video.channelAvatarUrl)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        video.channelName?.let { append(it) }
                        video.uploadedText?.let {
                            if (isNotEmpty()) append(" · ")
                            append(it)
                        }
                        video.viewCount?.let {
                            if (isNotEmpty()) append(" · ")
                            append(formatCount(it)).append(" vistas")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChannelAvatar(name: String?, avatarUrl: String?) {
    if (avatarUrl != null) {
        MusicPlayerImage(
            url = avatarUrl,
            contentDescription = name,
            modifier = Modifier.size(36.dp).clip(CircleShape),
            placeholderType = PlaceholderType.ARTIST,
            contentScale = ContentScale.Crop,
            isLowRes = true,
        )
        return
    }
    val label = name?.trim()?.firstOrNull()?.uppercase() ?: "Y"
    val colors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    val color = colors[(name?.hashCode()?.let { kotlin.math.abs(it) } ?: 0) % colors.size]
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

@Composable
private fun ChannelRow(channel: YouTubeChannel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicPlayerImage(
            url = channel.thumbnailUrl,
            contentDescription = channel.name,
            modifier = Modifier.size(64.dp).clip(CircleShape),
            placeholderType = PlaceholderType.ARTIST,
            isLowRes = true,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(channel.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Canal" + (channel.subscriberCount?.let { " · ${formatCount(it)} suscriptores" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistRow(playlist: YouTubePlaylist) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicPlayerImage(
            url = playlist.thumbnailUrl,
            contentDescription = playlist.name,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            placeholderType = PlaceholderType.PLAYLIST,
            isLowRes = true,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Playlist" + (playlist.streamCount?.let { " · $it vídeos" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun YouTubeSearchItem.key(): String = when (this) {
    is YouTubeSearchItem.Video -> "v:${video.id}"
    is YouTubeSearchItem.Channel -> "c:${channel.url}"
    is YouTubeSearchItem.Playlist -> "p:${playlist.url}"
}

internal fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun formatCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1f mil M".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1f M".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1f mil".format(value / 1_000.0)
    else -> value.toString()
}
