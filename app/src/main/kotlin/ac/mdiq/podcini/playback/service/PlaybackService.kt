package ac.mdiq.podcini.playback.service

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.activeTheatres
import ac.mdiq.podcini.playback.base.InTheatre.startTheatres
import ac.mdiq.podcini.playback.base.InTheatre.theatres
import ac.mdiq.podcini.playback.base.Media3Player
import ac.mdiq.podcini.playback.base.Media3Player.Companion.buildMetadata
import ac.mdiq.podcini.playback.base.Media3Player.Companion.releaseCache
import ac.mdiq.podcini.playback.base.SleepManager
import ac.mdiq.podcini.playback.base.SleepManager.Companion.sleepManager
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.episodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.fastForwardSecs
import ac.mdiq.podcini.storage.database.rewindSecs
import ac.mdiq.podcini.storage.utils.toSafeUri
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logpe
import ac.mdiq.podcini.utils.Logps
import ac.mdiq.podcini.utils.Logpt
import ac.mdiq.podcini.utils.timeIt
import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.bluetooth.BluetoothA2dp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_KEY_EVENT
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_STOP
import android.view.ViewConfiguration
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.work.impl.utils.futures.SettableFuture
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(Dispatchers.Main)

    private var mediaSession: MediaLibrarySession? = null
    private val notificationCustomButtons = NotificationCustomButton.entries.map { command -> command.commandButton }

    private var clickCount = 0
    private val clickHandler = Handler(Looper.getMainLooper())

    private val autoStateUpdated: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (theatres[0].mPlayer == null) return
            Logd(TAG, "autoStateUpdated onReceive called with action: ${intent.action}")
            val status = intent.getStringExtra("media_connection_status")
            Logd(TAG, "Received Auto Connection update: $status")
            if ("media_connected" != status) Logd(TAG, "Car was unplugged during playback.")
            else {
                when  {
                    theatres[0].mPlayer!!.isPaused || theatres[0].mPlayer!!.isPrepared -> theatres[0].mPlayer?.play()
                    theatres[0].mPlayer!!.isPreparing -> {
                        val value = theatres[0].mPlayer!!.isStartWhenPrepared
                        theatres[0].mPlayer?.isStartWhenPrepared = !value
                    }
                    theatres[0].mPlayer!!.isInitialized -> {
                        theatres[0].mPlayer?.isStartWhenPrepared = true
                        theatres[0].mPlayer?.prepare()
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Pauses playback when the headset is disconnected and the preference set
     */
    private val headsetDisconnected: BroadcastReceiver = object : BroadcastReceiver() {
        private val TAG = "headsetDisconnected"
        private val UNPLUGGED = 0
        private val PLUGGED = 1

        @RequiresPermission(Manifest.permission.VIBRATE)
        override fun onReceive(context: Context, intent: Intent) {
            // Don't pause playback after we just started, just because the receiver
            // delivers the current headset state (instead of a change)
            if (isInitialStickyBroadcast) return
            Logd(TAG, "headsetDisconnected onReceive called with action: ${intent.action}")

            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                Logd(TAG, "Headset plug event. State is $state")
                when (state) {
                    -1 -> Logpe(TAG, theatres[0].mPlayer?.curEpisode,"Received invalid ACTION_HEADSET_PLUG intent")
                    UNPLUGGED -> {}
                    PLUGGED -> unpauseIfPauseOnDisconnect(false)
                }
            }
        }
    }

    private val bluetoothStateUpdated: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.VIBRATE)
        override fun onReceive(context: Context, intent: Intent) {
            Logd(TAG, "bluetoothStateUpdated onReceive called with action: ${intent.action}")
            if (intent.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
                if (state == BluetoothA2dp.STATE_CONNECTED) {
                    Logd(TAG, "Received bluetooth connection intent")
                    unpauseIfPauseOnDisconnect(true)
                }
            }
        }
    }

    private val audioBecomingNoisy: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (theatres[0].mPlayer == null) return
            // sound is about to change, eg. bluetooth -> speaker
            Logd(TAG, "audioBecomingNoisy onReceive called with action: ${intent.action}")
            Logd(TAG, "Pausing playback because audio is becoming noisy")
//            pauseIfPauseOnDisconnect()
            transientPause = theatres[0].mPlayer!!.isPlaying
            if (appPrefs.pauseOnHeadsetDisconnect && !isCasting) theatres[0].mPlayer?.pause(false)
        }
    }

    private val shutdownReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Logd(TAG, "shutdownReceiver onReceive called with action: ${intent.action}")
            if (intent.action == ACTION_SHUTDOWN_PLAYBACK_SERVICE) EventFlow.postEvent(FlowEvent.PlaybackServiceEvent(FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN))
        }
    }

    inner class MediaLibrarySessionCK : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            Logd(TAG, "in MyMediaSessionCallback onConnect")
            isAutoController = controller.packageName == "com.google.android.projection.gearhead" || controller.packageName == "com.google.android.apps.automotive.templates.host"
            when {
                session.isMediaNotificationController(controller) -> {
                    Logd(TAG, "MyMediaSessionCallback onConnect isMediaNotificationController")
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    notificationCustomButtons.forEach { commandButton ->
                        Logd(TAG, "MyMediaSessionCallback onConnect commandButton ${commandButton.displayName}")
                        commandButton.sessionCommand?.let(sessionCommands::add)
                    }
                    return MediaSession.ConnectionResult.accept(sessionCommands.build(), playerCommands.build())
                }
                session.isAutoCompanionController(controller) -> {
                    Logd(TAG, "MyMediaSessionCallback onConnect isAutoCompanionController")
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    notificationCustomButtons.forEach { commandButton ->
                        Logd(TAG, "MyMediaSessionCallback onConnect commandButton ${commandButton.displayName}")
                        commandButton.sessionCommand?.let(sessionCommands::add)
                    }
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands.build())
                        .build()
                }
                else -> {
                    Logd(TAG, "MyMediaSessionCallback onConnect other controller: $controller")
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                }
            }
        }
        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onPostConnect(session, controller)
            Logd(TAG, "MyMediaSessionCallback onPostConnect")
            if (notificationCustomButtons.isNotEmpty()) {
                mediaSession?.setCustomLayout(notificationCustomButtons)
//                mediaSession?.setCustomLayout(customMediaNotificationProvider.notificationMediaButtons)
            }
        }
        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            Logd(TAG, "MyMediaSessionCallback onCustomCommand ${customCommand.customAction}")
            /* Handling custom command buttons from player notification. */
            when (customCommand.customAction) {
                NotificationCustomButton.REWIND.customAction -> theatres[0].mPlayer?.seekDelta(-rewindSecs * 1000)
                NotificationCustomButton.FORWARD.customAction -> theatres[0].mPlayer?.seekDelta(fastForwardSecs * 1000)
                NotificationCustomButton.SKIP.customAction -> if (appPrefs.showSkip) theatres[0].mPlayer?.skip()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Logd(TAG, "MyMediaSessionCallback onPlaybackResumption isForPlayback: $isForPlayback")
            val settable = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
//            scope.launch {
//                // Your app is responsible for storing the playlist and the start position to use here
//                val resumptionPlaylist = restorePlaylist()
//                settable.set(resumptionPlaylist)
//            }
            return settable
        }
        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            Logd(TAG, "in MyMediaSessionCallback onDisconnected")
            when {
                session.isMediaNotificationController(controller) -> {
                    Logd(TAG, "MyMediaSessionCallback onDisconnected isMediaNotificationController")
                }
                session.isAutoCompanionController(controller) -> {
                    Logd(TAG, "MyMediaSessionCallback onDisconnected isAutoCompanionController")
                }
            }
        }
        override fun onMediaButtonEvent(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, intent: Intent): Boolean {
            val keyEvent = if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) intent.extras!!.getParcelable(EXTRA_KEY_EVENT, KeyEvent::class.java)
            else {
                @Suppress("DEPRECATION")
                intent.extras!!.getParcelable(EXTRA_KEY_EVENT) as? KeyEvent
            }
            Logd(TAG, "onMediaButtonEvent ${keyEvent?.keyCode}")
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                val keyCode = keyEvent.keyCode
                if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    clickCount++
                    clickHandler.removeCallbacksAndMessages(null)
                    clickHandler.postDelayed({
                        when (clickCount) {
                            1 -> handleKeycode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false)
                            2 -> theatres[0].mPlayer?.seekDelta(fastForwardSecs * 1000)
                            3 -> theatres[0].mPlayer?.seekDelta(-rewindSecs * 1000)
                        }
                        clickCount = 0
                    }, ViewConfiguration.getDoubleTapTimeout().toLong())
                    return true
                } else return handleKeycode(keyCode, false)
            }
            return false
        }
        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            Logd(TAG, "MyMediaSessionCallback onGetItem called mediaId:$mediaId")
            return super.onGetItem(session, browser, mediaId)
        }
        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            Logd(TAG, "MyMediaSessionCallback onGetLibraryRoot called")
            val rootItem: MediaItem = MediaItem.Builder().setMediaId("ActQueue")
                .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).setTitle(actQueue.name).build())
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }
        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int,
                                   params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            Logd(TAG, "MyMediaSessionCallback onGetChildren called parentId:$parentId page:$page pageSize:$pageSize")
//            return super.onGetChildren(session, browser, parentId, page, pageSize, params)
            val mediaItemsInQueue: MutableList<MediaItem> by lazy {
                val list = mutableListOf<MediaItem>()
                actQueue.episodesSorted.forEach { e-> e.downloadUrl?.let { list += MediaItem.Builder().setMediaId(it).setUri(it.toSafeUri()).setMediaMetadata(buildMetadata(e)).build() } }
                Logd(TAG, "mediaItemsInQueue: ${list.size}")
                list
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(mediaItemsInQueue, params))
        }
        override fun onSubscribe(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
                                 params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
            Logd(TAG, "MyMediaSessionCallback onAddMediaItems called ${mediaItems.size} ${mediaItems[0]}")
            // TODO check this out
            /* This is the trickiest part, if you don't do this here, nothing will play */
            val episode = episodeByGuidOrUrl(null, mediaItems.first().mediaId, copy = false) ?: return Futures.immediateFuture(mutableListOf())
            if (!InTheatre.isCurMedia(episode)) {
                for (i in 0..1) {
                    if (episode.id != theatres[i].mPlayer?.curEpisode?.id) continue
                    PlaybackStarter(episode).start(i)
                }
            }
            val updatedMediaItems = mediaItems.map { it.buildUpon().setUri(it.mediaId).build() }.toMutableList()
//            updatedMediaItems += mediaItemsInQueue
//            Logd(TAG, "MyMediaSessionCallback onAddMediaItems updatedMediaItems: ${updatedMediaItems.size} ")
            return Futures.immediateFuture(updatedMediaItems)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Logd(TAG, "onCreate Service created.")
        timeIt("$TAG onCreate Service")

        isRunning = true
        playbackService = this

        if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            registerReceiver(autoStateUpdated, IntentFilter("com.google.android.gms.car.media.STATUS"), RECEIVER_NOT_EXPORTED)
            registerReceiver(shutdownReceiver, IntentFilter(ACTION_SHUTDOWN_PLAYBACK_SERVICE), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(autoStateUpdated, IntentFilter("com.google.android.gms.car.media.STATUS"))
            registerReceiver(shutdownReceiver, IntentFilter(ACTION_SHUTDOWN_PLAYBACK_SERVICE))
        }

        registerReceiver(headsetDisconnected, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        registerReceiver(bluetoothStateUpdated, IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED))
        registerReceiver(audioBecomingNoisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        procFlowEvents()
        sleepManager = SleepManager()

        if (mediaSession == null) createMediaSessionAndPlayers()

        EventFlow.postEvent(FlowEvent.PlaybackServiceEvent(FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED))
        timeIt("$TAG onCreate Service end")
    }

    fun createMediaSessionAndPlayers() {
        Logd(TAG, "recreateMediaSession")
        setMediaNotificationProvider(CustomMediaNotificationProvider())

        recreateMediaPlayers()
        startTheatres()

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        mediaSession = MediaLibrarySession.Builder(applicationContext, theatres[0].mPlayer!!.castPlayer!!, MediaLibrarySessionCK())
            .setId(packageName)
            .setSessionActivity(pendingIntent)
            .setCustomLayout(notificationCustomButtons)
            .build()
    }

    fun recreateMediaPlayers() {
        for (id in 0..<activeTheatres) {
            Logd(TAG, "recreateMediaPlayer creating player $id of $activeTheatres")
            var wasPlaying = false
            if (theatres[id].mPlayer != null) {
                wasPlaying = theatres[id].mPlayer!!.isPlaying
                if (wasPlaying) theatres[id].mPlayer!!.pause(reinit = false)
                theatres[id].mPlayer!!.shutdown()
            }
            theatres[id].mPlayer = Media3Player(id, if (activeTheatres > 1) { if (id == 0) -1 else 1} else 0)
        }
    }

    fun switchPlayersMode() {
        recreateMediaPlayers()
        startTheatres()
        mediaSession?.player = theatres[0].mPlayer!!.castPlayer!!
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Logd(TAG, "onTaskRemoved")
        val player = mediaSession?.player ?: return
        // Stop the service if not playing, continue playing in the background otherwise.
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == STATE_ENDED) stopSelf()
    }

    override fun onDestroy() {
        Logd(TAG, "Service is about to be destroyed")
        theatres[0].mPlayer?.onDestroy()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        theatres[1].mPlayer?.onDestroy()

        cancelFlowEvents()
        unregisterReceiver(autoStateUpdated)
        unregisterReceiver(headsetDisconnected)
        unregisterReceiver(shutdownReceiver)
        unregisterReceiver(bluetoothStateUpdated)
        unregisterReceiver(audioBecomingNoisy)
        sleepManager?.disable()

        releaseCache()

        InTheatre.cleanup()
        playbackService = null
        isRunning = false
        super.onDestroy()
    }

    fun isServiceReady(): Boolean = mediaSession?.player?.playbackState != STATE_IDLE && mediaSession?.player?.playbackState != STATE_ENDED

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    /**
     * Handles media button events. return: keycode was handled
     */
    private fun handleKeycode(keycode: Int, notificationButton: Boolean): Boolean {
        if (theatres[0].mPlayer == null) return false
        Logpt(TAG, theatres[0].mPlayer?.curEpisode, "Handling keycode: $keycode")
        // TODO: check out this
        fun startPlayingFromPreferences() {
            if (mediaSession == null) createMediaSessionAndPlayers()
            try {
                startTheatres()
                theatres[0].mPlayer?.startPlaying()
            } catch (e: Throwable) { Logps(TAG, theatres[0].mPlayer?.curEpisode, e, "EpisodeMedia was not loaded from preferences.") }
        }
        when (keycode) {
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                when {
                    theatres[0].mPlayer!!.isPlaying -> theatres[0].mPlayer?.pause(false)
                    theatres[0].mPlayer!!.isPlaying || theatres[0].mPlayer!!.isPrepared -> theatres[0].mPlayer?.play()
                    theatres[0].mPlayer!!.isPreparing -> theatres[0].mPlayer?.isStartWhenPrepared = !theatres[0].mPlayer!!.isStartWhenPrepared
                    theatres[0].mPlayer!!.isInitialized -> {
                        theatres[0].mPlayer?.isStartWhenPrepared = true
                        theatres[0].mPlayer?.prepare()
                    }
                    theatres[0].mPlayer?.curEpisode == null -> startPlayingFromPreferences()
                    else -> return false
                }
                sleepManager?.restart()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                when {
                    theatres[0].mPlayer!!.isPlaying || theatres[0].mPlayer!!.isPrepared -> theatres[0].mPlayer?.play()
                    theatres[0].mPlayer!!.isInitialized -> {
                        theatres[0].mPlayer?.isStartWhenPrepared = true
                        theatres[0].mPlayer?.prepare()
                    }
                    theatres[0].mPlayer?.curEpisode == null -> startPlayingFromPreferences()
                    else -> return false
                }
                sleepManager?.restart()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (theatres[0].mPlayer!!.isPlaying) {
                    theatres[0].mPlayer?.pause(false)
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                when {
                    // Handle remapped button as notification button which is not remapped again.
                    !notificationButton -> return handleKeycode(appPrefs.hardwareForwardButton.toInt(), true)
                    theatres[0].mPlayer!!.isPlaying || theatres[0].mPlayer!!.isPaused -> {
                        theatres[0].mPlayer?.skip()
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (theatres[0].mPlayer!!.isPlaying || theatres[0].mPlayer!!.isPaused) {
                    theatres[0].mPlayer?.seekDelta(fastForwardSecs * 1000)
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                when {
                    // Handle remapped button as notification button which is not remapped again.
                    !notificationButton -> return handleKeycode(appPrefs.hardwarePreviousButton.toInt(), true)
                    theatres[0].mPlayer!!.isPlaying || theatres[0].mPlayer!!.isPaused -> {
                        theatres[0].mPlayer?.seekTo(0)
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (theatres[0].mPlayer!!.isPlaying || theatres[0].mPlayer!!.isPaused) {
                    theatres[0].mPlayer?.seekDelta(-rewindSecs * 1000)
                    return true
                }
            }
            KEYCODE_MEDIA_STOP -> {
                if (theatres[0].mPlayer!!.isPlaying) theatres[0].mPlayer?.pause(reinit = true)
                return true
            }
            else -> {
                Logd(TAG, "Unhandled key code: $keycode")
                // only notify the user about an unknown key event if it is actually doing something
                if (theatres[0].mPlayer?.curEpisode != null && theatres[0].mPlayer!!.isPlaying) Logpe(TAG, theatres[0].mPlayer?.curEpisode, resources.getString(R.string.unknown_media_key, keycode))
            }
        }
        return false
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink == null) eventSink = scope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.QueueEvent -> onQueueEvent(event)
                    is FlowEvent.BufferUpdateEvent -> for (i in 0..1) theatres[i].mPlayer?.onBufferUpdate(event)
                    is FlowEvent.SleepTimerUpdatedEvent -> for (i in 0..1) theatres[i].mPlayer?.onSleepTimerUpdate(event)
                    is FlowEvent.EpisodeMediaEvent -> for (i in 0..1) theatres[i].mPlayer?.onEpisodeMediaEvent(event)   // TODO
                    else -> {}
                }
            }
        }
    }

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        if (event.action == FlowEvent.QueueEvent.Action.REMOVED) {
            mediaSession?.notifyChildrenChanged("ActQueue", actQueue.size(), null)
            for (e in event.episodes) {
                for (i in 0..1) {
                    if (e.id == theatres[i].mPlayer?.curEpisode?.id) {
                        Logd(TAG, "onQueueEvent: queue event removed ${e.title}")
                        theatres[i].mPlayer?.endPlayback(hasEnded = false, wasSkipped = true, shouldContinue = theatres[i].mPlayer!!.isPlaying)
                        break
                    }
                }
            }
        } else if (event.action == FlowEvent.QueueEvent.Action.CLEARED) {
            mediaSession?.notifyChildrenChanged("ActQueue", 0, null)
            for (i in 0..1) theatres[i].mPlayer?.endPlayback(hasEnded = false, wasSkipped = true, shouldContinue = theatres[i].mPlayer!!.isPlaying)
        }
    }

    /**
     * @param bluetooth true if the event for unpausing came from bluetooth
     */
    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun unpauseIfPauseOnDisconnect(bluetooth: Boolean) {
        if (theatres[0].mPlayer != null) {
            val audioManager = getAppContext().getSystemService(AUDIO_SERVICE) as AudioManager
            if (audioManager.mode != AudioManager.MODE_NORMAL || audioManager.isMusicActive) {
                Logd(TAG, "unpauseIfPauseOnDisconnect() audio is in use")
                return
            }
        }
        if (transientPause) {
            transientPause = false
            // TODO: need to handle for 31?
//            if (Build.VERSION.SDK_INT >= 31) return
            when {
                !bluetooth && appPrefs.unpauseOnHeadsetReconnect -> theatres[0].mPlayer?.play()
                bluetooth && appPrefs.unpauseOnBluetoothReconnect -> {
                    val vibrator = if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                        val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        manager.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        getSystemService(VIBRATOR_SERVICE) as Vibrator
                    }
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    theatres[0].mPlayer?.play()
                }
            }
        }
    }

    enum class NotificationCustomButton(val customAction: String, val commandButton: CommandButton) {
        SKIP(customAction = CUSTOM_COMMAND_SKIP_ACTION_ID,
            commandButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("Skip")
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SKIP_ACTION_ID, Bundle()))
                .setCustomIconResId(R.drawable.ic_notification_skip)
                .build(),
        ),
        REWIND(customAction = CUSTOM_COMMAND_REWIND_ACTION_ID,
            commandButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("Rewind")
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REWIND_ACTION_ID, Bundle()))
                .setCustomIconResId(R.drawable.ic_notification_fast_rewind)
                .build(),
        ),
        FORWARD(customAction = CUSTOM_COMMAND_FORWARD_ACTION_ID,
            commandButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("Forward")
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_FORWARD_ACTION_ID, Bundle()))
                .setCustomIconResId(R.drawable.ic_notification_fast_forward)
                .build(),
        ),
        RESTART(customAction = CUSTOM_COMMAND_RESTART_ACTION_ID,
            commandButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("Restart")
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_RESTART_ACTION_ID, Bundle()))
                .setCustomIconResId(R.drawable.baseline_skip_previous_24)
                .build(),
        ),
    }

    class CustomMediaNotificationProvider : DefaultMediaNotificationProvider(getAppContext()) {
        override fun addNotificationActions(mediaSession: MediaSession, mediaButtons: ImmutableList<CommandButton>, builder: NotificationCompat.Builder, actionFactory: MediaNotification.ActionFactory): IntArray {
            val defaultPlayPauseButton = mediaButtons.getOrNull(1)
            val notificationMediaButtons = ImmutableList.builder<CommandButton>().apply {
                add(NotificationCustomButton.RESTART.commandButton)
                add(NotificationCustomButton.REWIND.commandButton)
                if (defaultPlayPauseButton != null) add(defaultPlayPauseButton)
                add(NotificationCustomButton.FORWARD.commandButton)
                if (appPrefs.showSkip) add(NotificationCustomButton.SKIP.commandButton)
            }.build()
            return super.addNotificationActions(mediaSession, notificationMediaButtons, builder, actionFactory)
        }
        override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence = metadata.title ?: "No title"
        override fun getNotificationContentText(metadata: MediaMetadata): CharSequence = metadata.subtitle ?: "No text"
    }

    companion object {
        private val TAG: String = PlaybackService::class.simpleName ?: "Anonymous"

        var isAutoController: Boolean = false

        private const val CHANNEL_ID = "podcini playback service"

        private const val CUSTOM_COMMAND_SKIP_ACTION_ID = "ac.mdiq.podcini.SKIP"
        private const val CUSTOM_COMMAND_REWIND_ACTION_ID = "ac.mdiq.podcini.REWIND"
        private const val CUSTOM_COMMAND_FORWARD_ACTION_ID = "ac.mdiq.podcini.FORWARD"
        private const val CUSTOM_COMMAND_RESTART_ACTION_ID = "ac.mdiq.podcini.RESTART"

        const val ACTION_SHUTDOWN_PLAYBACK_SERVICE: String = "action.ac.mdiq.podcini.service.actionShutdownPlaybackService"

        var playbackService: PlaybackService? = null
        var mediaBrowser: MediaBrowser? = null

        var isRunning: Boolean = false

        var isCasting: Boolean = false
            internal set

        var episodeChangedWhenScreenOff: Boolean = false

        /**
         * Is true if the service was running, but paused due to headphone disconnect
         */
        private var transientPause = false
    }
}
