package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre.aController
import ac.mdiq.podcini.playback.base.InTheatre.aCtrlFuture
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.bitrate
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
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
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.playPause
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.simpleCache
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.SleepManager.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.cast.BaseActivity
import ac.mdiq.podcini.playback.saveClipInOriginalFormat
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.fallbackSpeed
import ac.mdiq.podcini.preferences.AppPreferences.videoPlayMode
import ac.mdiq.podcini.preferences.SleepTimerPreferences.SleepTimerDialog
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PLAYING
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.EmbeddedChapterImage
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.specs.VolumeAdaptionSetting
import ac.mdiq.podcini.storage.utils.durationStringAdapt
import ac.mdiq.podcini.storage.utils.durationStringFull
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.bsState
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeDetails
import ac.mdiq.podcini.ui.compose.PlaybackSpeedFullDialog
import ac.mdiq.podcini.ui.compose.ShareDialog
import ac.mdiq.podcini.ui.compose.distinctColorOf
import ac.mdiq.podcini.ui.utils.starter.VideoPlayerActivityStarter
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.FlowEvent.BufferUpdateEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.formatDateTimeFlex
import ac.mdiq.podcini.utils.formatLargeInteger
import android.content.ComponentName
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.common.util.concurrent.MoreExecutors
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class AudioPlayerVM: ViewModel() {
    internal var txtvPlaybackSpeed by mutableStateOf("")
    internal var curPlaybackSpeed by mutableFloatStateOf(1f)

    internal var sleepTimerActive by mutableStateOf(isSleepTimerActive())

    internal var bufferValue by mutableFloatStateOf(0f)
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AudioPlayerScreen(navController: AppNavigator) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val vm: AudioPlayerVM = viewModel()

    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = Color(0xDDFFD700)

    val actMain: MainActivity? = remember { generateSequence(context) { if (it is ContextWrapper) it.baseContext else null }.filterIsInstance<MainActivity>().firstOrNull() }

    var episodeFlow by remember { mutableStateOf<Flow<SingleQueryChange<Episode>>>(emptyFlow()) }

    var showPlayButton by remember { mutableStateOf(true) }

    var showHomeText by remember { mutableStateOf(false) }
    var chapertsLoaded by remember { mutableStateOf(false) }

    // TODO: somehow, these 2 are not used?
    //     var homeText: String? = remember { null }
    var readerhtml: String? by remember { mutableStateOf(null) }

    var volumeAdaption: VolumeAdaptionSetting by remember { mutableStateOf(VolumeAdaptionSetting.OFF) }

    val episodeChange by episodeFlow.collectAsStateWithLifecycle(initialValue = null)
    val curItem = episodeChange?.obj

    //    private var chapterControlVisible by mutableStateOf(false)
    var chapterIndex by remember { mutableIntStateOf(-1) }
    var displayedChapterIndex by remember { mutableIntStateOf(-1) }
    val curChapter = remember(curItem?.id, displayedChapterIndex) { if (curItem?.chapters.isNullOrEmpty() || displayedChapterIndex == -1) null else curItem.chapters[displayedChapterIndex] }
    var nextChapterStart by remember { mutableIntStateOf(Int.MAX_VALUE) }

    LaunchedEffect(key1 = curEpisode?.id) {
        Logd(TAG, "LaunchedEffect curMediaId: ${curItem?.title}")
        if (curEpisode != null) episodeFlow = realm.query<Episode>("id == $0", curEpisode!!.id).first().asFlow()
        showHomeText = false //            homeText = null
        chapertsLoaded = false
        volumeAdaption = VolumeAdaptionSetting.OFF
        displayedChapterIndex = -1
        vm.txtvPlaybackSpeed = DecimalFormat("0.00").format(curPBSpeed.toDouble())
        vm.curPlaybackSpeed = curPBSpeed
        if (isPlayingVideoLocally && curItem?.feed?.videoModePolicy != VideoMode.AUDIO_ONLY) bsState = MainActivity.BSState.Partial
    }

    LaunchedEffect(bsState, curItem?.id) {
        Logd(TAG, "LaunchedEffect(isBSExpanded, curItem?.id) isBSExpanded: $bsState")
        if (bsState == MainActivity.BSState.Expanded) {
            Logd(TAG, "LaunchedEffect loading details ${curItem?.id}")
            if (curItem != null && !chapertsLoaded) {
                scope.launch(Dispatchers.IO) {
                    gearbox.loadChapters(curItem)
                    vm.sleepTimerActive = isSleepTimerActive()
                    chapertsLoaded = true
                }.invokeOnCompletion { throwable -> if (throwable != null) Logs(TAG, throwable) }
            }
        }
    }

    LaunchedEffect(curItem?.position) {
        if (curItem != null) {
            if (bsState == MainActivity.BSState.Expanded) {
                chapterIndex = curItem.getCurrentChapterIndex(curItem.position)
                displayedChapterIndex = if (curItem.position > curItem.duration || chapterIndex >= curItem.chapters.size - 1) curItem.chapters.size - 1 else chapterIndex
                Logd(TAG, "LaunchedEffect(curItem?.position) chapterIndex $chapterIndex $displayedChapterIndex")
            }
            if (showPlayButton) showPlayButton = !isCurrentlyPlaying(curItem)
        }
    }

    var eventSink by remember { mutableStateOf<Job?>(null) }
    fun cancelFlowEvents() {
        Logd(TAG, "cancelFlowEvents")
        eventSink?.cancel()
        eventSink = null
    }
    fun procFlowEvents() {
        Logd(TAG, "procFlowEvents")
        if (eventSink == null) eventSink = scope.launch {
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
                            mPlayer?.isStreaming == true -> vm.bufferValue = event.progress
                            else -> vm.bufferValue = 0f
                        }
                    }
                    is FlowEvent.SleepTimerUpdatedEvent -> vm.sleepTimerActive = isSleepTimerActive()
                    is FlowEvent.SpeedChangedEvent -> {
                        vm.curPlaybackSpeed = event.newSpeed
                        vm.txtvPlaybackSpeed = DecimalFormat("0.00").format(event.newSpeed.toDouble())
                    }
                    else -> {}
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect Lifecycle.Event: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    bsState = MainActivity.BSState.Partial
                    procFlowEvents()
                }
                Lifecycle.Event.ON_START -> {
                    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    if (aCtrlFuture == null) {
                        aCtrlFuture = MediaController.Builder(context, sessionToken).buildAsync()
                        aCtrlFuture?.addListener({ aController = aCtrlFuture!!.get() }, MoreExecutors.directExecutor())
                    }
                    if (curItem != null) showPlayButton = !isCurrentlyPlaying(curItem)
                }
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            cancelFlowEvents()
            if (aCtrlFuture != null) MediaController.releaseFuture(aCtrlFuture!!)
            aCtrlFuture =  null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showVolumeDialog by remember { mutableStateOf(false) }
    var showSleepTimeDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    if (showVolumeDialog) CommonPopupCard(onDismissRequest = { showVolumeDialog = false }) {
        fun adaptionFactor(): Float = when {
            volumeAdaption != VolumeAdaptionSetting.OFF -> volumeAdaption.adaptionFactor
            curItem?.feed != null -> curItem.feed!!.volumeAdaptionSetting.adaptionFactor
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
                    Checkbox(checked = (setting == volumeAdaption),
                        onCheckedChange = { _ ->
                            if (forPodcast && curItem?.feed != null) runOnIOScope { upsert(curItem.feed!!) { it.volumeAdaptionSetting = setting} }
                            if (setting != volumeAdaption) {
                                volumeAdaption = setting
                                mPlayer?.setVolume(1.0f, 1.0f, adaptionFactor())
                                showVolumeDialog = false
                            }
                        }
                    )
                    Text(text = stringResource(setting.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
    if (showSleepTimeDialog) SleepTimerDialog { showSleepTimeDialog = false }
    if (showSpeedDialog) PlaybackSpeedFullDialog(settingCode = booleanArrayOf(true, true, true), indexDefault = 0, maxSpeed = 3f, onDismiss = {showSpeedDialog = false})

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ControlUI() {
        val buttonColor1 = Color(0xEEAA7700)
        LaunchedEffect(key1 = playerStat) { showPlayButton = playerStat != PLAYER_STATUS_PLAYING }

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
        val offsetX = remember(curItem?.id) { Animatable(0f) }
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
                            if (distance < 0) bsState = MainActivity.BSState.Hidden
                            else showSleepTimeDialog = true
                        }
//                        offsetX.animateTo(targetValue = 0f, animationSpec = tween(300))
                    }
                },
            )
        }) {
            fun ensureService() {
                if (curItem == null) return
                if (playbackService == null) PlaybackStarter(curItem).start()
            }
            val img = remember(curItem?.id) { ImageRequest.Builder(context).data(curItem?.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
            AsyncImage(model = img, contentDescription = "imgvCover", modifier = Modifier.width(50.dp).height(50.dp).border(border = BorderStroke(1.dp, buttonColor)).padding(start = 5.dp).combinedClickable(
                onClick = {
                    Logd(TAG, "playerUi icon was clicked")
                    if (bsState == MainActivity.BSState.Partial) {
                        if (curItem != null) {
                            val mediaType = curItem.getMediaType()
                            if (mediaType == MediaType.AUDIO || videoPlayMode == VideoMode.AUDIO_ONLY.code || videoMode == VideoMode.AUDIO_ONLY || (curItem.feed?.videoModePolicy == VideoMode.AUDIO_ONLY)) {
                                Logd(TAG, "popping as audio episode")
                                ensureService()
                                bsState = MainActivity.BSState.Expanded
                            } else {
                                Logd(TAG, "popping video context")
                                val intent = getPlayerActivityIntent(context, mediaType)
                                context.startActivity(intent)
                            }
                        }
                    } else bsState = MainActivity.BSState.Partial
                },
                onLongClick = {
                    if (curItem?.feed != null) {
                        navController.navigate("${Screens.FeedDetails.name}?feedId=${curItem.feed!!.id}")
                        bsState = MainActivity.BSState.Partial
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
            val recordColor = if (recordingStartTime == null) { if (curItem != null && exoPlayer != null && isPlaying) buttonColor else Color.Gray } else Color.Red
            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_fiber_manual_record_24), tint = recordColor, contentDescription = "record",
                modifier = Modifier.size(buttonSize).combinedClickable(
                    onClick = {
                        if (curItem != null && exoPlayer != null && isPlaying) {
                            if (recordingStartTime == null) {
                                recordingStartTime = exoPlayer!!.currentPosition
                                saveClipInOriginalFormat(recordingStartTime!!)
                            }
                            else {
                                saveClipInOriginalFormat(recordingStartTime!!, exoPlayer!!.currentPosition)
                                recordingStartTime = null
                            }
                        } else Loge(TAG, "Recording only works during playback.") },
                    onLongClick = {
                        if (curItem != null && exoPlayer != null && isPlaying) {
                            val pos = exoPlayer!!.currentPosition
                            runOnIOScope { upsert(curItem) { it.marks.add(pos) } }
                            Logt(TAG, "position $pos marked for ${curItem.title}")
                        } else Loge(TAG, "Marking position only works during playback.")
                    }))
            Spacer(Modifier.weight(0.1f))
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
                onClick = { mPlayer?.seekDelta(-AppPreferences.rewindSecs * 1000) }, onLongClick = { mPlayer?.seekTo(0) })) {
                val rewindSecs by remember { mutableStateOf(NumberFormat.getInstance().format(AppPreferences.rewindSecs.toLong())) }
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
                    if (curItem != null) {
                        showPlayButton = !showPlayButton
                        if (showPlayButton && recordingStartTime != null) {
                            saveClipInOriginalFormat(recordingStartTime!!, exoPlayer!!.currentPosition)
                            recordingStartTime = null
                        }
                        if (curItem.getMediaType() == MediaType.VIDEO && !isPlaying && (curItem.feed?.videoModePolicy != VideoMode.AUDIO_ONLY)) {
                            playPause()
                            context.startActivity(getPlayerActivityIntent(context, curItem.getMediaType()))
                        } else {
                            Logd(TAG, "Play button clicked: status: $status is ready: ${playbackService?.isServiceReady()}")
                            PlaybackStarter(curItem).shouldStreamThisTime(null).start()
                        }
                    }
                },
                onLongClick = {
                    if (isPlaying) {
                        val speedFB = fallbackSpeed
                        if (speedFB > 0.1f) toggleFallbackSpeed(speedFB)
                    } })) {
                val playButRes by remember(showPlayButton) { mutableIntStateOf(if (showPlayButton) R.drawable.ic_play_48dp else R.drawable.ic_pause) }
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
                onClick = { mPlayer?.seekDelta(AppPreferences.fastForwardSecs * 1000) }, onLongClick = {
                    if (isPlaying) {
                        val speedForward = AppPreferences.speedforwardSpeed
                        if (speedForward > 0.1f) speedForward(speedForward)
                    }
                })) {
                val fastForwardSecs by remember { mutableStateOf(NumberFormat.getInstance().format(AppPreferences.fastForwardSecs.toLong())) }
                Text(fastForwardSecs, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.TopCenter))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_fast_forward), tint = buttonColor, contentDescription = "forward", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
                if (AppPreferences.speedforwardSpeed > 0.1f) Text(NumberFormat.getInstance().format(AppPreferences.speedforwardSpeed), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.weight(0.1f))
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
                onClick = {
                    if (isPlaying) {
                        val speedForward = AppPreferences.skipforwardSpeed
                        if (speedForward > 0.1f) speedForward(speedForward)
                    } },
                onLongClick = {
                    //                    context.sendBroadcast(MediaButtonReceiver.createIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
                    if (isPlaying || isPaused) mPlayer?.skip()
                })) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_skip_48dp), tint = buttonColor, contentDescription = "skip", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
                if (AppPreferences.skipforwardSpeed > 0.1f) Text(NumberFormat.getInstance().format(AppPreferences.skipforwardSpeed), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.weight(0.1f))
        }
    }

    @Composable
    fun ProgressBar() {
        Box(modifier = Modifier.fillMaxWidth()) {
            var sliderValue by remember(curItem?.position) { mutableFloatStateOf((curItem?.position?:0).toFloat()) }
            val actColor = MaterialTheme.colorScheme.tertiary
            val inActColor = MaterialTheme.colorScheme.secondaryFixedDim
            val distColor = remember { distinctColorOf(actColor, inActColor) }
            Slider(colors = SliderDefaults.colors(activeTrackColor = actColor,  inactiveTrackColor = inActColor), modifier = Modifier.height(12.dp).padding(top = 2.dp),
                value = sliderValue, valueRange = 0f..( if ((curItem?.duration?:0) > 0) curItem?.duration?:0 else 30000).toFloat(),
                onValueChange = { sliderValue = it }, onValueChangeFinished = { mPlayer?.seekTo(sliderValue.toInt()) })
            if (vm.bufferValue > 0f) LinearProgressIndicator(progress = { vm.bufferValue }, color = MaterialTheme.colorScheme.primaryFixed.copy(alpha = 0.6f), trackColor = MaterialTheme.colorScheme.secondaryFixedDim, modifier = Modifier.height(8.dp).fillMaxWidth().align(Alignment.BottomStart))
            Text(durationStringFull(curItem?.duration?:0), color = distColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Row {
            val pastText by remember(curItem?.position) { mutableStateOf( run {
                if (curItem == null) return@run ""
                durationStringAdapt(curItem.position) + " *" + durationStringAdapt(curItem.timeSpent.toInt())
            } ) }
            Text(pastText, color = textColor, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            if (bitrate > 0) Text(formatLargeInteger(bitrate) + "bits", color = textColor, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            val lengthText by remember(curItem?.position) { mutableStateOf( run {
                if (curItem == null) return@run ""
                val remainingTime = max((curItem.duration - curItem.position), 0)
                val onSpeed = if (curPBSpeed > 0 && abs(curPBSpeed-1f) > 0.001) (remainingTime / curPBSpeed).toInt() else 0
                (if (onSpeed > 0) "*" + durationStringAdapt(onSpeed) else "") + " -" + durationStringAdapt(remainingTime)
            }) }
            Text(lengthText, color = textColor, style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    fun PlayerUI(modifier: Modifier) {
        Box(modifier = modifier.fillMaxWidth().height(100.dp).border(1.dp, MaterialTheme.colorScheme.tertiary).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
            Column {
                Text(curItem?.title ?: "No title", maxLines = 1, color = textColor, style = MaterialTheme.typography.bodyMedium)
                ProgressBar()
                ControlUI()
            }
        }
    }

    @Composable
    fun Toolbar() {
        var expanded by remember { mutableStateOf(false) }
        val mediaType = curItem?.getMediaType()
        val notAudioOnly = curItem?.feed?.videoModePolicy != VideoMode.AUDIO_ONLY
        var showShareDialog by remember { mutableStateOf(false) }
        if (showShareDialog && curItem != null && actMain != null) ShareDialog(curItem, actMain) {showShareDialog = false }
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down), tint = textColor, contentDescription = "Collapse", modifier = Modifier.clickable { bsState = MainActivity.BSState.Partial })
            if (curItem != null) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_feed), tint = textColor, contentDescription = "Open podcast",
                modifier = Modifier.clickable {
                    if (curItem.feed != null) {
                        navController.navigate("${Screens.FeedDetails.name}?feedId=${curItem.feed!!.id}")
                        bsState = MainActivity.BSState.Partial
                    }
                })
            IconButton(onClick = {
                navController.navigate("${Screens.Queues.name}?id=${actQueue.id}")
                bsState = MainActivity.BSState.Partial
            }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), contentDescription = "queue") }
            if (mediaType == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_fullscreen_24), tint = textColor, contentDescription = "Play video",
                modifier = Modifier.clickable {
                    if (!notAudioOnly && !curItem.forceVideo) {
                        upsertBlk(curItem) { it.forceVideo = false }
                        status = PlayerStatus.STOPPED
                        mPlayer?.pause(reinit = true)
                        playbackService?.recreateMediaPlayer()
                    }
                    VideoPlayerActivityStarter(context).start()
                })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_volume_adaption), tint = textColor, contentDescription = "Volume adaptation", modifier = Modifier.clickable { if (curItem != null) showVolumeDialog = true })
            val sleepRes = if (vm.sleepTimerActive) R.drawable.ic_sleep_off else R.drawable.ic_sleep
            Icon(imageVector = ImageVector.vectorResource(sleepRes), tint = textColor, contentDescription = "Sleep timer", modifier = Modifier.clickable { showSleepTimeDialog = true })
            (context as? BaseActivity)?.CastIconButton()
            IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
            DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                if (curItem != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                    showShareDialog = true
                    expanded = false
                })
                if (curItem != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                    val notes = if (showHomeText) readerhtml else curItem?.description
                    if (!notes.isNullOrEmpty()) {
                        val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                        val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                        context.startActivity(intent)
                    }
                    expanded = false
                })
                if (curItem != null) DropdownMenuItem(text = { Text(stringResource(R.string.clear_cache)) }, onClick = {
                    runOnIOScope { getCache().removeResource(curItem.id.toString()) }
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

    @Composable
    fun DetailUI(modifier: Modifier) {
        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(curItem!!)) { showChooseRatingDialog = false }
        Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            var resetPlayer by remember { mutableStateOf(false) }
            if (curItem != null) gearbox.PlayerDetailedGearPanel(curItem, resetPlayer) { resetPlayer = it }
            SelectionContainer { Text(curItem?.title ?: "No title", textAlign = TextAlign.Center, color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)) }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
                Spacer(modifier = Modifier.weight(0.2f))
                val ratingIconRes by remember(curItem?.rating) { mutableIntStateOf( Rating.fromCode(curItem?.rating ?: Rating.UNRATED.code).res) }
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = { showChooseRatingDialog = true }))
                Spacer(modifier = Modifier.weight(0.4f))
                val episodeDate by remember(curItem) { mutableStateOf(if (curItem == null) "" else formatDateTimeFlex(Date(curItem.pubDate)).trim()) }
                Text(episodeDate, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(0.6f))
            }
            SelectionContainer { Text((curItem?.feed?.title?:"").trim(), textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)) }

            if (curItem != null) EpisodeDetails(curItem, episodeFlow, bsState == MainActivity.BSState.Expanded)

            if (curItem != null) {
                val imgLarge = remember(curItem.id, displayedChapterIndex) {
                    if (displayedChapterIndex == -1 || curItem.chapters.isEmpty() || curItem.chapters[displayedChapterIndex].imageUrl.isNullOrEmpty()) curItem.imageUrl ?: curItem.feed?.imageUrl
                    else EmbeddedChapterImage.getModelFor(curItem, displayedChapterIndex)?.toString()
                }
                if (imgLarge != null) {
                    val img = remember(imgLarge) { ImageRequest.Builder(context).data(imgLarge).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                    AsyncImage(img, contentDescription = "imgvCover", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 10.dp).clickable(onClick = {}))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().then(if (bsState == MainActivity.BSState.Partial) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier.statusBarsPadding().navigationBarsPadding())) {
        PlayerUI(Modifier.align(if (bsState == MainActivity.BSState.Partial) Alignment.TopCenter else Alignment.BottomCenter).zIndex(1f))
        if (bsState == MainActivity.BSState.Expanded) {
            Column(Modifier.padding(bottom = 120.dp)) {
                Toolbar()
                DetailUI(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private const val TAG = "AudioPlayerScreen"
