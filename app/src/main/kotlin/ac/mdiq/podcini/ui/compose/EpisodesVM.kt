package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowEpisodeDownload
import ac.mdiq.podcini.playback.base.InTheatre.curMediaId
import ac.mdiq.podcini.playback.base.InTheatre.playedIds
import ac.mdiq.podcini.playback.base.InTheatre.playerStat
import ac.mdiq.podcini.playback.base.InTheatre.rememberPlayedIds
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodesWarnLocalRepeat
import ac.mdiq.podcini.storage.database.Episodes.hasAlmostEnded
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Episodes.vmIndexWithId
import ac.mdiq.podcini.storage.database.Feeds.addRemoteToMiscSyndicate
import ac.mdiq.podcini.storage.database.Queues.addToActiveQueue
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.smartRemoveFromQueue
import ac.mdiq.podcini.storage.database.RealmDB.MonitorEntity
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.subscribeEpisode
import ac.mdiq.podcini.storage.database.RealmDB.unsubscribeEpisode
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PLAYING
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLong
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoAction
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.downloadStates
import ac.mdiq.podcini.ui.screens.FeedScreenMode
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.ui.utils.episodeOnDisplay
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import ac.mdiq.podcini.util.MiscFormatter.formatLargeInteger
import ac.mdiq.podcini.util.MiscFormatter.stripDateTimeLines
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.MalformedURLException
import java.net.URL
import java.util.Date
import kotlin.math.roundToInt

const val VMS_CHUNK_SIZE = 50
private const val loadThreshold = (VMS_CHUNK_SIZE * 0.8).toInt()
private const val TAG = "EpisodesVM"

var showSwipeActionsDialog by mutableStateOf(false)

@Composable
fun InforBar(text: State<String>, swipeActions: SwipeActions) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val leftAction = remember { mutableStateOf<SwipeAction>(NoAction()) }
    val rightAction = remember { mutableStateOf<SwipeAction>(NoAction()) }
    fun refreshSwipeTelltale() {
        leftAction.value = swipeActions.actions.left[0]
        rightAction.value = swipeActions.actions.right[0]
    }
    refreshSwipeTelltale()
    Logd("InforBar", "textState: ${text.value}")
    Row {
        Icon(imageVector = ImageVector.vectorResource(leftAction.value.iconRes), tint = buttonColor, contentDescription = "left_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {  showSwipeActionsDialog = true }))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "left_arrow", modifier = Modifier.width(24.dp).height(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        Text(text.value, color = textColor, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(24.dp).height(24.dp))
        Icon(imageVector = ImageVector.vectorResource(rightAction.value.iconRes), tint = buttonColor, contentDescription = "right_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {  showSwipeActionsDialog = true }))
    }
}

@Stable
class EpisodeVM(var episode: Episode, val tag: String) {
    var positionState by mutableIntStateOf(episode.position)
    var durationState by mutableIntStateOf(episode.duration)
    var playedState by mutableIntStateOf(episode.playState)
    var hasComment by mutableStateOf(episode.comment.isNotBlank())
    var ratingState by mutableIntStateOf(episode.rating)
    var inProgressState by mutableStateOf(episode.isInProgress)
    var downloadState by mutableIntStateOf(if (episode.downloaded) DownloadStatus.State.COMPLETED.ordinal else DownloadStatus.State.UNKNOWN.ordinal)
    var viewCount by mutableIntStateOf(episode.viewCount)
    var likeCount by mutableIntStateOf(episode.likeCount)
    var actionButton by mutableStateOf(EpisodeActionButton(episode))
    var showAltActionsDialog by mutableStateOf(false)
    var dlPercent by mutableIntStateOf(0)
    var isSelected by mutableStateOf(false)
//    var prog by mutableFloatStateOf(0f)

    suspend fun updateVMFromDB() {
        val e = realm.query(Episode::class, "id == ${episode.id}").first().find() ?: return
        updateVM(e, arrayOf())
        actionButton.update(e)
    }

    suspend fun updateVM(e: Episode,  fa: Array<String>) {
        Logd(TAG, "updateVM onChange ${episode.title} ")
        Logd(TAG, "updateVM onChange ${e.title} ")
        if (episode.id == e.id) {
            episode = e
            withContext(Dispatchers.Main) {
                Logd(TAG, "updateVM fields: ${fa.joinToString()} ${e.position} ${e.downloaded}")
                playedState = e.playState
                ratingState = e.rating
                downloadState = if (e.downloaded) DownloadStatus.State.COMPLETED.ordinal else DownloadStatus.State.UNKNOWN.ordinal
                positionState = e.position
                durationState = e.duration
                inProgressState = e.isInProgress
                if ("playState" in fa || "downloaded" in fa) actionButton.update(e)
            }
        }
    }
}

enum class LayoutMode {
    Normal, WideImage, FeedTitle
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun EpisodeLazyColumn(activity: Context, vms: List<EpisodeVM>, feed: Feed? = null, layoutMode: Int = LayoutMode.Normal.ordinal,
                      showCoverImage: Boolean = true, forceFeedImage: Boolean = false,
                      showActionButtons: Boolean = true, showComment: Boolean = false,
                      buildMoreItems: (()->Unit) = {},
                      isDraggable: Boolean = false, dragCB: ((Int, Int)->Unit)? = null,
                      swipeActions: SwipeActions? = null,
                      refreshCB: (()->Unit)? = null,
                      actionButton_: ((Episode)->EpisodeActionButton)? = null, actionButtonCB: ((Episode, ButtonTypes)->Unit)? = null) {
    var selectMode by remember { mutableStateOf(false) }
    var selectedSize by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<EpisodeVM>() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var longPressIndex by remember { mutableIntStateOf(-1) }
    //    val dls = remember { DownloadServiceInterface.impl }
    val context = LocalContext.current
    val navController = LocalNavController.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val localTime = System.currentTimeMillis()

    fun multiSelectCB(index: Int, aboveOrBelow: Int): List<EpisodeVM> {
        return when (aboveOrBelow) {
            0 -> vms
            -1 -> if (index < vms.size) vms.subList(0, index+1) else vms
            1 -> if (index < vms.size) vms.subList(index, vms.size) else vms
            else -> listOf()
        }
    }

    var leftSwipeCB: ((EpisodeVM)->Unit)? = null
    var rightSwipeCB: ((EpisodeVM)->Unit)? = null
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

    val showConfirmYoutubeDialog = remember { mutableStateOf(false) }
    val ytUrls = remember { mutableListOf<String>() }
    gearbox.ConfirmAddEpisode(ytUrls, showConfirmYoutubeDialog.value, onDismissRequest = { showConfirmYoutubeDialog.value = false })

    var showChooseRatingDialog by remember { mutableStateOf(false) }
    if (showChooseRatingDialog) ChooseRatingDialog(selected) { showChooseRatingDialog = false }

    var showAddCommentDialog by remember { mutableStateOf(false) }
    if (showAddCommentDialog) AddCommentDialog(selected) { showAddCommentDialog = false }

    var showIgnoreDialog by remember { mutableStateOf(false) }
    var futureState by remember { mutableStateOf(EpisodeState.UNSPECIFIED) }
    var showPlayStateDialog by remember { mutableStateOf(false) }
    if (showPlayStateDialog) PlayStateDialog(selected, onDismissRequest = { showPlayStateDialog = false }, { futureState = it },{ showIgnoreDialog = true })

    var showPutToQueueDialog by remember { mutableStateOf(false) }
    if (showPutToQueueDialog) PutToQueueDialog(selected) { showPutToQueueDialog = false }

    var showShelveDialog by remember { mutableStateOf(false) }
    if (showShelveDialog) ShelveDialog(selected) { showShelveDialog = false }

    var showEraseDialog by remember { mutableStateOf(false) }
    if (showEraseDialog && feed != null) EraseEpisodesDialog(selected, feed, onDismissRequest = { showEraseDialog = false })

    if (showIgnoreDialog) IgnoreEpisodesDialog(selected, onDismissRequest = { showIgnoreDialog = false })
    if (futureState in listOf(EpisodeState.AGAIN, EpisodeState.LATER)) FutureStateDialog(selected, futureState, onDismissRequest = { futureState = EpisodeState.UNSPECIFIED })

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
                showAddCommentDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_comment_24), "Add comment")
                Text(stringResource(id = R.string.add_opinion_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                fun download(now: Boolean) {
                    for (vm in selected) if (vm.episode.feed != null && !vm.episode.feed!!.isLocalFeed) DownloadServiceInterface.impl?.downloadNow(activity, vm.episode, now)
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
                runOnIOScope { selected.forEach { addToQueueSync(it.episode) } }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to associated or active queue")
                Text(stringResource(id = R.string.add_to_associated_queue)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                addToActiveQueue(*(selected.map { it.episode }).toTypedArray())
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
                runOnIOScope { for (vm in selected) smartRemoveFromQueue(vm.episode) }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_remove), "Remove from active queue")
                Text(stringResource(id = R.string.remove_from_queue_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                runOnIOScope {
                    realm.write {
                        val selected_ = query(Episode::class, "id IN $0", selected.map { it.episode.id }.toList()).find()
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
                    for (vm in selected) {
                        if (!vm.episode.downloaded && vm.episode.feed?.isLocalFeed != true) continue
                        val almostEnded = hasAlmostEnded(vm.episode)
                        if (almostEnded && vm.episode.playState < EpisodeState.PLAYED.code) vm.episode = setPlayStateSync(state = EpisodeState.PLAYED, episode = vm.episode, resetMediaPosition = true, removeFromQueue = false)
                        if (almostEnded) upsert(vm.episode) { it.playbackCompletionDate = Date() }
                    }
                    deleteEpisodesWarnLocalRepeat(activity, selected.map { it.episode })
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
        if (selected.isNotEmpty() && selected[0].episode.isRemote.value)
            options.add {
                Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    onSelected()
                    CoroutineScope(Dispatchers.IO).launch {
                        ytUrls.clear()
                        for (vm in selected) {
                            try {
                                val url = URL(vm.episode.downloadUrl ?: "")
                                if (gearbox.isGearUrl(url)) ytUrls.add(vm.episode.downloadUrl!!)
                                else addRemoteToMiscSyndicate(vm.episode)
                            } catch (e: MalformedURLException) { Loge(TAG, "episode downloadUrl not valid: ${vm.episode} : ${vm.episode.downloadUrl}") }
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

        val scrollState = rememberScrollState()
        Column(modifier = modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.Bottom) {
            if (isExpanded) options.forEachIndexed { _, button -> FloatingActionButton(containerColor = MaterialTheme.colorScheme.onTertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).height(40.dp),onClick = {}) { button() } }
            FloatingActionButton(containerColor = MaterialTheme.colorScheme.onTertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiary, onClick = { isExpanded = !isExpanded }) { Icon(Icons.Filled.Edit, "Edit") }
        }
    }

    fun toggleSelected(vm: EpisodeVM) {
        vm.isSelected = !vm.isSelected
        if (vm.isSelected) selected.add(vm)
        else selected.remove(vm)
    }

    val isLoading = remember { mutableStateOf(false) }
    val loadedIndices = remember { mutableSetOf<Int>() }
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            //            .debounce(300)
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= 0 && lastVisibleIndex >= vms.size - loadThreshold && !isLoading.value && !loadedIndices.contains(lastVisibleIndex)) {
                    loadedIndices.add(lastVisibleIndex)
                    isLoading.value = true
                    buildMoreItems()
                    isLoading.value = false
                }
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
        var curVM by remember { mutableStateOf<EpisodeVM?>(null) }
        var subscribeLock by remember { mutableStateOf(false) }
        fun unsubscribeCurVM() {
            if (curVM != null) {
                Logd(TAG, "unsubscribeCurVM ${curVM?.episode?.id}")
                unsubscribeEpisode(curVM!!.episode,  curVM!!.tag)
                curVM!!.actionButton.update(curVM!!.episode)
                curVM = null
            }
        }
        fun subscribeCurVM() {
            if (subscribeLock) return
            subscribeLock = true
            Logd(TAG, "subscribeCurVM curVM?.episode: ${curVM?.episode?.id} curMediaId: $curMediaId")
            if (curVM?.episode?.id != curMediaId) unsubscribeCurVM()
            if (curMediaId > 0) {
                scope.launch {
                    snapshotFlow { lazyListState.layoutInfo }
                        .filter { it.visibleItemsInfo.isNotEmpty() }
                        .first()
                        .let { it0 ->
                            val visibleItems = it0.visibleItemsInfo.mapNotNull { vms.getOrNull(it.index) }
                            val vm = visibleItems.find { it.episode.id == curMediaId }
                            if (vm != null) {
                                Logd(TAG, "subscribeCurVM start monitor ${vm.episode.title}")
                                subscribeEpisode(vm.episode, MonitorEntity(vm.tag, onChanges = { e, fields -> vm.updateVM(e, fields) }))
                                curVM = vm
                                if (playerStat == PLAYER_STATUS_PLAYING) curVM!!.actionButton.type = ButtonTypes.PAUSE
                                else curVM!!.actionButton.update(curVM!!.episode)
                            }
                        }
                }
            }
            subscribeLock = false
        }

        val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
        LaunchedEffect(curMediaId, vms.size, episodeSortOrder) {
            Logd(TAG, "LaunchedEffect curMediaId vms episodeSortOrder")
            subscribeCurVM()
        }

        DisposableEffect(lazyListState) {
            val job = CoroutineScope(Dispatchers.Main.immediate).launch {
                snapshotFlow { lazyListState.isScrollInProgress }
                    .distinctUntilChanged()
                    .collectLatest { inProgress ->
                        if (!inProgress) {
                            delay(100)
                            Logd(TAG, "DisposableEffect lazyListState subscribeCurVM")
                            subscribeCurVM()
                        }
                    }
            }
            onDispose { job.cancel() }
        }
        var forceRecomposeKey by remember { mutableIntStateOf(0) }
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
                        rememberPlayedIds = false
                        scope.launch(Dispatchers.IO) {
                            if (playedIds.isNotEmpty()) {
                                val playedVMs = mutableListOf<EpisodeVM>()
                                for (id in playedIds) {
                                    val ind = vms.vmIndexWithId(id)
                                    if (ind >= 0) playedVMs.add(vms[ind])
                                }
                                if (playedVMs.isNotEmpty()) {
                                    for (vm in playedVMs) {
                                        vm.updateVMFromDB()
                                        withContext(Dispatchers.Main) { vm.actionButton.update(vm.episode) }
                                    }
                                }
                                playedIds.clear()
                            }
                        }
                    }
                    Lifecycle.Event.ON_STOP -> rememberPlayedIds = true
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                //                Logd(TAG, "DisposableEffect lifecycleOwner onDispose")
                unsubscribeCurVM()
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        @Composable
        fun MainRow(vm: EpisodeVM, index: Int, isBeingDragged: Boolean, yOffset: Float, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
            val titleMaxLines = if (layoutMode == LayoutMode.Normal.ordinal) if (showComment) 1 else 2 else 3
            @Composable
            fun TitleColumn(vm: EpisodeVM, index: Int, modifier: Modifier) {
                Column(modifier.padding(start = 6.dp, end = 6.dp).combinedClickable(
                    onClick = {
                        Logd(TAG, "clicked: ${vm.episode.title}")
                        if (selectMode) toggleSelected(vm)
                        else {
                            episodeOnDisplay = vm.episode
                            navController.navigate(Screens.EpisodeInfo.name)
                        }
                    },
                    onLongClick = {
                        selectMode = !selectMode
                        vm.isSelected = selectMode
                        selected.clear()
                        if (selectMode) {
                            selected.add(vms[index])
                            longPressIndex = index
                        } else {
                            selectedSize = 0
                            longPressIndex = -1
                        }
                        Logd(TAG, "long clicked: ${vm.episode.title}")
                    })) {
                    Text(vm.episode.title ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
                    @Composable
                    fun Comment() {
                        val comment = remember { stripDateTimeLines(vm.episode.comment).replace("\n", "  ") }
                        Text(comment, color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    @Composable
                    fun StatusRow() {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = ImageVector.vectorResource(EpisodeState.fromCode(vm.playedState).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (vm.playedState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                            if (vm.ratingState != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(vm.ratingState).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                            if (vm.hasComment) Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_comment_24), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "comment", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                            if (vm.episode.getMediaType() == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                            val dateSizeText = remember {
                                " · " + formatDateTimeFlex(vm.episode.getPubDate()) + " · " + getDurationStringLong(vm.durationState) +
                                        (if (vm.episode.size > 0) " · " + Formatter.formatShortFileSize(context, vm.episode.size) else "") +
                                        (if (vm.viewCount > 0) " · " + formatLargeInteger(vm.viewCount) else "") +
                                        (if (vm.likeCount > 0) " · " + formatLargeInteger(vm.likeCount) else "")
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
                                Icon(imageVector = ImageVector.vectorResource(EpisodeState.fromCode(vm.playedState).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (vm.playedState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                                if (vm.ratingState != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(vm.ratingState).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                                if (vm.episode.getMediaType() == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                                val dateSizeText = remember { " · " + getDurationStringLong(vm.durationState) + (if (vm.episode.size > 0) " · " + Formatter.formatShortFileSize(context, vm.episode.size) else "") }
                                Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val dateSizeText = remember { formatDateTimeFlex(vm.episode.getPubDate()) }
                                Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (vm.viewCount > 0) {
                                    val viewText = remember { " · " + formatLargeInteger(vm.viewCount) }
                                    Text(viewText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_people_alt_24), tint = textColor, contentDescription = "people", modifier = Modifier.width(16.dp).height(16.dp))
                                }
                                if (vm.likeCount > 0) {
                                    val likeText = remember { " · " + formatLargeInteger(vm.likeCount) }
                                    Text(likeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_thumb_up_24), tint = textColor, contentDescription = "likes", modifier = Modifier.width(16.dp).height(16.dp))
                                }
                            }
                        }
                        LayoutMode.FeedTitle.ordinal -> {
                            Text(vm.episode.feed?.title ?: "", color = textColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (showComment) Comment()
                            else StatusRow()
                        }
                    }
                    when (vm.playedState) {
                        EpisodeState.AGAIN.code, EpisodeState.LATER.code -> {
                            val dueText = remember { if (vm.episode.repeatTime > 0) "D:" + formatDateTimeFlex(vm.episode.dueDate) else "" }
                            if (dueText.isNotBlank()) {
                                val bgColor = if (localTime > vm.episode.repeatTime) Color.Cyan else MaterialTheme.colorScheme.surface
                                Text(dueText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(bgColor))
                            }
                        }
                        EpisodeState.SKIPPED.code -> {
                            val playedText = if (vm.episode.lastPlayedTime > 0) "P:" + formatDateTimeFlex(vm.episode.lastPlayedDate) else ""
                            val completionText = if (vm.episode.playbackCompletionTime > 0) " · C:" + formatDateTimeFlex(vm.episode.playbackCompletionDate) else ""
                            val stateSetText = if (vm.episode.playStateSetTime > 0) " · S:" + formatDateTimeFlex(vm.episode.playStateSetDate) else ""
                            val durationText = if (vm.episode.playedDuration > 0) " · " + getDurationStringLong(vm.episode.playedDuration) else ""
                            val dateSizeText = remember { playedText + completionText + stateSetText + durationText }
                            Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        else -> {}
                    }
                }
            }

            val density = LocalDensity.current
            val imageWidth = if (layoutMode == LayoutMode.WideImage.ordinal) 150.dp else 56.dp
            val imageHeight = if (layoutMode == LayoutMode.WideImage.ordinal) 100.dp else 56.dp
            Row(Modifier.background(if (vm.isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface).offset(y = with(density) { yOffset.toDp() })) {
                if (isDraggable) {
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(R.attr.dragview_background, typedValue, true)
                    Icon(imageVector = ImageVector.vectorResource(typedValue.resourceId), tint = buttonColor, contentDescription = "drag handle", modifier = Modifier.width(50.dp).align(Alignment.CenterVertically).padding(start = 10.dp, end = 15.dp).draggable(orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> onDrag(delta) }, onDragStarted = { onDragStart() }, onDragStopped = { onDragEnd() } ))
                }
                if (showCoverImage) {
                    val imgLoc = remember(vm.episode) { vm.episode.imageLocation(forceFeedImage) }
                    AsyncImage(model = ImageRequest.Builder(context).data(imgLoc).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(), contentDescription = "imgvCover",
                        modifier = Modifier.width(imageWidth).height(imageHeight)
                            .clickable(onClick = {
                                Logd(TAG, "icon clicked!")
                                when {
                                    selectMode -> toggleSelected(vm)
                                    vm.episode.feed != null && vm.episode.feed?.isSynthetic() != true -> {
                                        feedOnDisplay = vm.episode.feed!!
                                        feedScreenMode = FeedScreenMode.Info
                                        navController.navigate(Screens.FeedDetails.name)
                                    }
                                    else -> {
                                        episodeOnDisplay = vm.episode
                                        navController.navigate(Screens.EpisodeInfo.name)
                                    }
                                }
                            }))
                }
                Box(Modifier.weight(1f).wrapContentHeight()) {
                    TitleColumn(vm, index, modifier = Modifier.fillMaxWidth())
                    if (showActionButtons) {
                        if (actionButton_ == null) {
                            LaunchedEffect(playerStat) {
                                Logd(TAG, "LaunchedEffect playerStat: $playerStat")
                                if (vm.episode.id == curMediaId) {
                                    if (playerStat == PLAYER_STATUS_PLAYING) {
                                        vm.actionButton.type = ButtonTypes.PAUSE
                                        subscribeCurVM()
                                    }
                                    else vm.actionButton.update(vm.episode)
                                } else if (vm.actionButton.type == ButtonTypes.PAUSE) vm.actionButton.update(vm.episode)
                            }
                        } else LaunchedEffect(Unit) { vm.actionButton = actionButton_(vm.episode) }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).padding(end = 10.dp).align(Alignment.BottomEnd).pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { vms[index].showAltActionsDialog = true },
                                onTap = {
                                    val actType = vm.actionButton.type
                                    //                                vm.actionButton.update(vm.episode)
                                    vm.actionButton.onClick(activity)
                                    actionButtonCB?.invoke(vm.episode, actType)
                                }) }) {
//                            var dlStats by remember { mutableStateOf< DownloadStatus?>(null) }
//                            LaunchedEffect(downloadStates.size) { dlStats = downloadStates[vm.episode.downloadUrl] }
                            val dlStats = downloadStates[vm.episode.downloadUrl]
                            if (dlStats != null) {
                                Logd(TAG, "${vm.episode.id} dlStats: ${dlStats?.progress} ${dlStats?.state}")
                                vm.actionButton.processing = dlStats!!.progress
                                when (dlStats!!.state) {
                                    DownloadStatus.State.COMPLETED.ordinal -> runOnIOScope { vm.updateVMFromDB() }
                                    DownloadStatus.State.INCOMPLETE.ordinal -> vm.actionButton.type = ButtonTypes.DOWNLOAD
                                    else -> {}
                                }
                            }
                            Icon(imageVector = ImageVector.vectorResource(vm.actionButton.drawable), tint = buttonColor, contentDescription = null, modifier = Modifier.size(33.dp))
                            if (vm.actionButton.processing > -1) CircularProgressIndicator(progress = { 0.01f * vm.actionButton.processing }, strokeWidth = 4.dp, color = textColor, modifier = Modifier.size(37.dp).offset(y = 4.dp))
                        }
                        if (vm.showAltActionsDialog) vm.actionButton.AltActionsDialog(activity, onDismiss = { vm.showAltActionsDialog = false })
                    }
                }
            }
        }

        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vms, key = { _, vm -> vm.episode.id to forceRecomposeKey }) { index, vm ->
                val velocityTracker = remember { VelocityTracker() }
                val offsetX = remember { Animatable(0f) }
                Box(modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { velocityTracker.resetTracking() },
                        onHorizontalDrag = { change, dragAmount ->
                            //                            Logd(TAG, "onHorizontalDrag $dragAmount")
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        },
                        onDragEnd = {
                            scope.launch {
                                val velocity = velocityTracker.calculateVelocity().x
                                if (velocity > 1000f || velocity < -1000f) {
                                    if (velocity > 0) rightSwipeCB?.invoke(vm)
                                    else leftSwipeCB?.invoke(vm)
                                }
                                offsetX.animateTo(targetValue = 0f, animationSpec = tween(500))
                            }
                        }
                    )
                }.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
                    LaunchedEffect(key1 = selectMode, key2 = selectedSize) {
                        vm.isSelected = selectMode && vm in selected
                        //                        Logd(TAG, "LaunchedEffect $index ${vm.isSelected} ${selected.size}")
                    }
                    Column {
                        var yOffset by remember { mutableFloatStateOf(0f) }
                        var draggedIndex by remember { mutableStateOf<Int?>(null) }
                        MainRow(vm, index,
                            isBeingDragged = draggedIndex == index,
                            yOffset = if (draggedIndex == index) yOffset else 0f,
                            onDragStart = { draggedIndex = index },
                            onDrag = { delta -> yOffset += delta },
                            onDragEnd = {
                                draggedIndex?.let { startIndex ->
                                    val newIndex = (startIndex + (yOffset / rowHeightPx).toInt()).coerceIn(0, vms.lastIndex)
                                    Logd(TAG, "onDragEnd draggedIndex: $draggedIndex newIndex: $newIndex")
                                    if (newIndex != startIndex) {
                                        dragCB?.invoke(startIndex, newIndex)
                                        //                                        val item = vms.removeAt(startIndex)
                                        //                                        vms.add(newIndex, item)c
                                    }
                                }
                                draggedIndex = null
                                yOffset = 0f
                            })
                        if (showActionButtons && (vm.inProgressState || curVM?.episode?.id == vm.episode.id)) {
                            fun calcProg(): Float {
                                val pos = vm.positionState
                                val dur = vm.episode.duration
                                return if (dur > 0 && pos >= 0 && dur >= pos) 1f * pos / dur else 0f
                            }
                            val prog by remember(vm.episode.id, vm.positionState) { mutableFloatStateOf(calcProg()) }
                            val posText by remember(vm.episode.id, vm.positionState) { mutableStateOf(getDurationStringLong(vm.positionState)) }
                            val durText = remember(vm.episode.id) { getDurationStringLong(vm.episode.duration) }
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
                        if (eList.isEmpty()) for (i in 0..longPressIndex) selected.add(vms[i])
                        else selected.addAll(eList)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_downward_24), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                    .clickable(onClick = {
                        selected.clear()
                        val eList = multiSelectCB(longPressIndex, 1)
                        if (eList.isEmpty()) for (i in longPressIndex..<vms.size) selected.add(vms[i])
                        else selected.addAll(eList)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                var selectAllRes by remember { mutableIntStateOf(R.drawable.ic_select_all) }
                Icon(imageVector = ImageVector.vectorResource(selectAllRes), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                    .clickable(onClick = {
                        if (selectedSize != vms.size) {
                            selected.clear()
                            val eList = multiSelectCB(longPressIndex, 0)
                            if (eList.isEmpty()) for (vm in vms) selected.add(vm)
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
