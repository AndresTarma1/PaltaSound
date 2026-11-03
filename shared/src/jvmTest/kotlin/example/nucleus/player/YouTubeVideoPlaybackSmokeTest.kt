package example.nucleus.player

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Integration smoke for the direct-stream proxy + libmpv video path.
 *
 * It opens a normal public YouTube video, resolves it with yt-dlp, wraps the
 * URLs in the local proxy and waits until mpv advances the playback clock.
 * This test intentionally runs headful on Windows; it is skipped elsewhere.
 */
class YouTubeVideoPlaybackSmokeTest {
    @Test
    fun playsPublicVideo() = runBlocking {
        if (!example.nucleus.platform.Platform.isWindows) return@runBlocking

        val streams = withTimeout(120_000) {
            YtDlpResolver.resolveVideoUrls("dQw4w9WgXcQ", maxHeight = 720)
        }
        assertTrue(streams != null, "yt-dlp returned no streams")

        val videoProxy = DirectStreamProxyServer.register(streams!!.videoUrl)
        val audioProxy = streams.audioUrl?.let(DirectStreamProxyServer::register)

        val player = MpvVideoPlayer()
        try {
            player.load(videoProxy, audioProxy)
            val position = withTimeout(45_000) {
                player.positionMs.first { it > 1_000L }
            }
            assertTrue(position > 1_000L, "mpv did not advance playback (position=$position)")
        } finally {
            player.dispose()
            DirectStreamProxyServer.unregister(videoProxy)
            audioProxy?.let(DirectStreamProxyServer::unregister)
        }
    }
}
