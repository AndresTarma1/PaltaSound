package example.nucleus.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.PodcastItem
import example.nucleus.db.MusicPlayerDatabase
import example.nucleus.db.SavedPodcast
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Copia local de los podcasts guardados.
 *
 * La fuente de verdad sigue siendo la cuenta: guardar un podcast es un `likePlaylist`
 * remoto y en ningun momento se toca esta tabla para decidir si esta guardado. Lo que
 * aporta es que la biblioteca los muestre sin conexion y que no dependa de que el
 * estante remoto se lea bien.
 */
class PodcastRepository(
    val database: MusicPlayerDatabase,
) {

    fun getSavedPodcasts(): Flow<List<SavedPodcast>> =
        database.savedPodcastQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun isPodcastSaved(id: String): Flow<Boolean> =
        database.savedPodcastQueries.exists(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }

    suspend fun savePodcast(podcast: PodcastItem) = withContext(Dispatchers.IO) {
        try {
            database.savedPodcastQueries.insert(
                id = podcast.id,
                title = podcast.title,
                authorName = podcast.author?.name,
                authorId = podcast.author?.id,
                episodeCountText = podcast.episodeCountText,
                thumbnail = podcast.thumbnail,
                savedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            // El podcast ya esta guardado en la cuenta; si la copia local falla, avisar y
            // seguir, que perder el guardado local no debe romper el guardado remoto.
            Napier.w("No se pudo guardar el podcast ${podcast.id} en local: ${e.message}")
        }
    }

    suspend fun removePodcast(id: String) = withContext(Dispatchers.IO) {
        try {
            database.savedPodcastQueries.delete(id)
        } catch (e: Exception) {
            Napier.w("No se pudo quitar el podcast $id de local: ${e.message}")
        }
    }
}

fun savedPodcastToPodcastItem(saved: SavedPodcast): PodcastItem = PodcastItem(
    id = saved.id,
    title = saved.title,
    author = saved.authorName?.let { Artist(name = it, id = saved.authorId) },
    episodeCountText = saved.episodeCountText,
    thumbnail = saved.thumbnail,
    playEndpoint = null,
    shuffleEndpoint = null,
)