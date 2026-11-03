package example.nucleus.player

import example.nucleus.platform.Platform
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration smoke test for the production [MpvVideoPlayer] texture pipeline:
 * creates the D3D11 render context, opens a remote clip and waits for the
 * render loop to publish at least one frame.
 */
class MpvTexturePipelineSmokeTest {
    private val sampleUrl =
        "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"

    @Test
    fun playerPublishesTextureAndFrames() {
        if (!Platform.isWindows || !MpvLib.isAvailable || !MpvLib.renderApiAvailable) return
        val player = MpvVideoPlayer()
        try {
            assertTrue(player.tryInitTexture(), "texture pipeline did not initialize")
            assertNotNull(player.textureInfo.value, "texture info is null")
            val firstFrame = CountDownLatch(1)
            val registration = player.addFrameListener { firstFrame.countDown() }
            try {
                player.load(sampleUrl)
                assertTrue(
                    firstFrame.await(20, TimeUnit.SECONDS),
                    "no frame reached the TextureView listener",
                )
            } finally {
                registration.close()
            }
        } finally {
            player.dispose()
        }
    }
}
