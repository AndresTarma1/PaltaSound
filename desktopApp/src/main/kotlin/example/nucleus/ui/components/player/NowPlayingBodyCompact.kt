@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import example.nucleus.navigation.ScreenConfig
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.expressiveLayoutTween
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.utils.LocalAnimationsEnabled
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
    onNavigate: ((ScreenConfig) -> Unit)?,
    onCollapse: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    autoHide: NowPlayingAutoHide,
) {
    val hasPanel = selectedTab.hasPanel
    val animationsEnabled = LocalAnimationsEnabled.current

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
            // Al cambiar de pestaña la portada se va hacia la izquierda con fade
            // y el panel entra desde la derecha (y viceversa al volver): la
            // dirección de la transición depende del destino.
            // El morph de la carátula grande a la pequeña del chrome lo pone el
            // overlay compartido (misma clave en ambas).
            AnimatedContent(
                targetState = hasPanel,
                transitionSpec = {
                    if (!animationsEnabled) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (targetState) {
                        // → panel (letras/cola): contenido entra de la derecha,
                        // la portada sale hacia la izquierda.
                        (fadeIn(expressiveFadeTween()) +
                            slideInHorizontally(animationSpec = expressiveLayoutTween()) { it / 4 })
                            .togetherWith(
                                fadeOut(expressiveTween(200)) +
                                    slideOutHorizontally(animationSpec = expressiveLayoutTween()) { -it / 3 },
                            )
                    } else {
                        // → portada: vuelve desde la izquierda, el panel sale a la derecha.
                        (fadeIn(expressiveFadeTween()) +
                            slideInHorizontally(animationSpec = expressiveLayoutTween()) { -it / 4 })
                            .togetherWith(
                                fadeOut(expressiveTween(200)) +
                                    slideOutHorizontally(animationSpec = expressiveLayoutTween()) { it / 4 },
                            )
                    }
                },
                label = "nowPlayingCompactPane",
            ) { showPanel ->
                if (showPanel) {
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
                            modifier = Modifier
                                // Shared ANTES del tamaño (guía oficial), para que
                                // los bounds del morph sean los reales del elemento.
                                .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope, debugTag = "np-compact")
                                .size(coverSize)
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
            // Mini carátula + metadatos cuando hay panel: entra desde la izquierda
            // acompañando al morph compartido de la portada grande.
            AnimatedVisibility(
                visible = hasPanel,
                enter = if (animationsEnabled) {
                    fadeIn(expressiveFadeTween()) +
                        slideInHorizontally(animationSpec = expressiveLayoutTween()) { -it / 2 }
                } else {
                    EnterTransition.None
                },
                exit = if (animationsEnabled) {
                    fadeOut(expressiveTween(180)) +
                        slideOutHorizontally(animationSpec = expressiveLayoutTween()) { -it / 2 }
                } else {
                    ExitTransition.None
                },
                label = "nowPlayingChromeRow",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(
                        url = song.thumbnailUrl,
                        title = song.title,
                        modifier = Modifier
                            .size(48.dp)
                            // Sin clave compartida: la portada grande ya la tiene y
                            // dos nodos con la misma clave harían saltar el overlay.
                            // Aquí basta la entrada por slide/fade.
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
