package example.nucleus.utils.cipher

import example.nucleus.platform.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Generador de PoTokens vía sidecar **rustypipe-botguard**.
 *
 * Desde ~julio 2026 los programas de BotGuard solo entregan el minter WebPO cuando las
 * comprobaciones de entorno pasan (DOM/canvas a nivel JSDOM + ytcfg EVENT_ID): un runtime
 * JS embebido con shims stub (QuickJS) ya no basta. Por eso el token lo emite un proceso
 * externo que incluye un runtime Deno reducido + JSDOM (mismo patrón de proceso que
 * [YtDlpResolver] con yt-dlp.exe): la comunidad (ThetaDev/rustypipe-botguard) mantiene el
 * entorno y el snapshot del intérprete actualizados.
 *
 * Contrato de la CLI (v1, verificado):
 *   rustypipe-botguard [--no-snapshot | --snapshot-file <archivo>] [--user-agent <ua>] -- <contentBinding>
 *   stdout: `<poToken> [valid_until=<epoch>] [from_snapshot=yes|no]`
 *
 * El snapshot del intérprete se cachea en disco (tmp) para que la acuñación por video
 * (~50-500ms) reutilice el runtime ya resuelto.
 */
object RustyPipeBotGuardSidecar {

    private const val BIN_DIR = "rustypipe-botguard"
    private val EXE_NAME = if (example.nucleus.platform.Platform.isWindows) "rustypipe-botguard.exe" else "rustypipe-botguard"
    private const val API_VERSION = "1"
    private const val MINT_TIMEOUT_SECONDS = 20L

    private val log = java.util.logging.Logger.getLogger("RustyPipeBotGuardSidecar")

    /** Valida que el binario responde --version con API v1. Lazy y cacheado. */
    val isAvailable: Boolean by lazy {
        val exe = binaryPath ?: run {
            log.warning("rustypipe-botguard not found (not bundled and not on PATH) — PoTokens web deshabilitados")
            return@lazy false
        }
        runCatching {
            val proc = ProcessBuilder(exe, "--version").redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            if (!proc.waitFor(3, TimeUnit.SECONDS) || proc.exitValue() != 0) return@runCatching false
            val api = Regex("rustypipe-botguard-api\\s+(\\d+)").find(out)?.groupValues?.get(1)
            val ok = api == API_VERSION
            if (!ok) log.warning("rustypipe-botguard API $api distinta de $API_VERSION: $out")
            ok
        }.getOrDefault(false).also { ok ->
            if (ok) log.info("rustypipe-botguard sidecar disponible")
        }
    }

    /**
     * Acuña un PoToken para [contentBinding] (visitorData, videoId o dataSyncId).
     *
     * @param fresh Si true, ignora el snapshot del intérprete (`--no-snapshot`): corre BotGuard
     *   con un reto de homepage fresco. Es lo que exige el recovery de atestación de SABR
     *   (un integrity token acuñado hace horas puede ser rechazado por el stream).
     * @return El PoToken base64url, o null si el binario no está disponible o falló.
     */
    suspend fun mint(contentBinding: String, fresh: Boolean = false): String? {
        val exe = binaryPath ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val args = buildList {
                    add(exe)
                    if (fresh) {
                        add("--no-snapshot")
                    } else {
                        snapshotFile()?.let { add("--snapshot-file"); add(it.absolutePath) }
                    }
                    add("--"); add(contentBinding)
                }
                val proc = ProcessBuilder(args).redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText()
                if (!proc.waitFor(MINT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                    log.warning("rustypipe-botguard timed out para $contentBinding")
                    return@withContext null
                }
                if (proc.exitValue() != 0) {
                    log.warning("rustypipe-botguard falló (rc=${proc.exitValue()}): ${out.take(300)}")
                    return@withContext null
                }
                log.info("rustypipe-botguard mint OK binding=${contentBinding.take(12)} out=${out.take(48)}")
                // El stdout es una única línea: "<poToken> valid_until=<ts> from_snapshot=<sí|no>".
                // Extraer solo el primer token (el resto es metadata).
                out.trim().substringBefore(' ').takeIf { it.startsWith("M") && it.length > 40 }
            } catch (e: Exception) {
                log.warning("rustypipe-botguard invocación falló para $contentBinding: ${e.message}")
                null
            }
        }
    }

    /** Snapshot del intérprete persistido en tmp: reutiliza el reto resuelto entre acuñaciones. */
    private fun snapshotFile(): File? = runCatching {
        File(AppPaths.tmpDir).apply { mkdirs() }
            .resolve("rustypipe-botguard-snapshot.json")
    }.getOrNull()

    /** El binario incluido (con el runtime) tiene prioridad sobre una instalación en el PATH. */
    private val binaryPath: String? by lazy { locateBinary() }

    private fun locateBinary(): String? {
        val userDir = File(System.getProperty("user.dir"))
        val rootDir = userDir.parentFile ?: userDir
        val resProp = System.getProperty("compose.application.resources.dir")
        val isWindows = example.nucleus.platform.Platform.isWindows
        val exeName = EXE_NAME
        val candidates = buildList {
            resProp?.let {
                add(File(it, exeName))
                add(File(File(it, "windows"), exeName))
                add(File(File(it, "linux"), exeName))
            }
            add(File(userDir, "resources/$exeName"))
            add(File(userDir, "mpv-resources/windows/$exeName"))
            add(File(userDir, "mpv-resources/linux/$exeName"))
            add(File(rootDir, "mpv-resources/windows/$exeName"))
            add(File(rootDir, "mpv-resources/linux/$exeName"))
            // Compat: si en Linux solo está el .exe en windows, permitirlo
            if (!isWindows) {
                add(File(userDir, "mpv-resources/windows/rustypipe-botguard.exe"))
                add(File(rootDir, "mpv-resources/windows/rustypipe-botguard.exe"))
            }
        }
        candidates.firstOrNull { it.exists() }?.let {
            log.info("Using bundled rustypipe-botguard: ${it.absolutePath}")
            return it.absolutePath
        }
        return resolveOnPath(BIN_DIR)?.also { log.info("Using system rustypipe-botguard: $it") }
    }

    private fun resolveOnPath(name: String): String? = try {
        val which = if (System.getProperty("os.name").startsWith("Windows", true)) "where" else "which"
        val proc = ProcessBuilder(which, name).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0) {
            out.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        } else null
    } catch (e: Exception) {
        null
    }
}
