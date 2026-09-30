package example.nucleus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.ui.helpers.contextMenuArea

@Composable
fun BoxForContainerContextMenuItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onHoverChange: ((Boolean) -> Unit)? = null,
    onMenuAction: () -> Unit,
    content: @Composable BoxScope.(menuButtonModifier: Modifier, openMenuFromButton: () -> Unit) -> Unit
) {
    Box(
        modifier = modifier
            .contextMenuArea(
                enabled = enabled,
                onHoverChange = { hovered -> onHoverChange?.invoke(hovered) },
                onMenuAction = onMenuAction
            )
    ) {
        val menuButtonModifier = Modifier
        val openMenuFromButton = {
            onMenuAction()
        }

        content(menuButtonModifier, openMenuFromButton)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HoverCornerActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    visible: Boolean = true,
    size: Dp = 28.dp,
    iconSize: Dp = 16.dp,
    onButtonHoverChange: (Boolean) -> Unit = {}
) {
    var isButtonHovered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.graphicsLayer { alpha = if (visible) 1f else 0f },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (isButtonHovered) Color.White.copy(alpha = 0.25f)
                    else Color.Black.copy(alpha = 0.40f)
                )
        )
        IconButton(
            onClick = onClick,
            modifier = buttonModifier
                .size(size)
                .onPointerEvent(PointerEventType.Enter) {
                    isButtonHovered = true
                    onButtonHoverChange(true)
                }
                .onPointerEvent(PointerEventType.Exit) {
                    isButtonHovered = false
                    onButtonHoverChange(false)
                }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize),

            )
        }
    }
}

