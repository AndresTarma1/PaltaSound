package example.youtube

import example.youtube.model.YouTubeChannel
import example.youtube.model.YouTubePlaylist
import example.youtube.model.YouTubeSearchItem
import example.youtube.model.YouTubeSearchPage
import example.youtube.model.YouTubeDashStream
import example.youtube.model.YouTubeVideo
import example.youtube.model.YouTubeVideoDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptDecoder
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Facade over PipePipeExtractor for YouTube main.
 *
 * Phase 1 uses the extractor for metadata and for direct adaptive streams
 * (video-only + audio-only, with DASH byte ranges). yt-dlp remains the fallback
 * when a video has no direct streams. The SABR session arrives in phase 2.
 */
class YouTubeService(
    private val cookieProvider: () -> String? = { null },
    localDecoder: YoutubeJavaScriptDecoder? = null,
) {
    init {
        val locale = Locale.getDefault()
        NewPipe.init(
            PipePipeDownloader(cookieProvider = cookieProvider),
            Localization(locale.language.ifBlank { "en" }, locale.country.ifBlank { "US" }),
        )
        ServiceList.YouTube.setTokens(cookieProvider().orEmpty())
        // visionOS still returns direct adaptive formats (with init/index ranges),
        // unlike MWEB, which converts every adaptive format to SABR. Those ranges
        // are what let us build a local DASH manifest for mpv.
        NewPipe.setYoutubePlayerClient("visionos")
        YoutubeApiDecoder.setLocalDecoder(localDecoder)
    }

    fun updateCookie(cookie: String?) {
        ServiceList.YouTube.setTokens(cookie.orEmpty())
    }

    suspend fun search(query: String, filter: SearchFilter = SearchFilter.ALL): YouTubeSearchPage =
        withContext(Dispatchers.IO) {
            val factory = org.schabi.newpipe.extractor.services.youtube.linkHandler
                .YoutubeSearchQueryHandlerFactory.getInstance()
            val filterItem = factory.getFilterItem(0)
                ?: error("YouTube search filter 0 is not available")
            val handler = factory.fromQuery(query, listOf(filterItem), null)
            val extractor = ServiceList.YouTube.getSearchExtractor(handler)
            extractor.fetchPage()
            val page = extractor.initialPage
            YouTubeSearchPage(
                items = page.items.mapNotNull(::mapSearchItem),
                nextPage = page.nextPage,
            )
        }

    suspend fun video(videoIdOrUrl: String): YouTubeVideoDetails = withContext(Dispatchers.IO) {
        val url = normalizeWatchUrl(videoIdOrUrl)
        try {
            fullDetails(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // StreamInfo requires at least one playable stream. Without the JS
            // cipher engine (GraalVM native) stream extraction can fail even
            // though metadata is available; SABR handles playback separately.
            // Catch Throwable too: a missing QuickJS class surfaces as
            // NoClassDefFoundError, not Exception.
            metadataOnly(url)
        }
    }

    private fun fullDetails(url: String): YouTubeVideoDetails {
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        val video = info.toVideo()
        val related = info.relatedStreams
            .orEmpty()
            .mapNotNull { it as? StreamInfoItem }
            .mapNotNull(::mapStreamItem)
        val videoStreams = info.videoOnlyStreams.orEmpty().mapNotNull(::toDashStream)
        val audioStreams = info.audioStreams.orEmpty().mapNotNull(::toDashStream)
        return YouTubeVideoDetails(video, related, videoStreams, audioStreams)
    }

    /** Metadata without touching the stream lists (and therefore the cipher). */
    private fun metadataOnly(url: String): YouTubeVideoDetails {
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()
        val video = YouTubeVideo(
            id = videoIdFromUrl(extractor.url) ?: extractor.id,
            title = extractor.name.orEmpty(),
            url = extractor.url,
            channelName = extractor.uploaderName,
            channelUrl = extractor.uploaderUrl,
            durationSeconds = extractor.length.takeIf { it > 0 } ?: 0,
            thumbnailUrl = extractor.thumbnailUrl,
            viewCount = extractor.viewCount.takeIf { it >= 0 },
            uploadedText = extractor.textualUploadDate,
            description = extractor.description?.content,
            isLive = extractor.streamType == StreamType.LIVE_STREAM,
        )
        return YouTubeVideoDetails(video)
    }

    suspend fun channel(url: String): YouTubeChannel = withContext(Dispatchers.IO) {
        val extractor = ServiceList.YouTube.getChannelExtractor(normalizeChannelUrl(url))
        extractor.fetchPage()
        YouTubeChannel(
            id = extractor.id,
            name = extractor.name,
            url = extractor.url,
            thumbnailUrl = extractor.avatarUrl,
            subscriberCount = extractor.subscriberCount.takeIf { it >= 0 },
            description = extractor.description,
        )
    }

    suspend fun playlist(url: String): YouTubePlaylist = withContext(Dispatchers.IO) {
        val extractor = ServiceList.YouTube.getPlaylistExtractor(normalizePlaylistUrl(url))
        extractor.fetchPage()
        YouTubePlaylist(
            id = extractor.id,
            name = extractor.name,
            url = extractor.url,
            thumbnailUrl = extractor.thumbnailUrl,
            uploaderName = extractor.uploaderName,
            streamCount = extractor.streamCount.takeIf { it >= 0 },
        )
    }

    private fun mapSearchItem(item: org.schabi.newpipe.extractor.InfoItem): YouTubeSearchItem? =
        when (item) {
            is StreamInfoItem -> mapStreamItem(item)?.let(YouTubeSearchItem::Video)
            is ChannelInfoItem -> YouTubeSearchItem.Channel(
                YouTubeChannel(
                    id = item.uploaderId,
                    name = item.name,
                    url = item.url,
                    thumbnailUrl = item.thumbnailUrl,
                    subscriberCount = item.subscriberCount.takeIf { it >= 0 },
                    description = item.description,
                ),
            )

            is PlaylistInfoItem -> YouTubeSearchItem.Playlist(
                YouTubePlaylist(
                    id = item.uploaderId,
                    name = item.name,
                    url = item.url,
                    thumbnailUrl = item.thumbnailUrl,
                    uploaderName = item.uploaderName,
                    streamCount = item.streamCount.takeIf { it >= 0 },
                ),
            )

            else -> null
        }

    private fun mapStreamItem(item: StreamInfoItem): YouTubeVideo? {
        val url = item.url ?: return null
        val id = videoIdFromUrl(url) ?: return null
        return YouTubeVideo(
            id = id,
            title = item.name.orEmpty(),
            url = url,
            channelName = item.uploaderName,
            channelUrl = item.uploaderUrl,
            durationSeconds = item.duration.takeIf { it > 0 } ?: 0,
            thumbnailUrl = item.thumbnailUrl,
            viewCount = item.viewCount.takeIf { it >= 0 },
            uploadedText = item.textualUploadDate,
            isLive = item.streamType == StreamType.LIVE_STREAM,
        )
    }

    private fun StreamInfo.toVideo(): YouTubeVideo {
        val id = videoIdFromUrl(url) ?: id
        return YouTubeVideo(
            id = id,
            title = name.orEmpty(),
            url = url,
            channelName = uploaderName,
            channelUrl = uploaderUrl,
            durationSeconds = duration.takeIf { it > 0 } ?: 0,
            thumbnailUrl = thumbnailUrl,
            viewCount = viewCount.takeIf { it >= 0 },
            uploadedText = textualUploadDate,
            description = description?.content,
            isLive = streamType == StreamType.LIVE_STREAM,
        )
    }

    private fun toDashStream(stream: VideoStream): YouTubeDashStream? {
        val itag = stream.itagItem ?: return null
        if (stream.deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP) return null
        if (itag.initStart < 0 || itag.initEnd <= itag.initStart) return null
        if (itag.indexStart < 0 || itag.indexEnd <= itag.indexStart) return null
        val url = stream.content?.takeIf { it.isNotBlank() } ?: return null
        return YouTubeDashStream(
            url = url,
            mimeType = stream.format?.mimeType ?: itag.mediaFormat.mimeType,
            codecs = stream.codec ?: itag.codec,
            bandwidth = (stream.bitrate.takeIf { it > 0 } ?: itag.bitrate).coerceAtLeast(1),
            width = stream.width.takeIf { it > 0 },
            height = stream.height.takeIf { it > 0 },
            initStart = itag.initStart.toLong(),
            initEnd = itag.initEnd.toLong(),
            indexStart = itag.indexStart.toLong(),
            indexEnd = itag.indexEnd.toLong(),
        )
    }

    private fun toDashStream(stream: AudioStream): YouTubeDashStream? {
        val itag = stream.itagItem ?: return null
        if (stream.deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP) return null
        if (itag.initStart < 0 || itag.initEnd <= itag.initStart) return null
        if (itag.indexStart < 0 || itag.indexEnd <= itag.indexStart) return null
        val url = stream.content?.takeIf { it.isNotBlank() } ?: return null
        return YouTubeDashStream(
            url = url,
            mimeType = stream.format?.mimeType ?: itag.mediaFormat.mimeType,
            codecs = stream.codec ?: itag.codec,
            // AudioStream bitrates are expressed in kbps; DASH wants bits/s.
            bandwidth = ((stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate) * 1000)
                .coerceAtLeast(1),
            audioChannels = itag.audioChannels.takeIf { it > 0 },
            audioSamplingRate = itag.sampleRate.takeIf { it > 0 },
            initStart = itag.initStart.toLong(),
            initEnd = itag.initEnd.toLong(),
            indexStart = itag.indexStart.toLong(),
            indexEnd = itag.indexEnd.toLong(),
            isOriginalAudio = stream.audioTrackId.isNullOrBlank(),
        )
    }

    private fun normalizeWatchUrl(value: String): String {
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        return "https://www.youtube.com/watch?v=$value"
    }

    private fun normalizeChannelUrl(value: String): String =
        if (value.startsWith("http://") || value.startsWith("https://")) value
        else "https://www.youtube.com/channel/$value"

    private fun normalizePlaylistUrl(value: String): String =
        if (value.startsWith("http://") || value.startsWith("https://")) value
        else "https://www.youtube.com/playlist?list=$value"

    companion object {
        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

        fun videoIdFromUrl(value: String): String? {
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            if (uri.host?.endsWith("youtu.be") == true) {
                return uri.path?.trim('/')?.takeIf { it.isNotBlank() }
            }
            val query = uri.rawQuery ?: return null
            return query.split('&')
                .firstOrNull { it.startsWith("v=") }
                ?.substringAfter("v=")
                ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
        }
    }

    enum class SearchFilter {
        ALL,
        VIDEOS,
        CHANNELS,
        PLAYLISTS,
    }
}
