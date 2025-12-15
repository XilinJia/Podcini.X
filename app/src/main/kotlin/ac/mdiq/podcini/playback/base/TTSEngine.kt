package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object TTSEngine {
    const val TAG = "TTSEngine"

    var tts: TextToSpeech? = null
    var ttsReady = false
    var ttsWorking = false

    fun ensureTTS() {
        val context = getAppContext()
        if (!ttsReady && tts == null) CoroutineScope(Dispatchers.Default).launch {
            Logd(TAG, "starting TTS")
            tts = TextToSpeech(context) { status: Int ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    Logt(TAG, "TTS init success")
                } else Loge(TAG, context.getString(R.string.tts_init_failed))
            }
        }
    }

    fun closeTTS() {
        if (ttsWorking) CoroutineScope(Dispatchers.Default).launch {
            while (ttsWorking) delay(10000)
            tts?.stop()
            tts?.shutdown()
            ttsWorking = false
            ttsReady = false
            tts = null
        }
    }
}