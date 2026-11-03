@file:OptIn(com.metrolist.innertubex.sabr.ExperimentalSabrApi::class)

package example.nucleus.player

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.models.YouTubeClient as XClient
import com.metrolist.innertubex.models.response.PlayerResponse as XPlayerResponse
import com.metrolist.innertubex.sabr.SabrAudioStream
import com.metrolist.innertubex.sabr.SabrChunk
import com.metrolist.innertubex.sabr.SabrVideoStream
import com.metrolist.innertubex.sabr.toSabrBootstrap
import example.nucleus.utils.cipher.RustyPipeBotGuardSidecar
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files

/**
 * Downloads a YouTube-main video and its audio through SABR/UMP.
 *
 * The visionOS client advertises `serverAbrStreamingUrl` without an `n`
 * parameter, so this path does not need QuickJS and works in GraalVM native
 * images. The PoToken is minted by the bundled rustypipe-botguard sidecar.
 *
 * Both streams are materialized as local fragmented MP4 files; mpv then plays
 * them with `audio-add`, which gives exact seeking without a range proxy.
 */
object YouTubeSabrResolver {
    private const val DOWNLOAD_TIMEOUT_MS = 10 * 60_000L

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
        }
    }
    private val innerTube by lazy { InnerTube(httpClient) }

    suspend fun download(
        videoId: String,
        onProgress: (String) -> Unit = {},
    ): SabrLocalFiles? = withContext(Dispatchers.IO) {
        try {
            withTimeout(DOWNLOAD_TIMEOUT_MS) { downloadInternal(videoId, onProgress) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("[YouTubeSABR] falló $videoId: ${e.message}")
            null
        }
    }

    private suspend fun downloadInternal(
        videoId: String,
        onProgress: (String) -> Unit,
    ): SabrLocalFiles? {
        onProgress("preparando sesión SABR")
        val visitorData = innerTube.fetchFreshVisitorData() ?: run {
            Napier.w("[YouTubeSABR] sin visitorData para $videoId")
            return null
        }
        innerTube.visitorData = visitorData
        val poToken = RustyPipeBotGuardSidecar.mint(videoId, fresh = true) ?: run {
            Napier.w("[YouTubeSABR] sin PoToken para $videoId")
            return null
        }
        val client = XClient.VISIONOS_SABR
        val response = innerTube.player(
            client = client,
            videoId = videoId,
            signatureTimestamp = null,
            poToken = poToken,
            requestVisitorData = visitorData,
        )
        val parsed = json.decodeFromString<XPlayerResponse>(response.bodyAsText())
        if (parsed.playabilityStatus.status != "OK") {
            Napier.w(
                "[YouTubeSABR] $videoId playability=${parsed.playabilityStatus.status} " +
                    "reason=${parsed.playabilityStatus.reason}",
            )
            return null
        }
        val streaming = parsed.streamingData ?: run {
            Napier.w("[YouTubeSABR] $videoId sin streamingData")
            return null
        }
        val video = chooseVideo(streaming) ?: run {
            Napier.w("[YouTubeSABR] $videoId sin formato de vídeo SABR")
            return null
        }
        val audio = chooseAudio(streaming) ?: run {
            Napier.w("[YouTubeSABR] $videoId sin formato de audio SABR")
            return null
        }
        val sabrUrl = streaming.serverAbrStreamingUrl ?: run {
            Napier.w("[YouTubeSABR] $videoId sin serverAbrStreamingUrl")
            return null
        }
        Napier.i(
            "[YouTubeSABR] $videoId video=${video.itag}/${video.height}p " +
                "audio=${audio.itag} sabrUrlLen=${sabrUrl.length}",
        )

        val bootstrap = try {
            parsed.toSabrBootstrap(
                clientId = client.clientId.toInt(),
                clientVersion = client.clientVersion,
                audioFormat = audio,
                poToken = poToken,
                requestUserAgent = client.userAgent,
                requestOrigin = "https://www.youtube.com",
                serverAbrStreamingUrlOverride = sabrUrl +
                    "&cpn=" + generateClientPlaybackNonce(),
                videoFormat = video,
            )
        } catch (e: Exception) {
            Napier.w("[YouTubeSABR] bootstrap falló para $videoId: ${e.message}")
            return null
        }

        val dir = Files.createTempDirectory("paltasound-yt-sabr-${videoId.take(11)}-").toFile()
        val videoFile = File(dir, "video.mp4")
        val audioFile = File(dir, "audio.m4a")
        try {
            // Sequential on purpose: the app runs with a small heap, and two
            // concurrent UMP streams plus their protobuf buffers can push it
            // into GC thrashing (UI freezes without an OOM).
            onProgress("descargando vídeo ${video.height}p (${bootstrap.durationMs / 1000}s)")
            downloadChunks(
                videoFile,
                SabrVideoStream(httpClient, bootstrap).chunks(),
                "vídeo",
                bootstrap.selectedVideoContentLengthBytes,
            )
            onProgress("descargando audio")
            downloadChunks(
                audioFile,
                SabrAudioStream(httpClient, bootstrap).chunks(),
                "audio",
                bootstrap.contentLengthBytes,
            )
        } catch (t: Throwable) {
            runCatching { dir.deleteRecursively() }
            throw t
        }
        onProgress("descarga SABR completa")
        Napier.i("[YouTubeSABR] $videoId listo (${videoFile.length()} + ${audioFile.length()} bytes)")
        return SabrLocalFiles(videoFile, audioFile, bootstrap.durationMs)
    }

    private suspend fun downloadChunks(
        file: File,
        chunks: Flow<SabrChunk>,
        label: String,
        expectedBytes: Long?,
    ) {
        var written = 0L
        try {
            file.outputStream().buffered(1 shl 20).use { output ->
                chunks.collect { chunk ->
                    val start = chunk.startRange
                    if (start > written) {
                        error(
                            "SABR $label: hueco de ${start - written} bytes " +
                                "(esperado $written, llegó $start)",
                        )
                    }
                    val offset = (written - start).toInt().coerceAtLeast(0)
                    if (offset < chunk.data.size) {
                        var count = chunk.data.size - offset
                        if (expectedBytes != null) {
                            count = minOf(count, (expectedBytes - written).coerceAtLeast(0L).toInt())
                        }
                        if (count > 0) {
                            output.write(chunk.data, offset, count)
                            written += count
                        }
                    }
                    // innertubex keeps polling after the last segment and
                    // eventually throws "no progress". The content length is
                    // authoritative, so stop as soon as it is reached.
                    if (expectedBytes != null && written >= expectedBytes) {
                        throw DownloadComplete
                    }
                }
            }
        } catch (_: DownloadComplete) {
            // Expected end of the UMP stream.
        }
        if (written <= 0L) error("SABR $label: no se recibieron datos")
        if (expectedBytes != null && written < expectedBytes) {
            Napier.w("[YouTubeSABR] $label incompleto: $written/$expectedBytes bytes")
        }
        Napier.i("[YouTubeSABR] $label listo: ${written / 1024 / 1024} MB")
    }

    private object DownloadComplete : RuntimeException() {
        private fun readResolve(): Any = DownloadComplete
        override fun fillInStackTrace(): Throwable = this
    }

    private fun chooseVideo(
        streaming: XPlayerResponse.StreamingData,
    ): XPlayerResponse.StreamingData.Format? {
        val candidates = streaming.adaptiveFormats
            .filter { it.mimeType.startsWith("video/mp4") && it.height != null }
        val avc = candidates.filter {
            it.mimeType.contains("avc1", ignoreCase = true) && (it.height ?: 0) <= 1080
        }
        return (avc.ifEmpty { candidates }).maxWithOrNull(
            compareBy({ it.height ?: 0 }, { it.bitrate }),
        )
    }

    private fun chooseAudio(
        streaming: XPlayerResponse.StreamingData,
    ): XPlayerResponse.StreamingData.Format? {
        val audios = streaming.adaptiveFormats.filter { it.mimeType.startsWith("audio/") }
        return audios.firstOrNull { it.itag == 140 }
            ?: audios.filter { it.mimeType.startsWith("audio/mp4") }.maxByOrNull { it.bitrate }
            ?: audios.maxByOrNull { it.bitrate }
    }
}

/**
 * Local fragmented MP4 files produced by [YouTubeSabrResolver]. They live in a
 * per-video temp directory and are deleted when playback moves on.
 */
data class SabrLocalFiles(
    val videoFile: File,
    val audioFile: File,
    val durationMs: Long,
) {
    fun delete() {
        listOf(videoFile, audioFile).forEach { file ->
            if (!runCatching { file.delete() }.getOrDefault(false)) {
                file.deleteOnExit()
            }
        }
        runCatching { videoFile.parentFile?.delete() }
    }
}
