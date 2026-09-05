package example.nucleus.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import example.nucleus.data.repository.LayoutMode
import example.nucleus.ui.themes.LocalChromeSurface
import example.nucleus.ui.themes.LocalDimens
import example.nucleus.ui.themes.LocalLayoutMode

@Composable
fun NavigationRailDefault(
    activeConfig: ScreenConfig,
    changeQueueVisible: (Boolean) -> Unit,
    rootComponent: RootComponent,
){
    val square = LocalLayoutMode.current == LayoutMode.SQUARE
    val dimens = LocalDimens.current
    Box(modifier = Modifier.fillMaxHeight()) {
    NavigationRail(
        modifier = Modifier.width(90.dp),
        containerColor = LocalChromeSurface.current,
    ) {

        Spacer(Modifier.height(26.dp))

            mainTabs.forEach { tab ->
                NavigationRailItem(
                    selected = activeConfig == tab.config,
                    onClick = {
                        rootComponent.switchTab(tab.config)
                    },
                    icon = { Icon(tab.icon, null) },
                    label = {
                        Text(
                            when (tab.config) {
                                ScreenConfig.Home -> stringResource(Res.string.nav_home)
                                ScreenConfig.Search -> stringResource(Res.string.nav_search)
                                ScreenConfig.Library -> stringResource(Res.string.nav_library)
                                else -> ""
                            }
                        )
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    alwaysShowLabel = false,
                )
            }

            Spacer(Modifier.weight(1f))

            bottomTabs.forEach { tab ->
                NavigationRailItem(
                    selected = activeConfig == tab.config,
                    onClick = {
                        changeQueueVisible(false)
                        rootComponent.switchTab(tab.config)
                    },
                    icon = { Icon(tab.icon, null) },
                    label = {
                        Text(
                            when (tab.config) {
                                ScreenConfig.Account -> stringResource(Res.string.nav_account)
                                ScreenConfig.Settings -> stringResource(Res.string.nav_settings)
                                ScreenConfig.ListenTogether -> stringResource(Res.string.nav_listen_together)
                                else -> ""
                            }
                        )
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    alwaysShowLabel = false,
                )
            }
            Spacer(Modifier.height(16.dp))


    }
    if (square) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(dimens.chromeBorderWidth)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
    }
}

@Composable
fun WideNavigationRail(
    activeConfig: ScreenConfig,
    changeQueueVisible: (Boolean) -> Unit,
    rootComponent: RootComponent,
) {
    val square = LocalLayoutMode.current == LayoutMode.SQUARE
    val dimens = LocalDimens.current
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val isExpanded = state.targetValue == WideNavigationRailValue.Expanded
    val headerDescription = if (isExpanded) "Collapse rail" else "Expand rail"

    Box(modifier = Modifier.fillMaxHeight()) {
    WideNavigationRail(
        state = state,
        colors =  WideNavigationRailDefaults.colors(
            containerColor = LocalChromeSurface.current,
        ),
        header = {
            IconButton(
                modifier =
                    Modifier.padding(start = 24.dp).semantics {
                        stateDescription = if (isExpanded) "Expanded" else "Collapsed"
                    },
                onClick = {
                    scope.launch {
                        if (isExpanded) state.collapse() else state.expand()
                    }
                },
            ) {
                if (isExpanded) {
                    Icon(Icons.AutoMirrored.Filled.MenuOpen, headerDescription)
                } else {
                    Icon(Icons.Filled.Menu, headerDescription)
                }
            }
        }
    ) {
            mainTabs.forEach { tab ->
                WideNavigationRailItem(
                    railExpanded = isExpanded,
                    icon = { Icon(tab.icon, null) },
                    label = {
                        Text(
                            when (tab.config) {
                                ScreenConfig.Home -> stringResource(Res.string.nav_home)
                                ScreenConfig.Search -> stringResource(Res.string.nav_search)
                                ScreenConfig.Library -> stringResource(Res.string.nav_library)
                                else -> ""
                            }
                        )
                    },
                    selected = activeConfig == tab.config,
                    onClick = {
                        rootComponent.switchTab(tab.config)
                    },
                )
            }

            bottomTabs.forEach { tab ->
                WideNavigationRailItem(
                    railExpanded = isExpanded,
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = {
                        Text(
                            when (tab.config) {
                                ScreenConfig.Account -> stringResource(Res.string.nav_account)
                                ScreenConfig.Settings -> stringResource(Res.string.nav_settings)
                                ScreenConfig.ListenTogether -> stringResource(Res.string.nav_listen_together)
                                else -> ""
                            }
                        )
                    },
                    selected = activeConfig == tab.config,
                    onClick = {
                        changeQueueVisible(false)
                        rootComponent.switchTab(tab.config)
                    },
                )
        }
    }
    if (square) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(dimens.chromeBorderWidth)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
    }
}
