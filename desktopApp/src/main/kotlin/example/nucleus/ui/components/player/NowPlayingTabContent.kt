@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.MediaInfo
import example.nucleus.generated.resources.*
import example.nucleus.models.MediaMetadata
import example.nucleus.navigation.Route
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.expressiveLayoutTween
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.viewmodels.PlayerUiState

/**
 * Contenido animado de cada pestaña dentro de Now Playing.
 */
@Composable
internal fun NowPlayingTabContent(
    tab: NowPlayingTab,
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    lyricsTextStyle: androidx.compose.ui.text.TextStyle,
    onNavigate: ((Route) -> Unit)?,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            if (animationsEnabled) {
                (fadeIn(expressiveFadeTween()) +
                    slideInVertically(animationSpec = expressiveLayoutTween()) { it / 24 })
                    .togetherWith(fadeOut(expressiveTween(140)))
            } else {
                fadeIn(expressiveTween(0)) togetherWith fadeOut(expressiveTween(0))
            }
        },
        label = "now_playing_tab_content",
        modifier = Modifier.fillMaxSize(),
    ) { targetTab ->
        when (targetTab) {
            // La vista de portada no tiene panel: la compone el layout, no este contenido.
            NowPlayingTab.COVER -> Unit
            NowPlayingTab.LYRICS -> LyricsContent(
                lyrics = lyrics,
                textAlign = TextAlign.Start,
                style = lyricsTextStyle,
            )
            NowPlayingTab.QUEUE -> NowPlayingQueuePanel(
                state = state,
                modifier = Modifier.fillMaxSize(),
                bottomInset = 0.dp,
            )
        }
    }
}
