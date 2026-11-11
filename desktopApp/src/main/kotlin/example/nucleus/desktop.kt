package example.nucleus

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.glass.GlassDropdownMenu
import example.nucleus.ui.components.glass.LocalContentHaze
import example.nucleus.ui.components.glass.LocalLiquidGlass
import example.nucleus.ui.components.glass.rememberLiquidGlassControlStyle
import example.nucleus.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.AccountInfo
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass
import example.nucleus.data.repository.AppMode
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import dev.nucleusframework.window.TitleBarScope

@Composable
private fun TitleBarNavButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val useGlass = LocalLiquidGlass.current
    val hazeState = LocalContentHaze.current
    // La titlebar está sobre el contenido: la fuente queda fuera de rango y la
    // lente vive de su base esmerilada (consistente con el resto del cristal).
    if (useGlass && enabled && hazeState != null) {
        val interaction = remember { MutableInteractionSource() }
        val style = rememberLiquidGlassControlStyle(
            shape = RoundedCornerShape(10.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .hazeGlass(
                    input = HazeInput.Sources(hazeState),
                    style = style,
                    interactionSource = interaction,
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .focusable(interactionSource = interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TitleBarScope.DesktopTitleBar(
    currentSong: String?,
    isPlaying: Boolean,
    isLoggedIn: Boolean,
    accountInfo: AccountInfo?,
    appMode: AppMode,
    onModeChange: (AppMode) -> Unit,
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

    // Cuenta + selector de modo. Sin sesión el menú sigue disponible para
    // cambiar entre Música y YouTube; el bloque de sincronización se oculta.
    Box(modifier = Modifier.align(Alignment.End).padding(end = 4.dp)) {
        var showMenu by remember { mutableStateOf(false) }
        IconButton(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            onClick = { showMenu = true },
        ) {
            if (isSyncing) {
                LoadingIndicator(Modifier.size(14.dp))
                //CircularWavyProgressIndicator(Modifier.size(14.dp))
            } else if (accountInfo?.thumbnailUrl != null) {
                MusicPlayerImage(
                    url = accountInfo.thumbnailUrl,
                    contentDescription = accountInfo.name,
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    placeholderType = PlaceholderType.ARTIST,
                )
            } else if (isLoggedIn) {
                Icon(
                    if (ytmSyncEnabled) Icons.Rounded.CloudSync else Icons.Rounded.CloudOff,
                    contentDescription = stringResource(Res.string.ytm_sync),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = stringResource(Res.string.nav_account),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        GlassDropdownMenu(
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
                        Text(
                            text = accountInfo.name,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
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
                text = { Text(stringResource(Res.string.mode_music)) },
                leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                trailingIcon = {
                    if (appMode == AppMode.MUSIC) Icon(Icons.Filled.Check, contentDescription = null)
                },
                onClick = {
                    onModeChange(AppMode.MUSIC)
                    showMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.mode_youtube)) },
                leadingIcon = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                trailingIcon = {
                    if (appMode == AppMode.YOUTUBE) Icon(Icons.Filled.Check, contentDescription = null)
                },
                onClick = {
                    onModeChange(AppMode.YOUTUBE)
                    showMenu = false
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            if (isLoggedIn && appMode == AppMode.MUSIC) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.ytm_sync)) },
                    trailingIcon = { Switch(checked = ytmSyncEnabled, onCheckedChange = null) },
                    onClick = { onToggleSync(!ytmSyncEnabled) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (isSyncing) Res.string.sync_now_syncing else Res.string.sync_now)) },
                    leadingIcon = {
                        if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Sync, contentDescription = null)
                    },
                    enabled = !isSyncing,
                    onClick = { onSyncNow(); showMenu = false },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.offline_mode)) },
                trailingIcon = { Switch(checked = isOfflineMode, onCheckedChange = null) },
                onClick = { onToggleOfflineMode(!isOfflineMode) },
            )
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
fun TrayCustom(
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
