package example.nucleus.data.account

import com.metrolist.innertube.YouTube
import example.nucleus.network.AppHttpClient
import example.nucleus.platform.AppPaths
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.innertube.utils.sha1
import io.github.aakira.napier.Napier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AuthCredentials(
    val cookie: String,
    val visitorData: String = "",
    val dataSyncId: String = "",
    val accountName: String = "",
    val accountEmail: String = "",
    val accountAvatarUrl: String? = null
)

actual object AccountManager {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val cookieKey = stringPreferencesKey("yt_music_cookie")
    private lateinit var dataStore: DataStore<Preferences>

    private val _loginState = MutableStateFlow(false)
    actual val loginState: StateFlow<Boolean> = _loginState.asStateFlow()

    private val credentialsFile: File by lazy {
        AppPaths.ensureDirectories()
        val dir = File(AppPaths.roamingRoot)
        dir.mkdirs()
        File(dir, "credentials.json")
    }

    actual fun diagnose(cookie: String): List<String> {
        val warnings = mutableListOf<String>()
        val keys = cookie.split(";").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq == -1) null else part.substring(0, eq).trim()
        }.toSet()

        if ("SAPISID" !in keys) {
            warnings += "Falta SAPISID; la autenticacion fallara. Copia la cookie completa desde el header 'cookie' de una peticion a music.youtube.com."
        }
        if ("__Secure-3PAPISID" !in keys && "APISID" !in keys) {
            warnings += "Faltan __Secure-3PAPISID / APISID; la cookie puede estar incompleta."
        }
        if ("LOGIN_INFO" !in keys && "HSID" !in keys) {
            warnings += "Falta LOGIN_INFO / HSID; es posible que la sesion no este activa."
        }
        if (cookie.length < 200) {
            warnings += "La cookie parece demasiado corta (${cookie.length} chars). Asegurate de copiar todos los valores."
        }
        return warnings
    }

    actual fun init(dataStore: DataStore<Preferences>) {
        this.dataStore = dataStore
        // Intentar cargar desde credentials.json primero (datos más completos)
        val loaded = loadFromCredentialsFile()
        if (loaded != null) {
            applyCredentials(loaded)
            _loginState.value = true
            Napier.i("AccountManager: sesion restaurada desde credentials.json (${loaded.cookie.length} chars)")
            return
        }
        // Respaldo: cookie antigua de DataStore
        val saved = readFromLocalStorage()
        if (!saved.isNullOrBlank()) {
            applyToYouTube(saved)
            _loginState.value = true
            Napier.i("AccountManager: sesion restaurada desde DataStore (${saved.length} chars)")
            diagnose(saved).forEach { Napier.w("AccountManager [init]: $it") }
            // Migrar a credentials.json
            saveToCredentialsFile(AuthCredentials(cookie = saved))
        } else {
            Napier.i("AccountManager: no hay sesion guardada")
        }
    }

    actual fun setCookie(cookie: String) {
        val trimmed = cookie.trim()
        diagnose(trimmed).forEach { Napier.w("AccountManager [setCookie]: $it") }

        saveToLocalStorage(trimmed)
        saveToCredentialsFile(AuthCredentials(cookie = trimmed))
        applyToYouTube(trimmed)
        _loginState.value = true
        Napier.i("AccountManager: cookie guardada (${trimmed.length} chars)")
    }

    fun saveCredentials(cookie: String, visitorData: String?, dataSyncId: String?) {
        val trimmed = cookie.trim()
        saveToLocalStorage(trimmed)

        val existing = loadFromCredentialsFile()
        val creds = AuthCredentials(
            cookie = trimmed,
            visitorData = visitorData ?: existing?.visitorData ?: "",
            dataSyncId = dataSyncId ?: existing?.dataSyncId ?: "",
            accountName = existing?.accountName ?: "",
            accountEmail = existing?.accountEmail ?: "",
            accountAvatarUrl = existing?.accountAvatarUrl
        )
        saveToCredentialsFile(creds)
        applyCredentials(creds)
        _loginState.value = true
        Napier.i("AccountManager: credenciales guardadas (${trimmed.length} chars)")
    }

    actual fun clearCookie() {
        clearLocalStorage()
        if (credentialsFile.exists()) credentialsFile.delete()
        YouTube.cookie = null
        YouTube.visitorData = null
        YouTube.dataSyncId = null
        YouTube.useLoginForBrowse = false
        _loginState.value = false
        Napier.i("AccountManager: sesion cerrada")
    }

    actual fun getCookie(): String? {
        // Verificar credentials.json primero, luego DataStore
        loadFromCredentialsFile()?.let { return it.cookie }
        return readFromLocalStorage()?.takeIf { it.isNotBlank() }
    }

    actual val isLoggedIn: Boolean get() = getCookie() != null

    suspend fun fetchYtCfg(cookie: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val cookieMap = parseCookieString(cookie)
            val sapisid = cookieMap["SAPISID"] ?: return result
            val origin = "https://music.youtube.com"
            val currentTime = System.currentTimeMillis() / 1000
            val sapisidHash = sha1("$currentTime $sapisid $origin")

            val html = AppHttpClient.client.get(origin) {
                header("cookie", cookie)
                header("Authorization", "SAPISIDHASH ${currentTime}_${sapisidHash}")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
            }.bodyAsText()

            val setPattern = """ytcfg\.set\(\{(.*?)\}\)""".toRegex(RegexOption.DOT_MATCHES_ALL)
            for (match in setPattern.findAll(html)) {
                val block = match.groupValues[1]
                """"DATASYNC_ID"\s*:\s*"([^"]+)"""".toRegex().find(block)?.let {
                    result["DATASYNC_ID"] = it.groupValues[1]
                }
                """"SESSION_INDEX"\s*:\s*"?(\d+)"?""".toRegex().find(block)?.let {
                    result["SESSION_INDEX"] = it.groupValues[1]
                }
                """"LOGGED_IN"\s*:\s*(true|false)""".toRegex().find(block)?.let {
                    result["LOGGED_IN"] = it.groupValues[1]
                }
            }

            """"visitorData"\s*:\s*"([^"]+)"""".toRegex().find(html)?.let {
                result["VISITOR_DATA"] = it.groupValues[1]
            }
            """"accountName"\s*:\s*\{[^}]*"simpleText"\s*:\s*"([^"]+)"""".toRegex().find(html)?.let {
                result["ACCOUNT_NAME"] = it.groupValues[1]
            }
        } catch (e: Exception) {
            Napier.e("fetchYtCfg error: ${e.message}")
        }
        return result
    }

    // ─── Funciones auxiliares privadas ────────────────────────

    private fun applyCredentials(credentials: AuthCredentials) {
        YouTube.cookie = credentials.cookie
        credentials.visitorData.takeIf { it.isNotBlank() }?.let { YouTube.visitorData = it }
        credentials.dataSyncId.takeIf { it.isNotBlank() }?.let { raw ->
            YouTube.dataSyncId = raw.takeIf { !it.contains("||") }
                ?: raw.takeIf { it.endsWith("||") }?.substringBefore("||")
                ?: raw.substringAfter("||")
        }
        YouTube.useLoginForBrowse = true
    }

    private fun applyToYouTube(cookie: String) {
        YouTube.cookie = cookie
        YouTube.useLoginForBrowse = true
    }

    private fun saveToCredentialsFile(credentials: AuthCredentials) {
        try {
            credentialsFile.writeText(json.encodeToString(credentials))
        } catch (e: Exception) {
            Napier.e("AccountManager: no se pudo guardar credentials.json: ${e.message}")
        }
    }

    private fun loadFromCredentialsFile(): AuthCredentials? {
        return try {
            if (credentialsFile.exists()) {
                json.decodeFromString<AuthCredentials>(credentialsFile.readText())
            } else null
        } catch (e: Exception) {
            Napier.w("AccountManager: error al leer credentials.json: ${e.message}")
            null
        }
    }

    private fun saveToLocalStorage(cookie: String) {
        val store = getDataStoreOrNull() ?: return
        runBlocking(Dispatchers.IO) {
            try {
                store.edit { prefs -> prefs[cookieKey] = cookie }
            } catch (e: Exception) {
                Napier.e("AccountManager: no se pudo guardar la cookie en DataStore: ${e.message}")
            }
        }
    }

    private fun readFromLocalStorage(): String? {
        val store = getDataStoreOrNull() ?: return null
        return runBlocking(Dispatchers.IO) {
            try {
                store.data.first()[cookieKey]?.trim()?.ifBlank { null }
            } catch (e: Exception) {
                Napier.w("AccountManager: no se pudo leer la cookie de DataStore: ${e.message}")
                null
            }
        }
    }

    private fun clearLocalStorage() {
        val store = getDataStoreOrNull() ?: return
        runBlocking(Dispatchers.IO) {
            try {
                store.edit { prefs -> prefs.remove(cookieKey) }
            } catch (e: Exception) {
                Napier.e("AccountManager: no se pudo limpiar DataStore: ${e.message}")
            }
        }
    }

    private fun getDataStoreOrNull(): DataStore<Preferences>? {
        return if (this::dataStore.isInitialized) dataStore else {
            Napier.w("AccountManager: DataStore no inicializado; omitiendo persistencia de cookie")
            null
        }
    }
}
