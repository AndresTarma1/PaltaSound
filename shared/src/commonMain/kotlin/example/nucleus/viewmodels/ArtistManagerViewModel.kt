package example.nucleus.viewmodels

import androidx.lifecycle.viewModelScope
import example.nucleus.data.account.AccountManager
import example.nucleus.data.repository.ArtistRepository
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.utils.PendingAction
import example.nucleus.utils.PendingSyncQueue
import example.nucleus.utils.retryWithBackoff
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.ArtistPage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.logging.Logger

sealed class ArtistState {
    object Loading : ArtistState()
    data class Success(val artistPage: ArtistPage) : ArtistState()
    data class Error(val message: String, val isNotFound: Boolean = false) : ArtistState()
}
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistManagerViewModel(
    private val repository: ArtistRepository,
    private val userPreferences: UserPreferencesRepository,
    private val pendingSyncQueue: PendingSyncQueue,
) : ApplicationViewModel() {

    private val log = Logger.getLogger("ArtistViewModel")

    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow<ArtistState>(ArtistState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentBrowseId = MutableStateFlow<String?>(null)
    val currentBrowseId = _currentBrowseId.asStateFlow()
    val isSaved: StateFlow<Boolean> = _currentBrowseId
        .flatMapLatest{ id ->
            if (id == null) flowOf(false)
            else repository.isArtistSaved(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    fun loadArtist(browseId: String) {
        _currentBrowseId.value = browseId
        loadJob?.cancel()

        // Los ids sinteticos "local:..." no existen en YouTube: no hay nada que pedir.
        if (browseId.startsWith("local:")) {
            Napier.d("Artist browse skipped for synthetic id=$browseId")
            _uiState.value = ArtistState.Error("", isNotFound = true)
            return
        }

        loadJob = viewModelScope.launch {
            _uiState.value = ArtistState.Loading
            val result = YouTube.artist(browseId)
            if (!isActive) return@launch
            result
                .onSuccess {
                    _uiState.value = ArtistState.Success(it)
                }
                .onFailure {
                    Napier.w("Artist browse failed for id=$browseId: ${it.message}")
                    _uiState.value = ArtistState.Error(
                        it.message ?: "Error desconocido",
                        isNotFound = isInvalidArtistPage(it),
                    )
                }
        }
    }

    /**
     * YouTube responde 400/INVALID_ARGUMENT al pedir el browse de un canal que no
     * tiene página de artista (perfiles de usuario, uploaders sueltos): no es un
     * fallo de red, es que no hay nada que mostrar.
     */
    private fun isInvalidArtistPage(t: Throwable): Boolean {
        val message = t.message ?: return false
        return message.contains("INVALID_ARGUMENT") ||
            (message.contains("400") && message.contains("/browse"))
    }

    fun toggleSave() {
        val state = _uiState.value
        if (state !is ArtistState.Success) return

        viewModelScope.launch {
            val artist = state.artistPage.artist
            val wasSaved = isSaved.value
            if (wasSaved) {
                repository.removeArtist(artist.id)
            } else {
                repository.saveArtist(
                    artist = artist,
                    subscriberCount = state.artistPage.subscriberCountText
                )
            }

            // Solo local si no hay sesión o sync YTM off. Sin login no hay request remoto ni WARN.
            // Algunos artistas YTM no exponen channelId real; ahí tampoco se empuja.
            val channelId = artist.channelId
            val canPushRemote = channelId != null &&
                AccountManager.isLoggedIn &&
                userPreferences.ytmSyncEnabled.first()
            if (canPushRemote) {
                retryWithBackoff { YouTube.subscribeChannel(channelId, subscribe = !wasSaved) }
                    .onFailure {
                        Napier.w("Failed to push subscribe state for $channelId: ${it.message}")
                        pendingSyncQueue.enqueue(
                            PendingAction.SubscribeArtist(channelId, subscribed = !wasSaved),
                        )
                    }
            } else if (channelId != null && !AccountManager.isLoggedIn) {
                Napier.d("Artist follow kept local only (not logged in): ${artist.id}")
            }
        }
    }
}
