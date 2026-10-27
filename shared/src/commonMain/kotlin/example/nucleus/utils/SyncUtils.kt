package example.nucleus.utils

import example.nucleus.data.repository.AlbumRepository
import example.nucleus.data.repository.ArtistRepository
import example.nucleus.data.repository.PlaylistRepository
import example.nucleus.data.repository.SongRepository
import example.nucleus.db.DatabaseDao
import example.nucleus.db.entities.PlaylistEntity
import example.nucleus.models.toMediaMetadata
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.utils.completed
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

sealed class SyncOperation {
    data object FullSync : SyncOperation()
    data object SavedPlaylists : SyncOperation()
    /** Obtiene la lista completa de canciones de una playlist desde YouTube y sobrescribe la copia local. */
    data class SinglePlaylist(val playlistId: String, val browseId: String) : SyncOperation()
    /** Ejecuta [SinglePlaylist] para cada playlist local marcada con `isAutoSync`. */
    data object AutoSyncPlaylists : SyncOperation()
    /** Solo local: colapsa playlists que comparten el mismo `browseId`, conservando la más completa. */
    data object CleanupDuplicates : SyncOperation()
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data object Completed : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

data class SyncState(
    val overallStatus: SyncStatus = SyncStatus.Idle,
    val playlists: SyncStatus = SyncStatus.Idle,
    val currentOperation: String = ""
)

class SyncUtils(
    private val database: DatabaseDao,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val playlistRepository: PlaylistRepository,
) {
    private val syncJob = SupervisorJob()
    private val syncScope = CoroutineScope(Dispatchers.IO + syncJob)
    private val syncChannel = Channel<SyncOperation>(Channel.BUFFERED)
    private var processingJob: Job? = null

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init { startProcessingQueue() }

    private fun startProcessingQueue() {
        processingJob = syncScope.launch {
            for (operation in syncChannel) {
                try {
                    when (operation) {
                        SyncOperation.FullSync -> executeFullSync()
                        SyncOperation.SavedPlaylists -> executeSavedPlaylists()
                        is SyncOperation.SinglePlaylist -> executeSinglePlaylist(operation.playlistId, operation.browseId)
                        SyncOperation.AutoSyncPlaylists -> executeAutoSyncPlaylists()
                        SyncOperation.CleanupDuplicates -> executeCleanupDuplicates()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Napier.e("Error processing sync operation: $operation", e)
                }
            }
        }
    }

    private fun updateState(update: SyncState.() -> SyncState) {
        _syncState.value = _syncState.value.update()
    }

    fun syncSavedPlaylists() { syncScope.launch { syncChannel.send(SyncOperation.SavedPlaylists) } }
    fun performFullSync() { syncScope.launch { syncChannel.send(SyncOperation.FullSync) } }
    fun syncPlaylist(playlistId: String, browseId: String) {
        syncScope.launch { syncChannel.send(SyncOperation.SinglePlaylist(playlistId, browseId)) }
    }
    fun syncAutoSyncPlaylists() { syncScope.launch { syncChannel.send(SyncOperation.AutoSyncPlaylists) } }
    fun cleanupDuplicatePlaylists() { syncScope.launch { syncChannel.send(SyncOperation.CleanupDuplicates) } }

    private suspend fun executeFullSync() {
        updateState { copy(overallStatus = SyncStatus.Syncing, currentOperation = "Syncing full library") }
        try {
            executeLikedSongs()
            executeLibrarySongs()
            executeUploadedSongs()
            executeLikedAlbums()
            executeUploadedAlbums()
            executeArtistsSubscriptions()
            executeSavedPlaylists()
            updateState { copy(overallStatus = SyncStatus.Completed, currentOperation = "") }
        } catch (e: Exception) {
            updateState { copy(overallStatus = SyncStatus.Error(e.message ?: "Unknown error"), currentOperation = "") }
        }
    }

    /**
     * Obtiene "LM" (Liked Music) y reconcilia la bandera real `Song.liked` — la que el botón de
     * like/corazón y `PlayerViewModel.toggleLike()` leen/escriben — no solo la tabla separada de
     * caché `SavedSong` (que solo alimenta la lista de "canciones" de la Biblioteca). Sin esto,
     * una canción marcada como favorita desde tu teléfono/web de YTM nunca se mostraba como
     * favorita dentro del propio MusicPlayer.
     */
    private suspend fun executeLikedSongs() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing liked songs") }
        try {
            val remoteSongs = YouTube.playlist("LM").completed().getOrNull()?.songs.orEmpty()
            val remoteIds = remoteSongs.map { it.id }.toSet()

            // Refleja los des-likes hechos desde el propio YouTube.
            database.likedSongs().first()
                .filterNot { it.id in remoteIds }
                .forEach { database.insertSong(it.localToggleLike()) }

            val now = java.time.LocalDateTime.now()
            remoteSongs.forEachIndexed { index, song ->
                songRepository.saveSong(song)

                val timestamp = now.minusSeconds(index.toLong())
                val existing = database.songById(song.id).first()
                if (existing == null) {
                    database.insertSong(
                        song.toMediaMetadata().toSongEntity().copy(liked = true, likedDate = timestamp)
                    )
                } else if (!existing.liked || existing.likedDate != timestamp) {
                    database.insertSong(existing.copy(liked = true, likedDate = timestamp))
                }
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing liked songs", e)
        }
    }

    private suspend fun executeLibrarySongs() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing library songs") }
        try {
            val remoteSongs = YouTube.library("FEmusic_liked_videos").completed().getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.SongItem>().orEmpty()
            remoteSongs.forEach { song ->
                songRepository.saveSong(song)
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing library songs", e)
        }
    }

    private suspend fun executeUploadedSongs() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing uploaded songs") }
        try {
            val remoteSongs = YouTube.library("FEmusic_library_privately_owned_tracks", tabIndex = 1).completed().getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.SongItem>().orEmpty()
            remoteSongs.forEach { song ->
                songRepository.saveSong(song)
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing uploaded songs", e)
        }
    }

    private suspend fun executeLikedAlbums() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing liked albums") }
        try {
            val remoteAlbums = YouTube.library("FEmusic_liked_albums").completed().getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.AlbumItem>().orEmpty()
            remoteAlbums.forEach { album ->
                albumRepository.saveAlbum(album)
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing liked albums", e)
        }
    }

    private suspend fun executeUploadedAlbums() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing uploaded albums") }
        try {
            val remoteAlbums = YouTube.library("FEmusic_library_privately_owned_releases").completed().getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.AlbumItem>().orEmpty()
            remoteAlbums.forEach { album ->
                albumRepository.saveAlbum(album)
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing uploaded albums", e)
        }
    }

    private suspend fun executeArtistsSubscriptions() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing artists") }
        try {
            val remoteArtists = YouTube.library("FEmusic_library_corpus_artists").completed().getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.ArtistItem>().orEmpty()
            val remoteIds = remoteArtists.map { it.id }.toSet()

            // Refleja las des-suscripciones hechas desde el propio YouTube: elimina artistas guardados
            // localmente que ya no están en la lista remota de suscripciones (refleja la misma
            // reconciliación de executeSavedPlaylists).
            artistRepository.getSavedArtists().first()
                .map { it.id }
                .filterNot { it in remoteIds }
                .forEach { artistRepository.removeArtist(it) }

            remoteArtists.forEach { artist ->
                artistRepository.saveArtist(artist)
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing artists", e)
        }
    }

    private suspend fun executeSavedPlaylists() = withContext(Dispatchers.IO) {
        updateState { copy(playlists = SyncStatus.Syncing, currentOperation = "Syncing saved playlists") }

        try {
            val remotePlaylists = YouTube.library("FEmusic_liked_playlists")
                .completed()
                .getOrNull()
                ?.items
                ?.filterIsInstance<PlaylistItem>()
                ?.filterNot { it.id == "LM" || it.id == "SE" }
                ?.reversed()
                .orEmpty()

            val remoteIds = remotePlaylists.map { it.id }.toSet()
            val localPlaylists = database.playlistsByNameAsc().first()

            localPlaylists
                .filter { it.browseId != null }
                .filterNot { it.browseId in remoteIds }
                .forEach { playlist ->
                    playlistRepository.removePlaylist(playlist.id)
                    delay(50.milliseconds)
                }

            remotePlaylists.forEach { playlist ->
                try {
                    val existing = localPlaylists.firstOrNull { it.browseId == playlist.id }
                    val entity = PlaylistEntity(
                        id = existing?.id ?: playlist.id,
                        name = playlist.title,
                        browseId = playlist.id,
                        createdAt = existing?.createdAt,
                        lastUpdateTime = existing?.lastUpdateTime,
                        isEditable = playlist.isEditable,
                        bookmarkedAt = existing?.bookmarkedAt,
                        remoteSongCount = playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                        playEndpointParams = playlist.playEndpoint?.params,
                        thumbnailUrl = playlist.thumbnail,
                        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                        radioEndpointParams = playlist.radioEndpoint?.params,
                        isLocal = existing?.isLocal ?: false,
                        // Por defecto las playlists vinculadas sincronizan (comportamiento histórico
                        // del job); el usuario puede excluirlas una a una con el toggle de la pantalla.
                        isAutoSync = existing?.isAutoSync ?: true
                    )
                    database.insertPlaylist(entity)
                } catch (e: Exception) {
                    Napier.e("Failed to sync playlist ${playlist.title}", e)
                }
            }

            updateState { copy(playlists = SyncStatus.Completed, currentOperation = "") }
        } catch (e: Exception) {
            updateState { copy(playlists = SyncStatus.Error(e.message ?: "Unknown error"), currentOperation = "") }
        }
    }

    /**
     * Obtiene la lista completa de canciones de una playlist (paginando hasta agotar la
     * continuación, con tope de seguridad) y sobrescribe la copia local. Esta es la
     * contraparte de [PlaylistRepository.addSongToPlaylist]/`removeSongFromPlaylist`, que envían las
     * ediciones locales a YouTube inmediatamente — para cuando esto se ejecuta, YouTube ya debería
     * reflejar cualquier cambio local, por lo que una obtención y reemplazo completos aquí es segura
     * (no una sobrescritura con pérdida de ediciones locales pendientes).
     */
    private suspend fun executeSinglePlaylist(playlistId: String, browseId: String) = withContext(Dispatchers.IO) {
        try {
            val first = YouTube.playlist(browseId).getOrNull() ?: return@withContext
            val allSongs = first.songs.toMutableList()
            var token = first.songsContinuation?.takeIf { it.isNotBlank() }
            // Paginar hasta completar (tope de seguridad): antes solo se guardaba la primera
            // página y las playlists grandes quedaban parcialmente sincronizadas.
            var pages = 1
            while (token != null && pages < 20) {
                val next = YouTube.playlistContinuation(token).getOrNull()
                if (next == null) break
                allSongs.addAll(next.songs)
                token = next.continuation?.takeIf { it.isNotBlank() }
                pages++
                delay(50.milliseconds)
            }
            // Guardar bajo el id con el que se abrió (puede diferir del browseId remoto,
            // p. ej. espejos de playlists locales): si no, la caché se parte en dos filas.
            playlistRepository.savePlaylistWithSongs(first.playlist.copy(id = playlistId), allSongs)
        } catch (e: Exception) {
            Napier.e("Failed syncing playlist $playlistId ($browseId)", e)
        }
    }

    /**
     * Vuelve a obtener cada playlist vinculada con auto-sync activado, reconciliando cualquier
     * edición hecha desde la aplicación/sitio de YouTube desde que MusicPlayer la vio por última
     * vez. El interruptor por playlist vive en la pantalla de playlist; por defecto las
     * vinculadas sincronizan (se excluyen con el toggle).
     */
    private suspend fun executeAutoSyncPlaylists() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing auto-sync playlists") }
        try {
            database.allPlaylists().first()
                .filter { it.browseId != null && it.isAutoSync }
                .forEach { playlist ->
                    executeSinglePlaylist(playlist.id, playlist.browseId!!)
                    delay(50.milliseconds)
                }
            executeLikedPlaylistRefresh()
        } catch (e: Exception) {
            Napier.e("Failed syncing auto-sync playlists", e)
        } finally {
            updateState { copy(currentOperation = "") }
        }
    }

    /**
     * "Me gusta" no es una playlist con interruptor (es un estante del sistema), pero si el
     * usuario la guardó debe mantenerse al día igual que las vinculadas: refresca sus
     * canciones dentro de la sincronización global.
     */
    private suspend fun executeLikedPlaylistRefresh() {
        try {
            if (!playlistRepository.isPlaylistSavedOnce("LM")) return
            updateState { copy(currentOperation = "Syncing liked songs playlist") }
            executeSinglePlaylist("LM", "LM")
        } catch (e: Exception) {
            Napier.e("Failed syncing liked playlist", e)
        }
    }

    /** Colapsa playlists que comparten un `browseId` (puede ocurrir después de fallos de cuenta/biblioteca),
     * conservando la que tiene más canciones en caché local y eliminando el resto. Solo local, sin
     * llamadas de red. */
    private suspend fun executeCleanupDuplicates() = withContext(Dispatchers.IO) {
        try {
            database.allPlaylists().first()
                .filter { it.browseId != null }
                .groupBy { it.browseId }
                .values
                .filter { it.size > 1 }
                .forEach { duplicates ->
                    val keep = duplicates.maxByOrNull { database.countByPlaylist(it.id) }
                    duplicates.filterNot { it.id == keep?.id }.forEach { database.deletePlaylist(it.id) }
                }
        } catch (e: Exception) {
            Napier.e("Failed cleaning up duplicate playlists", e)
        }
    }

    fun cancelAllSyncs() {
        processingJob?.cancel()
        syncJob.cancel()
        updateState { SyncState() }
    }
}
