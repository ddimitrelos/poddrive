package com.poddrive.android

import android.app.Application
import com.poddrive.api.SpotifyAuthManager
import com.poddrive.api.SpotifyLibraryApiClient
import com.poddrive.api.createAuthHttpClient
import com.poddrive.api.createSpotifyHttpClient
import com.poddrive.db.PodDrive
import com.poddrive.repository.EpisodeRepository
import com.poddrive.repository.PodcastRepository
import com.poddrive.storage.DatabaseDriverFactory
import com.poddrive.storage.SecureStorage
import com.poddrive.storage.SqlDelightEpisodeRepository
import com.poddrive.storage.SqlDelightPodcastRepository
import com.poddrive.usecase.AuthenticateWithSpotifyUseCase
import com.poddrive.usecase.RevokeSpotifyAccessUseCase
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
        val libraryApiClient = SpotifyLibraryApiClient(apiHttpClient)
        syncPodcastLibraryUseCase = SyncPodcastLibraryUseCase(libraryApiClient, podcastRepository, episodeRepository)
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
