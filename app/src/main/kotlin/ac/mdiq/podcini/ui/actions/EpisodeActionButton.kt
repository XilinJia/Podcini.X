package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowEpisodeDownload
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.clearCurTempSpeed
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.playPause
import ac.mdiq.podcini.playback.base.TaskManager.Companion.taskManager
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.prefStreamOverDownload
import ac.mdiq.podcini.preferences.AppPreferences.videoPlayMode
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodesWarnLocalRepeat
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.AudioMediaTools
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.screens.TTSObj
import ac.mdiq.podcini.ui.screens.TTSObj.ensureTTS
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.text.HtmlCompat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class EpisodeActionButton(@JvmField var item: Episode, typeInit: ButtonTypes = ButtonTypes.NULL) {
    val TAG = this::class.simpleName ?: "ItemActionButton"

    private var _type = mutableStateOf(typeInit)
    var type: ButtonTypes
        get() = _type.value
        set(value) {
            _type.value = value
            label = value.label
            drawable = value.drawable
        }

    var processing by mutableIntStateOf(-1)

    var label by mutableIntStateOf(typeInit.label)
    var drawable by mutableIntStateOf(typeInit.drawable)

    init {
        update(item)
    }

    @UnstableApi
    fun onClick(context: Context) {
        when (type) {
            ButtonTypes.WEBSITE -> {
                if (!item.link.isNullOrEmpty()) IntentUtils.openInBrowser(context, item.link!!)
            }
            ButtonTypes.CANCEL -> {
                DownloadServiceInterface.impl?.cancel(context, item)
                if (AppPreferences.isAutodownloadEnabled) {
                    val item_ = upsertBlk(item) { it.disableAutoDownload() }
                    EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item_))
                }
                type = ButtonTypes.DOWNLOAD
            }
            ButtonTypes.PLAY -> {
                if (!item.fileExists()) {
                    Loge(TAG, context.getString(R.string.error_file_not_found) + ": ${item.title}")
                    val episode_ = upsertBlk(item) { it.setfileUrlOrNull(null) }
                    EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode_))
                    update(episode_)
                    return
                }
                PlaybackStarter(context, item).start()
                playVideoIfNeeded(context, item)
                type = ButtonTypes.PAUSE
            }
            ButtonTypes.STREAM -> {
                fun stream() {
                    PlaybackStarter(context, item).shouldStreamThisTime(true).start()
                    playVideoIfNeeded(context, item)
                }
                //        Logd("StreamActionButton", "item.feed: ${item.feedId}")
                UsageStatistics.logAction(UsageStatistics.ACTION_STREAM)
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
                }
                stream()
                type = ButtonTypes.PAUSE
            }
            ButtonTypes.DELETE -> {
                runOnIOScope { deleteEpisodesWarnLocalRepeat(context, listOf(item)) }
                update(item)
            }
            ButtonTypes.PAUSE -> {
                if (isCurrentlyPlaying(item)) {
                    playPause()
                    update(item)
                }
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
                UsageStatistics.logAction(UsageStatistics.ACTION_DOWNLOAD)
                if (mobileAllowEpisodeDownload || !isNetworkRestricted) {
                    DownloadServiceInterface.impl?.downloadNow(context, item, false)
                    Logd(TAG, "downloading ${item.title}")
                    type = ButtonTypes.CANCEL
                    return
                }
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.confirm_mobile_download_dialog_title),
                    message = context.getString(if (isNetworkRestricted && NetworkUtils.isVpnOverWifi) R.string.confirm_mobile_download_dialog_message_vpn else R.string.confirm_mobile_download_dialog_message),
                    confirmRes = R.string.confirm_mobile_download_dialog_download_later,
                    cancelRes = R.string.cancel_label,
                    neutralRes = R.string.confirm_mobile_download_dialog_allow_this_time,
                    onConfirm = {
                        DownloadServiceInterface.impl?.downloadNow(context, item, false)
                        Logd(TAG, "downloading ${item.title}")
                    },
                    onNeutral = {
                        DownloadServiceInterface.impl?.downloadNow(context, item, true)
                        Logd(TAG, "downloading ${item.title}")
                    })
            }
            ButtonTypes.TTS -> {
                Logd("TTSActionButton", "onClick called")
                if (item.link.isNullOrEmpty()) {
                    Loge(TAG, context.getString(R.string.episode_has_no_content))
                    type = ButtonTypes.NULL
                    return
                }
                processing = 1
                item = upsertBlk(item) { it.setPlayState(EpisodeState.BUILDING) }
                EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                ensureTTS(context)
                var readerText: String?
                runOnIOScope {
                    if (item.transcript == null) {
                        val url = item.link!!
                        val htmlSource = NetworkUtils.fetchHtmlSource(url)
                        val article = Readability4J(item.link!!, htmlSource).parse()
                        readerText = article.textContent
                        item = upsertBlk(item) { it.setTranscriptIfLonger(article.contentWithDocumentsCharsetOrUtf8) }
                        Logd(TAG, "readability4J: ${readerText?.substring(max(0, readerText!!.length - 100), readerText!!.length)}")
                    } else readerText = HtmlCompat.fromHtml(item.transcript!!, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                    Logd(TAG, "readerText: [$readerText]")
                    processing = 1
                    EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                    if (!readerText.isNullOrEmpty()) {
                        while (!TTSObj.ttsReady) runBlocking { delay(100) }
                        processing = 15
                        EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                        while (TTSObj.ttsWorking) runBlocking { delay(100) }
                        TTSObj.ttsWorking = true
                        if (item.feed?.language != null) {
                            val result = TTSObj.tts?.setLanguage(Locale(item.feed!!.language!!))
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                //                        Loge(TAG, "TTS language not supported ${item.feed!!.language} $result")
                                withContext(Dispatchers.Main) {
                                    Loge(TAG, context.getString(R.string.language_not_supported_by_tts) + " ${item.feed!!.language} $result")
                                }
                            }
                        }

                        var j = 0
                        val mediaFile = File(item.getMediafilePath(), item.getMediafilename())
                        TTSObj.tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                Logd(TAG, "onStart $utteranceId")
                            }
                            override fun onDone(utteranceId: String?) {
                                j++
                                Logd(TAG, "onDone ${mediaFile.length()} $utteranceId")
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String) {
                                Loge(TAG, "onError utterance error: $utteranceId $readerText")
                            }
                            override fun onError(utteranceId: String, errorCode: Int) {
                                Loge(TAG, "onError1 utterance error: $utteranceId $errorCode $readerText")
                            }
                        })

                        Logd(TAG, "readerText: ${readerText?.length}")
                        var startIndex = 0
                        var i = 0
                        val parts = mutableListOf<String>()
                        val chunkLength = TextToSpeech.getMaxSpeechInputLength() - 300  // TTS engine can't handle longer text
                        var status = TextToSpeech.ERROR
                        while (startIndex < readerText!!.length) {
                            Logd(TAG, "working on chunk $i $startIndex")
                            val endIndex = minOf(startIndex + chunkLength, readerText!!.length)
                            val chunk = readerText!!.substring(startIndex, endIndex)
                            Logd(TAG, "chunk: $chunk")
                            try {
                                val tempFile = File.createTempFile("tts_temp_${i}_", ".wav")
                                parts.add(tempFile.absolutePath)
                                status = TTSObj.tts?.synthesizeToFile(chunk, null, tempFile, tempFile.absolutePath) ?: 0
                                Logd(TAG, "status: $status chunk: ${chunk.substring(0, min(80, chunk.length))}")
                                if (status == TextToSpeech.ERROR) {
                                    Loge(TAG, "Error generating audio file ${tempFile.absolutePath}")
                                    break
                                }
                            } catch (e: Exception) { Logs(TAG, e, "writing temp file error")}
                            startIndex += chunkLength
                            i++
                            while (i - j > 0) runBlocking { delay(100) }
                            processing = 15 + 70 * startIndex / readerText!!.length
                            EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                        }
                        processing = 85
                        EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                        if (status == TextToSpeech.SUCCESS) {
                            AudioMediaTools.mergeAudios(parts.toTypedArray(), mediaFile.absolutePath, null)
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(mediaFile.absolutePath)
                            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0

                            retriever.release()
                            val mFilename = mediaFile.absolutePath
                            Logd(TAG, "saving TTS to file $mFilename")
                            val item_ = realm.query(Episode::class).query("id = ${item.id}").first().find()
                            if (item_ != null) {
                                item = upsertBlk(item_) {
                                    it.fillMedia(null, 0, "audio/*")
                                    it.fileUrl = Uri.fromFile(mediaFile).toString()
                                    it.duration = durationMs
                                    it.setIsDownloaded()
                                    it.setTranscriptIfLonger(readerText)
                                }
                            }
                        }
                        for (p in parts) {
                            val f = File(p)
                            f.delete()
                        }
                        TTSObj.ttsWorking = false
                    } else Loge(TAG, context.getString(R.string.episode_has_no_content))

                    item = upsertBlk(item) { it.setPlayState(EpisodeState.UNPLAYED) }

                    processing = 100
                    EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                }
                type = ButtonTypes.PLAY
            }
            ButtonTypes.PLAYLOCAL -> {
                if (PlaybackService.playbackService?.isServiceReady() == true && InTheatre.isCurMedia(item)) {
                    mPlayer?.play()
                    taskManager?.restartSleepTimer()
                } else {
                    clearCurTempSpeed()
                    PlaybackStarter(context, item).start()
                    if (item.playState < EpisodeState.PROGRESS.code || item.playState == EpisodeState.SKIPPED.code || item.playState == EpisodeState.AGAIN.code) item = runBlocking { setPlayStateSync(EpisodeState.PROGRESS.code, item, false) }
                }
                if (item.getMediaType() == MediaType.VIDEO) context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
                type = ButtonTypes.PAUSE
            }
            else -> {}
        }
    }

    fun update(item_: Episode) {
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
            ButtonTypes.PLAY, ButtonTypes.PLAYLOCAL -> {
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
    }

    @UnstableApi
    @Composable
    fun AltActionsDialog(context: Context, onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        onClick(context)
                        onDismiss()
                    }) { Icon(imageVector = ImageVector.vectorResource(drawable), contentDescription = type.name) }
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
    DELETE(R.string.delete_label, R.drawable.ic_delete),
    NULL(R.string.null_label, R.drawable.ic_questionmark),
//    NULLZAP(R.string.null_zap_label, R.drawable.ic_close_white),
    PAUSE(R.string.pause_label, R.drawable.ic_pause),
    DOWNLOAD(R.string.download_label, R.drawable.ic_download),
    TTS(R.string.TTS_label, R.drawable.text_to_speech),
    PLAYLOCAL(R.string.play_label, R.drawable.ic_play_24dp)
}
