package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.playback.SegmentSavingDataSource
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.activeTheatres
import ac.mdiq.podcini.playback.base.OKHTTP.encodeCredentials
import ac.mdiq.podcini.playback.base.OKHTTP.getOKHttpClient
import ac.mdiq.podcini.playback.cast.CastMediaPlayer.buildCastPlayer
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isCasting
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.playback.service.QuickSettingsTileService
import ac.mdiq.podcini.receiver.PodciniWidget
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.fastForwardSecs
import ac.mdiq.podcini.storage.database.isSkipSilence
import ac.mdiq.podcini.storage.database.rewindSecs
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.streamingCacheSizeMB
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.toWidget
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.utils.cacheDir
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.durationStringShort
import ac.mdiq.podcini.storage.utils.fs
import ac.mdiq.podcini.storage.utils.parent
import ac.mdiq.podcini.storage.utils.toSafeUri
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.LogeFor
import ac.mdiq.podcini.utils.LogsFor
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.LogtFor
import ac.mdiq.podcini.utils.sendLocalBroadcast
import ac.mdiq.podcini.utils.timeIt
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes.BASE_TYPE_APPLICATION
import androidx.media3.common.MimeTypes.BASE_TYPE_AUDIO
import androidx.media3.common.MimeTypes.BASE_TYPE_VIDEO
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_PLAY_PAUSE
import androidx.media3.common.Player.DiscontinuityReason
import androidx.media3.common.Player.Listener
import androidx.media3.common.Player.PositionInfo
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.Player.State
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.Tracks
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.TrackNameProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import okio.buffer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

class Media3Player(playerId: Int, val lr: Int) : MediaPlayerBase() {
    private var exoPlayer: ExoPlayer? = null

    private var mediaSource: MediaSource? = null
    private var mediaItem: MediaItem? = null

    private var exoplayerListener: Listener? = null

    private var exoplayerOffloadListener: ExoPlayer.AudioOffloadListener? = null
    private var bufferingUpdateListener: ((Int) -> Unit)? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var trackSelector: DefaultTrackSelector? = null

    private var playbackParameters: PlaybackParameters

    private var bufferedPercentagePrev = 0

    private var speedEnablesOffload = true
    private var silenceEnablesOffload = !isSkipSilence
    private var offloadEnabled = speedEnablesOffload && silenceEnablesOffload
    private var needChangeOffload = false

    private val formats: List<Format>
        get() {
            val formats_: MutableList<Format> = arrayListOf()
            val trackInfo = trackSelector!!.currentMappedTrackInfo ?: return emptyList()
            val trackGroups = trackInfo.getTrackGroups(audioRendererIndex)
            for (i in 0 until trackGroups.length) formats_.add(trackGroups[i].getFormat(0))
            return formats_
        }

    override val audioTracks: List<String>
        get() {
            val trackNames: MutableList<String> = mutableListOf()
            val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(context.resources)
            for (format in formats) trackNames.add(trackNameProvider.getTrackName(format))
            return trackNames
        }

    private val audioRendererIndex: Int
        get() {
            for (i in 0 until(exoPlayer?.rendererCount?:0)) if (exoPlayer?.getRendererType(i) == C.TRACK_TYPE_AUDIO) return i
            return -1
        }

    private val cacheMutex = Mutex()
    private suspend fun initCache() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            simpleCache?.let { return@withLock }
            val appContext = getAppContext()
            val cacheDir = File(appContext.cacheDir, "media_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val databaseProvider = StandaloneDatabaseProvider(appContext)
            val evictor = LeastRecentlyUsedCacheEvictor(streamingCacheSizeMB * 1024L * 1024L)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
    }

    init {
        this.playerId = playerId
        timeIt("$TAG start of init")
        if (exoPlayer == null) {
            exoplayerListener = object: Listener {
                private var hasStarted = false
                override fun onPlaybackStateChanged(playbackState: @State Int) {
                    Logd(TAG, "exoplayerListener onPlaybackStateChanged $playbackState")
                    when (playbackState) {
                        STATE_BUFFERING -> bufferingUpdateListener?.invoke(BUFFERING_STARTED)
                        STATE_READY -> bufferingUpdateListener?.invoke(BUFFERING_ENDED)
                        STATE_ENDED -> {
                            val currentPos = exoPlayer?.currentPosition ?: 0L
                            val totalDuration = exoPlayer?.duration ?: 0L
                            Logd(TAG, "exoplayerListener onPlaybackStateChanged currentPos: $currentPos totalDuration: $totalDuration")
                            if (totalDuration > 0 && (totalDuration - currentPos) > 5000) {
                                Logt(TAG, "Stream ended prematurely at $currentPos ms. Resuming...")
//                                exoPlayer?.stop()
//                                val currentMediaItem = exoPlayer?.currentMediaItem
//                                currentMediaItem?.let { item ->
//                                    exoPlayer?.setMediaItem(item, currentPos)
//                                    exoPlayer?.prepare()
//                                    exoPlayer?.play()
//                                }
                            }
                            exoPlayer?.seekTo(C.TIME_UNSET)
                            endPlayback(hasEnded = true, wasSkipped = false)
                        }
                        STATE_IDLE -> {
                            Logd(TAG, "exoplayerListener onPlaybackStateChanged STATE_IDLE ")
                            if (isCasting && hasStarted && !isSkipping) endPlayback(hasEnded = true, wasSkipped = false)
                            hasStarted = false
                            isSkipping = false
                        }
                    }
                }
                override fun onEvents(player: Player, events: Player.Events) {
//                    for (i in 0 until events.size()) {
//                        val eventCode = events.get(i)
////                        Logd(TAG, "exoplayerListener onEvents $i $eventCode")
//                    }
                    if (isCasting) {
                        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                            events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                            val currentPos = player.currentPosition
                            val totalDuration = player.duration
                            if (player.playbackState == STATE_ENDED && totalDuration > 0 && (totalDuration - currentPos) > 10000) {
                                Logt(TAG, "Cast device disconnected stream early at $currentPos ms. Remotely resuming...")
                                val currentItem = player.currentMediaItem
                                currentItem?.let { item ->
                                    player.setMediaItem(item, currentPos)
                                    player.prepare()
                                    player.play()
                                }
                            }
                        }
                    }
                }
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    Logd(TAG, "exoplayerListener onMediaItemTransition $reason")
//                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) endPlayback(hasEnded = true, wasSkipped = false)
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Logd(TAG, "exoplayerListener onIsPlayingChanged $isPlaying")
//                    val pos = getPosition()
                    if (isPlaying) hasStarted = true
//                    else onPlaybackPause(curEpisode, pos)
//                    val stat = if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
//                    setPlayerStatus(stat, curEpisode)
//                    savePlayerStatus(null, stat)
                }
                override fun onPlayerError(error: PlaybackException) {
                    fun handleTerminalError(message: String) {
                        LogeFor(TAG, curEpisode?.id, message)
                        castPlayer?.stop()
                        castPlayer?.clearMediaItems()
                        setPlayerStatus(PlayerStatus.STOPPED, curEpisode)
                    }
                    Loge(TAG, "exoplayerListener onPlayerError ${error.message}")
                    when (error.errorCode) {
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                        PlaybackException.ERROR_CODE_TIMEOUT -> {
                            LogtFor(TAG, curEpisode?.id, "player error: ${error.localizedMessage}, retrying...")
                            val lastPosition = exoPlayer?.currentPosition ?: 0L
                            exoPlayer?.prepare()
                            if (lastPosition > 0) exoPlayer?.seekTo(lastPosition)
                            exoPlayer?.play()
                        }
                        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                            castPlayer?.prepare()
                            castPlayer?.play()
                        }
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                            LogtFor(TAG, curEpisode?.id, "Caught Source Error 2000 (NPE). Attempting a clean recovery...")
                            val currentPosition = exoPlayer?.currentPosition ?: 0L
                            val currentMediaItem = exoPlayer?.currentMediaItem
                            if (currentMediaItem != null) {
                                exoPlayer?.stop()
                                exoPlayer?.setMediaItem(currentMediaItem)
                                exoPlayer?.seekTo(currentPosition)
                                exoPlayer?.prepare()
                                exoPlayer?.play()
                            }
                        }
                        else -> {
                            val cause = error.cause
                            LogeFor(TAG, curEpisode?.id, "Player error: ${error.localizedMessage} ${error.errorCode} ${cause?.message}")
                            when {
                                cause is AudioSink.InitializationException -> {
                                    if (enableFloat) {
                                        Logt(TAG, "system can not handle float sampling, recreating players with float off")
                                        enableFloat = false
                                        playbackService?.switchPlayersMode()
                                    }
                                }
                                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND || (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 404) -> handleTerminalError("Episode not found on server (404).")
                                cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 403 -> handleTerminalError("Access denied (403). Check your subscription.")
                            }
                        }
                    }
                    //                    if (wasDownloadBlocked(error)) {
                    //                        Logpe(TAG, "audioErrorListener: ${getAppContext().getString(R.string.download_error_blocked)}")
                    //                        setPlayerStatus(PlayerStatus.ERROR, curEpisode)
                    //                    } else {
                    //                        var cause = error.cause
                    //                        if (cause is HttpDataSourceException && cause.cause != null) cause = cause.cause
                    //                        if (cause != null && "Source error" == cause.message) cause = cause.cause
                    //                        Logpe(TAG, "audioErrorListener: ${if (cause != null) cause.message else error.message}")
                    //                        setPlayerStatus(PlayerStatus.ERROR, curEpisode)
                    //                    }
                }
                override fun onPositionDiscontinuity(oldPosition: PositionInfo, newPosition: PositionInfo, reason: @DiscontinuityReason Int) {
//                    Logt(TAG, "onPositionDiscontinuity ${oldPosition.positionMs} ${newPosition.positionMs} $reason")
//                    if (reason == DISCONTINUITY_REASON_SEEK) audioSeekCompleteListener?.invoke()
                }
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    Logd(TAG, "exoplayerListener onAudioSessionIdChanged $audioSessionId")
                    runOnIOScope {
                        try {
                            val newEnhancer = LoudnessEnhancer(audioSessionId)
                            val oldEnhancer = loudnessEnhancer
                            if (oldEnhancer != null) {
                                newEnhancer.enabled = oldEnhancer.enabled
                                if (oldEnhancer.enabled) newEnhancer.setTargetGain(oldEnhancer.targetGain.toInt())
                                oldEnhancer.release()
                            }
                            loudnessEnhancer = newEnhancer
                        } catch (e: Throwable) { LogsFor(TAG, curEpisode?.id, e, "Failed to init LoudnessEnhancer") }
                    }
                }
                override fun onTracksChanged(tracks: Tracks) {
                    Logd(TAG, "exoplayerListener onTracksChanged tracks: ${tracks.groups.size}")
                    tracks.groups.forEach { group ->
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                val format = group.getTrackFormat(i)
                                mimeType = when {
                                    format.sampleMimeType.isNullOrBlank() -> ""
                                    format.sampleMimeType!!.contains(BASE_TYPE_AUDIO) -> format.sampleMimeType!!.replace("$BASE_TYPE_AUDIO/", "")
                                    format.sampleMimeType!!.contains(BASE_TYPE_VIDEO) -> format.sampleMimeType!!.replace("$BASE_TYPE_VIDEO/", "")
                                    format.sampleMimeType!!.contains(BASE_TYPE_APPLICATION) -> format.sampleMimeType!!.replace("$BASE_TYPE_APPLICATION/", "")
                                    else -> format.sampleMimeType!!
                                }
                                channelCount = format.channelCount
                                sampleRate = format.sampleRate
                                Logd(TAG, "exoplayerListener onTracksChanged $i ${format.averageBitrate} ${format.bitrate}")
                                if (format.averageBitrate != Format.NO_VALUE) bitrate = format.averageBitrate
                                return@forEach
                            }
                        }
                    }
                }
                override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                    if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                        Logd(TAG, "exoplayerListener onDeviceInfoChanged Casting active: Switching to remote URLs")
                        isCasting = true
                    } else {
                        Logd(TAG, "exoplayerListener onDeviceInfoChanged Local play: Switching to local files")
                        isCasting = false
                    }
                }
            }
            exoplayerOffloadListener = object: ExoPlayer.AudioOffloadListener {
                override fun onOffloadedPlayback(offloadSchedulingEnabled: Boolean) {
                    LogtFor(TAG, curEpisode?.id,  "AudioOffloadListener Offload scheduling enabled: $offloadSchedulingEnabled")
                }
                override fun onSleepingForOffloadChanged(isSleepingForOffload: Boolean) {
                    LogtFor(TAG, curEpisode?.id, "AudioOffloadListener CPU is sleeping for offload: $isSleepingForOffload")
                }
            }
            createNativePlayer()
        }
        playbackParameters = castPlayer!!.playbackParameters
        timeIt("$TAG end of init")
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

    private fun switchOffload() {
        if (!needChangeOffload || exoPlayer == null || isCasting) return

        Logd(TAG, "switchOffload offloadSpeedEnabled: $speedEnablesOffload offloadSilenceEnabled: $silenceEnablesOffload")
        val enabled = speedEnablesOffload && silenceEnablesOffload
        if (enabled == offloadEnabled) {
            needChangeOffload = false
            return
        }
        offloadEnabled = enabled
        LogtFor(TAG, curEpisode?.id, "switchOffload set audio offload $offloadEnabled")

        val wasPlaying = castPlayer!!.isPlaying
        castPlayer!!.pause()
        exoPlayer!!.trackSelectionParameters = exoPlayer!!.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(if (offloadEnabled) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                .build())
            .build()

        needChangeOffload = false

        if (mediaSource != null) exoPlayer?.setMediaSource(mediaSource!!, curEpisode!!.position.toLong())
        else if (mediaItem != null) castPlayer?.setMediaItem(mediaItem!!)

        castPlayer!!.prepare()
        if (wasPlaying) castPlayer!!.play()
    }

    abstract class ChannelAudioProcessor : BaseAudioProcessor() {
        override fun onConfigure(inputFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            return AudioProcessor.AudioFormat(inputFormat.sampleRate, 2, inputFormat.encoding)
        }
        abstract fun setOutputBuffer(outputBuffer: ByteBuffer, mono: Float)
        abstract fun setOutputBuffer(outputBuffer: ByteBuffer, mono: Short)

        override fun queueInput(inputBuffer: ByteBuffer) {
            val encoding = inputAudioFormat.encoding
            val inChannels = inputAudioFormat.channelCount

            val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val frameSize = bytesPerSample * inChannels
            val frameCount = inputBuffer.remaining() / frameSize

            val outputSize = frameCount * 2 * bytesPerSample // always stereo output
            val outputBuffer = replaceOutputBuffer(outputSize)

            if (encoding == C.ENCODING_PCM_FLOAT) {
                while (inputBuffer.remaining() >= frameSize) {
                    val left = inputBuffer.float
                    val right = if (inChannels > 1) inputBuffer.float else left
                    val mono = (left + right) / 2f
                    setOutputBuffer(outputBuffer, mono)
                }
            } else {
                while (inputBuffer.remaining() >= frameSize) {
                    val left = inputBuffer.short.toInt()
                    val right = if (inChannels > 1) inputBuffer.short.toInt() else left
                    val mono = ((left + right) / 2).coerceIn(-32768, 32767).toShort()
                    setOutputBuffer(outputBuffer, mono)
                }
            }
            outputBuffer.flip()
        }
    }

    class LeftChannelAudioProcessor: ChannelAudioProcessor() {
        override fun setOutputBuffer(outputBuffer: ByteBuffer, mono: Float) {
            outputBuffer.putFloat(mono) // L
            outputBuffer.putFloat(0f)   // R
        }
        override fun setOutputBuffer(outputBuffer: ByteBuffer, mono: Short) {
            outputBuffer.putShort(mono) // L
            outputBuffer.putShort(0)   // R
        }
    }
    class RightChannelAudioProcessor : ChannelAudioProcessor() {
        override fun setOutputBuffer(outputBuffer: ByteBuffer, mono: Float) {
            outputBuffer.putFloat(0f) // L
            outputBuffer.putFloat(mono)   // R
        }
        override fun setOutputBuffer(outputBuffer: ByteBuffer, mono: Short) {
            outputBuffer.putShort(0) // L
            outputBuffer.putShort(mono)   // R
        }
    }

    override fun createNativePlayer() {
        if (exoPlayer != null) return
        timeIt("$TAG createNativePlayer")

        val loadControl = DefaultLoadControl.Builder()
        loadControl.setBufferDurationsMs(30_000, 60_000, 3_500, 5_000).setPrioritizeTimeOverSizeThresholds(true)

        val audioOffloadPreferences = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(if (offloadEnabled) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
            .build()
        Logd(TAG, "createNativePlayer creating exoPlayer lr: $lr")

        runBlocking { initCache() }

        // Initialize ExoPlayer
        trackSelector = DefaultTrackSelector(context)
        val renderersFactory = object : DefaultRenderersFactory(context) {
            init {
                setEnableAudioOutputPlaybackParameters(true)
            }
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                val builder = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloat)
                    .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                if (lr == -1) builder.setAudioProcessors(arrayOf(LeftChannelAudioProcessor()))
                else if (lr == 1) builder.setAudioProcessors(arrayOf(RightChannelAudioProcessor()))
                return builder.build()
            }
        }
        val upstream = OkHttpDataSource.Factory(getOKHttpClient()).createDataSource()
//        val cacheDataSource = CacheDataSource.Factory().setCache(getCache()).setUpstreamDataSourceFactory { upstream }.setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR).createDataSource()
        val cacheDataSource = CacheDataSource.Factory().setCache(getCache()).setUpstreamDataSourceFactory { upstream }.createDataSource()
        curDataSource = SegmentSavingDataSource(cacheDataSource)
        val recordingFactory = DataSource.Factory { curDataSource!! }
//        val recordingFactory = DataSource.Factory { cacheDataSource }
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(recordingFactory)

        class CustomDurationTimeline(oldTimeline: Timeline, private val correctDurationUs: Long) : ForwardingTimeline(oldTimeline) {
            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                val originalWindow = super.getWindow(windowIndex, window, defaultPositionProjectionUs)
                originalWindow.durationUs = correctDurationUs
                return originalWindow
            }
        }

        val customMediaSourceFactory = object : MediaSource.Factory by mediaSourceFactory {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val baseSource = mediaSourceFactory.createMediaSource(mediaItem)
                val trueDurationMs = mediaItem.requestMetadata.extras?.getLong("KEY_TRUE_DURATION_MS", -1L) ?: -1L
                if (trueDurationMs > 0) {
                    Logd(TAG, "createMediaSource trueDurationMs: $trueDurationMs")
                    val trueDurationUs = trueDurationMs * 1000
                    return object : WrappingMediaSource(baseSource) {
                        override fun onChildSourceInfoRefreshed(newTimeline: Timeline) {
                            super.onChildSourceInfoRefreshed(CustomDurationTimeline(newTimeline, trueDurationUs))
                        }
                    }
                }
                return baseSource
            }
        }

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(customMediaSourceFactory)
            .setLoadControl(loadControl.build())
            .setTrackSelector(trackSelector!!)
            .setSeekBackIncrementMs(rewindSecs * 1000L)
            .setSeekForwardIncrementMs(fastForwardSecs * 1000L)
            .build()
        exoPlayer?.setSeekParameters(SeekParameters.DEFAULT)

        exoPlayer?.trackSelectionParameters = exoPlayer!!.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(audioOffloadPreferences)
            .build()

        Logd(TAG, "createNativePlayer exoplayerListener == null: ${exoplayerListener == null}")
        if (exoplayerListener != null) {
            exoPlayer?.removeListener(exoplayerListener!!)
            exoPlayer?.addListener(exoplayerListener!!)
        }
        if (exoplayerOffloadListener != null) {
            exoPlayer?.removeAudioOffloadListener(exoplayerOffloadListener!!)
            exoPlayer?.addAudioOffloadListener(exoplayerOffloadListener!!)
        }
        castPlayer = buildCastPlayer(exoPlayer!!)

        timeIt("$TAG createNativePlayer end")
    }

    private fun release() {
        Logd(TAG, "release() called")
        castPlayer?.stop()
        exoPlayer?.stop()
//        castPlayer?.seekTo(0L)
//        castPlayer?.clearMediaItems()
        bufferingUpdateListener = null
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun prepareDataSource(media: Episode) {
        Logd(TAG, "prepareDataSource called ${media.title}")
        Logd(TAG, "prepareDataSource url [${media.downloadUrl}]")
        mediaItem = null
        mediaSource = null
        val url = media.downloadUrl
        if (url.isNullOrBlank()) {
            LogeFor(TAG, curEpisode?.id, "prepareDataSource: media downloadUrl is null or blank ${media.title}")
            upsertBlk(media) { it.setPlayState(EpisodeState.ERROR) }
            throw IllegalArgumentException("blank url")
        }
        val feed = media.feed
        val user = feed?.username
        val password = feed?.password
        bitrate = 0
        try {
            mediaSource = gearbox.formMediaSource(this, media)
            if (mediaSource != null) {
                Logd(TAG, "prepareDataSource setting with mediaSource")
                mediaItem = mediaSource?.mediaItem
                setSourceCredentials(user, password)
            } else {
                Logd(TAG, "prepareDataSource setting date source")
                prepareDataSource(media, url, user, password)
            }
        } catch (e: Throwable) {
            LogsFor(TAG, curEpisode?.id, "prepareDataSource: ${e.message}")
            upsertBlk(media) { it.setPlayState(EpisodeState.ERROR) }
            throw e
        }
    }

    override fun prepareDataSource(media: Episode, mediaUrl: String, user: String?, password: String?) {
        val metadata = buildMetadata(curEpisode!!)
        Logd(TAG, "prepareDataSource: $mediaUrl")
        val uri = mediaUrl.toSafeUri()
        Logd(TAG, "prepareDataSource position: ${media.position} uri: $uri")
        mediaItem = MediaItem.Builder().setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(Bundle().apply { putLong("KEY_TRUE_DURATION_MS", media.duration.toLong()) }).build())
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(positionWithRewind(media.position, media.lastPlayedTime).toLong()).build())
            .setCustomCacheKey(media.id.toString()).setMediaMetadata(metadata).build()
        setSourceCredentials(user, password)
    }

    private fun setSourceCredentials(user: String?, password: String?) {
        if (!user.isNullOrEmpty() && !password.isNullOrEmpty()) {
            // TODO: need to check
            mediaItem = mediaItem!!.buildUpon().setTag(encodeCredentials(user, password, "ISO-8859-1")).build()
        }
    }

    override fun shouldSetSource(): Boolean {
        return castPlayer?.playbackState in listOf(STATE_IDLE, STATE_ENDED)
    }

    override fun setSource() {
        Logd(TAG, "setSource() called")
        if (mediaSource == null && mediaItem == null) return
        if (needChangeOffload) {
            val enabled = speedEnablesOffload && silenceEnablesOffload
            if (enabled != offloadEnabled) {
                offloadEnabled = enabled
                LogtFor(TAG, curEpisode?.id, "setSource set audio offload $offloadEnabled")
                exoPlayer!!.trackSelectionParameters = exoPlayer!!.trackSelectionParameters.buildUpon().setAudioOffloadPreferences(AudioOffloadPreferences.Builder().setAudioOffloadMode(if (offloadEnabled) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED).build()).build()
            }
            needChangeOffload = false
        }
        if (isCasting) castPlayer?.setMediaItem(mediaItem!!, curEpisode!!.position.toLong())
        else {
            if (mediaSource != null) exoPlayer?.setMediaSource(mediaSource!!, positionWithRewind(curEpisode!!.position, curEpisode!!.lastPlayedTime).toLong())
            else castPlayer?.setMediaItem(mediaItem!!, positionWithRewind(curEpisode!!.position, curEpisode!!.lastPlayedTime).toLong())
        }
        castPlayer?.prepare()
    }

    override fun setPlaybackParams() {
        castPlayer?.playbackParameters = playbackParameters
        curPBSpeed = playbackParameters.speed
    }

    override fun setPlaybackParams(speed: Float, pitch: Float) {
        if (castPlayer == null || abs(castPlayer!!.playbackParameters.speed - speed) < 0.01f) return
        EventFlow.postEvent(FlowEvent.SpeedChangedEvent(playerId, speed))
        Logd(TAG, "setPlaybackParams speed=$speed pitch=${playbackParameters.pitch}")
        val wantsOffload = speed == 1f
        if (wantsOffload != speedEnablesOffload) {
            speedEnablesOffload = wantsOffload
            needChangeOffload = true
            if (isPlaying) switchOffload()
        }
        playbackParameters = PlaybackParameters(if (speed <= 0) playbackParameters.speed else speed, if (pitch <= 0f) playbackParameters.pitch else pitch)
        setPlaybackParams()
        Logd(TAG, "setPlaybackParams offloadEnabled $speedEnablesOffload")
    }

    override fun setSkipSilence() {
        val skipSilence = skipSilence ?: curEpisode?.feed?.skipSilence ?: isSkipSilence
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

    override fun getPlaybackSpeed(): Float {
        var retVal = 1f
        if (isPlaying || isPaused || isInitialized || isPrepared) retVal = playbackParameters.speed
        return retVal
    }

    override fun setDuration() {
        Logd(TAG, "prepare Setting duration of media")
        val dur = if (exoPlayer?.duration == C.TIME_UNSET) Episode.INVALID_TIME else exoPlayer!!.duration.toInt()
        if (dur > 0) upsertBlk(curEpisode!!) { it.duration = dur }
    }

    override fun getPlayerPosition(): Int {
        return if (castPlayer?.isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM) == true) castPlayer!!.currentPosition.toInt() else Episode.INVALID_TIME
    }

    override fun setCastPlayImmediately() {
        if (castPlayer?.isCommandAvailable(COMMAND_PLAY_PAUSE ) == true) castPlayer?.playWhenReady = true
    }

    override fun playChime() {
        RingtoneManager.getRingtone(context, appPrefs.ringToneUriString!!.toUri()).play()
    }

    override fun notifyWidget() {
        CoroutineScope(Dispatchers.IO).launch {
            val manager = GlanceAppWidgetManager(getAppContext())
            val glanceId = manager.getGlanceIds(PodciniWidget::class.java).find { it.toString() == widgetId }
            glanceId?.let { id ->
                val episodes = actQueue.episodesSorted.take(40).map { it.toWidget() }
                val json = Json.encodeToString(episodes)
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[stringPreferencesKey("episodes")] = json
                        this[stringPreferencesKey("update_type")] = "update"
                    }
                }
                PodciniWidget().update(context, id)
            }
        }
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
            castPlayer?.volume = 1f
            loudnessEnhancer?.enabled = true
            loudnessEnhancer?.setTargetGain((1000 * (volumeLeft - 1)).toInt())
        } else {
            castPlayer?.volume = volumeLeft
            loudnessEnhancer?.enabled = false
        }
        Logd(TAG, "Media player volume was set to $volumeLeft $volumeRight")
    }

    override fun shutdown() {
        Logd(TAG, "shutdown() called")
        try {
            bufferingUpdateListener = { }
            if (exoPlayer?.isPlaying == true) exoPlayer?.stop()
        } catch (e: Exception) { LogsFor(TAG, curEpisode?.id, e) }
        release()
        status = PlayerStatus.STOPPED
    }

    override fun setAudioTrack(track: Int) {
        val trackGroups = trackSelector!!.currentMappedTrackInfo?.getTrackGroups(audioRendererIndex) ?: return
        val override = TrackSelectionOverride(trackGroups.get(track), 0)
        val params = trackSelector!!.buildUponParameters().addOverride(override).build()
        trackSelector!!.setParameters(params)
    }

    override fun getSelectedAudioTrack(): Int {
        val tracks = exoPlayer?.currentTracks ?: return -1
        val availableFormats = formats
        Logd(TAG, "selectedAudioTrack called tracks: ${tracks.groups.size} formats: ${availableFormats.size}")
        for (group in tracks.groups) {
            if (group.isSelected) {
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        val selectedFormat = group.getTrackFormat(i)
                        val index = availableFormats.indexOf(selectedFormat)
                        if (index != -1) return index
                    }
                }
            }
        }
        return -1
    }

    override fun resetMediaPlayer() {
        Logd(TAG, "resetMediaPlayer()")
        if (curEpisode == null) {
            release()
            setPlayerStatus(PlayerStatus.STOPPED, null)
            return
        }
        val i = curEpisode?.feed?.audioType?: C.AUDIO_CONTENT_TYPE_SPEECH
        val a = exoPlayer!!.audioAttributes
        val b = AudioAttributes.Builder()
        b.setContentType(i)
        b.setFlags(a.flags)
        b.setUsage(a.usage)
        exoPlayer?.setAudioAttributes(b.build(), activeTheatres <= 1)

        bufferingUpdateListener = { percent: Int ->
            Logd(TAG, "bufferingUpdateListener percent: $percent")
            if (curEpisode != null) {
                when (percent) {
                    BUFFERING_STARTED -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.started(curEpisode!!))
                    BUFFERING_ENDED -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.ended(curEpisode!!))
                    else -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.progressUpdate(curEpisode!!, 0.01f * percent))
                }
            }
        }
    }

    override fun notifySystem() {
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
            context.sendBroadcast(i)
        }
    }

    private var curDataSource: SegmentSavingDataSource? = null

    /**
     * Wrapper to handle start/stop/save with SegmentSavingDataSource
     * startPositionMs: Long? = null, // Null for stop/save
     * endPositionMs: Long? = null,   // Null for start
     */
    override suspend fun saveClipInOriginalFormat(startPositionMs: Long, endPositionMs: Long?) {
        val mediaItem = exoPlayer!!.currentMediaItem ?: run {
            LogeFor(TAG, curEpisode?.id, "No current media item.")
            return
        }
        val uri = mediaItem.localConfiguration?.uri ?: run {
            LogeFor(TAG, curEpisode?.id, "No URI in MediaItem.")
            return
        }
        if (endPositionMs == null) {
            if (uri.scheme == "file" || uri.scheme == "content") {
                Logd(TAG, "uri is file or content, will extract from the file.")
                return
            }
            curDataSource?.startRecording(startPositionMs, bitrate, cacheDir)
            return
        }
        val tracks = exoPlayer!!.currentTracks
        val audioFormat = tracks.groups.asSequence()
            .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
            .firstOrNull { it.sampleMimeType?.startsWith("audio/") == true }
        if (audioFormat == null) {
            LogeFor(TAG, curEpisode?.id,  "No audio track found.")
            return
        }
        val mimeType = audioFormat.sampleMimeType
        Logd(TAG, "mimeType: [$mimeType]")
        val ext = getFileExtensionFromMimeType(mimeType)
        if (ext == null) {
            LogeFor(TAG, curEpisode?.id, "Audio format not supported: $ext")
            return
        }

        val startBytePlayer = exoPlayer?.contentPositionToByte(startPositionMs)
        val endBytePlayer = exoPlayer?.contentPositionToByte(endPositionMs)

        val clipname = "${durationStringShort(startPositionMs, false, "m")}-${durationStringShort(endPositionMs, false, "m")}.$ext"
        val outputFile = curEpisode!!.getClipFile(clipname)
        when {
            uri.scheme == "file" || uri.scheme == "content" -> {
                val bytesPerSecond = bitrate / 8.0
                val startByte = (startPositionMs * bytesPerSecond / 1000).toLong()
                val endByte = (endPositionMs * bytesPerSecond / 1000).toLong()
                val bytesToRead = endByte - startByte
                val tempFile = cacheDir / "temp_segment.${outputFile.extension}"
                try {
                    val sourceFile = uri.toUF()
                    val allBytes = sourceFile.readBytes()
                    val segmentBytes = allBytes.sliceArray(startByte.toInt() until (startByte + bytesToRead).toInt())
                    tempFile.writeBytes(segmentBytes)
                    val segment = tempFile.readBytes()
                    if (segment.isNotEmpty()) {
                        val adjustedSegment = when (audioFormat.sampleMimeType) {
                            "audio/mp3" -> adjustMp3Clip(segment)
                            "audio/aac" -> adjustRawAacClip(segment)
                            "audio/ogg" -> adjustLocalOggClip(segment)
                            "audio/mp4" -> adjustLocalMp4Clip(segment)
                            else -> segment
                        }
                        outputFile.writeBytes(adjustedSegment)
                        upsert(curEpisode!!) { it.clips.add(clipname) }
                        Logd(TAG, "Saved local clip to: ${outputFile.absPath}")
                    } else LogeFor(TAG, curEpisode?.id, "Failed to extract segment from local media")
                } catch (e: Exception) { LogsFor(TAG, curEpisode?.id, e, "FileKit operation failed")
                } finally { tempFile.delete() }
            }
            else -> {   // streaming
                val tempFileDS = curDataSource?.stopRecording(endPositionMs)
                val cache = getCache()
                val bytesPerSecond = bitrate / 8.0
                val startByte = startBytePlayer ?: (startPositionMs * bytesPerSecond / 1000).toLong()
                val endByte = endBytePlayer ?: (endPositionMs * bytesPerSecond / 1000).toLong()
                val bytesToRead = endByte - startByte
                val key = curEpisode!!.id.toString()
                val spans = cache.getCachedSpans(key)
                Logd(TAG, "spans found: ${spans.size} ${curEpisode!!.id}")
                for (sp in spans) Logd(TAG, "span: ${sp.position} ${sp.length}")
                val cacheSpans = cache.getCachedSpans(key).filter { span -> span.position <= startByte && (span.position + span.length) >= endByte }
                Logd(TAG, "cacheSpans found: ${cacheSpans.size}")
                val cacheSpan = cache.getCachedSpans(key).firstOrNull { span -> span.position <= startByte && (span.position + span.length) >= endByte }
                Logd(TAG, "cacheSpan found: ${cacheSpan != null}")
                if (cacheSpan?.file?.exists() == true) {
                    val javaFile = cacheSpan.file ?: run { LogeFor(TAG, curEpisode?.id,"CacheSpan is null or has no file"); return }
                    val path = javaFile.toOkioPath()
                    val tempFile = outputFile.parent()!! / "temp_segment.${outputFile.extension}"
                    try {
                        fs.source(path).buffer().use { input ->
                            val bytesToSkip = if (startByte >= cacheSpan.position) startByte - cacheSpan.position else 0L
                            input.skip(bytesToSkip)
                            val segmentData = input.readByteArray(bytesToRead)
                            val totalRead = segmentData.size
                            tempFile.writeBytes(segmentData)
                            Logd(TAG, "Total written: $totalRead bytes")
                        }
                    } catch (e: Exception) { LogsFor(TAG, curEpisode?.id, e, "Failed to extract from cache span") }

                    val segment = tempFile.readBytes()
                    tempFile.delete()
                    if (segment.isNotEmpty()) {
                        val adjustedSegment = when (audioFormat.sampleMimeType) {
                            "audio/mp3" -> adjustMp3Clip(segment)
                            "audio/aac" -> adjustRawAacClip(segment)
                            "audio/ogg" -> adjustOggClip(segment, cache, key, startByte, endByte)
                            "audio/mp4" -> adjustMp4Clip(segment, cache, key, startByte, endByte)
                            else -> segment
                        }
                        outputFile.writeBytes(adjustedSegment)
                        upsert(curEpisode!!) { it.clips.add(clipname) }
                        Logd(TAG, "Saved cached segment of ${(endPositionMs - startPositionMs) / 1000} seconds to: ${outputFile.absPath}")
                        return
                    } else Logd(TAG, "Failed to extract segment from cache")
                }
                Logd(TAG, "Single span not found for range $startByte to $endByte or failed to extract segment from cache. Attempting full extraction.")
                val fullBytes = getFullFileFromCache(cache, key)
                if (fullBytes != null && audioFormat.sampleMimeType == "audio/mp4") {
                    outputFile.writeBytes(fullBytes)
                    upsert(curEpisode!!) { it.clips.add(clipname) }
                    Logd(TAG, "Saved full MP4 file to: ${outputFile.absPath} (re-muxing needed for partial clip)")
                    return
                }
                if (tempFileDS != null) {
                    Logd(TAG, "Segment not available in cache or full file extraction. Trying with player extract")
                    val bytesPerSecond = bitrate / 8.0
                    val startByte = (startPositionMs * bytesPerSecond / 1000).toLong()
                    val endByte = (endPositionMs * bytesPerSecond / 1000).toLong()
                    val bytesToRead = endByte - startByte
                    val tempOutput = outputFile.parent()!! / "temp_segment.${outputFile.extension}"
                    try {
                        tempFileDS.source().buffer().use { input ->
                            val segmentData = input.readByteArray(bytesToRead)
                            val totalRead = segmentData.size
                            tempOutput.writeBytes(segmentData)
                            Logd(TAG, "Total written: $totalRead bytes")
                        }
                    } catch (e: Exception) { LogsFor(TAG, curEpisode?.id, e, "Failed to extract from cache span") }
                    val segment = tempOutput.readBytes()
                    tempOutput.delete()
                    if (segment.isNotEmpty()) {
                        val adjustedSegment = when (audioFormat.sampleMimeType) {
                            "audio/mp3" -> adjustMp3Clip(segment)
                            "audio/aac" -> adjustRawAacClip(segment)
                            "audio/ogg" -> adjustOggClip(segment, cache, key, startByte, endByte)
                            "audio/mp4" -> adjustMp4Clip(segment, cache, key, startByte, endByte)
                            else -> segment
                        }
                        outputFile.writeBytes(adjustedSegment)
                        upsert(curEpisode!!) { it.clips.add(clipname) }
                        Logd(TAG, "Saved clip to: ${outputFile.absPath}")
                    } else LogeFor(TAG, curEpisode?.id, "Failed to extract segment from temp file")
                    tempFileDS.delete()
                } else LogeFor(TAG, curEpisode?.id, "Failed saving clip: No temp file available after stopping recording")
            }
        }
    }

    // Format adjustments
    private fun adjustMp3Clip(bytes: ByteArray): ByteArray = bytes
    private fun adjustRawAacClip(bytes: ByteArray): ByteArray = bytes

    private fun adjustOggClip(bytes: ByteArray, cache: SimpleCache, key: String, startByte: Long, endByte: Long): ByteArray {
        if (startByte > 0) {
            val headerBytes = getHeaderBytesFromCache(cache, key, 1024)
            return headerBytes?.plus(bytes) ?: bytes
        }
        return bytes
    }

    private fun adjustMp4Clip(bytes: ByteArray, cache: SimpleCache, key: String, startByte: Long, endByte: Long): ByteArray {
        if (startByte > 0 || endByte < spansTotalLength(cache, key)) {
            LogtFor(TAG, curEpisode?.id, "MP4 clip may not be playable without re-muxing.")
            val fullFileBytes = getFullFileFromCache(cache, key)
            return fullFileBytes ?: bytes
        }
        return bytes
    }
    private fun adjustLocalOggClip(bytes: ByteArray): ByteArray = bytes
    private fun adjustLocalMp4Clip(bytes: ByteArray): ByteArray {
        LogtFor(TAG, curEpisode?.id, "Local MP4 clip may not be playable without re-muxing.")
        return bytes
    }

    private fun getHeaderBytesFromCache(cache: SimpleCache, key: String, maxHeaderSize: Int): ByteArray? {
        val firstSpan = cache.getCachedSpans(key).minByOrNull { it.position } ?: return null
        if (firstSpan.position > 0 || firstSpan.file?.exists() != true) return null
        return firstSpan.file!!.inputStream().use { input ->
            val buffer = ByteArray(maxHeaderSize)
            val bytesRead = input.read(buffer, 0, maxHeaderSize)
            if (bytesRead > 0) buffer.copyOf(bytesRead) else null
        }
    }

    private fun getFullFileFromCache(cache: SimpleCache, key: String): ByteArray? {
        val spans = cache.getCachedSpans(key).sortedBy { it.position }
        if (spans.isEmpty()) return null
        val outputStream = java.io.ByteArrayOutputStream()
        spans.forEach { span -> span.file?.inputStream()?.use { it.copyTo(outputStream) } }
        return outputStream.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun spansTotalLength(cache: SimpleCache, key: String): Long = cache.getCachedSpans(key).sumOf { it.length }

    private fun getFileExtensionFromMimeType(mimeType: String?): String? {
        return when (mimeType) {
            "audio/mp3", "audio/mpeg" -> "mp3"
            "audio/aac" -> "aac"
            "audio/mp4", "audio/mp4a-latm" -> "m4a"
            "audio/ogg" -> "ogg"
            else -> null
        }
    }

    fun Player.contentPositionToByte(positionMs: Long): Long? {
        val timeline = currentTimeline
        if (timeline.isEmpty) return null
        val window = Timeline.Window()
        timeline.getWindow(currentMediaItemIndex, window)
        val format = currentTracks.groups.firstOrNull { it.isSelected }?.getTrackFormat(0)
        val bitrate = format?.averageBitrate?.takeIf { it != Format.NO_VALUE } ?: return null
        return (positionMs * bitrate) / 8000 // bps to bytes
    }

    override fun onDestroy() {
        if (exoplayerListener != null) exoPlayer?.removeListener(exoplayerListener!!)
        if (exoplayerOffloadListener != null) exoPlayer?.removeAudioOffloadListener(exoplayerOffloadListener!!)
        exoplayerListener = null
        exoplayerOffloadListener = null
        bufferingUpdateListener = null
        loudnessEnhancer = null
        httpDataSourceFactory = null

        castPlayer = null
        exoPlayer = null

        super.onDestroy()
    }

    companion object {
        private val TAG: String = Media3Player::class.simpleName ?: "Anonymous"

        private const val ACTION_PLAYER_STATUS_CHANGED: String = "action.ac.mdiq.podcini.service.playerStatusChanged"

        private const val AVRCP_ACTION_PLAYER_STATUS_CHANGED = "com.android.music.playstatechanged"
        private const val AVRCP_ACTION_META_CHANGED = "com.android.music.metachanged"

        const val BUFFERING_STARTED: Int = -1
        const val BUFFERING_ENDED: Int = -2

        private var enableFloat = false     // float is not well handled in Android devices

        var httpDataSourceFactory:  OkHttpDataSource.Factory? = null
            get() {
                if (field == null) field = OkHttpDataSource.Factory(getOKHttpClient() as okhttp3.Call.Factory)
//                if (field == null) field = OkHttpDataSource.Factory(getOKHttpClient() as okhttp3.Call.Factory).setDefaultRequestProperties(mapOf("Accept-Ranges" to "bytes"))
                return field
            }

        var simpleCache: SimpleCache? = null

        fun getCache(): SimpleCache {
            return simpleCache ?: throw IllegalStateException("Cache not initialized yet!")
        }

        fun releaseCache() {
            simpleCache?.release()
            simpleCache = null
        }

        fun nuclearCacheWipe() {
            val cacheDir = File(getAppContext().cacheDir, "media_cache")
            if (cacheDir.exists()) {
                val success = cacheDir.deleteRecursively()
                Logt(TAG, "Physical cache folder deleted: $success")
            }
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
                .setArtworkUri((e.imageUrl ?: e.feed?.imageUrl ?: "").toSafeUri())
            return builder.build()
        }
    }
}
