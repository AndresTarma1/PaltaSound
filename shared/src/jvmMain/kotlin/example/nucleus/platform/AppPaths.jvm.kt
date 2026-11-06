package example.nucleus.platform

import java.io.File
import java.util.logging.Logger

actual object AppPaths {
    actual val appName: String = "PaltaSound"

    private val log = Logger.getLogger("AppPaths")
    private const val VENDOR_NAME = "Tarma"

    private val home get() = File(System.getProperty("user.home"))
    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    // --- Resolución de carpetas base del sistema (por SO) ---
    //
    // roamingRoot = persistente (db, ajustes, cookie); localRoot = volátil (caché, tmp, logs).
    //   Windows: %APPDATA%\Tarma\MusicPlayer  y  %LOCALAPPDATA%\Tarma\MusicPlayer
    //   Linux:   $XDG_DATA_HOME/MusicPlayer   y  $XDG_CACHE_HOME/MusicPlayer   (~/.local/share, ~/.cache)
    //   macOS:   ~/Library/Application Support/MusicPlayer  y  ~/Library/Caches/MusicPlayer

    private val roamingRootFile: File by lazy {
        when (Platform.current) {
            OperatingSystem.WINDOWS -> {
                val base = env("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")
                File(File(base, VENDOR_NAME), appName)
            }
            OperatingSystem.MAC -> File(home, "Library/Application Support/$appName")
            else -> {
                val base = env("XDG_DATA_HOME")?.let(::File) ?: File(home, ".local/share")
                File(base, appName)
            }
        }
    }

    private val localRootFile: File by lazy {
        when (Platform.current) {
            OperatingSystem.WINDOWS -> {
                val base = env("LOCALAPPDATA")?.let(::File) ?: File(home, "AppData/Local")
                File(File(base, VENDOR_NAME), appName)
            }
            OperatingSystem.MAC -> File(home, "Library/Caches/$appName")
            else -> {
                val base = env("XDG_CACHE_HOME")?.let(::File) ?: File(home, ".cache")
                File(base, appName)
            }
        }
    }

    // --- Puntos de montaje ---

    actual val roamingRoot: String get() = roamingRootFile.absolutePath
    actual val localRoot: String get() = localRootFile.absolutePath

    // Persistentes (roaming) — %APPDATA%\Tarma\MusicPlayer\
    actual val dataRoot: String get() = roamingRootFile.absolutePath
    actual val configRoot: String get() = roamingRootFile.absolutePath
    actual val databaseDir: String get() = File(roamingRootFile, "db").absolutePath
    actual val preferencesFile: String get() = File(roamingRootFile, "settings.properties").absolutePath
    actual val cookieFile: String get() = File(roamingRootFile, "yt_cookie.txt").absolutePath
    actual val exportsDir: String get() = File(roamingRootFile, "exports").absolutePath

    // Volátiles (local) — %LOCALAPPDATA%\Tarma\MusicPlayer\
    actual val cacheDir: String get() = File(localRootFile, "cache").absolutePath
    actual val imageCacheDir: String get() = File(File(localRootFile, "cache"), "image_cache").absolutePath
    actual val songsDir: String get() = File(File(localRootFile, "cache"), "songs").absolutePath
    actual val tmpDir: String get() = File(localRootFile, "tmp").absolutePath
    actual val logsDir: String get() = File(localRootFile, "logs").absolutePath

    actual fun ensureDirectories() {
        listOf(
            roamingRootFile,
            localRootFile,
            // Persistentes
            File(roamingRootFile, "db"),
            File(roamingRootFile, "exports"),
            // Volátiles
            File(localRootFile, "cache"),
            File(localRootFile, "cache/image_cache"),
            File(localRootFile, "cache/songs"),
            File(localRootFile, "tmp"),
            File(localRootFile, "logs"),
        ).forEach { dir ->
            try {
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    log.info("Creada carpeta: ${dir.absolutePath} (ok=$created)")
                }
            } catch (e: Exception) {
                log.warning("Error creando ${dir.absolutePath}: ${e.message}")
            }
        }
    }

}

