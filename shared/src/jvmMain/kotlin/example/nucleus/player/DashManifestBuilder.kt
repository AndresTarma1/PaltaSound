package example.nucleus.player

import example.youtube.model.YouTubeDashStream

/**
 * Builds one static DASH manifest for a separate YouTube video and audio stream.
 *
 * This is the mpv equivalent of Flow's `MergingMediaSource(true, true, video, audio)`:
 * both tracks share one Period/timeline, so the demuxer keeps A/V offsets and
 * seeks both representations together instead of attaching an external audio
 * track with `audio-add`.
 */
object DashManifestBuilder {
    fun build(
        video: YouTubeDashStream,
        audio: YouTubeDashStream,
        durationMs: Long,
    ): String {
        val duration = formatDuration(durationMs)
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" ")
            append("profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" ")
            append("type=\"static\" minBufferTime=\"PT1.5S\" mediaPresentationDuration=\"$duration\">")
            append("<Period id=\"0\" start=\"PT0S\">")
            appendAdaptationSet(video, isVideo = true)
            appendAdaptationSet(audio, isVideo = false)
            append("</Period></MPD>")
        }
    }

    private fun StringBuilder.appendAdaptationSet(
        stream: YouTubeDashStream,
        isVideo: Boolean,
    ) {
        val type = if (isVideo) "video" else "audio"
        append("<AdaptationSet id=\"$type\" contentType=\"$type\" ")
        append("mimeType=\"${xml(stream.mimeType)}\" ")
        append("segmentAlignment=\"true\" startWithSAP=\"1\">")
        append("<Representation id=\"$type\" bandwidth=\"${stream.bandwidth.coerceAtLeast(1)}\"")
        stream.codecs?.takeIf { it.isNotBlank() }?.let {
            append(" codecs=\"${xml(it)}\"")
        }
        if (isVideo) {
            stream.width?.takeIf { it > 0 }?.let { append(" width=\"$it\"") }
            stream.height?.takeIf { it > 0 }?.let { append(" height=\"$it\"") }
        } else {
            stream.audioSamplingRate?.takeIf { it > 0 }?.let {
                append(" audioSamplingRate=\"$it\"")
            }
        }
        append(">")
        if (!isVideo) {
            stream.audioChannels?.takeIf { it > 0 }?.let { channels ->
                append("<AudioChannelConfiguration ")
                append("schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" ")
                append("value=\"$channels\"/>")
            }
        }
        append("<BaseURL>${xml(stream.url)}</BaseURL>")
        append("<SegmentBase indexRange=\"${stream.indexStart}-${stream.indexEnd}\">")
        append("<Initialization range=\"${stream.initStart}-${stream.initEnd}\"/>")
        append("</SegmentBase>")
        append("</Representation></AdaptationSet>")
    }

    private fun formatDuration(ms: Long): String {
        val safe = ms.coerceAtLeast(1L)
        return "PT${safe / 1000}.${"%03d".format(safe % 1000)}S"
    }

    private fun xml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
