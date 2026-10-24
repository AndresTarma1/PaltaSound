package example.nucleus.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import example.nucleus.navigation.Route
import example.nucleus.ui.components.player.NowPlayingLayout
import example.nucleus.ui.components.player.rememberedNowPlayingTab
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

    // Se restaura la ultima pestaña que el usuario dejo al cerrar Now Playing. Arranca en
    // la portada solo la primera vez de cada sesion. El estado vive fuera de la
    // composicion (rememberedNowPlayingTab), asi que sobrevive a salir de la pantalla.
    var selectedTab by rememberedNowPlayingTab

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
