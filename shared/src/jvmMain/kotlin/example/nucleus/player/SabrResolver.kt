@file:OptIn(com.metrolist.innertubex.sabr.ExperimentalSabrApi::class)
package example.nucleus.player

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.models.YouTubeClient as XClient
import com.metrolist.innertubex.models.response.PlayerResponse as XPlayerResponse
import com.metrolist.innertubex.sabr.SabrAudioStream
import com.metrolist.innertubex.sabr.SabrBootstrap
import com.metrolist.innertubex.sabr.SabrFailureKind
import com.metrolist.innertubex.sabr.SabrProtocolException
import com.metrolist.innertubex.sabr.toSabrBootstrap
import example.nucleus.data.repository.AudioQuality
import example.nucleus.utils.cipher.PlayerJsFetcher
import example.nucleus.utils.cipher.RustyPipeBotGuardSidecar
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json

/**
 * Resolución de audio vía SABR/UMP (innertubex) servida a mpv por [SabrProxyServer].
 *
 * Cuándo se usa: las URLs progresivas con enforcement de Range (rqh=1/spc) rechazan el
 * `Range: bytes=0-` abierto de mpv (403) y en los videos más afectados el CDN solo
 * entrega el primer ~1MB. El flujo SABR en cambio entrega el audio completo en
 * segmentos fMP4: más rápido que yt-dlp (todo en-proceso) y sin sus 7s de arranque.
 *
 * Protocolo (igual que el probe `SabrProbeTest`):
 *  1. Player WEB_REMIX de innertubex (con sts + pot de sesión).
 *  2. Bootstrap desde `serverAbrStreamingUrl` (+n-transform y `cpn=`) y
 *     `videoPlaybackUstreamerConfig`, con el pot ligado al video.
 *  3. Verificación: abrir el flujo y leer 2 chunks (con timeout).
 *  4. Los mints van DIRECTO con reto fresco (`--no-snapshot`, en paralelo): el
 *     flujo SABR exige integrity token nuevo y el de snapshot siempre pide
 *     refresh de atestación (ahorra una ronda completa de ~10s).
 *  5. Si el UMP exige atestación de nuevo: 1 retry con mints renovados.
 *  6. Registro en [SabrProxyServer] -> URL local `http://127.0.0.1:port/sabr/<id>`.
 */
object SabrResolver {
    private const val RESOLVE_TIMEOUT_MS = 60_000L
    private const val VERIFY_TIMEOUT_MS = 30_000L
    private const val VERIFY_CHUNKS = 2

    private val jsonLenient = Json { ignoreUnknownKeys = true }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }
    }

    private val innerTube by lazy { InnerTube(httpClient) }

    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Resuelve [videoId] a una URL local servida por [SabrProxyServer], o null si SABR
     * no está disponible para el video.
     */
    suspend fun resolveLocalUrl(videoId: String, quality: AudioQuality): String? {
        return try {
            withTimeout(RESOLVE_TIMEOUT_MS) { resolveInternal(videoId, quality) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("[SABR] resolve falló para $videoId: ${e.message}")
            null
        }
    }

    private suspend fun resolveInternal(videoId: String, quality: AudioQuality): String? {
        val sts = PlayerJsFetcher.getSignatureTimestamp()
        val vd = innerTube.fetchFreshVisitorData()
            ?: return null.also { Napier.w("[SABR] sin visitorData para $videoId") }
        innerTube.visitorData = vd

        // Directo a mints frescos en paralelo: el flujo SABR exige integrity token
        // nuevo y el de snapshot siempre pide refresh (ahorra una ronda completa).
        val built = freshBootstrap(videoId, sts, vd, quality) ?: return null
        try {
            if (verifyOpens(built.first)) {
                return startSession(videoId, vd, sts, quality, built.first, built.second)
            }
            return null
        } catch (e: SabrProtocolException) {
            if (e.kind != SabrFailureKind.ATTESTATION_REQUIRED && e.kind != SabrFailureKind.RELOAD_PLAYER) {
                Napier.w("[SABR] verify protocolo no recuperable para $videoId: ${e.message}")
                return null
            }
            Napier.i("[SABR] refresh de atestación para $videoId (${e.kind})")
        }
        val fresh = buildFreshBootstrap(videoId, sts, vd, quality) ?: return null
        if (!verifyOpens(fresh.first)) return null
        return startSession(videoId, vd, sts, quality, fresh.first, fresh.second)
    }

    /**
     * Crea el estado de fetch, lanza su descarga en background y registra la sesión
     * en el proxy. El refresh de atestación lo resuelve el propio fetch ante fallos.
     */
    private fun startSession(
        videoId: String,
        visitorData: String,
        sts: Int?,
        quality: AudioQuality,
        bootstrap: SabrBootstrap,
        bitrateBps: Int,
    ): String {
        val state = SabrFetchState(
            mimeType = bootstrap.mimeType,
            bootstrap = bootstrap,
            bitrateBps = bitrateBps,
        )
        state.refreshBootstrap = { buildFreshBootstrap(videoId, sts, visitorData, quality)?.first }
        state.job = fetchScope.launch { fetchLoop(state) }
        val url = SabrProxyServer.register(state)
        Napier.i("[SABR] sesión lista para $videoId dur=${bootstrap.durationMs}ms len=${bootstrap.contentLengthBytes}")
        return url
    }

    /** Descarga el flujo completo al buffer con resume ante ATTESTATION_REQUIRED. */
    private suspend fun fetchLoop(state: SabrFetchState) {
        var bootstrap = state.bootstrap
        var refreshes = 0
        while (true) {
            try {
                // Reanudar cerca de lo ya descargado (con margen) para no re-bajar todo.
                val startMs = posToMs(state, state.size)
                SabrAudioStream(httpClient, bootstrap, initialPlayerTimeMs = startMs).chunks().collect { c ->
                    val cur = state.size
                    if (c.endRangeExclusive <= cur) return@collect
                    val off = (cur - c.startRange).toInt().coerceAtLeast(0)
                    if (off < c.data.size) state.append(c.data, off)
                }
                state.markDone()
                Napier.i("[SABR] fetch completo: ${state.size} bytes")
                return
            } catch (e: CancellationException) {
                state.markError(e)
                throw e
            } catch (e: SabrProtocolException) {
                if (e.kind == SabrFailureKind.ATTESTATION_REQUIRED && refreshes < 2) {
                    refreshes++
                    Napier.i("[SABR] fetch refresh de atestación ($refreshes/2)")
                    val fresh = state.refreshBootstrap?.invoke()
                    if (fresh != null) {
                        bootstrap = fresh
                        state.bootstrap = fresh
                        continue
                    }
                }
                state.markError(e)
                Napier.w("[SABR] fetch falló: ${e.message}")
                return
            } catch (e: Exception) {
                state.markError(e)
                Napier.w("[SABR] fetch falló: ${e.message}")
                return
            }
        }
    }

    /**
     * Mints frescos en paralelo (`--no-snapshot`) + player + bootstrap.
     * Es el camino principal: el flujo SABR exige integrity token nuevo.
     */
    private suspend fun freshBootstrap(
        videoId: String,
        sts: Int?,
        visitorData: String,
        quality: AudioQuality,
    ): Pair<SabrBootstrap, Int>? {
        val pots = freshPots(videoId, visitorData) ?: return null
        return buildBootstrap(videoId, sts, visitorData, pots.first, pots.second, quality)
    }

    /** Alias del retry (misma ruta fresca). */
    private suspend fun buildFreshBootstrap(
        videoId: String,
        sts: Int?,
        visitorData: String,
        quality: AudioQuality,
    ): Pair<SabrBootstrap, Int>? = freshBootstrap(videoId, sts, visitorData, quality)

    /** Dos mints frescos en paralelo; retorna (playerPot standard, videoPot base64url). */
    private suspend fun freshPots(videoId: String, visitorData: String): Pair<String, String>? =
        coroutineScope {
            val sessionDef = async { RustyPipeBotGuardSidecar.mint(visitorData, fresh = true) }
            val videoDef = async { RustyPipeBotGuardSidecar.mint(videoId, fresh = true) }
            val session = sessionDef.await()
            val video = videoDef.await()
            if (session == null || video == null) {
                Napier.w("[SABR] mint fresco falló para $videoId")
                null
            } else {
                base64UrlToStandard(session) to video
            }
        }

    private suspend fun buildBootstrap(
        videoId: String,
        sts: Int?,
        visitorData: String,
        playerPot: String,
        videoPot: String,
        quality: AudioQuality,
    ): Pair<SabrBootstrap, Int>? {
        return try {
            val parsed = fetchPlayer(videoId, sts, visitorData, playerPot) ?: return null
            val streaming = parsed.streamingData ?: return null.also {
                Napier.w("[SABR] $videoId sin streamingData")
            }
            val sabrUrl = streaming.serverAbrStreamingUrl ?: return null.also {
                Napier.w("[SABR] $videoId sin serverAbrStreamingUrl")
            }
            val audio = chooseAudio(parsed, streaming, quality) ?: return null.also {
                Napier.w("[SABR] $videoId sin formato de audio SABR")
            }
            val transformed = StreamUrlResolver.applyNTransform(sabrUrl) +
                "&cpn=" + generateClientPlaybackNonce()
            val bootstrap = parsed.toSabrBootstrap(
                clientId = XClient.WEB_REMIX.clientId.toInt(),
                clientVersion = XClient.WEB_REMIX.clientVersion,
                audioFormat = audio,
                poToken = videoPot,
                requestUserAgent = XClient.WEB_REMIX.userAgent,
                requestOrigin = "https://music.youtube.com",
                serverAbrStreamingUrlOverride = transformed,
            )
            bootstrap to audio.bitrate
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("[SABR] bootstrap falló para $videoId: ${e.message}")
            null
        }
    }

    private suspend fun fetchPlayer(
        videoId: String,
        sts: Int?,
        visitorData: String,
        playerPot: String,
    ): XPlayerResponse? {
        return try {
            val response = innerTube.player(
                client = XClient.WEB_REMIX,
                videoId = videoId,
                signatureTimestamp = sts,
                poToken = playerPot,
                requestVisitorData = visitorData,
            )
            val parsed = jsonLenient.decodeFromString<XPlayerResponse>(response.bodyAsText())
            if (parsed.playabilityStatus?.status != "OK") {
                Napier.w("[SABR] $videoId player status=${parsed.playabilityStatus?.status}")
                return null
            }
            parsed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("[SABR] player falló para $videoId: ${e.message}")
            null
        }
    }

    private fun chooseAudio(
        parsed: XPlayerResponse,
        streaming: XPlayerResponse.StreamingData,
        quality: AudioQuality,
    ): XPlayerResponse.StreamingData.Format? {
        val audios = streaming.adaptiveFormats
            .filter { it.mimeType.startsWith("audio/") }
            .sortedBy { it.bitrate }
        if (audios.isEmpty()) return null
        return when (quality) {
            AudioQuality.LOW -> audios.first()
            AudioQuality.HIGH -> audios.last()
            else -> audios.firstOrNull { it.itag == 140 } ?: audios.last()
        }.also {
            Napier.i("[SABR] formato elegido itag=${it.itag} bitrate=${it.bitrate} len=${it.contentLength}")
        }
    }

    private suspend fun verifyOpens(bootstrap: SabrBootstrap): Boolean {
        return try {
            withTimeout(VERIFY_TIMEOUT_MS) {
                val chunks = SabrAudioStream(httpClient, bootstrap).chunks().take(VERIFY_CHUNKS).toList()
                chunks.isNotEmpty().also {
                    Napier.i("[SABR] verify opens=${it} bytes=${chunks.sumOf { c -> c.data.size }}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SabrProtocolException) {
            // ATTESTATION_REQUIRED / RELOAD se manejan arriba con refresh; el resto es fallo.
            if (e.kind == SabrFailureKind.ATTESTATION_REQUIRED || e.kind == SabrFailureKind.RELOAD_PLAYER) throw e
            Napier.w("[SABR] verify protocolo: ${e.message}")
            false
        } catch (e: Exception) {
            Napier.w("[SABR] verify falló: ${e.message}")
            false
        }
    }

    private fun posToMs(state: SabrFetchState, pos: Long): Long {
        val bootstrap = state.bootstrap
        val bytesPerMs = when {
            bootstrap.contentLengthBytes != null && bootstrap.durationMs > 0 ->
                bootstrap.contentLengthBytes!!.toDouble() / bootstrap.durationMs.toDouble()
            state.bitrateBps > 0 -> state.bitrateBps.toDouble() / 8000.0
            else -> return 0L
        }
        // Margen hacia atrás: mejor solapar (se salta por startRange) que dejar hueco.
        return ((pos / bytesPerMs) - 5_000L).toLong().coerceAtLeast(0L)
    }

    private fun base64UrlToStandard(urlSafe: String): String {
        val s = urlSafe.replace('-', '+').replace('_', '/')
        return when (s.length % 4) {
            0 -> s
            2 -> "$s=="
            3 -> "$s="
            else -> s
        }
    }
}
