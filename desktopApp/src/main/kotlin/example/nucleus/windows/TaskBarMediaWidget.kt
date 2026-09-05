package example.nucleus.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import example.nucleus.player.PlaybackState
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.mp_next
import example.nucleus.generated.resources.mp_previous
import example.nucleus.generated.resources.tray_exit
import example.nucleus.generated.resources.tray_open
import example.nucleus.generated.resources.tray_pause
import example.nucleus.generated.resources.tray_play
import example.nucleus.ui.components.SlimSlider
import example.nucleus.ui.components.formatPlayerTimeValue
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.viewmodels.PlayerViewModel
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.utils.LocalUserPreferences
import io.github.aakira.napier.Napier
import org.jetbrains.compose.resources.stringResource
import uk.kulikov.taskbar.TaskBarAlignment
import uk.kulikov.taskbar.TaskBarStatus
import uk.kulikov.taskbar.WindowsTaskBar
import uk.kulikov.taskbar.rememberTaskBarState
import javax.swing.SwingUtilities

/**
 * Widget de medios que vive DENTRO de la barra de tareas de Windows (antes de la bandeja),
 * estilo Spotify: miniatura + título + controles de transporte. Un clic en el widget abre
 * un flyout anclado sobre la barra con scrubber de posición y control de volumen.
 *
 * La librería `uk.kulikov:compose-windows-taskbar` exige el AWT event thread (crea un
 * [ComposeWindow] y lo re-parenta a `Shell_TrayWnd`), pero esta app corre en el backend
 * Tao (sin event loop AWT en el hilo principal). Por eso el widget se aloja en su propia
 * composición dentro de un [ComposeWindow] creado en [Swing] (EDT): el ViewModel y el
 * [ColorScheme] se pasan como parámetros porque esa composición no hereda los
 * CompositionLocals de la app.
 *
 * En sistemas que no sean Windows no compone nada.
 */
@Composable
fun TaskBarMediaWidget(
    playerViewModel: PlayerViewModel,
    userPreferences: example.nucleus.data.repository.UserPreferencesRepository,
    animationsEnabled: Boolean = true,
    onExit: () -> Unit,
    onShowWindow: () -> Unit,
) {
    if (!isWindows()) return

    val colorScheme = MaterialTheme.colorScheme
    var hostWindow by remember { mutableStateOf<ComposeWindow?>(null) }

    LaunchedEffect(Unit) {
        var window: ComposeWindow? = null
        runCatching {
            SwingUtilities.invokeAndWait {
                window = ComposeWindow().apply {
                    isUndecorated = true
                    isResizable = false
                    isVisible = false
                    type = java.awt.Window.Type.UTILITY
                    focusableWindowState = false
                    isAutoRequestFocus = false
                    // Fuera de pantalla: la composición del widget solo corre cuando la
                    // ventana es displayable (el ComposeWindowPanel no renderiza oculto).
                    setLocation(-32000, -32000)
                    setSize(1, 1)
                    setContent {
                        // MusicPlayerImage (carátula) lee LocalUserPreferences;
                        // SlimSlider/PlayerSeekBar leen LocalAnimationsEnabled.
                        CompositionLocalProvider(
                            LocalUserPreferences provides userPreferences,
                            LocalAnimationsEnabled provides animationsEnabled,
                        ) {
                            MaterialTheme(colorScheme = colorScheme) {
                                val appScope = remember { TaskBarAppScope(onExit) }
                                with(appScope) {
                                    TaskBarWidgetContent(playerViewModel, onShowWindow)
                                }
                            }
                        }
                    }
                    isVisible = true
                }
            }
        }.onSuccess {
            Napier.i("[taskbar-widget] Host ComposeWindow creado en el EDT (displayable=${window?.isDisplayable}, visible=${window?.isVisible})")
        }.onFailure { e ->
            Napier.w("[taskbar-widget] No se pudo crear el host en el EDT: $e")
        }
        hostWindow = window
    }

    DisposableEffect(Unit) {
        onDispose {
            hostWindow?.let { window ->
                SwingUtilities.invokeLater {
                    runCatching { window.dispose() }
                }
            }
        }
    }
}

/** [ApplicationScope] sintético para la composición del widget (la librería no lo usa más allá de la firma). */
private class TaskBarAppScope(
    private val onExit: () -> Unit,
) : ApplicationScope {
    override fun exitApplication() = onExit()
}

@Composable
private fun ApplicationScope.TaskBarWidgetContent(
    playerViewModel: PlayerViewModel,
    onShowWindow: () -> Unit,
) {
    val state by playerViewModel.uiState.collectAsState()
    val progressState by playerViewModel.progressState.collectAsState()
    val volume by playerViewModel.volume.collectAsState()

    val song = state.currentSong
    val isPlaying = state.playbackState == PlaybackState.PLAYING
    val isLoading = state.playbackState == PlaybackState.LOADING

    val taskBarState = rememberTaskBarState()
    var flyoutOpen by remember { mutableStateOf(false) }

    // Si deja de haber canción, cerrar el flyout (el widget se oculta por visible = false).
    LaunchedEffect(song) {
        if (song == null) flyoutOpen = false
    }

    LaunchedEffect(flyoutOpen) {
        Napier.i("[taskbar-widget] Flyout visible = $flyoutOpen")
    }

    val playLabel = stringResource(Res.string.tray_play)
    val pauseLabel = stringResource(Res.string.tray_pause)
    val previousLabel = stringResource(Res.string.mp_previous)
    val nextLabel = stringResource(Res.string.mp_next)
    val openLabel = stringResource(Res.string.tray_open)
    val exitLabel = stringResource(Res.string.tray_exit)

    WindowsTaskBar(
        visible = song != null,
        state = taskBarState,
        size = DpSize(240.dp, Dp.Unspecified),
        alignment = TaskBarAlignment.BeforeTray,
        movable = true,
        onClick = {
            Napier.i("[taskbar-widget] Click en espacio vacío -> abrir flyout")
            flyoutOpen = true
        },
        onStatusChanged = { status ->
            when (status) {
                is TaskBarStatus.Injected -> Napier.i(
                    "[taskbar-widget] INYECTADO: mode=${status.info.activeMode}, edge=${status.info.edge}, " +
                        "scale=${status.info.scale}, taskbar=${status.info.taskBarBounds}, widget=${status.info.widgetBounds}, " +
                        "tray=${status.info.trayBounds}"
                )
                is TaskBarStatus.Failed -> Napier.w("[taskbar-widget] Fallo: ${status.error.message}")
                is TaskBarStatus.WaitingForTaskBar -> Napier.i("[taskbar-widget] Esperando taskbar (explorer?)...")
                is TaskBarStatus.Initializing -> Napier.i("[taskbar-widget] Inicializando...")
                is TaskBarStatus.Detached -> Napier.i("[taskbar-widget] Desconectado")
            }
        },
        contextMenu = {
            item(if (isPlaying) pauseLabel else playLabel) { playerViewModel.togglePlayPause() }
            separator()
            item(previousLabel) { playerViewModel.previous() }
            item(nextLabel) { playerViewModel.next() }
            separator()
            item(openLabel) { onShowWindow() }
            item(exitLabel) { exitApplication() }
        },
    ) {
        val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)

        // ── Contenido recortado a la altura de la barra de tareas ──
        if (song != null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(surfaceColor)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MusicPlayerImage(
                    url = song.thumbnailUrl,
                    contentDescription = song.title,
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(4.dp),
                    contentScale = ContentScale.Crop,
                    placeholderType = PlaceholderType.SONG,
                    iconSize = 14.dp,
                )

                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                TaskBarIconButton(Icons.Rounded.SkipPrevious, previousLabel, MaterialTheme.colorScheme.onSurface) {
                    playerViewModel.previous()
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    TaskBarIconButton(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (isPlaying) pauseLabel else playLabel,
                        MaterialTheme.colorScheme.onSurface,
                    ) { playerViewModel.togglePlayPause() }
                }
                TaskBarIconButton(Icons.Rounded.SkipNext, nextLabel, MaterialTheme.colorScheme.onSurface) {
                    playerViewModel.next()
                }
            }
        }

        // ── Flyout: scrubber + volumen (el widget se recorta, esto va en una ventana propia) ──
        Flyout(
            visible = flyoutOpen,
            onDismissRequest = { flyoutOpen = false },
            size = DpSize(340.dp, 110.dp),
        ) {
            if (song != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MusicPlayerImage(
                                url = song.thumbnailUrl,
                                contentDescription = song.title,
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentScale = ContentScale.Crop,
                                placeholderType = PlaceholderType.SONG,
                                iconSize = 24.dp,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artists.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            SlimSlider(
                                value = volume.coerceIn(0, 100) / 100f,
                                onValueChange = { playerViewModel.setVolume((it * 100).toInt()) },
                                modifier = Modifier.weight(1f),
                                activeColor = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${volume.coerceIn(0, 100)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(32.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Botón pequeño de transporte sin rizado, pensado para el espacio de la barra de tareas. */
@Composable
private fun TaskBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** Scrubber de posición con arrastre local (mismo patrón que el MiniPlayer). */
@Composable
private fun FlyoutSeekBar(
    progressState: example.nucleus.viewmodels.PlayerProgressState,
    onSeek: (Long) -> Unit,
) {
    var localSliderValue by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(progressState.positionMs, progressState.durationMs) {
        if (!isDragging && progressState.durationMs > 0) {
            localSliderValue = progressState.positionMs.toFloat() / progressState.durationMs
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatPlayerTimeValue(
                if (isDragging) (localSliderValue * progressState.durationMs).toLong() else progressState.positionMs
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = localSliderValue,
            onValueChange = {
                isDragging = true
                localSliderValue = it
            },
            onValueChangeFinished = {
                onSeek((localSliderValue * progressState.durationMs).toLong())
                isDragging = false
            },
            modifier = Modifier.weight(1f),
            enabled = progressState.durationMs > 0,
        )
        Text(
            text = formatPlayerTimeValue(progressState.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun isWindows(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("win")
