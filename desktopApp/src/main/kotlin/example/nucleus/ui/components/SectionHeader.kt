package example.nucleus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.layout.AppScreenContentHorizontal

/**
 * Cabecera de sección para las pantallas de exploración (Home, Artist, Search, Browse).
 *
 * Sustituye a los cuatro títulos sueltos que había: cada pantalla usaba un rol de
 * tipografía distinto para lo mismo. El título va en [MaterialTheme.typography.titleLargeEmphasized]
 * con un icono opcional en `primary` y una acción a la derecha.
 *
 * @param title Texto de la sección. Se recorta a una línea.
 * @param icon Icono opcional a la izquierda, tintado con `primary`.
 * @param actionLabel Etiqueta de la acción opcional de la derecha.
 * @param onAction Callback de la acción opcional.
 */
@Composable
fun SectionHeaderRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppScreenContentHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .pointerHoverIcon(PointerIcon.Hand),
            )
        }
    }
}
