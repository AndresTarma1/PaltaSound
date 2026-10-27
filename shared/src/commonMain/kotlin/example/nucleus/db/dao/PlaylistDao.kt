package example.nucleus.db.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import example.nucleus.db.MusicPlayerDatabase
import example.nucleus.db.entities.PlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

private fun Long?.toLocalDateTime(): LocalDateTime? =
    this?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }

private fun LocalDateTime?.toEpochMillis(): Long? =
    this?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

private fun mapPlaylist(
    id: String, name: String, browseId: String?, createdAt: Long?, lastUpdateTime: Long?,
    isEditable: Long, bookmarkedAt: Long?, remoteSongCount: Long?, playEndpointParams: String?,
    thumbnailUrl: String?, shuffleEndpointParams: String?, radioEndpointParams: String?,
    isLocal: Long, isAutoSync: Long
) = PlaylistEntity(
    id = id, name = name, browseId = browseId,
    createdAt = createdAt.toLocalDateTime(), lastUpdateTime = lastUpdateTime.toLocalDateTime(),
    isEditable = isEditable != 0L, bookmarkedAt = bookmarkedAt.toLocalDateTime(),
    remoteSongCount = remoteSongCount?.toInt(), playEndpointParams = playEndpointParams,
    thumbnailUrl = thumbnailUrl, shuffleEndpointParams = shuffleEndpointParams,
    radioEndpointParams = radioEndpointParams, isLocal = isLocal != 0L, isAutoSync = isAutoSync != 0L
)

class PlaylistDao(private val database: MusicPlayerDatabase) {

    fun allPlaylists(): Flow<List<PlaylistEntity>> =
        database.playlistQueries.selectAllPlaylists(::mapPlaylist)
            .asFlow()
            .mapToList(Dispatchers.IO)


    fun playlistById(id: String): Flow<PlaylistEntity?> =
        database.playlistQueries.playlistById(id, ::mapPlaylist)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    fun playlistsByNameAsc(): Flow<List<PlaylistEntity>> =
        database.playlistQueries.playlistsByNameAsc(::mapPlaylist)
            .asFlow()
            .mapToList(Dispatchers.IO)


    suspend fun insertPlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        database.playlistQueries.insertPlaylist(
            id = playlist.id, name = playlist.name, browseId = playlist.browseId,
            createdAt = playlist.createdAt.toEpochMillis(),
            lastUpdateTime = playlist.lastUpdateTime.toEpochMillis(),
            isEditable = if (playlist.isEditable) 1L else 0L,
            bookmarkedAt = playlist.bookmarkedAt.toEpochMillis(),
            remoteSongCount = playlist.remoteSongCount?.toLong(),
            playEndpointParams = playlist.playEndpointParams,
            thumbnailUrl = playlist.thumbnailUrl,
            shuffleEndpointParams = playlist.shuffleEndpointParams,
            radioEndpointParams = playlist.radioEndpointParams,
            isLocal = if (playlist.isLocal) 1L else 0L,
            isAutoSync = if (playlist.isAutoSync) 1L else 0L
        )
    }

    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        database.playlistQueries.deletePlaylist(id)
    }

    suspend fun setAutoSync(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        database.playlistQueries.updateAutoSync(if (enabled) 1L else 0L, id)
    }

    fun observePlaylist(id: String): Flow<PlaylistEntity?> =
        database.playlistQueries.playlistById(id, ::mapPlaylist)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    /** Removes bookmarked remote playlists (isLocal = 0); keeps the user's local playlists. */
    suspend fun deleteRemotePlaylists() = withContext(Dispatchers.IO) {
        database.playlistQueries.deleteRemotePlaylists()
    }

    fun countByPlaylist(playlistId: String): Long =
        database.playlistSongMapQueries.countByPlaylist(playlistId).executeAsOne()

    fun countDownloadedByPlaylist(playlistId: String): Long =
        database.playlistSongMapQueries.countDownloadedByPlaylist(playlistId).executeAsOne()
}
