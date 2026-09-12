package example.nucleus.player

import example.nucleus.data.repository.AudioQuality
import example.nucleus.utils.cipher.PoTokenManager
import example.nucleus.utils.cipher.PoTokenGenerator
import example.nucleus.utils.cipher.PoTokenResult
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.response.PlayerResponse
import example.nucleus.utils.cipher.PlayerJsFetcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YTPlayerutils {
    /**
     * Resuelve los datos de reproducción y la URL del stream para un video con la calidad de audio especificada.
     *
     * @return Un `Result` que contiene los datos de reproducción resueltos y la URL del stream.
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality = AudioQuality.NORMAL,
    ): Result<PlaybackData> = runCatching {

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Napier.i { "Signature timestamp for $videoId: $signatureTimestamp" }

        val isLoggedIn = YouTube.cookie != null

        if (YouTube.visitorData == null) {
            YouTube.visitorData().onSuccess { vd ->
                YouTube.visitorData = vd
                Napier.i("visitorData obtained: ${vd.take(8)}...")
            }.onFailure {
                Napier.w("Failed to fetch visitorData: ${it.message}")
            }
        }

        // PoToken vía sidecar rustypipe-botguard (ver RustyPipeBotGuardSidecar): el runtime
        // de BotGuard corre en un proceso externo con entorno JSDOM (desde 2026 el minter
        // WebPO no se entrega a runtimes embebidos con shims stub).
        //  - playerRequestPoToken va en el body del /player del cliente principal.
        //  - streamingDataPoToken se agrega como pot= a las URLs de los clientes web,
        //    lo que re-habilita sus formatos de solo-audio de alta calidad.
        // Si visitorData aún no está listo o la generación falla/excede el timeout,
        // seguimos sin poToken: los clientes no-web + yt-dlp cubren el playback.
        var poTokenResult: PoTokenResult? = YouTube.visitorData?.let { vd ->
            PoTokenManager.getWebClientPoToken(videoId, vd)
        }

        val mainPlayerResponse =
            YouTube.player(
                videoId,
                playlistId,
                FallbackClients.mainClient,
                signatureTimestamp,
                poTokenResult?.playerRequestPoToken,
            ).getOrThrow()
        Napier.d("Resolving playback stream for $videoId with quality=$audioQuality signatureTimestamp=$signatureTimestamp")
        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        // Si un cliente web falla validate (403 del CDN con pot de snapshot envejecido),
        // se reintenta la cadena UNA vez con pot de streaming fresco (--no-snapshot).
        var refreshed = false
        var sawWeb403 = false

        while (true) {
            format = null
            streamUrl = null
            streamExpiresInSeconds = null
            streamPlayerResponse = null
            sawWeb403 = false

            for (clientIndex in (-1 until FallbackClients.streamFallbackClients.size)) {
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            val client = if (clientIndex == -1) {
                streamPlayerResponse = mainPlayerResponse
                null
            } else {
                val fallbackClient = FallbackClients.streamFallbackClients[clientIndex]
                if (fallbackClient.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    Napier.d("Skipping playback client ${fallbackClient.clientName} for $videoId because login is required")
                    continue
                }
                val clientPoToken =
                    if (fallbackClient.useWebPoTokens) poTokenResult?.playerRequestPoToken else null
                val result = YouTube.player(videoId, playlistId, fallbackClient, signatureTimestamp, clientPoToken)
                streamPlayerResponse = result.getOrNull()
                if (streamPlayerResponse == null) {
                    Napier.w("Player response null for ${fallbackClient.clientName} for $videoId: ${result.exceptionOrNull()?.message}")
                }
                fallbackClient
            }

            // Sin poToken, los clientes web (useWebPoTokens) dan error 403 en el CDN y cargarían
            // player.js para sig/n: se omite su resolución de stream. CON poToken válido pasan a
            // ser candidatos (formatos de solo-audio de alta calidad); WEB_REMIX sigue sirviendo
            // como fuente de metadatos arriba.
            if ((client ?: FallbackClients.mainClient).useWebPoTokens && poTokenResult == null) {
                continue
            }

            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                val newPipeResponse = withContext(Dispatchers.IO) {
                    YouTube.newPipePlayer(videoId, streamPlayerResponse)
                }
                val responseToUse = newPipeResponse ?: streamPlayerResponse
                var clientName = client?.clientName
                    ?: FallbackClients.mainClient.clientName
                Napier.d("Trying playback client $clientName for $videoId; newPipeResponse=${newPipeResponse != null}")

                format = FormatSelector.findFormat(responseToUse, audioQuality)
                if (format == null) {
                    Napier.w("No audio format found for $videoId using client $clientName")
                    continue
                }

                clientName = client?.clientName ?: FallbackClients.mainClient.clientName
                val cacheKey = "$videoId|${format.itag}|$clientName"
                streamUrl = StreamCache.get(cacheKey)

                if (streamUrl == null) {
                    streamUrl = withContext(Dispatchers.IO) {
                        StreamUrlResolver.resolveUrl(format, videoId, responseToUse)
                    }
                    if (streamUrl != null) {
                        val ttl = minOf(
                            streamPlayerResponse.streamingData?.expiresInSeconds?.times(1000L)
                                ?: 300_000L,
                            300_000L,
                        )
                        StreamCache.put(cacheKey, streamUrl, ttl)
                    }
                }

                if (streamUrl == null) {
                    Napier.w("No stream URL found for $videoId itag=${format.itag} using client $clientName")
                    continue
                }

                // Agregar pot=streamingDataPoToken para clientes con PoToken habilitado (WEB_REMIX, WEB, TVHTML5).
                val effectiveClient = client ?: FallbackClients.mainClient
                val streamingPot = poTokenResult?.streamingDataPoToken

                // Transformación de n solo para clientes web (igual que Metrolist). Los clientes no-web (VISIONOS,
                // IOS, ANDROID_VR, ...) obtienen URLs cuya n está ausente o ya es manejada por NewPipe;
                // transformarlas con la función n del reproductor web las rompería.
                val needsNTransform = effectiveClient.useWebPoTokens ||
                    effectiveClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")
                if (needsNTransform) {
                    streamUrl = StreamUrlResolver.applyNTransform(streamUrl)
                }
                if (effectiveClient.useWebPoTokens && streamingPot != null) {
                    val separator = if (streamUrl.contains('?')) "&" else "?"
                    // El token es base64url ([A-Za-z0-9_-]); solo el padding '=' requiere escape.
                    streamUrl = streamUrl + separator + "pot=" + streamingPot.replace("=", "%3D")
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Napier.w("Missing stream expiry for $videoId using client $clientName; defaulting to 6h")
                    streamExpiresInSeconds = 21600 // 6 hours default
                }

                if (clientIndex == FallbackClients.streamFallbackClients.size - 1) {
                    break
                }

                if (StreamUrlResolver.validate(streamUrl)) {
                    Napier.d("Resolved stream for $videoId using client $clientName itag=${format.itag}")
                    break
                } else {
                    Napier.w("Stream URL validation failed for $videoId using client $clientName itag=${format.itag}; trying fallback client")
                    // Un 403 aquí con pot de snapshot suele ser integrity token envejecido:
                    // el reintento de abajo lo reemplaza por un mint fresco.
                    if (effectiveClient.useWebPoTokens && streamingPot != null) sawWeb403 = true
                }
            } else if (streamPlayerResponse != null) {
                Napier.w(
                    "Playback client response not OK for $videoId: status=${streamPlayerResponse.playabilityStatus.status}, reason=${streamPlayerResponse.playabilityStatus.reason}",
                )
            }
        }

        val complete = streamPlayerResponse != null &&
            streamPlayerResponse.playabilityStatus.status == "OK" &&
            streamExpiresInSeconds != null && format != null && streamUrl != null
        if (complete) {
            return@runCatching PlaybackData(
                audioConfig,
                videoDetails,
                playbackTracking,
                format,
                streamUrl,
                streamExpiresInSeconds,
            )
        }

        if (streamPlayerResponse == null) {
            throw Exception("Bad stream player response")
        }
        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            throw Error(streamPlayerResponse.playabilityStatus.reason ?: "Unknown error")
        }
        if (streamExpiresInSeconds == null) throw Exception("Missing stream expire time")
        if (format == null) throw Exception("Could not find format")
        if (streamUrl == null) throw Exception("Could not find stream url")

        // La cadena se agotó sin URL válida. Si algún cliente web fue rechazado por el
        // CDN (403) con pot de snapshot, reintentar UNA vez con pot de streaming fresco
        // (--no-snapshot): el integrity token envejecido es la causa típica.
        if (sawWeb403 && !refreshed && poTokenResult != null) {
            refreshed = true
            val fresh = PoTokenGenerator.mintVideoFresh(videoId)
            if (fresh != null) {
                poTokenResult = poTokenResult.copy(streamingDataPoToken = fresh)
                Napier.i("[PoToken] reintento de $videoId con pot de streaming fresco")
                continue
            }
        }

        error("unreachable: complete o retry debieron resolver arriba")
    }
    throw AssertionError("unreachable: el loop de clientes siempre retorna o lanza")
}

    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        return YouTube.player(videoId, playlistId, client = FallbackClients.mainClient)
    }

    /**
     * `sts` real del player.js de YouTube asociado al [videoId], extraído del mismo base.js
     * que usa el solucionador EJS para sig/n ([PlayerJsFetcher]). Es el canal que usa Metrolist
     * (leer `signatureTimestamp` del player.js), funciona sin sesión y es estable frente a
     * cambios de layout. Si no se puede obtener, devuelve null y [InnerTube] omite `playbackContext`
     * (mejor que inventar un valor tipo "días desde epoch").
     */
    private suspend fun getSignatureTimestampOrNull(videoId: String): Int? =
        PlayerJsFetcher.getSignatureTimestamp()

    /**
     * Invalida las URLs de stream en caché para un video.
     *
     * @param videoId El ID del video.
     */
    suspend fun forceRefreshForVideo(videoId: String) {
        StreamCache.invalidateForVideo(videoId)
    }
}
