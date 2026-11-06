package example.nucleus.viewmodels

import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.BrowseResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class YouTubeBrowseState {
    object Loading : YouTubeBrowseState()
    data class Success(val result: BrowseResult) : YouTubeBrowseState()
    data class Error(val message: String) : YouTubeBrowseState()
}

class YouTubeBrowseManagerViewModel : ApplicationViewModel() {

    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow<YouTubeBrowseState>(YouTubeBrowseState.Loading)
    val uiState: StateFlow<YouTubeBrowseState> = _uiState.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    fun load(browseId: String, params: String?) {
        _uiState.value = YouTubeBrowseState.Loading
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            val result = YouTube.browse(browseId, params)
            if (!isActive) return@launch
            result
                .onSuccess { result ->
                    _title.value = result.title ?: ""
                    _uiState.value = YouTubeBrowseState.Success(result)
                }
                .onFailure { error ->
                    _uiState.value = YouTubeBrowseState.Error(
                        error.message ?: "Error al cargar contenido"
                    )
                }
        }
    }
}
