@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package example.nucleus.ui.screens.youtube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.player.YouTubePlayerService
import example.nucleus.ui.components.formatClock
import example.nucleus.ui.components.formatVideoDuration
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.components.layout.appScrollContentPadding
import example.nucleus.ui.youtube.YouTubeVideoSurface
import example.nucleus.windows.HiddenCursor
import kotlinx.coroutines.delay
import example.nucleus.viewmodels.YouTubeWatchUiState
import example.nucleus.viewmodels.YouTubeWatchViewModel
import example.youtube.model.YouTubeVideo

@Composable
fun YouTubeWatchScreenRoute(
    viewModel: YouTubeWatchViewModel,
    playerService: YouTubePlayerService,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    YouTubeWatchScreen(
        state = state,
        playerService = playerService,
        onTogglePlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        onVolume = viewModel::setVolume,
        onRetry = viewModel::retry,
        onOpenVideo = onOpenVideo,
        onMinimize = {
            viewModel.minimize()
            onBack()
        },
    )
}

@Composable
fun YouTubeWatchScreen(
    state: YouTubeWatchUiState,
    playerService: YouTubePlayerService,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onMinimize: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }

    // El vídeo es un HWND nativo: se come los eventos de ratón sobre él, así
    // que el auto-hide despierta leyendo la posición real del cursor.
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) return@LaunchedEffect
        var last = HiddenCursor.cursorPosition()
        while (true) {
            delay(150)
            val current = HiddenCursor.cursorPosition()
            if (last != null && current != null &&
                (current.x != last.x || current.y != last.y)
            ) {
                interactionTick++
            }
            last = current
        }
    }

    LaunchedEffect(state.isPlaying, interactionTick) {
        if (state.isPlaying) {
            controlsVisible = true
            delay(3_000)
            controlsVisible = false
        } else {
            controlsVisible = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            HiddenCursor.show()
            // Si se sale del watch con el fullscreen abierto, cerrar la
            // ventana aparte.
            playerService.setFullscreen(false)
        }
    }

    val onToggleFullscreen = {
        controlsVisible = true
        interactionTick++
        playerService.setFullscreen(true)
    }

    // El vídeo vive en un único slot de composición: solo cambian sus bounds
    // con el layout windowed. La ventana principal no se redimensiona nunca
    // para fullscreen.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val wide = containerWidth >= 1000.dp
        val leftScroll = rememberScrollState()

        // Rect del player en modo windowed, relativo a esta pantalla. Se usa
        // tanto para reservar el hueco en el layout como para posicionar el
        // único PlayerCard.
        val cardWidth = if (wide) {
            val leftWidth = (containerWidth - 432.dp).coerceAtLeast(200.dp)
            val availableHeight = (containerHeight - 48.dp).coerceAtLeast(200.dp)
            val cardHeight = (availableHeight - 170.dp).coerceIn(160.dp, availableHeight)
            minOf(leftWidth, cardHeight * 16f / 9f)
        } else {
            val availableHeight = (containerHeight - 32.dp).coerceAtLeast(160.dp)
            val cardHeight = (availableHeight - 120.dp).coerceIn(140.dp, availableHeight)
            minOf(
                (containerWidth - 44.dp).coerceAtLeast(160.dp),
                cardHeight * 16f / 9f,
            )
        }
        val cardX = if (wide) {
            24.dp
        } else {
            16.dp + ((containerWidth - 44.dp) - cardWidth) / 2f
        }
        val cardY = if (wide) 24.dp else 16.dp

        // La ventana principal siempre usa el layout windowed; el fullscreen
        // del vídeo se muestra en una ventana aparte.
        run {
            if (wide) {
                // Reserva ~62% del alto para el reproductor y deja el resto para
                // la info con scroll. El player queda fuera del scroll: así el
                // HWND no se mueve ni se redimensiona mientras se lee la info.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        // Hueco del player; el PlayerCard real se dibuja encima
                        // desde el slot único del final.
                        Spacer(Modifier.width(cardWidth).aspectRatio(16f / 9f))
                        state.error?.let { error ->
                            Spacer(Modifier.height(8.dp))
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetry) { Text("Reintentar") }
                        }
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(leftScroll)
                                    .padding(end = 12.dp),
                            ) {
                                VideoInfo(state.video)
                            }
                            AppVerticalScrollbar(
                                state = leftScroll,
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            )
                        }
                    }
                    RelatedPane(
                        related = state.related,
                        onOpenVideo = onOpenVideo,
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxSize(),
                    )
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 28.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Spacer(Modifier.width(cardWidth).aspectRatio(16f / 9f))
                        }
                        state.error?.let { error ->
                            Spacer(Modifier.height(8.dp))
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetry) { Text("Reintentar") }
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(leftScroll)
                                    .padding(end = 12.dp),
                            ) {
                                VideoInfo(state.video)
                                if (state.related.isNotEmpty()) {
                                    Spacer(Modifier.height(20.dp))
                                    Text(
                                        text = "A continuación",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    state.related.forEach { video ->
                                        RelatedVideoRow(video, onOpenVideo)
                                    }
                                }
                            }
                            AppVerticalScrollbar(
                                state = leftScroll,
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }

        val cardModifier = Modifier
            .offset(x = cardX, y = cardY)
            .width(cardWidth)
            .aspectRatio(16f / 9f)
        Box(cardModifier) {
            PlayerCard(
                state = state,
                playerService = playerService,
                onTogglePlayPause = onTogglePlayPause,
                onSeek = onSeek,
                onVolume = onVolume,
                onMinimize = onMinimize,
                onToggleFullscreen = onToggleFullscreen,
                fullscreen = false,
                controlsVisible = controlsVisible,
            )
        }
    }
}

/**
 * Reproductor de vídeo: card con el vídeo, controles y auto-hide.
 *
 * En fullscreen la card ocupa todo; fuera de fullscreen mantiene su aspect
 * ratio 16:9 y los controles viven dentro de la card.
 */
@Composable
internal fun PlayerCard(
    state: YouTubeWatchUiState,
    playerService: YouTubePlayerService,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Int) -> Unit,
    onMinimize: () -> Unit,
    onToggleFullscreen: () -> Unit,
    fullscreen: Boolean = false,
    controlsVisible: Boolean = true,
) {
    val cardModifier = if (fullscreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    }
    val textureActive = playerService.textureInfo.collectAsState().value != null
    Box(
        modifier = cardModifier
            .clip(if (fullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp))
            .background(Color.Black),
    ) {
        YouTubeVideoSurface(
            playerService = playerService,
            visible = !state.loading,
            modifier = Modifier
                .fillMaxSize()
                // Only the HWND fallback needs a reserved strip; TextureView
                // draws in Compose, so the controls can overlay the video.
                .padding(bottom = if (textureActive) 0.dp else 64.dp),
            contentScale = ContentScale.Fit,
        )

        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.status ?: "Cargando vídeo…",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                    )
                }
                Text(
                    text = formatClock(state.positionMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = state.positionMs.toFloat()
                        .coerceIn(0f, state.durationMs.toFloat().coerceAtLeast(1f)),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                )
                Text(
                    text = formatClock(state.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White)
                Slider(
                    value = state.volume.toFloat(),
                    onValueChange = { onVolume(it.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.width(86.dp).padding(start = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                )
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (fullscreen) "Salir de pantalla completa" else "Pantalla completa",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onMinimize) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Minimizar reproductor",
                        tint = Color.White,
                    )
                }
            }
        }

    }
}

@Composable
private fun VideoInfo(video: YouTubeVideo?) {
    if (video == null) return
    Text(
        text = video.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        WatchChannelAvatar(video)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = video.channelName.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    video.viewCount?.let { append(formatCount(it)).append(" vistas") }
                    video.uploadedText?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    video.description?.takeIf { it.isNotBlank() }?.let { description ->
        Spacer(Modifier.height(14.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WatchChannelAvatar(video: YouTubeVideo) {
    val avatar = video.channelAvatarUrl
    if (avatar != null) {
        MusicPlayerImage(
            url = avatar,
            contentDescription = video.channelName,
            modifier = Modifier.size(40.dp).clip(CircleShape),
            placeholderType = PlaceholderType.ARTIST,
            contentScale = ContentScale.Crop,
            isLowRes = true,
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = video.channelName?.firstOrNull()?.uppercase() ?: "Y",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun RelatedPane(
    related: List<YouTubeVideo>,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = appScrollContentPadding(bottom = 16.dp),
        ) {
            item {
                Text(
                    text = "A continuación",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            items(related, key = { it.id }) { video ->
                RelatedVideoRow(video, onOpenVideo)
            }
        }
        AppVerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun RelatedVideoRow(video: YouTubeVideo, onOpenVideo: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenVideo(video.id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            MusicPlayerImage(
                url = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                placeholderType = PlaceholderType.SONG,
                contentScale = ContentScale.Crop,
                isLowRes = true,
            )
            if (video.durationSeconds > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = formatVideoDuration(video.durationSeconds),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = video.channelName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            video.uploadedText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
