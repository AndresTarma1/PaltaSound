package example.nucleus.player

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient as XClient
import com.metrolist.innertubex.models.response.PlayerResponse as XPlayerResponse
import com.metrolist.innertubex.sabr.ExperimentalSabrApi
import com.metrolist.innertubex.sabr.SabrAudioStream
import com.metrolist.innertubex.sabr.toSabrBootstrap
import example.nucleus.utils.cipher.PlayerJsFetcher
import example.nucleus.utils.cipher.PoTokenManager
import example.nucleus.utils.cipher.RustyPipeBotGuardSidecar
import com.metrolist.innertubex.sabr.SabrProtocolException
import com.metrolist.innertubex.sabr.SabrFailureKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

/** Prueba SABR/UDP de punta a punta: player innertubex -> bootstrap -> chunks de audio. */
class SabrProbeTest {
    @Test
    @OptIn(ExperimentalSabrApi::class)
    fun sabrProbe() = runBlocking {
        val videoId = "YckmB9-uKxw"
        val httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }
        val innerTube = InnerTube(httpClient)
        try {
            val sts = PlayerJsFetcher.getSignatureTimestamp()
            println("sts=$sts")

            // visitorData fresco de innertubex + tokens con el mismo binding.
            val vd = innerTube.fetchFreshVisitorData()
            innerTube.visitorData = vd
            println("vd=${vd?.take(12)}")

            // Tokens: player request -> sesión (standard b64); SABR bootstrap -> video (base64url).
            val pot = vd?.let { PoTokenManager.getWebClientPoToken(videoId, it) }
            println("pot playerRequest=${pot?.playerRequestPoToken?.take(12)} streaming=${pot?.streamingDataPoToken?.take(12)}")

            val response = innerTube.player(
                client = XClient.WEB_REMIX,
                videoId = videoId,
                signatureTimestamp = sts,
                poToken = pot?.playerRequestPoToken,
                requestVisitorData = vd,
            )
            val body = response.bodyAsText()
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<XPlayerResponse>(body)

            val streaming = parsed.streamingData
            println("status=${parsed.playabilityStatus?.status}")
            println("serverAbrStreamingUrl=${streaming?.serverAbrStreamingUrl?.take(90)}")
            val ustreamer = parsed.playerConfig
                ?.mediaCommonConfig
                ?.mediaUstreamerRequestConfig
                ?.videoPlaybackUstreamerConfig
            println("ustreamer=${ustreamer?.take(40)} len=${ustreamer?.length}")
            val audioFormats = streaming?.adaptiveFormats
                ?.filter { it.mimeType.startsWith("audio/") }
                ?.sortedBy { it.bitrate }
                .orEmpty()
            println("audioFormats=${audioFormats.map { "${it.itag}:${it.bitrate}" }}")

            if (streaming?.serverAbrStreamingUrl == null || ustreamer == null) {
                println("SABR-NO-CONTEXTS")
                return@runBlocking
            }

            // Formato de audio: preferimos itag 140, si no el de mayor bitrate de solo-audio.
            val audioFormat = audioFormats.lastOrNull() ?: error("no audio formats")
            println("selected itag=${audioFormat.itag} bitrate=${audioFormat.bitrate} len=${audioFormat.contentLength}")

            val bootstrap = parsed.toSabrBootstrap(
                clientId = XClient.WEB_REMIX.clientId.toInt(),
                clientVersion = XClient.WEB_REMIX.clientVersion,
                audioFormat = audioFormat,
                poToken = pot?.streamingDataPoToken,
                requestUserAgent = XClient.WEB_REMIX.userAgent,
                requestOrigin = "https://music.youtube.com",
                serverAbrStreamingUrlOverride = StreamUrlResolver.applyNTransform(
                    streaming.serverAbrStreamingUrl!!,
                ) + "&cpn=" + com.metrolist.innertubex.extraction.generateClientPlaybackNonce(),
            )
            println("bootstrap ok: durationMs=${bootstrap.durationMs} contentLen=${bootstrap.contentLengthBytes}")

            val stream = SabrAudioStream(httpClient, bootstrap)
            try {
                val chunks = stream.chunks().take(12).toList()
                var total = 0L
                chunks.forEachIndexed { i, c ->
                    total += c.data.size
                    println(
                        "CHUNK[$i] init=${c.isInitialization} seq=${c.sequenceNumber} " +
                            "range=${c.startRange}..${c.endRangeExclusive} ms=${c.startMs} bytes=${c.data.size}",
                    )
                }
                println("SABR-OK totalBytes=$total chunks=${chunks.size}")
            } catch (e: SabrProtocolException) {
                println("SABR-ATTEMPT1 kind=${e.kind} msg=${e.message}")
                if (e.kind == SabrFailureKind.ATTESTATION_REQUIRED && vd != null) {
                    println("SABR-REFRESH-ATTESTATION: re-mint fresco + player fresco + retry")
                    // Re-mint fresco: ignora snapshot para obtener integrity token nuevo.
                    fun base64UrlToStandard(urlSafe: String): String {
                        val s = urlSafe.replace('-', '+').replace('_', '/')
                        return when (s.length % 4) {
                            0 -> s
                            2 -> "$s=="
                            3 -> "$s="
                            else -> s
                        }
                    }
                    val freshSessionB64Url = RustyPipeBotGuardSidecar.mint(vd, fresh = true)
                    val freshVideoB64Url = RustyPipeBotGuardSidecar.mint(videoId, fresh = true)
                    println("fresh session=${freshSessionB64Url?.take(12)} video=${freshVideoB64Url?.take(12)}")
                    if (freshSessionB64Url != null && freshVideoB64Url != null) {
                        val freshPlayerPot = base64UrlToStandard(freshSessionB64Url)
                        val response2 = innerTube.player(
                            client = XClient.WEB_REMIX,
                            videoId = videoId,
                            signatureTimestamp = sts,
                            poToken = freshPlayerPot,
                            requestVisitorData = vd,
                        )
                        val parsed2 = Json { ignoreUnknownKeys = true }.decodeFromString<XPlayerResponse>(response2.bodyAsText())
                        val streaming2 = parsed2.streamingData
                        val audioFormats2 = streaming2?.adaptiveFormats
                            ?.filter { it.mimeType.startsWith("audio/") }
                            ?.sortedBy { it.bitrate }
                            .orEmpty()
                        val audioFormat2 = audioFormats2.lastOrNull() ?: error("no audio formats 2")
                        val bootstrap2 = parsed2.toSabrBootstrap(
                            clientId = XClient.WEB_REMIX.clientId.toInt(),
                            clientVersion = XClient.WEB_REMIX.clientVersion,
                            audioFormat = audioFormat2,
                            poToken = freshVideoB64Url,
                            requestUserAgent = XClient.WEB_REMIX.userAgent,
                            requestOrigin = "https://music.youtube.com",
                            serverAbrStreamingUrlOverride = StreamUrlResolver.applyNTransform(
                                streaming2?.serverAbrStreamingUrl ?: error("no sabr url 2"),
                            ) + "&cpn=" + com.metrolist.innertubex.extraction.generateClientPlaybackNonce(),
                        )
                        val stream2 = SabrAudioStream(httpClient, bootstrap2)
                        val chunks2 = stream2.chunks().take(12).toList()
                        var total2 = 0L
                        chunks2.forEachIndexed { i, c ->
                            total2 += c.data.size
                            println(
                                "CHUNK2[$i] init=${c.isInitialization} seq=${c.sequenceNumber} " +
                                    "range=${c.startRange}..${c.endRangeExclusive} ms=${c.startMs} bytes=${c.data.size}",
                            )
                        }
                        println("SABR-OK-RETRY totalBytes=$total2 chunks=${chunks2.size}")
                    } else {
                        println("SABR-REFRESH-FAILED mint null")
                        throw e
                    }
                } else {
                    throw e
                }
            }
        } finally {
            innerTube.close()
            httpClient.close()
        }
    }
}
