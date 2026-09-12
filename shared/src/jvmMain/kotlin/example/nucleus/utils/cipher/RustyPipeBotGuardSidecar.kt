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

    /**
     * Expiración (epoch seg) del integrity token del snapshot, informada por el sidecar
     * en cada mint (`valid_until=`). Los tokens acuñados con un integrity expirado son
     * "débiles": el /player los acepta pero el CDN capa el stream (1MB + 403).
     */
    @Volatile
    private var snapshotValidUntilSec: Long? = null

    /** Margen de seguridad antes de la expiración para forzar un re-solve fresco. */
    private const val SNAPSHOT_FRESH_MARGIN_SEC = 3600L

    /** Longitud mínima sana de un PoToken; debajo se asume snapshot degradado. */
    private const val MIN_HEALTHY_TOKEN_LEN = 100

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
                // Extraer el token + metadata (el valid_until decide si el snapshot sigue vigente).
                val parts = out.trim().split(Regex("\\s+"))
                val token = parts.firstOrNull()?.takeIf { it.startsWith("M") && it.length > 40 }
                    ?: return@withContext null
                val meta = parts.drop(1).mapNotNull {
                    it.split('=', limit = 2).takeIf { kv -> kv.size == 2 }?.let { kv -> kv[0] to kv[1] }
                }.toMap()
                meta["valid_until"]?.toLongOrNull()?.let { exp ->
                    if (!fresh) snapshotValidUntilSec = exp
                    log.info("rustypipe-botguard token len=${token.length} valid_until=$exp from_snapshot=${meta["from_snapshot"]}")
                }
                if (!fresh && token.length < MIN_HEALTHY_TOKEN_LEN) {
                    // Token degradado (snapshot rancio aunque diga vigente): re-solve fresco 1 vez.
                    log.warning("rustypipe-botguard token corto (${token.length}ch): snapshot degradado, re-solve fresco")
                    invalidateSnapshot()
                    return@withContext mint(contentBinding, fresh = true)
                }
                token
            } catch (e: Exception) {
                log.warning("rustypipe-botguard invocación falló para $contentBinding: ${e.message}")
                null
            }
        }
    }

    /**
     * Indica si el integrity token del snapshot sigue vigente (con margen).
     * Desconocido (sin mints aún) o expirado => false: el llamador debe invalidar
     * para forzar un re-solve fresco en el próximo mint.
     */
    fun isSnapshotFresh(marginSec: Long = SNAPSHOT_FRESH_MARGIN_SEC): Boolean {
        val exp = snapshotValidUntilSec ?: return false
        val fresh = exp - System.currentTimeMillis() / 1000 > marginSec
        if (!fresh) log.info("rustypipe-botguard snapshot vencido (valid_until=$exp)")
        return fresh
    }

    /** Borra el snapshot e invalida su vigencia: el próximo mint resuelve fresco y lo regenera. */
    fun invalidateSnapshot() {
        snapshotValidUntilSec = null
        runCatching { snapshotFile()?.delete() }
            .onFailure { log.warning("no se pudo borrar el snapshot: ${it.message}") }
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
