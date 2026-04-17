package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.storage.database.episodeById
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.unsubscribeEpisode
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState
import ac.mdiq.podcini.storage.model.CurrentState.Companion.LONG_MINUS_1
import ac.mdiq.podcini.storage.model.CurrentState.Companion.LONG_PLUS_1
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.QueueEntry
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logpe
import ac.mdiq.podcini.utils.timeIt
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object InTheatre {
    private val TAG: String = InTheatre::class.simpleName ?: "Anonymous"

    internal var aCtrlFuture: ListenableFuture<MediaController>? = null
    var aController: MediaController? = null

    internal var vCtrlFuture: ListenableFuture<MediaController>? = null
    var vController: MediaController? = null

    var actQueue by mutableStateOf(PlayQueue())

    var activeTheatres by mutableIntStateOf(1)

    class Theatre(val id: Int) {
        var mPlayer by mutableStateOf<MediaPlayerBase?>(null)

        var curStateMonitor: Job? = null

        fun monitorState() {
            if (curStateMonitor == null) curStateMonitor = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val cst = realm.query(CurrentState::class).query("id == $id").first()
                Logd(TAG, "start monitoring curState: ")
                val stateFlow = cst.asFlow()
                stateFlow.collect { changes: SingleQueryChange<CurrentState> ->
                    when (changes) {
                        is UpdatedObject -> {
                            mPlayer?.curState = changes.obj
                            Logd(TAG, "stateMonitor UpdatedObject ${changes.obj.curMediaId} playerStat: $theatres[0].playerStat ${changes.changedFields.joinToString()}")
                        }
                        is InitialObject -> {
                            mPlayer?.curState = changes.obj
                            Logd(TAG, "stateMonitor InitialObject ${changes.obj.curMediaId}")
                        }
                        else -> Logd(TAG, "stateMonitor other changes: $changes")
                    }
                }
            }
        }
    }

    val theatres: List<Theatre> = listOf(Theatre(0), Theatre(1))

    fun startTheatres() {
        timeIt("$TAG start of init")
        CoroutineScope(Dispatchers.IO).launch {
            for (i in 0..1) {
                Logd(TAG, "starting curState for player: ${theatres[i].mPlayer?.playerId}")
                theatres[i].mPlayer?.curState = realm.query(CurrentState::class).query("id == $i").first().find() ?: run {
                    val cs = CurrentState()
                    cs.id = i.toLong()
                    upsertBlk(cs) { }
                }
                if (theatres[i].mPlayer?.curState?.curMediaType != LONG_MINUS_1) {
                    if (theatres[i].mPlayer?.curState?.curMediaType == LONG_PLUS_1) {
                        if (theatres[i].mPlayer?.curState?.curMediaId != 0L) theatres[i].mPlayer?.setAsCurEpisode(episodeById(theatres[i].mPlayer?.curState?.curMediaId?:-1))
                    } else Logpe(TAG, theatres[i].mPlayer?.curEpisode,  "Could not restore EpisodeMedia object from preferences for theatre $i, curMediaType: ${theatres[i].mPlayer?.curState?.curMediaType} ")
                }
                Logd(TAG, "curEpisode from preference: ${theatres[i].mPlayer?.curEpisode?.title}")
                if (theatres[i].mPlayer?.curEpisode != null) {
                    val qes = realm.query(QueueEntry::class).query("episodeId == ${theatres[i].mPlayer?.curEpisode!!.id}").find()
                    if (qes.isNotEmpty()) {
                        realm.query(PlayQueue::class).query("id == ${qes[0].queueId}").first().find()?. let { actQueue = it }
                    }
                }
                theatres[i].curStateMonitor?.cancel()
                theatres[i].curStateMonitor = null
                theatres[i].monitorState()
            }
        }
        timeIt("$TAG end of init")
    }

    fun ensureAController() {
        if (aCtrlFuture == null) {
            val appContext = getAppContext()
            val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            aCtrlFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
            aCtrlFuture?.addListener({ aController = aCtrlFuture!!.get() }, ContextCompat.getMainExecutor(appContext))
        }
    }

    fun releaseAController() {
        aCtrlFuture?.let { future ->
            aController = null
            MediaController.releaseFuture(future)
            aCtrlFuture = null
        }
    }

    fun isCurrentlyPlaying(mPlayer: MediaPlayerBase?, media: Episode?): Boolean {
        return isCurMedia(mPlayer, media) && PlaybackService.isRunning && mPlayer?.isPlaying == true
    }

    fun isCurMedia(mPlayer: MediaPlayerBase?, media: Episode?): Boolean {
        return media != null && mPlayer?.curEpisode?.id == media.id
    }

    fun isCurrentlyPlaying(media: Episode?): Boolean {
        return isCurMedia(media) && PlaybackService.isRunning && (theatres[0].mPlayer?.isPlaying == true || theatres[1].mPlayer?.isPlaying == true)
    }

    fun isCurMedia(media: Episode?): Boolean {
        return media != null && (theatres[0].mPlayer?.curEpisode?.id == media.id || theatres[1].mPlayer?.curEpisode?.id == media.id)
    }

    fun isCurMedia(id: Long): Boolean {
        return (theatres[0].mPlayer?.curEpisode?.id == id || theatres[1].mPlayer?.curEpisode?.id == id)
    }

    fun cleanup() {
        Logd(TAG, "cleanup()")
        for (i in 0..1) {
            if (theatres[i].mPlayer?.curEpisode != null) unsubscribeEpisode(theatres[i].mPlayer?.curEpisode!!, TAG)
            theatres[i].curStateMonitor?.cancel()
            theatres[i].curStateMonitor = null
        }
    }
}