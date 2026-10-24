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
 * Frame del vídeo en bruto: la única miniatura de YouTube que llega en 16:9 **sin barras negras**.
 *
 * POR QUÉ HACE FALTA
 * ------------------
 * La miniatura que da la API de YouTube Music para una canción normal es un **cuadrado**
 * (`yt3.googleusercontent.com/...=w120-h120-l90-rj`, 120x120). Cuando el vídeo es 16:9, ese
 * cuadrado lleva el frame **encajado con barras negras grabadas en el JPEG**: no son un letterbox
 * de Compose, son píxeles de la imagen. Por eso `ContentScale.Crop` no las quita, y el cover se ve
 * como un cuadro negro con una foto en medio y el recorte ya hecho por el servidor.
 *
 * `maxresdefault.jpg` (1280x720) y `mqdefault.jpg` (320x180) son las dos que dan el frame entero,
 * sin barras. Se necesitan las dos porque no siempre existe la primera:
 *
 *  - `maxresdefault` (1280x720) solo está si el uploader subió un thumbnail de 720p o más, así que
 *    **devuelve 404 en bastantes vídeos**. Por eso `CoverArt` cae a `mqdefault` si esta falla.
 *  - `mqdefault` (320x180) está en todos los vídeos, pero es más pequeña: red de seguridad, no
 *    primera opción.
 *
 * El recorte al cuadrado lo hace luego `ContentScale.Crop`, que sobre un 16:9 limpio recorta los
 * lados y llena el marco entero. Se pierden los lados del frame, no la mitad de la imagen.
 */
fun videoThumbnailUrl(videoId: String?, maxRes: Boolean = true): String? {
    if (videoId.isNullOrBlank()) return null
    val name = if (maxRes) "maxresdefault" else "mqdefault"
    return "https://i.ytimg.com/vi/$videoId/$name.jpg"
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
