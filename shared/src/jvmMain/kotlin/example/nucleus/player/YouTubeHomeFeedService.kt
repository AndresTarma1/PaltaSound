package example.nucleus.player

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient as XClient
import example.nucleus.data.account.AccountManager
import example.youtube.model.YouTubeChannel
import example.youtube.model.YouTubeVideo
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class YouTubeHomeFeed(
    val items: List<YouTubeVideo>,
    val continuation: String? = null,
)

/**
 * YouTube's real home feed (`FEwhat_to_watch`) through InnerTube.
 *
 * It sends the account cookies when available, so the recommendations match
 * the signed-in user just like youtube.com. Parsing is renderer-agnostic: it
 * walks the JSON tree collecting videoRenderer/gridVideoRenderer/reelItemRenderer
 * nodes, which keeps working when YouTube reshuffles its section layout.
 */
class YouTubeHomeFeedService {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }
    }
    private val innerTube by lazy { InnerTube(httpClient) }

    suspend fun feed(continuation: String? = null): YouTubeHomeFeed? = withContext(Dispatchers.IO) {
        try {
            val cookie = AccountManager.getCookie()
            innerTube.cookie = cookie
            innerTube.useLoginForBrowse = true
            if (innerTube.visitorData.isNullOrBlank()) {
                innerTube.visitorData = innerTube.fetchFreshVisitorData()
            }
            val response = innerTube.browse(
                client = XClient.WEB,
                browseId = if (continuation == null) "FEwhat_to_watch" else null,
                continuation = continuation,
                setLogin = true,
            )
            val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                ?: return@withContext null
            val renderers = mutableListOf<RawRenderer>()
            collectVideos(root, renderers)
            val videos = renderers
                .mapNotNull(::toVideo)
                .distinctBy { it.id }
            YouTubeHomeFeed(videos, findContinuation(root))
        } catch (e: Exception) {
            io.github.aakira.napier.Napier.w("[YouTubeHome] feed falló: ${e.message}")
            null
        }
    }

    suspend fun subscriptions(): List<YouTubeChannel> = withContext(Dispatchers.IO) {
        try {
            val cookie = AccountManager.getCookie()
            innerTube.cookie = cookie
            innerTube.useLoginForBrowse = true
            if (innerTube.visitorData.isNullOrBlank()) {
                innerTube.visitorData = innerTube.fetchFreshVisitorData()
            }
            val response = innerTube.browse(
                client = XClient.WEB,
                browseId = "FEchannels",
                setLogin = true,
            )
            val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                ?: return@withContext emptyList()
            val renderers = mutableListOf<JsonObject>()
            collectChannels(root, renderers)
            renderers.mapNotNull(::toChannel)
                .distinctBy { it.id ?: it.url }
        } catch (e: Exception) {
            io.github.aakira.napier.Napier.w("[YouTubeHome] suscripciones fallaron: ${e.message}")
            emptyList()
        }
    }

    private fun collectChannels(element: JsonElement, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                (element["gridChannelRenderer"] as? JsonObject)?.let(out::add)
                (element["channelRenderer"] as? JsonObject)?.let(out::add)
                element.values.forEach { collectChannels(it, out) }
            }

            is JsonArray -> element.forEach { collectChannels(it, out) }
            else -> Unit
        }
    }

    private fun toChannel(renderer: JsonObject): YouTubeChannel? {
        val id = renderer.str("channelId") ?: return null
        val name = renderer.text("title") ?: return null
        val thumbnail = renderer.obj("thumbnail")?.arr("thumbnails")?.lastOrNull()
            ?.asObject()?.str("url")
            ?.let(::normalizeUrl)
        return YouTubeChannel(
            id = id,
            name = name,
            url = "https://www.youtube.com/channel/$id",
            thumbnailUrl = thumbnail,
            subscriberCount = null,
            description = null,
        )
    }

    private data class RawRenderer(val kind: String, val json: JsonObject)

    private fun collectVideos(element: JsonElement, out: MutableList<RawRenderer>) {
        when (element) {
            is JsonObject -> {
                // In-feed ads also expose lockupViewModel; they are not videos.
                if (element.containsKey("adSlotRenderer")) return
                for (key in VIDEO_RENDERERS) {
                    (element[key] as? JsonObject)?.let { out.add(RawRenderer(key, it)) }
                }
                element.values.forEach { collectVideos(it, out) }
            }

            is JsonArray -> element.forEach { collectVideos(it, out) }
            else -> Unit
        }
    }

    private fun findContinuation(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                (element["continuationCommand"] as? JsonObject)
                    ?.let { it.str("token") }
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                (element["nextContinuationData"] as? JsonObject)
                    ?.let { it.str("continuation") }
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                element.values.forEach { child ->
                    findContinuation(child)?.let { return it }
                }
            }

            is JsonArray -> element.forEach { child ->
                findContinuation(child)?.let { return it }
            }

            else -> Unit
        }
        return null
    }

    private fun toVideo(raw: RawRenderer): YouTubeVideo? = when (raw.kind) {
        "lockupViewModel" -> lockupVideo(raw.json)
        "shortsLockupViewModel" -> shortsLockupVideo(raw.json)
        "reelItemRenderer" -> reelVideo(raw.json)
        else -> classicVideo(raw.json)
    }

    private fun classicVideo(renderer: JsonObject): YouTubeVideo? {
        val videoId = renderer.str("videoId") ?: return null
        val title = renderer.text("title")
            ?: renderer.text("headline")
            ?: return null
        val lengthText = renderer.text("lengthText")
        val owner = renderer.text("ownerText")
            ?: renderer.text("shortBylineText")
            ?: renderer.text("longBylineText")
        val uploaded = renderer.text("publishedTimeText")
        val thumbnail = renderer.thumbnailUrl()
        val channelUrl = renderer.obj("ownerText")
            ?.arr("runs")?.firstOrNull()?.let { it as? JsonObject }
            ?.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("canonicalBaseUrl")
        return YouTubeVideo(
            id = videoId,
            title = title,
            url = "https://www.youtube.com/watch?v=$videoId",
            channelName = owner,
            channelUrl = channelUrl,
            durationSeconds = parseDuration(lengthText),
            thumbnailUrl = thumbnail,
            viewCount = null,
            uploadedText = uploaded,
            description = null,
            isLive = lengthText.isNullOrBlank() &&
                renderer.toString().contains("\"LIVE\"", ignoreCase = true),
        )
    }

    private fun lockupVideo(renderer: JsonObject): YouTubeVideo? {
        val contentType = renderer.str("contentType").orEmpty()
        if (contentType in NON_VIDEO_LOCKUPS) return null
        val videoId = renderer.str("contentId") ?: return null
        val metadata = renderer.obj("metadata")?.obj("lockupMetadataViewModel")
            ?: return null
        val title = metadata["title"].youtubeText() ?: return null
        val parts = lockupMetadataParts(metadata)
        val byline = lockupByline(parts)
        val partTexts = parts.mapNotNull { it["text"].youtubeText() }
        val badgeTexts = lockupBadges(renderer)
        val duration = badgeTexts.firstNotNullOfOrNull { text ->
            parseDuration(text).takeIf { it > 0 }
        } ?: 0L
        return YouTubeVideo(
            id = videoId,
            title = title,
            url = "https://www.youtube.com/watch?v=$videoId",
            channelName = byline?.first ?: partTexts.firstOrNull(),
            channelUrl = byline?.second,
            durationSeconds = duration,
            thumbnailUrl = lockupThumbnail(renderer)
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            viewCount = null,
            uploadedText = partTexts.drop(1).joinToString(" · ").takeIf { it.isNotBlank() },
            description = null,
            isLive = badgeTexts.any { it.equals("LIVE", ignoreCase = true) },
            channelAvatarUrl = metadata
                .obj("image")?.obj("decoratedAvatarViewModel")
                ?.obj("avatar")?.obj("avatarViewModel")
                ?.obj("image")?.thumbnailUrl()
                ?.let(::normalizeUrl),
        )
    }

    private fun shortsLockupVideo(renderer: JsonObject): YouTubeVideo? {
        val command = renderer.obj("onTap")?.obj("innertubeCommand")
        val reelWatch = command?.obj("reelWatchEndpoint")
        val videoId = reelWatch?.str("videoId") ?: return null
        val overlay = renderer.obj("overlayMetadata")
        val title = overlay?.obj("primaryText")?.str("content") ?: return null
        val views = overlay?.obj("secondaryText")?.str("content")
        val thumbnail = renderer.obj("thumbnailViewModel")?.obj("thumbnailViewModel")
            ?.obj("image")?.thumbnailUrl()
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        return YouTubeVideo(
            id = videoId,
            title = title,
            url = "https://www.youtube.com/shorts/$videoId",
            channelName = null,
            channelUrl = null,
            durationSeconds = 0L,
            thumbnailUrl = thumbnail,
            viewCount = null,
            uploadedText = views,
            description = null,
            isLive = false,
        )
    }

    private fun reelVideo(renderer: JsonObject): YouTubeVideo? {
        val videoId = renderer.str("videoId") ?: return null
        return YouTubeVideo(
            id = videoId,
            title = renderer.text("headline").orEmpty(),
            url = "https://www.youtube.com/shorts/$videoId",
            channelName = null,
            channelUrl = null,
            durationSeconds = 0L,
            thumbnailUrl = renderer.thumbnailUrl(),
            viewCount = null,
            uploadedText = renderer.text("viewCountText"),
            description = null,
            isLive = false,
        )
    }

    private fun lockupMetadataParts(metadata: JsonObject): List<JsonObject> =
        metadata
            .obj("metadata")?.obj("contentMetadataViewModel")
            ?.arr("metadataRows")
            ?.flatMap { row ->
                (row as? JsonObject)?.arr("metadataParts").orEmpty()
                    .mapNotNull { it as? JsonObject }
            }
            .orEmpty()

    private fun lockupByline(parts: List<JsonObject>): Pair<String, String?>? {
        for (part in parts) {
            val text = part.obj("text") ?: continue
            val name = text.youtubeText() ?: continue
            val action = text.arr("commandRuns")?.firstOrNull()?.asObject()
                ?.obj("onTap")?.obj("innertubeCommand")
                ?.obj("browseEndpoint")
            val id = action?.str("browseId")?.takeIf { it.startsWith("UC") } ?: continue
            val url = action.str("canonicalBaseUrl")
                ?: "https://www.youtube.com/channel/$id"
            return name to url
        }
        return parts.firstOrNull()
            ?.get("text").youtubeText()
            ?.let { it to null }
    }

    private fun lockupBadges(renderer: JsonObject): List<String> =
        lockupThumbnailViewModel(renderer)
            ?.arr("overlays")
            ?.flatMap { overlay ->
                val overlayObject = overlay.asObject() ?: return@flatMap emptyList()
                val badgeArray =
                    overlayObject.obj("thumbnailOverlayBadgeViewModel")?.arr("thumbnailBadges")
                        ?: overlayObject.obj("thumbnailBottomOverlayViewModel")?.arr("badges")
                badgeArray.orEmpty().mapNotNull { badge ->
                    badge.asObject()?.obj("thumbnailBadgeViewModel")?.get("text").youtubeText()
                }
            }
            .orEmpty()

    private fun lockupThumbnail(renderer: JsonObject): String? =
        lockupThumbnailViewModel(renderer)?.obj("image")?.thumbnailUrl()?.let(::normalizeUrl)

    private fun normalizeUrl(value: String): String =
        if (value.startsWith("//")) "https:$value" else value

    private fun lockupThumbnailViewModel(renderer: JsonObject): JsonObject? =
        renderer.obj("contentImage")?.let { image ->
            image.obj("thumbnailViewModel")
                ?: image.obj("collectionThumbnailViewModel")
                    ?.obj("primaryThumbnail")
                    ?.obj("thumbnailViewModel")
        }

    private fun JsonObject.thumbnailUrl(): String? {
        obj("thumbnail")?.arr("thumbnails")?.lastOrNull()
            ?.asObject()?.str("url")?.let { return normalizeUrl(it) }
        return obj("image")?.arr("sources")?.lastOrNull()
            ?.asObject()?.str("url")?.let(::normalizeUrl)
    }

    private fun parseDuration(value: String?): Long {
        if (value.isNullOrBlank() || ':' !in value) return 0L
        var seconds = 0L
        for (part in value.split(':')) {
            val number = part.trim().toLongOrNull() ?: return 0L
            seconds = seconds * 60 + number
        }
        return seconds
    }

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonObject.obj(name: String): JsonObject? = this[name].asObject()

    private fun JsonObject.arr(name: String): JsonArray? = this[name] as? JsonArray

    private fun JsonObject.str(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.text(name: String): String? =
        this[name].youtubeText()

    private fun JsonElement?.youtubeText(): String? = when (this) {
        is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> str("content") ?: str("simpleText")
            ?: arr("runs")?.firstOrNull()?.asObject()?.str("text")
        else -> null
    }

    private companion object {
        val VIDEO_RENDERERS = listOf(
            "videoRenderer",
            "gridVideoRenderer",
            "reelItemRenderer",
            "lockupViewModel",
            "shortsLockupViewModel",
        )
        val NON_VIDEO_LOCKUPS = setOf(
            "LOCKUP_CONTENT_TYPE_PLAYLIST",
            "LOCKUP_CONTENT_TYPE_PODCAST",
            "LOCKUP_CONTENT_TYPE_ALBUM",
            "LOCKUP_CONTENT_TYPE_CHANNEL",
            "LOCKUP_CONTENT_TYPE_COURSE",
        )
    }
}
