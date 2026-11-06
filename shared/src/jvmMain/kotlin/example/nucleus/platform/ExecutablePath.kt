package example.nucleus.platform

import java.util.concurrent.TimeUnit

/**
 * Busca un ejecutable en el PATH con `where` en Windows o `which` en el resto.
 * Devuelve la primera ruta no vacía, o `null` si no está instalado.
 */
internal fun findExecutableOnPath(name: String): String? = try {
    val which = if (Platform.isWindows) "where" else "which"
    val proc = ProcessBuilder(which, name).redirectErrorStream(true).start()
    val out = proc.inputStream.bufferedReader().readText()
    if (proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0) {
        out.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
    } else {
        null
    }
} catch (_: Exception) {
    null
}
