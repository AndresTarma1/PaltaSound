@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.db.DatabaseDao
import example.nucleus.db.entities.TopAlbumEntry
import example.nucleus.db.entities.TopArtistEntry
import example.nucleus.db.entities.TopSongEntry
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Periodo de las estadísticas (en ms), como Metrolist (1s/1m/3m/6m/1y/all). */
private enum class StatsPeriod(val label: String, val windowMs: Long?) {
    WEEK("1S", 7L * 24 * 3600 * 1000),
    MONTH("1M", 30L * 24 * 3600 * 1000),
    THREE_MONTHS("3M", 90L * 24 * 3600 * 1000),
    SIX_MONTHS("6M", 180L * 24 * 3600 * 1000),
    YEAR("1Y", 365L * 24 * 3600 * 1000),
    ALL("TODAS", null),
}

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val databaseDao = koinInject<DatabaseDao>()
    var period by remember { mutableStateOf(StatsPeriod.ALL) }

    var totalTime by remember { mutableStateOf(0L) }
    var uniqueSongs by remember { mutableStateOf(0L) }
    var topSongs by remember { mutableStateOf<List<TopSongEntry>>(emptyList()) }
    var topAlbums by remember { mutableStateOf<List<TopAlbumEntry>>(emptyList()) }
    var topArtists by remember { mutableStateOf<List<TopArtistEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(period) {
        isLoading = true
        val now = System.currentTimeMillis()
        val from = period.windowMs?.let { now - it } ?: 0L
        totalTime = databaseDao.totalPlayTimeInRange(from, now)
        uniqueSongs = databaseDao.uniqueSongCountInRange(from, now)
        topSongs = databaseDao.topSongs(10)
        topAlbums = databaseDao.topAlbums(10)
        topArtists = databaseDao.topArtists(10)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.stats_title),
                        style = MaterialTheme.typography.displaySmallEmphasized,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                actions = {
                    StatsPeriod.entries.forEach { p ->
                        FilterChip(
                            selected = p == period,
                            onClick = { period = p },
                            label = { Text(p.label, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            StatCard(
                                label = stringResource(Res.string.stats_time_listened),
                                value = formatPlaybackTime(totalTime),
                                modifier = Modifier.weight(1f),
                            )
                            StatCard(
                                label = stringResource(Res.string.stats_unique_songs),
                                value = "$uniqueSongs",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    item { StatsHeader(stringResource(Res.string.stats_top_songs)) }
                    items(topSongs, key = { it.id }) { song ->
                        StatsSongRow(song)
                    }

                    item { StatsHeader(stringResource(Res.string.stats_top_albums)) }
                    items(topAlbums, key = { it.albumName }) { album ->
                        StatsAlbumRow(album)
                    }

                    item { StatsHeader(stringResource(Res.string.stats_top_artists)) }
                    items(topArtists, key = { it.id }) { artist ->
                        StatsArtistRow(artist)
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMediumEmphasized,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StatsSongRow(song: TopSongEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MusicPlayerImage(
            url = song.thumbnailUrl,
            contentDescription = song.title,
            modifier = Modifier.size(44.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            placeholderType = PlaceholderType.SONG,
            iconSize = 18.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "x${song.playCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StatsAlbumRow(album: TopAlbumEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = album.albumName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "x${album.playCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StatsArtistRow(artist: TopArtistEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MusicPlayerImage(
            url = artist.thumbnailUrl,
            contentDescription = artist.name,
            modifier = Modifier.size(44.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            placeholderType = PlaceholderType.ARTIST,
            iconSize = 18.dp,
        )
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "x${artist.playCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** "3h 12m" / "45m" / "25s" a partir de milisegundos. */
private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}
