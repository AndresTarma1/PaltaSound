package example.nucleus.ui.screens.shared

import com.metrolist.innertube.models.SongItem
import example.nucleus.ui.components.formatClockSeconds
import example.nucleus.ui.components.formatQueueDuration

fun calculateTotalDuration(songs: List<SongItem>): String {
    val totalSeconds = songs.sumOf { it.duration ?: 0 }
    return if (totalSeconds < 60) "< 1 min" else formatQueueDuration(totalSeconds.toLong())
}

fun formatDuration(seconds: Int): String = formatClockSeconds(seconds.toLong())

