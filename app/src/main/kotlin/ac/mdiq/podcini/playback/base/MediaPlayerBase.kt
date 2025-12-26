package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.PodciniApp.Companion.getApp
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.service.HttpCredentialEncoder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.base.InTheatre.bitrate
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.curTempSpeed
import ac.mdiq.podcini.playback.base.InTheatre.savePlayerStatus
import ac.mdiq.podcini.playback.base.PositionSaver.Companion.positionSaver
import ac.mdiq.podcini.playback.base.SleepManager.Companion.sleepManager
import ac.mdiq.podcini.playback.service.QuickSettingsTileService
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.AppPreferences.streamingCacheSizeMB
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnableFrom
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnableTo
import ac.mdiq.podcini.preferences.SleepTimerPreferences.isInTimeRange
import ac.mdiq.podcini.preferences.SleepTimerPreferences.lastTimerValue
import ac.mdiq.podcini.storage.database.allowForAutoDelete
import ac.mdiq.podcini.storage.database.deleteMedia
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
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.sendLocalBroadcast
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.service.quicksettings.TileService
import android.util.Pair
import android.view.SurfaceHolder
import android.webkit.URLUtil
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

@UnstableApi
abstract class MediaPlayerBase protected constructor(protected val context: Context) {
    @Volatile
    private var oldStatus: PlayerStatus? = null
    internal var prevMedia: Episode? = null

//    protected var curPosition = Episode.INVALID_TIME

    protected var mediaSource: MediaSource? = null
    protected var mediaItem: MediaItem? = null

    internal var autoSkippedFeedMediaId: String? = null

    internal var mediaType: MediaType = MediaType.UNKNOWN
    internal val startWhenPrepared = AtomicBoolean(false)

    var prevPosition: Int = -1

    var isStreaming = false

    /**
     * A wifi-lock that is acquired if the media file is being streamed.
     */
    private var wifiLock: WifiManager.WifiLock? = null

    val isAudioChannelInUse: Boolean
        get() {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return (audioManager.mode != AudioManager.MODE_NORMAL || audioManager.isMusicActive)
        }
    
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

    open fun createStaticPlayer(context: Context) {}

    @OptIn(UnstableApi::class)
    protected fun setDataSource(media: Episode, metadata: MediaMetadata, mediaUrl: String, user: String?, password: String?) {
        Logd(TAG, "setDataSource: $mediaUrl")
        val uri = mediaUrl.toUri()
        mediaItem = MediaItem.Builder().setUri(uri).setCustomCacheKey(media.id.toString()).setMediaMetadata(metadata).build()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
//        val dataSourceFactory = CustomDataSourceFactory(context, httpDataSourceFactory)

        val cacheFactory = CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val segmentFactory = SegmentSavingDataSourceFactory(cacheFactory)
        val dataSourceFactory = DefaultDataSource.Factory(context, segmentFactory)

        mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem!!)
        setSourceCredentials(user, password)
    }

    @UnstableApi
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected open fun setDataSource(metadata: MediaMetadata, media: Episode) {
        Logd(TAG, "setDataSource called ${media.title}")
        Logd(TAG, "setDataSource url [${media.downloadUrl}]")
        val url = media.downloadUrl
        if (url.isNullOrBlank()) {
            Loge(TAG, "setDataSource: media downloadUrl is null or blank ${media.title}")
            upsertBlk(media) { it.setPlayState(EpisodeState.ERROR) }
            throw IllegalArgumentException("blank url")
        }
        val feed = media.feed
        val user = feed?.username
        val password = feed?.password
        bitrate = 0
        try {
            mediaSource = gearbox.formMediaSource(metadata, media)
            if (mediaSource != null) {
                Logd(TAG, "setDataSource setting for Podcast source")
                mediaItem = mediaSource?.mediaItem
                setSourceCredentials(user, password)
            } else {
                Logd(TAG, "setDataSource setting for Podcast source")
                setDataSource(media, metadata, url, user, password)
            }
        } catch (e: Throwable) {
            Loge(TAG, "setDataSource: ${e.message}")
            upsertBlk(media) { it.setPlayState(EpisodeState.ERROR) }
            throw e
        }
    }

    @OptIn(UnstableApi::class)
    private fun setSourceCredentials(user: String?, password: String?) {
        if (!user.isNullOrEmpty() && !password.isNullOrEmpty()) {
            if (httpDataSourceFactory == null)
                httpDataSourceFactory = OkHttpDataSource.Factory(PodciniHttpClient.getHttpClient() as okhttp3.Call.Factory).setUserAgent("Mozilla/5.0")

            val requestProperties = mutableMapOf<String, String>()
            requestProperties["Authorization"] = HttpCredentialEncoder.encode(user, password, "ISO-8859-1")
            httpDataSourceFactory!!.setDefaultRequestProperties(requestProperties)

            val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context, httpDataSourceFactory!!)
            val extractorsFactory = DefaultExtractorsFactory()
            extractorsFactory.setConstantBitrateSeekingEnabled(true)
            extractorsFactory.setMp3ExtractorFlags(Mp3Extractor.FLAG_DISABLE_ID3_METADATA)
            val f = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)

            mediaSource = f.createMediaSource(mediaItem!!)
        }
    }

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

    private fun positionUpdateInterval(duration: Int): Long {
        return if (getPref(AppPrefs.prefUseAdaptiveProgressUpdate, true)) max(MIN_POSITION_SAVER_INTERVAL, duration/50).toLong()
        else MIN_POSITION_SAVER_INTERVAL.toLong()
    }

    private fun onPlaybackStart(playable: Episode, position: Int) {
        val delayInterval = positionUpdateInterval(playable.duration)
        Logd(TAG, "onPlaybackStart ${playable.title}")
        Logd(TAG, "onPlaybackStart position: $position delayInterval: $delayInterval")
        if (position != Episode.INVALID_TIME) {
            upsertBlk(playable) {
                it.setPosition(position)
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
                    Logt(TAG, context.getString(R.string.pref_feed_skip_intro_toast, skipIntro))
                }
            }
            upsertBlk(playable) { it.setPlaybackStart() }
        }
        positionSaver?.startPositionSaver(delayInterval)
    }

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

    fun onPlaybackPause(playable: Episode?, position: Int) {
        Logd(TAG, "onPlaybackPause $position ${playable?.title}")
        positionSaver?.cancelPositionSaver()
        persistCurrentPosition(position == Episode.INVALID_TIME || playable == null, playable, position)
        Logd(TAG, "onPlaybackPause start ${playable?.timeSpent}")
        if (playable != null) SynchronizationQueueSink.enqueueEpisodePlayedIfSyncActive(context, playable, false)
    }

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
     * Releases internally used resources. This method should only be called when the object is not used anymore.
     */
    abstract fun shutdown()

    open fun setVideoSurface(surface: SurfaceHolder?) {
        throw UnsupportedOperationException("Setting Video Surface unsupported in Remote Media Player")
    }

    open fun resetVideoSurface() {
        Loge(TAG, "Resetting Video Surface unsupported in Remote Media Player")
    }

    open fun setAudioTrack(track: Int) {}

    fun skip() {
//        in first second of playback, ignoring skip
        if (getPosition() < 1000) return
        endPlayback(hasEnded = false, wasSkipped = true)
    }

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

    internal fun onPlaybackEnded(stopPlaying: Boolean) {
        Logd(TAG, "onPlaybackEnded stopPlaying: $stopPlaying")
        curTempSpeed = SPEED_USE_GLOBAL
        if (stopPlaying) positionSaver?.cancelPositionSaver()
    }

    fun onPostPlayback(playable: Episode, ended: Boolean, skipped: Boolean, playingNext: Boolean) {
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
        SynchronizationQueueSink.enqueueEpisodePlayedIfSyncActive(context, playable, completed)

        fun shouldSetPlayed(e: Episode): Boolean {
            return when (e.playState) {
                EpisodeState.FOREVER.code, EpisodeState.PLAYED.code -> false
                EpisodeState.AGAIN.code -> System.currentTimeMillis() - e.playStateSetTime >= e.duration
                else -> true
            }
        }
        runOnIOScope {
            if (ended || smartMarkAsPlayed || autoSkipped || (skipped && !getPref(AppPrefs.prefSkipKeepsEpisode, true))) {
                Logd(TAG, "onPostPlayback ended: $ended smartMarkAsPlayed: $smartMarkAsPlayed autoSkipped: $autoSkipped skipped: $skipped")
                // only mark the item as played if we're not keeping it anyways
                item = upsert(item) {
                    if (shouldSetPlayed(it)) it.setPlayState(EpisodeState.PLAYED)
                    upsertDB(it, item.position)
                    it.startTime = 0
                    it.startPosition = if (completed) -1 else it.position
                    if (ended || (skipped && smartMarkAsPlayed)) it.setPosition(0)
                    if (ended || skipped || playingNext) it.playbackCompletionDate = Date()
                }

                val action = item.feed?.autoDeleteAction
                val shouldAutoDelete = (action == AutoDeleteAction.ALWAYS || (action == AutoDeleteAction.GLOBAL && item.feed != null && allowForAutoDelete(item.feed!!)))
                val isItemdeletable = (!getPref(AppPrefs.prefFavoriteKeepsEpisode, true) || (!item.isSUPER && item.playState != EpisodeState.AGAIN.code && item.playState != EpisodeState.FOREVER.code))
                if (shouldAutoDelete && isItemdeletable) {
                    if (item.localFileAvailable()) item = deleteMedia(item)
                    if (getPref(AppPrefs.prefDeleteRemovesFromQueue, true)) removeFromAllQueues(listOf(item))
                } else if (getPref(AppPrefs.prefRemoveFromQueueMarkedPlayed, true)) removeFromAllQueues(listOf(item))
            }
        }
    }

    open fun isCasting(): Boolean = false

    /**
     * @return `true` if the WifiLock feature should be used, `false` otherwise.
     */
    protected open fun shouldLockWifi(): Boolean = false

    @Synchronized
    protected fun acquireWifiLockIfNecessary() {
        if (shouldLockWifi()) {
            if (wifiLock == null) {
                wifiLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL, TAG)
                wifiLock?.setReferenceCounted(false)
            }
            wifiLock?.acquire()
        }
    }

    @Synchronized
    protected fun releaseWifiLockIfNecessary() {
        if (wifiLock?.isHeld == true) wifiLock!!.release()
    }

    fun persistCurrentPosition(fromMediaPlayer: Boolean, playable_: Episode?, position_: Int) {
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
        it.setPosition(position)

        if (it.startPosition >= 0 && it.position > it.startPosition) it.playedDuration = (it.playedDurationWhenStarted + it.position - it.startPosition)
        if (it.startTime > 0) {
            var delta = (System.currentTimeMillis() - it.startTime)
            if (delta > 3 * max(it.playedDuration, 60000)) {
                Logt(TAG, "upsertDB likely invalid delta: $delta ${it.title}")
                it.startTime = System.currentTimeMillis()
                delta = 0L
            }
            else it.timeSpent = it.timeSpentOnStart + delta
        }

        it.lastPlayedTime = (System.currentTimeMillis())
        if (it.isNew) it.setPlayState(EpisodeState.UNPLAYED)
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
                gearbox.loadChapters(media)
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
                val fromSetting = autoEnableFrom()
                val toSetting = autoEnableTo()
                if (fromSetting != toSetting) {
                    val now: Calendar = GregorianCalendar()
                    now.timeInMillis = System.currentTimeMillis()
                    val currentHour = now[Calendar.HOUR_OF_DAY]
                    autoEnableByTime = isInTimeRange(fromSetting, toSetting, currentHour)
                }
                if (oldStatus != null && sleepPrefs.AutoEnable && autoEnableByTime && sleepManager?.isSleepTimerActive != true) {
                    sleepManager?.setSleepTimer(TimeUnit.MINUTES.toMillis(lastTimerValue()))
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
        TileService.requestListeningState(context, ComponentName(context, QuickSettingsTileService::class.java))
        sendLocalBroadcast(context, ACTION_PLAYER_STATUS_CHANGED)
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
            context.sendBroadcast(i)
        }
    }

    /**
     * Custom DataSource that saves clip data during read when recording is active.
     * Adapted to use an existing CacheDataSource instance.
     */
    @OptIn(UnstableApi::class)
    class SegmentSavingDataSource(private val cacheDataSource: CacheDataSource) : DataSource {
        private val TAG = "SegmentSavingDataSource"

        private var cacheListener: Cache.Listener? = null
        private var isRecording = false
        private var clipTempFile: File? = null
        private var clipTempFos: FileOutputStream? = null
        private var clipStartByte: Long = 0L
        private var clipBytesWritten: Long = 0L
        private lateinit var tempDir: File // Must be set externally, e.g., via constructor or setter

        override fun open(dataSpec: DataSpec): Long {
//            val keys = simpleCache?.keys
//            keys?.forEach { Logd(TAG, "key: $it") }
            val mediaId = dataSpec.key ?: dataSpec.uri.toString()
            val existingSpans = simpleCache?.getCachedSpans(mediaId)
            Logd(TAG, "Before listener: mediaId=[$mediaId] spans=${existingSpans?.size}, totalBytes=${existingSpans?.sumOf { it.length }}")

            cacheListener = object : Cache.Listener {
                override fun onSpanAdded(cache: Cache, span: CacheSpan) {
                    Logd(TAG, "Span added: key=$mediaId, position=${span.position}, length=${span.length}, file=${span.file?.absolutePath}")
                }
                override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
                    Logd(TAG, "Span removed: key=$mediaId, position=${span.position}, length=${span.length}")
                }
                override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
                    Logd(TAG, "Span touched: key=$mediaId, oldPos=${oldSpan.position}, newPos=${newSpan.position}")
                }
            }
            simpleCache?.addListener(mediaId, cacheListener!!)
            return cacheDataSource.open(dataSpec).also { Logd(TAG, "Open: position=${dataSpec.position}, length=$it") }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            var bytesRead = -1
            try {
                bytesRead = cacheDataSource.read(buffer, offset, length)
//            Logd(TAG, "read offset=$offset length=$length bytesRead=$bytesRead")
                if (bytesRead > 0 && isRecording) {
                    clipTempFos?.write(buffer, offset, bytesRead)
                    clipBytesWritten += bytesRead
                }
            } catch (e: Throwable) { Logd(TAG, "data source read/write error: ${e.message}") }
            return bytesRead
        }

        override fun getUri(): Uri? = cacheDataSource.uri

        override fun close() {
            Logd(TAG, "closing")
            if (isRecording) stopRecording(0) // Fallback if not explicitly stopped
            clipTempFos?.close()
            clipTempFos = null
            clipTempFile = null
            clipBytesWritten = 0L
            cacheDataSource.uri?.toString()?.let { mediaId -> cacheListener?.let { simpleCache?.removeListener(mediaId, it) } }
            cacheDataSource.close()
        }

        // Start recording at a given position (in ms, converted to bytes)
        fun startRecording(startPositionMs: Long, bitrate: Int, tmpDir: File) {
            tempDir = tmpDir
            if (!isRecording) {
                isRecording = true
                clipTempFile = File(tempDir, "clip_temp_${System.currentTimeMillis()}.tmp")
                clipTempFos = FileOutputStream(clipTempFile!!)
                clipStartByte = (startPositionMs * bitrate / 8 / 1000)
                clipBytesWritten = 0L
                Logd(TAG, "Started recording at byte offset $clipStartByte")
            } else Loge(TAG, "Cannot start recording: tempDir not set or already recording")
        }

        // Stop recording and return the temp file for processing
        fun stopRecording(endPositionMs: Long): File? {
            if (isRecording) {
                isRecording = false
                clipTempFos?.close()
                clipTempFos = null
                val endByte = (endPositionMs * bitrate / 8 / 1000)
                Logd(TAG, "Stopped recording at byte offset $endByte, written: $clipBytesWritten")
                return clipTempFile?.takeIf { it.exists() && clipBytesWritten > 0 }
            }
            return null
        }
        override fun addTransferListener(transferListener: TransferListener) {
            cacheDataSource.addTransferListener(transferListener)
        }
    }

    class SegmentSavingDataSourceFactory(private val upstreamFactory: CacheDataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return SegmentSavingDataSource(upstreamFactory.createDataSource())
        }
    }

    companion object {
        private val TAG: String = MediaPlayerBase::class.simpleName ?: "Anonymous"

        @SuppressLint("StaticFieldLeak")
        internal var mPlayer: MediaPlayerBase? = null
        var shouldRepeat by mutableStateOf(false)

        @Volatile
        var currentMediaType: MediaType? = MediaType.UNKNOWN

        @get:Synchronized
        var status by mutableStateOf(PlayerStatus.STOPPED)

        private const val MIN_POSITION_SAVER_INTERVAL: Int = 5000   // in millisoconds

        private const val AVRCP_ACTION_PLAYER_STATUS_CHANGED = "com.android.music.playstatechanged"
        private const val AVRCP_ACTION_META_CHANGED = "com.android.music.metachanged"

        const val ACTION_PLAYER_STATUS_CHANGED: String = "action.ac.mdiq.podcini.service.playerStatusChanged"

        val ELAPSED_TIME_FOR_SHORT_REWIND: Long = TimeUnit.MINUTES.toMillis(1)
        
        val ELAPSED_TIME_FOR_MEDIUM_REWIND: Long = TimeUnit.HOURS.toMillis(1)
        
        val ELAPSED_TIME_FOR_LONG_REWIND: Long = TimeUnit.DAYS.toMillis(1)

        
        val SHORT_REWIND: Long = TimeUnit.SECONDS.toMillis(3)
        
        val MEDIUM_REWIND: Long = TimeUnit.SECONDS.toMillis(10)
        
        val LONG_REWIND: Long = TimeUnit.SECONDS.toMillis(20)

        var simpleCache: SimpleCache? = null
        var curDataSource: SegmentSavingDataSource? = null

        var httpDataSourceFactory:  OkHttpDataSource.Factory? = null

        val prefPlaybackSpeed: Float
            get() {
                try { return getPref(AppPrefs.prefPlaybackSpeed, "1.00").toFloat()
                } catch (e: NumberFormatException) {
                    Logs(TAG, e)
                    putPref(AppPrefs.prefPlaybackSpeed, "1.0")
                    return 1.0f
                }
            }

        internal var normalSpeed = 1.0f
        internal var isSpeedForward = false
        internal var isFallbackSpeed = false

        val curDurationFB: Int
            get() = mPlayer?.getDuration() ?: curEpisode?.duration ?: Episode.INVALID_TIME

        val curPBSpeed: Float
            get() = mPlayer?.getPlaybackSpeed() ?: getCurrentPlaybackSpeed(curEpisode)

        private var isStartWhenPrepared: Boolean
            get() = mPlayer?.startWhenPrepared?.get() == true
            set(s) {
                mPlayer?.startWhenPrepared?.set(s)
            }

        fun getCache(context: Context): SimpleCache {
            return simpleCache ?: synchronized(this) {
                if (simpleCache != null) simpleCache!!
                else {
                    val cacheDir = File(context.filesDir, "media_cache")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(streamingCacheSizeMB * 1024L * 1024), StandaloneDatabaseProvider(context)).also { simpleCache = it }
                }
            }
        }

        @OptIn(UnstableApi::class)
        fun releaseCache() {
            simpleCache?.release()
            simpleCache = null
        }

        fun buildMetadata(e: Episode): MediaMetadata {
            val builder = MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(true)
                .setArtist(e.feed?.title?:"")
                .setTitle(e.getEpisodeTitle())
                .setAlbumArtist(e.feed?.title?:"")
                .setDisplayTitle(e.getEpisodeTitle())
                .setSubtitle(e.feed?.title?:"")
                .setArtworkUri((e.imageUrl ?: e.feed?.imageUrl ?: "").toUri())
            return builder.build()
        }

        fun buildMediaItem(e: Episode): MediaItem? {
            val url = e.downloadUrl ?: return null
            val metadata = buildMetadata(e)
            return MediaItem.Builder()
                .setMediaId(url)
                .setUri(url.toUri())
                .setMediaMetadata(metadata).build()
        }

        /**
         * @param currentPosition  current position in a media file in ms
         * @param lastPlayedTime  timestamp when was media paused
         * @return  new rewinded position for playback in milliseconds
         */
        fun calculatePositionWithRewind(currentPosition: Int, lastPlayedTime: Long): Int {
            if (currentPosition > 0 && lastPlayedTime > 0) {
                val elapsedTime = System.currentTimeMillis() - lastPlayedTime
                var rewindTime: Long = 0
                when {
                    elapsedTime > ELAPSED_TIME_FOR_LONG_REWIND -> rewindTime = LONG_REWIND
                    elapsedTime > ELAPSED_TIME_FOR_MEDIUM_REWIND -> rewindTime = MEDIUM_REWIND
                    elapsedTime > ELAPSED_TIME_FOR_SHORT_REWIND -> rewindTime = SHORT_REWIND
                }
                val newPosition = currentPosition - rewindTime.toInt()
                return max(newPosition, 0)
            } else return currentPosition
        }

        /**
         * Returns the currently configured playback speed for the specified media.
         */
        fun getCurrentPlaybackSpeed(media: Episode?): Float {
            var playbackSpeed = SPEED_USE_GLOBAL
            if (media != null) {
                playbackSpeed = curTempSpeed
                if (playbackSpeed == SPEED_USE_GLOBAL) {
                    val feed = media.feed
                    if (feed != null) playbackSpeed = feed.playSpeed
                }
            }
            if (playbackSpeed == SPEED_USE_GLOBAL) playbackSpeed = prefPlaybackSpeed
            return playbackSpeed
        }

        fun playPause() {
            Logd(TAG, "playPause status: $status")
            when {
                isPlaying -> {
                    mPlayer?.pause(reinit = false)
                    isSpeedForward =  false
                    isFallbackSpeed = false
                    curEpisode?.forceVideo = false
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
            if (!URLUtil.isNetworkUrl(media.downloadUrl)) {
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
