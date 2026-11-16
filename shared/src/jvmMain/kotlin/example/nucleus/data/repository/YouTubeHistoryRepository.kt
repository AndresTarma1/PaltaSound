package example.nucleus.data.repository

import example.nucleus.db.MusicPlayerDatabase
import example.youtube.model.YouTubeVideo

/**
 * Persistent "continue watching" history for YouTube main.
 *
 * Kept separate from the music library: a video is not a Song and its watch
 * progress must not appear in music history, playlists or statistics.
 */
class YouTubeHistoryRepository(
    private val database: MusicPlayerDatabase,
) {
    private val queries get() = database.youTubeWatchEntryQueries

    fun save(video: YouTubeVideo, positionMs: Long, durationMs: Long) {
        queries.insertOrReplace(
            videoId = video.id,
            title = video.title,
            channelName = video.channelName,
            durationSeconds = video.durationSeconds,
            thumbnailUrl = video.thumbnailUrl,
            viewCount = video.viewCount,
            uploadedText = video.uploadedText,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            lastWatchedAt = System.currentTimeMillis(),
        )
    }

    fun updateProgress(videoId: String, positionMs: Long, durationMs: Long) {
        queries.updateProgress(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            lastWatchedAt = System.currentTimeMillis(),
            videoId = videoId,
        )
    }

    fun recent(limit: Long = 12): List<YouTubeVideo> =
        queries.recent(limit)
            .executeAsList()
            .map { entry ->
                YouTubeVideo(
                    id = entry.videoId,
                    title = entry.title,
                    url = "https://www.youtube.com/watch?v=${entry.videoId}",
                    channelName = entry.channelName,
                    channelUrl = null,
                    durationSeconds = entry.durationSeconds,
                    thumbnailUrl = entry.thumbnailUrl,
                    viewCount = entry.viewCount,
                    uploadedText = entry.uploadedText,
                )
            }

    fun progress(videoId: String): Pair<Long, Long>? =
        queries.byId(videoId).executeAsOneOrNull()?.let { it.positionMs to it.durationMs }

    fun remove(videoId: String) = queries.delete(videoId)

    fun clear() = queries.clearAll()
}
