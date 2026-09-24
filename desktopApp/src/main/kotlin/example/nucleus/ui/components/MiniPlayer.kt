@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import example.nucleus.data.repository.LayoutMode
import example.nucleus.data.repository.MiniPlayerBackgroundStyle
import example.nucleus.player.PlaybackState
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.mp_collapse
import example.nucleus.generated.resources.mp_error
import example.nucleus.generated.resources.mp_expand
import example.nucleus.generated.resources.mp_queue
import example.nucleus.generated.resources.play_item
import example.nucleus.ui.components.artwork.LocalArtworkColors
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.player.heroCoverElement
import example.nucleus.ui.components.player.PlayerIconToggle
import example.nucleus.ui.components.player.PlayerProgressRow
import example.nucleus.ui.components.player.PlayerTransportRow
import example.nucleus.ui.components.player.PlayerVolumeControl
import example.nucleus.ui.components.player.LikeToggle
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalChromeSurface
import example.nucleus.ui.themes.LocalDimens
import example.nucleus.ui.themes.LocalLayoutMode
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.expressiveLayoutTween
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.ui.themes.rememberUiTween
import example.nucleus.ui.themes.songTitle
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.isWideThumbnail
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

/**
 * Barra / tarjeta de reproducción compacta al estilo Media 3 Expressive (YouTube Music / Pixel).
 *
 * Firma visual: cover redondeado con hover expand, título emphasized, play pill central
 * y toggles tonales (shuffle/repeat/queue) — sin lógica de playback propia.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayer(
    isOnNowPlaying: Boolean,
    onNowPlaying: () -> Unit,
    onToggleQueue: () -> Unit,
    isQueueVisible: Boolean,
    bgTransparent: Boolean = false,
    floating: Boolean = false,
    isDocked: Boolean = false,
    backgroundStyle: MiniPlayerBackgroundStyle = MiniPlayerBackgroundStyle.TRANSLUCENT,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val state by playerViewModel.uiState.collectAsState()
    val song = state.currentSong ?: return

    val isError = state.playbackState == PlaybackState.ERROR

    val ratio = remember(song.thumbnailUrl) {
        if (isWideThumbnail(song.thumbnailUrl)) 16f / 9f else 1f
    }
    val artworkColors = LocalArtworkColors.current

    val dimens = LocalDimens.current
    val chromeSurface = LocalChromeSurface.current
    val islands = LocalLayoutMode.current == LayoutMode.ISLANDS
    val square = LocalLayoutMode.current == LayoutMode.SQUARE
    val surfaceShape = if (islands) RoundedCornerShape(dimens.surfaceCorner) else RectangleShape
    val colorScheme = MaterialTheme.colorScheme

    val playerContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // —— IZQUIERDA: cover + metadatos + like ——
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val infoHeight = maxHeight
                    val thumbSize = (infoHeight * 0.88f).coerceIn(48.dp, 68.dp)
                    val coverShape = AppShapes.medium

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val animationsEnabled = LocalAnimationsEnabled.current
                        AnimatedVisibility(
                            visible = !isOnNowPlaying,
                            enter = if (animationsEnabled) {
                                fadeIn(expressiveFadeTween()) +
                                    expandHorizontally(animationSpec = expressiveLayoutTween())
                            } else {
                                EnterTransition.None
                            },
                            exit = if (animationsEnabled) {
                                fadeOut(expressiveTween(180)) +
                                    shrinkHorizontally(animationSpec = expressiveLayoutTween())
                            } else {
                                ExitTransition.None
                            },
                        ) {
                            var isHovered by remember { mutableStateOf(false) }
                            val overlayAlpha by animateFloatAsState(
                                targetValue = if (isHovered) 0.42f else 0f,
                                animationSpec = rememberUiTween(durationMillis = 160),
                                label = "coverHover",
                            )

                            Box(
                                modifier = Modifier
                                    .sizeIn(maxWidth = thumbSize * ratio, maxHeight = thumbSize)
                                    .aspectRatio(ratio)
                                    .heroCoverElement(song.id, sharedTransitionScope, this)
                                    .clip(coverShape)
                                    .onHover { isHovered = it }
                                    .clickable(onClick = onNowPlaying, role = Role.Button)
                                    .pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                MusicPlayerImage(
                                    url = song.thumbnailUrl,
                                    contentDescription = song.title,
                                    modifier = Modifier.fillMaxSize(),
                                    shape = coverShape,
                                    contentScale = ContentScale.Crop,
                                    placeholderType = PlaceholderType.SONG,
                                    iconSize = 24.dp,
                                )

                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .alpha(overlayAlpha)
                                        .background(Color.Black),
                                )

                                if (overlayAlpha > 0.01f) {
                                    Icon(
                                        imageVector = if (isOnNowPlaying) {
                                            Icons.Rounded.ExpandMore
                                        } else {
                                            Icons.Rounded.ExpandLess
                                        },
                                        contentDescription = if (isOnNowPlaying) {
                                            stringResource(Res.string.mp_collapse)
                                        } else {
                                            stringResource(Res.string.mp_expand)
                                        },
                                        tint = Color.White.copy(alpha = overlayAlpha.coerceIn(0f, 1f) / 0.42f),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(28.dp)
                                            .alpha(overlayAlpha / 0.42f),
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onNowPlaying, role = Role.Button)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(end = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.songTitle,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isError) {
                                    state.error ?: stringResource(Res.string.mp_error)
                                } else {
                                    song.artists.joinToString(", ") { it.name }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) {
                                    colorScheme.error
                                } else {
                                    colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        val liked = state.currentSong?.liked == true
                        LikeToggle(
                            liked = liked,
                            onToggle = { playerViewModel.toggleLike() },
                        )
                    }
                }

                // —— CENTRO: transporte + seek (firma Google media) ——
                Column(
                    modifier = Modifier.weight(1.55f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    PlayerTransportRow()
                    PlayerProgressRow()
                }

                // —— DERECHA: volumen + cola + expand ——
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerVolumeControl(modifier = Modifier.padding(end = 6.dp))

                    PlayerIconToggle(
                        selected = isQueueVisible,
                        onClick = onToggleQueue,
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(Res.string.mp_queue),
                        size = 40.dp,
                        iconSize = 22.dp,
                    )

                    FilledTonalIconButton(
                        onClick = onNowPlaying,
                        modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isOnNowPlaying) {
                                colorScheme.primaryContainer
                            } else {
                                colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                            },
                            contentColor = if (isOnNowPlaying) {
                                colorScheme.onPrimaryContainer
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                        ),
                    ) {
                        Icon(
                            imageVector = if (isOnNowPlaying) {
                                Icons.Rounded.ExpandMore
                            } else {
                                Icons.Rounded.ExpandLess
                            },
                            contentDescription = stringResource(Res.string.play_item),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }

    if (floating) {
        // Tarjeta flotante: SOLID / COVER / TRANSLUCENT (Haze).
        val floatingShape = AppShapes.extraLarge
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = dimens.miniPlayerFloatingMargin)
                .height(dimens.miniPlayerHeight)
                .clip(floatingShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .then(
                    when (backgroundStyle) {
                        MiniPlayerBackgroundStyle.TRANSLUCENT -> if (hazeState != null) {
                            val style = HazeMaterials.ultraThin(
                                containerColor = colorScheme.surfaceContainer,
                            )
                            Modifier.hazeBlur(
                                input = HazeInput.Sources(hazeState),
                                style = style,
                            )
                        } else {
                            Modifier.background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        colorScheme.surfaceContainer.copy(alpha = 0.94f),
                                        colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                                    ),
                                ),
                            )
                        }
                        else -> Modifier.background(colorScheme.surfaceContainer)
                    },
                )
                .border(
                    width = 1.dp,
                    color = colorScheme.outlineVariant.copy(alpha = 0.55f),
                    shape = floatingShape,
                ),
        ) {
            if (backgroundStyle == MiniPlayerBackgroundStyle.COVER) {
                MusicPlayerImage(
                    url = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(42.dp),
                    shape = RectangleShape,
                    contentScale = ContentScale.Crop,
                    placeholderType = PlaceholderType.SONG,
                    iconSize = 24.dp,
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    artworkColors.vibrant.copy(alpha = 0.72f),
                                    artworkColors.muted.copy(alpha = 0.58f),
                                ),
                            ),
                        ),
                )
            }
            playerContent()
        }
    } else if (isDocked) {
        // Pegado abajo, como flotante pero sin margenes flotantes — contenido de las rutas se ve por detras con Haze
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(dimens.miniPlayerHeight)
                .then(
                    if (isOnNowPlaying || bgTransparent) {
                        Modifier.background(Color.Transparent)
                    } else {
                        when (backgroundStyle) {
                            MiniPlayerBackgroundStyle.TRANSLUCENT -> if (hazeState != null) {
                                val style = HazeMaterials.ultraThin(
                                    containerColor = colorScheme.surfaceContainer,
                                )
                                Modifier.hazeBlur(
                                    input = HazeInput.Sources(hazeState),
                                    style = style,
                                )
                            } else {
                                Modifier.background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            colorScheme.surfaceContainer.copy(alpha = 0.94f),
                                            colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                                        ),
                                    ),
                                )
                            }
                            MiniPlayerBackgroundStyle.SOLID -> Modifier.background(colorScheme.surfaceContainer)
                            MiniPlayerBackgroundStyle.COVER -> Modifier.background(colorScheme.surfaceContainer)
                        }
                    }
                ),
        ) {
            if (backgroundStyle == MiniPlayerBackgroundStyle.COVER && !isOnNowPlaying && !bgTransparent) {
                MusicPlayerImage(
                    url = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(32.dp),
                    shape = RectangleShape,
                    contentScale = ContentScale.Crop,
                    placeholderType = PlaceholderType.SONG,
                    iconSize = 24.dp,
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    artworkColors.vibrant.copy(alpha = 0.6f),
                                    artworkColors.muted.copy(alpha = 0.5f),
                                ),
                            ),
                        ),
                )
            }
            playerContent()
        }
    } else if (square) {
        Column(modifier = modifier.fillMaxWidth().height(dimens.miniPlayerHeight)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.chromeBorderWidth)
                    .background(colorScheme.outlineVariant),
            )
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = chromeSurface,
                shape = RectangleShape,
                tonalElevation = 2.dp,
            ) {
                playerContent()
            }
        }
    } else {
        val useHazeDocked = isDocked && backgroundStyle == MiniPlayerBackgroundStyle.TRANSLUCENT && hazeState != null && !isOnNowPlaying && !bgTransparent
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (useHazeDocked) {
                        val style = HazeMaterials.ultraThin(containerColor = colorScheme.surfaceContainer)
                        Modifier.hazeBlur(
                            input = HazeInput.Sources(hazeState!!),
                            style = style,
                        )
                    } else Modifier
                )
                .then(
                    if (islands) {
                        Modifier
                            .padding(horizontal = dimens.surfaceGap, vertical = dimens.surfaceGap)
                            .border(
                                dimens.chromeBorderWidth,
                                colorScheme.outlineVariant.copy(alpha = 0.7f),
                                surfaceShape,
                            )
                    } else {
                        Modifier
                    },
                )
                .height(dimens.miniPlayerHeight),
            color = if (useHazeDocked) Color.Transparent else chromeSurface,
            shape = surfaceShape,
            tonalElevation = 2.dp,
            shadowElevation = if (islands) 2.dp else 0.dp,
        ) {
            playerContent()
        }
    }
}

@Composable
fun TimeText(millis: Long, seekValue: Float? = null) {
    Text(
        text = formatPlayerTimeValue(millis),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(36.dp),
        textAlign = TextAlign.Center,
    )
}
