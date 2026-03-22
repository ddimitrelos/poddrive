import SwiftUI
import shared  // KMP shared framework

/// Settings screen with "Disconnect Spotify" action (Story 1.5, FR3).
///
/// Displays the user's connected state and a "Disconnect Spotify" button.
/// On successful revocation, calls `onRevoked()` which sets `isAuthenticated = false`
/// in `PodDriveApp`, navigating back to the welcome screen.
///
/// Replaces the authenticated placeholder from Stories 1.3/1.4 until Epic 2+
/// provides a proper home screen.
struct SettingsView: View {

    @StateObject private var viewModel: SettingsViewModel
    private let onRevoked: () -> Void

    init(revokeUseCase: RevokeSpotifyAccessUseCase, onRevoked: @escaping () -> Void) {
        _viewModel = StateObject(
            wrappedValue: SettingsViewModel(revokeUseCase: revokeUseCase)
        )
        self.onRevoked = onRevoked
    }

    var body: some View {
        VStack(spacing: 16) {
            Text("PodDrive")
                .font(.largeTitle)
                .bold()

            Text("Connected to Spotify")
                .font(.body)
                .foregroundColor(.secondary)

            Spacer().frame(height: 32)

            switch viewModel.state {
            case .idle:
                Button(action: {
                    viewModel.revokeAccess()
                }) {
                    Text("Disconnect Spotify")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)

            case .revoking:
                Text("Disconnecting…")
                    .foregroundColor(.secondary)
                ProgressView()

            case .revoked:
                // Handled by .onChange below — no UI needed
                EmptyView()

            case .error(let message):
                Text(message)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)

                Button(action: {
                    viewModel.revokeAccess()
                }) {
                    Text("Retry")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        // task(id:) is available iOS 15+ and always receives the current value,
        // avoiding the iOS 17 regression where the deprecated onChange(of:perform:)
        // closure receives the OLD value, causing onRevoked() to never fire (P1 fix).
        .task(id: viewModel.stateIsRevoked) {
            guard viewModel.stateIsRevoked else { return }
            onRevoked()
        }
    }
}

// MARK: - Helpers

private extension SettingsViewModel {
    /// Bridge computed var used as the `id` for the `.task(id:)` modifier above.
    var stateIsRevoked: Bool {
        if case .revoked = state { return true }
        return false
    }
}
