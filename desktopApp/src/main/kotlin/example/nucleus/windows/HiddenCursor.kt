package example.nucleus.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import io.github.aakira.napier.Napier

/**
 * Oculta el cursor del sistema mientras Now Playing tiene los controles auto-ocultos.
 *
 * La ventana de la app es nativa de **tao** (`nucleus.decorated-window-tao`), no un
 * `JFrame` de Swing, así que ni Compose (`PointerIcon` no tiene un valor "oculto") ni
 * Jewel (`PlatformCursorController.hideCursor()` solo hace algo en macOS) ofrecen nada.
 * Se hace por FFI con JNA, que el proyecto ya usa para la barra de tareas.
 *
 * Se usa `SetCursor(LoadCursor(NULL, IDC_NONE))` en lugar de `ShowCursor(FALSE)`: aquel es
 * un contador global por hilo que el sistema reinicia solo y que hay que compensar con el
 * mismo número de `TRUE`, así que se desincroniza con facilidad. Con `SetCursor` basta
 * volver a fijar la flecha para restaurar el cursor.
 *
 * Los identificadores van como punteros, no como cadena: `IDC_ARROW` e `IDC_NONE` son
 * recursos del sistema y se pasan con MAKEINTRESOURCE, es decir el propio ID en la
 * posición del puntero. Pasarlos como texto hace que `LoadCursorW` devuelva NULL.
 *
 * Es best-effort: en macOS y Linux no hace nada, y cualquier fallo de FFI se ignora en
 * silencio para que la reproducción nunca se detenga por un cursor.
 */
object HiddenCursor {

    /** MAKEINTRESOURCE: el id va en la dirección del puntero. */
    private val IDC_ARROW = Pointer(32512)   // 0x7F00
    private val IDC_NONE = Pointer(32513)    // 0x7F01

    private interface User32 : StdCallLibrary {
        fun LoadCursorW(hInstance: Pointer?, lpCursorName: Pointer?): Pointer?
        fun SetCursor(hCursor: Pointer?): Pointer?

        companion object {
            val INSTANCE: User32 = Native.load("user32", User32::class.java)
        }
    }

    private val available: Boolean = runCatching {
        User32.INSTANCE.LoadCursorW(null, IDC_ARROW) != null
    }.getOrElse {
        Napier.w("[cursor] no disponible en esta plataforma: ${it.message}")
        false
    }

    /**
     * Fija el cursor a oculto o a la flecha. Es idempotente y barata (una llamada a
     * user32), así que se puede refrescar en cada tick: si la ventana recibe un
     * `WM_SETCURSOR` y Windows repone la flecha, el siguiente tick la vuelve a esconder.
     */
    fun hide() = set(IDC_NONE)

    /** Devuelve el cursor normal. Debe llamarse en cuanto el puntero se mueva. */
    fun show() = set(IDC_ARROW)

    private fun set(id: Pointer) {
        if (!available) return
        runCatching { User32.INSTANCE.SetCursor(User32.INSTANCE.LoadCursorW(null, id)) }
            .onFailure { Napier.w("[cursor] no se pudo aplicar: ${it.message}") }
    }
}