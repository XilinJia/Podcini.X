package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.playback.base.InTheatre.vCtrlFuture
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMediaId
import ac.mdiq.podcini.playback.base.InTheatre.vController
import ac.mdiq.podcini.playback.base.LocalMediaPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.TaskManager.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.cast.BaseActivity
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isCasting
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.preferences.AppPreferences.videoPlayMode
import ac.mdiq.podcini.preferences.SleepTimerPreferences.SleepTimerDialog
import ac.mdiq.podcini.preferences.ThemeSwitcher.getNoTitleTheme
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.compose.ChaptersDialog
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.PlaybackSpeedFullDialog
import ac.mdiq.podcini.ui.compose.ShareDialog
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.compose.isLightTheme
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.starter.MainActivityStarter
import ac.mdiq.podcini.ui.utils.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.toastMassege
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowCompat.getInsetsController
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoplayerActivity : BaseActivity() {
    var switchToAudioOnly = false

//    private var controllerFuture: ListenableFuture<MediaController>? = null

    private var cleanedNotes by mutableStateOf<String?>(null)
    private var feedTitle by mutableStateOf("")
    private var episodeTitle by mutableStateOf("")
    private var showAcrionBar by mutableStateOf(false)
    var landscape by mutableStateOf(false)

    var showChapterDialog by mutableStateOf(false)
    var showAudioControlDialog by mutableStateOf(false)
    var showSpeedDialog by mutableStateOf(false)
//    val showErrorDialog = mutableStateOf(false)
//    var errorMessage by mutableStateOf("")

    var showSleepTimeDialog by mutableStateOf(false)

    var showShareDialog by mutableStateOf(false)

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getNoTitleTheme(this))
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        var vmCode = 0
        if (curEpisode != null) {
            val media_ = curEpisode!!
            var vPol = media_.feed?.videoModePolicy
            if (vPol != null) {
                if (vPol == VideoMode.AUDIO_ONLY && media_.forceVideo) vPol = VideoMode.WINDOW_VIEW
                if (vPol != VideoMode.NONE) vmCode = vPol.code
            }
        }
        Logd(TAG, "onCreate vmCode: $vmCode")
        if (vmCode == 0) vmCode = videoPlayMode
        Logd(TAG, "onCreate vmCode: $vmCode")
        videoMode = VideoMode.entries.toTypedArray().getOrElse(vmCode) { VideoMode.WINDOW_VIEW }
        if (videoMode == VideoMode.AUDIO_ONLY) {
            switchToAudioOnly = true
            finish()
        }
        if (videoMode != VideoMode.FULL_SCREEN_VIEW && videoMode != VideoMode.WINDOW_VIEW) {
            Logd(TAG, "videoMode not selected, use window mode")
            videoMode = VideoMode.WINDOW_VIEW
        }
        landscape = videoMode == VideoMode.FULL_SCREEN_VIEW
        super.onCreate(savedInstanceState)
        setForVideoMode()

        setContent {
            CustomTheme(this) {
                if (showChapterDialog) ChaptersDialog(curEpisode!!, onDismissRequest = { showChapterDialog = false })
                if (showAudioControlDialog) PlaybackControlsDialog(onDismiss = { showAudioControlDialog = false })
                if (showSpeedDialog) PlaybackSpeedFullDialog(settingCode = booleanArrayOf(true, true, true), indexDefault = 0, maxSpeed = 3f, onDismiss = { showSpeedDialog = false })
                if (showShareDialog) {
                    val feedItem = curEpisode
                    if (feedItem != null) ShareDialog(feedItem, this@VideoplayerActivity) { showShareDialog = false }
                    else showShareDialog = false
                }

                if (showSleepTimeDialog) SleepTimerDialog { showSleepTimeDialog = false }

                LaunchedEffect(curMediaId) { cleanedNotes = null }
                if (toastMassege.isNotBlank()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
                if (commonConfirm != null) CommonConfirmDialog(commonConfirm!!)
                Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                    if (landscape) Box(modifier = Modifier.fillMaxSize()) { VideoPlayer() }
                    else {
                        val textColor = MaterialTheme.colorScheme.onSurface
                        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f)) { VideoPlayer() }
                            Text(curEpisode?.getEpisodeTitle() ?: "", color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(horizontal = 10.dp))
                            Text(curEpisode?.feed?.title ?: "", color = textColor, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 10.dp))
                            MediaDetails()
                        }
                    }
                }
            }
        }
        setForVideoMode()
    }

    private fun setForVideoMode() {
        Logd(TAG, "setForVideoMode videoMode: $videoMode")
        when (videoMode) {
            VideoMode.FULL_SCREEN_VIEW -> hideSystemUI()
            VideoMode.WINDOW_VIEW -> showSystemUI()
            else -> {}
        }
        val flags = window?.attributes?.flags
        Logd(TAG, "Current Flags: $flags")
    }

    private fun showSystemUI() {
        val win = window ?: return
        WindowCompat.setDecorFitsSystemWindows(win, true)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            win.decorView.apply { systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE }
            val insetsController = getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = isLightTheme(this)
        } else {
            val decorView = win.decorView
            decorView.post {
                WindowCompat.setDecorFitsSystemWindows(win, true)
                win.insetsController?.apply {
                    show(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
                    if (isLightTheme(this@VideoplayerActivity)) {
                        setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    } else {
                        setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    }
                }
            }
//            win.insetsController?.apply {
//                show(WindowInsetsCompat.Type.systemBars())
//                systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
//                if (isLightTheme(this@VideoplayerActivity)) {
//                    setSystemBarsAppearance(
//                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
//                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
//                } else {
//                    setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
//                }
//            }
        }
    }

    fun hideSystemUI() {
        val win = window ?: return
        WindowCompat.setDecorFitsSystemWindows(win, false)
        WindowInsetsControllerCompat(win, win.decorView).hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            win.decorView.apply { systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN }
        } else {
            win.insetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    fun VideoPlayer() {
        AndroidView(modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                PlayerView(context).apply {
                    this.player = LocalMediaPlayer.exoPlayer
                    useController = true
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            showAcrionBar = visibility == View.VISIBLE
                        }
                    )
                }
            }
        )
    }

    @UnstableApi
    @Composable
    fun MediaDetails() {
//        val textColor = MaterialTheme.colorScheme.onSurface
        if (cleanedNotes == null) loadMediaInfo()
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            ShownotesWebView(context).apply {
                setTimecodeSelectedListener { time: Int -> mPlayer?.seekTo(time) }
                setPageFinishedListener { postDelayed({ }, 50) }
            }
        }, update = { webView -> webView.loadDataWithBaseURL("https://127.0.0.1", if (cleanedNotes.isNullOrBlank()) "No notes" else cleanedNotes!!, "text/html", "utf-8", "about:blank") })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        videoMode = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) VideoMode.FULL_SCREEN_VIEW else VideoMode.WINDOW_VIEW
        setForVideoMode()
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        Logd(TAG, "onConfigurationChanged landscape: $landscape")
    }

    @UnstableApi
    override fun onResume() {
        super.onResume()
        setForVideoMode()
        switchToAudioOnly = false
        if (isCasting) {
            val intent = getPlayerActivityIntent(this)
            if (intent.component?.className != VideoplayerActivity::class.java.name) {
                finish()
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        window?.let {
            val insetsController = getInsetsController(it, it.decorView)
            insetsController.show(WindowInsetsCompat.Type.statusBars())
            insetsController.show(WindowInsetsCompat.Type.navigationBars())
            it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (vCtrlFuture != null) MediaController.releaseFuture(vCtrlFuture!!)
        super.onDestroy()
    }

    public override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isInPictureInPictureMode) compatEnterPictureInPicture()
    }

    @UnstableApi
    override fun onStart() {
        super.onStart()
        procFlowEvents()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        vCtrlFuture = MediaController.Builder(this, sessionToken).buildAsync()
        vCtrlFuture?.addListener({
            vController = vCtrlFuture!!.get()
//            Logd(TAG, "controllerFuture.addListener: $mediaController")
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    private var loadItemsRunning = false

    @UnstableApi
    private fun loadMediaInfo() {
        Logd(TAG, "loadMediaInfo called")
        if (curEpisode == null) return
        if (MediaPlayerBase.status == PlayerStatus.PLAYING && !isPlayingVideoLocally) {
            Logd(TAG, "Closing, no longer video")
            finish()
            MainActivityStarter(this).withOpenPlayer().start()
            return
        }
        if (!loadItemsRunning) {
            loadItemsRunning = true
            lifecycleScope.launch {
                try {
                    val episode = withContext(Dispatchers.IO) {
                        var episode_ = curEpisode
                        if (episode_ != null) {
                            val result = gearbox.buildCleanedNotes(episode_, ShownotesCleaner(this@VideoplayerActivity))
                            episode_ = result.first
                            cleanedNotes = result.second
                        }
                        episode_
                    }
                } catch (e: Throwable) { Logs(TAG, e, "loadMediaInfo failed")
                } finally { loadItemsRunning = false }
            }
        }
        val media = curEpisode
        if (media != null) {
            feedTitle = media.feed?.title?:""
            episodeTitle = media.getEpisodeTitle()
        }
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    // TODO
//                    is FlowEvent.SleepTimerUpdatedEvent -> if (event.isCancelled || event.wasJustEnabled()) supportInvalidateOptionsMenu()
                    is FlowEvent.PlaybackServiceEvent -> if (event.action == FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) finish()
                    is FlowEvent.MessageEvent -> onMessageEvent(event)
                    else -> {}
                }
            }
        }
    }

    private fun onMessageEvent(event: FlowEvent.MessageEvent) {
        commonConfirm = CommonConfirmAttrib(
            title = "",
            message = event.message,
            confirmRes = android.R.string.ok,
            cancelRes = R.string.cancel_label,
            onConfirm = { event.action?.invoke(this) })
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val buttonColor = Color(0xDDFFD700)
        if (showAcrionBar) TopAppBar(title = {
            if (landscape) Column {
                Text(text = episodeTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = feedTitle, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else { Text("")}
        },
            navigationIcon = { IconButton(onClick = { finish() }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } },
            actions = {
                if (!landscape) {
                    var sleepIconRes by remember { mutableIntStateOf(if (!isSleepTimerActive()) R.drawable.ic_sleep else R.drawable.ic_sleep_off) }
                    IconButton(onClick = { showSleepTimeDialog = true
                    }) { Icon(imageVector = ImageVector.vectorResource(sleepIconRes), contentDescription = "sleeper") }
                    IconButton(onClick = { showSpeedDialog = true
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playback_speed), contentDescription = "open podcast") }
                    IconButton(onClick = {
                        switchToAudioOnly = true
                        curEpisode?.forceVideo = false
                        finish()
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_audiotrack_24), contentDescription = "audio only") }
                    if (curEpisode != null) IconButton(onClick = {
                        val feedItem = curEpisode
                        if (feedItem != null) startActivity(MainActivity.getIntentToOpenFeed(this@VideoplayerActivity, feedItem.feedId!!))
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_feed), contentDescription = "open podcast") }
                    IconButton(onClick = { showShareDialog = true
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_share), contentDescription = "share") }
                }
                CastIconButton()
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
//                     DropdownMenuItem(text = { Text(stringResource(R.string.home_label)) }, onClick = {
//                         expanded = false
//                    })
                    if (landscape) {
                        var sleeperRes by remember { mutableIntStateOf(if (!isSleepTimerActive()) R.string.set_sleeptimer_label else R.string.sleep_timer_label) }
                        DropdownMenuItem(text = { Text(stringResource(sleeperRes)) }, onClick = {
                            showSleepTimeDialog = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.player_switch_to_audio_only)) }, onClick = {
                            switchToAudioOnly = true
                            curEpisode?.forceVideo = false
                            finish()
                            expanded = false
                        })
                        if (curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.open_podcast)) }, onClick = {
                            val feedItem = curEpisode
                            if (feedItem != null) startActivity(MainActivity.getIntentToOpenFeed(this@VideoplayerActivity, feedItem.feedId!!))
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                            showShareDialog = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.playback_speed)) }, onClick = {
                            showSpeedDialog = true
                            expanded = false
                        })
                    }
                    if (audioTracks.size >= 2) DropdownMenuItem(text = { Text(stringResource(R.string.audio_controls)) }, onClick = {
                        showAudioControlDialog = true
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.visit_website_label)) }, onClick = {
                        val url = getWebsiteLinkWithFallback(curEpisode)
                        if (url != null) openInBrowser(this@VideoplayerActivity, url)
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.chapters_label)) }, onClick = {
                        showChapterDialog = true
                        expanded = false
                    })
//                    DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
//                        expanded = false
//                    })
                }
            }
        )
    }

    private fun compatEnterPictureInPicture() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
//            if (videoMode == VideoMode.FULL_SCREEN_VIEW) supportActionBar?.hide()
//            videoEpisodeFragment.hideVideoControls(false)
            enterPictureInPictureMode()
        }
    }

    //Hardware keyboard support
//    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
//        val currentFocus = currentFocus
//        if (currentFocus is EditText) return super.onKeyUp(keyCode, event)
//        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
//        when (keyCode) {
//            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_SPACE -> {
//                playPause()
//                return true
//            }
//            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> {
//                playbackService?.mPlayer?.seekDelta(-rewindSecs * 1000)
//                return true
//            }
//            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> {
//                playbackService?.mPlayer?.seekDelta(fastForwardSecs * 1000)
//                return true
//            }
//            KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_ESCAPE -> {
//                //Exit fullscreen mode
//                onBackPressed()
//                return true
//            }
//            KeyEvent.KEYCODE_I -> {
//                compatEnterPictureInPicture()
//                return true
//            }
//            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_W -> {
//                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
//                return true
//            }
//            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
//                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
//                return true
//            }
//            KeyEvent.KEYCODE_M -> {
//                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
//                return true
//            }
//        }
//        //Go to x% of video:
//        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
//            seekTo((0.1f * (keyCode - KeyEvent.KEYCODE_0) * curDurationFB).toInt())
//            return true
//        }
//        return super.onKeyUp(keyCode, event)
//    }

    override fun isInPictureInPictureMode(): Boolean {
        return if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) super.isInPictureInPictureMode
        else false
    }

    @UnstableApi
    @Composable
    fun PlaybackControlsDialog(onDismiss: ()-> Unit) {
        val textColor = MaterialTheme.colorScheme.onSurface
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.audio_controls)) },
            text = {
                LazyColumn {
                    items(audioTracks.size) {index ->
                        Text(audioTracks[index], color = textColor, modifier = Modifier.clickable(onClick = {
                            mPlayer?.setAudioTrack((selectedAudioTrack + 1) % audioTracks.size)
//                            Handler(Looper.getMainLooper()).postDelayed({ setupAudioTracks() }, 500)
                        }))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.close_label)) } }
        )
    }

    companion object {
        private val TAG: String = VideoplayerActivity::class.simpleName ?: "Anonymous"
        var videoMode by mutableStateOf(VideoMode.NONE)
//        var media3Controller: MediaController? = null

        private val audioTracks: List<String>
            @UnstableApi
            get() {
                val tracks = mPlayer?.getAudioTracks()
                if (tracks.isNullOrEmpty()) return emptyList()
                return tracks
            }

        private val selectedAudioTrack: Int
            @UnstableApi
            get() = mPlayer?.getSelectedAudioTrack() ?: -1

        private fun getWebsiteLinkWithFallback(media: Episode?): String? {
            return when {
                media == null -> null
                !media.link.isNullOrBlank() -> media.link
                else -> media.getLinkWithFallback()
            }
        }
    }
}
