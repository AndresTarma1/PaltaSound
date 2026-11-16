package example.nucleus.player

import example.nucleus.models.MediaMetadata
import example.nucleus.viewmodels.QueueSource
import kotlinx.serialization.Serializable

interface Queue {
    val source: QueueSource
    val initialItems: List<MediaMetadata>
    var startIndex: Int
    val hasNextPage: Boolean

    suspend fun getInitialStatus(): Result<QueueStatus>
    suspend fun nextPage(): Result<List<MediaMetadata>>
}

@Serializable
data class QueueStatus(
    val items: List<MediaMetadata>,
    val hasNextPage: Boolean,
)
