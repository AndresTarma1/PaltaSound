@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import example.nucleus.listentogether.ConnectionState
import example.nucleus.listentogether.ListenTogetherEvent
import example.nucleus.listentogether.ListenTogetherManager
import example.nucleus.listentogether.RoomRole
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import example.nucleus.ui.helpers.desktopClickableCursor
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.ctaLabel
import example.nucleus.ui.themes.screenTitle
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.utils.LocalSnackbarHostState
import example.nucleus.utils.LocalSnackbarScope
import example.nucleus.utils.LocalUserPreferences
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Elemento de miembro estable para uso de clave en LazyColumn. */
private data class MemberItem(
    val userId: String,
    val username: String,
    val isHost: Boolean,
    val isMe: Boolean,
)

/**
 * Pantalla de Escuchar Juntos — crear o unirse a una sala de escucha sincronizada.
 */
@Composable
fun ListenTogetherScreen() {
    val manager: ListenTogetherManager = koinInject()
    val snackbar = LocalSnackbarHostState.current
    val scope = LocalSnackbarScope.current

    val connectionState by manager.connectionState.collectAsState()
    val roomState by manager.roomState.collectAsState()
    val role by manager.role.collectAsState()
    val myUserId by manager.userId.collectAsState()
    val pendingRequests by manager.pendingJoinRequests.collectAsState()

    val prefs = LocalUserPreferences.current
    val savedName by prefs.listenTogetherUsername.collectAsState("")
    var username by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    LaunchedEffect(savedName) { if (username.isBlank() && savedName.isNotBlank()) username = savedName }
    val defaultHost = stringResource(Res.string.lt_default_host)
    val defaultGuest = stringResource(Res.string.lt_default_guest)
    val fmtRequestRejected = stringResource(Res.string.lt_request_rejected)
    val fmtKicked = stringResource(Res.string.lt_kicked)
    val fmtError = stringResource(Res.string.lt_error_generic)
    val fmtConnectionError = stringResource(Res.string.lt_connection_error)

    LaunchedEffect(Unit) {
        manager.events.collect { event ->
            when (event) {
                is ListenTogetherEvent.JoinRejected -> snackbar.showSnackbar(fmtRequestRejected.format(event.reason))
                is ListenTogetherEvent.Kicked -> snackbar.showSnackbar(fmtKicked.format(event.reason))
                is ListenTogetherEvent.Error -> snackbar.showSnackbar(fmtError.format(event.message))
                is ListenTogetherEvent.ConnectionError -> snackbar.showSnackbar(fmtConnectionError.format(event.error))
                else -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 24.dp,
                    bottom = 24.dp + LocalMiniPlayerInset.current,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = circleAwareShape(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 1.dp,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(Res.string.lt_title),
                            style = MaterialTheme.typography.screenTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(Res.string.lt_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ConnectionBadge(connectionState)

                val room = roomState
                if (room == null) {
                    LobbyContent(
                        username = username,
                        onUsernameChange = { username = it },
                        joinCode = joinCode,
                        onJoinCodeChange = { joinCode = it.uppercase() },
                        busy = connectionState == ConnectionState.CONNECTING,
                        onCreate = {
                            val name = username.ifBlank { defaultHost }
                            scope.launch { prefs.setListenTogetherUsername(name.trim()) }
                            manager.createRoom(name)
                        },
                        onJoin = {
                            val name = username.ifBlank { defaultGuest }
                            scope.launch { prefs.setListenTogetherUsername(name.trim()) }
                            manager.joinRoom(joinCode.trim(), name)
                        },
                    )
                } else {
                    val codeCopiedMsg = stringResource(Res.string.lt_code_copied, room.roomCode)
                    RoomContent(
                        roomCode = room.roomCode,
                        isHost = role == RoomRole.HOST,
                        members = room.users.map {
                            MemberItem(it.userId, it.username, it.isHost, it.userId == myUserId)
                        },
                        pending = pendingRequests.map { it.userId to it.username },
                        onApprove = { manager.approveJoin(it) },
                        onReject = { manager.rejectJoin(it) },
                        onTransferHost = { manager.transferHost(it) },
                        onKick = { manager.kickUser(it) },
                        onCopyCode = {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(room.roomCode), null)
                            scope.launch { snackbar.showSnackbar(codeCopiedMsg) }
                        },
                        onLeave = { manager.leaveRoom() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.CONNECTED -> stringResource(Res.string.lt_connected) to MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING -> stringResource(Res.string.lt_connecting) to MaterialTheme.colorScheme.tertiary
        ConnectionState.RECONNECTING -> stringResource(Res.string.lt_reconnecting) to MaterialTheme.colorScheme.tertiary
        ConnectionState.ERROR -> stringResource(Res.string.lt_error) to MaterialTheme.colorScheme.error
        ConnectionState.DISCONNECTED -> stringResource(Res.string.lt_disconnected) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = AppShapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = circleAwareShape(),
                color = color,
            ) {}
            Text(
                label,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = color,
            )
        }
    }
}

@Composable
private fun LobbyContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    joinCode: String,
    onJoinCodeChange: (String) -> Unit,
    busy: Boolean,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(Res.string.lt_username_label)) },
        singleLine = true,
        shape = AppShapes.large,
        modifier = Modifier.fillMaxWidth(),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = AppShapes.xLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.lt_create_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Text(
                stringResource(Res.string.lt_create_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onCreate,
                enabled = !busy,
                shape = AppShapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .desktopClickableCursor(enabled = !busy),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.lt_create_btn), style = MaterialTheme.typography.ctaLabel)
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = AppShapes.xLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.lt_join_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            OutlinedTextField(
                value = joinCode,
                onValueChange = onJoinCodeChange,
                label = { Text(stringResource(Res.string.lt_join_code_label)) },
                singleLine = true,
                shape = AppShapes.large,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = onJoin,
                enabled = !busy && joinCode.isNotBlank(),
                shape = AppShapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .desktopClickableCursor(enabled = !busy && joinCode.isNotBlank()),
            ) {
                Text(stringResource(Res.string.lt_join_btn), style = MaterialTheme.typography.ctaLabel)
            }
        }
    }
}

@Composable
private fun RoomContent(
    roomCode: String,
    isHost: Boolean,
    members: List<MemberItem>,
    pending: List<Pair<String, String>>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onTransferHost: (String) -> Unit,
    onKick: (String) -> Unit,
    onCopyCode: () -> Unit,
    onLeave: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = AppShapes.xLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = AppShapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            ) {
                Text(
                    if (isHost) stringResource(Res.string.lt_host_badge) else stringResource(Res.string.lt_guest_badge),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    roomCode,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                FilledTonalIconButton(
                    onClick = onCopyCode,
                    modifier = Modifier
                        .size(40.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    shape = circleAwareShape(),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = stringResource(Res.string.lt_copy_code),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    if (pending.isNotEmpty()) {
        Text(
            stringResource(Res.string.lt_pending_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        pending.forEach { (userId, name) ->
            Surface(
                shape = AppShapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLargeEmphasized)
                    FilledTonalIconButton(
                        onClick = { onApprove(userId) },
                        modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Filled.Check, stringResource(Res.string.lt_approve))
                    }
                    IconButton(
                        onClick = { onReject(userId) },
                        modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(Icons.Filled.Close, stringResource(Res.string.lt_reject), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    Text(
        stringResource(Res.string.lt_members_title, members.size),
        style = MaterialTheme.typography.titleMediumEmphasized,
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height((members.size.coerceAtMost(6) * 56).dp.coerceAtLeast(56.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = members.size > 6,
    ) {
        items(members, key = { it.userId }) { member ->
            Surface(
                shape = AppShapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (member.isHost) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        if (member.isMe) {
                            stringResource(Res.string.lt_you_suffix, member.username)
                        } else {
                            member.username
                        },
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isHost && !member.isMe) {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(Res.string.lt_member_options),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 0.dp,
                                shadowElevation = 8.dp,
                                shape = AppShapes.large,
                            ) {
                                if (!member.isHost) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.lt_make_host)) },
                                        onClick = {
                                            showMenu = false
                                            onTransferHost(member.userId)
                                        },
                                        leadingIcon = { Icon(Icons.Filled.StarOutline, null) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(Res.string.lt_kick),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onKick(member.userId)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.PersonRemove,
                                            null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Button(
        onClick = onLeave,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = AppShapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.lt_leave_btn), style = MaterialTheme.typography.ctaLabel)
    }
}
