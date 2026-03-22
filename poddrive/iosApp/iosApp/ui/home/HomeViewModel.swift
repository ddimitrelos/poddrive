import Foundation
import shared

/// Drives the podcast library Shows screen on iOS (Story 2.1).
///
/// Delegates to the KMP `SyncPodcastLibraryUseCase` which fetches from Spotify,
/// upserts to SQLDelight, then returns the full library with unheard counts.
@MainActor
final class HomeViewModel: ObservableObject {

    enum State {
        case loading
        case success([Podcast])
        case error(String, cached: [Podcast])
    }

    @Published var state: State = .loading

    private let syncUseCase: SyncPodcastLibraryUseCase
    private let podcastRepository: any PodcastRepository

    init(syncUseCase: SyncPodcastLibraryUseCase, podcastRepository: any PodcastRepository) {
        self.syncUseCase = syncUseCase
        self.podcastRepository = podcastRepository
    }

    func loadLibrary() {
        state = .loading
        Task {
            do {
                let result = try await syncUseCase.execute()
                if let success = result as? ResultSuccess<NSArray, AppError> {
                    if let podcasts = success.value as? [Podcast] {
                        state = .success(podcasts)
                    } else if let array = success.value as? KotlinArray<Podcast> {
                        let podcasts = (0..<Int(array.size)).map { array.get(index: Int32($0))! }
                        state = .success(podcasts)
                    } else {
                        state = .error(
                            "Unexpected result type from sync: \(type(of: success.value))",
                            cached: getCachedPodcasts()
                        )
                    }
                } else if let failure = result as? ResultFailure<NSArray, AppError> {
                    let cached = getCachedPodcasts()
                    state = .error("Could not refresh library. \(failure.error)", cached: cached)
                }
            } catch {
                let cached = getCachedPodcasts()
                state = .error("Unexpected error: \(error.localizedDescription)", cached: cached)
            }
        }
    }

    private func getCachedPodcasts() -> [Podcast] {
        let result = podcastRepository.getAllWithUnheardCount()
        if let podcasts = result as? [Podcast] {
            return podcasts
        } else if let array = result as? KotlinArray<Podcast> {
            return (0..<Int(array.size)).map { array.get(index: Int32($0))! }
        }
        return []
    }
}
