package example.nucleus.ui.components.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.lyrics_fetch_error
import example.nucleus.generated.resources.lyrics_fetch_error_subtitle
import example.nucleus.generated.resources.lyrics_loading
import example.nucleus.generated.resources.lyrics_not_found
import example.nucleus.generated.resources.lyrics_not_found_retry
import example.nucleus.generated.resources.lyrics_retry
import example.nucleus.ui.components.ExpressiveEmptyState
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalUserPreferences
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsContent(
    lyrics: String?,
    textAlign: TextAlign = TextAlign.Start,
    style: TextStyle,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val userPreferences = LocalUserPreferences.current
    val synced by playerViewModel.syncedLyrics.collectAsState()
    val progress by playerViewModel.progressState.collectAsState()
    val loading by playerViewModel.lyricsLoading.collectAsState()
    val fetchFailed by playerViewModel.lyricsFetchFailed.collectAsState()
    // Global (Settings) + por canción (menú ⋮ Now Playing / DB).
    val globalOffsetMs by userPreferences.lyricsOffsetMs.collectAsState(0)
    val songOffsetMs by playerViewModel.currentSongLyricsOffsetMs.collectAsState()
    val lyricsOffsetMs = globalOffsetMs + songOffsetMs
    val syncedLines = synced

    val uiState by playerViewModel.uiState.collectAsState()
    val currentSongId = uiState.currentSong?.id
    // Lazy fetch: descarga al abrir la pestaña y al cambiar de cancion mientras estas en ella
    LaunchedEffect(currentSongId) {
        if (currentSongId != null) {
            // fetchLyrics ya hace early-return si es la misma cancion y ya hay cache/loading
            playerViewModel.fetchLyrics()
        }
    }

    val baseSize = if (style.fontSize == TextUnit.Unspecified) 20.sp else style.fontSize
    val baseLineHeight = if (style.lineHeight == TextUnit.Unspecified) 32.sp else style.lineHeight
    val immersiveLyricsStyle = style.copy(
        fontSize = baseSize * 1.6f,
        lineHeight = baseLineHeight * 1.8f,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )

    // Positivo = letras más tarde → restamos offset a la posición del audio.
    val adjustedPositionMs = (progress.positionMs - lyricsOffsetMs).coerceAtLeast(0L)

    when {
        !syncedLines.isNullOrEmpty() -> {
            SyncedLyricsView(
                lines = syncedLines,
                positionMs = adjustedPositionMs,
                onSeek = { lineTimeMs ->
                    playerViewModel.seekTo((lineTimeMs + lyricsOffsetMs).coerceAtLeast(0L))
                },
                modifier = Modifier.fillMaxSize(),
                textAlign = textAlign == TextAlign.Start,
            )
        }
        loading -> {
            LyricsLoadingState()
        }
        fetchFailed -> {
            ExpressiveEmptyState(
                icon = Icons.Rounded.CloudOff,
                title = stringResource(Res.string.lyrics_fetch_error),
                subtitle = stringResource(Res.string.lyrics_fetch_error_subtitle),
                actionLabel = stringResource(Res.string.lyrics_retry),
                onAction = { playerViewModel.retryFetchLyrics() },
            )
        }
        lyrics.isNullOrBlank() -> {
            ExpressiveEmptyState(
                icon = Icons.Rounded.Lyrics,
                title = stringResource(Res.string.lyrics_not_found),
                actionLabel = stringResource(Res.string.lyrics_not_found_retry),
                onAction = { playerViewModel.retryFetchLyrics() },
            )
        }
        else -> {
            val scrollState = rememberScrollState()

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .lyricsFadingEdges(topFade = 48.dp, bottomFade = 80.dp)
                        .verticalScroll(scrollState)
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = 32.dp,
                            bottom = 120.dp,
                        )
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 920.dp)
                            .align(if (textAlign == TextAlign.Start) Alignment.Start else Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            text = lyrics,
                            modifier = Modifier.padding(24.dp),
                            style = immersiveLyricsStyle,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f),
                            textAlign = textAlign,
                        )
                    }
                }
                AppVerticalScrollbar(
                    state = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(6.dp)
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun LyricsLoadingState() {
    val animationsEnabled = LocalAnimationsEnabled.current
    val infiniteTransition = rememberInfiniteTransition(label = "lyrics_loading_wave")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loading_alpha",
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loading_scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.size(68.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            if (animationsEnabled) {
                                this.alpha = alpha
                                this.scaleX = scale
                                this.scaleY = scale
                            }
                        },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(Res.string.lyrics_loading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
    }
}
