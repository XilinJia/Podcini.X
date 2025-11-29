package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowEpisodeDownload
import ac.mdiq.podcini.playback.base.InTheatre.curMediaId
import ac.mdiq.podcini.playback.base.InTheatre.playerStat
import ac.mdiq.podcini.storage.database.addRemoteToMiscSyndicate
import ac.mdiq.podcini.storage.database.addToActQueue
import ac.mdiq.podcini.storage.database.addToAssOrActQueue
import ac.mdiq.podcini.storage.database.deleteEpisodesWarnLocalRepeat

import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.setPlayState
import ac.mdiq.podcini.storage.database.smartRemoveFromActQueue
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PLAYING
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.getDurationStringLong
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeAction.Companion.onEpisode
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoAction
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.downloadStates
import ac.mdiq.podcini.ui.screens.FeedScreenMode
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.ui.screens.episodeOnDisplay
import ac.mdiq.podcini.ui.screens.feedOnDisplay
import ac.mdiq.podcini.ui.screens.feedScreenMode
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.formatDateTimeFlex
import ac.mdiq.podcini.utils.formatLargeInteger
import ac.mdiq.podcini.utils.stripDateTimeLines
import android.content.Context
import android.text.format.Formatter
import android.util.TypedValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.MalformedURLException
import java.net.URL
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "EpisodesVM"

var showSwipeActionsDialog by mutableStateOf(false)

@Composable
fun InforBar(text: State<String>, swipeActions: SwipeActions) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val leftAction = swipeActions.actions.left[0]
    val rightAction = swipeActions.actions.right[0]
    Logd("InforBar", "textState: ${text.value}")
    Row {
        Icon(imageVector = ImageVector.vectorResource(leftAction.iconRes), tint = buttonColor, contentDescription = "left_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {  showSwipeActionsDialog = true }))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "left_arrow", modifier = Modifier.width(24.dp).height(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        Text(text.value, color = textColor, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(24.dp).height(24.dp))
        Icon(imageVector = ImageVector.vectorResource(rightAction.iconRes), tint = buttonColor, contentDescription = "right_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {  showSwipeActionsDialog = true }))
    }
}

enum class LayoutMode {
    Normal, WideImage, FeedTitle
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun EpisodeLazyColumn(activity: Context, episodes: List<Episode>, feed: Feed? = null, layoutMode: Int = LayoutMode.Normal.ordinal,
                      showCoverImage: Boolean = true, forceFeedImage: Boolean = false,
                      showActionButtons: Boolean = true, showComment: Boolean = false,
                      isDraggable: Boolean = false, dragCB: ((Int, Int)->Unit)? = null,
                      swipeActions: SwipeActions? = null,
                      refreshCB: (()->Unit)? = null,
                      actionButton_: ((Episode)->EpisodeActionButton)? = null, actionButtonCB: ((Episode, ButtonTypes)->Unit)? = null) {

    var selectMode by remember { mutableStateOf(false) }
    var selectedSize by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<Episode>() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var longPressIndex by remember { mutableIntStateOf(-1) }
    val context = LocalContext.current
    val navController = LocalNavController.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val localTime = System.currentTimeMillis()

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
    val leftActionState = remember { mutableStateOf<SwipeAction>(NoAction()) }
    val rightActionState = remember { mutableStateOf<SwipeAction>(NoAction()) }
    if (swipeActions != null) {
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
        leftSwipeCB = {
            if (leftActionState.value is NoAction) showSwipeActionsDialog = true
            else leftActionState.value.performAction(it)
        }
        rightSwipeCB = {
            if (rightActionState.value is NoAction) showSwipeActionsDialog = true
            else rightActionState.value.performAction(it)
        }
        if (showSwipeActionsDialog) SwipeActionsSettingDialog(swipeActions, onDismissRequest = { showSwipeActionsDialog = false }) { actions ->
            swipeActions.actions = actions
            leftActionState.value = swipeActions.actions.left[0]
            rightActionState.value = swipeActions.actions.right[0]
        }
    }

    var showChooseRatingDialog by remember { mutableStateOf(false) }
    var showAddCommentDialog by remember { mutableStateOf(false) }
    var showEditTagsDialog by remember { mutableStateOf(false) }
    var showIgnoreDialog by remember { mutableStateOf(false) }
    var futureState by remember { mutableStateOf(EpisodeState.UNSPECIFIED) }
    var showPlayStateDialog by remember { mutableStateOf(false) }
    var showPutToQueueDialog by remember { mutableStateOf(false) }
    var showShelveDialog by remember { mutableStateOf(false) }
    var showEraseDialog by remember { mutableStateOf(false) }
    val showConfirmYoutubeDialog = remember { mutableStateOf(false) }
    val ytUrls = remember { mutableListOf<String>() }

    @Composable
    fun OpenDialogs() {
        gearbox.ConfirmAddEpisode(ytUrls, showConfirmYoutubeDialog.value, onDismissRequest = { showConfirmYoutubeDialog.value = false })
        if (showChooseRatingDialog) ChooseRatingDialog(selected) { showChooseRatingDialog = false }
        if (showAddCommentDialog) AddCommentDialog(selected) { showAddCommentDialog = false }
        if (showEditTagsDialog) TagSettingDialog(TagType.Episode, setOf(), onDismiss = { showEditTagsDialog = false }) { tags ->
            runOnIOScope { for (e in selected) upsert(e) { it.tags.addAll(tags) }  }
        }
        if (showPlayStateDialog) PlayStateDialog(selected, onDismissRequest = { showPlayStateDialog = false }, { futureState = it }, { showIgnoreDialog = true })
        if (showPutToQueueDialog) PutToQueueDialog(selected) { showPutToQueueDialog = false }
        if (showShelveDialog) ShelveDialog(selected) { showShelveDialog = false }
        if (showEraseDialog && feed != null) EraseEpisodesDialog(selected, feed, onDismissRequest = { showEraseDialog = false })
        if (showIgnoreDialog) IgnoreEpisodesDialog(selected, onDismissRequest = { showIgnoreDialog = false })
        if (futureState in listOf(EpisodeState.AGAIN, EpisodeState.LATER)) FutureStateDialog(selected, futureState, onDismissRequest = { futureState = EpisodeState.UNSPECIFIED })
    }

    OpenDialogs()

    @Composable
    fun EpisodeSpeedDial(modifier: Modifier = Modifier) {
        var isExpanded by remember { mutableStateOf(false) }
        fun onSelected() {
            isExpanded = false
            selectMode = false
        }
        val options = mutableListOf<@Composable () -> Unit>(
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                showPlayStateDialog = true
                onSelected()
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mark_played), "Set played state")
                Text(stringResource(id = R.string.set_play_state_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                showChooseRatingDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), "Set rating")
                Text(stringResource(id = R.string.set_rating_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                showEditTagsDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_label_24), "Edit tags")
                Text(stringResource(id = R.string.edit_tags)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                showAddCommentDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_comment_24), "Add comment")
                Text(stringResource(id = R.string.add_opinion_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                fun download(now: Boolean) {
                    for (e in selected) if (e.feed != null && !e.feed!!.isLocalFeed) DownloadServiceInterface.impl?.downloadNow(activity, e, now)
                }
                if (mobileAllowEpisodeDownload || !isNetworkRestricted) download(true)
                else {
                    commonConfirm = CommonConfirmAttrib(
                        title = context.getString(R.string.confirm_mobile_download_dialog_title),
                        message = context.getString(if (isNetworkRestricted && NetworkUtils.isVpnOverWifi) R.string.confirm_mobile_download_dialog_message_vpn else R.string.confirm_mobile_download_dialog_message),
                        confirmRes = R.string.confirm_mobile_download_dialog_download_later,
                        cancelRes = R.string.cancel_label,
                        neutralRes = R.string.confirm_mobile_download_dialog_allow_this_time,
                        onConfirm = { download(false) },
                        onNeutral = { download(true) })
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_download), "Download")
                Text(stringResource(id = R.string.download_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                runOnIOScope { selected.forEach { addToAssOrActQueue(it) } }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to associated or active queue")
                Text(stringResource(id = R.string.add_to_associated_queue)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                runOnIOScope { addToActQueue(selected) }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to active queue")
                Text(stringResource(id = R.string.add_to_queue_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                showPutToQueueDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to queue...")
                Text(stringResource(id = R.string.put_in_queue_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                runOnIOScope { for (e in selected) smartRemoveFromActQueue(e) }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_remove), "Remove from active queue")
                Text(stringResource(id = R.string.remove_from_all_queues)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                runOnIOScope {
                    realm.write {
                        val selected_ = query(Episode::class, "id IN $0", selected.map { it.id }.toList()).find()
                        for (e in selected_) {
                            for (e1 in selected_) {
                                if (e.id == e1.id) continue
                                e.related.add(e1)
                            }
                        }
                    }
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "Set related")
                Text(stringResource(id = R.string.set_related)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                runOnIOScope {
                    for (e_ in selected) {
                        var e = e_
                        if (!e.downloaded && e.feed?.isLocalFeed != true) continue
                        val almostEnded = e.hasAlmostEnded()
                        if (almostEnded && e.playState < EpisodeState.PLAYED.code) e = setPlayState(state = EpisodeState.PLAYED, episode = e, resetMediaPosition = true, removeFromQueue = false)
                        if (almostEnded) upsert(e) { it.playbackCompletionDate = Date() }
                    }
                    deleteEpisodesWarnLocalRepeat(activity, selected)
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "Delete media")
                Text(stringResource(id = R.string.delete_episode_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                showShelveDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_shelves_24), "Shelve")
                Text(stringResource(id = R.string.shelve_label)) } },
        )
        if (selected.isNotEmpty() && selected[0].isRemote.value)
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
                        withContext(Dispatchers.Main) { showConfirmYoutubeDialog.value = ytUrls.isNotEmpty() }
                    }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AddCircle, "Reserve episodes")
                    Text(stringResource(id = R.string.reserve_episodes_label))
                }
            }
        if (feed != null) {
            options.add {
                Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    onSelected()
                    showEraseDialog = true
                }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_delete_forever_24), "Erase episodes")
                    Text(stringResource(id = R.string.erase_episodes_label))
                }
            }
        }

        Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Bottom) {
            if (isExpanded) options.forEachIndexed { _, button -> FloatingActionButton(containerColor = MaterialTheme.colorScheme.onTertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).height(40.dp),onClick = {}) { button() } }
            FloatingActionButton(containerColor = MaterialTheme.colorScheme.onTertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiary, onClick = { isExpanded = !isExpanded }) { Icon(Icons.Filled.Edit, "Edit") }
        }
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

        val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
//        var forceRecomposeKey by remember { mutableIntStateOf(0) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                Logd(TAG, "LifecycleEventObserver: $event")
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        scope.launch {
                            lazyListState.scrollToItem(
                                lazyListState.firstVisibleItemIndex,
                                lazyListState.firstVisibleItemScrollOffset
                            )
                            //                        forceRecomposeKey++
                            Logd(TAG, "Screen on, triggered scroll for recomposition")
                        }
                    }
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

        val swipeVelocityThreshold = 1500f
        val swipeDistanceThreshold = with(LocalDensity.current) { 100.dp.toPx() }
        val useFeedImage = remember(feed) { feed?.useFeedImage() == true }

        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(episodes, key = { _, e -> e.id }) { index, episode_ ->
                val episode by rememberUpdatedState(episode_)
                var actionButton by remember(episode.id) { mutableStateOf(EpisodeActionButton(episode)) }
                var showAltActionsDialog by remember(episode.id) { mutableStateOf(false) }
                var isSelected by remember(episode.id, selectMode, selectedSize) { mutableStateOf( selectMode && episode in selected ) }

                fun toggleSelected(e: Episode) {
                    isSelected = !isSelected
                    if (isSelected) selected.add(e)
                    else selected.remove(e)
                }

                @Composable
                fun MainRow(index: Int, yOffset: Float, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
                    val titleMaxLines = if (layoutMode == LayoutMode.Normal.ordinal) if (showComment) 1 else 2 else 3
                    @Composable
                    fun TitleColumn(index: Int, modifier: Modifier) {
                        Column(modifier.padding(start = 6.dp, end = 6.dp).combinedClickable(
                            onClick = {
                                Logd(TAG, "clicked: ${episode.title}")
                                if (selectMode) toggleSelected(episode)
                                else {
                                    episodeOnDisplay = episode
                                    navController.navigate(Screens.EpisodeInfo.name)
                                }
                            },
                            onLongClick = {
                                selectMode = !selectMode
                                isSelected = selectMode
                                selected.clear()
                                if (selectMode) {
                                    selected.add(episodes[index])
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
                                val comment = remember { stripDateTimeLines(episode.comment).replace("\n", "  ") }
                                Text(comment, color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            @Composable
                            fun StatusRow() {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val playState = remember(episode.playState) { EpisodeState.fromCode(episode.playState) }
                                    Icon(imageVector = ImageVector.vectorResource(playState.res), tint = playState.color ?: MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (episode.playState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                                    if (episode.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(episode.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                                    if (episode.comment.isNotBlank()) Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_comment_24), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "comment", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                                    if (episode.getMediaType() == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                                    val dateSizeText = remember {
                                        " · " + formatDateTimeFlex(Date(episode.pubDate)) + " · " + getDurationStringLong(episode.duration) +
                                                (if (episode.size > 0) " · " + Formatter.formatShortFileSize(context, episode.size) else "") +
                                                (if (episode.viewCount > 0) " · " + formatLargeInteger(episode.viewCount) else "") +
                                                (if (episode.likeCount > 0) " · " + formatLargeInteger(episode.likeCount) else "")
                                    }
                                    Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            when (layoutMode) {
                                LayoutMode.Normal.ordinal -> {
                                    if (showComment) Comment()
                                    else StatusRow()
                                }
                                LayoutMode.WideImage.ordinal -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val playState = remember(episode.playState) { EpisodeState.fromCode(episode.playState) }
                                        Icon(imageVector = ImageVector.vectorResource(playState.res), tint = playState.color ?: MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (episode.playState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                                        if (episode.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(episode.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                                        if (episode.getMediaType() == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                                        val dateSizeText = remember { " · " + getDurationStringLong(episode.duration) + (if (episode.size > 0) " · " + Formatter.formatShortFileSize(context, episode.size) else "") }
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
                                    Text(episode.feed?.title ?: "", color = textColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (showComment) Comment()
                                    else StatusRow()
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
                                    val dateSizeText = remember {
                                        (if (episode.lastPlayedTime > 0) "P:" + formatDateTimeFlex(episode.lastPlayedDate) else "") +
                                                (if (episode.playbackCompletionTime > 0) " · C:" + formatDateTimeFlex(episode.playbackCompletionDate) else "") +
                                                (if (episode.playStateSetTime > 0) " · S:" + formatDateTimeFlex(episode.playStateSetDate) else "") +
                                                (if (episode.playedDuration > 0) " · " + getDurationStringLong(episode.playedDuration) else "") }
                                    Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                else -> {}
                            }
                        }
                    }

                    val density = LocalDensity.current
                    val imageWidth = if (layoutMode == LayoutMode.WideImage.ordinal) 150.dp else 56.dp
                    val imageHeight = if (layoutMode == LayoutMode.WideImage.ordinal) 100.dp else 56.dp
                    Row(Modifier.background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface).offset(y = with(density) { yOffset.toDp() })) {
                        if (isDraggable) {
                            val typedValue = TypedValue()
                            context.theme.resolveAttribute(R.attr.dragview_background, typedValue, true)
                            Icon(imageVector = ImageVector.vectorResource(typedValue.resourceId), tint = buttonColor, contentDescription = "drag handle", modifier = Modifier.width(50.dp).align(Alignment.CenterVertically).padding(start = 10.dp, end = 15.dp).draggable(orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta -> onDrag(delta) }, onDragStarted = { onDragStart() }, onDragStopped = { onDragEnd() } ))
                        }
                        if (showCoverImage && (feed == null || !useFeedImage)) {
                            val img = remember(episode.id) { ImageRequest.Builder(context).data(episode.imageLocation(forceFeedImage)).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                            AsyncImage(model = img, contentDescription = "imgvCover", modifier = Modifier.width(imageWidth).height(imageHeight)
                                .clickable(onClick = {
                                    Logd(TAG, "icon clicked!")
                                    when {
                                        selectMode -> toggleSelected(episode)
                                        feed == null && episode.feed?.isSynthetic() != true -> {
                                            feedOnDisplay = episode.feed!!
                                            feedScreenMode = FeedScreenMode.Info
                                            navController.navigate(Screens.FeedDetails.name)
                                        }
                                        else -> {
                                            episodeOnDisplay = episode
                                            navController.navigate(Screens.EpisodeInfo.name)
                                        }
                                    }
                                }))
                        }
                        Box(Modifier.weight(1f).wrapContentHeight()) {
                            TitleColumn(index, modifier = Modifier.fillMaxWidth())
                            if (showActionButtons) {
                                if (actionButton_ == null) {
//                                    LaunchedEffect(playerStat) {
//                                        if (episode.id == curMediaId) {
//                                            if (playerStat == PLAYER_STATUS_PLAYING) actionButton.type = ButtonTypes.PAUSE
//                                            else actionButton.update(episode)
//                                        } else if (actionButton.type == ButtonTypes.PAUSE) actionButton.update(episode)
//                                    }
                                    if (episode.id == curMediaId) {
                                        if (playerStat == PLAYER_STATUS_PLAYING) actionButton.type = ButtonTypes.PAUSE
                                        else actionButton.update(episode)
                                    } else if (actionButton.type == ButtonTypes.PAUSE) actionButton.update(episode)
                                    LaunchedEffect(episode.downloaded) { actionButton.update(episode) }
                                } else LaunchedEffect(Unit) { actionButton = actionButton_(episode) }
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).padding(end = 10.dp).align(Alignment.BottomEnd).pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { showAltActionsDialog = true },
                                        onTap = {
                                            val actType = actionButton.type
                                            actionButton.onClick(activity)
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
                                if (showAltActionsDialog) actionButton.AltActionsDialog(activity, onDismiss = { showAltActionsDialog = false })
                            }
                        }
                    }
                }

                val velocityTracker = remember { VelocityTracker() }
                val offsetX = remember(episode.id) { Animatable(0f) }
                var isDragging by remember(episode.id) { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxWidth().zIndex(if (isDragging) 10f else 0f).pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
//                            Logd(TAG, "detectHorizontalDragGestures onDragStart")
                            velocityTracker.resetTracking()
                        },
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
                        var yOffset by remember { mutableFloatStateOf(0f) }
                        var draggedIndex by remember { mutableStateOf<Int?>(null) }
                        MainRow(index,
                            yOffset = if (draggedIndex == index) yOffset else 0f,
                            onDragStart = {
                                Logd(TAG, "MainRow onDragStart")
                                isDragging = true
                                draggedIndex = index
                            },
                            onDrag = { delta -> yOffset += delta },
                            onDragEnd = {
                                Logd(TAG, "MainRow onDragEnd")
                                isDragging = false
                                draggedIndex?.let { startIndex ->
                                    val newIndex = (startIndex + (yOffset / rowHeightPx).toInt()).coerceIn(0, episodes.lastIndex)
                                    Logd(TAG, "onDragEnd draggedIndex: $draggedIndex newIndex: $newIndex")
                                    if (newIndex != startIndex) dragCB?.invoke(startIndex, newIndex)
                                }
                                draggedIndex = null
                                yOffset = 0f
                            })
                        if (showActionButtons && (episode.isInProgress || curMediaId == episode.id)) {
                            fun calcProg(): Float {
                                val pos = episode.position
                                val dur = episode.duration
                                return if (dur > 0 && pos >= 0 && dur >= pos) 1f * pos / dur else 0f
                            }
                            val prog by remember(episode.id, episode.position) { mutableFloatStateOf(calcProg()) }
                            val posText by remember(episode.id, episode.position) { mutableStateOf(getDurationStringLong(episode.position)) }
                            val durText = remember(episode.id) { getDurationStringLong(episode.duration) }
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
            Row(modifier = Modifier.align(Alignment.TopEnd).width(150.dp).height(45.dp).background(MaterialTheme.colorScheme.onTertiaryContainer),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_upward_24), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                    .clickable(onClick = {
                        selected.clear()
                        val eList = multiSelectCB(longPressIndex, -1)
                        if (eList.isEmpty()) for (i in 0..longPressIndex) selected.add(episodes[i])
                        else selected.addAll(eList)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_downward_24), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
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
            }
            EpisodeSpeedDial(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp, start = 16.dp))
        }
    }
}
