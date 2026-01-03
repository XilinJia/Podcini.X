package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.utils.NetworkUtils.wasDownloadBlocked
import ac.mdiq.podcini.playback.base.InTheatre.bitrate
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.savePlayerStatus
import ac.mdiq.podcini.playback.base.InTheatre.setAsCurEpisode
import ac.mdiq.podcini.playback.base.InTheatre.tempSkipSilence
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.AppPreferences.rewindSecs
import ac.mdiq.podcini.storage.database.getNextInQueue
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.audiofx.LoudnessEnhancer
import android.util.Pair
import android.view.SurfaceHolder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.DiscontinuityReason
import androidx.media3.common.Player.Listener
import androidx.media3.common.Player.PositionInfo
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.Player.State
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.SelectionOverride
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.TrackNameProvider
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@UnstableApi
class LocalMediaPlayer(context: Context) : MediaPlayerBase(context) {
    @Volatile
    private var videoSize: Pair<Int, Int>? = null

    private var trackSelector: DefaultTrackSelector? = null

    private var playbackParameters: PlaybackParameters

    private var bufferedPercentagePrev = 0

    private val formats: List<Format>
        get() {
            val formats_: MutableList<Format> = arrayListOf()
            val trackInfo = trackSelector!!.currentMappedTrackInfo ?: return emptyList()
            val trackGroups = trackInfo.getTrackGroups(audioRendererIndex)
            for (i in 0 until trackGroups.length) formats_.add(trackGroups[i].getFormat(0))
            return formats_
        }

    private val audioRendererIndex: Int
        get() {
            for (i in 0 until exoPlayer!!.rendererCount) if (exoPlayer?.getRendererType(i) == C.TRACK_TYPE_AUDIO) return i
            return -1
        }

    private val videoWidth: Int
        get() = exoPlayer?.videoFormat?.width ?: 0

    private val videoHeight: Int
        get() = exoPlayer?.videoFormat?.height ?: 0

    init {
        if (httpDataSourceFactory == null) runOnIOScope { httpDataSourceFactory = OkHttpDataSource.Factory(PodciniHttpClient.getHttpClient() as okhttp3.Call.Factory).setUserAgent("Mozilla/5.0") }
        if (exoPlayer == null) {
            exoplayerListener = object: Listener {
                override fun onPlaybackStateChanged(playbackState: @State Int) {
                    Logd(TAG, "onPlaybackStateChanged $playbackState")
                    when (playbackState) {
                        STATE_READY -> {}
                        STATE_ENDED -> {
                            setPlayerStatus(PlayerStatus.STOPPED, null)
                            exoPlayer?.seekTo(C.TIME_UNSET)
                            endPlayback(hasEnded = true, wasSkipped = false)
                        }
                        STATE_BUFFERING -> bufferingUpdateListener?.invoke(BUFFERING_STARTED)
                        else -> bufferingUpdateListener?.invoke(BUFFERING_ENDED)
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Logd(TAG, "onIsPlayingChanged $isPlaying")
                    val stat = if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                    setPlayerStatus(stat, curEpisode, getPosition())
                    savePlayerStatus(stat)
                }
                override fun onPlayerError(error: PlaybackException) {
                    Logd(TAG, "onPlayerError ${error.message}")
                    setPlayerStatus(PlayerStatus.ERROR, curEpisode)
                    if (wasDownloadBlocked(error)) {
                        Loge(TAG, "audioErrorListener: ${context.getString(R.string.download_error_blocked)}")
                        setPlayerStatus(PlayerStatus.ERROR, curEpisode)
                    } else {
                        var cause = error.cause
                        if (cause is HttpDataSourceException && cause.cause != null) cause = cause.cause
                        if (cause != null && "Source error" == cause.message) cause = cause.cause
                        Loge(TAG, "audioErrorListener: ${if (cause != null) cause.message else error.message}")
                        setPlayerStatus(PlayerStatus.ERROR, curEpisode)
                    }
                }
                override fun onPositionDiscontinuity(oldPosition: PositionInfo, newPosition: PositionInfo, reason: @DiscontinuityReason Int) {
                    Logd(TAG, "onPositionDiscontinuity ${oldPosition.positionMs} ${newPosition.positionMs} $reason")
//                    if (reason == DISCONTINUITY_REASON_SEEK) audioSeekCompleteListener?.invoke()
                }
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    Logd(TAG, "onAudioSessionIdChanged $audioSessionId")
                    initLoudnessEnhancer(audioSessionId)
                }
                override fun onTracksChanged(tracks: Tracks) {
                    Logd(TAG, "onTracksChanged tracks: ${tracks.groups.size}")
                    tracks.groups.forEach { group ->
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            Logd(TAG, "onTracksChanged $i ${format.averageBitrate} ${format.bitrate}")
                            if (format.averageBitrate != Format.NO_VALUE) {
                                bitrate = format.averageBitrate
                                Logd(TAG, "onTracksChanged Bitrate detected: $bitrate bps")
                                return@forEach
                            }
                        }
                    }
                }
            }

            exoplayerOffloadListener = object: ExoPlayer.AudioOffloadListener {
                override fun onOffloadedPlayback(offloadSchedulingEnabled: Boolean) {
                    Logt(TAG, "AudioOffloadListener Offload scheduling enabled: $offloadSchedulingEnabled")
                }
                override fun onSleepingForOffloadChanged(isSleepingForOffload: Boolean) {
                    Logt(TAG, "AudioOffloadListener CPU is sleeping for offload: $isSleepingForOffload")
                }
            }
            createStaticPlayer(context)
        }
        playbackParameters = exoPlayer!!.playbackParameters
    }

    override suspend fun invokeBufferListener() {
        if (exoPlayer != null && isPlaying) {
            withContext(Dispatchers.Main) {
                val pct = exoPlayer!!.bufferedPercentage
                if (bufferedPercentagePrev != pct) {
                    bufferingUpdateListener?.invoke(pct)
                    bufferedPercentagePrev = pct
                }
            }
        }
    }

    var speedEnablesOffload = true
    var silenceEnablesOffload = !isSkipSilence
    var offloadEnabled = speedEnablesOffload && silenceEnablesOffload
    var needChangeOffload = false

    fun switchOffload() {
        if (!needChangeOffload || exoPlayer == null) return

        Logd(TAG, "switchOffload offloadSpeedEnabled: $speedEnablesOffload offloadSilenceEnabled: $silenceEnablesOffload")
        val enabled = speedEnablesOffload && silenceEnablesOffload
        if (enabled == offloadEnabled) {
            needChangeOffload = false
            return
        }
        offloadEnabled = enabled
        Logt(TAG, "switchOffload set audio offload $offloadEnabled")

        val wasPlaying = exoPlayer!!.isPlaying
        val position = exoPlayer!!.currentPosition

        exoPlayer!!.pause()

        exoPlayer!!.trackSelectionParameters = exoPlayer!!.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(if (offloadEnabled) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                .build())
            .build()

        needChangeOffload = false

        if (mediaSource != null) exoPlayer?.setMediaSource(mediaSource!!, false)
        else if (mediaItem != null) exoPlayer?.setMediaItem(mediaItem!!)

        exoPlayer!!.prepare()

        if (wasPlaying) exoPlayer!!.play()
    }

    override fun createStaticPlayer(context: Context) {
        val loadControl = DefaultLoadControl.Builder()
        loadControl.setBufferDurationsMs(90_000, 300_000, 2_000, 10_000)

        val audioOffloadPreferences = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(if (offloadEnabled) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED) // Add additional options as needed
//            .setIsGaplessSupportRequired(true)
//            .setIsSpeedChangeSupportRequired(true)
            .build()
        Logd(TAG, "createStaticPlayer creating exoPlayer_")

        simpleCache = getCache(context)

        // Initialize ExoPlayer
        trackSelector = DefaultTrackSelector(context)
        val defaultRenderersFactory = DefaultRenderersFactory(context)
        exoPlayer = ExoPlayer.Builder(context, defaultRenderersFactory)
            .setLoadControl(loadControl.build())
            .setTrackSelector(trackSelector!!)
            .setSeekBackIncrementMs(rewindSecs * 1000L)
            .setSeekForwardIncrementMs(fastForwardSecs * 1000L)
            .build()

        exoPlayer!!.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        exoPlayer!!.trackSelectionParameters = exoPlayer!!.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(audioOffloadPreferences)
            .build()

        Logd(TAG, "createStaticPlayer exoplayerListener == null: ${exoplayerListener == null}")
        if (exoplayerListener != null) {
            exoPlayer?.removeListener(exoplayerListener!!)
            exoPlayer?.addListener(exoplayerListener!!)
        }
        if (exoplayerOffloadListener != null) {
            exoPlayer?.removeAudioOffloadListener(exoplayerOffloadListener!!)
            exoPlayer?.addAudioOffloadListener(exoplayerOffloadListener!!)
        }
    }

    private fun release() {
        Logd(TAG, "release() called")
        exoPlayer?.stop()
        exoPlayer?.seekTo(0L)
        bufferingUpdateListener = null
    }

    /**
     * Starts or prepares playback of the specified EpisodeMedia object. If another EpisodeMedia object is already being played, the currently playing
     * episode will be stopped and replaced with the new EpisodeMedia object. If the EpisodeMedia object is already being played, the method will not do anything.
     * Whether playback starts immediately depends on the given parameters. See below for more details.
     * States:
     * The end state depends on the given parameters.
     * If 'prepareImmediately' is set to true, the method will go into PREPARING state and after that into PREPARED state.
     * If 'startWhenPrepared' is set to true, the method will additionally go into PLAYING state.
     * If an unexpected error occurs while loading the EpisodeMedia's metadata or while setting the MediaPlayers data source, the object will enter the ERROR state.
     * This method is executed on an internal executor service.
     * @param playable           The EpisodeMedia object that is supposed to be played. This parameter must not be null.
     * @param streaming             The type of playback. If false, the EpisodeMedia object MUST provide access to a locally available file via
     * getLocalMediaUrl. If true, the EpisodeMedia object MUST provide access to a resource that can be streamed by
     * the Android MediaPlayer via getStreamUrl.
     * @param startWhenPrepared  Sets the 'startWhenPrepared' flag. This flag determines whether playback will start immediately after the
     * episode has been prepared for playback. Setting this flag to true does NOT mean that the episode will be prepared
     * for playback immediately (see 'prepareImmediately' parameter for more details)
     * @param prepareImmediately Set to true if the method should also prepare the episode for playback.
     */
    override fun prepareMedia(playable: Episode, streaming: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean, doPostPlayback: Boolean) {
        Logd(TAG, "prepareMedia status=$status stream=$streaming startWhenPrepared=$startWhenPrepared prepareImmediately=$prepareImmediately forceReset=$forceReset ${playable.getEpisodeTitle()} ")
//       showStackTrace()
        if (!forceReset && curEpisode?.id == prevMedia?.id && isPlaying) {
            Logd(TAG, "Method call to prepareMedia was ignored: media file already playing.")
            return
        }
        if (curEpisode != null) {
            prevMedia = curEpisode
            if (doPostPlayback) {
                Logd(TAG, "prepareMedia: curEpisode exist status=$status")
                Logd(TAG, "prepareMedia starts new playable:${playable.id} curEpisode:${curEpisode!!.id} prevMedia:${prevMedia?.id}")
                // set temporarily to pause in order to update list with current position
                if (isPlaying) {
                    val pos = curEpisode?.position ?: -1
                    seekTo(pos)
                    onPlaybackPause(curEpisode, pos)
                }
                // stop playback of this episode
                if (isPaused || isPlaying || isPrepared) exoPlayer?.stop()
                if (curEpisode?.id != playable.id) onPostPlayback(curEpisode!!, ended = false, skipped = true, true)
                setPlayerStatus(PlayerStatus.INDETERMINATE, null)
            }
        }

        Logd(TAG, "prepareMedia preparing for playable:${playable.id} ${playable.getEpisodeTitle()}")
        if (playable.playState < EpisodeState.PROGRESS.code) runOnIOScope { upsert(playable) { it.setPlayState(EpisodeState.PROGRESS, false) } }
        setAsCurEpisode(playable)

        this.isStreaming = streaming
        mediaType = curEpisode!!.getMediaType()
        videoSize = null
        resetMediaPlayer()

        this.startWhenPrepared.set(startWhenPrepared)
        val metadata = buildMetadata(curEpisode!!)
        setPlaybackParams(currentPlaybackSpeed(curEpisode))
        setRepeat(shouldRepeat)
        setSkipSilence()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when {
                    streaming -> {
                        val streamurl = curEpisode?.downloadUrl
                        Logd(TAG, "prepareMedia streamurl: $streamurl")
                        if (!streamurl.isNullOrBlank()) {
                            mediaItem = null
                            mediaSource = null
                            setDataSource(metadata, curEpisode!!)
                        } else throw IOException("episode downloadUrl is empty ${curEpisode?.title}")
                    }
                    else -> {
                        val localMediaurl = curEpisode?.fileUrl
                        Logd(TAG, "prepareMedia localMediaurl: $localMediaurl")
                        if (!localMediaurl.isNullOrBlank()) setDataSource(curEpisode!!, metadata, localMediaurl, null, null)
                        else throw IOException("Unable to read local file $localMediaurl")
                    }
                }
                withContext(Dispatchers.Main) {
                    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                    if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_CAR) setPlayerStatus(PlayerStatus.INITIALIZED, curEpisode)
                    if (prepareImmediately) prepare()
                }
            } catch (e: IOException) {
                Logs(TAG, e, "prepareMedia failed ${e.localizedMessage ?: ""}")
                withContext(Dispatchers.Main) { setPlayerStatus(PlayerStatus.ERROR, curEpisode) }
            } catch (e: IllegalStateException) {
                Logs(TAG, e, "prepareMedia failed ${e.localizedMessage ?: ""}")
                withContext(Dispatchers.Main) { setPlayerStatus(PlayerStatus.ERROR, curEpisode) }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { setPlayerStatus(PlayerStatus.ERROR, curEpisode) }
                Logs(TAG, e, "setDataSource error: [${e.localizedMessage}]")
            } finally { }
        }
    }

    private fun setSource() {
        Logd(TAG, "setSource() called")
        if (mediaSource == null && mediaItem == null) return

        if (needChangeOffload) {
            val enabled = speedEnablesOffload && silenceEnablesOffload
            if (enabled != offloadEnabled) {
                offloadEnabled = enabled
                Logt(TAG, "setSource set audio offload $offloadEnabled")
                exoPlayer!!.trackSelectionParameters = exoPlayer!!.trackSelectionParameters.buildUpon().setAudioOffloadPreferences(AudioOffloadPreferences.Builder().setAudioOffloadMode(if (offloadEnabled) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED).build()).build()
            }
            needChangeOffload = false
        }

        if (mediaSource != null) exoPlayer?.setMediaSource(mediaSource!!, false)
        else exoPlayer?.setMediaItem(mediaItem!!)
        exoPlayer?.prepare()
    }

    override fun play() {
        Logd(TAG, "play(): status: $status exoPlayer?.playbackState: ${exoPlayer?.playbackState}")
        if (isPaused || isPrepared) {
            Logd(TAG, "Resuming/Starting playback")
            acquireWifiLockIfNecessary()
            setPlaybackParams(currentPlaybackSpeed(curEpisode))
            setRepeat(shouldRepeat)
            setSkipSilence()
            setVolume(1.0f, 1.0f)
            if (curEpisode != null && isPrepared && curEpisode!!.position > 0)
                seekTo(positionWithRewind(curEpisode!!.position, curEpisode!!.lastPlayedTime))

            if (exoPlayer?.playbackState in listOf(STATE_IDLE, STATE_ENDED)) setSource()

            exoPlayer?.play()
            // Can't set params when paused - so always set it on start in case they changed
            exoPlayer?.playbackParameters = playbackParameters
            setPlayerStatus(PlayerStatus.PLAYING, curEpisode)
        } else Logt(TAG, "Call to play() was ignored because current state of PSMP object is $status")
    }

    override fun pause(reinit: Boolean) {
        releaseWifiLockIfNecessary()
        if (isPlaying || isError) {
            Logd(TAG, "Pausing playback $reinit")
            exoPlayer?.pause()
            setPlayerStatus(PlayerStatus.PAUSED, curEpisode, getPosition())
            if (isStreaming && reinit) reinit()
        } else Logt(TAG, "Ignoring call to pause: Player is in $status state")
    }

    override fun prepare() {
        if (isInitialized) {
            Logd(TAG, "prepare Preparing media player: status: $status")
            setPlayerStatus(PlayerStatus.PREPARING, curEpisode)
            setPlaybackParams(currentPlaybackSpeed(curEpisode))
            setSkipSilence()
            setSource()

//            onPrepared(startWhenPrepared.get())
            if (mediaType == MediaType.VIDEO) videoSize = Pair(videoWidth, videoHeight)
            if (curEpisode != null) {
                Logd(TAG, "prepare curEpisode: ${curEpisode?.title}")
                val pos = curEpisode!!.position
                if (pos > 0) seekTo(pos)
                if (curEpisode != null && curEpisode!!.duration <= 0) {
                    Logd(TAG, "Setting duration of media")
                    val dur = if (exoPlayer?.duration == C.TIME_UNSET) Episode.INVALID_TIME else exoPlayer!!.duration.toInt()
                    if (dur > 0) upsertBlk(curEpisode!!) { it.duration = dur }
                }
            }
            setPlayerStatus(PlayerStatus.PREPARED, curEpisode)
            if (startWhenPrepared.get()) play()
        }
    }

    override fun reinit() {
        Logd(TAG, "reinit() called")
        releaseWifiLockIfNecessary()
        when {
            curEpisode != null -> prepareMedia(playable = curEpisode!!, streaming = isStreaming, startWhenPrepared = startWhenPrepared.get(), prepareImmediately = false, forceReset = true, doPostPlayback = true)
            else -> Logd(TAG, "Call to reinit: media and mediaPlayer were null, ignored")
        }
    }

    override fun seekTo(t_: Int) {
        var t = t_
        if (t < 0) t = 0
        Logd(TAG, "seekTo() called $t")

        if (t >= getDuration()) {
            Logd(TAG, "Seek reached end of file, skipping to next episode")
            exoPlayer?.seekTo(t.toLong())   // can set curMedia to null
            endPlayback(true, wasSkipped = true)
            t = getPosition()
        }
        when  {
            isPlaying || isPaused || isPrepared -> {
                Logd(TAG, "seekTo t: $t")
                val statusBeforeSeeking = status
                exoPlayer?.seekTo(t.toLong())
                if (statusBeforeSeeking == PlayerStatus.PREPARED && curEpisode != null) upsertBlk(curEpisode!!) { it.setPosition(t) }
            }
            isInitialized -> {
                if (curEpisode != null) upsertBlk(curEpisode!!) { it.setPosition(t) }
                startWhenPrepared.set(false)
                prepare()
            }
            else -> {}
        }
    }

    override fun getPosition(): Int {
        var retVal = Episode.INVALID_TIME
//        showStackTrace()
        if (exoPlayer?.isPlaying == true && !status.isAtLeast(PlayerStatus.PREPARED)) Logt(TAG, "exoPlayer playbackState ${exoPlayer?.playbackState} player status $status")
        if (exoPlayer?.isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM) == true) retVal = exoPlayer!!.currentPosition.toInt()
//        Logd(TAG, "getPosition player position: $retVal")
        if (retVal <= 0 && curEpisode != null) retVal = curEpisode!!.position
//        Logd(TAG, "getPosition final position: $retVal")
        return retVal
    }

    override fun setPlaybackParams(speed: Float) {
        if (exoPlayer == null || abs(exoPlayer!!.playbackParameters.speed - speed) < 0.01f) return

        EventFlow.postEvent(FlowEvent.SpeedChangedEvent(speed))
        Logd(TAG, "setPlaybackParams speed=$speed pitch=${playbackParameters.pitch}")
        val wantsOffload = speed == 1f
        if (wantsOffload != speedEnablesOffload) {
            speedEnablesOffload = wantsOffload
            needChangeOffload = true
            if (isPlaying) switchOffload()
        }
        playbackParameters = PlaybackParameters(speed, playbackParameters.pitch)
        exoPlayer?.playbackParameters = playbackParameters
        Logd(TAG, "setPlaybackParams offloadEnabled $speedEnablesOffload")
    }

    override fun setSkipSilence() {
        val skipSilence = tempSkipSilence ?: curEpisode?.feed?.skipSilence ?: isSkipSilence
        Logd(TAG, "setSkipSilence skipSilence: $skipSilence")
        val wantsOffload = !skipSilence
        if (wantsOffload != silenceEnablesOffload) {
            silenceEnablesOffload = wantsOffload
            needChangeOffload = true
            if (isPlaying) switchOffload()
        }
        exoPlayer?.skipSilenceEnabled = skipSilence
        Logd(TAG, "setSkipSilence offloadEnabled $silenceEnablesOffload")
    }

    override fun setRepeat(repeat: Boolean) {
        exoPlayer?.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override fun getPlaybackSpeed(): Float {
        var retVal = 1f
        if (isPlaying || isPaused || isInitialized || isPrepared) retVal = playbackParameters.speed
        return retVal
    }

    override fun setVolume(volumeLeft: Float, volumeRight: Float, adaptionFactor: Float) {
        var volumeLeft = volumeLeft
        var volumeRight = volumeRight
//        Logd(TAG, "setVolume: $volumeLeft $volumeRight $adaptionFactor")
        if (adaptionFactor != 1f) {
            volumeLeft *= adaptionFactor
            volumeRight *= adaptionFactor
        }
        Logd(TAG, "setVolume 1: $volumeLeft $volumeRight")
        if (volumeLeft > 1) {
            exoPlayer?.volume = 1f
            loudnessEnhancer?.enabled = true
            loudnessEnhancer?.setTargetGain((1000 * (volumeLeft - 1)).toInt())
        } else {
            exoPlayer?.volume = volumeLeft
            loudnessEnhancer?.enabled = false
        }
        Logd(TAG, "Media player volume was set to $volumeLeft $volumeRight")
    }

    override fun shutdown() {
        Logd(TAG, "shutdown() called")
        try {
            bufferingUpdateListener = { }
//            TODO: should use: exoPlayer!!.playWhenReady ?
            if (exoPlayer?.isPlaying == true) exoPlayer?.stop()
        } catch (e: Exception) { Logs(TAG, e) }
        release()
        status = PlayerStatus.STOPPED
        releaseWifiLockIfNecessary()
    }

    override fun setVideoSurface(surface: SurfaceHolder?) {
        exoPlayer?.setVideoSurfaceHolder(surface)
    }

    override fun resetVideoSurface() {
        if (mediaType == MediaType.VIDEO) {
            Logd(TAG, "Resetting video surface")
            exoPlayer?.setVideoSurfaceHolder(null)
            reinit()
        } else Logt(TAG, "Resetting video surface for media of Audio type")
    }

    /**
     * Return width and height of the currently playing video as a pair.
     * @return Width and height as a Pair or null if the video size could not be determined. The method might still
     * return an invalid non-null value if the getVideoWidth() and getVideoHeight() methods of the media player return
     * invalid values.
     */
    override fun getVideoSize(): Pair<Int, Int>? {
        if (!isError && mediaType == MediaType.VIDEO) videoSize = Pair(videoWidth, videoHeight)
        return videoSize
    }

    override fun getAudioTracks(): List<String> {
        val trackNames: MutableList<String> = mutableListOf()
        val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(context.resources)
        for (format in formats) trackNames.add(trackNameProvider.getTrackName(format))
        return trackNames
    }

    override fun setAudioTrack(track: Int) {
        val trackInfo = trackSelector!!.currentMappedTrackInfo ?: return
        val trackGroups = trackInfo.getTrackGroups(audioRendererIndex)
        val override = SelectionOverride(track, 0)
        val rendererIndex = audioRendererIndex
        val params = trackSelector!!.buildUponParameters().setSelectionOverride(rendererIndex, trackGroups, override)
        trackSelector!!.setParameters(params)
    }

    override fun getSelectedAudioTrack(): Int {
        val trackSelections = exoPlayer!!.currentTrackSelections
        val availableFormats = formats
        Logd(TAG, "selectedAudioTrack called tracks: ${trackSelections.length} formats: ${availableFormats.size}")
        for (i in 0 until trackSelections.length) {
            val track = trackSelections[i] as? ExoTrackSelection ?: continue
            if (availableFormats.contains(track.selectedFormat)) return availableFormats.indexOf(track.selectedFormat)
        }
        return -1
    }

    override fun resetMediaPlayer() {
        Logd(TAG, "resetMediaPlayer()")
        release()
        if (curEpisode == null) {
            status = PlayerStatus.STOPPED
            return
        }
        val i = curEpisode?.feed?.audioType?: C.AUDIO_CONTENT_TYPE_SPEECH
        val a = exoPlayer!!.audioAttributes
        val b = AudioAttributes.Builder()
        b.setContentType(i)
        b.setFlags(a.flags)
        b.setUsage(a.usage)
        exoPlayer?.setAudioAttributes(b.build(), true)

        if (curEpisode != null) {
            bufferingUpdateListener = { percent: Int ->
                Logd(TAG, "bufferingUpdateListener $percent")
                when (percent) {
                    BUFFERING_STARTED -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.started())
                    BUFFERING_ENDED -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.ended())
                    else -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.progressUpdate(0.01f * percent))
                }
            }
        }
    }

    override fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean) {
        if (curEpisode == null) {
            Logd(TAG, "endPlayback curEpisode is null, return")
            releaseWifiLockIfNecessary()
            return
        }

//        val isPlaying = status == PlayerStatus.PLAYING
        // we're relying on the position stored in the EpisodeMedia object for post-playback processing
        val position = getPosition()
        if (position >= 0) upsertBlk(curEpisode!!) { it.setPosition(position) }
        Logd(TAG, "endPlayback hasEnded=$hasEnded wasSkipped=$wasSkipped shouldContinue=$shouldContinue")

        fun stopPlayer() {
            Logd(TAG, "endPlayback stopPlayer is called")
            onPlaybackEnded(true)
            releaseWifiLockIfNecessary()
            setAsCurEpisode(null)
            exoPlayer?.stop()
            if (isUnknown) setPlayerStatus(PlayerStatus.STOPPED, null)
            else Logd(TAG, "endPlayback Ignored call to stop: Current player state is: $status")
        }
        val currentMedia = curEpisode
        when {
            shouldContinue -> {
                // Load next episode if previous episode was in the queue and if there is an episode in the queue left.
                // Start playback immediately if continuous playback is enabled
                val nextMedia = getNextInQueue(currentMedia)
                if (nextMedia == null) {
                    Logd(TAG, "endPlayback nextMedia is null. call callback.onPlaybackEnded true")
                    stopPlayer()
                } else {
                    Logd(TAG, "endPlayback has nextMedia. call callback.onPlaybackEnded false")
                    if (wasSkipped) setPlayerStatus(PlayerStatus.INDETERMINATE, null)
                    onPlaybackEnded(true)
                    val needStreaming = (nextMedia.feed?.isLocalFeed != true && !nextMedia.localFileAvailable())
                    if (needStreaming) {
                        if (!isStreamingCapable(nextMedia)) {
                            if (currentMedia != null) onPostPlayback(currentMedia, hasEnded, wasSkipped, false)
                            return
                        } else acquireWifiLockIfNecessary()
                    } else releaseWifiLockIfNecessary()
                    prepareMedia(playable = nextMedia, streaming = needStreaming, startWhenPrepared = isPlaying, prepareImmediately = isPlaying)
                }
                if (currentMedia != null) onPostPlayback(currentMedia, hasEnded, wasSkipped, nextMedia != null)
            }
            isPlaying -> {
                Logd(TAG, "endPlayback isPlaying")
                releaseWifiLockIfNecessary()
                onPlaybackPause(currentMedia, currentMedia?.position?: 0)
            }
            else -> {
                Logd(TAG, "endPlayback else")
                stopPlayer()
            }
        }
    }

    override fun shouldLockWifi(): Boolean = isStreaming && getPref(AppPrefs.prefDisableWifiLock, false)

    companion object {
        private val TAG: String = LocalMediaPlayer::class.simpleName ?: "Anonymous"

        const val BUFFERING_STARTED: Int = -1
        const val BUFFERING_ENDED: Int = -2

        var exoPlayer: ExoPlayer? = null

        private var exoplayerListener: Listener? = null
        private var exoplayerOffloadListener: ExoPlayer.AudioOffloadListener? = null
        private var bufferingUpdateListener: ((Int) -> Unit)? = null
        private var loudnessEnhancer: LoudnessEnhancer? = null

//        fun resetMemoryBuffer() {
//            val memoryBufferSize = (128 * 1024 / 8) * BufferDurationSeconds
//            memoryBuffer = CircularByteBuffer(memoryBufferSize)
//        }

        private fun initLoudnessEnhancer(audioStreamId: Int) {
            runOnIOScope {
                try {
                    val newEnhancer = LoudnessEnhancer(audioStreamId)
                    val oldEnhancer = loudnessEnhancer
                    if (oldEnhancer != null) {
                        newEnhancer.enabled = oldEnhancer.enabled
                        if (oldEnhancer.enabled) newEnhancer.setTargetGain(oldEnhancer.targetGain.toInt())
                        oldEnhancer.release()
                    }
                    loudnessEnhancer = newEnhancer
                } catch (e: Throwable) { Logs(TAG, e, "Failed to init LoudnessEnhancer") }
            }
        }

        fun cleanup() {
            if (exoplayerListener != null) exoPlayer?.removeListener(exoplayerListener!!)
            exoplayerListener = null
            if (exoplayerOffloadListener != null) exoPlayer?.removeAudioOffloadListener(exoplayerOffloadListener!!)
            exoplayerOffloadListener = null
            bufferingUpdateListener = null
            loudnessEnhancer = null
            httpDataSourceFactory = null
        }
    }
}
