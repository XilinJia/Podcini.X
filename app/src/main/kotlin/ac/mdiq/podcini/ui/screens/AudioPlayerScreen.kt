package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.Recorder.saveClipInOriginalFormat
import ac.mdiq.podcini.playback.base.InTheatre.aController
import ac.mdiq.podcini.playback.base.InTheatre.aCtrlFuture
import ac.mdiq.podcini.playback.base.InTheatre.bitrate
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMediaId
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.InTheatre.onCurChangedUICB
import ac.mdiq.podcini.playback.base.InTheatre.onCurInitUICB
import ac.mdiq.podcini.playback.base.InTheatre.playerStat
import ac.mdiq.podcini.playback.base.LocalMediaPlayer.Companion.exoPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.curDurationFB
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.curPBSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCache
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isFallbackSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isSpeedForward
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.normalSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.playPause
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.TaskManager.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.cast.BaseActivity
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.fallbackSpeed
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.AppPreferences.videoPlayMode
import ac.mdiq.podcini.preferences.SleepTimerPreferences.SleepTimerDialog
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PLAYING
import ac.mdiq.podcini.storage.model.EmbeddedChapterImage
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.DurationConverter.convertOnSpeed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.isBSExpanded
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.compose.ChaptersDialog
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeClips
import ac.mdiq.podcini.ui.compose.EpisodeMarks
import ac.mdiq.podcini.ui.compose.PlaybackSpeedFullDialog
import ac.mdiq.podcini.ui.compose.ShareDialog
import ac.mdiq.podcini.ui.compose.SkipDialog
import ac.mdiq.podcini.ui.compose.SkipDirection
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.episodeOnDisplay
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.ui.utils.starter.VideoPlayerActivityStarter
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.FlowEvent.BufferUpdateEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.MiscFormatter
import ac.mdiq.podcini.util.MiscFormatter.formatLargeInteger
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.common.util.concurrent.MoreExecutors
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class AudioPlayerVM(val context: Context, val lcScope: CoroutineScope) {
    internal val actMain: MainActivity? = generateSequence(context) { if (it is ContextWrapper) it.baseContext else null }.filterIsInstance<MainActivity>().firstOrNull()

    internal var playerLocal: ExoPlayer? = null

    private var prevItem: Episode? = null
    internal var curItem by mutableStateOf<Episode?>(null)

    private var playButInit = false
    internal var showPlayButton: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                playButRes = when {
                    isVideoScreen -> if (value) R.drawable.ic_play_video_white else R.drawable.ic_pause_video_white
                    value -> R.drawable.ic_play_48dp
                    else -> R.drawable.ic_pause
                }
            }
        }
    internal var showTimeLeft = false
    internal var titleText by mutableStateOf("")
    internal var imgLoc by mutableStateOf<String?>(null)
    internal var imgLocLarge by mutableStateOf<String?>(null)
    internal var txtvPlaybackSpeed by mutableStateOf("")
    internal var curPlaybackSpeed by mutableStateOf(1f)
    private var isVideoScreen = false
    internal var playButRes by mutableIntStateOf(R.drawable.ic_play_48dp)
    internal var curPosition by mutableIntStateOf(0)
    internal var duration by mutableIntStateOf(0)
    internal var txtvLengtTexth by mutableStateOf("")
    internal var sliderValue by mutableFloatStateOf(0f)
    internal var bufferValue by mutableFloatStateOf(0f)
    internal var sleepTimerActive by mutableStateOf(isSleepTimerActive())
    internal var showSpeedDialog by mutableStateOf(false)

    internal var shownotesCleaner: ShownotesCleaner? = null

    internal var cleanedNotes by mutableStateOf<String?>(null)
    private var homeText: String? = null
    internal var showHomeText = false
    internal var readerhtml: String? = null

    internal var txtvPodcastTitle by mutableStateOf("")
    internal var episodeDate by mutableStateOf("")

    //    private var chapterControlVisible by mutableStateOf(false)
    internal var hasNextChapter by mutableStateOf(true)
    internal var rating by mutableStateOf(curItem?.rating ?: Rating.UNRATED.code)

    internal var resetPlayer by mutableStateOf(false)
//    internal val showErrorDialog = mutableStateOf(false)
//    internal var errorMessage by mutableStateOf("")

    internal var recordingStartTime by mutableStateOf<Long?>(null)

    internal var displayedChapterIndex by mutableIntStateOf(-1)
    internal val curChapter: Chapter?
        get() {
            if (curItem?.chapters.isNullOrEmpty() || displayedChapterIndex == -1) return null
            return curItem!!.chapters[displayedChapterIndex]
        }

    private var eventSink: Job?     = null
    internal fun cancelFlowEvents() {
        Logd(TAG, "cancelFlowEvents")
        eventSink?.cancel()
        eventSink = null
    }
    internal fun procFlowEvents() {
        Logd(TAG, "procFlowEvents")
        if (eventSink == null) eventSink = lcScope.launch {
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
                    is BufferUpdateEvent -> bufferUpdate(event)
//                    is FlowEvent.SleepTimerUpdatedEvent ->  if (event.isCancelled || event.wasJustEnabled()) loadMediaInfo(false)
                    is FlowEvent.SleepTimerUpdatedEvent ->  if (event.isCancelled || event.wasJustEnabled()) sleepTimerActive = isSleepTimerActive()
                    is FlowEvent.SpeedChangedEvent -> updatePlaybackSpeedButton(event)
                    else -> {}
                }
            }
        }
    }
    suspend fun onEpisodeInit(e: Episode) {
        withContext(Dispatchers.Main) {
            curItem = e
            Logd(TAG, "monitor: curItem changed")
            rating = e.rating
            updateUi()
            updateDetails()
            updateTimeline()
        }
    }
    suspend fun onEpisodeChanged(e: Episode, fields: Array<String>) {
        withContext(Dispatchers.Main) {
            Logd(TAG, "monitor: ${fields.joinToString()}")
            var isChanged = false
            var isDetailChanged = false
            for (f in fields) {
                if (f in listOf("startPosition", "timeSpent", "playedDurationWhenStarted", "timeSpentOnStart", "position", "startTime", "lastPlayedTime", "playedDuration")) continue
                isChanged = true
                if (f == "clips" || f == "marks") isDetailChanged = true
            }
            curItem = e
            if (isChanged) {
                Logd(TAG, "monitor: curItem changed")
                rating = curItem!!.rating
                updateUi()
                if (isDetailChanged) updateDetails()
            }
            updateTimeline()
        }
    }
    private fun updateTimeline() {
        if (isBSExpanded) {
            val newChapterIndex: Int = curItem!!.getCurrentChapterIndex(curItem!!.position)
            if (newChapterIndex >= 0 && newChapterIndex != displayedChapterIndex) refreshChapterData(newChapterIndex)
        }
        if (!playButInit && playButRes == R.drawable.ic_play_48dp && curEpisode != null) {
            showPlayButton = !isCurrentlyPlaying(curEpisode)
            playButInit = true
        }
        curPosition = convertOnSpeed(curItem!!.position, curPBSpeed)
        duration = convertOnSpeed(curItem!!.duration, curPBSpeed)
        val remainingTime: Int = convertOnSpeed(max((curItem!!.duration - curItem!!.position).toDouble(), 0.0).toInt(), curPBSpeed)
        showTimeLeft = getPref(AppPrefs.showTimeLeft, false)
        txtvLengtTexth = if (showTimeLeft) (if (remainingTime > 0) "-" else "") + DurationConverter.getDurationStringLong(remainingTime) else DurationConverter.getDurationStringLong(duration)
        sliderValue = curItem!!.position.toFloat()
    }

    private fun bufferUpdate(event: BufferUpdateEvent) {
        Logd(TAG, "bufferUpdate ${mPlayer?.isStreaming} ${event.progress}")
        when {
            event.hasStarted() || event.hasEnded() -> {}
            mPlayer?.isStreaming == true -> bufferValue = event.progress
            else -> bufferValue = 0f
        }
    }
    private fun updatePlaybackSpeedButton(event: FlowEvent.SpeedChangedEvent) {
        curPlaybackSpeed = event.newSpeed
        txtvPlaybackSpeed = DecimalFormat("0.00").format(event.newSpeed.toDouble())
    }
    internal fun updateUi() {
//        Logd(TAG, "updateUi called $media")
        titleText = curItem?.getEpisodeTitle() ?: ""
        txtvPlaybackSpeed = DecimalFormat("0.00").format(curPBSpeed.toDouble())
        curPlaybackSpeed = curPBSpeed
        if (isPlayingVideoLocally && curEpisode?.feed?.videoModePolicy != VideoMode.AUDIO_ONLY) isBSExpanded = false
        prevItem = curItem
    }
    internal fun updateDetails() {
        lcScope.launch {
            Logd(TAG, "in updateDetails")
            withContext(Dispatchers.IO) {
//                if (curEpisode != null && curEpisode?.id != curItem?.id) setItem(curEpisode!!)
                if (curItem != null) {
                    showHomeText = false
                    homeText = null
                    rating = curItem!!.rating
                    Logd(TAG, "updateDetails rating: $rating curItem!!.rating: ${curItem!!.rating}")
                    Logd(TAG, "updateDetails updateInfo ${cleanedNotes == null} ${prevItem?.identifyingValue} ${curItem!!.identifyingValue}")
                    if (cleanedNotes == null) {
                        val result = gearbox.buildCleanedNotes(curItem!!, shownotesCleaner)
//                        setItem(result.first)
                        cleanedNotes = result.second
                    }
                    prevItem = curItem
                }
                Logd(TAG, "updateDetails cleanedNotes: ${cleanedNotes?.length}")
            }
            withContext(Dispatchers.Main) {
                Logd(TAG, "subscribe: ${curItem?.getEpisodeTitle()}")
                if (curItem != null) {
                    val media = curItem!!
                    Logd(TAG, "displayMediaInfo ${curItem?.title} ${media.getEpisodeTitle()}")
                    val pubDateStr = MiscFormatter.formatDateTimeFlex(media.getPubDate())
                    txtvPodcastTitle = (media.feed?.title?:"").trim()
                    episodeDate = pubDateStr.trim()
                    titleText = curItem?.title ?:""
                    displayedChapterIndex = -1
                    refreshChapterData(media.getCurrentChapterIndex(media.position))
                }
                Logd(TAG, "Webview loaded")
            }
        }.invokeOnCompletion { throwable -> if (throwable != null) Logs(TAG, throwable) }
    }

    private fun refreshChapterData(chapterIndex: Int) {
        Logd(TAG, "in refreshChapterData $chapterIndex")
        if (curItem != null && chapterIndex > -1) {
            if (curItem!!.position > curItem!!.duration || chapterIndex >= (curItem?.chapters?: listOf()).size - 1) {
                displayedChapterIndex = curItem!!.chapters.size - 1
                hasNextChapter = false
            } else {
                displayedChapterIndex = chapterIndex
                hasNextChapter = true
            }
        }
        if (curItem != null) {
            imgLocLarge =
                if (displayedChapterIndex == -1 || curItem?.chapters.isNullOrEmpty() || curItem!!.chapters[displayedChapterIndex].imageUrl.isNullOrEmpty()) curItem!!.imageLocation
                else EmbeddedChapterImage.getModelFor(curItem!!, displayedChapterIndex)?.toString()
            Logd(TAG, "displayCoverImage: imgLoc: $imgLoc")
        }
    }

    internal fun seekToPrevChapter() {
        val curr: Chapter? = curChapter
        if (curr == null || displayedChapterIndex == -1) return
        when {
            displayedChapterIndex < 1 -> mPlayer?.seekTo(0)
            (curPositionFB - 10000 * curPBSpeed) < curr.start -> {
                refreshChapterData(displayedChapterIndex - 1)
                if (!curItem?.chapters.isNullOrEmpty()) mPlayer?.seekTo(curItem!!.chapters[displayedChapterIndex].start.toInt())
            }
            else -> mPlayer?.seekTo(curr.start.toInt())
        }
    }
    internal fun seekToNextChapter() {
        if (curItem?.chapters.isNullOrEmpty() || displayedChapterIndex == -1 || displayedChapterIndex + 1 >= curItem!!.chapters.size) return
        refreshChapterData(displayedChapterIndex + 1)
        mPlayer?.seekTo(curItem!!.chapters[displayedChapterIndex].start.toInt())
    }
    private var loadItemsRunning = false
    internal fun loadMediaInfo() {
        Logd(TAG, "loadMediaInfo() curMedia: ${curEpisode?.id}")
        if (actMain == null) return
        if (curEpisode == null) return
        if (!loadItemsRunning) {
            loadItemsRunning = true
            val curMediaChanged = curItem == null || curEpisode?.id != curItem?.id
            if (curEpisode != null && curEpisode?.id != curItem?.id) {
                setItem(curEpisode!!)
                updateUi()
            }
            if (isBSExpanded && curMediaChanged) {
                Logd(TAG, "loadMediaInfo loading details ${curEpisode?.id}")
                lcScope.launch {
                    withContext(Dispatchers.IO) { curEpisode?.apply { this.loadChapters(context, false) } }
                    val chapters: List<Chapter> = curItem?.chapters ?: listOf()
                    if (chapters.isNotEmpty()) {
                        val dividerPos = FloatArray(chapters.size)
                        for (i in chapters.indices) dividerPos[i] = chapters[i].start / curDurationFB.toFloat()
                    }
                    sleepTimerActive = isSleepTimerActive()
                }.invokeOnCompletion { throwable ->
                    if (throwable != null) Logs(TAG, throwable)
                    loadItemsRunning = false
                }
            }
        }
    }
    internal fun setItem(item_: Episode) {
        Logd(TAG, "setItem ${item_.title}")
        if (curItem?.identifyingValue != item_.identifyingValue) {
            curItem = item_
            rating = curItem!!.rating
            showHomeText = false
            homeText = null
            cleanedNotes = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioPlayerScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { AudioPlayerVM(context, scope) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    isBSExpanded = false
                    vm.procFlowEvents()
                    if (vm.shownotesCleaner == null) vm.shownotesCleaner = ShownotesCleaner(context)
                    if (curEpisode != null) vm.updateUi()
                }
                Lifecycle.Event.ON_START -> {
                    onCurChangedUICB = { e, f -> vm.onEpisodeChanged(e, f) }
                    onCurInitUICB = { e -> vm.onEpisodeInit(e) }
                    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    if (aCtrlFuture == null) {
                        aCtrlFuture = MediaController.Builder(context, sessionToken).buildAsync()
                        aCtrlFuture?.addListener({ aController = aCtrlFuture!!.get() }, MoreExecutors.directExecutor())
                    }
                }
                Lifecycle.Event.ON_RESUME -> vm.loadMediaInfo()
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.cancelFlowEvents()
            vm.playerLocal?.release()
            vm.playerLocal = null
            onCurChangedUICB = null
            onCurInitUICB = null
            if (aCtrlFuture != null) MediaController.releaseFuture(aCtrlFuture!!)
            aCtrlFuture =  null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isBSExpanded) {
        if (isBSExpanded) {
            if (vm.shownotesCleaner == null) vm.shownotesCleaner = ShownotesCleaner(context)
            if (vm.playerLocal == null) vm.playerLocal = ExoPlayer.Builder(context).build()
        }
        Logd(TAG, "isExpanded: $isBSExpanded")
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ControlUI() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val context = LocalContext.current

        LaunchedEffect(key1 = playerStat) { vm.showPlayButton = playerStat != PLAYER_STATUS_PLAYING }

        @Composable
        fun SpeedometerWithArc(speed: Float, maxSpeed: Float, trackColor: Color, modifier: Modifier) {
            val needleAngle = (speed / maxSpeed) * 270f - 225
            Canvas(modifier = modifier) {
                val radius = 1.3 * size.minDimension / 2
                val strokeWidth = 6.dp.toPx()
                val arcRect = Rect(left = strokeWidth / 2, top = strokeWidth / 2, right = size.width - strokeWidth / 2, bottom = size.height - strokeWidth / 2)
                drawArc(color = trackColor, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = Stroke(width = strokeWidth), topLeft = arcRect.topLeft, size = arcRect.size)
                val needleAngleRad = Math.toRadians(needleAngle.toDouble())
                val needleEnd = Offset(x = size.center.x + (radius * 0.7f * cos(needleAngleRad)).toFloat(), y = size.center.y + (radius * 0.7f * sin(needleAngleRad)).toFloat())
                drawLine(color = Color.Red, start = size.center, end = needleEnd, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color = Color.Cyan, center = size.center, radius = 3.dp.toPx())
            }
        }

        Row {
            fun ensureService() {
                if (curEpisode == null) return
                if (playbackService == null) PlaybackStarter(context, curEpisode!!).start()
            }
            AsyncImage(contentDescription = "imgvCover", model = ImageRequest.Builder(context).data(vm.imgLoc).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                modifier = Modifier.width(50.dp).height(50.dp).padding(start = 5.dp)
                    .clickable(onClick = {
                        Logd(TAG, "playerUi icon was clicked")
                        if (!isBSExpanded) {
                            val media = curEpisode
                            if (media != null) {
                                val mediaType = media.getMediaType()
                                if (mediaType == MediaType.AUDIO || videoPlayMode == VideoMode.AUDIO_ONLY.code || videoMode == VideoMode.AUDIO_ONLY || (media.feed?.videoModePolicy == VideoMode.AUDIO_ONLY)) {
                                    Logd(TAG, "popping as audio episode")
                                    ensureService()
                                    isBSExpanded = true
                                } else {
                                    Logd(TAG, "popping video context")
                                    val intent = getPlayerActivityIntent(context, mediaType)
                                    context.startActivity(intent)
                                }
                            }
                        } else isBSExpanded = false
                    }))
            val buttonSize = 46.dp
            Spacer(Modifier.weight(0.1f))
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).clickable(onClick = { vm.showSpeedDialog = true })) {
                SpeedometerWithArc(speed = vm.curPlaybackSpeed*100, maxSpeed = 300f, trackColor = textColor, modifier = Modifier.width(40.dp).height(40.dp).align(Alignment.TopCenter))
                Text(vm.txtvPlaybackSpeed, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.weight(0.1f))
            val recordColor = if (vm.recordingStartTime == null) { if (curEpisode != null && exoPlayer != null && status == PlayerStatus.PLAYING) textColor else Color.Gray } else Color.Red
            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_fiber_manual_record_24), tint = recordColor, contentDescription = "record",
                modifier = Modifier.size(buttonSize).combinedClickable(
                    onClick = {
                        if (curEpisode != null && exoPlayer != null && status == PlayerStatus.PLAYING) {
                            if (vm.recordingStartTime == null) {
                                vm.recordingStartTime = exoPlayer!!.currentPosition
                                saveClipInOriginalFormat(vm.recordingStartTime!!)
                            }
                            else {
                                saveClipInOriginalFormat(vm.recordingStartTime!!, exoPlayer!!.currentPosition)
                                vm.recordingStartTime = null
                            }
                        } else Loge(TAG, "Recording only works during playback.") },
                    onLongClick = {
                        if (curEpisode != null && exoPlayer != null && status == PlayerStatus.PLAYING) {
                            val pos = exoPlayer!!.currentPosition
                            runOnIOScope { upsert(curEpisode!!) { it.marks.add(pos) } }
                            Logt(TAG, "position $pos marked for ${curEpisode?.title}")
                        } else Loge(TAG, "Marking position only works during playback.")
                    }))
            Spacer(Modifier.weight(0.1f))
            var showSkipDialog by remember { mutableStateOf(false) }
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
                onClick = { mPlayer?.seekDelta(-AppPreferences.rewindSecs * 1000) },
                onLongClick = { showSkipDialog = true })) {
                var rewindSecs by remember { mutableStateOf(NumberFormat.getInstance().format(AppPreferences.rewindSecs.toLong())) }
                if (showSkipDialog) SkipDialog(SkipDirection.SKIP_REWIND, onDismissRequest = { showSkipDialog = false }) { rewindSecs = it.toString() }
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_fast_rewind), tint = textColor, contentDescription = "rewind", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
                Text(rewindSecs, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.weight(0.1f))
            fun toggleFallbackSpeed(speed: Float) {
                if (mPlayer == null || isSpeedForward) return
                if (status == PlayerStatus.PLAYING) {
                    val player = mPlayer!!
                    if (!isFallbackSpeed) {
                        normalSpeed = player.getPlaybackSpeed()
                        player.setPlaybackParams(speed, isSkipSilence)
                    } else player.setPlaybackParams(normalSpeed, isSkipSilence)
                    isFallbackSpeed = !isFallbackSpeed
                }
            }
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
                onClick = {
                    if (curEpisode != null) {
                        val media = curEpisode!!
                        vm.showPlayButton = !vm.showPlayButton
                        if (vm.showPlayButton && vm.recordingStartTime != null) {
                            saveClipInOriginalFormat(vm.recordingStartTime!!, exoPlayer!!.currentPosition)
                            vm.recordingStartTime = null
                        }
                        if (media.getMediaType() == MediaType.VIDEO && status != PlayerStatus.PLAYING && (media.feed?.videoModePolicy != VideoMode.AUDIO_ONLY)) {
                            playPause()
                            context.startActivity(getPlayerActivityIntent(context, curEpisode!!.getMediaType()))
                        } else {
                            Logd(TAG, "Play button clicked: status: $status is ready: ${playbackService?.isServiceReady()}")
                            PlaybackStarter(context, media).shouldStreamThisTime(null).start()
                        }
                    }
                },
                onLongClick = {
                    if (status == PlayerStatus.PLAYING) {
                        val speedFB = fallbackSpeed
                        if (speedFB > 0.1f) toggleFallbackSpeed(speedFB)
                    } })) {
                Icon(imageVector = ImageVector.vectorResource(vm.playButRes), tint = textColor, contentDescription = "play", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
                if (fallbackSpeed > 0.1f) Text(fallbackSpeed.toString(), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.weight(0.1f))
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
                onClick = { mPlayer?.seekDelta(AppPreferences.fastForwardSecs * 1000) },
                onLongClick = { showSkipDialog = true })) {
                var showSkipDialog by remember { mutableStateOf(false) }
                var fastForwardSecs by remember { mutableStateOf(NumberFormat.getInstance().format(AppPreferences.fastForwardSecs.toLong())) }
                if (showSkipDialog) SkipDialog(SkipDirection.SKIP_FORWARD, onDismissRequest = {showSkipDialog = false }) { fastForwardSecs = it.toString()}
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_fast_forward), tint = textColor, contentDescription = "forward", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
                Text(fastForwardSecs, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.weight(0.1f))
            fun speedForward(speed: Float) {
                if (mPlayer == null || isFallbackSpeed == true) return
                if (isSpeedForward == false) {
                    normalSpeed = mPlayer!!.getPlaybackSpeed()
                    mPlayer!!.setPlaybackParams(speed, isSkipSilence)
                } else mPlayer?.setPlaybackParams(normalSpeed, isSkipSilence)
                isSpeedForward = !isSpeedForward
            }
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).combinedClickable(
                onClick = {
                    if (status == PlayerStatus.PLAYING) {
                        val speedForward = AppPreferences.speedforwardSpeed
                        if (speedForward > 0.1f) speedForward(speedForward)
                    } },
                onLongClick = {
//                    context.sendBroadcast(MediaButtonReceiver.createIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
                    if (status in listOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED)) mPlayer?.skip()
                })) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_skip_48dp), tint = textColor, contentDescription = "rewind", modifier = Modifier.size(buttonSize).align(Alignment.TopCenter))
                if (AppPreferences.speedforwardSpeed > 0.1f) Text(NumberFormat.getInstance().format(AppPreferences.speedforwardSpeed), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.weight(0.1f))
        }
    }

    @Composable
    fun ProgressBar() {
        val textColor = MaterialTheme.colorScheme.onSurface
        Box(modifier = Modifier.fillMaxWidth()) {
            Slider(value = vm.sliderValue, valueRange = 0f..vm.duration.toFloat(),
                colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.height(12.dp).padding(top = 2.dp, bottom = 2.dp),
                onValueChange = { vm.sliderValue = it }, onValueChangeFinished = {
                    vm.curPosition = vm.sliderValue.toInt()
                    mPlayer?.seekTo(vm.curPosition)
                })
            if (vm.bufferValue > 0f) LinearProgressIndicator(progress = { vm.bufferValue }, color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.height(8.dp).fillMaxWidth().align(Alignment.BottomStart))
        }
        Row {
            Text(DurationConverter.getDurationStringLong(vm.curPosition), color = textColor, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            if (bitrate > 0) Text(formatLargeInteger(bitrate) + "bits", color = textColor, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            vm.showTimeLeft = getPref(AppPrefs.showTimeLeft, false)
            Text(vm.txtvLengtTexth, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable {
                vm.showTimeLeft = !vm.showTimeLeft
                putPref(AppPrefs.showTimeLeft, vm.showTimeLeft)
            })
        }
    }

    @Composable
    fun PlayerUI(modifier: Modifier) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Box(modifier = modifier.fillMaxWidth().height(100.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)).background(MaterialTheme.colorScheme.surface)) {
            Column {
                Text(vm.titleText, maxLines = 1, color = textColor, style = MaterialTheme.typography.bodyMedium)
                ProgressBar()
                ControlUI()
            }
        }
    }

    @Composable
    fun VolumeAdaptionDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(vm.curItem?.volumeAdaptionSetting ?: VolumeAdaptionSetting.OFF) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeAdaptionSetting.entries.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = (item == selectedOption),
                                onCheckedChange = { _ ->
                                    Logd(TAG, "row clicked: $item $selectedOption")
                                    if (item != selectedOption) {
                                        onOptionSelected(item)
                                        if (vm.curItem != null) {
                                            vm.curItem!!.volumeAdaptionSetting = item
                                            curEpisode!!.volumeAdaptionSetting = item
                                            mPlayer?.setVolume(1.0f, 1.0f)
                                        }
                                        onDismissRequest()
                                    }
                                }
                            )
                            Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }

    var showSleepTimeDialog by remember { mutableStateOf(false) }
    if (showSleepTimeDialog) SleepTimerDialog { showSleepTimeDialog = false }

    @Composable
    fun Toolbar() {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        val feedItem: Episode = curEpisode ?: return
        val textColor = MaterialTheme.colorScheme.onSurface
        val mediaType = curEpisode?.getMediaType()
        val notAudioOnly = curEpisode?.feed?.videoModePolicy != VideoMode.AUDIO_ONLY
        var showVolumeDialog by remember { mutableStateOf(false) }
        if (showVolumeDialog) VolumeAdaptionDialog { showVolumeDialog = false }
        var showShareDialog by remember { mutableStateOf(false) }
        if (showShareDialog && vm.curItem != null && vm.actMain != null) ShareDialog(vm.curItem!!, vm.actMain) {showShareDialog = false }
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down), tint = textColor, contentDescription = "Collapse", modifier = Modifier.clickable { isBSExpanded = false })
            if (vm.curItem != null) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_feed), tint = textColor, contentDescription = "Open podcast",
                modifier = Modifier.clickable {
                    if (feedItem.feed != null) {
                        feedOnDisplay = feedItem.feed!!
                        feedScreenMode = FeedScreenMode.List
                        mainNavController.navigate(Screens.FeedDetails.name)
                        isBSExpanded = false
                    }
                })
            var homeIcon by remember { mutableIntStateOf(R.drawable.outline_home_24)}
            Icon(imageVector = ImageVector.vectorResource(homeIcon), tint = textColor, contentDescription = "Home", modifier = Modifier.clickable {
                if (vm.curItem != null) {
                    episodeOnDisplay = vm.curItem!!
                    mainNavController.navigate(Screens.EpisodeInfo.name)
                    isBSExpanded = false
                }
            })
            if (mediaType == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_fullscreen_24), tint = textColor, contentDescription = "Play video",
                modifier = Modifier.clickable {
                    if (!notAudioOnly && curEpisode?.forceVideo != true) {
                        curEpisode?.forceVideo = true
                        status = PlayerStatus.STOPPED
                        mPlayer?.pause(reinit = true)
                        playbackService?.recreateMediaPlayer()
                    }
                    VideoPlayerActivityStarter(context).start()
                })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_volume_adaption), tint = textColor, contentDescription = "Volume adaptation", modifier = Modifier.clickable { if (vm.curItem != null) showVolumeDialog = true })
            val sleepRes = if (vm.sleepTimerActive) R.drawable.ic_sleep_off else R.drawable.ic_sleep
            Icon(imageVector = ImageVector.vectorResource(sleepRes), tint = textColor, contentDescription = "Sleep timer", modifier = Modifier.clickable { showSleepTimeDialog = true })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_share), tint = textColor, contentDescription = "Share", modifier = Modifier.clickable { if (vm.curItem != null) showShareDialog = true })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_offline_share_24), tint = textColor, contentDescription = "Share Note", modifier = Modifier.clickable {
                val notes = if (vm.showHomeText) vm.readerhtml else feedItem.description
                if (!notes.isNullOrEmpty()) {
                    val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                    val context = context
                    val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                    context.startActivity(intent)
                }
            })
            (context as? BaseActivity)?.CastIconButton()
            IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.clear_cache)) }, onClick = {
                    runOnIOScope {
                        if (vm.curItem != null) getCache(context).removeResource(vm.curItem!!.id.toString())
//                        val keys = cache.keys
//                        keys.forEach {
//                            Logd(TAG, "removing cache resource on key: $it")
//                            cache.removeResource(it)
//                        }
                    }
                    expanded = false
                })
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun DetailUI(modifier: Modifier) {
        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(vm.curItem!!)) { showChooseRatingDialog = false }
        var showChaptersDialog by remember { mutableStateOf(false) }
        if (showChaptersDialog) ChaptersDialog(media = vm.curItem!!, onDismissRequest = {showChaptersDialog = false})

        val scrollState = rememberScrollState()
        Column(modifier = modifier.fillMaxWidth().verticalScroll(scrollState)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            gearbox.PlayerDetailedGearPanel(vm)
            SelectionContainer { Text(vm.txtvPodcastTitle, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)) }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
                Spacer(modifier = Modifier.weight(0.2f))
                val ratingIconRes by derivedStateOf { Rating.fromCode(vm.rating).res }
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = { showChooseRatingDialog = true }))
                Spacer(modifier = Modifier.weight(0.4f))
                Text(vm.episodeDate, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(0.6f))
            }
            SelectionContainer { Text(vm.titleText, textAlign = TextAlign.Center, color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)) }

//            fun restoreFromPreference(): Boolean {
//                if ((context as MainActivity).bottomSheet.state != BottomSheetBehavior.STATE_EXPANDED) return false
//                Logd(TAG, "Restoring from preferences")
//                val context: Activity? = context
//                if (context != null) {
//                    val id = prefs!!.getString(PREF_PLAYABLE_ID, "")
//                    val scrollY = prefs!!.getInt(PREF_SCROLL_Y, -1)
//                    if (scrollY != -1) {
//                        if (id == curMedia?.id?.toString()) {
//                            Logd(TAG, "Restored scroll Position: $scrollY")
////                            binding.itemDescriptionFragment.scrollTo(binding.itemDescriptionFragment.scrollX, scrollY)
//                            return true
//                        }
//                        Logd(TAG, "reset scroll Position: 0")
////                        binding.itemDescriptionFragment.scrollTo(0, 0)
//                        return true
//                    }
//                }
//                return false
//            }
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                ShownotesWebView(context).apply {
                    setTimecodeSelectedListener { time: Int -> mPlayer?.seekTo(time) }
                    // Restoring the scroll position might not always work
                    setPageFinishedListener {
//                        postDelayed({ restoreFromPreference() }, 50)
                        postDelayed({ }, 50)
                    }
                }
            }, update = { webView -> webView.loadDataWithBaseURL("https://127.0.0.1", if (vm.cleanedNotes.isNullOrBlank()) "No notes" else vm.cleanedNotes!!, "text/html", "utf-8", "about:blank") })
            EpisodeMarks(vm.curItem)
            EpisodeClips(vm.curItem, vm.playerLocal)

            if (vm.displayedChapterIndex >= 0) {
                Row(modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_chapter_prev), tint = textColor, contentDescription = "prev_chapter", modifier = Modifier.width(36.dp).height(36.dp).clickable(onClick = { vm.seekToPrevChapter() }))
                    Text("Ch " + vm.displayedChapterIndex.toString() + ": " + vm.curChapter?.title, color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp, end = 10.dp).clickable(onClick = { showChaptersDialog = true }))
                    if (vm.hasNextChapter) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_chapter_next), tint = textColor, contentDescription = "next_chapter", modifier = Modifier.width(36.dp).height(36.dp).clickable(onClick = { vm.seekToNextChapter() }))
                }
            }
            AsyncImage(model = vm.imgLocLarge, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher), modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 10.dp).clickable(onClick = {}))
        }
    }

    if (vm.showSpeedDialog) PlaybackSpeedFullDialog(settingCode = booleanArrayOf(true, true, true), indexDefault = 0, maxSpeed = 3f, onDismiss = {vm.showSpeedDialog = false})
    LaunchedEffect(key1 = curMediaId) {
        vm.cleanedNotes = null
        if (curEpisode != null) {
            vm.updateUi()
            vm.imgLoc = curEpisode!!.imageLocation
            if (vm.curItem?.id != curEpisode?.id) vm.setItem(curEpisode!!)
        }
    }
    Box(modifier = Modifier.fillMaxWidth().then(if (!isBSExpanded) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())) {
        PlayerUI(Modifier.align(if (!isBSExpanded) Alignment.TopCenter else Alignment.BottomCenter).zIndex(1f))
        if (isBSExpanded) {
            if (vm.cleanedNotes == null) vm.updateDetails()
            Column(Modifier.padding(bottom = 120.dp)) {
                Toolbar()
                DetailUI(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private const val TAG = "AudioPlayerScreen"

private val curPositionFB: Int
    get() = mPlayer?.getPosition() ?: curEpisode?.position ?: Episode.INVALID_TIME
