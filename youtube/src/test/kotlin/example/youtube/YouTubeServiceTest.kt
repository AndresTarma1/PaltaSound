package example.youtube

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

class YouTubeServiceTest {
    @Test
    fun searchAndVideoSmoke() = runBlocking {
        val service = YouTubeService()
        val page = service.search("lofi hip hop")
        assertTrue(page.items.isNotEmpty(), "search returned no items")
        val firstVideo = page.items.filterIsInstance<example.youtube.model.YouTubeSearchItem.Video>().firstOrNull()
        assertTrue(firstVideo != null, "search returned no videos")
        val details = service.video(firstVideo!!.video.id)
        assertTrue(details.video.title.isNotBlank(), "video title is blank")
        println("YouTube smoke: ${details.video.title} / ${details.video.channelName}")
    }
}
