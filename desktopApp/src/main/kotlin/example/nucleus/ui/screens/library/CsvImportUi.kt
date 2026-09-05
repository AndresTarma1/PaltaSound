package example.nucleus.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.viewmodels.CsvImportState
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Tarjeta de progreso flotante y no bloqueante anclada en la esquina inferior derecha de la app.
 * Muestra el progreso de la importación CSV (buscando / completado / error) para que el usuario
 * pueda seguir navegando mientras la lista se resuelve, en lugar de quedar atrapado detrás de
 * un diálogo modal.
 *
 * Colocar dentro de un [Box] que llene la app; se posiciona automáticamente.
 */
@Composable
fun BoxScope.CsvImportProgressOverlay(
    state: CsvImportState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = state is CsvImportState.Searching ||
        state is CsvImportState.Done ||
        state is CsvImportState.Error

    // Mantener el último estado significativo para que la animación de salida aún tenga contenido
    // que renderizar después de que el ViewModel vuelva a Idle.
    var shown by remember { mutableStateOf<CsvImportState?>(null) }
    LaunchedEffect(state) { if (visible) shown = state }

    // Cerrar automáticamente la tarjeta de éxito después de un momento.
    LaunchedEffect(state) {
        if (state is CsvImportState.Done) {
            kotlinx.coroutines.delay(5000)
            onDismiss()
        }
    }

    val animationsEnabled = LocalAnimationsEnabled.current
    AnimatedVisibility(
        visible = visible,
        enter = if (animationsEnabled) slideInVertically(initialOffsetY = { it / 2 }) + fadeIn() else EnterTransition.None,
        exit = if (animationsEnabled) slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut() else ExitTransition.None,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(20.dp),
    ) {
        Surface(
            modifier = Modifier.width(320.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (val s = shown) {
                    is CsvImportState.Searching -> SearchingContent(s, onCancel)
                    is CsvImportState.Done -> DoneContent(s, onDismiss)
                    is CsvImportState.Error -> ErrorContent(s.message, onDismiss)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun SearchingContent(state: CsvImportState.Searching, onCancel: () -> Unit) {
    val progress = (state.found + 1).toFloat() / state.total.coerceAtLeast(1).toFloat()
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(Res.string.csv_importing),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.cancel),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        state.currentTitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(Res.string.csv_import_found_count, state.found, state.total),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun DoneContent(state: CsvImportState.Done, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.csv_import_complete),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(Res.string.csv_import_result, state.foundCount, state.totalCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.close_label),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(Res.string.csv_import_error, message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.close_label),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Pasos mostrados en el tutorial de "cómo importar", renderizados como filas numeradas. */
@Composable
private fun TutorialStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            modifier = Modifier.size(26.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** El cuerpo del tutorial rediseñado de importación CSV (se usa dentro de un AlertDialog). */
@Composable
fun CsvImportTutorialBody() {
    Column {
        Text(
            stringResource(Res.string.csv_tutorial_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        val steps = listOf(
            stringResource(Res.string.csv_tutorial_step1),
            stringResource(Res.string.csv_tutorial_step2),
            stringResource(Res.string.csv_tutorial_step3),
            stringResource(Res.string.csv_tutorial_step4),
            stringResource(Res.string.csv_tutorial_step5),
        )
        steps.forEachIndexed { i, step ->
            TutorialStep(i + 1, step)
            if (i < steps.lastIndex) Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        ) {
            Text(
                stringResource(Res.string.csv_tutorial_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** Icono de encabezado para el diálogo del tutorial. */
@Composable
fun CsvImportTutorialIcon() {
    Icon(
        Icons.Rounded.LibraryMusic,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}
