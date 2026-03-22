import SwiftUI
import shared

/// Management-mode home screen with bottom navigation (Shows / Discover / Settings).
/// Shows tab displays the podcast library from SyncPodcastLibraryUseCase. (Story 2.1)
struct HomeView: View {

    @StateObject private var viewModel: HomeViewModel
    private let revokeUseCase: RevokeSpotifyAccessUseCase
    private let onRevoked: () -> Void

    init(
        syncUseCase: SyncPodcastLibraryUseCase,
        podcastRepository: any PodcastRepository,
        revokeUseCase: RevokeSpotifyAccessUseCase,
        onRevoked: @escaping () -> Void
    ) {
        _viewModel = StateObject(
            wrappedValue: HomeViewModel(syncUseCase: syncUseCase, podcastRepository: podcastRepository)
        )
        self.revokeUseCase = revokeUseCase
        self.onRevoked = onRevoked
    }

    var body: some View {
        TabView {
            ShowsTab(viewModel: viewModel)
                .tabItem { Label("Shows", systemImage: "music.note.list") }

            Text("Discover — Coming in a future story")
                .tabItem { Label("Discover", systemImage: "magnifyingglass") }

            SettingsView(revokeUseCase: revokeUseCase, onRevoked: onRevoked)
                .tabItem { Label("Settings", systemImage: "gear") }
        }
        .task { viewModel.loadLibrary() }
    }
}

private struct ShowsTab: View {
    @ObservedObject var viewModel: HomeViewModel

    var body: some View {
        NavigationView {
            Group {
                switch viewModel.state {
                case .loading:
                    ProgressView("Loading library…")
                case .success(let podcasts):
                    podcastList(podcasts)
                case .error(let message, let cached):
                    ScrollView {
                        Text(message)
                            .foregroundColor(.red)
                            .padding()
                        if !cached.isEmpty {
                            podcastList(cached)
                        }
                    }
                }
            }
            .navigationTitle("Shows")
        }
    }

    private func podcastList(_ podcasts: [Podcast]) -> some View {
        List(podcasts, id: \.id) { podcast in
            ShowTileRow(podcast: podcast)
        }
        .listStyle(.plain)
    }
}

private struct ShowTileRow: View {
    let podcast: Podcast

    var body: some View {
        HStack(spacing: 12) {
            // Artwork placeholder (52×52dp, UX-DR6) — AsyncImage / SDWebImage in future story
            RoundedRectangle(cornerRadius: 6)
                .fill(Color(.systemGray5))
                .frame(width: 52, height: 52)
                .overlay(
                    Text(String(podcast.name.prefix(1)))
                        .font(.headline)
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(podcast.name)
                    .font(.body)
                    .lineLimit(1)
                Text(podcast.unheardEpisodeCount > 0 ? "\(podcast.unheardEpisodeCount) new" : "Up to date")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            if podcast.tier != EpisodeTier.none {
                TierBadgeView(tier: podcast.tier)
            }
        }
        .padding(.vertical, 4)
        .frame(minHeight: 44)  // ≥44pt touch target (UX-DR9)
    }
}

private struct TierBadgeView: View {
    let tier: EpisodeTier

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: tier == .loved ? "heart.fill" : "hand.thumbsup.fill")
                .font(.caption2)
            Text(tier == .loved ? "Loved" : "Liked")
                .font(.caption2)
        }
        .foregroundColor(tier == .loved ? Color(hex: "F87171") : Color(hex: "60A5FA"))
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(
            (tier == .loved ? Color(hex: "F87171") : Color(hex: "60A5FA")).opacity(0.13)
        )
        .cornerRadius(4)
        .accessibilityLabel(tier == .loved ? "Loved podcast" : "Liked podcast")
    }
}

// MARK: - Color hex init (private extension)
private extension Color {
    init(hex: String) {
        let scanner = Scanner(string: hex)
        var rgb: UInt64 = 0
        scanner.scanHexInt64(&rgb)
        let r = Double((rgb >> 16) & 0xFF) / 255
        let g = Double((rgb >> 8) & 0xFF) / 255
        let b = Double(rgb & 0xFF) / 255
        self.init(red: r, green: g, blue: b)
    }
}
