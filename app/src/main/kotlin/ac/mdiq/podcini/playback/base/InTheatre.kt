package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeMedia
import ac.mdiq.podcini.storage.database.RealmDB.MonitorEntity
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.subscribeEpisode
import ac.mdiq.podcini.storage.database.RealmDB.unsubscribeEpisode
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState
import ac.mdiq.podcini.storage.model.CurrentState.Companion.NO_MEDIA_PLAYING
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_OTHER
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Episode.Companion.PLAYABLE_TYPE_FEEDMEDIA
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import io.github.xilinjia.krdb.query.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object InTheatre {
    private val TAG: String = InTheatre::class.simpleName ?: "Anonymous"

    internal var aCtrlFuture: ListenableFuture<MediaController>? = null
    var aController: MediaController? = null
    internal var vCtrlFuture: ListenableFuture<MediaController>? = null
    var vController: MediaController? = null

    var curIndexInQueue = -1

    var curQueue: PlayQueue     // managed

//    private var curEpisodeMonitor: Job? = null
    var curEpisode: Episode? = null     // manged
        private set

    var curMediaId by mutableLongStateOf(-1L)

    private var curStateMonitor: Job? = null
    var curState: CurrentState      // managed

    var playerStat by mutableIntStateOf(PLAYER_STATUS_OTHER)

    var bitrate by mutableIntStateOf(0)

    init {
        curQueue = PlayQueue()
        curState = CurrentState()
        CoroutineScope(Dispatchers.IO).launch {
            Logd(TAG, "starting curQueue")
            var curQueue_ = realm.query(PlayQueue::class).sort("updated", Sort.DESCENDING).first().find()
            if (curQueue_ != null) curQueue = curQueue_
            else {
                for (i in 0..4) {
                    curQueue_ = PlayQueue()
                    if (i == 0) {
                        curQueue_.name = "Default"
                        curQueue = curQueue_
                    } else {
                        curQueue_.id = i.toLong()
                        curQueue_.name = "Queue $i"
                    }
                    upsert(curQueue_) {}
                }
                upsert(curQueue) { it.update() }
            }
            Logd(TAG, "starting curState")
            val curState_ = realm.query(CurrentState::class).first().find()
            if (curState_ != null) curState = curState_
            else {
                Logd(TAG, "creating new curState")
                upsert(curState) {}
            }
            loadPlayableFromPreferences()
        }
        monitorState()
    }

    fun monitorState() {
        if (curStateMonitor == null) curStateMonitor = CoroutineScope(Dispatchers.IO).launch {
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

    var onCurInitUICB: (suspend (e: Episode)->Unit)? = null
    var onCurChangedUICB: (suspend (e: Episode, fields: Array<String>)->Unit)? = null

    fun setCurEpisode(episode: Episode?) {
        Logd(TAG, "setCurEpisode episode: ${episode?.title}")
//        showStackTrace()
        if (episode != null && episode.id == curEpisode?.id) return
        if (curEpisode != null) unsubscribeEpisode(curEpisode!!, TAG)
        when {
            episode != null -> {
                curEpisode = episode
                Logd(TAG, "setCurEpisode start monitoring curEpisode ${curEpisode?.title}")
                subscribeEpisode(curEpisode!!, MonitorEntity(TAG,
                    onChanges = { e, f ->
                        if (e.id == curEpisode?.id) {
                            curEpisode = e
                            Logd(TAG, "setCurEpisode updating curEpisode [${curEpisode?.title}] ${f.joinToString()}")
                            onCurChangedUICB?.invoke(e, f)
                        }
                    },
                    onInit = { e ->
//                        curEpisode = e
                        onCurInitUICB?.invoke(e)
                    }
                ))
                curMediaId = episode.id
            }
            else -> {
                curEpisode = null
                curMediaId = -1L
            }
        }
    }

    fun setCurTempSpeed(speed: Float) {
        upsertBlk(curState) { it.curTempSpeed = speed }
    }

    fun clearCurTempSpeed() {
        upsertBlk(curState) { it.curTempSpeed = Feed.SPEED_USE_GLOBAL }
    }

    fun writePlayerStatus(playerStatus: PlayerStatus) {
        upsertBlk(curState) { it.curPlayerStatus = playerStatus.getAsInt() }
    }

    fun writeMediaPlaying(playable: Episode?, playerStatus: PlayerStatus) {
        Logd(TAG, "Writing playback preferences ${playable?.id}")
        if (playable == null) writeNoMediaPlaying()
        else {
            upsertBlk(curState) {
                it.curMediaType = PLAYABLE_TYPE_FEEDMEDIA.toLong()
                it.curIsVideo = playable.getMediaType() == MediaType.VIDEO
                val feedId = playable.feed?.id
                if (feedId != null) it.curFeedId = feedId
                it.curMediaId = playable.id
//                it.curPlayerStatus = playerStatus.getAsInt()
            }
        }
    }

    fun writeNoMediaPlaying() {
        upsertBlk(curState) {
            it.curMediaType = NO_MEDIA_PLAYING
            it.curFeedId = NO_MEDIA_PLAYING
            it.curMediaId = NO_MEDIA_PLAYING
            it.curPlayerStatus = PLAYER_STATUS_OTHER
        }
    }

    /**
     * Restores a playable object from a sharedPreferences file. This method might load data from the database,
     * depending on the type of playable that was restored.
     * @return The restored EpisodeMedia object
     */
    fun loadPlayableFromPreferences() {
        Logd(TAG, "loadPlayableFromPreferences currentlyPlayingType: $curState.curMediaType")
        if (curState.curMediaType != NO_MEDIA_PLAYING) {
            val type = curState.curMediaType.toInt()
            if (type == PLAYABLE_TYPE_FEEDMEDIA) {
                val mediaId = curState.curMediaId
                Logd(TAG, "loadPlayableFromPreferences getting mediaId: $mediaId")
                if (mediaId != 0L) setCurEpisode(getEpisodeMedia(mediaId))
                Logd(TAG, "loadPlayableFromPreferences: curMedia: ${curEpisode?.id}")
            } else Loge(TAG, "Could not restore EpisodeMedia object from preferences")
        }
    }

    @OptIn(UnstableApi::class)
    @JvmStatic
    fun isCurrentlyPlaying(media: Episode?): Boolean {
        return isCurMedia(media) && PlaybackService.isRunning && MediaPlayerBase.status == PlayerStatus.PLAYING
    }

    @JvmStatic
    fun isCurMedia(media: Episode?): Boolean {
        return media != null && curEpisode?.id == media.id
    }

    fun cleanup() {
        Logd(TAG, "cleanup()")
//        curEpisodeMonitor?.cancel()
//        curEpisodeMonitor = null
        if (curEpisode != null) unsubscribeEpisode(curEpisode!!, TAG)
        curStateMonitor?.cancel()
        curStateMonitor = null
    }
}