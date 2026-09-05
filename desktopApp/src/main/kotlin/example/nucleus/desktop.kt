package example.nucleus

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.rememberTrayState
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.AccountInfo
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.NucleusDecoratedWindowScope
import dev.nucleusframework.composenativetray.tray.api.Tray
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skiko.FPSCounter
import dev.nucleusframework.window.TitleBarScope

@Composable
private fun TitleBarNavButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .then(
                if (enabled) Modifier
                    .clickable(onClick = onClick)
                    .pointerHoverIcon(PointerIcon.Hand)
                else Modifier
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        )
    }
}

@Composable
fun TitleBarScope.DesktopTitleBar(
    currentSong: String?,
    isPlaying: Boolean,
    isLoggedIn: Boolean,
    accountInfo: AccountInfo?,
    ytmSyncEnabled: Boolean,
    isSyncing: Boolean,
    isOfflineMode: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleOfflineMode: (Boolean) -> Unit,
    onToggleSync: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
) {
    Row(
        modifier = Modifier.align(Alignment.Start).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TitleBarNavButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(Res.string.back),
            enabled = canGoBack,
            onClick = onBack,
        )
        TitleBarNavButton(
            icon = Icons.Filled.Refresh,
            contentDescription = stringResource(Res.string.refresh),
            enabled = true,
            onClick = onRefresh,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.app_name),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    // El menú de sincronización/cuenta solo tiene sentido con sesión iniciada: sin login no hay
    // nada que sincronizar (la cuenta es una cookie de YouTube Music).
    if (isLoggedIn) {
        Box(modifier = Modifier.align(Alignment.End).padding(end = 4.dp)) {
        var showMenu by remember { mutableStateOf(false) }
        IconButton(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            onClick = { showMenu = true }) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (accountInfo?.thumbnailUrl != null) {
                MusicPlayerImage(
                    url = accountInfo.thumbnailUrl,
                    contentDescription = accountInfo.name,
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    placeholderType = PlaceholderType.ARTIST,
                )
            } else {
                Icon(
                    if (ytmSyncEnabled) Icons.Rounded.CloudSync else Icons.Rounded.CloudOff,
                    contentDescription = stringResource(Res.string.ytm_sync),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            if (accountInfo != null) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MusicPlayerImage(
                        url = accountInfo.thumbnailUrl,
                        contentDescription = accountInfo.name,
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        placeholderType = PlaceholderType.ARTIST,
                    )
                    Column {
                        Text(accountInfo.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        accountInfo.email?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.ytm_sync)) },
                trailingIcon = { Switch(checked = ytmSyncEnabled, onCheckedChange = null) },
                onClick = { onToggleSync(!ytmSyncEnabled) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            DropdownMenuItem(
                text = { Text(stringResource(if (isSyncing) Res.string.sync_now_syncing else Res.string.sync_now)) },
                leadingIcon = {
                    if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Sync, contentDescription = null)
                },
                enabled = !isSyncing,
                onClick = { onSyncNow(); showMenu = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.offline_mode)) },
                trailingIcon = { Switch(checked = isOfflineMode, onCheckedChange = null) },
                onClick = { onToggleOfflineMode(!isOfflineMode) },
            )
        }
        }
    }

    AnimatedContent(
        targetState = currentSong,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) { song ->
        if (song != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = song,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 450.dp)
                        .basicMarquee(
                            velocity = if (isPlaying) 25.dp else 0.dp,
                        ),
                )
            }
        }
    }
}

@Composable
fun NucleusApplicationScope.TrayCustom(
    playerViewModel: PlayerViewModel,
    onToggleVisibility: () -> Unit,
    onShow: () -> Unit,
    handleExit: () -> Unit
) {

    val tooltipText = stringResource(Res.string.app_name)
    val pauseLabel = stringResource(Res.string.tray_pause)
    val playLabel = stringResource(Res.string.tray_play)
    val nextLabel = stringResource(Res.string.tray_next)
    val previousLabel = stringResource(Res.string.tray_previous)
    val openLabel = stringResource(Res.string.tray_open)
    val exitLabel = stringResource(Res.string.tray_exit)

    Tray(
        icon = Res.drawable.PaltaSound,
        tooltip = tooltipText,
        primaryAction = { onToggleVisibility() },
    ) {
        Item(
            label = "$pauseLabel/$playLabel",
            onClick = { playerViewModel.togglePlayPause() }
        )
        Item(
            label = nextLabel,
            onClick = { playerViewModel.next() }
        )
        Item(
            label = previousLabel,
            onClick = { playerViewModel.previous() }
        )
        Divider()
        Item(
            label = openLabel,
            onClick = { onShow() }
        )
        Divider()
        Item(
            label = exitLabel,
            onClick = {
                dispose()
                handleExit()
            }
        )
    }
}

@Composable
fun FrameWindowScope.windowBackgroundFlashingWorkaround(
    themeBg: Color
) {
    val awtColor = java.awt.Color(themeBg.toArgb())
    LaunchedEffect(window, themeBg) {
        window.background = awtColor
        window.contentPane.background = awtColor
    }
}

@Composable
fun FpsCounter(
    modifier: Modifier = Modifier
) {
    val fpsCounter = remember {
        FPSCounter(periodSeconds = 1.0)
    }

    var fps by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                fpsCounter.tick()

                // Solo cambia una vez por segundo
                fps = fpsCounter.average
            }
        }
    }

    Text(
        text = "FPS: $fps",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(4.dp)
    )
}