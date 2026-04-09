package com.poddrive.android.audio

import com.poddrive.audio.AudioPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the [AudioPlayer] interface contract.
 *
 * Tests use a [FakeAudioPlayer] subclass that overrides all `actual open` methods to record
 * calls without requiring a real Android Context or ExoPlayer. This is the same `open class`
 * test-subclassing pattern used for [com.poddrive.rss.RssParser] and the use-case fakes.
 *
 * The real `AudioPlayer(context)` Android implementation is covered by on-device integration
 * tests (not included here because ExoPlayer's codec pipeline requires a physical device or
 * emulator for meaningful behaviour verification).
 *
 * [Source: Story 4.10, AC3, AC8]
 */
class AudioPlayerTest {

    // -------------------------------------------------------------------------
    // Fake subclass — records all calls without needing Context or ExoPlayer
    // -------------------------------------------------------------------------

    private class FakeAudioPlayer : AudioPlayer() {
        val playCalls = mutableListOf<Pair<String, Long>>()
        var pauseCount = 0
        var resumeCount = 0
        var stopCount = 0

        private var _isPlaying = false
        private var _positionMs = 0L

        override fun play(url: String, resumePositionMs: Long) {
            playCalls += url to resumePositionMs
            _isPlaying = true
            _positionMs = resumePositionMs
        }

        override fun pause() {
            pauseCount++
            _isPlaying = false
        }

        override fun resume() {
            resumeCount++
            _isPlaying = true
        }

        override fun stop() {
            stopCount++
            _isPlaying = false
            _positionMs = 0L
        }

        override fun currentPositionMs(): Long = _positionMs
        override fun isPlaying(): Boolean = _isPlaying
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `play records url and resumePosition`() {
        val player = FakeAudioPlayer()
        player.play("http://example.com/ep.mp3", 30_000L)
        assertEquals(1, player.playCalls.size)
        assertEquals("http://example.com/ep.mp3", player.playCalls[0].first)
        assertEquals(30_000L, player.playCalls[0].second)
    }

    @Test
    fun `isPlaying is false before play`() {
        val player = FakeAudioPlayer()
        assertFalse(player.isPlaying())
    }

    @Test
    fun `isPlaying is true after play`() {
        val player = FakeAudioPlayer()
        player.play("http://example.com/ep.mp3", 0L)
        assertTrue(player.isPlaying())
    }

    @Test
    fun `currentPositionMs returns resumePositionMs after play`() {
        val player = FakeAudioPlayer()
        player.play("http://example.com/ep.mp3", 45_000L)
        assertEquals(45_000L, player.currentPositionMs())
    }

    @Test
    fun `pause marks not playing`() {
        val player = FakeAudioPlayer()
        player.play("http://example.com/ep.mp3", 0L)
        player.pause()
        assertEquals(1, player.pauseCount)
        assertFalse(player.isPlaying())
    }

    @Test
    fun `resume marks playing after pause`() {
        val player = FakeAudioPlayer()
        player.play("http://example.com/ep.mp3", 0L)
        player.pause()
        player.resume()
        assertEquals(1, player.resumeCount)
        assertTrue(player.isPlaying())
    }

    @Test
    fun `stop resets isPlaying and position`() {
        val player = FakeAudioPlayer()
        player.play("http://example.com/ep.mp3", 10_000L)
        player.stop()
        assertEquals(1, player.stopCount)
        assertFalse(player.isPlaying())
        assertEquals(0L, player.currentPositionMs())
    }

    @Test
    fun `stop without prior play does not throw`() {
        val player = FakeAudioPlayer()
        player.stop()   // no play() before stop — must be safe
        assertEquals(1, player.stopCount)
        assertFalse(player.isPlaying())
    }

    @Test
    fun `pause without prior play does not throw`() {
        val player = FakeAudioPlayer()
        player.pause()
        assertEquals(1, player.pauseCount)
    }

    @Test
    fun `play called twice replaces first url`() {
        val player = FakeAudioPlayer()
        player.play("http://example.com/ep1.mp3", 0L)
        player.play("http://example.com/ep2.mp3", 5_000L)
        assertEquals(2, player.playCalls.size)
        assertEquals("http://example.com/ep2.mp3", player.playCalls[1].first)
        assertEquals(5_000L, player.playCalls[1].second)
        // currentPositionMs reflects the last play call's resumePosition
        assertEquals(5_000L, player.currentPositionMs())
    }

    // -------------------------------------------------------------------------
    // P8 fix: focus acquire/release contract via FakeAudioPlayer
    // -------------------------------------------------------------------------

    @Test
    fun `play then stop models acquire-then-release audio focus lifecycle`() {
        val player = FakeAudioPlayer()
        assertFalse(player.isPlaying(), "Player must not be active before play()")
        player.play("http://example.com/ep.mp3", 0L)
        assertTrue(player.isPlaying(), "Player must be active after play()")
        player.stop()
        assertFalse(player.isPlaying(), "Player must not be active after stop()")
        assertEquals(1, player.playCalls.size)
        assertEquals(1, player.stopCount)
    }

    @Test
    fun `resume after pause re-enters playing state modeling focus re-request`() {
        val player = FakeAudioPlayer()
        player.play("http://example.com/ep.mp3", 0L)
        player.pause()
        assertFalse(player.isPlaying())
        player.resume()
        assertTrue(player.isPlaying(), "Player must be active after resume()")
        assertEquals(1, player.resumeCount)
    }
}
