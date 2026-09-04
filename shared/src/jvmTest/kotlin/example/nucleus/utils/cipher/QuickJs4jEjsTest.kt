package example.nucleus.utils.cipher

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class QuickJs4jEjsTest {
    @Test
    fun fullEjsPrepare() = runBlocking {
        val cacheDir = File(System.getenv("LOCALAPPDATA") ?: "", "Tarma/PaltaSound/cache/cipher_cache")
        val playerFile = File(cacheDir, "player_06ab6907.js")
        if (!playerFile.exists()) {
            println("player_06ab6907.js not found, skipping")
            return@runBlocking
        }
        val playerJs = playerFile.readText()
        println("playerJs length=${playerJs.length}")
        File(cacheDir, "pp_06ab6907.txt").delete()
        val t0 = System.currentTimeMillis()
        EjsCipherSolver.prepare(playerJs, "06ab6907")
        println("EJS prepare OK in ${System.currentTimeMillis() - t0}ms")
        val solved = EjsCipherSolver.solve("sig", "testSig123")
        println("solve sig => $solved")
        val solvedN = EjsCipherSolver.solve("n", "KdrqFlzJXl9EcCwlmEy")
        println("solve n => $solvedN")
        assertNotNull(solved, "sig solve returned null")
        println("shutdown OK, test DONE")
        System.out.flush()
    }

    @Test
    fun parsedScriptSolver() = runBlocking {
        val cacheDir = File(System.getenv("LOCALAPPDATA") ?: "", "Tarma/PaltaSound/cache/cipher_cache")
        val playerFile = File(cacheDir, "player_06ab6907.js")
        if (!playerFile.exists()) {
            println("no player file, skipping")
            return@runBlocking
        }
        val playerJs = playerFile.readText()
        val parse = PlayerScriptParser.parse(playerJs)
        println("PARSED: n=${parse.nFunctionCode != null} sig=${parse.sigFunctionCode != null} helpers=${parse.helperFunctions.size}")
        val solver = ParsedScriptSolver.create(playerJs)
        if (solver != null) {
            println("solver created: hasN=${solver.hasN()} hasSig=${solver.hasSig()}")
            if (solver.hasN()) println("solveN => ${solver.solveN("KdrqFlzJXl9EcCwlmEy")}")
            if (solver.hasSig()) println("solveSig => ${solver.solveSig("testSig123")}")
            solver.close()
        }
        println("DONE")
        System.out.flush()
    }

    @Test
    fun engineAvailableAndEval() = runBlocking {
        val mgr = javax.script.ScriptEngineManager()
        val names = mgr.engineFactories.joinToString { it.engineName }
        println("Engines: $names")
        val graal = mgr.getEngineByName("graal.js")
        if (graal != null) {
            val r = graal.eval("1+1").toString()
            println("graal 1+1 = $r")
            assertTrue(r == "2")
            val modern = graal.eval("(function(){ var f = (a,b) => a ?? b; return f(null,'x'); })()").toString()
            println("graal modern = $modern")
            assertTrue(modern == "x")
        } else {
            println("graal.js NOT available — usando quickjs-kt")
        }
    }
}