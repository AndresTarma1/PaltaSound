package example.nucleus.ui.screens.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.screens.shared.updateCheckSubtitle
import example.nucleus.viewmodels.AppViewModel
import example.nucleus.viewmodels.UpdateCheckState
import example.nucleus.viewmodels.UpdateStatus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SupportSettingsGroup() {
    val colors = LocalSettingsColors.current
    val appViewModel: AppViewModel = koinInject()
    val updateCheckState by appViewModel.checkState.collectAsState()
    val updateStatus by appViewModel.updateStatus.collectAsState()
    val pendingCrashReports by appViewModel.pendingCrashReports.collectAsState()
    LaunchedEffect(Unit) { appViewModel.refreshCrashReports() }

    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.section_support),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors,
    ) {
        val downloading = updateStatus as? UpdateStatus.Downloading
        val ready = updateStatus is UpdateStatus.Ready
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
            title = { Text(stringResource(Res.string.check_updates)) },
            subtitle = {
                Text(updateCheckSubtitle(updateStatus, updateCheckState))
            },
            colors = colors,
            action = {
                when {
                    ready -> {
                        FilledTonalButton(onClick = { appViewModel.checkForUpdates(manual = true) }) {
                            Text(stringResource(Res.string.btn_install_update))
                        }
                    }
                    downloading != null || updateCheckState is UpdateCheckState.Checking ->
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else -> TextButton(onClick = { appViewModel.checkForUpdates(manual = true) }) {
                        Text(stringResource(Res.string.btn_check))
                    }
                }
            },
            onClick = {
                if (ready) appViewModel.checkForUpdates(manual = true)
                else if (downloading == null && updateCheckState !is UpdateCheckState.Checking)
                    appViewModel.checkForUpdates(manual = true)
            }
        )
        ActionRow(
            label = stringResource(Res.string.report_bug),
            subtitle = stringResource(Res.string.report_bug_subtitle),
            icon = Icons.Rounded.BugReport,
            btnLabel = stringResource(Res.string.btn_report),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 3),
            onClick = { openReportBugPage() }
        )
        SettingsMenuLink(
            icon = {
                if (pendingCrashReports > 0) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text("$pendingCrashReports")
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, null)
                    }
                } else {
                    Icon(Icons.Rounded.BugReport, null)
                }
            },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
            title = { Text(stringResource(Res.string.send_crash_report)) },
            subtitle = {
                Text(
                    if (pendingCrashReports > 0)
                        stringResource(Res.string.crash_pending_count, pendingCrashReports)
                    else
                        stringResource(Res.string.no_crash_reports)
                )
            },
            colors = colors,
            action = {
                if (pendingCrashReports > 0) {
                    FilledTonalButton(onClick = { appViewModel.sendCrashReports() }) {
                        Text(stringResource(Res.string.crash_send))
                    }
                }
            },
            onClick = {
                if (pendingCrashReports > 0) {
                    appViewModel.sendCrashReports()
                }
            }
        )
    }
}
