@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.ctaLabel
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import kotlin.math.abs

internal fun formatSpeed(speed: Float): String = String.format(Locale.US, "%.1fx", speed)

/**
 * Acciones superiores estilo Apple Music / media player: iconos fantasma sin chip opaco.
 * Menú ⋮ incluye equalizer + offset de letras **por canción**.
 */
@Composable
fun NowPlayingTopActions(
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val speed by playerViewModel.playbackSpeed.collectAsState(1f)
    val songOffsetMs by playerViewModel.currentSongLyricsOffsetMs.collectAsState()
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
    val isDefaultSpeed = abs(speed - 1f) < 0.01f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box {
            TextButton(
                onClick = { showSpeedMenu = true },
                modifier = Modifier
                    .height(40.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isDefaultSpeed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Icon(
                    Icons.Rounded.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    formatSpeed(speed),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
            DropdownMenu(
                expanded = showSpeedMenu,
                onDismissRequest = { showSpeedMenu = false },
                shape = AppShapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
            ) {
                speeds.forEach { s ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                formatSpeed(s),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                            )
                        },
                        onClick = {
                            playerViewModel.setPlaybackSpeed(s)
                            showSpeedMenu = false
                        },
                        trailingIcon = {
                            if (abs(s - speed) < 0.01f) {
                                Icon(
                                    Icons.Rounded.Check,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }
            }
        }

        Box {
            IconButton(
                onClick = { onMenuToggle(true) },
                modifier = Modifier
                    .size(40.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(Res.string.more_options),
                    tint = if (songOffsetMs != 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onMenuToggle(false) },
                shape = AppShapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.equalizer_menu),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    },
                    onClick = {
                        onMenuToggle(false)
                        onOpenEqualizer()
                    },
                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.lyrics_offset_song),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {},
                    enabled = false,
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Timer,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        Text(
                            stringResource(Res.string.lyrics_offset_value, songOffsetMs),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = if (songOffsetMs != 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.lyrics_offset_earlier),
                            style = MaterialTheme.typography.ctaLabel,
                        )
                    },
                    onClick = { playerViewModel.nudgeCurrentSongLyricsOffset(-500) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.lyrics_offset_later),
                            style = MaterialTheme.typography.ctaLabel,
                        )
                    },
                    onClick = { playerViewModel.nudgeCurrentSongLyricsOffset(500) },
                )
                if (songOffsetMs != 0) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(Res.string.lyrics_offset_reset),
                                style = MaterialTheme.typography.ctaLabel,
                            )
                        },
                        onClick = { playerViewModel.resetCurrentSongLyricsOffset() },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.RestartAlt,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Compat: overlay absoluto legacy (ya no se usa en el layout principal). */
@Composable
fun BoxScope.TopActionOverlay(
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NowPlayingTopActions(
        showMenu = showMenu,
        onMenuToggle = onMenuToggle,
        onOpenEqualizer = onOpenEqualizer,
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(top = 8.dp, end = 12.dp),
    )
}
