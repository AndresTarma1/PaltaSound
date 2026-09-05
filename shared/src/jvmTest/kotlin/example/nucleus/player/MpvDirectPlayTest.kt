package example.nucleus.player

import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class MpvDirectPlayTest {
    @Test
    fun rapidDoubleOpenUri() = runBlocking {
        val data = YTPlayerutils.playerResponseForPlayback("dQw4w9WgXcQ").getOrThrow()
        val url = data.streamUrl
        println("URL len=${url.length}")
        val player = MpvAudioPlayer()
        player.init()
        // Simula doble play() rÃ¡pido como en la app (resolve -> startUrl dos veces seguidas)
        player.openUri(url)
        kotlinx.coroutines.delay(300)
        player.openUri(url)
        val started = player.awaitPlaybackStarted(10000)
        println("DOUBLE-OPEN RESULT started=$started")
    }

    @Test
    fun mpvPotVariants() = runBlocking {
        val data = YTPlayerutils.playerResponseForPlayback("hAYUQ1ltJj0").getOrThrow()
        val ourUrl = data.streamUrl
        val ourN = Regex("[?&]n=([^&]*)").find(ourUrl)?.groupValues?.get(1)
        println("OUR url len=${ourUrl.length} ourn=$ourN")
        val ytdlpUrl = YtDlpResolver.resolveAudioUrl("hAYUQ1ltJj0") ?: error("ytdlp failed")
        val ytdlpN = Regex("[?&]n=([^&]*)").find(ytdlpUrl)?.groupValues?.get(1)
        println("YTDLP url len=${ytdlpUrl.length} ytdlpn=$ytdlpN")
        println("ytdlpUrl => ${playOnce(ytdlpUrl)}")
        // Nuestra URL pero con el n bueno de yt-dlp (n no estÃ¡ firmado: sparams/lsparams no lo incluyen)
        val ourUrlGoodN = ourUrl.replace(Regex("([?&])n=[^&]*"), "$1n=$ytdlpN")
        println("ourUrl+goodN => ${playOnce(ourUrlGoodN)}")
    }

    @Test
    fun probe403fFreshMidRange() = runBlocking {
        val sts = example.nucleus.utils.cipher.PlayerJsFetcher.getSignatureTimestamp()
        if (com.metrolist.innertube.YouTube.visitorData == null) {
            com.metrolist.innertube.YouTube.visitorData().onSuccess { com.metrolist.innertube.YouTube.visitorData = it }
        }
        val vd = com.metrolist.innertube.YouTube.visitorData!!
        suspend fun resolveUrl(videoId: String): String {
            val pot = example.nucleus.utils.cipher.PoTokenManager.getWebClientPoToken(videoId, vd)
            val p = com.metrolist.innertube.YouTube.player(
                videoId, null, com.metrolist.innertube.models.YouTubeClient.WEB_REMIX, sts,
                pot?.playerRequestPoToken,
            ).getOrThrow()
            val fmt = FormatSelector.findFormat(p, example.nucleus.data.repository.AudioQuality.NORMAL)!!
            val raw = StreamUrlResolver.resolveUrl(fmt, videoId, p)!!
            return StreamUrlResolver.applyNTransform(raw) + "&pot=" + (pot!!.streamingDataPoToken.replace("=", "%3D"))
        }
        fun probe(name: String, url: String, range: String) {
            try {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.setRequestProperty("Range", range)
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                c.setRequestProperty("Referer", "https://music.youtube.com")
                c.connectTimeout = 8000; c.readTimeout = 8000
                val code = c.responseCode
                val hdr = c.getHeaderField("Content-Range") ?: ""
                (if (code < 400) c.inputStream else c.errorStream)?.close(); c.disconnect()
                println("PROBE $name range=$range => $code [$hdr]")
            } catch (e: Exception) { println("PROBE $name => EX ${e.message}") }
            Thread.sleep(300)
        }
        // dQw4: estado HOY
        val dqw4 = resolveUrl("dQw4w9WgXcQ")
        probe("dqw4-open", dqw4, "bytes=0-")
        probe("dqw4-1m", dqw4, "bytes=0-1048575")
        // YckmB9: URL FRESCA, primer toque con rango intermedio
        val yckmFresh = resolveUrl("YckmB9-uKxw")
        probe("yckm-fresh-mid-1m", yckmFresh, "bytes=1048576-2097151")
        probe("yckm-fresh-mid-512k", yckmFresh, "bytes=1048576-1572863")
        probe("yckm-fresh-open", yckmFresh, "bytes=0-")
        // segunda URL fresca de YckmB9: rango intermedio como PRIMER toque
        val yckmFresh2 = resolveUrl("YckmB9-uKxw")
        probe("yckm-fresh2-mid-512k", yckmFresh2, "bytes=1048576-1572863")
    }

    private fun playOnce(url: String): String {
        val h = MpvLib.mpv_create() ?: return "no-handle"
        try {
            MpvLib.mpv_set_property_string(h, "ytdl", "no")
            MpvLib.mpv_set_property_string(h, "video", "no")
            MpvLib.mpv_set_property_string(h, "ao", "null")
            MpvLib.mpv_set_property_string(h, "cache", "yes")
            MpvLib.mpv_set_property_string(h, "user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
            MpvLib.mpv_set_property_string(h, "referrer", "https://music.youtube.com")
            MpvLib.mpv_set_property_string(h, "http-header-fields", "Origin: https://music.youtube.com,Accept: */*")
            MpvLib.mpv_initialize(h)
            MpvLib.mpv_command(h, arrayOf("loadfile", url, "replace", null))
            MpvLib.mpv_set_property_string(h, "pause", "no")
            val deadline = System.currentTimeMillis() + 15000
            while (System.currentTimeMillis() < deadline) {
                val evPtr = MpvLib.mpv_wait_event(h, 1.0) ?: continue
                val ev = evPtr.reinterpret(64)
                when (ev.get(ValueLayout.JAVA_INT, 0L)) {
                    21 -> return "PLAYING"
                    7 -> {
                        val dataPtr = ev.get(ValueLayout.ADDRESS, 16L)
                        val reason = if (dataPtr.address() != 0L) dataPtr.reinterpret(16).get(ValueLayout.JAVA_INT, 0L) else -1
                        return "END_FILE reason=$reason"
                    }
                    2 -> {
                        val dataPtr = ev.get(ValueLayout.ADDRESS, 16L)
                        if (dataPtr.address() != 0L) {
                            val msg = dataPtr.reinterpret(24)
                            val textPtr = msg.get(ValueLayout.ADDRESS, 16L)
                            val text = if (textPtr.address() != 0L) textPtr.reinterpret(4096).getString(0L) else ""
                            if (text.isNotBlank()) println("MPVLOG $text")
                        }
                    }
                }
            }
            return "TIMEOUT"
        } finally {
            MpvLib.mpv_terminate_destroy(h)
        }
    }

    @Test
    fun mpvOpensWebRemixUrl() = runBlocking {
        val data = YTPlayerutils.playerResponseForPlayback("dQw4w9WgXcQ").getOrThrow()
        val url = data.streamUrl
        println("URL len=${url.length} itag=${data.format.itag} rqh=${url.contains("rqh=1")} spc=${url.contains("spc=")} c=${Regex("[?&]c=([^&]*)").find(url)?.groupValues?.get(1)}")
        val h = MpvLib.mpv_create() ?: error("no handle")
        try {
            MpvLib.mpv_set_property_string(h, "ytdl", "no")
            MpvLib.mpv_set_property_string(h, "video", "no")
            MpvLib.mpv_set_property_string(h, "audio-display", "no")
            MpvLib.mpv_set_property_string(h, "audio-channels", "stereo")
            MpvLib.mpv_set_property_string(h, "ao", "null")
            MpvLib.mpv_set_property_string(h, "initial-audio-sync", "no")
            MpvLib.mpv_set_property_string(h, "hr-seek", "no")
            MpvLib.mpv_set_property_string(h, "cache", "yes")
            MpvLib.mpv_set_property_string(h, "cache-secs", "45")
            MpvLib.mpv_set_property_string(h, "demuxer-max-bytes", "52428800")
            MpvLib.mpv_set_property_string(h, "demuxer-max-back-bytes", "10485760")
            MpvLib.mpv_set_property_string(h, "cache-pause", "yes")
            MpvLib.mpv_set_property_string(h, "cache-pause-initial", "yes")
            MpvLib.mpv_set_property_string(h, "cache-pause-wait", "1.0")
            MpvLib.mpv_set_property_string(h, "user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
            MpvLib.mpv_set_property_string(h, "referrer", "https://music.youtube.com")
            MpvLib.mpv_set_property_string(h, "http-header-fields", "Origin: https://music.youtube.com,Accept: */*")
            MpvLib.mpv_initialize(h)
            MpvLib.mpv_request_log_messages(h, "debug")
            MpvLib.mpv_command(h, arrayOf("loadfile", url, "replace", null))
            val deadline = System.currentTimeMillis() + 25000
            var restarts = 0
            while (System.currentTimeMillis() < deadline && restarts == 0) {
                val evPtr = MpvLib.mpv_wait_event(h, 1.0) ?: continue
                val ev = evPtr.reinterpret(64)
                when (ev.get(ValueLayout.JAVA_INT, 0L)) {
                    21 -> {
                        restarts++
                        println("EVENT PLAYBACK_RESTART")
                    }
                    7 -> {
                        val dataPtr = ev.get(ValueLayout.ADDRESS, 16L)
                        val reason = if (dataPtr.address() != 0L) dataPtr.reinterpret(16).get(ValueLayout.JAVA_INT, 0L) else -1
                        println("EVENT END_FILE reason=$reason")
                        break
                    }
                    2 -> {
                        val dataPtr = ev.get(ValueLayout.ADDRESS, 16L)
                        if (dataPtr.address() != 0L) {
                            val msg = dataPtr.reinterpret(32)
                            val levelPtr = msg.get(ValueLayout.ADDRESS, 8L)
                            val textPtr = msg.get(ValueLayout.ADDRESS, 16L)
                            val level = if (levelPtr.address() != 0L) levelPtr.reinterpret(64).getString(0L) else "?"
                            val text = if (textPtr.address() != 0L) textPtr.reinterpret(4096).getString(0L) else ""
                            if (level == "error" || level == "warn" || level == "fatal") {
                                println("MPVLOG [$level] $text")
                            }
                        }
                    }
                }
            }
            println("RESULT restarts=$restarts")
        } finally {
            MpvLib.mpv_terminate_destroy(h)
        }
    }
}
