package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeMedia
import ac.mdiq.podcini.storage.database.RealmDB.episodeMonitor
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import io.github.xilinjia.krdb.query.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object InTheatre {
    val TAG: String = InTheatre::class.simpleName ?: "Anonymous"

    internal var aCtrlFuture: ListenableFuture<MediaController>? = null
    var aController: MediaController? = null
    internal var vCtrlFuture: ListenableFuture<MediaController>? = null
    var vController: MediaController? = null

    var curIndexInQueue = -1

    var curQueue: PlayQueue     // managed

    var curEpisodeMonitor: Job? = null
    var curEpisode: Episode? = null     // unmanged
        private set

    var curMediaId by mutableLongStateOf(-1L)

    var curState: CurrentState      // managed

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
            var curState_ = realm.query(CurrentState::class).first().find()
            if (curState_ != null) curState = curState_
            else {
                Logd(TAG, "creating new curState")
                curState_ = CurrentState()
                curState = curState_
                upsert(curState_) {}
            }
            loadPlayableFromPreferences()
        }
    }

    fun setCurEpisode(episode: Episode?) {
        if (episode != null && episode.id == curEpisode?.id) return
        curEpisodeMonitor?.cancel()
        curEpisodeMonitor = null
        when {
            episode != null -> {
                curEpisode = unmanaged(episode)
                curEpisodeMonitor = episodeMonitor(curEpisode!!) {e, f ->  withContext(Dispatchers.Main) { curEpisode = unmanaged(e) } }
                curMediaId = episode.id
            }
            else -> {
                curEpisode = null
                curMediaId = -1L
            }
        }
    }

    fun clearCurTempSpeed() {
        curState = upsertBlk(curState) { it.curTempSpeed = Feed.SPEED_USE_GLOBAL }
    }

    fun writePlayerStatus(playerStatus: PlayerStatus) {
        Logd(TAG, "Writing player status playback preferences")
        curState = upsertBlk(curState) { it.curPlayerStatus = playerStatus.getAsInt() }
    }

    fun writeMediaPlaying(playable: Episode?, playerStatus: PlayerStatus) {
        Logd(TAG, "Writing playback preferences ${playable?.id}")
        if (playable == null) writeNoMediaPlaying()
        else {
            curState = upsertBlk(curState) {
                it.curMediaType = PLAYABLE_TYPE_FEEDMEDIA.toLong()
                it.curIsVideo = playable.getMediaType() == MediaType.VIDEO
                val feedId = playable.feed?.id
                if (feedId != null) it.curFeedId = feedId
                it.curMediaId = playable.id
                it.curPlayerStatus = playerStatus.getAsInt()
            }
        }
    }

    fun writeNoMediaPlaying() {
        curState = upsertBlk(curState) {
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

    @JvmStatic
    fun isCurrentlyPlaying(media: Episode?): Boolean {
        return isCurMedia(media) && PlaybackService.isRunning && MediaPlayerBase.status == PlayerStatus.PLAYING
    }

    @JvmStatic
    fun isCurMedia(media: Episode?): Boolean {
        return media != null && curEpisode?.id == media.id
    }
}