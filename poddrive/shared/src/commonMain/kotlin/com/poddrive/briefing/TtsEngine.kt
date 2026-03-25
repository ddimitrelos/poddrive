package com.poddrive.briefing

/**
 * Platform-agnostic text-to-speech abstraction.
 * Android actual: wraps android.speech.tts.TextToSpeech
 * iOS actual: wraps AVSpeechSynthesizer (Story 3.4 / iOS phase)
 *
 * [constructor context] is typed as [Any] at the KMP boundary;
 * platform actuals cast it to the appropriate context type.
 *
 * [Source: architecture.md#Audio Boundary]
 * [Story 4.5]
 */
expect class TtsEngine(context: Any) {
    /** Speak [text] immediately, flushing any queued speech. */
    fun speak(text: String)
    /** Release TTS resources — call from ViewModel.onCleared(). */
    fun shutdown()
}
