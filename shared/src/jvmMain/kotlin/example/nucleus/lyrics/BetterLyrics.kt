package example.nucleus.lyrics

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TTMLResponse(val ttml: String? = null)

/**
 * BetterLyrics API client (word-synced TTML lyrics) — port of Metrolist's `betterlyrics/BetterLyrics`.
 * Fetches TTML from lyrics-api.boidu.dev and converts it to enriched LRC via [TTMLParser].
 */
object BetterLyrics {
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest {
                url("https://lyrics-api.boidu.dev")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                header("Accept", "application/json")
            }
            expectSuccess = false
        }
    }

    private suspend fun fetchTTML(artist: String, title: String, duration: Int, album: String?): String? =
        runCatching {
            val response = client.get("/getLyrics") {
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
