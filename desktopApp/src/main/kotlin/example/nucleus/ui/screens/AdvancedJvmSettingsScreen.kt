package example.nucleus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import example.nucleus.data.repository.JvmConfig
import example.nucleus.data.repository.RenderApi
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.screens.shared.displayDescription
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.utils.AppRestarter
import example.nucleus.viewmodels.JvmSettingsUiState
import example.nucleus.viewmodels.JvmSettingsViewModel
import kotlinx.coroutines.launch
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedJvmSettingsScreen(
    viewModel: JvmSettingsViewModel,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showRestartDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints {
            val dialogWidth = (maxWidth * 0.9f).coerceIn(420.dp, maxWidth)
            val dialogHeight = (maxHeight * 0.7f).coerceIn(420.dp, maxHeight)

            Surface(
                modifier = Modifier.width(dialogWidth).height(dialogHeight),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                    ) {
                        Header(onDismiss = onDismiss)

                        Spacer(Modifier.height(20.dp))
                        SectionTitle(stringResource(Res.string.jvm_settings_title))
                        Spacer(Modifier.height(8.dp))
                        RenderApiSelector(
                            selected = uiState.renderApi,
                            onSelected = viewModel::setRenderApi,
                        )
                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { showRestartDialog = true },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(stringResource(Res.string.save_changes))
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showRestartDialog) {
        RestartDialog(
            uiState = uiState,
            onDismiss = { showRestartDialog = false },
            onSaveWithoutRestart = {
                scope.launch {
                    viewModel.saveRenderApi()
                    showRestartDialog = false
                }
            },
            onSaveAndRestart = {
                scope.launch {
                    if (viewModel.saveRenderApi()) {
                        AppRestarter.restartWithJvmArgs(uiState.toConfig())
                    }
                }
            },
        )
    }
}

@Composable
private fun Header(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                stringResource(Res.string.jvm_settings_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                stringResource(Res.string.jvm_settings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, stringResource(Res.string.close_label))
        }
    }
}

@Composable
private fun RestartDialog(
    uiState: JvmSettingsUiState,
    onDismiss: () -> Unit,
    onSaveWithoutRestart: () -> Unit,
    onSaveAndRestart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.apply_changes_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.apply_changes_message))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.selected_renderer, uiState.renderApi.displayName()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    uiState.renderApi.displayDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onSaveAndRestart, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(Res.string.save_restart))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSaveWithoutRestart) {
                    Text(stringResource(Res.string.save_no_restart))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
    )
}

private fun JvmSettingsUiState.toConfig() = JvmConfig(
    xmx = xmx,
    xms = xms,
    useG1GC = useG1GC,
    useZGC = useZGC,
    gcLogging = gcLogging,
    renderApi = renderApi,
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}


@Composable
private fun RenderApiSelector(
    selected: RenderApi,
    onSelected: (RenderApi) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RenderApi.entries.forEach { renderApi ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelected(renderApi) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .background(
                            if (selected == renderApi) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == renderApi,
                        onClick = { onSelected(renderApi) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (renderApi == RenderApi.DIRECTX) stringResource(Res.string.renderer_directx) + " " + stringResource(Res.string.renderer_default) else renderApi.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            renderApi.displayDescription(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

