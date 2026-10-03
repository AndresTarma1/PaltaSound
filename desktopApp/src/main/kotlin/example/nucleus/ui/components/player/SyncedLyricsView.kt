package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.ui.themes.uiTween
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.utils.LocalUserPreferences
import kotlinx.coroutines.CoroutineScope
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
 * Retardo relativo de una fila respecto al ancla en la cascada. Las filas lejanas siguen
 * el salto mas despacio, hasta un tope: pasado un tope el retraso ya no se percibe y solo
 * costaria desincronizar el conjunto (LAG_STAGGER y LAG en Sonora).
 */
private const val LYRICS_LAG_ROWS = 24
private const val LYRICS_LAG_STAGGER = 0.35f

private fun rowLag(distanceFromActive: Int): Float =
    (distanceFromActive.coerceAtMost(LYRICS_LAG_ROWS).toFloat() / LYRICS_LAG_ROWS) *
        LYRICS_LAG_STAGGER

/**
 * Muelle del desplazamiento, con los valores de Sonora (`Springs::LYRICS_SCROLL`,
 * rigidez 170 y amortiguacion 23 sobre masa 1). El amortiguamiento relativo resultante es
 * 0,88: llega rapido y frena sin rebote.
 */
private fun lyricsScrollSpring() = spring<Float>(
    dampingRatio = 0.88f,
    stiffness = 170f,
    visibilityThreshold = 0.5f,
)

/**
 * Muelle de una fila. Es el del scroll con la frecuencia escalada por la distancia al
 * ancla, que es lo que hace `lag_spring` en Sonora (aside.rs:2691): la fila activa usa la
 * misma frecuencia y por tanto no se separa, y las lejanas llegan progressive mas tarde.
 *
 * El amortiguamiento relativo se deja igual que en el scroll a proposito. Si bajase, la fila
 * pasaria de largo y volveria, y eso se lee como un temblor; aqui lo unico que cambia es
 * cuando llega, no si se pasa.
 */
private fun lyricsRowSpring(distanceFromActive: Int): FiniteAnimationSpec<Float> {
    val frequency = 1f - rowLag(distanceFromActive)
    return spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 170f * frequency * frequency,
        visibilityThreshold = 0.5f,
    )
}

/**
 * Letras sincronizadas interactivas al estilo Sonora:
 * - Desvanecido continuo en bordes superior e inferior
 * - La línea activa se resalta con opacidad completa, peso destacado y relleno karaoke por palabra
 * - Sin caja en la activa: el realce es de tamaño y opacidad (lo pasado al 0,40, lo que
 *   viene al 0,60), no un fondo, que es lo que hace que la letra respire
 * - Columna acotada a 648dp y centrada, para que los versos no se estiren
 * - Cascada por fila al cambiar de línea: cada una llega con retardo según su distancia
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

    // Cascada al cambiar de linea. Cada fila persigue su sitio con un muelle propio y algo
    // mas lento cuanto mas lejos esta del ancla, que es lo que hace que el cambio se
    // propague en vez de mover el bloque entero de golpe (Sonora, aside.rs:2691
    // `lag_spring`). Se aplica en la capa, asi que no toca layout.
    //
    // `cascadeJumpPx` es el desplazamiento total de la lista; `scrollProgress` su avance.
    // La fila se dibuja en `progress - scrollProgress`: en el instante del cambio ambas
    // valen cero (sin salto) y, como la fila va mas lenta, se queda atras hasta que
    // converge. Es lo que evita el tirón de un salto mas una correccion con tween.
    val cascadeJumpPx = remember { mutableFloatStateOf(0f) }
    val scrollProgress = remember { Animatable(1f) }

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

            /**
             * Pixeles que faltan (o sobran) para dejar [target] en el ancla, o `null` si la
             * fila aun no esta medida. Coloca la fila sin animar para poder medirla.
             */
            suspend fun anchorDelta(target: Int): Float? {
                var item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                if (item == null) {
                    listState.scrollToItem(target, -topAnchorPx.toInt())
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == target } }
                        .filter { it }.first()
                    item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                }
                return item?.let { it.offset - topAnchorPx }
            }

            /**
             * Lleva [target] al ancla con el muelle de scroll. [cascade] ademas reparte el
             * movimiento entre las filas, que es lo que se ve al cambiar de linea de forma
             * automatica; al resincronizar a mano se llama sin cascada, porque el usuario ya
             * ha pedido un salto directo a la linea.
             *
             * Se ejecuta en el ambito de quien la llama: al cambiar de linea antes de que
             * termine el salto anterior, este se cancela en vez de acumularse.
             */
            suspend fun CoroutineScope.scrollToLine(target: Int, cascade: Boolean) {
                val jump = anchorDelta(target) ?: return
                if (abs(jump) < 1f) return

                if (!animationsEnabled) {
                    listState.scrollBy(jump)
                    scrollProgress.snapTo(1f)
                    cascadeJumpPx.value = 0f
                    return
                }

                cascadeJumpPx.value = if (cascade) jump else 0f
                scrollProgress.snapTo(0f)
                // Ambas avanzan a la vez y con el mismo muelle, asi que su resta es
                // exactamente el retardo de la fila. En paralelo y no en serie: en serie una
                // de las dos estaria avanzada cuando la otra arranca.
                launch {
                    launch { scrollProgress.animateTo(1f, lyricsScrollSpring()) }
                    listState.animateScrollBy(jump, animationSpec = lyricsScrollSpring())
                }
            }

            // Auto-scroll hacia la línea activa cuando no se ha alejado manualmente.
            LaunchedEffect(activeIndex, linesIdentity) {
                if (activeIndex < 0 || lines.isEmpty()) return@LaunchedEffect
                if (!userScrolledAway) scrollToLine(activeIndex, cascade = true)
            }

            // Columna acotada y centrada. Sin tope, en una ventana ancha los versos se
            // estiran de borde a borde y dejan de leerse como letra (REACH en Sonora).
            // El relleno inferior se calcula aqui porque maxHeight pertenece al
            // BoxWithConstraints exterior y el Box anidado lo oculta.
            val listBottomPadding = maxHeight * 0.65f + LocalMiniPlayerInset.current
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
            LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 648.dp),
                    contentPadding = PaddingValues(
                        top = 40.dp,
                        bottom = listBottomPadding,
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
                        // Progreso propio de la fila: se reinicia con cada cambio de linea y
                        // llega con un muelle mas lento segun la distancia. Solo se compone
                        // para las filas que la lista dibuja, asi que el coste es acotado.
                        val rowProgress = remember { Animatable(1f) }
                        LaunchedEffect(activeIndex, linesIdentity) {
                            if (activeIndex < 0 || !animationsEnabled) return@LaunchedEffect
                            rowProgress.snapTo(0f)
                            rowProgress.animateTo(1f, lyricsRowSpring(distance))
                        }
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
                            lagOffsetPx = {
                                // Se queda atras mientras su muelle va por detras del del
                                // scroll, y converge cuando lo alcanza.
                                val lag = cascadeJumpPx.value * rowLag(distance)
                                if (lag == 0f) 0f else lag * (rowProgress.value - scrollProgress.value)
                            },
                            onClick = {
                                userScrolledAway = false
                                onSeek(line.timeMs)
                            },
                        )
                    }
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
                            scrollToLine(activeIndex, cascade = false)
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
    /** Desfase de la cascada en px. Se lee en la capa, nunca en la composición. */
    lagOffsetPx: () -> Float = { 0f },
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

    // Base alpha: activa = 1.0f. Estilo Sonora (aside.rs:1222-1247): lo pasado al 0,40
    // y lo que viene al 0,60, sin mas degradado por distancia — la jerarquia la marca
    // el tamano, no un rango de transparencias.
    val targetAlpha = when {
        !useAlpha -> 1f
        isActive -> 1f
        isHovered -> 0.88f
        isPast -> 0.40f
        else -> 0.60f
    }

    val rowAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = motionSpec,
        label = "lyricAlpha",
    )

    // El tamano del texto NO cambia con el estado: se mide siempre con el mayor y el
    // realce se aplica en la capa. Si el tamano fuese real, al activarse una linea se
    // volveria a medir, una linea de una sola linea pasaria a ocupar dos y el alto de la
    // fila cambiaria. El LazyColumn recolocaria entonces todo lo que hay debajo y el
    // cambio se veria como un desborde, y al volver, igual. Es layout, no animacion.
    val fontSizeSp = activeTextSize
    val lineHeightSp = lineSpacing

    // Reducir en la capa no toca layout: el ancho disponible y el numero de lineas son
    // los mismos antes y despues, asi que la fila no se mueve al activarse.
    val deemphasizedScale =
        ((activeTextSize - 4f) / activeTextSize).coerceIn(0.82f, 1f)
    val emphasisScale by animateFloatAsState(
        targetValue = if (isActive) 1f else deemphasizedScale,
        animationSpec = motionSpec,
        label = "lyricEmphasis",
    )

    // El peso tampoco cambia: el bold es mas ancho y por si solo puede anadir una linea.
    // La jerarquia la llevan la opacidad y la escala.
    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeightSp.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )

    // Sin cajas ni fondos: la fila es solo texto clicable. El realce lo dan la escala y
    // la opacidad en la capa; ni siquiera el hover pinta fondo, para que la letra se
    // lea como en Sonora: versos limpios sobre el fondo, sin contenedores.
    val baseModifier = Modifier
        .fillMaxWidth()
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
        .pointerHoverIcon(PointerIcon.Hand)
        .graphicsLayer {
            // Hover y emphasize se combinan en una sola escala: sumarlos por separado
            // haria que la fila creciera dos veces al pasar por la activa con el raton.
            val hoverScale = if (useScale && isHovered) 1.008f else 1f
            val totalScale = emphasisScale * hoverScale
            scaleX = totalScale
            scaleY = totalScale
            alpha = rowAlpha
            transformOrigin = if (startAligned) TransformOrigin(0f, 0.5f) else TransformOrigin(0.5f, 0.5f)
            translationY = lagOffsetPx()
        }
        .padding(horizontal = 16.dp, vertical = 10.dp)

    // Sin sombra ni relieve tampoco en GLOW: el brillo de una sombra elevada rompe la
    // lectura plana de la referencia. La jerarquia la llevan escala y opacidad.
    val rowModifier = baseModifier

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
        // Con relleno por palabra la fila es SIEMPRE un FlowRow, no solo cuando es la
        // activa. Antes solo lo era al activarse, y al pasar de `Text` a `FlowRow` el
        // texto se re-median y envolvia de otra manera: una linea de una sola linea
        // saltaba a dos y todo lo de abajo se recolocaba. Estructura fija, y la fila no
        // cambia de alto al cambiar de estado.
        } else if (line.words.isNotEmpty() && animationStyle == LyricsAnimationStyle.KARAOKE) {
            FlowRow(
                modifier = rowModifier,
                horizontalArrangement = horizontalArrangement,
            ) {
                line.words.forEach { w ->
                    // El relleno por palabra solo tiene sentido en la linea que canta. En
                    // las demas, pintar lo ya cantado en `primary` las haria competir con la
                    // activa en vez de cederle el foco.
                    val color = when {
                        !isActive -> activeColor
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
                color = activeColor,
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
    // La pausa instrumental es un verso mas, no un widget: mismo estilo de linea, sin
    // caja, sin icono y sin puntos animados. Solo cambia el tinte con el estado.
    val tint = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Text(
        text = "♪ " + stringResource(Res.string.lyrics_instrumental),
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
        ),
        color = tint,
        textAlign = if (startAligned) TextAlign.Start else TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
