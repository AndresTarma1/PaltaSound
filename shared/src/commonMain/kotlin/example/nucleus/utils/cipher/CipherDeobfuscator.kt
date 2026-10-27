package example.nucleus.utils.cipher

import io.github.aakira.napier.Napier
import java.net.URLEncoder

object CipherDeobfuscator {
    private const val TAG = "CipherDeobfuscator"

    private var currentPlayerHash: String? = null
    private var preparedHash: String? = null

    private var nativeCipher: NativePlayerCipher? = null
    private var nativeReady: Boolean = false

    private fun cipherMode(): String =
        (System.getProperty("melodist.cipher") ?: "native").trim().lowercase()

    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? {
        Napier.i("[CIPHER] deobfuscateStreamUrl videoId=$videoId")
        return try {
            deobfuscateInternal(signatureCipher, videoId, isRetry = false)
        } catch (e: Exception) {
            Napier.e(e) { "[CIPHER] Failed, retrying with fresh JS: ${e.message}" }
            try {
                PlayerJsFetcher.invalidateCache()
                resetPrepared()
                deobfuscateInternal(signatureCipher, videoId, isRetry = true)
            } catch (retryE: Exception) {
                Napier.e(retryE) { "[CIPHER] Retry also failed: ${retryE.message}" }
                null
            }
        }
    }

    private suspend fun deobfuscateInternal(signatureCipher: String, videoId: String, isRetry: Boolean): String? {
        val params = parseQueryParams(signatureCipher)
        val obfuscatedSig = params["s"] ?: return null
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"] ?: return null

        Napier.i("[CIPHER] Parsed: sigLen=${obfuscatedSig.length}, url=${baseUrl.take(60)}...")

        if (!ensurePrepared(forceRefresh = isRetry)) return null

        val deobfuscatedSig = solveSig(obfuscatedSig) ?: run {
            Napier.e("[CIPHER] sig solve returned null")
            return null
        }
        val separator = if ("?" in baseUrl) "&" else "?"
        return "$baseUrl${separator}$sigParam=${URLEncoder.encode(deobfuscatedSig, "UTF-8")}"
    }

    suspend fun transformNParamInUrl(url: String): String? {
        val nMatch = Regex("[?&]n=([^&]+)").find(url) ?: run {
            Napier.d("[NTRACE] No n param found, skipping")
            return url
        }
        return try {
            val nValue = java.net.URLDecoder.decode(nMatch.groupValues[1], "UTF-8")

            if (!ensurePrepared(forceRefresh = false)) {
                Napier.e("[NTRACE] player not prepared, fail-closed")
                return null
            }

            val transformedN = solveN(nValue) ?: run {
                Napier.e("[NTRACE] n solve returned null, fail-closed")
                return null
            }

            val result = url.replaceFirst(
                Regex("([?&])n=[^&]+"),
                "$1n=${URLEncoder.encode(transformedN, "UTF-8")}"
            )
            Napier.i("[NTRACE] n-transform SUCCESS")
            result
        } catch (e: Exception) {
            Napier.e("[NTRACE] N-transform exception: ${e::class.simpleName}: ${e.message}")
            null
        }
    }

    suspend fun warmup(): Boolean = ensurePrepared(forceRefresh = false)

    private fun resetPrepared() {
        nativeCipher?.close()
        nativeCipher = null
        nativeReady = false
        preparedHash = null
        currentPlayerHash = null
    }

    private suspend fun ensurePrepared(forceRefresh: Boolean): Boolean {
        if (!forceRefresh && preparedHash != null && nativeReady) return true

        Napier.i("[CIPHER] Fetching player.js (forceRefresh=$forceRefresh)...")
        val result = PlayerJsFetcher.getPlayerJs(forceRefresh = forceRefresh)
        if (result == null) {
            Napier.e("[CIPHER] PlayerJsFetcher.getPlayerJs() returned null")
            return false
        }
        val (playerJs, hash) = result

        if (forceRefresh) resetPrepared()

        var success = false
        try {
            val nc = NativePlayerCipher.create(playerJs, hash)
            nativeCipher?.close()
            nativeCipher = nc
            nativeReady = true
            success = true
            Napier.i("[CIPHER] Native solver ready for player hash=$hash")
        } catch (e: Exception) {
            Napier.e(e) { "[CIPHER] Native solver failed: ${e.message}" }
            nativeReady = false
            nativeCipher = null
        }

        if (success) {
            preparedHash = hash
            currentPlayerHash = hash
        }
        return success
    }

    private suspend fun solveSig(input: String): String? {
        val nc = nativeCipher
        if (nativeReady && nc != null) {
            return nc.solveSig(input)
        }
        return null
    }

    private suspend fun solveN(input: String): String? {
        val nc = nativeCipher
        if (nativeReady && nc != null) {
            return nc.solveN(input)
        }
        return null
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result[key] = value
            }
        }
        return result
    }

    fun getDebugInfo(): Map<String, Any?> {
        return mapOf(
            "playerHash" to currentPlayerHash,
            "mode" to cipherMode(),
            "nativeReady" to nativeReady,
        )
    }
}
