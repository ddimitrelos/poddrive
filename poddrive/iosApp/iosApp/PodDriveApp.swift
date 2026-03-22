import SwiftUI
import shared  // KMP shared framework

@main
struct PodDriveApp: App {

    @State private var isAuthenticated = false

    // All dependencies built once in init(), sharing a single DB connection and auth manager.
    private let syncUseCase: SyncPodcastLibraryUseCase
    private let podcastRepository: any PodcastRepository
    private let revokeUseCase: RevokeSpotifyAccessUseCase

    init() {
        // Shared DB — single connection for both sync writes and cache reads (P1-2 fix)
        let dbFactory = DatabaseDriverFactory()
        let db = PodDrive(driver: dbFactory.createDriver())
        let podcastRepo = SqlDelightPodcastRepository(db: db)
        let episodeRepo = SqlDelightEpisodeRepository(db: db)

        // Auth dependencies — shared so revokeUseCase and syncUseCase operate on the same token state
        let storage = SecureStorage()
        let authHttpClient = SpotifyHttpClientKt.createAuthHttpClient()
        let clientId = Bundle.main.infoDictionary?["SPOTIFY_CLIENT_ID"] as? String ?? ""
        let authManager = SpotifyAuthManager(tokenStorage: storage, httpClient: authHttpClient, clientId: clientId)

        revokeUseCase = RevokeSpotifyAccessUseCase(authManager: authManager)

        let apiHttpClient = SpotifyHttpClientKt.createSpotifyHttpClient(tokenStorage: storage, tokenRefresher: authManager)
        let libraryClient = SpotifyLibraryApiClient(httpClient: apiHttpClient)
        syncUseCase = SyncPodcastLibraryUseCase(
            libraryApiClient: libraryClient,
            podcastRepository: podcastRepo,
            episodeRepository: episodeRepo
        )
        podcastRepository = podcastRepo
    }

    var body: some Scene {
        WindowGroup {
            if isAuthenticated {
                HomeView(
                    syncUseCase: syncUseCase,
                    podcastRepository: podcastRepository,
                    revokeUseCase: revokeUseCase,
                    onRevoked: { isAuthenticated = false }
                )
            } else {
                OnboardingView(onAuthenticated: { isAuthenticated = true })
            }
        }
    }
}
