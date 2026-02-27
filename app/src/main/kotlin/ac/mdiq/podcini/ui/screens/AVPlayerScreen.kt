package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.bitrate
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.ensureAController
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.InTheatre.playVideo
import ac.mdiq.podcini.playback.base.InTheatre.playerStat
import ac.mdiq.podcini.playback.base.LocalMediaPlayer.Companion.exoPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.curPBSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCache
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isFallbackSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isPaused
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isPlaying
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isSpeedForward
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.normalSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.simpleCache
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.SleepManager.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.cast.BaseActivity
import ac.mdiq.podcini.playback.saveClipInOriginalFormat
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.fallbackSpeed
import ac.mdiq.podcini.storage.database.fastForwardSecs
import ac.mdiq.podcini.storage.database.rewindSecs
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.skipforwardSpeed
import ac.mdiq.podcini.storage.database.speedforwardSpeed
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PLAYING
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.EmbeddedChapterImage
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.specs.VideoMode
import ac.mdiq.podcini.storage.specs.VolumeAdaptionSetting
import ac.mdiq.podcini.storage.utils.durationStringAdapt
import ac.mdiq.podcini.storage.utils.durationStringFull
import ac.mdiq.podcini.ui.actions.Combo
import ac.mdiq.podcini.activity.MainActivity.Companion.findActivity
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.EpisodeDetails
import ac.mdiq.podcini.ui.compose.PlaybackSpeedFullDialog
import ac.mdiq.podcini.ui.compose.ShareDialog
import ac.mdiq.podcini.ui.compose.SleepTimerDialog
import ac.mdiq.podcini.ui.compose.distinctColorOf
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.FlowEvent.BufferUpdateEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.formatDateTimeFlex
import ac.mdiq.podcini.utils.formatLargeInteger
import ac.mdiq.podcini.utils.formatNumberKmp
import ac.mdiq.podcini.utils.formatWithGrouping
import ac.mdiq.podcini.utils.openInBrowser
import ac.mdiq.podcini.utils.timeIt
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.net.URL
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

enum class PSState {
    Hidden, PartiallyExpanded, Expanded;
    companion object {
        @OptIn(ExperimentalMaterial3Api::class)
        fun fromSheet(value:  SheetValue): PSState {
            return when (value) {
                SheetValue.Hidden -> Hidden
                SheetValue.PartiallyExpanded -> PartiallyExpanded
                SheetValue.Expanded -> Expanded
            }
        }
    }
}

var psState by mutableStateOf(PSState.PartiallyExpanded)

var curVideoMode by mutableStateOf(VideoMode.DEFAULT)

class AVPlayerVM: ViewModel() {

    var episodeFeed = curEpisode?.feed

    var landscape by mutableStateOf(false)

    var switchToAudioOnly = false

    var showActionBar by mutableStateOf(true)

    internal var txtvPlaybackSpeed by mutableStateOf("")
    internal var curPlaybackSpeed by mutableFloatStateOf(1f)

    internal var sleepTimerActive by mutableStateOf(isSleepTimerActive())

    internal var bufferValue by mutableFloatStateOf(0f)

    var volumeAdaption by mutableStateOf(VolumeAdaptionSetting.OFF)

    var showPlayButton by mutableStateOf(true)

    val audioTracks: List<String>
        get() {
            val tracks = mPlayer?.getAudioTracks()
            if (tracks.isNullOrEmpty()) return emptyList()
            return tracks
        }

    val selectedAudioTrack: Int
        get() = mPlayer?.getSelectedAudioTrack() ?: -1

    fun getWebsiteLinkWithFallback(media: Episode?): String? {
        return when {
            media == null -> null
            !media.link.isNullOrBlank() -> media.link
            else -> media.getLinkWithFallback()
        }
    }

    var eventSink by mutableStateOf<Job?>(null)
    fun cancelFlowEvents() {
        Logd(TAG, "cancelFlowEvents")
        eventSink?.cancel()
        eventSink = null
    }
    fun procFlowEvents() {
        Logd(TAG, "procFlowEvents")
        if (eventSink == null) eventSink = viewModelScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.PlaybackServiceEvent -> {
                        //                        if (event.action == FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN)
                        //                            (context as? MainActivity)?.bottomSheet?.state = BottomSheetBehavior.STATE_EXPANDED
                        //                        when (event.action) {
                        //                            FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN -> actMain?.setPlayerVisible(false)
                        //                            FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED -> if (curEpisode != null) actMain?.setPlayerVisible(true)
                        //                PlaybackServiceEvent.Action.SERVICE_RESTARTED -> (context as MainActivity).setPlayerVisible(true)
                        //                        }
                    }
                    is BufferUpdateEvent -> {
                        when {
                            event.hasStarted() || event.hasEnded() -> {}
                            mPlayer?.isStreaming == true -> bufferValue = event.progress
                            else -> bufferValue = 0f
                        }
                    }
                    is FlowEvent.SleepTimerUpdatedEvent -> sleepTimerActive = isSleepTimerActive()
                    is FlowEvent.SpeedChangedEvent -> {
                        curPlaybackSpeed = event.newSpeed
                        txtvPlaybackSpeed = formatNumberKmp(event.newSpeed.toDouble())
                    }
                    else -> {}
                }
            }
        }
    }

    init {
        timeIt("$TAG start of init")
        procFlowEvents()

        viewModelScope.launch { snapshotFlow { curEpisode?.position }.distinctUntilChanged().collect { if (showPlayButton) showPlayButton = !isCurrentlyPlaying(curEpisode) } }
        viewModelScope.launch { snapshotFlow { curEpisode?.id }.distinctUntilChanged().collect {
            Logd(TAG, "snapshotFlow { curEpisode?.id } collect")
            episodeFeed = curEpisode?.feed
            volumeAdaption = VolumeAdaptionSetting.OFF
            txtvPlaybackSpeed = formatNumberKmp(curPBSpeed.toDouble())
            curPlaybackSpeed = curPBSpeed
        } }
        viewModelScope.launch { snapshotFlow { playerStat }.distinctUntilChanged().collect { showPlayButton = playerStat != PLAYER_STATUS_PLAYING } }

        timeIt("$TAG end of vm init")
    }

    override fun onCleared() {
        super.onCleared()
        cancelFlowEvents()
    }
}

@Composable
fun VolumeDialog(vm: AVPlayerVM, onDismissRequest: () -> Unit) {
    CommonPopupCard(onDismissRequest = onDismissRequest) {
        fun adaptionFactor(): Float = when {
            vm.volumeAdaption != VolumeAdaptionSetting.OFF -> vm.volumeAdaption.adaptionFactor
            vm.episodeFeed != null -> vm.episodeFeed!!.volumeAdaptionSetting.adaptionFactor
            else -> 1f
        }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var forCurrent by remember { mutableStateOf(true) }
            var forPodcast by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Checkbox(checked = forCurrent, onCheckedChange = { isChecked -> forCurrent = isChecked })
                Text(stringResource(R.string.current_episode))
                Spacer(Modifier.weight(1f))
                Checkbox(checked = forPodcast, onCheckedChange = { isChecked -> forPodcast = isChecked })
                Text(stringResource(R.string.current_podcast))
                Spacer(Modifier.weight(1f))
            }
            VolumeAdaptionSetting.entries.forEach { setting ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = (setting == vm.volumeAdaption),
                        onCheckedChange = { _ ->
                            if (forPodcast && vm.episodeFeed != null) runOnIOScope { upsert(vm.episodeFeed!!) { it.volumeAdaptionSetting = setting} }
                            if (setting != vm.volumeAdaption) {
                                vm.volumeAdaption = setting
                                mPlayer?.setVolume(1.0f, 1.0f, adaptionFactor())
                                onDismissRequest()
                            }
                        }
                    )
                    Text(text = stringResource(setting.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlUI(vm: AVPlayerVM, navController: AppNavigator) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = Color(0xDDFFD700)
    val buttonColor1 = Color(0xEEAA7700)

    DisposableEffect(Unit) {
        timeIt("$TAG start of DisposableEffect(Unit")
        ensureAController()
        timeIt("$TAG end of DisposableEffect(Unit")
        onDispose {}
    }

    var showSpeedDialog by remember { mutableStateOf(false) }
    if (showSpeedDialog) PlaybackSpeedFullDialog(settingCode = booleanArrayOf(true, true, true), indexDefault = 0, maxSpeed = 3f, onDismiss = {showSpeedDialog = false})

    var showVolumeDialog by remember { mutableStateOf(false) }
    if (showVolumeDialog) VolumeDialog(vm) { showVolumeDialog = false }

    var showSleepTimeDialog by remember { mutableStateOf(false) }
    if (showSleepTimeDialog) SleepTimerDialog { showSleepTimeDialog = false }

    @Composable
    fun SpeedometerWithArc(speed: Float, maxSpeed: Float, trackColor: Color, modifier: Modifier) {
        val needleAngleRad = remember(speed) { Math.toRadians(((speed / maxSpeed) * 270f - 225).toDouble()) }
        Canvas(modifier = modifier) {
            val radius = 1.3 * size.minDimension / 2
            val strokeWidth = 6.dp.toPx()
            val arcRect = Rect(left = strokeWidth / 2, top = strokeWidth / 2, right = size.width - strokeWidth / 2, bottom = size.height - strokeWidth / 2)
            drawArc(color = trackColor, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = Stroke(width = strokeWidth), topLeft = arcRect.topLeft, size = arcRect.size)
            val needleEnd = Offset(x = size.center.x + (radius * 0.7f * cos(needleAngleRad)).toFloat(), y = size.center.y + (radius * 0.7f * sin(needleAngleRad)).toFloat())
            drawLine(color = Color.Red, start = size.center, end = needleEnd, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = Color.Cyan, center = size.center, radius = 3.dp.toPx())
        }
    }

    var recordingStartTime by remember { mutableStateOf<Long?>(null) }

    val velocityTracker = remember { VelocityTracker() }
    val offsetX = remember(curEpisode?.id) { Animatable(0f) }
    val swipeVelocityThreshold = 1500f
    val swipeDistanceThreshold = with(LocalDensity.current) { 100.dp.toPx() }
    Row(Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = {
                Logd(TAG, "detectHorizontalDragGestures onDragStart")
                velocityTracker.resetTracking()
            },
            onHorizontalDrag = { change, dragAmount ->
                Logd(TAG, "detectHorizontalDragGestures onHorizontalDrag $dragAmount")
                if (abs(dragAmount) > 4) {
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                }
            },
            onDragEnd = {
                Logd(TAG, "detectHorizontalDragGestures onDragEnd")
                scope.launch {
                    val velocity = velocityTracker.calculateVelocity().x
                    val distance = offsetX.value
                    Logd(TAG, "detectHorizontalDragGestures velocity: $velocity distance: $distance")
                    val shouldSwipe = abs(distance) > swipeDistanceThreshold && abs(velocity) > swipeVelocityThreshold
                    if (shouldSwipe) {
                        if (distance < 0) psState = PSState.Hidden
                        else showSleepTimeDialog = true
                    }
                    //                        offsetX.animateTo(targetValue = 0f, animationSpec = tween(300))
                }
            },
        )
    }) {
        AsyncImage(model = ImageRequest.Builder(context).data(curEpisode?.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).build(), placeholder = painterResource(R.drawable.ic_launcher_foreground), error = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "imgvCover", modifier = Modifier.width(50.dp).height(50.dp).border(border = BorderStroke(1.dp, buttonColor)).padding(start = 5.dp).combinedClickable(
            onClick = {
                Logd(TAG, "playerUi icon was clicked $psState")
                if (psState == PSState.PartiallyExpanded) {
                    if (curEpisode != null) {
                        val mediaType = curEpisode!!.getMediaType()
//                        if (mediaType == MediaType.AUDIO || appPrefs.videoPlaybackMode == VideoMode.AUDIO_ONLY.code || curVideoMode == VideoMode.AUDIO_ONLY || (vm.episodeFeed?.videoModePolicy == VideoMode.AUDIO_ONLY)) {
//                            Logd(TAG, "popping as audio episode")
//                            if (playbackService == null) PlaybackStarter(curEpisode!!).start()
//                        } else {
//                            Logd(TAG, "popping video context")
////                            val intent = getPlayerActivityIntent(context, mediaType)
////                            context.startActivity(intent)
//                        }
                        if (playbackService == null) PlaybackStarter(curEpisode!!).start()
                        psState = PSState.Expanded
                    }
                } else psState = PSState.PartiallyExpanded
            },
            onLongClick = {
                if (vm.episodeFeed != null) {
                    navController.navigate("${Screens.FeedDetails.name}?feedId=${vm.episodeFeed!!.id}")
                    psState = PSState.PartiallyExpanded
                }
            }))
        val buttonSize = 46.dp
        Spacer(Modifier.weight(0.1f))
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
            onClick = { showSpeedDialog = true },
            onLongClick = { showVolumeDialog = true }
        )) {
            SpeedometerWithArc(speed = vm.curPlaybackSpeed*100, maxSpeed = 300f, trackColor = buttonColor, modifier = Modifier.width(40.dp).height(40.dp).align(Alignment.TopCenter))
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_volume_adaption), tint = buttonColor1, contentDescription = "Volume adaptation", modifier = Modifier.align(Alignment.Center))
            Text(vm.txtvPlaybackSpeed, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.weight(0.1f))
        val recordColor = if (recordingStartTime == null) { if (curEpisode != null && exoPlayer != null && isPlaying) buttonColor else Color.Gray } else Color.Red
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_fiber_manual_record_24), tint = recordColor, contentDescription = "record",
            modifier = Modifier.size(buttonSize).combinedClickable(
                onClick = {
                    if (curEpisode != null && exoPlayer != null && isPlaying) {
                        val pos = exoPlayer!!.currentPosition
                        runOnIOScope { upsert(curEpisode!!) { it.marks.add(pos) } }
                        Logt(TAG, "position $pos marked for ${curEpisode?.title}")
                    } else Loge(TAG, "Marking position only works during playback.") },
                onLongClick = {
                    if (curEpisode != null && exoPlayer != null && isPlaying) {
                        if (recordingStartTime == null) {
                            recordingStartTime = exoPlayer!!.currentPosition
                            saveClipInOriginalFormat(recordingStartTime!!)
                        }
                        else {
                            saveClipInOriginalFormat(recordingStartTime!!, exoPlayer!!.currentPosition)
                            recordingStartTime = null
                        }
                    } else Loge(TAG, "Recording only works during playback.")
                }))
        Spacer(Modifier.weight(0.1f))
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
            onClick = { mPlayer?.seekDelta(-rewindSecs * 1000) }, onLongClick = { mPlayer?.seekTo(0) })) {
            val rewindSecs = remember(rewindSecs) { formatWithGrouping(rewindSecs.toLong()) }
            Text(rewindSecs, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.TopCenter))
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_fast_rewind), tint = buttonColor, contentDescription = "rewind", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
        }
        Spacer(Modifier.weight(0.1f))
        fun toggleFallbackSpeed(speed: Float) {
            if (mPlayer == null || isSpeedForward) return
            if (isPlaying) {
                val player = mPlayer!!
                if (!isFallbackSpeed) {
                    normalSpeed = player.getPlaybackSpeed()
                    player.setPlaybackParams(speed)
                } else player.setPlaybackParams(normalSpeed)
                isFallbackSpeed = !isFallbackSpeed
            }
        }
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
            onClick = {
                if (curEpisode != null) {
                    vm.showPlayButton = !vm.showPlayButton
                    if (vm.showPlayButton && recordingStartTime != null) {
                        saveClipInOriginalFormat(recordingStartTime!!, exoPlayer!!.currentPosition)
                        recordingStartTime = null
                    }
                    Logd(TAG, "Play button clicked: status: $status is ready: ${playbackService?.isServiceReady()}")
                    PlaybackStarter(curEpisode!!).shouldStreamThisTime(null).start()
                    if (curEpisode?.getMediaType() == MediaType.VIDEO && !isPlaying && (vm.episodeFeed?.videoModePolicy != VideoMode.AUDIO_ONLY)) {
                        if (!isPlaying) psState = PSState.Expanded
                    }
                }
            },
            onLongClick = {
                if (isPlaying) {
                    val speedFB = fallbackSpeed
                    if (speedFB > 0.1f) toggleFallbackSpeed(speedFB)
                } })) {
            val playButRes by remember(vm.showPlayButton) { mutableIntStateOf(if (vm.showPlayButton) R.drawable.ic_play_48dp else R.drawable.ic_pause) }
            Icon(imageVector = ImageVector.vectorResource(playButRes), tint = buttonColor, contentDescription = "play", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
            if (fallbackSpeed > 0.1f) Text(fallbackSpeed.toString(), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.weight(0.1f))
        fun speedForward(speed: Float) {
            if (mPlayer == null || isFallbackSpeed) return
            if (!isSpeedForward) {
                normalSpeed = mPlayer!!.getPlaybackSpeed()
                mPlayer!!.setPlaybackParams(speed)
            } else mPlayer?.setPlaybackParams(normalSpeed)
            isSpeedForward = !isSpeedForward
        }
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
            onClick = { mPlayer?.seekDelta(fastForwardSecs * 1000) }, onLongClick = {
                if (isPlaying) {
                    val speedForward = speedforwardSpeed
                    if (speedForward > 0.1f) speedForward(speedForward)
                }
            })) {
            val fastForwardSecs = remember(fastForwardSecs) { formatWithGrouping(fastForwardSecs.toLong()) }
            Text(fastForwardSecs, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.TopCenter))
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_fast_forward), tint = buttonColor, contentDescription = "forward", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
            if (speedforwardSpeed > 0.1f) Text(formatNumberKmp(speedforwardSpeed), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.weight(0.1f))
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
            onClick = {
                if (isPlaying) {
                    val speedForward = skipforwardSpeed
                    if (speedForward > 0.1f) speedForward(speedForward)
                } },
            onLongClick = {
                //                    context.sendBroadcast(MediaButtonReceiver.createIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
                if (isPlaying || isPaused) mPlayer?.skip()
            })) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_skip_48dp), tint = buttonColor, contentDescription = "skip", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
            if (skipforwardSpeed > 0.1f) Text(formatNumberKmp(skipforwardSpeed), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.weight(0.1f))
    }
}

@Composable
fun ProgressBar(vm: AVPlayerVM) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = Color(0xDDFFD700)
    Box(modifier = Modifier.fillMaxWidth()) {
        var sliderValue by remember(curEpisode?.position) { mutableFloatStateOf((curEpisode?.position?:0).toFloat()) }
        val actColor = MaterialTheme.colorScheme.tertiary
        val inActColor = MaterialTheme.colorScheme.secondaryFixedDim
        val distColor = remember { distinctColorOf(actColor, inActColor) }
        Slider(colors = SliderDefaults.colors(activeTrackColor = actColor,  inactiveTrackColor = inActColor), modifier = Modifier.height(12.dp).padding(top = 2.dp),
            value = sliderValue, valueRange = 0f..( if ((curEpisode?.duration?:0) > 0) curEpisode?.duration?:0 else 30000).toFloat(),
            onValueChange = { sliderValue = it }, onValueChangeFinished = { mPlayer?.seekTo(sliderValue.toInt()) })
        if (vm.bufferValue > 0f) LinearProgressIndicator(progress = { vm.bufferValue }, color = MaterialTheme.colorScheme.primaryFixed.copy(alpha = 0.6f), trackColor = MaterialTheme.colorScheme.secondaryFixedDim, modifier = Modifier.height(8.dp).fillMaxWidth().align(Alignment.BottomStart))
        Text(durationStringFull(curEpisode?.duration?:0), color = distColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
    }
    Row {
        val pastText = remember(curEpisode?.position) { run {
            if (curEpisode == null) return@run ""
            durationStringAdapt(curEpisode!!.position) + " *" + durationStringAdapt(curEpisode!!.timeSpent.toInt())
        }  }
        Text(pastText, color = textColor, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        if (bitrate > 0) Text(formatLargeInteger(bitrate) + "bits", color = textColor, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        val lengthText = remember(curPBSpeed, curEpisode?.position) {  run {
            if (curEpisode == null) return@run ""
            val remainingTime = max((curEpisode!!.duration - curEpisode!!.position), 0)
            val onSpeed = if (curPBSpeed > 0 && abs(curPBSpeed-1f) > 0.001) (remainingTime / curPBSpeed).toInt() else 0
            (if (onSpeed > 0) "*" + durationStringAdapt(onSpeed) else "") + " -" + durationStringAdapt(remainingTime)
        } }
        Text(lengthText, color = textColor, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PlayerUIScreen(modifier: Modifier, navController: AppNavigator) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val vm: AVPlayerVM = viewModel()
    Box(modifier = modifier.fillMaxWidth().height(100.dp).border(1.dp, MaterialTheme.colorScheme.tertiary).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
        Column {
            Text(curEpisode?.title ?: "No title", maxLines = 1, color = textColor, style = MaterialTheme.typography.bodyMedium)
            ProgressBar(vm)
            ControlUI(vm, navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AVPlayerScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val vm: AVPlayerVM = viewModel()

    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = Color(0xDDFFD700)

    var showHomeText by remember { mutableStateOf(false) }
    var chapertsLoaded by remember { mutableStateOf(false) }

    // TODO: somehow, these 2 are not used?
    //     var homeText: String? = remember { null }
    var readerhtml: String? by remember { mutableStateOf(null) }

    //    private var chapterControlVisible by mutableStateOf(false)
    var chapterIndex by remember { mutableIntStateOf(-1) }
    var displayedChapterIndex by remember { mutableIntStateOf(-1) }
//    val curChapter = remember(curEpisode?.id, displayedChapterIndex) { if (curEpisode?.chapters.isNullOrEmpty() || displayedChapterIndex == -1) null else curEpisode.chapters[displayedChapterIndex] }
//    var nextChapterStart by remember { mutableIntStateOf(Int.MAX_VALUE) }

    fun isAutoRotateEnabled(): Boolean {
        return Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    }
    var isRotationEnabled by remember { mutableStateOf(isAutoRotateEnabled()) }
    DisposableEffect(context) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                isRotationEnabled = isAutoRotateEnabled()
                Logd(TAG, "ContentObserver onChange isRotationEnabled: $isRotationEnabled")
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val configuration = LocalConfiguration.current
    if (isRotationEnabled) vm.landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    else if (isPlayingVideoLocally && vm.episodeFeed != null && vm.episodeFeed!!.videoModePolicy != VideoMode.AUDIO_ONLY)
        vm.landscape = vm.episodeFeed!!.videoModePolicy == VideoMode.FULL_SCREEN || appPrefs.videoPlaybackMode == VideoMode.FULL_SCREEN.code
    if (!vm.landscape) vm.showActionBar = true

    LaunchedEffect(key1 = curEpisode?.id) {
        Logd(TAG, "LaunchedEffect curMediaId: ${curEpisode?.title}")
        showHomeText = false
        //            homeText = null
        chapertsLoaded = false
        displayedChapterIndex = -1
        vm.episodeFeed = curEpisode?.feed
        if (psState == PSState.Hidden) psState = PSState.PartiallyExpanded
        //        if (isPlayingVideoLocally && vm.episodeFeed != null && vm.episodeFeed!!.videoModePolicy != VideoMode.AUDIO_ONLY) {
//            if (!isRotationEnabled) vm.landscape = vm.episodeFeed!!.videoModePolicy == VideoMode.FULL_SCREEN || appPrefs.videoPlaybackMode == VideoMode.FULL_SCREEN.code
////            curVideoMode = vm.episodeFeed!!.videoModePolicy
//            psState = PSState.Expanded
//        }
    }

    LaunchedEffect(psState, curEpisode?.id) {
        Logd(TAG, "LaunchedEffect(isBSExpanded, curItem?.id) isBSExpanded: $psState")
        if (psState == PSState.Expanded) {
            Logd(TAG, "LaunchedEffect loading details ${curEpisode?.id}")
            if (curEpisode != null && !chapertsLoaded) {
                scope.launch(Dispatchers.IO) {
                    gearbox.loadChapters(curEpisode!!)
                    vm.sleepTimerActive = isSleepTimerActive()
                    chapertsLoaded = true
                }.invokeOnCompletion { throwable -> if (throwable != null) Logs(TAG, throwable) }
            }
        }
    }

    LaunchedEffect(curEpisode?.position) {
        if (curEpisode != null) {
            if (psState == PSState.Expanded) {
                chapterIndex = curEpisode!!.getCurrentChapterIndex(curEpisode!!.position)
                displayedChapterIndex = if (curEpisode!!.position > curEpisode!!.duration || chapterIndex >= curEpisode!!.chapters.size - 1) curEpisode!!.chapters.size - 1 else chapterIndex
                Logd(TAG, "LaunchedEffect(curEpisode?.position) chapterIndex $chapterIndex $displayedChapterIndex")
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect Lifecycle.Event: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> psState = PSState.PartiallyExpanded
                Lifecycle.Event.ON_START -> if (curEpisode != null) vm.showPlayButton = !isCurrentlyPlaying(curEpisode)
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showAudioControlDialog by remember { mutableStateOf(false) }
    @Composable
    fun PlaybackControlsDialog(onDismiss: ()-> Unit) {
        val textColor = MaterialTheme.colorScheme.onSurface
        AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.audio_controls)) },
            text = {
                LazyColumn {
                    items(vm.audioTracks) { track ->
                        Text(track, color = textColor, modifier = Modifier.clickable(onClick = {
                            mPlayer?.setAudioTrack((vm.selectedAudioTrack + 1) % vm.audioTracks.size)
                            //                            Handler(Looper.getMainLooper()).postDelayed({ setupAudioTracks() }, 500)
                        }))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.close_label)) } }
        )
    }
    if (showAudioControlDialog) PlaybackControlsDialog(onDismiss = { showAudioControlDialog = false })

    var showVolumeDialog by remember { mutableStateOf(false) }
    var showSleepTimeDialog by remember { mutableStateOf(false) }

    if (showVolumeDialog) VolumeDialog(vm) { showVolumeDialog = false }
    if (showSleepTimeDialog) SleepTimerDialog { showSleepTimeDialog = false }

    var showSpeedDialog by remember { mutableStateOf(false) }
    if (showSpeedDialog) PlaybackSpeedFullDialog(settingCode = booleanArrayOf(true, true, true), indexDefault = 0, maxSpeed = 3f, onDismiss = {showSpeedDialog = false})

    @Composable
    fun PlayerUI(modifier: Modifier) {
        Box(modifier = modifier.fillMaxWidth().height(100.dp).border(1.dp, MaterialTheme.colorScheme.tertiary).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
            Column {
                Text(curEpisode?.title ?: "No title", maxLines = 1, color = textColor, style = MaterialTheme.typography.bodyMedium)
                ProgressBar(vm)
                ControlUI(vm, navController)
            }
        }
    }

    var showShareDialog by remember { mutableStateOf(false) }
    if (showShareDialog && curEpisode != null) ShareDialog(curEpisode!!) {showShareDialog = false }

    @Composable
    fun VideoToolBar(modifier: Modifier = Modifier) {
        var expanded by remember { mutableStateOf(false) }
        val buttonColor = Color(0xDDFFD700)
        if (vm.showActionBar) Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down), tint = textColor, contentDescription = "Collapse", modifier = Modifier.clickable { psState = PSState.PartiallyExpanded })
            if (vm.landscape) Column {
                Text(text = curEpisode?.title?:"", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = curEpisode?.feed?.title?:"", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                if (!vm.episodeFeed?.downloadUrl.isNullOrBlank() && gearbox.isGearFeed(URL(vm.episodeFeed!!.downloadUrl!!))) IconButton(onClick = {
                    vm.switchToAudioOnly = true
                    if (curEpisode != null) {
                        upsertBlk(curEpisode!!) { it.forceVideo = false }
                        mPlayer?.pause(reinit = true)
                        PlaybackStarter(curEpisode!!).shouldStreamThisTime(null).start(force = true)
                    }
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_audiotrack_24), contentDescription = "audio only") }
                var sleepIconRes by remember { mutableIntStateOf(if (!isSleepTimerActive()) R.drawable.ic_sleep else R.drawable.ic_sleep_off) }
                IconButton(onClick = { showSleepTimeDialog = true }) { Icon(imageVector = ImageVector.vectorResource(sleepIconRes), contentDescription = "sleeper") }
                (context as? BaseActivity)?.CastIconButton()
                IconButton(onClick = { showShareDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_share), contentDescription = "share") }
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                    if (vm.landscape) {
                        var sleeperRes by remember { mutableIntStateOf(if (!isSleepTimerActive()) R.string.set_sleeptimer_label else R.string.sleep_timer_label) }
                        DropdownMenuItem(text = { Text(stringResource(sleeperRes)) }, onClick = {
                            showSleepTimeDialog = true
                            expanded = false
                        })
                        if (curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.queue)) }, onClick = {
                            navController.navigate("${Screens.Queues.name}?id=${actQueue.id}")
                            expanded = false
                        })
                        if (curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.open_podcast)) }, onClick = {
                            if (vm.episodeFeed != null) navController.navigate("${Screens.FeedDetails.name}?feedId=${vm.episodeFeed!!.id}")
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
                    if (vm.audioTracks.size >= 2) DropdownMenuItem(text = { Text(stringResource(R.string.audio_controls)) }, onClick = {
                        showAudioControlDialog = true
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.visit_website_label)) }, onClick = {
                        val url = vm.getWebsiteLinkWithFallback(curEpisode)
                        if (url != null) openInBrowser(url)
                        expanded = false
                    })
                }
            }
        }
    }

    @Composable
    fun Toolbar() {
        var expanded by remember { mutableStateOf(false) }
        val mediaType = remember(curEpisode?.id) { curEpisode?.getMediaType() }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down), tint = textColor, contentDescription = "Collapse", modifier = Modifier.clickable { psState = PSState.PartiallyExpanded })
            if (mediaType == MediaType.VIDEO && !vm.episodeFeed?.downloadUrl.isNullOrBlank() && gearbox.isGearFeed(URL(vm.episodeFeed!!.downloadUrl!!))) Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_fullscreen_24), tint = textColor, contentDescription = "Play video",
                modifier = Modifier.clickable {
                    upsertBlk(curEpisode!!) { it.forceVideo = true }
                    mPlayer?.pause(reinit = true)
                    PlaybackStarter(curEpisode!!).shouldStreamThisTime(null).start(force = true)
                })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_volume_adaption), tint = textColor, contentDescription = "Volume adaptation", modifier = Modifier.clickable { if (curEpisode != null) showVolumeDialog = true })
            val sleepRes = if (vm.sleepTimerActive) R.drawable.ic_sleep_off else R.drawable.ic_sleep
            Icon(imageVector = ImageVector.vectorResource(sleepRes), tint = textColor, contentDescription = "Sleep timer", modifier = Modifier.clickable { showSleepTimeDialog = true })
            (context as? BaseActivity)?.CastIconButton()
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                    if (curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                        showShareDialog = true
                        expanded = false
                    })
                    if (curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                        val notes = if (showHomeText) readerhtml else curEpisode?.description
                        if (!notes.isNullOrEmpty()) {
                            val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                            context.startActivity(intent)
                        }
                        expanded = false
                    })
                    if (curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.clear_cache)) }, onClick = {
                        runOnIOScope { getCache().removeResource(curEpisode!!.id.toString()) }
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_all_cache)) }, onClick = {
                        runOnIOScope {
                            val keys = simpleCache?.keys ?: return@runOnIOScope
                            keys.forEach {
                                Logd(TAG, "removing cache resource on key: $it")
                                simpleCache!!.removeResource(it)
                            }
                        }
                        expanded = false
                    })
                }
            }
        }
    }

    @Composable
    fun DetailUI(modifier: Modifier) {
        val comboAction = remember { Combo() }
        comboAction.ActionOptions()

        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(curEpisode!!)) { showChooseRatingDialog = false }
        val swipeVelocityThreshold = 1500f
        val swipeDistanceThreshold = with(LocalDensity.current) { 100.dp.toPx() }
        val velocityTracker = remember { VelocityTracker() }
        val offsetX = remember { Animatable(0f) }
        Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { velocityTracker.resetTracking() },
                onHorizontalDrag = { change, dragAmount ->
                    //                            Logd(TAG, "detectHorizontalDragGestures onHorizontalDrag $dragAmount")
                    if (abs(dragAmount) > 4) {
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                },
                onDragEnd = {
                    //                            Logd(TAG, "detectHorizontalDragGestures onDragEnd")
                    scope.launch {
                        val velocity = velocityTracker.calculateVelocity().x
                        val distance = offsetX.value
                        //                                Logd(TAG, "detectHorizontalDragGestures velocity: $velocity distance: $distance")
                        val shouldSwipe = abs(distance) > swipeDistanceThreshold && abs(velocity) > swipeVelocityThreshold
                        if (shouldSwipe) {
                            if (distance > 0) navController.navigate("${Screens.Queues.name}?id=${actQueue.id}")
                            else navController.navigate("${Screens.FeedDetails.name}?feedId=${vm.episodeFeed!!.id}")
                            psState = PSState.PartiallyExpanded
                        }
                        offsetX.animateTo(targetValue = 0f, animationSpec = tween(300))
                    }
                },
            )
        }.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            var resetPlayer by remember { mutableStateOf(false) }
            if (curEpisode != null) gearbox.PlayerDetailedGearPanel(curEpisode!!, resetPlayer) { resetPlayer = it }
            SelectionContainer { Text(curEpisode?.title ?: "No title", textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)) }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(0.2f))
                val ratingIconRes by remember(curEpisode?.rating) { mutableIntStateOf( Rating.fromCode(curEpisode?.rating ?: Rating.UNRATED.code).res) }
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = { showChooseRatingDialog = true }))
                Spacer(modifier = Modifier.weight(0.4f))
                val episodeDate = remember(curEpisode?.pubDate) { if (curEpisode == null) "" else formatDateTimeFlex(curEpisode!!.pubDate).trim() }
                Text(episodeDate, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(0.4f))
                if (curEpisode != null) Icon(imageVector = ImageVector.vectorResource(comboAction.iconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "Combo", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).clickable(onClick = {  comboAction.performAction(curEpisode!!) }))
                Spacer(modifier = Modifier.weight(0.2f))
            }
            SelectionContainer { Text((vm.episodeFeed?.title?:"").trim(), textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)) }

            if (curEpisode != null) EpisodeDetails(curEpisode!!, psState == PSState.Expanded)

            if (curEpisode != null) {
                val imgLarge = remember(curEpisode!!.id, displayedChapterIndex) {
                    if (displayedChapterIndex == -1 || curEpisode!!.chapters.isEmpty() || curEpisode!!.chapters[displayedChapterIndex].imageUrl.isNullOrEmpty()) curEpisode!!.imageUrl ?: curEpisode?.feed?.imageUrl
                    else EmbeddedChapterImage.getModelFor(curEpisode!!, displayedChapterIndex)?.toString()
                }
                if (imgLarge != null) {
                    AsyncImage( ImageRequest.Builder(context).data(imgLarge).memoryCachePolicy(CachePolicy.ENABLED).build(), placeholder = painterResource(R.drawable.ic_launcher_foreground), error = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "imgvCover", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(10.dp).clickable(onClick = {}))
                }
            }
        }
    }

    @Composable
    fun FullScreenVideoPlayer() {
        val context = LocalContext.current
        val view = LocalView.current
        DisposableEffect(Unit) {
            val activity = context.findActivity()
            Logd(TAG, "FullScreenVideoPlayer activity: ${activity?.title}")
            if (!isRotationEnabled) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val window = activity?.window ?: return@DisposableEffect onDispose {}
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                if (!isRotationEnabled) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exoPlayer
                        useController = true
                        //                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> vm.showActionBar = visibility == View.VISIBLE })
                },
                onRelease = { playerView -> playerView.player = null }
            )
        }
    }

    @Composable
    fun VideoPlayer() {
        AndroidView(modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            },
            update = { playerView -> playerView.player = exoPlayer },
            onRelease = { view -> view.player = null }
        )
    }

    Logd(TAG, "landscape: ${vm.landscape}")
//    if ((landscape || curVideoMode == VideoMode.FULL_SCREEN || (curVideoMode == VideoMode.DEFAULT && appPrefs.videoPlaybackMode == VideoMode.FULL_SCREEN.code)) && playVideo && bsState == BSState.Expanded) {
    if (vm.landscape && playVideo && psState == PSState.Expanded) {
        Box {
            FullScreenVideoPlayer()
            VideoToolBar(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    } else Box(modifier = Modifier.fillMaxWidth().then(if (psState == PSState.PartiallyExpanded) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier.statusBarsPadding().navigationBarsPadding())) {
        PlayerUI(Modifier.align(if (psState == PSState.PartiallyExpanded) Alignment.TopCenter else Alignment.BottomCenter).zIndex(1f))
        if (psState == PSState.Expanded) {
            Column(Modifier.padding(bottom = 100.dp)) {
                if (playVideo) {
                    VideoToolBar()
                    VideoPlayer()
                } else Toolbar()
                Row {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), tint = buttonColor, contentDescription = "queues icon", modifier = Modifier.width(24.dp).height(24.dp))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "left_arrow", modifier = Modifier.width(24.dp).height(24.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(24.dp).height(24.dp))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_feed), tint = buttonColor, contentDescription = "feed icon", modifier = Modifier.width(24.dp).height(24.dp))
                }
                DetailUI(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private const val TAG = "AudioPlayerScreen"
