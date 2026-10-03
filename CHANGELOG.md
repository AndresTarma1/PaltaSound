# Changelog

Todas las versiones de PaltaSound. Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [0.8.2] - 2026-10-03

### Añadido

- **Dos instaladores Windows: JVM y GraalVM nativo.** La variante JVM incluye los runtimes JS (GraalJS + QuickJS) con cipher web completo y auto-actualización desde `latest.yml`; la variante GraalVM nativa los excluye (flag `-PjsEngines=false` → `compileOnly`, sin Truffle en la imagen) con cipher degradado y actualización manual desde la página de release. Linux sigue en JVM con motores. La app instalada en nativo lo detecta (`isGraalVmNativeImage`) y ofrece "Abrir release" (estado `ManualOnly`) en vez de migrar de variante sin avisar (`AppViewModel.kt:127`, `SupportSection.kt:59`).
- **Podcasts guardados en Biblioteca**: pestaña Podcasts y sección en la mixta con los podcasts de la cuenta (estante `FEmusic_library_non_music_audio_list`), copia local `SavedPodcast` y refresco al abrir la pestaña (`LibraryViewModel.kt:243`, `PodcastsTab.kt:1`, `PodcastRepository.kt:1`).
- **Now Playing estilo Sonora**: fondo ambiental animado con resplandores de la paleta a la deriva (se omite si la app ya usa portada difuminada, y queda fijo sin animaciones), letra limpia sin cajas/sombras/fondos de hover, scroll de letras con muelles (`stiffness 170`, cascada por fila), realce de la línea activa sin cambios de layout, controles acotados al ancho de la columna, auto-hide del cromo y cursor oculto real (`NowPlayingAmbientBackground.kt:1`, `SyncedLyricsView.kt:122`, `NowPlayingLayouts.kt:379`).
- **Reproducción de video en Now Playing** con `libmpv` SW render (`vo=libmpv`, `bgr0`, `MpvRenderContext` + `MpvVideoRenderer` `~30fps`, `VideoSurface` OPAQUE). Toggle video/caratula en Now Playing y overlay a pantalla completa con cola empujando (no superpone), miniPlayer transparente auto-hide (mouse move, 2.6s), doble clic y `F11`/`Esc` para fullscreen, rueda = volumen (`VideoFullscreenOverlay.kt:284`, `App.kt:380`).
- **Panel de ajustes de video en Now Playing** (gear en `NowPlayingTopBar` y overlay) con video on/off, calidad, ajuste `FIT`/`CROP` y auto-fullscreen; quitados del Settings global (`NowPlayingSection.kt:32`).
- **PoTokens web vía sidecar `rustypipe-botguard`** (`RustyPipeBotGuardSidecar.kt:26` API v1, snapshot en tmp, `PoTokenManager.kt:69` `WEB_REMIX` + `WEB` con `pot=`) para clientes web (`FallbackClients.kt:15`). Bundling para GraalVM y JVM (`desktopApp/build.gradle.kts:222`, `mpv-resources/windows/rustypipe-botguard.exe`).
- **Video+audio juntos primero** (`FormatSelector.kt:52` `findMuxedFormat` progresivo, `YTPlayerutils.kt:124` compara alturas y solo usa muxed si no degrada resolución; `YtDlpResolver.kt:54` fallback).
- **Recursos de string** `video_settings`, `video_hint`, `video_fullscreen_enter/exit` hardcodeados → `Res.string` (`values/strings.xml:746`, `VideoFullscreenOverlay.kt:277`).

### Corregido

- Guardar un podcast no aparecía en Biblioteca: se leía el estante de canales (`..._channels_list`, devuelve canales de música como `USER_CHANNEL`) en vez del de podcasts (`..._non_music_audio_list`, con los `PodcastItem MPSP...`). Verificado contra la API real con sonda temporal. Además el guardado fallaba en silencio (solo log); ahora revierte el estado y avisa (`PlaylistManagerViewModel.kt:325`).
- La línea activa de letras cambiaba el layout (tamaño/peso/padding + `Text`→`FlowRow` en karaoke) y movía todo lo de abajo: ahora el tamaño es fijo y el realce va en la capa, y karaoke es siempre `FlowRow` (`SyncedLyricsView.kt:449`).
- El botón de volumen pisaba al de colapsar en la vista de portada: ahora queda encima con aire (`NowPlayingLayouts.kt:887`).
- CI Windows GraalVM `native-image` fallaba con `ForceOnModulePath` de `truffle-runtime` (`Module descriptor for the module org.graalvm.truffle.runtime was not found` en el fat jar). Se resuelve excluyendo los runtimes JS en la variante nativa (`-PjsEngines=false`, `desktopApp/build.gradle.kts:193`, `shared/build.gradle.kts:14`): Windows vuelve a compilar `packageGraalvmNsis` sin motores junto al NSIS JVM.
- CI `Download rustypipe-botguard`: Linux hacía `tar` sobre `.zip` por comparar `rustypipe_bin` en vez de `rustypipe_url`. Ahora compara la URL y usa directorio temporal dedicado (`build-release.yml:91`). Binario Windows `mpv-resources/windows/rustypipe-botguard.exe` añadido a `.gitignore`.
- Seek en video ya no queda `cargando y reconectando`: `MpvAudioPlayer.kt:286` `cache-pause=no` + cache 150MiB en video y `seekTo:350` `pause=no`; `PlayerService.kt:269` `seekToMs` absoluto en video y watchdog `STALL_TICKS_VIDEO=25` + `SEEK_STALL_GRACE_MS=8000`.
- Cerrar video/miniPlayer a NowPlaying: `NowPlayingLayouts.kt:110` usa `showVideo` compartido del `PlayerViewModel` (`PlayerViewModel.kt:86`), overlay `MiniPlayer` `onNowPlaying` cierra NowPlaying (`Navigation.kt:404`).
- `RustyPipeBotGuardSidecar.kt:29` ahora maneja `exe` vs binario Linux y busca en `mpv-resources/linux/` + `resolveOnPath`.
- `MpvAudioPlayer.kt:118` `vo=libmpv` y `MpvLib:259` FFM hardening; `PlayerService` anti-stall `BUFFERING`.

### Cambiado

- **CI** `.github/workflows/build-release.yml:36` descarga `rustypipe-botguard` para Windows y Linux (`x86_64` `v0.1.2` desde Codeberg) y verifica el binario empaquetado. Windows compila los dos NSIS (JVM con motores + GraalVM nativo con `-PjsEngines=false`); `merge-update-yml.py` deja solo el JVM en `latest.yml` y publica el nativo como descarga manual, con paso de verificación de ambos.
- `App.kt:202` `LocalAppFullscreen` + `WindowPlacement.Fullscreen` oculta title bar en fullscreen (`App.kt:390`), `.gitignore` ignora `graphify-out` y `mpv-resources/windows/rustypipe-botguard.exe`.

## [0.8.1] - 2026-08-20

### Añadido

- **Importar CSV con control**: check `Abrir página para extraer` en el tutorial — desmarcado solo abre el selector de archivos.

### Corregido

- Sincronización automática con YouTube al entrar a Cuenta deshabilitada (ahora solo manual tras login).
- Fondo de NowPlaying siempre transparente (preferencia `nowPlayingBackground` quitada de Ajustes).
- Diálogo de actualización en modo oscuro: texto negro sobre fondo oscuro → `color=scheme.onSurface` explícito `App.kt:421` y `CrashReportDialog.kt:38`.
- Mini reproductor integrado (DOCKED) ahora muestra el contenido de las rutas por detrás con Haze translúcido como el flotante, pegado abajo sin márgenes `Navigation.kt:118` `MiniPlayer.kt:612`.
- Barras de scroll (`AppVerticalScrollbar`) ya no quedan detrás del mini flotante/pegado (`LocalMiniPlayerInset` en `AppScrollbars.jvm.kt:62`).
- Cola NowPlaying y principal ya no se re-renderiza al saltar 5 puestos: keys estables `song.id` y `scrollToItem` instantáneo si `distance>3`.
- Tamaño de celdas en Biblioteca mixta `200dp → 150dp` igual que Álbumes/Artistas `LibraryMixedTab.kt:289`.

### Optimizado

- `ArtworkColorPalette` genera paleta en `Dispatchers.IO` y solo si `GRADIENT`/`dynamicColor`.
- `BlurredImageBackgroundLayer` `blur 99dp → 32dp` y gate por estilo.
- `PlayerViewModel` cola persistente `distinctUntilChangedBy { queueSession }` + serialización en `Default` sin loguear `CancellationException`.
- `SyncedLyricsView` `binarySearch` y `useFading` solo si `lines>6`; `NowPlaying` sin doble `BoxWithConstraints`.
- `Coil` `Memory 16→12MB`, `Disk 256→128MB`.
- `CsvImport` `ISRC` primero + `Semaphore(6)` `chunked(20)` `awaitAll` con `Mutex`.
- `MpvLib` validación `isFile/length>=50MB` + `try/catch` por candidatos y `isAvailable` flag con diálogo en vez de `ExceptionInInitializerError`.

## [0.8.0] - 2026-08-09

### Añadido

- **Diseño Material 3 Expressive**: nueva escala de formas (`AppShapes` 4–28dp + xLarge), motion con muelles expresivos (overshoot moderado) en transiciones de pantalla, mini reproductor y tabs de Now Playing, y armazón redondeado en todos los modos de layout (el modo Cuadrado conserva sus bordes separadores con esquinas suaves).
- **Mini reproductor flotante**: nuevo estilo "Flotante" (tarjeta sobre el contenido de las rutas, con padding inferior automático en las screens para que nada quede oculto) además de la barra pegada.
- **Fondos del mini reproductor flotante**: color sólido, de la carátula (desenfocada con gradiente de sus colores) y semitransparente con **desenfoque real del contenido de la ruta** detrás de la tarjeta (integración de la librería Haze, como SimpMusic).
- **Navegación en la barra de título** estilo web: botones de atrás (se desactiva en la raíz) y recargar. El botón de recarga recrea la pantalla activa (detalle) o fuerza la recarga del ViewModel (Home/Search/Library). Se eliminaron los botones de atrás duplicados en Álbum/Playlist/Artista/Búsqueda de YouTube y el botón de refrescar del Home.
- **Ajustes reorganizados por dominio**: Audio, Apariencia, Mini reproductor, Now Playing, Sincronización, Overlay, Aplicación, Avanzado y Soporte, cada uno con su propio ViewModel (se eliminó el `SettingsViewModel` gigante). Nuevas opciones:
  - Widget del reproductor en la barra de tareas de Windows (opcional).
  - Guardar la cola de reproducción al cerrar (opcional) — con restauración completa de la cola, orden, aleatorio y repetición.
  - Estilo de animación, tamaño y espaciado de letras, y romanización de letras.
  - Normalización de volumen (LUFS).
- **Estadísticas de reproducción**: tiempo escuchado, canciones/álbumes/artistas más reproducidos (contadores persistidos en la base de datos).

### Corregido

- Al reproducir tras restaurar la cola persistente, la primera canción quedaba en 0:00/0:00: ahora el play resuelve y reproduce el stream de la canción actual cuando el reproductor aún no tiene medio cargado.
- La búsqueda de canciones similares devolvía vacío siempre: `YouTube.related` ahora envía `endpoint.params` al browse.
- El efecto de desenfoque (Haze) del mini reproductor quedaba "stuck" (tarjeta transparente sin blur) al cambiar de estilo de fondo: se fuerza la invalidación por pre-draw en desktop.
- `hazeSource` solo se activa cuando el mini reproductor flotante está en modo translúcido (menos coste de render al hacer scroll).
- El botón de recarga del TitleBar ahora recarga Home, Search y Library a través de sus ViewModels (antes no hacían nada al ser singletons).
- Se quitó el loading infinito cuando no se encuentran las letras: la búsqueda ahora tiene un timeout (20 s) y cancela correctamente al cambiar de canción, en vez de quedarse esperando para siempre. Cuando no hay letras disponibles se muestra el estado vacío.
- El panel de medios de Windows ahora muestra **PaltaSound** (en vez de "Aplicación Desconocida"): se registra el AppUserModelID del proceso y la app repara su acceso directo del menú Inicio en el arranque para que el panel muestre el nombre correcto.
- El auto-updater vuelve a funcionar para los instaladores `.exe`: el artefacto ahora se nombra `paltasound-<v>-win-x64-nsis.exe` (target `Nsis`), porque el updater selecciona el EXE filtrando por el sufijo `-nsis.` del nombre.
- El empaquetado GraalVM de Windows copia explícitamente `libmpv-2.dll` y `yt-dlp.exe`; así los builds limpios de GitHub Actions no generan instaladores incompletos.
- El instalador EXE (NSIS) ahora es asistido: muestra la licencia **GPL-3.0** con botón de aceptación y permite elegir la carpeta de instalación.

### Cambiado

- Se eliminó la opción "Canciones similares" del menú contextual de canciones; el menú vuelve a su agrupación original (Radio / Me gusta / Descargar).

### Compilación

- Los instaladores de **Windows** (`.msi` y `.exe`) se generan en GitHub Actions como binario **GraalVM nativo** (sin JVM).
- Se restaura el archivo `LICENSE` (GNU GPL-3.0) en la raíz del repo.
- **Linux** (`.deb`/`.rpm`) mantiene la distribución JVM (el native-image de GraalVM falla en los runners de GitHub por memoria).
