package example.nucleus.player

import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Headless smoke test for the SABR video MVP: download video+audio, play them
 * locally with mpv (`audio-add`) and verify seek + A/V clock sync.
 *
 * It uses the visionOS SABR client, which has no `n` parameter and therefore
 * does not need QuickJS. Requires network and the bundled botguard sidecar.
 */
class YouTubeSabrVideoSmokeTest {
    @Test
    fun downloadsAndSeeks() = runBlocking {
        val files = assertNotNull(
            YouTubeSabrResolver.download("dQw4w9WgXcQ"),
            "SABR download returned null",
        )
        assertTrue(files.videoFile.length() > 0L, "empty video file")
        assertTrue(files.audioFile.length() > 0L, "empty audio file")

        val handle = MpvLib.mpv_create()
        assertNotNull(handle, "mpv_create returned null")
        try {
            MpvLib.mpv_set_option_string(handle, "vo", "null")
            MpvLib.mpv_set_option_string(handle, "ao", "null")
            MpvLib.mpv_set_option_string(handle, "ytdl", "no")
            MpvLib.mpv_set_option_string(handle, "osc", "no")
            MpvLib.mpv_set_option_string(handle, "input-default-bindings", "no")
            assertEquals(0, MpvLib.mpv_initialize(handle), "mpv_initialize failed")
            MpvLib.mpv_command(
                handle,
                arrayOf("loadfile", files.videoFile.absolutePath, "replace", null),
            )
            waitUntil(handle, 15_000) { property(handle, "core-idle") == "no" }
            assertEquals(
                0,
                MpvLib.mpv_command(
                    handle,
                    arrayOf("audio-add", files.audioFile.absolutePath, "select"),
                ),
                "audio-add failed",
            )
            assertTrue(
                waitUntil(handle, 20_000) { position(handle) > 2.0 },
                "playback did not start",
            )

            MpvLib.mpv_command(handle, arrayOf("seek", "120", "absolute+exact"))
            assertTrue(
                waitUntil(handle, 30_000) { position(handle) > 121.0 },
                "seek to 120 s did not advance",
            )
            val pos = position(handle)
            val audioPts = property(handle, "audio-pts")?.toDoubleOrNull()
            assertNotNull(audioPts, "audio-pts is null after seek")
            assertTrue(
                abs(pos - audioPts) < 1.0,
                "A/V drift after seek: pos=$pos audioPts=$audioPts",
            )
        } finally {
            runCatching { MpvLib.mpv_terminate_destroy(handle) }
            files.delete()
        }
    }

    private fun position(handle: MemorySegment): Double =
        property(handle, "time-pos")?.toDoubleOrNull() ?: 0.0

    private fun waitUntil(
        handle: MemorySegment,
        timeoutMs: Long,
        predicate: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val event = MpvLib.mpv_wait_event(handle, 0.25) ?: continue
            val ev = event.reinterpret(64)
            if (ev.get(ValueLayout.JAVA_INT, 0L) == MpvLib.MPV_EVENT_END_FILE) {
                val data = ev.get(ValueLayout.ADDRESS, 16L)
                val reason = if (data.address() != 0L) {
                    data.reinterpret(16).get(ValueLayout.JAVA_INT, 0L)
                } else {
                    -1
                }
                if (reason == MpvLib.MPV_END_FILE_REASON_ERROR) return false
            }
            if (predicate()) return true
        }
        return false
    }

    private fun property(handle: MemorySegment, name: String): String? {
        val ptr = MpvLib.mpv_get_property_string(handle, name) ?: return null
        return try {
            ptr.reinterpret(4096).getString(0)
        } finally {
            MpvLib.mpv_free(ptr)
        }
    }
}
