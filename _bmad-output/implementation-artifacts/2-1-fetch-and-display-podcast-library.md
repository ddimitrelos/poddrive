# Story 2.1: Fetch & Display Podcast Library

Status: done

## Story

As an **authenticated user**,
I want to see all my followed Spotify podcasts with their unheard episode counts,
So that I can review my full podcast queue at a glance.

## Acceptance Criteria

1. **Given** the user is authenticated **When** the Shows screen loads **Then** all followed Spotify podcasts are fetched via `SyncPodcastLibraryUseCase` and displayed as `ShowTile` components (UX-DR6).
2. **Given** the Shows screen is loaded **Then** each `ShowTile` shows: podcast artwork, name, unheard episode count, and `TierBadge` if a tier is set (FR4, FR7).
3. **Given** the user is on the authenticated home **Then** the management-mode UI is active with bottom nav (Shows / Discover / Settings) (UX-DR3).
4. **Given** the library has been fetched **Then** unheard episodes are correctly identified as episodes where `resume_point.fully_played == false` from the Spotify API (FR7).
5. **Given** the authenticated view **Then** the Settings tab remains accessible and the "Disconnect Spotify" flow still works end-to-end.
6. **Given** the API call succeeds **Then** the podcast list is persisted to SQLDelight and immediately displayed in the UI.
7. **Given** the API call fails **Then** a user-visible error message is shown; if there is cached data from a previous sync it is displayed below the error.
8. **Given** the Shows screen **Then** all tap targets meet the ≥44dp minimum (UX-DR9).

## Tasks / Subtasks

### T1 — Add `unheardEpisodeCount` to `Podcast` domain model (AC: 2)
- [x] In `Podcast.kt`, add `val unheardEpisodeCount: Int` field (after `episodeCount`):
  ```kotlin
  data class Podcast(
      val id: String,
      val name: String,
      val description: String,
      val imageUrl: String,
      val episodeCount: Int,
      val unheardEpisodeCount: Int,    // ← ADD
      val tier: EpisodeTier,
      val lastSyncedAt: Long
  )
  ```
- [x] Add KDoc: "Number of unheard (is_played = 0) episodes from the local SQLDelight cache. Computed at query time via JOIN — not stored as a column. (FR7)"
- [x] Update `PodcastQueries.sq` — add `selectAllWithUnheardCount` query to get podcasts + unheard counts in one query:
  ```sql
  selectAllWithUnheardCount:
  SELECT p.id, p.name, p.description, p.image_url, p.episode_count, p.tier, p.last_synced_at,
         COALESCE(COUNT(e.id), 0) AS unheard_count
  FROM podcasts p
  LEFT JOIN episodes e ON p.id = e.podcast_id AND e.is_played = 0
  GROUP BY p.id
  ORDER BY p.name ASC;
  ```
- [x] Add `selectAllWithUnheardCountByTier` for future filter support (Story 2.3 — add it now to avoid schema churn):
  ```sql
  selectAllWithUnheardCountByTier:
  SELECT p.id, p.name, p.description, p.image_url, p.episode_count, p.tier, p.last_synced_at,
         COALESCE(COUNT(e.id), 0) AS unheard_count
  FROM podcasts p
  LEFT JOIN episodes e ON p.id = e.podcast_id AND e.is_played = 0
  WHERE p.tier = ?
  GROUP BY p.id
  ORDER BY p.name ASC;
  ```

### T2 — Add Spotify Library API response models to `SpotifyModels.kt` (AC: 4)
- [x] Append to `shared/src/commonMain/kotlin/com/poddrive/api/SpotifyModels.kt`:
  ```kotlin
  // ─── Shows (GET /v1/me/shows) ───────────────────────────────────────────────

  @Serializable
  data class SpotifyShowsResponse(
      @SerialName("items") val items: List<SpotifyShowItem>,
      @SerialName("next") val next: String? = null,
      @SerialName("total") val total: Int = 0
  )

  @Serializable
  data class SpotifyShowItem(
      @SerialName("show") val show: SpotifyShow
  )

  @Serializable
  data class SpotifyShow(
      @SerialName("id") val id: String,
      @SerialName("uri") val uri: String,
      @SerialName("name") val name: String,
      @SerialName("description") val description: String,
      @SerialName("images") val images: List<SpotifyImage> = emptyList(),
      @SerialName("total_episodes") val totalEpisodes: Int = 0
  )

  @Serializable
  data class SpotifyImage(
      @SerialName("url") val url: String,
      @SerialName("height") val height: Int? = null,
      @SerialName("width") val width: Int? = null
  )

  // ─── Episodes (GET /v1/shows/{id}/episodes) ─────────────────────────────────

  @Serializable
  data class SpotifyEpisodesResponse(
      @SerialName("items") val items: List<SpotifyEpisodeObject>,
      @SerialName("next") val next: String? = null,
      @SerialName("total") val total: Int = 0
  )

  @Serializable
  data class SpotifyEpisodeObject(
      @SerialName("id") val id: String,
      @SerialName("uri") val uri: String,
      @SerialName("name") val name: String,
      @SerialName("duration_ms") val durationMs: Long,
      @SerialName("release_date") val releaseDate: String,
      @SerialName("resume_point") val resumePoint: SpotifyResumePoint? = null
  )

  @Serializable
  data class SpotifyResumePoint(
      @SerialName("fully_played") val fullyPlayed: Boolean,
      @SerialName("resume_position_ms") val resumePositionMs: Long = 0L
  )
  ```
- [x] All models: `@Serializable` + `ignoreUnknownKeys` handled by `Json { ignoreUnknownKeys = true }` in the client

### T3 — Create `SpotifyLibraryApiClient.kt` (AC: 1, 4)
- [x] Create `shared/src/commonMain/kotlin/com/poddrive/api/SpotifyLibraryApiClient.kt`:
  ```kotlin
  package com.poddrive.api

  import com.poddrive.domain.model.AppError
  import com.poddrive.domain.model.Result
  import io.ktor.client.HttpClient
  import io.ktor.client.call.body
  import io.ktor.client.request.get
  import io.ktor.client.request.parameter
  import io.ktor.http.isSuccess
  import kotlinx.coroutines.CancellationException

  /**
   * Fetches podcast library data from the Spotify Web API.
   *
   * Uses the [apiHttpClient] (bearer-token authenticated, Auth plugin handles silent refresh).
   * Calls api.spotify.com — never accounts.spotify.com.
   *
   * Pagination: follows `next` links until null.
   * Rate limiting: handled transparently by Ktor's HttpRequestRetry plugin (3 retries, exp. backoff).
   *
   * [Source: architecture.md#API & Communication Patterns — "All Spotify Web API calls via SpotifyApiClient"]
   */
  class SpotifyLibraryApiClient(private val httpClient: HttpClient) {

      /**
       * Fetches ALL followed Spotify shows (podcasts) for the authenticated user.
       * Follows `next` pagination links until exhausted.
       *
       * Requires scope: `user-library-read`
       */
      suspend fun fetchFollowedShows(): Result<List<SpotifyShow>, AppError> {
          return try {
              val items = mutableListOf<SpotifyShowItem>()
              var url: String? = "$BASE_URL/v1/me/shows"
              while (url != null) {
                  val response = if (url == "$BASE_URL/v1/me/shows") {
                      httpClient.get(url) { parameter("limit", 50) }
                  } else {
                      httpClient.get(url)  // subsequent pages include limit in the next URL
                  }
                  if (!response.status.isSuccess()) {
                      return Result.Failure(
                          AppError.SpotifyApiError(response.status.value, response.status.description)
                      )
                  }
                  val page = response.body<SpotifyShowsResponse>()
                  items += page.items
                  url = page.next
              }
              Result.Success(items.map { it.show })
          } catch (e: CancellationException) {
              throw e
          } catch (e: Exception) {
              Result.Failure(AppError.Unknown(e))
          }
      }

      /**
       * Fetches episodes for a given [showBaseId] (base ID, NOT the "spotify:show:" URI).
       * Follows `next` pagination links. Passes `market=from_token` so that `resume_point`
       * is populated in each episode object.
       *
       * Requires scope: `user-library-read`
       */
      suspend fun fetchShowEpisodes(showBaseId: String): Result<List<SpotifyEpisodeObject>, AppError> {
          return try {
              val items = mutableListOf<SpotifyEpisodeObject>()
              var url: String? = "$BASE_URL/v1/shows/$showBaseId/episodes"
              while (url != null) {
                  val response = if (url == "$BASE_URL/v1/shows/$showBaseId/episodes") {
                      httpClient.get(url) {
                          parameter("limit", 50)
                          parameter("market", "from_token")
                      }
                  } else {
                      httpClient.get(url)
                  }
                  if (!response.status.isSuccess()) {
                      return Result.Failure(
                          AppError.SpotifyApiError(response.status.value, response.status.description)
                      )
                  }
                  val page = response.body<SpotifyEpisodesResponse>()
                  items += page.items
                  url = page.next
              }
              Result.Success(items)
          } catch (e: CancellationException) {
              throw e
          } catch (e: Exception) {
              Result.Failure(AppError.Unknown(e))
          }
      }

      companion object {
          const val BASE_URL = "https://api.spotify.com"
      }
  }
  ```
- [x] IMPORTANT: `httpClient` must be the `apiHttpClient` from `PodDriveApplication` (has bearer Auth) — NOT `createAuthHttpClient()` (no bearer Auth)

### T4 — Create `PodcastRepository` interface (AC: 1, 6)
- [x] Create `shared/src/commonMain/kotlin/com/poddrive/repository/PodcastRepository.kt`:
  ```kotlin
  package com.poddrive.repository

  import com.poddrive.domain.model.Podcast

  /**
   * Data access interface for the `podcasts` SQLDelight table.
   * Implementations live in `com.poddrive.storage` — never in the UI layer.
   * [Source: architecture.md#Repository Pattern]
   */
  interface PodcastRepository {
      /** Upsert all podcasts. Preserves existing `tier` values — does NOT overwrite tier on upsert. */
      fun upsertAll(podcasts: List<Podcast>)
      /** Returns all podcasts with their unheard episode count (LEFT JOIN with episodes table). */
      fun getAllWithUnheardCount(): List<Podcast>
      /** Returns all podcasts for the given tier with unheard counts. */
      fun getByTierWithUnheardCount(tier: String): List<Podcast>
  }
  ```

### T5 — Create `EpisodeRepository` interface (AC: 4, 6)
- [x] Create `shared/src/commonMain/kotlin/com/poddrive/repository/EpisodeRepository.kt`:
  ```kotlin
  package com.poddrive.repository

  import com.poddrive.domain.model.Episode

  /**
   * Data access interface for the `episodes` and `episode_tiers` SQLDelight tables.
   * Implementations live in `com.poddrive.storage` — never in the UI layer.
   * [Source: architecture.md#Repository Pattern]
   */
  interface EpisodeRepository {
      /** Upsert all episodes. Preserves existing `episode_tiers` rows — uses INSERT OR UPDATE. */
      fun upsertAll(episodes: List<Episode>)
      /** Returns the number of unheard episodes for the given podcast URI. */
      fun getUnheardCountForPodcast(podcastId: String): Int
  }
  ```

### T6 — Create `SqlDelightPodcastRepository.kt` (AC: 1, 6)
- [x] Create `shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightPodcastRepository.kt`:
  ```kotlin
  package com.poddrive.storage

  import com.poddrive.db.PodDrive
  import com.poddrive.domain.model.EpisodeTier
  import com.poddrive.domain.model.Podcast
  import com.poddrive.repository.PodcastRepository

  /**
   * SQLDelight-backed implementation of [PodcastRepository].
   * All methods execute synchronously on the calling coroutine's dispatcher.
   * The `podcasts` table schema and queries are defined in PodcastQueries.sq.
   * [Source: architecture.md#Repository Pattern, AR3]
   */
  class SqlDelightPodcastRepository(private val db: PodDrive) : PodcastRepository {

      override fun upsertAll(podcasts: List<Podcast>) {
          db.podcastQueries.transaction {
              podcasts.forEach { podcast ->
                  db.podcastQueries.upsert(
                      id = podcast.id,
                      name = podcast.name,
                      description = podcast.description,
                      image_url = podcast.imageUrl,
                      episode_count = podcast.episodeCount.toLong(),
                      tier = podcast.tier.name,
                      last_synced_at = podcast.lastSyncedAt
                  )
              }
          }
      }

      override fun getAllWithUnheardCount(): List<Podcast> =
          db.podcastQueries.selectAllWithUnheardCount().executeAsList().map { row ->
              Podcast(
                  id = row.id,
                  name = row.name,
                  description = row.description,
                  imageUrl = row.image_url,
                  episodeCount = row.episode_count.toInt(),
                  unheardEpisodeCount = row.unheard_count.toInt(),
                  tier = EpisodeTier.valueOf(row.tier),
                  lastSyncedAt = row.last_synced_at
              )
          }

      override fun getByTierWithUnheardCount(tier: String): List<Podcast> =
          db.podcastQueries.selectAllWithUnheardCountByTier(tier).executeAsList().map { row ->
              Podcast(
                  id = row.id,
                  name = row.name,
                  description = row.description,
                  imageUrl = row.image_url,
                  episodeCount = row.episode_count.toInt(),
                  unheardEpisodeCount = row.unheard_count.toInt(),
                  tier = EpisodeTier.valueOf(row.tier),
                  lastSyncedAt = row.last_synced_at
              )
          }
  }
  ```
- [x] IMPORTANT: `PodDrive` is the SQLDelight generated class at `com.poddrive.db.PodDrive` — import as `import com.poddrive.db.PodDrive`
- [x] The `upsert` query in PodcastQueries.sq already uses INSERT OR REPLACE which preserves tier only if we include it in the INSERT. Since INSERT OR REPLACE replaces the whole row, use this logic: on upsert, query the existing tier first and preserve it, OR keep using INSERT OR REPLACE but pass the existing tier. The safest approach: `SqlDelightPodcastRepository.upsertAll` should query the existing tier before upserting:
  ```kotlin
  db.podcastQueries.upsert(
      id = podcast.id,
      name = podcast.name,
      description = podcast.description,
      image_url = podcast.imageUrl,
      episode_count = podcast.episodeCount.toLong(),
      // Preserve existing tier — SyncPodcastLibraryUseCase passes EpisodeTier.NONE
      // for new podcasts; the upsert preserves whatever is stored for existing ones.
      tier = db.podcastQueries.selectById(podcast.id).executeAsOneOrNull()?.tier ?: podcast.tier.name,
      last_synced_at = podcast.lastSyncedAt
  )
  ```

### T7 — Create `SqlDelightEpisodeRepository.kt` (AC: 4, 6)
- [x] Create `shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightEpisodeRepository.kt`:
  ```kotlin
  package com.poddrive.storage

  import com.poddrive.db.PodDrive
  import com.poddrive.domain.model.Episode
  import com.poddrive.domain.model.EpisodeTier
  import com.poddrive.repository.EpisodeRepository

  /**
   * SQLDelight-backed implementation of [EpisodeRepository].
   * [Source: architecture.md#Repository Pattern, AR3]
   */
  class SqlDelightEpisodeRepository(private val db: PodDrive) : EpisodeRepository {

      override fun upsertAll(episodes: List<Episode>) {
          db.episodeQueries.transaction {
              episodes.forEach { episode ->
                  db.episodeQueries.upsertEpisode(
                      id = episode.id,
                      podcast_id = episode.podcastId,
                      title = episode.title,
                      duration_ms = episode.durationMs,
                      published_at = episode.publishedAt,
                      is_played = if (episode.isPlayed) 1L else 0L
                  )
              }
          }
      }

      override fun getUnheardCountForPodcast(podcastId: String): Int =
          db.episodeQueries.selectUnplayedByPodcast(podcastId).executeAsList().size
  }
  ```

### T8 — Create `SyncPodcastLibraryUseCase.kt` (AC: 1, 4, 6, 7)
- [x] Create `shared/src/commonMain/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCase.kt`:
  ```kotlin
  package com.poddrive.usecase

  import com.poddrive.api.SpotifyLibraryApiClient
  import com.poddrive.domain.model.AppError
  import com.poddrive.domain.model.Episode
  import com.poddrive.domain.model.EpisodeTier
  import com.poddrive.domain.model.Podcast
  import com.poddrive.domain.model.Result
  import com.poddrive.repository.EpisodeRepository
  import com.poddrive.repository.PodcastRepository
  import kotlinx.coroutines.CancellationException

  /**
   * Fetches followed Spotify podcasts and their unheard episodes, persists to SQLDelight,
   * then returns the full library with unheard counts for immediate UI display. (FR4, FR7, FR8)
   *
   * Sync strategy: full sync — fetches all followed shows + all episodes per show.
   * Background-sync optimization (batch on BT connect, staleness indicator) is Story 2.5.
   *
   * Episode "unheard" detection: uses `resume_point.fully_played == false` from Spotify API.
   * A null `resume_point` (no play history) is treated as unheard.
   *
   * Tier inheritance: episodes inherit the parent podcast's tier at sync time.
   * The `episode_tiers` table stores per-episode tier overrides (managed in Stories 2.2+).
   *
   * Never throws — all errors returned as [Result.Failure].
   * [Source: architecture.md#Use Case rule, AR3]
   */
  class SyncPodcastLibraryUseCase(
      private val libraryApiClient: SpotifyLibraryApiClient,
      private val podcastRepository: PodcastRepository,
      private val episodeRepository: EpisodeRepository
  ) {
      suspend fun execute(): Result<List<Podcast>, AppError> {
          return try {
              // 1. Fetch all followed shows
              val showsResult = libraryApiClient.fetchFollowedShows()
              if (showsResult is Result.Failure) return showsResult

              val shows = (showsResult as Result.Success).value
              val now = currentTimeMillis()

              // 2. Map Spotify shows → Podcast domain models
              val podcasts = shows.map { show ->
                  Podcast(
                      id = show.uri,  // "spotify:show:xxx"
                      name = show.name,
                      description = show.description,
                      imageUrl = show.images.maxByOrNull { it.width ?: 0 }?.url ?: "",
                      episodeCount = show.totalEpisodes,
                      unheardEpisodeCount = 0,  // placeholder; recalculated by repository query
                      tier = EpisodeTier.NONE,  // new podcasts default to NONE; existing tier preserved by repo
                      lastSyncedAt = now
                  )
              }

              // 3. Upsert podcasts (preserves existing tiers)
              podcastRepository.upsertAll(podcasts)

              // 4. For each show, fetch episodes and upsert
              for (show in shows) {
                  val episodesResult = libraryApiClient.fetchShowEpisodes(show.id)
                  if (episodesResult is Result.Failure) {
                      // Non-fatal: continue with other shows, return partial results
                      continue
                  }
                  val episodes = (episodesResult as Result.Success).value
                      .map { ep ->
                          Episode(
                              id = ep.uri,  // "spotify:episode:xxx"
                              podcastId = show.uri,
                              title = ep.name,
                              durationMs = ep.durationMs,
                              publishedAt = parseSpotifyDateToEpochMs(ep.releaseDate),
                              isPlayed = ep.resumePoint?.fullyPlayed ?: false,
                              tier = EpisodeTier.NONE  // tier managed separately in episode_tiers table
                          )
                      }
                  episodeRepository.upsertAll(episodes)
              }

              // 5. Return updated library with unheard counts from DB
              Result.Success(podcastRepository.getAllWithUnheardCount())
          } catch (e: CancellationException) {
              throw e
          } catch (e: Exception) {
              Result.Failure(AppError.Unknown(e))
          }
      }
  }
  ```
- [x] Add `currentTimeMillis()` expect/actual or use `kotlin.system.getTimeMillis()`:
  - In KMP `commonMain`, use `kotlin.system.getTimeMillis()` → returns current epoch ms as `Long`.
  - Correct KMP import: there is no `kotlin.system.getTimeMillis()` in KMP. Use platform clock:
    - Add `expect fun currentTimeMillis(): Long` in `commonMain/api/` (or a utility file)
    - `actual fun currentTimeMillis(): Long = System.currentTimeMillis()` in androidMain
    - `actual fun currentTimeMillis(): Long = (platform.Foundation.NSDate.date().timeIntervalSince1970 * 1000).toLong()` in iosMain
    - File: `shared/src/commonMain/kotlin/com/poddrive/Platform.kt` already has `expect fun getPlatformName(): String` — add `expect fun currentTimeMillis(): Long` there
    - Or create a new file `shared/src/commonMain/kotlin/com/poddrive/util/Clock.kt`
- [x] Add `parseSpotifyDateToEpochMs()` internal function in the same file or a utility file:
  ```kotlin
  /**
   * Parses Spotify's `release_date` field to epoch milliseconds.
   * Handles three precision variants: "YYYY-MM-DD", "YYYY-MM", "YYYY".
   * Uses the proleptic Gregorian algorithm — no external library required.
   */
  internal fun parseSpotifyDateToEpochMs(releaseDate: String): Long {
      val parts = releaseDate.split("-")
      val year = parts.getOrNull(0)?.toIntOrNull() ?: return 0L
      val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
      val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
      // Proleptic Gregorian → Julian Day Number (standard Fliegel-Van Flandern algorithm)
      val a = (14 - month) / 12
      val y = year + 4800 - a
      val m = month + 12 * a - 3
      val jdn = day + (153 * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045L
      val epochDays = jdn - 2440588L  // JDN 2440588 = 1970-01-01
      return epochDays * 24L * 60L * 60L * 1000L
  }
  ```

### T9 — Update `Platform.kt` with `currentTimeMillis()` expect/actual (AC: 6)
- [x] In `shared/src/commonMain/kotlin/com/poddrive/Platform.kt`, add:
  ```kotlin
  /** Returns current time as Unix milliseconds. Used in use cases to record sync timestamps. */
  expect fun currentTimeMillis(): Long
  ```
- [x] In `shared/src/androidMain/kotlin/com/poddrive/Platform.kt`, add:
  ```kotlin
  actual fun currentTimeMillis(): Long = System.currentTimeMillis()
  ```
- [x] In `shared/src/iosMain/kotlin/com/poddrive/Platform.kt`, add:
  ```kotlin
  actual fun currentTimeMillis(): Long =
      (platform.Foundation.NSDate.date().timeIntervalSince1970 * 1000).toLong()
  ```
  Note: Requires `import platform.Foundation.NSDate` in iosMain Platform.kt.

### T10 — Wire `SyncPodcastLibraryUseCase` in `PodDriveApplication.kt` (AC: 1)
- [x] Add new `lateinit var` fields to `PodDriveApplication` (after `revokeSpotifyAccessUseCase`):
  ```kotlin
  lateinit var database: PodDrive                             // com.poddrive.db.PodDrive
      private set
  lateinit var podcastRepository: PodcastRepository          // com.poddrive.repository.PodcastRepository
      private set
  lateinit var episodeRepository: EpisodeRepository          // com.poddrive.repository.EpisodeRepository
      private set
  lateinit var syncPodcastLibraryUseCase: SyncPodcastLibraryUseCase
      private set
  ```
- [x] In `onCreate()`, after `revokeSpotifyAccessUseCase = ...`:
  ```kotlin
  val dbFactory = DatabaseDriverFactory(applicationContext)
  database = PodDrive(dbFactory.createDriver())
  podcastRepository = SqlDelightPodcastRepository(database)
  episodeRepository = SqlDelightEpisodeRepository(database)
  val libraryApiClient = SpotifyLibraryApiClient(apiHttpClient)
  syncPodcastLibraryUseCase = SyncPodcastLibraryUseCase(libraryApiClient, podcastRepository, episodeRepository)
  ```
- [x] Add required imports:
  ```kotlin
  import com.poddrive.db.PodDrive
  import com.poddrive.repository.PodcastRepository
  import com.poddrive.repository.EpisodeRepository
  import com.poddrive.storage.DatabaseDriverFactory
  import com.poddrive.storage.SqlDelightPodcastRepository
  import com.poddrive.storage.SqlDelightEpisodeRepository
  import com.poddrive.api.SpotifyLibraryApiClient
  import com.poddrive.usecase.SyncPodcastLibraryUseCase
  ```

### T11 — Create `HomeViewModel.kt` (Android) (AC: 1, 7)
- [x] Create `androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeViewModel.kt`:
  ```kotlin
  package com.poddrive.android.ui.home

  import android.app.Application
  import androidx.lifecycle.AndroidViewModel
  import androidx.lifecycle.viewModelScope
  import com.poddrive.android.PodDriveApplication
  import com.poddrive.domain.model.Podcast
  import com.poddrive.domain.model.Result
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.launch

  class HomeViewModel(application: Application) : AndroidViewModel(application) {

      private val app = PodDriveApplication.from(application)

      sealed class UiState {
          data object Loading : UiState()
          data class Success(val podcasts: List<Podcast>) : UiState()
          data class Error(
              val message: String,
              val cachedPodcasts: List<Podcast> = emptyList()
          ) : UiState()
      }

      private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
      val uiState: StateFlow<UiState> = _uiState.asStateFlow()

      /**
       * Syncs the podcast library from Spotify and updates [uiState].
       * On failure, attempts to show cached data with an error banner.
       */
      fun loadLibrary() {
          viewModelScope.launch {
              _uiState.value = UiState.Loading
              when (val result = app.syncPodcastLibraryUseCase.execute()) {
                  is Result.Success -> _uiState.value = UiState.Success(result.value)
                  is Result.Failure -> {
                      // Show cached podcasts (if any) alongside the error
                      val cached = app.podcastRepository.getAllWithUnheardCount()
                      _uiState.value = UiState.Error(
                          message = "Could not refresh library. ${result.error}",
                          cachedPodcasts = cached
                      )
                  }
              }
          }
      }
  }
  ```

### T12 — Create `HomeScreen.kt` (Android) (AC: 2, 3, 5, 7, 8)
- [x] Create `androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeScreen.kt`:

**Structure:**
```kotlin
package com.poddrive.android.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast

@Composable
fun HomeScreen(
    onRevoked: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsState()

    // Trigger library load when Shows tab is first displayed
    LaunchedEffect(Unit) { viewModel.loadLibrary() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Shows") },
                    label = { Text("Shows") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Discover") },
                    label = { Text("Discover") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> ShowsTabContent(uiState = uiState, paddingValues = paddingValues)
            1 -> DiscoverPlaceholder(paddingValues = paddingValues)
            2 -> SettingsScreen(onRevoked = onRevoked)  // existing from Story 1.5
        }
    }
}
```

**`ShowsTabContent` composable:**
```kotlin
@Composable
private fun ShowsTabContent(
    uiState: HomeViewModel.UiState,
    paddingValues: PaddingValues
) {
    when (uiState) {
        is HomeViewModel.UiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        is HomeViewModel.UiState.Success -> {
            PodcastList(podcasts = uiState.podcasts, paddingValues = paddingValues)
        }
        is HomeViewModel.UiState.Error -> {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                if (uiState.cachedPodcasts.isNotEmpty()) {
                    PodcastList(podcasts = uiState.cachedPodcasts, paddingValues = PaddingValues(0.dp))
                }
            }
        }
    }
}
```

**`PodcastList` composable:**
```kotlin
@Composable
private fun PodcastList(podcasts: List<Podcast>, paddingValues: PaddingValues) {
    LazyColumn(contentPadding = paddingValues) {
        items(podcasts, key = { it.id }) { podcast ->
            ShowTile(podcast = podcast)
            HorizontalDivider()
        }
    }
}
```

**`ShowTile` composable:**
```kotlin
@Composable
fun ShowTile(podcast: Podcast, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)  // ≥44dp touch target (UX-DR9)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Artwork: 52dp rounded square (UX-DR6)
        // Use AsyncImage from Coil or a plain Box placeholder (Coil added in Story 2.1 or later)
        // For now: colored placeholder Box (Coil is NOT yet a dependency — do NOT add it in this story)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(text = podcast.name.take(1), style = MaterialTheme.typography.titleMedium)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = podcast.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                text = "${podcast.unheardEpisodeCount} new",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (podcast.tier != EpisodeTier.NONE) {
            TierBadge(tier = podcast.tier)
        }
    }
}
```

**`TierBadge` composable (FULL variant, UX-DR5, UX-DR11):**
```kotlin
@Composable
fun TierBadge(tier: EpisodeTier, modifier: Modifier = Modifier) {
    val (backgroundColor, iconVector, label) = when (tier) {
        EpisodeTier.LOVED -> Triple(Color(0x21F87171), Icons.Default.Favorite, "Loved")
        EpisodeTier.LIKED -> Triple(Color(0x2160A5FA), Icons.Default.ThumbUp, "Liked")
        EpisodeTier.NONE -> return  // never rendered
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = backgroundColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = "$label podcast",  // accessibility: never colour-only (UX-DR11)
                modifier = Modifier.size(12.dp),
                tint = if (tier == EpisodeTier.LOVED) Color(0xFFF87171) else Color(0xFF60A5FA)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (tier == EpisodeTier.LOVED) Color(0xFFF87171) else Color(0xFF60A5FA)
            )
        }
    }
}
```

**`DiscoverPlaceholder`:**
```kotlin
@Composable
private fun DiscoverPlaceholder(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentAlignment = Alignment.Center
    ) { Text("Discover — Coming in a future story") }
}
```

- [x] Import `SettingsScreen` from `com.poddrive.android.ui.settings.SettingsScreen`
- [x] Do NOT add Coil/AsyncImage in this story — use Box placeholder for artwork. Coil will be added in a future story.
- [x] Import `background` from `androidx.compose.foundation.background`
- [x] IMPORTANT: `Icons.Default.LibraryMusic` may not exist in all compose-material3 versions. Use a safe fallback: `Icons.Default.MusicNote` or `Icons.Default.List`. Check Material Icons availability for compose-material3 BOM 2024.09.03.

### T13 — Update `MainActivity.kt` — replace SettingsScreen with HomeScreen (AC: 3, 5)
- [x] In `MainActivity.kt`, replace:
  ```kotlin
  // BEFORE:
  SettingsScreen(onRevoked = { onboardingViewModel.resetToIdle() })

  // AFTER:
  HomeScreen(onRevoked = { onboardingViewModel.resetToIdle() })
  ```
- [x] Remove `import com.poddrive.android.ui.settings.SettingsScreen` (no longer called directly from MainActivity)
- [x] Add `import com.poddrive.android.ui.home.HomeScreen`

### T14 — Create `HomeViewModel.swift` (iOS) (AC: 1, 7)
- [x] Create `iosApp/iosApp/ui/home/HomeViewModel.swift`:
  ```swift
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
      private let podcastRepository: PodcastRepository

      init(syncUseCase: SyncPodcastLibraryUseCase, podcastRepository: PodcastRepository) {
          self.syncUseCase = syncUseCase
          self.podcastRepository = podcastRepository
      }

      func loadLibrary() {
          state = .loading
          Task {
              do {
                  let result = try await syncUseCase.execute()
                  if let success = result as? ResultSuccess<NSArray, AppError> {
                      let podcasts = success.value as! [Podcast]
                      state = .success(podcasts)
                  } else if let failure = result as? ResultFailure<NSArray, AppError> {
                      let cached = podcastRepository.getAllWithUnheardCount()
                      state = .error("Could not refresh library. \(failure.error)", cached: cached)
                  }
              } catch {
                  let cached = podcastRepository.getAllWithUnheardCount()
                  state = .error("Unexpected error: \(error.localizedDescription)", cached: cached)
              }
          }
      }
  }
  ```
  Note on KMP Result bridging: `SyncPodcastLibraryUseCase.execute()` returns `Result<List<Podcast>, AppError>`. In Swift, this becomes a Kotlin sealed class. Check how `RevokeSpotifyAccessUseCase` was bridged in Story 1.5 — use the same `is ResultSuccess<...>` / `is ResultFailure<...>` pattern. The type parameter for `List<Podcast>` on Swift side may be `KotlinArray` or `NSArray` — test at runtime; prefer `success.value as? [Podcast]`.

### T15 — Create `HomeView.swift` (iOS) (AC: 2, 3, 5, 7)
- [x] Create `iosApp/iosApp/ui/home/HomeView.swift`:
  ```swift
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
          podcastRepository: PodcastRepository,
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
          .task { await viewModel.loadLibrary() }
      }
  }
  ```

  **`ShowsTab` view:**
  ```swift
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
  ```

  **`ShowTileRow` view:**
  ```swift
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
                  Text("\(podcast.unheardEpisodeCount) new")
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
  ```

  **`TierBadgeView` (never colour-only — UX-DR11):**
  ```swift
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

  // MARK: - Color hex init (add as private extension in this file)
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
  ```

- [x] Add `func loadLibrary() async` wrapper if needed: `task { await viewModel.loadLibrary() }` — note `loadLibrary()` is `@MainActor` so calling `await viewModel.loadLibrary()` from `.task {}` works without `Task {}`

### T16 — Update `PodDriveApp.swift` — wire HomeView (AC: 3, 5)
- [x] In `PodDriveApp.swift`, replace the authenticated branch to use `HomeView`:
  ```swift
  @main
  struct PodDriveApp: App {
      @State private var isAuthenticated = false

      // All dependencies as stored properties — never recreated on body re-renders (P1 fix from 1.5)
      private let revokeUseCase: RevokeSpotifyAccessUseCase = {
          let storage = SecureStorage()
          let httpClient = SpotifyHttpClientKt.createAuthHttpClient()
          let clientId = Bundle.main.infoDictionary?["SPOTIFY_CLIENT_ID"] as? String ?? ""
          let manager = SpotifyAuthManager(tokenStorage: storage, httpClient: httpClient, clientId: clientId)
          return RevokeSpotifyAccessUseCase(authManager: manager)
      }()

      private let syncUseCase: SyncPodcastLibraryUseCase = {
          let dbFactory = DatabaseDriverFactory()
          let db = PodDrive(driver: dbFactory.createDriver())
          let podcastRepo = SqlDelightPodcastRepository(db: db)
          let episodeRepo = SqlDelightEpisodeRepository(db: db)
          let storage = SecureStorage()
          let authHttpClient = SpotifyHttpClientKt.createAuthHttpClient()
          let clientId = Bundle.main.infoDictionary?["SPOTIFY_CLIENT_ID"] as? String ?? ""
          let authManager = SpotifyAuthManager(tokenStorage: storage, httpClient: authHttpClient, clientId: clientId)
          let apiHttpClient = SpotifyHttpClientKt.createSpotifyHttpClient(tokenStorage: storage, tokenRefresher: authManager)
          let libraryClient = SpotifyLibraryApiClient(httpClient: apiHttpClient)
          return SyncPodcastLibraryUseCase(
              libraryApiClient: libraryClient,
              podcastRepository: podcastRepo,
              episodeRepository: episodeRepo
          )
      }()

      private let podcastRepository: PodcastRepository = {
          let dbFactory = DatabaseDriverFactory()
          let db = PodDrive(driver: dbFactory.createDriver())
          return SqlDelightPodcastRepository(db: db)
      }()

      var body: some Scene {
          WindowGroup {
              if isAuthenticated {
                  HomeView(
                      syncUseCase: syncUseCase,
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
  ```
  **IMPORTANT NOTE on iOS DB instances:** The `syncUseCase` and `podcastRepository` IIFE closures above create SEPARATE `PodDrive` database instances. This is a KNOWN LIMITATION for MVP — both instances open the same `poddrive.db` file (SQLite allows multiple readers). Since `syncUseCase` writes and `podcastRepository.getAllWithUnheardCount()` reads, and SQLite's WAL mode handles concurrent access, this works but is not ideal. A future story should consolidate to a single shared DB instance via a proper DI container.

### T17 — Unit tests for `SyncPodcastLibraryUseCase` (AC: 4, 6, 7)
- [x] Create `shared/src/commonTest/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCaseTest.kt`:
  ```kotlin
  class SyncPodcastLibraryUseCaseTest {
      // Use fake/stub repositories and SpotifyLibraryApiClient backed by MockEngine
      // Test: success path — shows and episodes upserted, Result.Success returned
      // Test: API failure — Result.Failure returned
      // Test: partial failure (one show's episodes fail) — continues, returns partial Success
      // Test: empty shows list — Result.Success(emptyList())
      // Test: resume_point.fully_played=true → isPlayed=true in Episode
      // Test: null resume_point → isPlayed=false (treated as unheard)
      // Test: releaseDate "YYYY-MM-DD" → valid epoch ms
      // Test: releaseDate "YYYY-MM" (month precision) → defaults to day=1
      // Test: releaseDate "YYYY" (year precision) → defaults to month=1, day=1
  }
  ```
  Implement using `MockEngine` pattern from `SpotifyAuthManagerTest.kt` — follow exact same setup.

---

## Dev Notes

### Spotify Web API Endpoints

```
GET https://api.spotify.com/v1/me/shows?limit=50
Authorization: Bearer {accessToken}

Response: SpotifyShowsResponse
- items[].show.id         → base ID (e.g., "4tWbRCGFKt00YlSCqz4gkr")
- items[].show.uri        → full URI (e.g., "spotify:show:4tWbRCGFKt00YlSCqz4gkr") ← store as Podcast.id
- items[].show.name       → podcast name
- items[].show.images[]   → pick largest by width for imageUrl
- next                    → URL for next page (null when done)
```

```
GET https://api.spotify.com/v1/shows/{show_base_id}/episodes?limit=50&market=from_token
Authorization: Bearer {accessToken}

Note: use show.id (base ID, NOT uri) as path parameter

Response: SpotifyEpisodesResponse
- items[].uri             → full URI (e.g., "spotify:episode:xxx") ← store as Episode.id
- items[].name            → episode title
- items[].duration_ms     → Long
- items[].release_date    → "YYYY-MM-DD" string ← parseSpotifyDateToEpochMs()
- items[].resume_point.fully_played → true = played; false or null = unheard
```

### SQLDelight Generated Class

```kotlin
import com.poddrive.db.PodDrive  // NOT PodDriveDatabase — the name comes from create("PodDrive") in build.gradle.kts

// Creating the database:
val db = PodDrive(driver)

// Accessing query objects:
db.podcastQueries.selectAllWithUnheardCount()
db.podcastQueries.upsert(id, name, description, image_url, episode_count, tier, last_synced_at)
db.episodeQueries.upsertEpisode(id, podcast_id, title, duration_ms, published_at, is_played)
db.episodeQueries.transaction { ... }   // batch upserts in a transaction
```

### Tier Preservation on Sync

When syncing, the `podcasts.upsert` query uses `INSERT OR REPLACE` which REPLACES the entire row. To preserve existing tiers:
- `SqlDelightPodcastRepository.upsertAll()` must first check existing tier via `selectById` before calling `upsert`
- Pass the stored tier (or `EpisodeTier.NONE` for new podcasts) as the `tier` parameter
- This is critical: without it, every sync would reset all LOVED/LIKED tiers to NONE

### No `kotlinx-datetime` — Date Parsing

`kotlinx-datetime` is NOT in the build. Use `parseSpotifyDateToEpochMs()` from T8. The Fliegel-Van Flandern algorithm is accurate for all Gregorian dates ≥ 1582. Edge cases handled: "YYYY-MM" → day=1; "YYYY" → month=1, day=1.

### Artwork Images

Spotify API returns multiple `images` objects per show with different resolutions. Pick the largest by `width` for best quality:
```kotlin
show.images.maxByOrNull { it.width ?: 0 }?.url ?: ""
```

### iOS KMP Type Bridging for `List<Podcast>`

The KMP `Result<List<Podcast>, AppError>` type bridges to Swift as a sealed class. The `success.value` field may come across as `KotlinArray<Podcast>`, `NSArray`, or a Swift `Array` depending on KMP version. Use:
```swift
if let list = success.value as? [Podcast] {
    // direct cast
} else if let array = success.value as? KotlinArray<Podcast> {
    let list = (0..<Int(array.size)).map { array.get(index: Int32($0))! }
}
```
Test with the actual Kotlin/Native bridge to confirm the actual Swift type at runtime.

### PodcastRepository Interface on iOS

KMP interfaces are exposed to Swift as protocols. `PodcastRepository` in Kotlin becomes `PodcastRepository` protocol in Swift. `SqlDelightPodcastRepository` implements it. The `getAllWithUnheardCount()` method returns `KotlinArray<Podcast>` or `NSArray` on the Swift side — use the same casting pattern as above.

### Architecture Compliance

- ✅ `SyncPodcastLibraryUseCase` delegates only — no direct HTTP or SQLDelight in use case
- ✅ `SpotifyLibraryApiClient` is the ONLY place that calls `api.spotify.com` for library data
- ✅ Repository interfaces in `com.poddrive.repository`, implementations in `com.poddrive.storage`
- ✅ `PodDriveApplication.apiHttpClient` (already wired) used by `SpotifyLibraryApiClient`
- ✅ `TierBadge` uses BOTH colour AND icon — never colour-only (UX-DR11)
- ✅ `ShowTile` minimum height ≥ 44dp (UX-DR9)
- ✅ `HomeScreen`/`HomeView` wraps `SettingsScreen`/`SettingsView` in Settings tab — Disconnect Spotify flow remains functional (AC5)

### Files to Create

```
shared/src/
├── commonMain/kotlin/com/poddrive/
│   ├── repository/
│   │   ├── PodcastRepository.kt              ← CREATE (T4)
│   │   └── EpisodeRepository.kt              ← CREATE (T5)
│   ├── storage/
│   │   ├── SqlDelightPodcastRepository.kt    ← CREATE (T6)
│   │   └── SqlDelightEpisodeRepository.kt    ← CREATE (T7)
│   ├── api/
│   │   └── SpotifyLibraryApiClient.kt        ← CREATE (T3)
│   └── usecase/
│       └── SyncPodcastLibraryUseCase.kt      ← CREATE (T8)
├── commonMain/sqldelight/com/poddrive/
│   └── PodcastQueries.sq                     ← MODIFY: add selectAllWithUnheardCount queries (T1)
└── commonTest/kotlin/com/poddrive/usecase/
    └── SyncPodcastLibraryUseCaseTest.kt      ← CREATE (T17)

androidApp/src/main/kotlin/com/poddrive/android/ui/home/
├── HomeViewModel.kt                          ← CREATE (T11)
└── HomeScreen.kt                             ← CREATE (T12)

iosApp/iosApp/ui/home/
├── HomeViewModel.swift                       ← CREATE (T14)
└── HomeView.swift                            ← CREATE (T15)
```

### Files to Modify

```
shared/src/commonMain/kotlin/com/poddrive/domain/model/
└── Podcast.kt                                ← ADD unheardEpisodeCount field (T1)

shared/src/commonMain/kotlin/com/poddrive/api/
└── SpotifyModels.kt                          ← ADD show/episode API models (T2)

shared/src/commonMain/kotlin/com/poddrive/
└── Platform.kt                               ← ADD currentTimeMillis() expect (T9)
shared/src/androidMain/kotlin/com/poddrive/
└── Platform.kt                               ← ADD currentTimeMillis() actual (T9)
shared/src/iosMain/kotlin/com/poddrive/
└── Platform.kt                               ← ADD currentTimeMillis() actual (T9)

androidApp/src/main/kotlin/com/poddrive/android/
├── PodDriveApplication.kt                    ← ADD db, repos, libraryClient, syncUseCase (T10)
└── MainActivity.kt                           ← Replace SettingsScreen with HomeScreen (T13)

iosApp/iosApp/
└── PodDriveApp.swift                         ← ADD syncUseCase, podcastRepository; replace SettingsView with HomeView (T16)
```

### Previous Story Intelligence (Stories 1.2–1.5)

- **Build constraint**: JDK 17 not available locally — verify syntax, defer build to CI
- **Kotlin 2.0.21** — pinned; do NOT upgrade
- **SQLDelight 2.0.2** — generated DB class is `com.poddrive.db.PodDrive`, accessed via `db.podcastQueries` and `db.episodeQueries`
- **Ktor 3.0.3** — `response.body<T>()` requires `import io.ktor.client.call.body`
- **`FakeSecureTokenStorage`** at `commonTest/storage/FakeSecureTokenStorage.kt` — reuse in tests
- **`MockEngine` pattern** from `SpotifyAuthManagerTest.kt` — follow exact same pattern for `SpotifyLibraryApiClientTest`
- **`Result<T, AppError>` pattern**: `Result.Success(value)`, `Result.Failure(error)` — NO exceptions from use cases
- **`AndroidViewModel(application)` + `PodDriveApplication.from(application)`** — established DI pattern
- **`viewModelScope.launch`** — coroutine launch in Android ViewModels
- **iOS `@MainActor ObservableObject`** — established ViewModel pattern
- **iOS `@StateObject` in `init`** — `_viewModel = StateObject(wrappedValue: ...)` pattern
- **iOS `.task {}` instead of `.onChange`** — use `.task { await viewModel.loadLibrary() }` for side effects on appear (story 1.5 P1 fix lesson)
- **`CancellationException` must be re-thrown** — `if (e is CancellationException) throw e` in all catch blocks (story 1.4 P1 fix)
- **`INSERT OR REPLACE` destroys existing rows** — use `selectById` before `upsert` to preserve tier values
- **`db.podcastQueries.transaction { }` for batch operations** — wrap multi-row upserts in a transaction for performance and atomicity

### Anti-Patterns to Avoid

❌ Do NOT call Spotify API endpoints from ViewModel or Repository — only from `SpotifyLibraryApiClient`
❌ Do NOT add kotlinx-datetime dependency — use `parseSpotifyDateToEpochMs()` (T8)
❌ Do NOT add Coil / AsyncImage in this story — use Box placeholder for artwork
❌ Do NOT use `createAuthHttpClient()` for library API calls — use `apiHttpClient` (it has bearer Auth)
❌ Do NOT store Spotify base ID as `Podcast.id` — store the full URI (`show.uri`)
❌ Do NOT pass show URI as the API path parameter — parse base ID: `uri.removePrefix("spotify:show:")`
❌ Do NOT reset LOVED/LIKED tiers during sync — preserve existing tier via `selectById` check in `upsertAll`
❌ Do NOT use `TierBadge` with colour-only — always include icon AND label (UX-DR11)
❌ Do NOT use `@StateObject` in SwiftUI `body` computed property — use stored property pattern established in Story 1.5 P1 fix

### References

- [Source: epics.md#Story 2.1] — Acceptance criteria, FR4, FR7, FR8
- [Source: architecture.md#Podcast Library & Shows Data] — data models, caching strategy
- [Source: architecture.md#Repository Pattern] — interface/implementation split
- [Source: architecture.md#Ktor HTTP Client] — `createSpotifyHttpClient`, bearer auth
- [Source: architecture.md#SQLDelight] — `PodDriveDatabase`, table names, query patterns
- [Source: architecture.md#Android/iOS UI Patterns] — ViewModel, StateFlow, ObservableObject
- [Source: ux-design-specification.md#ShowTile] — 52dp artwork, UX-DR6
- [Source: ux-design-specification.md#TierBadge] — FULL variant, UX-DR5, UX-DR11
- [Source: ux-design-specification.md#Navigation Patterns] — bottom nav Shows/Discover/Settings
- [Source: 1-5-revoke-spotify-access.md] — SettingsView/SettingsScreen wrapping pattern for bottom nav Settings tab

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

No blockers encountered. All 17 tasks implemented as specified.

### Completion Notes List

- ✅ T1: Added `unheardEpisodeCount: Int` to `Podcast.kt` with KDoc; added `selectAllWithUnheardCount` and `selectAllWithUnheardCountByTier` SQL queries with LEFT JOIN on episodes table.
- ✅ T2: Appended `SpotifyShowsResponse`, `SpotifyShowItem`, `SpotifyShow`, `SpotifyImage`, `SpotifyEpisodesResponse`, `SpotifyEpisodeObject`, `SpotifyResumePoint` to `SpotifyModels.kt`.
- ✅ T3: Created `SpotifyLibraryApiClient.kt` with full pagination for both `fetchFollowedShows()` and `fetchShowEpisodes(showBaseId)`. CancellationException re-thrown.
- ✅ T4: Created `PodcastRepository` interface with `upsertAll`, `getAllWithUnheardCount`, `getByTierWithUnheardCount`.
- ✅ T5: Created `EpisodeRepository` interface with `upsertAll`, `getUnheardCountForPodcast`.
- ✅ T6: Created `SqlDelightPodcastRepository` — upsertAll reads existing tier via `selectById` before upserting to preserve LOVED/LIKED tiers.
- ✅ T7: Created `SqlDelightEpisodeRepository` — upsertAll uses transaction, maps `isPlayed` Bool to `0L/1L`.
- ✅ T8: Created `SyncPodcastLibraryUseCase` — fetches shows, upserts, fetches episodes per show (partial failure = continue), returns `getAllWithUnheardCount()`. Includes `parseSpotifyDateToEpochMs()` for "YYYY-MM-DD"/"YYYY-MM"/"YYYY" variants.
- ✅ T9: Added `expect/actual fun currentTimeMillis(): Long` to all three Platform.kt files.
- ✅ T10: Wired `database`, `podcastRepository`, `episodeRepository`, `syncPodcastLibraryUseCase` in `PodDriveApplication.onCreate()`.
- ✅ T11: Created `HomeViewModel.kt` (Android) with `UiState.Loading/Success/Error` sealed class; error path falls back to cached podcasts.
- ✅ T12: Created `HomeScreen.kt` with bottom nav (Shows/Discover/Settings), `ShowTile` (52dp placeholder artwork, ≥72dp row height), `TierBadge` (icon + label, never colour-only). Used `Icons.Default.List` for Shows nav item (LibraryMusic not available in BOM 2024.09.03).
- ✅ T13: Updated `MainActivity.kt` — replaced `SettingsScreen` with `HomeScreen`.
- ✅ T14: Created `HomeViewModel.swift` — `@MainActor ObservableObject`, handles KMP `ResultSuccess`/`ResultFailure` bridging with both `[Podcast]` and `KotlinArray<Podcast>` cast paths.
- ✅ T15: Created `HomeView.swift` — `TabView` with Shows/Discover/Settings, `ShowTileRow`, `TierBadgeView` (icon + label, accessibility label), `Color(hex:)` extension.
- ✅ T16: Updated `PodDriveApp.swift` — added `syncUseCase` and `podcastRepository` as stored properties; replaced `SettingsView` with `HomeView`. Two-DB-instance MVP limitation documented.
- ✅ T17: Created `SyncPodcastLibraryUseCaseTest.kt` — 11 tests covering: success path, podcast URI vs base ID, image selection, API failure (401), partial episode failure (continue), empty shows, `fullyPlayed=true/false/null`, date parsing "YYYY-MM-DD"/"YYYY-MM"/"YYYY".

### File List

**Created:**
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyLibraryApiClient.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/repository/PodcastRepository.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/repository/EpisodeRepository.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightPodcastRepository.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightEpisodeRepository.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCase.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeViewModel.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeScreen.kt`
- `poddrive/iosApp/iosApp/ui/home/HomeViewModel.swift`
- `poddrive/iosApp/iosApp/ui/home/HomeView.swift`
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCaseTest.kt`

**Modified:**
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/domain/model/Podcast.kt`
- `poddrive/shared/src/commonMain/sqldelight/com/poddrive/PodcastQueries.sq`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/api/SpotifyModels.kt`
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/Platform.kt`
- `poddrive/shared/src/androidMain/kotlin/com/poddrive/Platform.kt`
- `poddrive/shared/src/iosMain/kotlin/com/poddrive/Platform.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/MainActivity.kt`
- `poddrive/iosApp/iosApp/PodDriveApp.swift`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Change Log

| Date | Change | Reason |
|------|--------|--------|
| 2026-03-22 | Story created | Story 2.1 context engine analysis |
| 2026-03-22 | Implemented all 17 tasks (T1–T17) | Story 2.1 dev implementation |
