package example.nucleus.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import dev.nucleusframework.window.tao.TextureView
import dev.nucleusframework.window.tao.nucleusD3D11SharedTextureSource
import dev.nucleusframework.window.tao.rememberTextureViewController
import example.nucleus.platform.Platform
import example.nucleus.player.YouTubePlayerService

/**
 * YouTube video surface. Prefers the GPU texture path (mpv D3D11 render API
 * -> shared texture -> TextureView) and falls back to the legacy child HWND
 * when D3D11 or the render API is unavailable.
 *
 * The TextureView path has no native child window: controls, rounded corners,
 * clipping and the mini player are ordinary Compose content.
 */
@Composable
fun YouTubeVideoSurface(
    playerService: YouTubePlayerService,
    visible: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val texture by playerService.textureInfo.collectAsState()
    val fallback by playerService.textureFallback.collectAsState()
    val info = texture
    if (Platform.isWindows && info != null) {
        val controller = rememberTextureViewController()
        val source = remember(info.sharedHandle, info.width, info.height) {
            nucleusD3D11SharedTextureSource(info.sharedHandle, info.width, info.height)
        }
        DisposableEffect(playerService, controller) {
            val registration = playerService.addFrameListener {
                controller.markFrameAvailable()
            }
            onDispose { registration.close() }
        }
        TextureView(
            source = source,
            modifier = modifier,
            controller = controller,
            filterQuality = FilterQuality.Low,
            contentScale = contentScale,
        )
    } else if (Platform.isWindows && !fallback) {
        // The texture is still being prepared. Keep a black box until the
        // service decides between TextureView and the HWND fallback.
        Box(modifier.background(Color.Black))
    } else {
        NativeVideoSurface(
            playerService = playerService,
            visible = visible,
            modifier = modifier,
        )
    }
}
