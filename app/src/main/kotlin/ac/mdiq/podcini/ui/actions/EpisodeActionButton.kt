package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowEpisodeDownload
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.clearCurTempSpeed
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.playPause
import ac.mdiq.podcini.playback.base.SleepManager.Companion.sleepManager
import ac.mdiq.podcini.playback.base.TTSEngine
import ac.mdiq.podcini.playback.base.TTSEngine.ensureTTS
import ac.mdiq.podcini.playback.base.TTSEngine.tts
import ac.mdiq.podcini.playback.base.TTSEngine.ttsReady
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.prefStreamOverDownload
import ac.mdiq.podcini.preferences.AppPreferences.videoPlayMode
import ac.mdiq.podcini.storage.database.deleteEpisodesWarnLocalRepeat
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.setPlayState
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.utils.mergeAudios
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonMessageAttrib
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.compose.commonMessage
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.openInBrowser
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class EpisodeActionButton( var item: Episode, typeInit: ButtonTypes = ButtonTypes.NULL) {
    private val TAG = this::class.simpleName ?: "ItemActionButton"

    private var _type = mutableStateOf(typeInit)
    var type: ButtonTypes
        get() = _type.value
        set(value) {
//            Logd(TAG, "set ButtonTypes to $value")
            _type.value = value
            label = value.label
            drawable = value.drawable
        }

    var typeToCancel: ButtonTypes = ButtonTypes.DOWNLOAD

    var processing by mutableIntStateOf(-1)

    var speaking by mutableStateOf(false)

    var label by mutableIntStateOf(typeInit.label)
    var drawable by mutableIntStateOf(typeInit.drawable)

    init {
        if (type == ButtonTypes.NULL) update(item)
    }

    val ttsTmpFiles = mutableListOf<String>()
    var ttsJob: Job? = null

    @UnstableApi
    fun onClick(context: Context) {
        fun fileNotExist(): Boolean {
            if (!item.fileExists()) {
                Loge(TAG, context.getString(R.string.error_file_not_found) + ": ${item.title}")
                val episode_ = upsertBlk(item) { it.setfileUrlOrNull(null) }
                EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode_))
                update(episode_)
                return true
            }
            return false
        }
        Logd(TAG, "onClick type: $type")
        when (type) {
            ButtonTypes.WEBSITE -> if (!item.link.isNullOrEmpty()) openInBrowser(context, item.link!!)
            ButtonTypes.CANCEL -> {
                if (typeToCancel == ButtonTypes.DOWNLOAD) {
                    DownloadServiceInterface.impl?.cancel(context, item)
                    if (AppPreferences.isAutodownloadEnabled) upsertBlk(item) { it.disableAutoDownload() }
                    type = ButtonTypes.DOWNLOAD
                } else if (typeToCancel == ButtonTypes.TTS) {
                    runOnIOScope {
                        for (p in ttsTmpFiles) {
                            val f = File(p)
                            f.delete()
                        }
                    }
                    TTSEngine.ttsWorking = false
                    processing = -1
                    ttsJob?.cancel()
                    ttsJob = null
                    type = ButtonTypes.TTS
                }
            }
            ButtonTypes.PLAY -> {
                if (fileNotExist()) return
                PlaybackStarter(context, item).start()
                playVideoIfNeeded(context, item)
//                type = ButtonTypes.PAUSE  leave it to playerStat
            }
            ButtonTypes.PLAYONCE -> {
                if (fileNotExist()) return
                PlaybackStarter(context, item).start()
                playVideoIfNeeded(context, item)
                actQueue = PlayQueue()
                //                type = ButtonTypes.PAUSE  leave it to playerStat
            }
            ButtonTypes.PLAYREPEAT -> {
                if (fileNotExist()) return
                PlaybackStarter(context, item).setToRepeat(true).start()
                playVideoIfNeeded(context, item)
                actQueue = PlayQueue()
                //                type = ButtonTypes.PAUSE  leave it to playerStat
            }
            ButtonTypes.STREAM -> {
                fun stream() {
                    PlaybackStarter(context, item).shouldStreamThisTime(true).start()
                    playVideoIfNeeded(context, item)
                }
                //        Logd("StreamActionButton", "item.feed: ${item.feedId}")
                if (!NetworkUtils.isStreamingAllowed) {
                    commonConfirm = CommonConfirmAttrib(
                        title = context.getString(R.string.stream_label),
                        message = context.getString(R.string.confirm_mobile_streaming_notification_message),
                        confirmRes = R.string.confirm_mobile_streaming_button_always,
                        cancelRes = R.string.cancel_label,
                        neutralRes = R.string.confirm_mobile_streaming_button_once,
                        onConfirm = {
                            NetworkUtils.mobileAllowStreaming = true
                            stream()
                        },
                        onNeutral = { stream() })
                    return
                } else stream()
//                type = ButtonTypes.PAUSE  leave it to playerStat
            }
            ButtonTypes.STREAMREPEAT -> {
                fun stream() {
                    PlaybackStarter(context, item).shouldStreamThisTime(true).setToRepeat(true).start()
                    playVideoIfNeeded(context, item)
                }
                //        Logd("StreamActionButton", "item.feed: ${item.feedId}")
                if (!NetworkUtils.isStreamingAllowed) {
                    commonConfirm = CommonConfirmAttrib(
                        title = context.getString(R.string.stream_label),
                        message = context.getString(R.string.confirm_mobile_streaming_notification_message),
                        confirmRes = R.string.confirm_mobile_streaming_button_always,
                        cancelRes = R.string.cancel_label,
                        neutralRes = R.string.confirm_mobile_streaming_button_once,
                        onConfirm = {
                            NetworkUtils.mobileAllowStreaming = true
                            stream()
                        },
                        onNeutral = { stream() })
                    return
                } else stream()
                //                type = ButtonTypes.PAUSE  leave it to playerStat
            }
            ButtonTypes.STREAMONCE -> {
                //        Logd("StreamActionButton", "item.feed: ${item.feedId}")
                PlaybackStarter(context, item).shouldStreamThisTime(true).start()
                playVideoIfNeeded(context, item)
                actQueue = PlayQueue()
                //                type = ButtonTypes.PAUSE  leave it to playerStat
            }
            ButtonTypes.DELETE -> {
                runOnIOScope { deleteEpisodesWarnLocalRepeat(listOf(item)) }
                update(item)
            }
            ButtonTypes.PAUSE -> {
                if (isCurrentlyPlaying(item)) {
                    playPause()
                    update(item)
                }
                if (tts?.isSpeaking == true) tts?.stop()
            }
            ButtonTypes.DOWNLOAD -> {
                fun shouldNotDownload(media: Episode?): Boolean {
                    if (media?.downloadUrl.isNullOrBlank()) {
                        Loge(TAG, "episode downloadUrl is null or blank: ${media?.title}")
                        return true
                    }
                    val isDownloading = DownloadServiceInterface.impl?.isDownloadingEpisode(media.downloadUrl!!) == true
                    return isDownloading || media.downloaded
                }
                if (shouldNotDownload(item)) return
                if (mobileAllowEpisodeDownload || !isNetworkRestricted) {
                    DownloadServiceInterface.impl?.downloadNow(context, item, false)
                    Logd(TAG, "downloading ${item.title}")
                    typeToCancel = ButtonTypes.DOWNLOAD
                    type = ButtonTypes.CANCEL
                    return
                }
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.confirm_mobile_download_dialog_title),
                    message = context.getString(if (isNetworkRestricted && NetworkUtils.isVpnOverWifi) R.string.confirm_mobile_download_dialog_message_vpn else R.string.confirm_mobile_download_dialog_message),
                    confirmRes = R.string.confirm_mobile_download_dialog_download_later,
                    cancelRes = R.string.cancel_label,
                    neutralRes = R.string.confirm_mobile_download_dialog_allow_this_time,
                    onConfirm = { DownloadServiceInterface.impl?.downloadNow(context, item, false) },
                    onNeutral = { DownloadServiceInterface.impl?.downloadNow(context, item, true) })
            }
            ButtonTypes.JUSTTTS -> {
                Logd("JUSTTTSButton", "onClick called")
                type = ButtonTypes.PAUSE
                ensureTTS()
                fun doTTS(textSourceIndex: Int) {
                    runOnIOScope {
                        var readerText: String? = null
                        when (textSourceIndex) { //                        1 -> readerText = HtmlCompat.fromHtml(ShownotesCleaner(context).processShownotes(item.description ?: "", item.duration), HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            1 -> readerText = HtmlCompat.fromHtml(item.description ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            2 -> {
                                if (item.transcript == null) {
                                    runBlocking {
                                        val url = item.link!!
                                        val htmlSource = NetworkUtils.fetchHtmlSource(url)
                                        val article = Readability4J(item.link!!, htmlSource).parse()
                                        readerText = article.textContent ?: ""
                                        item = upsertBlk(item) { it.setTranscriptIfLonger(article.contentWithDocumentsCharsetOrUtf8) }
                                        Logd(TAG, "readability4J: ${readerText.substring(max(0, readerText.length - 100), readerText.length)}")
                                    }
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
                        speaking = true
                        if (!item.feed?.langSet.isNullOrEmpty()) {
                            val lang = item.feed!!.langSet.first()
                            val result = tts?.setLanguage(Locale(lang))
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) Loge(TAG, context.getString(R.string.language_not_supported_by_tts) + lang)
                        }
                        var startIndex = 0
                        tts?.setSpeechRate(item.feed?.playSpeed ?: 1.0f)
                        val chunkLength = TextToSpeech.getMaxSpeechInputLength() / 2
                        while (startIndex < readerText!!.length) {
                            val endIndex = minOf(startIndex + chunkLength, readerText?.length ?: 0)
                            Logd(TAG, "startIndex: $startIndex endIndex: $endIndex")
                            val chunk = readerText?.substring(startIndex, endIndex) ?: ""
                            Logd(TAG, "chunk: $chunk")
                            tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                            startIndex += chunkLength
                        }
                        while (tts?.isSpeaking == true) delay(1000)
                        speaking = false
                    }
                }
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.choose_tts_source),
                    message = "",
                    confirmRes = R.string.description_label,
                    cancelRes = R.string.cancel_label,
                    neutralRes = R.string.transcript,
                    onConfirm = { doTTS(1) },
                    onNeutral = { doTTS(2) })
            }
            ButtonTypes.TTS -> {
                Logd("TTSActionButton", "onClick called")
//                if (item.link.isNullOrEmpty()) {
//                    Loge(TAG, context.getString(R.string.episode_has_no_content))
//                    type = ButtonTypes.NULL
//                    return
//                }
                fun doTTS(textSourceIndex: Int) {
                    processing = 1
                    item = upsertBlk(item) { it.setPlayState(EpisodeState.BUILDING) }
                    typeToCancel = ButtonTypes.TTS
                    type = ButtonTypes.CANCEL
                    ttsTmpFiles.clear()
                    ensureTTS()
                    ttsJob = runOnIOScope {
                        var readerText: String? = null
                        processing = 1
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
                            processing = 5
                            while (!ttsReady) { delay(100) }
                            withContext(Dispatchers.Main) { processing = 15 }
                            while (TTSEngine.ttsWorking) { delay(100) }
                            TTSEngine.ttsWorking = true
                            if (!item.feed?.langSet.isNullOrEmpty()) {
                                val lang = item.feed!!.langSet.first()
                                val result = tts?.setLanguage(Locale(lang))
                                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) Loge(TAG, context.getString(R.string.language_not_supported_by_tts) + " $lang $result")
                            }

                            var engineIndex = 0
                            val mediaFile = File(item.getMediafilePath(), item.getMediafilename())
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
                                withContext(Dispatchers.Main) { processing = 15 + 70 * startIndex / readerText.length }
                            }
                            Logd(TAG, "chunks finished, status: $status")
                            withContext(Dispatchers.Main) { processing = 85 }
                            if (status == TextToSpeech.SUCCESS) {
                                Logd(TAG, "TTS success, merging files to: ${mediaFile.absolutePath}")
                                mergeAudios(ttsTmpFiles.toTypedArray(), mediaFile.absolutePath, null)
                                var durationMs = 0
                                val retriever = Episode.MediaMetadataRetrieverCompat()
                                try {
                                    retriever.setDataSource(mediaFile.absolutePath)
                                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    if (!durationStr.isNullOrBlank()) durationMs = durationStr.toInt()
                                } catch (e: Exception) {
                                    Logs(TAG, e, "Get duration failed.")
                                    if (durationMs !in 30000..18000000) durationMs = 30000
                                } finally { retriever.release() }

                                val mFilename = mediaFile.absolutePath
                                Logd(TAG, "saving TTS to file $mFilename")
                                val item_ = realm.query(Episode::class).query("id = ${item.id}").first().find()
                                if (item_ != null) {
                                    item = upsertBlk(item_) {
                                        it.size = 0
                                        it.mimeType = "audio/*"
                                        it.fileUrl = Uri.fromFile(mediaFile).toString()
                                        it.duration = durationMs
                                        it.setIsDownloaded()
                                        it.setTranscriptIfLonger(readerText)
                                    }
                                }
                            }
                            for (p in ttsTmpFiles) {
                                val f = File(p)
                                f.delete()
                            }
                            tts?.setOnUtteranceProgressListener(null)
                            TTSEngine.ttsWorking = false
                            item = upsertBlk(item) { it.setPlayState(EpisodeState.UNPLAYED) }
                            withContext(Dispatchers.Main) { processing = -1 }
                        } else {
                            Loge(TAG, context.getString(R.string.episode_has_no_content))
                            withContext(Dispatchers.Main) {
                                processing = -1
                                update(item)
                            }
                        }
                    }
                }
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.choose_tts_source),
                    message = "",
                    confirmRes = R.string.description_label,
                    cancelRes = R.string.cancel_label,
                    neutralRes = R.string.transcript,
                    onConfirm = { doTTS(1) },
                    onNeutral = { doTTS(2) })
            }
            ButtonTypes.PLAYLOCAL -> {
                if (PlaybackService.playbackService?.isServiceReady() == true && InTheatre.isCurMedia(item)) {
                    mPlayer?.play()
                    sleepManager?.restartSleepTimer()
                } else {
                    clearCurTempSpeed()
                    PlaybackStarter(context, item).start()
                    if (item.playState < EpisodeState.PROGRESS.code || item.playState == EpisodeState.SKIPPED.code || item.playState == EpisodeState.AGAIN.code) item = runBlocking { setPlayState(EpisodeState.PROGRESS, item, false) }
                }
                if (item.getMediaType() == MediaType.VIDEO) context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
//                type = ButtonTypes.PAUSE  leave it to playerStat
            }
            else -> {}
        }
    }

    fun update(item_: Episode) {
//        Logd(TAG, "update type: $type ${item.title}")
        item = item_
        fun undownloadedType(): ButtonTypes {
            fun isDownloadingMedia(): Boolean {
                return DownloadServiceInterface.impl?.isDownloadingEpisode(item.downloadUrl!!) == true
            }
            return when {
                item.downloadUrl.isNullOrBlank() -> ButtonTypes.TTS
                item.feed == null || item.feedId == null || item.feed?.type == Feed.FeedType.YOUTUBE.name || (prefStreamOverDownload && item.feed?.prefStreamOverDownload == true) -> ButtonTypes.STREAM
                isDownloadingMedia() -> ButtonTypes.CANCEL
                else -> ButtonTypes.DOWNLOAD
            }
        }
        when (type) {
            ButtonTypes.WEBSITE -> {}
            ButtonTypes.PLAYLOCAL -> if (isCurrentlyPlaying(item)) type = ButtonTypes.PAUSE
            ButtonTypes.PLAY -> {
                if (!item.downloaded) type = undownloadedType()
                else if (isCurrentlyPlaying(item)) type = ButtonTypes.PAUSE
            }
            ButtonTypes.STREAM -> if (isCurrentlyPlaying(item)) type = ButtonTypes.PAUSE
            ButtonTypes.PAUSE -> {
                type = when {
                    item.feed?.isLocalFeed == true -> ButtonTypes.PLAYLOCAL
                    item.downloaded -> ButtonTypes.PLAY
                    item.feed == null || item.feedId == null || item.feed?.type == Feed.FeedType.YOUTUBE.name || (prefStreamOverDownload && item.feed?.prefStreamOverDownload == true) -> ButtonTypes.STREAM
                    else -> ButtonTypes.DOWNLOAD
                }
            }
            else -> {
                // TODO: ensure TTS
                //        val media = item.media ?: return TTSActionButton(item)
                //        Logd("ItemActionButton", "forItem: local feed: ${item.feed?.isLocalFeed} downloaded: ${item.downloaded} playing: ${isCurrentlyPlaying(item)}  ${item.title} ")
                type = when {
                    isCurrentlyPlaying(item) -> ButtonTypes.PAUSE
                    item.feed?.isLocalFeed == true -> ButtonTypes.PLAYLOCAL
                    item.downloaded -> ButtonTypes.PLAY
                    else -> undownloadedType()
                }
            }
        }
//        Logd(TAG, "update type: $type")
    }

    @UnstableApi
    @Composable
    fun AltActionsDialog(context: Context, onDismiss: () -> Unit) {
        CommonPopupCard(onDismissRequest = onDismiss) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Logd(TAG, "button label: $type")
                if (type != ButtonTypes.TTS) {
                    IconButton(onClick = {
                        type = ButtonTypes.TTS
                        onClick(context)
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.text_to_speech), contentDescription = "TTS") }
                }
                if (type != ButtonTypes.JUSTTTS) {
                    IconButton(onClick = {
                        type = ButtonTypes.JUSTTTS
                        onClick(context)
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.text_to_speech_svgrepo_com), contentDescription = "JUSTTTS") }
                }
                if (type != ButtonTypes.WEBSITE) {
                    IconButton(onClick = {
                        val btn = EpisodeActionButton(item, ButtonTypes.WEBSITE)
                        btn.onClick(context)
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "Web") }
                }
                if (type !in listOf(ButtonTypes.PLAY, ButtonTypes.DOWNLOAD, ButtonTypes.DELETE)) {
                    IconButton(onClick = {
                        val btn = EpisodeActionButton(item, ButtonTypes.DOWNLOAD)
                        btn.onClick(context)
                        type = btn.type
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_download), contentDescription = "Download") }
                }
                if (type !in listOf(ButtonTypes.STREAM, ButtonTypes.DOWNLOAD, ButtonTypes.DELETE)) {
                    IconButton(onClick = {
                        val btn = EpisodeActionButton(item, ButtonTypes.DELETE)
                        btn.onClick(context)
                        type = btn.type
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_delete), contentDescription = "Delete") }
                }
                if (type !in listOf(ButtonTypes.PAUSE, ButtonTypes.STREAM, ButtonTypes.DOWNLOAD)) {
                    IconButton(onClick = {
                        val btn = EpisodeActionButton(item, ButtonTypes.PLAYREPEAT)
                        btn.onClick(context)
                        type = btn.type
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.outline_autoplay_24), contentDescription = "PlayRepeat") }
                }
                if (type !in listOf(ButtonTypes.PLAY, ButtonTypes.PAUSE, ButtonTypes.STREAM, ButtonTypes.DOWNLOAD)) {
                    IconButton(onClick = {
                        val btn = EpisodeActionButton(item, ButtonTypes.PLAY)
                        btn.onClick(context)
                        type = btn.type
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_play_24dp), contentDescription = "Play") }
                }
                if (type !in listOf(ButtonTypes.PAUSE, ButtonTypes.STREAM, ButtonTypes.DOWNLOAD)) {
                    IconButton(onClick = {
                        val btn = EpisodeActionButton(item, ButtonTypes.PLAYONCE)
                        btn.onClick(context)
                        type = btn.type
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.outline_play_pause_24), contentDescription = "PlayOnce") }
                }
                if (type !in listOf(ButtonTypes.PLAY, ButtonTypes.PAUSE, ButtonTypes.DELETE)) {
                    IconButton(onClick = {
                        val btn = EpisodeActionButton(item, ButtonTypes.STREAMREPEAT)
                        btn.onClick(context)
                        type = btn.type
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.outline_repeat_24), contentDescription = "StreamRepeat") }
                }
                if (type !in listOf(ButtonTypes.PLAY, ButtonTypes.PAUSE, ButtonTypes.STREAM, ButtonTypes.DELETE)) {
                    IconButton(onClick = {
                        val btn = EpisodeActionButton(item, ButtonTypes.STREAM)
                        btn.onClick(context)
                        type = btn.type
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_stream), contentDescription = "Stream") }
                }
                if (type !in listOf(ButtonTypes.PLAY, ButtonTypes.PAUSE, ButtonTypes.DELETE)) {
                    IconButton(onClick = {
                        val btn = EpisodeActionButton(item, ButtonTypes.STREAMONCE)
                        btn.onClick(context)
                        type = btn.type
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.play_stream_svgrepo_com), contentDescription = "StreamOnce") }
                }
            }
        }
    }
    
    companion object {
        @OptIn(UnstableApi::class)
        fun playVideoIfNeeded(context: Context, item: Episode) {
            if (item.forceVideo || (item.feed?.videoModePolicy != VideoMode.AUDIO_ONLY
                            && videoPlayMode != VideoMode.AUDIO_ONLY.code && videoMode != VideoMode.AUDIO_ONLY
                            && item.getMediaType() == MediaType.VIDEO))
                context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
        }
    }
}

enum class ButtonTypes(val label: Int, val drawable: Int) {
    WEBSITE(R.string.visit_website_label, R.drawable.ic_web),
    CANCEL(R.string.cancel_download_label, R.drawable.ic_cancel),
    PLAY(R.string.play_label, R.drawable.ic_play_24dp),
    STREAM(R.string.stream_label, R.drawable.ic_stream),
    PLAYONCE(R.string.play_once, R.drawable.outline_play_pause_24),
    STREAMONCE(R.string.stream_once, R.drawable.play_stream_svgrepo_com),

    PLAYREPEAT(R.string.play_repeat, R.drawable.outline_autoplay_24),
    STREAMREPEAT(R.string.stream_repeat, R.drawable.outline_repeat_24),

    DELETE(R.string.delete_label, R.drawable.ic_delete),
    NULL(R.string.null_label, R.drawable.ic_questionmark),
//    NULLZAP(R.string.null_zap_label, R.drawable.ic_close_white),
    PAUSE(R.string.pause_label, R.drawable.ic_pause),
    DOWNLOAD(R.string.download_label, R.drawable.ic_download),
    TTS(R.string.TTS_label, R.drawable.text_to_speech),
    JUSTTTS(R.string.just_TTS, R.drawable.text_to_speech_svgrepo_com),
    PLAYLOCAL(R.string.play_label, R.drawable.ic_play_24dp)
}
