package example.nucleus.lifecycle

import example.nucleus.download.DownloadService
import example.nucleus.overlay.GlobalHotkeyManager
import example.nucleus.player.PlayerService
import example.nucleus.player.WindowsMediaSession
import example.nucleus.utils.SyncUtils
import example.nucleus.utils.cipher.PoTokenManager

class AppLifecycleManager(
    private val mediaSession: WindowsMediaSession,
    private val playerService: PlayerService,
    private val downloadService: DownloadService,
    private val syncUtils: SyncUtils,
    private val hotkeyManager: GlobalHotkeyManager,
) {

    fun cleanUpAndExit() {
        // Se debe desregistrar el hook global de teclado de bajo nivel (WH_KEYBOARD_LL, via jnativehook)
        // ANTES de que el proceso muera. Si no lo hacemos, Windows mantiene el hook muerto en su cadena
        // de entrada de todo el sistema hasta que detecta que el proceso propietario desapareció
        // (LowLevelHooksTimeout, ~300ms por defecto) — cada evento de ratón/teclado en todo el sistema
        // se bloquea hasta entonces. Esa era la causa del síntoma de "el ratón se congela un momento al salir".
        runCatching { hotkeyManager.stop() }
        runCatching { syncUtils.cancelAllSyncs() }
        runCatching { downloadService.release() }
        runCatching { mediaSession.release() }
        runCatching { playerService.release() }
        // Cierre acotado: resetear el PoTokenManager es rápido; si colgara igualmente, halt(0) está abajo.
        runCatching {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeout(2_000) { PoTokenManager.reset() }
            }
        }
        // Runtime.halt() omite los shutdown hooks del JVM (los nuestros ya se ejecutaron arriba;
        // cualquier hook de biblioteca de terceros también se omite). Aquí es seguro: nuestro estado
        // ya fue guardado y liberado; cualquier cosa que un hook "libere elegantemente" (archivos
        // temporales, manejadores GPU/nativos) es reclamada por el SO al morir el proceso de todas formas.
        // exitProcess()/System.exit() ejecutaría esos hooks sincronísticamente primero, añadiendo
        // un retraso evitable al cierre.
        Runtime.getRuntime().halt(0)
    }
}

