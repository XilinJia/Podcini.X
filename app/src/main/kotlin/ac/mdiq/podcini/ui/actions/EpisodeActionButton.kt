package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowEpisodeDownload
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.prefStreamOverDownload
import ac.mdiq.podcini.preferences.AppPreferences.videoPlayMode
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodesWarnLocalRepeat
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.RealmDB
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.storage.utils.AudioMediaTools
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.screens.FEObj
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.text.HtmlCompat
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import kotlin.math.max
import kotlin.math.min

abstract class EpisodeActionButton internal constructor(@JvmField var item: Episode, label: Int, drawable: Int) {
    val TAG = this::class.simpleName ?: "ItemActionButton"

    open val visibility: Boolean
        get() = true

    var processing by mutableIntStateOf(-1)
    val actionState = mutableIntStateOf(0)

    val label by mutableIntStateOf(label)
    var drawable by mutableIntStateOf(drawable)

    abstract fun onClick(context: Context)

    open fun forItem(item_: Episode): EpisodeActionButton {
        item = item_
        // TODO: ensure TTS
//        val media = item.media ?: return TTSActionButton(item)
        val isDownloadingMedia = when (item.downloadUrl) {
            null -> false
            else -> DownloadServiceInterface.impl?.isDownloadingEpisode(item.downloadUrl!!) == true
        }
//        Logd("ItemActionButton", "forItem: local feed: ${item.feed?.isLocalFeed} downloaded: ${item.downloaded} playing: ${isCurrentlyPlaying(item)}  ${item.title} ")
        return when {
            isCurrentlyPlaying(item) -> PauseActionButton(item)
            item.feed?.isLocalFeed == true -> PlayLocalActionButton(item)
            item.downloaded -> PlayActionButton(item)
            isDownloadingMedia -> CancelDownloadActionButton(item)
            item.feed == null || item.feedId == null || item.feed?.type == Feed.FeedType.YOUTUBE.name
                    || (prefStreamOverDownload && item.feed?.prefStreamOverDownload == true) -> StreamActionButton(item)
            else -> DownloadActionButton(item)
        }
    }

    @Composable
    fun AltActionsDialog(context: Context, includeTTS: Boolean = false, onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Logd(TAG, "button label: $label")
                    if (label != R.string.play_label && label != R.string.pause_label && label != R.string.download_label) {
                        IconButton(onClick = {
                            PlayActionButton(item).onClick(context)
                            onDismiss()
                        }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_play_24dp), contentDescription = "Play") }
                    }
                    if (label != R.string.stream_label && label != R.string.play_label && label != R.string.pause_label && label != R.string.delete_label) {
                        IconButton(onClick = {
                            StreamActionButton(item).onClick(context)
                            onDismiss()
                        }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_stream), contentDescription = "Stream") }
                    }
                    if (label != R.string.download_label && label != R.string.play_label && label != R.string.delete_label) {
                        IconButton(onClick = {
                            DownloadActionButton(item).onClick(context)
                            onDismiss()
                        }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_download), contentDescription = "Download") }
                    }
                    if (label != R.string.delete_label && label != R.string.download_label && label != R.string.stream_label) {
                        IconButton(onClick = {
                            DeleteActionButton(item).onClick(context)
                            onDismiss()
                        }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_delete), contentDescription = "Delete") }
                    }
                    if (label != R.string.visit_website_label) {
                        IconButton(onClick = {
                            VisitWebsiteActionButton(item).onClick(context)
                            onDismiss()
                        }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "Web") }
                    }
                    if (includeTTS && label != R.string.TTS_label) {
                        IconButton(onClick = {
                            TTSActionButton(item).onClick(context)
                            onDismiss()
                        }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.text_to_speech), contentDescription = "TTS") }
                    }
                }
            }
        }
    }
    
    companion object {
        fun playVideoIfNeeded(context: Context, item: Episode) {
            if (item.forceVideo == true || (item.feed?.videoModePolicy != VideoMode.AUDIO_ONLY
                    && videoPlayMode != VideoMode.AUDIO_ONLY.code && videoMode != VideoMode.AUDIO_ONLY
                    && item.getMediaType() == MediaType.VIDEO))
                context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
        }
    }
}

class VisitWebsiteActionButton(item: Episode) : EpisodeActionButton(item, R.string.visit_website_label, R.drawable.ic_web) {
    override val visibility: Boolean
        get() = !item.link.isNullOrEmpty()

    override fun onClick(context: Context) {
        if (!item.link.isNullOrEmpty()) IntentUtils.openInBrowser(context, item.link!!)
        actionState.value = label
    }

    override fun forItem(item_: Episode): EpisodeActionButton {
        item = item_
        return this
    }
}

class CancelDownloadActionButton(item: Episode) : EpisodeActionButton(item, R.string.cancel_download_label, R.drawable.ic_cancel) {
    override fun onClick(context: Context) {
        DownloadServiceInterface.impl?.cancel(context, item)
        if (AppPreferences.isAutodownloadEnabled) {
            val item_ = upsertBlk(item) { it.disableAutoDownload() }
            EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item_))
        }
        actionState.value = label
    }
}

class PlayActionButton(item: Episode) : EpisodeActionButton(item, R.string.play_label, R.drawable.ic_play_24dp) {
    override fun onClick(context: Context) {
        Logd("PlayActionButton", "onClick called file: ${item.fileUrl}")
        if (!item.fileExists()) {
            Loge(TAG, context.getString(R.string.error_file_not_found))
            notifyMissingEpisodeMediaFile(context, item)
            return
        }
        if (PlaybackService.playbackService?.isServiceReady() == true && InTheatre.isCurMedia(item)) {
            PlaybackService.playbackService?.mPlayer?.resume()
            PlaybackService.playbackService?.taskManager?.restartSleepTimer()
        } else {
            PlaybackService.clearCurTempSpeed()
            PlaybackServiceStarter(context, item).callEvenIfRunning(true).start()
            if (item.playState < PlayState.PROGRESS.code) item = runBlocking { setPlayStateSync(PlayState.PROGRESS.code, item, false) }
            EventFlow.postEvent(FlowEvent.PlayEvent(item))
        }
        playVideoIfNeeded(context, item)
        actionState.value = label
    }

    /**
     * Notifies the database about a missing EpisodeMedia file. This method will correct the EpisodeMedia object's
     * values in the DB and send a FeedItemEvent.
     */
    fun notifyMissingEpisodeMediaFile(context: Context, media: Episode) {
        Logd(TAG, "notifyMissingEpisodeMediaFile called")
        Logt(TAG, "The feedmanager was notified about a missing episode. It will update its database now.")
        val episode = realm.query(Episode::class).query("id == ${media.id}").first().find()
        if (episode != null) {
            val episode_ = upsertBlk(episode) {
                it.downloaded = false
                it.fileUrl = null
            }
            EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode_))
        }
        EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.error_file_not_found)))
    }
}

class PlayPauseActionButton(item: Episode) : EpisodeActionButton(item, R.string.play_label, R.drawable.ic_play_24dp) {
    private var isPlaying: Boolean = false

    override fun onClick(context: Context) {
        if (!isPlaying) {
            Logd("PlayActionButton", "onClick called file: ${item.fileUrl}")
            if (!item.fileExists()) {
                Loge(TAG, context.getString(R.string.error_file_not_found))
                notifyMissingEpisodeMediaFile(context, item)
                return
            }
            if (PlaybackService.playbackService?.isServiceReady() == true && InTheatre.isCurMedia(item)) {
                PlaybackService.playbackService?.mPlayer?.resume()
                PlaybackService.playbackService?.taskManager?.restartSleepTimer()
            } else {
                PlaybackService.clearCurTempSpeed()
                PlaybackServiceStarter(context, item).callEvenIfRunning(true).start()
                if (item.playState < PlayState.PROGRESS.code) item = runBlocking { setPlayStateSync(PlayState.PROGRESS.code, item, false) }
                EventFlow.postEvent(FlowEvent.PlayEvent(item))
            }
            playVideoIfNeeded(context, item)
        } else {
            if (isCurrentlyPlaying(item)) context.sendBroadcast(MediaButtonReceiver.createIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
        actionState.value = label
        isPlaying = !isPlaying
        drawable = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_24dp
    }

    /**
     * Notifies the database about a missing EpisodeMedia file. This method will correct the EpisodeMedia object's
     * values in the DB and send a FeedItemEvent.
     */
    fun notifyMissingEpisodeMediaFile(context: Context, media: Episode) {
        Logd(TAG, "notifyMissingEpisodeMediaFile called")
        Logt(TAG, "The feedmanager was notified about a missing episode. It will update its database now.")
        val episode = realm.query(Episode::class).query("id == ${media.id}").first().find()
//        val episode = media.episodeOrFetch()
        if (episode != null) {
            val episode_ = upsertBlk(episode) {
                it.downloaded = false
                it.fileUrl = null
            }
            EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode_))
        }
        EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.error_file_not_found)))
    }
}

class StreamActionButton(item: Episode) : EpisodeActionButton(item, R.string.stream_label, R.drawable.ic_stream) {
    override fun onClick(context: Context) {
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
                    stream(context, item)
                },
                onNeutral = { stream(context, item) })
            return
        }
        stream(context, item)
        actionState.value = label
    }

    companion object {
        fun stream(context: Context, media: Episode) {
            if (!InTheatre.isCurMedia(media)) PlaybackService.clearCurTempSpeed()
            PlaybackServiceStarter(context, media).shouldStreamThisTime(true).callEvenIfRunning(true).start()
            val item = runBlocking { setPlayStateSync(PlayState.PROGRESS.code, media, false) }
            EventFlow.postEvent(FlowEvent.PlayEvent(item))
            playVideoIfNeeded(context, media)
        }
    }
}

class StreamPauseActionButton(item: Episode) : EpisodeActionButton(item, R.string.stream_label, R.drawable.ic_stream) {
    private var isPlaying: Boolean = false

    override fun onClick(context: Context) {
//        Logd("StreamActionButton", "item.feed: ${item.feedId}")
        if (!isPlaying) {
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
                        StreamActionButton.Companion.stream(context, item)
                    },
                    onNeutral = { StreamActionButton.Companion.stream(context, item) })
                return
            }
            stream(context, item)
        } else {
            if (isCurrentlyPlaying(item)) context.sendBroadcast(MediaButtonReceiver.createIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
        actionState.value = label
        isPlaying = !isPlaying
        drawable = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_stream
    }

    companion object {
        fun stream(context: Context, media: Episode) {
            if (!InTheatre.isCurMedia(media)) PlaybackService.clearCurTempSpeed()
            PlaybackServiceStarter(context, media).shouldStreamThisTime(true).callEvenIfRunning(true).start()
            val item = runBlocking { setPlayStateSync(PlayState.PROGRESS.code, media, false) }
            EventFlow.postEvent(FlowEvent.PlayEvent(item))
            playVideoIfNeeded(context, media)
        }
    }
}

class DeleteActionButton(item: Episode) : EpisodeActionButton(item, R.string.delete_label, R.drawable.ic_delete) {
    override val visibility: Boolean
        get() {
            return (item.downloaded || item.feed?.isLocalFeed == true)
        }
    override fun onClick(context: Context) {
        runOnIOScope {
            deleteEpisodesWarnLocalRepeat(context, listOf(item))
            withContext(Dispatchers.Main) {  actionState.value = label }
        }
    }
}

class NullActionButton(item: Episode) : EpisodeActionButton(item, R.string.null_label, R.drawable.ic_questionmark) {
    override fun onClick(context: Context) {}
}

class PauseActionButton(item: Episode) : EpisodeActionButton(item, R.string.pause_label, R.drawable.ic_pause) {
    override fun onClick(context: Context) {
        Logd("PauseActionButton", "onClick called")
        if (isCurrentlyPlaying(item)) context.sendBroadcast(MediaButtonReceiver.createIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE))
//        EventFlow.postEvent(FlowEvent.PlayEvent(item, Action.END))
        actionState.value = label
    }
}

class DownloadActionButton(item: Episode) : EpisodeActionButton(item, R.string.download_label, R.drawable.ic_download) {
    override val visibility: Boolean
        get() = item.feed?.isLocalFeed != true

    override fun onClick(context: Context) {
        if (shouldNotDownload(item)) return
        UsageStatistics.logAction(UsageStatistics.ACTION_DOWNLOAD)
        if (mobileAllowEpisodeDownload || !isNetworkRestricted) DownloadServiceInterface.impl?.downloadNow(context, item, false)
        else {
            commonConfirm = CommonConfirmAttrib(
                title = context.getString(R.string.confirm_mobile_download_dialog_title),
                message = context.getString(if (isNetworkRestricted && NetworkUtils.isVpnOverWifi) R.string.confirm_mobile_download_dialog_message_vpn else R.string.confirm_mobile_download_dialog_message),
                confirmRes = R.string.confirm_mobile_download_dialog_download_later,
                cancelRes = R.string.cancel_label,
                neutralRes = R.string.confirm_mobile_download_dialog_allow_this_time,
                onConfirm = { DownloadServiceInterface.impl?.downloadNow(context, item, false) },
                onNeutral = { DownloadServiceInterface.impl?.downloadNow(context, item, true) })
        }
        actionState.value = label
    }

    private fun shouldNotDownload(media: Episode?): Boolean {
        if (media?.downloadUrl == null) return true
        val isDownloading = DownloadServiceInterface.impl?.isDownloadingEpisode(media.downloadUrl!!) == true
        return isDownloading || media.downloaded
    }
}

class TTSActionButton(item: Episode) : EpisodeActionButton(item, R.string.TTS_label, R.drawable.text_to_speech) {
    private var readerText: String? = null
    override val visibility: Boolean
        get() = !item.link.isNullOrEmpty()

    override fun onClick(context: Context) {
        Logd("TTSActionButton", "onClick called")
        if (item.link.isNullOrEmpty()) {
            Loge(TAG, context.getString(R.string.episode_has_no_content))
            return
        }
        processing = 1
        item = upsertBlk(item) { it.setPlayState(PlayState.BUILDING) }
        EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
        RealmDB.runOnIOScope {
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
                while (!FEObj.ttsReady) runBlocking { delay(100) }
                processing = 15
                EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                while (FEObj.ttsWorking) runBlocking { delay(100) }
                FEObj.ttsWorking = true
                if (item.feed?.language != null) {
                    val result = FEObj.tts?.setLanguage(Locale(item.feed!!.language!!))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                        Loge(TAG, "TTS language not supported ${item.feed!!.language} $result")
                        withContext(Dispatchers.Main) {
                            Loge(TAG, context.getString(R.string.language_not_supported_by_tts) + " ${item.feed!!.language} $result")
                        }
                    }
                }

                var j = 0
                val mediaFile = File(item.getMediafilePath(), item.getMediafilename())
                FEObj.tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
                        status = FEObj.tts?.synthesizeToFile(chunk, null, tempFile, tempFile.absolutePath) ?: 0
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
                FEObj.ttsWorking = false
            } else Loge(TAG, context.getString(R.string.episode_has_no_content))

            item = upsertBlk(item) { it.setPlayState(PlayState.UNPLAYED) }

            processing = 100
            EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
            actionState.value = label
        }
    }
}

class PlayLocalActionButton(item: Episode) : EpisodeActionButton(item, R.string.play_label, R.drawable.ic_play_24dp) {
    override fun onClick(context: Context) {
        Logd("PlayLocalActionButton", "onClick called")
        if (PlaybackService.playbackService?.isServiceReady() == true && InTheatre.isCurMedia(item)) {
            PlaybackService.playbackService?.mPlayer?.resume()
            PlaybackService.playbackService?.taskManager?.restartSleepTimer()
        } else {
            PlaybackService.clearCurTempSpeed()
            PlaybackServiceStarter(context, item).callEvenIfRunning(true).start()
            item = runBlocking { setPlayStateSync(PlayState.PROGRESS.code, item, false) }
            EventFlow.postEvent(FlowEvent.PlayEvent(item))
        }
        if (item.getMediaType() == MediaType.VIDEO) context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
        actionState.value = label
    }
}
