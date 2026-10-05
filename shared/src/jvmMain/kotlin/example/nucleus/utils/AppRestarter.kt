package example.nucleus.utils

import example.nucleus.data.repository.JvmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

object AppRestarter {

    private const val appliedMarkerArg = "-Dmusicplayer.jvmConfigApplied=true"

    val requiredJvmArgs = listOf(
        "--add-modules=java.sql",
        "--enable-native-access=ALL-UNNAMED",
        "-Dorg.sqlite.tmpdir=${System.getProperty("user.home")}/.musicplayer/tmp",
        "-XX:+UseCompressedOops",
        // Flags transversales (funcionan con SerialGC y G1). El GC concreto lo añade
        // config.toJvmArgs() (SerialGC por defecto, G1/ZGC como opción avanzada).
        "-XX:MinHeapFreeRatio=10",
        "-XX:MaxHeapFreeRatio=30",
        "-XX:MaxMetaspaceSize=192m",
        "-XX:CompressedClassSpaceSize=96m",
        "-XX:ReservedCodeCacheSize=128m",
        "-XX:CICompilerCount=2",
        "-XX:ActiveProcessorCount=4",
        "-Xss768k",
        "-Dkotlinx.coroutines.io.parallelism=16",
        "-Dskiko.gpu.resourceCacheLimit=128M",
        "-Dskiko.buffering=DOUBLE",
        "-Dskiko.vsync.enabled=true",
    )

    // Los flags específicos de GC (MaxGCPauseMillis, G1PeriodicGCInterval, UseStringDeduplication)
    // los añade config.toJvmArgs() según el GC elegido (G1). SerialGC no los usa.
    val gcTuningArgs = emptyList<String>()

    fun previewJvmArgs(config: JvmConfig): List<String> =
        requiredJvmArgs + gcTuningArgs + config.toJvmArgs() + appliedMarkerArg

    suspend fun restartWithJvmArgs(config: JvmConfig) {
        withContext(Dispatchers.IO) {
            try {
                // En una compilación instalada (jpackage), `jpackage.app-path` es el ejecutable
                // nativo del lanzador. Relanzar ESTE relee el .cfg de la aplicación (runtime
                // empaquetado, directorios de recursos, opciones de módulo) y, al iniciar,
                // JvmConfigLauncher.applySync() re-aplica el render guardado desde disco — así que
                // no es necesario pasar argumentos JVM por aquí. La ruta anterior
                // `java -cp <classpath> MainKt` descartaba todas las opciones de lanzamiento de
                // jpackage y el JVM nuevo moría de inmediato, lo que parecía "la app simplemente
                // se cierra y nunca vuelve a abrir". Solo se recurre al relanzamiento con java
                // cuando se ejecuta sin empaquetar (dev/gradle).
                val appPath = System.getProperty("jpackage.app-path")
                val command: List<String> = if (!appPath.isNullOrBlank()) {
                    listOf(appPath)
                } else {
                    val javaHome = System.getProperty("java.home")
                    val javaBin = if (isWindows()) "$javaHome\\bin\\java.exe" else "$javaHome/bin/java"
                    val classpath = System.getProperty("java.class.path")
                    val mainClass = System.getProperty("sun.java.command")?.split(" ")?.firstOrNull()
                        ?: "example.nucleus.MainKt"
                    listOf(javaBin) + previewJvmArgs(config) + listOf("-cp", classpath, mainClass)
                }

                ProcessBuilder(command)
                    .directory(java.io.File(System.getProperty("user.dir")))
                    .start()

                exitProcess(0)
            } catch (e: Exception) {
                e.printStackTrace()
                exitProcess(0)
            }
        }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }
}
