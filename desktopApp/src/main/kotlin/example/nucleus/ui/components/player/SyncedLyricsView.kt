package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.data.repository.LyricsAnimationStyle
import example.nucleus.lyrics.LyricLine
import example.nucleus.lyrics.Romanizer
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.lyrics_instrumental
import example.nucleus.generated.resources.lyrics_sync_jump
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.expressiveScrollDuration
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.ui.themes.uiTween
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.utils.LocalUserPreferences
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * Aplica un desvanecimiento suave con gradiente a los bordes superior e inferior
 * (estilo Spotify / Metrolist) para que el contenido de letras se difumine elegantemente.
 */
fun Modifier.lyricsFadingEdges(
    topFade: Dp = 56.dp,
    bottomFade: Dp = 80.dp,
): Modifier = this.then(
    Modifier
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val topPx = topFade.toPx()
            val bottomPx = bottomFade.toPx()
            if (size.height <= 0f) return@drawWithContent

            if (topPx > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f,
                        endY = topPx,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottomPx > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - bottomPx,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
)

/**
 * Letras sincronizadas interactivas al estilo Spotify / Metrolist:
 * - Desvanecido continuo en bordes superior e inferior
 * - La línea activa se resalta con opacidad completa, peso destacado y relleno karaoke por palabra
 * - Las líneas inactivas se atenúan suavemente y se iluminan al pasar el cursor
 * - Detección de navegación manual con botón flotante para resincronizar
 * - Soporte para pausas instrumentales con indicador animado
 * - Romanización integrada
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textAlign: Boolean = true, // true = alineado al inicio (estilo Spotify/Metrolist), false = centrado
) {
    val userPreferences = LocalUserPreferences.current
    val textSize by userPreferences.lyricsTextSize.collectAsState(36f)
    val lineSpacing by userPreferences.lyricsLineSpacing.collectAsState(50f)
    val animationStyle by userPreferences.lyricsAnimationStyle.collectAsState(LyricsAnimationStyle.KARAOKE)
    val romanizeEnabled by userPreferences.lyricsRomanize.collectAsState(false)
    val animationsEnabled = LocalAnimationsEnabled.current
    val coroutineScope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val linesIdentity = remember(lines) { lines.firstOrNull()?.timeMs to lines.size }

    // Última línea con timeMs <= positionMs (binario O(log n) vs lineal; evita escanear 300 lineas a 10 Hz).
    val activeIndex = remember(lines, positionMs) {
        if (lines.isEmpty()) -1 else {
            val idx = lines.binarySearchBy(positionMs) { it.timeMs }
            if (idx >= 0) {
                var last = idx
                while (last + 1 < lines.size && lines[last + 1].timeMs <= positionMs) last++
                last
            } else {
                val insertion = -idx - 1
                insertion - 1
            }
        }
    }
    val latestActiveIndex by rememberUpdatedState(activeIndex)

    // Estado para saber si el usuario hizo scroll manual y se alejó del renglón activo
    var userScrolledAway by remember { mutableStateOf(false) }

    // Si el usuario hace scroll manual y la línea activa sale del viewport, activamos el botón de resincronización
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isNotEmpty() && activeIndex >= 0) {
                val isCurrentVisible = visible.any { it.index == activeIndex }
                if (!isCurrentVisible) {
                    userScrolledAway = true
                }
            }
        }
    }

    // Al cambiar de canción o set de letras: reiniciar scroll al inicio
    LaunchedEffect(linesIdentity) {
        userScrolledAway = false
        listState.scrollToItem(0, 0)
    }

    Box(modifier = modifier) {
        // Solo Offscreen+blur cuando hay suficientes lineas para necesitar fade
        val useFading = lines.size > 6
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(if (useFading) Modifier.lyricsFadingEdges(topFade = 48.dp, bottomFade = 80.dp) else Modifier)
        ) {
            val viewportPx = with(LocalDensity.current) { maxHeight.toPx() }
            val topAnchorPx = viewportPx * 0.32f

            suspend fun scrollToAnchor(target: Int, animated: Boolean = true) {
                if (target < 0 || lines.isEmpty()) return

                suspend fun deltaToAnchor(index: Int): Float? {
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                        ?: return null
                    return item.offset - topAnchorPx
                }

                var delta = deltaToAnchor(target)
                if (delta == null) {
                    listState.scrollToItem(target, -topAnchorPx.toInt())
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == target } }
                        .filter { it }
                        .first()
                    delta = deltaToAnchor(target)
                }

                val distance = delta ?: return
                if (abs(distance) < 1.5f) return

                if (!animated || !animationsEnabled) {
                    listState.scrollBy(distance)
                } else {
                    val spec = if (abs(distance) > viewportPx * 1.5f) {
                        expressiveTween<Float>(durationMillis = 340)
                    } else {
                        uiTween<Float>(animationsEnabled = true, durationMillis = expressiveScrollDuration)
                    }
                    listState.animateScrollBy(distance, animationSpec = spec)
                }
            }

            // Auto-scroll hacia la línea activa cuando no se ha alejado manualmente
            LaunchedEffect(activeIndex, linesIdentity) {
                if (activeIndex < 0 || lines.isEmpty()) return@LaunchedEffect
                if (!userScrolledAway) {
                    scrollToAnchor(activeIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 40.dp,
                    bottom = maxHeight * 0.65f + LocalMiniPlayerInset.current,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(
                    items = lines,
                    key = { i, line -> "${line.timeMs}-$i-${line.text.hashCode()}" },
                ) { i, line ->
                    val distance = if (activeIndex >= 0) abs(i - activeIndex) else 0
                    LyricLineRow(
                        line = line,
                        positionMs = positionMs,
                        isActive = i == activeIndex,
                        isPast = i < activeIndex,
                        distanceFromActive = distance,
                        startAligned = textAlign,
                        activeTextSize = textSize,
                        lineSpacing = lineSpacing,
                        animationStyle = animationStyle,
                        romanize = romanizeEnabled,
                        onClick = {
                            userScrolledAway = false
                            onSeek(line.timeMs)
                        },
                    )
                }
            }

            // Botón flotante para resincronizar si el usuario navegó lejos de la línea actual
            AnimatedVisibility(
                visible = userScrolledAway && activeIndex >= 0,
                enter = fadeIn(expressiveTween(200)) + slideInVertically(expressiveTween(250)) { it / 2 },
                exit = fadeOut(expressiveTween(150)) + slideOutVertically(expressiveTween(150)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp + LocalMiniPlayerInset.current),
            ) {
                Surface(
                    onClick = {
                        userScrolledAway = false
                        coroutineScope.launch {
                            scrollToAnchor(activeIndex)
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = stringResource(Res.string.lyrics_sync_jump),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineRow(
    line: LyricLine,
    positionMs: Long,
    isActive: Boolean,
    isPast: Boolean,
    distanceFromActive: Int,
    startAligned: Boolean,
    activeTextSize: Float,
    lineSpacing: Float,
    animationStyle: LyricsAnimationStyle,
    romanize: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val animationsEnabled = LocalAnimationsEnabled.current

    val sungColor = MaterialTheme.colorScheme.primary
    val activeColor = MaterialTheme.colorScheme.onSurface

    val useScale = animationStyle == LyricsAnimationStyle.KARAOKE
    val useAlpha = animationStyle != LyricsAnimationStyle.NONE

    val motionSpec = uiTween<Float>(animationsEnabled, durationMillis = 280)

    // Base alpha: activa = 1.0f.
    // Inactivas: atenuación suave estilo Spotify/Metrolist según distancia, y realce al pasar el cursor
    val targetAlpha = when {
        !useAlpha -> 1f
        isActive -> 1f
        isHovered -> 0.88f
        isPast -> (0.40f - (distanceFromActive * 0.02f)).coerceAtLeast(0.24f)
        else -> (0.54f - (distanceFromActive * 0.02f)).coerceAtLeast(0.32f)
    }

    val rowAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = motionSpec,
        label = "lyricAlpha",
    )

    val scale by animateFloatAsState(
        targetValue = if (!useScale) 1f else if (isActive) 1.035f else if (isHovered) 1.01f else 0.99f,
        animationSpec = motionSpec,
        label = "lyricScale",
    )

    val fontSizeSp by animateFloatAsState(
        targetValue = if (isActive) activeTextSize else (activeTextSize - 4f).coerceAtLeast(14f),
        animationSpec = motionSpec,
        label = "lyricFontSize",
    )

    val weightProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = motionSpec,
        label = "lyricWeight",
    )

    val hoverBgAlpha by animateFloatAsState(
        targetValue = if (isHovered && !isActive) 0.07f else 0f,
        animationSpec = motionSpec,
        label = "hoverBgAlpha",
    )

    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = hoverBgAlpha),
            shape = RoundedCornerShape(12.dp),
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
        .pointerHoverIcon(PointerIcon.Hand)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = rowAlpha
            transformOrigin = if (startAligned) TransformOrigin(0f, 0.5f) else TransformOrigin(0.5f, 0.5f)
        }
        .padding(horizontal = 14.dp, vertical = 7.dp)

    val rowModifier = if (isActive && animationStyle == LyricsAnimationStyle.GLOW) {
        baseModifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(12.dp),
            clip = false,
            ambientColor = sungColor.copy(alpha = 0.45f),
            spotColor = sungColor.copy(alpha = 0.45f),
        )
    } else {
        baseModifier
    }

    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = fontSizeSp.sp,
        lineHeight = (lineSpacing * (fontSizeSp / activeTextSize)).sp,
        fontWeight = if (weightProgress > 0.5f) FontWeight.Bold else FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )
    val horizontalArrangement = if (startAligned) Arrangement.Start else Arrangement.Center

    // Romanizacion cacheada por linea; si se activa para muchas lineas es CPU-bound pero
    // se hace una sola vez por linea gracias a remember (no por frame).
    val romanized = remember(line.text, romanize) {
        if (romanize && line.text.isNotBlank()) Romanizer.romanize(line.text) else null
    }

    val isInstrumental = line.text.isBlank() || line.text.trim() == "♪" || line.text.trim().lowercase() == "[instrumental]"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (startAligned) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (isInstrumental) {
            InstrumentalLineRow(
                isActive = isActive,
                modifier = rowModifier,
                startAligned = startAligned,
            )
        } else if (isActive && line.words.isNotEmpty() && animationStyle == LyricsAnimationStyle.KARAOKE) {
            FlowRow(
                modifier = rowModifier,
                horizontalArrangement = horizontalArrangement,
            ) {
                line.words.forEach { w ->
                    val color = when {
                        positionMs >= w.endMs -> sungColor
                        positionMs >= w.startMs -> {
                            val frac = ((positionMs - w.startMs).toFloat() /
                                (w.endMs - w.startMs).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                            lerp(activeColor, sungColor, frac)
                        }
                        else -> activeColor
                    }
                    Text(
                        text = w.text + " ",
                        style = style,
                        color = color,
                    )
                }
            }
        } else {
            Text(
                text = line.text,
                style = style,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface,
                modifier = rowModifier,
                textAlign = if (startAligned) TextAlign.Start else TextAlign.Center,
            )
        }

        if (romanized != null) {
            Text(
                text = romanized,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = (activeTextSize - 18f).coerceAtLeast(12f).sp,
                    lineHeight = (lineSpacing - 20f).coerceAtLeast(16f).sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = if (isActive) sungColor.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign = if (startAligned) TextAlign.Start else TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun InstrumentalLineRow(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    startAligned: Boolean = true,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val infiniteTransition = rememberInfiniteTransition(label = "instrumental_wave")

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3",
    )

    val tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (startAligned) Arrangement.Start else Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
            modifier = Modifier.padding(end = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(6.dp)
                    .size(20.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scales = listOf(dot1Scale, dot2Scale, dot3Scale)
            scales.forEach { s ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            if (isActive && animationsEnabled) {
                                scaleX = s
                                scaleY = s
                            }
                        }
                        .background(tint, CircleShape)
                )
            }
        }

        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(Res.string.lyrics_instrumental),
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
