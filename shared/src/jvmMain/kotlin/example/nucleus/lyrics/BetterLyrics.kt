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
 */
object BetterLyrics {
    private const val BASE_URL = "https://lyrics-api.boidu.dev"
    private val client = AppHttpClient.client

    private suspend fun fetchTTML(artist: String, title: String, duration: Int, album: String?): String? =
        runCatching {
            val response = client.get("$BASE_URL/getLyrics") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                header("Accept", "application/json")
                parameter("s", title)
                parameter("a", artist)
                if (duration > 0) parameter("d", duration)
                if (!album.isNullOrBlank()) parameter("al", album)
            }
            if (response.status.isSuccess() || response.status == HttpStatusCode.OK) {
                response.body<TTMLResponse>().ttml?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                when (response.status) {
                    // La API publica ahora exige X-API-Key para las consultas que no esten en
                    // su cache; la key es privada de la extension Better Lyrics. No es un fallo
                    // del cliente: esa letra simplemente no esta disponible por esta via.
                    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                        Napier.w("[BetterLyrics] ${response.status} (uncached queries require an X-API-Key) — skipping")
                    else -> Napier.w("[BetterLyrics] API status ${response.status}")
                }
                null
            }
        }.getOrElse {
            Napier.e("[BetterLyrics] fetchTTML failed", it)
            null
        }

    /** Returns enriched LRC (line + optional word timings), or null if unavailable. */
    suspend fun getLyrics(title: String, artist: String, duration: Int, album: String? = null): String? {
        val ttml = fetchTTML(artist, title, duration, album) ?: return null
        val parsed = TTMLParser.parseTTML(ttml)
        if (parsed.isEmpty()) return null
        return TTMLParser.toLRC(parsed)
    }
}
