package example.nucleus.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import example.nucleus.data.repository.OVERLAY_POS_UNSET
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.listentogether.ConnectionState
import example.nucleus.listentogether.ListenTogetherManager
import example.nucleus.listentogether.UserInfo
import example.nucleus.player.PlaybackState
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.themes.AppTheme
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalUserPreferences
import example.nucleus.viewmodels.LibraryViewModel
import example.nucleus.viewmodels.PlayerViewModel
import example.nucleus.viewmodels.RepeatMode
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val OVERLAY_W = 460.0
private const val OVERLAY_H = 640.0

private enum class OverlayTab { QUEUE, SEARCH, PLAYLISTS, ALBUMS, TOGETHER }

private sealed interface OverlaySearchState {
    data object Idle : OverlaySearchState
    data object Loading : OverlaySearchState
    data class Results(val items: List<SongItem>) : OverlaySearchState
    data object Empty : OverlaySearchState
}

/**
 * Superposición de música estilo Steam siempre visible, anclada abajo a la derecha y arrastrable
 * desde su barra superior. Se activa con un atajo de teclado global (ver [GlobalHotkeyManager])
 * para que el usuario pueda gestionar la cola de reproducción, buscar, explorar listas de
 * reproducción/álbumes guardados o usar Escuchar Juntos sin salir de su juego.
 *
 * NOTA: aparece sobre juegos en modo **sin bordes / ventana**; el modo pantalla completa
 * exclusiva (true fullscreen) no la muestra.
 * Debe llamarse desde dentro de un scope `application { }` (renderiza su propia [Window]).
 */
@OptIn(FlowPreview::class)
@Composable
fun MusicOverlayWindow(
    visible: Boolean,
    onDismiss: () -> Unit,
    playerViewModel: PlayerViewModel,
    userPreferences: UserPreferencesRepository,
    savedPosX: Int,
    savedPosY: Int,
) {
    if (!visible) return

    // Restaurar la última posición a la que el usuario arrastró; de lo contrario, usar la esquina inferior derecha.
    val position = remember(savedPosX, savedPosY) {
        if (savedPosX != OVERLAY_POS_UNSET && savedPosY != OVERLAY_POS_UNSET) {
            WindowPosition(savedPosX.dp, savedPosY.dp)
        } else {
            runCatching {
                val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                val bounds = ge.maximumWindowBounds
                val scale = ge.defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.takeIf { it > 0 } ?: 1.0
                val rightLogical = (bounds.x + bounds.width) / scale
                val bottomLogical = (bounds.y + bounds.height) / scale
                WindowPosition((rightLogical - OVERLAY_W - 16).dp, (bottomLogical - OVERLAY_H - 16).dp)
            }.getOrDefault(WindowPosition(Alignment.BottomEnd))
        }
    }

    val state = rememberWindowState(width = OVERLAY_W.dp, height = OVERLAY_H.dp, position = position)

    // Guardar la posición cada vez que el usuario arrastra la ventana (con debounce).
    LaunchedEffect(state) {
        snapshotFlow { state.position }
            .drop(1)
            .debounce(400)
            .collect { pos ->
                if (pos is WindowPosition.Absolute) {
                    userPreferences.setOverlayPosition(pos.x.value.toInt(), pos.y.value.toInt())
                }
            }
    }

    Window(
        onCloseRequest = onDismiss,
        visible = true,
        state = state,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        title = "MusicPlayer",
        onPreviewKeyEvent = { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onDismiss(); true } else false
        },
    ) {
        LaunchedEffect(Unit) {
            window.toFront()
            window.requestFocus()
        }

        // Ventana sin decoración → la barra de título es el área de arrastre (WindowDraggableArea
        // actualiza state.position, que el efecto anterior persiste). El botón de cerrar está fuera de ella.
        val draggable: @Composable (@Composable () -> Unit) -> Unit = { content -> WindowDraggableArea(content = content) }

        AppTheme(userPreferences = userPreferences) {
            CompositionLocalProvider(
                LocalPlayerViewModel provides playerViewModel,
                LocalUserPreferences provides userPreferences,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                ) {
                    OverlayContent(playerViewModel = playerViewModel, onDismiss = onDismiss, draggable = draggable)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OverlayContent(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    draggable: @Composable (@Composable () -> Unit) -> Unit,
) {
    val state by playerViewModel.uiState.collectAsState()
    val volume by playerViewModel.volume.collectAsState()
    val progress by playerViewModel.progressState.collectAsState()
    val song = state.currentSong
    val isPlaying = state.playbackState == PlaybackState.PLAYING
    val isLoading = state.playbackState == PlaybackState.LOADING

    var selectedTab by remember { mutableStateOf(OverlayTab.QUEUE) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // ── Barra de título arrastrable (+ botón de cerrar fuera del área de arrastre) ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                draggable {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.DragHandle, null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(Res.string.overlay_title),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Rounded.Close, stringResource(Res.string.close_label), modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Pista actual ──
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicPlayerImage(
                url = song?.thumbnailUrl,
                contentDescription = song?.title,
                modifier = Modifier.size(52.dp),
                shape = example.nucleus.ui.themes.AppShapes.medium,
                contentScale = ContentScale.Crop,
                placeholderType = PlaceholderType.SONG,
                iconSize = 22.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song?.title ?: stringResource(Res.string.overlay_nothing_playing),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (song != null) {
                    Text(
                        song.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (song != null && progress.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(10.dp))
        }

        // ── Controles de reproducción ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { playerViewModel.toggleShuffle() }, modifier = Modifier.size(38.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(
                    Icons.Rounded.Shuffle, stringResource(Res.string.mp_shuffle), modifier = Modifier.size(18.dp),
                    tint = if (state.isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { playerViewModel.previous() }, modifier = Modifier.size(44.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(Res.string.mp_previous), modifier = Modifier.size(26.dp))
            }
            FilledIconButton(
                onClick = { playerViewModel.togglePlayPause() },
                modifier = Modifier.size(50.dp).pointerHoverIcon(PointerIcon.Hand),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                if (isLoading) {
                    LoadingIndicator(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface)
                } else {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (isPlaying) Res.string.tray_pause else Res.string.tray_play),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            IconButton(onClick = { playerViewModel.next() }, modifier = Modifier.size(44.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Rounded.SkipNext, stringResource(Res.string.mp_next), modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = { playerViewModel.toggleRepeat() }, modifier = Modifier.size(38.dp).pointerHoverIcon(PointerIcon.Hand)) {
                Icon(
                    if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    stringResource(Res.string.mp_repeat), modifier = Modifier.size(18.dp),
                    tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.VolumeUp, stringResource(Res.string.mp_volume),
                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = volume.coerceIn(0, 100) / 100f,
                onValueChange = { playerViewModel.setVolume((it * 100).toInt()) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── Pestañas ──
        val tabs = listOf(
            OverlayTab.QUEUE to stringResource(Res.string.overlay_tab_queue),
            OverlayTab.SEARCH to stringResource(Res.string.overlay_tab_search),
            OverlayTab.PLAYLISTS to stringResource(Res.string.overlay_tab_playlists),
            OverlayTab.ALBUMS to stringResource(Res.string.overlay_tab_albums),
            OverlayTab.TOGETHER to stringResource(Res.string.overlay_tab_together),
        )
        SecondaryScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab },
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
        ) {
            tabs.forEach { (tab, label) ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    text = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                OverlayTab.QUEUE -> QueueTab(playerViewModel)
                OverlayTab.SEARCH -> SearchTab(playerViewModel)
                OverlayTab.PLAYLISTS -> PlaylistsTab(playerViewModel)
                OverlayTab.ALBUMS -> AlbumsTab(playerViewModel)
                OverlayTab.TOGETHER -> TogetherTab()
            }
        }
    }
}

// ── Pestaña de cola de reproducción ──────────────────────────────────────────

@Composable
private fun QueueTab(playerViewModel: PlayerViewModel) {
    val state by playerViewModel.uiState.collectAsState()
    val queue = state.queue
    val listState = rememberLazyListState()

    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex in queue.indices) runCatching { listState.scrollToItem(state.currentIndex) }
    }

    if (queue.isEmpty()) {
        CenteredText(stringResource(Res.string.overlay_empty_queue))
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(queue, key = { i, s -> "${i}_${s.id}" }) { index, item ->
            SongRow(
                thumbnail = item.thumbnailUrl,
                title = item.title,
                subtitle = item.artists.joinToString(", ") { it.name },
                highlighted = index == state.currentIndex,
                onClick = { playerViewModel.playAtIndex(index) },
            )
        }
    }
}

// ── Pestaña de búsqueda ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTab(playerViewModel: PlayerViewModel) {
    var query by remember { mutableStateOf("") }
    var searchState by remember { mutableStateOf<OverlaySearchState>(OverlaySearchState.Idle) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    fun runSearch() {
        val q = query.trim()
        if (q.isBlank()) { searchState = OverlaySearchState.Idle; return }
        searchState = OverlaySearchState.Loading
        scope.launch {
            val items = runCatching {
                YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    ?.items?.filterIsInstance<SongItem>().orEmpty()
            }.getOrDefault(emptyList())
            searchState = if (items.isEmpty()) OverlaySearchState.Empty else OverlaySearchState.Results(items.take(20))
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focusRequester.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(stringResource(Res.string.overlay_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
        )
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = searchState) {
                is OverlaySearchState.Idle -> Unit
                is OverlaySearchState.Loading -> CenteredSpinner()
                is OverlaySearchState.Empty -> CenteredText(stringResource(Res.string.overlay_no_results))
                is OverlaySearchState.Results -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(s.items, key = { it.id }) { item ->
                        SongRow(
                            thumbnail = item.thumbnail,
                            title = item.title,
                            subtitle = item.artists.joinToString(", ") { it.name },
                            onClick = { playerViewModel.playSingle(item); query = ""; searchState = OverlaySearchState.Idle },
                            trailing = {
                                IconAction(Icons.AutoMirrored.Filled.QueueMusic, stringResource(Res.string.overlay_play_next)) {
                                    playerViewModel.playNext(item)
                                }
                                IconAction(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(Res.string.overlay_add_queue)) {
                                    playerViewModel.addToQueue(item)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── Pestaña de listas de reproducción ───────────────────────────────────────

@Composable
private fun PlaylistsTab(playerViewModel: PlayerViewModel) {
    val libraryViewModel: LibraryViewModel = koinInject()
    val playlists by libraryViewModel.savedPlaylists.collectAsState()

    fun play(p: PlaylistItem, shuffle: Boolean) {
        if (p.id.startsWith("LOCAL_")) {
            libraryViewModel.resolveLocalPlaylistSongs(p.id, onResolved = { songs ->
                playerViewModel.playPlaylist(songs, 0, p.id, p.title, shuffle)
            })
        } else {
            playerViewModel.playPlaylistFromId(p.id, p.playEndpoint, p.title, shuffle = shuffle)
        }
    }

    if (playlists.isEmpty()) {
        CenteredText(stringResource(Res.string.overlay_empty_playlists))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(playlists, key = { it.id }) { p ->
            SongRow(
                thumbnail = p.thumbnail,
                title = p.title,
                subtitle = p.songCountText ?: p.author?.name ?: "",
                placeholder = PlaceholderType.PLAYLIST,
                onClick = { play(p, shuffle = false) },
                trailing = {
                    IconAction(Icons.Rounded.Shuffle, stringResource(Res.string.overlay_shuffle)) { play(p, shuffle = true) }
                },
            )
        }
    }
}

// ── Pestaña de álbumes ─────────────────────────────────────────────────────

@Composable
private fun AlbumsTab(playerViewModel: PlayerViewModel) {
    val libraryViewModel: LibraryViewModel = koinInject()
    val albums by libraryViewModel.savedAlbums.collectAsState()

    if (albums.isEmpty()) {
        CenteredText(stringResource(Res.string.overlay_empty_albums))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(albums, key = { it.id }) { a: AlbumItem ->
            SongRow(
                thumbnail = a.thumbnail,
                title = a.title,
                subtitle = a.artists?.joinToString(", ") { it.name } ?: "",
                placeholder = PlaceholderType.ALBUM,
                onClick = { playerViewModel.playAlbumFromBrowseId(browseId = a.browseId, title = a.title) },
                trailing = {
                    IconAction(Icons.Rounded.Shuffle, stringResource(Res.string.overlay_shuffle)) {
                        playerViewModel.playAlbumFromBrowseId(browseId = a.browseId, title = a.title, shuffle = true)
                    }
                },
            )
        }
    }
}

// ── Pestaña de Escuchar Juntos ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TogetherTab() {
    val manager: ListenTogetherManager = koinInject()
    val prefs = LocalUserPreferences.current
    val scope = rememberCoroutineScope()

    val room by manager.roomState.collectAsState()
    val connection by manager.connectionState.collectAsState()
    val savedName by prefs.listenTogetherUsername.collectAsState(initial = "")

    val r = room
    if (r != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.overlay_lt_room, r.roomCode),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(Res.string.overlay_lt_members, r.users.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { manager.leaveRoom() }) {
                    Text(stringResource(Res.string.lt_leave_btn), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(r.users, key = { it.userId }) { user -> MemberRow(user) }
            }
        }
        return
    }

    // Sala de espera: crear o unirse (nombre de usuario persistido).
    var username by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    LaunchedEffect(savedName) { if (username.isBlank() && savedName.isNotBlank()) username = savedName }
    val busy = connection == ConnectionState.CONNECTING
    val defaultHost = stringResource(Res.string.lt_default_host)
    val defaultGuest = stringResource(Res.string.lt_default_guest)

    fun persistName(name: String) { scope.launch { prefs.setListenTogetherUsername(name.trim()) } }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(Res.string.lt_username_label)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val name = username.ifBlank { defaultHost }
                persistName(name)
                manager.createRoom(name)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(Res.string.lt_create_btn))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        OutlinedTextField(
            value = joinCode,
            onValueChange = { joinCode = it.uppercase() },
            label = { Text(stringResource(Res.string.lt_join_code_label)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                val name = username.ifBlank { defaultGuest }
                persistName(name)
                manager.joinRoom(joinCode.trim(), name)
            },
            enabled = !busy && joinCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.lt_join_btn))
        }
    }
}

@Composable
private fun MemberRow(user: UserInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = if (user.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(10.dp),
        ) {}
        Text(
            user.username,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (user.isHost) {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                Text(
                    stringResource(Res.string.overlay_lt_host),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

// ── Elementos compartidos de fila ───────────────────────────────────────────

@Composable
private fun SongRow(
    thumbnail: String?,
    title: String,
    subtitle: String,
    placeholder: PlaceholderType = PlaceholderType.SONG,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) else Modifier)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(vertical = 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MusicPlayerImage(
            url = thumbnail,
            contentDescription = title,
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(6.dp),
            contentScale = ContentScale.Crop,
            placeholderType = placeholder,
            iconSize = 18.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.bodyMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp).pointerHoverIcon(PointerIcon.Hand)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BoxScope.CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
