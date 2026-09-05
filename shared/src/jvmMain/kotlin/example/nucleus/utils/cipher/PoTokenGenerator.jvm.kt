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
     * @param videoId El ID del video al que se vincula el token de datos de streaming.
     * @param sessionId El ID de sesión al que se vincula el token de solicitud del reproductor.
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
                playerRequestPoToken = sessionBoundToken,
                streamingDataPoToken = videoBoundToken,
            )
        } catch (e: Exception) {
            Napier.e("[PoToken] Failed to generate PoToken: ${e.message}")
            null
        }
    }

    /**
     * Acuña el token de sesión (primer mint del snapshot). La inicialización completa del
     * runtime ocurre aquí (~1-5s); el resultado debe cachearse por sesión ([PoTokenManager]).
     *
     * El `/player` espera `service_integrity_dimensions.po_token` como **base64 estándar**
     * (TYPE_BYTES), por eso se convierte desde el base64url que emite el sidecar (ese mismo
     * base64url sí es válido para el parámetro `pot=` de las URLs del CDN).
     *
     * @return El token de sesión en base64 estándar.
     */
    suspend fun prepareSession(sessionId: String): String =
        base64UrlToStandard(mintBase64(sessionId))

    /**
     * Acuña un token ligado a [videoId] reutilizando el snapshot del sidecar (~50-500ms).
     * Se mantiene en base64url (el formato aceptado por el parámetro `pot=` del CDN).
     *
     * @return El token en base64url.
     */
    suspend fun mintVideo(videoId: String): String =
        mintBase64(videoId)

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
