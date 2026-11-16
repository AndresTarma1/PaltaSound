package example.nucleus.platform

/**
 * Contrato multiplataforma para rutas de la aplicacion.
 *
 * Sigue las mejores prácticas de cada SO:
 * - Windows: datos persistentes en %APPDATA%, volátiles en %LOCALAPPDATA%
 * - Linux/macOS: XDG Base Directory Specification
 *
 * commonMain depende solo de Strings para evitar acoplarse a java.io.File.
 */
expect object AppPaths {
    val appName: String

    /** Raíz de datos persistentes (roaming). Sigue al usuario entre equipos. */
    val roamingRoot: String
    /** Raíz de datos volátiles/pesados (local). Los limpiadores de disco pueden borrarla. */
    val localRoot: String

    // Persistentes — no deben perderse en limpiezas de disco
    val dataRoot: String
    val configRoot: String
    val databaseDir: String
    val preferencesFile: String
    val cookieFile: String
    val exportsDir: String

    // Volátiles — se pueden regenerar
    val cacheDir: String
    val imageCacheDir: String
    val songsDir: String
    val tmpDir: String
    val logsDir: String

    fun ensureDirectories()
}

