package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.utils.NetworkUtils.wasDownloadBlocked
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre.bitrate
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curIndexInQueue
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.setCurEpisode
import ac.mdiq.podcini.playback.base.InTheatre.writePlayerStatus
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.AppPreferences.rewindSecs
import ac.mdiq.podcini.storage.database.Episodes.indexOfItemWithId
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Queues.getNextInQueue
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.config.ClientConfig
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.audiofx.LoudnessEnhancer
import android.util.Pair
import android.view.SurfaceHolder
import android.webkit.URLUtil
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK
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
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.SelectionOverride
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.TrackNameProvider
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class LocalMediaPlayer(context: Context) : MediaPlayerBase(context) {

    @Volatile
    private var videoSize: Pair<Int, Int>? = null
    private var seekLatch: CountDownLatch? = null

    private val bufferUpdateInterval = 5000L
    private var playbackParameters: PlaybackParameters

    private var bufferedPercentagePrev = 0

    private val formats: List<Format>
        get() {
            val formats_: MutableList<Format> = arrayListOf()
            val trackInfo = trackSelector!!.currentMappedTrackInfo ?: return emptyList()
            val trackGroups = trackInfo.getTrackGroups(audioRendererIndex)
            for (i in 0 until trackGroups.length) {
                formats_.add(trackGroups[i].getFormat(0))
            }
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
        if (httpDataSourceFactory == null) runOnIOScope { httpDataSourceFactory = OkHttpDataSource.Factory(PodciniHttpClient.getHttpClient() as okhttp3.Call.Factory).setUserAgent(ClientConfig.USER_AGENT) }
        if (exoPlayer == null) {
            exoplayerListener = object : Listener {
                override fun onPlaybackStateChanged(playbackState: @State Int) {
                    Logd(TAG, "onPlaybackStateChanged $playbackState")
                    when (playbackState) {
                        STATE_READY -> {}
                        STATE_ENDED -> {
                            setPlayerStatus(PlayerStatus.STOPPED, null)
                            exoPlayer?.seekTo(C.TIME_UNSET)
                            audioCompletionListener?.invoke()
                        }
                        STATE_BUFFERING -> bufferingUpdateListener?.invoke(BUFFERING_STARTED)
                        else -> bufferingUpdateListener?.invoke(BUFFERING_ENDED)
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Logd(TAG, "onIsPlayingChanged $isPlaying")
                    val stat = if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                    setPlayerStatus(stat, curEpisode, getPosition())
                    writePlayerStatus(stat)
                }
                override fun onPlayerError(error: PlaybackException) {
                    Logd(TAG, "onPlayerError ${error.message}")
                    setPlayerStatus(PlayerStatus.ERROR, curEpisode)
                    if (wasDownloadBlocked(error)) audioErrorListener?.invoke(context.getString(R.string.download_error_blocked))
                    else {
                        var cause = error.cause
                        if (cause is HttpDataSourceException && cause.cause != null) cause = cause.cause
                        if (cause != null && "Source error" == cause.message) cause = cause.cause
                        audioErrorListener?.invoke((if (cause != null) cause.message else error.message) ?:"no message")
                    }
                }
                override fun onPositionDiscontinuity(oldPosition: PositionInfo, newPosition: PositionInfo, reason: @DiscontinuityReason Int) {
                    Logd(TAG, "onPositionDiscontinuity ${oldPosition.positionMs} ${newPosition.positionMs} $reason")
                    if (reason == DISCONTINUITY_REASON_SEEK) audioSeekCompleteListener?.invoke()
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
            createStaticPlayer(context)
        }
        playbackParameters = exoPlayer!!.playbackParameters
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            while (true) {
                delay(bufferUpdateInterval)
                withContext(Dispatchers.Main) {
                    if (exoPlayer != null && bufferedPercentagePrev != exoPlayer!!.bufferedPercentage) {
                        bufferingUpdateListener?.invoke(exoPlayer!!.bufferedPercentage)
                        bufferedPercentagePrev = exoPlayer!!.bufferedPercentage
                    }
                }
            }
        }
    }

    private fun release() {
        Logd(TAG, "release() called")
        exoPlayer?.stop()
        exoPlayer?.seekTo(0L)
        audioSeekCompleteListener = null
        audioCompletionListener = null
        audioErrorListener = null
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
        if (!forceReset && curEpisode?.id == prevMedia?.id && status == PlayerStatus.PLAYING) {
            Logd(TAG, "Method call to prepareMedia was ignored: media file already playing.")
            return
        }
        if (curEpisode != null) {
            prevMedia = curEpisode
            if (doPostPlayback) {
                Logd(TAG, "prepareMedia: curEpisode exist status=$status")
                Logd(TAG, "prepareMedia starts new playable:${playable.id} curEpisode:${curEpisode!!.id} prevMedia:${prevMedia?.id}")
                // set temporarily to pause in order to update list with current position
                if (status == PlayerStatus.PLAYING) {
                    val pos = curEpisode?.position ?: -1
                    seekTo(pos)
                    onPlaybackPause(curEpisode, pos)
                }
                // stop playback of this episode
                if (status in listOf(PlayerStatus.PAUSED, PlayerStatus.PLAYING, PlayerStatus.PREPARED)) exoPlayer?.stop()
                // TODO: testing, this appears not right and not needed
//            if (prevMedia != null && curEpisode?.id != prevMedia?.id) callback.onPostPlayback(prevMedia!!, ended = false, skipped = true, true)
                if (curEpisode?.id != playable.id) onPostPlayback(curEpisode!!, ended = false, skipped = true, true)
                setPlayerStatus(PlayerStatus.INDETERMINATE, null)
            }
        }

        Logd(TAG, "prepareMedia preparing for playable:${playable.id} ${playable.getEpisodeTitle()}")
        var item = playable
        if (item.playState < PlayState.PROGRESS.code) item = runBlocking { setPlayStateSync(PlayState.PROGRESS.code, item, false) }
        val eList = if (item.feed?.queue != null) curQueue.episodes else item.feed?.getVirtualQueueItems() ?: listOf()
        curIndexInQueue = eList.indexOfItemWithId(item.id)
        setCurEpisode(item)
        Logd(TAG, "prepareMedia eList: ${eList.size} curIndexInQueue: $curIndexInQueue")

        this.isStreaming = streaming
        mediaType = curEpisode!!.getMediaType()
        videoSize = null
        resetMediaPlayer()

        this.startWhenPrepared.set(startWhenPrepared)
        val metadata = buildMetadata(curEpisode!!)
        setPlaybackParams(getCurrentPlaybackSpeed(curEpisode), isSkipSilence)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when {
                    streaming -> {
                        val streamurl = curEpisode!!.downloadUrl
                        Logd(TAG, "streamurl: $streamurl")
                        if (!streamurl.isNullOrBlank()) {
                            mediaItem = null
                            mediaSource = null
                            setDataSource(metadata, curEpisode!!)
                        } else throw IOException("episode downloadUrl is empty ${curEpisode?.title}")
                    }
                    else -> {
                        val localMediaurl = curEpisode!!.fileUrl
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
                setPlayerStatus(PlayerStatus.ERROR, curEpisode)
            } catch (e: IllegalStateException) {
                Logs(TAG, e, "prepareMedia failed ${e.localizedMessage ?: ""}")
                setPlayerStatus(PlayerStatus.ERROR, curEpisode)
            } catch (e: Throwable) {
                setPlayerStatus(PlayerStatus.ERROR, curEpisode)
                Logs(TAG, e, "setDataSource error: [${e.localizedMessage}]")
            } finally { }
        }
    }

    override fun play() {
        Logd(TAG, "play(): status: $status exoPlayer?.playbackState: ${exoPlayer?.playbackState}")
        if (status in listOf(PlayerStatus.PAUSED, PlayerStatus.PREPARED)) {
            Logd(TAG, "Resuming/Starting playback")
            acquireWifiLockIfNecessary()
            setPlaybackParams(getCurrentPlaybackSpeed(curEpisode), isSkipSilence)
            setVolume(1.0f, 1.0f)
            if (curEpisode != null && status == PlayerStatus.PREPARED && curEpisode!!.position > 0)
                seekTo(calculatePositionWithRewind(curEpisode!!.position, curEpisode!!.lastPlayedTime))

            if (exoPlayer?.playbackState in listOf(STATE_IDLE, STATE_ENDED)) {
                if (mediaSource != null || mediaItem != null) {
                    if (mediaSource != null) exoPlayer?.setMediaSource(mediaSource!!, false)
                    else exoPlayer?.setMediaItem(mediaItem!!)
                    exoPlayer?.prepare()
                }
            }
            exoPlayer?.play()
            // Can't set params when paused - so always set it on start in case they changed
            exoPlayer?.playbackParameters = playbackParameters
            setPlayerStatus(PlayerStatus.PLAYING, curEpisode)
        } else Logt(TAG, "Call to play() was ignored because current state of PSMP object is $status")
    }

    override fun pause(reinit: Boolean) {
        releaseWifiLockIfNecessary()
        if (status in listOf(PlayerStatus.PLAYING, PlayerStatus.ERROR)) {
            Logd(TAG, "Pausing playback $reinit")
//             pauseAllPlayersAndStopSelf()
            exoPlayer?.pause()
            setPlayerStatus(PlayerStatus.PAUSED, curEpisode, getPosition())
            if (isStreaming && reinit) reinit()
        } else Logd(TAG, "Ignoring call to pause: Player is in $status state")
    }

    override fun prepare() {
        if (status == PlayerStatus.INITIALIZED) {
            Logd(TAG, "prepare Preparing media player: status: $status")
            setPlayerStatus(PlayerStatus.PREPARING, curEpisode)
            if (mediaSource != null || mediaItem != null) {
                if (mediaSource != null) exoPlayer?.setMediaSource(mediaSource!!, false)
                else exoPlayer?.setMediaItem(mediaItem!!)
                exoPlayer?.prepare()
            }
//            onPrepared(startWhenPrepared.get())
            if (mediaType == MediaType.VIDEO) videoSize = Pair(videoWidth, videoHeight)
            if (curEpisode != null) {
                Logd(TAG, "prepare curEpisode: ${curEpisode?.title}")
                val pos = curEpisode!!.position
                if (pos > 0) seekTo(pos)
                if (curEpisode != null && curEpisode!!.duration <= 0) {
                    Logd(TAG, "Setting duration of media")
                    upsertBlk(curEpisode!!) { it.duration = (if (exoPlayer?.duration == C.TIME_UNSET) Episode.INVALID_TIME else exoPlayer!!.duration.toInt()) }
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
            audioSeekCompleteListener?.invoke()
            endPlayback(true, wasSkipped = true)
            t = getPosition()
        }

        when (status) {
            PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.PREPARED -> {
                Logd(TAG, "seekTo t: $t")
                if (seekLatch != null && seekLatch!!.count > 0) try { seekLatch!!.await(3, TimeUnit.SECONDS) } catch (e: InterruptedException) { Logs(TAG, e) }
                seekLatch = CountDownLatch(1)
                val statusBeforeSeeking = status
                exoPlayer?.seekTo(t.toLong())
                audioSeekCompleteListener?.invoke()
                if (statusBeforeSeeking == PlayerStatus.PREPARED && curEpisode != null) upsertBlk(curEpisode!!) { it.setPosition(t) }
                try { seekLatch!!.await(3, TimeUnit.SECONDS) } catch (e: InterruptedException) { Logs(TAG, e) }
            }
            PlayerStatus.INITIALIZED -> {
                if (curEpisode != null) upsertBlk(curEpisode!!) { it.setPosition(t) }
                startWhenPrepared.set(false)
                prepare()
            }
            else -> {}
        }
    }

    override fun getPosition(): Int {
        var retVal = Episode.INVALID_TIME
        if (exoPlayer != null && status.isAtLeast(PlayerStatus.PREPARED)) retVal = exoPlayer!!.currentPosition.toInt()
        Logd(TAG, "getPosition player position: $retVal")
        if (retVal <= 0 && curEpisode != null) retVal = curEpisode!!.position
        Logd(TAG, "getPosition final position: $retVal")
        return retVal
    }

    override fun setPlaybackParams(speed: Float, skipSilence: Boolean) {
        EventFlow.postEvent(FlowEvent.SpeedChangedEvent(speed))
        Logd(TAG, "setPlaybackParams speed=$speed pitch=${playbackParameters.pitch} skipSilence=$skipSilence")
        playbackParameters = PlaybackParameters(speed, playbackParameters.pitch)
        exoPlayer?.skipSilenceEnabled = skipSilence
        exoPlayer?.playbackParameters = playbackParameters
    }

    override fun getPlaybackSpeed(): Float {
        var retVal = 1f
        if (status in listOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.INITIALIZED, PlayerStatus.PREPARED)) retVal = playbackParameters.speed
        return retVal
    }

    override fun setVolume(volumeLeft: Float, volumeRight: Float) {
        var volumeLeft = volumeLeft
        var volumeRight = volumeRight
        Logd(TAG, "setVolume: $volumeLeft $volumeRight")
        if (curEpisode != null) {
            val adaptionFactor = when {
                curEpisode?.volumeAdaptionSetting != VolumeAdaptionSetting.OFF -> curEpisode!!.volumeAdaptionSetting.adaptionFactor
                curEpisode!!.feed != null -> curEpisode!!.feed!!.volumeAdaptionSetting.adaptionFactor
                else -> 1f
            }
            if (adaptionFactor != 1f) {
                volumeLeft *= adaptionFactor
                volumeRight *= adaptionFactor
            }
        }
        Logd(TAG, "setVolume 1: $volumeLeft $volumeRight")
        if (volumeLeft > 1) {
            exoPlayer?.volume = 1f
            loudnessEnhancer?.setEnabled(true)
            loudnessEnhancer?.setTargetGain((1000 * (volumeLeft - 1)).toInt())
        } else {
            exoPlayer?.volume = volumeLeft
            loudnessEnhancer?.setEnabled(false)
        }
        Logd(TAG, "Media player volume was set to $volumeLeft $volumeRight")
    }

    override fun shutdown() {
        Logd(TAG, "shutdown() called")
        try {
            audioCompletionListener = {}
            audioSeekCompleteListener = {}
            bufferingUpdateListener = { }
            audioErrorListener = {}

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
        if (status != PlayerStatus.ERROR && mediaType == MediaType.VIDEO) videoSize = Pair(videoWidth, videoHeight)
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
            audioCompletionListener = {
                Logd(TAG, "audioCompletionListener called")
                endPlayback(hasEnded = true, wasSkipped = false)
            }
            audioSeekCompleteListener = {
                Logd(TAG, "audioSeekCompleteListener $status ${exoPlayer?.isPlaying}")
                seekLatch?.countDown()
//                if ((status == PlayerStatus.PLAYING && exoPlayer?.isPlaying != true) && curEpisode != null) onPlaybackStart(curEpisode!!, getPosition())
            }
            bufferingUpdateListener = { percent: Int ->
                Logd(TAG, "bufferingUpdateListener $percent")
                when (percent) {
                    BUFFERING_STARTED -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.started())
                    BUFFERING_ENDED -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.ended())
                    else -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.progressUpdate(0.01f * percent))
                }
            }
            audioErrorListener = { message: String ->
                Loge(TAG, "audioErrorListener: $message")
                setPlayerStatus(PlayerStatus.ERROR, curEpisode)
            }
        }
    }

    override fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean) {
        releaseWifiLockIfNecessary()
        if (curEpisode == null) {
            Logd(TAG, "endPlayback curEpisode is null, return")
            return
        }

        val isPlaying = status == PlayerStatus.PLAYING
        // we're relying on the position stored in the EpisodeMedia object for post-playback processing
        val position = getPosition()
        if (position >= 0) upsertBlk(curEpisode!!) { it.setPosition(position) }
        Logd(TAG, "endPlayback hasEnded=$hasEnded wasSkipped=$wasSkipped shouldContinue=$shouldContinue")

        val currentMedia = curEpisode
        when {
            shouldContinue -> {
                // Load next episode if previous episode was in the queue and if there is an episode in the queue left.
                // Start playback immediately if continuous playback is enabled
                var nextMedia = getNextInQueue(currentMedia) { showStreamingNotAllowedDialog(context, PlaybackStarter(context, it).intent) }
                if (nextMedia == null) {
                    Logd(TAG, "nextMedia is null. call callback.onPlaybackEnded true")
                    onPlaybackEnded(true)
                    setCurEpisode(null)
                    exoPlayer?.stop()
                    releaseWifiLockIfNecessary()
                    if (status == PlayerStatus.INDETERMINATE) setPlayerStatus(PlayerStatus.STOPPED, null)
                    else Logd(TAG, "Ignored call to stop: Current player state is: $status")
                } else {
                    Logd(TAG, "has nextMedia. call callback.onPlaybackEnded false")
                    if (wasSkipped) setPlayerStatus(PlayerStatus.INDETERMINATE, null)
                    onPlaybackEnded(true)
                    prepareMedia(playable = nextMedia, streaming = !nextMedia.localFileAvailable(), startWhenPrepared = isPlaying, prepareImmediately = isPlaying)
                }
                val hasNext = nextMedia != null
                if (currentMedia != null) onPostPlayback(currentMedia, hasEnded, wasSkipped, hasNext)
            }
            isPlaying -> onPlaybackPause(currentMedia, currentMedia?.position?: 0)
        }
    }

    override fun shouldLockWifi(): Boolean = isStreaming

    companion object {
        private val TAG: String = LocalMediaPlayer::class.simpleName ?: "Anonymous"

        const val BUFFERING_STARTED: Int = -1
        const val BUFFERING_ENDED: Int = -2

        private var trackSelector: DefaultTrackSelector? = null

        var streaming: Boolean? = getPref(AppPrefs.prefStreamOverDownload, false)

        var exoPlayer: ExoPlayer? = null

        private var exoplayerListener: Listener? = null
        private var audioSeekCompleteListener: (()->Unit)? = null
        private var audioCompletionListener: (()->Unit)? = null
        private var audioErrorListener: ((String) -> Unit)? = null
        private var bufferingUpdateListener: ((Int) -> Unit)? = null
        private var loudnessEnhancer: LoudnessEnhancer? = null

        fun isStreaming(media: Episode): Boolean = !media.localFileAvailable() || URLUtil.isContentUrl(media.downloadUrl)

//        fun resetMemoryBuffer() {
//            val memoryBufferSize = (128 * 1024 / 8) * BufferDurationSeconds
//            memoryBuffer = CircularByteBuffer(memoryBufferSize)
//        }

        fun createStaticPlayer(context: Context) {
//            val loadControl = DefaultLoadControl.Builder()
//            loadControl.setBufferDurationsMs(30000, 120000, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
//            if (streaming == true) loadControl.setBackBuffer(streamingBackBufferSecs * 1000, true) else loadControl.setBackBuffer(rewindSecs * 1000 + 500, true)
            Logd(TAG, "createStaticPlayer reset back buffer for streaming == $streaming")

            trackSelector = DefaultTrackSelector(context)
            val audioOffloadPreferences = AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED) // Add additional options as needed
                .setIsGaplessSupportRequired(true)
                .setIsSpeedChangeSupportRequired(true)
                .build()
            Logd(TAG, "createStaticPlayer creating exoPlayer_")

            simpleCache = getCache(context)

            // Initialize ExoPlayer
            val trackSelector = DefaultTrackSelector(context)
            val defaultRenderersFactory = DefaultRenderersFactory(context)
            exoPlayer = ExoPlayer.Builder(context, defaultRenderersFactory)
                .setTrackSelector(trackSelector)
                .setSeekBackIncrementMs(rewindSecs * 1000L)
                .setSeekForwardIncrementMs(fastForwardSecs * 1000L)
                .build()

            exoPlayer?.setSeekParameters(SeekParameters.EXACT)
            exoPlayer!!.trackSelectionParameters = exoPlayer!!.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(audioOffloadPreferences)
                .build()

//            if (BuildConfig.DEBUG) exoPlayer!!.addAnalyticsListener(EventLogger())

            Logd(TAG, "createStaticPlayer exoplayerListener == null: ${exoplayerListener == null}")
            if (exoplayerListener != null) {
                exoPlayer?.removeListener(exoplayerListener!!)
                exoPlayer?.addListener(exoplayerListener!!)
            }
        }

        private fun initLoudnessEnhancer(audioStreamId: Int) {
            runOnIOScope {
                try {
                    val newEnhancer = LoudnessEnhancer(audioStreamId)
                    val oldEnhancer = loudnessEnhancer
                    if (oldEnhancer != null) {
                        newEnhancer.setEnabled(oldEnhancer.enabled)
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
            audioSeekCompleteListener = null
            audioCompletionListener = null
            audioErrorListener = null
            bufferingUpdateListener = null
            loudnessEnhancer = null
            httpDataSourceFactory = null
        }
    }
}
