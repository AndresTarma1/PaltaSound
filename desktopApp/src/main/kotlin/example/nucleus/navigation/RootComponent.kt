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
            is ScreenConfig.Settings -> Child.Settings
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
     * "Recargar página" estilo web: cada componente que sabe recargarse
     * implementa [Reloadable]. Las pantallas sin recarga propia conservan el
     * comportamiento anterior: se recrea el child activo en la pila.
     */
    fun refresh() {
        val reloadable = childStack.value.active.instance.reloadable
        if (reloadable != null) {
            reloadable.reload()
            return
        }

        // Fallback para pantallas sin reload propio: recrear el child activo
        // conserva el comportamiento anterior del botón de refrescar.
        val config = childStack.value.active.configuration
        navigation.navigate { stack ->
            val dropped = stack.dropLast(1)
            if (dropped.isEmpty()) stack else dropped
        }
        navigation.navigate { stack ->
            if (stack.lastOrNull() == config) stack else stack + config
        }
    }


    sealed class Child(val reloadable: Reloadable?) {
        data class Home(val component: HomeComponent) : Child(component)
        data class Search(val component: SearchComponent) : Child(component)
        data class Library(val component: LibraryComponent) : Child(component)
        data class Account(val component: AccountComponent) : Child(component)
        data object Settings : Child(null)
        data object Stats : Child(null)
        data object ListenTogether : Child(null)
        data class NowPlaying(val component: NowPlayingScreenRoot) : Child(null)
        data class Album(val component: AlbumComponent) : Child(component)
        data class Playlist(val component: PlaylistComponent) : Child(component)
        data class Artist(val component: ArtistComponent) : Child(component)
        data class YouTubeBrowse(val component: YouTubeBrowseComponent) : Child(component)
        data class YouTubeHome(val component: YouTubeHomeComponent) : Child(component)
        data class YouTubeShorts(val component: YouTubeHomeComponent) : Child(component)
        data class YouTubeWatch(val component: YouTubeWatchComponent) : Child(component)
    }
}

/** Pantalla que puede recargarse sin recrear su componente. */
interface Reloadable {
    fun reload()
}

// Cada componente posee su propio ViewModel para que sobreviva al retroceso de la pila

class HomeComponent(componentContext: ComponentContext, val viewModel: HomeViewModel) :
    ComponentContext by componentContext, Reloadable {
    override fun reload() = viewModel.forceReload()
}

class SearchComponent(
    componentContext: ComponentContext,
    val viewModel: SearchViewModel
) : ComponentContext by componentContext, Reloadable {
    override fun reload() = viewModel.refresh()
}

class LibraryComponent(
    componentContext: ComponentContext,
    val viewModel: LibraryViewModel
) : ComponentContext by componentContext, Reloadable {
    override fun reload() = viewModel.refreshYtmLibrary()
}

class AlbumComponent(
    componentContext: ComponentContext,
    private val browseId: String,
    val viewModel: AlbumManagerViewModel
) : ComponentContext by componentContext, Reloadable {
    init {
        viewModel.loadAlbum(browseId)
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() { viewModel.dispose() }
        })
    }

    override fun reload() = viewModel.loadAlbum(browseId, force = true)
}

class PlaylistComponent(
    componentContext: ComponentContext,
    private val playlistId: String,
    val viewModel: PlaylistManagerViewModel
) : ComponentContext by componentContext, Reloadable {
    init {
        viewModel.loadPlaylist(playlistId)
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() { viewModel.dispose() }
        })
    }

    override fun reload() = viewModel.loadPlaylist(playlistId, force = true)
}

class ArtistComponent(
    componentContext: ComponentContext,
    private val artistId: String,
    val viewModel: ArtistManagerViewModel
) : ComponentContext by componentContext, Reloadable {
    init {
        viewModel.loadArtist(artistId)
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() { viewModel.dispose() }
        })
    }

    override fun reload() = viewModel.loadArtist(artistId)
}

class AccountComponent(componentContext: ComponentContext, val viewModel: AccountManagerViewModel) :
    ComponentContext by componentContext, Reloadable {
    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() { viewModel.dispose() }
        })
    }

    override fun reload() = viewModel.retry()
}

class YouTubeBrowseComponent(
    componentContext: ComponentContext,
    private val browseId: String,
    private val params: String?,
    val viewModel: YouTubeBrowseManagerViewModel
) : ComponentContext by componentContext, Reloadable {
    init {
        viewModel.load(browseId, params)
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() { viewModel.dispose() }
        })
    }

    override fun reload() = viewModel.load(browseId, params)
}

class YouTubeHomeComponent(
    componentContext: ComponentContext,
    val viewModel: YouTubeHomeViewModel,
) : ComponentContext by componentContext, Reloadable {
    override fun reload() = viewModel.refreshHome(force = true)
}

class YouTubeWatchComponent(
    componentContext: ComponentContext,
    val videoId: String,
    val viewModel: YouTubeWatchViewModel,
) : ComponentContext by componentContext, Reloadable {
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

    override fun reload() = viewModel.load(videoId)
}

class NowPlayingScreenRoot(
    componentContext: ComponentContext,
    val viewModel: PlayerViewModel,
) : ComponentContext by componentContext