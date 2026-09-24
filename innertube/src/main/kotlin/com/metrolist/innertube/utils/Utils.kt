package com.metrolist.innertube.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.LibraryPage
import com.metrolist.innertube.pages.PlaylistPage
import java.security.MessageDigest

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
    val page = getOrThrow()
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0
    
    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++
        
        val continuationPage = YouTube.playlistContinuation(continuation).getOrNull() ?: break
        
        if (continuationPage.songs.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            songs += continuationPage.songs
        }
        
        continuation = continuationPage.continuation
    }
    PlaylistPage(
        playlist = page.playlist,
        songs = songs,
        songsContinuation = null,
        continuation = page.continuation
    )
}

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val items = page.items.toMutableList()
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0
    
    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++
        
        val continuationPage = YouTube.libraryContinuation(continuation).getOrNull() ?: break
        
        if (continuationPage.items.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            items += continuationPage.items
        }
        
        continuation = continuationPage.continuation
    }
    LibraryPage(
        items = items,
        continuation = null
    )
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null
            else part.substring(0, splitIndex) to part.substring(splitIndex + 1)
        }
        .toMap()

fun String.parseTime(): Int? {
    val text = trim()
    if (text.isEmpty()) return null

    // Human formats YTM uses for podcasts / long content: "45 min", "1 h 30 min", "1,5 h"
    parseHumanDuration(text)?.let { return it }

    try {
        // YouTube Music returns duration with locale-dependent separators
        // (":" en-US, "." some locales, "," EU). Accept all.
        val parts = text.split(Regex("[:.,]")).map { it.trim().toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (_: Exception) {
        // not a clock-style duration — fall through
    }
    return null
}

private fun parseHumanDuration(text: String): Int? {
    // "1 h 30 min" / "1 hr 20 minutes" / "2 horas 15 min"
    val hoursPattern = Regex(
        """(?i)(\d+(?:[.,]\d+)?)\s*(?:h|hr|hrs|hour|hours|hora|horas)\s*(?:(\d+)\s*(?:m|min|mins|minute|minutes|minuto|minutos))?"""
    )
    hoursPattern.find(text)?.let { match ->
        val hours = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        return (hours * 3600.0).toInt() + minutes * 60
    }

    // "45 min" / "90 minutes" / "45 minutos"
    val minutesPattern = Regex(
        """(?i)(\d+(?:[.,]\d+)?)\s*(?:m|min|mins|minute|minutes|minuto|minutos)\b"""
    )
    minutesPattern.find(text)?.let { match ->
        val minutes = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let
        return (minutes * 60.0).toInt()
    }

    return null
}

fun isPrivateId(browseId: String): Boolean {
    return browseId.contains("privately")
}
