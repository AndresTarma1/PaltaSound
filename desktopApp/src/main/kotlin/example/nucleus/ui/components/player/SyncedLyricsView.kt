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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import example.nucleus.data.repository.LYRICS_FRAME_HEIGHT
import example.nucleus.data.repository.LYRICS_FRAME_MOST
import example.nucleus.data.repository.LYRICS_FRAME_WIDTH
import example.nucleus.data.repository.LYRICS_LINE_SPACING_DEFAULT
import example.nucleus.data.repository.LYRICS_TEXT_SCALE_DEFAULT
import example.nucleus.data.repository.LYRICS_TEXT_SIZE_BASE
import example.nucleus.data.repository.LyricsAnimationStyle
import example.nucleus.lyrics.LyricLine
import example.nucleus.lyrics.Romanizer
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.lyrics_instrumental
import example.nucleus.generated.resources.lyrics_provider_credit
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
 * Ancho maximo de una linea de letra, en multiples de su propio tamano. `REACH` en Sonora
 * (crates/views/src/chrome/aside.rs:70): sin tope, un verso corto se estira de borde a borde del
 * panel mientras uno largo se parte en tres, y la caja deja de leerse como columna.
 */
private const val LYRICS_VERSE_REACH_EM = 24f

/**
 * La romanizacion se escribe en el pelano de cuerpo contra el de titulo, no con una resta en sp.
 * Sonora: `Text::Body` 1.0 contra `Text::Title` 1.69, o sea 0.59 (metrics.rs:113-123).
 */
private const val LYRICS_ROMANIZED_RATIO = 0.59f

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
 *
 * 0.12 y no 0.35: el bloque tiene que leerse como uno solo (asi rueda Apple Music); la
 * cascada queda como un acento de vida al asentarse, no como una ola que recorre la lista.
 */
private const val LYRICS_LAG_ROWS = 24
private const val LYRICS_LAG_STAGGER = 0.12f

private fun rowLag(distanceFromActive: Int): Float =
    (distanceFromActive.coerceAtMost(LYRICS_LAG_ROWS).toFloat() / LYRICS_LAG_ROWS) *
        LYRICS_LAG_STAGGER

/**
 * Rampa de opacidad por distancia a la linea activa (estilo Apple Music): un solo foco a
 * plena opacidad y el resto cayendo en escalones por lejania. Dos tablas porque la
 * lectura cambia con el tema: sobre fondo claro la jerarquia la da el propio texto y las
 * alphas del tema oscuro se ven mas duras. Con el blur haciendo el trabajo de quitar
 * foco, la rampa puede ser generosa: las inactivas siguen presentes, solo que blandas.
 */
private val LYRICS_ALPHA_DARK = floatArrayOf(1f, 0.66f, 0.54f, 0.44f, 0.38f)
private val LYRICS_ALPHA_LIGHT = floatArrayOf(1f, 0.72f, 0.62f, 0.52f, 0.46f)

/** Cuanto cae de mas una linea ya cantada frente a una por venir a la misma distancia. */
private const val LYRICS_PAST_DIP = 0.08f

/**
 * Opacidad de reposo de una fila inactiva. Lo cantado cae un peldano extra: el foco mira
 * hacia adelante, y asi la linea a d=1 por venir queda por encima de la simetrica.
 */
private fun distanceAlpha(distance: Int, isPast: Boolean, dark: Boolean): Float {
    val steps = if (dark) LYRICS_ALPHA_DARK else LYRICS_ALPHA_LIGHT
    val index = distance.coerceAtMost(steps.lastIndex)
    val base = steps[index]
    return if (isPast && index > 0) (base - LYRICS_PAST_DIP).coerceAtLeast(steps.last()) else base
}

/** Escala de reposo de las lineas no activas: timida, la jerarquia la lleva la opacidad. */
private const val LYRICS_INACTIVE_SCALE = 0.95f

/**
 * Blur de las lineas inactivas: la activa va nitida y el resto desenfocado; el hover
 * enfoca la fila bajo el puntero para leerla sin saltar a ella. Sobre la tipografia
 * base (~27sp) 3px desenfoca de sobra sin volverla ilegible; es el unico mando.
 */
private const val LYRICS_INACTIVE_BLUR_PX = 3f

/** Borde difuso del barrido karaoke, en em del tamano de letra, con topes en px. */
private const val LYRICS_SWEEP_EDGE_EM = 0.35f
private const val LYRICS_SWEEP_EDGE_MIN_PX = 4f
private const val LYRICS_SWEEP_EDGE_MAX_PX = 16f

/** Hueco minimo (ms) para que una pausa instrumental muestre los puntos de compas. */
private const val LYRICS_INTERLUDE_GAP_MS = 8_000L

/** Tope del barrido de linea sin timings por palabra: rellenar un hueco de 30s se lee raro. */
private const val LYRICS_LINE_SWEEP_MAX_MS = 10_000L

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
 * Letras sincronizadas interactivas al estilo Apple Music:
 * - Un solo foco: la línea activa a plena opacidad y el resto en rampa por distancia
 * - Desvanecido continuo en bordes superior e inferior
 * - Relleno karaoke con barrido y borde degradado (por palabra, o por línea sin timings)
 * - Sin caja en la activa: el realce es de tamaño y opacidad, no un fondo, que es lo
 *   que hace que la letra respire
 * - Columna acotada a 24em y centrada, para que los versos no se estiren
 * - Cascada mínima por fila al cambiar de línea: acento de vida, no coreografía
 * - Las líneas inactivas van difuminadas; el hover enfoca la que está bajo el puntero
 * - Halo de la activa (preset GLOW)
 * - Detección de navegación manual con botón flotante para resincronizar
 * - Soporte para pausas instrumentales con puntos de compás animados
 * - Crédito del proveedor de las letras al final de la lista
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
    provider: String? = null, // fuente de las letras, acreditada al final de la lista
) {
    val userPreferences = LocalUserPreferences.current
    // Multiplicadores, no sp absolutos. Los valores por defecto son los mismos que devuelve el
    // repositorio: el fallback solo se ve un frame, pero si se desincroniza con el default real
    // la letra aparece a un tamaño que el usuario nunca eligio.
    val textScale by userPreferences.lyricsTextSize.collectAsState(LYRICS_TEXT_SCALE_DEFAULT)
    val lineSpacingRatio by userPreferences.lyricsLineSpacing.collectAsState(LYRICS_LINE_SPACING_DEFAULT)
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
             * Cuánto crece la letra con el tamaño de la ventana. Replica
             * `lyrics_frame_scale` de Sonora (crates/views/src/chrome/aside.rs:2410):
             *
             * ```rust
             * (viewport.width / 1152.0).min(viewport.height / 648.0).clamp(1.0, 2.0)
             * ```
             *
             * El `clamp` es lo importante y no es un detalle: sin él, una ventana pequeña
             * encogería la letra y una enorme la haría absurda. El texto mantiene su tamaño hasta
             * 1152x648 y a partir de ahí crece con la ventana, con techo en 2x. El mas pequeno de
             * los dos ejes manda, para que agrandar en vertical no desborde el ancho de la linea.
             */
            val frameScale = with(LocalDensity.current) {
                val w = maxWidth.toPx()
                val h = maxHeight.toPx()
                if (w <= 0f || h <= 0f) {
                    1f
                } else {
                    minOf(w / LYRICS_FRAME_WIDTH, h / LYRICS_FRAME_HEIGHT)
                        .coerceIn(1f, LYRICS_FRAME_MOST)
                }
            }

            // El tamaño sale de los tres factores, y solo este ultimo es cosa del usuario.
            val verseSp = LYRICS_TEXT_SIZE_BASE * frameScale * textScale
            val lineSpacingSp = verseSp * lineSpacingRatio

            // `REACH` de Sonora: una linea de letra no ocupa mas de 24 em. Sin tope, un verso
            // corto se estira de borde a borde del panel mientras otro largo se parte en tres,
            // y la caja deja de leerse como una columna (aside.rs:70-72).
            val reach = verseSp * LYRICS_VERSE_REACH_EM

            // Ancho maximo real del texto de una fila en px: la columna (acotada por reach)
            // menos los rellenos (16+16 de la lista, 16+16 de la fila). Lo usa la medicion
            // del verso para decidir si el barrido de linea aplica: un bloque envuelto a
            // dos lineas fisicas no barre, porque el degradado horizontal cubriria el alto
            // completo y ambas se rellenarian a la vez.
            val maxTextWidthPx = with(LocalDensity.current) {
                (minOf(maxWidth, reach.dp) - 64.dp).toPx().toInt().coerceAtLeast(1)
            }

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

            // Columna acotada y centrada, con el tope en `reach` en vez de un dp fijo. Los 648.dp
            // que habia aqui eran el `REACH` de Sonora congelado en el tamano base (27 x 24 = 648):
            // al hacer la letra dependente de la ventana, un tope fijo se queda corto en las grandes
            // y wasteful en las pequenas.
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
                        .widthIn(max = reach.dp),
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
                            activeTextSize = verseSp,
                            lineSpacing = lineSpacingSp,
                            animationStyle = animationStyle,
                            romanize = romanizeEnabled,
                            windowEndMs = lines.getOrNull(i + 1)?.timeMs,
                            maxTextWidthPx = maxTextWidthPx,
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

                    // Credito del proveedor: al final del texto, en tipografia de apoyo. Es
                    // informativo (de donde salio la letra), no interactivo.
                    provider?.takeIf { it.isNotBlank() }?.let { source ->
                        item(key = "lyrics_provider_credit") {
                            Text(
                                text = stringResource(Res.string.lyrics_provider_credit, source),
                                modifier = Modifier.padding(top = 20.dp, start = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
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
                    // Glass de chrome (estilo Apple): translucido sobre el fondo ya
                    // difuminado, con borde neutro en vez de acento. El acento queda para
                    // el icono y el texto, que es lo que el usuario lee.
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
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
    /** Inicio de la siguiente linea: tope de la ventana de barrido y de la pausa instrumental. */
    windowEndMs: Long?,
    /** Ancho maximo real del texto en px (columna menos rellenos), para medir el verso. */
    maxTextWidthPx: Int,
    /** Desfase de la cascada en px. Se lee en la capa, nunca en la composición. */
    lagOffsetPx: () -> Float = { 0f },
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val animationsEnabled = LocalAnimationsEnabled.current

    val sungColor = MaterialTheme.colorScheme.primary
    val activeColor = MaterialTheme.colorScheme.onSurface

    val useScale = animationStyle.usesScale
    val useAlpha = animationStyle != LyricsAnimationStyle.NONE
    val useSweep = animationStyle.usesSweep
    val useBlur = animationStyle.usesBlur

    val motionSpec = uiTween<Float>(animationsEnabled, durationMillis = 280)

    // La rampa por distancia es la jerarquia (estilo Apple Music): un solo foco a plena
    // opacidad y el resto cayendo por lejania. Lo cantado cae un peldano extra — el foco
    // mira hacia adelante — asi que la linea a d=1 por venir es la mas viva de las
    // inactivas. Hover solo puede subir, nunca competir con la activa.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val distanceStep = distanceAlpha(distanceFromActive, isPast, darkTheme)
    val targetAlpha = when {
        !useAlpha -> 1f
        isActive -> 1f
        isHovered -> maxOf(distanceStep, 0.90f)
        else -> distanceStep
    }

    val rowAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = motionSpec,
        label = "lyricAlpha",
    )

    // EL ALPHA VA EN EL COLOR, NO EN graphicsLayer
    // ---------------------------------------------
    // Con `alpha` dentro de un `graphicsLayer`, Compose promociona el nodo a una CAPA OFFSCREEN
    // para poder aislar la opacidad. Ahi dentro el texto se rasteriza a una textura y la textura
    // es la que se escala, no las letras: se re-muestrea al 85% y sale blando.
    //
    // Por eso se veia nitida justo la linea activa y borrosas todas las demas: la activa esta a
    // alpha 1.0 y escala 1.0, no cumple ninguna de las dos condiciones y se dibuja directamente;
    // las demas cumplian las dos.
    //
    // Poner la opacidad en el color no necesita capa: el texto se dibuja con el color ya
    // translucido, la escala es una transformacion de canvas normal y las letras se vuelven a
    // componer al tamano nuevo. Visualmente es el mismo desvanecido, pero sin el re-muestreo.
    val dimActive = activeColor.copy(alpha = activeColor.alpha * rowAlpha)
    val dimSung = sungColor.copy(alpha = sungColor.alpha * rowAlpha)

    // El tamano del texto NO cambia con el estado: se mide siempre con el mayor y el
    // realce se aplica en la capa. Si el tamano fuese real, al activarse una linea se
    // volveria a medir, una linea de una sola linea pasaria a ocupar dos y el alto de la
    // fila cambiaria. El LazyColumn recolocaria entonces todo lo que hay debajo y el
    // cambio se veria como un desborde, y al volver, igual. Es layout, no animacion.
    val fontSizeSp = activeTextSize
    val lineHeightSp = lineSpacing

    // Reducir en la capa no toca layout: el ancho disponible y el numero de lineas son
    // los mismos antes y despues, asi que la fila no se mueve al activarse. El 0.95 es
    // timido a proposito (Apple): la jerarquia la lleva la opacidad, no el tamano.
    val emphasisScale by animateFloatAsState(
        targetValue = if (isActive || !useScale) 1f else LYRICS_INACTIVE_SCALE,
        animationSpec = motionSpec,
        label = "lyricEmphasis",
    )

    // Halo de la activa. GLOW lo tine con el primario (el preset completo); el resto de
    // estilos usan una sombra negra muy baja para despegar la linea del fondo difuminado
    // sin colorearla. Sin offset: es un halo, no una sombra proyectada.
    val activeShadow = when {
        !isActive -> null
        animationStyle.usesGlow -> Shadow(
            color = sungColor.copy(alpha = 0.30f),
            blurRadius = 20f,
        )
        else -> Shadow(
            color = Color.Black.copy(alpha = if (darkTheme) 0.35f else 0.18f),
            blurRadius = 14f,
        )
    }

    // El peso tampoco cambia: el bold es mas ancho y por si solo puede anadir una linea.
    // La jerarquia la llevan la opacidad y la escala.
    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeightSp.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        shadow = activeShadow,
    )

    // Blur de las inactivas: la activa va nitida y el resto desenfocado; el hover enfoca
    // la fila bajo el puntero para leerla sin saltar a ella, y al salir vuelve. Solo en
    // los estilos animados: NONE/FADE son listas planas y difuminarlas cambiaria lo que
    // prometen. Se lee en la capa (renderEffect), nunca por recomposición.
    val blurTarget = if (useBlur && !isActive && !isHovered) LYRICS_INACTIVE_BLUR_PX else 0f
    val blurRadius by animateFloatAsState(
        targetValue = blurTarget,
        animationSpec = motionSpec,
        label = "lyricBlur",
    )

    // Sin cajas ni fondos: la fila es solo texto clicable. El realce lo dan la escala y
    // la opacidad en la capa; ni siquiera el hover pinta fondo, para que la letra se
    // lea limpia sobre el fondo, sin contenedores.
    val baseModifier = Modifier
        .fillMaxWidth()
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
        .pointerHoverIcon(PointerIcon.Hand)
        .graphicsLayer {
            // SIN `alpha` aqui a proposito: ver la nota de [dimActive] mas arriba. Con alpha en la
            // capa, el texto se rasteriza a textura y se ve borroso al escalarlo.
            // Hover y emphasize se combinan en una sola escala: sumarlos por separado
            // haria que la fila creciera dos veces al pasar por la activa con el raton.
            val hoverScale = if (useScale && isHovered) 1.008f else 1f
            val totalScale = emphasisScale * hoverScale
            scaleX = totalScale
            scaleY = totalScale
            transformOrigin = if (startAligned) TransformOrigin(0f, 0.5f) else TransformOrigin(0.5f, 0.5f)
            translationY = lagOffsetPx()
            // El blur entra en la capa, no en el color: es el mismo nodo el que se
            // desenfoca. Por debajo de 0.05px no hay nada que ver y se quita el efecto
            // (sin el, la fila se dibuja directa y no paga el pase offscreen).
            renderEffect = if (blurRadius > 0.05f) BlurEffect(blurRadius, blurRadius) else null
        }
        .padding(horizontal = 16.dp, vertical = 10.dp)

    // La fila no lleva caja ni relieve propio: el peso visual lo llevan escala y
    // opacidad, y el halo vive en el estilo del texto (activo), no en un contenedor.
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
                rowAlpha = rowAlpha,
                // Puntos de compas solo cuando la pausa es larga: en un hueco de dos
                // segundos serian ruido, en una intro de quince son el unico pulso vivo.
                pulseDots = isActive &&
                    (windowEndMs ?: line.timeMs) - line.timeMs >= LYRICS_INTERLUDE_GAP_MS,
            )
        // Con relleno por palabra la fila es SIEMPRE un FlowRow, no solo cuando es la
        // activa. Antes solo lo era al activarse, y al pasar de `Text` a `FlowRow` el
        // texto se re-median y envolvia de otra manera: una linea de una sola linea
        // saltaba a dos y todo lo de abajo se recolocaba. Estructura fija, y la fila no
        // cambia de alto al cambiar de estado.
        } else if (line.words.isNotEmpty() && useSweep) {
            // Ancho real de cada palabra, medido una vez al activarse la linea: el borde
            // del barrido se calcula en px y sin la medida habria que adivinar que
            // fraccion de una palabra corta y de una larga es el mismo pixelaje. Solo se
            // mide la activa; no es por frame.
            val textMeasurer = rememberTextMeasurer()
            val wordWidths = remember(line.words, style, isActive) {
                if (isActive) {
                    line.words.map { w ->
                        textMeasurer.measure(AnnotatedString(w.text + " "), style).size.width.toFloat()
                    }
                } else {
                    emptyList()
                }
            }
            val sweepEdgePx = with(LocalDensity.current) {
                (activeTextSize.sp.toPx() * LYRICS_SWEEP_EDGE_EM)
                    .coerceIn(LYRICS_SWEEP_EDGE_MIN_PX, LYRICS_SWEEP_EDGE_MAX_PX)
            }

            FlowRow(
                modifier = rowModifier,
                horizontalArrangement = horizontalArrangement,
            ) {
                line.words.forEachIndexed { wi, w ->
                    // El relleno solo tiene sentido en la linea que canta: en las demas,
                    // pintar lo ya cantado en `primary` las haria competir con la activa
                    // en vez de cederle el foco. dimActive/dimSung llevan ya la opacidad
                    // de la fila, para no volver a aplicarla en la capa.
                    when {
                        !isActive -> Text(
                            text = w.text + " ",
                            style = style,
                            color = dimActive,
                        )
                        positionMs >= w.endMs -> Text(
                            text = w.text + " ",
                            style = style,
                            color = dimSung,
                        )
                        positionMs >= w.startMs -> {
                            // Barrido estilo Apple Music: la palabra se rellena de
                            // izquierda a derecha con un borde de degradado suave, no
                            // salta de color. El margen sale del ancho medido (con topes)
                            // para que una palabra corta no complete de golpe.
                            val width = wordWidths.getOrNull(wi) ?: 0f
                            val edge = if (width > 0f) {
                                (sweepEdgePx / width).coerceIn(0.02f, 0.42f)
                            } else {
                                0.12f
                            }
                            val frac = ((positionMs - w.startMs).toFloat() /
                                (w.endMs - w.startMs).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                            val sweep = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to dimSung,
                                    (frac - edge).coerceIn(0f, 1f) to dimSung,
                                    (frac + edge).coerceIn(0f, 1f) to dimActive,
                                    1f to dimActive,
                                ),
                            )
                            Text(text = w.text + " ", style = style.copy(brush = sweep))
                        }
                        else -> Text(
                            text = w.text + " ",
                            style = style,
                            color = dimActive,
                        )
                    }
                }
            }
        } else {
            // Sin timings por palabra (LRC plano) el barrido baja a nivel de linea: de
            // `timeMs` a la siguiente, con tope para que un hueco instrumental de treinta
            // segundos no deje el verso rellenandose media cancion. Solo aplica si el
            // verso ocupa una sola linea fisica: en un bloque envuelto el gradiente
            // horizontal cubriria el alto completo y las dos lineas se rellenarian a la
            // vez, que se lee raro. La medicion solo corre con la linea activa y su
            // estilo (que ya cambia al activarse por el halo), asi que no es por frame.
            val textMeasurer = rememberTextMeasurer()
            val sweepWindowEnd = if (isActive && useSweep && line.text.isNotBlank()) {
                windowEndMs
                    ?.takeIf { it > line.timeMs }
                    ?.coerceAtMost(line.timeMs + LYRICS_LINE_SWEEP_MAX_MS)
            } else {
                null
            }
            val singleLine = remember(line.text, style, maxTextWidthPx) {
                if (sweepWindowEnd != null) {
                    textMeasurer.measure(
                        text = AnnotatedString(line.text),
                        style = style,
                        constraints = Constraints(maxWidth = maxTextWidthPx),
                    ).lineCount <= 1
                } else {
                    false
                }
            }
            val lineSweep = if (sweepWindowEnd != null && singleLine) {
                val frac = ((positionMs - line.timeMs).toFloat() /
                    (sweepWindowEnd - line.timeMs).toFloat()).coerceIn(0f, 1f)
                // Borde ancho en fraccion del verso entero: en una linea completa (~600px)
                // son ~24px, la misma suavidad que el borde por palabra.
                val edge = 0.04f
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to dimSung,
                        (frac - edge).coerceIn(0f, 1f) to dimSung,
                        (frac + edge).coerceIn(0f, 1f) to dimActive,
                        1f to dimActive,
                    ),
                )
            } else {
                null
            }
            Text(
                text = line.text,
                style = if (lineSweep != null) style.copy(brush = lineSweep) else style,
                color = if (lineSweep != null) Color.Unspecified else dimActive,
                modifier = rowModifier,
                textAlign = if (startAligned) TextAlign.Start else TextAlign.Center,
            )
        }

        if (romanized != null) {
            // Proporcion de la linea principal, no una resta en sp. El `-18f` de antes era un
            // numero magico que solo valia para el tamano por defecto: con la letra dependiendo
            // de la ventana, 27-18 daba 9sp (invisible) en vez de una cifra legible.
            // Sonora usa el pelano de cuerpo contra el de titulo: Body 1.0 / Title 1.69 ≈ 0.59.
            val romanSize = (activeTextSize * LYRICS_ROMANIZED_RATIO).coerceAtLeast(10f)
            Text(
                text = romanized,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = romanSize.sp,
                    lineHeight = (romanSize * lineSpacing / activeTextSize).sp,
                    fontWeight = FontWeight.Normal,
                ),
                // El 0.82/0.45 es el desvanecido propio de la romanizacion, compuesto con el de la fila.
                color = if (isActive) dimSung.copy(alpha = dimSung.alpha * 0.82f)
                else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = MaterialTheme.colorScheme.onSurface.alpha * 0.45f * rowAlpha
                ),
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
    rowAlpha: Float = 1f,
    pulseDots: Boolean = false,
) {
    // La pausa instrumental es un verso mas, no un widget: mismo estilo de linea, sin
    // caja ni icono. Solo cambia el tinte con el estado y, cuando la pausa es larga, los
    // tres puntos de compas (estilo Apple Music).
    // El alpha de la fila entra en el color por el mismo motivo que en el resto: con
    // `graphicsLayer { alpha }` el texto se rasteriza a textura y sale blando.
    val tint = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = MaterialTheme.colorScheme.primary.alpha * rowAlpha)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(
            alpha = MaterialTheme.colorScheme.onSurface.alpha * 0.38f * rowAlpha
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (startAligned) Arrangement.Start else Arrangement.Center,
    ) {
        Text(
            text = "♪ " + stringResource(Res.string.lyrics_instrumental),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
            ),
            color = tint,
            textAlign = if (startAligned) TextAlign.Start else TextAlign.Center,
        )
        if (pulseDots) {
            Spacer(Modifier.width(12.dp))
            InterludeDots(color = tint)
        }
    }
}

/**
 * Tres puntos que respiran al compas durante una pausa instrumental larga: ciclo de
 * ~1.1s con desfase de 150ms por punto, asi la ola recorre de izquierda a derecha.
 * Con animaciones desactivadas quedan fijos a media intensidad.
 */
@Composable
private fun InterludeDots(color: Color) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val transition = rememberInfiniteTransition(label = "lyrics_interlude_dots")

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val pulse = if (animationsEnabled) {
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(550, delayMillis = i * 150, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "interlude_dot_$i",
                ).value
            } else {
                0.5f
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        alpha = lerp(0.30f, 0.90f, pulse)
                        scaleX = lerp(0.80f, 1.10f, pulse)
                        scaleY = lerp(0.80f, 1.10f, pulse)
                    }
                    .background(color, CircleShape),
            )
        }
    }
}
