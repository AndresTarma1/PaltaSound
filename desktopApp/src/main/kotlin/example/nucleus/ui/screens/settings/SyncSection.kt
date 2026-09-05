package example.nucleus.ui.screens.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.data.account.AccountManager
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.screens.shared.syncNowSubtitle
import example.nucleus.viewmodels.SyncSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncSettingsGroup(
    onShowYtmSyncWarning: () -> Unit
) {
    val colors = LocalSettingsColors.current
    val viewModel: SyncSettingsViewModel = koinInject()
    val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsState()
    val ytmSyncEnabled by viewModel.ytmSyncEnabled.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isSyncing = syncState.overallStatus is example.nucleus.utils.SyncStatus.Syncing
    // Sin sesión iniciada no hay nada que sincronizar (cuenta = cookie de YouTube Music).
    val isLoggedIn by remember { AccountManager.loginState }.collectAsState(false)

    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.section_sync),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors,
    ) {
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.WifiOff, null) },
            title = { Text(stringResource(Res.string.offline_mode)) },
            subtitle = { Text(stringResource(Res.string.offline_mode_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = if (isLoggedIn) 3 else 1),
            colors = colors,
            state = offlineModeEnabled,
            onCheckedChange = { viewModel.setOfflineModeEnabled(it) }
        )
        if (isLoggedIn) {
            SettingsSwitch(
                icon = { Icon(Icons.Rounded.CloudSync, null) },
                title = { Text(stringResource(Res.string.ytm_sync)) },
                subtitle = { Text(stringResource(Res.string.ytm_sync_subtitle)) },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                colors = colors,
                state = ytmSyncEnabled,
                onCheckedChange = { checked ->
                    if (checked) onShowYtmSyncWarning()
                    else viewModel.setYtmSyncEnabled(false)
                }
            )
            SettingsMenuLink(
                icon = { Icon(Icons.Rounded.Sync, null) },
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                title = { Text(stringResource(Res.string.sync_now)) },
                subtitle = {
                    Text(syncNowSubtitle(isSyncing, syncState.currentOperation))
                },
                colors = colors,
                action = {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                },
                onClick = { if (!isSyncing) viewModel.syncNow() }
            )
        }
    }
}
