package example.nucleus.data.repository

import example.nucleus.db.MusicPlayerDatabase
import example.nucleus.db.Playlist
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import example.nucleus.db.SavedPlaylist
import example.nucleus.utils.retryWithBackoff
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PlaylistRepository(
    val database: MusicPlayerDatabase,
    private val userPreferences: UserPreferencesRepository,
) {

    companion object {
        /**
         * Estanterías del sistema que YouTube no permite editar manualmente: "LM" (Me gusta, se
         * maneja con likes), "SE" (episodios guardados), mezclas/radios ("RD...") y podcasts
         * ("MPSP..."). Nunca son destino válido para añadir canciones.
         */
        fun isSystemShelf(playlistId: String): Boolean =
            playlistId == "LM" || playlistId == "SE" ||
                playlistId.startsWith("RD") || playlistId.startsWith("MPSP")

        /** Subconjunto que además invalida el `browseId` para operaciones remotas (comportamiento histórico). */
        private fun hasNoBrowseId(playlistId: String): Boolean =
            playlistId.startsWith("LOCAL_") ||
                playlistId == "LM" || playlistId == "SE" || playlistId.startsWith("RD")
    }

    /**
     * ¿Puede el usuario añadir canciones a esta playlist? Locales siempre; estanterías del
     * sistema nunca; playlists de YouTube solo si la fila sincronizada dice que son editables
     * (sin fila conocida se asume editable para no bloquear listas propias aún no sincronizadas).
     */
    suspend fun isEditablePlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        if (playlistId.startsWith("LOCAL_")) return@withContext true
        if (isSystemShelf(playlistId)) return@withContext false
        return@withContext database.playlistQueries.playlistById(playlistId)
            .executeAsOneOrNull()?.isEditable != 0L
    }

    /**
     * El `browseId` de YouTube para una playlist local, o null si no está vinculada a una playlist
     * real de YT. Dos rutas independientes llegan aquí: la extracción de SavedPlaylists de [SyncUtils]
     * escribe una fila `Playlist` con una columna explícita de `browseId`; el flujo normal de "guardar
     * esta playlist de YouTube" ([savePlaylistWithSongs], mediante el botón de marcador de la pantalla
     * de playlist) solo escribe en `SavedPlaylist` y nunca toca `Playlist` — para esos casos, el propio
     * `id` de la playlist ES el browseId (así es como el resto de la app maneja los ids de playlists no locales).
     *
     * También excluye estanterías generadas automáticamente que YouTube no permite editar: "LM" (Música
     * que te gusta, impulsada por likes, no una lista real de elementos de playlist), "SE" (episodios
     * guardados) y Mezclas/Radios (ids "RD...") — enviar una adición/eliminación a una de estas no es
     * una operación válida.
     */
    suspend fun getBrowseId(playlistId: String): String? = withContext(Dispatchers.IO) {
        database.playlistQueries.playlistById(playlistId).executeAsOneOrNull()?.browseId
            ?: playlistId.takeIf { !hasNoBrowseId(it) }
    }

    /** Marca/desmarca Me gusta a nivel local (bandera `liked`), creando la fila si no existe. */
    suspend fun setSongLiked(song: SongItem, liked: Boolean) = withContext(Dispatchers.IO) {
        if (!database.songQueries.songExists(song.id).executeAsOne()) {
            database.songQueries.insertSongIfNotExists(
                id = song.id,
                title = song.title,
                duration = song.duration?.toLong() ?: -1L,
                thumbnailUrl = song.thumbnail,
                albumId = song.album?.id,
                albumName = song.album?.name,
                explicit = if (song.explicit) 1L else 0L,
                year = null,
                date = null,
                dateModified = null,
                liked = if (liked) 1L else 0L,
                likedDate = if (liked) System.currentTimeMillis() else null,
                totalPlayTime = 0L,
                inLibrary = null,
                dateDownload = null,
                isLocal = 0L,
                libraryAddToken = null,
                libraryRemoveToken = null,
                lyricsOffset = 0L,
                romanizeLyrics = 1L,
                isAgeRestricted = 0L,
                isDownloaded = 0L,
                isUploaded = 0L,
                isVideo = 0L
            )
        }
        database.songQueries.updateSongLike(
            if (liked) 1L else 0L,
            if (liked) System.currentTimeMillis() else null,
            song.id
        )
    }

    suspend fun getSavedAt(playlistId: String): Long? = withContext(Dispatchers.IO) {
        database.savedPlaylistQueries.selectById(playlistId).executeAsOneOrNull()?.savedAt
    }

    /**
     * Renombra localmente (caché + fila vinculada si existe).
     */
    suspend fun renamePlaylist(playlistId: String, newTitle: String) = withContext(Dispatchers.IO) {
        database.transaction {
            database.savedPlaylistQueries.updateTitle(newTitle, playlistId)
            database.playlistQueries.updatePlaylistName(newTitle, playlistId)
        }
    }

    fun observeAutoSync(playlistId: String): Flow<Boolean> =
        database.playlistQueries.playlistById(playlistId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.isAutoSync == 1L }

    /**
     * Activa/desactiva la sincronización automática de una playlist vinculada. Si aún no hay
     * fila `Playlist` (p. ej. guardada por marcador sin sincronización completa), la crea con
     * los datos del guardado para que el job de auto-sync la recoja. Devuelve false si la
     * playlist no está vinculada a ninguna real de YouTube.
     */
    suspend fun setAutoSync(playlistId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val existing = database.playlistQueries.playlistById(playlistId).executeAsOneOrNull()
        if (existing != null) {
            database.playlistQueries.updateAutoSync(if (enabled) 1L else 0L, playlistId)
            return@withContext true
        }
        val browseId = getBrowseId(playlistId) ?: return@withContext false
        val saved = database.savedPlaylistQueries.selectById(playlistId).executeAsOneOrNull()
        database.playlistQueries.insertPlaylist(
            id = playlistId,
            name = saved?.title ?: playlistId,
            browseId = browseId,
            createdAt = saved?.savedAt,
            lastUpdateTime = System.currentTimeMillis(),
            isEditable = 1L,
            bookmarkedAt = null,
            remoteSongCount = 0L,
            playEndpointParams = null,
            thumbnailUrl = saved?.thumbnail,
            shuffleEndpointParams = null,
            radioEndpointParams = null,
            isLocal = 0L,
            isAutoSync = if (enabled) 1L else 0L
        )
        true
    }

    suspend fun createPlaylist(playlist: Playlist) {
        database.transaction {
            database.playlistQueries.insertPlaylist(
                id = playlist.id,
                name = playlist.name,
                browseId = playlist.browseId,
                createdAt = playlist.createdAt,
                lastUpdateTime = System.currentTimeMillis(),
                isEditable = 1L,
                bookmarkedAt = null,
                remoteSongCount = 0L,
                playEndpointParams = null,
                thumbnailUrl = null,
                shuffleEndpointParams = null,
                radioEndpointParams = null,
                isLocal = 1L,
                isAutoSync = 0L
            )
        }
    }

    fun getSavedPlaylists(): Flow<List<SavedPlaylist>> {
        return database.savedPlaylistQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun isPlaylistSaved(id: String): Flow<Boolean> {
        return database.savedPlaylistQueries.exists(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }
    }

    suspend fun isPlaylistSavedOnce(id: String): Boolean = withContext(Dispatchers.IO) {
        database.savedPlaylistQueries.exists(id).executeAsOneOrNull() ?: false
    }

    suspend fun savePlaylist(playlist: PlaylistItem) = withContext(Dispatchers.IO) {
        database.savedPlaylistQueries.insert(
            id = playlist.id,
            title = playlist.title,
            authorName = playlist.author?.name,
            authorId = playlist.author?.id,
            songCountText = playlist.songCountText,
            thumbnail = playlist.thumbnail,
            savedAt = System.currentTimeMillis()
        )
    }

    suspend fun removePlaylist(id: String) = withContext(Dispatchers.IO) {
        database.savedPlaylistQueries.delete(id)
        database.playlistSongMapQueries.deletePlaylistSongMapsByPlaylist(id)
    }

    suspend fun savePlaylistWithSongs(playlist: PlaylistItem, songs: List<SongItem>) = withContext(Dispatchers.IO) {
        database.transaction {
            database.savedPlaylistQueries.insert(
                id = playlist.id,
                title = playlist.title,
                authorName = playlist.author?.name,
                authorId = playlist.author?.id,
                songCountText = playlist.songCountText,
                thumbnail = playlist.thumbnail,
                savedAt = System.currentTimeMillis()
            )

            database.playlistSongMapQueries.deletePlaylistSongMapsByPlaylist(playlist.id)

            songs.forEachIndexed { index, song ->
                database.songQueries.insertSongIfNotExists(
                    id = song.id,
                    title = song.title,
                    duration = song.duration?.toLong() ?: -1L,
                    thumbnailUrl = song.thumbnail,
                    albumId = song.album?.id,
                    albumName = song.album?.name,
                    explicit = if (song.explicit) 1L else 0L,
                    year = null,
                    date = null,
                    dateModified = null,
                    liked = 0L,
                    likedDate = null,
                    totalPlayTime = 0L,
                    inLibrary = null,
                    dateDownload = null,
                    isLocal = 0L,
                    libraryAddToken = null,
                    libraryRemoveToken = null,
                    lyricsOffset = 0L,
                    romanizeLyrics = 1L,
                    isAgeRestricted = 0L,
                    isDownloaded = 0L,
                    isUploaded = 0L,
                    isVideo = 0L
                )

                song.artists.forEachIndexed { artistIdx, artist ->
                    val artistId = artist.id ?: return@forEachIndexed
                    database.artistQueries.insertArtistIfNotExists(
                        id = artistId,
                        name = artist.name,
                        thumbnailUrl = null,
                        channelId = null,
                        lastUpdateTime = 0L,
                        bookmarkedAt = null,
                        isLocal = 0L
                    )
                    database.songArtistMapQueries.insertSongArtistMap(
                        songId = song.id,
                        artistId = artistId,
                        position = artistIdx.toLong()
                    )
                }

                database.playlistSongMapQueries.insertPlaylistSongMap(
                    playlistId = playlist.id,
                    songId = song.id,
                    position = index.toLong(),
                    setVideoId = song.setVideoId
                )
            }

            database.savedPlaylistQueries.updateSongCountText("${songs.size} canciones", playlist.id)
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, song: SongItem) = withContext(Dispatchers.IO) {
        // Las estanterías del sistema no admiten añadidos manuales ("LM" va por likes, ver
        // LibraryPlaylistsViewModel). Salir sin escribir evita snapshots locales fantasma que
        // nunca se sincronizarían.
        if (isSystemShelf(playlistId)) return@withContext

        val alreadyExists = database.playlistSongMapQueries.selectByPlaylist(playlistId).executeAsList()
            .any { it.songId == song.id }
        if (alreadyExists) return@withContext

        val currentCount = database.playlistSongMapQueries.countByPlaylist(playlistId).executeAsOne()

        database.songQueries.insertSongIfNotExists(
            id = song.id,
            title = song.title,
            duration = song.duration?.toLong() ?: -1L,
            thumbnailUrl = song.thumbnail,
            albumId = song.album?.id,
            albumName = song.album?.name,
            explicit = if (song.explicit) 1L else 0L,
            year = null,
            date = null,
            dateModified = null,
            liked = 0L,
            likedDate = null,
            totalPlayTime = 0L,
            inLibrary = null,
            dateDownload = null,
            isLocal = 0L,
            libraryAddToken = null,
            libraryRemoveToken = null,
            lyricsOffset = 0L,
            romanizeLyrics = 1L,
            isAgeRestricted = 0L,
            isDownloaded = 0L,
            isUploaded = 0L,
            isVideo = 0L
        )

        song.artists.forEachIndexed { artistIdx, artist ->
            val artistId = artist.id ?: return@forEachIndexed
            database.artistQueries.insertArtistIfNotExists(
                id = artistId,
                name = artist.name,
                thumbnailUrl = null,
                channelId = null,
                lastUpdateTime = 0L,
                bookmarkedAt = null,
                isLocal = 0L
            )
            database.songArtistMapQueries.insertSongArtistMap(
                songId = song.id,
                artistId = artistId,
                position = artistIdx.toLong()
            )
        }

        database.playlistSongMapQueries.insertPlaylistSongMap(
            playlistId = playlistId,
            songId = song.id,
            position = currentCount,
            setVideoId = null
        )

        database.savedPlaylistQueries.selectById(playlistId).executeAsOneOrNull()?.let {
            database.savedPlaylistQueries.updateSongCountTextAndThumbnail(
                songCountText = "${currentCount + 1} canciones",
                thumbnail = song.thumbnail,
                id = playlistId
            )
        }

        // Enviar a la playlist de YouTube vinculada — controlado por "Sincronizar con YouTube Music"
        // (experimental, activado por el usuario) ya que esto escribe en la cuenta real del usuario.
        // Mejor esfuerzo: el estado local ya se guardó arriba, así que un error de red aquí no
        // pierde la acción del usuario, simplemente deja la playlist sin sincronizar.
        val browseId = getBrowseId(playlistId)
        if (browseId != null && userPreferences.ytmSyncEnabled.first()) {
            retryWithBackoff { YouTube.addToPlaylist(browseId, song.id) }
                .onSuccess { setVideoId ->
                    if (setVideoId != null) updateSetVideoId(playlistId, song.id, setVideoId)
                }
                .onFailure { Napier.w("Failed to push added song ${song.id} to playlist $browseId: ${it.message}") }
        }
    }

    /** Persiste el `setVideoId` de YTM para una canción ya existente en una playlist local (usado para sincronización remota). */
    suspend fun updateSetVideoId(playlistId: String, songId: String, setVideoId: String) = withContext(Dispatchers.IO) {
        database.playlistSongMapQueries.updateSetVideoId(setVideoId, playlistId, songId)
    }

    /** El `setVideoId` de YTM almacenado para una canción en una playlist local, o null. */
    suspend fun getSetVideoId(playlistId: String, songId: String): String? = withContext(Dispatchers.IO) {
        database.playlistSongMapQueries.selectByPlaylist(playlistId).executeAsList()
            .firstOrNull { it.songId == songId }?.setVideoId
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        val rows = database.playlistSongMapQueries.selectByPlaylist(playlistId).executeAsList()
        val row = rows.firstOrNull { it.songId == songId } ?: return@withContext
        database.playlistSongMapQueries.deletePlaylistSongMap(row.id)

        // Enviar la eliminación a la playlist de YouTube vinculada (misma condición/justificación que al agregar).
        // Sin un setVideoId almacenado (por ejemplo, la canción se agregó antes de que existiera esta
        // función de sincronización) no hay forma de identificar el elemento ante la API de YouTube,
        // así que simplemente omitimos la llamada remota.
        val browseId = getBrowseId(playlistId)
        val setVideoId = row.setVideoId
        if (browseId != null && setVideoId != null && userPreferences.ytmSyncEnabled.first()) {
            retryWithBackoff { YouTube.removeFromPlaylist(browseId, songId, setVideoId) }
                .onFailure { Napier.w("Failed to push removed song $songId from playlist $browseId: ${it.message}") }
        }

        val remaining = database.playlistSongMapQueries.countByPlaylist(playlistId).executeAsOne()
        database.savedPlaylistQueries.selectById(playlistId).executeAsOneOrNull()?.let {
            val fallbackThumbnail = database.playlistSongMapQueries.songsByPlaylist(playlistId)
                .executeAsList()
                .lastOrNull()
                ?.thumbnailUrl
            database.savedPlaylistQueries.updateSongCountTextAndThumbnail(
                songCountText = "$remaining canciones",
                thumbnail = fallbackThumbnail,
                id = playlistId
            )
        }
    }

    suspend fun getCachedPlaylistSongs(playlistId: String): List<SongItem>? = withContext(Dispatchers.IO) {
        val isSaved = database.savedPlaylistQueries.exists(playlistId).executeAsOne()
        if (!isSaved) return@withContext null

        val count = database.playlistSongMapQueries.countByPlaylist(playlistId).executeAsOne()
        if (count == 0L) return@withContext null

        val songs = database.playlistSongMapQueries.songsByPlaylist(playlistId).executeAsList()

        val artistRows = database.songArtistMapQueries.artistsForPlaylistSongs(playlistId).executeAsList()
        val artistsBySong: Map<String, List<Artist>> = artistRows
            .groupBy { it.songId }
            .mapValues { (_, rows) -> rows.map { row -> Artist(name = row.name, id = row.id) } }

        return@withContext songs.map { song ->
            dbSongToSongItem(song, artistsBySong[song.id] ?: emptyList())
        }
    }

    fun getCachedPlaylistItem(playlistId: String): PlaylistItem? {
        val saved = database.savedPlaylistQueries.selectById(playlistId).executeAsOneOrNull()
            ?: return null
        return savedPlaylistToPlaylistItem(saved)
    }
}
