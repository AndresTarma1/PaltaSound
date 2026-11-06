package example.nucleus.ui.components

/**
 * Formato de reloj compartido por reproductor, colas y YouTube: `m:ss` o
 * `h:mm:ss`. Los milisegundos negativos se tratan como cero.
 */
internal fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

internal fun formatClockSeconds(seconds: Long): String = formatClock(seconds * 1000L)

/** Duración de un vídeo: vacío si se desconoce (0 o negativo). */
internal fun formatVideoDuration(seconds: Long): String =
    if (seconds <= 0) "" else formatClockSeconds(seconds)

/** Variante del mini player: siempre minutos, sin horas. */
internal fun formatMiniTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/** Duración total de una cola: "1 h 2 min" / "2 min". */
internal fun formatQueueDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
