package example.nucleus.db

import example.nucleus.db.dao.AlbumDao
import example.nucleus.db.dao.ArtistDao
import example.nucleus.db.dao.FormatDao
import example.nucleus.db.dao.HistoryDao
import example.nucleus.db.dao.LyricsDao
import example.nucleus.db.dao.MappingDao
import example.nucleus.db.dao.PlaylistDao
import example.nucleus.db.dao.SongDao
import example.nucleus.db.entities.AlbumEntity
import example.nucleus.db.entities.AlbumWithSongs
import example.nucleus.db.entities.ArtistEntity
import example.nucleus.db.entities.ArtistWithStats
import example.nucleus.db.entities.FormatEntity
import example.nucleus.db.entities.PlaylistEntity
import example.nucleus.db.entities.SearchHistoryEntry
import example.nucleus.db.entities.SongEntity
import example.nucleus.db.entities.SongWithRelations
import example.nucleus.db.entities.TopAlbumEntry
import example.nucleus.db.entities.TopArtistEntry
import example.nucleus.db.entities.TopSongEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class DatabaseDao(private val database: MusicPlayerDatabase) {

    private val songs = SongDao(database)
    private val artists = ArtistDao(database)
    private val albums = AlbumDao(database)
    private val playlists = PlaylistDao(database)
    private val mappings = MappingDao(database)
    private val history = HistoryDao(database)
    private val lyrics = LyricsDao(database)
    private val formats = FormatDao(database)

    fun allSongs(): Flow<List<SongEntity>> = songs.allSongs()
    fun songsInLibrary(): Flow<List<SongEntity>> = songs.songsInLibrary()
    fun songById(id: String): Flow<SongEntity?> = songs.songById(id)
    fun likedSongs(): Flow<List<SongEntity>> = songs.likedSongs()
    fun songWithRelations(songId: String): Flow<SongWithRelations?> =
        flow {
            val song = songs.songById(songId).firstOrNull()
            emit(
                song?.let {
                    SongWithRelations(
                        song = it,
                        artists = mappings.artistsForSong(songId),
                        album = mappings.albumForSong(songId)
                    )
                }
            )
        }

    suspend fun insertSong(song: SongEntity) = songs.insertSong(song)
    suspend fun updateSong(song: SongEntity) = songs.updateSong(song)
    suspend fun updateSongMetadata(song: SongEntity) = songs.updateSongMetadata(song)
    suspend fun updateSongLyricsOffset(songId: String, offsetMs: Int) =
        songs.updateSongLyricsOffset(songId, offsetMs)
    suspend fun deleteSong(id: String) = songs.deleteSong(id)

    fun allArtists(): Flow<List<ArtistEntity>> = artists.allArtists()
    fun artistsByPlayTime(): Flow<List<ArtistWithStats>> = artists.artistsByPlayTime()
    fun artistsByCreateDate(): Flow<List<ArtistWithStats>> = artists.artistsByCreateDate()
    fun artistsByName(): Flow<List<ArtistWithStats>> = artists.artistsByName()
    fun artistById(id: String): Flow<ArtistEntity?> = artists.artistById(id)
    fun bookmarkedArtists(): Flow<List<ArtistEntity>> = artists.bookmarkedArtists()
    suspend fun insertArtist(artist: ArtistEntity) = artists.insertArtist(artist)
    suspend fun updateArtistBookmark(id: String, bookmarkedAt: LocalDateTime?) = artists.updateArtistBookmark(id, bookmarkedAt)
    suspend fun deleteArtist(id: String) = artists.deleteArtist(id)

    fun allAlbums(): Flow<List<AlbumEntity>> = albums.allAlbums()
    fun albumById(id: String): Flow<AlbumEntity?> = albums.albumById(id)
    fun bookmarkedAlbums(): Flow<List<AlbumEntity>> = albums.bookmarkedAlbums()
    suspend fun insertAlbum(album: AlbumEntity) = albums.insertAlbum(album)
    suspend fun updateAlbumBookmark(id: String, bookmarkedAt: LocalDateTime?) = albums.updateAlbumBookmark(id, bookmarkedAt)
    suspend fun deleteAlbum(id: String) = albums.deleteAlbum(id)
    fun albumWithArtists(albumId: String) = albums.albumWithArtists(albumId)
    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?> = albums.albumWithSongs(albumId)
    fun countDownloadedByAlbum(albumId: String): Long = albums.countDownloadedByAlbum(albumId)

    // Playlist DAO
    fun allPlaylists(): Flow<List<PlaylistEntity>> = playlists.allPlaylists()
    fun playlistsByNameAsc(): Flow<List<PlaylistEntity>> = playlists.playlistsByNameAsc()
    fun playlistById(id: String): Flow<PlaylistEntity?> = playlists.playlistById(id)
    suspend fun insertPlaylist(playlist: PlaylistEntity) = playlists.insertPlaylist(playlist)
    suspend fun deletePlaylist(id: String) = playlists.deletePlaylist(id)
    fun songIdsInPlaylist(playlistId: String): List<String> = mappings.songIdsInPlaylist(playlistId)
    fun countByPlaylist(playlistId: String): Long = playlists.countByPlaylist(playlistId)
    fun countDownloadedByPlaylist(playlistId: String): Long = playlists.countDownloadedByPlaylist(playlistId)

    suspend fun insertSongArtistMap(songId: String, artistId: String, position: Int) = mappings.insertSongArtistMap(songId, artistId, position)
    suspend fun insertSongAlbumMap(songId: String, albumId: String, index: Int) = mappings.insertSongAlbumMap(songId, albumId, index)
    suspend fun insertAlbumArtistMap(albumId: String, artistId: String, order: Int) = mappings.insertAlbumArtistMap(albumId, artistId, order)
    suspend fun insertPlaylistSongMap(playlistId: String, songId: String, position: Int, setVideoId: String? = null) = mappings.insertPlaylistSongMap(playlistId, songId, position, setVideoId)

    fun artistsForSong(songId: String): List<ArtistEntity> = mappings.artistsForSong(songId)
    fun albumForSong(songId: String): AlbumEntity? = mappings.albumForSong(songId)

    suspend fun insertEvent(songId: String, timestamp: LocalDateTime, playTime: Long) = history.insertEvent(songId, timestamp, playTime)
    fun searchHistory(): Flow<List<SearchHistoryEntry>> = history.searchHistory()
    suspend fun insertSearchHistory(query: String) = history.insertSearchHistory(query)
    suspend fun deleteSearchHistory(query: String) = history.deleteSearchHistory(query)
    suspend fun clearSearchHistory() = history.clearSearchHistory()

    /**
     * Wipes account-scoped library data when switching YouTube accounts: bookmarked remote
     * playlists (e.g. "Liked Music") and saved songs/albums/artists/podcasts. Keeps the user's local
     * playlists, downloads and local play history.
     *
     * Bookmarked playlists actually live in TWO places: the `Playlist` table (populated by
     * SyncUtils's pull) and `SavedPlaylist` (populated by the playlist screen's bookmark button —
     * the normal way users save a playlist, e.g. "Liked Music"). Only wiping the former left a
     * stale "Liked Music" from the previous account visible after switching, since its real entry
     * lived in SavedPlaylist and was never cleared.
     */
    suspend fun clearAccountLibrary() = withContext(Dispatchers.IO) {
        playlists.deleteRemotePlaylists()
        val staleSavedPlaylistIds = database.savedPlaylistQueries.selectRemoteIds().executeAsList()
        staleSavedPlaylistIds.forEach { id ->
            database.playlistSongMapQueries.deletePlaylistSongMapsByPlaylist(id)
        }
        database.savedPlaylistQueries.deleteRemote()
        database.savedSongQueries.deleteAll()
        database.savedAlbumQueries.deleteAll()
        database.savedArtistQueries.deleteAll()
        // Los podcasts tambien son datos de la cuenta: sin esto, al cambiar de cuenta
        // seguirian apareciendo en la biblioteca los de la anterior.
        database.savedPodcastQueries.deleteAll()
    }

    suspend fun insertLyrics(id: String, lyricsText: String, provider: String = "Unknown") = lyrics.insertLyrics(id, lyricsText, provider)
    suspend fun getLyrics(id: String) = lyrics.getLyrics(id)
    suspend fun deleteLyrics(id: String) = lyrics.deleteLyrics(id)
    fun downloadedSongs(): Flow<List<SongEntity>> = songs.downloadedSongs()
    fun downloadedSongsCount(): Flow<Long> = songs.downloadedSongsCount()
    suspend fun updateSongDownloadStatus(songId: String, isDownloaded: Boolean, dateDownload: Long?) = songs.updateSongDownloadStatus(songId, isDownloaded, dateDownload)

    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<SongEntity>> = songs.quickPicks(now)
    fun searchSongs(query: String, limit: Long = 20): Flow<List<SongEntity>> = songs.searchSongs(query, limit)

    fun recentPlayedSongs(limit: Int = 10): Flow<List<SongWithRelations>> = flow {
        val events = database.eventQueries.recentEvents(limit.toLong()).executeAsList()
        val songIds = events.map { it.songId }.distinct()
        val result = songIds.mapNotNull { id ->
            val song = songs.songById(id).firstOrNull() ?: return@mapNotNull null
            SongWithRelations(
                song = song,
                artists = mappings.artistsForSong(id),
                album = mappings.albumForSong(id)
            )
        }
        emit(result)
    }

    suspend fun insertFormat(format: FormatEntity) = formats.insertFormat(format)

    // ── Estadísticas ──

    suspend fun incrementPlayCount(songId: String, year: Int, month: Int) = withContext(Dispatchers.IO) {
        val y = year.toLong()
        val m = month.toLong()
        val existing = database.playCountQueries.playCountByMonth(songId, y, m).executeAsOneOrNull() ?: 0L
        database.playCountQueries.insertPlayCount(songId, y, m, existing + 1L)
    }

    suspend fun updateSongTotalPlayTime(playedMs: Long, songId: String) = withContext(Dispatchers.IO) {
        database.songQueries.updateSongTotalPlayTime(playedMs, songId)
    }

    suspend fun totalPlayTimeInRange(fromMs: Long, toMs: Long): Long = withContext(Dispatchers.IO) {
        database.eventQueries.totalPlayTimeInRange(fromMs, toMs).executeAsOne()
    }

    suspend fun uniqueSongCountInRange(fromMs: Long, toMs: Long): Long = withContext(Dispatchers.IO) {
        database.eventQueries.uniqueSongCountInRange(fromMs, toMs).executeAsOne()
    }

    suspend fun topSongs(limit: Long): List<TopSongEntry> = withContext(Dispatchers.IO) {
        database.playCountQueries.topSongs(limit).executeAsList().map { r ->
            TopSongEntry(
                id = r.component1(),
                title = r.component2(),
                thumbnailUrl = r.component4(),
                playCount = r.component25() ?: 0L,
            )
        }
    }

    suspend fun topAlbums(limit: Long): List<TopAlbumEntry> = withContext(Dispatchers.IO) {
        database.playCountQueries.topAlbums(limit).executeAsList().map { r ->
            TopAlbumEntry(
                albumName = r.component1(),
                playCount = r.component2() ?: 0L,
            )
        }
    }

    suspend fun topArtists(limit: Long): List<TopArtistEntry> = withContext(Dispatchers.IO) {
        database.playCountQueries.topArtists(limit).executeAsList().map { r ->
            TopArtistEntry(
                id = r.component1(),
                name = r.component2(),
                thumbnailUrl = r.component3(),
                playCount = r.component8() ?: 0L,
            )
        }
    }

    fun <T> transaction(block: () -> T): T {
        var result: T? = null
        database.transaction { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
