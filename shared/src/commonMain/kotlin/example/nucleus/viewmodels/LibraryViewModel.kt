package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.remote.ApiService
import example.nucleus.data.repository.AlbumRepository
import example.nucleus.data.repository.ArtistRepository
import example.nucleus.data.account.AccountManager
import example.nucleus.data.repository.PlaylistRepository
import example.nucleus.data.repository.PodcastRepository
import example.nucleus.data.repository.SongRepository
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.data.repository.savedAlbumToAlbumItem
import example.nucleus.data.repository.savedArtistToArtistItem
import example.nucleus.data.repository.savedPlaylistToPlaylistItem
import example.nucleus.data.repository.savedPodcastToPodcastItem
import example.nucleus.data.repository.savedSongToSongItem
import example.nucleus.db.MusicDatabase
import example.nucleus.platform.CsvFilePicker
import example.nucleus.utils.CsvPlaylistParser
import example.nucleus.utils.CsvSongRow
import example.nucleus.utils.withMissingMetadataResolved
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

enum class LibraryTab {
    LIBRARY, ALBUMS, ARTISTS, PLAYLISTS, PODCASTS
}

enum class LibrarySortOrder {
    NAME_ASC, NAME_DESC, DATE_ADDED
}

enum class YtmLibraryFilter(val filter: YouTube.LibraryFilter) {
    RECENT_ACTIVITY(YouTube.LibraryFilter.FILTER_RECENT_ACTIVITY),
    RECENTLY_PLAYED(YouTube.LibraryFilter.FILTER_RECENTLY_PLAYED),
    PLAYLISTS_AZ(YouTube.LibraryFilter.FILTER_PLAYLISTS_ALPHABETICAL),
    PLAYLISTS_RECENT(YouTube.LibraryFilter.FILTER_PLAYLISTS_RECENTLY_SAVED),
}

// Estado para el contenido remoto de YTM
sealed class YtmLibraryState {
    data object Idle : YtmLibraryState()
    data object Loading : YtmLibraryState()
    data class Success(
        val playlists: List<PlaylistItem> = emptyList(),
        val likedSongs: List<SongItem> = emptyList(),
        val albums: List<AlbumItem> = emptyList(),
        val artists: List<ArtistItem> = emptyList(),
        val podcasts: List<PodcastItem> = emptyList(),
    ) : YtmLibraryState()
    data class Error(val message: String) : YtmLibraryState()
}

class LibraryViewModel(
    private val apiService: ApiService,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val podcastRepository: PodcastRepository,
    private val loginState: StateFlow<Boolean>? = null
) : ViewModel() {

    private val _selectedTab = MutableStateFlow<LibraryTab?>(LibraryTab.LIBRARY)
    val selectedTab = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow<LibrarySortOrder>(LibrarySortOrder.NAME_ASC)
    val sortOrder = _sortOrder.asStateFlow()

    private val _selectedYtmFilter = MutableStateFlow<YtmLibraryFilter?>(null)
    val selectedYtmFilter = _selectedYtmFilter.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSortOrder(order: LibrarySortOrder) { _sortOrder.value = order }
    fun clearSearch() { _searchQuery.value = "" }
    fun setYtmFilter(filter: YtmLibraryFilter?) {
        _selectedYtmFilter.value = filter
        if (filter != null) loadYtmLibraryWithFilter(filter)
    }

    // Fuentes locales (SQLDelight). savedAlbums/savedPlaylists también las consume MusicOverlay.
    val savedAlbums = albumRepository.getSavedAlbums().map { it.map(::savedAlbumToAlbumItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val savedPlaylists = playlistRepository.getSavedPlaylists().map { it.map(::savedPlaylistToPlaylistItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val savedArtists = artistRepository.getSavedArtists().map { it.map(::savedArtistToArtistItem) }

    val continuation = MutableStateFlow<String?>(null)

    // ── Remote YTM (cuenta) ─────────────────────────────────

    private val _ytmState = MutableStateFlow<YtmLibraryState>(YtmLibraryState.Idle)
    val ytmState: StateFlow<YtmLibraryState> = _ytmState.asStateFlow()
    private var ytmLibraryRequested = false

    // ── Combinado (local + YTM) → filtrado → ordenado en UNA sola pasada ──
    //
    // Antes había hasta 5 StateFlow por categoría (saved*, ytm*, combined*, filtered*,
    // sortedFiltered*), y cada stateIn retenía una copia completa de la lista en memoria.
    // Ahora combine() calcula merged+filtrado+ordenado sobre la marcha y solo se persiste el
    // resultado final (una copia por categoría). La pipeline de canciones (savedSongs→
    // filteredSongs→sortedFilteredSongs) no la consume ninguna pantalla y se eliminó.

    val sortedFilteredAlbums = combine(savedAlbums, ytmState, searchQuery, sortOrder) { local, ytm, query, order ->
        mergedFilteredSorted(
            local, (ytm as? YtmLibraryState.Success)?.albums.orEmpty(),
            query, order, { it.id }, { it.title },
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sortedFilteredArtists = combine(savedArtists, ytmState, searchQuery, sortOrder) { local, ytm, query, order ->
        mergedFilteredSorted(
            local, (ytm as? YtmLibraryState.Success)?.artists.orEmpty(),
            query, order, { it.id }, { it.title },
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sortedFilteredPlaylists = combine(savedPlaylists, ytmState, searchQuery, sortOrder) { local, ytm, query, order ->
        mergedFilteredSorted(
            local, (ytm as? YtmLibraryState.Success)?.playlists.orEmpty(),
            query, order, { it.id }, { it.title },
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Los podcasts se guardan en la cuenta, pero tambien tienen copia local para que
    // aparezcan sin conexion. Se fusionan por id,MANDANDO la local: es la que tiene el
    // titulo y el autor cacheados del momento en que se guardo.
    val savedPodcasts = podcastRepository.getSavedPodcasts()
        .map { it.map(::savedPodcastToPodcastItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sortedFilteredPodcasts = combine(savedPodcasts, ytmState, searchQuery, sortOrder) { local, ytm, query, order ->
        mergedFilteredSorted(
            local, (ytm as? YtmLibraryState.Success)?.podcasts.orEmpty(),
            query, order, { it.id }, { it.title },
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun <T> mergedFilteredSorted(
        local: List<T>,
        remote: List<T>,
        query: String,
        order: LibrarySortOrder,
        idOf: (T) -> String,
        titleOf: (T) -> String,
    ): List<T> {
        val merged = (local + remote).distinctBy(idOf)
        val filtered = if (query.isBlank()) merged else merged.filter { titleOf(it).contains(query, ignoreCase = true) }
        return when (order) {
            LibrarySortOrder.NAME_ASC -> filtered.sortedBy(titleOf)
            LibrarySortOrder.NAME_DESC -> filtered.sortedByDescending(titleOf)
            LibrarySortOrder.DATE_ADDED -> filtered
        }
    }

    init {
        // La biblioteca remota se solicita solo cuando el usuario abre Library, no al arrancar.
        loginState?.onEach { isLoggedIn ->
                if (isLoggedIn && ytmLibraryRequested) loadYtmLibrary()
                else _ytmState.value = YtmLibraryState.Idle
            }?.launchIn(viewModelScope)
    }

    /** Solicita la biblioteca remota al entrar en la pantalla Library. */
    fun ensureYtmLibraryLoaded() {
        if (ytmLibraryRequested) return
        ytmLibraryRequested = true
        if (loginState?.value == true) loadYtmLibrary()
    }

    /** Carga la biblioteca remota de YouTube Music:
     *  - Playlists propias (FEmusic_liked_playlists)
     *  - Álbumes guardados (FEmusic_liked_albums)
     *  - Artistas suscritos (FEmusic_library_corpus_artists)
     *  - Podcasts guardados (FEmusic_library_non_music_audio_list)
     *
     *  Los podcasts van en un estante aparte del de música: no aparecen en ninguno de los
     *  tres anteriores, por eso hace falta pedirlo explícitamente.
     */
    fun loadYtmLibrary() {
        _ytmState.value = YtmLibraryState.Loading
        viewModelScope.launch {
            try {
                // Playlists
                val playlists = YouTube.library("FEmusic_liked_playlists")
                    .getOrNull()?.items?.filterIsInstance<PlaylistItem>() ?: emptyList()


                // Álbumes guardados
                val ytmAlbums = YouTube.library("FEmusic_liked_albums", tabIndex = 0)
                    .getOrNull()?.items?.filterIsInstance<AlbumItem>() ?: emptyList()

                // Artistas suscritos
                val ytmArtists = YouTube.library("FEmusic_library_corpus_artists", tabIndex = 0)
                    .getOrNull()?.items?.filterIsInstance<ArtistItem>() ?: emptyList()

                // Podcasts guardados
                val ytmPodcasts = fetchSavedPodcasts()

                _ytmState.value = YtmLibraryState.Success(
                    playlists = playlists,
                    likedSongs = emptyList(),
                    albums = ytmAlbums,
                    artists = ytmArtists,
                    podcasts = ytmPodcasts,
                )
            } catch (e: Exception) {
                _ytmState.value = YtmLibraryState.Error(e.message ?: "Error al cargar biblioteca")
            }
        }
    }

    /**
     * Podcasts guardados en la cuenta.
     *
     * El estante es `FEmusic_library_non_music_audio_list`, NO el de canales: el de
     * canales (`FEmusic_library_non_music_audio_channels_list`) devuelve los canales de
     * música suscritos como `USER_CHANNEL` y no contiene los podcasts guardados.
     * Este otro devuelve un grid con los accesos "Agregar podcast" / "Episodios para
     * después" y, detras, un `musicTwoRowItemRenderer` por podcast con `pageType`
     * `MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE`, que es lo que se convierte en
     * `PodcastItem`. Los dos primeros se descartan solos al filtrar por tipo.
     *
     * Un fallo aquí no debe tumbar el resto de la biblioteca.
     */
    private suspend fun fetchSavedPodcasts(): List<PodcastItem> =
        YouTube.libraryPodcastEpisodes()
            .getOrNull()
            ?.items
            ?.filterIsInstance<PodcastItem>()
            .orEmpty()

    /**
     * Recarga solo el estante de podcasts.
     *
     * Guardar un podcast es una llamada remota y no toca la base de datos local, así que
     * este ViewModel no se entera de nada: sin este refresco, uno guardado ahora no
     * aparecería hasta reiniciar la app.
     */
    fun refreshPodcasts() {
        if (loginState?.value != true) return
        val current = _ytmState.value as? YtmLibraryState.Success ?: return
        viewModelScope.launch {
            _ytmState.value = current.copy(podcasts = fetchSavedPodcasts())
        }
    }

    /** Carga la biblioteca remota usando un filtro específico de innertube */
    fun loadYtmLibraryWithFilter(filter: YtmLibraryFilter) {
        // Los filtros de innertube son de música: ningun estante devuelve podcasts, asi que
        // se capturan antes de pasar a Loading y se vuelven a poner debajo. Si no, al
        // cambiar de filtro la pestana de podcasts se quedaria vacia.
        val previousPodcasts = (_ytmState.value as? YtmLibraryState.Success)?.podcasts.orEmpty()
        _ytmState.value = YtmLibraryState.Loading
        viewModelScope.launch {
            try {
                val result = apiService.getLibraryWithFilter(filter.filter)
                val items = result.getOrNull()?.items.orEmpty()

                val playlists = items.filterIsInstance<PlaylistItem>()
                val albums = items.filterIsInstance<AlbumItem>()
                val artists = items.filterIsInstance<ArtistItem>()
                val songs = items.filterIsInstance<SongItem>()

                _ytmState.value = YtmLibraryState.Success(
                    playlists = playlists,
                    likedSongs = songs,
                    albums = albums,
                    artists = artists,
                    podcasts = previousPodcasts,
                )
            } catch (e: Exception) {
                _ytmState.value = YtmLibraryState.Error(e.message ?: "Error al cargar biblioteca")
            }
        }
    }

    // ── Tabs / local actions ────────────────────────────────

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
        // Al abrir la pestana se revalida el estante: es lo unico que puede haber cambiado
        // desde la ultima visita, porque guardar un podcast es remoto y no avisa a nadie.
        if (tab == LibraryTab.PODCASTS) refreshPodcasts()
    }

    fun selectMixedTab() { _selectedTab.value = LibraryTab.LIBRARY }

    fun removeSong(id: String) { viewModelScope.launch { songRepository.removeSong(id) } }
    fun removeAlbum(browseId: String) { viewModelScope.launch { albumRepository.removeAlbum(browseId) } }
    fun removeArtist(id: String) { viewModelScope.launch { artistRepository.removeArtist(id) } }
    fun removePlaylist(id: String) { viewModelScope.launch { playlistRepository.removePlaylist(id) } }

    /**
     * Crea una nueva playlist local
     */
    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylist(name: String) {
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = null,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            playlistRepository.savePlaylist(playlist)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylistWithSong(name: String, song: SongItem) {
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = song.thumbnail,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            playlistRepository.savePlaylistWithSongs(playlist, listOf(song))
        }
    }

    fun refreshYtmLibrary() = loadYtmLibrary()

    fun resolveAlbumSongsForPlayback(
        browseId: String,
        onResolved: (List<SongItem>) -> Unit,
        onFallback: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val songs = YouTube.album(browseId).getOrNull()?.songs.orEmpty()
                if (songs.isNotEmpty()) onResolved(songs) else onFallback()
            } catch (_: Exception) {
                onFallback()
            }
        }
    }

    fun resolvePlaylistSongsForPlayback(
        playlistId: String,
        onResolved: (List<SongItem>) -> Unit,
        onFallback: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val songs = YouTube.playlist(playlistId).getOrNull()?.songs.orEmpty()
                if (songs.isNotEmpty()) onResolved(songs) else onFallback()
            } catch (_: Exception) {
                onFallback()
            }
        }
    }

    /** Resuelve las canciones en caché local de una playlist LOCAL_ (usado por el overlay del juego). */
    fun resolveLocalPlaylistSongs(
        playlistId: String,
        onResolved: (List<SongItem>) -> Unit,
        onFallback: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val songs = playlistRepository.getCachedPlaylistSongs(playlistId).orEmpty()
            if (songs.isNotEmpty()) onResolved(songs) else onFallback()
        }
    }
}


class LibrarySongsViewModel(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val playlistRepository: PlaylistRepository,
    private val artistRepository: ArtistRepository,
) : ViewModel() {
    val savedSongs = songRepository.getSavedSongs().map { it.map(::savedSongToSongItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

class LibraryAlbumsViewModel(
    private val albumRepository: AlbumRepository,
) : ViewModel() {
    val savedAlbums = albumRepository.getSavedAlbums().map { it.map(::savedAlbumToAlbumItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

class LibraryArtistsViewModel(
    private val artistRepository: ArtistRepository,
) : ViewModel() {
    val savedArtists = artistRepository.getSavedArtists().map { it.map(::savedArtistToArtistItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

sealed class CsvImportState {
    data object Idle : CsvImportState()
    data class Ready(
        val suggestedName: String,
        val songs: List<CsvSongRow>,
        val totalCount: Int,
    ) : CsvImportState()
    data class Searching(
        val currentTitle: String,
        val found: Int,
        val total: Int,
    ) : CsvImportState()
    data class Done(
        val playlistName: String,
        val foundCount: Int,
        val totalCount: Int,
    ) : CsvImportState()
    data class Error(val message: String) : CsvImportState()
}

class LibraryPlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
    private val userPreferences: UserPreferencesRepository,
) : ViewModel() {
    val savedPlaylists = playlistRepository.getSavedPlaylists().map { it.map(::savedPlaylistToPlaylistItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val localPlaylists = savedPlaylists.map { playlists ->
        playlists.filter { it.id.startsWith("LOCAL_") }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _csvImportState = MutableStateFlow<CsvImportState>(CsvImportState.Idle)
    val csvImportState: StateFlow<CsvImportState> = _csvImportState.asStateFlow()

    private var importJob: kotlinx.coroutines.Job? = null

    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylist(name: String, song: SongItem? = null) {
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = song?.thumbnail,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            if (song == null) {
                playlistRepository.savePlaylist(playlist)
            } else {
                playlistRepository.savePlaylistWithSongs(playlist, listOf(song))
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylist(name: String, songs: List<SongItem>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = "${songs.size} canciones",
                thumbnail = songs.firstOrNull()?.thumbnail,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            playlistRepository.savePlaylistWithSongs(playlist, songs)
        }
    }

    fun addSongToLocalPlaylist(playlistId: String, song: SongItem) {
        viewModelScope.launch {
            val resolvedSong = withContext(Dispatchers.IO){ song.withMissingMetadataResolved()}
            playlistRepository.addSongToPlaylist(playlistId, resolvedSong)
            mirrorAddToYtm(playlistId, resolvedSong)
        }
    }

    fun removeSongFromLocalPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            // Leer el setVideoId antes de eliminar la fila local; necesario para la eliminación remota.
            val setVideoId = playlistRepository.getSetVideoId(playlistId, songId)
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
            mirrorRemoveFromYtm(playlistId, songId, setVideoId)
        }
    }

    // ── Sincronización con YouTube Music (experimental) ────────────────────────────────────
    // Refleja las ediciones de playlists locales en una playlist de YTM en la cuenta iniciada.
    // Es optimista y mejor esfuerzo: el cambio local nunca se revierte si la llamada remota falla.

    private suspend fun ytmSyncActive(): Boolean =
        userPreferences.ytmSyncEnabled.first() && AccountManager.loginState.value

    private suspend fun mirrorAddToYtm(localPlaylistId: String, song: SongItem) {
        if (!ytmSyncActive()) return
        // Las playlists que ya tienen un browseId son playlists reales de YouTube (guardadas desde YTM);
        // PlaylistRepository.addSongToPlaylist ya las sincroniza incondicionalmente. Reflejar aquí
        // también crearía una SEGUNDA playlist duplicada (resolveOrCreateRemotePlaylist no tiene
        // forma de saber que esta ya existe remotamente) y añadiría la canción dos veces.
        if (playlistRepository.getBrowseId(localPlaylistId) != null) return
        runCatching {
            withContext(Dispatchers.IO) {
                val remoteId = resolveOrCreateRemotePlaylist(localPlaylistId) ?: return@withContext
                val setVideoId = YouTube.addToPlaylist(remoteId, song.id).getOrNull()
                if (setVideoId != null) {
                    playlistRepository.updateSetVideoId(localPlaylistId, song.id, setVideoId)
                }
            }
        }.onFailure { Napier.e("[ytm-sync] add failed", it) }
    }

    private suspend fun mirrorRemoveFromYtm(localPlaylistId: String, songId: String, setVideoId: String?) {
        if (setVideoId == null || !ytmSyncActive()) return
        // Misma lógica que mirrorAddToYtm: las playlists vinculadas con browseId ya son
        // gestionadas por PlaylistRepository.removeSongFromPlaylist.
        if (playlistRepository.getBrowseId(localPlaylistId) != null) return
        val remoteId = userPreferences.getRemotePlaylistId(localPlaylistId) ?: return
        runCatching {
            withContext(Dispatchers.IO) { YouTube.removeFromPlaylist(remoteId, songId, setVideoId) }
        }.onFailure { Napier.e("[ytm-sync] remove failed", it) }
    }

    /** La playlist remota de YTM que refleja [localPlaylistId], creándola en el primer uso. */
    private suspend fun resolveOrCreateRemotePlaylist(localPlaylistId: String): String? {
        userPreferences.getRemotePlaylistId(localPlaylistId)?.let { return it }
        val name = playlistRepository.getCachedPlaylistItem(localPlaylistId)?.title ?: return null
        val remoteId = runCatching { YouTube.createPlaylist(name) }.getOrNull() ?: return null
        userPreferences.setRemotePlaylistId(localPlaylistId, remoteId)
        return remoteId
    }

    fun exportPlaylist(playlistId: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val item = playlistRepository.getCachedPlaylistItem(playlistId)
                val songs = playlistRepository.getCachedPlaylistSongs(playlistId).orEmpty()
                if (item == null || songs.isEmpty()) {
                    withContext(Dispatchers.Main) { onDone(false, "Playlist vacía") }
                    return@launch
                }
                val csv = buildString {
                    appendLine("Track Name,Artist Name,Album")
                    songs.forEach { s ->
                        val t = s.title.replace("\"", "\"\"")
                        val a = s.artists.joinToString(", ") { it.name }.replace("\"", "\"\"")
                        val al = s.album?.name?.replace("\"", "\"\"") ?: ""
                        appendLine("\"$t\",\"$a\",\"$al\"")
                    }
                }
                // Por ahora retornamos el CSV; la UI decide donde guardarlo (por ahora solo notifica)
                withContext(Dispatchers.Main) { onDone(true, csv) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onDone(false, e.message ?: "Error") }
            }
        }
    }

    fun importCsvFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CsvFilePicker.pickAndReadCsvFile() ?: return@launch
            val songs = CsvPlaylistParser.parse(result.content)
            withContext(Dispatchers.Main) {
                if (songs.isEmpty()) {
                    _csvImportState.value = CsvImportState.Error("No se encontraron canciones válidas en el archivo")
                } else {
                    _csvImportState.value = CsvImportState.Ready(
                        suggestedName = result.fileName,
                        songs = songs,
                        totalCount = songs.size,
                    )
                }
            }
        }
    }

    fun initiateCsvImport(suggestedName: String, songs: List<CsvSongRow>) {
        if (songs.isEmpty()) {
            _csvImportState.value = CsvImportState.Error("No songs to import")
            return
        }
        _csvImportState.value = CsvImportState.Ready(
            suggestedName = suggestedName,
            songs = songs,
            totalCount = songs.size,
        )
    }

    fun confirmCsvImport(playlistName: String) {
        val current = _csvImportState.value as? CsvImportState.Ready ?: return
        val songs = current.songs
        if (songs.isEmpty()) return

        importJob = viewModelScope.launch {
            _csvImportState.value = CsvImportState.Searching(
                currentTitle = songs.first().title,
                found = 0,
                total = songs.size,
            )

            val foundSongs = mutableListOf<SongItem>()
            val mutex = kotlinx.coroutines.sync.Mutex()
            val semaphore = Semaphore(6)
            // Procesar en chunks para no saturar y actualizar UI cada ~10
            for (chunk in songs.chunked(20)) {
                coroutineScope {
                    chunk.map { row ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                // ISRC primero si existe (1 hit), sino title+artist
                                val query = row.isrc?.takeIf { it.isNotBlank() } ?: "${row.title} ${row.artist}"
                                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                                result?.items?.firstOrNull { it is SongItem }?.let {
                                    mutex.withLock { foundSongs.add(it as SongItem) }
                                }
                            }
                        }
                    }.awaitAll()
                }
                // Batch UI update cada chunk
                val currentSize = mutex.withLock { foundSongs.size }
                _csvImportState.value = CsvImportState.Searching(
                    currentTitle = chunk.lastOrNull()?.title ?: "",
                    found = currentSize,
                    total = songs.size,
                )
            }

            if (foundSongs.isEmpty()) {
                _csvImportState.value = CsvImportState.Error("No se encontraron canciones en YouTube Music")
                return@launch
            }

            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = playlistName,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = foundSongs.firstOrNull()?.thumbnail,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
            playlistRepository.savePlaylistWithSongs(playlist, foundSongs)

            _csvImportState.value = CsvImportState.Done(
                playlistName = playlistName,
                foundCount = foundSongs.size,
                totalCount = songs.size,
            )
        }
    }

    fun dismissCsvImportResult() {
        _csvImportState.value = CsvImportState.Idle
    }

    fun cancelCsvImport() {
        importJob?.cancel()
        _csvImportState.value = CsvImportState.Idle
    }
}

class LibraryMixedViewModel(
    musicDatabase: MusicDatabase
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val database = musicDatabase.database

    var albums = database.savedAlbumQueries.selectAll()
    var playlists = database.playlistQueries.playlistsByNameAsc()
}
