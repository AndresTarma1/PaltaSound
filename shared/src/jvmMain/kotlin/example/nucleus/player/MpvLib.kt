package example.nucleus.player

import example.nucleus.platform.Platform
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.io.File
import java.util.logging.Logger

/**
 * Binding de libmpv vía FFM (`java.lang.foreign`, JDK 22+) en lugar de JNA.
 *
 * Por qué FFM: JNA crea proxies dinámicos por reflexión en runtime, lo que NO funciona en
 * GraalVM Native Image (mundo cerrado). FFM (downcalls con MethodHandle) sí compila y ejecuta
 * en native-image. Esta es la única implementación usada por la app (JVM y nativa).
 *
 * Mantiene la MISMA superficie de API que el binding JNA anterior para minimizar los cambios
 * en [MpvAudioPlayer]: mismas funciones, handle como [MemorySegment] en vez de Pointer.
 */
object MpvLib {

    // mpv_event_id (client.h) — identificadores de eventos de mpv
    const val MPV_EVENT_NONE = 0
    const val MPV_EVENT_SHUTDOWN = 1
    const val MPV_EVENT_END_FILE = 7
    const val MPV_EVENT_FILE_LOADED = 8
    const val MPV_EVENT_PLAYBACK_RESTART = 21
    const val MPV_EVENT_PROPERTY_CHANGE = 22

    // mpv_end_file_reason — razones de fin de archivo
    const val MPV_END_FILE_REASON_EOF = 0
    const val MPV_END_FILE_REASON_STOP = 2
    const val MPV_END_FILE_REASON_QUIT = 3
    const val MPV_END_FILE_REASON_ERROR = 4
    const val MPV_END_FILE_REASON_REDIRECT = 5

    // mpv_format — formatos de datos de mpv
    const val MPV_FORMAT_NONE = 0
    const val MPV_FORMAT_FLAG = 3
    const val MPV_FORMAT_INT64 = 4
    const val MPV_FORMAT_DOUBLE = 5

    private val log = Logger.getLogger("MpvLib")

    // Arena global que mantiene viva la biblioteca cargada durante toda la vida de la app.
    private val arena: Arena = Arena.ofShared()
    private val linker: Linker = Linker.nativeLinker()

    private val C_POINTER = ValueLayout.ADDRESS
    private val C_INT = ValueLayout.JAVA_INT
    private val C_LONG = ValueLayout.JAVA_LONG
    private val C_DOUBLE = ValueLayout.JAVA_DOUBLE

    private var mpvCreate: MethodHandle? = null
    private var mpvInitialize: MethodHandle? = null
    private var mpvCommand: MethodHandle? = null
    private var mpvTerminateDestroy: MethodHandle? = null
    private var mpvSetPropertyString: MethodHandle? = null
    private var mpvGetPropertyString: MethodHandle? = null
    private var mpvFree: MethodHandle? = null
    private var mpvObserveProperty: MethodHandle? = null
    private var mpvWaitEvent: MethodHandle? = null
    private var mpvWakeup: MethodHandle? = null
    private var mpvRequestLogMessages: MethodHandle? = null

    private var loadError: Throwable? = null
    val isAvailable: Boolean get() = loadError == null && mpvCreate != null
    fun getLoadError(): Throwable? = loadError

    private fun ensureAvailable() {
        if (!isAvailable) {
            throw IllegalStateException(
                "libmpv no disponible: ${loadError?.message ?: "desconocido"} — verifica que libmpv-2.dll no esté corrupto/bloqueado, instala VC++ Redistributable y reinstala la aplicación",
                loadError
            )
        }
    }

    init {
        try {
            val lookup = resolveLibrary()
            fun bind(name: String, descriptor: FunctionDescriptor): MethodHandle =
                linker.downcallHandle(
                    lookup.find(name).orElseThrow { UnsatisfiedLinkError("mpv symbol not found: $name") },
                    descriptor,
                )

            mpvCreate = bind("mpv_create", FunctionDescriptor.of(C_POINTER))
        mpvInitialize = bind("mpv_initialize", FunctionDescriptor.of(C_INT, C_POINTER))
        mpvCommand = bind("mpv_command", FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER))
        mpvTerminateDestroy = bind("mpv_terminate_destroy", FunctionDescriptor.ofVoid(C_POINTER))
        mpvSetPropertyString = bind(
            "mpv_set_property_string",
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER),
        )
        mpvGetPropertyString = bind("mpv_get_property_string", FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER))
        mpvFree = bind("mpv_free", FunctionDescriptor.ofVoid(C_POINTER))
        mpvObserveProperty = bind(
            "mpv_observe_property",
            FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_POINTER, C_INT),
        )
        mpvWaitEvent = bind("mpv_wait_event", FunctionDescriptor.of(C_POINTER, C_POINTER, C_DOUBLE))
        mpvWakeup = bind("mpv_wakeup", FunctionDescriptor.ofVoid(C_POINTER))
        mpvRequestLogMessages = bind(
            "mpv_request_log_messages",
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
        )
        } catch (e: Throwable) {
            loadError = e
            log.severe("MpvLib: no se pudo inicializar libmpv: ${e.message} — verifica que libmpv-2.dll no esté corrupto/bloqueado y reinstala si hace falta")
        }
    }

    /**
     * Resuelve y carga libmpv:
     * - Windows: `libmpv-2.dll` empaquetado junto a la app (se busca en las carpetas habituales).
     * - Linux/macOS: libmpv del sistema por ruta absoluta (versiones `libmpv.so.2` / `libmpv.2.dylib`).
     * Devuelve el [SymbolLookup] para enlazar los símbolos.
     */
    private fun resolveLibrary(): SymbolLookup {
        if (Platform.isWindows) {
            val userDir = File(System.getProperty("user.dir"))
            val rootDir = userDir.parentFile ?: userDir
            val resProp = System.getProperty("compose.application.resources.dir")
            val possibleDirs = mutableListOf(
                userDir,                          // distributable nativo: DLL junto al exe
                File(userDir, "resources"),
                File(userDir, "app/resources"),
                File(userDir, "app/app/resources"),
                File(userDir, "mpv-resources/windows"),
                File(userDir, "mpv-resources"),
                File(rootDir, "mpv-resources/windows"),
                File(rootDir, "mpv-resources"),
                File(rootDir, "resources"),
            )
            if (resProp != null) {
                possibleDirs.add(File(resProp))
                possibleDirs.add(File(resProp, "windows"))
            }

            for (dir in possibleDirs) {
                val dll = File(dir, "libmpv-2.dll")
                if (!dll.isFile || !dll.canRead()) continue
                // libmpv real pesa ~120 MB (120_987_648). Un puntero LFS o DLL corrupta pesa <1 MB / 4 KB.
                if (dll.length() < 50_000_000L) {
                    log.warning("MpvLib: ${dll.absolutePath} parece corrupto/incompleto (size=${dll.length()}), saltando")
                    continue
                }
                try {
                    log.info("MpvLib: cargando libmpv (FFM) desde ${dll.absolutePath} (${dll.length()} bytes)")
                    return SymbolLookup.libraryLookup(dll.absolutePath, arena)
                } catch (e: IllegalArgumentException) {
                    log.warning("MpvLib: no se pudo cargar ${dll.absolutePath}: ${e.message}")
                    continue
                } catch (e: UnsatisfiedLinkError) {
                    log.warning("MpvLib: UnsatisfiedLinkError en ${dll.absolutePath}: ${e.message}")
                    continue
                }
            }
            log.warning("MpvLib: no se encontró libmpv-2.dll válido en: ${possibleDirs.joinToString { it.absolutePath }}")
            // Fallback por nombre (depende de PATH / System32). En GraalVM native-image fallará con IllegalArgumentException
            // si la DLL no está en el PATH, pero lo intentamos para no romper instalaciones con libmpv del sistema.
            return try {
                SymbolLookup.libraryLookup("libmpv-2", arena)
            } catch (e: Exception) {
                log.severe("MpvLib: fallback SymbolLookup('libmpv-2') falló: ${e.message}")
                throw e
            }
        }

        // Linux/macOS: libmpv del sistema.
        val soNames = if (Platform.isMac)
            listOf("libmpv.2.dylib", "libmpv.dylib")
        else
            listOf("libmpv.so.2", "libmpv.so.1", "libmpv.so")

        val searchDirs = listOf(
            "/usr/lib/x86_64-linux-gnu",
            "/usr/lib64",
            "/usr/lib",
            "/usr/local/lib",
            "/opt/homebrew/lib",
            "/usr/local/opt/mpv/lib",
            "/app/lib",
        )
        for (dir in searchDirs) {
            for (name in soNames) {
                val f = File(dir, name)
                if (f.exists()) {
                    log.info("MpvLib: cargando libmpv del sistema (FFM) desde ${f.absolutePath}")
                    return SymbolLookup.libraryLookup(f.absolutePath, arena)
                }
            }
        }
        log.info("MpvLib: libmpv del sistema no encontrada en rutas conocidas; probando resolución por nombre")
        return SymbolLookup.libraryLookup(if (Platform.isMac) "mpv" else "mpv", arena)
    }

    // ── API pública (misma forma que el binding JNA) ──────────────────────────

    /** Convierte [s] en una C-string NUL-terminada en [allocator] (compatible JDK 21+). */
    private fun SegmentAllocator.utf8(s: String): MemorySegment {
        val bytes = s.toByteArray(Charsets.UTF_8)
        val seg = allocate(bytes.size + 1L)
        bytes.forEachIndexed { i, b -> seg.set(ValueLayout.JAVA_BYTE, i.toLong(), b) }
        seg.set(ValueLayout.JAVA_BYTE, bytes.size.toLong(), 0.toByte())
        return seg
    }

    fun mpv_create(): MemorySegment? {
        ensureAvailable()
        val seg = mpvCreate!!.invokeWithArguments() as MemorySegment
        return if (seg.address() == 0L) null else seg
    }

    fun mpv_initialize(handle: MemorySegment): Int {
        ensureAvailable()
        return mpvInitialize!!.invokeWithArguments(handle) as Int
    }

    /** [args] es una lista de argumentos terminada en null (el array se copia en una arena confinada). */
    fun mpv_command(handle: MemorySegment, args: Array<String?>) {
        ensureAvailable()
        Arena.ofConfined().use { a ->
            val arr = a.allocate(C_POINTER, (args.size + 1).toLong())
            args.forEachIndexed { i, s ->
                val p: MemorySegment = if (s != null) a.utf8(s) else MemorySegment.NULL
                arr.set(C_POINTER, (i * C_POINTER.byteSize()).toLong(), p)
            }
            mpvCommand!!.invokeWithArguments(handle, arr)
        }
    }

    fun mpv_terminate_destroy(handle: MemorySegment) {
        ensureAvailable()
        mpvTerminateDestroy!!.invokeWithArguments(handle)
    }

    fun mpv_set_property_string(handle: MemorySegment, name: String, value: String): Int {
        ensureAvailable()
        return Arena.ofConfined().use { a ->
            mpvSetPropertyString!!.invokeWithArguments(
                handle,
                a.utf8(name),
                a.utf8(value),
            ) as Int
        }
    }

    /** Devuelve un segmento apuntando a la cadena nativa; hay que liberarlo con [mpv_free]. */
    fun mpv_get_property_string(handle: MemorySegment, name: String): MemorySegment? {
        ensureAvailable()
        return Arena.ofConfined().use { a ->
            val seg = mpvGetPropertyString!!.invokeWithArguments(handle, a.utf8(name)) as MemorySegment
            if (seg.address() == 0L) null else seg
        }
    }

    fun mpv_free(data: MemorySegment) {
        ensureAvailable()
        mpvFree!!.invokeWithArguments(data)
    }

    fun mpv_observe_property(handle: MemorySegment, replyUserdata: Long, name: String, format: Int): Int {
        ensureAvailable()
        return Arena.ofConfined().use { a ->
            mpvObserveProperty!!.invokeWithArguments(handle, replyUserdata, a.utf8(name), format) as Int
        }
    }

    /** Bloquea hasta [timeout] segundos (negativo = indefinidamente). Devuelve null en error/fin. */
    fun mpv_wait_event(handle: MemorySegment, timeout: Double): MemorySegment? {
        ensureAvailable()
        val seg = mpvWaitEvent!!.invokeWithArguments(handle, timeout) as MemorySegment
        return if (seg.address() == 0L) null else seg
    }

    fun mpv_wakeup(handle: MemorySegment) {
        ensureAvailable()
        mpvWakeup!!.invokeWithArguments(handle)
    }

    /** Habilita el log de mpv: nivel mínimo [level] ("no", "fatal", "error", "warn", "info", "debug", "trace"). */
    fun mpv_request_log_messages(handle: MemorySegment, level: String): Int {
        ensureAvailable()
        return Arena.ofConfined().use { a ->
            mpvRequestLogMessages!!.invokeWithArguments(handle, a.utf8(level)) as Int
        }
    }
}
