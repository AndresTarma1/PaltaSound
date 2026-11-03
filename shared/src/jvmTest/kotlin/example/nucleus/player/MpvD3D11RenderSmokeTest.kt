package example.nucleus.player

import example.nucleus.platform.Platform
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end smoke test for the mpv D3D11 render API: decode a small remote
 * clip and render one frame into the shared texture that TextureView samples.
 */
class MpvD3D11RenderSmokeTest {
    private val sampleUrl =
        "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"

    @Test
    fun rendersOneFrameIntoSharedTexture() {
        if (!Platform.isWindows || !MpvLib.isAvailable || !MpvLib.renderApiAvailable) return
        val output = assertNotNull(MpvD3D11Output.create(640, 360), "D3D11 texture creation failed")
        val handle = assertNotNull(MpvLib.mpv_create(), "mpv_create returned null")
        var renderContext: MemorySegment? = null
        try {
            MpvLib.mpv_set_option_string(handle, "vo", "libmpv")
            MpvLib.mpv_set_option_string(handle, "hwdec", "auto-safe")
            MpvLib.mpv_set_option_string(handle, "ao", "null")
            MpvLib.mpv_set_option_string(handle, "pause", "no")
            MpvLib.mpv_set_option_string(handle, "video-timing-offset", "0")
            MpvLib.mpv_set_option_string(
                handle,
                "user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            )
            kotlin.test.assertEquals(0, MpvLib.mpv_initialize(handle), "mpv_initialize failed")

            val ctx = assertNotNull(
                MpvLib.mpv_render_context_create(handle, output.device),
                "mpv_render_context_create(D3D11) returned null",
            )
            renderContext = ctx

            MpvLib.mpv_command(handle, arrayOf("loadfile", sampleUrl, "replace"))
            MpvLib.mpv_request_log_messages(handle, "info")

            val deadline = System.nanoTime() + 20.seconds.inWholeNanoseconds
            var frames = 0
            var flagsSeen = 0L
            while (System.nanoTime() < deadline && frames < 60) {
                val event = MpvLib.mpv_wait_event(handle, 0.05)
                if (event != null) {
                    val id = event.reinterpret(64).get(ValueLayout.JAVA_INT, 0L)
                    if (id == 8 || id == 21) {
                        // Skip the black title card so the sampled pixels are
                        // actual picture content.
                        MpvLib.mpv_command(handle, arrayOf("seek", "5", "absolute+exact"))
                    }
                    if (id == 2) {
                        val data = event.reinterpret(64).get(ValueLayout.ADDRESS, 16L)
                        if (data.address() != 0L) {
                            fun str(off: Long): String {
                                val p = data.reinterpret(32).get(ValueLayout.ADDRESS, off)
                                return if (p.address() == 0L) "" else p.reinterpret(4096).getString(0)
                            }
                            println("mpv log ${str(8)} ${str(16)}")
                        }
                    }
                    if (id == MpvLib.MPV_EVENT_END_FILE) {
                        val data = event.reinterpret(64).get(ValueLayout.ADDRESS, 16L)
                        val reason = if (data.address() != 0L) {
                            data.reinterpret(16).get(ValueLayout.JAVA_INT, 0L)
                        } else -1
                        println("mpv end-file reason=$reason")
                        break
                    }
                }
                val flags = MpvLib.mpv_render_context_update(ctx)
                flagsSeen = flagsSeen or flags
                if ((flags and MpvLib.MPV_RENDER_UPDATE_FRAME) == 0L) continue
                assertTrue(output.acquire(2_000), "keyed mutex acquire failed")
                val rc = try {
                    MpvLib.mpv_render_context_render(ctx, output.texture)
                } finally {
                    output.release()
                }
                if (rc >= 0) {
                    MpvLib.mpv_render_context_report_swap(ctx)
                    frames++
                }
            }
            assertTrue(frames > 0, "mpv did not render a frame within 20 s (flags=$flagsSeen)")
            val samples = listOf(80 to 80, 320 to 180, 560 to 300)
            assertTrue(output.acquire(2_000), "keyed mutex acquire failed for samples")
            try {
                println(
                    "rendered $frames frames, samples=" + samples.joinToString { (x, y) ->
                        val pixel = output.readPixelArgb(x, y)
                        "($x,$y)=" + (pixel?.let { "0x${it.toUInt().toString(16)}" } ?: "null")
                    },
                )
                val center = output.readPixelArgb(output.width / 2, output.height / 2)
                assertTrue(center != null && center != 0, "rendered texture is empty")
                assertTrue(((center ushr 24) and 0xff) != 0, "rendered texture is transparent")
            } finally {
                output.release()
            }
        } finally {
            renderContext?.let { MpvLib.mpv_render_context_free(it) }
            MpvLib.mpv_terminate_destroy(handle)
            output.close()
        }
    }
}
