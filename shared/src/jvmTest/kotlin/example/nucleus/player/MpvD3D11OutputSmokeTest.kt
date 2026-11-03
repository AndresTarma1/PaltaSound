package example.nucleus.player

import example.nucleus.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke test for the D3D11 shared texture used by TextureView. It only
 * exercises the native resource creation; no window or mpv instance needed.
 */
class MpvD3D11OutputSmokeTest {
    @Test
    fun createsSharedTextureWithKeyedMutex() {
        if (!Platform.isWindows) return
        val output = assertNotNull(MpvD3D11Output.create(64, 64), "D3D11 texture creation failed")
        try {
            assertTrue(output.sharedHandle != 0L, "shared handle is 0")
            assertTrue(output.acquire(1_000), "keyed mutex acquire failed")
            output.release()
            println("D3D11 shared texture handle=0x${output.sharedHandle.toString(16)}")
        } finally {
            output.close()
        }
    }

    @Test
    fun createsMpvD3D11RenderContext() {
        if (!Platform.isWindows || !MpvLib.isAvailable || !MpvLib.renderApiAvailable) return
        val output = assertNotNull(MpvD3D11Output.create(64, 64), "D3D11 texture creation failed")
        val handle = assertNotNull(MpvLib.mpv_create(), "mpv_create returned null")
        try {
            MpvLib.mpv_set_option_string(handle, "vo", "libmpv")
            MpvLib.mpv_set_option_string(handle, "hwdec", "no")
            assertEquals(0, MpvLib.mpv_initialize(handle), "mpv_initialize failed")
            val renderContext = assertNotNull(
                MpvLib.mpv_render_context_create(handle, output.device),
                "mpv_render_context_create(D3D11) returned null",
            )
            MpvLib.mpv_render_context_free(renderContext)
        } finally {
            MpvLib.mpv_terminate_destroy(handle)
            output.close()
        }
    }
}
