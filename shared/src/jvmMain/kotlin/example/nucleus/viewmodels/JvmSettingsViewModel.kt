package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.JvmConfigRepository
import example.nucleus.data.repository.RenderApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JvmSettingsUiState(
    val xmx: String = "512m",
    val xms: String = "64m",
    val useG1GC: Boolean = true,
    val useZGC: Boolean = false,
    val gcLogging: Boolean = false,
    val renderApi: RenderApi = RenderApi.DIRECTX,
)

class JvmSettingsViewModel(
    private val jvmConfigRepository: JvmConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JvmSettingsUiState())
    val uiState: StateFlow<JvmSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            jvmConfigRepository.config.collect { config ->
                _uiState.update {
                    it.copy(
                        xmx = config.xmx,
                        xms = config.xms,
                        useG1GC = config.useG1GC,
                        useZGC = config.useZGC,
                        gcLogging = config.gcLogging,
                        renderApi = config.renderApi,
                    )
                }
            }
        }
    }

    fun setRenderApi(renderApi: RenderApi) {
        _uiState.update { it.copy(renderApi = renderApi) }
    }

    suspend fun saveRenderApi(): Boolean {
        jvmConfigRepository.updateRenderApi(_uiState.value.renderApi)
        return true
    }
}
