package example.nucleus.bootstrap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.data.repository.CrashReport
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun CrashReportDialog(
    reports: List<Pair<File, CrashReport>>,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (reports.isEmpty()) return

    val count = reports.size
    val latest = reports.first().second
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
        title = {
            Text(
                if (count == 1) stringResource(Res.string.crash_detected)
                else stringResource(Res.string.crash_detected_count).replace("%d", count.toString()),
                color = scheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = if (count == 1) stringResource(Res.string.crash_description)
                    else stringResource(Res.string.crash_description_count).replace("%1\$d", count.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.crash_version_info)
                        .replace("%1\$s", latest.appVersion)
                        .replace("%2\$s", latest.os),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${latest.exception}: ${latest.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.crash_send_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSend) {
                Text(stringResource(Res.string.crash_send), color = scheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.crash_no_send), color = scheme.primary)
            }
        },
    )
}
