package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.service.HttpCredentialEncoder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.playback.base.InTheatre.bitrate
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.setCurEpisode
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.AppPreferences.streamingCacheSizeMB
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.config.ClientConfig
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.util.Pair
import android.view.SurfaceHolder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.provider.DocumentsContractCompat.isTreeUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

abstract class MediaPlayerBase protected constructor(protected val context: Context, protected val callback: MediaPlayerCallback) {

    @Volatile
    private var oldStatus: PlayerStatus? = null
    internal var prevMedia: Episode? = null

    protected var mediaSource: MediaSource? = null
    protected var mediaItem: MediaItem? = null

    internal var mediaType: MediaType = MediaType.UNKNOWN
    internal val startWhenPrepared = AtomicBoolean(false)

    var isStreaming = false

    /**
     * A wifi-lock that is acquired if the media file is being streamed.
     */
    private var wifiLock: WifiLock? = null

    /**
     * Returns a PSMInfo object that contains information about the current state of the PSMP object.
     * @return The PSMPInfo object.
     */
    @get:Synchronized
    val playerInfo: MediaPlayerInfo
        get() = MediaPlayerInfo(oldStatus, status, curEpisode)

    val isAudioChannelInUse: Boolean
        get() {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return (audioManager.mode != AudioManager.MODE_NORMAL || audioManager.isMusicActive)
        }
    
    init {
        status = PlayerStatus.STOPPED
    }

    protected open fun setPlayable(playable: Episode?) {
        if (playable != null && playable !== curEpisode) setCurEpisode(playable)
    }

    open fun getVideoSize(): Pair<Int, Int>? = null

    abstract fun getPlaybackSpeed(): Float

    abstract fun getDuration(): Int

    abstract fun getPosition(): Int

    open fun getAudioTracks(): List<String> = emptyList()

    open fun getSelectedAudioTrack(): Int = -1

    open fun createMediaPlayer() {}

    protected fun setDataSource(media: Episode, metadata: MediaMetadata, mediaUrl: String, user: String?, password: String?) {
        Logd(TAG, "setDataSource: $mediaUrl")
        val uri = Uri.parse(mediaUrl)
        fun copyToCache(context: Context, sourceUri: Uri): File {
            val cacheFile = File(context.cacheDir, "temp_audio.mp3")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
            }
            return cacheFile
        }
        if (uri.scheme == "content") {
            when {
                isTreeUri(uri) -> {
                    val localFile = copyToCache(context, uri)
                    mediaItem = MediaItem.Builder().setUri(Uri.fromFile(localFile)).setCustomCacheKey(media.id.toString()).setMediaMetadata(metadata).build()
                }
                else -> mediaItem = MediaItem.Builder().setUri(uri).setCustomCacheKey(media.id.toString()).setMediaMetadata(metadata).build()
            }
        } else mediaItem = MediaItem.Builder().setUri(uri).setCustomCacheKey(media.id.toString()).setMediaMetadata(metadata).build()
        if (mediaItem != null) {
//        mediaSource = null
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            val dataSourceFactory = CustomDataSourceFactory(context, httpDataSourceFactory)
            mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem!!)
            setSourceCredentials(user, password)
        } else Loge(TAG, "episode mediaUrl not valid $mediaUrl")
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected open fun setDataSource(metadata: MediaMetadata, media: Episode) {
        Logd(TAG, "setDataSource1 called ${media.title}")
        Logd(TAG, "setDataSource1 url [${media.downloadUrl}]")
        val url = media.downloadUrl
        if (url.isNullOrBlank()) {
            Loge(TAG, "setDataSource: media downloadUrl is null or blank ${media.title}")
            return
        }
        val feed = media.feed
        val user = feed?.username
        val password = feed?.password
        bitrate = 0
        mediaSource = gearbox.formMediaSource(metadata, media, context)
        if (mediaSource != null) {
            Logd(TAG, "setDataSource1 setting for Podcast source")
            mediaItem = mediaSource?.mediaItem
            setSourceCredentials(user, password)
        } else {
            Logd(TAG, "setDataSource1 setting for Podcast source")
            setDataSource(media, metadata, url,user, password)
        }
    }

    private fun setSourceCredentials(user: String?, password: String?) {
        if (!user.isNullOrEmpty() && !password.isNullOrEmpty()) {
            if (httpDataSourceFactory == null)
                httpDataSourceFactory = OkHttpDataSource.Factory(PodciniHttpClient.getHttpClient() as okhttp3.Call.Factory).setUserAgent(ClientConfig.USER_AGENT)

            val requestProperties = HashMap<String, String>()
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
     * During execution of the method, the object will be in the INITIALIZING state. The end state depends on the given parameters.
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
    abstract fun playMediaObject(playable: Episode, streaming: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean = false)

    /**
     * Resumes playback if the PSMP object is in PREPARED or PAUSED state. If the PSMP object is in an invalid state.
     * nothing will happen.
     * This method is executed on an internal executor service.
     */
    abstract fun resume()

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

    /**
     * Sets the playback parameters.
     * - Speed
     * - SkipSilence (ExoPlayer only)
     * This method is executed on an internal executor service.
     */
    abstract fun setPlaybackParams(speed: Float, skipSilence: Boolean)

    /**
     * Sets the playback volume.
     * This method is executed on an internal executor service.
     */
    abstract fun setVolume(volumeLeft: Float, volumeRight: Float)

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
        endPlayback(hasEnded = false, wasSkipped = true, shouldContinue = true, toStoppedState = true)
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
     * @param toStoppedState   If true, the playback state gets set to STOPPED if the media player
     * is not loading/playing after this call, and the UI will reflect that.
     * Only relevant if {@param shouldContinue} is set to false, otherwise
     * this method's behavior defaults as if this parameter was true.
     *
     * @return a Future, just for the purpose of tracking its execution.
     */
    internal abstract fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean, toStoppedState: Boolean)

    /**
     * @return `true` if the WifiLock feature should be used, `false` otherwise.
     */
    protected open fun shouldLockWifi(): Boolean = false

    open fun isCasting(): Boolean = false

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
        if (wifiLock != null && wifiLock!!.isHeld) wifiLock!!.release()
    }

    /**
     * Sets the player status of the PSMP object. PlayerStatus and media attributes have to be set at the same time
     * so that getPSMPInfo can't return an invalid state (e.g. status is PLAYING, but media is null).
     * This method will notify the callback about the change of the player status (even if the new status is the same
     * as the old one).
     * It will also call [MediaPlayerCallback.onPlaybackPause] or [MediaPlayerCallback.onPlaybackStart]
     * depending on the status change.
     * @param newStatus The new PlayerStatus. This must not be null.
     * @param media  The new playable object of the PSMP object. This can be null.
     * @param position  The position to be set to the current EpisodeMedia object in case playback started or paused.
     * Will be ignored if given the value of [Episode.INVALID_TIME].
     */
    // TODO: this routine can be very problematic!!!
    @Synchronized
    protected fun setPlayerStatus(newStatus: PlayerStatus, media: Episode?, position: Int = Episode.INVALID_TIME) {
        Logd(TAG, "setPlayerStatus: Setting player status to $newStatus from $status")
        this.oldStatus = status
        status = newStatus
        if (media != null) {
            // TODO: test, this appears not necessary
//            setPlayable(newMedia)
            if (newStatus != PlayerStatus.INDETERMINATE && newStatus != PlayerStatus.SEEKING) {
                when {
                    oldStatus == PlayerStatus.PLAYING && newStatus != PlayerStatus.PLAYING && media.id == prevMedia?.id -> callback.onPlaybackPause(media, position)
                    oldStatus != PlayerStatus.PLAYING && newStatus == PlayerStatus.PLAYING -> callback.onPlaybackStart(media, position)
                }
            }
        }
        if (media != null && status == PlayerStatus.PLAYING) prevMedia = media
        callback.statusChanged(MediaPlayerInfo(oldStatus, status, curEpisode))
    }

    class MediaPlayerInfo(
            @JvmField val oldPlayerStatus: PlayerStatus?,
            @JvmField var playerStatus: PlayerStatus,
            @JvmField var playable: Episode?)

    /**
     * Custom DataSource that saves clip data during read when recording is active.
     * Adapted to use an existing CacheDataSource instance.
     */
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
            val keys = simpleCache?.keys
            keys?.forEach { Logd(TAG, "key: $it") }
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
            val bytesRead = cacheDataSource.read(buffer, offset, length)
//            Logd(TAG, "read offset=$offset length=$length bytesRead=$bytesRead")
            if (bytesRead > 0 && isRecording) {
                clipTempFos?.write(buffer, offset, bytesRead)
                clipBytesWritten += bytesRead
            }
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
                clipStartByte = (startPositionMs * bitrate / 8 / 1000).toLong()
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
                val endByte = (endPositionMs * bitrate / 8 / 1000).toLong()
                Logd(TAG, "Stopped recording at byte offset $endByte, written: $clipBytesWritten")
                return clipTempFile?.takeIf { it.exists() && clipBytesWritten > 0 }
            }
            return null
        }
        override fun addTransferListener(transferListener: TransferListener) {
            cacheDataSource.addTransferListener(transferListener)
        }
    }

    class CustomDataSourceFactory(
            private val context: Context,
            private val upstreamFactory: DataSource.Factory) : DataSource.Factory {

        override fun createDataSource(): DataSource {
            return object : DataSource {
                private var dataSource: DataSource? = null
                private var segmentSaver: SegmentSavingDataSource? = null

                override fun open(dataSpec: DataSpec): Long {
                    Logd("CustomDataSourceFactory", "dataSpec.uri.scheme: ${dataSpec.uri.scheme}")
                    dataSource = if (dataSpec.uri.scheme == "file" || dataSpec.uri.scheme == "content") {
                        curDataSource = null
                        FileDataSource.Factory().createDataSource()
                    }
                    else {
                        val cacheDs = CacheDataSource(getCache(context), upstreamFactory.createDataSource(), CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                        curDataSource = SegmentSavingDataSource(cacheDs).also { segmentSaver = it }
                        curDataSource
                    }
                    return dataSource!!.open(dataSpec)
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return dataSource?.read(buffer, offset, length) ?: -1
                }
                override fun close() = dataSource?.close() ?: Unit
                override fun addTransferListener(transferListener: TransferListener) {
                    dataSource?.addTransferListener(transferListener)
                }
                override fun getUri(): Uri? = dataSource?.uri
                override fun getResponseHeaders(): Map<String, List<String>> = dataSource?.responseHeaders ?: emptyMap()
            }
        }
    }

    companion object {
        private val TAG: String = MediaPlayerBase::class.simpleName ?: "Anonymous"

        @get:Synchronized
        @JvmStatic
        var status by mutableStateOf(PlayerStatus.STOPPED)

        @JvmField
        val ELAPSED_TIME_FOR_SHORT_REWIND: Long = TimeUnit.MINUTES.toMillis(1)
        @JvmField
        val ELAPSED_TIME_FOR_MEDIUM_REWIND: Long = TimeUnit.HOURS.toMillis(1)
        @JvmField
        val ELAPSED_TIME_FOR_LONG_REWIND: Long = TimeUnit.DAYS.toMillis(1)

        @JvmField
        val SHORT_REWIND: Long = TimeUnit.SECONDS.toMillis(3)
        @JvmField
        val MEDIUM_REWIND: Long = TimeUnit.SECONDS.toMillis(10)
        @JvmField
        val LONG_REWIND: Long = TimeUnit.SECONDS.toMillis(20)

        var simpleCache: SimpleCache? = null
        var curDataSource: SegmentSavingDataSource? = null

        @JvmStatic
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
                .setArtworkUri(Uri.parse(e.imageLocation?:""))
            return builder.build()
        }

        fun buildMediaItem(e: Episode): MediaItem? {
            val url = e.downloadUrl ?: return null
            val metadata = buildMetadata(e)
            return MediaItem.Builder()
                .setMediaId(url)
                .setUri(Uri.parse(url))
                .setMediaMetadata(metadata).build()
        }

        /**
         * @param currentPosition  current position in a media file in ms
         * @param lastPlayedTime  timestamp when was media paused
         * @return  new rewinded position for playback in milliseconds
         */
        @JvmStatic
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
                return max(newPosition.toDouble(), 0.0).toInt()
            } else return currentPosition
        }

        /**
         * Returns the currently configured playback speed for the specified media.
         */
        @JvmStatic
        fun getCurrentPlaybackSpeed(media: Episode?): Float {
            var playbackSpeed = Feed.SPEED_USE_GLOBAL
            if (media != null) {
                playbackSpeed = curState.curTempSpeed
                // TODO: something strange here?
                if (playbackSpeed == Feed.SPEED_USE_GLOBAL) {
                    val feed = media.feed
                    if (feed != null) playbackSpeed = feed.playSpeed
                }
            }
            if (playbackSpeed == Feed.SPEED_USE_GLOBAL) playbackSpeed = prefPlaybackSpeed
            return playbackSpeed
        }
    }
}
