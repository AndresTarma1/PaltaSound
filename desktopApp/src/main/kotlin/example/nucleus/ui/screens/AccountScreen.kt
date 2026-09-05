@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.screens

import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.navigation.Route
import com.metrolist.innertube.YouTube
import example.nucleus.ui.components.ExpressiveEmptyState
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.helpers.desktopClickableCursor
import example.nucleus.ui.helpers.desktopInteractiveSurface
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.ctaLabel
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.ui.themes.mediaItemTitle
import example.nucleus.ui.themes.screenTitle
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.viewmodels.AccountState
import example.nucleus.viewmodels.AccountManagerViewModel
import com.metrolist.innertube.models.PlaylistItem
import example.nucleus.data.account.BrowserCookieExtractor
import example.nucleus.data.account.BrowserLoginHelper
import example.nucleus.data.account.BrowserProfile
import example.nucleus.data.account.CookieExtractResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AccountScreenState(
    val uiState: AccountState = AccountState.NotLoggedIn,
    val cookieInput: String = "",
    val cookieWarnings: List<String> = emptyList(),
)

data class AccountActions(
    val onCookieInputChange: (String) -> Unit,
    val onLogin: () -> Unit,
    val onLogout: () -> Unit,
    val onReset: () -> Unit,
    val onRetry: () -> Unit,
    val onRefreshPlaylists: () -> Unit,
    val onNavigate: (Route) -> Unit,
    val onLoginWithCookie: (String) -> Unit = {},
)


@Composable
fun AccountScreenRoute(
    viewModel: AccountManagerViewModel,
    onNavigate: (Route) -> Unit,
) {

    val playerViewModel = LocalPlayerViewModel.current

    val uiState by viewModel.uiState.collectAsState()
    val cookieInput by viewModel.cookieInput.collectAsState()
    val cookieWarnings by viewModel.cookieWarnings.collectAsState()

    val state = AccountScreenState(
        uiState = uiState,
        cookieInput = cookieInput,
        cookieWarnings = cookieWarnings,
    )

    val actions = remember(viewModel, onNavigate) {
        AccountActions(
            onCookieInputChange = { viewModel.onCookieInputChange(it) },
            onLoginWithCookie = { viewModel.loginWithCookie(it) },
            onLogin = {
                val raw = viewModel.cookieInput.value
                // Detectar y parsear formato Metrolist: ***INNERTUBE COOKIE*** =xxx
                val metrolistRegex = """\*\*\*INNERTUBE COOKIE\*\*\*\s*=([\s\S]*?)(?:\n\*\*\*|\z)""".toRegex()
                val match = metrolistRegex.find(raw)
                if (match != null) {
                    val parsed = match.groupValues[1].trim()
                    // Extraer visitorData
                    """\*\*\*VISITOR DATA\*\*\*\s*=([^\n]+)""".toRegex().find(raw)?.let {
                        val vd = it.groupValues[1].trim()
                        if (vd.isNotEmpty()) YouTube.visitorData = vd
                    }
                    // Extraer dataSyncId
                    """\*\*\*DATASYNC ID\*\*\*\s*=([^\n]+)""".toRegex().find(raw)?.let {
                        val ds = it.groupValues[1].trim()
                        if (ds.isNotEmpty()) YouTube.dataSyncId = ds
                    }
                    if (parsed.isNotEmpty()) viewModel.onCookieInputChange(parsed)
                }
                viewModel.login()
            },
            onLogout = { viewModel.logout() },
            onReset = { viewModel.reset() },
            onRetry = { viewModel.retry() },
            onRefreshPlaylists = { viewModel.refreshPlaylists() },
            onNavigate = onNavigate,
        )
    }

    AccountScreen(
        state = state,
        actions = actions,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    state: AccountScreenState,
    actions: AccountActions,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.account_title),
                        style = MaterialTheme.typography.screenTitle,
                    )
                },
                actions = {
                    if (state.uiState is AccountState.LoggedIn) {
                        FilledTonalIconButton(
                            onClick = actions.onLogout,
                            modifier = Modifier.desktopClickableCursor(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(Res.string.logout),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        val animationsEnabled = LocalAnimationsEnabled.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = state.uiState,
                transitionSpec = {
                    if (animationsEnabled) fadeIn(expressiveTween(280)) togetherWith fadeOut(expressiveFadeTween())
                    else EnterTransition.None togetherWith ExitTransition.None
                },
                label = "accountContent"
            ) { contentState ->
                when (contentState) {
                    is AccountState.NotLoggedIn -> LoginSection(
                        cookieInput = state.cookieInput,
                        cookieWarnings = state.cookieWarnings,
                        onCookieInputChange = actions.onCookieInputChange,
                        onLogin = actions.onLogin,
                        onLoginWithCookie = actions.onLoginWithCookie
                    )
                    is AccountState.Loading -> LoadingSection()
                    is AccountState.LoggedIn -> LoggedInSection(
                        state = contentState,
                        onRefresh = actions.onRefreshPlaylists,
                        onNavigate = actions.onNavigate,
                    )
                    is AccountState.Error -> ErrorSection(
                        message = contentState.message,
                        onRetry = actions.onRetry,
                        onReset = actions.onReset
                    )
                    is AccountState.CookieExpired -> CookieExpiredSection(
                        cookieInput = state.cookieInput,
                        cookieWarnings = state.cookieWarnings,
                        onCookieInputChange = actions.onCookieInputChange,
                        onRenew = actions.onLogin,
                        onLogout = actions.onLogout
                    )
                }
            }
        }
    }
}


@Composable
private fun LoginSection(
    cookieInput: String,
    cookieWarnings: List<String> = emptyList(),
    onCookieInputChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLoginWithCookie: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showCookie by remember { mutableStateOf(false) }
    var browsers by remember { mutableStateOf<List<BrowserProfile>>(emptyList()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var browserLoginStep by remember { mutableStateOf<String?>(null) }

    val hasBrowser = remember { BrowserLoginHelper.findBrowserExecutable() != null }

    LaunchedEffect(Unit) {
        browsers = withContext(Dispatchers.IO) {
            BrowserCookieExtractor.detectBrowsers()
        }
    }

    // suspend, not @Composable: called from scope.launch{} below, and stringResource() can't be
    // called outside composition — getString() is the coroutine-safe equivalent.
    suspend fun handleCookieResult(result: CookieExtractResult) {
        browserLoginStep = null
        when (result) {
            is CookieExtractResult.Success -> {
                onLoginWithCookie(result.cookie)
            }
            is CookieExtractResult.Error -> {
                browserLoginStep = getString(Res.string.browser_login_error, result.message)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
            start = 20.dp,
            end = 20.dp,
            top = 20.dp,
            bottom = 20.dp + LocalMiniPlayerInset.current
        ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.size(88.dp),
                shape = circleAwareShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 1.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(stringResource(Res.string.login_title), style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(
                stringResource(Res.string.login_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // ── Browser sign in ─────────────────────────────────────────
            if (hasBrowser) {
                val browserSignInInteraction = remember { MutableInteractionSource() }
                Card(
                    onClick = {
                        scope.launch {
                            browserLoginStep = getString(Res.string.opening_browser)
                            val result = BrowserLoginHelper.loginWithBrowser { status ->
                                browserLoginStep = status
                            }
                            handleCookieResult(result)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .desktopInteractiveSurface(
                            shape = AppShapes.xLarge,
                            interactionSource = browserSignInInteraction,
                        ),
                    shape = AppShapes.xLarge,
                    interactionSource = browserSignInInteraction,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = AppShapes.medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(Res.string.sign_in_with_browser),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                stringResource(Res.string.sign_in_with_browser_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (browserLoginStep != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(browserLoginStep ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Import from browser ─────────────────────────────────────
            if (browsers.isNotEmpty()) {
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.desktopClickableCursor(),
                    shape = AppShapes.extraLarge,
                ) {
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(Res.string.import_from_browser),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }

                AnimatedVisibility(
                    visible = showAdvanced,
                    enter = if (LocalAnimationsEnabled.current) fadeIn() else EnterTransition.None,
                    exit = if (LocalAnimationsEnabled.current) fadeOut() else ExitTransition.None,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(Res.string.pick_browser_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        browsers.forEach { browser ->
                            val browserInteraction = remember(browser.name) { MutableInteractionSource() }
                            Card(
                                onClick = {
                                    scope.launch {
                                        browserLoginStep = getString(Res.string.reading_cookies, browser.name)
                                        val result = withContext(Dispatchers.IO) {
                                            BrowserCookieExtractor.extractYouTubeCookies(browser)
                                        }
                                        handleCookieResult(result)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .desktopInteractiveSurface(
                                        shape = AppShapes.large,
                                        interactionSource = browserInteraction,
                                    ),
                                shape = AppShapes.large,
                                interactionSource = browserInteraction,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = AppShapes.medium,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Language,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(browser.name, style = MaterialTheme.typography.titleMediumEmphasized)
                                        Text(
                                            browser.userDataDir.absolutePath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.Login,
                                        contentDescription = stringResource(Res.string.cd_import),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Divider ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    stringResource(Res.string.or_paste_cookie),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // ── Manual cookie paste (existing) ─────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = AppShapes.xLarge,
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(Res.string.need_metrolist),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                    }

                    HelpStep("1", stringResource(Res.string.step1))
                    HelpStep("2", stringResource(Res.string.step2))
                    HelpStep("3", stringResource(Res.string.step3))
                    HelpStep("4", stringResource(Res.string.step4))

                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.token_example), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        shape = AppShapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ) {
                        Text(
                            stringResource(Res.string.token_example_value),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = cookieInput,
                onValueChange = onCookieInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.cookie_label)) },
                placeholder = { Text(stringResource(Res.string.cookie_placeholder), style = MaterialTheme.typography.bodySmall) },
                visualTransformation = if (showCookie) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (cookieInput.isNotBlank()) onLogin() }),
                trailingIcon = {
                    IconButton(
                        onClick = { showCookie = !showCookie },
                        modifier = Modifier.desktopClickableCursor(),
                    ) {
                        Icon(if (showCookie) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showCookie) stringResource(Res.string.hide_label) else stringResource(Res.string.show_label))
                    }
                },
                shape = AppShapes.large,
                minLines = 2,
                maxLines = 3,
                supportingText = {
                    Text(stringResource(Res.string.characters, cookieInput.length), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )

            AnimatedVisibility(
                visible = cookieWarnings.isNotEmpty(),
                enter = if (LocalAnimationsEnabled.current) fadeIn() else EnterTransition.None,
                exit = if (LocalAnimationsEnabled.current) fadeOut() else ExitTransition.None,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
                    shape = AppShapes.large,
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.warnings_title), style = MaterialTheme.typography.labelMediumEmphasized, color = MaterialTheme.colorScheme.onErrorContainer)
                        cookieWarnings.take(3).forEach { warning ->
                            Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        if (cookieWarnings.size > 3) {
                            Text("...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Button(
                onClick = onLogin,
                enabled = cookieInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .desktopClickableCursor(enabled = cookieInput.isNotBlank()),
                shape = AppShapes.extraLarge,
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.login_title), style = MaterialTheme.typography.ctaLabel)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                shape = AppShapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(Res.string.privacy_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(circleAwareShape())
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}


@Composable
private fun LoadingSection() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                stringResource(Res.string.verifying_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun ErrorSection(message: String, onRetry: () -> Unit, onReset: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExpressiveEmptyState(
                icon = Icons.Default.ErrorOutline,
                title = stringResource(Res.string.could_not_load_account),
                subtitle = message,
                modifier = Modifier.heightIn(max = 280.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReset,
                    shape = AppShapes.extraLarge,
                    modifier = Modifier.desktopClickableCursor(),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.back_to_login), style = MaterialTheme.typography.ctaLabel)
                }
                Button(
                    onClick = onRetry,
                    shape = AppShapes.extraLarge,
                    modifier = Modifier.desktopClickableCursor(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.retry), style = MaterialTheme.typography.ctaLabel)
                }
            }
        }
    }
}


@Composable
private fun LoggedInSection(
    state: AccountState.LoggedIn,
    onRefresh: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Header de perfil
        AccountProfileHeader(accountInfo = state.accountInfo)

        Spacer(Modifier.height(8.dp))

        // Sección de playlists
        PlaylistsSection(
            playlists = state.playlists,
            isLoading = state.isLoadingPlaylists,
            onRefresh = onRefresh,
            onNavigate = onNavigate
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AccountProfileHeader(accountInfo: com.metrolist.innertube.models.AccountInfo) {
    val cookiePath = ""
    val cookieSize = 0L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = AppShapes.xLarge,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                if (!accountInfo.thumbnailUrl.isNullOrBlank()) {
                    MusicPlayerImage(
                        url = accountInfo.thumbnailUrl,
                        contentDescription = accountInfo.name,
                        modifier = Modifier.size(64.dp),
                        shape = circleAwareShape(),
                        placeholderType = PlaceholderType.ARTIST,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(circleAwareShape())
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            accountInfo.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        accountInfo.name,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!accountInfo.email.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            accountInfo.email.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!accountInfo.channelHandle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            accountInfo.channelHandle.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }

                // Badge de sesión activa
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            stringResource(Res.string.connected),
                            style = MaterialTheme.typography.labelMediumEmphasized,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = AppShapes.extraLarge,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    border = null
                )
            }

            // Barra de info: dónde está guardada la cookie
            if (cookieSize > 0L) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        stringResource(Res.string.cookie_info, cookieSize, cookiePath),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


@Composable
private fun PlaylistsSection(
    playlists: List<PlaylistItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNavigate: (Route) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header de sección
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(Res.string.your_playlists),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            FilledTonalIconButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.desktopClickableCursor(enabled = !isLoading),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.cd_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when {
            isLoading && playlists.isEmpty() -> {
                // Skeleton loaders
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(6) { PlaylistSkeletonItem() }
                }
            }
            playlists.isEmpty() -> {
                ExpressiveEmptyState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = stringResource(Res.string.no_playlists_found),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                )
            }
            else -> {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    playlists.forEach { playlist ->
                        PlaylistAccountItem(
                            playlist = playlist,
                            onClick = { onNavigate(Route.Playlist(playlist.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistAccountItem(
    playlist: PlaylistItem,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .desktopInteractiveSurface(
                shape = AppShapes.large,
                interactionSource = interaction,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        MusicPlayerImage(
            url = playlist.thumbnail,
            contentDescription = playlist.title,
            modifier = Modifier.size(52.dp),
            shape = AppShapes.medium,
            placeholderType = PlaceholderType.PLAYLIST,
        )

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.title,
                style = MaterialTheme.typography.mediaItemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!playlist.author?.name.isNullOrBlank() || playlist.songCountText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        playlist.author?.name?.let { append(it) }
                        if (playlist.author?.name != null && playlist.songCountText != null) append(" • ")
                        playlist.songCountText?.let { append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}


@Composable
private fun PlaylistSkeletonItem() {
    val animationsEnabled = LocalAnimationsEnabled.current
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val animAlpha = if (animationsEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmerAlpha"
        )
    } else {
        null
    }
    val alpha = animAlpha?.value ?: 0.5f
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(AppShapes.medium)
                .background(shimmerColor)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(AppShapes.small)
                    .background(shimmerColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(10.dp)
                    .clip(AppShapes.extraSmall)
                    .background(shimmerColor)
            )
        }
    }
}


@Composable
private fun CookieExpiredSection(
    cookieInput: String,
    cookieWarnings: List<String>,
    onCookieInputChange: (String) -> Unit,
    onRenew: () -> Unit,
    onLogout: () -> Unit
) {
    var showCookie by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
            start = 20.dp,
            end = 20.dp,
            top = 20.dp,
            bottom = 20.dp + LocalMiniPlayerInset.current
        ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(84.dp).clip(circleAwareShape()).background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LockClock, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(stringResource(Res.string.session_expired_title), style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(
                stringResource(Res.string.session_expired_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                shape = AppShapes.xLarge,
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Res.string.renew_cookie), style = MaterialTheme.typography.labelMediumEmphasized)
                    Text(stringResource(Res.string.renew_cookie_desc), style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = cookieInput,
                onValueChange = onCookieInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.new_cookie_label)) },
                placeholder = { Text(stringResource(Res.string.cookie_placeholder), style = MaterialTheme.typography.bodySmall) },
                visualTransformation = if (showCookie) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { showCookie = !showCookie },
                        modifier = Modifier.desktopClickableCursor(),
                    ) {
                        Icon(if (showCookie) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                    }
                },
                shape = AppShapes.large,
                minLines = 2,
                maxLines = 3,
                supportingText = {
                    Text(stringResource(Res.string.characters, cookieInput.length), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )

            AnimatedVisibility(
                visible = cookieWarnings.isNotEmpty(),
                enter = if (LocalAnimationsEnabled.current) fadeIn() else EnterTransition.None,
                exit = if (LocalAnimationsEnabled.current) fadeOut() else ExitTransition.None,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
                    shape = AppShapes.large,
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.warnings_title), style = MaterialTheme.typography.labelMediumEmphasized, color = MaterialTheme.colorScheme.onErrorContainer)
                        cookieWarnings.take(3).forEach { warning ->
                            Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Button(
                onClick = onRenew,
                enabled = cookieInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .desktopClickableCursor(enabled = cookieInput.isNotBlank()),
                shape = AppShapes.extraLarge,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.renew_session), style = MaterialTheme.typography.ctaLabel)
            }

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .desktopClickableCursor(),
                shape = AppShapes.extraLarge,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.logout), style = MaterialTheme.typography.ctaLabel)
            }
        }
    }
}
