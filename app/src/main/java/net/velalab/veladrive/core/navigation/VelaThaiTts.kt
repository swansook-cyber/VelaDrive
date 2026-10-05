package net.velalab.veladrive.core.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VelaThaiTts(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var initialized = false
    private var lastSpokenKey: String? = null

    var isMuted: Boolean = false
        private set

    fun start() {
        if (tts == null) {
            tts = TextToSpeech(appContext, this)
        }
    }

    override fun onInit(status: Int) {
        initialized = status == TextToSpeech.SUCCESS
        if (!initialized) return

        val engine = tts ?: return
        val result = engine.setLanguage(Locale("th", "TH"))
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            initialized = false
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            tts?.stop()
        }
    }

    fun toggleMuted() {
        setMuted(!isMuted)
    }

    fun speakGuidance(snapshot: VelaGuidanceSnapshot?) {
        if (!initialized || isMuted || snapshot?.isNavigating != true) return

        val current = snapshot.currentInstruction?.trim().orEmpty()
        if (current.isBlank()) return

        val prep = snapshot.preparationInstruction?.trim().orEmpty()
        val key = "${current}|${prep}"
        if (key == lastSpokenKey) return

        lastSpokenKey = key

        val message =
            if (prep.isNotBlank() && prep != current) {
                "${current}. ${prep}"
            } else {
                current
            }

        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "vela-guidance-${message.hashCode()}"
        )
    }

    fun resetDeduplication() {
        lastSpokenKey = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
        lastSpokenKey = null
    }
}
