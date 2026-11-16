package example.nucleus.utils

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.YTItem

/**
 * Returns true if the thumbnail URL corresponds to a wide (16:9) image,
 * as opposed to a square album/artist cover.
 */
fun YTItem.isWideThumbnail(): Boolean {
    return !(this is ArtistItem || this is AlbumItem) && thumbnail?.let { url ->
        url.contains("ytimg.com/vi/") ||
                url.contains("hqdefault") ||
                url.contains("mqdefault") ||
                url.contains("maxresdefault") ||
                url.contains("sddefault")
    } == true
}

fun isWideThumbnail(url: String?) : Boolean{
    return url?.let { url ->
        url.contains("ytimg.com/vi/") ||
        url.contains("hqdefault") ||
        url.contains("mqdefault") ||
        url.contains("maxresdefault") ||
        url.contains("sddefault")
    }  == true
}

/**
 * Returns the correct aspect ratio for a YTItem thumbnail.
 * - Artists and albums → 1:1
 * - Songs / playlists with video thumbnails → 16:9
 */
fun YTItem.thumbnailAspectRatio(): Float = if (isWideThumbnail()) 16f / 9f else 1f

/**
 * Resizes a YouTube thumbnail URL by appending/replacing width and height parameters.
 */
fun String?.resize(width: Int, height: Int): String? {
    if (this == null) return null
    return if (this.contains("=w") || this.contains("-w")) {
        this.replaceFirst(Regex("([=-])w\\d+"), "$1w$width")
            .replaceFirst(Regex("([=-])h\\d+"), "$1h$height")
    } else {
        "$this=w$width-h$height"
    }
}


/**
 * Upscales a YouTube thumbnail URL to a target size.
 * Handles `w226-h226`, `=w120-h120` and `=s120` formats.
 */
fun upscaleThumbnailUrl(url: String?, targetSize: Int): String? {
    if (url == null) return null
    return url
        .replace(Regex("w\\d+-h\\d+"), "w${targetSize}-h${targetSize}")
        .replace(Regex("=w\\d+-h\\d+"), "=w${targetSize}-h${targetSize}")
        .replace(Regex("=s\\d+"), "=s${targetSize}")
}
