---
status: done
story_id: "2.3"
story_key: 2-3-tier-filter-tabs-on-shows-screen
epic: 2
created: 2026-03-22
---

# Story 2.3 — Tier Filter Tabs on Shows Screen

## User Story

As a **user**,
I want to filter my podcast list by All / Loved / Liked tabs,
So that I can quickly focus on shows in a specific tier.

---

## Acceptance Criteria

**AC1 — Filter renders on Shows screen**
**Given** the Shows screen is displaying the full library
**When** the screen loads
**Then** a `TierFilterTabRow` with three pill tabs ("All", "Loved", "Liked") appears above the podcast list

**AC2 — Filter applies correctly**
**When** the user taps a `TierFilterTab` pill
**Then** the list filters to show only podcasts matching the selected tier:
- "All" → all followed podcasts (default)
- "Loved" → only `EpisodeTier.LOVED`-tagged podcasts
- "Liked" → only `EpisodeTier.LIKED`-tagged podcasts

**AC3 — Empty state**
**When** a filter is active and no podcasts match
**Then** a helpful prompt is shown: "No [Loved/Liked] shows yet — long-press a show to tag it"

**AC4 — Filter resets on relaunch**
Filter state resets to "All" on app restart (stateless per session per UX spec).

**AC5 — Accessibility**
`TierFilterTabRow` announces selection state; never colour-only (UX-DR11).
Role = semantics tab list / tab (Android) or `accessibilityAddTraits(.isSelected)` (iOS).

---

## Scope — What Changes

This story is **UI-only**. No new use cases, no new DB queries, no shared/commonMain changes.

Filtering is **in-memory** from the already-loaded `uiState.podcasts` list.
`PodcastRepository.getByTierWithUnheardCount(tier)` already exists but is NOT used here — the full list is already loaded.

### Files Modified

| File | Change |
|------|--------|
| `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeViewModel.kt` | Add `_selectedFilter` StateFlow + `setFilter()` |
| `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeScreen.kt` | Add `TierFilterTabRow` composable, filter logic, empty state |
| `poddrive/iosApp/iosApp/ui/home/HomeViewModel.swift` | Add `@Published var selectedFilter: EpisodeTier` |
| `poddrive/iosApp/iosApp/ui/home/HomeView.swift` | Add `TierFilterRow` + `TierPill`, filter logic in `ShowsTab`, empty state |

No new files created. No shared module changes.

---

## Subtasks

- [x] T1 — Android: Add `selectedFilter` StateFlow + `setFilter()` to `HomeViewModel.kt`
- [x] T2 — Android: Add `TierFilterTabRow` composable to `HomeScreen.kt`
- [x] T3 — Android: Update `ShowsTabContent` + `PodcastList` for filter + empty state
- [x] T4 — iOS: Add `selectedFilter` published property to `HomeViewModel.swift`
- [x] T5 — iOS: Add `TierFilterRow` + `TierPill` to `HomeView.swift`
- [x] T6 — iOS: Update `ShowsTab` for filter logic + empty state

---

## Technical Requirements

### T1 — Filter State: Android (`HomeViewModel.kt`)

Add to `HomeViewModel` after `selectedPodcast` StateFlow:

```kotlin
private val _selectedFilter = MutableStateFlow(EpisodeTier.NONE)
val selectedFilter: StateFlow<EpisodeTier> = _selectedFilter.asStateFlow()

fun setFilter(filter: EpisodeTier) {
    _selectedFilter.value = filter
}
```

`EpisodeTier.NONE` = "All" (no filter). LOVED / LIKED = filtered views.

Collect in `HomeScreen` via:
```kotlin
val selectedFilter by viewModel.selectedFilter.collectAsState()
```

No new imports needed — `MutableStateFlow`, `StateFlow`, `asStateFlow()` already imported in `HomeViewModel.kt`.

---

### T2 — TierFilterTabRow: Android (`HomeScreen.kt`)

Add new composable to `HomeScreen.kt`. Place it near the bottom of the file, after `TierBadgeIconOnly`:

```kotlin
@Composable
fun TierFilterTabRow(
    selected: EpisodeTier,
    onSelect: (EpisodeTier) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == EpisodeTier.NONE,
            onClick = { onSelect(EpisodeTier.NONE) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selected == EpisodeTier.LOVED,
            onClick = { onSelect(EpisodeTier.LOVED) },
            leadingIcon = {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text("Loved") }
        )
        FilterChip(
            selected = selected == EpisodeTier.LIKED,
            onClick = { onSelect(EpisodeTier.LIKED) },
            leadingIcon = {
                Icon(
                    Icons.Default.ThumbUp,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text("Liked") }
        )
    }
}
```

**Import to add:** `import androidx.compose.material3.FilterChip`

`FilterChip` is a stable API in Material3 BOM 2024.09.03 — no `@OptIn` annotation required. The `@OptIn(ExperimentalMaterial3Api::class)` already on `HomeScreen` covers any edge case.

---

### T3 — Filter Logic + Empty State: Android (`HomeScreen.kt`)

**Update `HomeScreen` to collect filter state:**

```kotlin
val selectedFilter by viewModel.selectedFilter.collectAsState()
```

Pass `selectedFilter` and `viewModel::setFilter` down to `ShowsTabContent`.

**Update `ShowsTabContent` signature:**

```kotlin
@Composable
private fun ShowsTabContent(
    uiState: HomeViewModel.UiState,
    paddingValues: PaddingValues,
    selectedFilter: EpisodeTier,
    onFilterSelected: (EpisodeTier) -> Unit,
    onLongPress: (Podcast) -> Unit
)
```

In the `Success` branch, compute filtered list before passing to `PodcastList`:

```kotlin
is HomeViewModel.UiState.Success -> {
    val filteredPodcasts = when (selectedFilter) {
        EpisodeTier.NONE -> uiState.podcasts
        else             -> uiState.podcasts.filter { it.tier == selectedFilter }
    }
    PodcastList(
        podcasts = filteredPodcasts,
        paddingValues = paddingValues,
        selectedFilter = selectedFilter,
        onFilterSelected = onFilterSelected,
        onLongPress = onLongPress
    )
}
```

**Update `PodcastList` signature:**

```kotlin
@Composable
private fun PodcastList(
    podcasts: List<Podcast>,
    paddingValues: PaddingValues,
    selectedFilter: EpisodeTier,
    onFilterSelected: (EpisodeTier) -> Unit,
    onLongPress: (Podcast) -> Unit
)
```

Inside `LazyColumn`, after the "My Shows" header `item {}`, add:

```kotlin
item {
    TierFilterTabRow(selected = selectedFilter, onSelect = onFilterSelected)
}
```

After the filter row item, check for empty state:

```kotlin
if (podcasts.isEmpty() && selectedFilter != EpisodeTier.NONE) {
    item {
        val tierLabel = if (selectedFilter == EpisodeTier.LOVED) "Loved" else "Liked"
        Box(
            modifier = Modifier
                .fillParentMaxSize()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No $tierLabel shows yet — long-press a show to tag it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
} else {
    items(podcasts, key = { it.id }) { podcast ->
        ShowTile(podcast = podcast, onLongPress = { onLongPress(podcast) })
        HorizontalDivider()
    }
}
```

**Import to add:** `import androidx.compose.ui.text.style.TextAlign`

---

### T4 — Filter State: iOS (`HomeViewModel.swift`)

Add to `HomeViewModel` after `showTierSheet`:

```swift
/// Active tier filter. `.none` = "All" (default). Resets to `.none` on app restart.
@Published var selectedFilter: EpisodeTier = .none
```

No setter method needed — SwiftUI binds directly to the `@Published` property via `$viewModel.selectedFilter`.

---

### T5 — TierFilterRow + TierPill: iOS (`HomeView.swift`)

Add two new private structs to `HomeView.swift`, above the `Color(hex:)` extension:

```swift
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
```

**Critical:** `Color(hex:)` is already a `private extension Color` in `HomeView.swift`. Reuse it directly — do NOT declare it again.

---

### T6 — Filter Logic + Empty State: iOS (`HomeView.swift`)

**Update `ShowsTab` to use filter:**

`ShowsTab` receives `viewModel: HomeViewModel` via `@ObservedObject`. Replace the `podcastList(_:)` helper with `podcastListWithFilter(allPodcasts:)`:

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
                    podcastListWithFilter(allPodcasts: podcasts)
                case .error(let message, let cached):
                    ScrollView {
                        Text(message)
                            .foregroundColor(.red)
                            .padding()
                        if !cached.isEmpty {
                            podcastListWithFilter(allPodcasts: cached)
                        }
                    }
                }
            }
            .navigationTitle("Shows")
        }
    }

    @ViewBuilder
    private func podcastListWithFilter(allPodcasts: [Podcast]) -> some View {
        let filtered: [Podcast] = {
            switch viewModel.selectedFilter {
            case .none:  return allPodcasts
            case .loved: return allPodcasts.filter { $0.tier == .loved }
            case .liked: return allPodcasts.filter { $0.tier == .liked }
            default:     return allPodcasts
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
```

Remove the old `podcastList(_:)` helper from `ShowsTab`.

---

## Architecture Compliance

- **No shared/commonMain changes** — filtering is purely presentational
- **`Dispatchers.IO`** — no new DB calls; no threading changes needed
- **`EpisodeTier.NONE`** used as "All" sentinel — intentional and consistent; no new enum needed
- **`PodcastRepository.getByTierWithUnheardCount`** — EXISTS but NOT used here; in-memory filtering avoids redundant DB round-trip
- **`HomeViewModel.kt` StateFlow pattern** — new `_selectedFilter` follows exact same pattern as `_selectedPodcast` (Story 2.2)
- **No new `UiState` variants** — filter is an orthogonal `StateFlow`, not a replacement for `UiState`

---

## Key Patterns from Previous Stories

### Android (from Story 2.2)
- StateFlow: `private val _x = MutableStateFlow(default)` + `val x: StateFlow<T> = _x.asStateFlow()`
- In composable: `val x by viewModel.x.collectAsState()`
- `@OptIn(ExperimentalMaterial3Api::class)` already on `HomeScreen` composable — covers `FilterChip` if needed
- Colour constants already in file: `Color(0xFFF87171)` coral, `Color(0xFF60A5FA)` blue

### iOS (from Story 2.2)
- `@ObservedObject var viewModel: HomeViewModel` in `ShowsTab` — use `$viewModel.selectedFilter` for `@Binding`
- KMP `EpisodeTier` Swift bridge: use `.none`, `.loved`, `.liked` (lowercase)
- `Color(hex:)` is `private extension Color` in `HomeView.swift` — reuse directly, never duplicate
- iOS 16 min target — **do NOT use `ContentUnavailableView`** (iOS 17+ only)

### Android — Existing `PodcastList` structure (from Story 2.1)
```
LazyColumn {
    item { "My Shows" header + underline }
    item { TierFilterTabRow }   ← ADD HERE (Story 2.3)
    item { empty state }        ← ADD HERE when filtered list empty
    items(podcasts) { ShowTile + Divider }
}
```

---

## Anti-Patterns to Avoid

❌ Do NOT call `getByTierWithUnheardCount` from ViewModel for filtering — in-memory from loaded list is correct
❌ Do NOT add a new `FilteredUiState` variant — filter is orthogonal `StateFlow`, not a UiState change
❌ Do NOT persist filter selection to SQLDelight — ephemeral (resets on relaunch per UX spec)
❌ Do NOT add `GetFilteredPodcastsUseCase` — zero new use cases for this story
❌ Do NOT use `ContentUnavailableView` on iOS — iOS 16 min target; use manual `VStack` empty state
❌ Do NOT show `TierFilterTabRow` in Loading or Error states — Success branch only
❌ iOS: Do NOT use `@State var selectedFilter` in the View — belongs in `HomeViewModel` (consistent with `showTierSheet`)
❌ Do NOT duplicate `Color(hex:)` extension in `HomeView.swift` — already defined once, reuse it

---

## Tests

No new unit tests required — filter logic is trivial `List.filter { }` in the UI layer; no test framework available for instrumented UI tests in MVP.

**Manual verification checklist:**
1. Shows tab loads → three pills visible, "All" selected by default
2. Tap "Loved" → only LOVED-tagged podcasts shown; "Loved" pill is selected (filled)
3. Tap "Liked" → only LIKED-tagged podcasts shown
4. Tap "All" → full list restored
5. Filter active with no matches → empty state text: "No Loved shows yet — long-press a show to tag it"
6. Long-press on a filtered row → tier bottom sheet still opens (regression check)
7. Tier assignment via bottom sheet → updated podcast still visible in filtered view (or disappears if tier no longer matches filter)
8. Kill and relaunch app → filter resets to "All"

---

## Source References

- [Source: epics.md#Story 2.3] — User story, AC1-AC4, UX-DR6
- [Source: ux-design-specification.md#TierFilterTabs] — Pill anatomy, SELECTED/UNSELECTED states, accessibility role=TabList
- [Source: 2-2-loved-liked-tier-assignment-and-management.md#Dev Notes] — StateFlow patterns, iOS KMP bridge, colour constants, `any PodcastRepository` type
- [Source: HomeScreen.kt] — Existing `PodcastList`, `ShowsTabContent`, `TierBadge`, colour constants
- [Source: HomeView.swift] — Existing `ShowsTab`, `ShowTileRow`, `TierBadgeView`, `Color(hex:)` extension
- [Source: HomeViewModel.swift] — `@Published` patterns, `showTierSheet` as precedent for filter state placement

---

## Dev Notes

### Implementation Notes (claude-sonnet-4-6, 2026-03-22)

**Android:**
- `HomeViewModel.kt`: Added `_selectedFilter MutableStateFlow(EpisodeTier.NONE)` + `selectedFilter` StateFlow + `setFilter()`. No new imports — all StateFlow APIs already imported.
- `HomeScreen.kt`: Added `import androidx.compose.material3.FilterChip` and `import androidx.compose.ui.text.style.TextAlign`. Added `TierFilterTabRow` composable (public, for potential reuse) with three `FilterChip` pills. Updated `HomeScreen` to collect `selectedFilter` and pass it + `viewModel::setFilter` down. Updated `ShowsTabContent` and `PodcastList` signatures to accept filter state. Filter computation (`when (selectedFilter)`) done in `ShowsTabContent` before passing to `PodcastList`. Empty state uses `fillParentMaxSize()` within `LazyColumn` for correct centering. Filter also applied to `cachedPodcasts` in the Error branch for consistency.

**iOS:**
- `HomeViewModel.swift`: Added `@Published var selectedFilter: EpisodeTier = .none` after `showTierSheet`.
- `HomeView.swift`: Added `TierFilterRow` and `TierPill` private structs above `Color(hex:)` extension. `TierPill` uses `accessibilityAddTraits(.isSelected)` for AC5. Reuses existing `Color(hex:)` extension without redeclaration. Updated `ShowsTab` to replace `podcastList(_:)` with `@ViewBuilder podcastListWithFilter(allPodcasts:)`. Empty state uses manual `VStack` (iOS 16 — no `ContentUnavailableView`). `$viewModel.selectedFilter` binding passed to `TierFilterRow`.

---

## Files Modified

- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeViewModel.kt`
- `poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/home/HomeScreen.kt`
- `poddrive/iosApp/iosApp/ui/home/HomeViewModel.swift`
- `poddrive/iosApp/iosApp/ui/home/HomeView.swift`

---

## Change Log

- 2026-03-22: Story 2.3 created by claude-sonnet-4-6. Status: ready-for-dev.
- 2026-03-22: Story 2.3 implemented by claude-sonnet-4-6. Added TierFilterTabRow (Android) + TierFilterRow/TierPill (iOS) with in-memory filter logic and empty states on both platforms. All 6 tasks complete. Status: review.
