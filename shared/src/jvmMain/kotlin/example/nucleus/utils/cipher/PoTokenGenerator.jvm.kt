package example.nucleus.utils.cipher

import io.github.aakira.napier.Napier

/**
 * Implementación JVM de [PoTokenGenerator]: genera PoTokens vía sidecar
 * rustypipe-botguard (ver [RustyPipeBotGuardSidecar]).
 *
 * Historia: la versión previa corría BotGuard en QuickJS embebido (WAA Create → snapshot →
 * GenerateIT → minter). Desde julio 2026 YouTube sirve programas cuyo minter WebPO
 * (`webPoSignalOutput[0]`) solo se entrega si las comprobaciones de entorno pasan
 * (DOM y canvas a nivel JSDOM + yt.config_.EVENT_ID), imposible de replicar con shims
 * stub en QuickJS; el pipeline además quedó pactado al protocolo viejo (challenge por
 * WAA /Create con requestKey fijo y GenerateIT sobre /api/jnn). El sidecar embebe un
 * runtime Deno reducido + JSDOM que sí los cubre y se mantiene al día con YouTube.
 *
 * Pipeline (por sesión, vía [prepareSession], más un mint por video):
 *   1. rustypipe-botguard resuelve el reto (homepage ytAtN + ytcfg), inicializa BotGuard
 *      y crea el minter WebPO (snapshot cacheado en disco).
 *   2. Cada acuñación reutiliza el snapshot: ~50-500ms por token.
 */
actual object PoTokenGenerator {

    /**
     * Genera PoTokens para la autenticación de streaming de video.
     *
     * Contrato canónico (rustypipe-botguard / BgUtils, y YouTube):
     *  - `playerRequestPoToken`: token ligado al **videoId** (content-bound) → va en
     *    `serviceIntegrityDimensions.poToken` del `/player`.
     *  - `streamingDataPoToken`: token ligado a la **sesión/visitorData** (session-bound) →
     *    va en el parámetro `pot=` de las URLs del CDN.
     *
     * (Metrolist/Android invierte este par y YouTube lo acepta igual; alineamos con el
     *  contrato de escritorio para reducir riesgo si YouTube endurece la validación.)
     *
     * @param videoId Identificador del video (content-bound) para el token del `/player`.
     * @param sessionId Identificador de sesión/visitorData (session-bound) para el `pot=` del stream.
     * @return Un [PoTokenResult] con los tokens generados, o `null` si la generación falla.
     */
    actual suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        if (!RustyPipeBotGuardSidecar.isAvailable) return null
        Napier.i("[PoToken] Generating PoToken for videoId=$videoId sessionId=${sessionId.take(8)}...")
        return try {
            val sessionBoundToken = prepareSession(sessionId)
            val videoBoundToken = mintVideo(videoId)
            Napier.i("[PoToken] PoToken generated successfully")
            PoTokenResult(
                playerRequestPoToken = videoBoundToken,
                streamingDataPoToken = sessionBoundToken,
            )
        } catch (e: Exception) {
            Napier.e("[PoToken] Failed to generate PoToken: ${e.message}")
            null
        }
    }

    /**
     * Acuña el token de **sesión** (session-bound) reutilizando el snapshot del sidecar.
     * Es el que va al parámetro `pot=` del CDN y se cachea por sesión ([PoTokenManager]).
     * Se mantiene en base64url (formato aceptado por `pot=`).
     *
     * @return El token de sesión en base64url.
     */
    suspend fun prepareSession(sessionId: String): String =
        mintBase64(sessionId)

    /**
     * Acuña el token ligado al **videoId** (content-bound) para el `/player`. Se mantiene en
     * base64 estándar (el campo `serviceIntegrityDimensions.poToken` es bytes en proto-JSON).
     *
     * @return El token del video en base64 estándar.
     */
    suspend fun mintVideo(videoId: String): String =
        base64UrlToStandard(mintBase64(videoId))

    private suspend fun mintBase64(identifier: String): String =
        RustyPipeBotGuardSidecar.mint(identifier)
            ?: throw PoTokenException("rustypipe-botguard mint returned null")

    /** Conversión base64url → base64 estándar (restaura el padding si faltara). */
    private fun base64UrlToStandard(urlSafe: String): String {
        val s = urlSafe.replace('-', '+').replace('_', '/')
        return when (s.length % 4) {
            0 -> s
            2 -> s + "=="
            3 -> s + "="
            else -> s
        }
    }
}
