package example.nucleus.utils.cipher

import com.dokar.quickjs.QuickJs
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Solver nativo "hint-free" usando QuickJS (sin GraalJS).
 *
 * Evalúa el `player.js` crudo con stubs de DOM/red, inyecta un export antes del cierre
 * `})(_yt_player);` y llama a las funciones nativas de descifrado de firma y transformación `n`.
 */
class NativePlayerCipher private constructor(
    val playerJs: String,
    val hash: String,
    private val analysis: NativePlayerScriptAnalyzer.Analysis,
) {
    private val quickJs: QuickJs = QuickJs.create(Dispatchers.Default)
    private var closed = false

    suspend fun prepare() {
        withContext(Dispatchers.Default) {
            // Stubs mínimos.
            quickJs.evaluate<Any?>(
                """
                var window=this; var self=this; var globalThis=this;
                function __stub() {
                  var f = function(){};
                  return new Proxy(f, {
                    get: function(t, p) { if (p === Symbol.toPrimitive) return function(){ return ""; }; if (p === "then") return undefined; return __stub(); },
                    apply: function() { return __stub(); },
                    construct: function() { return __stub(); }
                  });
                }
                var document=__stub(); var navigator=__stub(); var location=__stub();
                var XMLHttpRequest=__stub(); var fetch=__stub(); var Headers=__stub();
                var Request=__stub(); var Response=__stub(); var AbortController=__stub();
                var setTimeout=function(f){ return 0; }; var clearTimeout=function(){};
                """.trimIndent(),
            )

            val marker = "})(_yt_player);"
            val idx = playerJs.lastIndexOf(marker)
            if (idx < 0) throw CipherException("Native cipher: export marker not found in player.js")

            val exportBlock = """
                _yt_player.__melodist = {
                  sig: function(input) { return ${analysis.sigExpression}; },
                  n: function(token) {
                    try {
                      var url = "https://www.youtube.com/watch?v=dummy&n=" + encodeURIComponent(String(token));
                      return (new g.${analysis.nClassName}(url, true)).get("n");
                    } catch (e) { return "ERR:" + ((e && e.message) ? e.message : e); }
                  }
                };
            """.trimIndent()

            val modified = playerJs.substring(0, idx) + exportBlock + playerJs.substring(idx)
            quickJs.evaluate<Any?>("$modified\n;undefined;")
        }
    }

    suspend fun solveSig(input: String): String? {
        if (closed) return null
        return try {
            withContext(Dispatchers.Default) {
                quickJs.evaluate<String?>(
                    """
                    (function() {
                      try {
                        var v = _yt_player.__melodist.sig(${ParsedScriptSolver.jsStringLiteral(input)});
                        if (v == null) return null;
                        var t = String(v);
                        return t.startsWith("ERR:") ? null : t;
                      } catch (e) { return null; }
                    })()
                    """.trimIndent(),
                )
            }
        } catch (e: Exception) {
            Napier.e(e) { "[NativeCipher] sig solve failed: ${e.message}" }
            null
        }
    }

    suspend fun solveN(token: String): String? {
        if (closed) return null
        return try {
            withContext(Dispatchers.Default) {
                quickJs.evaluate<String?>(
                    """
                    (function() {
                      try {
                        var v = _yt_player.__melodist.n(${ParsedScriptSolver.jsStringLiteral(token)});
                        if (v == null) return null;
                        var t = String(v);
                        return (t === "null" || t.startsWith("ERR:")) ? null : t;
                      } catch (e) { return null; }
                    })()
                    """.trimIndent(),
                )
            }
        } catch (e: Exception) {
            Napier.e(e) { "[NativeCipher] n solve failed: ${e.message}" }
            null
        }
    }

    fun close() {
        closed = true
        runCatching { quickJs.close() }
    }

    suspend fun validate() {
        val sig = solveSig("testSig123")
        if (sig.isNullOrBlank()) {
            throw CipherException("Native cipher probe failed: sig returned null/blank")
        }
        val n = solveN("KdrqFlzJXl9EcCwlmEy")
        if (n.isNullOrBlank() || n.startsWith("ERR:")) {
            throw CipherException("Native cipher probe failed: n returned null/blank/error")
        }
        Napier.i("[NativeCipher] probe OK: sig=$sig n=$n")
    }

    companion object {
        suspend fun create(playerJs: String, hash: String): NativePlayerCipher {
            val analysis = NativePlayerScriptAnalyzer.analyze(playerJs)
            Napier.i("[NativeCipher] analyzed: nClass=${analysis.nClassName} sig=${analysis.sigExpression}")
            val cipher = NativePlayerCipher(playerJs, hash, analysis)
            cipher.prepare()
            cipher.validate()
            return cipher
        }
    }
}
