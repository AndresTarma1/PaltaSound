package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.BackgroundStyle
import example.nucleus.data.repository.LyricsAnimationStyle
import example.nucleus.data.repository.NowPlayingDesign
import example.nucleus.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la sección "Now Playing" de Ajustes: pantalla completa, portadas y letras. */
class NowPlayingSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val highResCoverArt: StateFlow<Boolean> = preferencesRepository.highResCoverArt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val imagesEnabled: StateFlow<Boolean> = preferencesRepository.imagesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val crossfadeEnabled: StateFlow<Boolean> = preferencesRepository.crossfadeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val nowPlayingBackground: StateFlow<BackgroundStyle> = preferencesRepository.nowPlayingBackground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackgroundStyle.SOLID_COLOR)

    val nowPlayingDesign: StateFlow<NowPlayingDesign> = preferencesRepository.nowPlayingDesign
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NowPlayingDesign.YOUTUBE_MUSIC)

    val lyricsTextSize: StateFlow<Float> = preferencesRepository.lyricsTextSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 40f)

    val lyricsLineSpacing: StateFlow<Float> = preferencesRepository.lyricsLineSpacing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 54f)

    val lyricsAnimationStyle: StateFlow<LyricsAnimationStyle> = preferencesRepository.lyricsAnimationStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricsAnimationStyle.KARAOKE)

    val lyricsRomanize: StateFlow<Boolean> = preferencesRepository.lyricsRomanize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lyricsOffsetMs: StateFlow<Int> = preferencesRepository.lyricsOffsetMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val queuePersistenceEnabled: StateFlow<Boolean> = preferencesRepository.queuePersistenceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val nowPlayingAutoHide: StateFlow<Boolean> = preferencesRepository.nowPlayingAutoHide
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setHighResCoverArt(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHighResCoverArt(enabled) }
    }

    fun setImagesEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setImagesEnabled(enabled) }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCrossfadeEnabled(enabled) }
    }

    fun setLyricsTextSize(size: Float) {
        viewModelScope.launch { preferencesRepository.setLyricsTextSize(size) }
    }

    fun setLyricsLineSpacing(spacing: Float) {
        viewModelScope.launch { preferencesRepository.setLyricsLineSpacing(spacing) }
    }

    fun setLyricsAnimationStyle(style: LyricsAnimationStyle) {
        viewModelScope.launch { preferencesRepository.setLyricsAnimationStyle(style) }
    }

    fun setLyricsRomanize(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLyricsRomanize(enabled) }
    }

    fun setLyricsOffsetMs(offsetMs: Int) {
        viewModelScope.launch { preferencesRepository.setLyricsOffsetMs(offsetMs) }
    }

    fun setQueuePersistenceEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setQueuePersistenceEnabled(enabled) }
    }

    fun setNowPlayingAutoHide(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNowPlayingAutoHide(enabled) }
    }

    fun setNowPlayingDesign(design: NowPlayingDesign) {
        viewModelScope.launch { preferencesRepository.setNowPlayingDesign(design) }
    }

    fun setNowPlayingBackground(style: BackgroundStyle) {
        viewModelScope.launch { preferencesRepository.setNowPlayingBackground(style) }
    }
}
