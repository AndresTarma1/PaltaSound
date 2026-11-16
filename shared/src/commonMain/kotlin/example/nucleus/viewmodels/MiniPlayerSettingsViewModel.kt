package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.MiniPlayerBackgroundStyle
import example.nucleus.data.repository.MiniPlayerDesign
import example.nucleus.data.repository.MiniPlayerStyle
import example.nucleus.data.repository.SeekBarStyle
import example.nucleus.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la sección "Mini reproductor" de Ajustes: estilo, fondo y barra de progreso. */
class MiniPlayerSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val miniPlayerStyle: StateFlow<MiniPlayerStyle> = preferencesRepository.miniPlayerStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiniPlayerStyle.BAR)

    val miniPlayerBackgroundStyle: StateFlow<MiniPlayerBackgroundStyle> = preferencesRepository.miniPlayerBackgroundStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiniPlayerBackgroundStyle.TRANSLUCENT)

    val miniPlayerDesign: StateFlow<MiniPlayerDesign> = preferencesRepository.miniPlayerDesign
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiniPlayerDesign.EXPRESSIVE)

    val seekBarStyle: StateFlow<SeekBarStyle> = preferencesRepository.seekBarStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeekBarStyle.WAVY)

    fun setMiniPlayerStyle(style: MiniPlayerStyle) {
        viewModelScope.launch { preferencesRepository.setMiniPlayerStyle(style) }
    }

    fun setMiniPlayerDesign(design: MiniPlayerDesign) {
        viewModelScope.launch { preferencesRepository.setMiniPlayerDesign(design) }
    }

    fun setMiniPlayerBackgroundStyle(style: MiniPlayerBackgroundStyle) {
        viewModelScope.launch { preferencesRepository.setMiniPlayerBackgroundStyle(style) }
    }

    fun setSeekBarStyle(style: SeekBarStyle) {
        viewModelScope.launch { preferencesRepository.setSeekBarStyle(style) }
    }
}
