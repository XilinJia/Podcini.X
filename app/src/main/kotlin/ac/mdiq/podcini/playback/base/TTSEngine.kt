package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.utils.MediaMetadataRetrieverCompat
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.generateFileName
import ac.mdiq.podcini.storage.utils.mediaDir
import ac.mdiq.podcini.storage.utils.mergeAudios
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.ui.compose.CommonMessageAttrib
import ac.mdiq.podcini.ui.compose.commonMessage
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import android.media.MediaMetadataRetriever
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object TTSEngine {
    const val TAG = "TTSEngine"

    val ttsTmpFiles = mutableListOf<String>()
    var ttsJob: Job? = null

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

    fun doTTSNow(item_: Episode, textSourceIndex: Int, speaking: MutableState<Boolean>) {
        var item = item_
        runOnIOScope {
            var readerText: String? = null
            when (textSourceIndex) {
                1 -> readerText = HtmlCompat.fromHtml(item.description ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                2 -> {
                    if (item.transcript == null) {
                        val url = item.link!!
                        val htmlSource = NetworkUtils.fetchHtmlSource(url)
                        val article = Readability4J(item.link!!, htmlSource).parse()
                        readerText = article.textContent ?: ""
                        item = upsert(item) { it.setTranscriptIfLonger(article.contentWithDocumentsCharsetOrUtf8) }
                        Logd(TAG, "readability4J: ${readerText.substring(max(0, readerText.length - 100), readerText.length)}")
                    } else readerText = HtmlCompat.fromHtml(item.transcript!!, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                }
            }
            Logd(TAG, "readerText: $readerText")
            commonMessage = CommonMessageAttrib(
                title = "",
                message = readerText!!,
                OKRes = R.string.stop,
                onOK = {
                    tts?.stop()
                    commonMessage = null
                }
            )
            while (!ttsReady) delay(200)
            if (tts?.isSpeaking == true) tts?.stop()
            speaking.value = true
            if (!item.feed?.langSet.isNullOrEmpty()) {
                val lang = item.feed!!.langSet.first()
                val result = tts?.setLanguage(Locale(lang))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) Loge(TAG, getAppContext().getString(R.string.language_not_supported_by_tts) + lang)
            }
            var startIndex = 0
            tts?.setSpeechRate(item.feed?.playSpeed ?: 1.0f)
            val chunkLength = TextToSpeech.getMaxSpeechInputLength() / 2
            while (startIndex < readerText.length) {
                val endIndex = minOf(startIndex + chunkLength, readerText.length)
                Logd(TAG, "startIndex: $startIndex endIndex: $endIndex")
                val chunk = readerText.substring(startIndex, endIndex)
                Logd(TAG, "chunk: $chunk")
                tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                startIndex += chunkLength
            }
            while (tts?.isSpeaking == true) delay(1000)
            speaking.value = false
        }
    }

    fun doTTS(item_: Episode, textSourceIndex: Int, processing: MutableIntState, update: (Episode)->Unit) {
        var item = item_
        processing.intValue = 1
        item = upsertBlk(item) { it.setPlayState(EpisodeState.BUILDING) }
        ttsTmpFiles.clear()
        ensureTTS()
        ttsJob = runOnIOScope {
            var readerText: String? = null
            processing.intValue = 1
            when (textSourceIndex) {
                1 -> readerText = HtmlCompat.fromHtml(item.description ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                2 -> {
                    if (item.transcript == null) {
                        val url = item.link!!
                        val htmlSource = NetworkUtils.fetchHtmlSource(url)
                        val article = Readability4J(item.link!!, htmlSource).parse()
                        readerText = article.textContent
                        item = upsertBlk(item) { it.setTranscriptIfLonger(article.contentWithDocumentsCharsetOrUtf8) }
                        Logd(TAG, "readability4J: ${readerText?.substring(max(0, readerText.length - 100), readerText.length)}")
                    } else readerText = HtmlCompat.fromHtml(item.transcript!!, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                }
            }

            Logd(TAG, "readerText: [$readerText]")
            if (!readerText.isNullOrBlank()) {
                processing.intValue = 5
                while (!ttsReady) { delay(100) }
                withContext(Dispatchers.Main) { processing.intValue = 15 }
                while (ttsWorking) { delay(100) }
                ttsWorking = true
                if (!item.feed?.langSet.isNullOrEmpty()) {
                    val lang = item.feed!!.langSet.first()
                    val result = tts?.setLanguage(Locale(lang))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) Loge(TAG, getAppContext().getString(R.string.language_not_supported_by_tts) + " $lang $result")
                }

                var engineIndex = 0
                val mediaFile = mediaDir / generateFileName(item.feed?.title ?: "") / item.getMediafilename()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { engineIndex++ }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) {}    // deprecated but have to override
                    override fun onError(utteranceId: String, errorCode: Int) {}
                })

                Logd(TAG, "readerText length: ${readerText.length}")
                var startIndex = 0
                var i = 0
                val chunkLength = TextToSpeech.getMaxSpeechInputLength() / 5   // TTS engine can't handle longer text
                var status = TextToSpeech.ERROR
                while (startIndex < readerText.length) {
                    Logd(TAG, "working on chunk $i $startIndex")
                    val endIndex = minOf(startIndex + chunkLength, readerText.length)
                    val chunk = readerText.substring(startIndex, endIndex)
                    Logd(TAG, "chunk: $chunk")
                    try {
                        val tempFile = File.createTempFile("tts_temp_${i}_", ".wav")
                        ttsTmpFiles.add(tempFile.absolutePath)
                        status = tts?.synthesizeToFile(chunk, null, tempFile, tempFile.absolutePath) ?: 0
                        Logd(TAG, "status: $status chunk: ${chunk.take(min(80, chunk.length))}")
                        if (status == TextToSpeech.ERROR) {
                            Loge(TAG, "Error generating audio file ${tempFile.absolutePath}")
                            break
                        }
                    } catch (e: Exception) { Logs(TAG, e, "writing temp file error")}
                    startIndex += chunkLength
                    i++
                    while (i - engineIndex > 0) delay(100)
                    withContext(Dispatchers.Main) { processing.intValue = 15 + 70 * startIndex / readerText.length }
                }
                Logd(TAG, "chunks finished, status: $status")
                withContext(Dispatchers.Main) { processing.intValue = 85 }
                if (status == TextToSpeech.SUCCESS) {
                    Logd(TAG, "TTS success, merging files to: ${mediaFile.absPath}")
                    mergeAudios(ttsTmpFiles.toTypedArray(), mediaFile.absPath, null)
                    var durationMs = 0
                    val retriever = MediaMetadataRetrieverCompat()
                    try {
                        retriever.setDataSource(mediaFile.absPath)
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        if (!durationStr.isNullOrBlank()) durationMs = durationStr.toInt()
                    } catch (e: Exception) {
                        Logs(TAG, e, "Get duration failed.")
                        if (durationMs !in 30000..18000000) durationMs = 30000
                    } finally { retriever.release() }

                    val mFilename = mediaFile.absPath
                    Logd(TAG, "saving TTS to file $mFilename")
                    val item_ = realm.query(Episode::class).query("id = ${item.id}").first().find()
                    if (item_ != null) {
                        item = upsertBlk(item_) {
                            it.size = 0
                            it.mimeType = "audio/*"
                            it.fileUrl = mediaFile.absPath
                            it.duration = durationMs
                            it.downloaded = true
                            it.setTranscriptIfLonger(readerText)
                        }
                    }
                }
                for (p in ttsTmpFiles) p.toUF().delete()
                tts?.setOnUtteranceProgressListener(null)
                ttsWorking = false
                item = upsertBlk(item) { it.setPlayState(EpisodeState.UNPLAYED) }
                withContext(Dispatchers.Main) { processing.intValue = -1 }
            } else {
                Loge(TAG, getAppContext().getString(R.string.episode_has_no_content))
                withContext(Dispatchers.Main) {
                    processing.intValue = -1
                    update(item)
                }
            }
        }
    }

}