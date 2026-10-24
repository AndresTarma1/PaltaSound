package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.BackgroundStyle
import example.nucleus.data.repository.DarkLevel
import example.nucleus.data.repository.IslandStyle
import example.nucleus.data.repository.CornerStyle
import example.nucleus.data.repository.LayoutMode
import example.nucleus.data.repository.NavigationRailStyle
import example.nucleus.data.repository.ThemeMode
import example.nucleus.data.repository.ThemePalette
import example.nucleus.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la sección "Apariencia" de Ajustes: tema, layout, fondo, escala y fuente. */
class AppearanceSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val darkLevel: StateFlow<DarkLevel> = preferencesRepository.darkLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DarkLevel.DIM)

    val layoutMode: StateFlow<LayoutMode> = preferencesRepository.layoutMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LayoutMode.ATTACHED)

    val islandStyle: StateFlow<IslandStyle> = preferencesRepository.islandStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IslandStyle.COMFORTABLE)

    val navigationRailStyle: StateFlow<NavigationRailStyle> = preferencesRepository.navigationRailStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavigationRailStyle.DEFAULT)

    val themePalette: StateFlow<ThemePalette> = preferencesRepository.themePalette
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePalette.DEFAULT)

    val dynamicColorFromArtwork: StateFlow<Boolean> = preferencesRepository.dynamicColorFromArtwork
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appBackgroundStyle: StateFlow<BackgroundStyle> = preferencesRepository.appBackgroundStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackgroundStyle.BLURRED_COVER)

    val uiScale: StateFlow<Float> = preferencesRepository.uiScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val selectedFont: StateFlow<String> = preferencesRepository.selectedFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val animationsEnabled: StateFlow<Boolean> = preferencesRepository.animationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDarkLevel(level: DarkLevel) {
        viewModelScope.launch { preferencesRepository.setDarkLevel(level) }
    }

    fun setLayoutMode(mode: LayoutMode) {
        viewModelScope.launch { preferencesRepository.setLayoutMode(mode) }
    }

    val cornerStyle: StateFlow<CornerStyle> = preferencesRepository.cornerStyle
.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CornerStyle.SOFT)

fun setIslandStyle(style: IslandStyle) {
        viewModelScope.launch { preferencesRepository.setIslandStyle(style) }
    }

    fun setCornerStyle(style: CornerStyle) {
        viewModelScope.launch { preferencesRepository.setCornerStyle(style) }
    }

    fun setNavigationRailStyle(style: NavigationRailStyle) {
        viewModelScope.launch { preferencesRepository.setNavigationRailStyle(style) }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch { preferencesRepository.setThemePalette(palette) }
    }

    fun setDynamicColorFromArtwork(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColorFromArtwork(enabled) }
    }

    fun setAppBackgroundStyle(style: BackgroundStyle) {
        viewModelScope.launch { preferencesRepository.setAppBackgroundStyle(style) }
    }

    fun setUiScale(scale: Float) {
        viewModelScope.launch { preferencesRepository.setUiScale(scale) }
    }

    fun setSelectedFont(name: String) {
        viewModelScope.launch { preferencesRepository.setSelectedFont(name) }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAnimationsEnabled(enabled) }
    }
}
