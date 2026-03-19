# Story 1.1: KMP Monorepo & Build Foundation

Status: in-progress

## Story

As a **developer**,
I want the KMP monorepo initialised with Android and iOS app shells, CI/CD pipeline, and Gradle Version Catalog,
so that the team has a green, buildable project baseline to develop all subsequent features against.

## Acceptance Criteria

1. **Given** a fresh repository **When** the project is built **Then** a KMP monorepo exists with `shared/`, `androidApp/`, `iosApp/` modules matching architecture spec (AR1)
2. **Given** the monorepo **Then** Gradle Version Catalog (`gradle/libs.versions.toml`) manages all dependencies — no hardcoded versions in any `build.gradle.kts` (AR9)
3. **Given** the monorepo **Then** the Android app shell (Jetpack Compose, min SDK 29) launches on emulator/device showing a placeholder screen
4. **Given** the monorepo **Then** the iOS app shell (SwiftUI, iOS 16+, Swift 5.9+) launches on simulator/device showing a placeholder screen
5. **Given** the monorepo **When** a commit is pushed to GitHub **Then** GitHub Actions CI runs `.github/workflows/android-ci.yml` and `.github/workflows/ios-ci.yml` (macOS runner for iOS) and both produce a green build
6. **Given** the monorepo **Then** Fastlane is configured (`fastlane/Fastfile` + `fastlane/Appfile`) for Android → Google Play internal track and iOS → TestFlight (AR9)
7. **Given** the iOS app shell **Then** XCFramework output from the KMP `shared` module is embedded in `iosApp.xcodeproj` via Gradle Kotlin/Native task (not SPM — SPM is post-MVP)

## Tasks / Subtasks

- [x] Initialise root monorepo (AC: 1)
  - [x] Create `poddrive/` root directory with `settings.gradle.kts` including `shared`, `androidApp` modules
  - [x] Create root `build.gradle.kts` with KMP plugin applied to `shared`
  - [x] Create `gradle/libs.versions.toml` (Gradle Version Catalog) — declare all library versions here
- [x] Set up `shared/` KMP module (AC: 1, 2)
  - [x] Create `shared/build.gradle.kts` with `kotlin("multiplatform")` version 2.x and `kotlin("plugin.serialization")`
  - [x] Create source set directories: `shared/src/commonMain/kotlin/com/poddrive/`, `shared/src/androidMain/kotlin/com/poddrive/`, `shared/src/iosMain/kotlin/com/poddrive/`
  - [x] Create `shared/src/commonTest/kotlin/com/poddrive/` for future unit tests
  - [x] Add empty package placeholder file in `commonMain` (e.g., `com/poddrive/.gitkeep` or stub)
  - [x] Configure Kotlin/Native XCFramework output task in `shared/build.gradle.kts`
- [x] Set up `androidApp/` module (AC: 2, 3)
  - [x] Create Android app module: min SDK API 29, Kotlin 1.9+, Jetpack Compose enabled
  - [x] Add `androidApp/build.gradle.kts` — reference `shared` module as Gradle dependency
  - [x] Create `AndroidManifest.xml` (stub — no permissions needed yet)
  - [x] Create `MainActivity.kt` with placeholder Compose screen (single `Text("PodDrive")`)
  - [x] Create `PodDriveApplication.kt` (stub `Application` subclass)
  - [x] Create `ui/theme/Theme.kt` stub (Material 3 dark theme placeholder)
- [x] Set up `iosApp/` module (AC: 4, 7)
  - [x] Create Xcode project: `iosApp/iosApp.xcodeproj`, SwiftUI, Swift 5.9+, iOS 16+ deployment target
  - [x] Create `PodDriveApp.swift` (`@main` entry point)
  - [x] Create placeholder `ContentView.swift` (`Text("PodDrive")`)
  - [x] Embed XCFramework: add Gradle-generated XCFramework as embedded framework in Xcode build phases
  - [x] Create `ui/theme/Theme.swift` stub
- [x] Configure GitHub Actions CI (AC: 5)
  - [x] Create `.github/workflows/android-ci.yml`: checkout → Java 17 setup → `./gradlew :androidApp:assembleDebug` → `./gradlew :shared:testDebugUnitTest`
  - [x] Create `.github/workflows/ios-ci.yml` (macOS runner): checkout → Gradle XCFramework build → `xcodebuild -scheme iosApp -sdk iphonesimulator`
  - [x] Both workflows trigger on push to `main` and PR
- [x] Configure Fastlane (AC: 6)
  - [x] Create `fastlane/Appfile` with package name `com.poddrive.android` and app identifier `com.poddrive.ios`
  - [x] Create `fastlane/Fastfile` with lanes: `android beta` (Google Play internal) and `ios beta` (TestFlight)
- [ ] Verify end-to-end green build (AC: 1–7)
  - [ ] Run `./gradlew build` from monorepo root — must succeed with zero errors
  - [ ] Run Android app on emulator — placeholder screen visible
  - [ ] Run iOS app on simulator — placeholder screen visible
  - [ ] Confirm CI workflow passes on push

## Dev Notes

### Critical Architecture Decisions for This Story

- **Monorepo root:** `poddrive/` — all modules live here. Root `settings.gradle.kts` registers `shared` and `androidApp`. iOS (`iosApp/`) is an Xcode project, **not** a Gradle module — do not include it in `settings.gradle.kts`
- **KMP plugin version:** Use `kotlin("multiplatform")` version `2.x` (latest 2.x stable, e.g. 2.0.21). The PRD references Kotlin 1.9 for Android, but KMP requires Kotlin 2.x for stable multiplatform support. Use 2.x in `shared/build.gradle.kts`
- **Gradle DSL:** Kotlin DSL only (`*.kts`) — never Groovy. Every Gradle file uses `.kts` extension
- **Version Catalog:** `gradle/libs.versions.toml` is the single source of truth for all library versions. Reference via `libs.xyz` in build files — no hardcoded version strings anywhere
- **Android package:** `com.poddrive.android`; KMP shared package root: `com.poddrive` (no `.android` suffix in `commonMain`)
- **iOS bundle ID:** `com.poddrive.ios`
- **XCFramework (MVP rule):** Kotlin/Native Gradle task generates `shared.xcframework`. Embed in `iosApp.xcodeproj` via Build Phases → "Embed Frameworks". **Do NOT use SPM** — SPM local package is post-MVP per architecture gap analysis [Source: architecture.md#Gap Analysis]
- **Scope boundary:** This story creates an empty `shared` module with the correct source set skeleton only. No SQLDelight, no SecureStorage, no Ktor, no Bluetooth — those are Story 1.2+. No UI beyond a single placeholder screen on each platform

### Project Structure to Create

Match this directory tree exactly (from architecture.md):

```
poddrive/
├── .github/
│   └── workflows/
│       ├── android-ci.yml
│       └── ios-ci.yml
├── fastlane/
│   ├── Fastfile
│   └── Appfile
├── gradle/
│   └── libs.versions.toml              # Gradle Version Catalog
├── build.gradle.kts                    # Root — plugin declarations only
├── settings.gradle.kts                 # Registers: shared, androidApp (NOT iosApp)
│
├── shared/                             # KMP shared module — empty skeleton for now
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/poddrive/
│       ├── commonTest/kotlin/com/poddrive/
│       ├── androidMain/kotlin/com/poddrive/
│       └── iosMain/kotlin/com/poddrive/
│
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/poddrive/android/
│           ├── MainActivity.kt
│           ├── PodDriveApplication.kt
│           └── ui/theme/Theme.kt
│
└── iosApp/
    ├── iosApp.xcodeproj/
    └── iosApp/
        ├── PodDriveApp.swift           # @main entry point
        ├── ContentView.swift           # Placeholder only
        ├── Info.plist
        └── ui/theme/Theme.swift
```

> **NOT created in this story:** `sqldelight/` (Story 1.2), subdirectories under `commonMain/` like `domain/`, `usecase/`, `api/`, `storage/` (Story 1.2+)

### Naming & Convention Rules

From [Source: architecture.md#Naming Patterns]:

- Kotlin files: PascalCase → `MainActivity.kt`, `PodDriveApplication.kt`, `SpotifyApiClient.kt`
- Swift files: PascalCase → `PodDriveApp.swift`, `ContentView.swift`
- Gradle modules (directory names): camelCase → `shared`, `androidApp`
- Android package root: `com.poddrive.android`
- KMP commonMain package root: `com.poddrive`
- Constants: `SCREAMING_SNAKE_CASE`

### Testing Requirements

- No business logic exists in this story — `commonMain` is empty
- Verify Gradle build succeeds: `./gradlew build` (zero errors)
- Verify Android unit test task runs cleanly (zero tests is fine): `./gradlew :shared:testDebugUnitTest`
- Primary verification: CI workflows must produce green builds

### CI Configuration Details

- **Android workflow** (`android-ci.yml`):
  - Runner: `ubuntu-latest`
  - Java: 17 (required for AGP 8.x)
  - Steps: checkout → `actions/setup-java@v4` (Java 17, Temurin) → `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest`
  - Trigger: `push` to `main`, `pull_request`

- **iOS workflow** (`ios-ci.yml`):
  - Runner: `macos-latest` (required for Xcode toolchain)
  - Steps: checkout → `./gradlew :shared:assembleXCFramework` → `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination "platform=iOS Simulator,name=iPhone 15" build`
  - Trigger: `push` to `main`, `pull_request`
  - Note: Gradle XCFramework build MUST run before xcodebuild — iosApp depends on the generated framework

- **Fastlane** (stub configuration for now — credentials added before first release):
  - `Appfile`: `package_name("com.poddrive.android")` and `app_identifier("com.poddrive.ios")`
  - `Fastfile`: `lane :android_beta` (Google Play internal) and `lane :ios_beta` (TestFlight)

### Architecture References

From [Source: architecture.md#KMP Setup]:
```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.x"
    kotlin("plugin.serialization") version "2.x"
}
```

From [Source: architecture.md#Infrastructure & Deployment]:
- CI/CD: GitHub Actions (Android `ubuntu-latest`; iOS `macos-latest`)
- Android distribution: Fastlane → Google Play internal track
- iOS distribution: Fastlane → TestFlight
- Environment config: `BuildConfig` fields (Android) / `.xcconfig` (iOS)
- Dependency management: Gradle Version Catalog (`libs.versions.toml`)

### Project Structure Notes

- `iosApp/` is an Xcode project — it is NOT a Gradle subproject and NOT registered in `settings.gradle.kts`
- `sqldelight/` directory (for `.sq` schema files) is created in Story 1.2 — do not create it here
- `shared/src/commonMain/kotlin/com/poddrive/` subdirectories (`domain/`, `usecase/`, `api/`, `storage/`, `session/`, `briefing/`, `bluetooth/`, `recommendation/`) are scaffolded in Story 1.2+ — only the root package dir is needed now
- The `androidApp/src/main/kotlin/com/poddrive/android/` subdirectories (`ui/onboarding/`, `ui/home/`, `ui/drive/`, `ui/library/`, `ui/settings/`, `service/`) are created in their respective feature stories

### References

- [Source: architecture.md#Project Structure Decision] — KMP monorepo rationale
- [Source: architecture.md#Platform Initialization] — Android/iOS project setup
- [Source: architecture.md#KMP Setup] — Gradle plugin snippet
- [Source: architecture.md#Complete Project Directory Structure] — authoritative directory tree
- [Source: architecture.md#First Implementation Story] — scope definition for this story
- [Source: architecture.md#Gap Analysis] — XCFramework vs SPM decision (use XCFramework for MVP)
- [Source: architecture.md#Infrastructure & Deployment] — CI/CD + Fastlane requirements
- [Source: architecture.md#Dependency management] — Gradle Version Catalog requirement
- [Source: epics.md#Story 1.1] — user story + acceptance criteria

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- HALT: Local Gradle build verification blocked — JDK 17 and Android SDK not installed on this Windows dev machine. All files are created; verification must be completed via CI (`android-ci.yml` on `ubuntu-latest`, `ios-ci.yml` on `macos-latest`) after pushing to GitHub. iOS simulator test is only possible on macOS.
- `gradle-wrapper.jar` obtained via `curl` from `https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar` (43453 bytes) since `gradle wrapper` command was not available locally.
- `gradlew` execute bit set via `git update-index --chmod=+x`; `.gitattributes` created to enforce LF line endings.
- `project.pbxproj` written manually with consistent UUID references; must be validated on macOS xcodebuild (CI `macos-latest` runner is the definitive check). XCFramework linked statically (Link Binary With Libraries only, not Embed Frameworks).

### Completion Notes List

All KMP monorepo files created. Implementation tasks complete:
- Root Gradle infrastructure: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` (Kotlin 2.0.21, AGP 8.3.2, Compose BOM 2024.05.00)
- Gradle wrapper bootstrapped from Gradle v8.7.0 official sources
- `shared/` KMP module: `androidTarget()` + 3 iOS targets (x64/Arm64/SimArm64), XCFramework output task configured, `expect/actual Platform.kt` stubs, trivial unit test
- `androidApp/` module: Compose (Kotlin 2.x `compose.compiler` plugin), Material3 dark theme, `Text("PodDrive")` placeholder
- `iosApp/` Xcode project: `project.pbxproj` with `SWIFT_VERSION=5.9`, `IPHONEOS_DEPLOYMENT_TARGET=16.0`, `PRODUCT_BUNDLE_IDENTIFIER=com.poddrive.ios`; XCFramework in Link Binary With Libraries phase
- GitHub Actions: `android-ci.yml` (ubuntu-latest, Java 17 Temurin) + `ios-ci.yml` (macos-latest, XCFramework before xcodebuild, `CODE_SIGNING_ALLOWED=NO`)
- Fastlane stubs: `Appfile` + `Fastfile` with `android_beta` and `ios_beta` lanes

**Pending:** Verification task requires JDK 17 + Android SDK (not installed). Push to GitHub and verify CI green badges to complete the story.

### File List

poddrive/gradle/libs.versions.toml
poddrive/settings.gradle.kts
poddrive/build.gradle.kts
poddrive/gradle/wrapper/gradle-wrapper.properties
poddrive/gradle/wrapper/gradle-wrapper.jar
poddrive/gradlew
poddrive/gradlew.bat
poddrive/shared/build.gradle.kts
poddrive/shared/src/commonMain/kotlin/com/poddrive/Platform.kt
poddrive/shared/src/androidMain/kotlin/com/poddrive/Platform.kt
poddrive/shared/src/iosMain/kotlin/com/poddrive/Platform.kt
poddrive/shared/src/commonTest/kotlin/com/poddrive/PlatformTest.kt
poddrive/androidApp/build.gradle.kts
poddrive/androidApp/src/main/AndroidManifest.xml
poddrive/androidApp/src/main/kotlin/com/poddrive/android/MainActivity.kt
poddrive/androidApp/src/main/kotlin/com/poddrive/android/PodDriveApplication.kt
poddrive/androidApp/src/main/kotlin/com/poddrive/android/ui/theme/Theme.kt
poddrive/iosApp/iosApp.xcodeproj/project.pbxproj
poddrive/iosApp/iosApp/PodDriveApp.swift
poddrive/iosApp/iosApp/ContentView.swift
poddrive/iosApp/iosApp/Info.plist
poddrive/iosApp/iosApp/ui/theme/Theme.swift
.github/workflows/android-ci.yml
.github/workflows/ios-ci.yml
fastlane/Appfile
fastlane/Fastfile
.gitignore (modified — added KMP/Android/iOS/Fastlane patterns)
.gitattributes (created — enforces LF for gradlew)

### Change Log

- 2026-03-19: Created KMP monorepo scaffold — poddrive/ root, shared/ KMP module, androidApp/ Android shell, iosApp/ iOS shell, GitHub Actions CI, Fastlane stubs, Gradle wrapper v8.7
