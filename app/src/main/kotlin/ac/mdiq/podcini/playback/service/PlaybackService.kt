package ac.mdiq.podcini.playback.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils.isStreamingAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowStreaming
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.*
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.loadPlayableFromPreferences
import ac.mdiq.podcini.playback.base.InTheatre.setCurEpisode
import ac.mdiq.podcini.playback.base.InTheatre.writeNoMediaPlaying
import ac.mdiq.podcini.playback.base.LocalMediaPlayer.Companion.createStaticPlayer
import ac.mdiq.podcini.playback.base.LocalMediaPlayer.Companion.exoPlayer
import ac.mdiq.podcini.playback.base.LocalMediaPlayer.Companion.isStreaming
import ac.mdiq.podcini.playback.base.LocalMediaPlayer.Companion.streaming
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.EXTRA_ALLOW_STREAM_ALWAYS
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.EXTRA_ALLOW_STREAM_THIS_TIME
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.buildMediaItem
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.currentMediaType
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.showStreamingNotAllowedDialog
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.TaskManager.Companion.taskManager
import ac.mdiq.podcini.playback.cast.CastMediaPlayer
import ac.mdiq.podcini.playback.cast.CastStateListener
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.rewindSecs
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeByGuidOrUrl
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.ui.utils.starter.MainActivityStarter
import ac.mdiq.podcini.ui.utils.starter.VideoPlayerActivityStarter
import ac.mdiq.podcini.util.*
import ac.mdiq.podcini.util.FlowEvent.PlayEvent.Action
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.bluetooth.BluetoothA2dp
import android.content.*
import android.content.Intent.EXTRA_KEY_EVENT
import android.media.AudioManager
import android.os.*
import android.os.Build.VERSION_CODES
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_STOP
import android.view.ViewConfiguration
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.session.*
import androidx.work.impl.utils.futures.SettableFuture
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max

class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(Dispatchers.Main)

    private var mediaSession: MediaLibrarySession? = null
    private val notificationCustomButtons = NotificationCustomButton.entries.map { command -> command.commandButton }
    private lateinit var castStateListener: CastStateListener

    private var clickCount = 0
    private val clickHandler = Handler(Looper.getMainLooper())

    private var isForeground = false

    private val autoStateUpdated: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Logd(TAG, "autoStateUpdated onReceive called with action: ${intent.action}")
            val status = intent.getStringExtra("media_connection_status")
            val isConnectedToCar = "media_connected" == status
            Logd(TAG, "Received Auto Connection update: $status")
            if (!isConnectedToCar) Logd(TAG, "Car was unplugged during playback.")
            else {
                val playerStatus = MediaPlayerBase.status
                when (playerStatus) {
                    PlayerStatus.PAUSED, PlayerStatus.PREPARED -> mPlayer?.resume()
                    PlayerStatus.PREPARING -> mPlayer?.startWhenPrepared?.set(!mPlayer!!.startWhenPrepared.get())
                    PlayerStatus.INITIALIZED -> {
                        mPlayer?.startWhenPrepared?.set(true)
                        mPlayer?.prepare()
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

        override fun onReceive(context: Context, intent: Intent) {
            // Don't pause playback after we just started, just because the receiver
            // delivers the current headset state (instead of a change)
            if (isInitialStickyBroadcast) return
            Logd(TAG, "headsetDisconnected onReceive called with action: ${intent.action}")

            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                Logd(TAG, "Headset plug event. State is $state")
                if (state != -1) {
                    when (state) {
                        UNPLUGGED -> Logd(TAG, "Headset was unplugged during playback.")
                        PLUGGED -> {
                            Logd(TAG, "Headset was plugged in during playback.")
                            unpauseIfPauseOnDisconnect(false)
                        }
                    }
                } else Loge(TAG, "Received invalid ACTION_HEADSET_PLUG intent")
            }
        }
    }

    private val bluetoothStateUpdated: BroadcastReceiver = object : BroadcastReceiver() {
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
            // sound is about to change, eg. bluetooth -> speaker
            Logd(TAG, "audioBecomingNoisy onReceive called with action: ${intent.action}")
            Logd(TAG, "Pausing playback because audio is becoming noisy")
//            pauseIfPauseOnDisconnect()
            transientPause = (status == PlayerStatus.PLAYING)
            if (getPref(AppPrefs.prefPauseOnHeadsetDisconnect, true) && !isCasting) mPlayer?.pause(false)
        }
    }

    private val shutdownReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Logd(TAG, "shutdownReceiver onReceive called with action: ${intent.action}")
            if (intent.action == ACTION_SHUTDOWN_PLAYBACK_SERVICE) EventFlow.postEvent(FlowEvent.PlaybackServiceEvent(FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN))
        }
    }

    val rootItem = MediaItem.Builder()
        .setMediaId("CurQueue")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setTitle(curQueue.name)
                .build())
        .build()

    val mediaItemsInQueue: MutableList<MediaItem> by lazy {
        val list = mutableListOf<MediaItem>()
        curQueue.episodes.forEach {
            val item = buildMediaItem(it)
            if (item != null) list += item
        }
        Logd(TAG, "mediaItemsInQueue: ${list.size}")
        list
    }

    inner class MediaLibrarySessionCK : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            Logd(TAG, "in MyMediaSessionCallback onConnect")
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
                NotificationCustomButton.REWIND.customAction -> mPlayer?.seekDelta(-rewindSecs * 1000)
                NotificationCustomButton.FORWARD.customAction -> mPlayer?.seekDelta(fastForwardSecs * 1000)
                NotificationCustomButton.SKIP.customAction -> if (getPref(AppPrefs.prefShowSkip, true)) mPlayer?.skip()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Logd(TAG, "MyMediaSessionCallback onPlaybackResumption ")
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
            val keyEvent = if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) intent.extras!!.getParcelable(EXTRA_KEY_EVENT, KeyEvent::class.java) else intent.extras!!.getParcelable(EXTRA_KEY_EVENT) as? KeyEvent
            Logd(TAG, "onMediaButtonEvent ${keyEvent?.keyCode}")
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                val keyCode = keyEvent.keyCode
                if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    clickCount++
                    clickHandler.removeCallbacksAndMessages(null)
                    clickHandler.postDelayed({
                        when (clickCount) {
                            1 -> handleKeycode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false)
                            2 -> mPlayer?.seekDelta(fastForwardSecs * 1000)
                            3 -> mPlayer?.seekDelta(-rewindSecs * 1000)
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
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }
        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int,
                                   params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            Logd(TAG, "MyMediaSessionCallback onGetChildren called parentId:$parentId page:$page pageSize:$pageSize")
//            return super.onGetChildren(session, browser, parentId, page, pageSize, params)
            return Futures.immediateFuture(LibraryResult.ofItemList(mediaItemsInQueue, params))
        }
        override fun onSubscribe(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
                                 params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
            Logd(TAG, "MyMediaSessionCallback onAddMediaItems called ${mediaItems.size} ${mediaItems[0]}")
            /* This is the trickiest part, if you don't do this here, nothing will play */
            val episode = getEpisodeByGuidOrUrl(null, mediaItems.first().mediaId) ?: return Futures.immediateFuture(mutableListOf())
            if (!InTheatre.isCurMedia(episode)) {
                PlaybackStarter(applicationContext, episode).start()
                EventFlow.postEvent(FlowEvent.PlayEvent(episode))
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
        taskManager = TaskManager(applicationContext)

        recreateMediaSessionIfNeeded()

        castStateListener = object : CastStateListener(this) {
            override fun onSessionStartedOrEnded() {
                recreateMediaPlayer()
            }
        }
        EventFlow.postEvent(FlowEvent.PlaybackServiceEvent(FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED))
    }

    fun recreateMediaSessionIfNeeded() {
        if (mediaSession != null) return
        Logd(TAG, "recreateMediaSessionIfNeeded")
        setMediaNotificationProvider(CustomMediaNotificationProvider(applicationContext))

        recreateMediaPlayer()
        if (exoPlayer == null) createStaticPlayer(applicationContext)
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        mediaSession = MediaLibrarySession.Builder(applicationContext, exoPlayer!!, MediaLibrarySessionCK())
            .setId(packageName)
            .setSessionActivity(pendingIntent)
            .setCustomLayout(notificationCustomButtons)
            .build()
    }

    fun recreateMediaPlayer() {
        Logd(TAG, "recreateMediaPlayer")
        val media = curEpisode
        var wasPlaying = false
        if (mPlayer != null) {
            wasPlaying = status == PlayerStatus.PLAYING
            mPlayer!!.pause(reinit = false)
            mPlayer!!.shutdown()
        }
        mPlayer = CastMediaPlayer.getInstanceIfConnected(applicationContext)
        if (mPlayer == null) mPlayer = LocalMediaPlayer(applicationContext) // Cast not supported or not connected

        Logd(TAG, "recreateMediaPlayer wasPlaying: $wasPlaying")
        if (media != null) mPlayer!!.playMediaObject(media, !media.localFileAvailable(), wasPlaying, true)
        isCasting = mPlayer!!.isCasting()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Logd(TAG, "onTaskRemoved")
        val player = mediaSession?.player ?: return
        // Stop the service if not playing, continue playing in the background otherwise.
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == STATE_ENDED) stopSelf()
    }

    override fun onDestroy() {
        Logd(TAG, "Service is about to be destroyed")
        currentMediaType = MediaType.UNKNOWN
        castStateListener.destroy()

        LocalMediaPlayer.cleanup()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer =  null
        mPlayer?.shutdown()

        cancelFlowEvents()
        unregisterReceiver(autoStateUpdated)
        unregisterReceiver(headsetDisconnected)
        unregisterReceiver(shutdownReceiver)
        unregisterReceiver(bluetoothStateUpdated)
        unregisterReceiver(audioBecomingNoisy)
        taskManager?.shutdown()

        playbackService = null
        isRunning = false
        super.onDestroy()
    }

    fun isServiceReady(): Boolean = mediaSession?.player?.playbackState != STATE_IDLE && mediaSession?.player?.playbackState != STATE_ENDED

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logd(TAG, "onStartCommand intent is null: ${intent == null} intent?.action ${intent?.action} running: $isRunning")
        if (intent == null && flags == 0) {
            Logd(TAG, "onStartCommand Service restarted by system with null intent. return")
            return START_STICKY
        }
        val keycode = intent?.getIntExtra(MediaButtonReceiver.EXTRA_KEYCODE, -1) ?: -1
        val customAction = intent?.getStringExtra(MediaButtonReceiver.EXTRA_CUSTOM_ACTION)
        val hardwareButton = intent?.getBooleanExtra(MediaButtonReceiver.EXTRA_HARDWAREBUTTON, false) == true
        val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) intent?.getParcelableExtra(EXTRA_KEY_EVENT, KeyEvent::class.java) else intent?.getParcelableExtra(EXTRA_KEY_EVENT)

        Logd(TAG, "onStartCommand flags=$flags startId=$startId keycode=$keycode keyEvent=$keyEvent customAction=$customAction hardwareButton=$hardwareButton action=${intent?.action.toString()} ${curEpisode?.getEpisodeTitle()}")
        if (keycode == -1 && curEpisode == null && customAction == null) {
            Logd(TAG, "onStartCommand PlaybackService was started with no arguments, return")
            return START_NOT_STICKY
        }
        if ((flags and START_FLAG_REDELIVERY) != 0) {
            Logd(TAG, "onStartCommand is a redelivered intent, calling stopForeground now. return")
            return START_NOT_STICKY
        }
        Logd(TAG, "onStartCommand mPlayer?.prevMedia: ${mPlayer?.prevMedia?.title}")
        Logd(TAG, "onStartCommand curEpisode: ${curEpisode?.title}")
        Logd(TAG, "onStartCommand status: $status")
        if (keycode == -1 && curEpisode != null) {
            if (mPlayer?.prevMedia?.id == curEpisode?.id) {
                Logd(TAG, "onStartCommand playing same media: $status")
//                if (status in listOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED)) return super.onStartCommand(intent, flags, startId)
                if (status in listOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED)) return START_STICKY
                Logd(TAG, "onStartCommand playing same media: $status proceed")
            }
        }
        when {
            keycode != -1 -> {
                // TODO: likely not happening
                Logd(TAG, "onStartCommand Received hardware button event: $hardwareButton")
                handleKeycode(keycode, !hardwareButton)
                return super.onStartCommand(intent, flags, startId)
            }
            keyEvent?.keyCode == KEYCODE_MEDIA_STOP -> {
                // TODO: likely not happening
                Logd(TAG, "onStartCommand Received button event: ${keyEvent.keyCode}")
                handleKeycode(keyEvent.keyCode, !hardwareButton)
                return super.onStartCommand(intent, flags, startId)
            }
            curEpisode != null -> {
                Logd(TAG, "onStartCommand starting notification")
                val CHANNEL_ID = "podcini playback service"
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(CHANNEL_ID, "Title", NotificationManager.IMPORTANCE_LOW).apply {
                        setSound(null, null)
                        enableVibration(false)
                    }
                    nm.createNotificationChannel(channel)
                }
                val notification = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).setContentTitle("").setContentText("").build()
//                if (isForeground) nm.notify(1, notification)
//                else {
//                    Logd(TAG, "onStartCommand starting foreground")
//                    startForeground(1, notification)
//                    isForeground = true
//                }
                startForeground(1, notification)
                recreateMediaSessionIfNeeded()
                val allowStreamThisTime = intent?.getBooleanExtra(EXTRA_ALLOW_STREAM_THIS_TIME, false) == true
                val allowStreamAlways = intent?.getBooleanExtra(EXTRA_ALLOW_STREAM_ALWAYS, false) == true
                if (allowStreamAlways) mobileAllowStreaming = true
                startPlaying(allowStreamThisTime)
                return START_STICKY
            }
            else -> Logd(TAG, "onStartCommand case when not (keycode != -1 and playable != null)")
        }
        return START_NOT_STICKY
    }

    /**
     * Handles media button events. return: keycode was handled
     */
    private fun handleKeycode(keycode: Int, notificationButton: Boolean): Boolean {
        Logd(TAG, "Handling keycode: $keycode")
        val info = mPlayer?.playerInfo
        val status = info?.playerStatus
        when (keycode) {
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                when {
                    status == PlayerStatus.PLAYING -> mPlayer?.pause(false)
                    status in listOf(PlayerStatus.PAUSED, PlayerStatus.PREPARED) -> mPlayer?.resume()
                    status == PlayerStatus.PREPARING -> mPlayer?.startWhenPrepared?.set(!mPlayer!!.startWhenPrepared.get())
                    status == PlayerStatus.INITIALIZED -> {
                        mPlayer?.startWhenPrepared?.set(true)
                        mPlayer?.prepare()
                    }
                    curEpisode == null -> startPlayingFromPreferences()
                    else -> return false
                }
                taskManager?.restartSleepTimer()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                when {
                    status in listOf(PlayerStatus.PAUSED, PlayerStatus.PREPARED) -> mPlayer?.resume()
                    status == PlayerStatus.INITIALIZED -> {
                        mPlayer?.startWhenPrepared?.set(true)
                        mPlayer?.prepare()
                    }
                    curEpisode == null -> startPlayingFromPreferences()
                    else -> return false
                }
                taskManager?.restartSleepTimer()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (status == PlayerStatus.PLAYING) {
                    mPlayer?.pause(false)
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                when {
                    // Handle remapped button as notification button which is not remapped again.
                    !notificationButton -> return handleKeycode(getPref(AppPrefs.prefHardwareForwardButton, "0").toInt(), true)
                    status in listOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED) -> {
                        mPlayer?.skip()
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (status in listOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED)) {
                    mPlayer?.seekDelta(fastForwardSecs * 1000)
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                when {
                    // Handle remapped button as notification button which is not remapped again.
                    !notificationButton -> return handleKeycode(getPref(AppPrefs.prefHardwarePreviousButton, "0").toInt(), true)
                    status in listOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED) -> {
                        mPlayer?.seekTo(0)
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (status in listOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED)) {
                    mPlayer?.seekDelta(-rewindSecs * 1000)
                    return true
                }
            }
            KEYCODE_MEDIA_STOP -> {
                if (status == PlayerStatus.PLAYING) mPlayer?.pause(reinit = true)
                return true
            }
            else -> {
                Logd(TAG, "Unhandled key code: $keycode")
                // only notify the user about an unknown key event if it is actually doing something
                if (info?.playable != null && info.playerStatus == PlayerStatus.PLAYING) Loge(TAG, String.format(resources.getString(R.string.unknown_media_key), keycode))
            }
        }
        return false
    }

    private fun startPlayingFromPreferences() {
        recreateMediaSessionIfNeeded()
        scope.launch {
            try {
                withContext(Dispatchers.IO) { loadPlayableFromPreferences() }
                withContext(Dispatchers.Main) { startPlaying(false) }
            } catch (e: Throwable) { Logs(TAG, e, "EpisodeMedia was not loaded from preferences. Stopping service.") }
        }
    }

    private fun startPlaying(allowStreamThisTime: Boolean) {
        Logd(TAG, "startPlaying called allowStreamThisTime: $allowStreamThisTime")
        val media = curEpisode ?: return
        val localFeed = URLUtil.isContentUrl(media.downloadUrl)
        streaming = isStreaming(media)
        if (streaming!! && !localFeed && !isStreamingAllowed && !allowStreamThisTime) {
            showStreamingNotAllowedDialog(this, PlaybackStarter(this, media).intent)
            writeNoMediaPlaying()
            return
        }
        mPlayer?.playMediaObject(media, streaming!!, startWhenPrepared = true, true)
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
                    is FlowEvent.BufferUpdateEvent -> onBufferUpdate(event)
                    is FlowEvent.SleepTimerUpdatedEvent -> onSleepTimerUpdate(event)
                    is FlowEvent.FeedChangeEvent -> onFeedPrefsChanged(event)
                    is FlowEvent.PlayerErrorEvent -> onPlayerError(event)
                    is FlowEvent.PlayEvent -> if (event.action != Action.END) setCurEpisode(event.episode)
                    is FlowEvent.EpisodeMediaEvent -> onEpisodeMediaEvent(event)
                    else -> {}
                }
            }
        }
    }

    private fun onEpisodeMediaEvent(event: FlowEvent.EpisodeMediaEvent) {
        if (event.action == FlowEvent.EpisodeMediaEvent.Action.REMOVED) {
            for (e in event.episodes) {
                if (e.id == curEpisode?.id) {
                    setCurEpisode(e)
                    mPlayer?.endPlayback(hasEnded = false, wasSkipped = true)
                    break
                }
            }
        }
    }

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        if (event.action == FlowEvent.QueueEvent.Action.REMOVED) {
//            Logd(TAG, "onQueueEvent: ending playback curEpisode ${curEpisode?.title}")
            notifyCurQueueItemsChanged()
            for (e in event.episodes) {
                if (e.id == curEpisode?.id) {
                    Logd(TAG, "onQueueEvent: queue event removed ${e.title}")
                    mPlayer?.endPlayback(hasEnded = false, wasSkipped = true)
                    break
                }
            }
        }
    }

    fun notifyCurQueueItemsChanged(range_: Int = -1) {
        val range = if (range_ > 0) range_ else curQueue.size()
        Logd(TAG, "notifyCurQueueItemsChanged curQueue: ${curQueue.id}")
        mediaSession?.notifyChildrenChanged("CurQueue", range, null)
    }

    private fun onFeedPrefsChanged(event: FlowEvent.FeedChangeEvent) {
        if (curEpisode?.feed?.id == event.feed.id) curEpisode?.feed = null
    }

    private fun onPlayerError(event: FlowEvent.PlayerErrorEvent) {
        if (status == PlayerStatus.PLAYING) mPlayer!!.pause(reinit = false)
    }

    private fun onBufferUpdate(event: FlowEvent.BufferUpdateEvent) {
        if (event.hasEnded() && curEpisode != null && curEpisode!!.duration <= 0 && (mPlayer?.getDuration()?:0) > 0) curEpisode!!.duration = mPlayer!!.getDuration()
    }

    private fun onSleepTimerUpdate(event: FlowEvent.SleepTimerUpdatedEvent) {
        when {
            event.isOver -> {
                mPlayer?.pause(reinit = true)
                mPlayer?.setVolume(1.0f, 1.0f)
            }
            event.getTimeLeft() < TaskManager.NOTIFICATION_THRESHOLD -> {
                val multiplicators = floatArrayOf(0.1f, 0.2f, 0.3f, 0.3f, 0.3f, 0.4f, 0.4f, 0.4f, 0.6f, 0.8f)
                val multiplicator = multiplicators[max(0.0, (event.getTimeLeft().toInt() / 1000).toDouble()).toInt()]
                Logd(TAG, "onSleepTimerAlmostExpired: $multiplicator")
                mPlayer?.setVolume(multiplicator, multiplicator)
            }
            event.isCancelled -> mPlayer?.setVolume(1.0f, 1.0f)
        }
    }

    /**
     * @param bluetooth true if the event for unpausing came from bluetooth
     */
    private fun unpauseIfPauseOnDisconnect(bluetooth: Boolean) {
        if (mPlayer != null && mPlayer!!.isAudioChannelInUse) {
            Logd(TAG, "unpauseIfPauseOnDisconnect() audio is in use")
            return
        }
        if (transientPause) {
            transientPause = false
            if (Build.VERSION.SDK_INT >= 31) return
            when {
                !bluetooth && getPref(AppPrefs.prefUnpauseOnHeadsetReconnect, true) -> mPlayer?.resume()
                bluetooth && getPref(AppPrefs.prefUnpauseOnBluetoothReconnect, false) -> {
                    // let the user know we've started playback again...
                    val v = applicationContext.getSystemService(VIBRATOR_SERVICE) as? Vibrator
                    v?.vibrate(500)
                    mPlayer?.resume()
                }
            }
        }
    }

    enum class NotificationCustomButton(val customAction: String, val commandButton: CommandButton) {
        SKIP(
            customAction = CUSTOM_COMMAND_SKIP_ACTION_ID,
            commandButton = CommandButton.Builder()
                .setDisplayName("Skip")
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SKIP_ACTION_ID, Bundle()))
                .setIconResId(R.drawable.ic_notification_skip)
                .build(),
        ),
        REWIND(
            customAction = CUSTOM_COMMAND_REWIND_ACTION_ID,
            commandButton = CommandButton.Builder()
                .setDisplayName("Rewind")
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REWIND_ACTION_ID, Bundle()))
                .setIconResId(R.drawable.ic_notification_fast_rewind)
                .build(),
        ),
        FORWARD(
            customAction = CUSTOM_COMMAND_FORWARD_ACTION_ID,
            commandButton = CommandButton.Builder()
                .setDisplayName("Forward")
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_FORWARD_ACTION_ID, Bundle()))
                .setIconResId(R.drawable.ic_notification_fast_forward)
                .build(),
        ),
    }
    
    class CustomMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
        override fun addNotificationActions(mediaSession: MediaSession, mediaButtons: ImmutableList<CommandButton>,
                                            builder: NotificationCompat.Builder, actionFactory: MediaNotification.ActionFactory): IntArray {
            /* Retrieving notification default play/pause button from mediaButtons list. */
            val defaultPlayPauseButton = mediaButtons.getOrNull(1)
            val defaultRestartButton = mediaButtons.getOrNull(0)
            val notificationMediaButtons = if (defaultPlayPauseButton != null) {
                /* Overriding received mediaButtons list to ensure required buttons order: [rewind15, play/pause, forward15]. */
                ImmutableList.builder<CommandButton>().apply {
                    if (defaultRestartButton != null) add(defaultRestartButton)
                    add(NotificationCustomButton.REWIND.commandButton)
                    add(defaultPlayPauseButton)
                    add(NotificationCustomButton.FORWARD.commandButton)
                    if (getPref(AppPrefs.prefShowSkip, true)) add(NotificationCustomButton.SKIP.commandButton)
                }.build()
                /* Fallback option to handle nullability, in case retrieving default play/pause button fails for some reason (should never happen). */
            } else mediaButtons
            return super.addNotificationActions(mediaSession, notificationMediaButtons, builder, actionFactory)
        }
        override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence {
            return metadata.title ?: "No title"
        }
        override fun getNotificationContentText(metadata: MediaMetadata): CharSequence {
            return metadata.subtitle ?: "No text"
        }
    }

    companion object {
        private val TAG: String = PlaybackService::class.simpleName ?: "Anonymous"

        private const val CUSTOM_COMMAND_REWIND_ACTION_ID = "1_REWIND"
        private const val CUSTOM_COMMAND_FORWARD_ACTION_ID = "2_FAST_FWD"
        private const val CUSTOM_COMMAND_SKIP_ACTION_ID = "3_SKIP"

        const val ACTION_SHUTDOWN_PLAYBACK_SERVICE: String = "action.ac.mdiq.podcini.service.actionShutdownPlaybackService"

        var playbackService: PlaybackService? = null
        var mediaBrowser: MediaBrowser? = null

        @JvmField
        var isRunning: Boolean = false

        @JvmStatic
        @Volatile
        var isCasting: Boolean = false
            internal set

        /**
         * Is true if the service was running, but paused due to headphone disconnect
         */
        private var transientPause = false

        val isPlayingVideoLocally: Boolean
            get() = when {
                isCasting -> false
                playbackService != null -> currentMediaType == MediaType.VIDEO
                else -> curEpisode?.getMediaType() == MediaType.VIDEO
            }

        /**
         * Returns an intent which starts an audio- or videoplayer, depending on the
         * type of media that is being played or the medaitype that is provided as an argument.
         * If the playbackservice is not running, the type of the last played media will be looked up.
         */
        @JvmStatic
        fun getPlayerActivityIntent(context: Context, mediaType_: MediaType? = null): Intent {
            val mediaType = mediaType_ ?: currentMediaType
            val showVideoPlayer = if (isRunning) mediaType == MediaType.VIDEO && !isCasting else curState.curIsVideo
            return if (showVideoPlayer) VideoPlayerActivityStarter(context).intent
            else MainActivityStarter(context).withOpenPlayer().getIntent()
        }
    }
}
