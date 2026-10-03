package example.nucleus

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.PodcastItem
import example.nucleus.data.local.DatabaseDriverFactory
import example.nucleus.db.MusicPlayerDatabase
import example.nucleus.data.repository.PodcastRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El DDL aditivo esta duplicado a mano (esta cadena y el .sq). Si divergen, la tabla
 * existe con columnas distintas y las consultas fallan al usarla, no al arrancar. Estos
 * dos casos cubren las dos rutas: base nueva (Schema.create) y base existente (aditivo).
 */
class SavedPodcastSchemaTest {

    private val files = mutableListOf<File>()

    private fun tempDb(name: String): Pair<JdbcSqliteDriver, MusicPlayerDatabase> {
        val f = File.createTempFile(name, ".db")
        f.delete()
        files += f
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${f.absolutePath}",
            properties = Properties().apply { setProperty("journal_mode", "WAL") },
        )
        return driver to MusicPlayerDatabase(driver)
    }

    @AfterTest
    fun cleanup() {
        files.forEach {
            it.delete()
            File(it.absolutePath + "-wal").delete()
            File(it.absolutePath + "-shm").delete()
        }
    }

    private fun podcast(id: String) = PodcastItem(
        id = id,
        title = "Negra y criminal",
        author = Artist(name = "Radio Polombia", id = "UC_test"),
        episodeCountText = "120 episodios",
        thumbnail = "https://example.invalid/t.jpg",
        playEndpoint = null,
        shuffleEndpoint = null,
    )

    @Test
    fun `base nueva - Schema create incluye SavedPodcast`() = runBlocking {
        val (driver, db) = tempDb("fresh")
        MusicPlayerDatabase.Schema.create(driver)

        val repo = PodcastRepository(db)
        repo.savePodcast(podcast("MPSP_fresh"))

        val rows = repo.getSavedPodcasts().first()
        assertEquals(1, rows.size, "no se guardo el podcast")
        assertEquals("Negra y criminal", rows.first().title)
        assertEquals("Radio Polombia", rows.first().authorName)
        assertEquals("120 episodios", rows.first().episodeCountText)
        assertTrue(repo.isPodcastSaved("MPSP_fresh").first())

        repo.removePodcast("MPSP_fresh")
        assertTrue(repo.getSavedPodcasts().first().isEmpty(), "no se elimino el podcast")
        driver.close()
    }

    @Test
    fun `base existente - el DDL aditivo crea la tabla con las columnas del sq`() = runBlocking {
        val (driver, db) = tempDb("existing")
        MusicPlayerDatabase.Schema.create(driver)
        // Simula una base creada antes de que existiera la tabla.
        driver.execute(null, "DROP TABLE SavedPodcast", 0)

        DatabaseDriverFactory.applyAdditiveSchema(driver)
        // Idempotente: se ejecuta en cada arranque.
        DatabaseDriverFactory.applyAdditiveSchema(driver)

        val repo = PodcastRepository(db)
        repo.savePodcast(podcast("MPSP_existing"))

        val rows = repo.getSavedPodcasts().first()
        assertEquals(1, rows.size, "el DDL aditivo no deja la tabla utilizable")
        assertEquals("MPSP_existing", rows.first().id)
        assertEquals("https://example.invalid/t.jpg", rows.first().thumbnail)
        driver.close()
    }
}