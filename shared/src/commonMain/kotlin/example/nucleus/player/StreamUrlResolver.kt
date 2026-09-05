package example.nucleus.player

import example.nucleus.utils.cipher.CipherDeobfuscator
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.response.PlayerResponse
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

object StreamUrlResolver {

    private val validateClient = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 5000
            requestTimeoutMillis = 8000
        }
    }
    /**
     * Resolves a stream URL for a given YouTube video format.
     *
     * @param format The streaming format to resolve a URL for.
     * @param videoId The YouTube video ID.
     * @param playerResponse The player response data.
     * @return A stream URL for the given format, or `null` if resolution fails.
     */
    suspend fun resolveUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
    ): String? {
        val formatUrl = format.url
        Napier.i("[RESOLVE_URL] videoId=$videoId itag=${format.itag} url=${formatUrl?.take(80)} sigCipher=${format.signatureCipher?.take(50)} cipher=${format.cipher?.take(50)}")
        if (formatUrl != null) {
            Napier.i("[RESOLVE_URL] URL analysis: hasN=${"n=" in formatUrl} hasPot=${"pot=" in formatUrl} urlLength=${formatUrl.length}")
        }

        if (formatUrl != null || format.signatureCipher != null || format.cipher != null) {
            val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
            if (deobfuscatedUrl != null) {
                // Keep the n-param intact; YTPlayerutils transforms it via the EJS solver.
                Napier.d("Using NewPipe-deobfuscated stream URL for $videoId itag=${format.itag} (hasN=${"n=" in deobfuscatedUrl})")
                return deobfuscatedUrl
            }
            if (formatUrl != null) {
                Napier.d("NewPipe deobfuscation failed, falling back to raw URL for $videoId itag=${format.itag} (hasN=${"n=" in formatUrl})")
                return formatUrl
            }
        }

        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Napier.d("Trying CipherDeobfuscator for $videoId itag=${format.itag}")
            val customUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customUrl != null) {
                Napier.d("Using CipherDeobfuscator stream URL for $videoId itag=${format.itag}")
                return customUrl
            }
            Napier.d("CipherDeobfuscator failed for $videoId itag=${format.itag}")
        }

        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Napier.d("Using NewPipe StreamInfo URL for $videoId itag=${format.itag}")
                return streamUrl
            }
            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second
            if (audioStream != null) {
                Napier.d("Using NewPipe StreamInfo audio fallback URL for $videoId requestedItag=${format.itag}")
                return audioStream
            }
        }

        return null
    }

    // Every googlevideo stream URL carries a throttling n-param regardless of client,
    /**
 * Determines whether to apply the n-parameter transformation to stream URLs.
 *
 * @return `true` to indicate the transformation should be applied.
 */
    fun shouldApplyNTransform(clientName: String?): Boolean = true

    /**
     * Transforms the `n` parameter in a stream URL.
     *
     * @param url The stream URL to transform.
     * @return The stream URL with the `n` parameter transformed.
     */
    suspend fun applyNTransform(url: String): String {
        return CipherDeobfuscator.transformNParamInUrl(url)
    }

    /**
     * Checks whether a stream URL is likely playable.
     *
     * @param url The stream URL to validate.
     * @return `true` if the URL appears playable or if validation fails due to a network error, `false` if the server rejects it.
     */
    suspend fun validate(url: String): Boolean {
        // Ranged GET with the same headers mpv sends, to reject URLs the CDN won't serve
        // (403/throttled) before handing them to the player.
        return try {
            val response: HttpResponse = validateClient.get(url) {
                // Range ABIERTO: es el perfil exacto que manda mpv/ffmpeg al abrir el stream.
                // Las URLs con restricciones de YouTube (rqh=1 / spc=) rechazan este perfil con
                // 403 (aceptan solo rangos acotados pequeños), así que este probe detecta el
                // problema ANTES de entregar la URL al reproductor.
                header(HttpHeaders.Range, "bytes=0-")
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                header("Referer", "https://music.youtube.com")
            }
            val ok = response.status.value == 200 || response.status.value == 206
            if (!ok) Napier.w("Stream URL not playable (status=${response.status.value})")
            ok
        } catch (e: Exception) {
            Napier.w("Stream URL validation request failed: ${e.message}; assuming playable")
            true // network hiccup shouldn't reject an otherwise-good URL
        }
    }
}
