package example.nucleus.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.platform.NativeDesktop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import java.io.File

/**
 * Exporta una playlist y muestra el resultado. Si el usuario pulsa "Abrir carpeta",
 * abre el directorio del archivo exportado.
 */
fun exportPlaylistWithFeedback(
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    export: ((Boolean, String) -> Unit) -> Unit,
) {
    export { success, path ->
        scope.launch {
            if (success) {
                val result = snackbar.showSnackbar(
                    message = getString(Res.string.export_playlist_success, path),
                    actionLabel = "Abrir carpeta",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    runCatching {
                        val file = File(path)
                        NativeDesktop.openFolder(file.parentFile ?: file)
                    }
                }
            } else {
                snackbar.showSnackbar(getString(Res.string.export_playlist_error, path))
            }
        }
    }
}
