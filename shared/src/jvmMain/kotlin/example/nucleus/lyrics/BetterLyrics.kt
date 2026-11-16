package example.nucleus.lyrics

import example.nucleus.network.AppHttpClient
import io.github.aakira.napier.Napier
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data class TTMLResponse(val ttml: String? = null)

/**
 * BetterLyrics API client (word-synced TTML lyrics) — port of Metrolist's `betterlyrics/BetterLyrics`.
 * Fetches TTML from the official API and converts it to enriched LRC via [TTMLParser].
 *
 * Dos backends:
 *  - `/getLyrics`     — Apple Music (TTML por sílabas). Sin key sirve las consultas
 *    cacheadas; la X-API-Key solo hace falta para cache misses cuando el servidor
 *    la exige. Se puede configurar con `PALTASOUND_BETTERLYRICS_API_KEY`.
 *  - `/qq/getLyrics`  — Portato / QQ Music (TTML con tiempos por palabra) en el
 *    host original de la extensión; no necesita key.
 */
object BetterLyrics {
    private const val BASE_URL = "https://api.betterlyrics.org"
    // El host original devuelve TTML para QQ; el oficial devuelve QRC, que no
    // parseamos. Portato se queda en el host de la extensión.
    private const val PORTATO_BASE_URL = "https://lyrics-api.boidu.dev"
    private const val APPLE_PATH = "/getLyrics"
    private const val PORTATO_PATH = "/qq/getLyrics"
    const val API_KEY_ENV = "PALTASOUND_BETTERLYRICS_API_KEY"
    private val client = AppHttpClient.client

    private suspend fun fetchTTML(
        baseUrl: String,
        path: String,
        artist: String,
        title: String,
        duration: Int,
        album: String?,
        apiKey: String? = null,
    ): String? = runCatching {
        val response = client.get("$baseUrl$path") {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            header("Accept", "application/json")
            if (!apiKey.isNullOrBlank()) header("X-API-Key", apiKey)
            parameter("s", title)
            parameter("a", artist)
            if (duration > 0) parameter("d", duration)
            if (!album.isNullOrBlank()) parameter("al", album)
        }
        if (response.status.isSuccess() || response.status == HttpStatusCode.OK) {
            response.body<TTMLResponse>().ttml?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            when (response.status) {
                // Sin key, un 401 significa "cache miss": es lo normal y no se
                // registra para no llenar el log en cada canción. Con key sí es
                // un error accionable (key inválida o caducada).
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> {
                    if (!apiKey.isNullOrBlank()) {
                        Napier.w("[BetterLyrics] API key rejected (${response.status})")
                    }
                }
                // 404 = no hay letra para esa canción; la cadena sigue.
                HttpStatusCode.NotFound -> Unit
                else -> Napier.w("[BetterLyrics] API status ${response.status}")
            }
            null
        }
    }.getOrElse { e ->
        if (e is CancellationException) throw e
        Napier.e("[BetterLyrics] fetchTTML failed", e)
        null
    }

    private fun toLRC(ttml: String?): String? {
        if (ttml == null) return null
        val parsed = TTMLParser.parseTTML(ttml)
        if (parsed.isEmpty()) return null
        return TTMLParser.toLRC(parsed)
    }

    /**
     * Apple Music endpoint. Intenta siempre las consultas cacheadas sin key;
     * si hay `PALTASOUND_BETTERLYRICS_API_KEY`, la envía para permitir las que
     * no están en caché.
     */
    suspend fun getLyrics(title: String, artist: String, duration: Int, album: String? = null): String? {
        val apiKey = System.getenv(API_KEY_ENV)?.takeIf { it.isNotBlank() }
        return toLRC(fetchTTML(BASE_URL, APPLE_PATH, artist, title, duration, album, apiKey))
    }

    /** QQ Music karaoke timings through BetterLyrics' Portato endpoint. */
    suspend fun getPortatoLyrics(title: String, artist: String, duration: Int, album: String? = null): String? =
        toLRC(fetchTTML(PORTATO_BASE_URL, PORTATO_PATH, artist, title, duration, album))
}
