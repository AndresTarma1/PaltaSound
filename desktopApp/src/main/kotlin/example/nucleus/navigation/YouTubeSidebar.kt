package example.nucleus.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.ui.themes.LocalChromeSurface
import kotlinx.coroutines.launch

/**
 * Standard Material 3 wide rail for YouTube mode. The header button toggles
 * the expanded/collapsed state; the grid adapts independently so the number
 * of columns does not depend on the rail state.
 */
@Composable
fun YouTubeSidebar(
    activeConfig: ScreenConfig,
    rootComponent: RootComponent,
) {
    val state = rememberWideNavigationRailState(
        initialValue = WideNavigationRailValue.Expanded,
    )
    val scope = rememberCoroutineScope()
    val isExpanded = state.targetValue == WideNavigationRailValue.Expanded

    Box(modifier = Modifier.fillMaxHeight()) {
        WideNavigationRail(
            state = state,
            colors = WideNavigationRailDefaults.colors(
                containerColor = LocalChromeSurface.current,
            ),
            header = {
                Row(
                    modifier = Modifier.padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (isExpanded) state.collapse() else state.expand()
                            }
                        },
                    ) {
                        Icon(
                            if (isExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                            contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                        )
                    }
                    if (isExpanded) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF0033)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "YouTube",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
            },
        ) {
            WideNavigationRailItem(
                railExpanded = isExpanded,
                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                label = { Text("Inicio") },
                selected = activeConfig is ScreenConfig.YouTubeHome ||
                    activeConfig is ScreenConfig.YouTubeWatch,
                onClick = { rootComponent.switchTab(ScreenConfig.YouTubeHome) },
            )
            WideNavigationRailItem(
                railExpanded = isExpanded,
                icon = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                label = { Text("Shorts") },
                selected = activeConfig is ScreenConfig.YouTubeShorts,
                onClick = { rootComponent.switchTab(ScreenConfig.YouTubeShorts) },
            )
            Spacer(Modifier.height(16.dp))
            WideNavigationRailItem(
                railExpanded = isExpanded,
                icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                label = { Text("Cuenta") },
                selected = activeConfig is ScreenConfig.Account,
                onClick = { rootComponent.switchTab(ScreenConfig.Account) },
            )
            WideNavigationRailItem(
                railExpanded = isExpanded,
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                label = { Text("Ajustes") },
                selected = activeConfig is ScreenConfig.Settings,
                onClick = { rootComponent.switchTab(ScreenConfig.Settings) },
            )
        }
    }
}
