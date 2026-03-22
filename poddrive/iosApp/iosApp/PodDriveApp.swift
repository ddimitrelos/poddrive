import SwiftUI
import shared  // KMP shared framework

@main
struct PodDriveApp: App {

    @State private var isAuthenticated = false

    /// Eagerly-built use case — stored once at app startup so the Ktor HttpClient
    /// is never re-created on SwiftUI body re-renders (P1 fix, Story 1.5).
    ///
    /// `revokeAccess()` only calls `tokenStorage.clear()` — httpClient is not invoked
    /// during revocation. SecureStorage uses fixed Keychain keys, so this singleton
    /// and any other SecureStorage instance share the same underlying Keychain entries.
    private let revokeUseCase: RevokeSpotifyAccessUseCase = {
        let storage = SecureStorage()
        let httpClient = SpotifyHttpClientKt.createAuthHttpClient()
        let clientId = Bundle.main.infoDictionary?["SPOTIFY_CLIENT_ID"] as? String ?? ""
        let manager = SpotifyAuthManager(
            tokenStorage: storage,
            httpClient: httpClient,
            clientId: clientId
        )
        return RevokeSpotifyAccessUseCase(authManager: manager)
    }()

    var body: some Scene {
        WindowGroup {
            if isAuthenticated {
                // Story 1.5: Settings screen replaces authenticated placeholder.
                // Future stories (Epic 2+) will replace this with real HomeScreen navigation.
                SettingsView(
                    revokeUseCase: revokeUseCase,
                    onRevoked: { isAuthenticated = false }
                )
            } else {
                OnboardingView(onAuthenticated: {
                    isAuthenticated = true
                })
            }
        }
    }
}
