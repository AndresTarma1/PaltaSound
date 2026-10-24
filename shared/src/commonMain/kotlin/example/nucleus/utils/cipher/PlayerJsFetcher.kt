package example.nucleus.utils.cipher

import example.nucleus.network.AppHttpClient
import example.nucleus.platform.AppPaths
import io.github.aakira.napier.Napier
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import java.io.File

object PlayerJsFetcher {
    private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
    private const val PLAYER_JS_URL_TEMPLATE = "https://www.youtube.com/s/player/%s/player_ias.vflset/en_GB/base.js"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

    private val httpClient = AppHttpClient.client

    // Coincidir tanto /s/player/hash/ como \/s\/player\/hash\/ (barras escapadas en JSON)
    private val PLAYER_HASH_REGEX = Regex("""\\?/s\\?/player\\?/([a-zA-Z0-9_-]+)\\?/""")

    private fun getCacheDir(): File {
        val base = File(AppPaths.cacheDir, "cipher_cache")
        if (!base.exists()) base.mkdirs()
        return base
    }

    private fun getCacheFile(hash: String): File = File(getCacheDir(), "player_$hash.js")

    private fun getHashFile(): File = File(getCacheDir(), "current_hash.txt")

    suspend fun getPlayerJs(forceRefresh: Boolean = false): Pair<String, String>? {
        Napier.d("getPlayerJs: forceRefresh=$forceRefresh")

        try {
            getCacheDir()

            if (!forceRefresh) {
                val cached = readFromCache()
                if (cached != null) {
                    Napier.d("Cache hit: hash=${cached.second}, length=${cached.first.length}")
                    return cached
                }
                Napier.d("Cache miss, fetching fresh")
            }

            val hash = fetchPlayerHash() ?: run {
                Napier.e("Failed to extract player hash from iframe_api")
                return null
            }
            Napier.d("Player hash: $hash")

            val playerJs = downloadPlayerJs(hash) ?: run {
                Napier.e("Failed to download player JS for hash=$hash")
                return null
            }

            Napier.d("Downloaded player.js: hash=$hash, length=${playerJs.length}")
            writeToCache(hash, playerJs)

            return Pair(playerJs, hash)
        } catch (e: Exception) {
            Napier.e("getPlayerJs failed: ${e.message}")
            return null
        }
    }

    fun invalidateCache() {
        try {
            val cacheDir = getCacheDir()
            if (cacheDir.exists()) cacheDir.listFiles()?.forEach { it.delete() }
            Napier.d("Cache invalidated")
        } catch (e: Exception) {
            Napier.e("Failed to invalidate cache: ${e.message}")
        }
    }

    /** Memoización del sts por hash: evita releer/escannear el player.js en cada video. */
    private var cachedStsHash: String? = null
    private var cachedSts: Int? = null

    /**
     * Extrae `signatureTimestamp` (sts) del player.js ya descargado — el mismo canal que usa
     * el solucionador EJS para sig/n. El hash del player.js se resuelve desde el iframe_api
     * y es el mismo que referencia la página del video, así que el sts es el correcto para el
     * `/player` (playbackContext). Funciona sin sesión y se memoiza por hash.
     *
     * @return El valor de `signatureTimestamp`, o `null` si el player.js o el token no están.
     */
    @Suppress("SynchronizedMethod")
    suspend fun getSignatureTimestamp(): Int? {
        val playerJs = getPlayerJs() ?: run {
            Napier.e("getSignatureTimestamp: player.js no disponible")
            return null
        }
        if (playerJs.second == cachedStsHash) return cachedSts
        val match = Regex("signatureTimestamp[=:]\\s*(\\d+)").find(playerJs.first)
            ?: run {
                Napier.e("getSignatureTimestamp: no se encontró signatureTimestamp en el player.js")
                return null
            }
        val value = match.groupValues[1].toIntOrNull() ?: return null
        cachedStsHash = playerJs.second
        cachedSts = value
        Napier.d("getSignatureTimestamp: sts=$value (hash=${playerJs.second.take(8)})")
        return value
    }

    private fun readFromCache(): Pair<String, String>? {
        try {
            val hashFile = getHashFile()
            if (!hashFile.exists()) return null

            val lines = hashFile.readText().split("\n")
            if (lines.size < 2) return null

            val hash = lines[0]
            val timestamp = lines[1].toLongOrNull() ?: return null

            if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
                Napier.d("Cache expired for hash=$hash")
                return null
            }

            val cacheFile = getCacheFile(hash)
            if (!cacheFile.exists()) return null

            val js = cacheFile.readText()
            if (js.isEmpty()) return null

            return Pair(js, hash)
        } catch (e: Exception) {
            Napier.e("readFromCache error: ${e.message}")
            return null
        }
    }

    private fun writeToCache(hash: String, playerJs: String) {
        try {
            val cacheDir = getCacheDir()
            cacheDir.listFiles()?.filter { it.name.startsWith("player_") }?.forEach { it.delete() }
            getCacheFile(hash).writeText(playerJs)
            getHashFile().writeText("$hash\n${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Napier.e("writeToCache error: ${e.message}")
        }
    }

    private suspend fun fetchPlayerHash(): String? {
        Napier.d("Fetching iframe_api...")
        val body = try {
            httpClient.get(IFRAME_API_URL) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }.bodyAsText()
        } catch (e: Exception) {
            Napier.e("iframe_api request failed: ${e.message}")
            return null
        }

        val match = PLAYER_HASH_REGEX.find(body)
        if (match == null) {
            Napier.e("Player hash regex not found in iframe_api response")
            return null
        }

        return match.groupValues[1]
    }

    private suspend fun downloadPlayerJs(hash: String): String? {
        val url = PLAYER_JS_URL_TEMPLATE.format(hash)
        Napier.d("Downloading player.js from: $url")
        return try {
            httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }.bodyAsText()
        } catch (e: Exception) {
            Napier.e("player.js download failed: ${e.message}")
            null
        }
    }

    fun getCacheInfo(): Map<String, Any?> {
        return try {
            val hashFile = getHashFile()
            if (!hashFile.exists()) return mapOf("exists" to false)

            val lines = hashFile.readText().split("\n")
            val hash = lines.getOrNull(0)
            val timestamp = lines.getOrNull(1)?.toLongOrNull()
            val cacheFile = hash?.let { getCacheFile(it) }

            mapOf(
                "exists" to true,
                "hash" to hash,
                "timestamp" to timestamp,
                "ageMs" to (timestamp?.let { System.currentTimeMillis() - it }),
                "fileExists" to (cacheFile?.exists() == true),
                "fileSize" to (cacheFile?.length()),
            )
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }
}
