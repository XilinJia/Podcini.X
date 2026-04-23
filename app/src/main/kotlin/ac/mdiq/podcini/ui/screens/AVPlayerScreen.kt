package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.activity.MainActivity.Companion.findActivity
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.activeTheatres
import ac.mdiq.podcini.playback.base.InTheatre.ensureAController
import ac.mdiq.podcini.playback.base.InTheatre.theatres
import ac.mdiq.podcini.playback.base.Media3Player.Companion.getCache
import ac.mdiq.podcini.playback.base.Media3Player.Companion.simpleCache
import ac.mdiq.podcini.playback.base.PlayerStatusSimple
import ac.mdiq.podcini.playback.base.SleepManager.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.cast.BaseActivity
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
import ac.mdiq.podcini.storage.specs.EmbeddedChapterImage
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.specs.VideoMode
import ac.mdiq.podcini.storage.specs.VolumeAdaptionSetting
import ac.mdiq.podcini.storage.utils.durationStringAdapt
import ac.mdiq.podcini.storage.utils.durationStringFull
import ac.mdiq.podcini.ui.actions.Combo
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.EpisodeDetails
import ac.mdiq.podcini.ui.compose.PlaybackSpeedFullDialog
import ac.mdiq.podcini.ui.compose.ShareDialog
import ac.mdiq.podcini.ui.compose.SleepTimerDialog
import ac.mdiq.podcini.ui.compose.borderColor
import ac.mdiq.podcini.ui.compose.buttonColor
import ac.mdiq.podcini.ui.compose.distinctColorOf
import ac.mdiq.podcini.ui.compose.textColor
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.FlowEvent.BufferUpdateEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
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
import androidx.compose.ui.draw.blur
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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAG = "AudioPlayerScreen"

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

private var activePlayer by mutableIntStateOf(0)

var psState by mutableStateOf(PSState.PartiallyExpanded)

var curVideoMode by mutableStateOf(VideoMode.DEFAULT)

class AVPlayerVM0: ViewModel() {
    var landscape by mutableStateOf(false)
    internal var sleepTimerActive by mutableStateOf(isSleepTimerActive())

    private var eventSink by mutableStateOf<Job?>(null)
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
                    is FlowEvent.SleepTimerUpdatedEvent -> sleepTimerActive = isSleepTimerActive()
                    else -> {}
                }
            }
        }
    }

    init {
        procFlowEvents()
    }

    override fun onCleared() {
        super.onCleared()
        eventSink?.cancel()
        eventSink = null
    }
}

class AVPlayerVM(val playerId: Int): ViewModel() {
    var episodeFeed = theatres[playerId].mPlayer?.curEpisode?.feed

    var showActionBar by mutableStateOf(true)

    internal var curPlaybackSpeed by mutableFloatStateOf(1f)

    internal var bufferValue by mutableFloatStateOf(0f)

    var volumeAdaption by mutableStateOf(VolumeAdaptionSetting.OFF)

    var showPlayButton by mutableStateOf(true)

    private var eventSink by mutableStateOf<Job?>(null)
    fun procFlowEvents() {
        Logd(TAG, "procFlowEvents")
        if (eventSink == null) eventSink = viewModelScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is BufferUpdateEvent -> {
                        when {
                            event.hasStarted() || event.hasEnded() -> {}
                            theatres[playerId].mPlayer?.isStreaming == true -> bufferValue = event.progress
                            else -> bufferValue = 0f
                        }
                    }
                    is FlowEvent.SpeedChangedEvent -> if (event.playerId == playerId) curPlaybackSpeed = event.newSpeed
                    else -> {}
                }
            }
        }
    }

    private var posJob: Job? = null
    private var curIdJob: Job? = null
    private var curStateJob: Job? = null

    fun start() {
        timeIt("$TAG start of init vm $playerId")
        procFlowEvents()

        posJob = viewModelScope.launch { snapshotFlow { theatres[playerId].mPlayer?.curEpisode?.position }.distinctUntilChanged().collect { if (showPlayButton) showPlayButton = theatres[playerId].mPlayer?.isCurrentlyPlaying(theatres[playerId].mPlayer?.curEpisode) != true } }
        curIdJob = viewModelScope.launch { snapshotFlow { theatres[playerId].mPlayer?.curEpisode?.id }.distinctUntilChanged().collect {
            Logd(TAG, "snapshotFlow { curEpisode?.id } collect")
            episodeFeed = theatres[playerId].mPlayer?.curEpisode?.feed
            volumeAdaption = VolumeAdaptionSetting.OFF
            curPlaybackSpeed = theatres[playerId].mPlayer?.curPBSpeed?:1f
        } }
        curStateJob = viewModelScope.launch { snapshotFlow { theatres[playerId].mPlayer?.statusSimple }.distinctUntilChanged().collect {
            showPlayButton = theatres[playerId].mPlayer?.statusSimple != PlayerStatusSimple.PLAYING
            Logd(TAG, "curPlayerStatus changed playerId: $playerId showPlayButton $showPlayButton")
        } }
        viewModelScope.launch { snapshotFlow { theatres[playerId].mPlayer?.curPBSpeed }.distinctUntilChanged().collect {
            curPlaybackSpeed = theatres[playerId].mPlayer?.curPBSpeed ?: 1f
            Logd(TAG, "curPlaybackSpeed changed playerId: $playerId curPlaybackSpeed $curPlaybackSpeed")
        } }

        timeIt("$TAG end of vm init")
    }

    fun stop() {
        posJob?.cancel()
        posJob = null
        curIdJob?.cancel()
        curIdJob = null
        curStateJob?.cancel()
        curStateJob = null
        eventSink?.cancel()
        eventSink = null
    }

    override fun onCleared() {
        super.onCleared()
        stop()
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
                                theatres[vm.playerId].mPlayer?.setVolume(1.0f, 1.0f, adaptionFactor())
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
fun ControlUI(vm: AVPlayerVM) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val buttonColor1 = Color(0xEEAA7700)

    DisposableEffect(Unit) {
        timeIt("$TAG start of DisposableEffect(Unit")
        ensureAController()
        timeIt("$TAG end of DisposableEffect(Unit")
        onDispose {}
    }

    var showSpeedDialog by remember { mutableStateOf(false) }
    if (showSpeedDialog) PlaybackSpeedFullDialog(vm.playerId, indexDefault = 0, maxSpeed = 3f, onDismiss = {showSpeedDialog = false})

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
    val offsetX = remember(theatres[vm.playerId].mPlayer?.curEpisode?.id) { Animatable(0f) }
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
        AsyncImage(model = ImageRequest.Builder(context).data(theatres[vm.playerId].mPlayer?.curEpisode?.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).build(), placeholder = painterResource(R.drawable.ic_launcher_foreground), error = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "imgvCover", modifier = Modifier.width(50.dp).height(50.dp).border(border = BorderStroke(1.dp, borderColor)).padding(start = 5.dp).combinedClickable(
            onClick = {
                Logd(TAG, "playerUi icon was clicked $psState")
                activePlayer = vm.playerId
                if (psState == PSState.PartiallyExpanded) {
                    if (theatres[vm.playerId].mPlayer?.curEpisode != null) {
                        if (playbackService == null) PlaybackStarter(theatres[vm.playerId].mPlayer?.curEpisode!!).start(vm.playerId)
                        psState = PSState.Expanded
                    }
                } else psState = PSState.PartiallyExpanded
            },
            onLongClick = {
                if (vm.episodeFeed != null) {
                    navTo(FeedDetails(feedId=vm.episodeFeed!!.id))
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
            Text(formatNumberKmp(vm.curPlaybackSpeed.toDouble()), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.weight(0.1f))
        val recordColor = if (recordingStartTime == null) { if (theatres[vm.playerId].mPlayer?.curEpisode != null && theatres[vm.playerId].mPlayer != null && theatres[vm.playerId].mPlayer!!.isPlaying) buttonColor else Color.Gray } else Color.Red
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_fiber_manual_record_24), tint = recordColor, contentDescription = "record",
            modifier = Modifier.size(buttonSize).combinedClickable(
                onClick = {
                    if (theatres[vm.playerId].mPlayer?.curEpisode != null && theatres[vm.playerId].mPlayer != null && theatres[vm.playerId].mPlayer!!.isPlaying) {
                        val pos = theatres[vm.playerId].mPlayer!!.getPosition().toLong()
                        runOnIOScope { upsert(theatres[vm.playerId].mPlayer?.curEpisode!!) { it.marks.add(pos) } }
                        Logt(TAG, "position $pos marked for ${theatres[vm.playerId].mPlayer?.curEpisode?.title}")
                    } else Loge(TAG, "Marking position only works during playback.") },
                onLongClick = {
                    if (theatres[vm.playerId].mPlayer?.curEpisode != null && theatres[vm.playerId].mPlayer != null && theatres[vm.playerId].mPlayer!!.isPlaying) {
                        scope.launch {
                            if (recordingStartTime == null) {
                                recordingStartTime = theatres[vm.playerId].mPlayer!!.getPosition().toLong()
                                theatres[vm.playerId].mPlayer?.saveClipInOriginalFormat(recordingStartTime!!)
                            } else {
                                theatres[vm.playerId].mPlayer?.saveClipInOriginalFormat(recordingStartTime!!, theatres[vm.playerId].mPlayer!!.getPosition().toLong())
                                recordingStartTime = null
                            }
                        }
                    } else Loge(TAG, "Recording only works during playback.")
                }))
        Spacer(Modifier.weight(0.1f))
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
            onClick = { theatres[vm.playerId].mPlayer?.seekDelta(-rewindSecs * 1000) }, onLongClick = { theatres[vm.playerId].mPlayer?.seekTo(0) })) {
            val rewindSecs = remember(rewindSecs) { formatWithGrouping(rewindSecs.toLong()) }
            Text(rewindSecs, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.TopCenter))
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_fast_rewind), tint = buttonColor, contentDescription = "rewind", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
        }
        Spacer(Modifier.weight(0.1f))
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
            onClick = {
                Logd(TAG, "onClick Play/Pause: vm.playerId: ${vm.playerId}")
                if (theatres[vm.playerId].mPlayer?.curEpisode != null) {
                    vm.showPlayButton = !vm.showPlayButton
                    if (vm.showPlayButton && recordingStartTime != null) {
                        scope.launch(Dispatchers.IO) {
                            theatres[vm.playerId].mPlayer?.saveClipInOriginalFormat(recordingStartTime!!, (theatres[vm.playerId].mPlayer?.getPosition()?:0).toLong())
                            recordingStartTime = null
                        }
                    }
                    Logd(TAG, "Play button clicked: status: ${theatres[vm.playerId].mPlayer?.status} is ready: ${playbackService?.isServiceReady()}")
                    PlaybackStarter(theatres[vm.playerId].mPlayer?.curEpisode!!).shouldStreamThisTime(null).start(vm.playerId)
                    if (theatres[vm.playerId].mPlayer?.curEpisode?.getMediaType() == MediaType.VIDEO && !theatres[vm.playerId].mPlayer!!.isPlaying && (vm.episodeFeed?.videoModePolicy != VideoMode.AUDIO_ONLY)) {
                        if (!theatres[vm.playerId].mPlayer!!.isPlaying) psState = PSState.Expanded
                    }
                }
            },
            onLongClick = {
                if (theatres[vm.playerId].mPlayer!!.isPlaying) {
                    val speedFB = fallbackSpeed
                    if (speedFB > 0.1f) theatres[vm.playerId].mPlayer?.toggleFallbackSpeed(speedFB)
                } })) {
            val playButRes by remember(vm.showPlayButton) { mutableIntStateOf(if (vm.showPlayButton) R.drawable.ic_play_48dp else R.drawable.ic_pause) }
            Icon(imageVector = ImageVector.vectorResource(playButRes), tint = buttonColor, contentDescription = "play", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
            if (fallbackSpeed > 0.1f) Text(fallbackSpeed.toString(), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.weight(0.1f))
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
            onClick = { theatres[vm.playerId].mPlayer?.seekDelta(fastForwardSecs * 1000) }, onLongClick = {
                if (theatres[vm.playerId].mPlayer!!.isPlaying) {
                    val speedForward = speedforwardSpeed
                    if (speedForward > 0.1f) theatres[vm.playerId].mPlayer?.speedForward(speedForward)
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
                if (theatres[vm.playerId].mPlayer!!.isPlaying) {
                    val speedForward = skipforwardSpeed
                    if (speedForward > 0.1f) theatres[vm.playerId].mPlayer?.speedForward(speedForward)
                } },
            onLongClick = {
                //                    context.sendBroadcast(MediaButtonReceiver.createIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
                if (theatres[vm.playerId].mPlayer!!.isPlaying || theatres[vm.playerId].mPlayer!!.isPaused) theatres[vm.playerId].mPlayer?.skip()
            })) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_skip_48dp), tint = buttonColor, contentDescription = "skip", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
            if (skipforwardSpeed > 0.1f) Text(formatNumberKmp(skipforwardSpeed), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.weight(0.1f))
    }
}

@Composable
fun ProgressBar(vm: AVPlayerVM) {
    Box(modifier = Modifier.fillMaxWidth()) {
        var sliderValue by remember(theatres[vm.playerId].mPlayer?.curEpisode?.position) { mutableFloatStateOf((theatres[vm.playerId].mPlayer?.curEpisode?.position?:0).toFloat()) }
        val actColor = MaterialTheme.colorScheme.tertiary
        val inActColor = MaterialTheme.colorScheme.secondaryFixedDim
        val distColor = remember { distinctColorOf(actColor, inActColor) }
        Slider(colors = SliderDefaults.colors(activeTrackColor = actColor,  inactiveTrackColor = inActColor), modifier = Modifier.height(12.dp).padding(top = 2.dp),
            value = sliderValue, valueRange = 0f..( if ((theatres[vm.playerId].mPlayer?.curEpisode?.duration?:0) > 0) theatres[vm.playerId].mPlayer?.curEpisode?.duration?:0 else 30000).toFloat(),
            onValueChange = { sliderValue = it }, onValueChangeFinished = { theatres[vm.playerId].mPlayer?.seekTo(sliderValue.toInt()) })
        if (vm.bufferValue > 0f) LinearProgressIndicator(progress = { vm.bufferValue }, color = MaterialTheme.colorScheme.primaryFixed.copy(alpha = 0.6f), trackColor = MaterialTheme.colorScheme.secondaryFixedDim, modifier = Modifier.height(8.dp).fillMaxWidth().align(Alignment.BottomStart))
        Text(durationStringFull(theatres[vm.playerId].mPlayer?.curEpisode?.duration?:0), color = distColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
    }
    Row {
        val pastText = remember(theatres[vm.playerId].mPlayer?.curEpisode?.position) { if (theatres[vm.playerId].mPlayer?.curEpisode == null) "" else durationStringAdapt(theatres[vm.playerId].mPlayer?.curEpisode!!.position) + " *" + durationStringAdapt(theatres[vm.playerId].mPlayer?.curEpisode!!.timeSpent.toInt()) }
        Text(pastText, color = textColor, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        if ((theatres[vm.playerId].mPlayer?.bitrate?:0) > 0) Text((if (theatres[vm.playerId].mPlayer?.isStereo == true) "Stereo" else "Mono") + ": " + formatLargeInteger(theatres[vm.playerId].mPlayer!!.bitrate) + "bits", color = textColor, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        val lengthText = remember(theatres[vm.playerId].mPlayer?.curPBSpeed, theatres[vm.playerId].mPlayer?.curEpisode?.position) {  run {
            if (theatres[vm.playerId].mPlayer?.curEpisode == null) return@run ""
            val remainingTime = max((theatres[vm.playerId].mPlayer?.curEpisode!!.duration - theatres[vm.playerId].mPlayer?.curEpisode!!.position), 0)
            val pbs = theatres[vm.playerId].mPlayer?.curPBSpeed?:0f
            val onSpeed = if (pbs > 0 && abs(pbs-1f) > 0.001) (remainingTime / pbs).toInt() else 0
            (if (onSpeed > 0) "*" + durationStringAdapt(onSpeed) else "") + " -" + durationStringAdapt(remainingTime)
        } }
        Text(lengthText, color = textColor, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AVPlayerScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val vm0: AVPlayerVM0 = viewModel()
    val vms: List<AVPlayerVM> = listOf(
        viewModel(key = "0", factory = viewModelFactory { initializer { AVPlayerVM(playerId = 0) } }),
        viewModel(key = "1", factory = viewModelFactory { initializer { AVPlayerVM(playerId = 1) } })
    )

    DisposableEffect(vms[0]) {
        vms[0].start()
        onDispose { vms[0].stop() }
    }

    DisposableEffect(vms[1], activeTheatres) {
        if (activeTheatres == 2) vms[1].start()
        else vms[1].stop()
        onDispose { vms[1].stop() }
    }

    LaunchedEffect(activeTheatres) { if (activeTheatres ==1) activePlayer = 0 }

    var showHomeText by remember { mutableStateOf(false) }

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
    if (isRotationEnabled) vm0.landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    else if (theatres[vms[activePlayer].playerId].mPlayer?.isPlayingVideoLocally == true && vms[activePlayer].episodeFeed != null && vms[activePlayer].episodeFeed!!.videoModePolicy != VideoMode.AUDIO_ONLY)
        vm0.landscape = vms[activePlayer].episodeFeed!!.videoModePolicy == VideoMode.FULL_SCREEN || appPrefs.videoPlaybackMode == VideoMode.FULL_SCREEN.code
    if (!vm0.landscape) vms[activePlayer].showActionBar = true

    LaunchedEffect(key1 = theatres[vms[0].playerId].mPlayer?.curEpisode?.id) {
        Logd(TAG, "LaunchedEffect curMediaId: ${theatres[vms[0].playerId].mPlayer?.curEpisode?.title}")
        showHomeText = false
        displayedChapterIndex = -1
        vms[0].episodeFeed = theatres[vms[0].playerId].mPlayer?.curEpisode?.feed
        if (psState == PSState.Hidden) psState = PSState.PartiallyExpanded
    }

    LaunchedEffect(key1 = theatres[vms[1].playerId].mPlayer?.curEpisode?.id) {
        Logd(TAG, "LaunchedEffect curMediaId: ${theatres[vms[1].playerId].mPlayer?.curEpisode?.title}")
        showHomeText = false
        displayedChapterIndex = -1
        vms[1].episodeFeed = theatres[vms[1].playerId].mPlayer?.curEpisode?.feed
        if (psState == PSState.Hidden) psState = PSState.PartiallyExpanded
    }

    LaunchedEffect(psState, activePlayer, theatres[vms[activePlayer].playerId].mPlayer?.curEpisode?.id) {
        Logd(TAG, "LaunchedEffect(isBSExpanded, curItem?.id) isBSExpanded: $psState")
        if (psState == PSState.Expanded) vm0.sleepTimerActive = isSleepTimerActive()
    }

    LaunchedEffect(activePlayer, theatres[vms[activePlayer].playerId].mPlayer?.curEpisode?.position) {
        if (theatres[vms[activePlayer].playerId].mPlayer?.curEpisode != null) {
            if (psState == PSState.Expanded) {
                chapterIndex = theatres[vms[activePlayer].playerId].mPlayer?.curEpisode!!.getCurrentChapterIndex(theatres[vms[activePlayer].playerId].mPlayer?.curEpisode!!.position)
                displayedChapterIndex = if (theatres[vms[activePlayer].playerId].mPlayer?.curEpisode!!.position > theatres[vms[activePlayer].playerId].mPlayer?.curEpisode!!.duration || chapterIndex >= theatres[vms[activePlayer].playerId].mPlayer?.curEpisode!!.chapters.size - 1) theatres[vms[activePlayer].playerId].mPlayer?.curEpisode!!.chapters.size - 1 else chapterIndex
                Logd(TAG, "LaunchedEffect(curEpisode?.position) chapterIndex $chapterIndex $displayedChapterIndex")
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect Lifecycle.Event: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> psState = PSState.PartiallyExpanded
                Lifecycle.Event.ON_START -> {}
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
    if (showAudioControlDialog) AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { showAudioControlDialog = false }, title = { Text(stringResource(R.string.audio_controls)) },
        text = {
            LazyColumn {
                items(theatres[vms[activePlayer].playerId].mPlayer!!.audioTracks) { track ->
                    Text(track, color = textColor, modifier = Modifier.clickable {
                        theatres[vms[activePlayer].playerId].mPlayer?.setAudioTrack(((theatres[vms[activePlayer].playerId].mPlayer?.getSelectedAudioTrack() ?: -1) + 1) % theatres[vms[activePlayer].playerId].mPlayer!!.audioTracks.size)
                        //                            Handler(Looper.getMainLooper()).postDelayed({ setupAudioTracks() }, 500)
                    })
                }
            }
        },
        confirmButton = { TextButton(onClick = { showAudioControlDialog = false }) { Text(stringResource(R.string.close_label)) } }
    )

    var showVolumeDialog by remember { mutableStateOf(false) }
    if (showVolumeDialog) VolumeDialog(vms[activePlayer]) { showVolumeDialog = false }

    var showSleepTimeDialog by remember { mutableStateOf(false) }
    if (showSleepTimeDialog) SleepTimerDialog { showSleepTimeDialog = false }

    var showSpeedDialog by remember { mutableStateOf(false) }
    if (showSpeedDialog) PlaybackSpeedFullDialog(vms[activePlayer].playerId, indexDefault = 0, maxSpeed = 3f, onDismiss = {showSpeedDialog = false})

    @Composable
    fun PlayerUI(vm: AVPlayerVM, modifier: Modifier) {
        Box(modifier = modifier.fillMaxWidth().height(100.dp).border(1.dp, MaterialTheme.colorScheme.tertiary)) {
            AsyncImage(model = theatres[vm.playerId].mPlayer?.curEpisode?.imageUrl?:theatres[vm.playerId].mPlayer?.curEpisode?.feed?.imageUrl?:"", contentDescription = "bgImage", contentScale = ContentScale.FillBounds, error = painterResource(R.drawable.teaser), modifier = Modifier.matchParentSize().blur(radiusX = 3.dp, radiusY = 3.dp))
            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)))
            Column {
                Text(theatres[vm.playerId].mPlayer?.curEpisode?.title ?: "No title", maxLines = 1, color = textColor, style = MaterialTheme.typography.bodyMedium)
                ProgressBar(vm)
                ControlUI(vm)
            }
        }
    }

    var showShareDialog by remember { mutableStateOf(false) }
    if (showShareDialog && theatres[vms[activePlayer].playerId].mPlayer?.curEpisode != null) ShareDialog(theatres[vms[activePlayer].playerId].mPlayer?.curEpisode!!) {showShareDialog = false }

    @Composable
    fun VideoToolBar(vm: AVPlayerVM, modifier: Modifier = Modifier) {
        var expanded by remember { mutableStateOf(false) }
        if (vm.showActionBar) Row(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down), tint = textColor, contentDescription = "Collapse", modifier = Modifier.clickable { psState = PSState.PartiallyExpanded })
            if (vm0.landscape) Column {
                Text(text = theatres[vm.playerId].mPlayer?.curEpisode?.title?:"", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = theatres[vm.playerId].mPlayer?.curEpisode?.feed?.title?:"", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                if (!vm.episodeFeed?.downloadUrl.isNullOrBlank() && gearbox.isGearFeed(vm.episodeFeed!!.downloadUrl!!)) IconButton(onClick = {
//                    vm.switchToAudioOnly = true
                    if (theatres[vm.playerId].mPlayer?.curEpisode != null) {
                        upsertBlk(theatres[vm.playerId].mPlayer?.curEpisode!!) { it.forceVideo = false }
                        theatres[vm.playerId].mPlayer?.pause(reinit = true)
                        PlaybackStarter(theatres[vm.playerId].mPlayer?.curEpisode!!).shouldStreamThisTime(null).start(force = true)
                    }
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_audiotrack_24), contentDescription = "audio only") }
                var sleepIconRes by remember { mutableIntStateOf(if (!isSleepTimerActive()) R.drawable.ic_sleep else R.drawable.ic_sleep_off) }
                IconButton(onClick = { showSleepTimeDialog = true }) { Icon(imageVector = ImageVector.vectorResource(sleepIconRes), contentDescription = "sleeper") }
                (context as? BaseActivity)?.CastIconButton()
                IconButton(onClick = { activePlayer = vm.playerId; showShareDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_share), contentDescription = "share") }
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, borderColor), onDismissRequest = { expanded = false }) {
                    if (vm0.landscape) {
                        var sleeperRes by remember { mutableIntStateOf(if (!isSleepTimerActive()) R.string.set_sleeptimer_label else R.string.sleep_timer_label) }
                        DropdownMenuItem(text = { Text(stringResource(sleeperRes)) }, onClick = {
                            showSleepTimeDialog = true
                            expanded = false
                        })
                        if (theatres[vm.playerId].mPlayer?.curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.queue)) }, onClick = {
                            navTo(Queues(id=actQueue.id))
                            expanded = false
                        })
                        if (theatres[vm.playerId].mPlayer?.curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.open_podcast)) }, onClick = {
                            if (vm.episodeFeed != null) navTo(FeedDetails(feedId=vm.episodeFeed!!.id))
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                            activePlayer = vm.playerId
                            showShareDialog = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.playback_speed)) }, onClick = {
                            activePlayer = vm.playerId
                            showSpeedDialog = true
                            expanded = false
                        })
                    }
                    if (theatres[vm.playerId].mPlayer!!.audioTracks.size >= 2) DropdownMenuItem(text = { Text(stringResource(R.string.audio_controls)) }, onClick = {
                        activePlayer = vm.playerId
                        showAudioControlDialog = true
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.visit_website_label)) }, onClick = {
                        val url = when {
                            theatres[vm.playerId].mPlayer?.curEpisode == null -> null
                            !theatres[vm.playerId].mPlayer?.curEpisode!!.link.isNullOrBlank() -> theatres[vm.playerId].mPlayer?.curEpisode!!.link
                            else -> theatres[vm.playerId].mPlayer?.curEpisode!!.getLinkWithFallback()
                        }
                        if (url != null) openInBrowser(url)
                        expanded = false
                    })
                }
            }
        }
    }

    @Composable
    fun Toolbar(vm: AVPlayerVM) {
        var expanded by remember { mutableStateOf(false) }
        val mediaType = remember(theatres[vm.playerId].mPlayer?.curEpisode?.id) { theatres[vm.playerId].mPlayer?.curEpisode?.getMediaType() }
        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down), tint = textColor, contentDescription = "Collapse", modifier = Modifier.clickable { psState = PSState.PartiallyExpanded })
            if (mediaType == MediaType.VIDEO && !vm.episodeFeed?.downloadUrl.isNullOrBlank() && gearbox.isGearFeed(vm.episodeFeed!!.downloadUrl!!)) Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_fullscreen_24), tint = textColor, contentDescription = "Play video",
                modifier = Modifier.clickable {
                    upsertBlk(theatres[vm.playerId].mPlayer?.curEpisode!!) { it.forceVideo = true }
                    theatres[vm.playerId].mPlayer?.pause(reinit = true)
                    PlaybackStarter(theatres[vm.playerId].mPlayer?.curEpisode!!).shouldStreamThisTime(null).start(force = true)
                })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_volume_adaption), tint = textColor, contentDescription = "Volume adaptation", modifier = Modifier.clickable {
                if (theatres[vm.playerId].mPlayer?.curEpisode != null) {
                    activePlayer = vm.playerId
                    showVolumeDialog = true
                } })
            val sleepRes = if (vm0.sleepTimerActive) R.drawable.ic_sleep_off else R.drawable.ic_sleep
            Icon(imageVector = ImageVector.vectorResource(sleepRes), tint = textColor, contentDescription = "Sleep timer", modifier = Modifier.clickable { showSleepTimeDialog = true })
            (context as? BaseActivity)?.CastIconButton()
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, borderColor), onDismissRequest = { expanded = false }) {
                    if (theatres[vm.playerId].mPlayer?.curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                        activePlayer = vm.playerId
                        showShareDialog = true
                        expanded = false
                    })
                    if (theatres[vm.playerId].mPlayer?.curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                        val notes = if (showHomeText) readerhtml else theatres[vm.playerId].mPlayer?.curEpisode?.description
                        if (!notes.isNullOrEmpty()) {
                            val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                            context.startActivity(intent)
                        }
                        expanded = false
                    })
                    if (theatres[vm.playerId].mPlayer?.curEpisode != null) DropdownMenuItem(text = { Text(stringResource(R.string.clear_cache)) }, onClick = {
                        runOnIOScope { getCache().removeResource(theatres[vm.playerId].mPlayer?.curEpisode!!.id.toString()) }
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
//                    DropdownMenuItem(text = { Text(stringResource(R.string.reset_player)) }, onClick = {
//                        playbackService?.recreateMediaPlayer()
//                        expanded = false
//                    })
                }
            }
        }
    }

    @Composable
    fun DetailUI(vm: AVPlayerVM, modifier: Modifier) {
        val comboAction = remember { Combo() }
        comboAction.ActionOptions()

        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(theatres[vm.playerId].mPlayer?.curEpisode!!)) { showChooseRatingDialog = false }
        val swipeVelocityThreshold = 1500f
        val swipeDistanceThreshold = with(LocalDensity.current) { 100.dp.toPx() }
        val velocityTracker = remember { VelocityTracker() }
        val offsetX = remember { Animatable(0f) }
        Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { velocityTracker.resetTracking() },
                onHorizontalDrag = { change, dragAmount ->
                    if (abs(dragAmount) > 4) {
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                },
                onDragEnd = {
                    scope.launch {
                        val velocity = velocityTracker.calculateVelocity().x
                        val distance = offsetX.value
                        val shouldSwipe = abs(distance) > swipeDistanceThreshold && abs(velocity) > swipeVelocityThreshold
                        if (shouldSwipe) {
                            if (distance > 0) navTo(Queues(id=actQueue.id))
                            else navTo(FeedDetails(feedId=vm.episodeFeed!!.id))
                            psState = PSState.PartiallyExpanded
                        }
                        offsetX.animateTo(targetValue = 0f, animationSpec = tween(300))
                    }
                },
            )
        }.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            var resetPlayer by remember { mutableStateOf(false) }
            if (theatres[vm.playerId].mPlayer?.curEpisode != null) gearbox.PlayerDetailedGearPanel(theatres[vm.playerId].mPlayer?.curEpisode!!, resetPlayer) { resetPlayer = it }
            SelectionContainer { Text(theatres[vm.playerId].mPlayer?.curEpisode?.title ?: "No title", textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)) }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(0.2f))
                val ratingIconRes by remember(theatres[vm.playerId].mPlayer?.curEpisode?.rating) { mutableIntStateOf( Rating.fromCode(theatres[vm.playerId].mPlayer?.curEpisode?.rating ?: Rating.UNRATED.code).res) }
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable { showChooseRatingDialog = true })
                Spacer(modifier = Modifier.weight(0.4f))
                val episodeDate = remember(theatres[vm.playerId].mPlayer?.curEpisode?.pubDate) { if (theatres[vm.playerId].mPlayer?.curEpisode == null) "" else formatDateTimeFlex(theatres[vm.playerId].mPlayer?.curEpisode!!.pubDate).trim() }
                Text(episodeDate, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(0.4f))
                if (theatres[vm.playerId].mPlayer?.curEpisode != null) Icon(imageVector = ImageVector.vectorResource(comboAction.iconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "Combo", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).clickable {  comboAction.performAction(theatres[vm.playerId].mPlayer?.curEpisode!!) })
                Spacer(modifier = Modifier.weight(0.2f))
            }
            SelectionContainer { Text((vm.episodeFeed?.title?:"").trim(), textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)) }

            if (theatres[vm.playerId].mPlayer?.curEpisode != null) EpisodeDetails(theatres[vm.playerId].mPlayer?.curEpisode!!, psState == PSState.Expanded, true)

            if (theatres[vm.playerId].mPlayer?.curEpisode != null) {
                val imgLarge = remember(theatres[vm.playerId].mPlayer?.curEpisode!!.id, displayedChapterIndex) {
                    if (displayedChapterIndex == -1 || theatres[vm.playerId].mPlayer?.curEpisode!!.chapters.isEmpty() || theatres[vm.playerId].mPlayer?.curEpisode!!.chapters[displayedChapterIndex].imageUrl.isNullOrEmpty()) theatres[vm.playerId].mPlayer?.curEpisode!!.imageUrl ?: theatres[vm.playerId].mPlayer?.curEpisode?.feed?.imageUrl
                    else EmbeddedChapterImage.getModelFor(theatres[vm.playerId].mPlayer?.curEpisode!!, displayedChapterIndex)?.toString()
                }
                if (imgLarge != null) AsyncImage( ImageRequest.Builder(context).data(imgLarge).memoryCachePolicy(CachePolicy.ENABLED).build(), placeholder = painterResource(R.drawable.ic_launcher_foreground), error = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "imgvCover", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(10.dp))
            }
        }
    }

    @Composable
    fun FullScreenVideoPlayer(vm: AVPlayerVM) {
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
                if (!isRotationEnabled) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = theatres[vm.playerId].mPlayer?.castPlayer
                        useController = true
                        //                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { playerView ->
                    playerView.player = theatres[vm.playerId].mPlayer?.castPlayer
                    playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> vm.showActionBar = visibility == View.VISIBLE })
                },
                onRelease = { playerView -> playerView.player = null }
            )
        }
    }

    @Composable
    fun VideoPlayer(vm: AVPlayerVM) {
        AndroidView(modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            },
            update = { playerView -> playerView.player = theatres[vm.playerId].mPlayer?.castPlayer },
            onRelease = { view -> view.player = null }
        )
    }

//    Logd(TAG, "landscape: ${vm.landscape}")
//    if ((landscape || curVideoMode == VideoMode.FULL_SCREEN || (curVideoMode == VideoMode.DEFAULT && appPrefs.videoPlaybackMode == VideoMode.FULL_SCREEN.code)) && playVideo && bsState == BSState.Expanded) {
    if (vm0.landscape && theatres[vms[activePlayer].playerId].mPlayer?.playingVideo == true && psState == PSState.Expanded) {
        Box {
            FullScreenVideoPlayer(vms[activePlayer])
            VideoToolBar(vms[activePlayer], modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    } else Box(modifier = Modifier.fillMaxWidth().then(if (psState == PSState.PartiallyExpanded) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier.statusBarsPadding().navigationBarsPadding())) {
        Column(Modifier.align(if (psState == PSState.PartiallyExpanded) Alignment.TopCenter else Alignment.BottomCenter).zIndex(1f)) {
            if (activeTheatres == 2) {
                PlayerUI(vms[1], Modifier)
                var sliderValue by remember { mutableFloatStateOf(0.5f) }
                Slider(modifier = Modifier.height(12.dp).padding(top = 2.dp), value = sliderValue, onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        when {
                            sliderValue > 0.51 -> {
                                val v = ((1f - sliderValue) / 0.5f).pow(2)
                                theatres[vms[1].playerId].mPlayer?.setVolume(1f, 1f)
                                theatres[vms[0].playerId].mPlayer?.setVolume(v, v)
                            }
                            sliderValue < 0.49 -> {
                                val v = (sliderValue / 0.5f).pow(2)
                                theatres[vms[1].playerId].mPlayer?.setVolume(v, v)
                                theatres[vms[0].playerId].mPlayer?.setVolume(1f, 1f)
                            }
                            else -> {
                                theatres[vms[0].playerId].mPlayer?.setVolume(1f, 1f)
                                theatres[vms[1].playerId].mPlayer?.setVolume(1f, 1f)
                            }
                        }
                    })
            }
            PlayerUI(vms[0], Modifier)
        }
        if (psState == PSState.Expanded) {
            Column(Modifier.padding(bottom = playerMinHeight.dp)) {
                if (theatres[vms[activePlayer].playerId].mPlayer?.playingVideo == true) {
                    VideoToolBar(vms[activePlayer])
                    VideoPlayer(vms[activePlayer])
                } else Toolbar(vms[activePlayer])
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), tint = buttonColor, contentDescription = "queues icon", modifier = Modifier.width(24.dp).height(24.dp))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "left_arrow", modifier = Modifier.width(24.dp).height(24.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(24.dp).height(24.dp))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_feed), tint = buttonColor, contentDescription = "feed icon", modifier = Modifier.width(24.dp).height(24.dp))
                }
                DetailUI(vms[activePlayer], modifier = Modifier.fillMaxSize())
            }
        }
    }
}
