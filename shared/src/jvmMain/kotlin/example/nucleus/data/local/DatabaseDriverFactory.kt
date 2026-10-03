package example.nucleus.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import example.nucleus.data.AppDirs
import example.nucleus.db.MusicPlayerDatabase
import java.io.File
import java.util.Properties
import java.util.logging.Logger

actual object DatabaseDriverFactory {

    private val log = Logger.getLogger("DatabaseDriverFactory")
    private const val SCHEMA_VERSION_FILE = "schema_version"
    const val APP_SCHEMA_VERSION = 5L

    actual fun createDriver(): SqlDriver {
        val appDir = AppDirs.databaseDir
        log.info("DB dir: ${appDir.absolutePath}, exists=${appDir.exists()}, writable=${appDir.canWrite()}")

        if (!appDir.exists()) {
            val created = appDir.mkdirs()
            log.info("Created DB dir: $created")
        }

        val dbFile = File(appDir, "musicplayer.db")
        val versionFile = File(appDir, SCHEMA_VERSION_FILE)

        log.info("DB file: ${dbFile.absolutePath}, exists=${dbFile.exists()}")

        val storedVersion = try {
            if (versionFile.exists()) versionFile.readText().trim().toLong() else 0L
        } catch (e: Exception) {
            log.warning("Could not read schema_version: ${e.message}")
            0L
        }

        log.info("Stored schema version: $storedVersion, required: $APP_SCHEMA_VERSION")

        if (dbFile.exists() && storedVersion < APP_SCHEMA_VERSION) {
            log.info("Schema version mismatch ($storedVersion < $APP_SCHEMA_VERSION) — recreating DB")
            dbFile.delete()
        }

        val needsCreate = !dbFile.exists()
        log.info("needsCreate=$needsCreate")

        // Verificacion defensiva para runtimes recortados en empaquetados.
        try {
            Class.forName("java.sql.DriverManager")
        } catch (e: ClassNotFoundException) {
            log.severe("java.sql.DriverManager not found! The java.sql module is missing from the runtime.")
            log.severe("Ensure 'includeAllModules = true' in compose.desktop.nativeDistributions")
            throw e
        }


        val driver = try {
            JdbcSqliteDriver(
                url = "jdbc:sqlite:${dbFile.absolutePath}",
                properties = Properties().apply {
                    setProperty("journal_mode", "WAL")
                }
            )
        } catch (e: Exception) {
            log.severe("Failed to open SQLite driver: ${e.message}")
            throw e
        }

        if (needsCreate) {
            try {
                MusicPlayerDatabase.Schema.create(driver)
            } catch (e: Exception) {
                log.severe("Failed to create schema: ${e.message}")
                throw e
            }
        } else {
            // Tablas añadidas despues de la creacion inicial.
            //
            // No se sube APP_SCHEMA_VERSION a proposito: arriba, una version mayor borra
            // el fichero entero y con el todas las playlists, canciones y descargas del
            // usuario. Por eso las tablas nuevas se crean aqui de forma idempotente y la
            // version se queda igual. El coste es que esto hay que acordarlo al añadir una:
            // el DDL de abajo tiene que coincidir con el del .sq, o la base nueva y la
            // existente acabaran con esquemas distintos.
            applyAdditiveSchema(driver)
        }

        try { versionFile.writeText(APP_SCHEMA_VERSION.toString()) } catch (_: Exception) {}

        log.info("DB ready: ${dbFile.absolutePath}")
        return driver
    }

    /**
     * DDL de las tablas que se Incorporaron despues de que existiera la base. Todas son
     * `IF NOT EXISTS`, asi que repetirlas en cada arranque no cuesta nada.
     */
    internal fun applyAdditiveSchema(driver: SqlDriver) {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS SavedPodcast (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                authorName TEXT,
                authorId TEXT,
                episodeCountText TEXT,
                thumbnail TEXT,
                savedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        for (sql in statements) {
            try {
                driver.execute(null, sql, 0)
            } catch (e: Exception) {
                // Que falle una tabla nueva no debe impedir abrir la app: la consulta que
                // la use ya dira que no existe, en vez de reventar el arranque.
                log.warning("No se pudo aplicar el DDL aditivo: ${e.message}")
            }
        }
    }
}
