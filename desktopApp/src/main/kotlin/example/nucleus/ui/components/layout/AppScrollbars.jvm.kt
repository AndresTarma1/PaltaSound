package example.nucleus.ui.components.layout

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.ui.themes.LocalMiniPlayerInset

/** Ancho del track del scrollbar (incluye holgura de hit-target). */
val AppScrollbarTrackWidth = 12.dp

/**
 * Espacio reservado al final del contenido scrolleable para que filas/secciones
 * no queden debajo del [AppVerticalScrollbar] anclado al borde derecho.
 */
val AppScrollbarGutter = 14.dp

/** Padding horizontal de contenido de pantallas de exploración (Home, Artist, Search…). */
val AppScreenContentHorizontal = 20.dp

/**
 * Padding de lista con gutter de scrollbar al final.
 * Usar en LazyColumn/LazyGrid que dibujan [AppVerticalScrollbar] superpuesto a la derecha.
 */
fun appScrollContentPadding(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
    start: Dp = 0.dp,
    end: Dp = AppScrollbarGutter,
): PaddingValues = PaddingValues(start = start, top = top, end = end, bottom = bottom)

@Composable
fun appScrollbarStyle() = LocalScrollbarStyle.current.copy(
    thickness = 5.dp,
    unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
    hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
    shape = RoundedCornerShape(3.dp)
)

@Composable
private fun AppVerticalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier,
) {
    VerticalScrollbar(
        adapter = adapter,
        modifier = modifier
            .width(AppScrollbarTrackWidth)
            .padding(vertical = 2.dp)
            .padding(start = 4.dp)
            .padding(bottom = LocalMiniPlayerInset.current),
        style = appScrollbarStyle()
    )
}

@Composable
fun AppVerticalScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
) {
    AppVerticalScrollbar(rememberScrollbarAdapter(state), modifier)
}

@Composable
fun AppVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    AppVerticalScrollbar(rememberScrollbarAdapter(state), modifier)
}

@Composable
fun AppVerticalScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    AppVerticalScrollbar(rememberScrollbarAdapter(state), modifier)
}
