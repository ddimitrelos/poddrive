# Story 2.2: LOVED/LIKED Tier Assignment & Management

Status: done

## Story

As a **user**,
I want to tag any podcast as LOVED or LIKED, change its tier, or remove the tag via a long-press menu,
so that PodDrive knows which shows matter most to me and can prioritise them in my drive briefing.

## Acceptance Criteria

1. **Given** a podcast is displayed in the library **When** the user long-presses a `ShowTile` **Then** a bottom sheet appears with options: Set LOVED / Set LIKED / Remove Tier (FR5, FR6).
2. **Given** the bottom sheet is shown **When** the user selects a tier **Then** it is saved via `SetEpisodeTierUseCase` and persisted to SQLDelight on-device only — not transmitted externally (NFR7, AR3).
3. **Given** a tier is saved **Then** the `TierBadge` on the `ShowTile` updates immediately using both colour AND distinct iconography: heart icon for LOVED (coral `#F87171`), thumbs icon for LIKED (blue `#60A5FA`) — never colour alone (UX-DR11, UX-DR2).
4. **Given** this story **Then** `TierBadge` ICON_ONLY variant is implemented: 8dp coloured dot with correct icon — distinct from the FULL variant (label + icon) already implemented in Story 2.1 (UX-DR5).
5. **Given** the tier action is non-destructive **Then** a bottom sheet (not a modal alert dialog) is used for tier selection (UX-DR9). **Platform note:** On iOS, a native `.confirmationDialog` (action sheet presented from the bottom of the screen) is an acceptable equivalent per iOS HIG, provided it is not a centred alert. The Android implementation uses `ModalBottomSheet`.

## Tasks / Subtasks

### T1 — Add `setPodcastTier` to `PodcastRepository` interface (AC: 2)

- [x] In `shared/src/commonMain/kotlin/com/poddrive/repository/PodcastRepository.kt`, add one method:
  ```kotlin
  package com.poddrive.repository

  import com.poddrive.domain.model.EpisodeTier
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
      /** Updates the tier for a single podcast by ID. Use EpisodeTier.NONE to remove the tag. */
      fun setPodcastTier(id: String, tier: EpisodeTier)         // ← ADD (Story 2.2)
  }
  ```
- [x] Add `import com.poddrive.domain.model.EpisodeTier` at the top of the file

### T2 — Implement `setPodcastTier` in `SqlDelightPodcastRepository` (AC: 2)

- [x] In `shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightPodcastRepository.kt`, add the implementation after `getByTierWithUnheardCount`:
  ```kotlin
  override fun setPodcastTier(id: String, tier: EpisodeTier) {
      db.podcastQueries.updateTier(tier = tier.name, id = id)
  }
  ```
- [x] The `updateTier` query already exists in `Podcast.sq`:
  ```sql
  updateTier:
  UPDATE podcasts SET tier = ? WHERE id = ?;
  ```
  — NO schema changes needed.

### T3 — Create `SetEpisodeTierUseCase` (AC: 2)

- [x] Create `shared/src/commonMain/kotlin/com/poddrive/usecase/SetEpisodeTierUseCase.kt`:
  ```kotlin
  package com.poddrive.usecase

  import com.poddrive.domain.model.AppError
  import com.poddrive.domain.model.EpisodeTier
  import com.poddrive.domain.model.Result
  import com.poddrive.repository.PodcastRepository
  import kotlinx.coroutines.CancellationException

  /**
   * Persists a LOVED/LIKED tier (or NONE to remove) for a given podcast.
   *
   * Updates the `podcasts.tier` column via [PodcastRepository.setPodcastTier].
   * The `episode_tiers` table (per-episode overrides) is managed separately — this use case
   * only changes the podcast-level tier which drives the drive briefing priority (FR15).
   *
   * Never throws — all errors returned as [Result.Failure].
   * Data stored on-device only — never transmitted to any server (NFR7).
   * [Source: epics.md#Story 2.2, AR5, AR10]
   */
  class SetEpisodeTierUseCase(
      private val podcastRepository: PodcastRepository
  ) {
      /**
       * Sets the tier for podcast [podcastId] to [tier].
       * Pass [EpisodeTier.NONE] to remove the current tag.
       */
      fun execute(podcastId: String, tier: EpisodeTier): Result<Unit, AppError> {
          return try {
              podcastRepository.setPodcastTier(podcastId, tier)
              Result.Success(Unit)
          } catch (e: CancellationException) {
              throw e
          } catch (e: Exception) {
              Result.Failure(AppError.Unknown(e))
          }
      }
  }
  ```
- [x] Note: `execute()` is NOT `suspend` — `setPodcastTier` is a synchronous SQLDelight call. The ViewModel dispatches it to `Dispatchers.IO`.

### T4 — Wire `SetEpisodeTierUseCase` in `PodDriveApplication.kt` (AC: 2)

- [x] In `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt`, add after `syncPodcastLibraryUseCase`:
  ```kotlin
  import com.poddrive.usecase.SetEpisodeTierUseCase   // add to imports

  // ... in class body after syncPodcastLibraryUseCase declaration:
  lateinit var setEpisodeTierUseCase: SetEpisodeTierUseCase
      private set
  ```
- [x] In `onCreate()`, after `syncPodcastLibraryUseCase = ...`:
  ```kotlin
  setEpisodeTierUseCase = SetEpisodeTierUseCase(podcastRepository)
  ```

### T5 — Update `HomeViewModel.kt` (Android) — add tier sheet state + `setTier()` (AC: 1, 2, 3)

- [x] Replace the full content of `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeViewModel.kt`:
  ```kotlin
  package com.poddrive.android.ui.home

  import android.app.Application
  import androidx.lifecycle.AndroidViewModel
  import androidx.lifecycle.viewModelScope
  import com.poddrive.android.PodDriveApplication
  import com.poddrive.domain.model.EpisodeTier
  import com.poddrive.domain.model.Podcast
  import com.poddrive.domain.model.Result
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.launch
  import kotlinx.coroutines.withContext

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

      /** Podcast currently targeted by the tier bottom sheet. Null = sheet dismissed. */
      private val _selectedPodcast = MutableStateFlow<Podcast?>(null)
      val selectedPodcast: StateFlow<Podcast?> = _selectedPodcast.asStateFlow()

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
                      val cached = withContext(Dispatchers.IO) { app.podcastRepository.getAllWithUnheardCount() }
                      _uiState.value = UiState.Error(
                          message = "Could not refresh library. ${result.error}",
                          cachedPodcasts = cached
                      )
                  }
              }
          }
      }

      /** Opens the tier bottom sheet for [podcast]. */
      fun showTierSheet(podcast: Podcast) {
          _selectedPodcast.value = podcast
      }

      /** Dismisses the tier bottom sheet without making changes. */
      fun dismissTierSheet() {
          _selectedPodcast.value = null
      }

      /**
       * Sets the tier for [podcast] to [tier] (pass null or [EpisodeTier.NONE] to remove).
       * Dismisses the sheet, persists the change, then refreshes the list from the DB.
       */
      fun setTier(podcast: Podcast, tier: EpisodeTier) {
          _selectedPodcast.value = null  // dismiss sheet immediately (optimistic UX)
          viewModelScope.launch {
              val result = withContext(Dispatchers.IO) {
                  app.setEpisodeTierUseCase.execute(podcast.id, tier)
              }
              when (result) {
                  is Result.Success -> {
                      // Reload from DB to reflect updated tier badge on the ShowTile
                      val updated = withContext(Dispatchers.IO) {
                          app.podcastRepository.getAllWithUnheardCount()
                      }
                      _uiState.value = UiState.Success(updated)
                  }
                  is Result.Failure -> {
                      // Non-fatal: show error banner but keep the list visible
                      val current = (_uiState.value as? UiState.Success)?.podcasts ?: emptyList()
                      _uiState.value = UiState.Error(
                          message = "Could not update tier. ${result.error}",
                          cachedPodcasts = current
                      )
                  }
              }
          }
      }
  }
  ```

### T6 — Update `HomeScreen.kt` (Android) — long-press, bottom sheet, `TierBadgeIconOnly` (AC: 1, 3, 4, 5)

- [x] Replace the full content of `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeScreen.kt`:

```kotlin
package com.poddrive.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poddrive.android.ui.settings.SettingsScreen
import com.poddrive.domain.model.EpisodeTier
import com.poddrive.domain.model.Podcast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRevoked: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsState()
    val selectedPodcast by viewModel.selectedPodcast.collectAsState()

    // Trigger library load when Shows tab is first displayed
    LaunchedEffect(Unit) { viewModel.loadLibrary() }

    // Tier bottom sheet — shown when selectedPodcast is non-null
    if (selectedPodcast != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissTierSheet() },
            sheetState = sheetState
        ) {
            TierSheetContent(
                podcast = selectedPodcast!!,
                onSetLoved = { viewModel.setTier(selectedPodcast!!, EpisodeTier.LOVED) },
                onSetLiked = { viewModel.setTier(selectedPodcast!!, EpisodeTier.LIKED) },
                onRemoveTier = { viewModel.setTier(selectedPodcast!!, EpisodeTier.NONE) }
            )
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Shows") },
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
            0 -> ShowsTabContent(
                uiState = uiState,
                paddingValues = paddingValues,
                onLongPress = { viewModel.showTierSheet(it) }
            )
            1 -> DiscoverPlaceholder(paddingValues = paddingValues)
            2 -> SettingsScreen(onRevoked = onRevoked)
        }
    }
}

/** Bottom sheet content for tier selection (UX-DR9 — bottom sheet, not dialog). */
@Composable
private fun TierSheetContent(
    podcast: Podcast,
    onSetLoved: () -> Unit,
    onSetLiked: () -> Unit,
    onRemoveTier: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = podcast.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        ListItem(
            headlineContent = { Text("Set Loved") },
            leadingContent = {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFF87171))
            },
            modifier = Modifier.combinedClickable(
                role = Role.Button,
                onClick = onSetLoved
            )
        )
        ListItem(
            headlineContent = { Text("Set Liked") },
            leadingContent = {
                Icon(Icons.Default.ThumbUp, contentDescription = null, tint = Color(0xFF60A5FA))
            },
            modifier = Modifier.combinedClickable(
                role = Role.Button,
                onClick = onSetLiked
            )
        )
        ListItem(
            headlineContent = { Text("Remove Tier") },
            leadingContent = {
                Icon(Icons.Default.Delete, contentDescription = null)
            },
            modifier = Modifier.combinedClickable(
                role = Role.Button,
                onClick = onRemoveTier
            )
        )
    }
}

@Composable
private fun ShowsTabContent(
    uiState: HomeViewModel.UiState,
    paddingValues: PaddingValues,
    onLongPress: (Podcast) -> Unit
) {
    when (uiState) {
        is HomeViewModel.UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        is HomeViewModel.UiState.Success -> {
            PodcastList(
                podcasts = uiState.podcasts,
                paddingValues = paddingValues,
                onLongPress = onLongPress
            )
        }
        is HomeViewModel.UiState.Error -> {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                if (uiState.cachedPodcasts.isNotEmpty()) {
                    PodcastList(
                        podcasts = uiState.cachedPodcasts,
                        paddingValues = PaddingValues(0.dp),
                        onLongPress = onLongPress
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastList(
    podcasts: List<Podcast>,
    paddingValues: PaddingValues,
    onLongPress: (Podcast) -> Unit
) {
    LazyColumn(contentPadding = paddingValues) {
        items(podcasts, key = { it.id }) { podcast ->
            ShowTile(podcast = podcast, onLongPress = { onLongPress(podcast) })
            HorizontalDivider()
        }
    }
}

/**
 * Single podcast row. Long-press opens the tier bottom sheet. (UX-DR6, UX-DR9)
 * Touch target ≥44dp enforced via `heightIn(min = 72.dp)`.
 */
@Composable
fun ShowTile(
    podcast: Podcast,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)  // ≥44dp touch target (UX-DR9)
            .combinedClickable(
                onClick = {},  // future: navigate to episode list
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Artwork placeholder (52dp rounded square) — Coil/AsyncImage added in a future story
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

/**
 * FULL variant `TierBadge` — icon + label pill. Used on `ShowTile` in management mode.
 * Never colour-only: always has icon AND text (UX-DR11).
 * [Source: ux-design-specification.md#TierBadge, UX-DR5]
 */
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

/**
 * ICON_ONLY variant `TierBadge` — 8dp coloured dot with icon. Used in drive mode (Story 3.x+).
 * Distinct from [TierBadge] FULL variant: no label text, smaller footprint.
 * Never colour-only: icon provides the non-colour signal (UX-DR5, UX-DR11).
 * [Source: ux-design-specification.md#TierBadge, UX-DR5]
 */
@Composable
fun TierBadgeIconOnly(tier: EpisodeTier, modifier: Modifier = Modifier) {
    if (tier == EpisodeTier.NONE) return
    val (tintColor, iconVector, description) = when (tier) {
        EpisodeTier.LOVED -> Triple(Color(0xFFF87171), Icons.Default.Favorite, "Loved")
        EpisodeTier.LIKED -> Triple(Color(0xFF60A5FA), Icons.Default.ThumbUp, "Liked")
        EpisodeTier.NONE -> return
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .background(tintColor.copy(alpha = 0.2f), shape = MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = description,  // accessibility: icon provides non-colour signal (UX-DR11)
            modifier = Modifier.size(6.dp),
            tint = tintColor
        )
    }
}

@Composable
private fun DiscoverPlaceholder(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentAlignment = Alignment.Center
    ) { Text("Discover — Coming in a future story") }
}
```

**Critical import note:** `combinedClickable` requires `androidx.compose.foundation.combinedClickable` — it is in `foundation` not `foundation.gestures`. The `@OptIn(ExperimentalMaterial3Api::class)` annotation is required for `ModalBottomSheet` and `rememberModalBottomSheetState` in Material3 BOM 2024.09.03.

### T7 — Update `HomeViewModel.swift` (iOS) — add `setEpisodeTierUseCase` + tier sheet state (AC: 1, 2, 3)

- [x] Replace the full content of `poddrive/iosApp/iosApp/ui/home/HomeViewModel.swift`:
  ```swift
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

      /// The podcast currently targeted by the tier sheet. Set before showTierSheet = true.
      private(set) var selectedPodcast: Podcast? = nil

      private let syncUseCase: SyncPodcastLibraryUseCase
      private let setTierUseCase: SetEpisodeTierUseCase
      private let podcastRepository: any PodcastRepository

      init(
          syncUseCase: SyncPodcastLibraryUseCase,
          setTierUseCase: SetEpisodeTierUseCase,
          podcastRepository: any PodcastRepository
      ) {
          self.syncUseCase = syncUseCase
          self.setTierUseCase = setTierUseCase
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
                      state = .error("Could not refresh library. \(failure.error)", cached: getCachedPodcasts())
                  }
              } catch {
                  state = .error("Unexpected error: \(error.localizedDescription)", cached: getCachedPodcasts())
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
          selectedPodcast = nil
          Task {
              let result = setTierUseCase.execute(podcastId: podcast.id, tier: tier)
              if result is ResultSuccess<KotlinUnit, AppError> {
                  // Reload from DB to reflect updated tier badge
                  state = .success(getCachedPodcasts())
              } else if let failure = result as? ResultFailure<KotlinUnit, AppError> {
                  let current: [Podcast]
                  if case .success(let list) = state { current = list } else { current = [] }
                  state = .error("Could not update tier. \(failure.error)", cached: current)
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
  ```

**Note on `SetEpisodeTierUseCase` Swift bridging:** `execute()` returns `Result<Unit, AppError>`. On the Swift side, this bridges to a sealed class. `Unit` maps to `KotlinUnit`. Pattern for checking success: `result is ResultSuccess<KotlinUnit, AppError>`. `execute()` is NOT `async` in KMP (it is synchronous) — call it directly, NOT with `await`. The `Task {}` block is only needed to satisfy `@MainActor` isolation when reading from the repository.

### T8 — Update `HomeView.swift` (iOS) — long-press + `.confirmationDialog` (AC: 1, 3, 5)

- [x] Replace the full content of `poddrive/iosApp/iosApp/ui/home/HomeView.swift`:
  ```swift
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
                  .onLongPressGesture {
                      viewModel.showTierSheetFor(podcast)
                  }
          }
          .listStyle(.plain)
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

  // MARK: - TierBadgeIconOnly — ICON_ONLY variant (8pt dot + icon, no label)
  // Used in drive mode (Story 3.x+). Distinct from TierBadgeView FULL variant.
  // Never colour-only: icon provides non-colour signal (UX-DR5, UX-DR11).
  private struct TierBadgeIconOnly: View {
      let tier: EpisodeTier

      var body: some View {
          ZStack {
              Circle()
                  .fill((tier == .loved ? Color(hex: "F87171") : Color(hex: "60A5FA")).opacity(0.2))
                  .frame(width: 8, height: 8)
              Image(systemName: tier == .loved ? "heart.fill" : "hand.thumbsup.fill")
                  .font(.system(size: 4))
                  .foregroundColor(tier == .loved ? Color(hex: "F87171") : Color(hex: "60A5FA"))
          }
          .accessibilityLabel(tier == .loved ? "Loved" : "Liked")
      }
  }

  // MARK: - Color hex init (reused from Story 2.1)
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

**Note on iOS `confirmationDialog` vs bottom sheet:** iOS does not have a `ModalBottomSheet` equivalent in SwiftUI. `.confirmationDialog` is the correct idiomatic choice — it renders as an action sheet on iPhone (slides up from bottom) which satisfies UX-DR9 "bottom sheets, not modal/alert dialogs". Do NOT use `Alert` or `sheet(isPresented:)` for this interaction.

**Note on `EpisodeTier` in Swift:** KMP `EpisodeTier` enum bridges to Swift as `EpisodeTier.loved`, `EpisodeTier.liked`, `EpisodeTier.none` (lowercase). This is the standard KMP enum Swift convention.

### T9 — Wire `SetEpisodeTierUseCase` in `PodDriveApp.swift` (iOS) (AC: 2)

- [x] In `poddrive/iosApp/iosApp/PodDriveApp.swift`:
  - Add stored property: `private let setTierUseCase: SetEpisodeTierUseCase`
  - In `init()`, after `podcastRepository = podcastRepo`:
    ```swift
    setTierUseCase = SetEpisodeTierUseCase(podcastRepository: podcastRepo)
    ```
  - Update `HomeView(...)` call in `body` to add the new parameter:
    ```swift
    HomeView(
        syncUseCase: syncUseCase,
        setTierUseCase: setTierUseCase,
        podcastRepository: podcastRepository,
        revokeUseCase: revokeUseCase,
        onRevoked: { isAuthenticated = false }
    )
    ```
- [x] Full updated `PodDriveApp.swift`:
  ```swift
  import SwiftUI
  import shared

  @main
  struct PodDriveApp: App {

      @State private var isAuthenticated = false

      private let syncUseCase: SyncPodcastLibraryUseCase
      private let setTierUseCase: SetEpisodeTierUseCase
      private let podcastRepository: any PodcastRepository
      private let revokeUseCase: RevokeSpotifyAccessUseCase

      init() {
          let dbFactory = DatabaseDriverFactory()
          let db = PodDrive(driver: dbFactory.createDriver())
          let podcastRepo = SqlDelightPodcastRepository(db: db)
          let episodeRepo = SqlDelightEpisodeRepository(db: db)

          let storage = SecureStorage()
          let authHttpClient = SpotifyHttpClientKt.createAuthHttpClient()
          let clientId = Bundle.main.infoDictionary?["SPOTIFY_CLIENT_ID"] as? String ?? ""
          let authManager = SpotifyAuthManager(tokenStorage: storage, httpClient: authHttpClient, clientId: clientId)

          revokeUseCase = RevokeSpotifyAccessUseCase(authManager: authManager)

          let apiHttpClient = SpotifyHttpClientKt.createSpotifyHttpClient(tokenStorage: storage, tokenRefresher: authManager)
          let libraryClient = SpotifyLibraryApiClient(httpClient: apiHttpClient)
          syncUseCase = SyncPodcastLibraryUseCase(
              libraryApiClient: libraryClient,
              podcastRepository: podcastRepo,
              episodeRepository: episodeRepo
          )
          podcastRepository = podcastRepo
          setTierUseCase = SetEpisodeTierUseCase(podcastRepository: podcastRepo)  // ← ADD (Story 2.2)
      }

      var body: some Scene {
          WindowGroup {
              if isAuthenticated {
                  HomeView(
                      syncUseCase: syncUseCase,
                      setTierUseCase: setTierUseCase,                         // ← ADD (Story 2.2)
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

### T10 — Unit tests for `SetEpisodeTierUseCase` (AC: 2)

- [x] Create `shared/src/commonTest/kotlin/com/poddrive/usecase/SetEpisodeTierUseCaseTest.kt`:
  ```kotlin
  package com.poddrive.usecase

  import com.poddrive.domain.model.AppError
  import com.poddrive.domain.model.EpisodeTier
  import com.poddrive.domain.model.Podcast
  import com.poddrive.domain.model.Result
  import com.poddrive.repository.PodcastRepository
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertIs

  class SetEpisodeTierUseCaseTest {

      // -----------------------------------------------------------------------
      // Fake
      // -----------------------------------------------------------------------

      private class FakePodcastRepository : PodcastRepository {
          data class TierUpdate(val id: String, val tier: EpisodeTier)

          val tierUpdates = mutableListOf<TierUpdate>()
          var shouldThrow: Exception? = null

          override fun upsertAll(podcasts: List<Podcast>) = Unit
          override fun getAllWithUnheardCount(): List<Podcast> = emptyList()
          override fun getByTierWithUnheardCount(tier: String): List<Podcast> = emptyList()

          override fun setPodcastTier(id: String, tier: EpisodeTier) {
              shouldThrow?.let { throw it }
              tierUpdates.add(TierUpdate(id, tier))
          }
      }

      // -----------------------------------------------------------------------
      // Tests
      // -----------------------------------------------------------------------

      @Test
      fun `execute sets LOVED tier and returns Success`() {
          val repo = FakePodcastRepository()
          val useCase = SetEpisodeTierUseCase(repo)

          val result = useCase.execute("spotify:show:abc", EpisodeTier.LOVED)

          assertIs<Result.Success<Unit, AppError>>(result)
          assertEquals(1, repo.tierUpdates.size)
          assertEquals(FakePodcastRepository.TierUpdate("spotify:show:abc", EpisodeTier.LOVED), repo.tierUpdates[0])
      }

      @Test
      fun `execute sets LIKED tier and returns Success`() {
          val repo = FakePodcastRepository()
          val useCase = SetEpisodeTierUseCase(repo)

          val result = useCase.execute("spotify:show:xyz", EpisodeTier.LIKED)

          assertIs<Result.Success<Unit, AppError>>(result)
          assertEquals(EpisodeTier.LIKED, repo.tierUpdates[0].tier)
      }

      @Test
      fun `execute sets NONE tier (remove tag) and returns Success`() {
          val repo = FakePodcastRepository()
          val useCase = SetEpisodeTierUseCase(repo)

          val result = useCase.execute("spotify:show:xyz", EpisodeTier.NONE)

          assertIs<Result.Success<Unit, AppError>>(result)
          assertEquals(EpisodeTier.NONE, repo.tierUpdates[0].tier)
      }

      @Test
      fun `execute returns Failure when repository throws`() {
          val repo = FakePodcastRepository()
          repo.shouldThrow = RuntimeException("SQLite locked")
          val useCase = SetEpisodeTierUseCase(repo)

          val result = useCase.execute("spotify:show:abc", EpisodeTier.LOVED)

          assertIs<Result.Failure<Unit, AppError>>(result)
          assertIs<AppError.Unknown>(result.error)
      }
  }
  ```

---

## Dev Notes

### Architecture Compliance

- ✅ `SetEpisodeTierUseCase` delegates only — no direct SQLDelight calls in use case (AR10)
- ✅ `Result<Unit, AppError>` — no exceptions cross KMP boundary (AR5)
- ✅ `CancellationException` re-thrown in `SetEpisodeTierUseCase.execute()` catch block (Story 1.4 lesson)
- ✅ `execute()` is synchronous (no `suspend`) — SQLDelight write is fast; dispatched to `Dispatchers.IO` by ViewModel
- ✅ Tier persisted to SQLDelight on-device only — never transmitted (NFR7)
- ✅ `TierBadge` uses colour AND icon — never colour alone (UX-DR11)
- ✅ Bottom sheet (not modal dialog) for non-destructive action (UX-DR9)
- ✅ `setPodcastTier` reuses existing `updateTier` query from `Podcast.sq` — no schema migration needed

### Existing SQLDelight Infrastructure — No Changes Needed

`Podcast.sq` already has all queries needed:
```sql
updateTier:
UPDATE podcasts SET tier = ? WHERE id = ?;
```
No new `.sq` files or queries needed. SQLDelight schema is NOT changing.

### `ModalBottomSheet` in Compose Material3

`ModalBottomSheet` and `rememberModalBottomSheetState` require `@OptIn(ExperimentalMaterial3Api::class)` on the calling composable. Apply it to `HomeScreen`. In Material3 BOM 2024.09.03, this API is experimental but stable for use.

The sheet is conditionally composed (`if (selectedPodcast != null)`) — this means a new `sheetState` is created each time the sheet opens. This is the correct pattern for `ModalBottomSheet`; do NOT hoist the `sheetState` above the `if` block.

### `combinedClickable` — Required for Long Press

- Import: `androidx.compose.foundation.combinedClickable`
- `combinedClickable(onClick = {}, onLongClick = ...)` — both `onClick` and `onLongClick` must be provided
- This replaces the plain `clickable` modifier on `ShowTile`; no behaviour regression
- The empty `onClick = {}` is intentional for now; future story will navigate to episode list

### iOS `.confirmationDialog` vs `.sheet`

Use `.confirmationDialog(isPresented:)` not `.sheet(item:)` for tier selection on iOS. Reasons:
1. `Podcast` (KMP-generated class) does not conform to `Identifiable` — `.sheet(item:)` would require wrapping
2. `.confirmationDialog` renders as a native action sheet (slides from bottom) — correct iOS equivalent of Android `ModalBottomSheet`
3. `.sheet(isPresented:)` would show a half-page modal — not appropriate for a 3-option menu

### iOS `EpisodeTier` Swift Enum Names

KMP `EpisodeTier` enum (`LOVED`, `LIKED`, `NONE`) bridges to Swift as **lowercase** values:
- `EpisodeTier.loved` (not `EpisodeTier.LOVED`)
- `EpisodeTier.liked`
- `EpisodeTier.none`

Comparison: `podcast.tier != EpisodeTier.none` (lowercase `.none`)

### iOS `SetEpisodeTierUseCase` — Synchronous, Not Async

`SetEpisodeTierUseCase.execute()` is synchronous Kotlin (not a `suspend` function). In the KMP Swift bridge, it will NOT be an `async` function — call it directly:
```swift
let result = setTierUseCase.execute(podcastId: podcast.id, tier: tier)  // NO await
```
Wrap in `Task {}` only to satisfy `@MainActor` isolation when re-reading the repository afterward.

### FakePodcastRepository in Tests

For `SetEpisodeTierUseCaseTest`, define a new `FakePodcastRepository` inline in the test file. Do NOT reuse the `FakePodcastRepository` from `SyncPodcastLibraryUseCaseTest.kt` — that class is in the private scope of its own test file and does not implement `setPodcastTier`. The new fake must implement all 4 interface methods.

### Tier Refresh After Save (Android)

After a successful `setTier()` call, `HomeViewModel` calls `podcastRepository.getAllWithUnheardCount()` on `Dispatchers.IO` and replaces `_uiState.value` with a new `UiState.Success`. This causes `LazyColumn` to recompose with the updated `tier` value — the `TierBadge` on the affected `ShowTile` will appear or change immediately. No additional UI animation needed.

### Project Structure — Files to Create

```
shared/src/
└── commonMain/kotlin/com/poddrive/
│   └── usecase/
│       └── SetEpisodeTierUseCase.kt              ← CREATE (T3)
└── commonTest/kotlin/com/poddrive/
    └── usecase/
        └── SetEpisodeTierUseCaseTest.kt          ← CREATE (T10)
```

### Project Structure — Files to Modify

```
shared/src/commonMain/kotlin/com/poddrive/
├── repository/
│   └── PodcastRepository.kt                     ← ADD setPodcastTier() method (T1)
└── storage/
    └── SqlDelightPodcastRepository.kt            ← ADD setPodcastTier() impl (T2)

androidApp/src/main/kotlin/com/poddrive/android/
├── PodDriveApplication.kt                        ← ADD setEpisodeTierUseCase field + init (T4)
└── ui/home/
    ├── HomeViewModel.kt                          ← ADD tier sheet state + setTier() (T5)
    └── HomeScreen.kt                             ← ADD long-press, bottom sheet, TierBadgeIconOnly (T6)

iosApp/iosApp/
├── PodDriveApp.swift                             ← ADD setTierUseCase field + pass to HomeView (T9)
└── ui/home/
    ├── HomeViewModel.swift                       ← ADD setTierUseCase, showTierSheet state, setTier() (T7)
    └── HomeView.swift                            ← ADD setTierUseCase param, long-press, confirmationDialog, TierBadgeIconOnly (T8)
```

### Previous Story Intelligence (2.1 + 1.x)

- **Kotlin 2.0.21** — pinned; do NOT upgrade
- **SQLDelight 2.0.2** — `db.podcastQueries.updateTier(tier, id)` — parameter ORDER matters: tier first, id second (matches Podcast.sq `UPDATE podcasts SET tier = ? WHERE id = ?`)
- **`Dispatchers.IO`** — required for ALL SQLDelight reads and writes in Android ViewModels (established in 2.1)
- **`CancellationException` must be re-thrown** — `if (e is CancellationException) throw e` — always (from 1.4 P1 fix)
- **`PodDriveApplication.from(application)`** — established DI pattern; `setEpisodeTierUseCase` accessed as `app.setEpisodeTierUseCase`
- **iOS stored properties in `init()`** — dependencies built once in `PodDriveApp.init()`, NOT lazily in body. Prevents re-init on recompose (from 2.1 P1 fix)
- **iOS `@StateObject` in `init`** — `_viewModel = StateObject(wrappedValue: ...)` pattern — do NOT use `@StateObject` in body computed property
- **iOS `any PodcastRepository`** — the `podcastRepository` parameter type is `any PodcastRepository` (existential), NOT just `PodcastRepository`. Match this in new signatures
- **SQLDelight `db.podcastQueries.selectAll()` inside `transaction {}`** — safe; the batch-tier-preservation pattern in `upsertAll` reads all tiers before the transaction loop. `setPodcastTier` is a simple single-row update — no transaction needed
- **`INSERT OR REPLACE` destroys existing rows** — N/A for this story (we use `updateTier` not `upsert`). No tier preservation issue here

### Anti-Patterns to Avoid

❌ Do NOT call `db.podcastQueries.updateTier(...)` directly from `SetEpisodeTierUseCase` — go through `PodcastRepository`
❌ Do NOT make `SetEpisodeTierUseCase.execute()` a `suspend fun` — the underlying SQLDelight call is synchronous
❌ Do NOT use `Alert` or `Dialog` for tier selection on iOS — use `.confirmationDialog` (action sheet)
❌ Do NOT use `Alert` or `AlertDialog` in Compose for tier selection — use `ModalBottomSheet` (UX-DR9)
❌ Do NOT forget `@OptIn(ExperimentalMaterial3Api::class)` on `HomeScreen` — `ModalBottomSheet` requires it
❌ Do NOT access `_selectedPodcast.value` inside the `if (selectedPodcast != null)` block after calling `viewModel.setTier()` — it may already be null. Capture the value before calling (shown in T6 as `selectedPodcast!!` which is safe within the `if` block only)
❌ Do NOT use `Sheet(item:)` for tier selection on iOS — `Podcast` is not `Identifiable`
❌ Do NOT add `@Suppress("UNCHECKED_CAST")` globally — only suppress at the specific cast site if needed
❌ Do NOT change `episode_tiers` table in this story — per-episode tier overrides are future scope

### References

- [Source: epics.md#Story 2.2] — Acceptance criteria, FR5, FR6, NFR7
- [Source: architecture.md#Data Architecture] — SQLDelight schema, `updateTier` query, AR3
- [Source: architecture.md#Use Case rule] — AR10, AR5 — use case delegates only
- [Source: ux-design-specification.md#TierBadge] — ICON_ONLY variant, UX-DR5, UX-DR11
- [Source: ux-design-specification.md#Navigation] — UX-DR9 bottom sheets
- [Source: 2-1-fetch-and-display-podcast-library.md] — TierBadge FULL variant, ShowTile, HomeViewModel, HomeView patterns
- [Source: Podcast.sq] — `updateTier` query already present — no migration needed

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Gradle daemon requires TCP loopback networking (127.0.0.1) for main↔daemon IPC. This is blocked in the Claude Code sandbox environment on Windows (java.io.IOException: Unable to establish loopback connection). All code was verified by static review. Reviewer should run `./gradlew :shared:commonTest :shared:assemble :androidApp:assembleDebug` locally before merging.

### Completion Notes List

- T1: Added `setPodcastTier(id, tier)` to `PodcastRepository` interface with `EpisodeTier` import. Added no-op stub to `SyncPodcastLibraryUseCaseTest.FakePodcastRepository` to keep that test compilable.
- T2: Added `setPodcastTier` override in `SqlDelightPodcastRepository` calling `db.podcastQueries.updateTier(tier = tier.name, id = id)`. Parameter order (tier first, id second) confirmed against `Podcast.sq` line 32.
- T3: Created `SetEpisodeTierUseCase`. `execute()` is non-suspend (SQLDelight call is synchronous). `CancellationException` is re-thrown before the general catch. Returns `Result<Unit, AppError>`.
- T4: Wired `setEpisodeTierUseCase` in `PodDriveApplication.onCreate()` after `syncPodcastLibraryUseCase`. Import added.
- T5: Replaced `HomeViewModel.kt` — added `_selectedPodcast` StateFlow, `showTierSheet()`, `dismissTierSheet()`, `setTier()` (optimistic dismiss → `Dispatchers.IO` write → DB reload).
- T6: Replaced `HomeScreen.kt` — `@OptIn(ExperimentalMaterial3Api::class)` on `HomeScreen`, `ModalBottomSheet` outside `Scaffold`, `sheetState` inside `if` block, `combinedClickable` (correct package), `TierSheetContent`, `TierBadgeIconOnly` added.
- T7: Replaced `HomeViewModel.swift` — added `setTierUseCase`, `showTierSheet`, `selectedPodcast`, `showTierSheetFor()`, `setTier()`. `execute()` called without `await` (synchronous KMP bridge). EpisodeTier uses lowercase Swift names.
- T8: Replaced `HomeView.swift` — added `setTierUseCase` init param, `.confirmationDialog` on `TabView`, `.onLongPressGesture` on rows, `TierBadgeIconOnly`. Uses `.confirmationDialog` (not `.sheet(item:)`) because `Podcast` is not `Identifiable`.
- T9: Updated `PodDriveApp.swift` — added `setTierUseCase` stored property, initialized in `init()`, passed to `HomeView`.
- T10: Created `SetEpisodeTierUseCaseTest.kt` with self-contained `FakePodcastRepository` implementing all 4 interface methods. 4 tests cover LOVED, LIKED, NONE tiers, and repository exception path. Plain `@Test` (non-suspend, no `runTest` needed).

### File List

- `poddrive/shared/src/commonMain/kotlin/com/poddrive/repository/PodcastRepository.kt` (modified)
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/storage/SqlDelightPodcastRepository.kt` (modified)
- `poddrive/shared/src/commonMain/kotlin/com/poddrive/usecase/SetEpisodeTierUseCase.kt` (created)
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/SetEpisodeTierUseCaseTest.kt` (created)
- `poddrive/shared/src/commonTest/kotlin/com/poddrive/usecase/SyncPodcastLibraryUseCaseTest.kt` (modified — stub added to FakePodcastRepository)
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt` (modified)
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeViewModel.kt` (modified)
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeScreen.kt` (modified)
- `poddrive/iosApp/iosApp/ui/home/HomeViewModel.swift` (modified)
- `poddrive/iosApp/iosApp/ui/home/HomeView.swift` (modified)
- `poddrive/iosApp/iosApp/PodDriveApp.swift` (modified)

## Change Log

- 2026-03-22: Story 2.2 implemented by claude-sonnet-4-6. All 10 tasks complete. Added `setPodcastTier` to repository layer, created `SetEpisodeTierUseCase`, wired DI on both Android and iOS, updated Android HomeViewModel/HomeScreen (ModalBottomSheet, long-press, TierBadgeIconOnly), updated iOS HomeViewModel/HomeView (confirmationDialog, long-press, TierBadgeIconOnly), added 4-test `SetEpisodeTierUseCaseTest`. Story status set to "review".
