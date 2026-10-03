package example.nucleus.di

import app.cash.sqldelight.db.SqlDriver
import example.nucleus.data.account.AccountManager
import example.nucleus.data.local.DatabaseDriverFactory
import example.nucleus.data.remote.ApiService
import example.nucleus.data.repository.AlbumRepository
import example.nucleus.data.repository.ArtistRepository
import example.nucleus.data.repository.PlaylistRepository
import example.nucleus.data.repository.PodcastRepository
import example.nucleus.data.repository.SearchRepository
import example.nucleus.data.repository.SongRepository
import example.nucleus.bootstrap.JvmConfigLauncher
import example.nucleus.lifecycle.AppLifecycleManager
import example.nucleus.viewmodels.AccountManagerViewModel
import example.nucleus.viewmodels.AlbumManagerViewModel
import example.nucleus.viewmodels.ArtistManagerViewModel
import example.nucleus.viewmodels.ApplicationViewModel
import example.nucleus.viewmodels.DownloadViewModel
import example.nucleus.viewmodels.HomeViewModel
import example.nucleus.viewmodels.LibraryAlbumsViewModel
import example.nucleus.viewmodels.LibraryArtistsViewModel
import example.nucleus.viewmodels.LibraryMixedViewModel
import example.nucleus.viewmodels.LibraryPlaylistsViewModel
import example.nucleus.viewmodels.LibrarySongsViewModel
import example.nucleus.viewmodels.LibraryViewModel
import example.nucleus.viewmodels.PlayerViewModel
import example.nucleus.viewmodels.PlaylistManagerViewModel
import example.nucleus.viewmodels.SearchViewModel
import example.nucleus.viewmodels.AdvancedSettingsViewModel
import example.nucleus.viewmodels.ApplicationSettingsViewModel
import example.nucleus.viewmodels.AppearanceSettingsViewModel
import example.nucleus.viewmodels.AudioSettingsViewModel
import example.nucleus.viewmodels.MiniPlayerSettingsViewModel
import example.nucleus.viewmodels.NowPlayingSettingsViewModel
import example.nucleus.viewmodels.OverlaySettingsViewModel
import example.nucleus.viewmodels.SyncSettingsViewModel
import example.nucleus.viewmodels.JvmSettingsViewModel
import example.nucleus.viewmodels.PlayerCoordinator
import example.nucleus.viewmodels.PlayerCoordinatorImpl
import example.nucleus.viewmodels.YouTubeBrowseManagerViewModel
import example.nucleus.db.DatabaseDao
import example.nucleus.db.MusicPlayerDatabase
import example.nucleus.db.MusicDatabase
import example.nucleus.player.AudioStreamResolver
import example.nucleus.download.DownloadService
import example.nucleus.player.PlayerService
import example.nucleus.player.QueueManager
import example.nucleus.player.WindowsMediaSession
import example.nucleus.listentogether.ListenTogetherClient
import example.nucleus.listentogether.ListenTogetherManager
import example.nucleus.utils.OfflineModeController
import example.nucleus.utils.PendingSyncQueue
import example.nucleus.utils.SyncUtils
import example.nucleus.overlay.GlobalHotkeyManager
import example.nucleus.overlay.OverlayController
import example.nucleus.viewmodels.AppViewModel
import org.koin.dsl.module

val appModule = module {

    // Base de datos
    single<SqlDriver> { DatabaseDriverFactory.createDriver() }
    single<MusicPlayerDatabase> { MusicPlayerDatabase(get<SqlDriver>()) }

    single<MusicDatabase> { MusicDatabase(get<MusicPlayerDatabase>()) }
    single<DatabaseDao> { get<MusicDatabase>().dao }

    // Capa de datos
    single<ApiService> { ApiService() }
    single<AlbumRepository> { AlbumRepository(get()) }
    single<ArtistRepository> { ArtistRepository(get()) }
    single<SongRepository> { SongRepository(get()) }
    single<PlaylistRepository> { PlaylistRepository(get(), get()) }
    single<PodcastRepository> { PodcastRepository(get()) }
    single<SearchRepository> { SearchRepository(get()) }
    single<SyncUtils> { SyncUtils(get(), get(), get(), get(), get()) }

    // Reproductor (singletons — compartidos en toda la app)
    // ✅ PlayerService se inicializa perezosamente — solo al primero play()
    single<PlayerService> { PlayerService(get()) }
    single<AudioStreamResolver> { AudioStreamResolver(get()) }
    single<WindowsMediaSession> { WindowsMediaSession() }
    single<DownloadService> { DownloadService(get(), get()) }
    single<QueueManager> { QueueManager() }
    single<AppViewModel> { AppViewModel() }
    // Cola de sincronización remota sin conexión (likes/suscripciones que fallaron al enviar sin conexión).
    single<PendingSyncQueue> { PendingSyncQueue(get()) }
    // Interruptor de red global — debe resolverse eager al inicio (ver main()).
    single<OfflineModeController> { OfflineModeController(get()) }
    // ✅ DownloadViewModel singleton — mantiene estado de descargas compartido
    single<DownloadViewModel> { DownloadViewModel(get(), get(), get()) }
    // ✅ PlayerViewModel singleton, pero inicialización pesada diferida al init{} interno
    single<PlayerViewModel> { PlayerViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<PlayerCoordinator> { PlayerCoordinatorImpl(get<PlayerViewModel>(), get<DownloadViewModel>()) }

    // Escuchar Juntos (sincronización WebSocket con el servidor relay meowery)
    single<ListenTogetherClient> { ListenTogetherClient() }
    single<ListenTogetherManager> { ListenTogetherManager(get()) }
    single<AppLifecycleManager> { AppLifecycleManager(get(), get(), get(), get(), get()) }
    single<JvmConfigLauncher> { JvmConfigLauncher(get()) }

    // Overlay de juego — el atajo global de teclado activa/desactiva una ventana de música siempre visible.
    single { GlobalHotkeyManager(onTrigger = { OverlayController.toggle() }) }

    // ViewModels — loginState de AccountManager para reaccionar a cambios de sesión
    factory { AccountManagerViewModel(get(), get(), get()) }
    factory { YouTubeBrowseManagerViewModel() }
    single { HomeViewModel(databaseDao = get(), loginState = AccountManager.loginState, preferencesRepository = get()) }
    single { SearchViewModel(get()) }
    single { LibraryViewModel(get(), get(), get(), get(), get(), get(), loginState = AccountManager.loginState) }
    single { LibrarySongsViewModel(get(), get(), get(), get()) }
    single { LibraryAlbumsViewModel(get()) }
    single { LibraryArtistsViewModel(get()) }
    single { LibraryPlaylistsViewModel(get(), get()) }
    single { LibraryMixedViewModel(get()) }
    factory { AlbumManagerViewModel(get(), get()) }
    factory { PlaylistManagerViewModel(get(), get(), get(), get(), get()) }
    factory { ArtistManagerViewModel(get(), get(), get()) }
    // ViewModels de Ajustes — uno por dominio, inyectados en su propia sección
    single { AudioSettingsViewModel(get()) }
    single { AppearanceSettingsViewModel(get()) }
    single { MiniPlayerSettingsViewModel(get()) }
    single { NowPlayingSettingsViewModel(get()) }
    single { ApplicationSettingsViewModel(get()) }
    single { AdvancedSettingsViewModel(get()) }
    single { SyncSettingsViewModel(get(), get()) }
    single { OverlaySettingsViewModel(get()) }
    single { JvmSettingsViewModel(get()) }
}
