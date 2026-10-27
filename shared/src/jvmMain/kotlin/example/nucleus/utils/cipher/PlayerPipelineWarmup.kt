package example.nucleus.utils.cipher

import com.metrolist.innertube.YouTube
import example.nucleus.utils.cipher.CipherDeobfuscator
import java.util.logging.Logger

/**
 * Precalienta en segundo plano las piezas costosas del pipeline WEB de reproducción para
 * que el primer stream web no pague su cold-start. Se invoca al arrancar la app.
 */
object PlayerPipelineWarmup {
    private val log = Logger.getLogger("PlayerPipelineWarmup")
    private var done = false

    @Synchronized
    fun markDone() { done = true }

    suspend fun warmup() {
        if (done) return
        // 1) Solver de cipher (nativo primero, EJS fallback).
        runCatching {
            val ok = CipherDeobfuscator.warmup()
            if (ok) log.info("Cipher solver precalentado") else log.warning("prewarm cipher devolvio false")
        }.onFailure { log.warning("prewarm cipher fallo: ${it.message}") }

        // 2) PoToken via sidecar (si hay visitorData y el binario esta presente).
        runCatching {
            val vd = YouTube.visitorData().getOrNull() ?: return@runCatching
            PoTokenManager.prewarm(vd)
            log.info("PoToken sidecar precalentado")
        }.onFailure { log.warning("prewarm PoToken fallo: ${it.message}") }

        markDone()
    }
}
