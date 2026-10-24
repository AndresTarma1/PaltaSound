@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)

package example.nucleus.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import example.nucleus.generated.resources.*

/**
 * Vistas del reproductor a pantalla completa, al estilo de Sonora: la primera no es una
 * pestaña sino la ausencia de panel — la portada a pantalla completa. Las otras dos
 * abren el panel lateral (letra o cola) cuando hay ancho, y ocupan toda la pantalla
 * cuando no.
 */
enum class NowPlayingTab {
    COVER,
    LYRICS,
    QUEUE;

    /** Si este estado muestra un panel junto a la portada. */
    val hasPanel: Boolean get() = this != COVER
}

/**
 * Última pestaña que el usuario abrió en Now Playing.
 *
 * Vive fuera de la composición a propósito: al cerrar la pantalla de reproducción y
 * volver a entrar se restaura la pestaña que se dejó, en vez de aparecer siempre en la
 * portada. Es estado de sesión — no toca disco ni sobrevive a reiniciar la aplicación.
 */
internal val rememberedNowPlayingTab = mutableStateOf(NowPlayingTab.COVER)

internal fun tabIcon(tab: NowPlayingTab) = when (tab) {
    NowPlayingTab.COVER -> Icons.Rounded.Album
    NowPlayingTab.LYRICS -> Icons.Rounded.Lyrics
    NowPlayingTab.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
}
