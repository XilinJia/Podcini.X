package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeMedia
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.CurrentState.Companion.NO_MEDIA_PLAYING
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_OTHER
import ac.mdiq.podcini.util.Logd
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object InTheatre {
    val TAG: String = InTheatre::class.simpleName ?: "Anonymous"

    var curIndexInQueue = -1

    var curQueue: PlayQueue     // managed

    var curEpisode: Episode? = null     // unmanged
        set(value) {
            when {
                value != null -> {
                    field = unmanaged(value)
                    curMediaId = value.id
                }
                else -> {
                    field = null
                    curMediaId = -1L
                }
            }
        }

    var curMediaId by mutableLongStateOf(-1L)

    var curState: CurrentState      // managed

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
            if (type == Episode.PLAYABLE_TYPE_FEEDMEDIA) {
                val mediaId = curState.curMediaId
                Logd(TAG, "loadPlayableFromPreferences getting mediaId: $mediaId")
                if (mediaId != 0L) {
                    curEpisode = getEpisodeMedia(mediaId)
                    if (curEpisode != null) curEpisode = curEpisode
                }
                Logd(TAG, "loadPlayableFromPreferences: curMedia: ${curEpisode?.id}")
            } else Log.e(TAG, "Could not restore EpisodeMedia object from preferences")
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