package example.nucleus.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.ui.graphics.vector.ImageVector


sealed class Route {

    abstract val label: String?
    abstract val icon: ImageVector?

    object Home : Route() {
        override val label = "Home"
        override val icon = Icons.Filled.Home
    }

    object Search : Route() {
        override val label = "Search"
        override val icon = Icons.Filled.Search
    }

    object Library : Route() {
        override val label = "Library"
        override val icon = Icons.Filled.LibraryMusic
    }

    object Account : Route() {
        override val label = "Account"
        override val icon = Icons.Filled.Person
    }

    object Settings : Route() {
        override val label = "Settings"
        override val icon = Icons.Filled.Settings
    }

    object ListenTogether : Route() {
        override val label = "Listen Together"
        override val icon = Icons.Filled.Groups
    }

    object NowPlaying : Route() {
        override val label = null
        override val icon = Icons.Rounded.Lyrics
    }

    object Stats : Route() {
        override val label = null
        override val icon = null
    }

    data class Playlist(val playlistId: String) : Route() {
        override val label = null
        override val icon = null
    }

    data class Album(val browseId: String) : Route() {
        override val label = null
        override val icon = null
    }

    data class Artist(val artistId: String) : Route() {
        override val label = null
        override val icon = null
    }

    data class YouTubeBrowse(val browseId: String, val params: String? = null) : Route() {
        override val label = null
        override val icon = null
    }

    object YouTubeHome : Route() {
        override val label = "YouTube"
        override val icon = null
    }

    object YouTubeShorts : Route() {
        override val label = "Shorts"
        override val icon = null
    }

    data class YouTubeWatch(val videoId: String) : Route() {
        override val label = null
        override val icon = null
    }
}