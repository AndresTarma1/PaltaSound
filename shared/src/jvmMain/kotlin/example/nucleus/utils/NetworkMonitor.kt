package example.nucleus.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Fast connectivity probe used to tell "no internet" apart from a per-song playback failure
 * (age-gated video, needs yt-dlp, etc). A raw TCP connect is quicker and more decisive than
 * waiting for the full resolve/yt-dlp cascade to time out on its own.
 */
actual object NetworkMonitor {
    /**
     * True during an AOT cache training session (JDK 25 `-XX:AOTMode=record` sets
     * `nucleus.aot.mode=TRAINING`). Training must not touch the network: every remote response
     * is a different class set on every run, and the training dataset is what decides how big
     * the `ro`/`rw` regions of `app.aot` end up being.
     */
    private val isAotTraining: Boolean =
        System.getProperty("nucleus.aot.mode")?.equals("TRAINING", ignoreCase = true) == true

    actual suspend fun isOnline(timeoutMs: Int): Boolean = withContext(Dispatchers.IO) {
        if (isAotTraining) return@withContext false
        runCatching {
            Socket().use { it.connect(InetSocketAddress("music.youtube.com", 443), timeoutMs) }
            true
        }.getOrDefault(false)
    }
}
