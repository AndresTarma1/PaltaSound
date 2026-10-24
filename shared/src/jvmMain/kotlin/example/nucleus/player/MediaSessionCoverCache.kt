package example.nucleus.player

import example.nucleus.data.AppDirs
import example.nucleus.utils.upscaleThumbnailUrl
import io.github.aakira.napier.Napier
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Collections

/**
 * Materializa carátulas en disco para SMTC/MPRIS.
 *
 * Nucleus 2.4.7+ en Windows carga bien covers **locales** (`file://` o ruta bare)
 * vía `StorageFile` + `CreateFromFile`. Las URLs `https` del CDN de YouTube siguen
 * siendo frágiles (red, caducidad, offline). Preferimos un JPEG/PNG en caché por
 * [songId] y hacemos fallback a la URL remota si la descarga falla.
 */
object MediaSessionCoverCache {

    private val dir: File by lazy {
        File(AppDirs.imageCacheDir, "media-session-covers").also { it.mkdirs() }
    }

    /**
     * songId → last remote URL used to fill the cache file (avoid redownload same).
     * LRU acotada: solo sirve para no re-descargar la misma carátula, no necesita crecer
     * sin límite con cada canción de la sesión.
     */
    private val urlBySongId = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 256
        },
    )

    /**
     * Devuelve la mejor [coverUrl] para media-control:
     * 1. Path/`file://` ya local
     * 2. Fichero en caché por [songId]
     * 3. Descarga de [remoteOrLocalUrl] a caché y path local
     * 4. Fallback: URL remota original (o null)
     */
    fun resolveForMediaSession(songId: String, remoteOrLocalUrl: String?): String? {
        val raw = remoteOrLocalUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (raw == null) {
            return existingCacheFile(songId)?.let { toCoverUrl(it) }
        }

        asLocalFile(raw)?.let { local ->
            if (local.isFile && local.length() > 0L) return toCoverUrl(local)
        }

        existingCacheFile(songId)?.let { cached ->
            val previousUrl = urlBySongId[songId]
            if (previousUrl == null || previousUrl == raw || looksSameAsset(previousUrl, raw)) {
                return toCoverUrl(cached)
            }
        }

        if (!isRemoteHttp(raw)) {
            return raw
        }

        val downloaded = downloadToCache(songId, raw)
        if (downloaded != null) {
            urlBySongId[songId] = raw
            return toCoverUrl(downloaded)
        }

        // Fallback online (comportamiento previo) por si SMTC aún puede pintar https.
        return raw
    }

    fun clearSong(songId: String) {
        urlBySongId.remove(songId)
        for (ext in listOf("jpg", "jpeg", "png", "webp")) {
            File(dir, "$songId.$ext").delete()
        }
    }

    private fun existingCacheFile(songId: String): File? {
        for (ext in listOf("jpg", "jpeg", "png", "webp")) {
            val f = File(dir, "$songId.$ext")
            if (f.isFile && f.length() > 64L) return f
        }
        return null
    }

    private fun downloadToCache(songId: String, url: String): File? {
        val candidates = buildList {
            upscaleThumbnailUrl(url, 544)?.let { add(it) }
            add(url)
        }.distinct()

        for (candidate in candidates) {
            val file = tryDownload(songId, candidate)
            if (file != null) return file
        }
        return null
    }

    private fun tryDownload(songId: String, url: String): File? {
        return try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            )
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.disconnect()
                Napier.d("MediaSessionCoverCache: HTTP $code for $songId")
                return null
            }
            val contentType = connection.contentType.orEmpty().lowercase()
            val ext = when {
                contentType.contains("png") -> "png"
                contentType.contains("webp") -> "webp"
                contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
                url.substringAfterLast('.').lowercase() in setOf("png", "webp", "jpg", "jpeg") ->
                    url.substringAfterLast('.').lowercase().let { if (it == "jpeg") "jpg" else it }
                else -> "jpg"
            }
            val target = File(dir, "$songId.$ext")
            val part = File(dir, "$songId.$ext.part")
            connection.inputStream.use { input ->
                part.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()
            if (part.length() < 64L) {
                part.delete()
                return null
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            // Limpia otras extensiones viejas del mismo id
            for (other in listOf("jpg", "jpeg", "png", "webp")) {
                if (other != ext) File(dir, "$songId.$other").delete()
            }
            target
        } catch (t: Throwable) {
            Napier.d("MediaSessionCoverCache: download failed for $songId: ${t.message}")
            null
        }
    }

    private fun asLocalFile(value: String): File? {
        val v = value.trim()
        return when {
            v.startsWith("file:", ignoreCase = true) -> {
                runCatching { File(URI(v)) }.getOrNull()
                    ?: runCatching {
                        val path = URI(v).path ?: return@runCatching null
                        File(path)
                    }.getOrNull()
            }
            // Windows bare path / Unix absolute
            v.length >= 2 && (v[1] == ':' || v.startsWith("/") || v.startsWith("\\\\")) -> File(v)
            else -> null
        }
    }

    private fun isRemoteHttp(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)

    private fun looksSameAsset(a: String, b: String): Boolean {
        if (a == b) return true
        fun base(u: String) = u.substringBefore('?').substringBefore('=')
        return base(a) == base(b)
    }

    /**
     * Nucleus Windows acepta ruta bare o file://; preferimos URI file para cross-platform.
     */
    private fun toCoverUrl(file: File): String = file.absoluteFile.toURI().toString()
}
