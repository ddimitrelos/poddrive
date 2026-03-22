import AuthenticationServices
import Foundation
import shared  // KMP shared framework

/// Drives the Spotify OAuth PKCE flow on iOS.
///
/// Opens `ASWebAuthenticationSession` with `prefersEphemeralWebBrowserSession = true`
/// (no cookies / session sharing — required for security).
/// Validates the CSRF `state` parameter on redirect before exchanging the code.
/// Extracts the `code` from the callback URL and calls through to the KMP
/// `AuthenticateWithSpotifyUseCase`.
///
/// Configure `SPOTIFY_CLIENT_ID` via an `.xcconfig` build setting injected into Info.plist.
@MainActor
final class OnboardingViewModel: NSObject, ObservableObject {

    enum State {
        case idle
        case launchingBrowser
        case exchangingTokens
        case authenticated
        case error(String)
    }

    @Published var state: State = .idle

    private var authManager: SpotifyAuthManager?
    private var useCase: AuthenticateWithSpotifyUseCase?
    /// Main Ktor client for Spotify Web API calls (api.spotify.com).
    /// Configured with bearer Auth + silent token refresh via authManager (Story 1.4).
    /// Stored for use by future authenticated screens (library sync, playback).
    private var apiHttpClient: HttpClient?
    private var pendingVerifier: String?
    /// Stored CSRF state value — must match the `state` parameter in the redirect callback.
    private var pendingState: String?
    private var authSession: ASWebAuthenticationSession?

    override init() {
        super.init()
        setupKMPDependencies()
    }

    // MARK: - OAuth flow

    func startOAuth() {
        guard let authManager = authManager else {
            state = .error("Auth manager not initialised. Set SPOTIFY_CLIENT_ID.")
            return
        }

        let verifier = authManager.generateCodeVerifier()
        let challenge = authManager.generateCodeChallenge(verifier: verifier)
        let stateParam = UUID().uuidString
        let urlString = authManager.buildAuthorizationUrl(
            codeChallenge: challenge,
            state: stateParam
        )

        guard let url = URL(string: urlString) else {
            state = .error("Failed to build authorization URL.")
            return
        }

        pendingVerifier = verifier
        pendingState = stateParam  // P1-1: persist for CSRF validation on redirect
        state = .launchingBrowser

        let session = ASWebAuthenticationSession(
            url: url,
            callbackURLScheme: "poddrive"
        ) { [weak self] callbackURL, error in
            guard let self else { return }

            if let error {
                let nsError = error as NSError
                // P2-5: Check both the error domain and code to correctly identify user
                // cancellation vs a real session error. Using only code == 1 can match
                // errors from other frameworks.
                let isCancellation =
                    nsError.domain == ASWebAuthenticationSessionErrorDomain &&
                    nsError.code == ASWebAuthenticationSessionError.canceledLogin.rawValue
                if isCancellation {
                    Task { @MainActor in
                        self.pendingVerifier = nil
                        self.pendingState = nil
                        self.state = .idle
                    }
                } else {
                    Task { @MainActor in
                        self.pendingVerifier = nil
                        self.pendingState = nil
                        self.state = .error("OAuth failed: \(error.localizedDescription)")
                    }
                }
                return
            }

            guard let callbackURL,
                  let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)
            else {
                Task { @MainActor in self.state = .error("OAuth redirect URL was invalid.") }
                return
            }

            let queryItems = components.queryItems
            let code = queryItems?.first(where: { $0.name == "code" })?.value
            let returnedState = queryItems?.first(where: { $0.name == "state" })?.value

            // P1-1: Validate CSRF state before processing the authorization code.
            guard returnedState != nil, returnedState == self.pendingState else {
                Task { @MainActor in
                    self.pendingVerifier = nil
                    self.pendingState = nil
                    self.state = .error("OAuth state mismatch — possible CSRF attack. Please try again.")
                }
                return
            }

            guard let code = code, let verifier = self.pendingVerifier else {
                Task { @MainActor in self.state = .error("OAuth redirect missing code parameter.") }
                return
            }

            self.pendingVerifier = nil
            self.pendingState = nil
            Task { await self.exchangeTokens(code: code, verifier: verifier) }
        }

        session.prefersEphemeralWebBrowserSession = true
        session.presentationContextProvider = self
        session.start()
        authSession = session
    }

    func resetError() {
        state = .idle
    }

    // MARK: - Private

    private func exchangeTokens(code: String, verifier: String) async {
        await MainActor.run { state = .exchangingTokens }

        guard let useCase else {
            await MainActor.run { state = .error("Use case not initialised.") }
            return
        }

        do {
            // KMP suspend function bridged via kotlin-native coroutines
            let result = try await useCase.execute(authCode: code, codeVerifier: verifier)
            await MainActor.run {
                if result is ResultSuccess<KotlinUnit, AppError> {
                    state = .authenticated
                } else if let failure = result as? ResultFailure<KotlinUnit, AppError> {
                    state = .error("Token exchange failed: \(failure.error)")
                }
            }
        } catch {
            await MainActor.run { state = .error("Unexpected error: \(error.localizedDescription)") }
        }
    }

    private func setupKMPDependencies() {
        // Read client ID injected via xcconfig → Info.plist at build time.
        // In the Xcode project, add SPOTIFY_CLIENT_ID to Debug.xcconfig / Release.xcconfig,
        // then expose it in Info.plist as: <key>SPOTIFY_CLIENT_ID</key><string>$(SPOTIFY_CLIENT_ID)</string>
        let clientId = Bundle.main.infoDictionary?["SPOTIFY_CLIENT_ID"] as? String ?? ""
        guard !clientId.isEmpty else {
            state = .error("SPOTIFY_CLIENT_ID not configured. Add to xcconfig.")
            return
        }

        // Story 1.4: Split into two Ktor clients to avoid circular refresh loops.
        //
        // authHttpClient — plain client (no Auth plugin) for SpotifyAuthManager token ops.
        //   Token endpoints at accounts.spotify.com use form-body credentials, not bearer auth.
        //
        // apiHttpClient  — full client with bearer Auth + silent refresh via manager.
        //   Used for all api.spotify.com calls in future authenticated stories.
        let secureStorage = SecureStorage()
        let authHttpClient = SpotifyHttpClientKt.createAuthHttpClient()
        let manager = SpotifyAuthManager(
            tokenStorage: secureStorage,
            httpClient: authHttpClient,
            clientId: clientId
        )
        // manager (SpotifyAuthManager) conforms to the KMP TokenRefresher protocol —
        // no manual bridging required; KMP exports the fun interface as a Swift protocol.
        let mainApiClient = SpotifyHttpClientKt.createSpotifyHttpClient(
            tokenStorage: secureStorage,
            tokenRefresher: manager
        )
        apiHttpClient = mainApiClient
        authManager = manager
        useCase = AuthenticateWithSpotifyUseCase(authManager: manager)
    }
}

// MARK: - ASWebAuthenticationPresentationContextProviding

extension OnboardingViewModel: ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}
