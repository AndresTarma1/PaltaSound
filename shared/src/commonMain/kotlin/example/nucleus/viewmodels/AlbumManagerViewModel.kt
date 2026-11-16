package example.nucleus.viewmodels

import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.AlbumRepository
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.logging.Logger

sealed class AlbumState {
    object Loading : AlbumState()
    data class Success(
        val albumPage: AlbumPage,
        val isSaved: Boolean = false,
        val isSaving: Boolean = false,
        val isLoadingForPlay: Boolean = false,
    ) : AlbumState()
    data class Error(val message: String) : AlbumState()
}

class AlbumManagerViewModel(
    private val repository: AlbumRepository,
    private val playerCoordinator: PlayerCoordinator,
) : ApplicationViewModel() {

    private val log = Logger.getLogger("AlbumViewModel")

    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow<AlbumState>(AlbumState.Loading)
    val uiState: StateFlow<AlbumState> = _uiState.asStateFlow()

    private val _currentBrowseId = MutableStateFlow<String?>(null)

    private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
    val songs: StateFlow<List<SongItem>> = _songs.asStateFlow()

    private val _continuation = MutableStateFlow<String?>(null)

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val hasMoreSongs: StateFlow<Boolean> = _continuation
        .map { token -> token != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private inline fun updateSuccess(transform: AlbumState.Success.() -> AlbumState.Success) {
        _uiState.update { state ->
            if (state is AlbumState.Success) state.transform() else state
        }
    }

    fun loadAlbum(browseId: String, force: Boolean = false) {
        if (!force && _currentBrowseId.value == browseId) return
        _currentBrowseId.value = browseId
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            val cachedSongs = repository.getCachedAlbumSongs(browseId)
            val cachedAlbum = repository.getCachedAlbumItem(browseId)
            val hasCache = cachedSongs != null && cachedAlbum != null

            if (hasCache) {
                log.info("Cargando álbum desde caché: $browseId (${cachedSongs.size} canciones)")
                _songs.value = cachedSongs
                _continuation.value = null
                _uiState.value = AlbumState.Success(
                    albumPage = AlbumPage(
                        album = cachedAlbum,
                        songs = cachedSongs,
                        otherVersions = emptyList()
                    ),
                    isSaved = true,
                )
                if (!force) return@launch
            } else {
                _uiState.value = AlbumState.Loading
                _songs.value = emptyList()
                _continuation.value = null
            }

            log.info("Recargando álbum desde YouTube: $browseId")
            val result = YouTube.album(browseId)
            if (!isActive) return@launch
            result
                .onSuccess { page ->
                    _songs.value = page.songs
                    val saved = repository.isAlbumSavedOnce(browseId)
                    _uiState.value = AlbumState.Success(
                        albumPage = page,
                        isSaved = saved,
                    )
                    if (saved) {
                        repository.saveAlbumWithSongs(page.album, page.songs)
                    }
                }
                .onFailure {
                    if (!hasCache) {
                        _uiState.value = AlbumState.Error(it.message ?: "Error desconocido")
                    }
                }
        }
    }

    fun loadMoreSongs() {
        val token = _continuation.value ?: return
        if (_isLoadingMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            var success = false

            repeat(3) { attempt ->
                if (success) return@repeat
                YouTube.playlistContinuation(token)
                .onSuccess { page ->
                        if (page.songs.isNotEmpty()) _songs.value += page.songs
                        _continuation.value = page.continuation?.takeIf { it.isNotBlank() }
                        success = true
                    }
                    .onFailure { delay(500L * (attempt + 1)) }
            }

            if (!success) log.warning("Falló la carga de más canciones tras 3 intentos")
            _isLoadingMore.value = false
        }
    }

    private suspend fun fetchAllRemainingPages(): List<SongItem> {
        var token = _continuation.value
        while (token != null) {
            var nextToken: String? = null
            var fetched = false

            repeat(3) { attempt ->
                if (fetched) return@repeat
                YouTube.playlistContinuation(token)
                    .onSuccess { page ->
                        if (page.songs.isNotEmpty()) _songs.value += page.songs
                        nextToken = page.continuation?.takeIf { it.isNotBlank() }
                        fetched = true
                    }
                    .onFailure {
                        log.warning("Error cargando continuación (intento ${attempt + 1}): ${it.message}")
                        delay(500L * (attempt + 1))
                    }
            }

            if (!fetched) {
                log.warning("No se pudo cargar una página, abortando")
                break
            }

            token = nextToken
            _continuation.value = token
        }
        return _songs.value
    }

    fun playAllSongs(shuffle: Boolean = false) {
        val state = _uiState.value as? AlbumState.Success ?: return
        if (state.isLoadingForPlay) return
        // Siempre reproducir la lista finita de canciones del álbum (sin recomendaciones/automix)
        loadAndPlayAll(shuffle, state)
    }

    private fun loadAndPlayAll(shuffle: Boolean, state: AlbumState.Success) {
        viewModelScope.launch {
            val allSongs = if (_continuation.value != null) {
                log.info("Cargando todas las páginas antes de reproducir álbum...")
                updateSuccess { copy(isLoadingForPlay = true) }
                try {
                    fetchAllRemainingPages()
                } finally {
                    updateSuccess { copy(isLoadingForPlay = false) }
                }
            } else {
                _songs.value
            }

            if (allSongs.isEmpty()) return@launch
            val finalList = if (shuffle) allSongs.shuffled() else allSongs
            playerCoordinator.playAlbum(
                songs = finalList,
                startIndex = 0,
                browseId = state.albumPage.album.browseId,
                title = state.albumPage.album.title,
            )
        }
    }

    fun toggleSave() {
        val state = _uiState.value as? AlbumState.Success ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            if (state.isSaved) {
                repository.removeAlbum(state.albumPage.album.browseId)
                updateSuccess { copy(isSaved = false) }
                log.info("Álbum eliminado de guardados: ${state.albumPage.album.browseId}")
            } else {
                updateSuccess { copy(isSaving = true) }
                try {
                    repository.saveAlbumWithSongs(state.albumPage.album, _songs.value)
                    updateSuccess { copy(isSaved = true) }
                    log.info("Álbum guardado con ${_songs.value.size} canciones: ${state.albumPage.album.browseId}")
                } finally {
                    updateSuccess { copy(isSaving = false) }
                }
            }
        }
    }
}
