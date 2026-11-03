package example.nucleus.player

import example.nucleus.platform.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/** Shared texture published by mpv's D3D11 renderer for TextureView. */
data class MpvTextureInfo(
    val sharedHandle: Long,
    val width: Int,
    val height: Int,
)

/**
 * Video-only libmpv instance.
 *
 * On Windows the preferred output is a shared D3D11 texture rendered by mpv's
 * D3D11 render API and composited by Nucleus' `TextureView` (no child HWND).
 * The old `wid`/HWND path remains as a fallback for machines where D3D11 or
 * the render API is unavailable.
 */
class MpvVideoPlayer {
    private val log = Logger.getLogger("MpvVideoPlayer")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var handle: MemorySegment? = null
    private var eventThread: Thread? = null
    private var tickJob: Job? = null
    @Volatile private var eventLoopRunning = false
    @Volatile private var wantsPlay = false
    @Volatile private var attachedHwnd: Long = 0L
    @Volatile private var failed = false
    @Volatile private var surfaceDirty = false
    @Volatile private var texturePipelineUnavailable = false

    private var d3d11Output: MpvD3D11Output? = null
    private var renderContext: MemorySegment? = null
    private var renderThread: Thread? = null
    @Volatile private var renderRunning = false
    private val frameListeners = CopyOnWriteArrayList<() -> Unit>()

    private val _textureInfo = MutableStateFlow<MpvTextureInfo?>(null)
    val textureInfo: StateFlow<MpvTextureInfo?> = _textureInfo.asStateFlow()
    val textureAvailable: Boolean get() = _textureInfo.value != null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _ended = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ended: SharedFlow<Unit> = _ended.asSharedFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _videoSize = MutableStateFlow(0 to 0)
    val videoSize: StateFlow<Pair<Int, Int>> = _videoSize.asStateFlow()

    private var volume = 100
    private var speed = 1.0f
    @Volatile private var pendingAudioUrl: String? = null

    private val initializing = AtomicBoolean(false)

    /**
     * Attaches the video output to a native Windows HWND.
     *
     * `wid` is only read by mpv during init, so a different HWND forces a
     * rebuild on the next [load]. The common path reuses the same host HWND
     * for the whole app lifetime and never gets here.
     */
    fun attach(hwnd: Long) {
        if (hwnd == 0L || textureAvailable) return
        if (attachedHwnd != 0L && attachedHwnd != hwnd) {
            log.info("attach: HWND cambió de $attachedHwnd a $hwnd; se recreará mpv")
            stop()
            surfaceDirty = true
        }
        attachedHwnd = hwnd
    }

    /**
     * Tries the TextureView path before any HWND exists. Returns true when the
     * D3D11 shared texture and the mpv D3D11 render context are ready.
     */
    fun tryInitTexture(): Boolean {
        if (handle != null) return textureAvailable
        if (!initializing.compareAndSet(false, true)) return textureAvailable
        return try {
            initializeTexturePipeline()
        } finally {
            initializing.set(false)
        }
    }

    fun init() {
        if (handle != null) {
            if (!surfaceDirty) return
            log.info("init: recreando libmpv")
            teardown()
        }
        if (!initializing.compareAndSet(false, true)) return
        try {
            if (initializeTexturePipeline()) return
            initializeFallback()
        } catch (t: Throwable) {
            log.severe("MpvVideoPlayer init failed: ${t.message}")
            _error.value = t.message ?: "No se pudo iniciar libmpv para vídeo"
            handle = null
            d3d11Output?.close()
            d3d11Output = null
            _textureInfo.value = null
            throw t
        } finally {
            initializing.set(false)
        }
    }

    private fun configureCommon(h: MemorySegment) {
        MpvLib.mpv_set_option_string(h, "ytdl", "no")
        // googlevideo rejects libmpv's default User-Agent with HTTP 403.
        MpvLib.mpv_set_option_string(
            h,
            "user-agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        )
        MpvLib.mpv_set_option_string(h, "http-header-fields", "Accept: */*")
        MpvLib.mpv_set_option_string(h, "referrer", "https://www.youtube.com/")
        MpvLib.mpv_set_option_string(h, "video", "yes")
        MpvLib.mpv_set_option_string(h, "audio-display", "no")
        MpvLib.mpv_set_option_string(h, "hwdec", "auto-safe")
        MpvLib.mpv_set_option_string(h, "osc", "no")
        MpvLib.mpv_set_option_string(h, "input-default-bindings", "no")
        MpvLib.mpv_set_option_string(h, "input-vo-keyboard", "no")
        MpvLib.mpv_set_option_string(h, "keep-open", "no")
        MpvLib.mpv_set_option_string(h, "hr-seek", "yes")
        MpvLib.mpv_set_option_string(h, "cache", "yes")
        MpvLib.mpv_set_option_string(h, "cache-secs", "30")
        MpvLib.mpv_set_option_string(h, "demuxer-max-bytes", "268435456")
        MpvLib.mpv_set_option_string(h, "demuxer-max-back-bytes", "67108864")
        if (Platform.isWindows) MpvLib.mpv_set_option_string(h, "ao", "wasapi")
    }

    private fun initializeTexturePipeline(): Boolean {
        if (!Platform.isWindows || !MpvLib.renderApiAvailable || texturePipelineUnavailable) {
            return false
        }
        val output = runCatching { MpvD3D11Output.create() }.getOrNull() ?: run {
            log.warning("Textura D3D11 no disponible; se usará el HWND")
            texturePipelineUnavailable = true
            return false
        }
        var h: MemorySegment? = null
        var ctx: MemorySegment? = null
        return try {
            h = MpvLib.mpv_create() ?: error("mpv_create devolvió null")
            configureCommon(h)
            MpvLib.mpv_set_option_string(h, "vo", "libmpv")
            MpvLib.mpv_set_option_string(h, "vd-lavc-dr", "yes")
            MpvLib.mpv_set_option_string(h, "video-timing-offset", "0")
            val rc = MpvLib.mpv_initialize(h)
            if (rc < 0) error("mpv_initialize falló (código $rc)")
            ctx = MpvLib.mpv_render_context_create(h, output.device)
                ?: error("mpv_render_context_create(D3D11) falló")
            handle = h
            d3d11Output = output
            renderContext = ctx
            _textureInfo.value = MpvTextureInfo(output.sharedHandle, output.width, output.height)
            log.info(
                "init: TextureView ${output.width}x${output.height} " +
                    "shared=0x${output.sharedHandle.toString(16)}",
            )
            setVolume(volume)
            setSpeed(speed)
            startEventLoop(h)
            startTicker()
            startRenderLoop()
            surfaceDirty = false
            true
        } catch (t: Throwable) {
            log.warning("TextureView no disponible: ${t.message}; fallback a HWND")
            ctx?.let { runCatching { MpvLib.mpv_render_context_free(it) } }
            h?.let { runCatching { MpvLib.mpv_terminate_destroy(it) } }
            output.close()
            if (handle === h) handle = null
            d3d11Output = null
            renderContext = null
            _textureInfo.value = null
            texturePipelineUnavailable = true
            false
        }
    }

    private fun initializeFallback() {
        log.info("init: creando libmpv (hwnd=$attachedHwnd)")
        val h = MpvLib.mpv_create() ?: error("mpv_create devolvió null")
        try {
            configureCommon(h)
            MpvLib.mpv_set_option_string(h, "vo", "gpu")
            if (attachedHwnd != 0L) {
                MpvLib.mpv_set_option_string(h, "wid", attachedHwnd.toString())
                log.info("init: wid=$attachedHwnd")
            }
            val rc = MpvLib.mpv_initialize(h)
            if (rc < 0) error("mpv_initialize falló (código $rc)")
            handle = h
            log.info("init: mpv_initialize OK (HWND)")
            setVolume(volume)
            setSpeed(speed)
            startEventLoop(h)
            startTicker()
            surfaceDirty = false
        } catch (t: Throwable) {
            runCatching { MpvLib.mpv_terminate_destroy(h) }
            throw t
        }
    }

    /**
     * Loads a video URL and, optionally, a separate audio URL.
     *
     * For muxed progressive URLs [audioUrl] is null. For DASH-style separate
     * streams the audio is attached with `audio-add ... select`.
     */
    fun load(videoUrl: String, audioUrl: String? = null) {
        init()
        val h = handle ?: error("mpv no inicializado")
        _error.value = null
        _positionMs.value = 0L
        _durationMs.value = 0L
        failed = false
        wantsPlay = true
        if (tickJob?.isActive != true) startTicker()
        pendingAudioUrl = audioUrl?.takeIf { it.isNotBlank() }
        log.info("load: video=${videoUrl.take(80)} audio=${audioUrl?.take(60)}")
        val rc = MpvLib.mpv_command(h, arrayOf("loadfile", videoUrl, "replace"))
        log.info("load: loadfile rc=$rc")
        MpvLib.mpv_set_property_string(h, "pause", "no")
    }

    fun play() {
        wantsPlay = true
        val h = handle ?: return
        MpvLib.mpv_set_property_string(h, "pause", "no")
    }

    fun pause() {
        wantsPlay = false
        val h = handle ?: return
        MpvLib.mpv_set_property_string(h, "pause", "yes")
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    /** Registers a TextureView controller callback. Safe from any thread. */
    fun addFrameListener(listener: () -> Unit): java.lang.AutoCloseable {
        frameListeners += listener
        return java.lang.AutoCloseable { frameListeners -= listener }
    }

    private fun startRenderLoop() {
        if (renderThread != null) return
        renderRunning = true
        renderThread = Thread({
            while (renderRunning) {
                val ctx = renderContext
                val output = d3d11Output
                if (ctx == null || output == null) break
                try {
                    val flags = MpvLib.mpv_render_context_update(ctx)
                    if ((flags and MpvLib.MPV_RENDER_UPDATE_FRAME) == 0L) {
                        Thread.sleep(4)
                        continue
                    }
                    if (!output.acquire(2_000)) {
                        Thread.sleep(4)
                        continue
                    }
                    val rc = try {
                        MpvLib.mpv_render_context_render(ctx, output.texture)
                    } finally {
                        output.release()
                    }
                    if (rc >= 0) {
                        MpvLib.mpv_render_context_report_swap(ctx)
                        if (!output.hasKeyedMutex) output.flush()
                        frameListeners.forEach { listener -> runCatching { listener() } }
                    }
                } catch (t: Throwable) {
                    if (renderRunning) {
                        log.warning("render loop: ${t.message}")
                        runCatching { Thread.sleep(100) }
                    }
                }
            }
        }, "mpv-video-render").apply {
            isDaemon = true
            start()
        }
    }

    fun seekToMs(positionMs: Long) {
        val h = handle ?: return
        val seconds = (positionMs.coerceAtLeast(0L) / 1000.0).toString()
        // mpv takes one combined flag argument: `seek <target> <flags>`.
        MpvLib.mpv_command(h, arrayOf("seek", seconds, "absolute+exact"))
    }

    fun setVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        handle?.let { MpvLib.mpv_set_property_string(it, "volume", volume.toString()) }
    }

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.25f, 2.0f)
        handle?.let { MpvLib.mpv_set_property_string(it, "speed", speed.toString()) }
    }

    fun stop() {
        wantsPlay = false
        failed = true
        handle?.let { MpvLib.mpv_command(it, arrayOf("stop")) }
        _isPlaying.value = false
        _isBuffering.value = false
    }

    fun dispose() {
        teardown()
        attachedHwnd = 0L
        surfaceDirty = false
    }

    private fun teardown() {
        failed = true
        eventLoopRunning = false
        renderRunning = false
        renderThread?.let { thread ->
            runCatching { thread.join(2_000) }
        }
        renderThread = null
        renderContext?.let { ctx -> runCatching { MpvLib.mpv_render_context_free(ctx) } }
        renderContext = null
        tickJob?.cancel()
        tickJob = null
        handle?.let { h ->
            runCatching { MpvLib.mpv_command(h, arrayOf("stop")) }
            runCatching { MpvLib.mpv_wakeup(h) }
            runCatching { MpvLib.mpv_terminate_destroy(h) }
        }
        handle = null
        d3d11Output?.close()
        d3d11Output = null
        _textureInfo.value = null
        _isPlaying.value = false
        _isBuffering.value = false
        eventThread = null
        initializing.set(false)
    }

    private fun startEventLoop(h: MemorySegment) {
        if (eventThread != null) return
        eventLoopRunning = true
        eventThread = Thread({
            while (eventLoopRunning) {
                val event = runCatching { MpvLib.mpv_wait_event(h, -1.0) }.getOrNull() ?: continue
                val id = event.reinterpret(64).get(ValueLayout.JAVA_INT, 0L)
                when (id) {
                    MpvLib.MPV_EVENT_FILE_LOADED -> {
                        val audio = pendingAudioUrl
                        pendingAudioUrl = null
                        if (audio != null) {
                            log.info("file loaded: añadiendo audio externo")
                            MpvLib.mpv_command(h, arrayOf("audio-add", audio, "select"))
                        }
                    }

                    MpvLib.MPV_EVENT_PLAYBACK_RESTART -> {
                        _isBuffering.value = false
                        _isPlaying.value = true
                    }

                    MpvLib.MPV_EVENT_END_FILE -> {
                        _isPlaying.value = false
                        _isBuffering.value = false
                        val data = event.reinterpret(64).get(ValueLayout.ADDRESS, 16L)
                        val reason = if (data.address() != 0L) {
                            data.reinterpret(16).get(ValueLayout.JAVA_INT, 0L)
                        } else {
                            -1
                        }
                        log.info("end-file reason=$reason")
                        if (reason == MpvLib.MPV_END_FILE_REASON_EOF) {
                            _ended.tryEmit(Unit)
                        } else if (reason == MpvLib.MPV_END_FILE_REASON_ERROR) {
                            failed = true
                            _error.value = "mpv terminó la reproducción con error"
                        }
                    }

                    MpvLib.MPV_EVENT_SHUTDOWN -> eventLoopRunning = false
                }
            }
        }, "mpv-video-events").apply {
            isDaemon = true
            start()
        }
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            var debugTicks = 0
            while (handle != null && !failed) {
                val h = handle ?: break
                val pos = getDouble(h, "time-pos")?.times(1000.0)?.toLong() ?: 0L
                val dur = getDouble(h, "duration")?.times(1000.0)?.toLong() ?: 0L
                val paused = getString(h, "pause") == "yes"
                val idle = getString(h, "core-idle") == "yes"
                _positionMs.value = pos.coerceAtLeast(0L)
                if (dur > 0) _durationMs.value = dur
                _isPlaying.value = wantsPlay && !paused
                _isBuffering.value = wantsPlay && idle && pos <= 0L
                getVideoSize(h)?.let { _videoSize.value = it }
                if (debugTicks < 10 && debugTicks % 2 == 0) {
                    log.info(
                        "tick#$debugTicks idle=${getString(h, "idle-active")} " +
                            "vo=${getString(h, "vo-configured")} pos=$pos " +
                            "open=${getString(h, "stream-open-filename")?.take(60)} " +
                            "path=${getString(h, "path")?.take(70)}",
                    )
                }
                debugTicks++
                delay(300)
            }
        }
    }

    private fun getString(h: MemorySegment, name: String): String? {
        val ptr = MpvLib.mpv_get_property_string(h, name) ?: return null
        return try {
            ptr.reinterpret(4096).getString(0)
        } finally {
            MpvLib.mpv_free(ptr)
        }
    }

    private fun getDouble(h: MemorySegment, name: String): Double? =
        getString(h, name)?.toDoubleOrNull()

    private fun getVideoSize(h: MemorySegment): Pair<Int, Int>? {
        val w = getString(h, "width")?.toIntOrNull() ?: return null
        val height = getString(h, "height")?.toIntOrNull() ?: return null
        return if (w > 0 && height > 0) w to height else null
    }
}
