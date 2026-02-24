package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.PodciniApp.Companion.getApp
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowEpisodeDownload
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.playerStat
import ac.mdiq.podcini.storage.database.addRemoteToMiscSyndicate
import ac.mdiq.podcini.storage.database.addToAssQueue
import ac.mdiq.podcini.storage.database.addToQueue
import ac.mdiq.podcini.storage.database.deleteEpisodesWarnLocalRepeat
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.smartRemoveFromAllQueues
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PLAYING
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.durationStringFull
import ac.mdiq.podcini.ui.actions.ActionButton
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.EpisodeAction
import ac.mdiq.podcini.ui.actions.NoAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.downloadStates
import ac.mdiq.podcini.ui.screens.FeedScreenMode
import ac.mdiq.podcini.ui.screens.LocalNavController
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.ui.screens.handleBackSubScreens
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.formatDateTimeFlex
import ac.mdiq.podcini.utils.formatLargeInteger
import ac.mdiq.podcini.utils.stripDateTimeLines
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.MalformedURLException
import java.net.URL
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "EpisodesVM"

var showSwipeActionsDialog by mutableStateOf(false)

@Composable
fun InforBar(swipeActions: SwipeActions, content: @Composable (RowScope.()->Unit)) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val leftAction = swipeActions.left
    val rightAction = swipeActions.right
//    Logd("InforBar", "textState: ${text.value}")
    Row {
        Icon(imageVector = ImageVector.vectorResource(leftAction.iconRes), tint = buttonColor, contentDescription = "left_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {  showSwipeActionsDialog = true }))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "left_arrow", modifier = Modifier.width(24.dp).height(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        content()
        Spacer(modifier = Modifier.weight(1f))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(24.dp).height(24.dp))
        Icon(imageVector = ImageVector.vectorResource(rightAction.iconRes), tint = buttonColor, contentDescription = "right_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {  showSwipeActionsDialog = true }))
    }
}

enum class LayoutMode {
    Normal, WideImage, FeedTitle
}

enum class StatusRowMode {
    Normal, Comment, Tags, Todos
}

@Composable
fun EpisodeLazyColumn(episodes: List<Episode>, feed: Feed? = null, isExternal: Boolean = false,
                      lazyListState: LazyListState = rememberLazyListState(), scrollToOnStart: Int = -1,
                      layoutMode: Int = LayoutMode.Normal.ordinal,
                      showCoverImage: Boolean = true, forceFeedImage: Boolean = false,
                      statusRowMode: StatusRowMode = StatusRowMode.Normal,
                      swipeActions: SwipeActions? = null,
                      refreshCB: (()->Unit)? = null, selectModeCB: ((Boolean)->Unit)? = null,
                      showActionButtons: Boolean = true,
                      actionButtonType: ButtonTypes? = null, actionButtonCB: ((Episode, ButtonTypes)->Unit)? = null) {

    var selectMode by remember { mutableStateOf(false) }
    var selectedSize by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<Episode>() }
    val scope = rememberCoroutineScope()
    var longPressIndex by remember { mutableIntStateOf(-1) }
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val localTime = remember { System.currentTimeMillis() }

//    val currentEntry = navController.navController.currentBackStackEntryAsState().value

    fun multiSelectCB(index: Int, aboveOrBelow: Int): List<Episode> {
        return when (aboveOrBelow) {
            0 -> episodes
            -1 -> if (index < episodes.size) episodes.subList(0, index+1) else episodes
            1 -> if (index < episodes.size) episodes.subList(index, episodes.size) else episodes
            else -> listOf()
        }
    }

    var leftSwipeCB: ((Episode)->Unit)? = null
    var rightSwipeCB: ((Episode)->Unit)? = null
    val leftActionState = remember { mutableStateOf<EpisodeAction>(NoAction()) }
    val rightActionState = remember { mutableStateOf<EpisodeAction>(NoAction()) }
    if (swipeActions != null) {
        leftActionState.value = swipeActions.left
        rightActionState.value = swipeActions.right
        leftSwipeCB = {
            if (leftActionState.value is NoAction) showSwipeActionsDialog = true
            else leftActionState.value.performAction(it)
        }
        rightSwipeCB = {
            if (rightActionState.value is NoAction) showSwipeActionsDialog = true
            else rightActionState.value.performAction(it)
        }
        if (showSwipeActionsDialog) swipeActions.SwipeActionsSettingDialog(onDismissRequest = { showSwipeActionsDialog = false })
    }

    var showChooseRatingDialog by remember { mutableStateOf(false) }
    var showAddCommentDialog by remember { mutableStateOf(false) }
    var showEditTagsDialog by remember { mutableStateOf(false) }
    var showIgnoreDialog by remember { mutableStateOf(false) }
    var futureState by remember { mutableStateOf(EpisodeState.UNSPECIFIED) }
    var showPlayStateDialog by remember { mutableStateOf(false) }
    var showPutToQueueDialog by remember { mutableStateOf(false) }
    var showShelveDialog by remember { mutableStateOf(false) }
    var showMulticastDialog by remember { mutableStateOf(false) }
    var showEraseDialog by remember { mutableStateOf(false) }
    val ytUrls = remember { mutableListOf<String>() }

    @Composable
    fun OpenDialogs() {
        if (ytUrls.isNotEmpty()) gearbox.ConfirmAddEpisode(ytUrls, onDismissRequest = { ytUrls.clear() })
        if (showChooseRatingDialog) ChooseRatingDialog(selected) { showChooseRatingDialog = false }
        if (showAddCommentDialog) {
            var editCommentText by remember { mutableStateOf(TextFieldValue("") ) }
            CommentEditingDialog(textState = editCommentText, autoSave = false, onTextChange = { editCommentText = it }, onDismissRequest = { showAddCommentDialog = false },
                onSave = { runOnIOScope { for (e in selected) upsert(e) { it.addComment(editCommentText.text) } } })
        }
        if (showEditTagsDialog) TagSettingDialog(TagType.Episode, setOf(), multiples = true, onDismiss = { showEditTagsDialog = false }) { tags ->
            runOnIOScope { for (e in selected) upsert(e) { it.tags.addAll(tags) }  }
        }
        if (showPlayStateDialog) PlayStateDialog(selected, onDismissRequest = { showPlayStateDialog = false }, { futureState = it }, { showIgnoreDialog = true })
        if (showPutToQueueDialog) PutToQueueDialog(selected) { showPutToQueueDialog = false }
        if (showShelveDialog) ShelveDialog(selected) { showShelveDialog = false }
        if (showMulticastDialog) MulticastDialog(selected, { showMulticastDialog = false })

        if (showEraseDialog && feed != null) EraseEpisodesDialog(selected, feed, onDismissRequest = { showEraseDialog = false })
        if (showIgnoreDialog) IgnoreEpisodesDialog(selected, onDismissRequest = { showIgnoreDialog = false })
        if (futureState in listOf(EpisodeState.AGAIN, EpisodeState.FOREVER, EpisodeState.LATER)) FutureStateDialog(selected, futureState, onDismissRequest = { futureState = EpisodeState.UNSPECIFIED })
    }

    OpenDialogs()

    DisposableEffect(episodeForInfo) {
        if (episodeForInfo != null) handleBackSubScreens.add(TAG)
        else handleBackSubScreens.remove(TAG)
        onDispose { handleBackSubScreens.remove(TAG) }
    }
    BackHandler(enabled = handleBackSubScreens.contains(TAG)) {
        Logd(TAG, "BackHandler")
        episodeForInfo = null
    }

    var refreshing by remember { mutableStateOf(false)}
    PullToRefreshBox(modifier = Modifier.fillMaxSize(), isRefreshing = refreshing, indicator = {}, onRefresh = {
        refreshing = true
        refreshCB?.invoke()
        refreshing = false
    }) {
        fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
            if (fromIndex != toIndex && fromIndex in indices && toIndex in indices) {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
        }
//        val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                Logd(TAG, "LifecycleEventObserver: $event")
                when (event) {
                    Lifecycle.Event.ON_START -> {}
                    Lifecycle.Event.ON_STOP -> {}
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                //                Logd(TAG, "DisposableEffect lifecycleOwner onDispose")
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(episodes.size, scrollToOnStart) {
            val lifecycleState = lifecycleOwner.lifecycle.currentState
            Logd(TAG, "LaunchedEffect(scrollToOnStart) ${episodes.size} $scrollToOnStart ${lazyListState.firstVisibleItemIndex} $lifecycleState")
            if (episodes.size > 5 && lifecycleState >= Lifecycle.State.RESUMED) {
                when {
                    scrollToOnStart < 0 -> {
//                        scope.launch { lazyListState.scrollToItem(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) }
                        Logd(TAG, "Screen on, triggered scroll for recomposition")
                    }
                    scrollToOnStart >= 0 -> {
                        scope.launch { if (scrollToOnStart >= 0) lazyListState.scrollToItem(scrollToOnStart) }
                        Logd(TAG, "on start, triggered scroll for recomposition: $scrollToOnStart")
                    }
                }
            }
        }

        val swipeVelocityThreshold = 1500f
        val swipeDistanceThreshold = with(LocalDensity.current) { 100.dp.toPx() }
        val useFeedImage = remember(feed?.useEpisodeImage) { feed?.useFeedImage() == true }

        val titleMaxLines = if (layoutMode == LayoutMode.Normal.ordinal) { if (statusRowMode == StatusRowMode.Comment) 1 else 2 } else 3
//        val density = LocalDensity.current
        val imageWidth = if (layoutMode == LayoutMode.WideImage.ordinal) 150.dp else 56.dp
        val imageHeight = if (layoutMode == LayoutMode.WideImage.ordinal) 100.dp else 56.dp

        Logd(TAG, "outside of LazyColumn")
        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize().padding(start = 5.dp, end = 5.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = episodes, key = { it.id }) { episode_ ->
                val episode by rememberUpdatedState(episode_)
                var actionButton by remember(episode.id) { mutableStateOf(if (actionButtonType != null) ActionButton(episode, actionButtonType) else ActionButton(episode)) }
                var showAltActionsDialog by remember(episode.id) { mutableStateOf(false) }
                var isSelected by remember(episode.id, selectMode, selectedSize) { mutableStateOf( selectMode && episode in selected ) }

                fun toggleSelected(e: Episode) {
                    isSelected = !isSelected
                    if (isSelected) selected.add(e)
                    else selected.remove(e)
                }

                val velocityTracker = remember { VelocityTracker() }
                val offsetX = remember(episode.id) { Animatable(0f) }

                Box(modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
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
                                    if (distance > 0) rightSwipeCB?.invoke(episode)
                                    else leftSwipeCB?.invoke(episode)
                                }
                                offsetX.animateTo(targetValue = 0f, animationSpec = tween(300))
                            }
                        },
                    )
                }.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
                    Column {
                        @Composable
                        fun TitleColumn(modifier: Modifier) {
                            Column(modifier.padding(start = 6.dp, end = 6.dp).combinedClickable(
                                onClick = {
                                    Logd(TAG, "clicked: ${episode.title}")
                                    if (selectMode) toggleSelected(episode)
//                                    else navController.navigate("${Screens.EpisodeInfo.name}?episodeId=${episode.id}")
                                    else episodeForInfo = episode
                                },
                                onLongClick = {
                                    selectMode = !selectMode
                                    selectModeCB?.invoke(selectMode)
                                    isSelected = selectMode
                                    selected.clear()
                                    if (selectMode) {
                                        selected.add(episode)
                                        val index = episodes.indexOfFirst { it.id == episode.id }
                                        longPressIndex = index
                                    } else {
                                        selectedSize = 0
                                        longPressIndex = -1
                                    }
                                    Logd(TAG, "long clicked: ${episode.title}")
                                })) {
                                Text(episode.title ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
                                @Composable
                                fun Comment() {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val playState = remember(episode.playState) { EpisodeState.fromCode(episode.playState) }
                                        Icon(imageVector = ImageVector.vectorResource(playState.res), tint = playState.color ?: MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (episode.playState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                                        if (episode.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(episode.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                                        val comment = remember(episode.comment) { stripDateTimeLines(episode.comment).replace("\n", "  ") }
                                        Spacer(Modifier.width(10.dp))
                                        Text(comment, color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                @Composable
                                fun Tags() {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val playState = remember(episode.playState) { EpisodeState.fromCode(episode.playState) }
                                        Icon(imageVector = ImageVector.vectorResource(playState.res), tint = playState.color ?: MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (episode.playState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                                        if (episode.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(episode.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                                        val tags = remember(episode.tags.size) { episode.tags.joinToString(",") }
                                        Spacer(Modifier.width(10.dp))
                                        Text(tags, color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                @Composable
                                fun Todos() {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val playState = remember(episode.playState) { EpisodeState.fromCode(episode.playState) }
                                        Icon(imageVector = ImageVector.vectorResource(playState.res), tint = playState.color ?: MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (episode.playState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                                        if (episode.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(episode.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                                        val todos = remember(episode.todos.size) { episode.todos.filter { !it.completed }.joinToString(" | ") { it.title + if (it.dueTime > 0) ("D:" + formatDateTimeFlex(Date(it.dueTime))) else "" } }
                                        Spacer(Modifier.width(10.dp))
                                        Text(todos, color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                @Composable
                                fun StatusRow() {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val playState = remember(episode.playState) { EpisodeState.fromCode(episode.playState) }
                                        Icon(imageVector = ImageVector.vectorResource(playState.res), tint = playState.color ?: MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (episode.playState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                                        if (episode.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(episode.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                                        if (episode.comment.isNotBlank()) Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_comment_24), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "comment", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                                        if (episode.getMediaType() == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                                        val dateSizeText = remember(episode.id) {
                                            " · " + formatDateTimeFlex(Date(episode.pubDate)) + " · " + durationStringFull(episode.duration) +
                                                    (if (episode.size > 0) " · " + Formatter.formatShortFileSize(context, episode.size) else "") +
                                                    (if (episode.viewCount > 0) " · " + formatLargeInteger(episode.viewCount) else "") +
                                                    (if (episode.likeCount > 0) " · " + formatLargeInteger(episode.likeCount) else "")
                                        }
                                        Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                when (layoutMode) {
                                    LayoutMode.Normal.ordinal -> {
                                        when (statusRowMode) {
                                            StatusRowMode.Comment -> Comment()
                                            StatusRowMode.Tags -> Tags()
                                            StatusRowMode.Todos -> Todos()
                                            else -> StatusRow()
                                        }
                                    }
                                    LayoutMode.WideImage.ordinal -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val playState = remember(episode.playState) { EpisodeState.fromCode(episode.playState) }
                                            Icon(imageVector = ImageVector.vectorResource(playState.res), tint = playState.color ?: MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (episode.playState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                                            if (episode.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(episode.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                                            if (episode.getMediaType() == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                                            val dateSizeText = remember { " · " + durationStringFull(episode.duration) + (if (episode.size > 0) " · " + Formatter.formatShortFileSize(context, episode.size) else "") }
                                            Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val dateSizeText = remember { formatDateTimeFlex(Date(episode.pubDate)) }
                                            Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (episode.viewCount > 0) {
                                                val viewText = remember { " · " + formatLargeInteger(episode.viewCount) }
                                                Text(viewText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_people_alt_24), tint = textColor, contentDescription = "people", modifier = Modifier.width(16.dp).height(16.dp))
                                            }
                                            if (episode.likeCount > 0) {
                                                val likeText = remember { " · " + formatLargeInteger(episode.likeCount) }
                                                Text(likeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_thumb_up_24), tint = textColor, contentDescription = "likes", modifier = Modifier.width(16.dp).height(16.dp))
                                            }
                                        }
                                    }
                                    LayoutMode.FeedTitle.ordinal -> {
                                        Logd(TAG, "title: ${episode.feed?.title}")
                                        Text(episode.feed?.title ?: "", color = textColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        when (statusRowMode) {
                                            StatusRowMode.Comment -> Comment()
                                            StatusRowMode.Tags -> Tags()
                                            StatusRowMode.Todos -> Todos()
                                            else -> StatusRow()
                                        }
                                    }
                                }
                                when (episode.playState) {
                                    EpisodeState.AGAIN.code, EpisodeState.LATER.code -> {
                                        val dueText = remember { if (episode.repeatTime > 0) "D:" + formatDateTimeFlex(episode.dueDate) else "" }
                                        if (dueText.isNotBlank()) {
                                            val bgColor = if (localTime > episode.repeatTime) Color.Cyan else MaterialTheme.colorScheme.surface
                                            Text(dueText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(bgColor))
                                        }
                                    }
                                    EpisodeState.SKIPPED.code -> {
                                        val dateSizeText = remember(episode.id) {
                                            (if (episode.lastPlayedTime > 0) "P:" + formatDateTimeFlex(episode.lastPlayedDate) else "") +
                                                    (if (episode.playbackCompletionTime > 0) " · C:" + formatDateTimeFlex(episode.playbackCompletionDate) else "") +
                                                    (if (episode.playStateSetTime > 0) " · S:" + formatDateTimeFlex(episode.playStateSetDate) else "") +
                                                    (if (episode.playedDuration > 0) " · " + durationStringFull(episode.playedDuration) else "") }
                                        Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    else -> {}
                                }
                            }
                        }
                        Row(Modifier.background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                            if (showCoverImage && (feed == null || !useFeedImage)) {
                                Box(modifier = Modifier.width(imageWidth).height(imageHeight).clickable(onClick = {
                                    Logd(TAG, "icon clicked!")
                                    when {
                                        selectMode -> toggleSelected(episode)
                                        feed == null && episode.feed?.isSynthetic() != true -> navController.navigate("${Screens.FeedDetails.name}?feedId=${episode.feed!!.id}&modeName=${FeedScreenMode.Info.name}")
                                        else -> episodeForInfo = episode
                                    }
                                })) {
                                    AsyncImage(model = ImageRequest.Builder(context).data(episode.imageLocation(forceFeedImage)).memoryCachePolicy(CachePolicy.ENABLED).build(), placeholder = painterResource(R.drawable.ic_launcher_foreground), error = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "imgvCover", modifier = Modifier.fillMaxSize())
                                    if (episode.feed != null && episode.feed!!.useFeedImage() && episode.feed!!.rating != Rating.UNRATED.code)
                                        Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(episode.feed!!.rating).res), tint = buttonColor, contentDescription = "rating", modifier = Modifier.width(imageWidth/4).height(imageHeight/4).align(Alignment.BottomStart).background(MaterialTheme.colorScheme.tertiaryContainer) )
                                }
                            }
                            Box(Modifier.weight(1f).wrapContentHeight()) {
                                TitleColumn(modifier = Modifier.fillMaxWidth())
                                if (showActionButtons) {
                                    when {
                                        episode.id == curEpisode?.id -> {
                                            if (playerStat == PLAYER_STATUS_PLAYING) actionButton.type = ButtonTypes.PAUSE
                                            else actionButton.update(episode)
                                        }
                                        actionButton.speaking -> actionButton.type = ButtonTypes.PAUSE
                                        actionButton.type == ButtonTypes.PAUSE -> actionButton.update(episode)
                                    }
                                    LaunchedEffect(episode.downloaded) { actionButton.update(episode) }
                                    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.size(50.dp).align(Alignment.BottomEnd).pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = { showAltActionsDialog = true },
                                            onTap = {
                                                val actType = actionButton.type
                                                actionButton.onClick()
                                                actionButtonCB?.invoke(episode, actType)
                                            }) }) {
                                        val dlStats by remember { derivedStateOf { downloadStates[episode.downloadUrl] } }
                                        if (dlStats != null) {
                                            Logd(TAG, "${episode.id} dlStats: ${dlStats?.progress} ${dlStats?.state}")
                                            actionButton.processing = dlStats!!.progress
                                            when (dlStats!!.state) {
                                                DownloadStatus.State.COMPLETED.ordinal -> actionButton.update(episode)
                                                DownloadStatus.State.INCOMPLETE.ordinal -> actionButton.type = ButtonTypes.DOWNLOAD
                                                else -> {}
                                            }
                                        }
                                        Icon(imageVector = ImageVector.vectorResource(actionButton.drawable), tint = buttonColor, contentDescription = null, modifier = Modifier.size(33.dp))
                                        if (actionButton.processing > -1) CircularProgressIndicator(progress = { 0.01f * actionButton.processing }, strokeWidth = 4.dp, color = textColor, modifier = Modifier.size(37.dp).offset(y = 4.dp))
                                    }
                                    if (showAltActionsDialog) actionButton.AltActionsDialog(onDismiss = { showAltActionsDialog = false })
                                }
                            }
                        }

                        if (showActionButtons && (episode.position > 0 || curEpisode?.id == episode.id)) {
                            fun calcProg(): Float {
                                val pos = episode.position
                                val dur = episode.duration
                                return if (dur > 0 && pos >= 0 && dur >= pos) 1f * pos / dur else 0f
                            }
                            val prog by remember(episode.id, episode.position) { mutableFloatStateOf(calcProg()) }
                            val posText by remember(episode.id, episode.position) { mutableStateOf(durationStringFull(episode.position)) }
                            val durText = remember(episode.id) { durationStringFull(episode.duration) }
                            Row {
                                Text(posText, color = textColor, style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(progress = { prog }, modifier = Modifier.weight(1f).height(4.dp).align(Alignment.CenterVertically))
                                Text(durText, color = textColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        if (selectMode) {
            Row(modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.tertiaryContainer), horizontalArrangement = Arrangement.spacedBy(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_upward_24), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp).padding(start = 10.dp)
                    .clickable(onClick = {
                        selected.clear()
                        val eList = multiSelectCB(longPressIndex, -1)
                        if (eList.isEmpty()) for (i in 0..longPressIndex) selected.add(episodes[i])
                        else selected.addAll(eList)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_downward_24), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                    .clickable(onClick = {
                        selected.clear()
                        val eList = multiSelectCB(longPressIndex, 1)
                        if (eList.isEmpty()) for (i in longPressIndex..<episodes.size) selected.add(episodes[i])
                        else selected.addAll(eList)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                var selectAllRes by remember { mutableIntStateOf(R.drawable.ic_select_all) }
                Icon(imageVector = ImageVector.vectorResource(selectAllRes), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                    .clickable(onClick = {
                        if (selectedSize != episodes.size) {
                            selected.clear()
                            val eList = multiSelectCB(longPressIndex, 0)
                            if (eList.isEmpty()) for (e in episodes) selected.add(e)
                            else selected.addAll(eList)
                            selectAllRes = R.drawable.ic_select_none
                        } else {
                            selected.clear()
                            longPressIndex = -1
                            selectAllRes = R.drawable.ic_select_all
                        }
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                @Composable
                fun EpisodeSpeedDial(modifier: Modifier = Modifier) {
                    var isExpanded by remember { mutableStateOf(false) }
                    val bgColor = MaterialTheme.colorScheme.tertiaryContainer
                    val fgColor = remember { complementaryColorOf(bgColor) }
                    fun onSelected() {
                        isExpanded = false
                        selectMode = false
                        selectModeCB?.invoke(selectMode)
                    }
                    val options = mutableListOf<@Composable () -> Unit>(
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            showPlayStateDialog = true
                            onSelected()
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mark_played), contentDescription = "Set played state")
                            Text(stringResource(id = R.string.set_play_state_label)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            showChooseRatingDialog = true
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), contentDescription = "Set rating")
                            Text(stringResource(id = R.string.set_rating_label)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            showEditTagsDialog = true
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_label_24), contentDescription = "Edit tags")
                            Text(stringResource(id = R.string.edit_tags)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            showAddCommentDialog = true
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_comment_24), contentDescription = "Add comment")
                            Text(stringResource(id = R.string.add_comments)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            fun download(now: Boolean) {
                                for (e in selected) if (e.feed != null && !e.feed!!.isLocalFeed) DownloadServiceInterface.impl?.downloadNow(e, now)
                            }
                            if (mobileAllowEpisodeDownload || !getApp().networkMonitor.isNetworkRestricted) download(true)
                            else {
                                commonConfirm = CommonConfirmAttrib(
                                    title = context.getString(R.string.confirm_mobile_download_dialog_title),
                                    message = context.getString(if (getApp().networkMonitor.isNetworkRestricted && getApp().networkMonitor.isVpnOverWifi) R.string.confirm_mobile_download_dialog_message_vpn else R.string.confirm_mobile_download_dialog_message),
                                    confirmRes = R.string.confirm_mobile_download_dialog_download_later,
                                    cancelRes = R.string.cancel_label,
                                    neutralRes = R.string.confirm_mobile_download_dialog_allow_this_time,
                                    onConfirm = { download(false) },
                                    onNeutral = { download(true) })
                            }
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_download), contentDescription = "Download")
                            Text(stringResource(id = R.string.download_label)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            runOnIOScope { selected.forEach { addToAssQueue(listOf(it)) } }
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), contentDescription = "Add to associated or active queue")
                            Text(stringResource(id = R.string.add_to_associated_queue)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            runOnIOScope { addToQueue(selected, actQueue) }
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), contentDescription = "Add to active queue")
                            Text(stringResource(id = R.string.add_to_active_queue)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            showPutToQueueDialog = true
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), contentDescription = "Add to queue...")
                            Text(stringResource(id = R.string.add_to_queue)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            runOnIOScope { for (e in selected) smartRemoveFromAllQueues(e) }
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_remove), contentDescription = "Remove from active queue")
                            Text(stringResource(id = R.string.remove_from_all_queues)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            runOnIOScope {
                                realm.write {
                                    val selected_ = query(Episode::class, "id IN $0", selected.map { it.id }.toList()).find()
                                    for (e in selected_) {
                                        for (e1 in selected_) {
                                            if (e.id == e1.id) continue
                                            Logd(TAG, "set related: ${e.id} ${e1.id}")
                                            e.related.add(e1)
                                        }
                                    }
                                }
                            }
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), contentDescription = "Set related")
                            Text(stringResource(id = R.string.set_related)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            runOnIOScope {
                                realm.write {
                                    for (e_ in selected) {
                                        val e = findLatest(e_)
                                        if (e == null || (!e.downloaded && e.feed?.isLocalFeed != true)) continue
                                        val almostEnded = e.hasAlmostEnded()
                                        if (almostEnded) {
                                            if (e.playState < EpisodeState.PLAYED.code) e.setPlayState(EpisodeState.PLAYED)
                                            e.playbackCompletionDate = Date()
                                        }
                                    }
                                }
                                deleteEpisodesWarnLocalRepeat(selected)
                            }
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), contentDescription = "Delete media")
                            Text(stringResource(id = R.string.delete_episode_label)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            showShelveDialog = true
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_shelves_24), contentDescription = "Shelve")
                            Text(stringResource(id = R.string.shelve_label)) } },
                        { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                            onSelected()
                            showMulticastDialog = true
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_share), contentDescription = "Multicast")
                            Text(stringResource(id = R.string.multicast_to_devices)) } },
                    )
                    if (selected.isNotEmpty() && isExternal)
                        options.add {
                            Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                                onSelected()
                                CoroutineScope(Dispatchers.IO).launch {
                                    ytUrls.clear()
                                    for (e in selected) {
                                        try {
                                            val url = URL(e.downloadUrl ?: "")
                                            if (gearbox.isGearUrl(url)) ytUrls.add(e.downloadUrl!!)
                                            else addRemoteToMiscSyndicate(e)
                                        } catch (ex: MalformedURLException) { Loge(TAG, "episode downloadUrl not valid: ${e.title} : ${e.downloadUrl}") }
                                    }
                                }
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AddCircle, contentDescription = "Reserve episodes")
                                Text(stringResource(id = R.string.reserve_episodes_label))
                            }
                        }
                    if (feed != null) {
                        options.add {
                            Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                                onSelected()
                                showEraseDialog = true
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_delete_forever_24), contentDescription = "Erase episodes")
                                Text(stringResource(id = R.string.erase_episodes_label))
                            }
                        }
                    }
                    if (isExpanded) CommonPopupCard(onDismissRequest = { isExpanded = false }) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { options.forEachIndexed { _, entry -> entry() } }
                    }
                    FloatingActionButton(containerColor = bgColor, contentColor = fgColor, onClick = { isExpanded = !isExpanded }) { Icon(Icons.Filled.Menu, "Menu") }
                }
                EpisodeSpeedDial(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}
