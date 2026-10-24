@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.MediaInfo
import example.nucleus.generated.resources.*
import example.nucleus.generated.resources.Res
import example.nucleus.models.MediaMetadata
import example.nucleus.navigation.Route
import example.nucleus.viewmodels.PlayerUiState
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

@Composable
internal fun NowPlayingCompactBody(
    state: PlayerUiState,
    song: MediaMetadata,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    selectedTab: NowPlayingTab,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    coverScale: Animatable<Float, *>,
    coverAlpha: Animatable<Float, *>,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    autoHide: NowPlayingAutoHide,
) {
    val hasPanel = selectedTab.hasPanel

    // Layout vertical tipo móvil: el contenido scrolleable (letra, cola) manda y ocupa
    // todo el espacio; el chrome se ancla abajo.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (hasPanel) {
                NowPlayingTabContent(
                    tab = selectedTab,
                    song = song,
                    state = state,
                    lyrics = lyrics,
                    mediaInfo = mediaInfo,
                    lyricsTextStyle = MaterialTheme.typography.bodyLarge,
                    onNavigate = onNavigate,
                )
            } else {
                // Vista de portada en vertical: carátula grande arriba, metadatos debajo.
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val coverSize = minOf(maxWidth * 0.86f, maxHeight - 132.dp, 320.dp)
                        .coerceAtLeast(96.dp)
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CoverArt(
                            url = song.thumbnailUrl,
                            title = song.title,
                            videoId = song.id,
                            modifier = Modifier
                                .size(coverSize)
                                .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                                .coverEnter(coverScale, coverAlpha)
                                .autoHideCoverGrow(autoHide, restScale = 1.1f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        NowPlayingSongDetails(
                            state = state,
                            song = song,
                            textAlign = TextAlign.Center,
                            onNavigate = onNavigate,
                            onCollapse = onCollapse,
                            compact = true,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chrome anclado abajo: pista, progreso y transporte. La carátula aquí solo
        // aparece cuando hay panel — con la vista de portada ya está arriba.
        // Acotado y centrado: en ventanas alargadas la barra no debe ir de borde a borde.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally)
                .autoHideChrome(autoHide, sink = 18.dp)
                .onHover { autoHide.chromeHover = it },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (hasPanel) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(
                        url = song.thumbnailUrl,
                        title = song.title,
                        videoId = song.id,
                        modifier = Modifier
                            .size(48.dp)
                            .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                            .coverEnter(coverScale, coverAlpha),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    NowPlayingSongDetails(
                        state = state,
                        song = song,
                        textAlign = TextAlign.Start,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            PlayerProgressRow()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PlayerTransportRow()
                // Se usa el botón que despliega el slider en un popup, que en el
                // layout ancho ya está en uso.
                Spacer(Modifier.width(4.dp))
                PlayerVolumeVertical(
                    trackHeight = 88.dp,
                    onBusyChange = { autoHide.volumePopup = it },
                )
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(40.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(Res.string.mp_collapse),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}
