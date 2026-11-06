package example.nucleus.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenConfigTest {

    @Test
    fun podcastBrowseIdsOpenThePlaylistScreen() {
        assertEquals(
            ScreenConfig.Playlist("MPSP123"),
            ScreenConfig.collection("MPSP123"),
        )
    }

    @Test
    fun otherBrowseIdsOpenTheAlbumScreen() {
        assertEquals(
            ScreenConfig.Album("MPREb_123"),
            ScreenConfig.collection("MPREb_123"),
        )
    }
}
