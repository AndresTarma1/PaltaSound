package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.account.AccountManager
import example.nucleus.data.remote.ApiService
import example.nucleus.data.repository.BackgroundStyle
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.db.DatabaseDao
import example.nucleus.db.entities.ArtistEntity
import example.nucleus.db.entities.LyricsEntity
import example.nucleus.download.DownloadService
import example.nucleus.lyrics.BetterLyrics
import example.nucleus.lyrics.LyricLine
import example.nucleus.lyrics.SyncedLyrics
import com.metrolist.lrclib.LrcLib
import com.metrolist.kugou.KuGou
import example.nucleus.models.MediaMetadata
import example.nucleus.models.toMediaMetadata
import example.nucleus.player.*
import example.nucleus.utils.NetworkMonitor
import example.nucleus.utils.PendingAction
import example.nucleus.utils.PendingSyncQueue
import example.nucleus.utils.awaitOnline
import example.nucleus.utils.withMissingMetadataResolved
import example.nucleus.viewmodels.queues.YouTubePlaylistQueue
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.MediaInfo
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.HttpURLConnection
import java.net.URI
import java.util.Collections
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

class PlayerViewModel(
    private val playerService: PlayerService,
    private val streamResolver: AudioStreamResolver,
    private val mediaSession: WindowsMediaSession,
    private val apiService: ApiService,
    private val userPreferences: UserPreferencesRepository,
    val databaseDao: DatabaseDao,
    private val queueManager: QueueManager,
    private val pendingSyncQueue: PendingSyncQueue,
) : ViewModel() {
    private val log = Logger.getLogger("PlayerViewModel")

    val highResCoverArt = userPreferences.highResCoverArt
    val seekBarStyle = userPreferences.seekBarStyle
    val playbackSpeed = userPreferences.playbackSpeed

    // Mientras pienso en un buen diseño xd
//    val background: Flow<BackgroundStyle?> = userPreferences.fullScreenPlayer
//        .combine(userPreferences.nowPlayingBackground) { isFullScreen, style ->
//            if (isFullScreen) style else null
//        }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { userPreferences.setPlaybackSpeed(speed) }
    }

    val likedSongIds: StateFlow<Set<String>> = databaseDao.likedSongs()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _progressState = MutableStateFlow(PlayerProgressState())
    val progressState: StateFlow<PlayerProgressState> = _progressState.asStateFlow()

    val mpvError: StateFlow<String?> = playerService.mpvError
    fun clearMpvError() = playerService.clearMpvError()

    /**
     * Emite la posición objetivo (ms) cada vez que el usuario realiza un salto. Se usa en Listen Together
     * para transmitir los saltos del anfitrión; es inofensivo cuando la función no está en uso.
     */
    private val _seekEvents = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val seekEvents: SharedFlow<Long> = _seekEvents.asSharedFlow()

    // Stubs para compatibilidad con NowPlayingOverlay (prefetch deshabilitado temporalmente)
    val currentSongLyricsOffsetMs: StateFlow<Int> = MutableStateFlow(0)
    fun nudgeCurrentSongLyricsOffset(deltaMs: Int) {}
    fun resetCurrentSongLyricsOffset() {}

    /** Mensajes transitorios para el usuario (por ejemplo, si una canción no se pudo reproducir). La UI los muestra como snackbars. */
    private val _playbackMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val playbackMessages: SharedFlow<String> = _playbackMessages.asSharedFlow()

    /** Fallos consecutivos de resolución/reproducción; el salto automático se detiene al alcanzar [MAX_CONSECUTIVE_FAILURES]. */
    private var consecutiveFailures = 0

    /** Aviso de login para yt-dlp ya mostrado en esta sesión (para no spamear el snackbar). */
    private var ytDlpLoginNoticeShown = false

    /**
     * Cache LRU (500) de `videostatsPlaybackUrl` por canción. Se llena al resolver el stream
     * en-proceso y permite registrar reproducciones de canciones reproducidas desde caché/offline
     * (que no pasan por la resolución) igual que hace Metrolist.
     */
    private val trackingUrlCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
                size > 500
        }
    )

    /** Canciones ya reportadas a YouTube (acotado): evita re-reportar el mismo tick. */
    private val reportedPlaybackIds = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) =
                size > 100
        }
    )

    /**
     * Cuando es verdadero, Listen Together está aplicando un comando remoto, por lo que los
     * observadores no deben retransmitir el cambio de estado resultante (previene bucles de
     * retroalimentación entre anfitrión e invitado).
     */
    @Volatile
    var allowInternalSync: Boolean = false

    /**
     * Verdadero mientras el usuario es invitado en Listen Together: no puede controlar la
     * reproducción compartida, por lo que el botón de play/pause local se reutiliza para
     * silenciar/activar su propia salida de audio.
     */
    @Volatile
    var listenTogetherGuestMode: Boolean = false

    private var resolveJob: Job? = null
    private var fetchMoreJob: Job? = null
    private var prefetchJob: Job? = null
    private var reconnectWatchJob: Job? = null
    private var playRequestId = 0L
    private var currentQueue: Queue? = null

    /** Verdadero cuando el usuario activó el modo sin conexión, o una sonda de conectividad indica que estamos fuera de línea. */
    private suspend fun isEffectivelyOffline(): Boolean =
        userPreferences.offlineModeEnabled.first() || !NetworkMonitor.isOnline()

    /**
     * Inicialización de mpv. Se delega al primer `play()` (PlayerService.play llama a init()
     * internamente), así que NO se llama al arrancar: mpv y su DLL + cache (~20 MB) se cargan
     * solo cuando el usuario reproduce algo.
     */
    fun initialize() {
        playerService.init()
        viewModelScope.launch {
            val savedVolume = userPreferences.readSavedVolume()
            playerService.setVolume(savedVolume)
        }
    }

    /** Al arranque: hidrata el volumen guardado en la UI sin crear mpv (se crea en el primer play). */
    fun primeVolume() {
        viewModelScope.launch {
            playerService.primeVolume()
        }
    }

    private val queueJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Restaura la cola guardada (si la hay) y persiste los cambios de la cola. */
    private fun setupQueuePersistence() {
        viewModelScope.launch {
            if (!userPreferences.queuePersistenceEnabled.first()) return@launch
            val savedJson = userPreferences.savedQueueJson.first()
            if (savedJson.isNullOrBlank()) return@launch
            val session = runCatching {
                queueJson.decodeFromString<QueueSession>(savedJson)
            }.getOrNull() ?: return@launch
            if (session.items.isEmpty()) return@launch
            val shuffle = userPreferences.savedQueueShuffle.first()
            val repeat = runCatching {
                RepeatMode.valueOf(userPreferences.savedQueueRepeat.first())
            }.getOrDefault(RepeatMode.OFF)
            Napier.i("Restaurando cola guardada (${session.items.size} canciones, shuffle=$shuffle, repeat=$repeat)")
            _uiState.update {
                it.copy(
                    currentSong = session.currentSong(),
                    queue = session.queueItems(),
                    currentIndex = session.currentIndex,
                    queueSource = session.source,
                    isShuffled = shuffle,
                    repeatMode = repeat,
                    queueSession = session,
                )
            }
        }

        viewModelScope.launch {
            _uiState
                .distinctUntilChangedBy { it.queueSession }
                .collectLatest { state ->
                    if (!userPreferences.queuePersistenceEnabled.first()) {
                        // Persistencia desactivada: limpiar cualquier cola guardada.
                        userPreferences.saveQueue(null)
                        return@collectLatest
                    }
                    if (state.queue.isEmpty()) {
                        userPreferences.saveQueue(null)
                        return@collectLatest
                    }
                    delay(1000) // debounce: solo guardar cuando la cola se estabiliza
                    // Serializar fuera de Main (puede ser pesado con colas grandes) y no loguear cancelaciones.
                    val json = try {
                        withContext(Dispatchers.Default) {
                            queueJson.encodeToString(state.queueSession)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Napier.w("Fallo al serializar cola: ${e.message}")
                        return@collectLatest
                    }
                    try {
                        userPreferences.saveQueue(json)
                        userPreferences.saveQueueShuffle(state.isShuffled)
                        userPreferences.saveQueueRepeat(state.repeatMode.name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Napier.w("Fallo al persistir la cola: ${e.message}")
                    }
                }
        }
    }

    init {
        // Seek desde los media controls del sistema (SMTC/MPRIS) hacia el reproductor.
        mediaSession.setSeekHandler { target -> seekTo(target) }

        setupQueuePersistence()

        viewModelScope.launch {
            userPreferences.equalizerBands.collect { bands ->
                playerService.setEqualizer(bands)
            }
        }

        viewModelScope.launch {
            userPreferences.loudnessLevel.collect { level ->
                playerService.setLoudness(level.lufs)
            }
        }

        viewModelScope.launch {
            userPreferences.crossfadeEnabled.collect { enabled ->
                playerService.setCrossfadeEnabled(enabled)
            }
        }

        viewModelScope.launch {
            userPreferences.playbackSpeed.collect { speed ->
                playerService.setPlaybackSpeed(speed)
            }
        }

        viewModelScope.launch {
            playerService.playbackState.collect { state ->
                _uiState.update { it.copy(playbackState = state) }

                mediaSession.setPlaybackStatus(
                    isPlaying = state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING,
                    isPaused = state == PlaybackState.PAUSED,
                )

                when (state) {
                    PlaybackState.PLAYING -> consecutiveFailures = 0 // una pista realmente inició
                    PlaybackState.ENDED -> onTrackEnded()
                    PlaybackState.ERROR -> log.warning("Error de reproducción; el usuario puede reintentar manualmente")
                    else -> Unit
                }
            }
        }

        // Anti-stall: re-resolve + seek si la posición se congela o el stream muere a mitad.
        viewModelScope.launch {
            playerService.recoveryRequests.collect { resumeMs ->
                val song = _uiState.value.currentSong ?: return@collect
                Napier.w("Playback recovery requested for ${song.id} @${resumeMs}ms")
                _playbackMessages.tryEmit("Reconectando…")
                resolveAndPlay(song, resumePositionMs = resumeMs)
            }
        }

        viewModelScope.launch {
            playerService.position
                .combine(playerService.duration) { pos, dur -> pos to dur }
                .distinctUntilChanged()
                .collect { (pos, dur) ->
                    _progressState.update { it.copy(positionMs = pos, durationMs = dur) }
                }
        }

        // Registro de reproducción en YouTube estilo Metrolist: el tick se envía solo cuando la
        // canción se escuchó al menos [HISTORY_REPORT_MS] (30s), una vez por canción, y con
        // fallback del player response para las reproducidas desde caché/offline.
        viewModelScope.launch {
            _uiState
                .combine(_progressState) { state, progress -> state to progress }
                .collect { (state, progress) ->
                    val song = state.currentSong ?: return@collect
                    if (YouTube.cookie == null) return@collect
                    if (progress.positionMs < HISTORY_REPORT_MS) return@collect
                    if (reportedPlaybackIds.containsKey(song.id)) return@collect
                    reportedPlaybackIds[song.id] = true
                    viewModelScope.launch(Dispatchers.IO) {
                        registerPlaybackFor(song.id)
                    }
                }
        }

        // Estadísticas locales: registra playTime real + PlayCount cuando cambia la canción
        // (incluido el final de la cola / stop, donde currentSong pasa a null).
        viewModelScope.launch {
            var statSongId: String? = null
            var statStartPos = 0L
            _uiState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collect { songId ->
                    if (songId != statSongId) {
                        statSongId?.let { id ->
                            val endPos = _progressState.value.positionMs
                            recordPlaybackStats(id, statStartPos, endPos)
                        }
                        statSongId = songId
                        statStartPos = _progressState.value.positionMs
                    }
                }
        }

        viewModelScope.launch {
            playerService.volume
                .collect { vol ->
                    _volume.value = vol
                    mediaSession.setVolume(vol / 100f)
                }
        }

        viewModelScope.launch {
            _uiState
                .map { it.currentSong }
                .distinctUntilChanged()
                .collectLatest { song ->
                    if (song != null) {
                        mediaSession.updateMetadata(
                            title = song.title,
                            artist = song.artists.joinToString(", ") { it.name },
                            album = song.album?.title ?: "",
                            // URL remota directa: el SMTC la descarga vía CreateFromUri(https://).
                            // Un archivo local (file://) es best-effort en SMTC de Windows y no carga.
                            thumbnailUrl = song.thumbnailUrl,
                            durationMs = song.duration.toLong() * 1000L,
                        )
                    } else {
                        mediaSession.resetToIdle()
                    }
                }
        }

        viewModelScope.launch {
            _uiState.map { it.currentSong?.id }
                .distinctUntilChanged()
                .filterNotNull()
                .collectLatest { fetchLyrics(); fetchMetadataInfo() }
        }
    }

    fun toggleLike() {
        val song = _uiState.value.currentSong ?: return
        doToggleLike(song)
    }

    fun toggleLikeForSong(song: SongItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = databaseDao.songById(song.id).firstOrNull()
            val newLiked: Boolean
            if (entity != null) {
                newLiked = !entity.liked
                databaseDao.insertSong(entity.localToggleLike())
            } else {
                newLiked = true
                databaseDao.insertSong(song.toMediaMetadata().toSongEntity().localToggleLike())
                song.artists.forEachIndexed { i, artist ->
                    artist.id?.let { id ->
                        val artistEntity = ArtistEntity(
                            id = id,
                            name = artist.name,
                            lastUpdateTime = java.time.LocalDateTime.now()
                        )
                        databaseDao.insertArtist(artistEntity)
                        databaseDao.insertSongArtistMap(song.id, id, i)
                    }
                }
                song.album?.let { album ->
                    databaseDao.insertSongAlbumMap(song.id, album.id, 0)
                }
            }
            if (AccountManager.isLoggedIn) {
                example.nucleus.utils.retryWithBackoff { YouTube.likeVideo(song.id, newLiked) }
                    .onFailure {
                        Napier.w("Failed to push like state for ${song.id}: ${it.message}")
                        pendingSyncQueue.enqueue(PendingAction.LikeSong(song.id, newLiked))
                    }
            }
        }
    }

    private fun doToggleLike(song: MediaMetadata) {
        val newLiked = !song.liked
        val newLikedDate = if (newLiked) java.time.LocalDateTime.now() else null

        _uiState.update { state ->
            state.currentSong?.let { current ->
                state.copy(currentSong = current.copy(liked = newLiked, likedDate = newLikedDate))
            } ?: state
        }

        viewModelScope.launch(Dispatchers.IO) {
            val entity = databaseDao.songById(song.id).firstOrNull()
            if (entity != null) {
                databaseDao.insertSong(entity.localToggleLike())
            } else {
                databaseDao.insertSong(song.toSongEntity().localToggleLike())
                song.artists.forEachIndexed { i, artist ->
                    artist.id?.let { id ->
                        val artistEntity = ArtistEntity(
                            id = id,
                            name = artist.name,
                            lastUpdateTime = java.time.LocalDateTime.now()
                        )
                        databaseDao.insertArtist(artistEntity)
                        databaseDao.insertSongArtistMap(song.id, id, i)
                    }
                }
                song.album?.let { album ->
                    databaseDao.insertSongAlbumMap(song.id, album.id, 0)
                }
            }

            if (AccountManager.isLoggedIn) {
                example.nucleus.utils.retryWithBackoff { YouTube.likeVideo(song.id, newLiked) }
                    .onFailure {
                        Napier.w("Failed to push like state for ${song.id}: ${it.message}")
                        pendingSyncQueue.enqueue(PendingAction.LikeSong(song.id, newLiked))
                    }
            }
        }
    }

    fun playSingle(song: SongItem) = playSingle(song.toMediaMetadata())

    /**
     * Encola una sola canción e inicia la reproducción inmediatamente.
     *
     * La cola relacionada (para reproducción automática/siguiente) se carga en segundo plano
     * y no bloquea el inicio de la reproducción.
     *
     * @param song La canción a reproducir.
     */
    fun playSingle(song: MediaMetadata) {
        val queue = LocalQueue(QueueSource.Single(song.id), listOf(song), 0)
        currentQueue = queue
        val uiState = queueManager.buildUiState(queue, false)
        
        _uiState.update { current ->
            current.copy(
                currentSong = uiState.currentSong,
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                queueSource = uiState.queueSource,
                error = null,
                isShuffled = uiState.isShuffled,
                queueSession = uiState.queueSession,
            )
        }
        // Reproducir inmediatamente; la cola de radio (para reproducción automática/siguiente) se carga
        // en segundo plano y nunca bloquea ni falla la reproducción — tocar una canción no debe depender
        // de su endpoint de radio.
        resolveAndPlay(song)
        if (!listenTogetherGuestMode) fetchRelatedQueue(song, _uiState.value.queueSession)
    }

    /**
     * Obtiene un álbum por su browse ID e inicia la reproducción.
     *
     * Si se encuentran canciones, reproduce el álbum comenzando desde el índice especificado.
     * Si [shuffle] es verdadero, la cola se barajea antes de la reproducción. Si no se encuentran
     * canciones, invoca [onEmpty].
     *
     * @param browseId El identificador browse del álbum.
     * @param title El título del álbum.
     * @param startIndex La posición de la cola desde la que iniciar la reproducción.
     * @param shuffle Si se debe barajear la cola.
     * @param onEmpty Callback invocado si el álbum no contiene canciones.
     */
    fun playAlbumFromBrowseId(
        browseId: String,
        playlistId: String? = null,
        title: String,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        onEmpty: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val songs = apiService.getAlbum(browseId).getOrNull()?.songs.orEmpty()
            if (songs.isNotEmpty()) {
                playAlbum(songs, startIndex, browseId, title)
                if (shuffle) _uiState.update(PlayerQueueCoordinator::shuffleFromStart)
            } else {
                onEmpty?.invoke()
            }
        }
    }

    fun playPlaylistFromId(
        playlistId: String,
        endpoint: WatchEndpoint? = null,
        title: String,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        onEmpty: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val page = YouTube.playlist(playlistId).getOrNull()
            if (page == null || page.songs.isEmpty()) {
                onEmpty?.invoke()
                return@launch
            }

            val queue = YouTubePlaylistQueue(
                playlistId = playlistId,
                playlistTitle = title,
                initialSongs = page.songs,
                initialContinuation = page.songsContinuation?.takeIf { it.isNotBlank() },
                startIndex = startIndex,
            )
            playPlaylistWithQueue(queue, shuffle)
        }
    }

    @JvmName("playAlbumFromSongItems")
    fun playAlbum(songs: List<SongItem>, startIndex: Int = 0, browseId: String, title: String) =
        playAlbum(songs.map { it.toMediaMetadata() }, startIndex, browseId, title)

    fun playAlbum(songs: List<MediaMetadata>, startIndex: Int = 0, browseId: String, title: String) {
        if (songs.isEmpty()) return
        val queue = LocalQueue(QueueSource.Album(browseId, title), songs, startIndex)
        currentQueue = queue
        val uiState = queueManager.buildUiState(queue, false)
        
        _uiState.update { current ->
            current.copy(
                currentSong = uiState.currentSong,
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                queueSource = uiState.queueSource,
                error = null,
                isShuffled = uiState.isShuffled,
                queueSession = uiState.queueSession,
            )
        }
        uiState.currentSong?.let(::resolveAndPlay)
    }

    @JvmName("playPlaylistFromSongItems")
    fun playPlaylist(songs: List<SongItem>, startIndex: Int = 0, playlistId: String, title: String, shuffle: Boolean = false) =
        playPlaylist(songs.map { it.toMediaMetadata() }, startIndex, playlistId, title, shuffle)

    fun playPlaylist(songs: List<MediaMetadata>, startIndex: Int = 0, playlistId: String, title: String, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val queue = LocalQueue(QueueSource.Playlist(playlistId, title), songs, startIndex)
        currentQueue = queue
        val uiState = queueManager.buildUiState(queue, shuffle)
        
        _uiState.update { current ->
            current.copy(
                currentSong = uiState.currentSong,
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                queueSource = uiState.queueSource,
                error = null,
                isShuffled = uiState.isShuffled,
                queueSession = uiState.queueSession,
            )
        }
        uiState.currentSong?.let(::resolveAndPlay)
    }

    fun playPlaylistWithQueue(queue: YouTubePlaylistQueue, shuffle: Boolean = false) {
        if (queue.initialSongs.isEmpty()) return
        
        val implQueue = YouTubePlaylistQueueImpl(
            playlistId = queue.playlistId,
            playlistTitle = queue.playlistTitle,
            initialSongs = queue.initialSongs,
            initialContinuation = queue.initialContinuation,
            startIndex = queue.startIndex,
        )
        currentQueue = implQueue
        
        val uiState = queueManager.buildUiState(implQueue, shuffle)
        
        _uiState.update { current ->
            current.copy(
                currentSong = uiState.currentSong,
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                queueSource = uiState.queueSource,
                error = null,
                isShuffled = uiState.isShuffled,
                queueSession = uiState.queueSession,
            )
        }
        checkAndFetchMoreSongs(_uiState.value, _uiState.value.currentIndex)
        _uiState.value.currentSong?.let(::resolveAndPlay)
    }

    @JvmName("playCustomFromSongItems")
    fun playCustom(songs: List<SongItem>, startIndex: Int = 0) =
        playCustom(songs.map { it.toMediaMetadata() }, startIndex)

    fun playCustom(songs: List<MediaMetadata>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val queue = LocalQueue(QueueSource.Custom, songs, startIndex)
        currentQueue = queue
        val uiState = queueManager.buildUiState(queue, false)
        
        _uiState.update { current ->
            current.copy(
                currentSong = uiState.currentSong,
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                queueSource = uiState.queueSource,
                error = null,
                isShuffled = uiState.isShuffled,
                queueSession = uiState.queueSession,
            )
        }
        uiState.currentSong?.let(::resolveAndPlay)
    }

    fun togglePlayPause() {
        // Como invitado en Listen Together, el anfitrión controla la reproducción — el botón de
        // play/pause silencia la salida local en su lugar. Las acciones aplicadas remotamente
        // (allowInternalSync) ignoran esto.
        if (listenTogetherGuestMode && !allowInternalSync) {
            toggleMute()
            return
        }
        val state = _uiState.value
        if (state.currentSong == null || state.queue.isEmpty() || state.currentIndex !in state.queue.indices) {
            mediaSession.resetToIdle()
            return
        }
        // Tras restaurar la cola persistente, mpv aún no tiene medio cargado: un toggle
        // (resume sin pista) no haría nada y la canción quedaría en 0:00. Hay que resolver
        // y reproducir la pista actual en su lugar.
        if (!playerService.hasLoadedMedia()) {
            resolveAndPlay(state.currentSong)
            return
        }
        playerService.togglePlayPause()
    }

    fun seekTo(millis: Long) {
        playerService.seekTo(millis)
        _seekEvents.tryEmit(millis)
    }

    private var volumePersistJob: Job? = null

    fun setVolume(value: Int) {
        playerService.setVolume(value)
        volumePersistJob?.cancel()
        volumePersistJob = viewModelScope.launch {
            delay(500.milliseconds)
            userPreferences.setVolumen(value)
        }
    }

    /**
     * Alterna entre la salida de audio silenciada y activada.
     */
    fun toggleMute() {
        playerService.toggleMute()
    }

    /**
     * Reproduce un endpoint con vista previa instantánea opcional mientras carga la cola.
     *
     * Muestra [previewSong] inmediatamente en el mini-reproductor mientras obtiene la cola
     * completa desde el endpoint. Si la obtención de la cola tiene éxito, la cola obtenida
     * reemplaza la vista previa. Si la obtención falla y se proporcionó [previewSong], la
     * reproducción recurre a reproducir esa canción sola.
     *
     * @param previewSong Una canción opcional para mostrar inmediatamente. Si la carga de la cola falla, esta canción se reproduce como pista individual.
     */
    fun playEndpoint(endpoint: WatchEndpoint, shuffle: Boolean = false, previewSong: MediaMetadata? = null) {
        viewModelScope.launch {
            // Mostrar el mini-reproductor instantáneamente con la canción clicada en lugar de esperar
            // a la construcción de la cola por red (YouTube.next). La cola real la reemplaza cuando llega.
            _uiState.update {
                it.copy(
                    currentSong = previewSong ?: it.currentSong,
                    playbackState = PlaybackState.LOADING,
                    error = null,
                )
            }
            val result = withContext(Dispatchers.IO) {
                YouTube.next(endpoint).getOrNull()
            }

            if (result != null && result.items.isNotEmpty()) {
                val songs = result.items.map { it.toMediaMetadata() }
                val startIdx = result.currentIndex ?: 0
                val source = QueueSource.Playlist(endpoint.playlistId ?: endpoint.videoId ?: "", result.title ?: "Playlist")

                val session = PlayerQueueCoordinator.collectionSession(source, songs, startIdx)
                    .copy(continuation = result.continuation, endpoint = endpoint)

                _uiState.update { current ->
                    val base = current.copy(
                        currentSong = session.currentSong(),
                        queue = session.queueItems(),
                        currentIndex = session.currentIndex,
                        queueSource = source,
                        error = null,
                        isShuffled = false,
                        queueSession = session
                    )
                    if (shuffle) PlayerQueueCoordinator.shuffleFromStart(base) else base
                }
                _uiState.value.currentSong?.let(::resolveAndPlay)
            } else if (previewSong != null) {
                // La obtención de radio/cola falló — aún así reproducir la canción sola en lugar de mostrar error.
                log.warning("next() failed for ${endpoint.videoId}; playing single song")
                playSingle(previewSong)
            } else {
                _uiState.update { it.copy(playbackState = PlaybackState.IDLE, error = "No se pudieron cargar las canciones de la lista.") }
            }
        }
    }

    private fun checkAndFetchMoreSongs(state: PlayerUiState, nextIndex: Int) {
        if (listenTogetherGuestMode) return
        val session = state.queueSession

        if (session.playlistQueue != null && session.playlistQueue.hasNextPage() && nextIndex >= session.order.size - 3) {
            fetchMoreJob?.cancel()
            fetchMoreJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val newSongs = session.playlistQueue.loadNextPage()
                    if (newSongs.isNotEmpty()) {
                        val newMetadata = newSongs.map { it.toMediaMetadata() }
                        _uiState.update { currentState ->
                            val currentSession = currentState.queueSession
                            val updatedItems = currentSession.items + newMetadata
                            val newOrder = currentSession.order + newMetadata.indices.map { currentSession.items.size + it }
                            val updatedSession = currentSession.copy(
                                items = updatedItems,
                                order = newOrder,
                                playlistQueue = session.playlistQueue,
                            )
                            currentState.copy(
                                queueSession = updatedSession,
                                queue = updatedSession.queueItems()
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.warning("Error fetching more playlist songs: ${e.message}")
                }
            }
            return
        }

        if (session.continuation != null && session.endpoint != null && nextIndex >= session.order.size - 3) {
            // Prevenir múltiples obtenciones en paralelo
            val currentContinuation = session.continuation
            _uiState.update { it.copy(queueSession = it.queueSession.copy(continuation = null)) }

            fetchMoreJob?.cancel()
            fetchMoreJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val result = YouTube.next(session.endpoint, currentContinuation).getOrNull()
                    if (result != null && result.items.isNotEmpty()) {
                        val newSongs = result.items.map { it.toMediaMetadata() }

                        _uiState.update { currentState ->
                            val currentSession = currentState.queueSession
                            val updatedItems = currentSession.items + newSongs
                            val newOrder = currentSession.order + newSongs.indices.map { currentSession.items.size + it }

                            val updatedSession = currentSession.copy(
                                items = updatedItems,
                                order = newOrder,
                                continuation = result.continuation
                            )

                            currentState.copy(
                                queueSession = updatedSession,
                                queue = updatedSession.queueItems()
                            )
                        }
                    } else {
                        // Restaurar continuation si la obtención falló y queremos reintentar?
                        // Depende de la implementación, se puede restaurar o dejarlo null.
                        if (result?.continuation != null) {
                            _uiState.update { it.copy(queueSession = it.queueSession.copy(continuation = result.continuation)) }
                        }
                    }
                } catch (e: Exception) {
                    log.warning("Error al obtener más canciones: ${e.message}")
                    // restaurar el token para reintentar más tarde
                    _uiState.update { it.copy(queueSession = it.queueSession.copy(continuation = currentContinuation)) }
                }
            }
        }
    }

    fun next() {
        if (listenTogetherGuestMode && !allowInternalSync) return
        val state = _uiState.value
        if (state.queueSession.order.isEmpty()) return

        // Skip manual: no respetar Repeat.ONE (eso solo aplica al fin de pista).
        val nextIndex = PlayerQueueCoordinator.nextIndex(state, ignoreRepeatOne = true) ?: run {
            if (state.repeatMode == RepeatMode.OFF) stop()
            return
        }

        // Evitar “recargar” la misma canción si no hay avance real.
        if (nextIndex == state.currentIndex &&
            state.queueSession.order.size <= 1 &&
            state.repeatMode == RepeatMode.OFF
        ) {
            stop()
            return
        }

        playAtIndex(nextIndex)

        // Prefetch / auto-carga de cola tras el salto
        currentQueue?.let { queue ->
            viewModelScope.launch(Dispatchers.IO) {
                val newItems = queueManager.checkAndLoadMore(
                    queue,
                    nextIndex,
                    _uiState.value.queueSession.items.size,
                )
                if (newItems.isNotEmpty()) {
                    _uiState.update { currentState ->
                        val currentSession = currentState.queueSession
                        val updatedItems = currentSession.items + newItems
                        val newOrder = currentSession.order +
                            newItems.indices.map { currentSession.items.size + it }
                        val updatedSession = currentSession.copy(
                            items = updatedItems,
                            order = newOrder,
                        )
                        currentState.copy(
                            queueSession = updatedSession,
                            queue = updatedSession.queueItems(),
                        )
                    }
                }
            }
        }
        if (currentQueue == null) {
            checkAndFetchMoreSongs(_uiState.value, nextIndex)
        }
    }

    fun previous() {
        if (listenTogetherGuestMode && !allowInternalSync) return
        val state = _uiState.value
        if (state.queueSession.order.isEmpty()) return

        // Estándar media player: >3s reinicia la pista actual; no re-resuelve stream.
        if (_progressState.value.positionMs > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = PlayerQueueCoordinator.previousIndex(state, ignoreRepeatOne = true) ?: return
        if (prevIndex == state.currentIndex) {
            // Ya en la primera (o cola de 1): solo reiniciar sin cascade resolve.
            seekTo(0L)
            return
        }
        playAtIndex(prevIndex)
    }

    fun toggleShuffle() {
        _uiState.update(PlayerQueueCoordinator::toggleShuffle)
    }

    fun toggleRepeat() {
        _uiState.update {
            it.copy(
                repeatMode = when (it.repeatMode) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
            )
        }
    }

    fun stop() {
        resolveJob?.cancel()
        playRequestId += 1
        playerService.stop()
        _progressState.value = PlayerProgressState()
        _uiState.update {
            it.copy(
                currentSong = null,
                queue = emptyList(),
                currentIndex = 0,
                playbackState = PlaybackState.IDLE
            )
        }
        mediaSession.resetToIdle()
    }

    fun addToQueue(song: SongItem) = addToQueue(song.toMediaMetadata())

    fun addToQueue(song: MediaMetadata) {
        // append() agrega la canción al final del orden de reproducción, lo cual es correcto
        // tanto si el barajeo está activado como no (termina al final del orden barajeo actual).
        // No se necesita re-barajear.
        _uiState.update { state -> PlayerQueueCoordinator.append(state, song) }
    }

    fun playNext(song: SongItem) = playNext(song.toMediaMetadata())

    fun playNext(song: MediaMetadata) {
        // insertNext() coloca la canción justo después de la actual en el orden de reproducción,
        // lo cual ya respeta el barajeo (currentIndex es un índice dentro del orden barajeo). El
        // código anterior TAMBIÉN llamaba a rebuildShuffleOrder aquí, lo cual duplicaba la canción
        // y re-barajeaba toda la cola — enviando la pista "reproducir siguiente" a una posición
        // aleatoria/última. Usar insertNext solo.
        _uiState.update { state -> PlayerQueueCoordinator.insertNext(state, song) }
    }

    fun playNextResolved(song: SongItem) {
        viewModelScope.launch {
            val resolvedSong = withContext(Dispatchers.IO) { song.withMissingMetadataResolved() }
            playNext(resolvedSong)
        }
    }

    fun addToQueueResolved(song: SongItem) {
        viewModelScope.launch {
            val resolvedSong = withContext(Dispatchers.IO) { song.withMissingMetadataResolved() }
            addToQueue(resolvedSong)
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.queueSession.order.size) return

        val session = state.queueSession
        val newOrder = session.order.toMutableList().apply { removeAt(index) }
        val newItems = session.items
        val newIndex = when {
            newOrder.isEmpty() -> {
                stop()
                return
            }

            index < state.currentIndex -> state.currentIndex - 1
            index == state.currentIndex -> {
                val nextIdx = index.coerceAtMost(newOrder.lastIndex)
                val nextSong = newOrder.getOrNull(nextIdx)?.let(newItems::getOrNull)
                _uiState.update {
                    it.copy(
                        queue = newOrder.mapNotNull { idx -> newItems.getOrNull(idx) },
                        currentIndex = nextIdx,
                        currentSong = nextSong,
                        queueSession = session.copy(order = newOrder, currentIndex = nextIdx)
                    )
                }
                nextSong?.let(::resolveAndPlay)
                return
            }

            else -> state.currentIndex
        }
        _uiState.update {
            it.copy(
                queue = newOrder.mapNotNull { idx -> newItems.getOrNull(idx) },
                currentIndex = newIndex,
                queueSession = session.copy(order = newOrder, currentIndex = newIndex)
            )
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _uiState.update { state -> PlayerQueueCoordinator.move(state, fromIndex, toIndex) }
    }

    /** Sincroniza la cola completa desde el host (invitado) sin reiniciar la pista actual. */
    fun syncQueueFromHost(items: List<MediaMetadata>, currentIndex: Int) {
        if (items.isEmpty()) return
        val idx = currentIndex.coerceIn(0, items.lastIndex)
        val session = QueueSession(
            source = QueueSource.Custom,
            items = items,
            order = items.indices.toList(),
            currentIndex = idx,
        )
        _uiState.update {
            it.copy(
                queueSession = session,
                queue = session.queueItems(),
                currentIndex = idx,
                // No tocamos currentSong/playbackState/progress — solo la cola
            )
        }
    }

    /** Reintenta la reproducción de la canción actual (para una acción de "reintentar" en un error de reproducción). */
    fun retry() {
        val song = _uiState.value.currentSong ?: return
        _uiState.update { it.copy(error = null) }
        resolveAndPlay(song)
    }

    fun playAtIndex(index: Int) {
        if (listenTogetherGuestMode && !allowInternalSync) return
        val state = _uiState.value
        if (index < 0 || index >= state.queueSession.order.size) return

        val song = state.queueSession.order.getOrNull(index)?.let(state.queueSession.items::getOrNull) ?: return
        _progressState.update { it.copy( positionMs = 0, durationMs = song.duration.toLong() * 1000) }
        _uiState.update {
            it.copy(
                currentSong = song,
                currentIndex = index,
                error = null,
                queueSession = state.queueSession.copy(currentIndex = index)
            )
        }
        checkAndFetchMoreSongs(_uiState.value, index)
        resolveAndPlay(song)
    }

    /**
     * Reproduce la canción dada, resolviendo su fuente de audio mediante múltiples estrategias
     * de respaldo.
     *
     * @param resumePositionMs si > 0, reanuda cerca de esa posición tras re-resolver (stall mid-track).
     */
    private fun resolveAndPlay(song: MediaMetadata, resumePositionMs: Long = 0L) {
        resolveJob?.cancel()
        reconnectWatchJob?.cancel()
        playRequestId += 1
        val requestId = playRequestId
        val resumeMs = resumePositionMs.coerceAtLeast(0L)
        val isResume = resumeMs > 500L

        if (!isResume) {
            _progressState.update { it.copy(positionMs = 0, durationMs = song.duration.toLong() * 1000) }
        } else {
            _progressState.update {
                it.copy(
                    positionMs = resumeMs,
                    durationMs = it.durationMs.takeIf { d -> d > 0 }
                        ?: song.duration.toLong() * 1000,
                )
            }
        }

        resolveJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    playbackState = if (isResume) PlaybackState.BUFFERING else PlaybackState.LOADING,
                    error = null,
                )
            }
            if (!isResume) {
                _progressState.update { it.copy(positionMs = 0, durationMs = song.duration.toLong() * 1000) }
            }
            playerService.stopAudioOnly()

            try {
                val cachedFile = withContext(Dispatchers.IO) {
                    DownloadService.getCachedFile(song.id)
                }
                if (requestId != playRequestId) return@launch

                // Las canciones sin caché necesitan una resolución en vivo; fallar rápido si estamos
                // sin conexión (o el usuario activó el modo sin conexión) en lugar de agotar toda la
                // cascada de en-proceso + yt-dlp solo para que termine agotando el tiempo de todas formas.
                val offline = cachedFile == null && isEffectivelyOffline()
                if (offline) {
                    if (requestId == playRequestId) handlePlaybackFailure(song)
                    return@launch
                }

                fun startUrl(url: String) {
                    if (isResume) playerService.playFrom(url, resumeMs) else playerService.play(url)
                }

                // videostatsPlaybackUrl de la pista resuelta — se guarda para registrar la
                // reproducción en la cuenta después (historial/recomendaciones) cuando se
                // supere el umbral de escucha (ver collector en init). El cache permite
                // reportar también las canciones reproducidas desde caché/offline.
                val played: Boolean = when {
                    cachedFile != null -> {
                        startUrl(cachedFile.absolutePath)
                        true
                    }
                    YtDlpResolver.needsYtDlp(song.id) -> {
                        // Video conocido como problemático (todos los clientes en-proceso dan 403 en
                        // esta sesión): saltar directamente a yt-dlp en lugar de repetir el ciclo lento
                        // de resolución + fallo de mpv.
                        playViaYtDlp(song, requestId, resumeMs)
                    }
                    else -> {
                        val playbackData = try {
                            withContext(Dispatchers.IO) {
                                streamResolver.resolveAudioStream(song.id)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Napier.w("In-process resolve failed for ${song.id} (${e.message}); falling back to yt-dlp")
                            null
                        }
                        val streamUrl = playbackData?.streamUrl
                        playbackData?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                            ?.takeIf { it.isNotBlank() }
                            ?.let { trackingUrlCache[song.id] = it }
                        if (requestId != playRequestId) return@launch

                        if (!streamUrl.isNullOrEmpty()) {
                            startUrl(streamUrl)
                            // El stream resuelto puede pasar la validación HTTP pero dar 403 en mpv
                            // (por ejemplo, URLs con enforcement de Range rqh/spc). Si la
                            // reproducción no inicia realmente, probar SABR en-proceso antes de
                            // recurrir a yt-dlp.
                            val started = playerService.awaitPlaybackStarted()
                            if (started || requestId != playRequestId) {
                                true
                            } else {
                                Napier.w("Stream did not start for ${song.id}; trying SABR fallback")
                                trySabrThenYtDlp(song, requestId, resumeMs)
                            }
                        } else {
                            Napier.w("No in-process stream for ${song.id}; trying SABR fallback")
                            trySabrThenYtDlp(song, requestId, resumeMs)
                        }
                    }
                }
                if (requestId != playRequestId) return@launch

                if (!played) {
                    handlePlaybackFailure(song)
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    cacheSongMetadata(song)
                }

                prefetchNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Playback failed for ${song.id} - ${song.title}", e)
                if (requestId == playRequestId) handlePlaybackFailure(song)
            }
        }
    }

    /**
     * Registra la reproducción en YouTube (videostatsPlaybackUrl) una vez la canción se escuchó
     * el umbral mínimo. Usa el URL cacheado al resolver el stream; si falta (reproducción desde
     * caché/offline o vía yt-dlp), re-fetchea el player response ligero para obtenerlo, igual
     * que Metrolist.
     */
    private suspend fun registerPlaybackFor(songId: String) {
        val url = trackingUrlCache[songId]
            ?: YTPlayerutils.playerResponseForMetadata(songId)
                .getOrNull()
                ?.playbackTracking
                ?.videostatsPlaybackUrl
                ?.baseUrl
        if (url.isNullOrBlank()) {
            Napier.w("No playback tracking URL for $songId; skipping YouTube history registration")
            return
        }
        runCatching { YouTube.registerPlayback(playbackTracking = url) }
            .onFailure { Napier.w("registerPlayback failed for $songId: ${it.message}") }
    }

    /**
     * Estadísticas locales (estilo Metrolist): registra el tiempo escuchado real de una canción
     * (>= [HISTORY_REPORT_MS] para evitar ruido), acumula totalPlayTime en Song e incrementa el
     * PlayCount del mes actual.
     */
    private suspend fun recordPlaybackStats(songId: String, startPosMs: Long, endPosMs: Long) {
        val playedMs = (endPosMs - startPosMs).coerceAtLeast(0L)
        if (playedMs < HISTORY_REPORT_MS) return
        val now = java.time.LocalDateTime.now()
        withContext(Dispatchers.IO) {
            runCatching {
                databaseDao.insertEvent(songId, now, playedMs)
                databaseDao.updateSongTotalPlayTime(playedMs, songId)
                databaseDao.incrementPlayCount(songId, now.year, now.monthValue)
            }.onFailure { Napier.w("Failed to record stats for $songId: ${it.message}") }
        }
    }

    companion object {
        private const val MAX_CONSECUTIVE_FAILURES = 5

        /** Mínimo escuchado (ms) antes de registrar la reproducción en YouTube, igual que Metrolist (default 30s). */
        private const val HISTORY_REPORT_MS = 30_000L
    }

    /**
     * Una pista no pudo ser resuelta/reproducida. Si es porque estamos sin conexión, detener y
     * dejar que el usuario reintente una vez que se restablezca la conectividad — la reproducción
     * automática simplemente fallaría de la misma manera en cada pista restante de la cola. De lo
     * contrario, notificar y avanzar automáticamente a la siguiente pista, a menos que muchas
     * pistas consecutivas hayan fallado (probablemente un problema más amplio) — entonces también
     * detenerse.
     */
    private suspend fun handlePlaybackFailure(song: MediaMetadata) {
        consecutiveFailures++
        _uiState.update { it.copy(error = null) }

        val manualOffline = userPreferences.offlineModeEnabled.first()
        if (manualOffline || !NetworkMonitor.isOnline()) {
            consecutiveFailures = 0
            val message = if (manualOffline) "Modo sin conexión activado. Reproducción pausada."
            else "Sin conexión a internet. Reproducción pausada."
            _playbackMessages.tryEmit(message)
            _uiState.update { it.copy(playbackState = PlaybackState.ERROR, error = message) }
            // Solo reanudar automáticamente por una caída real de conectividad — el modo sin
            // conexión manual es una decisión deliberada que el usuario debe desactivar por sí
            // mismo, no algo que anulamos en su nombre.
            if (!manualOffline) watchForReconnect(song)
            return
        }

        val hasNext = PlayerQueueCoordinator.nextIndex(_uiState.value) != null
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES || !hasNext) {
            consecutiveFailures = 0
            _playbackMessages.tryEmit("No se pudo reproducir «${song.title}»")
            _uiState.update { it.copy(playbackState = PlaybackState.ERROR) }
        } else {
            _playbackMessages.tryEmit("No se pudo reproducir «${song.title}», pasando a la siguiente")
            next()
        }
    }

    /**
     * Vigila que se restablezca la conectividad y reintenta automáticamente [song] una vez que
     * lo haga — el usuario no debería tener que darse cuenta y presionar reproducir manualmente
     * de nuevo. Se cancela con la siguiente llamada a [resolveAndPlay] (cambio de canción,
     * reintento manual, etc.) para que nunca se ejecute con una canción obsoleta.
     */
    private fun watchForReconnect(song: MediaMetadata) {
        reconnectWatchJob?.cancel()
        reconnectWatchJob = viewModelScope.launch {
            NetworkMonitor.awaitOnline()
            if (!userPreferences.offlineModeEnabled.first() && _uiState.value.currentSong?.id == song.id) {
                retry()
            }
        }
    }

    /**
     * Pre-resuelve la URL del stream de la siguiente pista en segundo plano para que [next] sea
     * casi instantáneo (pre-calienta la misma entrada de StreamCache que usará [resolveAndPlay]).
     * Omite archivos en caché y videos conocidos como exclusivos de yt-dlp (re-resolverlos en
     * proceso es inútil).
     */
    private fun prefetchNext() {
        val state = _uiState.value
        val nextIdx = PlayerQueueCoordinator.nextIndex(state) ?: return
        val nextSong = state.queueSession.order.getOrNull(nextIdx)
            ?.let(state.queueSession.items::getOrNull) ?: return
        if (YtDlpResolver.needsYtDlp(nextSong.id)) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (DownloadService.getCachedFile(nextSong.id) != null) return@launch
                streamResolver.resolveAudioStream(nextSong.id) // result discarded; StreamCache warmed
            } catch (_: Exception) {
                // mejor esfuerzo de pre-carga
            }
        }
    }

    /**
     * Resuelve [song] mediante yt-dlp y la reproduce; mantiene el mini-reproductor en LOADING
     * mientras tanto. Retorna verdadero si la reproducción se inició, falso si la URL no pudo
     * resolverse (el llamador maneja el fallo/salto).
     */
    private suspend fun playViaYtDlp(
        song: MediaMetadata,
        requestId: Long,
        resumePositionMs: Long = 0L,
    ): Boolean {
        _uiState.update {
            it.copy(
                playbackState = if (resumePositionMs > 500L) PlaybackState.BUFFERING else PlaybackState.LOADING,
                error = null,
            )
        }
        val ytUrl = withContext(Dispatchers.IO) {
            YtDlpResolver.resolveAudioUrl(song.id, streamResolver.currentAudioQuality())
        }
        return requestId != playRequestId || if (ytUrl != null) {
            if (resumePositionMs > 500L) {
                playerService.playFrom(ytUrl, resumePositionMs)
            } else {
                playerService.play(ytUrl)
            }
            true
        } else {
            false
        } // superseded — not a failure
    }

    /**
     * Intenta reproducir [song] vía SABR en-proceso (proxy local -> mpv); si falla
     * (o SABR está desactivado en ajustes), marca el video y recurre a yt-dlp.
     * Retorna verdadero si la reproducción se inició por cualquiera de las dos vías.
     * Si se cae a yt-dlp sin sesión iniciada, avisa una vez por sesión: yt-dlp
     * extrae mejor con cuenta (formatos con login, videos con restricción).
     */
    private suspend fun trySabrThenYtDlp(
        song: MediaMetadata,
        requestId: Long,
        resumeMs: Long,
    ): Boolean {
        val sabrEnabled = userPreferences.sabrEnabled.first()
        val sabrUrl = if (sabrEnabled) {
            try {
                withContext(Dispatchers.IO) {
                    SabrResolver.resolveLocalUrl(song.id, streamResolver.currentAudioQuality())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("SABR resolve failed for ${song.id} (${e.message}); falling back to yt-dlp")
                null
            }
        } else {
            Napier.d("SABR desactivado en ajustes para ${song.id}; yt-dlp directo")
            null
        }
        if (!sabrUrl.isNullOrEmpty() && requestId == playRequestId) {
            if (resumeMs > 500L) playerService.playFrom(sabrUrl, resumeMs) else playerService.play(sabrUrl)
            // SABR materializa el audio en background: dar más margen que a una URL directa.
            if (playerService.awaitPlaybackStarted(timeoutMs = 45_000)) return true
            Napier.w("SABR stream did not start for ${song.id}; falling back to yt-dlp")
        }
        if (requestId == playRequestId) YtDlpResolver.markNeedsYtDlp(song.id)
        if (requestId == playRequestId && !AccountManager.isLoggedIn && !ytDlpLoginNoticeShown) {
            ytDlpLoginNoticeShown = true
            _playbackMessages.tryEmit(
                "yt-dlp extrae mejor con sesión iniciada. Iniciá sesión en Ajustes > Cuenta para videos difíciles.",
            )
        }
        return playViaYtDlp(song, requestId, resumeMs)
    }

    private suspend fun cacheSongMetadata(song: MediaMetadata) {
        val exists = databaseDao.songById(song.id).firstOrNull() != null
        if (!exists) {
            databaseDao.insertSong(song.toSongEntity())
        }
        val currentArtists = databaseDao.artistsForSong(song.id)
        if (currentArtists.isEmpty() && song.artists.isNotEmpty()) {
            song.artists.forEachIndexed { i, artist ->
                // Las canciones que vienen de Listen Together (y algunos resultados de búsqueda) solo
                // llevan el nombre del artista, sin id de YouTube. Sin un id, el mapeo canción↔artista
                // se omitía por completo, por lo que el historial no mostraba artista. Usar un id
                // sintético estable como respaldo para que el nombre persista.
                val id = artist.id ?: "local:${artist.name.trim().lowercase().hashCode()}"
                if (artist.name.isBlank()) return@forEachIndexed
                val artistEntity = ArtistEntity(
                    id = id,
                    name = artist.name,
                    lastUpdateTime = java.time.LocalDateTime.now()
                )
                databaseDao.insertArtist(artistEntity)
                databaseDao.insertSongArtistMap(song.id, id, i)
            }
        }
        song.album?.let { album ->
            val currentAlbum = databaseDao.albumForSong(song.id)
            if (currentAlbum == null) {
                databaseDao.insertSongAlbumMap(song.id, album.id, 0)
            }
        }
    }

    private fun onTrackEnded() {
        if (listenTogetherGuestMode) return
        val state = _uiState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> state.currentSong?.let(::resolveAndPlay)
            RepeatMode.ALL -> next()
            RepeatMode.OFF -> if (state.currentIndex < state.queue.lastIndex) next()
        }
    }

    private fun fetchRelatedQueue(song: MediaMetadata, sessionSeed: QueueSession) {
        if (listenTogetherGuestMode) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val endpoint = WatchEndpoint(videoId = song.id)
                val result = YouTube.next(endpoint).getOrNull() ?: return@launch
                val originalSongId = song.id

                _uiState.update { state ->
                    if (state.currentSong?.id != originalSongId || sessionSeed.source !is QueueSource.Single) return@update state

                    val suggestedCurrent = result.items.find { it.id == originalSongId }?.toMediaMetadata()
                    val related = result.items
                        .filter { it.id != originalSongId }
                        .map { it.toMediaMetadata() }
                    val items = listOfNotNull(
                        state.currentSong.let {
                            if (it.duration <= 0 && suggestedCurrent != null && suggestedCurrent.duration > 0) {
                                it.copy(duration = suggestedCurrent.duration)
                            } else {
                                it
                            }
                        }
                    ) + related
                    val order = items.indices.toList()

                    state.copy(
                        currentSong = items.firstOrNull(),
                        queue = items,
                        currentIndex = 0,
                        queueSource = QueueSource.Single(originalSongId),
                        isShuffled = false,
                        queueSession = QueueSession(
                            source = QueueSource.Single(originalSongId),
                            items = items,
                            order = order,
                            currentIndex = 0
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()

    private val _currentMediaInfo = MutableStateFlow<MediaInfo?>(null)
    val currentMediaInfo: StateFlow<MediaInfo?> = _currentMediaInfo.asStateFlow()

    fun fetchMetadataInfo() {
        val song = _uiState.value.currentSong ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _currentMediaInfo.value = null
            try {
                val mediaInfo = YouTube.getMediaInfo(song.id).getOrNull()
                _currentMediaInfo.value = mediaInfo
            } catch (_: Exception) {
                _currentMediaInfo.value = null
            }
        }
    }

    /** Letras sincronizadas por tiempo (BetterLyrics o LRC sincronizado de YouTube). Nulo cuando solo existe texto plano. */
    private val _syncedLyrics = MutableStateFlow<List<LyricLine>?>(null)
    val syncedLyrics: StateFlow<List<LyricLine>?> = _syncedLyrics.asStateFlow()

    /** true mientras se buscan letras de la canción actual (distinto de "sin letras"). */
    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    private val _lyricsFetchFailed = MutableStateFlow(false)
    val lyricsFetchFailed: StateFlow<Boolean> = _lyricsFetchFailed.asStateFlow()

    fun retryFetchLyrics() {
        lastLyricsSongId = null
        viewModelScope.launch { fetchLyrics() }
    }

    // Canción para la que ya se pidieron (o están en curso) las letras. Evita re-fetch al solo
    // cambiar de pestaña (Cola/Info -> Letras) sobre la misma canción.
    private var lastLyricsSongId: String? = null

    /** Job único de búsqueda de letras: se cancela al cambiar de canción para no aplicar LRC viejo. */
    private var lyricsJob: Job? = null

    /** Prefetch en background de la siguiente pista; no toca el estado visible. */
    private var lyricsPrefetchJob: Job? = null

    /**
     * Cache en memoria de LRC crudo (o marcador NOT_FOUND) por song id.
     * Complementa la tabla SQL `Lyrics` para hits rápidos entre pistas.
     */
    private val lyricsMemoryCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedLyrics>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLyrics>?) =
                size > 48
        }
    )

    private data class CachedLyrics(
        val raw: String,
        val provider: String,
        val notFound: Boolean = false,
    )

    fun fetchLyrics() {
        val song = _uiState.value.currentSong ?: return
        if (song.id == lastLyricsSongId && lyricsJob?.isActive == true) return
        if (song.id == lastLyricsSongId && _currentLyrics.value != null && !_lyricsLoading.value) return

        val requestSongId = song.id
        lastLyricsSongId = requestSongId
        lyricsJob?.cancel()

        // Cache en memoria: parsear fuera de Main para no congelar al cambiar de cancion.
        lyricsMemoryCache[requestSongId]?.let { cached ->
            lyricsJob = viewModelScope.launch(Dispatchers.Default) {
                if (_uiState.value.currentSong?.id != requestSongId || lastLyricsSongId != requestSongId) return@launch
                applyCachedLyrics(cached)
                _lyricsLoading.value = false
                prefetchNextLyrics()
            }
            return
        }

        // Limpiar de inmediato para que la UI no muestre letras de la pista anterior.
        _currentLyrics.value = null
        _syncedLyrics.value = null
        _lyricsLoading.value = true

        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            fun stillCurrent(): Boolean =
                _uiState.value.currentSong?.id == requestSongId && lastLyricsSongId == requestSongId

            fun publishPlain(text: String?, provider: String = "Unknown") {
                if (!stillCurrent()) return
                val plain = text.orEmpty()
                _syncedLyrics.value = null
                _currentLyrics.value = plain
                _lyricsLoading.value = false
                rememberLyrics(requestSongId, plain, provider, notFound = plain.isBlank())
            }

            fun publishSynced(parsed: List<LyricLine>, plainFallback: String, provider: String, raw: String) {
                if (!stillCurrent()) return
                _syncedLyrics.value = parsed
                _currentLyrics.value = plainFallback
                _lyricsLoading.value = false
                rememberLyrics(requestSongId, raw, provider, notFound = false)
            }

            try {
                // 1) Disco (SQLDelight)
                val dbHit = databaseDao.getLyrics(requestSongId)
                if (dbHit != null) {
                    val notFound = dbHit.lyrics == LyricsEntity.LYRICS_NOT_FOUND || dbHit.lyrics.isBlank()
                    val cached = CachedLyrics(
                        raw = if (notFound) "" else dbHit.lyrics,
                        provider = dbHit.provider,
                        notFound = notFound,
                    )
                    lyricsMemoryCache[requestSongId] = cached
                    if (stillCurrent()) {
                        applyCachedLyrics(cached)
                        _lyricsLoading.value = false
                    }
                    return@launch
                }

                withTimeout(20_000L) {
                    val result = lookupLyricsRemote(song) ?: return@withTimeout
                    if (!stillCurrent()) return@withTimeout

                    if (result.raw.isNotEmpty() && SyncedLyrics.isSynced(result.raw)) {
                        val parsed = SyncedLyrics.parse(result.raw)
                        if (parsed.isNotEmpty()) {
                            publishSynced(
                                parsed = parsed,
                                plainFallback = parsed.joinToString("\n") { it.text },
                                provider = result.provider,
                                raw = result.raw,
                            )
                            return@withTimeout
                        }
                    }
                    publishPlain(result.raw.ifBlank { null }, result.provider)
                }
            } catch (_: TimeoutCancellationException) {
                Napier.w("Lyrics lookup timed out for $requestSongId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Lyrics lookup failed for $requestSongId: ${e.message}")
            } finally {
                // null means loading in the UI; always finish with an explicit empty result
                // solo si seguimos en la misma canción (si no, otro job ya tomó el relevo).
                if (stillCurrent() && _currentLyrics.value == null) {
                    _currentLyrics.value = ""
                    _syncedLyrics.value = null
                    _lyricsLoading.value = false
                    rememberLyrics(requestSongId, "", "None", notFound = true)
                }
                if (stillCurrent()) {
                    prefetchNextLyrics()
                }
            }
        }
    }

    private fun applyCachedLyrics(cached: CachedLyrics) {
        if (cached.notFound || cached.raw.isBlank()) {
            _syncedLyrics.value = null
            _currentLyrics.value = ""
            return
        }
        if (SyncedLyrics.isSynced(cached.raw)) {
            val parsed = SyncedLyrics.parse(cached.raw)
            if (parsed.isNotEmpty()) {
                _syncedLyrics.value = parsed
                _currentLyrics.value = parsed.joinToString("\n") { it.text }
                return
            }
        }
        _syncedLyrics.value = null
        _currentLyrics.value = cached.raw
    }

    private fun rememberLyrics(songId: String, raw: String, provider: String, notFound: Boolean) {
        val storeRaw = if (notFound) LyricsEntity.LYRICS_NOT_FOUND else raw
        lyricsMemoryCache[songId] = CachedLyrics(
            raw = if (notFound) "" else raw,
            provider = provider,
            notFound = notFound,
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                databaseDao.insertLyrics(songId, storeRaw, provider)
            }
        }
    }

    private data class RemoteLyrics(val raw: String, val provider: String)

    private suspend fun lookupLyricsRemote(song: MediaMetadata): RemoteLyrics? {
        val artist = song.artists.joinToString(", ") { it.name }
        val album = song.album?.title

        // Intentar proveedores sincronizados en orden de fiabilidad. El primer LRC utilizable gana.
        //   1) LrcLib  — gratuito, confiable, sincronizado por líneas
        //   2) KuGou   — sincronizado por líneas
        //   3) BetterLyrics — sincronizado por palabras
        //   4) YouTube lyrics endpoint
        suspend fun <T> attempt(block: suspend () -> T): T? = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        attempt { LrcLib.getLyrics(song.title, artist, song.duration, album).getOrNull() }
            ?.let { return RemoteLyrics(it, "LrcLib") }
        attempt { KuGou.getLyrics(song.title, artist, song.duration, album).getOrNull() }
            ?.let { return RemoteLyrics(it, "KuGou") }
        attempt { BetterLyrics.getLyrics(song.title, artist, song.duration, album) }
            ?.let { return RemoteLyrics(it, "BetterLyrics") }

        val nextResult = attempt { YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull() }
            ?: return null
        val endpoint = nextResult.lyricsEndpoint ?: return null
        val lyrics = attempt { YouTube.lyrics(endpoint).getOrNull() } ?: return null
        return RemoteLyrics(lyrics, "YouTube")
    }

    /** Prefetch de la siguiente canción de la cola (no bloquea UI ni pisa estado actual). */
    private fun prefetchNextLyrics() {
        val state = _uiState.value
        val queue = state.queue
        val index = state.currentIndex
        if (queue.isEmpty() || index < 0 || index >= queue.lastIndex) return
        val next = queue.getOrNull(index + 1) ?: return
        if (lyricsMemoryCache.containsKey(next.id)) return

        lyricsPrefetchJob?.cancel()
        lyricsPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbHit = databaseDao.getLyrics(next.id)
                if (dbHit != null) {
                    val notFound = dbHit.lyrics == LyricsEntity.LYRICS_NOT_FOUND || dbHit.lyrics.isBlank()
                    lyricsMemoryCache[next.id] = CachedLyrics(
                        raw = if (notFound) "" else dbHit.lyrics,
                        provider = dbHit.provider,
                        notFound = notFound,
                    )
                    return@launch
                }
                withTimeout(20_000L) {
                    val result = lookupLyricsRemote(next) ?: run {
                        rememberLyrics(next.id, "", "None", notFound = true)
                        return@withTimeout
                    }
                    val notFound = result.raw.isBlank()
                    rememberLyrics(next.id, result.raw, result.provider, notFound = notFound)
                }
            } catch (_: TimeoutCancellationException) {
                Napier.w("Lyrics prefetch timed out for ${next.id}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Lyrics prefetch failed for ${next.id}: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        resolveJob?.cancel()
        reconnectWatchJob?.cancel()
        lyricsJob?.cancel()
        lyricsPrefetchJob?.cancel()
        playRequestId += 1
        resolveJob = null
        playerService.stopAudioOnly()
        super.onCleared()
    }
}
