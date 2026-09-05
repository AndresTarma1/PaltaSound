package example.nucleus.utils.cipher

import io.github.aakira.napier.Napier
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Orquestador de generación de poTokens con el patrón de Metrolist (utils/potoken):
 *
 *  - Cachea el token ligado a la SESIÓN (visitorData): el challenge BotGuard y la
 *    creación del minter son costosos (~1-2s) y solo deben ocurrir una vez por sesión.
 *  - Acuña el token por VIDEO en cada reproducción (~50-200ms) reutilizando el minter.
 *  - Tope de 8s por generación: si QuickJS cuelga, la reproducción continúa sin
 *    poToken (los clientes no-web lo cubren) en lugar de bloquear el playback.
 *  - Reintento único recreando el motor desde cero si un mint falla con minter vivo
 *    (p.ej. el challenge expiró).
 */
actual object PoTokenManager {

    private val lock = Mutex()

    private var cachedSessionId: String? = null
    private var cachedSessionPot: String? = null

    /**
     * Devuelve el par de tokens (sesión → /player, video → pot= en URL) para [videoId].
     *
     * El pipeline usa un retry único completo (motor + challenge frescos) si el primer
     * intento falla: los programas BotGuard son volátiles (se regeneran por request) y
     * sueltan errores intermitentes tipo "Snapshot failed: not a function" (~1-2%) que
     * re-probar con un challenge nuevo siempre resuelve.
     *
     * @return El resultado, o null si la generación falló o excedió el timeout (el
     *         llamador debe continuar sin poToken, igual que Metrolist).
     */
    actual suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? = try {
        withTimeout(GENERATION_TIMEOUT_MS) {
            try {
                internalGet(videoId, sessionId, forceRecreate = false)
            } catch (e: Exception) {
                Napier.w("[PoToken] Generation attempt failed (${e.message}); retrying with fresh session")
                reset()
                internalGet(videoId, sessionId, forceRecreate = true)
            }
        }
    } catch (e: TimeoutCancellationException) {
        Napier.w("[PoToken] Generation exceeded ${GENERATION_TIMEOUT_MS}ms; proceeding without PoToken")
        reset()
        null
    } catch (e: Exception) {
        Napier.w("[PoToken] Generation failed: ${e.message}")
        null
    }

    private suspend fun internalGet(
        videoId: String,
        sessionId: String,
        forceRecreate: Boolean,
    ): PoTokenResult {
        var freshSession = false
        val sessionPot = lock.withLock {
            if (forceRecreate || sessionId != cachedSessionId || cachedSessionPot == null) {
                // Estado previo inválido (o primera vez): recrear motor + challenge + minter.
                resetEngine()
                val pot = PoTokenGenerator.prepareSession(sessionId)
                cachedSessionId = sessionId
                cachedSessionPot = pot
                freshSession = true
                pot
            } else {
                cachedSessionPot!!
            }
        }
        return try {
            PoTokenResult(
                playerRequestPoToken = sessionPot,
                streamingDataPoToken = PoTokenGenerator.mintVideo(videoId),
            )
        } catch (t: Throwable) {
            if (freshSession) throw t
            // El minter pudo expirar desde que se preparó la sesión: un reintento con
            // sesión nueva suele resolverlo (mismo patrón que Metrolist).
            Napier.w("[PoToken] Video mint failed (${t.message}); recreating session")
            internalGet(videoId, sessionId, forceRecreate = true)
        }
    }

    /**
     * Calienta el pipeline BotGuard al arrancar (challenge + minter + mint dummy) para
     * esconder el cold-start de la primera reproducción real.
     */
    actual suspend fun prewarm(sessionId: String) {
        runCatching { getWebClientPoToken(PREWARM_VIDEO_ID, sessionId) }
            .onFailure { Napier.d("[PoToken] Prewarm skipped: ${it.message}") }
    }

    /** Invalida la sesión cacheada y libera el motor. */
    actual suspend fun reset() {
        lock.withLock { resetEngine() }
    }

    /** Llamar sosteniendo [lock]. */
    private suspend fun resetEngine() {
        cachedSessionId = null
        cachedSessionPot = null
    }

    private val GENERATION_TIMEOUT_MS = 8_000L.milliseconds

    /** Id estable cualquiera; el token resultante se descarta. Solo calienta el motor. */
    private const val PREWARM_VIDEO_ID = "dQw4w9WgXcQ"
}
