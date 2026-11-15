@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.library

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.glass.GlassFilterChip
import example.nucleus.ui.components.layout.HorizontalScrollableRow
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.viewmodels.LibraryTab
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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

    HorizontalScrollableRow(
        modifier = modifier
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        allTabs.forEach { (tab, label) ->
            val isSelected = if (tab == null) {
                selectedTab == null || selectedTab == LibraryTab.LIBRARY
            } else {
                selectedTab == tab
            }
            item {
                GlassFilterChip(
                    selected = isSelected,
                    leadingIcon = {
                        if (isSelected) Icon(
                            Icons.Default.Check,
                            contentDescription = null
                        )
                    },
                    onClick = { onTabSelected(tab ?: LibraryTab.LIBRARY) },
                    label = {
                        Text(
                            label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    shape = RoundedCornerShape(50.dp),
                    showBorder = false,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}
