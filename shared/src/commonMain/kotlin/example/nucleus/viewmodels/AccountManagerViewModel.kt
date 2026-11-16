package example.nucleus.viewmodels

import androidx.lifecycle.viewModelScope
import example.nucleus.data.account.AccountManager
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.db.DatabaseDao
import example.nucleus.utils.SyncUtils
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.PlaylistItem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ─── Estados de la interfaz ────────────────────────────────────────────────

sealed class AccountState {
    data object NotLoggedIn : AccountState()
    data object Loading : AccountState()
    data class LoggedIn(
        val accountInfo: AccountInfo,
        val playlists: List<PlaylistItem> = emptyList(),
        val isLoadingPlaylists: Boolean = false,
    ) : AccountState()
    data class Error(val message: String) : AccountState()
    /** Cookie guardada pero expirada — pedir al usuario que la renueve */
    data object CookieExpired : AccountState()
}

// ─── ViewModel ────────────────────────────────────────────────

class AccountManagerViewModel(
    private val databaseDao: DatabaseDao,
    private val userPreferences: UserPreferencesRepository,
    private val syncUtils: SyncUtils,
) : ApplicationViewModel() {

    /**
     * Obtiene la biblioteca de la cuenta (canciones/álbumes/artistas favoritas, playlists guardadas)
     * después de un inicio de sesión exitoso o restauración de sesión — anteriormente nada activaba esto.
     * También reconcilia las listas de canciones de las playlists vinculadas a YouTube cuando
     * "Sincronizar con YouTube Music" está activado.
     *
     * Limitado por enfriamiento: este ViewModel es una fábrica de Koin que se recrea cada vez que
     * el usuario navega a la pantalla de Cuenta, así que sin un enfriamiento una sincronización completa
     * (más una recarga completa de playlist-canciones cuando ytmSyncEnabled está activo) accedería
     * a YouTube en cada visita.
     */
    private suspend fun triggerPostLoginSync() {
        val now = System.currentTimeMillis()
        val last = userPreferences.lastFullSyncAt.first()
        if (now - last < SYNC_COOLDOWN_MS) return
        userPreferences.setLastFullSyncAt(now)

        syncUtils.performFullSync()
        if (userPreferences.ytmSyncEnabled.first()) {
            syncUtils.syncAutoSyncPlaylists()
        }
    }

    /**
     * Detecta un cambio de cuenta de YouTube y limpia la biblioteca de la cuenta anterior para que
     * no se mezcle con la nueva (por ejemplo, la "Música Favorita" guardada de la cuenta antigua).
     * Mantiene las playlists locales, descargas e historial local. No hace nada en el primer inicio
     * de sesión o cuando la cuenta no ha cambiado.
     */
    private suspend fun handleAccountChange(info: AccountInfo) {
        val currentId = info.email ?: info.channelHandle ?: info.name
        if (currentId.isBlank()) return
        val lastId = userPreferences.lastAccountId.first()
        if (lastId.isNotBlank() && lastId != currentId) {
            runCatching { databaseDao.clearAccountLibrary() }
                .onSuccess { Napier.i("Account changed; cleared previous account's library") }
                .onFailure { Napier.w("Failed clearing account library on switch: ${it.message}") }
        }
        if (lastId != currentId) userPreferences.setLastAccountId(currentId)
    }

    private val _uiState = MutableStateFlow<AccountState>(AccountState.NotLoggedIn)
    val uiState: StateFlow<AccountState> = _uiState.asStateFlow()

    private val _cookieInput = MutableStateFlow("")
    val cookieInput: StateFlow<String> = _cookieInput.asStateFlow()

    /** Advertencias de diagnóstico sobre la cookie pegada (claves faltantes, longitud, etc.) */
    private val _cookieWarnings = MutableStateFlow<List<String>>(emptyList())
    val cookieWarnings: StateFlow<List<String>> = _cookieWarnings.asStateFlow()

    init {
        if (AccountManager.isLoggedIn) {
            loadAccountInfo()
        }
    }

    fun onCookieInputChange(value: String) {
        _cookieInput.value = value
        _cookieWarnings.value = if (value.isBlank()) emptyList() else AccountManager.diagnose(value)
    }

    fun login() {
        val cookie = _cookieInput.value.trim()
        if (cookie.isBlank()) return
        _cookieWarnings.value = AccountManager.diagnose(cookie)
        loginInternal(cookie)
    }

    fun loginWithCookie(cookie: String) {
        if (cookie.isBlank()) return
        _cookieInput.value = cookie
        _cookieWarnings.value = emptyList()
        loginInternal(cookie)
    }

    private fun loginInternal(cookie: String) {
        _uiState.value = AccountState.Loading

        AccountManager.setCookie(cookie)

        viewModelScope.launch {
            if (YouTube.visitorData == null) {
                YouTube.visitorData().onSuccess { YouTube.visitorData = it }
            }

            YouTube.accountInfo()
                .onSuccess { info ->
                    _uiState.value = AccountState.LoggedIn(
                        accountInfo = info, isLoadingPlaylists = true
                    )
                    handleAccountChange(info)
                    loadPlaylists()
                    triggerPostLoginSync()
                }
                .onFailure { error ->
                    AccountManager.clearCookie()
                    _cookieInput.value = cookie
                    val msg = error.message ?: ""
                    val isNetworkError = msg.contains("timeout", true) ||
                            msg.contains("unreachable", true) ||
                            msg.contains("Unable to resolve host", true) ||
                            msg.contains("Network is unreachable", true) ||
                            msg.contains("connect", true)
                    _uiState.value = AccountState.Error(
                        if (isNetworkError) "Sin conexión a Internet. Verifica tu conexión e intenta de nuevo."
                        else "Cookie inválida o expirada. Verifica que la cookie sea correcta."
                    )
                }
        }
    }

    fun logout() {
        AccountManager.clearCookie()
        _cookieInput.value = ""
        _cookieWarnings.value = emptyList()
        _uiState.value = AccountState.NotLoggedIn
    }

    fun reset() {
        AccountManager.clearCookie()
        _cookieInput.value = ""
        _cookieWarnings.value = emptyList()
        _uiState.value = AccountState.NotLoggedIn
    }

    fun retry() {
        if (AccountManager.isLoggedIn) {
            loadAccountInfo()
        } else {
            _uiState.value = AccountState.NotLoggedIn
        }
    }

    /** Llamado desde cualquier parte de la app cuando detecta un 401/403 */
    fun onAuthError() {
        if (_uiState.value is AccountState.LoggedIn) {
            _uiState.value = AccountState.CookieExpired
        }
    }

    private fun loadAccountInfo() {
        _uiState.value = AccountState.Loading
        viewModelScope.launch {
            YouTube.accountInfo()
                .onSuccess { info ->
                    _uiState.value = AccountState.LoggedIn(
                        accountInfo = info,
                        isLoadingPlaylists = true
                    )
                    handleAccountChange(info)
                    loadPlaylists()
                }
                .onFailure { err ->
                    val isAuthError = isAuthenticationError(err)
                    _uiState.value = if (isAuthError) {
                        AccountState.CookieExpired
                    } else {
                        AccountState.Error(err.message ?: "No se pudo obtener la información de la cuenta")
                    }
                }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            YouTube.library("FEmusic_liked_playlists")
                .onSuccess { page ->
                    val currentState = _uiState.value
                    if (currentState is AccountState.LoggedIn) {
                        _uiState.value = currentState.copy(
                            playlists = page.items.filterIsInstance<PlaylistItem>(),
                            isLoadingPlaylists = false
                        )
                    }
                }
                .onFailure { err ->
                    val currentState = _uiState.value
                    if (currentState is AccountState.LoggedIn) {
                        if (isAuthenticationError(err)) {
                            _uiState.value = AccountState.CookieExpired
                        } else {
                            _uiState.value = currentState.copy(isLoadingPlaylists = false)
                        }
                    }
                }
        }
    }

    fun refreshPlaylists() {
        val currentState = _uiState.value
        if (currentState !is AccountState.LoggedIn) return
        _uiState.value = currentState.copy(isLoadingPlaylists = true)
        loadPlaylists()
    }

    companion object {
        private const val SYNC_COOLDOWN_MS = 30 * 60 * 1000L

        fun isAuthenticationError(err: Throwable): Boolean {
            val msg = err.message ?: ""
            return msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true)
        }
    }
}
