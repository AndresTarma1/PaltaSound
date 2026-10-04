package example.nucleus

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import coil3.compose.setSingletonImageLoaderFactory
import com.arkivanov.decompose.DecomposeSettings
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.aotTraining
import dev.nucleusframework.application.nucleusApplication
import example.nucleus.bootstrap.AppEnvironment
import example.nucleus.bootstrap.AppStartup
import example.nucleus.bootstrap.JvmConfigLauncher
import example.nucleus.bootstrap.PlatformCrashHandler
import example.nucleus.data.account.AccountManager
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.di.appModule
import example.nucleus.di.dataStoreModule
import example.nucleus.lifecycle.AppLifecycleManager
import example.nucleus.logging.AppFileLogger
import example.nucleus.navigation.RootComponent
import example.nucleus.ui.components.CoilSetup
import example.nucleus.utils.OfflineModeController
import example.nucleus.viewmodels.AppViewModel
import example.nucleus.windows.AppUserModelId
import example.nucleus.viewmodels.DownloadViewModel
import example.nucleus.viewmodels.PlayerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext.startKoin


@OptIn(ExperimentalDecomposeApi::class)
fun main() = nucleusApplication(backend = NucleusBackend.Tao) {
    // Windows solo muestra el panel de medios del sistema si el proceso tiene un AppUserModelID explícito.
    // Debe llamarse antes de crear cualquier ventana.
    AppUserModelId.register()

    // Configuración inicial
    DecomposeSettings.update { DecomposeSettings(mainThreadCheckEnabled = false) }
    AppEnvironment.initialize()
    PlatformCrashHandler.register()

    // Inicializar Koin
    val koinApp = PlatformCrashHandler.runSafely("Error al iniciar Koin") {
        startKoin { modules(appModule, dataStoreModule) }
    }
    val koin = koinApp.koin

    val userPreferencesRepository = PlatformCrashHandler.runSafely("Error creando UserPreferencesRepository") {
        koin.get<UserPreferencesRepository>()
    }
    runBlocking {
        userPreferencesRepository.migrateDisabledIslandsLayout()
        // Preferencias de logging a disco (después de DataStore; W/E ya se escriben siempre).
        AppFileLogger.applyPreferences(
            logToFile = userPreferencesRepository.logToFile.first(),
            verbose = userPreferencesRepository.logVerbose.first(),
        )
    }

    // Inicializar AccountManager
    PlatformCrashHandler.runSafely("Error iniciando AccountManager") {
        val dataStore = koin.get<DataStore<Preferences>>()
        AccountManager.init(dataStore)
    }

    // Configurar app
    koin.get<JvmConfigLauncher>().applySync()
    koin.get<OfflineModeController>()

    // Obtener ViewModels
    val playerViewModel = PlatformCrashHandler.runSafely("Error creando PlayerViewModel") {
        koin.get<PlayerViewModel>()
    }
    val downloadViewModel = PlatformCrashHandler.runSafely("Error creando DownloadViewModel") {
        koin.get<DownloadViewModel>()
    }
    val appViewModel = PlatformCrashHandler.runSafely("Error creando AppViewModel") {
        koin.get<AppViewModel>()
    }
    val lifecycleManager = koin.get<AppLifecycleManager>()

    // Inicializar servicios nativos en background (mpv, media controls, Listen Together).
    // En training AOT se omiten a proposito: cada subsistema carga cientos de clases que
    // no cabrian en la region `ro` de la cache ("Unable to allocate from 'ro' region") y
    // el arranque en frio no los necesita perfilados (se calientan al primer uso real).
    if (!isAotTraining) {
        AppStartup.startDeferred(playerViewModel)
    }

    // Restaurar estado de ventana
    val saved = runBlocking {
        Triple(
            userPreferencesRepository.windowWidth.first(),
            userPreferencesRepository.windowHeight.first(),
            userPreferencesRepository.windowMaximized.first(),
        )
    }

    setSingletonImageLoaderFactory { context -> CoilSetup.createImageLoader(context) }
    val windowState = rememberWindowState(
        width = saved.first.coerceAtLeast(900).dp,
        height = saved.second.coerceAtLeast(600).dp,
        position = WindowPosition(Alignment.Center),
        placement = if (saved.third) WindowPlacement.Maximized else WindowPlacement.Floating,
    )
    val lifecycle = remember { LifecycleRegistry() }
    val rootComponent = remember {
        RootComponent(DefaultComponentContext(lifecycle))
    }

    aotTraining()

    App(
        rootComponent = rootComponent,
        appViewModel = appViewModel,
        playerViewModel = playerViewModel,
        downloadViewModel = downloadViewModel,
        userPreferences = userPreferencesRepository,
        windowState = windowState,
        onExit = { lifecycleManager.cleanUpAndExit() },
    )
}
