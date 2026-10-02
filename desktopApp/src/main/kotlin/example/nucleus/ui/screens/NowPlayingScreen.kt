package example.nucleus.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import example.nucleus.navigation.Route
import example.nucleus.ui.components.player.NowPlayingLayout
import example.nucleus.ui.components.player.NowPlayingTab
import example.nucleus.viewmodels.PlayerViewModel

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val playerState by viewModel.uiState.collectAsState()
    val currentLyrics by viewModel.currentLyrics.collectAsState()
    val currentSongMediaInfo by viewModel.currentMediaInfo.collectAsState()
    val currentSong = playerState.currentSong

    // Se abre en la portada, no en la letra: es la vista que muestra la caratula a
    // pantalla completa, y la letra/cola se eligen desde la pill. Sonora hace lo mismo.
    var selectedTab by remember { mutableStateOf(NowPlayingTab.COVER) }

    if (currentSong != null) {

            NowPlayingLayout(
                state = playerState,
                song = currentSong,
                onCollapse = onBack,
                onNavigate = { route ->
                    onNavigate(route)
                },
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                lyrics = currentLyrics,
                mediaInfo = currentSongMediaInfo,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }

}
