@file:OptIn(ExperimentalSharedTransitionApi::class)

package example.nucleus.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.models.MediaMetadata
import example.nucleus.player.PlaybackState
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.player.LikeToggle
import example.nucleus.ui.components.player.PlayerTransportRow
import example.nucleus.ui.components.player.heroCoverElement
import example.nucleus.ui.themes.songTitle
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.viewmodels.PlayerUiState
import example.nucleus.viewmodels.PlayerViewModel
import org.jetbrains.compose.resources.stringResource
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.mp_error
import example.nucleus.generated.resources.mp_expand
import example.nucleus.generated.resources.mp_next
import example.nucleus.generated.resources.mp_previous
import example.nucleus.generated.resources.mp_queue

/**
 * Diseño COMPACTO del mini reproductor: una sola fila fina con portada,
 * título, controles esenciales y línea de progreso inferior.
 *
 * Versión pura (sin ViewModel) para poder previsualizarla en Ajustes con
 * datos de ejemplo.
 */
@Composable
internal fun MiniPlayerCompactContent(
    song: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    isError: Boolean,
    errorText: String?,
    progress: Float,
    liked: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleQueue: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val expandModifier = if (interactive) {
        Modifier
            .clickable(onClick = onExpand, role = Role.Button)
            .pointerHoverIcon(PointerIcon.Hand)
    } else {
        Modifier
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val showPrev = maxWidth > 360.dp
        val showLike = maxWidth > 420.dp
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(expandModifier)
                        .heroCoverElement(
                            songId = song.id,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            debugTag = "compact",
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MusicPlayerImage(
                        url = song.thumbnailUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
                        contentScale = ContentScale.Crop,
                        placeholderType = PlaceholderType.SONG,
                        iconSize = 20.dp,
                        coilSizeOverride = 96,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(expandModifier),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.songTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isError) {
                            errorText ?: ""
                        } else {
                            song.artists.joinToString(", ") { it.name }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (showLike) {
                    if (interactive) {
                        LikeToggle(liked = liked, onToggle = onToggleLike)
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }
                if (showPrev) {
                    CompactIconButton(
                        onClick = onPrevious,
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(Res.string.mp_previous),
                        enabled = interactive,
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp).padding(8.dp),
                        strokeWidth = 3.dp,
                    )
                } else {
                    FilledIconButton(
                        onClick = { if (interactive) onTogglePlay() },
                        modifier = Modifier
                            .size(44.dp)
                            .then(if (interactive) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                CompactIconButton(
                    onClick = onNext,
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(Res.string.mp_next),
                    enabled = interactive,
                )
                CompactIconButton(
                    onClick = onToggleQueue,
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = stringResource(Res.string.mp_queue),
                    enabled = interactive,
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.IconButton(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        modifier = modifier
            .size(36.dp)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Diseño VINILO del mini reproductor: disco circular que gira al reproducir,
 * título + progreso lineal y transporte centrado.
 *
 * Versión pura (sin ViewModel) para poder previsualizarla en Ajustes con
 * datos de ejemplo.
 */
@Composable
internal fun MiniPlayerVinylContent(
    song: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    isError: Boolean,
    errorText: String?,
    progress: Float,
    liked: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleQueue: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val spin = rememberInfiniteTransition(label = "vinyl")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable<Float>(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
        ),
        label = "vinylAngle",
    )
    val expandModifier = if (interactive) {
        Modifier
            .clickable(onClick = onExpand, role = Role.Button)
            .pointerHoverIcon(PointerIcon.Hand)
    } else {
        Modifier
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val showPrev = maxWidth > 360.dp
        val showLike = maxWidth > 460.dp
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Disco de vinilo: base negra que rota, carátula pequeña como
            // etiqueta y agujero central fijo. La carátula va a 96px: a
            // este tamaño el full-res solo añade ruido y memoria.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .then(expandModifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = if (isPlaying && animationsEnabled) angle else 0f
                        }
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.87f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Surcos del vinilo: anillos sutiles sobre el negro.
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .heroCoverElement(
                                songId = song.id,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                debugTag = "vinyl",
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        MusicPlayerImage(
                            url = song.thumbnailUrl,
                            contentDescription = song.title,
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            contentScale = ContentScale.Crop,
                            placeholderType = PlaceholderType.SONG,
                            iconSize = 20.dp,
                            coilSizeOverride = 96,
                        )
                    }
                }
                // Agujero del eje: fijo, no rota con el disco.
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(expandModifier),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.songTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isError) {
                        errorText ?: ""
                    } else {
                        song.artists.joinToString(", ") { it.name }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
            }

            Spacer(Modifier.width(8.dp))

            if (showLike) {
                if (interactive) {
                    LikeToggle(liked = liked, onToggle = onToggleLike)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (showPrev) {
                CompactIconButton(
                    onClick = onPrevious,
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(Res.string.mp_previous),
                    enabled = interactive,
                )
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp).padding(10.dp),
                    strokeWidth = 3.dp,
                )
            } else {
                FilledIconButton(
                    onClick = { if (interactive) onTogglePlay() },
                    modifier = Modifier
                        .size(44.dp)
                        .then(if (interactive) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            CompactIconButton(
                onClick = onNext,
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(Res.string.mp_next),
                enabled = interactive,
            )
            CompactIconButton(
                onClick = onToggleQueue,
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = stringResource(Res.string.mp_queue),
                enabled = interactive,
            )
        }
    }
}

/**
 * Envoltorios con estado real para usar dentro del MiniPlayer.
 */
@Composable
internal fun MiniPlayerCompactLive(
    song: MediaMetadata,
    state: PlayerUiState,
    isError: Boolean,
    onNowPlaying: () -> Unit,
    onToggleQueue: () -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val progress by playerViewModel.progressState.collectAsState()
    val fraction = if (progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    MiniPlayerCompactContent(
        song = song,
        isPlaying = state.playbackState == PlaybackState.PLAYING,
        isLoading = state.playbackState == PlaybackState.LOADING ||
            state.playbackState == PlaybackState.BUFFERING,
        isError = isError,
        errorText = state.error ?: stringResource(Res.string.mp_error),
        progress = fraction,
        liked = song.liked,
        onTogglePlay = { playerViewModel.togglePlayPause() },
        onPrevious = { playerViewModel.previous() },
        onNext = { playerViewModel.next() },
        onToggleLike = { playerViewModel.toggleLike() },
        onToggleQueue = onToggleQueue,
        onExpand = onNowPlaying,
        modifier = modifier,
        interactive = true,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

@Composable
internal fun MiniPlayerVinylLive(
    song: MediaMetadata,
    state: PlayerUiState,
    isError: Boolean,
    onNowPlaying: () -> Unit,
    onToggleQueue: () -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val progress by playerViewModel.progressState.collectAsState()
    val fraction = if (progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    MiniPlayerVinylContent(
        song = song,
        isPlaying = state.playbackState == PlaybackState.PLAYING,
        isLoading = state.playbackState == PlaybackState.LOADING ||
            state.playbackState == PlaybackState.BUFFERING,
        isError = isError,
        errorText = state.error ?: stringResource(Res.string.mp_error),
        progress = fraction,
        liked = song.liked,
        onTogglePlay = { playerViewModel.togglePlayPause() },
        onPrevious = { playerViewModel.previous() },
        onNext = { playerViewModel.next() },
        onToggleLike = { playerViewModel.toggleLike() },
        onToggleQueue = onToggleQueue,
        onExpand = onNowPlaying,
        modifier = modifier,
        interactive = true,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

/** Canción de ejemplo para las vistas previas de Ajustes. */
internal val MiniPlayerPreviewSong = MediaMetadata(
    id = "preview",
    title = "Canción de ejemplo",
    artists = listOf(MediaMetadata.Artist(id = null, name = "Artista de ejemplo")),
    duration = 187000,
    thumbnailUrl = null,
)
