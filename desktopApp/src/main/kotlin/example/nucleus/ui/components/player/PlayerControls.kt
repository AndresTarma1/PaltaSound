@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import example.nucleus.data.repository.SeekBarStyle
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.mp_like
import example.nucleus.generated.resources.mp_next
import example.nucleus.generated.resources.mp_previous
import example.nucleus.generated.resources.mp_repeat
import example.nucleus.generated.resources.mp_shuffle
import example.nucleus.generated.resources.mp_volume
import example.nucleus.generated.resources.tray_pause
import example.nucleus.generated.resources.tray_play
import example.nucleus.player.PlaybackState
import example.nucleus.ui.components.PlayerSeekBar
import example.nucleus.ui.components.SlimSlider
import example.nucleus.ui.components.TimeText
import example.nucleus.ui.helpers.onHover
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.rememberInteractionSpring
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.viewmodels.PlayerViewModel
import example.nucleus.viewmodels.RepeatMode
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
/**
 * Toggle M3E: contenedor tonal suave cuando está activo, transparente al reposo.
 * Hover desktop con state-layer sutil vía IconButtonDefaults.
 */
@Composable
fun PlayerIconToggle(
    selected: Boolean,
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    selectedContainer: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
    selectedContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedContent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val container = when {
        selected -> selectedContainer
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .pointerHoverIcon(PointerIcon.Hand),
        interactionSource = interaction,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = container,
            contentColor = if (selected) selectedContent else unselectedContent,
        ),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Fila de transporte Expressive: shuffle · anterior · play pill · siguiente · repeat.
 * Compartida por el MiniPlayer y la columna izquierda de Now Playing.
 */
@Composable
fun PlayerTransportRow(
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = LocalPlayerViewModel.current,
) {
    val state by playerViewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val isPlaying = state.playbackState == PlaybackState.PLAYING
    val isLoading = state.playbackState == PlaybackState.LOADING ||
        state.playbackState == PlaybackState.BUFFERING

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        PlayerIconToggle(
            selected = state.isShuffled,
            onClick = { playerViewModel.toggleShuffle() },
            imageVector = Icons.Rounded.Shuffle,
            contentDescription = stringResource(Res.string.mp_shuffle),
            size = 36.dp,
            iconSize = 20.dp,
        )

        IconButton(
            onClick = { playerViewModel.previous() },
            modifier = Modifier
                .size(44.dp)
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(Res.string.mp_previous),
                tint = colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }

        // Play pill — acento Expressive principal
        FilledIconButton(
            onClick = { playerViewModel.togglePlayPause() },
            modifier = Modifier
                .size(width = 56.dp, height = 48.dp)
                .pointerHoverIcon(PointerIcon.Hand),
            shape = AppShapes.extraLarge,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
            ),
        ) {
            if (isLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(28.dp),
                    color = colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) {
                        stringResource(Res.string.tray_pause)
                    } else {
                        stringResource(Res.string.tray_play)
                    },
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        IconButton(
            onClick = { playerViewModel.next() },
            modifier = Modifier
                .size(44.dp)
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = stringResource(Res.string.mp_next),
                tint = colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }

        val repeatOn = state.repeatMode != RepeatMode.OFF
        PlayerIconToggle(
            selected = repeatOn,
            onClick = { playerViewModel.toggleRepeat() },
            imageVector = if (state.repeatMode == RepeatMode.ONE) {
                Icons.Rounded.RepeatOne
            } else {
                Icons.Rounded.Repeat
            },
            contentDescription = stringResource(Res.string.mp_repeat),
            size = 36.dp,
            iconSize = 20.dp,
        )
    }
}

/**
 * Fila de progreso: tiempo actual · seek bar (estilo configurable) · duración.
 * Arrastrable con seek local hasta soltar, igual que en el MiniPlayer.
 */
@Composable
fun PlayerProgressRow(
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = LocalPlayerViewModel.current,
) {
    val state by playerViewModel.uiState.collectAsState()
    val progressState by playerViewModel.progressState.collectAsState()
    val isPlaying = state.playbackState == PlaybackState.PLAYING

    var localSliderValue by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val seekBarStyle by playerViewModel.seekBarStyle.collectAsState(SeekBarStyle.WAVY)

    LaunchedEffect(progressState.positionMs, progressState.durationMs) {
        if (!isDragging && progressState.durationMs > 0) {
            localSliderValue = progressState.positionMs.toFloat() / progressState.durationMs
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimeText(
            if (isDragging) {
                (localSliderValue * progressState.durationMs).toLong()
            } else {
                progressState.positionMs
            },
        )

        PlayerSeekBar(
            style = seekBarStyle,
            value = localSliderValue,
            onValueChange = {
                isDragging = true
                localSliderValue = it
            },
            onValueChangeFinished = {
                val targetPosition = (localSliderValue * progressState.durationMs).toLong()
                playerViewModel.seekTo(targetPosition)
                isDragging = false
            },
            modifier = Modifier.weight(1f),
            isPlaying = isPlaying || isDragging,
        )

        TimeText(progressState.durationMs)
    }
}

/**
 * Píldora de volumen M3E: porcentaje · slider fantasma · mute.
 */
@Composable
fun PlayerVolumeControl(
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = LocalPlayerViewModel.current,
) {
    val volume by playerViewModel.volume.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val volumeFloat = (volume.coerceIn(0, 100)) / 100f
    val volumePercent = volume.coerceIn(0, 100)
    var hovered by remember { mutableStateOf(false) }

    Surface(
        shape = AppShapes.extraLarge,
        color = if (hovered) {
            colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
        } else {
            colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (hovered) {
                colorScheme.outlineVariant.copy(alpha = 0.35f)
            } else {
                Color.Transparent
            },
        ),
        modifier = modifier.onHover { hovered = it },
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "$volumePercent",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
                color = if (volumePercent == 0) {
                    colorScheme.error
                } else if (hovered) {
                    colorScheme.primary
                } else {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.End,
            )

            SlimSlider(
                value = volumeFloat,
                onValueChange = { playerViewModel.setVolume((it * 100).toInt()) },
                modifier = Modifier.width(72.dp),
                activeColor = if (volumePercent == 0) colorScheme.error else colorScheme.primary,
                inactiveColor = colorScheme.onSurface.copy(alpha = 0.16f),
                trackHeight = 4.dp,
                thumbSize = if (hovered) 10.dp else 8.dp,
                draggedThumbSize = 14.dp,
            )

            IconButton(
                onClick = { playerViewModel.toggleMute() },
                modifier = Modifier
                    .size(36.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    imageVector = when {
                        volumeFloat == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                        volumeFloat < 0.4f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = stringResource(Res.string.mp_volume),
                    tint = if (volumePercent == 0) colorScheme.error else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Posicionador personalizado de Popup que ubica la tarjeta flotante de volumen
 * exactamente por encima (arriba) del botón mute, centrada horizontalmente.
 */
private class AboveAnchorPopupPositionProvider(
    private val gapPx: Int = 8,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val y = anchorBounds.top - popupContentSize.height - gapPx

        val clampedX = x.coerceIn(8, maxOf(8, windowSize.width - popupContentSize.width - 8))
        val clampedY = y.coerceAtLeast(8)

        return IntOffset(clampedX, clampedY)
    }
}

/**
 * Volumen de Now Playing estilo flotante (Popup) con hover:
 * El botón se mantiene fijo en el layout sin desplazar a otros elementos.
 * Al pasar el ratón o arrastrar, despliega una tarjeta elegante con porcentaje
 * y pista vertical de volumen flotando sutilmente sobre el botón.
 */
@Composable
fun PlayerVolumeVertical(
    modifier: Modifier = Modifier,
    trackHeight: Dp = 120.dp,
    playerViewModel: PlayerViewModel = LocalPlayerViewModel.current,
    onBusyChange: ((Boolean) -> Unit)? = null,
) {
    val volume by playerViewModel.volume.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val volumePercent = volume.coerceIn(0, 100)
    val fraction = volumePercent / 100f

    var isButtonHovered by remember { mutableStateOf(false) }
    var isPopupHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var showPopup by remember { mutableStateOf(false) }

    // El popup abierto (o el arrastre) mantiene despierto el auto-hide de Now Playing
    LaunchedEffect(showPopup) { onBusyChange?.invoke(showPopup) }
    DisposableEffect(Unit) { onDispose { onBusyChange?.invoke(false) } }

    // Periodo de gracia para evitar desvanecimientos repentinos al mover el puntero entre botón y popup
    LaunchedEffect(isButtonHovered, isPopupHovered, isDragging) {
        if (isButtonHovered || isPopupHovered || isDragging) {
            showPopup = true
        } else {
            delay(250.milliseconds)
            if (!isButtonHovered && !isPopupHovered && !isDragging) {
                showPopup = false
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (showPopup) {
            Popup(
                popupPositionProvider = remember { AboveAnchorPopupPositionProvider(gapPx = 6) },
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = false),
            ) {
                AnimatedVisibility(
                    visible = showPopup,
                    enter = fadeIn(tween(150)) +
                        slideInVertically(tween(180)) { it / 3 } +
                        scaleIn(spring(), transformOrigin = TransformOrigin(0.5f, 1f)),
                    exit = fadeOut(tween(100)) +
                        slideOutVertically(tween(120)) { it / 3 } +
                        scaleOut(transformOrigin = TransformOrigin(0.5f, 1f)),
                ) {
                    Surface(
                        modifier = Modifier
                            .onHover { isPopupHovered = it }
                            .shadow(16.dp, RoundedCornerShape(22.dp))
                            .border(
                                width = 1.dp,
                                color = colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(22.dp),
                            ),
                        shape = RoundedCornerShape(22.dp),
                        color = colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                        tonalElevation = 8.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .width(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Indicator de porcentaje o mute
                            Text(
                                text = if (volumePercent == 0) "MUTE" else "$volumePercent%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = if (volumePercent == 0) {
                                    colorScheme.error
                                } else {
                                    colorScheme.primary
                                },
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )

                            // Pista vertical interactiva
                            val density = LocalDensity.current
                            val trackHeightPx = with(density) { trackHeight.toPx() }

                            val thumbSize by animateDpAsState(
                                targetValue = if (isDragging || isPopupHovered) 18.dp else 14.dp,
                                animationSpec = rememberInteractionSpring(),
                                label = "volumeThumbSize",
                            )

                            fun setFromPosition(y: Float) {
                                val newFraction = (1f - y / trackHeightPx).coerceIn(0f, 1f)
                                playerViewModel.setVolume((newFraction * 100).roundToInt())
                            }

                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(trackHeight)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .pointerInput(playerViewModel) {
                                        detectTapGestures { offset -> setFromPosition(offset.y) }
                                    }
                                    .pointerInput(playerViewModel) {
                                        detectVerticalDragGestures(
                                            onDragStart = { isDragging = true },
                                            onDragEnd = { isDragging = false },
                                            onDragCancel = { isDragging = false },
                                        ) { change, _ ->
                                            change.consume()
                                            setFromPosition(change.position.y)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                // Fondo inactivo (cápsula estilizada)
                                Box(
                                    modifier = Modifier
                                        .width(8.dp)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(
                                            if (volumePercent == 0) {
                                                colorScheme.errorContainer.copy(alpha = 0.4f)
                                            } else {
                                                colorScheme.onSurface.copy(alpha = 0.12f)
                                            },
                                        ),
                                )

                                // Relleno activo (desde el fondo)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .width(8.dp)
                                        .fillMaxHeight(fraction)
                                        .clip(CircleShape)
                                        .background(
                                            if (volumePercent == 0) {
                                                colorScheme.error
                                            } else {
                                                colorScheme.primary
                                            },
                                        ),
                                )

                                // Perilla flotante
                                val thumbOffsetPx = with(density) {
                                    val usableHeight = trackHeightPx - thumbSize.toPx()
                                    (usableHeight * (1f - fraction)).roundToInt()
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset { IntOffset(0, thumbOffsetPx) }
                                        .size(thumbSize)
                                        .shadow(4.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(
                                            if (volumePercent == 0) colorScheme.error else colorScheme.primary,
                                        )
                                        .border(
                                            width = 2.dp,
                                            color = colorScheme.surface,
                                            shape = CircleShape,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Botón Mute / Volumen fijo
        FilledTonalIconButton(
            onClick = { playerViewModel.toggleMute() },
            modifier = Modifier
                .size(40.dp)
                .onHover { isButtonHovered = it }
                .pointerHoverIcon(PointerIcon.Hand),
            shape = RoundedCornerShape(12.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isButtonHovered || showPopup) {
                    colorScheme.surfaceContainerHighest
                } else {
                    colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                },
                contentColor = if (volumePercent == 0) colorScheme.error else colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = when {
                    volumePercent == 0 -> Icons.AutoMirrored.Rounded.VolumeOff
                    volumePercent < 40 -> Icons.AutoMirrored.Rounded.VolumeDown
                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                },
                contentDescription = stringResource(Res.string.mp_volume),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Botón de "me gusta" (corazón) reutilizado por MiniPlayer y Now Playing.
 */
@Composable
fun LikeToggle(
    liked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    PlayerIconToggle(
        selected = liked,
        onClick = onToggle,
        imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription = stringResource(Res.string.mp_like),
        modifier = modifier,
        selectedContainer = colorScheme.errorContainer.copy(alpha = 0.55f),
        selectedContent = colorScheme.error,
        unselectedContent = colorScheme.onSurfaceVariant,
    )
}
