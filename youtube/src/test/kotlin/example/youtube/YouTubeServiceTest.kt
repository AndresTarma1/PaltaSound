package example.youtube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YouTubeServiceTest {

    @Test
    fun extractVideoIdFromWatchUrl() {
        assertEquals(
            "abc123",
            YouTubeService.videoIdFromUrl("https://www.youtube.com/watch?v=abc123"),
        )
        assertEquals(
            "abc 123",
            YouTubeService.videoIdFromUrl("https://www.youtube.com/watch?v=abc%20123"),
        )
    }

    @Test
    fun extractVideoIdFromShortUrl() {
        assertEquals(
            "abc123",
            YouTubeService.videoIdFromUrl("https://youtu.be/abc123"),
        )
        assertEquals(
            "abc123",
            YouTubeService.videoIdFromUrl("https://youtu.be/abc123?t=10"),
        )
    }

    @Test
    fun rejectInputWithoutVideoId() {
        assertNull(YouTubeService.videoIdFromUrl("not a url"))
        assertNull(YouTubeService.videoIdFromUrl("https://www.youtube.com/"))
    }
}
