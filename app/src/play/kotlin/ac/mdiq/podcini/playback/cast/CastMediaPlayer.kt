package ac.mdiq.podcini.playback.cast

import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.setCurEpisode
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.AppPreferences.isSkipSilence
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.Queues.getNextInQueue
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Implementation of MediaPlayerBase suitable for remote playback on Cast Devices.
 */
@SuppressLint("VisibleForTests")
@OptIn(UnstableApi::class)
class CastMediaPlayer(context: Context) : MediaPlayerBase(context) {
    @Volatile
    private var mediaInfo: MediaInfo? = null
    @Volatile
    private var remoteState: Int
//    private val castContext by lazy { CastContext.getSharedInstance(context) }
    private val remoteMediaClient: RemoteMediaClient?
    private val isBuffering: AtomicBoolean

    private val remoteMediaClientCallback: RemoteMediaClient.Callback = object : RemoteMediaClient.Callback() {
        override fun onMetadataUpdated() {
            super.onMetadataUpdated()
            this@CastMediaPlayer.onStatusUpdated()
        }
        override fun onPreloadStatusUpdated() {
            super.onPreloadStatusUpdated()
            this@CastMediaPlayer.onStatusUpdated()
        }
        override fun onStatusUpdated() {
            super.onStatusUpdated()
            this@CastMediaPlayer.onStatusUpdated()
        }
        override fun onMediaError(mediaError: MediaError) {
            Loge(TAG, "onMediaError ${mediaError.reason}")
        }
    }

    init {
        if (castContext == null) castContext = CastContext.getSharedInstance(context.applicationContext)
        remoteMediaClient = castContext!!.sessionManager.currentCastSession?.remoteMediaClient
        remoteMediaClient?.registerCallback(remoteMediaClientCallback)
        setCurEpisode(null)
        isStreaming = true
        isBuffering = AtomicBoolean(false)
        remoteState = MediaStatus.PLAYER_STATE_UNKNOWN
    }

    private fun setBuffering(buffering: Boolean) {
        when {
            buffering && isBuffering.compareAndSet(false, true) -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.started())
            !buffering && isBuffering.compareAndSet(true, false) -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.ended())
        }
    }

    private fun toPlayable(info: MediaInfo?): Episode? {
        if (info == null || info.metadata == null) return null
        if (matches(info, curEpisode)) return curEpisode
        val streamUrl = info.metadata!!.getString(KEY_STREAM_URL)
//        return if (streamUrl == null) makeRemoteMedia(info) else callback.findMedia(streamUrl)
//        return if (streamUrl == null) null else callback.findMedia(streamUrl)
        return if (streamUrl == null) null else getEpisodeByGuidOrUrl(null, streamUrl)
    }

    private fun toMediaInfo(playable: Episode?): MediaInfo? {
        return when {
            playable == null -> null
            matches(mediaInfo, playable) -> mediaInfo
            else -> from(playable)
        }
    }

    private fun onStatusUpdated() {
        val mediaStatus = remoteMediaClient?.mediaStatus
        if (mediaStatus == null) {
            Logd(TAG, "Received null MediaStatus")
            return
        } else Logd(TAG, "Received remote status/media update. New state=" + mediaStatus.playerState)

        var state = mediaStatus.playerState
        val oldState = remoteState
        mediaInfo = mediaStatus.mediaInfo
        val mediaChanged = !matches(mediaInfo, curEpisode)
        var stateChanged = state != oldState
        if (!mediaChanged && !stateChanged) {
            Logd(TAG, "Both media and state haven't changed, so nothing to do")
            return
        }
//        var currentMedia = if (mediaChanged) toPlayable(mediaInfo) else curEpisode
        if (mediaChanged) setCurEpisode(toPlayable(mediaInfo))
//        val oldMedia = curEpisode
        val position = mediaStatus.streamPosition.toInt()
        // check for incompatible states
        if ((state == MediaStatus.PLAYER_STATE_PLAYING || state == MediaStatus.PLAYER_STATE_PAUSED) && curEpisode == null) {
            Loge(TAG, "onStatusUpdated returned playing or pausing state, but with no media")
            state = MediaStatus.PLAYER_STATE_UNKNOWN
            stateChanged = oldState != MediaStatus.PLAYER_STATE_UNKNOWN
        }
        if (stateChanged) remoteState = state
        if (mediaChanged && stateChanged && oldState == MediaStatus.PLAYER_STATE_PLAYING && state != MediaStatus.PLAYER_STATE_IDLE) {
            onPlaybackPause(null, Episode.INVALID_TIME)
            // We don't want setPlayerStatus to handle the onPlaybackPause callback
            setPlayerStatus(PlayerStatus.INDETERMINATE, null)
        }
        Logd(TAG, "onStatusUpdated state: $state")
        setBuffering(state == MediaStatus.PLAYER_STATE_BUFFERING)
        when (state) {
            MediaStatus.PLAYER_STATE_PLAYING -> {
                if (!stateChanged) {
                    //These steps are necessary because they won't be performed by setPlayerStatus()
                    if (position >= 0 && curEpisode != null) upsertBlk(curEpisode!!) { it.setPosition(position) }
                    if (curEpisode != null) upsertBlk(curEpisode!!) { it.setPlaybackStart() }
                }
                setPlayerStatus(PlayerStatus.PLAYING, curEpisode, position)
            }
            MediaStatus.PLAYER_STATE_PAUSED -> setPlayerStatus(PlayerStatus.PAUSED, curEpisode, position)
            MediaStatus.PLAYER_STATE_LOADING -> { Logd(TAG, "Remote player loading") }
            MediaStatus.PLAYER_STATE_BUFFERING -> setPlayerStatus(PlayerStatus.PREPARING, curEpisode, curEpisode?.position ?: Episode.INVALID_TIME)
            MediaStatus.PLAYER_STATE_IDLE -> {
                val reason = mediaStatus.idleReason
                when (reason) {
                    MediaStatus.IDLE_REASON_CANCELED -> {
                        // Essentially means stopped at the request of a user
                        onPlaybackEnded(true)
                        setPlayerStatus(PlayerStatus.STOPPED, curEpisode)
                        if (curEpisode != null) {
                            if (position >= 0) upsertBlk(curEpisode!!) { it.setPosition(position) }
                            onPostPlayback(curEpisode!!, ended = false, skipped = false, playingNext = false)
                        }
                        // onPlaybackEnded pretty much takes care of updating the UI
                        return
                    }
                    MediaStatus.IDLE_REASON_INTERRUPTED -> {
                        // Means that a request to load a different media was sent
                        // Not sure if curEpisode already reflects the to be loaded one
                        if (mediaChanged && oldState == MediaStatus.PLAYER_STATE_PLAYING) {
                            onPlaybackPause(null, Episode.INVALID_TIME)
                            setPlayerStatus(PlayerStatus.INDETERMINATE, null)
                        }
                        setPlayerStatus(PlayerStatus.PREPARING, curEpisode)
                    }
                    // This probably only happens when we connected but no command has been sent yet.
                    MediaStatus.IDLE_REASON_NONE -> setPlayerStatus(PlayerStatus.INITIALIZED, curEpisode)
                    MediaStatus.IDLE_REASON_FINISHED -> {
                        // This is our onCompletionListener...
                        if (mediaChanged && curEpisode != null) setCurEpisode(curEpisode)
                        endPlayback(true, wasSkipped = false)
                        return
                    }
                    MediaStatus.IDLE_REASON_ERROR -> {
                        Loge(TAG, "Got an error status from the Chromecast. Skipping, if possible, to the next episode...")
                        endPlayback(false, wasSkipped = false)
                        return
                    }
                    else -> return
                }
            }
            MediaStatus.PLAYER_STATE_UNKNOWN -> if (status != PlayerStatus.INDETERMINATE) setPlayerStatus(PlayerStatus.INDETERMINATE, null)
            else -> Loge(TAG, "Remote media state undetermined!")
        }
        if (mediaChanged && curEpisode != null) onPostPlayback(curEpisode!!, ended = false, skipped = false, playingNext = true)
    }

    /**
     * Internal implementation of prepareMedia. This method has an additional parameter that
     * allows the caller to force a media player reset even if
     * the given playable parameter is the same object as the currently playing media.
     * @see .prepareMedia
     */
    override fun prepareMedia(playable: Episode, streaming: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean, doPostPlayback: Boolean) {
       Logd(TAG, "prepareMedia")
        if (!isCastable(playable, castContext?.sessionManager?.currentCastSession)) {
            Loge(TAG, "media provided is not compatible with cast device: ${playable.title}, getting next in queue")
            var prevPlayable: Episode? = playable
            var nextPlayable: Episode? = playable
            do {
                prevPlayable = nextPlayable
                nextPlayable = getNextInQueue(nextPlayable) { showStreamingNotAllowedDialog(context, PlaybackStarter(context, it).intent) }
            } while (nextPlayable != null && nextPlayable.id != prevPlayable?.id && !isCastable(nextPlayable, castContext?.sessionManager?.currentCastSession))
            if (nextPlayable != null) prepareMedia(nextPlayable, streaming, startWhenPrepared, prepareImmediately, forceReset)
            return
        }

        if (!forceReset && curEpisode?.id == prevMedia?.id && status == PlayerStatus.PLAYING) {
            // episode is already playing -> ignore method call
            Logd(TAG, "Method call to prepareMedia was ignored: media file already playing.")
            return
        }
        if (curEpisode != null && doPostPlayback) {
            if (curEpisode?.id != playable.id) onPostPlayback(curEpisode!!, false, false, true)
            setPlayerStatus(PlayerStatus.INDETERMINATE, null)
        }

        setCurEpisode(playable)
        this.mediaType = curEpisode!!.getMediaType()
        this.startWhenPrepared.set(startWhenPrepared)

        val metadata = buildMetadata(curEpisode!!)
        try {
            setPlaybackParams(getCurrentPlaybackSpeed(curEpisode), isSkipSilence)
            when {
                streaming -> {
                    val streamurl = curEpisode!!.downloadUrl
                    if (!streamurl.isNullOrBlank()) {
                        mediaItem = null
                        mediaSource = null
                        setDataSource(metadata, curEpisode!!)
                    } else throw IOException("episode downloadUrl is empty ${curEpisode?.title}")
                }
                else -> {}
            }
            mediaInfo = toMediaInfo(curEpisode)
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_CAR) setPlayerStatus(PlayerStatus.INITIALIZED, curEpisode)
            if (prepareImmediately) prepare()
        } catch (e: IOException) {
            Logs(TAG, e, e.localizedMessage ?: "")
            setPlayerStatus(PlayerStatus.ERROR, curEpisode)
        } catch (e: IllegalStateException) {
            Logs(TAG, e, e.localizedMessage ?: "")
            setPlayerStatus(PlayerStatus.ERROR, curEpisode)
        } catch (e: Throwable) {
            Logs(TAG, e, "setDataSource error: [${e.localizedMessage}]")
            setPlayerStatus(PlayerStatus.ERROR, curEpisode)
        } finally { }
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun setDataSource(metadata: androidx.media3.common.MediaMetadata, media: Episode) {
        Logd(TAG, "setDataSource1 called")
        if (!gearbox.formCastMediaSource(media)) {
            media.effectUrl = media.downloadUrl ?: ""
            media.effectMimeType = media.mimeType ?: ""
        }
    }

    override fun play() {
        val newPosition = calculatePositionWithRewind(curEpisode!!.position, curEpisode!!.lastPlayedTime)
        seekTo(newPosition)
        remoteMediaClient?.play()
    }

    override fun pause(reinit: Boolean) {
        remoteMediaClient?.pause()
    }

    override fun prepare() {
        if (status == PlayerStatus.INITIALIZED) {
            Logd(TAG, "Preparing media player $mediaInfo")
            setPlayerStatus(PlayerStatus.PREPARING, curEpisode)
            var position = curEpisode!!.position
            if (position > 0) position = calculatePositionWithRewind(position, curEpisode!!.lastPlayedTime)
            remoteMediaClient?.load(MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(startWhenPrepared.get())
                .setCurrentTime(position.toLong()).build())
        }
    }

    override fun reinit() {
        Logd(TAG, "reinit() called")
        if (curEpisode != null) prepareMedia(playable = curEpisode!!, streaming = false, startWhenPrepared = startWhenPrepared.get(), prepareImmediately = false, forceReset = true, doPostPlayback = true)
        else Logd(TAG, "Call to reinit was ignored: media was null")
    }

    override fun seekTo(t: Int) {
        remoteMediaClient?.seek(MediaSeekOptions.Builder().setPosition(t.toLong()).setResumeState(MediaSeekOptions.RESUME_STATE_PLAY).build())?.addStatusListener {
            if (it.isSuccess) Logd(TAG, "seekTo Seek succeeded to position $t ms")
            else Loge(TAG, "seekTo failed")
        }
    }

    override fun getDuration(): Int {
//        if (curMedia != null && remoteMediaClient?.currentItem?.media?.entity != curMedia?.id.toString()) return curMedia!!.getDuration()
        var retVal = remoteMediaClient?.streamDuration?.toInt() ?: Episode.INVALID_TIME
        if (retVal == Episode.INVALID_TIME && curEpisode != null && curEpisode!!.duration > 0) retVal = curEpisode!!.duration
        return retVal
    }

    override fun getPosition(): Int {
//        Logd(TAG, "getPosition: $status ${remoteMediaClient?.approximateStreamPosition} ${curMedia?.getPosition()} ${remoteMediaClient?.currentItem?.media?.entity} ${curMedia?.id.toString()} ${curMedia?.getEpisodeTitle()}")
        var retVal = remoteMediaClient?.approximateStreamPosition?.toInt() ?: Episode.INVALID_TIME
        if (retVal <= 0 && curEpisode != null && curEpisode!!.position >= 0) retVal = curEpisode!!.position
        return retVal
    }

    override fun setPlaybackParams(speed: Float, skipSilence: Boolean) {
        val playbackRate = max(MediaLoadOptions.PLAYBACK_RATE_MIN, min(MediaLoadOptions.PLAYBACK_RATE_MAX, speed.toDouble()))
        remoteMediaClient?.setPlaybackRate(playbackRate)
    }

    override fun getPlaybackSpeed(): Float {
        return remoteMediaClient?.mediaStatus?.playbackRate?.toFloat() ?: 1.0f
    }

    override fun setVolume(volumeLeft: Float, volumeRight: Float) {
        Logd(TAG, "Setting the Stream volume on Remote Media Player")
        remoteMediaClient?.setStreamVolume(volumeLeft.toDouble())
    }

    override fun shutdown() {
        remoteMediaClient?.stop()
        remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
    }

    // TODO: better merge with LocalMediaPlayer
    override fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean) {
        Logd(TAG, "endPlayback() called")
        val isPlaying = status == PlayerStatus.PLAYING
        if (status != PlayerStatus.INDETERMINATE) setPlayerStatus(PlayerStatus.INDETERMINATE, null)

        if (curEpisode != null && wasSkipped) {
            // current position only really matters when we skip
            val position = getPosition()
            if (position >= 0) upsertBlk(curEpisode!!) { it.setPosition(position) }
        }
        val currentMedia = curEpisode
        when {
            shouldContinue -> {
                var nextMedia = getNextInQueue(currentMedia) { showStreamingNotAllowedDialog(context, PlaybackStarter(context, it).intent) }
                val playNextEpisode = isPlaying && nextMedia != null
                when {
                    playNextEpisode -> Logd(TAG, "Playback of next episode will start immediately.")
                    nextMedia == null -> Logd(TAG, "No more episodes available to play")
                    else -> Logd(TAG, "Loading next episode, but not playing automatically.")
                }
                if (nextMedia != null) {
                    onPlaybackEnded(!playNextEpisode)
                    prepareMedia(nextMedia, streaming = true, startWhenPrepared = playNextEpisode, prepareImmediately = playNextEpisode, forceReset = false)
                }
                if (currentMedia != null) {
                    var playingNext = true
                    if (nextMedia == null) {
                        remoteMediaClient?.stop()
                        playingNext = false
                    }
                    onPostPlayback(currentMedia, hasEnded, wasSkipped, playingNext)
                }
            }
            isPlaying -> onPlaybackPause(currentMedia, currentMedia?.position ?: Episode.INVALID_TIME)
        }
    }

    override fun isCasting(): Boolean = true

    companion object {
        private const val TAG = "CastMediaPlayer"

        /**
         * Converts [Episode] objects into a format suitable for sending to a Cast Device.
         * Before using this method, one should make sure isCastable(EpisodeMedia) returns
         * `true`. This method should not run on the main thread.
         *
         * @param media The [Episode] object to be converted.
         * @return [MediaInfo] object in a format proper for casting.
         */
        fun from(media: Episode?): MediaInfo? {
            if (media == null) return null
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
            val feedItem = media
            metadata.putString(MediaMetadata.KEY_TITLE, media.getEpisodeTitle())
            val subtitle = media.feed?.title?:""
            metadata.putString(MediaMetadata.KEY_SUBTITLE, subtitle)

            val feed: Feed? = feedItem.feed
            // Manual because cast does not support embedded images
            val url: String = if (feedItem.imageUrl == null && feed != null) feed.imageUrl?:"" else feedItem.imageUrl?:""
            if (url.isNotEmpty()) metadata.addImage(WebImage(url.toUri()))
            val calendar = Calendar.getInstance()
            calendar.time = feedItem.getPubDate()
            metadata.putDate(MediaMetadata.KEY_RELEASE_DATE, calendar)
            if (feed != null) {
                if (!feed.author.isNullOrEmpty()) metadata.putString(MediaMetadata.KEY_ARTIST, feed.author!!)
                if (!feed.downloadUrl.isNullOrEmpty()) metadata.putString(KEY_FEED_URL, feed.downloadUrl!!)
                if (!feed.link.isNullOrEmpty()) metadata.putString(KEY_FEED_WEBSITE, feed.link!!)
            }
            if (!feedItem.identifier.isNullOrEmpty()) metadata.putString(KEY_EPISODE_IDENTIFIER, feedItem.identifier!!)
            else metadata.putString(KEY_EPISODE_IDENTIFIER, media.downloadUrl ?: "")
            if (!feedItem.link.isNullOrEmpty()) metadata.putString(KEY_EPISODE_LINK, feedItem.link!!)

            // This field only identifies the id on the device that has the original version.
            // Idea is to perhaps, on a first approach, check if the version on the local DB with the
            // same id matches the remote object, and if not then search for episode and feed identifiers.
            // This at least should make media recognition for a single device much quicker.
            metadata.putInt(KEY_MEDIA_ID, media.id.toInt())
            // A way to identify different casting media formats in case we change it in the future and
            // senders with different versions share a casting device.
            metadata.putInt(KEY_FORMAT_VERSION, FORMAT_VERSION_VALUE)
            metadata.putString(KEY_STREAM_URL, media.downloadUrl!!)

            Logd(TAG, "media: ${media.id} ${feedItem.title}")
            Logd(TAG, "url: ${media.getMediaType()} $media.effectUrl")
            val builder = MediaInfo.Builder(media.effectUrl)
                .setEntity(media.id.toString())
                .setContentType(media.effectMimeType)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(metadata)
            if (media.duration > 0) builder.setStreamDuration(media.duration.toLong())
            return builder.build()
        }

        const val KEY_MEDIA_ID: String = "ac.mdiq.podcini.cast.MediaId"

        const val KEY_EPISODE_IDENTIFIER: String = "ac.mdiq.podcini.cast.EpisodeId"
        const val KEY_EPISODE_LINK: String = "ac.mdiq.podcini.cast.EpisodeLink"
        const val KEY_STREAM_URL: String = "ac.mdiq.podcini.cast.StreamUrl"
        const val KEY_FEED_URL: String = "ac.mdiq.podcini.cast.FeedUrl"
        const val KEY_FEED_WEBSITE: String = "ac.mdiq.podcini.cast.FeedWebsite"
        const val KEY_EPISODE_NOTES: String = "ac.mdiq.podcini.cast.EpisodeNotes"

        /**
         * The field `Podcini.FormatVersion` specifies which version of MediaMetaData
         * fields we're using. Future implementations should try to be backwards compatible with earlier
         * versions, and earlier versions should be forward compatible until the version indicated by
         * `MAX_VERSION_FORWARD_COMPATIBILITY`. If an update makes the format unreadable for
         * an earlier version, then its version number should be greater than the
         * `MAX_VERSION_FORWARD_COMPATIBILITY` value set on the earlier one, so that it
         * doesn't try to parse the object.
         */
        const val KEY_FORMAT_VERSION: String = "ac.mdiq.podcini.ui.cast.FormatVersion"
        const val FORMAT_VERSION_VALUE: Int = 1
        const val MAX_VERSION_FORWARD_COMPATIBILITY: Int = 9999

        fun isCastable(media: Episode?, castSession: CastSession?): Boolean {
            if (media == null || castSession == null || castSession.castDevice == null) return false
//        if (media is EpisodeMedia || media is RemoteMedia) {
            val url = media.downloadUrl
            if (url.isNullOrEmpty()) return false
            if (url.startsWith(ContentResolver.SCHEME_CONTENT)) return false /* Local feed */
            return when (media.getMediaType()) {
                MediaType.AUDIO -> castSession.castDevice!!.hasCapability(CastDevice.CAPABILITY_AUDIO_OUT)
                MediaType.VIDEO -> {
                    if (media.feed?.videoModePolicy ==  VideoMode.AUDIO_ONLY)
                        castSession.castDevice!!.hasCapability(CastDevice.CAPABILITY_AUDIO_OUT)
                    else castSession.castDevice!!.hasCapability(CastDevice.CAPABILITY_VIDEO_OUT)
                }
                else -> false
            }
            return false
        }
        
        /**
         * Compares a [MediaInfo] instance with a [Episode] one and evaluates whether they
         * represent the same podcast episode.
         * @param info      the [MediaInfo] object to be compared.
         * @param media     the [Episode] object to be compared.
         * @return <true>true</true> if there's a match, `false` otherwise.
         */
        fun matches(info: MediaInfo?, media: Episode?): Boolean {
            if (info == null || media == null) return false
            if (info.contentId != media.downloadUrl) return false

            val metadata = info.metadata
            if (metadata == null || metadata.getString(KEY_EPISODE_IDENTIFIER) != media.identifier) return false

            val feed: Feed? = media.feed
            return feed != null && metadata.getString(KEY_FEED_URL) == feed.downloadUrl
        }

        @SuppressLint("StaticFieldLeak")
        var castContext:  CastContext? = null
        fun getInstanceIfConnected(context: Context): MediaPlayerBase? {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return null
            try {
                if (castContext == null) castContext = CastContext.getSharedInstance(context.applicationContext)
                if (castContext?.castState == CastState.CONNECTED) return CastMediaPlayer(context) } catch (e: Exception) { Logs(TAG, e) }
            return null
        }
    }
}
