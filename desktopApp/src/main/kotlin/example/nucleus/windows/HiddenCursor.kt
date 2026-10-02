package example.nucleus.windows

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import io.github.aakira.napier.Napier
import java.util.Arrays

/**
 * Oculta el cursor del sistema mientras Now Playing tiene los controles auto-ocultos.
 *
 * La ventana es nativa de **tao** (`nucleus.decorated-window-tao`), no un `JFrame` de
 * Swing, asi que no hay API de Compose ni de Jewel para esto: `PointerIcon` no tiene un
 * valor "oculto" y `PlatformCursorController.hideCursor()` solo hace algo en macOS. Se
 * resuelve por FFI con JNA, que el proyecto ya usa para la barra de tareas.
 *
 * ## Por que un cursor propio y no `IDC_NONE`
 *
 * No existe un `IDC_NONE`. Los identificadores de `winuser.h` son una serie contigua y el
 * siguiente a `IDC_ARROW` (32512) es `IDC_IBEAM` (32513), un cursor de insercion de texto.
 * Verificado sobre Windows 11:
 *
 * ```
 * id 32512 -> 0x65539   IDC_ARROW
 * id 32513 -> 0x65541   IDC_IBEAM   (no es "sin cursor")
 * id 32522 -> NULL      IDC_SIZEALL no existe como tal
 * ```
 *
 * Por eso se crea con `CreateIcon` un `HCURSOR` de 32x32 con la mascara AND a unos y el
 * plano XOR a ceros, que es la forma canonica de un cursor completamente transparente
 * (AND = 1 significa "no dibujar este pixel"). Comprobado con `GetIconInfo` +
 * `GetBitmapBits`: el cursor es monocromo, sin plano de color, y los 128 bytes de la
 * mascara estan todos a `0xFF`.
 *
 * ## Por que `SetCursor` y no `ShowCursor(FALSE)`
 *
 * `ShowCursor` es un contador global por hilo que reinicia el sistema y que hay que
 * compensar con el mismo numero de `TRUE`. Si alguna vez se descuadra, el cursor se queda
 * invisible en toda la aplicacion. `SetCursor` solo fija el cursor actual, sin estado.
 *
 * ## Por que hay que repetirla
 *
 * `SetCursor` no es permanente: Windows repone el cursor de la ventana con cada
 * `WM_SETCURSOR`, y la propia ventana puede hacerlo al repintar. Por eso
 * [hide] es idempotente y barata (una sola llamada a user32, los handles estan
 * cacheados) y el motor de auto-hide la refresca en cada tick mientras los controles estan
 * ocultos.
 *
 * Best-effort por completo: en macOS y Linux no hace nada, y cualquier fallo de FFI se
 * ignora para que la reproduccion nunca se detenga por un cursor.
 */
object HiddenCursor {

    /** `IDC_ARROW`. MAKEINTRESOURCE: el id va en la direccion del puntero. */
    private const val IDC_ARROW = 32512

    /**
     * El id del cursor del sistema convertido en puntero, al modo MAKEINTRESOURCE.
     * `createConstant` construye un puntero con ese valor sin reservar memoria, que es
     * justo lo que espera `LoadCursorW` cuando el nombre es un recurso y no un string.
     */
    private val IDC_ARROW_PTR: Pointer = Pointer.createConstant(IDC_ARROW.toLong())

    /** `SM_CXCURSOR`: ancho del cursor del sistema. */
    private const val SM_CXCURSOR = 13

    private interface User32 : StdCallLibrary {
        fun LoadCursorW(hInstance: Pointer?, lpCursorName: Pointer?): Pointer?
        fun SetCursor(hCursor: Pointer?): Pointer?
        fun CreateIcon(
            hInstance: Pointer?,
            nWidth: Int,
            nHeight: Int,
            nPlanes: Int,
            nBitsPixel: Int,
            lpANDbits: ByteArray?,
            lpXORbits: ByteArray?,
        ): Pointer?

        fun GetSystemMetrics(nIndex: Int): Int

        companion object {
            val INSTANCE: User32 = Native.load("user32", User32::class.java)
        }
    }

    /**
     * Cursor totalmente transparente, del tamaño del del sistema.
     *
     * Se resuelve una sola vez: [hide] y [show] solo llaman a `SetCursor` con este handle.
     */
    private val blankCursor: Pointer? by lazy {
        runCatching {
            val size = User32.INSTANCE.GetSystemMetrics(SM_CXCURSOR).coerceAtLeast(16)
            // Las filas de un bitmap monocromo empiezan en un limite de DWORD.
            val stride = ((size + 31) / 32) * 4
            // AND a 1 = pixel transparente; XOR a 0 = nada que dibujar encima.
            val andBits = ByteArray(stride * size).also { Arrays.fill(it, 0xFF.toByte()) }
            val xorBits = ByteArray(stride * size)
            User32.INSTANCE.CreateIcon(null, size, size, 1, 1, andBits, xorBits)
        }.onFailure { Napier.w("[cursor] no se pudo crear el cursor invisible: ${it.message}") }
            .getOrNull()
    }

    /** Cursor de flecha del sistema, cargado una sola vez. */
    private val arrowCursor: Pointer? by lazy {
        runCatching {
            User32.INSTANCE.LoadCursorW(null, IDC_ARROW_PTR)
        }.onFailure { Napier.w("[cursor] no se pudo cargar la flecha: ${it.message}") }
            .getOrNull()
    }

    /** Solo en Windows. En macOS y Linux el objeto entero es un no-op. */
    private val supported: Boolean = runCatching {
        Platform.isWindows() && User32.INSTANCE.LoadCursorW(null, IDC_ARROW_PTR) != null
    }.getOrElse {
        Napier.w("[cursor] no disponible en esta plataforma: ${it.message}")
        false
    }

    /**
     * Fija el cursor invisible. Idempotente: pensarse para llamarla en cada tick, porque
     * cualquier `WM_SETCURSOR` repone la flecha. Si no se pudo crear el cursor transparente,
     * recurre a `SetCursor(NULL)`, que en Windows equivale a no dibujar cursor.
     */
    fun hide() {
        if (!supported) return
        val target = blankCursor
        runCatching { User32.INSTANCE.SetCursor(target) }
            .onFailure { Napier.w("[cursor] no se pudo ocultar: ${it.message}") }
    }

    /** Devuelve el cursor normal. Debe llamarse en cuanto el puntero se mueva. */
    fun show() {
        if (!supported) return
        val target = arrowCursor
        runCatching { User32.INSTANCE.SetCursor(target) }
            .onFailure { Napier.w("[cursor] no se pudo restaurar: ${it.message}") }
    }
}