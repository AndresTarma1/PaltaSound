package example.nucleus.ui.components.context

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import example.nucleus.download.DownloadState
import com.metrolist.innertube.models.SongItem
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.themes.AppShapes
import org.jetbrains.compose.resources.stringResource

sealed interface SongMenuAction {
    data object StartRadio : SongMenuAction
    data object ToggleLike : SongMenuAction
    data object Download : SongMenuAction
    data object CancelDownload : SongMenuAction
    data object RemoveDownload : SongMenuAction
    data object PlayNext : SongMenuAction
    data object AddToQueue : SongMenuAction
    data object AddToPlaylist : SongMenuAction
    data object RemoveFromLibrary : SongMenuAction
    data object RemoveFromPlaylist : SongMenuAction
}

@Composable
private fun MenuLeadingIcon(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
//        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
    )
}

private data class DownloadMenuSpec(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val action: SongMenuAction,
)

/** Un solo item dentro de un grupo, con la forma correcta según su posición. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupMenuItem(
    text: String,
    icon: ImageVector,
    itemIndex: Int,
    groupItemCount: Int,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { MenuLeadingIcon(icon, tint) },
        shape = groupItemShapeOf(itemIndex, groupItemCount),
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongContextMenuContent(
    song: SongItem,
    liked: Boolean,
    downloadState: DownloadState?,
    showQueueActions: Boolean = true,
    showRemoveFromLibrary: Boolean = false,
    showRemoveFromPlaylist: Boolean = false,
    onAction: (SongMenuAction) -> Unit,
) {
    val downloadSpec = when (downloadState) {
        is DownloadState.Completed -> DownloadMenuSpec(
            label = stringResource(Res.string.context_remove_download),
            icon = Icons.Default.DeleteOutline,
            tint = MaterialTheme.colorScheme.error,
            action = SongMenuAction.RemoveDownload,
        )
        is DownloadState.Downloading, is DownloadState.Queued -> DownloadMenuSpec(
            label = stringResource(Res.string.context_cancel_download),
            icon = Icons.Default.Cancel,
            tint = MaterialTheme.colorScheme.error,
            action = SongMenuAction.CancelDownload,
        )
        else -> DownloadMenuSpec(
            label = stringResource(Res.string.context_download),
            icon = Icons.Default.Download,
            tint = MaterialTheme.colorScheme.primary,
            action = SongMenuAction.Download,
        )
    }

    // Cuántos GRUPOS tiene el menu, no cuántos items. Es lo que decide si un grupo es leading,
    // middle o trailing: el grupo de la cola solo existe si `showQueueActions`.
    val groupCount = if (showQueueActions) 3 else 2

    // Cuántos items tiene el último grupo, en base a los flags condicionales.
    val trailingGroupCount = 1 + // Add to playlist siempre
            (if (showRemoveFromLibrary) 1 else 0) +
            (if (showRemoveFromPlaylist) 1 else 0)

    Column(modifier = Modifier.width(260.dp)) {
        // Grupo 1: Radio / Like / Download
        StableDropdownMenuGroup(groupIndex = 0, groupCount = groupCount) {
            GroupMenuItem(
                text = stringResource(Res.string.context_start_radio),
                icon = Icons.Default.Radio,
                itemIndex = 0,
                groupItemCount = 3,
                onClick = { onAction(SongMenuAction.StartRadio) },
            )
            GroupMenuItem(
                text = stringResource(if (liked) Res.string.context_unlike else Res.string.context_like),
                icon = if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                itemIndex = 1,
                groupItemCount = 3,
                onClick = { onAction(SongMenuAction.ToggleLike) },
            )
            GroupMenuItem(
                text = downloadSpec.label,
                icon = downloadSpec.icon,
                itemIndex = 2,
                groupItemCount = 3,
                tint = downloadSpec.tint,
                onClick = { onAction(downloadSpec.action) },
            )
        }

        if (showQueueActions) {
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))

            // Grupo 2: Play next / Add to queue
            StableDropdownMenuGroup(groupIndex = 1, groupCount = groupCount) {
                GroupMenuItem(
                    text = stringResource(Res.string.context_play_next),
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    itemIndex = 0,
                    groupItemCount = 2,
                    onClick = { onAction(SongMenuAction.PlayNext) },
                )
                GroupMenuItem(
                    text = stringResource(Res.string.context_add_queue),
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    itemIndex = 1,
                    groupItemCount = 2,
                    onClick = { onAction(SongMenuAction.AddToQueue) },
                )
            }
        }

        Spacer(Modifier.height(MenuDefaults.GroupSpacing))

        // Grupo 3: Add to playlist / Remove from library / Remove from playlist
        StableDropdownMenuGroup(groupIndex = groupCount - 1, groupCount = groupCount) {
            GroupMenuItem(
                text = stringResource(Res.string.context_add_playlist),
                icon = Icons.Default.AddCircleOutline,
                itemIndex = 0,
                groupItemCount = trailingGroupCount,
                onClick = { onAction(SongMenuAction.AddToPlaylist) },
            )

            if (showRemoveFromLibrary) {
                GroupMenuItem(
                    text = stringResource(Res.string.context_remove_library),
                    icon = Icons.Default.Delete,
                    itemIndex = 1,
                    groupItemCount = trailingGroupCount,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { onAction(SongMenuAction.RemoveFromLibrary) },
                )
            }

            if (showRemoveFromPlaylist) {
                GroupMenuItem(
                    text = stringResource(Res.string.context_remove_playlist),
                    icon = Icons.Default.RemoveCircleOutline,
                    itemIndex = if (showRemoveFromLibrary) 2 else 1,
                    groupItemCount = trailingGroupCount,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { onAction(SongMenuAction.RemoveFromPlaylist) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionContextMenuContent(
    title: String,
    isPlaylist: Boolean,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    // Cuántos GRUPOS tiene el menu. Los de export y remove solo existen si su accion no es null,
    // y eso es lo que decide si cada grupo queda leading, middle o trailing.
    val groupCount = 1 + // Main (open/play/shuffle) siempre
            (if (onExport != null) 1 else 0) +
            (if (onRemoveFromLibrary != null) 1 else 0)

    // Cuántos items tiene el primer grupo (título es aparte, sin grupo).
    val mainGroupCount = 1 + // Open siempre
            (if (onPlay != null) 1 else 0) +
            (if (onShuffle != null) 1 else 0)

    Column(modifier = Modifier.width(260.dp)) {
        Surface(
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        Spacer(Modifier.height(MenuDefaults.GroupSpacing))

        StableDropdownMenuGroup(groupIndex = 0, groupCount = groupCount) {
            GroupMenuItem(
                text = stringResource(
                    if (isPlaylist) Res.string.context_open_playlist else Res.string.context_open_album
                ),
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                itemIndex = 0,
                groupItemCount = mainGroupCount,
                onClick = onOpen,
            )

            onPlay?.let { action ->
                GroupMenuItem(
                    text = stringResource(Res.string.context_play),
                    icon = Icons.Default.PlayArrow,
                    itemIndex = 1,
                    groupItemCount = mainGroupCount,
                    onClick = action,
                )
            }

            onShuffle?.let { action ->
                GroupMenuItem(
                    text = stringResource(Res.string.context_shuffle),
                    icon = Icons.Default.Shuffle,
                    itemIndex = if (onPlay != null) 2 else 1,
                    groupItemCount = mainGroupCount,
                    onClick = action,
                )
            }
        }

        onExport?.let { action ->
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            StableDropdownMenuGroup(groupIndex = 1, groupCount = groupCount) {
                GroupMenuItem(
                    text = stringResource(Res.string.export_playlist),
                    icon = Icons.Default.Save,
                    itemIndex = 0,
                    groupItemCount = 1,
                    onClick = action,
                )
            }
        }

        onRemoveFromLibrary?.let { action ->
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            StableDropdownMenuGroup(groupIndex = groupCount - 1, groupCount = groupCount) {
                GroupMenuItem(
                    text = stringResource(Res.string.context_remove_library),
                    icon = Icons.Default.Delete,
                    itemIndex = 0,
                    groupItemCount = 1,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = action,
                )
            }
        }
    }
}
