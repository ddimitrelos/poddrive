import SwiftUI
import shared

@main
struct PodDriveApp: App {

    @State private var isAuthenticated = false

    private let syncUseCase: SyncPodcastLibraryUseCase
    private let setTierUseCase: SetEpisodeTierUseCase
    private let podcastRepository: any PodcastRepository
    private let revokeUseCase: RevokeSpotifyAccessUseCase

    init() {
        let dbFactory = DatabaseDriverFactory()
        let db = PodDrive(driver: dbFactory.createDriver())
        let podcastRepo = SqlDelightPodcastRepository(db: db)
        let episodeRepo = SqlDelightEpisodeRepository(db: db)

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
        setTierUseCase = SetEpisodeTierUseCase(podcastRepository: podcastRepo)  // ← ADD (Story 2.2)
    }

    var body: some Scene {
        WindowGroup {
            if isAuthenticated {
                HomeView(
                    syncUseCase: syncUseCase,
                    setTierUseCase: setTierUseCase,                         // ← ADD (Story 2.2)
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
