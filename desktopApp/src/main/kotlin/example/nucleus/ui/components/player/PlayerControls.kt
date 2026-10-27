@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import example.nucleus.ui.components.TimeText
import example.nucleus.ui.helpers.onHover
import example.nucleus.ui.helpers.pointerScrollToInt
import example.nucleus.ui.themes.AppShapes
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.viewmodels.PlayerViewModel
import example.nucleus.viewmodels.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import example.nucleus.ui.themes.rememberInteractionSpring
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

        // Play pill con acento Expressive principal
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
 * Control de volumen del MiniPlayer: botón de mute + pista vertical al hover + tooltip del % .
 *
 * Sustituye a la antigua "píldora" (porcentaje + slider horizontal + mute en línea) que vivía aquí.
 * Era cramped: 24dp de ancho para el número, 72dp de pista y tres elementos compitiendo en el alto
 * de una barra estrecha, con tipografía ad-hoc de 10sp que se veía como maqueta.
 *
 * El layout no se mueve nunca porque la pista y el tooltip viven en un popup. Se unificó el
 * componente para que MiniPlayer y Now Playing compartan implementación.
 */
@Composable
fun PlayerVolumeControl(
    modifier: Modifier = Modifier,
    trackHeight: Dp = 132.dp,
    playerViewModel: PlayerViewModel = LocalPlayerViewModel.current,
    onBusyChange: ((Boolean) -> Unit)? = null,
) {
    PlayerVolumeTooltip(
        modifier = modifier,
        trackHeight = trackHeight,
        playerViewModel = playerViewModel,
        onBusyChange = onBusyChange,
    )
}

/**
 * Implementación única del volumen.
 *
 * Al pasar el ratón por el icono emerge **la pista vertical arrastrable** (una tarjeta con solo la
 * pista, sin cápsula ni mute) y, justo encima, **un tooltip con el porcentaje**. El mute se queda en
 * el icono, que es donde ya estaba y donde cabe bien.
 *
 * POR QUÉ [TooltipBox] Y NO UN `Popup` A MANO
 * ------------------------------------------
 * Antes esto era un `Popup` con dos `PopupPositionProvider` escritos a mano, un `AnimatedVisibility`
 * con `tween` + `spring` a pulso y un `LaunchedEffect` que tenía que mantener tres estados de hover
 * (botón, popup, arrastre) en sincronía con un periodo de gracia de 250 ms para que no parpadease al
 * cruzar del icono a la tarjeta. Todo eso lo trae ya `TooltipBox`:
 *
 *  - **Posición**: `TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above, …)`
 *    centra y además da la vuelta a "debajo" si no cabe arriba. Los dos posicionadores a mano solo
 *    centtaban, y con la ventana estrecha la tarjeta se salía por arriba.
 *  - **Aparición**: escala 0.8 -> 1 y alpha 0 -> 1 con los muelles del `MotionScheme`.
 *  - **Cierre al hacer click fuera**, que el `Popup` a mano no tenía.
 *
 * LO ÚNICO QUE HAY QUE CONTROLAR A MANO
 * -------------------------------------
 * Al salir el ratón del icono, M3 cierra el tooltip **salvo que sea persistente**:
 *
 * ```kotlin
 * PointerEventType.Exit -> if (!state.isPersistent) state.dismiss()
 * ```
 *
 * Aquí hace falta persistente, porque el puntero tiene que poder **entrar** en la pista para
 * arrastrar. Eso deja el cierre en nuestras manos y el periodo de gracia evita el parpadeo al
 * cruzar del icono a la tarjeta.
 *
 * `focusable = false` para que no robe el foco ni cierre los menús que hubiera abiertos, y
 * `hasAction = true` porque la pista es arrastrable.
 *
 * El tooltip lleva dentro las dos cosas apiladas (etiqueta + tarjeta) porque el ancla tiene que
 * estar en la composición principal: si la pista fuera el ancla, se estaría posicionando respecto a
 * algo que solo existe porque ella misma está visible.
 */
@Composable
fun PlayerVolumeTooltip(
    modifier: Modifier = Modifier,
    trackHeight: Dp = 132.dp,
    playerViewModel: PlayerViewModel = LocalPlayerViewModel.current,
    onBusyChange: ((Boolean) -> Unit)? = null,
) {
    val volume by playerViewModel.volume.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val volumePercent = volume.coerceIn(0, 100)
    val fraction = volumePercent / 100f
    val isMuted = volumePercent == 0

    var isButtonHovered by remember { mutableStateOf(false) }
    var isCardHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // Persistente a proposito: es lo que permite pasar el raton del icono a la pista sin que se
    // cierre. El cierre lo hace el LaunchedEffect de abajo.
    val tooltipState = rememberTooltipState(isPersistent = true)
    val isVisible = tooltipState.isVisible

    // El tooltip abierto (o el arrastre) mantiene despierto el auto-hide de Now Playing
    LaunchedEffect(isVisible) { onBusyChange?.invoke(isVisible) }
    DisposableEffect(Unit) { onDispose { onBusyChange?.invoke(false) } }

    // El abrir lo hace M3 con el hover del ancla (enableUserInput = true). Aqui solo se decide
    // cuando cerrar, con un periodo de gracia para que no parpadee al cruzar a la pista.
    LaunchedEffect(isButtonHovered, isCardHovered, isDragging) {
        if (!isButtonHovered && !isCardHovered && !isDragging) {
            delay(250.milliseconds)
            if (!isButtonHovered && !isCardHovered && !isDragging) {
                tooltipState.dismiss()
            }
        }
    }

    val positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
        positioning = TooltipAnchorPosition.Above,
        spacingBetweenTooltipAndAnchor = 8.dp,
    )

    TooltipBox(
        modifier = modifier,
        positionProvider = positionProvider,
        state = tooltipState,
        focusable = false,
        enableUserInput = true,
        hasAction = true,
        tooltip = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PlainTooltip {
                    Text("$volumePercent%")
                }
                VolumeTrackCard(
                    fraction = fraction,
                    isMuted = isMuted,
                    isHovered = isCardHovered,
                    isDragging = isDragging,
                    trackHeight = trackHeight,
                    onHoverChange = { isCardHovered = it },
                    onDragStateChange = { isDragging = it },
                    onVolumeChange = { playerViewModel.setVolume(it) },
                )
            }
        },
    ) {
        FilledTonalIconButton(
            onClick = { playerViewModel.toggleMute() },
            modifier = Modifier
                .size(40.dp)
                .onHover { isButtonHovered = it }
                .pointerScrollToInt(
                    value = volumePercent,
                    range = 0..100,
                    onValueChange = { playerViewModel.setVolume(it) },
                )
                .pointerHoverIcon(PointerIcon.Hand),
            shape = AppShapes.medium,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isButtonHovered || isVisible) {
                    colorScheme.secondaryContainer
                } else {
                    colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                },
                contentColor = when {
                    isMuted -> colorScheme.error
                    isButtonHovered || isVisible -> colorScheme.onSecondaryContainer
                    else -> colorScheme.onSurfaceVariant
                },
            ),
        ) {
            Icon(
                imageVector = when {
                    isMuted -> Icons.AutoMirrored.Rounded.VolumeOff
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
 * La tarjeta de la pista vertical arrastrable. Solo la pista: el porcentaje va en el tooltip de
 * [PlayerVolumeTooltip] y el mute se quedó en el icono, así que aquí no cabe nada más.
 *
 * Va en su propio composable porque es la mitad "contenido" del tooltip, compartida por MiniPlayer
 * y Now Playing; el contenedor, la etiqueta y el botón los aporta [PlayerVolumeTooltip].
 *
 * NO se usa `RichTooltip` como contenedor: fuerza `TooltipMinWidth` y `TooltipMinHeight`, que están
 * calculados para un título y una línea de texto, y descuadrarían una pista vertical estrecha. Se
 * usa `Surface` con los tokens del tema.
 */
@Composable
private fun VolumeTrackCard(
    fraction: Float,
    isMuted: Boolean,
    isHovered: Boolean,
    isDragging: Boolean,
    trackHeight: Dp,
    onHoverChange: (Boolean) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onVolumeChange: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.onHover(onHoverChange),
        shape = AppShapes.large,
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val density = LocalDensity.current
            val trackHeightPx = with(density) { trackHeight.toPx() }

            // La perilla crece con hover/arrastre (muelle), como pide M3 Expressive.
            val thumbSize by animateDpAsState(
                targetValue = when {
                    isDragging -> 20.dp
                    isHovered -> 18.dp
                    else -> 14.dp
                },
                animationSpec = rememberInteractionSpring(),
                label = "volumeThumbSize",
            )

            fun setFromPosition(y: Float) {
                val newFraction = (1f - y / trackHeightPx).coerceIn(0f, 1f)
                onVolumeChange((newFraction * 100).roundToInt())
            }

            Box(
                modifier = Modifier
                    // 24dp de zona tactil, no de pista visible: la guia de targets pide 32-48dp y
                    // el resto se come el padding de la tarjeta.
                    .width(24.dp)
                    .height(trackHeight)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(Unit) {
                        detectTapGestures { offset -> setFromPosition(offset.y) }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { onDragStateChange(true) },
                            onDragEnd = { onDragStateChange(false) },
                            onDragCancel = { onDragStateChange(false) },
                        ) { change, _ ->
                            change.consume()
                            setFromPosition(change.position.y)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Carril inactivo
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (isMuted) {
                                colorScheme.errorContainer.copy(alpha = 0.5f)
                            } else {
                                colorScheme.onSurface.copy(alpha = 0.12f)
                            },
                        ),
                )

                // Relleno activo, desde abajo
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(8.dp)
                        .fillMaxHeight(fraction)
                        .clip(CircleShape)
                        .background(if (isMuted) colorScheme.error else colorScheme.primary),
                )

                // Perilla. El offset se calcula con el hueco que deja la propia perilla, para que
                // en 0 y en 100 quede entera dentro de la pista en vez de salirse media.
                val thumbOffsetPx = with(density) {
                    val usableHeight = trackHeightPx - thumbSize.toPx()
                    (usableHeight * (1f - fraction)).roundToInt()
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, thumbOffsetPx) }
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(if (isMuted) colorScheme.error else colorScheme.primary),
                )
            }
        }
    }
}

/**
 * Volumen de Now Playing: mismo control que el MiniPlayer.
 *
 * Se mantiene este nombre porque lo usan dos sitios en NowPlayingLayouts; la lógica vive toda en
 * [PlayerVolumeTooltip], así que ambos sitios comparten implementación.
 */
@Composable
fun PlayerVolumeVertical(
    modifier: Modifier = Modifier,
    trackHeight: Dp = 120.dp,
    playerViewModel: PlayerViewModel = LocalPlayerViewModel.current,
    onBusyChange: ((Boolean) -> Unit)? = null,
) {
    PlayerVolumeTooltip(
        modifier = modifier,
        trackHeight = trackHeight,
        playerViewModel = playerViewModel,
        onBusyChange = onBusyChange,
    )
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
        selectedContent = colorScheme.error,
        unselectedContent = colorScheme.onSurfaceVariant,
    )
}
