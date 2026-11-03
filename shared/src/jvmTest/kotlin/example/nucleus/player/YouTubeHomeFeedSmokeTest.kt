package example.nucleus.player

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Smoke test for the personalized YouTube home feed (FEwhat_to_watch). */
class YouTubeHomeFeedSmokeTest {
    @Test
    fun loadsHomeFeed() = runBlocking {
        val feed = assertNotNull(
            YouTubeHomeFeedService().feed(),
            "home feed returned null",
        )
        assertTrue(feed.items.isNotEmpty(), "home feed returned no videos")
        val subscriptions = YouTubeHomeFeedService().subscriptions()
        println("HOME subscriptions=${subscriptions.size}")
    }
}
