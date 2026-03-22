import SwiftUI
import shared

/// Management-mode home screen with bottom navigation (Shows / Discover / Settings).
/// Stories 2.1, 2.2: Shows tab with library sync + LOVED/LIKED tier assignment.
struct HomeView: View {

    @StateObject private var viewModel: HomeViewModel
    private let revokeUseCase: RevokeSpotifyAccessUseCase
    private let onRevoked: () -> Void

    init(
        syncUseCase: SyncPodcastLibraryUseCase,
        setTierUseCase: SetEpisodeTierUseCase,
        podcastRepository: any PodcastRepository,
        revokeUseCase: RevokeSpotifyAccessUseCase,
        onRevoked: @escaping () -> Void
    ) {
        _viewModel = StateObject(wrappedValue: HomeViewModel(
            syncUseCase: syncUseCase,
            setTierUseCase: setTierUseCase,
            podcastRepository: podcastRepository
        ))
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
        // Tier action sheet — iOS native equivalent of Android ModalBottomSheet (UX-DR9)
        .confirmationDialog(
            viewModel.selectedPodcast?.name ?? "Set Tier",
            isPresented: $viewModel.showTierSheet,
            titleVisibility: .visible
        ) {
            Button("Set Loved") { viewModel.setTier(EpisodeTier.loved) }
            Button("Set Liked") { viewModel.setTier(EpisodeTier.liked) }
            Button("Remove Tier", role: .destructive) { viewModel.setTier(EpisodeTier.none) }
            Button("Cancel", role: .cancel) { }
        }
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
                    podcastListWithFilter(allPodcasts: podcasts)
                case .error(let message, let cached):
                    ScrollView {
                        Text(message)
                            .foregroundColor(.red)
                            .padding()
                        if !cached.isEmpty {
                            // P-13: no filter row in Error state (spec anti-pattern)
                            podcastListNoFilter(podcasts: cached)
                        }
                    }
                }
            }
            .navigationTitle("Shows")
        }
    }

    /// Plain list with no filter row — used in Error state (spec anti-pattern: no filter in error).
    @ViewBuilder
    private func podcastListNoFilter(podcasts: [Podcast]) -> some View {
        List(podcasts, id: \.id) { podcast in
            ShowTileRow(podcast: podcast)
                .onLongPressGesture {
                    viewModel.showTierSheetFor(podcast)
                }
        }
        .listStyle(.plain)
    }

    @ViewBuilder
    private func podcastListWithFilter(allPodcasts: [Podcast]) -> some View {
        let filtered: [Podcast] = {
            // P-20: removed bare `default` — using @unknown default so the compiler warns
            //        when a new EpisodeTier case is added to the KMP enum.
            switch viewModel.selectedFilter {
            case .none:  return allPodcasts
            case .loved: return allPodcasts.filter { $0.tier == .loved }
            case .liked: return allPodcasts.filter { $0.tier == .liked }
            @unknown default: return allPodcasts
            }
        }()

        VStack(spacing: 0) {
            TierFilterRow(selected: $viewModel.selectedFilter)
            if filtered.isEmpty && viewModel.selectedFilter != .none {
                // Empty filtered state (iOS 16 — no ContentUnavailableView)
                let label = viewModel.selectedFilter == .loved ? "Loved" : "Liked"
                VStack(spacing: 12) {
                    Image(systemName: viewModel.selectedFilter == .loved ? "heart.fill" : "hand.thumbsup.fill")
                        .font(.largeTitle)
                        .foregroundColor(.secondary)
                    Text("No \(label) shows yet")
                        .font(.headline)
                    Text("Long-press a show to tag it")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding()
            } else {
                List(filtered, id: \.id) { podcast in
                    ShowTileRow(podcast: podcast)
                        .onLongPressGesture {
                            viewModel.showTierSheetFor(podcast)
                        }
                }
                .listStyle(.plain)
            }
        }
    }
}

private struct ShowTileRow: View {
    let podcast: Podcast

    var body: some View {
        HStack(spacing: 12) {
            // Artwork placeholder (52×52pt) — AsyncImage / SDWebImage in future story
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

// MARK: - TierBadgeView — FULL variant (icon + label)
// Never colour-only: icon + label provides both visual signals (UX-DR11)
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

// MARK: - TierBadgeIconOnly — ICON_ONLY variant (decorative dot + icon, no label)
// Used in drive mode (Story 3.x+). Distinct from TierBadgeView FULL variant.
// Never colour-only: icon provides non-colour signal (UX-DR5, UX-DR11).
// P-17: 24pt container / 16pt icon (was 8pt/4pt — illegible). Decorative-only, no tap target.
// P-19: exhaustive switch replaces binary ternary (future tiers would silently use Liked style).
private struct TierBadgeIconOnly: View {
    let tier: EpisodeTier

    private var tierStyle: (color: Color, icon: String)? {
        switch tier {
        case .loved: return (Color(hex: "F87171"), "heart.fill")
        case .liked: return (Color(hex: "60A5FA"), "hand.thumbsup.fill")
        default:     return nil  // .none and any unrecognised future tier: don't render
        }
    }

    var body: some View {
        if let style = tierStyle {
            ZStack {
                Circle()
                    .fill(style.color.opacity(0.2))
                    .frame(width: 24, height: 24)
                Image(systemName: style.icon)
                    .font(.system(size: 12))
                    .foregroundColor(style.color)
            }
            .accessibilityHidden(true)  // P-17: purely decorative indicator in drive mode
        }
    }
}

// MARK: - TierFilterRow — three pill filter tabs (UX-DR6)
private struct TierFilterRow: View {
    @Binding var selected: EpisodeTier

    var body: some View {
        HStack(spacing: 8) {
            TierPill(label: "All", icon: nil, tier: nil, selected: selected == .none) {
                selected = .none
            }
            TierPill(label: "Loved", icon: "heart.fill", tier: .loved, selected: selected == .loved) {
                selected = .loved
            }
            TierPill(label: "Liked", icon: "hand.thumbsup.fill", tier: .liked, selected: selected == .liked) {
                selected = .liked
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        // P-12: expose the HStack as an accessibility container so VoiceOver
        //        announces it as a tab group, not just a sequence of isolated buttons.
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Filter tabs")
    }
}

private struct TierPill: View {
    let label: String
    let icon: String?
    let tier: EpisodeTier?
    let selected: Bool
    let action: () -> Void

    private var accentColor: Color {
        switch tier {
        case .loved: return Color(hex: "F87171")
        case .liked: return Color(hex: "60A5FA")
        default:     return Color.primary
        }
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let icon {
                    Image(systemName: icon).font(.caption2)
                }
                Text(label).font(.subheadline)
            }
            .foregroundColor(selected ? .white : accentColor)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(selected ? accentColor : accentColor.opacity(0.1))
            .clipShape(Capsule())
            .overlay(Capsule().stroke(accentColor, lineWidth: selected ? 0 : 1))
        }
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityLabel(label)
    }
}

// MARK: - Color hex init (reused from Story 2.1)
private extension Color {
    init(hex: String) {
        let scanner = Scanner(string: hex.hasPrefix("#") ? String(hex.dropFirst()) : hex)
        var rgb: UInt64 = 0
        scanner.scanHexInt64(&rgb)
        let r = Double((rgb >> 16) & 0xFF) / 255
        let g = Double((rgb >> 8) & 0xFF) / 255
        let b = Double(rgb & 0xFF) / 255
        self.init(red: r, green: g, blue: b)
    }
}
