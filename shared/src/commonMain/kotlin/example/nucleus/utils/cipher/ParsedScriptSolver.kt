package example.nucleus.utils.cipher

import com.dokar.quickjs.QuickJs
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Solver ligero estilo InnerTubeX (ParsedScript): extrae las funciones `n`/`sig` del player.js
 * por heurística (split/join + helpers), genera un script chico y lo ejecuta en QuickJS
 * (quickjs-kt). Evita el solver EJS pesado (que en QuickJS agota la memoria al ejecutar el
 * cipher `n` del player actual).
 */
class ParsedScriptSolver private constructor(
    private val quickJs: QuickJs,
    private val parseResult: PlayerScriptParser.ParseResult,
) {
    private val mutex = Mutex()

    fun hasN(): Boolean = parseResult.nFunctionCode != null && !parseResult.nInvoker.isNullOrBlank()
    fun hasSig(): Boolean = parseResult.sigFunctionCode != null && !parseResult.sigInvoker.isNullOrBlank()

    suspend fun solveN(input: String): String? = call("_solveN", input)
    suspend fun solveSig(input: String): String? = call("_solveSig", input)

    private suspend fun call(functionName: String, input: String): String? = mutex.withLock {
        withContext(Dispatchers.Default) {
            try {
                quickJs.evaluate<String?>(
                    """
                    (function() {
                        if (typeof $functionName !== 'function') return null;
                        const value = $functionName(${jsStringLiteral(input)});
                        if (value == null) return null;
                        const text = String(value);
                        return text.length <= 262144 ? text : null;
                    })()
                    """.trimIndent(),
                )
            } catch (e: Exception) {
                Napier.w("[ParsedScript] $functionName failed: ${e.message}")
                null
            }
        }
    }

    fun close() {
        quickJs.close()
    }

    companion object {
        /** Literal string JS válido. */
        internal fun jsStringLiteral(s: String): String =
            buildString(s.length + 2) {
                append('"')
                for (c in s) {
                    when (c) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> {
                            if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
                        }
                    }
                }
                append('"')
            }

        /**
         * Crea el solver si el player tiene funciones extraíbles.
         *
         * @return null si no se pudo extraer ninguna función (player VM/table-driven).
         */
        suspend fun create(playerJs: String): ParsedScriptSolver? {
            val parseResult = PlayerScriptParser.parse(playerJs)
            if (parseResult.nFunctionCode == null && parseResult.sigFunctionCode == null) {
                Napier.i("[ParsedScript] No n/sig functions extracted; falling back to EJS")
                return null
            }
            Napier.i(
                "[ParsedScript] Extracted n=${parseResult.nFunctionCode != null} sig=${parseResult.sigFunctionCode != null} helpers=${parseResult.helperFunctions.size}",
            )
            val script = PlayerScriptParser.generateSolverScript(parseResult)
            // Se captura Throwable y no Exception a proposito: sin el runtime QuickJS en el
            // classpath (variante GraalVM nativa, -PjsEngines=false), `QuickJs.create()`
            // lanza NoClassDefFoundError/UnsatisfiedLinkError, que son Error, no Exception.
            // Devolver null aqui es degradar al EJS; no debe reventar el pipeline.
            val quickJs = try {
                QuickJs.create(Dispatchers.Default).also {
                    it.memoryLimit = 512L * 1024 * 1024
                    it.evaluationTimeoutMillis = 10_000L
                }
            } catch (t: Throwable) {
                Napier.w("[ParsedScript] QuickJS no disponible (${t::class.simpleName}): sin solver ligero")
                return null
            }
            try {
                withContext(Dispatchers.Default) { quickJs.evaluate<Any?>("$script\n;undefined;") }
                return ParsedScriptSolver(quickJs, parseResult)
            } catch (e: Exception) {
                Napier.e("[ParsedScript] Solver script eval failed: ${e.message}")
                quickJs.close()
                return null
            }
        }
    }
}