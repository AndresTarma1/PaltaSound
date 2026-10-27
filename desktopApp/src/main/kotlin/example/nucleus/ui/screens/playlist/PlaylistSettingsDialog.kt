package example.nucleus.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.btn_save
import example.nucleus.generated.resources.cancel
import example.nucleus.generated.resources.playlist_cover_change
import example.nucleus.generated.resources.playlist_name_label
import example.nucleus.generated.resources.playlist_settings_title
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.themes.AppShapes
import example.nucleus.generated.resources.auto_sync_playlist
import example.nucleus.generated.resources.auto_sync_playlist_subtitle
import org.jetbrains.compose.resources.stringResource

/**
 * Modal "Configurar playlist": renombrar, portada y sincronización automática.
 * La sincronización solo aparece para playlists guardadas y vinculadas a YouTube.
 */
@Composable
fun PlaylistSettingsDialog(
    playlistTitle: String,
    coverUrl: String?,
    showAutoSync: Boolean,
    autoSyncEnabled: Boolean?,
    onToggleAutoSync: ((Boolean) -> Unit)?,
    onRename: (String) -> Unit,
    onEditCover: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(playlistTitle) { mutableStateOf(playlistTitle) }
    val nameChanged = name.trim().isNotBlank() && name.trim() != playlistTitle

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(Res.string.playlist_settings_title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.playlist_name_label)) },
                    singleLine = true,
                    shape = AppShapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (onEditCover != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MusicPlayerImage(
                            url = coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            placeholderType = PlaceholderType.PLAYLIST,
                            iconSize = 22.dp,
                            contentScale = ContentScale.Crop,
                        )
                        OutlinedButton(
                            onClick = onEditCover,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.playlist_cover_change))
                        }
                    }
                }

                if (showAutoSync && autoSyncEnabled != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = AppShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(Res.string.auto_sync_playlist),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(Res.string.auto_sync_playlist_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = autoSyncEnabled,
                                onCheckedChange = { onToggleAutoSync?.invoke(it) },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameChanged,
                onClick = {
                    onRename(name.trim())
                    onDismiss()
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource(Res.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
