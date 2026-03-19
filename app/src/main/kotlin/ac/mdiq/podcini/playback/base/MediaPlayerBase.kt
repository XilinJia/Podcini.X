package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.PodciniApp.Companion.getApp
import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.SleepTimer.autoEnableFrom
import ac.mdiq.podcini.playback.SleepTimer.autoEnableTo
import ac.mdiq.podcini.playback.SleepTimer.isInTimeRange
import ac.mdiq.podcini.playback.SleepTimer.lastTimerValue
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkUrl
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curTempSpeed
import ac.mdiq.podcini.playback.base.InTheatre.savePlayerStatus
import ac.mdiq.podcini.playback.base.PositionSaver.Companion.positionSaver
import ac.mdiq.podcini.playback.base.SleepManager.Companion.sleepManager
import ac.mdiq.podcini.playback.service.QuickSettingsTileService
import ac.mdiq.podcini.storage.database.allowForAutoDelete
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.deleteMedia
import ac.mdiq.podcini.storage.database.feedsMap
import ac.mdiq.podcini.storage.database.removeFromAllQueues
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.sleepPrefs
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState.Companion.SPEED_USE_GLOBAL
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed.AutoDeleteAction
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.loadChapters
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.sendLocalBroadcast
import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class MediaPlayerBase {
    private var oldStatus: PlayerStatus? = null
    internal var prevMedia: Episode? = null

    internal var autoSkippedFeedMediaId: String? = null

    internal var mediaType: MediaType = MediaType.UNKNOWN
    internal val startWhenPrepared = AtomicBoolean(false)

    var prevPosition: Int = -1

    var isStreaming = false

    var widgetId: String = ""

    init {
        status = PlayerStatus.STOPPED
    }

    open fun getVideoSize(): Pair<Int, Int>? = null

    abstract fun getPlaybackSpeed(): Float

    open fun getDuration(): Int = curEpisode?.duration ?: Episode.INVALID_TIME

    abstract fun getPosition(): Int

    open suspend fun invokeBufferListener() {}

    open fun getAudioTracks(): List<String> = emptyList()

    open fun getSelectedAudioTrack(): Int = -1

    open fun resetMediaPlayer() {}

    open fun createStaticPlayer() {}

    open fun isCasting(): Boolean = false

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected abstract fun setDataSource(media: Episode)

    /**
     * Starts or prepares playback of the specified EpisodeMedia object. If another EpisodeMedia object is already being played, the currently playing
     * episode will be stopped and replaced with the new EpisodeMedia object. If the EpisodeMedia object is already being played, the method will
     * not do anything.
     * Whether playback starts immediately depends on the given parameters. See below for more details.
     *
     * States:
     * The end state depends on the given parameters.
     *
     * If 'prepareImmediately' is set to true, the method will go into PREPARING state and after that into PREPARED state. If
     * 'startWhenPrepared' is set to true, the method will additionally go into PLAYING state.
     *
     * If an unexpected error occurs while loading the EpisodeMedia's metadata or while setting the MediaPlayers data source, the object
     * will enter the ERROR state.
     *
     * This method is executed on an internal executor service.
     *
     * @param playable           The EpisodeMedia object that is supposed to be played. This parameter must not be null.
     * @param streaming             The type of playback. If false, the EpisodeMedia object MUST provide access to a locally available file via
     * getLocalMediaUrl. If true, the EpisodeMedia object MUST provide access to a resource that can be streamed by
     * the Android MediaPlayer via getStreamUrl.
     * @param startWhenPrepared  Sets the 'startWhenPrepared' flag. This flag determines whether playback will start immediately after the
     * episode has been prepared for playback. Setting this flag to true does NOT mean that the episode will be prepared
     * for playback immediately (see 'prepareImmediately' parameter for more details)
     * @param prepareImmediately Set to true if the method should also prepare the episode for playback.
     */
    abstract fun prepareMedia(playable: Episode, streaming: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean = false, doPostPlayback: Boolean = false)

    /**
     * Resumes playback if the PSMP object is in PREPARED or PAUSED state. If the PSMP object is in an invalid state.
     * nothing will happen.
     * This method is executed on an internal executor service.
     */
    abstract fun play()

    /**
     * Saves the current position and pauses playback. Note that, if audiofocus is abandoned, the lockscreen controls will also disapear.
     * This method is executed on an internal executor service.
     * @param reinit is true if service should reinit after pausing if the media file is being streamed
     */
    abstract fun pause(reinit: Boolean)

    /**
     * Prepared media player for playback if the service is in the INITALIZED state.
     * This method is executed on an internal executor service.
     */
    abstract fun prepare()

    /**
     * Resets the media player and moves it into INITIALIZED state.
     * This method is executed on an internal executor service.
     */
    abstract fun reinit()

    /**
     * Seeks to the specified position. If the PSMP object is in an invalid state, this method will do nothing.
     * Invalid time values (< 0) will be ignored.
     * This method is executed on an internal executor service.
     */
    abstract fun seekTo(t: Int)

    /**
     * Seek a specific position from the current position
     * @param delta offset from current position (positive or negative)
     */
    fun seekDelta(delta: Int) {
        val curPosition = getPosition()
        if (curPosition != Episode.INVALID_TIME) seekTo(curPosition + delta)
        else Loge(TAG, "seekDelta getPosition() returned INVALID_TIME in seekDelta")
    }

    abstract fun setPlaybackParams(speed: Float)

    open fun setSkipSilence() {}

    open fun setRepeat(repeat: Boolean) {}

    abstract fun setVolume(volumeLeft: Float, volumeRight: Float, adaptionFactor: Float = 1.0f)

    /**
     * Internal method that handles end of playback.
     * Currently, it has 5 use cases:
     *  * Media playback has completed: call with (true, false, true, true)
     *  * User asks to skip to next episode: call with (false, true, true, true)
     *  * Skipping to next episode due to playback error: call with (false, false, true, true)
     *  * Stopping the media player: call with (false, false, false, true)
     *  * We want to change the media player implementation: call with (false, false, false, false)
     *
     * @param hasEnded         If true, we assume the current media's playback has ended, for
     * purposes of post playback processing.
     * @param wasSkipped       Whether the user chose to skip the episode (by pressing the skip button).
     * @param shouldContinue   If true, the media player should try to load, and possibly play,
     * the next item, based on the user preferences and whether such item exists.
     *
     * @return a Future, just for the purpose of tracking its execution.
     */
    internal abstract fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean = true)

    /**
     * Releases internally used resources. This method should only be called when the object is not used anymore.
     */
    abstract fun shutdown()

//    open fun resetVideoSurface() {
//        Loge(TAG, "Resetting Video Surface unsupported in Remote Media Player")
//    }

    open fun setAudioTrack(track: Int) {}

    fun skip() {
//        in first second of playback, ignoring skip
        if (getPosition() < 1000) return
        endPlayback(hasEnded = false, wasSkipped = !shouldRepeat)
    }

    /**
     * @param currentPosition  current position in a media file in ms
     * @param lastPlayedTime  timestamp when was media paused
     * @return  new rewinded position for playback in milliseconds
     */
    protected fun positionWithRewind(currentPosition: Int, lastPlayedTime: Long): Int {
        if (currentPosition > 0 && lastPlayedTime > 0) {
            val elapsedTime = nowInMillis() - lastPlayedTime
            val rewindTime: Long = when {
                elapsedTime > 1.days.inWholeMilliseconds ->  20.seconds.inWholeMilliseconds
                elapsedTime > 1.hours.inWholeMilliseconds -> 10.seconds.inWholeMilliseconds
                elapsedTime > 1.minutes.inWholeMilliseconds -> 3.seconds.inWholeMilliseconds
                else -> 0L
            }
            val newPosition = currentPosition - rewindTime.toInt()
            return max(newPosition, 0)
        } else return currentPosition
    }

    private fun onPlaybackStart(playable: Episode, position: Int) {
        val delayInterval = if (appPrefs.useAdaptiveProgressUpdate) max(MIN_POSITION_SAVER_INTERVAL, playable.duration/50).toLong() else MIN_POSITION_SAVER_INTERVAL.toLong()
        Logd(TAG, "onPlaybackStart ${playable.title}")
        Logd(TAG, "onPlaybackStart position: $position delayInterval: $delayInterval")
        if (position != Episode.INVALID_TIME) {
            upsertBlk(playable) {
                it.position = position
                it.setPlaybackStart()
            }
        } else {
            // skip intro
            val feed = playable.feed
            val skipIntro = feed?.introSkip ?: 0
            val skipIntroMS = skipIntro * 1000
            if (skipIntro > 0 && playable.position < skipIntroMS) {
                val duration = getDuration()
                if (duration !in 1..skipIntroMS) {
                    Logd(TAG, "onPlaybackStart skipIntro ${playable.getEpisodeTitle()}")
                    seekTo(skipIntroMS)
                    Logt(TAG, getAppContext().getString(R.string.pref_feed_skip_intro_toast, skipIntro))
                }
            }
            upsertBlk(playable) { it.setPlaybackStart() }
        }
        positionSaver?.startPositionSaver(delayInterval)
    }

    protected fun onPlaybackPause(playable: Episode?, position: Int) {
        Logd(TAG, "onPlaybackPause $position ${playable?.title}")
        positionSaver?.cancelPositionSaver()
        persistCurrentPosition(position == Episode.INVALID_TIME || playable == null, playable, position)
        Logd(TAG, "onPlaybackPause start ${playable?.timeSpent}")
        if (playable != null) SynchronizationQueueSink.enqueueEpisodePlayedIfSyncActive(playable, false)
    }

    protected fun onPlaybackEnded(stopPlaying: Boolean) {
        Logd(TAG, "onPlaybackEnded stopPlaying: $stopPlaying")
        curTempSpeed = SPEED_USE_GLOBAL
        if (stopPlaying) positionSaver?.cancelPositionSaver()
    }

    protected fun onPostPlayback(playable: Episode, ended: Boolean, skipped: Boolean, playingNext: Boolean) {
        Logd(TAG, "onPostPlayback(): ended=$ended skipped=$skipped playingNext=$playingNext media=${playable.getEpisodeTitle()} ")
        var item = playable
        val smartMarkAsPlayed = playable.hasAlmostEnded()
        if (!ended && smartMarkAsPlayed) Logd(TAG, "smart mark as played")

        var autoSkipped = false
        if (autoSkippedFeedMediaId != null && autoSkippedFeedMediaId == item.identifyingValue) {
            autoSkippedFeedMediaId = null
            autoSkipped = true
        }
        val completed = ended || smartMarkAsPlayed
        SynchronizationQueueSink.enqueueEpisodePlayedIfSyncActive(playable, completed)

        fun shouldSetPlayed(e: Episode): Boolean {
            return when (e.playState) {
                EpisodeState.FOREVER.code, EpisodeState.PLAYED.code -> false
                EpisodeState.AGAIN.code -> nowInMillis() - e.playStateSetTime >= e.duration
                else -> true
            }
        }
        runOnIOScope {
            if (ended || smartMarkAsPlayed || autoSkipped || (skipped && !appPrefs.skipKeepsEpisode)) {
                Logd(TAG, "onPostPlayback ended: $ended smartMarkAsPlayed: $smartMarkAsPlayed autoSkipped: $autoSkipped skipped: $skipped")
                // only mark the item as played if we're not keeping it anyway
                item = upsert(item) {
                    if (it.playState == EpisodeState.FOREVER.code) it.repeatTime = it.repeatInterval + nowInMillis()
                    if (shouldSetPlayed(it)) it.setPlayState(EpisodeState.PLAYED)
                    upsertDB(it, item.position)
                    it.startTime = 0
                    it.startPosition = if (completed) -1 else it.position
                    if (ended || (skipped && smartMarkAsPlayed)) it.position = 0
                    if (ended || skipped || playingNext) it.playbackCompletionTime = nowInMillis()
                }

                val action = item.feed?.autoDeleteAction
                val shouldAutoDelete = (action == AutoDeleteAction.ALWAYS || (action == AutoDeleteAction.GLOBAL && item.feed != null && allowForAutoDelete(item.feed!!)))
                val isItemdeletable = (!appPrefs.favoriteKeepsEpisode || (item.rating < Rating.GOOD.code && item.playState != EpisodeState.AGAIN.code && item.playState != EpisodeState.FOREVER.code))
                if (shouldAutoDelete && isItemdeletable) {
                    if (!item.fileUrl.isNullOrBlank()) item = deleteMedia(item)
                    if (appPrefs.deleteRemovesFromQueue) removeFromAllQueues(listOf(item))
                } else if (appPrefs.removeFromQueueMarkPlayed) removeFromAllQueues(listOf(item))
            }
        }
    }

    internal fun persistCurrentPosition(fromMediaPlayer: Boolean, playable_: Episode?, position_: Int) {
        var playable = if (curEpisode != null && playable_?.id == curEpisode?.id) curEpisode else playable_
        var position = position_
        val duration_: Int
        if (fromMediaPlayer) {
//            position = (media3Controller?.currentPosition ?: 0).toInt() // testing the controller
            position = getPosition()
            duration_ = getDuration()
            playable = curEpisode
        } else duration_ = playable?.duration ?: Episode.INVALID_TIME

        if (position != Episode.INVALID_TIME && duration_ != Episode.INVALID_TIME && playable != null) {
            Logd(TAG, "persistCurrentPosition to position: $position duration: $duration_ ${playable.getEpisodeTitle()}")
            upsertBlk(playable) { upsertDB(it, position) }
            prevPosition = position
        }
    }

    private fun upsertDB(it: Episode, position: Int) {
        it.position = position

        if (it.startPosition >= 0 && it.position > it.startPosition) it.playedDuration = (it.playedDurationWhenStarted + it.position - it.startPosition)
        if (it.startTime > 0) {
            var delta = (nowInMillis() - it.startTime)
            if (delta > 3 * max(it.playedDuration, 60000)) {
                Logt(TAG, "upsertDB likely invalid delta: $delta ${it.title}")
                it.startTime = nowInMillis()
                delta = 0L
            }
            else it.timeSpent = it.timeSpentOnStart + delta
        }

        it.lastPlayedTime = (System.currentTimeMillis())
        if (it.playState == EpisodeState.NEW.code) it.setPlayState(EpisodeState.UNPLAYED)
        Logd(TAG, "upsertDB ${it.startTime} timeSpent: ${it.timeSpent} playedDuration: ${it.playedDuration}")
    }

    /**
     * Starts a new thread that loads the chapter marks from a playable object. If another chapter loader is already active,
     * it will be cancelled first.
     * On completion, the callback's onChapterLoaded method will be called.
     */
    @Synchronized
    private fun startChapterLoader(media: Episode) {
        // TODO: what to do?
        fun onChapterLoaded(media: Episode?) {
            //            sendNotificationBroadcast(NOTIFICATION_TYPE_RELOAD, 0)
        }
        runOnIOScope {
            try {
                loadChapters(media, false)
                withContext(Dispatchers.Main) { onChapterLoaded(media) }
            } catch (e: Throwable) { Logs(TAG, e, "Error loading chapters:") }
        }
    }

    /**
     * Sets the player status of the PSMP object. PlayerStatus and media attributes have to be set at the same time
     * so that getPSMPInfo can't return an invalid state (e.g. status is PLAYING, but media is null).
     * This method will notify the callback about the change of the player status (even if the new status is the same
     * as the old one).
     * It will also call [onPlaybackPause] or [onPlaybackStart]
     * depending on the status change.
     * @param newStatus The new PlayerStatus. This must not be null.
     * @param media  The new playable object of the PSMP object. This can be null.
     * @param position  The position to be set to the current EpisodeMedia object in case playback started or paused.
     * Will be ignored if given the value of [Episode.INVALID_TIME].
     */
    // TODO: this routine can be very problematic!!!
    @Synchronized
    protected fun setPlayerStatus(newStatus: PlayerStatus, media: Episode?, position: Int = Episode.INVALID_TIME) {
        Logd(TAG, "setPlayerStatus: Setting player status from $status to $newStatus ${media?.id} == ${prevMedia?.id}")
        if (status == newStatus && media != null && media.id == prevMedia?.id) return
//        showStackTrace()
        this.oldStatus = status
        status = newStatus
        if (media != null) {
            if (!isUnknown) {
                when {
                    oldStatus == PlayerStatus.PLAYING && !isPlaying && media.id == prevMedia?.id -> onPlaybackPause(media, position)
                    oldStatus != PlayerStatus.PLAYING && isPlaying -> onPlaybackStart(media, position)
                    else -> Logd(TAG, "setPlayerStatus case else, isPlaying: $isPlaying ${media.id == prevMedia?.id} not handled")
                }
            }
        }

        currentMediaType = mediaType
        Logd(TAG, "setPlayerStatus $status")
        when {
            isInitialized -> savePlayerStatus(curEpisode, status)
            isPrepared -> {
                savePlayerStatus(curEpisode, status)
                if (curEpisode != null) startChapterLoader(curEpisode!!)
            }
            isPaused -> savePlayerStatus(status)
            isStopped -> {}
            isPlaying -> {
                savePlayerStatus(status)
                persistCurrentPosition(true, null, Episode.INVALID_TIME)
                // set sleep timer if auto-enabled
                var autoEnableByTime = true
                val fromSetting = autoEnableFrom
                val toSetting = autoEnableTo
                if (fromSetting != toSetting) {
                    val now: Calendar = GregorianCalendar()
                    now.timeInMillis = nowInMillis()
                    val currentHour = now[Calendar.HOUR_OF_DAY]
                    autoEnableByTime = isInTimeRange(fromSetting, toSetting, currentHour)
                }
                if (oldStatus != null && sleepPrefs.AutoEnable && autoEnableByTime && sleepManager?.isSleepTimerActive != true) {
                    sleepManager?.setSleepTimer(lastTimerValue.minutes.inWholeMilliseconds)
                    // TODO: what to do?
//                    EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.sleep_timer_enabled_label), { taskManager?.disableSleepTimer() }, context.getString(R.string.undo)))
                }
            }
            isError -> {
                savePlayerStatus(null)
                pause(reinit = false)
            }
            else -> {}
        }
        val context = getAppContext()
        TileService.requestListeningState(context, ComponentName(context, QuickSettingsTileService::class.java))
        sendLocalBroadcast(ACTION_PLAYER_STATUS_CHANGED)
        bluetoothNotifyChange(AVRCP_ACTION_PLAYER_STATUS_CHANGED)
        bluetoothNotifyChange(AVRCP_ACTION_META_CHANGED)
    }

    private fun bluetoothNotifyChange(whatChanged: String) {
        Logd(TAG, "bluetoothNotifyChange $whatChanged")
        if (curEpisode != null) {
            val i = Intent(whatChanged)
            i.putExtra("id", 1L)
            i.putExtra("artist", "")
            i.putExtra("album", curEpisode!!.feed?.title?:"")
            i.putExtra("track", curEpisode!!.getEpisodeTitle())
            i.putExtra("playing", isPlaying)
            i.putExtra("duration", curEpisode!!.duration.toLong())
            i.putExtra("position", curEpisode!!.position.toLong())
            getAppContext().sendBroadcast(i)
        }
    }

    companion object {
        private val TAG: String = MediaPlayerBase::class.simpleName ?: "Anonymous"

        internal var mPlayer: MediaPlayerBase? = null
        var shouldRepeat by mutableStateOf(false)

        var currentMediaType: MediaType? = MediaType.UNKNOWN

        @get:Synchronized
        var status by mutableStateOf(PlayerStatus.STOPPED)

        private const val MIN_POSITION_SAVER_INTERVAL: Int = 5000   // in millisoconds

        private const val AVRCP_ACTION_PLAYER_STATUS_CHANGED = "com.android.music.playstatechanged"
        private const val AVRCP_ACTION_META_CHANGED = "com.android.music.metachanged"

        const val ACTION_PLAYER_STATUS_CHANGED: String = "action.ac.mdiq.podcini.service.playerStatusChanged"

        internal var normalSpeed = 1.0f
        internal var isSpeedForward = false
        internal var isFallbackSpeed = false

        val curPBSpeed: Float
            get() = mPlayer?.getPlaybackSpeed() ?: prefSpeedOf(curEpisode)

        private var isStartWhenPrepared: Boolean
            get() = mPlayer?.startWhenPrepared?.get() == true
            set(s) {
                mPlayer?.startWhenPrepared?.set(s)
            }

        fun prefSpeedOf(media: Episode?): Float {
            var playbackSpeed = SPEED_USE_GLOBAL
            if (media != null) {
                playbackSpeed = curTempSpeed
                if (playbackSpeed == SPEED_USE_GLOBAL && media.feedId != null && feedsMap.containsKey(media.feedId!!)) playbackSpeed = feedsMap[media.feedId!!]!!.playSpeed
            }
            if (playbackSpeed == SPEED_USE_GLOBAL) playbackSpeed = appPrefs.playbackSpeed
            return playbackSpeed
        }

        fun playPause() {
            Logd(TAG, "playPause status: $status")
            when {
                isPlaying -> {
                    mPlayer?.pause(reinit = false)
                    isSpeedForward =  false
                    isFallbackSpeed = false
                    if (curEpisode != null) upsertBlk(curEpisode!!) { it.forceVideo = false }
                }
                isPaused || isPrepared -> {
                    mPlayer?.play()
                    sleepManager?.restartSleepTimer()
                }
                isPreparing -> isStartWhenPrepared = !isStartWhenPrepared
                isInitialized -> {
                    isStartWhenPrepared = true
                    mPlayer?.prepare()
                    sleepManager?.restartSleepTimer()
                }
                else -> Loge(TAG, "Play/Pause button was pressed and PlaybackService state was unknown: $status")
            }
        }

        fun isStreamingCapable(media: Episode): Boolean {
//            showStackTrace()
            if (!isNetworkUrl(media.downloadUrl)) {
                Loge(TAG, "streaming media without a remote downloadUrl: ${media.downloadUrl}. Abort")
                return false
            }
            if (!getApp().networkMonitor.isConnected) {
                Loge(TAG, "streaming media but network is not available, abort")
                return false
            }
            return true
        }

        val isPlaying: Boolean
            get() = status == PlayerStatus.PLAYING
        val isPaused: Boolean
            get() = status == PlayerStatus.PAUSED
        val isPrepared: Boolean
            get() = status == PlayerStatus.PREPARED
        val isPreparing: Boolean
            get() = status == PlayerStatus.PREPARING
        val isInitialized: Boolean
            get() = status == PlayerStatus.INITIALIZED
        val isStopped: Boolean
            get() = status == PlayerStatus.STOPPED
        val isUnknown: Boolean
            get() = status == PlayerStatus.INDETERMINATE
        val isError: Boolean
            get() = status == PlayerStatus.ERROR
    }
}
