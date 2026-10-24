package example.nucleus.overlay

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Un atajo de teclado global (modificadores + tecla). Los modificadores se persisten como una
 * máscara empaquetada (bit0 ctrl, bit1 alt, bit2 shift, bit3 meta) para coincidir con
 * [example.nucleus.data.repository.UserPreferencesRepository].
 */
data class HotkeyCombo(
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val meta: Boolean,
    val keyCode: Int,
) {
    val modsMask: Int
        get() = (if (ctrl) 1 else 0) or (if (alt) 2 else 0) or (if (shift) 4 else 0) or (if (meta) 8 else 0)

    fun label(): String = buildList {
        if (ctrl) add("Ctrl")
        if (alt) add("Alt")
        if (shift) add("Shift")
        if (meta) add("Win")
        add(NativeKeyEvent.getKeyText(keyCode))
    }.joinToString(" + ")

    companion object {
        /** Valor por defecto razonable: Ctrl + Shift + M. */
        val DEFAULT = HotkeyCombo(ctrl = true, alt = false, shift = true, meta = false, keyCode = NativeKeyEvent.VC_M)

        fun fromPrefs(code: Int, mods: Int): HotkeyCombo =
            if (code == 0) DEFAULT
            else HotkeyCombo(
                ctrl = mods and 1 != 0,
                alt = mods and 2 != 0,
                shift = mods and 4 != 0,
                meta = mods and 8 != 0,
                keyCode = code,
            )
    }
}

/**
 * Registra un gancho de teclado a nivel del sistema (mediante jnativehook) que ejecuta [onTrigger]
 * cuando se presiona el [HotkeyCombo] configurado, incluso mientras otra aplicación/juego tiene el
 * foco. También ofrece un modo de "captura" único para que la pantalla de configuración pueda
 * registrar una nueva combinación del usuario.
 *
 * Las devoluciones de llamada se despachan en el hilo principal de Compose (Dispatchers.Main)
 * para que puedan manipular estado de UI/Compose de forma segura.
 */
class GlobalHotkeyManager(
    private val onTrigger: () -> Unit,
) : NativeKeyListener {

    @Volatile private var combo: HotkeyCombo = HotkeyCombo.DEFAULT
    @Volatile private var enabled: Boolean = true
    @Volatile private var captureCallback: ((HotkeyCombo) -> Unit)? = null
    private var started = false

    fun start() {
        if (started) return
        // jnativehook es muy verboso con java.util.logging — silenciarlo.
        runCatching {
            val pkg = GlobalScreen::class.java.`package`?.name ?: "com.github.kwhat.jnativehook"
            Logger.getLogger(pkg).apply {
                level = Level.OFF
                useParentHandlers = false
            }
        }
        runCatching {
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(this)
            started = true
        }.onFailure { Napier.e("[overlay] failed to register native hook: ${it.message}") }
    }

    fun stop() {
        runCatching {
            if (started) {
                GlobalScreen.removeNativeKeyListener(this)
                GlobalScreen.unregisterNativeHook()
                started = false
            }
        }
    }

    fun updateCombo(c: HotkeyCombo) { combo = c }
    fun setEnabled(value: Boolean) { enabled = value }

    /** Registrar la siguiente pulsación de tecla que no sea modificador (con sus modificadores activos) como una nueva combinación, una sola vez. */
    fun beginCapture(onCaptured: (HotkeyCombo) -> Unit) { captureCallback = onCaptured }
    fun cancelCapture() { captureCallback = null }

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        val ctrl = e.modifiers and NativeKeyEvent.CTRL_MASK != 0
        val alt = e.modifiers and NativeKeyEvent.ALT_MASK != 0
        val shift = e.modifiers and NativeKeyEvent.SHIFT_MASK != 0
        val meta = e.modifiers and NativeKeyEvent.META_MASK != 0
        val code = e.keyCode

        val capture = captureCallback
        if (capture != null) {
            if (isModifierKey(code)) return // esperar una tecla real, no un modificador aislado
            captureCallback = null
            val captured = HotkeyCombo(ctrl, alt, shift, meta, code)
            dispatch { capture(captured) }
            return
        }

        if (!enabled) return
        val target = combo
        if (code == target.keyCode &&
            ctrl == target.ctrl && alt == target.alt && shift == target.shift && meta == target.meta
        ) {
            dispatch(onTrigger)
        }
    }

    override fun nativeKeyReleased(e: NativeKeyEvent) {}
    override fun nativeKeyTyped(e: NativeKeyEvent) {}

    private fun isModifierKey(code: Int): Boolean = code == NativeKeyEvent.VC_CONTROL ||
        code == NativeKeyEvent.VC_SHIFT || code == NativeKeyEvent.VC_ALT || code == NativeKeyEvent.VC_META

    private fun dispatch(block: () -> Unit) = GlobalScope.launch(Dispatchers.Main) { block() }
}
