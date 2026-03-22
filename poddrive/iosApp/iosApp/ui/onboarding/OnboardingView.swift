import SwiftUI

/// Welcome / onboarding screen for iOS.
/// Hosts the "Connect Spotify" button that triggers the PKCE OAuth flow.
/// Shows loading and error states, with a retry CTA on failure (AC5).
struct OnboardingView: View {

    @StateObject private var viewModel = OnboardingViewModel()

    var onAuthenticated: () -> Void

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                Text("PodDrive")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white)

                Text("Your podcast co-pilot")
                    .font(.system(size: 16))
                    .foregroundColor(.white.opacity(0.7))
                    .padding(.top, 8)

                Spacer()

                stateView

                Spacer().frame(height: 48)
            }
            .padding(.horizontal, 24)
        }
        .onChange(of: viewModel.state) { newState in
            if case .authenticated = newState {
                onAuthenticated()
            }
        }
    }

    @ViewBuilder
    private var stateView: some View {
        switch viewModel.state {
        case .idle:
            connectButton

        case .launchingBrowser:
            VStack(spacing: 16) {
                Text("Opening Spotify login…")
                    .foregroundColor(.white.opacity(0.8))
                ProgressView()
                    .tint(.white)
            }

        case .exchangingTokens:
            VStack(spacing: 16) {
                Text("Connecting…")
                    .foregroundColor(.white.opacity(0.8))
                ProgressView()
                    .tint(.white)
            }

        case .error(let message):
            VStack(spacing: 16) {
                Text(message)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
                    .font(.system(size: 14))

                connectButton

                Button("Cancel") {
                    viewModel.resetError()
                }
                .foregroundColor(.white.opacity(0.6))
            }

        case .authenticated:
            EmptyView() // handled via onChange
        }
    }

    private var connectButton: some View {
        Button {
            viewModel.startOAuth()
        } label: {
            Text("Connect Spotify")
                .font(.system(size: 16, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Color(red: 0.11, green: 0.73, blue: 0.33)) // Spotify green
                .foregroundColor(.white)
                .cornerRadius(50)
        }
    }
}
