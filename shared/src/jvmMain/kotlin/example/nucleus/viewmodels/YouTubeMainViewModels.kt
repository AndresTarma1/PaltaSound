package example.nucleus.viewmodels

import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.data.repository.YouTubeHistoryRepository
import example.nucleus.player.DashManifestBuilder
import example.nucleus.player.DirectStreamProxyServer
import example.nucleus.player.YtDlpResolver
import example.nucleus.player.YouTubeHomeFeedService
import example.nucleus.player.YouTubePlayerService
import example.nucleus.player.YouTubeSabrResolver
import example.youtube.YouTubeService
import example.youtube.model.YouTubeChannel
import example.youtube.model.YouTubeDashStream
import example.youtube.model.YouTubeSearchItem
import example.youtube.model.YouTubeVideo
import example.youtube.model.YouTubeVideoDetails
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class YouTubeHomeUiState(
    val query: String = "",
    val loading: Boolean = false,
    val items: List<YouTubeSearchItem> = emptyList(),
    val home: List<YouTubeVideo> = emptyList(),
    val subscriptions: List<YouTubeChannel> = emptyList(),
    val homeLoading: Boolean = true,
    val homeError: String? = null,
    val homeContinuation: String? = null,
    val error: String? = null,
    val hasSearched: Boolean = false,
)

class YouTubeHomeViewModel(
    private val service: YouTubeService,
    private val homeFeed: YouTubeHomeFeedService,
) : ApplicationViewModel() {
    private val _state = MutableStateFlow(YouTubeHomeUiState())
    val state: StateFlow<YouTubeHomeUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var homeJob: Job? = null
    private var subscriptionsJob: Job? = null
    private var homeLoaded = false

    fun refreshHome(force: Boolean = false) {
        if (!force && homeLoaded) return
        homeJob?.cancel()
        _state.update { it.copy(homeLoading = true, homeError = null) }
        homeJob = viewModelScope.launch {
            val feed = homeFeed.feed()
            if (feed == null) {
                homeLoaded = false
                _state.update {
                    it.copy(
                        homeLoading = false,
                        homeError = "No se pudo cargar el inicio de YouTube",
                    )
                }
            } else {
                homeLoaded = true
                _state.update {
                    it.copy(
                        home = feed.items,
                        homeContinuation = feed.continuation,
                        homeLoading = false,
                        homeError = null,
                    )
                }
            }
        }
    }

    fun refreshSubscriptions(force: Boolean = false) {
        if (!force && subscriptionsJob?.isActive == true) return
        subscriptionsJob?.cancel()
        subscriptionsJob = viewModelScope.launch {
            val channels = homeFeed.subscriptions()
            _state.update { it.copy(subscriptions = channels) }
        }
    }

    fun loadMoreHome() {
        val continuation = _state.value.homeContinuation ?: return
        if (_state.value.homeLoading) return
        _state.update { it.copy(homeLoading = true, homeError = null) }
        homeJob = viewModelScope.launch {
            val feed = homeFeed.feed(continuation)
            if (feed == null) {
                _state.update { it.copy(homeLoading = false) }
            } else {
                _state.update { current ->
                    current.copy(
                        home = (current.home + feed.items).distinctBy { video -> video.id },
                        homeContinuation = feed.continuation,
                        homeLoading = false,
                    )
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update {
            it.copy(
                query = value,
                hasSearched = if (value.isBlank()) false else it.hasSearched,
            )
        }
    }

    fun search(query: String = _state.value.query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        _state.update { it.copy(query = trimmed, loading = true, error = null, hasSearched = true) }
        searchJob = viewModelScope.launch {
            runCatching { service.search(trimmed) }
                .onSuccess { page ->
                    _state.update { it.copy(loading = false, items = page.items) }
                }
                .onFailure { error ->
                    Napier.w("YouTube search failed: ${error.message}")
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "No se pudo buscar en YouTube",
                        )
                    }
                }
        }
    }
}

data class YouTubeWatchUiState(
    val loading: Boolean = true,
    val status: String? = null,
    val video: YouTubeVideo? = null,
    val related: List<YouTubeVideo> = emptyList(),
    val error: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 100,
)

class YouTubeWatchViewModel(
    private val service: YouTubeService,
    private val playerService: YouTubePlayerService,
    private val musicPlayer: PlayerViewModel,
    private val history: YouTubeHistoryRepository,
    private val userPreferences: UserPreferencesRepository,
) : ApplicationViewModel() {
    private val _state = MutableStateFlow(YouTubeWatchUiState())
    val state: StateFlow<YouTubeWatchUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var progressJob: Job? = null
    private var currentVideoId: String? = null
    private var currentVideo: YouTubeVideo? = null
    private var minimized = false
    private var restoringFromMini = false

    init {
        viewModelScope.launch {
            playerService.isPlaying.collect { value ->
                _state.update { it.copy(isPlaying = value) }
            }
        }
        viewModelScope.launch {
            playerService.isBuffering.collect { value ->
                _state.update { it.copy(isBuffering = value) }
            }
        }
        viewModelScope.launch {
            playerService.positionMs.collect { value ->
                _state.update { it.copy(positionMs = value) }
            }
        }
        viewModelScope.launch {
            playerService.durationMs.collect { value ->
                _state.update { it.copy(durationMs = value) }
            }
        }
        viewModelScope.launch {
            playerService.error.collect { value ->
                if (value != null) _state.update { it.copy(error = value) }
            }
        }
    }

    fun load(videoId: String) {
        if (restoringFromMini && currentVideoId == videoId && _state.value.video != null) {
            restoringFromMini = false
            minimized = false
            playerService.restoreFromMini()
            _state.update { it.copy(loading = false, status = null, error = null) }
            return
        }
        currentVideoId = videoId
        minimized = false
        restoringFromMini = false
        progressJob?.cancel()
        progressJob = null
        // A new video must replace the floating mini player, not stack a
        // second video surface on the same HWND while metadata/download runs.
        if (playerService.minimized.value) {
            playerService.stop()
        } else {
            playerService.restoreFromMini()
        }
        loadJob?.cancel()
        _state.value = YouTubeWatchUiState(loading = true)
        loadJob = viewModelScope.launch {
            runCatching {
                Napier.i("[YouTubeWatch] loading metadata for $videoId")
                val details = service.video(videoId)
                currentVideo = details.video
                playerService.setCurrentVideo(details.video)
                val resumeMs = history.progress(videoId)?.first ?: 0L
                Napier.i("[YouTubeWatch] metadata OK: ${details.video.title} resume=${resumeMs}ms")
                _state.update {
                    it.copy(
                        video = details.video,
                        related = details.related,
                        durationMs = details.video.durationSeconds * 1000L,
                    )
                }
                val sabrEnabled = runCatching { userPreferences.sabrEnabled.first() }
                    .getOrDefault(true)
                val sabrFiles = if (sabrEnabled) {
                    Napier.i("[YouTubeWatch] intentando SABR para $videoId")
                    _state.update { it.copy(status = "Preparando SABR…") }
                    YouTubeSabrResolver.download(videoId) { step ->
                        Napier.i("[YouTubeSABR] $step")
                        _state.update { it.copy(status = step) }
                    }
                } else {
                    null
                }

                // The screen may have been popped while SABR was downloading.
                if (currentVideoId != videoId) return@launch

                if (sabrFiles != null) {
                    Napier.i(
                        "[YouTubeWatch] SABR listo (${sabrFiles.durationMs}ms); " +
                            "pausing music",
                    )
                    musicPlayer.pause()
                    history.save(details.video, resumeMs, details.video.durationSeconds * 1000L)
                    revealVideoSurface()
                    playerService.playSabrFiles(sabrFiles)
                } else {
                    if (sabrEnabled) {
                        Napier.w("[YouTubeWatch] SABR no disponible; usando DASH")
                        _state.update { it.copy(status = "SABR no disponible; usando DASH…") }
                    }
                    val dash = buildDashPlayback(details)
                    if (dash != null) {
                        Napier.i("[YouTubeWatch] DASH local listo; pausing music")
                        musicPlayer.pause()
                        history.save(details.video, resumeMs, details.video.durationSeconds * 1000L)
                        revealVideoSurface()
                        playerService.playManifest(dash.manifestUrl, dash.cleanupUrls)
                    } else {
                        val streams = YtDlpResolver.resolveVideoUrls(videoId)
                            ?: error("yt-dlp no pudo resolver el vídeo $videoId")
                        Napier.i("[YouTubeWatch] streams OK (muxed=${streams.muxed}); pausing music")
                        musicPlayer.pause()
                        history.save(details.video, resumeMs, details.video.durationSeconds * 1000L)
                        revealVideoSurface()
                        playerService.playVideo(details.video, streams)
                    }
                }
                if (resumeMs > 500L) playerService.seekTo(resumeMs)
                Napier.i("[YouTubeWatch] player.load() returned")
                startProgressSaver(details.video)
                _state.update { it.copy(loading = false, status = null) }
            }.onFailure { error ->
                Napier.w("YouTube watch failed for $videoId: ${error.message}")
                _state.update {
                    it.copy(
                        loading = false,
                        status = null,
                        error = error.message ?: "No se pudo cargar el vídeo",
                    )
                }
            }
        }
    }

    /**
     * Makes the NativeView visible/sized before libmpv is initialized. The
     * surface is attached while loading (hidden), so mpv gets a valid `wid`
     * only once Compose had a frame to show it.
     */
    private suspend fun revealVideoSurface() {
        _state.update { it.copy(loading = false, status = null) }
        delay(100)
    }

    private data class DashPlayback(
        val manifestUrl: String,
        val cleanupUrls: List<String>,
    )

    /**
     * Builds one local MPD with separate video/audio AdaptationSets.
     *
     * This mirrors Flow's `MergingMediaSource(true, true, video, audio)`: both
     * tracks share a period/timeline, so mpv seeks and syncs them together.
     */
    private fun buildDashPlayback(details: YouTubeVideoDetails): DashPlayback? {
        if (details.video.durationSeconds <= 0L) return null

        // SegmentBase is an ISO-BMFF profile: prefer MP4/AAC, and fall back to
        // yt-dlp when the video only exposes WebM representations.
        val video = details.videoStreams
            .filter { it.height != null && it.mimeType.startsWith("video/mp4") }
            .let { candidates ->
                candidates.filter {
                    it.codecs?.startsWith("avc1", ignoreCase = true) == true &&
                        (it.height ?: 0) <= 1080
                }.ifEmpty { candidates }
            }
            .maxWithOrNull(
                compareBy<YouTubeDashStream> { it.height ?: 0 }
                    .thenBy { it.bandwidth },
            ) ?: return null

        val audioCandidates = details.audioStreams
            .filter { it.mimeType.startsWith("audio/mp4") }
        if (audioCandidates.isEmpty()) return null
        val audio = audioCandidates
            .filter { it.isOriginalAudio }
            .ifEmpty { audioCandidates }
            .maxByOrNull { it.bandwidth } ?: return null

        return runCatching {
            val videoProxy = DirectStreamProxyServer.register(video.url)
            val audioProxy = DirectStreamProxyServer.register(audio.url)
            try {
                val manifest = DashManifestBuilder.build(
                    video = video.copy(url = videoProxy),
                    audio = audio.copy(url = audioProxy),
                    durationMs = details.video.durationSeconds * 1000L,
                )
                val manifestUrl = DirectStreamProxyServer.registerManifest(manifest)
                Napier.i(
                    "[YouTubeWatch] DASH video=${video.height}p/${video.codecs} " +
                        "audio=${audio.codecs}/${audio.bandwidth}",
                )
                DashPlayback(manifestUrl, listOf(videoProxy, audioProxy))
            } catch (t: Throwable) {
                DirectStreamProxyServer.unregister(videoProxy)
                DirectStreamProxyServer.unregister(audioProxy)
                throw t
            }
        }.getOrElse { error ->
            Napier.w("[YouTubeWatch] no se pudo construir el DASH local: ${error.message}")
            null
        }
    }

    fun togglePlayPause() {
        playerService.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerService.seekTo(positionMs)
    }

    fun setVolume(value: Int) {
        _state.update { it.copy(volume = value) }
        playerService.setVolume(value)
    }

    fun retry() {
        currentVideoId?.let(::load)
    }

    private fun startProgressSaver(video: YouTubeVideo) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                val state = _state.value
                if (state.durationMs > 0 && state.positionMs > 0) {
                    history.updateProgress(video.id, state.positionMs, state.durationMs)
                }
            }
        }
    }

    private fun saveCurrentProgress() {
        val video = currentVideo ?: return
        val state = _state.value
        if (state.durationMs > 0) {
            history.updateProgress(video.id, state.positionMs, state.durationMs)
        }
    }

    /**
     * Called when the watch screen is popped; keeps the singleton player
     * reusable. The optional id prevents an old screen's teardown from
     * cancelling a newer video if the navigation order is reversed.
     */
    fun stopPlayback(videoId: String? = null) {
        if (videoId != null && currentVideoId != videoId) return
        // Leaving the Watch screen through the minimize button must keep the
        // player alive for the floating mini player.
        if (videoId != null && minimized) return
        saveCurrentProgress()
        loadJob?.cancel()
        loadJob = null
        progressJob?.cancel()
        progressJob = null
        minimized = false
        restoringFromMini = false
        playerService.stop()
    }

    fun minimize() {
        if (currentVideoId == null || _state.value.video == null) return
        saveCurrentProgress()
        minimized = true
        playerService.minimize()
    }

    fun restoreFromMini() {
        restoringFromMini = true
        minimized = false
        playerService.restoreFromMini()
    }

    fun closeMini() {
        minimized = false
        restoringFromMini = false
        currentVideoId = null
        loadJob?.cancel()
        loadJob = null
        progressJob?.cancel()
        progressJob = null
        playerService.stop()
    }

    fun playRelated(video: YouTubeVideo) {
        load(video.id)
    }

    override fun onCleared() {
        loadJob?.cancel()
        progressJob?.cancel()
        playerService.stop()
    }
}
