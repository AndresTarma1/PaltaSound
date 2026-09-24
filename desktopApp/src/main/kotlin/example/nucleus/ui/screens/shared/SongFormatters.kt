package example.nucleus.ui.screens.shared

import com.metrolist.innertube.models.SongItem

fun calculateTotalDuration(songs: List<SongItem>): String {
    val totalSeconds = songs.sumOf { it.duration ?: 0 }
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours} h ${minutes} min"
        minutes > 0 -> "${minutes} min"
        else -> "< 1 min"
    }
}
fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
    }
}

