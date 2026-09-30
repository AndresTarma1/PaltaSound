package example.nucleus.ui.components.layout

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun HorizontalScrollableRow(
    modifier: Modifier = Modifier,
    state: LazyListState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: LazyListScope.() -> Unit
) {
    val scrollbarStyle = appScrollbarStyle()

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = state,
                contentPadding = contentPadding,
                horizontalArrangement = horizontalArrangement,
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth() // Ocupa todo el ancho para simular la integración nativa
                .height(12.dp)
                .padding(vertical = 2.dp, horizontal = 28.dp),
            style = scrollbarStyle
        )
    }
}

@Composable
fun HorizontalScrollableGrid(
    rows: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    content: LazyGridScope.() -> Unit
) {
    val scrollbarStyle = appScrollbarStyle()

    Box(modifier = modifier) { // Eliminamos fillMaxSize() para respetar los modificadores pasados (ej. height de 260.dp)
        Column(modifier = Modifier.fillMaxSize()) { // Cambiamos fillMaxWidth a fillMaxSize para que el weight() funcione correctamente
            LazyHorizontalGrid(
                rows = rows,
                state = state,
                contentPadding = contentPadding,
                horizontalArrangement = horizontalArrangement,
                verticalArrangement = verticalArrangement,
                modifier = Modifier.fillMaxWidth().weight(1f), // weight para dejar el espacio exacto libre a la barra
                content = content
            )

            // Espacio garantizado al fondo para la barra de scroll
            Spacer(modifier = Modifier.height(16.dp))
        }

        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(10.dp) // ALTURA NECESARIA para que el scroll sea visible (había sido eliminada por error)
                .padding(horizontal = 20.dp),
            style = scrollbarStyle
        )
    }
}
