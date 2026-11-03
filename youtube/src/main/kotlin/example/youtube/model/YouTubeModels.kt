package example.youtube.model

/**
 * Lightweight YouTube-main models used by the desktop UI.
 *
 * These are intentionally independent from the music models: a video has a
 * channel, view count, live state and a watch URL, while a song has artists,
 * albums and lyrics.
 */
data class YouTubeVideo(
    val id: String,
    val title: String,
    val url: String,
    val channelName: String?,
    val channelUrl: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val viewCount: Long?,
    val uploadedText: String?,
    val description: String? = null,
    val isLive: Boolean = false,
    val channelAvatarUrl: String? = null,
)

data class YouTubeChannel(
    val id: String?,
    val name: String,
    val url: String,
    val thumbnailUrl: String?,
    val subscriberCount: Long?,
    val description: String?,
)

data class YouTubePlaylist(
    val id: String?,
    val name: String,
    val url: String,
    val thumbnailUrl: String?,
    val uploaderName: String?,
    val streamCount: Long?,
)

sealed interface YouTubeSearchItem {
    data class Video(val video: YouTubeVideo) : YouTubeSearchItem
    data class Channel(val channel: YouTubeChannel) : YouTubeSearchItem
    data class Playlist(val playlist: YouTubePlaylist) : YouTubeSearchItem
}

data class YouTubeSearchPage(
    val items: List<YouTubeSearchItem>,
    val nextPage: Any? = null,
)

data class YouTubeVideoDetails(
    val video: YouTubeVideo,
    val related: List<YouTubeVideo> = emptyList(),
    val videoStreams: List<YouTubeDashStream> = emptyList(),
    val audioStreams: List<YouTubeDashStream> = emptyList(),
)

/**
 * One progressive YouTube stream plus the byte ranges its DASH index needs.
 *
 * Keeping the init/index ranges lets us build a single local MPD with separate
 * video and audio AdaptationSets, so mpv handles A/V offsets and seeking as one
 * timeline instead of attaching an external audio track.
 */
data class YouTubeDashStream(
    val url: String,
    val mimeType: String,
    val codecs: String?,
    val bandwidth: Int,
    val width: Int? = null,
    val height: Int? = null,
    val audioChannels: Int? = null,
    val audioSamplingRate: Int? = null,
    val initStart: Long,
    val initEnd: Long,
    val indexStart: Long,
    val indexEnd: Long,
    val isOriginalAudio: Boolean = true,
)
