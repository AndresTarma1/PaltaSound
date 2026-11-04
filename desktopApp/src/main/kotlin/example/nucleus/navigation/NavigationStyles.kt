package example.nucleus.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import kotlinx.coroutines.launch
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import example.nucleus.data.repository.LayoutMode
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalChromeSurface
import example.nucleus.ui.themes.LocalDimens
import example.nucleus.ui.themes.LocalLayoutMode

/**
 * Contenedor flotante con material Liquid Glass para los rails (BETA).
 *
 * Una sola superficie: el fondo vive DENTRO del estilo Glass, sin Boxes ni
 * bordes extra que se verían como recuadros. Óptica plana (refracción mínima)
 * porque el rail estrecho plegaría la refracción de borde en una banda central.
 */
@Composable
internal fun GlassRailContainer(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    // GlassStyle compara por identidad: recordar por (color, forma).
    val style = remember(colorScheme.surfaceContainer, shape) {
        GlassStyle.regular.then {
            backgroundColor(colorScheme.surfaceContainer.copy(alpha = 0.38f))
            tint(colorScheme.surfaceContainer.copy(alpha = 0.10f))
            shape(shape)
            edgeShadow(Color.Transparent)
            specularIntensity(0.15f)
            ambientResponse(0.06f)
            optics {
                copy(
                    blurRadius = OpticalSizeValue.Fixed(28.dp),
                    refractionStrength = 0.25f,
                    refractionDisplacement = 16.dp,
                    refractionHeightFraction = 0.15f,
                    depth = OpticalSizeValue.Fixed(1f),
                )
            }
        }
    }
    // Sin clip ni border: GlassStyle.shape ya enmascara la refracción y el
    // clip recortaría la capa expandida del cristal.
    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = style,
            ),
        content = content,
    )
}

@Composable
fun NavigationRailDefault(
    activeConfig: ScreenConfig,
    changeQueueVisible: (Boolean) -> Unit,
    rootComponent: RootComponent,
    liquidGlass: Boolean = false,
    hazeState: HazeState? = null,
){
    val square = LocalLayoutMode.current == LayoutMode.SQUARE
    val dimens = LocalDimens.current
    if (liquidGlass && hazeState != null && !square) {
        // Variante flotante BETA: una sola tarjeta Glass, sin el Surface
        // interno del rail (dos fondos = dos recuadros).
        val railShape = AppShapes.extraLarge
        Box(modifier = Modifier.fillMaxHeight()) {
            GlassRailContainer(
                hazeState = hazeState,
                shape = railShape,
                elevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(88.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
            }
        }
        return
    }
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
    liquidGlass: Boolean = false,
    hazeState: HazeState? = null,
) {
    val square = LocalLayoutMode.current == LayoutMode.SQUARE
    val dimens = LocalDimens.current
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val isExpanded = state.targetValue == WideNavigationRailValue.Expanded
    val headerDescription = if (isExpanded) "Collapse rail" else "Expand rail"
    if (liquidGlass && hazeState != null && !square) {
        // Variante flotante BETA: columna propia con ancho animado, sin el
        // Surface interno del WideNavigationRail de M3.
        val railShape = AppShapes.extraLarge
        val railWidth by animateDpAsState(
            targetValue = if (isExpanded) 236.dp else 96.dp,
            animationSpec = tween(durationMillis = 280),
            label = "wideRailWidth",
        )
        Box(modifier = Modifier.fillMaxHeight()) {
            GlassRailContainer(
                hazeState = hazeState,
                shape = railShape,
                elevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(railWidth)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isExpanded) 12.dp else 0.dp),
                        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            modifier = Modifier.semantics {
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
                    Spacer(Modifier.height(4.dp))
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.weight(1f))

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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        return
    }

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
