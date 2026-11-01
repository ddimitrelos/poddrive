package com.poddrive.android.ui.drive

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the in-app playback controls panel added in Story 4.11 (AC7).
 *
 * Tests call [InAppPlaybackControls] (package-visible via `internal`) directly, avoiding the
 * need to instantiate [DriveSessionViewModel] or [PodDriveApplication]. This keeps tests
 * fast and self-contained.
 *
 * Run with: `./gradlew :androidApp:connectedDebugAndroidTest` (requires emulator or device).
 *
 * [Source: Story 4.11, AC1-AC3, AC5, AC7]
 */
class DriveSessionScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeUiState(
        isPlayingInApp: Boolean = false,
        playbackPositionMs: Long = 0L,
        playbackDurationMs: Long = 0L,
        playbackEpisodeTitle: String = "",
        isHandingOff: Boolean = false
    ) = DriveUiState(
        episodes = emptyList(),
        isHandingOff = isHandingOff,
        handoffEpisodeId = null,
        spotifyIntentLaunched = false,
        isPlayingInApp = isPlayingInApp,
        playbackPositionMs = playbackPositionMs,
        playbackDurationMs = playbackDurationMs,
        playbackEpisodeTitle = playbackEpisodeTitle
    )

    // -------------------------------------------------------------------------
    // AC1 — Progress visible while playing
    // -------------------------------------------------------------------------

    @Test
    fun showsEpisodeTitleAndTimeLabelsWhilePlaying() {
        val uiState = makeUiState(
            isPlayingInApp = true,
            playbackPositionMs = 30_000L,
            playbackDurationMs = 120_000L,
            playbackEpisodeTitle = "Interesting Episode"
        )
        composeTestRule.setContent {
            MaterialTheme {
                InAppPlaybackControls(
                    uiState = uiState,
                    isPlaying = true,
                    onPause = {},
                    onResume = {},
                    onSkip = {}
                )
            }
        }
        // Episode title visible (AC1)
        composeTestRule.onNodeWithText("Interesting Episode").assertIsDisplayed()
        // Elapsed time label: 30 s = "00:30" (AC1)
        composeTestRule.onNodeWithText("00:30").assertIsDisplayed()
        // Remaining time label: 90 s = "-01:30" (AC1)
        composeTestRule.onNodeWithText("-01:30").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // AC2 — Pause / resume button
    // -------------------------------------------------------------------------

    @Test
    fun showsPauseButtonWhenPlaying() {
        val uiState = makeUiState(isPlayingInApp = true, playbackDurationMs = 60_000L)
        composeTestRule.setContent {
            MaterialTheme {
                InAppPlaybackControls(
                    uiState = uiState,
                    isPlaying = true,
                    onPause = {},
                    onResume = {},
                    onSkip = {}
                )
            }
        }
        // Pause button is visible when isPlaying = true (AC2)
        composeTestRule.onNodeWithText("⏸ Pause").assertIsDisplayed()
    }

    @Test
    fun showsResumeButtonWhenPaused() {
        val uiState = makeUiState(isPlayingInApp = false, playbackDurationMs = 60_000L)
        composeTestRule.setContent {
            MaterialTheme {
                InAppPlaybackControls(
                    uiState = uiState,
                    isPlaying = false,
                    onPause = {},
                    onResume = {},
                    onSkip = {}
                )
            }
        }
        // Resume button is visible when isPlaying = false (AC2)
        composeTestRule.onNodeWithText("▶ Resume").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // AC3 — Skip button
    // -------------------------------------------------------------------------

    @Test
    fun showsSkipButton() {
        val uiState = makeUiState(isPlayingInApp = true, playbackDurationMs = 60_000L)
        composeTestRule.setContent {
            MaterialTheme {
                InAppPlaybackControls(
                    uiState = uiState,
                    isPlaying = true,
                    onPause = {},
                    onResume = {},
                    onSkip = {}
                )
            }
        }
        // Skip button is always visible when controls are shown (AC3)
        composeTestRule.onNodeWithText("⏭ Skip").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // formatDuration — pure function tests (can run without Compose infrastructure)
    // -------------------------------------------------------------------------

    @Test
    fun formatDurationZeroMs() {
        assert(formatDuration(0L) == "00:00") {
            "formatDuration(0) expected '00:00' but got '${formatDuration(0L)}'"
        }
    }

    @Test
    fun formatDurationThirtySeconds() {
        assert(formatDuration(30_000L) == "00:30") {
            "formatDuration(30_000) expected '00:30' but got '${formatDuration(30_000L)}'"
        }
    }

    @Test
    fun formatDurationOneMinuteFiveSeconds() {
        assert(formatDuration(65_000L) == "01:05") {
            "formatDuration(65_000) expected '01:05' but got '${formatDuration(65_000L)}'"
        }
    }

    @Test
    fun formatDurationOverSixtyMinutes() {
        // 62 minutes 10 seconds — no hour component; minutes can exceed 59
        assert(formatDuration(3_730_000L) == "62:10") {
            "formatDuration(3_730_000) expected '62:10' but got '${formatDuration(3_730_000L)}'"
        }
    }
}
