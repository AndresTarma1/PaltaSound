package example.nucleus.player

import example.youtube.model.YouTubeVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the second libmpv instance used for YouTube main video.
 *
 * Music keeps its existing audio-only MpvAudioPlayer. A single video player
 * lives for the whole app so switching between watch screens does not tear
 * down and recreate libmpv.
 */
class YouTubePlayerService {
    private val player = MpvVideoPlayer()
    private val surfaceHwnd = MutableStateFlow(0L)
    private val surfaceToken = AtomicLong(0L)
    private val nextSurfaceToken = AtomicLong(0L)
    private val _currentVideo = MutableStateFlow<YouTubeVideo?>(null)
    private val _minimized = MutableStateFlow(false)
    private val _fullscreen = MutableStateFlow(false)
    private var currentProxyUrls: List<String> = emptyList()
    private var currentSabrFiles: SabrLocalFiles? = null

    val currentVideo: StateFlow<YouTubeVideo?> = _currentVideo
    val minimized: StateFlow<Boolean> = _minimized
    val fullscreen: StateFlow<Boolean> = _fullscreen
    val textureInfo: StateFlow<MpvTextureInfo?> get() = player.textureInfo
    val textureAvailable: Boolean get() = player.textureAvailable
    private val _textureFallback = MutableStateFlow(false)
    val textureFallback: StateFlow<Boolean> = _textureFallback

    fun addFrameListener(listener: () -> Unit): java.lang.AutoCloseable =
        player.addFrameListener(listener)

    val isPlaying: StateFlow<Boolean> = player.isPlaying
    val isBuffering: StateFlow<Boolean> = player.isBuffering
    val positionMs: StateFlow<Long> = player.positionMs
    val durationMs: StateFlow<Long> = player.durationMs
    val error: StateFlow<String?> = player.error
    val videoSize: StateFlow<Pair<Int, Int>> = player.videoSize

    /** Reserves a monotonically increasing id for a new video surface. */
    fun newSurfaceToken(): Long = nextSurfaceToken.incrementAndGet()

    /**
     * Makes [token] the active surface, unless a newer surface already claimed
     * it. Tokens only grow, so a stale Watch screen disposing/repositioning
     * after the mini player attached can never steal the shared HWND again.
     */
    fun attachSurface(hwnd: Long, token: Long): Boolean {
        if (hwnd == 0L || token == 0L) return false
        while (true) {
            val current = surfaceToken.get()
            if (token < current) return false
            if (surfaceToken.compareAndSet(current, token)) break
        }
        surfaceHwnd.value = hwnd
        player.attach(hwnd)
        return true
    }

    /** True while [token] is the surface allowed to move/show the shared HWND. */
    fun ownsSurface(token: Long): Boolean =
        token != 0L && surfaceToken.get() == token

    fun setCurrentVideo(video: YouTubeVideo?) {
        _currentVideo.value = video
    }

    fun minimize() {
        if (_currentVideo.value != null) {
            _fullscreen.value = false
            _minimized.value = true
        }
    }

    fun setFullscreen(value: Boolean) {
        _fullscreen.value = value
    }

    fun restoreFromMini() {
        _minimized.value = false
    }

    fun closeMini() {
        stop()
    }

    /**
     * Releases [token] only if it still owns the surface. An old Watch screen
     * disposing after the mini player attached must not hide the mini's host.
     *
     * @return true when [token] was the active surface and has been cleared.
     */
    fun detachSurface(token: Long): Boolean {
        if (token == 0L) return false
        if (!surfaceToken.compareAndSet(token, 0L)) return false
        surfaceHwnd.value = 0L
        return true
    }

    private suspend fun awaitSurface(): Long {
        // TextureView path: no HWND is needed. Init the D3D11 + render
        // pipeline off the UI thread and let the composable attach later.
        val textureReady = withContext(Dispatchers.IO) {
            runCatching { player.tryInitTexture() }.getOrDefault(false)
        }
        if (textureReady) {
            _textureFallback.value = false
            return 0L
        }
        _textureFallback.value = true
        return withTimeoutOrNull(5_000) { surfaceHwnd.first { it != 0L } }
            ?: error("La superficie de vídeo no está disponible")
    }

    suspend fun playManifest(manifestUrl: String, cleanupUrls: List<String>) {
        val hwnd = awaitSurface()
        withContext(Dispatchers.IO) {
            player.attach(hwnd)
            currentProxyUrls.forEach(DirectStreamProxyServer::unregister)
            currentProxyUrls = cleanupUrls + manifestUrl
            currentSabrFiles?.delete()
            currentSabrFiles = null
            player.load(manifestUrl)
        }
    }

    /**
     * Plays a fully downloaded SABR session. Both files are local, so seeking
     * is handled by mpv without a range proxy.
     */
    suspend fun playSabrFiles(files: SabrLocalFiles) {
        try {
            val hwnd = awaitSurface()
            withContext(Dispatchers.IO) {
                player.attach(hwnd)
                currentProxyUrls.forEach(DirectStreamProxyServer::unregister)
                currentProxyUrls = emptyList()
                currentSabrFiles?.delete()
                currentSabrFiles = files
                // mpv treats file:/C:/... as a relative path on Windows; an
                // absolute path is passed as a single argv entry, spaces included.
                player.load(
                    files.videoFile.absolutePath,
                    files.audioFile.absolutePath,
                )
            }
        } catch (t: Throwable) {
            files.delete()
            throw t
        }
    }

    suspend fun playVideo(video: YouTubeVideo, streams: YtDlpResolver.ResolvedVideoStreams) {
        // Do not initialize libmpv before the HWND exists: `wid` is an option
        // applied before mpv_initialize, so an early init would open a separate
        // mpv window instead of embedding the video.
        val hwnd = awaitSurface()
        withContext(Dispatchers.IO) {
            player.attach(hwnd)
            currentProxyUrls.forEach(DirectStreamProxyServer::unregister)
            currentProxyUrls = buildList {
                add(DirectStreamProxyServer.register(streams.videoUrl))
                streams.audioUrl?.let { add(DirectStreamProxyServer.register(it)) }
            }
            currentSabrFiles?.delete()
            currentSabrFiles = null
            player.load(currentProxyUrls[0], currentProxyUrls.getOrNull(1))
        }
    }

    fun togglePlayPause() = player.togglePlayPause()
    fun pause() = player.pause()
    fun play() = player.play()
    fun seekTo(millis: Long) = player.seekToMs(millis)
    fun setVolume(value: Int) = player.setVolume(value)

    fun stop() {
        _minimized.value = false
        _fullscreen.value = false
        _currentVideo.value = null
        player.stop()
        currentProxyUrls.forEach(DirectStreamProxyServer::unregister)
        currentProxyUrls = emptyList()
        currentSabrFiles?.delete()
        currentSabrFiles = null
    }

    fun dispose() {
        stop()
        player.dispose()
    }
}
