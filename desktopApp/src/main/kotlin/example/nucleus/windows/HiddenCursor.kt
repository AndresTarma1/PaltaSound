package example.nucleus.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
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
 * Se usa `SetCursor(LoadCursorW(NULL, IDC_NONE))` en lugar de `ShowCursor(FALSE)`: aquel es
 * un contador global por hilo que el sistema reinicia solo y que hay que compensar con el
 * mismo número de `TRUE`, así que se desincroniza con facilidad. Con `SetCursor` basta
 * volver a fijar la flecha para restaurar el cursor.
 *
 * Es best-effort: en macOS y Linux no hace nada, y cualquier fallo de FFI se ignora en
 * silencio para que la reproducción nunca se detenga por un cursor.
 */
object HiddenCursor {

    private const val IDC_ARROW = 32512   // 0x7F00
    private const val IDC_NONE = 32513    // 0x7F01

    private interface User32 : StdCallLibrary {
        fun LoadCursorW(hInstance: Pointer?, lpCursorName: WString): Pointer?
        fun SetCursor(hCursor: Pointer?): Pointer?

        companion object {
            val INSTANCE: User32 = Native.load("user32", User32::class.java)
        }
    }

    private val available: Boolean = runCatching {
        User32.INSTANCE.LoadCursorW(null, WString("$IDC_ARROW")) != null
    }.getOrElse {
        Napier.w("HiddenCursor no disponible en esta plataforma: ${it.message}")
        false
    }

    /** Oculta el cursor. Silencioso si la plataforma no lo soporta. */
    fun hide() {
        if (!available) return
        runCatching {
            User32.INSTANCE.SetCursor(User32.INSTANCE.LoadCursorW(null, WString("$IDC_NONE")))
        }.onFailure { Napier.w("No se pudo ocultar el cursor: ${it.message}") }
    }

    /** Devuelve el cursor normal. Debe llamarse en cuanto el puntero se mueva. */
    fun show() {
        if (!available) return
        runCatching {
            User32.INSTANCE.SetCursor(User32.INSTANCE.LoadCursorW(null, WString("$IDC_ARROW")))
        }.onFailure { Napier.w("No se pudo restaurar el cursor: ${it.message}") }
    }
}