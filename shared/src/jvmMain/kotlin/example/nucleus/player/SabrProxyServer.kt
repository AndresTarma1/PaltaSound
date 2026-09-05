package example.nucleus.player

import com.metrolist.innertubex.sabr.ExperimentalSabrApi
import com.metrolist.innertubex.sabr.SabrAudioStream
import com.metrolist.innertubex.sabr.SabrBootstrap
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Estado de descarga SABR compartido entre el fetch en background ([SabrResolver]) y
 * los requests HTTP de mpv. Los bytes se acumulan contiguos; los lectores esperan
 * (con timeout) a que el rango pedido esté disponible.
 */
class SabrFetchState internal constructor(
    val mimeType: String,
    @Volatile var bootstrap: SabrBootstrap,
    val bitrateBps: Int,
    /** Reconstruye el bootstrap con atestación fresca (re-mint + player nuevo). */
    var refreshBootstrap: (suspend () -> SabrBootstrap?)? = null,
) {
    internal val mutex = Mutex()
    private val parts = mutableListOf<ByteArray>()

    @Volatile
    internal var size: Long = 0L

    @Volatile
    internal var done: Boolean = false

    @Volatile
    internal var error: Throwable? = null

    internal var job: Job? = null

    /** Agrega bytes contiguos al final; retorna la nueva posición final. */
    suspend fun append(data: ByteArray, offset: Int = 0): Long = mutex.withLock {
        if (error != null || done) return size
        if (offset < data.size) {
            parts.add(if (offset == 0) data else data.copyOfRange(offset, data.size))
            size += (data.size - offset)
        }
        size
    }

    suspend fun markDone() = mutex.withLock { done = true }

    suspend fun markError(e: Throwable) = mutex.withLock { if (!done) error = e }

    /** Lee [start, endExclusive) si está disponible; null si falta (el llamador espera/reintenta). */
    suspend fun slice(start: Long, endExclusive: Long): ByteArray? = mutex.withLock {
        if (endExclusive > size) return null
        var pos = 0L
        var remaining = (endExclusive - start).toInt()
        val out = ByteArray(remaining)
        var outPos = 0
        for (part in parts) {
            if (remaining <= 0) break
            val partEnd = pos + part.size
            if (partEnd <= start) {
                pos = partEnd
                continue
            }
            val from = (start - pos).toInt().coerceAtLeast(0)
            val take = minOf(part.size - from, remaining)
            part.copyInto(out, outPos, from, from + take)
            outPos += take
            remaining -= take
            pos = partEnd
        }
        if (remaining > 0) return null
        out
    }
}

/**
 * Proxy HTTP local (127.0.0.1) que sirve a mpv el audio SABR/UMP como stream
 * progresivo normal con soporte completo de `Range` (incluido seek).
 *
 * Por qué existe: las URLs progresivas con enforcement de Range (rqh=1/spc) rechazan
 * con 403 el `Range: bytes=0-` abierto de mpv, y en los videos más afectados el CDN
 * solo entrega el primer ~1MB. El flujo SABR en cambio entrega el audio completo en
 * segmentos fMP4; este proxy los acumula ([SabrFetchState], poblado en background por
 * [SabrResolver]) y los sirve con 200/206 exactos: mpv nunca ve un body corto.
 */
object SabrProxyServer {
    private const val MAX_SESSIONS = 8
    private const val READ_TIMEOUT_MS = 60_000L
    private const val READ_POLL_MS = 200L

    private val sessions = ConcurrentHashMap<String, SabrFetchState>()

    @Volatile
    private var server: HttpServer? = null

    @Volatile
    private var port: Int = 0

    /** Registra el estado (ya con fetch en marcha) y devuelve la URL local para mpv. */
    fun register(state: SabrFetchState): String {
        if (sessions.size >= MAX_SESSIONS) {
            sessions.entries.minByOrNull { it.value.bootstrap.durationMs }?.let {
                sessions.remove(it.key)?.job?.cancel()
            }
        }
        val id = UUID.randomUUID().toString().replace("-", "")
        sessions[id] = state
        ensureStarted()
        return "http://127.0.0.1:$port/sabr/$id"
    }

    fun unregister(id: String) {
        sessions.remove(id)?.job?.cancel()
    }

    private fun ensureStarted() {
        if (server != null) return
        synchronized(this) {
            if (server != null) return
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            s.createContext("/sabr", ::handle)
            s.executor = Executors.newCachedThreadPool { r ->
                Thread(r, "sabr-proxy").apply { isDaemon = true }
            }
            s.start()
            port = s.address.port
            server = s
            Napier.i("[SABR] proxy local en http://127.0.0.1:$port/sabr")
        }
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val id = exchange.requestURI.path.removePrefix("/sabr/").substringBefore("/")
            val state = sessions[id]
            if (state == null) {
                exchange.sendResponseHeaders(404, -1)
                return
            }
            val length = state.bootstrap.contentLengthBytes
            if (exchange.requestMethod == "HEAD") {
                sendHeaders(exchange, state, 0, length, 200, length ?: 0L)
                return
            }
            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            val range = parseRange(rangeHeader)
            val start = range?.first ?: 0L
            if (length != null && start >= length) {
                exchange.responseHeaders.set("Content-Range", "bytes */$length")
                exchange.sendResponseHeaders(416, -1)
                return
            }
            val endExclusive = when {
                length == null -> range?.second
                else -> minOf(range?.second ?: length, length)
            }

            runBlocking {
                serveFromBuffer(exchange, state, start, endExclusive, length, rangeHeader != null)
            }
        } catch (e: Exception) {
            Napier.w("[SABR] proxy error: ${e.message}")
            runCatching { exchange.close() }
        } finally {
            runCatching { exchange.close() }
        }
    }

    private suspend fun serveFromBuffer(
        exchange: HttpExchange,
        state: SabrFetchState,
        start: Long,
        endExclusive: Long?,
        length: Long?,
        isRangeRequest: Boolean,
    ) {
        // Objetivo: rango cerrado exacto, o todo lo restante si es abierto/sin longitud.
        // Se envían headers temprano con la longitud exacta (cuando se conoce) y los bytes
        // se escriben a medida que el fetch los acumula: inicio rápido + seeks exactos.
        val target = endExclusive ?: length
        if (target != null && target <= start) {
            exchange.sendResponseHeaders(416, -1)
            return
        }
        // Esperar los primeros bytes (o el fallo del fetch) antes de comprometer headers.
        val firstOk = withTimeout(READ_TIMEOUT_MS) {
            while (true) {
                if (state.size > start || state.done || state.error != null) return@withTimeout true
                delay(READ_POLL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        if (!firstOk || state.error != null && state.size <= start) {
            exchange.sendResponseHeaders(502, -1)
            return
        }
        val code = if (isRangeRequest && length != null) 206 else 200
        val declared = if (length != null && target != null) target - start else 0L
        sendHeaders(exchange, state, start, length, code, declared)

        try {
            exchange.responseBody.use { out ->
                var pos = start
                while (target == null || pos < target) {
                    val available = state.size
                    if (available <= pos) {
                        if (state.done || state.error != null) break
                        delay(READ_POLL_MS)
                        continue
                    }
                    val wantUntil = if (target != null) minOf(target, available) else available
                    val chunk = state.slice(pos, wantUntil) ?: run {
                        delay(READ_POLL_MS)
                        continue
                    }
                    out.write(chunk)
                    pos += chunk.size
                }
                out.flush()
            }
        } catch (_: Exception) {
            // Cliente desconectado (seek o stop de mpv).
        }
    }

    private fun sendHeaders(
        exchange: HttpExchange,
        state: SabrFetchState,
        start: Long,
        length: Long?,
        code: Int,
        responseLength: Long = 0L,
    ) {
        val h = exchange.responseHeaders
        h.set("Content-Type", state.mimeType.ifBlank { "audio/mp4" })
        h.set("Accept-Ranges", "bytes")
        if (code == 206 && length != null) {
            h.set("Content-Range", "bytes $start-${start + responseLength - 1}/$length")
            exchange.sendResponseHeaders(206, responseLength)
        } else if (length != null && responseLength > 0) {
            exchange.sendResponseHeaders(code, responseLength)
        } else {
            exchange.sendResponseHeaders(code, 0L)
        }
    }

    private fun parseRange(header: String?): Pair<Long, Long?>? {
        if (header == null) return null
        val m = Regex("bytes=(\\d+)-(\\d*)").find(header.trim()) ?: return null
        val start = m.groupValues[1].toLongOrNull() ?: return null
        val end = m.groupValues[2].takeIf { it.isNotEmpty() }?.toLongOrNull()
        return start to end?.let { it + 1 }
    }
}
