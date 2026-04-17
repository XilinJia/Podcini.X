package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadRequest.Companion.requestFor
import ac.mdiq.podcini.net.download.Downloader.Companion.downloaderFor
import ac.mdiq.podcini.net.download.EpisodeAdrDLManager
import ac.mdiq.podcini.net.download.EpisodeDLManager.Companion.updateDB
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowEpisodeDownload
import ac.mdiq.podcini.net.utils.NetworkUtils.networkMonitor
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.activeTheatres
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.InTheatre.theatres
import ac.mdiq.podcini.playback.base.TTSEngine
import ac.mdiq.podcini.playback.base.TTSEngine.doTTS
import ac.mdiq.podcini.playback.base.TTSEngine.doTTSNow
import ac.mdiq.podcini.playback.base.TTSEngine.ensureTTS
import ac.mdiq.podcini.playback.base.TTSEngine.tts
import ac.mdiq.podcini.playback.base.TTSEngine.ttsJob
import ac.mdiq.podcini.playback.base.TTSEngine.ttsTmpFiles
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.deleteEpisodesWarnLocalRepeat
import ac.mdiq.podcini.storage.database.prefStreamOverDownload
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.tmpQueue
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.VideoMode
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.screens.PSState
import ac.mdiq.podcini.ui.screens.curVideoMode
import ac.mdiq.podcini.ui.screens.psState
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.openInBrowser
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking

class ActionButton(var item: Episode, typeInit: ButtonTypes = ButtonTypes.NULL) {
    private val TAG = this::class.simpleName ?: "ItemActionButton"

    private var _type = mutableStateOf(typeInit)
    var type: ButtonTypes
        get() = _type.value
        set(value) {
//            Logd(TAG, "set ButtonTypes to $value")
            _type.value = value
            label = value.labelRes
            drawable = value.drawable
        }

    var typeToCancel: ButtonTypes = ButtonTypes.DOWNLOAD

    var processing = mutableIntStateOf(-1)

    var speaking = mutableStateOf(false)

    var label by mutableIntStateOf(typeInit.labelRes)
    var drawable by mutableIntStateOf(typeInit.drawable)

    init {
        if (type == ButtonTypes.NULL) {
            if (item.feed?.prefActionType != null && item.feed!!.prefActionType!! !in playActions.map { it.name })
                try { type = ButtonTypes.valueOf(item.feed!!.prefActionType!!) } catch (e: Throwable) { Loge(TAG, "error in getting feed prefActionType: ${item.feed?.prefActionType}") }
            else update(item)
        }
    }

    fun onClick() {
        val context = getAppContext()
        fun fileNotExist(): Boolean {
            if (!item.isDownloaded()) {
                Loge(TAG, context.getString(R.string.error_file_not_found) + ": ${item.title}")
                val episode_ = upsertBlk(item) { it.fileUrl = null }
                EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode_))
                update(episode_)
                return true
            }
            return false
        }
        fun askToStream(stream: ()->Unit) {
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
        }
        fun askForPlayer(play: (Int)->Unit) {
            commonConfirm = CommonConfirmAttrib(
                title = context.getString(R.string.select_player),
                message = "",
                confirmRes = R.string.the_default,
                cancelRes = R.string.secondary,
                onConfirm = { play(0) },
                onCancel = { play(1) })
            return
        }
        Logd(TAG, "onClick type: $type")
        when (type) {
            ButtonTypes.WEBSITE -> if (!item.link.isNullOrEmpty()) openInBrowser(item.link!!)
            ButtonTypes.CANCEL -> {
                if (typeToCancel == ButtonTypes.DOWNLOAD) {
                    runBlocking { EpisodeAdrDLManager.manager?.cancel(item) }
                    if (appPrefs.enableAutoDl) upsertBlk(item) { it.isAutoDownloadEnabled = false }
                    type = ButtonTypes.DOWNLOAD
                } else if (typeToCancel == ButtonTypes.TTS) {
                    runOnIOScope { for (p in ttsTmpFiles) p.toUF().delete() }
                    TTSEngine.ttsWorking = false
                    processing.intValue = -1
                    ttsJob?.cancel()
                    ttsJob = null
                    type = ButtonTypes.TTS
                }
            }
            ButtonTypes.PLAY -> {
                if (fileNotExist()) return
                if (activeTheatres == 1) {
                    PlaybackStarter(item).start(0)
                    playVideoIfNeeded(item)
                } else askForPlayer { i-> PlaybackStarter(item).start(i) }
            }
            ButtonTypes.PLAY_ONE -> {
                if (fileNotExist()) return
                if (activeTheatres == 1) {
                    PlaybackStarter(item).start(0)
                    playVideoIfNeeded(item)
                    actQueue = tmpQueue()
                } else askForPlayer { i->
                    PlaybackStarter(item).start(i)
                    actQueue = tmpQueue()
                }
            }
            ButtonTypes.PLAY_REPEAT -> {
                if (fileNotExist()) return
                if (activeTheatres == 1) {
                    PlaybackStarter(item).setToRepeat(true).start(0)
                    playVideoIfNeeded(item)
                    actQueue = tmpQueue()
                } else askForPlayer { i->
                    PlaybackStarter(item).setToRepeat(true).start(i)
                    actQueue = tmpQueue()
                }
            }
            ButtonTypes.STREAM -> {
                //        Logd("StreamActionButton", "item.feed: ${item.feedId}")
                askToStream {
                    if (activeTheatres == 1) {
                        PlaybackStarter(item).shouldStreamThisTime(true).start(0)
                        playVideoIfNeeded(item)
                    } else askForPlayer { i-> PlaybackStarter(item).shouldStreamThisTime(true).start(i) }
                }
            }
            ButtonTypes.STREAM_REPEAT -> {
                //        Logd("StreamActionButton", "item.feed: ${item.feedId}")
                askToStream {
                    if (activeTheatres == 1) {
                        PlaybackStarter(item).shouldStreamThisTime(true).setToRepeat(true).start(0)
                        playVideoIfNeeded(item)
                        actQueue = tmpQueue()
                    } else askForPlayer { i->
                        PlaybackStarter(item).shouldStreamThisTime(true).setToRepeat(true).start(i)
                        actQueue = tmpQueue()
                    }
                }
            }
            ButtonTypes.STREAM_ONE -> {
                if (activeTheatres == 1) {
                    PlaybackStarter(item).shouldStreamThisTime(true).start(0)
                    playVideoIfNeeded(item)
                    actQueue = tmpQueue()
                } else askForPlayer { i->
                    PlaybackStarter(item).shouldStreamThisTime(true).start(i)
                    actQueue = tmpQueue()
                }
            }
            ButtonTypes.DELETE -> {
                runOnIOScope { deleteEpisodesWarnLocalRepeat(listOf(item)) }
                update(item)
            }
            ButtonTypes.PAUSE -> {
                if (isCurrentlyPlaying(item)) {
                    for (i in 0..1) {
                        if (item.id != theatres[i].mPlayer?.curEpisode?.id) continue
                        theatres[i].mPlayer?.pause(false)
                        update(item)
                    }
                }
                if (tts?.isSpeaking == true) tts?.stop()
            }
            ButtonTypes.DOWNLOAD -> {
                fun downloadNow() {
                    runOnIOScope {
                        val request = requestFor(item).build()
                        val downloader = downloaderFor(request)
                        downloader?.download()
                        val status = downloader?.result
                        if (status?.isSuccessful == true) updateDB(request)
                    }
                }
                fun shouldNotDownload(): Boolean {
                    if (item.downloadUrl.isNullOrBlank()) {
                        Loge(TAG, "episode downloadUrl is null or blank: ${item.title}")
                        return true
                    }
                    val isDownloading = EpisodeAdrDLManager.manager?.isDownloading(item.downloadUrl!!) == true
                    return isDownloading || item.downloaded
                }
                if (shouldNotDownload()) return
                if (mobileAllowEpisodeDownload || !networkMonitor.isNetworkRestricted) {
                    downloadNow()
                    Logd(TAG, "downloading ${item.title}")
                    typeToCancel = ButtonTypes.DOWNLOAD
                    type = ButtonTypes.CANCEL
                    return
                }
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.confirm_mobile_download_dialog_title),
                    message = context.getString(if (networkMonitor.isNetworkRestricted && networkMonitor.isVpnOverWifi) R.string.confirm_mobile_download_dialog_message_vpn else R.string.confirm_mobile_download_dialog_message),
                    confirmRes = R.string.confirm_mobile_download_dialog_download_later,
                    cancelRes = R.string.cancel_label,
                    neutralRes = R.string.confirm_mobile_download_dialog_allow_this_time,
                    onConfirm = { EpisodeAdrDLManager.manager?.download( listOf(item)) },
                    onNeutral = { downloadNow() })
            }
            ButtonTypes.TTS_NOW -> {
                Logd("JUSTTTSButton", "onClick called")
                type = ButtonTypes.PAUSE
                ensureTTS()
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.choose_tts_source),
                    message = "",
                    confirmRes = R.string.description_label,
                    cancelRes = R.string.cancel_label,
                    neutralRes = R.string.transcript,
                    onConfirm = { doTTSNow(item, 1, speaking) },
                    onNeutral = { doTTSNow(item, 2, speaking) })
            }
            ButtonTypes.TTS -> {
                Logd("TTSActionButton", "onClick called")
//                if (item.link.isNullOrEmpty()) {
//                    Loge(TAG, context.getString(R.string.episode_has_no_content))
//                    type = ButtonTypes.NULL
//                    return
//                }
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.choose_tts_source),
                    message = "",
                    confirmRes = R.string.description_label,
                    cancelRes = R.string.cancel_label,
                    neutralRes = R.string.transcript,
                    onConfirm = {
                        typeToCancel = ButtonTypes.TTS
                        type = ButtonTypes.CANCEL
                        doTTS(item, 1, processing) { update(it) }
                    },
                    onNeutral = {
                        typeToCancel = ButtonTypes.TTS
                        type = ButtonTypes.CANCEL
                        doTTS(item, 2, processing) { update(it) }
                    })
            }
            ButtonTypes.PLAY_LOCAL -> {
                if (PlaybackService.playbackService?.isServiceReady() == true && InTheatre.isCurMedia(item)) {
                    for (i in 0..1) {
                        if (item.id != theatres[i].mPlayer?.curEpisode?.id) continue
                        theatres[i].mPlayer?.play()
                    }
                } else {
                    if (activeTheatres == 1) {
                        PlaybackStarter(item).start(0)
                        if (item.playState < EpisodeState.PROGRESS.code || item.playState == EpisodeState.SKIPPED.code || item.playState == EpisodeState.AGAIN.code) item = upsertBlk(item) { it.setPlayState(EpisodeState.PROGRESS) }
                    } else askForPlayer { i->
                        PlaybackStarter(item).start(i)
                        if (item.playState < EpisodeState.PROGRESS.code || item.playState == EpisodeState.SKIPPED.code || item.playState == EpisodeState.AGAIN.code) item = upsertBlk(item) { it.setPlayState(EpisodeState.PROGRESS) }
                    }
                }
                playVideoIfNeeded(item)
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
                return EpisodeAdrDLManager.manager?.isDownloading(item.downloadUrl!!) == true
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
            ButtonTypes.PLAY_LOCAL -> if (isCurrentlyPlaying(item)) type = ButtonTypes.PAUSE
            ButtonTypes.PLAY, ButtonTypes.PLAY_ONE, ButtonTypes.PLAY_REPEAT -> {
                if (!item.downloaded) type = undownloadedType()
                else if (isCurrentlyPlaying(item)) type = ButtonTypes.PAUSE
            }
            ButtonTypes.STREAM, ButtonTypes.STREAM_ONE, ButtonTypes.STREAM_REPEAT -> if (isCurrentlyPlaying(item)) type = ButtonTypes.PAUSE
            ButtonTypes.PAUSE -> {
                type = when {
                    item.feed?.prefActionType != null -> ButtonTypes.valueOf(item.feed!!.prefActionType!!)
                    item.feed?.isLocalFeed == true -> ButtonTypes.PLAY_LOCAL
                    item.downloaded -> {
                        if (item.feed?.prefActionType != null && item.feed!!.prefActionType!! in playActions.map { it.name }) ButtonTypes.valueOf(item.feed!!.prefActionType!!)
                        else ButtonTypes.PLAY
                    }
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
                    item.feed?.isLocalFeed == true -> ButtonTypes.PLAY_LOCAL
                    item.downloaded -> ButtonTypes.PLAY
                    else -> undownloadedType()
                }
            }
        }
//        Logd(TAG, "update type: $type")
    }

    
    @Composable
    fun AltActionsDialog(onDismiss: () -> Unit) {
        CommonPopupCard(onDismissRequest = onDismiss) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Logd(TAG, "button label: $type")
                if (type != ButtonTypes.TTS) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        type = ButtonTypes.TTS
                        onClick()
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.TTS.drawable), modifier = Modifier.size(24.dp), contentDescription = "TTS")
                        Text(stringResource(ButtonTypes.TTS.labelRes))
                    }
                }
                if (type != ButtonTypes.TTS_NOW) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        type = ButtonTypes.TTS_NOW
                        onClick()
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.TTS_NOW.drawable), modifier = Modifier.size(24.dp), contentDescription = "TTS now")
                        Text(stringResource(ButtonTypes.TTS_NOW.labelRes))
                    }
                }
                if (type != ButtonTypes.WEBSITE) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        val btn = ActionButton(item, ButtonTypes.WEBSITE)
                        btn.onClick()
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.WEBSITE.drawable), modifier = Modifier.size(24.dp), contentDescription = "Web")
                        Text(stringResource(ButtonTypes.WEBSITE.labelRes))
                    }
                }
                if (type !in listOf(ButtonTypes.PLAY, ButtonTypes.DOWNLOAD, ButtonTypes.DELETE)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        val btn = ActionButton(item, ButtonTypes.DOWNLOAD)
                        btn.onClick()
                        type = btn.type
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.DOWNLOAD.drawable), modifier = Modifier.size(24.dp), contentDescription = "Download")
                        Text(stringResource(ButtonTypes.DOWNLOAD.labelRes))
                    }
                }
                if (type !in listOf(ButtonTypes.STREAM, ButtonTypes.DOWNLOAD, ButtonTypes.DELETE)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        val btn = ActionButton(item, ButtonTypes.DELETE)
                        btn.onClick()
                        type = btn.type
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.DELETE.drawable), modifier = Modifier.size(24.dp), contentDescription = "Delete")
                        Text(stringResource(ButtonTypes.DELETE.labelRes))
                    }
                }
                if (type !in listOf(ButtonTypes.PAUSE, ButtonTypes.STREAM, ButtonTypes.DOWNLOAD)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        val btn = ActionButton(item, ButtonTypes.PLAY_REPEAT)
                        btn.onClick()
                        type = btn.type
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.PLAY_REPEAT.drawable), modifier = Modifier.size(24.dp), contentDescription = "Play repeat")
                        Text(stringResource(ButtonTypes.PLAY_REPEAT.labelRes))
                    }
                }
                if (type !in listOf(ButtonTypes.PLAY, ButtonTypes.PAUSE, ButtonTypes.STREAM, ButtonTypes.DOWNLOAD)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        val btn = ActionButton(item, ButtonTypes.PLAY)
                        btn.onClick()
                        type = btn.type
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.PLAY.drawable), modifier = Modifier.size(24.dp), contentDescription = "Play")
                        Text(stringResource(ButtonTypes.PLAY.labelRes))
                    }
                }
                if (type !in listOf(ButtonTypes.PAUSE, ButtonTypes.STREAM, ButtonTypes.DOWNLOAD)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        val btn = ActionButton(item, ButtonTypes.PLAY_ONE)
                        btn.onClick()
                        type = btn.type
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.PLAY_ONE.drawable), modifier = Modifier.size(24.dp), contentDescription = "Play one")
                        Text(stringResource(ButtonTypes.PLAY_ONE.labelRes))
                    }
                }
                if (type !in listOf(ButtonTypes.PLAY, ButtonTypes.PAUSE, ButtonTypes.DELETE)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        val btn = ActionButton(item, ButtonTypes.STREAM_REPEAT)
                        btn.onClick()
                        type = btn.type
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.STREAM_REPEAT.drawable), modifier = Modifier.size(24.dp), contentDescription = "Stream repeat")
                        Text(stringResource(ButtonTypes.STREAM_REPEAT.labelRes))
                    }
                }
                if (type !in listOf(ButtonTypes.PLAY, ButtonTypes.PAUSE, ButtonTypes.STREAM, ButtonTypes.DELETE)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        val btn = ActionButton(item, ButtonTypes.STREAM)
                        btn.onClick()
                        type = btn.type
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.STREAM.drawable), modifier = Modifier.size(24.dp), contentDescription = "Stream")
                        Text(stringResource(ButtonTypes.STREAM.labelRes))
                    }
                }
                if (type !in listOf(ButtonTypes.PLAY, ButtonTypes.PAUSE, ButtonTypes.DELETE)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        val btn = ActionButton(item, ButtonTypes.STREAM_ONE)
                        btn.onClick()
                        type = btn.type
                        onDismiss()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(ButtonTypes.STREAM_ONE.drawable), modifier = Modifier.size(24.dp), contentDescription = "Stream one")
                        Text(stringResource(ButtonTypes.STREAM_ONE.labelRes))
                    }
                }
            }
        }
    }
    
    companion object {
        @OptIn(ExperimentalMaterial3Api::class)
        fun playVideoIfNeeded(item: Episode) {
            for (i in 0..1) {
                if (item.id != theatres[i].mPlayer?.curEpisode?.id) continue
                if (item.forceVideo || (item.feed?.videoModePolicy != VideoMode.AUDIO_ONLY && appPrefs.videoPlaybackMode != VideoMode.AUDIO_ONLY.code && curVideoMode != VideoMode.AUDIO_ONLY && item.getMediaType() == MediaType.VIDEO)) {
                    theatres[i].mPlayer?.playVideo = true
                    psState = PSState.Expanded
                } else theatres[i].mPlayer?.playVideo = false
            }
        }
    }
}

val streamActions = listOf(ButtonTypes.STREAM, ButtonTypes.STREAM_REPEAT, ButtonTypes.STREAM_ONE)
val playActions = listOf(ButtonTypes.PLAY, ButtonTypes.PLAY_REPEAT, ButtonTypes.PLAY_ONE)

enum class ButtonTypes(val labelRes: Int, val drawable: Int) {
    WEBSITE(R.string.visit_website_label, R.drawable.ic_web),
    CANCEL(R.string.cancel_download_label, R.drawable.ic_cancel),
    PLAY(R.string.play_label, R.drawable.ic_play_24dp),
    STREAM(R.string.stream_label, R.drawable.ic_stream),
    PLAY_ONE(R.string.play_one, R.drawable.outline_play_pause_24),
    STREAM_ONE(R.string.stream_one, R.drawable.play_stream_svgrepo_com),

    PLAY_REPEAT(R.string.play_repeat, R.drawable.outline_autoplay_24),
    STREAM_REPEAT(R.string.stream_repeat, R.drawable.outline_repeat_24),

    DELETE(R.string.delete_label, R.drawable.ic_delete),
    NULL(R.string.null_label, R.drawable.ic_questionmark),
    PAUSE(R.string.pause_label, R.drawable.ic_pause),
    DOWNLOAD(R.string.download_label, R.drawable.ic_download),
    TTS(R.string.TTS_label, R.drawable.text_to_speech),
    TTS_NOW(R.string.TTS_now, R.drawable.text_to_speech_svgrepo_com),
    PLAY_LOCAL(R.string.play_label, R.drawable.ic_play_24dp)
}
