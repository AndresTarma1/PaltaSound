package example.nucleus.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class ScreenConfig {
    @Serializable
    data object Home : ScreenConfig()

    @Serializable
    data object Search : ScreenConfig()

    @Serializable
    data object Library : ScreenConfig()

    @Serializable
    data object Account : ScreenConfig()

    @Serializable
    data object Settings : ScreenConfig()

    @Serializable
    data object Stats : ScreenConfig()

    @Serializable
    data object ListenTogether : ScreenConfig()

    @Serializable
    data object NowPlaying : ScreenConfig()

    @Serializable
    data class Album(val browseId: String) : ScreenConfig()

    @Serializable
    data class Playlist(val playlistId: String) : ScreenConfig()

    @Serializable
    data class Artist(val artistId: String) : ScreenConfig()

    @Serializable
    data class YouTubeBrowse(val browseId: String, val params: String? = null) : ScreenConfig()

    @Serializable
    data object YouTubeHome : ScreenConfig()

    @Serializable
    data object YouTubeShorts : ScreenConfig()

    @Serializable
    data class YouTubeWatch(val videoId: String) : ScreenConfig()

    companion object {
        private const val PODCAST_BROWSE_PREFIX = "MPSP"

        /**
         * Punto único para abrir una colección por browseId: los pódcast usan
         * un browseId `MPSP...` que se sirve con la pantalla de playlist.
         */
        fun collection(browseId: String): ScreenConfig =
            if (browseId.startsWith(PODCAST_BROWSE_PREFIX)) {
                Playlist(browseId)
            } else {
                Album(browseId)
            }
    }
}