package com.poddrive.android

import android.app.Application
import com.poddrive.api.ItunesPodcastSearchClient
import com.poddrive.api.SpotifyAuthManager
import com.poddrive.audio.AudioPlayer
import com.poddrive.api.SpotifyLibraryApiClient
import com.poddrive.api.SpotifyPlayerApiClient
import com.poddrive.api.createAuthHttpClient
import com.poddrive.api.createItunesHttpClient
import com.poddrive.api.createSpotifyHttpClient
import com.poddrive.db.PodDrive
import com.poddrive.repository.DriveSessionRepository
import com.poddrive.repository.EpisodeRepository
import com.poddrive.repository.PodcastRepository
import com.poddrive.session.DriveSessionManager
import com.poddrive.storage.DatabaseDriverFactory
import com.poddrive.storage.SecureStorage
import com.poddrive.storage.SqlDelightDriveSessionRepository
import com.poddrive.storage.SqlDelightEpisodeRepository
import com.poddrive.storage.SqlDelightPodcastRepository
import com.poddrive.usecase.AuthenticateWithSpotifyUseCase
import com.poddrive.usecase.BuildEpisodeQueueUseCase
import com.poddrive.usecase.HandlePlaybackFailureUseCase
import com.poddrive.usecase.MarkEpisodePlayedUseCase
import com.poddrive.rss.RssParser
import com.poddrive.usecase.ResolveEpisodeAudioUrlsUseCase
import com.poddrive.usecase.ResolvePodcastRssFeedUseCase
import com.poddrive.usecase.RevokeSpotifyAccessUseCase
import com.poddrive.usecase.SearchSpotifyShowsUseCase
import com.poddrive.usecase.SetEpisodeTierUseCase
import com.poddrive.usecase.SyncPodcastLibraryUseCase
import io.ktor.client.HttpClient

class PodDriveApplication : Application() {

    // Application-scoped singletons — Story 1.2+ initialises infrastructure here.
    // A proper DI framework (Koin/Hilt) can replace this in a future story.
    lateinit var secureStorage: SecureStorage
        private set

    lateinit var authManager: SpotifyAuthManager
        private set

    /**
     * Main Ktor client for all Spotify Web API calls (api.spotify.com).
     * Configured with bearer Auth + silent token refresh via [authManager].
     * Stored here so future stories (library sync, playback) can inject it.
     *
     * DO NOT use this client inside [SpotifyAuthManager] — use the internal authHttpClient
     * to avoid circular refresh loops. [Source: Story 1.4 Dev Notes — Two HTTP Clients]
     */
    lateinit var apiHttpClient: HttpClient
        private set

    lateinit var authenticateWithSpotifyUseCase: AuthenticateWithSpotifyUseCase
        private set

    lateinit var revokeSpotifyAccessUseCase: RevokeSpotifyAccessUseCase
        private set

    lateinit var database: PodDrive
        private set

    lateinit var podcastRepository: PodcastRepository
        private set

    lateinit var episodeRepository: EpisodeRepository
        private set

    lateinit var syncPodcastLibraryUseCase: SyncPodcastLibraryUseCase
        private set

    lateinit var setEpisodeTierUseCase: SetEpisodeTierUseCase
        private set

    lateinit var buildEpisodeQueueUseCase: BuildEpisodeQueueUseCase
        private set

    lateinit var driveSessionRepository: DriveSessionRepository
        private set

    lateinit var driveSessionManager: DriveSessionManager
        private set

    lateinit var handlePlaybackFailureUseCase: HandlePlaybackFailureUseCase
        private set

    lateinit var markEpisodePlayedUseCase: MarkEpisodePlayedUseCase
        private set

    /**
     * Exposed for [OnboardingViewModel] (Story 2.6) — used for fetchFollowedShows() skip-guard
     * and as the backing client for [searchSpotifyShowsUseCase].
     */
    lateinit var libraryApiClient: SpotifyLibraryApiClient
        private set

    lateinit var searchSpotifyShowsUseCase: SearchSpotifyShowsUseCase
        private set

    /**
     * Story 4.7: Exposed so [DriveSessionViewModel] can construct [SpotifyAutoPlayCoordinator]
     * with a live [isHandingOff] lambda referencing the ViewModel's own UI state.
     * The coordinator itself is created lazily inside the ViewModel (not here), because the
     * isHandingOff lambda must close over _uiState which is only available post-construction.
     */
    lateinit var playerApiClient: SpotifyPlayerApiClient
        private set

    /**
     * Story 4.8: Plain Ktor client for the iTunes Podcast Search API and RSS feed fetching.
     * Does NOT carry Spotify bearer auth headers — these are public, unauthenticated APIs.
     * Kept separate from [apiHttpClient] to avoid leaking Spotify tokens to third parties.
     */
    val itunesHttpClient: HttpClient by lazy { createItunesHttpClient() }

    lateinit var resolvePodcastRssFeedUseCase: ResolvePodcastRssFeedUseCase
        private set

    /** Story 4.9: Resolves direct audio URLs from RSS feeds for unresolved episodes. */
    lateinit var resolveEpisodeAudioUrlsUseCase: ResolveEpisodeAudioUrlsUseCase
        private set

    /**
     * Story 4.10: In-app audio playback engine backed by ExoPlayer.
     *
     * Lazy so ExoPlayer is not instantiated at app startup — only when the drive screen is
     * first opened. Owned at application scope so it survives ViewModel recreation and
     * continues playing in the background when [DriveSessionForegroundService] holds the
     * WakeLock. Accessed from [com.poddrive.android.ui.drive.DriveSessionViewModel].
     *
     * DO NOT create additional AudioPlayer instances elsewhere — this singleton ensures a
     * single active ExoPlayer across the app lifecycle.
     */
    val audioPlayer: AudioPlayer by lazy { AudioPlayer(applicationContext) }

    override fun onCreate() {
        super.onCreate()

        // P2-2: Fail fast at startup if SPOTIFY_CLIENT_ID was not injected at build time.
        check(BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()) {
            "SPOTIFY_CLIENT_ID is not configured. Pass -PSPOTIFY_CLIENT_ID=<id> to Gradle " +
                "or set it in gradle.properties / CI secrets."
        }

        secureStorage = SecureStorage(applicationContext)

        // Story 1.4: Split into two Ktor clients to break the circular dependency:
        //   authHttpClient — plain client (no Auth plugin) used by SpotifyAuthManager for
        //                    token endpoint calls (exchange + refresh). Accounts.spotify.com
        //                    uses form-body credentials, not bearer tokens.
        //   apiHttpClient  — full client with Auth + refreshTokens → authManager.
        //                    Used for all api.spotify.com calls in future stories.
        val authHttpClient = createAuthHttpClient()
        authManager = SpotifyAuthManager(
            tokenStorage = secureStorage,
            httpClient = authHttpClient,
            clientId = BuildConfig.SPOTIFY_CLIENT_ID
        )
        // authManager is fully initialised before apiHttpClient is created.
        // The tokenRefresher lambda captures authManager by reference; it is only invoked
        // when a 401 occurs at runtime — long after onCreate completes.
        apiHttpClient = createSpotifyHttpClient(
            tokenStorage = secureStorage,
            tokenRefresher = authManager
        )
        authenticateWithSpotifyUseCase = AuthenticateWithSpotifyUseCase(authManager)
        revokeSpotifyAccessUseCase = RevokeSpotifyAccessUseCase(authManager)

        val dbFactory = DatabaseDriverFactory(applicationContext)
        database = PodDrive(dbFactory.createDriver())
        podcastRepository = SqlDelightPodcastRepository(database)
        episodeRepository = SqlDelightEpisodeRepository(database)
        libraryApiClient = SpotifyLibraryApiClient(apiHttpClient)
        searchSpotifyShowsUseCase = SearchSpotifyShowsUseCase(libraryApiClient)
        setEpisodeTierUseCase = SetEpisodeTierUseCase(podcastRepository)
        buildEpisodeQueueUseCase = BuildEpisodeQueueUseCase(episodeRepository)
        driveSessionRepository = SqlDelightDriveSessionRepository(database)
        driveSessionManager = DriveSessionManager(driveSessionRepository)
        handlePlaybackFailureUseCase = HandlePlaybackFailureUseCase(driveSessionManager)
        markEpisodePlayedUseCase = MarkEpisodePlayedUseCase(episodeRepository)
        // Story 4.7: Expose SpotifyPlayerApiClient so DriveSessionViewModel can construct the
        // coordinator with a live isHandingOff lambda (see DriveSessionViewModel.autoPlayCoordinator).
        playerApiClient = SpotifyPlayerApiClient(apiHttpClient)
        // Story 4.8: RSS feed discovery via iTunes Search API.
        resolvePodcastRssFeedUseCase = ResolvePodcastRssFeedUseCase(
            podcastRepository = podcastRepository,
            itunesClient = ItunesPodcastSearchClient(itunesHttpClient)
        )
        // Story 4.9: Episode audio URL resolution from RSS feeds.
        // Reuses itunesHttpClient — same plain client, no Spotify auth headers (AC2 requirement).
        resolveEpisodeAudioUrlsUseCase = ResolveEpisodeAudioUrlsUseCase(
            episodeRepository = episodeRepository,
            httpClient = itunesHttpClient,
            rssParser = RssParser()
        )
        syncPodcastLibraryUseCase = SyncPodcastLibraryUseCase(
            libraryApiClient, podcastRepository, episodeRepository,
            resolvePodcastRssFeedUseCase, resolveEpisodeAudioUrlsUseCase
        )
    }

    companion object {
        fun from(app: android.app.Application): PodDriveApplication =
            app as? PodDriveApplication
                ?: error(
                    "Expected PodDriveApplication but got ${app::class.simpleName}. " +
                        "Ensure android:name=\".PodDriveApplication\" is set in AndroidManifest.xml."
                )
    }
}
