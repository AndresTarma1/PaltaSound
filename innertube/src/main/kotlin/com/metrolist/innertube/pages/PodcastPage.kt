package com.metrolist.innertube.pages

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.MusicMultiRowListItemRenderer
import com.metrolist.innertube.models.MusicResponsiveListItemRenderer
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.Run
import com.metrolist.innertube.models.splitBySeparator
import com.metrolist.innertube.utils.parseTime

data class PodcastPage(
    val podcast: PodcastItem,
    val episodes: List<EpisodeItem>,
    val continuation: String?,
    val isChannelSubscribed: Boolean = false,
) {
    companion object {
        /**
         * Scans every •-separated group until one parses as a duration.
         * Podcast episodes don't always put the clock in the last group
         * (it can live in secondSubtitle/secondarySubtitle, or after a date).
         */
        private fun findDuration(vararg groupLists: List<List<Run>>?): Int? =
            groupLists.firstNotNullOfOrNull { groups ->
                groups?.firstNotNullOfOrNull { group ->
                    group.firstOrNull()?.text?.parseTime()
                }
            }

        fun fromMusicMultiRowListItemRenderer(
            renderer: MusicMultiRowListItemRenderer,
            podcast: PodcastItem? = null
        ): EpisodeItem? {
            val subtitleGroups = renderer.subtitle?.runs?.splitBySeparator()
            val secondSubtitleGroups = renderer.secondSubtitle?.runs?.splitBySeparator()
            val secondarySubtitleGroups = renderer.secondarySubtitle?.runs?.splitBySeparator()
            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

            return EpisodeItem(
                id = renderer.onTap?.watchEndpoint?.videoId ?: return null,
                title = renderer.title?.runs?.firstOrNull()?.text ?: return null,
                author = podcast?.author,
                podcast = podcast?.let {
                    Album(name = it.title, id = it.id)
                },
                duration = findDuration(subtitleGroups, secondSubtitleGroups, secondarySubtitleGroups),
                publishDateText = subtitleGroups?.firstOrNull()?.firstOrNull()?.text,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit = false,
                endpoint = renderer.onTap.watchEndpoint,
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
            )
        }

        fun fromMusicResponsiveListItemRenderer(
            renderer: MusicResponsiveListItemRenderer,
            podcast: PodcastItem? = null
        ): EpisodeItem? {
            val secondaryLineGroups = renderer.flexColumns
                .getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.splitBySeparator()
            val fixedDuration = renderer.fixedColumns?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.firstOrNull()
                ?.text
                ?.parseTime()
            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

            return EpisodeItem(
                id = renderer.videoId ?: return null,
                title = renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text
                    ?.runs?.firstOrNull()?.text ?: return null,
                author = podcast?.author ?: secondaryLineGroups?.firstOrNull()?.firstOrNull()?.let {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                },
                podcast = podcast?.let {
                    Album(name = it.title, id = it.id)
                },
                duration = fixedDuration ?: findDuration(secondaryLineGroups),
                publishDateText = secondaryLineGroups?.getOrNull(1)?.firstOrNull()?.text,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit = renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
            )
        }
    }
}
