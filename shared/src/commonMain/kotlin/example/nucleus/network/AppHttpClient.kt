package example.nucleus.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cliente HTTP compartido por las APIs auxiliares: letras (LRCLIB, BetterLyrics), validación de
 * URLs de stream, `player.js` y `ytcfg`.
 *
 * Antes cada integración creaba su propio `HttpClient(CIO)`. Cada instancia levanta su propio
 * `SelectorManager` (un hilo dedicado con su selector) más su dispatcher de workers, así que
 * cuatro clientes suponían cuatro hilos selectores y una docena de workers de más para el mismo
 * tipo de tráfico (GET de JSON/texto contra hosts distintos).
 *
 * No se usa para InnerTube ni para SABR: esos van por OkHttp y necesitan `expectSuccess = true`,
 * políticas de conexión propias y el [com.metrolist.innertube.OfflineGatePlugin].
 */
object AppHttpClient {

    val client: HttpClient by lazy {
        HttpClient(CIO) {
            // Cada llamada decide qué hacer con 4xx/5xx: BetterLyrics inspecciona el status,
            // AccountManager parsea HTML y la validación de stream solo acepta 200/206.
            expectSuccess = false

            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }

            // Defaults razonables para todas las integraciones; quien necesite otra cosa lo
            // sobreescribe por petición con `timeout { ... }`.
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
    }
}
