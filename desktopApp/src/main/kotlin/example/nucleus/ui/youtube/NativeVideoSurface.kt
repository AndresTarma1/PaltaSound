package example.nucleus.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import dev.nucleusframework.application.LocalNucleusWindow
import dev.nucleusframework.window.tao.TaoWindow
import example.nucleus.platform.Platform
import example.nucleus.player.YouTubePlayerService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

private val videoSurfaceLog = Logger.getLogger("NativeVideoSurface")

/** Space reserved inside the watch card for the overlay controls. */
private val WatchControlsInset = 64.dp

/**
 * Video surface for YouTube main.
 *
 * A native Win32 child HWND (no Swing/Canvas/AWT) is positioned directly from
 * Compose layout; libmpv creates its video child under it via `wid`.
 *
 * We deliberately do not use Nucleus' `NativeView` here: its DirectComposition
 * blending overlay blocks the Tao event loop inside
 * `DirectContext.flushAndSubmit` when a wheel event arrives with video active
 * (see the thread dumps). The raw HWND gives the same embedding without that
 * overlay.
 */
@Composable
fun NativeVideoSurface(
    playerService: YouTubePlayerService,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (!Platform.isWindows) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "El vídeo embebido de YouTube está disponible en Windows.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val nucleusWindow = LocalNucleusWindow.current
    val taoWindow = nucleusWindow.unsafe.taoWindow
    val parentHwnd = taoWindow?.nativeHandle ?: 0L
    // Keep one host HWND per top-level window for the whole app lifetime.
    // Destroying it here leaves libmpv painting into a dead `wid` when the
    // user leaves the Watch screen and opens another video.
    val host = remember(parentHwnd, taoWindow) {
        VideoHostWindow.acquire(parentHwnd, taoWindow)
    }
    // One token per composition instance. Tokens only grow, so a Watch surface
    // that is still disposing can never reposition/hide the mini player that
    // already owns the shared HWND.
    val surfaceToken = remember { playerService.newSurfaceToken() }
    val bounds = remember { intArrayOf(0, 0, 0, 0) }
    val controlsInsetPx = with(LocalDensity.current) { WatchControlsInset.roundToPx() }
    LaunchedEffect(host, controlsInsetPx) {
        host.setControlsInset(controlsInsetPx)
    }
    DisposableEffect(host, surfaceToken) {
        onDispose {
            val wasCurrent = playerService.detachSurface(surfaceToken)
            // While minimizing, the mini player is about to attach to the same
            // HWND; hiding here would leave the mini surface black. When
            // restoring, the Watch screen reattaches on the next frame.
            if (wasCurrent && !playerService.minimized.value) host.hide()
        }
    }
    LaunchedEffect(host.hwnd, visible, surfaceToken) {
        videoSurfaceLog.info(
            "NativeVideoSurface host hwnd=0x${host.hwnd.toString(16)} visible=$visible",
        )
        if (host.hwnd != 0L && playerService.attachSurface(host.hwnd, surfaceToken)) {
            if (visible) {
                if (bounds[2] > 0 && bounds[3] > 0) {
                    host.setComposeBounds(
                        surfaceToken, bounds[0], bounds[1], bounds[2], bounds[3],
                    )
                    host.show()
                }
            } else {
                host.hide()
            }
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val position = coords.positionInRoot().round()
                bounds[0] = position.x
                bounds[1] = position.y
                bounds[2] = coords.size.width.coerceAtLeast(1)
                bounds[3] = coords.size.height.coerceAtLeast(1)
                // Only the newest surface may move the HWND. Without this, the
                // disposing Watch screen overwrote the mini player's position
                // and left the video floating where the big player used to be.
                if (visible && playerService.ownsSurface(surfaceToken)) {
                    host.setComposeBounds(
                        surfaceToken, bounds[0], bounds[1], bounds[2], bounds[3],
                    )
                    host.show()
                }
            }
            .background(Color.Transparent),
    )
}

/**
 * Remembers the current windowed video rect before entering native fullscreen.
 * The fullscreen prepare hook restores it before the window geometry changes,
 * so the HWND never has to be resized/repositioned frame by frame while the
 * scene is in an intermediate size.
 */
fun saveVideoHostWindowedBounds(parentHwnd: Long) {
    if (parentHwnd == 0L) return
    VideoHostWindow.saveExistingWindowedBounds(parentHwnd)
}

/**
 * Win32 child window used as libmpv's `wid` parent. It is created under the
 * Tao main HWND and positioned directly from Compose layout. The HWND is
 * cached per top-level window and only hidden on unmount, because libmpv binds
 * `wid` once at init time and cannot be moved to a new HWND.
 */
private class VideoHostWindow private constructor(
    private val parentHwnd: Long,
    private val taoWindow: TaoWindow?,
) {
    private val destroyed = AtomicBoolean(false)
    private var shown = false
    private var hasBounds = false
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE
    private var lastWidth = 0
    private var lastHeight = 0
    private var windowedBounds: IntArray? = null
    /** While a fullscreen transition settles, Compose must not move the host. */
    private var ignoreComposeBoundsUntilNanos = 0L
    private var lastComposeToken = 0L

    @Volatile
    private var controlsInsetPx = 0

    val hwnd: Long = if (Platform.isWindows) createWindow() else 0L

    init {
        // Registered once per top-level window. The hook runs synchronously
        // before Nucleus changes the window geometry, which lets us place the
        // HWND at its final rect before the scene resizes.
        taoWindow?.onFullscreenPrepare { width, height, fullscreen ->
            prepareFullscreen(width, height, fullscreen)
        }
    }

    fun isAlive(): Boolean =
        hwnd != 0L && runCatching { User32Ex.INSTANCE.IsWindow(Pointer(hwnd)) }.getOrDefault(false)

    fun setControlsInset(value: Int) {
        controlsInsetPx = value.coerceAtLeast(0)
    }

    fun saveWindowedBounds() {
        if (!hasBounds) return
        windowedBounds = intArrayOf(lastX, lastY, lastWidth, lastHeight)
    }

    fun show() {
        if (!isAlive() || shown) return
        shown = true
        runCatching { User32Ex.INSTANCE.ShowWindow(Pointer(hwnd), SW_SHOW) }
    }

    fun hide() {
        if (!isAlive() || !shown) return
        shown = false
        runCatching { User32Ex.INSTANCE.ShowWindow(Pointer(hwnd), SW_HIDE) }
    }

    fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        if (!isAlive() || width <= 0 || height <= 0) return
        if (hasBounds && x == lastX && y == lastY && width == lastWidth && height == lastHeight) {
            return
        }
        hasBounds = true
        lastX = x
        lastY = y
        lastWidth = width
        lastHeight = height
        runCatching {
            User32Ex.INSTANCE.SetWindowPos(
                Pointer(hwnd),
                null,
                x,
                y,
                width,
                height,
                SWP_NOACTIVATE,
            )
        }
    }

    /** Bounds coming from Compose layout; paused during a fullscreen toggle. */
    fun setComposeBounds(token: Long, x: Int, y: Int, width: Int, height: Int) {
        // A different surface (the mini player taking over) always wins over
        // the settle window: it is not an intermediate measurement.
        if (token == lastComposeToken && System.nanoTime() < ignoreComposeBoundsUntilNanos) return
        lastComposeToken = token
        setBounds(x, y, width, height)
    }

    private fun prepareFullscreen(width: Int, height: Int, fullscreen: Boolean) {
        if (width <= 0 || height <= 0) return
        // The scene is about to change size in one jump. Keep Compose from
        // overwriting the pre-positioned rect with intermediate measurements.
        ignoreComposeBoundsUntilNanos = System.nanoTime() + 250_000_000L
        if (fullscreen) {
            val inset = controlsInsetPx.coerceIn(0, height - 1)
            setBounds(0, 0, width, (height - inset).coerceAtLeast(1))
        } else {
            windowedBounds?.let { setBounds(it[0], it[1], it[2], it[3]) }
        }
    }

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        val value = hwnd
        if (value != 0L) {
            videoSurfaceLog.info("VideoHostWindow destroy hwnd=0x${value.toString(16)}")
            runCatching { User32Ex.INSTANCE.DestroyWindow(Pointer(value)) }
            hosts.remove(parentHwnd, this)
        }
    }

    private fun createWindow(): Long {
        return try {
            val instance = Kernel32Ex.INSTANCE.GetModuleHandleW(null)
            val child = parentHwnd != 0L
            val handle = User32Ex.INSTANCE.CreateWindowExW(
                0,
                WString("STATIC"),
                WString(""),
                (if (child) WS_CHILD else WS_POPUP) or WS_CLIPCHILDREN or WS_CLIPSIBLINGS,
                0,
                0,
                2,
                2,
                if (child) Pointer(parentHwnd) else null,
                null,
                instance,
                null,
            )
            val value = handle?.let { Pointer.nativeValue(it) } ?: 0L
            videoSurfaceLog.info(
                "VideoHostWindow created hwnd=0x${value.toString(16)} parent=0x${parentHwnd.toString(16)} child=$child",
            )
            value
        } catch (t: Throwable) {
            videoSurfaceLog.severe("VideoHostWindow failed: ${t.message}")
            0L
        }
    }

    private interface User32Ex : StdCallLibrary {
        fun CreateWindowExW(
            dwExStyle: Int,
            lpClassName: WString,
            lpWindowName: WString?,
            dwStyle: Int,
            x: Int,
            y: Int,
            nWidth: Int,
            nHeight: Int,
            hWndParent: Pointer?,
            hMenu: Pointer?,
            hInstance: Pointer?,
            lpParam: Pointer?,
        ): Pointer?

        fun DestroyWindow(hWnd: Pointer): Boolean

        fun IsWindow(hWnd: Pointer): Boolean

        fun ShowWindow(hWnd: Pointer, nCmdShow: Int): Boolean

        fun SetWindowPos(
            hWnd: Pointer,
            hWndInsertAfter: Pointer?,
            x: Int,
            y: Int,
            cx: Int,
            cy: Int,
            uFlags: Int,
        ): Boolean

        companion object {
            val INSTANCE: User32Ex = Native.load("user32", User32Ex::class.java)
        }
    }

    private interface Kernel32Ex : StdCallLibrary {
        fun GetModuleHandleW(lpModuleName: WString?): Pointer?

        companion object {
            val INSTANCE: Kernel32Ex = Native.load("kernel32", Kernel32Ex::class.java)
        }
    }

    companion object {
        private val hosts = ConcurrentHashMap<Long, VideoHostWindow>()

        fun acquire(parentHwnd: Long, taoWindow: TaoWindow?): VideoHostWindow {
            val existing = hosts[parentHwnd]
            if (existing != null && existing.isAlive()) return existing
            if (existing != null) hosts.remove(parentHwnd, existing)
            return hosts.computeIfAbsent(parentHwnd) { VideoHostWindow(it, taoWindow) }
        }

        fun saveExistingWindowedBounds(parentHwnd: Long) {
            hosts[parentHwnd]?.saveWindowedBounds()
        }

        private const val WS_POPUP = 0x80000000.toInt()
        private const val WS_CHILD = 0x40000000
        private const val WS_CLIPCHILDREN = 0x02000000
        private const val WS_CLIPSIBLINGS = 0x04000000
        private const val SW_HIDE = 0
        private const val SW_SHOW = 5
        private const val SWP_NOACTIVATE = 0x0010
    }
}
