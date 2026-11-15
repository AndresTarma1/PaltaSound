@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.library

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.glass.GlassAlertDialog
import example.nucleus.ui.screens.library.CsvImportTutorialBody
import example.nucleus.ui.screens.library.CsvImportTutorialIcon
import example.nucleus.ui.themes.AppShapes
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CreatePlaylistDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.create_playlist_dialog)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.playlist_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.btn_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
internal fun CsvTutorialDialog(
    openExtractPage: Boolean,
    onOpenExtractPageChange: (Boolean) -> Unit,
    onConfirm: (openPage: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = { CsvImportTutorialIcon() },
        title = { Text(stringResource(Res.string.csv_tutorial_title)) },
        text = {
            Column {
                CsvImportTutorialBody()
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.small)
                        .clickable { onOpenExtractPageChange(!openExtractPage) }
                        .padding(4.dp)
                ) {
                    Checkbox(
                        checked = openExtractPage,
                        onCheckedChange = onOpenExtractPageChange)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.csv_tutorial_open_page),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(openExtractPage) }) {
                Text(
                    if (openExtractPage) stringResource(Res.string.csv_tutorial_btn) else stringResource(
                        Res.string.csv_import_open_picker)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
internal fun CsvImportNameDialog(
    name: String,
    onNameChange: (String) -> Unit,
    totalCount: Int,
    suggestedName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.csv_import_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.csv_songs_found, totalCount))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.playlist_name_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim().ifBlank { suggestedName }) },
            ) { Text(stringResource(Res.string.csv_btn_import)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
internal fun DeletePlaylistDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_playlist_title)) },
        text = { Text(stringResource(Res.string.delete_playlist_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.delete_playlist_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
