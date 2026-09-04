package example.nucleus.player

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class WebRemixUrlCheckTest {
    private val http = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 8000
            requestTimeoutMillis = 15000
        }
    }

    @Test
    fun webRemixUrlIsCdnValid() = runBlocking {
        checkVideo("dQw4w9WgXcQ")
    }

    @Test
    fun webRemixUrlFailingVideo() = runBlocking {
        checkVideo("hAYUQ1ltJj0")
    }

    private suspend fun checkVideo(videoId: String) {
        val data = YTPlayerutils.playerResponseForPlayback(videoId).getOrThrow()
        val url = data.streamUrl
        println("RESOLVED $videoId len=${url.length} hasPot=${"pot=" in url} hasN=${"n=" in url} itag=${data.format.itag}")
        suspend fun fullStatus(u: String, label: String) {
            http.prepareGet(u) {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                header("Referer", "https://music.youtube.com")
                header("Origin", "https://music.youtube.com")
                header(HttpHeaders.Accept, "*/*")
            }.execute { response ->
                println("$label status=${response.status.value}")
            }
        }
        // Variante B: sin pot
        val noPot = url.replace(Regex("[?&]pot=[^&]*"), "")
        println("VARIANT noPot len=${noPot.length}")
        fullStatus(noPot, "FULL-noPot")
        // Variante C: original con pot
        fullStatus(url, "FULL-withPot")
    }
}
