@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.library

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.viewmodels.LibraryTab
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Pestañas como grupo de botones conectado (M3 Expressive ButtonGroup):
 * seleccionado lleno y resto tonal, con formas conectadas. Si no caben,
 * el sobrante colapsa al menú overflow en vez de cortarse.
 */
@Composable
internal fun LibraryTabRow(
    selectedTab: LibraryTab?,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allTabs = listOf(
        null to stringResource(Res.string.filter_all),
        LibraryTab.SONGS to stringResource(Res.string.tab_songs),
        LibraryTab.PLAYLISTS to stringResource(Res.string.tab_playlists),
        LibraryTab.ARTISTS to stringResource(Res.string.tab_artists),
        LibraryTab.ALBUMS to stringResource(Res.string.tab_albums),
        LibraryTab.PODCASTS to stringResource(Res.string.tab_podcasts),
    )

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(menuState = it) },
            expandedRatio = 0f,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            allTabs.forEach { (tab, label) ->
                val isSelected = if (tab == null) {
                    selectedTab == null || selectedTab == LibraryTab.LIBRARY
                } else {
                    selectedTab == tab
                }
                toggleableItem(
                    checked = isSelected,
                    onCheckedChange = { _ -> onTabSelected(tab ?: LibraryTab.LIBRARY) },
                    label = label,
                    icon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                            Text(
                                label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    },
                )
            }
        }
    }
}
