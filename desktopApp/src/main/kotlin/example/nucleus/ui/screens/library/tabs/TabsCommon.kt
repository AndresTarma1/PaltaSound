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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
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
import example.nucleus.ui.components.glass.LocalProgressiveTopInset
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.navigation.ScreenConfig
import example.nucleus.ui.components.ItemContentSource
import example.nucleus.ui.screens.shared.SectionListItem
import example.nucleus.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.YTItem
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.utils.LocalAnimationsEnabled

internal val LibraryGridMinCellWidth = 150.dp
internal val LibraryGridSpacing = 12.dp

@Composable
internal fun libraryGridContentPadding(): PaddingValues = PaddingValues(
    start = 20.dp,
    end = 20.dp,
    // Las barras fijas (título + tabs) flotan sobre el contenido: el top inset
    // hace que la primera fila arranque debajo y que al scrollear el contenido
    // pase por detrás del difuminado.
    top = 8.dp + LocalProgressiveTopInset.current,
    bottom = maxOf(80.dp, LocalMiniPlayerInset.current),
)

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

/**
 * Estado vacío que conserva la cabecera (pestañas + búsqueda): sin ella el
 * usuario quedaba atrapado al filtrar sin resultados, sin forma de cambiar
 * de pestaña ni de limpiar la búsqueda (solo reiniciando la app).
 */
@Composable
internal fun LibraryEmptyWithHeader(
    header: @Composable () -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = libraryGridContentPadding(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "library_header") { header() }
            item(key = "library_empty") {
                LibraryEmptyState(icon = icon, title = title, subtitle = subtitle)
            }
        }
    }
}

/**
 * Filas de lista para las pestañas de biblioteca (modo LISTA). Reutiliza las
 * filas de sección con navegación, reproducir, aleatorio y eliminar.
 */
@Composable
internal fun LibraryListRows(
    items: List<YTItem>,
    onNavigate: (ScreenConfig) -> Unit,
    playerViewModel: PlayerViewModel,
    sourceFor: ((YTItem) -> ItemContentSource)? = null,
    onRemoveCollection: ((YTItem) -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = libraryGridContentPadding(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "library_header") { header() }
            items(items = items, key = { it.id }) { item ->
                SectionListItem(
                    item = item,
                    onNavigate = onNavigate,
                    playerViewModel = playerViewModel,
                    source = sourceFor?.invoke(item) ?: ItemContentSource.YOUTUBE,
                    onRemoveCollection = onRemoveCollection,
                )
            }
        }
        AppVerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}
