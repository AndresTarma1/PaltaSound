package example.nucleus.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import example.nucleus.viewmodels.*
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class RootComponent(
    componentContext: ComponentContext,
) : ComponentContext by componentContext, KoinComponent {

    private val navigation = StackNavigation<ScreenConfig>()

    val childStack: Value<ChildStack<ScreenConfig, Child>> =
        childStack(
            source = navigation,
            serializer = serializer<ScreenConfig>(),
            initialConfiguration = ScreenConfig.Home,
            handleBackButton = true,
            childFactory = ::createChild
        )

    private fun createChild(config: ScreenConfig, componentContext: ComponentContext): Child {
        return when (config) {
            is ScreenConfig.Home -> Child.Home(HomeComponent(componentContext, get()))
            is ScreenConfig.Search -> Child.Search(SearchComponent(componentContext, get()))
            is ScreenConfig.Library -> Child.Library(LibraryComponent(componentContext, get()))
            is ScreenConfig.Account -> Child.Account(AccountComponent(componentContext, get()))
            is ScreenConfig.Settings -> Child.Settings(SettingsComponent(componentContext))
            is ScreenConfig.Stats -> Child.Stats
            is ScreenConfig.ListenTogether -> Child.ListenTogether
            is ScreenConfig.NowPlaying -> Child.NowPlaying(NowPlayingScreenRoot(componentContext, get()))
            is ScreenConfig.Album -> Child.Album(AlbumComponent(componentContext, config.browseId, get()))
            is ScreenConfig.Playlist -> Child.Playlist(PlaylistComponent(componentContext, config.playlistId, get()))
            is ScreenConfig.Artist -> Child.Artist(ArtistComponent(componentContext, config.artistId, get()))
            is ScreenConfig.YouTubeBrowse -> Child.YouTubeBrowse(YouTubeBrowseComponent(componentContext, config.browseId, config.params, get()))
            is ScreenConfig.YouTubeHome -> Child.YouTubeHome(YouTubeHomeComponent(componentContext, get()))
            is ScreenConfig.YouTubeShorts -> Child.YouTubeShorts(YouTubeHomeComponent(componentContext, get()))
            is ScreenConfig.YouTubeWatch -> Child.YouTubeWatch(YouTubeWatchComponent(componentContext, config.videoId, get()))
        }
    }

    fun navigateTo(config: ScreenConfig) {
        navigation.navigate { stack ->
            stack.filterNot { it == config } + config
        }
    }

    fun onBack() {
        navigation.pop()
    }

    fun switchTab(config: ScreenConfig) {
        navigation.replaceAll(config)
    }

    /** ¿Hay historial al que volver? (la pila tiene más de una entrada). */
    fun canGoBack(): Boolean = childStack.value.items.size > 1

    /**
     * "Recargar página" estilo web.
     *  - Pestañas (Home/Search/Library): sus ViewModels son singletons y cargan en su `init`,
     *    así que se llama directamente a su método de recarga.
     *  - Pantallas de detalle: se saca la pantalla activa de la pila y se vuelve a meter para
     *    que su componente se recree y recargue sus datos (init de los componentes).
     */
    fun refresh() {
        when (val config = childStack.value.active.configuration) {
            is ScreenConfig.Home -> get<HomeViewModel>().forceReload()
            is ScreenConfig.Search -> get<SearchViewModel>().refresh()
            is ScreenConfig.Library -> get<LibraryViewModel>().refreshYtmLibrary()
            is ScreenConfig.YouTubeHome,
            is ScreenConfig.YouTubeShorts,
            -> get<YouTubeHomeViewModel>().refreshHome(force = true)
            else -> {
                navigation.navigate { stack ->
                    val dropped = stack.dropLast(1)
                    if (dropped.isEmpty()) stack else dropped
                }
                navigation.navigate { stack ->
                    if (stack.lastOrNull() == config) stack else stack + config
                }
            }
        }
    }


    sealed class Child {
        data class Home(val component: HomeComponent) : Child()
        data class Search(val component: SearchComponent) : Child()
        data class Library(val component: LibraryComponent) : Child()
        data class Account(val component: AccountComponent) : Child()
        data class Settings(val component: SettingsComponent) : Child()
        data object Stats : Child()
        data object ListenTogether : Child()
        data class NowPlaying(val component: NowPlayingScreenRoot) : Child()
        data class Album(val component: AlbumComponent) : Child()
        data class Playlist(val component: PlaylistComponent) : Child()
        data class Artist(val component: ArtistComponent) : Child()
        data class YouTubeBrowse(val component: YouTubeBrowseComponent) : Child()
        data class YouTubeHome(val component: YouTubeHomeComponent) : Child()
        data class YouTubeShorts(val component: YouTubeHomeComponent) : Child()
        data class YouTubeWatch(val component: YouTubeWatchComponent) : Child()
    }
}

// Cada componente posee su propio ViewModel para que sobreviva al retroceso de la pila

class HomeComponent(componentContext: ComponentContext, val viewModel: HomeViewModel) :
    ComponentContext by componentContext

class SearchComponent(
    componentContext: ComponentContext,
    val viewModel: SearchViewModel
) : ComponentContext by componentContext

class SettingsComponent(componentContext: ComponentContext) : ComponentContext by componentContext

class LibraryComponent(
    componentContext: ComponentContext,
    val viewModel: LibraryViewModel
) : ComponentContext by componentContext

class AlbumComponent(
    componentContext: ComponentContext,
    browseId: String,
    val viewModel: AlbumManagerViewModel
) : ComponentContext by componentContext {
    init {
        viewModel.loadAlbum(browseId)
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() { viewModel.dispose() }
        })
    }
}

class PlaylistComponent(
    componentContext: ComponentContext,
    playlistId: String,
    val viewModel: PlaylistManagerViewModel
) : ComponentContext by componentContext {
    init {
        viewModel.loadPlaylist(playlistId)
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() { viewModel.dispose() }
        })
    }
}

class ArtistComponent(
    componentContext: ComponentContext,
    artistId: String,
    val viewModel: ArtistManagerViewModel
) : ComponentContext by componentContext {
    init {
        viewModel.loadArtist(artistId)
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() { viewModel.dispose() }
        })
    }
}

class AccountComponent(componentContext: ComponentContext, val viewModel: AccountManagerViewModel) :
    ComponentContext by componentContext {
    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() { viewModel.dispose() }
        })
    }
}

class YouTubeBrowseComponent(
    componentContext: ComponentContext,
    browseId: String,
    params: String?,
    val viewModel: YouTubeBrowseManagerViewModel
) : ComponentContext by componentContext {
    init {
        viewModel.load(browseId, params)
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() { viewModel.dispose() }
        })
    }
}

class YouTubeHomeComponent(
    componentContext: ComponentContext,
    val viewModel: YouTubeHomeViewModel,
) : ComponentContext by componentContext

class YouTubeWatchComponent(
    componentContext: ComponentContext,
    val videoId: String,
    val viewModel: YouTubeWatchViewModel,
) : ComponentContext by componentContext {
    init {
        viewModel.load(videoId)
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() {
                // Shared singleton ViewModel: stop playback, but keep it alive for
                // the next watch screen. Pass the id so a late teardown cannot
                // cancel the next video.
                viewModel.stopPlayback(videoId)
            }
        })
    }
}

class NowPlayingScreenRoot(
    componentContext: ComponentContext,
    val viewModel: PlayerViewModel,
) : ComponentContext by componentContext