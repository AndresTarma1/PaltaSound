package example.nucleus.ui.screens.shared

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.play_item
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.ctaLabel
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.mediaItemTitle
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.utils.LocalAnimationsEnabled
import org.jetbrains.compose.resources.stringResource

/**
 * Side for collection hero icon buttons (like / download / shuffle).
 * Sized to fit the narrow playlist/album side column (~280–320 dp) without clipping.
 */
val CollectionHeroActionSize: Dp = 48.dp

/** Ancho compartido de la columna hero en layout wide (playlist / álbum). */
val CollectionWideHeroWidth: Dp = 300.dp

/** Cover compartido en layout wide. */
val CollectionWideCoverSize: Dp = 240.dp

/** Cover compartido en layout compacto. */
val CollectionCompactCoverSize: Dp = 190.dp

val CollectionWidePaddingStart: Dp = 48.dp
val CollectionWidePaddingTop: Dp = 32.dp
val CollectionWidePaddingBottom: Dp = 16.dp
val CollectionWideGap: Dp = 40.dp

private val CollectionHeroIconSize: Dp = 22.dp
private val CollectionHeroPlayHeight: Dp = 52.dp
private val CollectionHeroPlayMinWidth: Dp = 100.dp
private val CollectionHeroActionGap: Dp = 8.dp

/**
 * Shared album/playlist hero actions — same order and sizes:
 * like · download · play (EFAB) · shuffle.
 *
 * Download sits before play so it stays visible in the narrow side column
 * (previously the trailing control was clipped past ~280 dp).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionHeroActionRow(
    isSaved: Boolean,
    isSaving: Boolean,
    onToggleSave: () -> Unit,
    isLoadingForPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isDownloading: Boolean,
    isFullyDownloaded: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSave: Boolean = true,
    showDownload: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CollectionHeroActionGap, Alignment.CenterHorizontally),
    ) {
        if (showSave) {
            CollectionHeroIconButton(
                onClick = { if (!isSaving) onToggleSave() },
                enabled = !isSaving,
                active = isSaved,
                isLoading = isSaving,
                icon = if (isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            )
        }

        if (showDownload) {
            CollectionHeroIconButton(
                onClick = onDownloadClick,
                // Stay clickable when fully downloaded (delete downloads).
                enabled = !isDownloading || isFullyDownloaded,
                active = isFullyDownloaded,
                isLoading = isDownloading && !isFullyDownloaded,
                icon = if (isFullyDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                useWavyProgress = true,
            )
        }

        CollectionHeroPlayButton(
            isLoading = isLoadingForPlay,
            onClick = onPlay,
        )

        CollectionHeroIconButton(
            onClick = { if (!isLoadingForPlay) onShuffle() },
            enabled = !isLoadingForPlay,
            active = false,
            isLoading = false,
            icon = Icons.Default.Shuffle,
        )
    }
}

@Composable
fun CollectionHeroPlayButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = { if (!isLoading) onClick() },
        shape = AppShapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(2.dp, 4.dp),
        modifier = modifier
            .height(CollectionHeroPlayHeight)
            .defaultMinSize(minWidth = CollectionHeroPlayMinWidth, minHeight = CollectionHeroPlayHeight)
            .pointerHoverIcon(if (isLoading) PointerIcon.Default else PointerIcon.Hand),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(Res.string.play_item),
                style = MaterialTheme.typography.ctaLabel,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionHeroIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    active: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useWavyProgress: Boolean = false,
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled || isLoading,
        modifier = modifier
            .size(CollectionHeroActionSize)
            .pointerHoverIcon(
                if (enabled && !isLoading) PointerIcon.Hand else PointerIcon.Default,
            ),
        shape = circleAwareShape(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
    ) {
        val animationsEnabled = LocalAnimationsEnabled.current
        Crossfade(
            targetState = isLoading,
            animationSpec = if (animationsEnabled) expressiveFadeTween() else snap(),
            label = "collection_hero_icon",
        ) { loading ->
            if (loading) {
                if (useWavyProgress) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(CollectionHeroIconSize),
                )
            }
        }
    }
}

/**
 * Compact sticky chrome for playlist/album when the hero scrolls away.
 * Tonal download + primary play — same language as [CollectionHeroActionRow].
 */
@Composable
fun CollectionStickyHeaderBar(
    title: String,
    isDownloading: Boolean,
    isFullyDownloaded: Boolean,
    onDownloadClick: () -> Unit,
    onPlayClick: () -> Unit,
    playContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.mediaItemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            CollectionHeroIconButton(
                onClick = onDownloadClick,
                enabled = !isDownloading || isFullyDownloaded,
                active = isFullyDownloaded,
                isLoading = isDownloading && !isFullyDownloaded,
                icon = if (isFullyDownloaded) Icons.Default.Delete else Icons.Default.Download,
                useWavyProgress = true,
            )

            FilledTonalIconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(CollectionHeroActionSize)
                    .pointerHoverIcon(PointerIcon.Hand),
                shape = circleAwareShape(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = playContentDescription,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
