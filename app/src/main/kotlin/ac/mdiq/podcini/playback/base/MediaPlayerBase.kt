package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkUrl
import ac.mdiq.podcini.net.utils.NetworkUtils.networkMonitor
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.isCurMedia
import ac.mdiq.podcini.playback.base.SleepManager.Companion.autoEnableFrom
import ac.mdiq.podcini.playback.base.SleepManager.Companion.autoEnableTo
import ac.mdiq.podcini.playback.base.SleepManager.Companion.isInTimeRange
import ac.mdiq.podcini.playback.base.SleepManager.Companion.lastTimerValue
import ac.mdiq.podcini.playback.base.SleepManager.Companion.sleepManager
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.episodeChangedWhenScreenOff
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isAutoController
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.storage.database.MonitorEntity
import ac.mdiq.podcini.storage.database.allowForAutoDelete
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.checkAndMarkDuplicates
import ac.mdiq.podcini.storage.database.curIndexInActQueue
import ac.mdiq.podcini.storage.database.deleteMedia
import ac.mdiq.podcini.storage.database.episodeById
import ac.mdiq.podcini.storage.database.feedsMap
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.removeFromAllQueues
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.sleepPrefs
import ac.mdiq.podcini.storage.database.subscribeEpisode
import ac.mdiq.podcini.storage.database.unsubscribeEpisode
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState
import ac.mdiq.podcini.storage.model.CurrentState.Companion.LONG_MINUS_1
import ac.mdiq.podcini.storage.model.CurrentState.Companion.LONG_PLUS_1
import ac.mdiq.podcini.storage.model.CurrentState.Companion.SPEED_USE_GLOBAL
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed.AutoDeleteAction
import ac.mdiq.podcini.storage.model.QueueEntry
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.specs.VideoMode
import ac.mdiq.podcini.storage.utils.loadChapters
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.ui.screens.curVideoMode
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logpe
import ac.mdiq.podcini.utils.Logps
import ac.mdiq.podcini.utils.Logpt
import ac.mdiq.podcini.utils.Logt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class MediaPlayerBase {
    val context = getAppContext()

    var playerId: Int = -1

    var castPlayer: Player? = null

    private var oldStatus: PlayerStatus? = null

    @get:Synchronized
    var status by mutableStateOf(PlayerStatus.STOPPED)

    var statusSimple by mutableStateOf(PlayerStatusSimple.OTHER)

    var curState by mutableStateOf(CurrentState())

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

    private var normalSpeed = 1.0f
    var isSpeedForward = false
    var isFallbackSpeed = false

    open val audioTracks: List<String> = listOf()

    private var autoSkippedFeedMediaId: String? = null

    private  var mediaType: MediaType = MediaType.UNKNOWN

    private val startWhenPrepared = atomic(false)
    internal var isStartWhenPrepared: Boolean
        get() = startWhenPrepared.value
        set(s) {
            startWhenPrepared.value = s
        }


    var isStreaming = false

    var widgetId: String = ""

    private var prevPosition: Int = -1

    var curSpeed by mutableFloatStateOf(SPEED_USE_GLOBAL)
    var curPBSpeed by mutableFloatStateOf(1f)
    var curPitch: Float = SPEED_USE_GLOBAL

    internal var prevMedia: Episode? = null
    var curEpisode by mutableStateOf<Episode?>(null)
    var currentMediaType: MediaType? = MediaType.UNKNOWN

    var playingVideo by mutableStateOf(false)

//    internal var videoSize: Pair<Int, Int>? = null
//    open val videoWidth: Int = 0
//    open val videoHeight: Int = 0

    var skipSilence: Boolean? = null
    var bitrate by mutableIntStateOf(0)
    var isStereo by mutableStateOf(true)

    var shouldRepeat by mutableStateOf(false)

    val isPlayingVideoLocally: Boolean
        get() = when {
            PlaybackService.isCasting -> false
            playbackService != null -> currentMediaType == MediaType.VIDEO
            else -> curEpisode?.getMediaType() == MediaType.VIDEO
        }

    init {
        status = PlayerStatus.STOPPED
    }

    fun prefSpeedOf(media: Episode?): Pair<Float, Float> {
        var speed = SPEED_USE_GLOBAL
        if (media != null) {
            speed = curSpeed
            if (speed == SPEED_USE_GLOBAL && media.feedId != null && feedsMap.containsKey(media.feedId!!)) speed = feedsMap[media.feedId!!]!!.playSpeed
        }
        if (speed == SPEED_USE_GLOBAL) speed = appPrefs.playbackSpeed

        var pitch = SPEED_USE_GLOBAL
        if (media != null) {
            pitch = curPitch
            if (pitch == SPEED_USE_GLOBAL && media.feedId != null && feedsMap.containsKey(media.feedId!!)) pitch = feedsMap[media.feedId!!]!!.playPitch
        }
        if (pitch == SPEED_USE_GLOBAL) pitch = appPrefs.playbackPitch
        return Pair(speed, pitch)
    }

    fun toggleFallbackSpeed(speed: Float) {
        if (isSpeedForward) return
        if (isPlaying) {
            if (!isFallbackSpeed) {
                normalSpeed = getPlaybackSpeed()
                setPlaybackParams(speed)
            } else setPlaybackParams(normalSpeed)
            isFallbackSpeed = !isFallbackSpeed
        }
    }

    fun speedForward(speed: Float) {
        if (isFallbackSpeed) return
        if (!isSpeedForward) {
            normalSpeed = getPlaybackSpeed()
            setPlaybackParams(speed)
        } else setPlaybackParams(normalSpeed)
        isSpeedForward = !isSpeedForward
    }

    fun setAsCurEpisode(episode: Episode?, force: Boolean = false) {
        Logd(TAG, "setAsCurEpisode episode: ${episode?.title}")
                //        showStackTrace()
        if (episode != null && episode.id == curEpisode?.id && !force) return
        if (curEpisode != null) unsubscribeEpisode(curEpisode!!, TAG)
        val episode_ = if (episode != null) episodeById(episode.id) else null
        when {
            episode_ != null -> {
                curEpisode = episode_
                playingVideo = (episode_.forceVideo || (episode_.feed?.videoModePolicy != VideoMode.AUDIO_ONLY && appPrefs.videoPlaybackMode != VideoMode.AUDIO_ONLY.code && curVideoMode != VideoMode.AUDIO_ONLY && episode_.getMediaType() == MediaType.VIDEO))
                skipSilence = null
                shouldRepeat = false
                curSpeed = SPEED_USE_GLOBAL
                Logd(TAG, "setAsCurEpisode start monitoring curEpisode ${curEpisode?.title}")
                runOnIOScope {
                    if (!actQueue.contains(curEpisode!!)) {
                        val qes = realm.query(QueueEntry::class).query("episodeId == ${curEpisode!!.id}").find()
                        if (qes.isNotEmpty()) {
                            val q = queuesLive.find { it.id == qes[0].queueId }
                            if (q != null) actQueue = q
                        }
                    }
                    subscribeEpisode(curEpisode!!,
                        MonitorEntity(TAG, onInit = {  },
                            onChanges = { e, f ->
                                if (e.id == curEpisode?.id) {
                                    curEpisode = e
                                    Logd(TAG, "setAsCurEpisode updating curEpisode [${curEpisode?.title}] ${f.joinToString()}")
                                }
                            }
                        ))
                }
            }
            else -> {
                curEpisode = null
                savePlayerStatus(null, null)
            }
        }
    }

    fun savePlayerStatus(episode: Episode?, playerStatus: PlayerStatus?) {
        Logd(TAG, "Writing playback preferences ${episode?.id}")
        runOnIOScope {
            when {
                episode == null && playerStatus != null -> {
                    statusSimple = playerStatus.toStatusInt()
//                    upsert(curState) { it.curPlayerStatus = playerStatus.getAsInt() }
                }
                episode == null || playerStatus == null -> {
                    statusSimple = PlayerStatusSimple.OTHER
                    upsert(curState) {
                        it.curMediaType = LONG_MINUS_1
                        it.curFeedId = LONG_MINUS_1
                        it.curMediaId = LONG_MINUS_1
//                        it.curPlayerStatus = PlayerStatusInt.OTHER.code
                    }
                }
                else -> {
                    statusSimple = playerStatus.toStatusInt()
                    upsert(curState) {
//                        it.curPlayerStatus = playerStatus.getAsInt()
                        it.curMediaType = LONG_PLUS_1
                        it.curIsVideo = episode.getMediaType() == MediaType.VIDEO
                        val feedId = episode.feed?.id
                        if (feedId != null) it.curFeedId = feedId
                        it.curMediaId = episode.id
                    }
                }
            }
        }
    }

    private fun getNextInQueue(): Episode? {
        Logd(TAG, "getNextInQueue called curEpisode: ${curEpisode?.getEpisodeTitle()}")
        if (!actQueue.playInSequence) {
            Logd(TAG, "getNextInQueue(), but follow queue is not enabled.")
            savePlayerStatus(null, null)
            return null
        }
        val qes = actQueue.entries
        if (qes.isEmpty()) {
            Logd(TAG, "getNextInQueue queue is empty")
            savePlayerStatus(null, null)
            return null
        }
        var curIndex = qes.indexOfFirst { isCurMedia(it.episodeId) }
        if (curIndex < 0 && curIndexInActQueue >= 0) {
            curIndex = curIndexInActQueue
            curIndexInActQueue = -1
        }
        Logd(TAG, "getNextInQueue curIndexInQueue: $curIndex ${qes.size}")
        val nextQE = if (curIndex >= 0 && curIndex < qes.size) {
            when {
                !isCurMedia(qes[curIndex].episodeId) -> qes[curIndex]
                qes.size == 1 -> return null
                else -> {
                    var j = if (curIndex < qes.size - 1) curIndex + 1 else 0
                    val start = j
                    while (isCurMedia(qes[j].episodeId)) {
                        j = if (j < qes.size - 1) j + 1 else 0
                        if (j == start) break
                    }
                    qes[j]
                }
            }
        } else qes[0]
        if (isCurMedia(nextQE.episodeId)) return null
        var nextItem = episodeById(nextQE.episodeId) ?: return null
        Logd(TAG, "getNextInQueue nextItem ${nextItem.title}")
        nextItem = checkAndMarkDuplicates(nextItem)
        episodeChangedWhenScreenOff = true
        return nextItem
    }

    fun startPlaying() {
        Logd(TAG, "startPlaying called")
        if (curEpisode == null) {
            Logpt(TAG, curEpisode, "startPlaying: No media to play")
            return
        }
        val media = curEpisode!!
        val needStreaming = media.feed?.isLocalFeed != true && media.fileUrl.isNullOrBlank()
        if (needStreaming && !isStreamingCapable(media)) return
        prepareMedia(playable = media, streaming = needStreaming, startWhenPrepared = true, prepareImmediately = true, forceReset = true, doPostPlayback = false)
    }

    fun onSleepTimerUpdate(event: FlowEvent.SleepTimerUpdatedEvent) {
        when {
            event.isOver -> {
                Logd(TAG, "sleep timer is over")
                pause(reinit = false)
                setVolume(1.0f, 1.0f)
            }
            event.getTimeLeft() < SleepManager.SLEEP_TIMER_ENDING_THRESHOLD -> {
                val multiplicators = floatArrayOf(0.1f, 0.1f, 0.2f, 0.2f, 0.3f, 0.3f, 0.4f, 0.4f, 0.5f, 0.5f, 0.6f, 0.6f, 0.7f, 0.7f, 0.8f, 0.8f, 0.9f, 0.9f)
                val multiplicator = multiplicators[min(multiplicators.size-1, (event.getTimeLeft().toInt() / 1000))]
                Logd(TAG, "onSleepTimerAlmostExpired: $multiplicator")
                setVolume(multiplicator, multiplicator)
            }
            event.isCancelled -> setVolume(1.0f, 1.0f)
        }
    }

    fun onBufferUpdate(event: FlowEvent.BufferUpdateEvent) {
        if (event.episode.id != curEpisode?.id) return
        if (event.hasEnded() && curEpisode != null && curEpisode!!.duration <= 0 && getDuration() > 0) upsertBlk(curEpisode!!) { it.duration = getDuration() }
    }

    fun onEpisodeMediaEvent(event: FlowEvent.EpisodeMediaEvent) {
        if (event.action == FlowEvent.EpisodeMediaEvent.Action.REMOVED) {
            for (e in event.episodes) {
                if (e.id == curEpisode?.id) {
                    setAsCurEpisode(e)
                    endPlayback(hasEnded = false, wasSkipped = true)
                    break
                }
            }
        }
    }

    private var positionSaverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Synchronized
    private fun startPositionSaver(delayInterval: Long) {
        cancelPositionSaver()
        positionSaverJob = scope.launch {
            while (isActive) {
                delay(delayInterval)
                val curPosition = getPosition()
                val curDuration = getDuration()
                Logd(TAG, "positionSaverTick currentPosition: $curPosition")
                if (curPosition != prevPosition) {
                    // skip ending
                    val remainingTime = curDuration - curPosition
                    val item = curEpisode ?: continue
                    val skipEnd = item.feed?.endingSkip?:0
                    val skipEndMS = skipEnd * 1000
                    //                  Logd(TAG, "skipEndingIfNecessary: checking " + remainingTime + " " + skipEndMS + " speed " + currentPlaybackSpeed)
                    if (skipEnd > 0 && skipEndMS < curDuration && (remainingTime - skipEndMS < 0)) {
                        Logd(TAG, "skipEndingIfNecessary: Skipping the remaining $remainingTime $skipEndMS")
                        Logt(TAG, getAppContext().getString(R.string.pref_feed_skip_ending_toast, skipEnd))
                        autoSkippedFeedMediaId = item.identifyingValue
                        skip()
                    }
                    persistCurrentPosition(true, null, Episode.INVALID_TIME)
                    prevPosition = curPosition
                }
                invokeBufferListener()
            }
        }
        Logd(TAG, "Started PositionSaver with interval: $delayInterval")
    }

    @Synchronized
    private fun cancelPositionSaver() {
        Logd(TAG, "canelling PositionSaver")
        positionSaverJob?.cancel()
        positionSaverJob = null
    }

    abstract fun getPlaybackSpeed(): Float

    abstract fun setDuration()

    fun getDuration(): Int = curEpisode?.duration ?: Episode.INVALID_TIME

    abstract fun getPlayerPosition(): Int

    fun getPosition(): Int {
        var retVal = Episode.INVALID_TIME
        //        showStackTrace()
        if (castPlayer?.isPlaying == true && !status.isAtLeast(PlayerStatus.PREPARED)) Logpt(TAG, curEpisode, "exoPlayer playbackState ${castPlayer?.playbackState} player status $status")
        retVal = getPlayerPosition()
//        Logd(TAG, "getPosition player position: $retVal")
        if (retVal <= 0 && curEpisode != null) retVal = curEpisode!!.position
//        Logd(TAG, "getPosition final position: $retVal")
        return retVal
    }

    open suspend fun invokeBufferListener() {}

    open fun getSelectedAudioTrack(): Int = -1

    open fun resetMediaPlayer() {}

    open fun createNativePlayer() {}

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected abstract fun prepareDataSource(media: Episode)

    protected abstract fun prepareDataSource(media: Episode, mediaUrl: String, user: String?, password: String?)

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
    fun prepareMedia(playable: Episode, streaming: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean = false, doPostPlayback: Boolean = true) {
        Logd(TAG, "prepareMedia status=$status stream=$streaming startWhenPrepared=$startWhenPrepared prepareImmediately=$prepareImmediately forceReset=$forceReset ${playable.getEpisodeTitle()} ")
        //       showStackTrace()
        if (!forceReset && playable.id == prevMedia?.id && isPlaying) {
            Logd(TAG, "prepareMedia Method call was ignored: media file already playing.")
            return
        }

        if (curEpisode != null) {
            prevMedia = curEpisode
            if (doPostPlayback) {
                Logd(TAG, "prepareMedia: curEpisode exist status=$status")
                Logd(TAG, "prepareMedia starts new playable:${playable.id} curEpisode:${curEpisode!!.id} prevMedia:${prevMedia?.id}")
                // set temporarily to pause in order to update list with current position
                if (isPlaying) onPlaybackPause(curEpisode, curEpisode?.position ?: -1)
                // stop playback of this episode
                if (isPaused || isPlaying || isPrepared) castPlayer?.stop()
                if (curEpisode?.id != playable.id) onPostPlayback(curEpisode!!, ended = false, skipped = true, true)
                setPlayerStatus(PlayerStatus.INDETERMINATE, null)
            }
        }

        Logd(TAG, "prepareMedia preparing for playable:${playable.id} ${playable.getEpisodeTitle()}")
        if (playable.playState < EpisodeState.PROGRESS.code) runOnIOScope { upsert(playable) { it.setPlayState(EpisodeState.PROGRESS) } }
        setAsCurEpisode(playable)

        this.isStreaming = streaming
        mediaType = curEpisode!!.getMediaType()
//        videoSize = null
        resetMediaPlayer()

        isStartWhenPrepared = startWhenPrepared
        prefSpeedOf(curEpisode).let { (sp, pi)-> setPlaybackParams(sp, pi) }
        setRepeat(shouldRepeat)
        setSkipSilence()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                when {
                    streaming -> {
                        Logd(TAG, "prepareMedia streamurl: ${curEpisode?.downloadUrl}")
                        if (!curEpisode?.downloadUrl.isNullOrBlank()) prepareDataSource(curEpisode!!)
                        else throw IOException("episode downloadUrl is empty ${curEpisode?.title}")
                    }
                    else -> {   // TODO: playing video often gets here??
                        Logd(TAG, "prepareMedia localMediaurl: ${curEpisode?.fileUrl}")
                        if (!curEpisode?.fileUrl.isNullOrBlank()) prepareDataSource(curEpisode!!, curEpisode!!.fileUrl!!, null, null)
                        else throw IOException("Unable to read local file ${curEpisode?.fileUrl}")
                    }
                }
                withContext(Dispatchers.Main) {
//                    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
//                    if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_CAR) setPlayerStatus(PlayerStatus.INITIALIZED, curEpisode)
                    if (!isAutoController) setPlayerStatus(PlayerStatus.INITIALIZED, curEpisode)
                    if (prepareImmediately) prepare()
                }
            } catch (e: IOException) {
                Logps(TAG, curEpisode, e, "prepareMedia failed ${e.localizedMessage ?: ""}")
                withContext(Dispatchers.Main) { setPlayerStatus(PlayerStatus.ERROR, curEpisode) }
            } catch (e: IllegalStateException) {
                Logps(TAG, curEpisode, e, "prepareMedia failed ${e.localizedMessage ?: ""}")
                withContext(Dispatchers.Main) { setPlayerStatus(PlayerStatus.ERROR, curEpisode) }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { setPlayerStatus(PlayerStatus.ERROR, curEpisode) }
                Logps(TAG, curEpisode, e, "setDataSource error: [${e.localizedMessage}]")
            } finally { }
        }
    }

    open fun shouldSetSource(): Boolean = true

    fun playPause() {
        Logd(TAG, "playPause status: $status")
        when {
            isPlaying -> pause(reinit = false)
            isPaused || isPrepared -> play()
            isPreparing -> isStartWhenPrepared = !isStartWhenPrepared
            isInitialized -> {
                isStartWhenPrepared = true
                prepare()
            }
            else -> Logpe(TAG, curEpisode, "Play/Pause button was pressed and PlaybackService state was unknown: $status")
        }
    }

    /**
     * Resumes playback if the PSMP object is in PREPARED or PAUSED state. If the PSMP object is in an invalid state.
     * nothing will happen.
     * This method is executed on an internal executor service.
     */
    fun play() {
        Logd(TAG, "play(): status: $status playbackState: ${castPlayer?.playbackState}")
        if (isPaused || isPrepared) {
            Logd(TAG, "play() Resuming/Starting playback")
            if (shouldSetSource()) setSource()
            val volAdpFac = if (curEpisode != null) curEpisode!!.feed?.volumeAdaptionSetting?.adaptionFactor ?: 1f else 1f
            setVolume(1.0f, 1.0f, volAdpFac)
            Logd(TAG, "play(): position: ${curEpisode?.position}")
            castPlayer?.play()
            setPlaybackParams()
            setPlayerStatus(PlayerStatus.PLAYING, curEpisode)
            sleepManager?.restart()
        } else Logpt(TAG, curEpisode, "Call to play() was ignored because current state of PSMP object is $status")
    }

    /**
     * Saves the current position and pauses playback. Note that, if audiofocus is abandoned, the lockscreen controls will also disapear.
     * This method is executed on an internal executor service.
     * @param reinit is true if service should reinit after pausing if the media file is being streamed
     */
    fun pause(reinit: Boolean) {
        if (isPlaying || isError) {
            Logd(TAG, "Pausing playback $reinit")
            castPlayer?.pause()
            setPlayerStatus(PlayerStatus.PAUSED, curEpisode, getPosition())
            if (isStreaming && reinit) reinit()
            isSpeedForward =  false
            isFallbackSpeed = false
            if (curEpisode != null) upsertBlk(curEpisode!!) { it.forceVideo = false }
        } else Logpt(TAG, curEpisode, "Ignoring call to pause: Player is in $status state")
    }

    internal abstract fun setSource()

    /**
     * Prepared media player for playback if the service is in the INITALIZED state.
     * This method is executed on an internal executor service.
     */
    fun prepare() {
        Logd(TAG, "prepare Preparing media player: status: $status")
        if (isInitialized) {
            setPlayerStatus(PlayerStatus.PREPARING, curEpisode)
            setSource()
//            if (mediaType == MediaType.VIDEO) videoSize = Pair(videoWidth, videoHeight)
            if (curEpisode != null && curEpisode!!.duration <= 0) setDuration()
            setPlayerStatus(PlayerStatus.PREPARED, curEpisode)
            if (isStartWhenPrepared) play()
        } else Logt(TAG, "prepare() call ignored with status: $status")
    }

    /**
     * Resets the media player and moves it into INITIALIZED state.
     * This method is executed on an internal executor service.
     */
    fun reinit() {
        Logd(TAG, "reinit() called")
        when {
            curEpisode != null -> prepareMedia(playable = curEpisode!!, streaming = isStreaming, startWhenPrepared = isStartWhenPrepared, prepareImmediately = false, forceReset = true, doPostPlayback = true)
            else -> Logd(TAG, "Call to reinit: media and mediaPlayer were null, ignored")
        }
    }

    /**
     * Seeks to the specified position. If the PSMP object is in an invalid state, this method will do nothing.
     * Invalid time values (< 0) will be ignored.
     * This method is executed on an internal executor service.
     */
    fun seekTo(t_: Int) {
        var t = t_
        if (t < 0) t = 0
        Logd(TAG, "seekTo() called $t status: $status")

        when  {
            isPlaying || isPaused || isPrepared -> {
                Logd(TAG, "seekTo t: $t status: $status")
                castPlayer?.seekTo(t.toLong())
                if (curEpisode != null) upsertBlk(curEpisode!!) { it.position = t }
            }
            isInitialized -> {
                if (curEpisode != null) upsertBlk(curEpisode!!) { it.position = t }
                isStartWhenPrepared = false
                prepare()
            }
            else -> {}
        }
    }

    /**
     * Seek a specific position from the current position
     * @param delta offset from current position (positive or negative)
     */
    fun seekDelta(delta: Int) {
        val curPosition = getPosition()
        if (curPosition != Episode.INVALID_TIME) seekTo(curPosition + delta)
        else Logpe(TAG,  curEpisode, "seekDelta getPosition() returned INVALID_TIME in seekDelta")
    }

    abstract fun setPlaybackParams()

    abstract fun setPlaybackParams(speed: Float, pitch: Float = 0f)

    open fun setSkipSilence() {}

    fun setRepeat(repeat: Boolean) {
        castPlayer?.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    abstract fun setVolume(volumeLeft: Float, volumeRight: Float, adaptionFactor: Float = 1.0f)

    internal abstract fun playChime()

    internal abstract fun notifyWidget()

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
     */
    internal fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean = true) {
//        showStackTrace()
        if (curEpisode == null) {
            Logd(TAG, "endPlayback curEpisode is null, return")
            return
        }
        // we're relying on the position stored in the EpisodeMedia object for post-playback processing
        val position = getPosition()
        if (position >= 0) upsertBlk(curEpisode!!) { it.position = position }
        Logd(TAG, "endPlayback hasEnded=$hasEnded wasSkipped=$wasSkipped shouldContinue=$shouldContinue ${curEpisode?.title}")

        fun stopPlayer() {
            Logd(TAG, "endPlayback stopPlayer is called")
            curSpeed = SPEED_USE_GLOBAL
            cancelPositionSaver()
            setAsCurEpisode(null)
            castPlayer?.stop()
            if (isUnknown) setPlayerStatus(PlayerStatus.STOPPED, null)
            else Logd(TAG, "endPlayback Ignored call to stop: Current player state is: $status")
        }
        val currentMedia = curEpisode
        when {
            shouldContinue -> {
                // Load next episode if previous episode was in the queue and if there is an episode in the queue left.
                // Start playback immediately if continuous playback is enabled
                val nextMedia = getNextInQueue()
                if (nextMedia == null) {
                    Logd(TAG, "endPlayback nextMedia is null.")
                    stopPlayer()
                } else {
                    Logd(TAG, "endPlayback has nextMedia. status: $status ${nextMedia.title}")
                    val wasPlayng = isPlaying
                    if (wasSkipped) setPlayerStatus(PlayerStatus.INDETERMINATE, null)
                    curSpeed = SPEED_USE_GLOBAL
                    cancelPositionSaver()
                    Logd(TAG, "endPlayback useRingTone: ${appPrefs.useRingTone} ringToneUriString: ${appPrefs.ringToneUriString}")
                    if (appPrefs.useRingTone && !appPrefs.ringToneUriString.isNullOrBlank()) playChime()
                    val needStreaming = (nextMedia.feed?.isLocalFeed != true && nextMedia.fileUrl.isNullOrBlank())
                    if (needStreaming) {
                        if (!isStreamingCapable(nextMedia)) {
                            if (currentMedia != null) onPostPlayback(currentMedia, hasEnded, wasSkipped, false)
                            return
                        }
                    }
                    prepareMedia(playable = nextMedia, streaming = needStreaming, startWhenPrepared = wasPlayng, prepareImmediately = wasPlayng)
                    if (widgetId.isNotEmpty()) notifyWidget()
                }
                if (currentMedia != null) onPostPlayback(currentMedia, hasEnded, wasSkipped, nextMedia != null)
            }
            isPlaying -> {
                Logd(TAG, "endPlayback isPlaying")
                onPlaybackPause(currentMedia, currentMedia?.position?: 0)
            }
            else -> {
                Logd(TAG, "endPlayback else")
                stopPlayer()
            }
        }
    }

    /**
     * Releases internally used resources. This method should only be called when the object is not used anymore.
     */
    abstract fun shutdown()

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
                    Logpt(TAG, curEpisode, context.getString(R.string.pref_feed_skip_intro_toast, skipIntro))
                }
            }
            upsertBlk(playable) { it.setPlaybackStart() }
        }
        startPositionSaver(delayInterval)
    }

    protected fun onPlaybackPause(playable: Episode?, position: Int) {
        Logd(TAG, "onPlaybackPause $position ${playable?.title}")
        cancelPositionSaver()
        persistCurrentPosition(position == Episode.INVALID_TIME || playable == null, playable, position)
        Logd(TAG, "onPlaybackPause start ${playable?.timeSpent}")
        if (playable != null) SynchronizationQueueSink.enqueueEpisodePlayedIfSyncActive(playable, false)
    }

    private fun onPostPlayback(playable: Episode, ended: Boolean, skipped: Boolean, playingNext: Boolean) {
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

    private fun persistCurrentPosition(fromMediaPlayer: Boolean, playable_: Episode?, position_: Int) {
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
        if (position > it.duration) it.duration = position
        if (it.startPosition >= 0 && it.position > it.startPosition) it.playedDuration = (it.playedDurationWhenStarted + it.position - it.startPosition)
        if (it.startTime > 0) {
            var delta = (nowInMillis() - it.startTime)
            if (delta > 3 * max(it.playedDuration, 60000)) {
                Logpt(TAG, curEpisode, "upsertDB likely invalid delta: $delta ${it.title}")
                it.startTime = nowInMillis()
                delta = 0L
            } else it.timeSpent = it.timeSpentOnStart + delta
        }
        it.lastPlayedTime = (System.currentTimeMillis())
        if (it.playState == EpisodeState.NEW.code) it.setPlayState(EpisodeState.UNPLAYED)
        Logd(TAG, "upsertDB ${it.startTime} timeSpent: ${it.timeSpent} playedDuration: ${it.playedDuration}")
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
        oldStatus = status
        status = newStatus
        if (media != null) {
            if (!isUnknown) {
                val position_ = if (position == Episode.INVALID_TIME) media.position else position
                when {
                    oldStatus == PlayerStatus.PLAYING && !isPlaying && media.id == prevMedia?.id -> onPlaybackPause(media, position_)
                    oldStatus != PlayerStatus.PLAYING && isPlaying -> onPlaybackStart(media, position_)
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
                if (curEpisode != null) runOnIOScope { try { loadChapters(curEpisode!!, false) } catch (e: Throwable) { Logps(TAG, curEpisode, e, "Error loading chapters for: ${curEpisode?.title}") } }
            }
            isPaused -> savePlayerStatus(null, status)
            isStopped -> {}
            isPlaying -> {
                savePlayerStatus(null, status)
                // TODO: testing
//                persistCurrentPosition(true, null, Episode.INVALID_TIME)
                // set sleep timer if auto-enabled
                var autoEnableByTime = true
                val fromSetting = autoEnableFrom
                val toSetting = autoEnableTo
                if (fromSetting != toSetting) autoEnableByTime = isInTimeRange(fromSetting, toSetting, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour)
                if (oldStatus != null && sleepPrefs.AutoEnable && autoEnableByTime && sleepManager?.isActive != true) {
                    sleepManager?.setTimer(lastTimerValue.minutes.inWholeMilliseconds)
                    // TODO: what to do?
//                    EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.sleep_timer_enabled_label), { sleepManager?.disableSleepTimer() }, context.getString(R.string.undo)))
                }
            }
            isError -> {
                savePlayerStatus(null, null)
                pause(reinit = false)
            }
            else -> {}
        }
        notifySystem()
    }

    abstract fun notifySystem()

    abstract suspend fun saveClipInOriginalFormat(startPositionMs: Long, endPositionMs: Long? = null)

    open fun onDestroy() {
        currentMediaType = MediaType.UNKNOWN
        cancelPositionSaver()
        shutdown()
    }

    fun isCurrentlyPlaying(media: Episode?): Boolean {
        return isCurMedia(media) && PlaybackService.isRunning && isPlaying
    }

    fun isCurMedia(media: Episode?): Boolean {
        return media != null && curEpisode?.id == media.id
    }

    companion object {
        private val TAG: String = MediaPlayerBase::class.simpleName ?: "Anonymous"

        private const val MIN_POSITION_SAVER_INTERVAL: Int = 5000   // in millisoconds

        fun isStreamingCapable(media: Episode): Boolean {
//            showStackTrace()
            if (!isNetworkUrl(media.downloadUrl)) {
                Logpe(TAG,  media, "streaming media without a remote downloadUrl: ${media.downloadUrl}. Abort")
                return false
            }
            if (!networkMonitor.isConnected) {
                Logpe(TAG,  media, "streaming media but network is not available, abort")
                return false
            }
            return true
        }
    }
}
