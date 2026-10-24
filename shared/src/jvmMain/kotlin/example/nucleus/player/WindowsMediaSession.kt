package example.nucleus.player

import dev.nucleusframework.media.control.MediaControlEvent
import dev.nucleusframework.media.control.MediaControlService
import dev.nucleusframework.media.control.MediaMetadata
import dev.nucleusframework.media.control.MediaPlaybackState
import dev.nucleusframework.media.control.MediaPlaybackStatus
import io.github.aakira.napier.Napier
import java.util.logging.Logger

/**
 * Media controls del sistema (Now Playing) expuestos a `PlayerViewModel`.
 *
 * Delega en el `nucleus.media-control` de Nucleus (`MediaControlService`), que envuelve:
 *  - Windows: SMTC (System Media Transport Controls)
 *  - Linux:   MPRIS (D-Bus; `playerctl`, indicadores de escritorio)
 *  - macOS:   Now Playing / MPRemoteCommandCenter
 *
 * El AppUserModelID lo gestiona Nucleus: `MediaControlService.configure()` aplica
 * `NucleusApp.aumid` al proceso (`SetCurrentProcessExplicitAppUserModelID`) y escribe
 * `System.AppUserModel.ID` en el acceso directo del menú Inicio, para que el panel de
 * medios muestre "PaltaSound" y no "Aplicación Desconocida".
 */
class WindowsMediaSession {

    private val log = Logger.getLogger("WindowsMediaSession")

    @Volatile private var configured = false

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null
    private var onSeek: ((Long) -> Unit)? = null

    private var positionProvider: (() -> Long)? = null

    fun initError(): String? =
        if (MediaControlService.isAvailable()) null else "nucleus.media-control no disponible"

    fun isInitialized(): Boolean = configured

    fun setCallbacks(
        onPlay: () -> Unit,
        onPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onStop: () -> Unit,
    ) {
        this.onPlay = onPlay
        this.onPause = onPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onStop = onStop
        attachCallbacks()
    }

    fun setPositionProvider(provider: () -> Long) {
        this.positionProvider = provider
    }

    /** Recibe seek desde el sistema (SeekBy / SetPosition). */
    fun setSeekHandler(handler: (Long) -> Unit) {
        this.onSeek = handler
    }

    /** Configura el backend nativo y registra el listener de eventos. */
    fun initialize() {
        if (configured) return
        configured = true
        if (!MediaControlService.isAvailable()) {
            log.warning("MediaControlService no disponible; media controls desactivados")
            return
        }
        MediaControlService.configure()
        Napier.i("[mediasession] media-control inicializado (${platformLabel()})")
        attachCallbacks()
    }

    private fun platformLabel(): String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "SMTC"
        System.getProperty("os.name").lowercase().contains("linux") -> "MPRIS"
        else -> "Now Playing"
    }

    private fun attachCallbacks() {
        if (!configured || !MediaControlService.isAvailable()) return
        MediaControlService.attach { event ->
            when (event) {
                MediaControlEvent.Play -> onPlay?.invoke()
                MediaControlEvent.Pause -> onPause?.invoke()
                // En esta app onPlay/onPause se cablean a togglePlayPause.
                MediaControlEvent.Toggle -> onPlay?.invoke()
                MediaControlEvent.Next -> onNext?.invoke()
                MediaControlEvent.Previous -> onPrevious?.invoke()
                MediaControlEvent.Stop -> onStop?.invoke()
                is MediaControlEvent.SeekBy -> onSeek?.invoke((positionProvider?.invoke() ?: 0L) + event.offsetMs)
                is MediaControlEvent.SetPosition -> onSeek?.invoke(event.positionMs)
                else -> {}
            }
        }
    }

    fun updateMetadata(title: String, artist: String, album: String, thumbnailUrl: String? = null, durationMs: Long? = null) {
        if (!configured || !MediaControlService.isAvailable()) return
        // coverUrl: https remoto, file:// o ruta local (Nucleus 2.4.7+ Windows CreateFromFile).
        MediaControlService.setMetadata(
            MediaMetadata(
                title = title,
                artist = artist,
                album = album,
                coverUrl = thumbnailUrl,
                duration = durationMs,
            ),
        )
    }

    fun setPlaybackStatus(isPlaying: Boolean, isPaused: Boolean) {
        if (!configured || !MediaControlService.isAvailable()) return
        val status = when {
            isPlaying -> MediaPlaybackStatus.PLAYING
            isPaused -> MediaPlaybackStatus.PAUSED
            else -> MediaPlaybackStatus.STOPPED
        }
        MediaControlService.setPlaybackState(MediaPlaybackState(status, positionProvider?.invoke()))
    }

    fun setVolume(volume: Float) {
        if (!configured || !MediaControlService.isAvailable()) return
        MediaControlService.setVolume(volume.toDouble().coerceIn(0.0, 1.0))
    }

    fun resetToIdle() {
        if (!configured || !MediaControlService.isAvailable()) return
        MediaControlService.setMetadata(MediaMetadata(title = "MusicPlayer", artist = "", album = "", coverUrl = null, duration = null))
        MediaControlService.setPlaybackState(MediaPlaybackState(MediaPlaybackStatus.STOPPED, null))
    }

    fun release() {
        runCatching { MediaControlService.detach() }
        configured = false
    }
}
