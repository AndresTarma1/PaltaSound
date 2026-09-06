package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.AudioQuality
import example.nucleus.data.repository.LoudnessLevel
import example.nucleus.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la sección "Audio" de Ajustes: calidad de streaming, ecualizador y normalización. */
class AudioSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val audioQuality: StateFlow<AudioQuality> = preferencesRepository.audioQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.NORMAL)

    val equalizerBands: StateFlow<List<Float>> = preferencesRepository.equalizerBands
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(5) { 0f })

    val loudnessLevel: StateFlow<LoudnessLevel> = preferencesRepository.loudnessLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoudnessLevel.OFF)

    val sabrEnabled: StateFlow<Boolean> = preferencesRepository.sabrEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch { preferencesRepository.setAudioQuality(quality) }
    }

    fun setEqualizerBands(bands: List<Float>) {
        viewModelScope.launch { preferencesRepository.setEqualizerBands(bands) }
    }

    fun setLoudnessLevel(level: LoudnessLevel) {
        viewModelScope.launch { preferencesRepository.setLoudnessLevel(level) }
    }

    fun setSabrEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSabrEnabled(enabled) }
    }
}
