import Foundation
import shared

/// Drives the podcast library Shows screen on iOS (Stories 2.1, 2.2).
///
/// Delegates to KMP use cases for library sync and tier assignment.
@MainActor
final class HomeViewModel: ObservableObject {

    enum State {
        case loading
        case success([Podcast])
        case error(String, cached: [Podcast])
    }

    @Published var state: State = .loading

    /// Controls the tier `.confirmationDialog`. True = sheet visible.
    @Published var showTierSheet: Bool = false

    /// Active tier filter. `.none` = "All" (default). Resets to `.none` on app restart (ephemeral).
    @Published var selectedFilter: EpisodeTier = .none

    /// The podcast currently targeted by the tier sheet. Set before showTierSheet = true.
    /// P-15: @Published so SwiftUI re-renders the confirmationDialog title on assignment.
    @Published private(set) var selectedPodcast: Podcast? = nil

    private let syncUseCase: SyncPodcastLibraryUseCase
    private let setTierUseCase: SetEpisodeTierUseCase
    private let podcastRepository: any PodcastRepository

    /// In-progress load task — cancelled before starting a new sync.
    private var loadTask: Task<Void, Never>?

    /// In-progress setTier task — P-10: stored so it can be cancelled on dealloc or re-entry.
    private var setTierTask: Task<Void, Never>?

    init(
        syncUseCase: SyncPodcastLibraryUseCase,
        setTierUseCase: SetEpisodeTierUseCase,
        podcastRepository: any PodcastRepository
    ) {
        self.syncUseCase = syncUseCase
        self.setTierUseCase = setTierUseCase
        self.podcastRepository = podcastRepository
    }

    deinit {
        // P-10: cancel tasks on dealloc to prevent dangling references
        loadTask?.cancel()
        setTierTask?.cancel()
    }

    func loadLibrary() {
        loadTask?.cancel()
        state = .loading
        loadTask = Task {
            do {
                let result = try await syncUseCase.execute()
                if let success = result as? ResultSuccess<NSArray, AppError> {
                    if let podcasts = success.value as? [Podcast] {
                        state = .success(podcasts)
                    } else if let array = success.value as? KotlinArray<Podcast> {
                        // P-3: compactMap replaces force-unwrap to avoid crash on nil elements
                        let podcasts = (0..<Int(array.size)).compactMap { array.get(index: Int32($0)) as? Podcast }
                        state = .success(podcasts)
                    } else {
                        state = .error(
                            "Unexpected result type from sync: \(type(of: success.value))",
                            cached: await getCachedPodcastsAsync()
                        )
                    }
                } else if let failure = result as? ResultFailure<NSArray, AppError> {
                    state = .error("Could not refresh library. \(failure.error)", cached: await getCachedPodcastsAsync())
                } else {
                    state = .error("Unexpected sync result type: \(type(of: result))", cached: await getCachedPodcastsAsync())
                }
            } catch {
                state = .error("Unexpected error: \(error.localizedDescription)", cached: await getCachedPodcastsAsync())
            }
        }
    }

    /// Opens the tier action sheet for [podcast].
    func showTierSheetFor(_ podcast: Podcast) {
        selectedPodcast = podcast
        showTierSheet = true
    }

    /// Sets tier for the currently selected podcast, then refreshes the list.
    func setTier(_ tier: EpisodeTier) {
        guard let podcast = selectedPodcast else { return }
        showTierSheet = false
        let podcastId = podcast.id
        selectedPodcast = nil  // P-16: nil after capturing podcastId, before dispatch starts

        setTierTask?.cancel()  // P-10: cancel any in-flight tier write
        // P-9: use Task.detached sub-task to run synchronous KMP call off @MainActor,
        //      instead of withCheckedContinuation + DispatchQueue (which could hang if
        //      the KMP/ObjC bridge raises an unhandled exception).
        let uc = self.setTierUseCase  // capture let-property on @MainActor before detaching
        setTierTask = Task { [weak self] in
            guard let self else { return }
            let result = await Task.detached(priority: .userInitiated) { [uc, id = podcastId, t = tier] in
                uc.execute(podcastId: id, tier: t)
            }.value
            guard !Task.isCancelled else { return }
            if result is ResultSuccess<KotlinUnit, AppError> {
                self.state = .success(await self.getCachedPodcastsAsync())
            } else if let failure = result as? ResultFailure<KotlinUnit, AppError> {
                let current: [Podcast]
                if case .success(let list) = self.state { current = list } else { current = [] }
                self.state = .error("Could not update tier. \(failure.error)", cached: current)
            }
        }
    }

    /// Fetches cached podcasts from the repository on a background thread.
    /// P-9: uses Task.detached instead of withCheckedContinuation + DispatchQueue.
    /// P-3: compactMap replaces force-unwrap in KotlinArray branch.
    private func getCachedPodcastsAsync() async -> [Podcast] {
        let repo = self.podcastRepository
        return await Task.detached(priority: .userInitiated) {
            let result = repo.getAllWithUnheardCount()
            if let arr = result as? [Podcast] {
                return arr
            } else if let array = result as? KotlinArray<Podcast> {
                // P-3: compactMap — avoids crash if any element is nil
                return (0..<Int(array.size)).compactMap { array.get(index: Int32($0)) as? Podcast }
            }
            return []
        }.value
    }
}
