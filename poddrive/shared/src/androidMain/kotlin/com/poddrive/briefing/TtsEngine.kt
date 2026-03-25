package com.poddrive.briefing

import android.content.Context
import android.speech.tts.TextToSpeech

/**
 * Android actual implementation of [TtsEngine].
 * Wraps [android.speech.tts.TextToSpeech].
 * Initialisation is deferred to the OS TTS engine — speak() is safe to call immediately
 * (the TTS engine queues utterances and plays once initialised).
 * [Story 4.5]
 */
actual class TtsEngine actual constructor(context: Any) {

    private val tts: TextToSpeech = TextToSpeech(context as Context) { /* init callback — no-op for MVP */ }

    actual fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pd_tts_${text.hashCode()}")
    }

    actual fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
