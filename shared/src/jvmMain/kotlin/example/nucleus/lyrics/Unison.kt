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
data class UnisonEnvelope(val data: UnisonData? = null)

/**
 * Letra de la base comunitaria de Better Lyrics.
 *
 * `format` decide el parser: TTML se convierte a LRC enriquecido con
 * [TTMLParser]; `lrc`/`plain` se devuelven tal cual y el llamador decide si es
 * sincronizada (detecta timestamps) o texto plano. La consulta es por videoId,
 * así que no hay riesgo de emparejar la canción equivocada.
 */
@Serializable
data class UnisonData(
    val id: Long = 0,
    val videoId: String = "",
    val song: String = "",
    val artist: String = "",
    val lyrics: String? = null,
    val format: String? = null,
    val syncType: String? = null,
    val effectiveScore: Double = 0.0,
    val voteCount: Int = 0,
)

object Unison {
    private const val BASE_URL = "https://unison.betterlyrics.org"
    private val client = AppHttpClient.client

    /**
     * Devuelve la letra cruda (LRC enriquecido si el origen era TTML) o null si
     * el video no tiene entrada en Unison.
     */
    suspend fun getLyrics(videoId: String): String? {
        if (videoId.isBlank()) return null
        return runCatching {
            val response = client.get("$BASE_URL/lyrics") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                header("Accept", "application/json")
                parameter("v", videoId)
            }
            // 404 es lo normal: la canción aún no está en la base comunitaria.
            if (response.status == HttpStatusCode.NotFound) return null
            if (!response.status.isSuccess()) {
                Napier.w("[Unison] API status ${response.status}")
                return null
            }
            val data = response.body<UnisonEnvelope>().data ?: return null
            val raw = data.lyrics?.trim().orEmpty()
            if (raw.isEmpty()) return null

            when (data.format?.lowercase()) {
                "ttml" -> {
                    val parsed = TTMLParser.parseTTML(raw)
                    if (parsed.isEmpty()) null else TTMLParser.toLRC(parsed)
                }
                // "lrc" y "plain" se devuelven tal cual: PlayerViewModel ya
                // distingue sincronizada de texto plano por los timestamps.
                else -> raw
            }
        }.getOrElse {
            Napier.e("[Unison] fetch failed", it)
            null
        }
    }
}
