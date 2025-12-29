package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isPlaying
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.shouldRepeat
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.storage.database.MonitorEntity
import ac.mdiq.podcini.storage.database.episodeById
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.subscribeEpisode
import ac.mdiq.podcini.storage.database.unsubscribeEpisode
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState
import ac.mdiq.podcini.storage.model.CurrentState.Companion.NO_MEDIA_PLAYING
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_OTHER
import ac.mdiq.podcini.storage.model.CurrentState.Companion.SPEED_USE_GLOBAL
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Episode.Companion.PLAYABLE_TYPE_FEEDMEDIA
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.QueueEntry
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object InTheatre {
    private val TAG: String = InTheatre::class.simpleName ?: "Anonymous"

    const val VIRTUAL_QUEUE_SIZE = 50

    internal var aCtrlFuture: ListenableFuture<MediaController>? = null
    var aController: MediaController? = null

    internal var vCtrlFuture: ListenableFuture<MediaController>? = null
    var vController: MediaController? = null

    var actQueue by mutableStateOf(PlayQueue())

    var curEpisode by mutableStateOf<Episode?>(null)

    var curTempSpeed: Float = SPEED_USE_GLOBAL

    var tempSkipSilence: Boolean? = null

    private var curStateMonitor: Job? = null
    var curState: CurrentState = CurrentState()

    var playerStat by mutableIntStateOf(PLAYER_STATUS_OTHER)

    var bitrate by mutableIntStateOf(0)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            Logd(TAG, "starting curState")
            restoreMediaFromPreferences()
            if (curEpisode != null) {
                val qes = realm.query(QueueEntry::class).query("episodeId == ${curEpisode!!.id}").find()
                if (qes.isNotEmpty()) {
                    val q = realm.query(PlayQueue::class).query("id == ${qes[0].queueId}").first().find()
                    if (q != null) actQueue = q
                }
            }
        }
        monitorState()
    }

    fun monitorState() {
        if (curStateMonitor == null) curStateMonitor = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            curState = realm.query(CurrentState::class).query("id == 0").first().find() ?: upsertBlk(CurrentState()) {}
            val item_ = realm.query(CurrentState::class).first()
            Logd(TAG, "start monitoring curState: ")
            val stateFlow = item_.asFlow()
            stateFlow.collect { changes: SingleQueryChange<CurrentState> ->
                when (changes) {
                    is UpdatedObject -> {
                        curState = changes.obj
                        if (changes.changedFields.contains("curPlayerStatus")) withContext(Dispatchers.Main) { playerStat = curState.curPlayerStatus }
                        Logd(TAG, "stateMonitor UpdatedObject ${changes.obj.curMediaId} playerStat: $playerStat ${changes.changedFields.joinToString()}")
                    }
                    is InitialObject -> {
                        curState = changes.obj
                        Logd(TAG, "stateMonitor InitialObject ${changes.obj.curMediaId}")
                    }
                    else -> Logd(TAG, "stateMonitor other changes: $changes")
                }
            }
        }
    }

    // TODO: these appear not needed
    var onCurInitUICB: (suspend (e: Episode)->Unit)? = null
    var onCurChangedUICB: (suspend (e: Episode, fields: Array<String>)->Unit)? = null

    fun setAsCurEpisode(episode: Episode?) {
        Logd(TAG, "setCurEpisode episode: ${episode?.title}")
//        showStackTrace()
        if (episode != null && episode.id == curEpisode?.id) return
        if (curEpisode != null) unsubscribeEpisode(curEpisode!!, TAG)
        val episode_ = episodeById(episode!!.id)
        when {
            episode_ != null -> {
                curEpisode = episode_
                shouldRepeat = false
                curTempSpeed = SPEED_USE_GLOBAL
                Logd(TAG, "setCurEpisode start monitoring curEpisode ${curEpisode?.title}")
                runOnIOScope {
                    val qes = realm.query(QueueEntry::class).query("episodeId == ${curEpisode!!.id}").find()
                    if (qes.isNotEmpty()) {
                        val q = queuesLive.find { it.id == qes[0].queueId }
                        if (q != null) actQueue = q
                    }
                    subscribeEpisode(curEpisode!!,
                        MonitorEntity(TAG,
                            onInit = { e -> onCurInitUICB?.invoke(e) },
                            onChanges = { e, f ->
                                if (e.id == curEpisode?.id) {
                                    curEpisode = e
                                    Logd(TAG, "setCurEpisode updating curEpisode [${curEpisode?.title}] ${f.joinToString()}")
                                    onCurChangedUICB?.invoke(e, f)
                                }
                            }
                        ))
                }
            }
            else -> curEpisode = null
        }
    }

    fun savePlayerStatus(playerStatus: PlayerStatus?) {
        runOnIOScope {
            if (playerStatus != null) upsert(curState) { it.curPlayerStatus = playerStatus.getAsInt() }
            else upsert(curState) {
                it.curMediaType = NO_MEDIA_PLAYING
                it.curFeedId = NO_MEDIA_PLAYING
                it.curMediaId = NO_MEDIA_PLAYING
                it.curPlayerStatus = PLAYER_STATUS_OTHER
            }
        }
    }

    fun savePlayerStatus(episode: Episode?, playerStatus: PlayerStatus) {
        Logd(TAG, "Writing playback preferences ${episode?.id}")
        if (episode == null) savePlayerStatus(null)
        else runOnIOScope {
            upsert(curState) {
                it.curPlayerStatus = playerStatus.getAsInt()
                it.curMediaType = PLAYABLE_TYPE_FEEDMEDIA.toLong()
                it.curIsVideo = episode.getMediaType() == MediaType.VIDEO
                val feedId = episode.feed?.id
                if (feedId != null) it.curFeedId = feedId
                it.curMediaId = episode.id
            }
        }
    }

    // TODO: check out this
    /**
     * Restores a playable object from a sharedPreferences file. This method might load data from the database,
     * depending on the type of playable that was restored.
     * @return The restored EpisodeMedia object
     */
    fun restoreMediaFromPreferences() {
        Logd(TAG, "loadPlayableFromPreferences currentlyPlayingType: $curState.curMediaType")
        if (curState.curMediaType != NO_MEDIA_PLAYING) {
            val type = curState.curMediaType.toInt()
            if (type == PLAYABLE_TYPE_FEEDMEDIA) {
                if (curState.curMediaId != 0L) setAsCurEpisode(episodeById(curState.curMediaId))
            } else Loge(TAG, "Could not restore EpisodeMedia object from preferences")
        }
    }

    @OptIn(UnstableApi::class)
    fun isCurrentlyPlaying(media: Episode?): Boolean {
        return isCurMedia(media) && PlaybackService.isRunning && isPlaying
    }

    fun isCurMedia(media: Episode?): Boolean {
        return media != null && curEpisode?.id == media.id
    }

    fun cleanup() {
        Logd(TAG, "cleanup()")
        if (curEpisode != null) unsubscribeEpisode(curEpisode!!, TAG)
        curStateMonitor?.cancel()
        curStateMonitor = null
    }
}