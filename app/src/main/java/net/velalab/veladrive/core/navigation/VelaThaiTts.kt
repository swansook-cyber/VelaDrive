package net.velalab.veladrive.core.navigation

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

class VelaThaiTts(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var initialized = false
    private var lastSpokenKey: String? = null
    private var pendingSnapshot: VelaGuidanceSnapshot? = null

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
        if (
            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            initialized = false
            return
        }

        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setSpeechRate(0.95f)
        engine.setPitch(1.0f)

        pendingSnapshot?.let {
            pendingSnapshot = null
            speakGuidance(it)
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
        if (snapshot?.isNavigating != true || isMuted) return

        if (!initialized) {
            pendingSnapshot = snapshot
            return
        }

        val current = snapshot.currentInstruction?.trim().orEmpty()
        if (current.isBlank()) return

        val prep = snapshot.preparationInstruction?.trim().orEmpty()
        val stage = voiceStage(snapshot.distanceToNextManeuverMeters)
        val key = "${current}|${prep}|${stage}"
        if (key == lastSpokenKey) return

        lastSpokenKey = key

        val message =
            when {
                stage == VoiceStage.NOW ->
                    if (prep.isNotBlank() && prep != current) {
                        "${current}. ${prep}"
                    } else {
                        current
                    }
                prep.isNotBlank() && prep != current ->
                    "${current}. ${prep}"
                else ->
                    current
            }

        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "vela-guidance-${key.hashCode()}"
        )
    }

    fun resetDeduplication() {
        lastSpokenKey = null
        pendingSnapshot = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
        lastSpokenKey = null
        pendingSnapshot = null
    }

    internal companion object {
        enum class VoiceStage {
            FAR,
            PREPARE,
            NEAR,
            NOW
        }

        fun voiceStage(distanceMeters: Double?): VoiceStage {
            val distance = distanceMeters ?: return VoiceStage.FAR
            return when {
                distance <= 80.0 -> VoiceStage.NOW
                distance <= 250.0 -> VoiceStage.NEAR
                distance <= 700.0 -> VoiceStage.PREPARE
                else -> VoiceStage.FAR
            }
        }
    }
}
