@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.library.tabs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.ExpressiveEmptyState
import example.nucleus.ui.components.SectionHeaderRow
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.utils.LocalAnimationsEnabled

/**
 * Cabecera de las secciones de la biblioteca. Ambas delegan en [SectionHeaderRow], el
 * componente compartido con el resto de pantallas de exploración; aquí solo aportan el
 * icono de origen y, en el caso de YouTube Music, el spinner de carga.
 */
@Composable
internal fun YtmSectionHeader(title: String, isLoading: Boolean = false) {
    SectionHeaderRow(
        title = title,
        icon = Icons.Default.CloudDone,
        modifier = Modifier.padding(vertical = 6.dp),
        trailing = {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
            }
        },
    )
}

@Composable
internal fun LocalSectionHeader(title: String) {
    SectionHeaderRow(
        title = title,
        icon = Icons.Default.PhoneAndroid,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
internal fun LibraryGridSkeleton(count: Int = 4, isCircle: Boolean = false) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val animAlpha = if (animationsEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmerAlpha"
        )
    } else {
        null
    }
    val alpha = animAlpha?.value ?: 0.42f
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f)
    val shape = if (isCircle) circleAwareShape() else AppShapes.large

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(count) {
            Column(horizontalAlignment = if (isCircle) Alignment.CenterHorizontally else Alignment.Start) {
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth()
                        .background(color, shape)
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(13.dp).background(color, MaterialTheme.shapes.small))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.45f).height(10.dp).background(color, MaterialTheme.shapes.extraSmall))
            }
        }
    }
}

@Composable
internal fun LibraryEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    ExpressiveEmptyState(
        icon = icon,
        title = title,
        subtitle = subtitle,
    )
}
