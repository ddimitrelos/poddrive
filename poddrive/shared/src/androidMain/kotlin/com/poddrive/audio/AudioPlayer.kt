package com.poddrive.audio

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Android ExoPlayer-backed implementation of [AudioPlayer].
 *
 * Two constructors:
 * - `AudioPlayer()` (no-arg) — satisfies the `expect` declaration; used as the super-constructor
 *   for test subclasses ([FakeAudioPlayer]). Creates an instance with no context — all player
 *   operations are no-ops until a context is provided.
 * - `AudioPlayer(context: Context)` — production constructor. Stores the context for ExoPlayer
 *   and [AudioManager] operations. Created lazily by [com.poddrive.android.PodDriveApplication].
 *
 * Audio focus (AC4):
 * - [play] and [resume] request [AudioManager.AUDIOFOCUS_GAIN] before starting playback.
 * - [AudioManager.OnAudioFocusChangeListener] pauses on LOSS and LOSS_TRANSIENT.
 * - [stop] abandons focus so other apps (e.g. navigation) can resume.
 *
 * Background playback (AC5):
 * - ExoPlayer is created inside [play] and released inside [stop].
 * - The enclosing [com.poddrive.android.service.DriveSessionForegroundService] holds the
 *   WakeLock and MediaSession notification; ExoPlayer inherits that lifecycle context.
 *
 * [Source: Story 4.10, AC3-AC5; ADR-002]
 */
actual open class AudioPlayer actual constructor() {

    /** Nullable — null when constructed via no-arg constructor (test subclassing path). */
    private var _context: Context? = null

    private var exoPlayer: ExoPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Lazy so Looper.getMainLooper() is not called during the no-arg test-subclassing constructor
    // path — the main Looper is not prepared in JVM unit tests and would throw RuntimeException.
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Listener that pauses playback on audio focus loss.
     * Posts to the main thread via [mainHandler] so ExoPlayer is always called from main (P1).
     */
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> mainHandler.post { pause() }
            // AUDIOFOCUS_GAIN: resume() is NOT called automatically here — the user should
            // decide when to resume (e.g. after a navigation prompt finishes).
        }
    }

    /**
     * Production constructor — stores [context] for ExoPlayer and AudioManager operations.
     * Chains to the no-arg `actual constructor()` required by the `expect` declaration.
     */
    constructor(context: Context) : this() {
        _context = context
    }

    /**
     * Plays [url] from [resumePositionMs]. Requests audio focus first (AC4).
     * Replaces any currently active ExoPlayer instance.
     */
    actual open fun play(url: String, resumePositionMs: Long) {
        val ctx = _context ?: return
        requestAudioFocus(ctx)
        // Release any existing player before creating a new one (AC1 — single active player).
        exoPlayer?.release()
        val player = ExoPlayer.Builder(ctx).build().also { exoPlayer = it }
        player.setMediaItem(MediaItem.fromUri(url))
        player.seekTo(resumePositionMs)
        player.prepare()
        player.play()
    }

    /** Pauses ExoPlayer without releasing it or abandoning audio focus. */
    actual open fun pause() {
        exoPlayer?.pause()
    }

    /** Re-requests audio focus and resumes ExoPlayer. No-op if no player is loaded. */
    actual open fun resume() {
        val ctx = _context ?: return
        requestAudioFocus(ctx)
        exoPlayer?.play()
    }

    /**
     * Stops and releases ExoPlayer, then abandons audio focus (AC4).
     * Safe to call when no media is playing.
     */
    actual open fun stop() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        abandonAudioFocus()
    }

    /** Returns ExoPlayer's current position in ms, or 0 if no player is active. */
    actual open fun currentPositionMs(): Long = exoPlayer?.currentPosition ?: 0L

    /** Returns true if ExoPlayer reports it is currently playing. */
    actual open fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false

    /**
     * Returns the active [ExoPlayer] instance so [DriveSessionForegroundService] can attach it to
     * a [androidx.media3.session.MediaSession] for hardware media button and lock screen integration.
     * Returns null if no episode is currently loaded.
     */
    actual open fun getExoPlayerForSession(): Any? = exoPlayer

    // -------------------------------------------------------------------------
    // Audio focus helpers
    // -------------------------------------------------------------------------

    private fun requestAudioFocus(context: Context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Abandon any existing request before building a new one to avoid duplicates.
        audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
        audioFocusRequest = request
        val result = manager.requestAudioFocus(request)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            android.util.Log.w("AudioPlayer", "Audio focus request not granted (result=$result)")
        }
    }

    private fun abandonAudioFocus() {
        val ctx = _context ?: return
        val manager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }
}
