package example.nucleus.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.layout.AppScreenContentHorizontal
import example.nucleus.ui.components.layout.HorizontalScrollableRow
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.utils.LocalAnimationsEnabled

/**
 * CompositionLocal que provee el acceso diferido (lambda) al valor de traducción del shimmer.
 * Al usar una lambda `(() -> Float)?`, el CompositionLocal provee siempre la misma referencia
 * de función, evitando invalidar y recomponer todo el árbol cuando el float cambia.
 */
val LocalShimmerTranslation = staticCompositionLocalOf<(() -> Float)?> { null }

/**
 * Envuelve contenido skeleton con UNA sola animación shimmer compartida.
 * Coloca este composable en el nivel de la pantalla (HomeScreen, SearchScreen, etc.)
 * para que todos los skeleton hijos compartan la misma animación sin costo de recomposición.
 */
@Composable
fun ProvideShimmerTransition(content: @Composable () -> Unit) {
    val animationsEnabled = LocalAnimationsEnabled.current
    if (!animationsEnabled) {
        // Sin animaciones: proveer una traducción estática (sin animación infinita, que requiere duración > 0)
        val staticProvider = remember { { 0f } }
        CompositionLocalProvider(LocalShimmerTranslation provides staticProvider) {
            content()
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslation"
    )
    
    // Devolvemos una lambda estable para diferir la lectura del estado a la fase de dibujo (draw phase)
    val translationProvider = remember(translateAnimation) { { translateAnimation.value } }
    
    CompositionLocalProvider(LocalShimmerTranslation provides translationProvider) {
        content()
    }
}

/**
 * ✅ Optimizador de Renderizado:
 * Dibuja un fondo con efecto shimmer animado evaluando la traducción directamente en la fase
 * de dibujo (`drawBehind`), previniendo recomposiciones masivas a 60 FPS en componentes skeleton.
 */
@Composable
fun Modifier.shimmerBackground(
    shape: Shape = RectangleShape
): Modifier {
    val translationProvider = LocalShimmerTranslation.current
    
    // Si no hay ProvideShimmerTransition activo en la pantalla, creamos una animación fallback
    val finalProvider = if (translationProvider != null) {
        translationProvider
    } else {
        val animationsEnabled = LocalAnimationsEnabled.current
        if (!animationsEnabled) {
            // Sin animaciones: valor estático
            remember { { 0f } }
        } else {
            val transition = rememberInfiniteTransition(label = "shimmerFallback")
            val anim = transition.animateFloat(
                initialValue = -1000f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "shimmerFallbackTranslation"
            )
            remember(anim) { { anim.value } }
        }
    }

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    )

    return this
        .clip(shape)
        .drawBehind {
            val translate = finalProvider()
            val brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(translate, translate),
                end = Offset(translate + 500f, translate + 500f)
            )
            drawRect(brush)
        }
}

@Composable
fun ChipRowSkeleton() {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = AppScreenContentHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(3) {
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(32.dp)
                    .shimmerBackground(RoundedCornerShape(28.dp))
            )
        }
    }
}

@Composable
fun SectionSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        // Icono + titulo: la misma composicion que SectionHeaderRow, para que el esqueleto
        // y el contenido no diverjan al cargar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppScreenContentHorizontal, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(24.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
        }

        val scrollState = rememberLazyListState()
        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AppScreenContentHorizontal, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            state = scrollState,
        ) {
            items(8) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shimmerBackground(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(18.dp)
                            .shimmerBackground(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(13.dp)
                            .shimmerBackground(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun SongSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .shimmerBackground(RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(20.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(14.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .shimmerBackground(circleAwareShape())
        )
    }
}
