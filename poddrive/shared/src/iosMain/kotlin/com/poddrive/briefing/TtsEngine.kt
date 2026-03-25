package com.poddrive.briefing

/**
 * iOS actual implementation of [TtsEngine].
 * Stub for MVP Phase 1 (Android-first). Full AVSpeechSynthesizer integration is Story 3.4.
 * [Story 4.5]
 */
actual class TtsEngine actual constructor(context: Any) {
    actual fun speak(text: String) {
        // iOS TTS implementation deferred to Story 3.4 (MVP Phase 2)
    }
    actual fun shutdown() {
        // no-op stub
    }
}
