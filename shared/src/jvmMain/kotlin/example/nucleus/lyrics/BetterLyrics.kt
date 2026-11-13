package example.nucleus.lyrics

import example.nucleus.network.AppHttpClient
import io.github.aakira.napier.Napier
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class TTMLResponse(val ttml: String? = null)

/**
 * BetterLyrics API client (word-synced TTML lyrics) — port of Metrolist's `betterlyrics/BetterLyrics`.
 * Fetches TTML from lyrics-api.boidu.dev and converts it to enriched LRC via [TTMLParser].
 *
 * Dos backends del mismo host:
 *  - `/getLyrics`     — Apple Music (TTML por sílabas). Requiere la X-API-Key privada
 *    de la extensión Better Lyrics para consultas no cacheadas, así que queda
 *    desactivado por defecto. Se puede habilitar con la variable de entorno
 *    `PALTASOUND_BETTERLYRICS_API_KEY`.
 *  - `/qq/getLyrics`  — Portato / QQ Music (karaoke con tiempos por palabra), sin key.
 */
object BetterLyrics {
    private const val BASE_URL = "https://lyrics-api.boidu.dev"
    private const val APPLE_PATH = "/getLyrics"
    private const val PORTATO_PATH = "/qq/getLyrics"
    const val API_KEY_ENV = "PALTASOUND_BETTERLYRICS_API_KEY"
    private val client = AppHttpClient.client

    private suspend fun fetchTTML(
        path: String,
        artist: String,
        title: String,
        duration: Int,
        album: String?,
        apiKey: String? = null,
    ): String? = runCatching {
        val response = client.get("$BASE_URL$path") {
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
                // La API publica exige X-API-Key para las consultas que no esten en
                // su cache; la key es privada de la extension Better Lyrics. Es un
                // estado esperado, no un fallo: se registra solo en debug.
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                    Napier.d("[BetterLyrics] ${response.status} (uncached queries require an X-API-Key) — skipping")
                else -> Napier.w("[BetterLyrics] API status ${response.status}")
            }
            null
        }
    }.getOrElse {
        Napier.e("[BetterLyrics] fetchTTML failed", it)
        null
    }

    private fun toLRC(ttml: String?): String? {
        if (ttml == null) return null
        val parsed = TTMLParser.parseTTML(ttml)
        if (parsed.isEmpty()) return null
        return TTMLParser.toLRC(parsed)
    }

    /**
     * Apple Music endpoint. Devuelve null sin hacer red si no hay
     * `PALTASOUND_BETTERLYRICS_API_KEY`: así no se repite el 401 en cada canción.
     */
    suspend fun getLyrics(title: String, artist: String, duration: Int, album: String? = null): String? {
        val apiKey = System.getenv(API_KEY_ENV)?.takeIf { it.isNotBlank() } ?: return null
        return toLRC(fetchTTML(APPLE_PATH, artist, title, duration, album, apiKey))
    }

    /** QQ Music karaoke timings through BetterLyrics' Portato endpoint. */
    suspend fun getPortatoLyrics(title: String, artist: String, duration: Int, album: String? = null): String? =
        toLRC(fetchTTML(PORTATO_PATH, artist, title, duration, album))
}
