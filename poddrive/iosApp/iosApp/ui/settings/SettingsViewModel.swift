import Foundation
import shared  // KMP shared framework

/// Drives the "Disconnect Spotify" revoke flow on iOS (Story 1.5).
///
/// Delegates to the KMP `RevokeSpotifyAccessUseCase` which clears all stored
/// tokens via `SpotifyAuthManager.revokeAccess()`. No network call is made —
/// this is a local-only operation.
///
/// On `.revoked`, the parent view calls `onRevoked()` to set `isAuthenticated = false`
/// in `PodDriveApp`, returning the user to the welcome screen.
@MainActor
final class SettingsViewModel: ObservableObject {

    enum State {
        case idle
        case revoking
        case revoked
        case error(String)
    }

    @Published var state: State = .idle

    private let revokeUseCase: RevokeSpotifyAccessUseCase

    init(revokeUseCase: RevokeSpotifyAccessUseCase) {
        self.revokeUseCase = revokeUseCase
    }

    /// Clears all stored Spotify credentials and transitions to `.revoked`.
    func revokeAccess() {
        state = .revoking
        Task {
            do {
                let result = try await revokeUseCase.execute()
                await MainActor.run {
                    if result is ResultSuccess<KotlinUnit, AppError> {
                        state = .revoked
                    } else {
                        state = .error("Failed to disconnect Spotify. Please try again.")
                    }
                }
            } catch {
                await MainActor.run {
                    state = .error("Unexpected error: \(error.localizedDescription)")
                }
            }
        }
    }
}
