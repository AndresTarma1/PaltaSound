package example.nucleus.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Local HTTP proxy for direct googlevideo URLs.
 *
 * libmpv's own HTTP stack gets 403 on the `rqh=1` URLs returned by yt-dlp
 * (the CDN expects a bounded Range request plus a browser-like identity).
 * This proxy forwards explicit ranges with the expected headers, stitching
 * bounded upstream chunks into one continuous response so mpv can play and
 * seek without the CDN's open-ended throttling.
 *
 * It is intentionally small and generic: phase 2 can replace it with the
 * SABR/DASH proxy without changing the player-facing URL contract.
 */
object DirectStreamProxyServer {
    private val log = Logger.getLogger("DirectStreamProxyServer")
    private val sessions = ConcurrentHashMap<String, String>()
    private val manifests = ConcurrentHashMap<String, String>()
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    @Volatile private var server: HttpServer? = null
    @Volatile private var port: Int = 0

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    // googlevideo throttles open-ended downloads but serves bounded ranges at
    // full speed, so the proxy stitches 4 MB upstream ranges into one response.
    private const val CHUNK_BYTES = 4L * 1024 * 1024

    fun register(url: String): String {
        ensureStarted()
        // Keep the map bounded; players open at most a handful of streams.
        if (sessions.size > 16) {
            sessions.keys.take(sessions.size - 16).forEach(sessions::remove)
        }
        val id = UUID.randomUUID().toString().replace("-", "")
        sessions[id] = url
        return "http://127.0.0.1:$port/stream/$id"
    }

    fun registerManifest(xml: String): String {
        ensureStarted()
        val id = UUID.randomUUID().toString().replace("-", "")
        manifests[id] = xml
        return "http://127.0.0.1:$port/manifest/$id"
    }

    fun unregister(localUrl: String) {
        val id = localUrl.substringAfterLast('/')
        if (localUrl.contains("/manifest/")) {
            manifests.remove(id)
        } else {
            sessions.remove(id)
        }
    }

    private fun ensureStarted() {
        if (server != null) return
        synchronized(this) {
            if (server != null) return
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            s.createContext("/stream/") { exchange -> handle(exchange) }
            s.createContext("/manifest/") { exchange -> handleManifest(exchange) }
            s.executor = java.util.concurrent.Executors.newCachedThreadPool { r ->
                Thread(r, "direct-stream-proxy").apply { isDaemon = true }
            }
            s.start()
            server = s
            port = s.address.port
            log.info("DirectStreamProxy escuchando en 127.0.0.1:$port")
        }
    }

    private fun handleManifest(exchange: HttpExchange) {
        val id = exchange.requestURI.path.removePrefix("/manifest/")
        val xml = manifests[id]
        if (xml == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }
        val bytes = xml.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.apply {
            set("Content-Type", "application/dash+xml")
            set("Cache-Control", "no-store")
        }
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun handle(exchange: HttpExchange) {
        val id = exchange.requestURI.path.removePrefix("/stream/")
        val upstreamUrl = sessions[id]
        if (upstreamUrl.isNullOrBlank()) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }

        val rangeHeader = exchange.requestHeaders.getFirst("Range")
        val requested = parseRange(rangeHeader)
        var headersSent = false
        try {
            val total = totalBytes(upstreamUrl)
            if (total == null) {
                streamSingle(exchange, upstreamUrl, requested, rangeHeader != null) {
                    headersSent = true
                }
                return
            }

            val start = requested.start.coerceIn(0L, total - 1)
            val end = (requested.end ?: (total - 1)).coerceIn(start, total - 1)
            val length = end - start + 1
            val firstEnd = minOf(start + CHUNK_BYTES - 1, end)

            var response = upstream(upstreamUrl, "bytes=$start-$firstEnd")
            // Some account/visitor-bound URLs refuse a bounded Range but still
            // serve the full object. Only use that fallback from byte 0: a 200
            // response cannot satisfy a mid-file seek.
            if (response.statusCode() == 403 && start == 0L) {
                response.body().close()
                log.warning("proxy 403 con Range; reintentando sin Range")
                response = upstream(upstreamUrl, null)
            }
            var status = response.statusCode()
            if (status !in 200..299) {
                log.warning("Upstream HTTP $status para ${upstreamUrl.take(80)}")
                response.body().close()
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
                return
            }
            if (status == 200 && start > 0L) {
                response.body().close()
                log.warning("Upstream ignoró el Range desde $start; no se puede seekear")
                exchange.sendResponseHeaders(502, -1)
                exchange.close()
                return
            }

            val contentType = response.headers().firstValue("Content-Type")
                .orElse("application/octet-stream")
            val downstreamStatus = if (rangeHeader != null || start > 0L) 206 else 200
            exchange.responseHeaders.apply {
                set("Content-Type", contentType)
                set("Accept-Ranges", "bytes")
                if (downstreamStatus == 206) {
                    set("Content-Range", "bytes $start-$end/$total")
                }
                set("Cache-Control", "no-store")
            }
            exchange.sendResponseHeaders(downstreamStatus, length)
            headersSent = true
            log.info(
                "proxy $downstreamStatus range=$start-$end/$total " +
                    "chunks=${((length + CHUNK_BYTES - 1) / CHUNK_BYTES)}",
            )

            exchange.responseBody.use { output ->
                var pos = start
                while (pos <= end) {
                    val chunkEnd = minOf(pos + CHUNK_BYTES - 1, end)
                    if (pos != start) {
                        response = upstream(upstreamUrl, "bytes=$pos-$chunkEnd")
                        status = response.statusCode()
                        if (status !in 200..299) {
                            response.body().close()
                            error("upstream HTTP $status en el byte $pos")
                        }
                        if (status == 200) {
                            response.body().close()
                            error("upstream ignoró el Range en el byte $pos")
                        }
                    }
                    val expected = chunkEnd - pos + 1
                    val copied = response.body().use { input ->
                        copy(input, output, expected)
                    }
                    if (copied < expected) {
                        log.warning(
                            "upstream cortó en $pos: $copied/$expected bytes; " +
                                "se aborta para no corromper el stream",
                        )
                        break
                    }
                    pos = chunkEnd + 1
                    output.flush()
                }
            }
        } catch (e: Exception) {
            // mpv closes the socket as soon as it has buffered enough, so a
            // reset here is normal and must not be reported as a failure.
            if (headersSent && isClientDisconnect(e)) {
                log.fine("cliente cerró el stream de ${upstreamUrl.take(80)}")
            } else {
                log.warning("proxy falló para ${upstreamUrl.take(80)}: ${e.message}")
            }
            if (!headersSent) {
                runCatching {
                    exchange.sendResponseHeaders(502, -1)
                }
            }
            runCatching { exchange.close() }
        }
    }

    /** Fallback for URLs without a `clen` parameter: one upstream request. */
    private fun streamSingle(
        exchange: HttpExchange,
        upstreamUrl: String,
        requested: ByteRange,
        hasRange: Boolean,
        onHeadersSent: () -> Unit,
    ) {
        var response = upstream(upstreamUrl, if (hasRange) requested.toHeader() else null)
        if (response.statusCode() == 403 && requested.start == 0L) {
            response.body().close()
            response = upstream(upstreamUrl, null)
        }
        val status = response.statusCode()
        if (status !in 200..299) {
            response.body().close()
            log.warning("Upstream HTTP $status para ${upstreamUrl.take(80)}")
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
            return
        }
        val contentType = response.headers().firstValue("Content-Type")
            .orElse("application/octet-stream")
        val contentLength = response.headers().firstValue("Content-Length")
            .orElse(null)?.toLongOrNull()
        exchange.responseHeaders.apply {
            set("Content-Type", contentType)
            set("Accept-Ranges", "bytes")
            if (status == 206) {
                response.headers().firstValue("Content-Range")
                    .orElse(null)?.let { set("Content-Range", it) }
            }
            set("Cache-Control", "no-store")
        }
        exchange.sendResponseHeaders(status, contentLength ?: 0)
        onHeadersSent()
        response.body().use { input ->
            exchange.responseBody.use { output -> copy(input, output, Long.MAX_VALUE) }
        }
    }

    private fun upstream(url: String, rangeHeader: String?): HttpResponse<InputStream> {
        val builder = HttpRequest.newBuilder(URI(url))
            .GET()
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://www.youtube.com/")
            .header("Origin", "https://www.youtube.com")
            .header("DNT", "1")
            .header("Sec-Fetch-Site", "cross-site")
            .header("Sec-Fetch-Mode", "no-cors")
            .header("Sec-Fetch-Dest", "video")
        if (rangeHeader != null) builder.header("Range", rangeHeader)
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
    }

    private data class ByteRange(val start: Long, val end: Long?) {
        fun toHeader(): String = "bytes=$start-${end ?: ""}"
    }

    /**
     * Forwards the range untouched. DASH `SegmentBase` needs to read the
     * representation sequentially, so capping `bytes=N-` makes the demuxer
     * believe the file ends at the cap and corrupts every later packet.
     */
    private fun parseRange(header: String?): ByteRange {
        if (header == null) return ByteRange(0L, null)
        val match = Regex("""bytes=(\d+)-(\d*)""").find(header)
            ?: return ByteRange(0L, null)
        val start = match.groupValues[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val end = match.groupValues[2].toLongOrNull()?.takeIf { it >= start }
        return ByteRange(start, end)
    }

    private fun isClientDisconnect(error: Throwable): Boolean =
        generateSequence<Throwable>(error) { it.cause }.any {
            it is java.net.SocketException ||
                it is java.io.IOException && it.message?.contains("reset", ignoreCase = true) == true
        }

    /** googlevideo includes the full object size in the `clen` query parameter. */
    private fun totalBytes(url: String): Long? = runCatching {
        URI(url).rawQuery
            ?.split('&')
            ?.firstOrNull { it.startsWith("clen=") }
            ?.substringAfter('=')
            ?.toLongOrNull()
    }.getOrNull()

    private fun copy(
        input: InputStream,
        output: java.io.OutputStream,
        limit: Long,
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var remaining = limit
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return limit - remaining
    }
}
