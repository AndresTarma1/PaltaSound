package example.nucleus.ui.utils

import com.metrolist.innertube.models.YTItem

import example.nucleus.utils.isWideThumbnail as sharedIsWideThumbnail
import example.nucleus.utils.thumbnailAspectRatio as sharedThumbnailAspectRatio

/**
 * Returns true if the thumbnail URL corresponds to a wide (16:9) image,
 * as opposed to a square album/artist cover.
 */
@Deprecated(
    message = "Usa example.nucleus.utils.isWideThumbnail",
    replaceWith = ReplaceWith("this.isWideThumbnail()", "example.nucleus.utils.isWideThumbnail")
)
fun YTItem.isWideThumbnail(): Boolean = this.sharedIsWideThumbnail()

/**
 * Returns the correct aspect ratio for a YTItem thumbnail.
 * - Artists and albums → 1:1
 * - Songs / playlists with video thumbnails → 16:9
 */
@Deprecated(
    message = "Usa example.nucleus.utils.thumbnailAspectRatio",
    replaceWith = ReplaceWith("this.thumbnailAspectRatio()", "example.nucleus.utils.thumbnailAspectRatio")
)
fun YTItem.thumbnailAspectRatio(): Float = this.sharedThumbnailAspectRatio()

