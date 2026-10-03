package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nucleusframework.updater.DownloadProgress
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GitHubProvider
import dev.nucleusframework.core.runtime.ExecutableRuntime
import example.nucleus.data.repository.CrashReportRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    /** Página de release de GitHub — alternativa si no se encuentra un activo de instalador. */
    val releaseUrl: String? = null,
    /** URL de descarga directa del activo del instalador .msi/.exe. */
    val installerUrl: String? = null,
    val installerName: String? = null,
    val installerSize: Long? = null,
)

/**
 * Todo el ciclo de vida de actualización en un solo flow. La descarga se delega por completo a
 * [NucleusUpdater] (proveedor GitHub + metadata `latest.yml` estilo electron-builder). La descarga se
 * ejecuta en segundo plano (el usuario sigue usando la app); cuando termina mostramos un prompt
 * "instalar ahora / después", y el estado [Ready] persiste para que Configuración pueda ofrecer
 * "instalar actualización" sin volver a descargar.
 */
sealed interface UpdateStatus {
    data object None : UpdateStatus
    /** [progress] en 0f..1f, o -1f cuando el servidor no reportó un content length. */
    data class Downloading(val info: AppUpdateInfo, val progress: Float) : UpdateStatus
    /** Instalador completamente descargado y en disco, listo para iniciar. */
    data class Ready(val info: AppUpdateInfo, val file: File) : UpdateStatus
    /** Existe una versión más nueva pero no tiene activo de instalador para esta plataforma — abrir la página de release. */
    data class ManualOnly(val info: AppUpdateInfo) : UpdateStatus
    data class Failed(val info: AppUpdateInfo) : UpdateStatus
}

/** Retroalimentación para la entrada manual "buscar actualizaciones" en Configuración. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data object Failed : UpdateCheckState
}

class AppViewModel : ViewModel() {

    companion object {
        /**
         * Fuente única de verdad para la versión de la app mostrada/comparada (también se muestra en
         * Configuración). Debe mantenerse sincronizada con `packageVersion` en
         * `desktopApp/build.gradle.kts`, porque el updater compara esta cadena contra la versión del
         * metadato `latest.yml` publicado en GitHub Releases.
         */
        const val CURRENT_VERSION = "0.8.2"
    }

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.None)
    val updateStatus: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** Flag de una sola vez que controla el modal "instalar ahora / después"; se establece cuando una descarga termina. */
    private val _showInstallPrompt = MutableStateFlow(false)
    val showInstallPrompt: StateFlow<Boolean> = _showInstallPrompt.asStateFlow()

    private val _checkState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val checkState: StateFlow<UpdateCheckState> = _checkState.asStateFlow()

    private val _pendingCrashReports = MutableStateFlow(0)
    val pendingCrashReports: StateFlow<Int> = _pendingCrashReports.asStateFlow()

    private var downloadJob: Job? = null

    private val updater: NucleusUpdater = NucleusUpdater {
        currentVersion = CURRENT_VERSION
        provider = GitHubProvider(owner = "AndresTarma1", repo = "PaltaSound")
        channel = "latest"
    }

    /** Compatible con la clase base homónima de `shared` (misma FQN): cancela la descarga en curso. */
    fun dispose() {
        downloadJob?.cancel()
    }

    /**
     * Busca un release más nuevo. Al iniciar se pasa [manual] = false: una actualización encontrada se descarga
     * silenciosamente en segundo plano y solo interrumpe al usuario con el prompt de instalación cuando está lista.
     * Desde Configuración se pasa [manual] = true para que se muestre la retroalimentación de actualizado/fallido,
     * y — si ya hay un instalador descargado — el prompt de instalación se reabre en vez de no hacer nada.
     */
    fun checkForUpdates(manual: Boolean = false) {
        when (_status.value) {
            is UpdateStatus.Ready -> { if (manual) _showInstallPrompt.value = true; return }
            is UpdateStatus.Downloading -> return // ya está trabajando; no apilar descargas
            else -> {}
        }

        viewModelScope.launch {
            if (manual) _checkState.value = UpdateCheckState.Checking
            val result = withContext(Dispatchers.IO) {
                runCatching { updater.checkForUpdates() }.getOrNull()
            }
            when (result) {
                is UpdateResult.NotAvailable -> {
                    _status.value = UpdateStatus.None
                    if (manual) _checkState.value = UpdateCheckState.UpToDate
                }
                is UpdateResult.Error -> {
                    Napier.e("Update check failed: ${result.exception.message}")
                    _status.value = UpdateStatus.None
                    if (manual) _checkState.value = UpdateCheckState.Failed
                }
                is UpdateResult.Available -> {
                    val info = toAppUpdateInfo(result.info)
                    if (manual) _checkState.value = UpdateCheckState.Idle
                    // Binario nativo GraalVM: latest.yml solo lista el instalador JVM, y el
                    // selector no distingue runtimes (ambos son formato "nsis"). Descargarlo
                    // migraria al usuario de variante sin avisar, asi que no se descarga:
                    // se informa con la version remota real y se ofrece la pagina de release.
                    if (ExecutableRuntime.isGraalVmNativeImage) {
                        _status.value = UpdateStatus.ManualOnly(info)
                    } else {
                        startDownload(result.info, info)
                    }
                }
                null -> {
                    if (manual) _checkState.value = UpdateCheckState.Failed
                }
            }
        }
    }

    private fun startDownload(update: UpdateInfo, info: AppUpdateInfo) {
        downloadJob?.cancel()
        _status.value = UpdateStatus.Downloading(info, -1f)
        downloadJob = viewModelScope.launch {
            var lastFile: File? = null
            updater.downloadUpdate(update)
                .onEach { progress ->
                    lastFile = progress.file
                    _status.value = UpdateStatus.Downloading(info, progressPercent(progress))
                }
                .catch { e ->
                    Napier.e("Update download failed: ${e.message}")
                    _status.value = UpdateStatus.Failed(info)
                }
                .collect { }
            // El flow termina de forma natural solo cuando el archivo ya está verificado y listo.
            val file = lastFile
            if (file != null && file.exists()) {
                _status.value = UpdateStatus.Ready(info, file)
                _showInstallPrompt.value = true
            } else {
                _status.value = UpdateStatus.Failed(info)
            }
        }
    }

    private fun progressPercent(progress: DownloadProgress): Float =
        if (progress.totalBytes > 0) (progress.bytesDownloaded.toDouble() / progress.totalBytes).toFloat()
        else -1f

    /** Reintentar una descarga fallida desde la entrada "buscar" o el prompt. */
    fun retryDownload() {
        (_status.value as? UpdateStatus.Failed)?.let { checkForUpdates(manual = false) }
    }

    /** Inicia el instalador descargado y, en caso de éxito, llama a [onQuit] para que la app pueda cerrarse. */
    fun installUpdate(onQuit: () -> Unit) {
        val ready = _status.value as? UpdateStatus.Ready ?: return
        viewModelScope.launch {
            _showInstallPrompt.value = false
            val ok = withContext(Dispatchers.IO) {
                runCatching { updater.installAndRestart(ready.file) }
                    .onFailure { Napier.e("Update launch failed: ${it.message}") }
                    .isSuccess
            }
            if (ok) onQuit()
        }
    }

    /** Ocultar el prompt pero mantener el instalador descargado + [UpdateStatus.Ready] para después. */
    fun postponeInstall() {
        _showInstallPrompt.value = false
    }

    /** Reiniciar la retroalimentación transitoria de Configuración (actualizado/fallido) a inactivo. */
    fun clearCheckFeedback() {
        _checkState.value = UpdateCheckState.Idle
    }

    /** Re-lee cuántos reportes de crash hay pendientes por enviar (IO en segundo plano). */
    fun refreshCrashReports() {
        viewModelScope.launch {
            _pendingCrashReports.value = withContext(Dispatchers.IO) {
                CrashReportRepository.getUnsentReports().size
            }
        }
    }

    /** Abre los reportes pendientes como issues de GitHub y los marca como enviados. */
    fun sendCrashReports() {
        val reports = CrashReportRepository.getUnsentReports()
        reports.forEach { (_, report) -> CrashReportRepository.openCrashAsGitHubIssue(report) }
        CrashReportRepository.markAllAsSent()
        refreshCrashReports()
    }

    private fun toAppUpdateInfo(info: UpdateInfo): AppUpdateInfo {
        val file = info.currentFile
        return AppUpdateInfo(
            currentVersion = CURRENT_VERSION,
            latestVersion = info.version,
            releaseUrl = "https://github.com/AndresTarma1/PaltaSound/releases/latest",
            installerUrl = file.url,
            installerName = file.fileName,
            installerSize = file.size.takeIf { it > 0 },
        )
    }
}