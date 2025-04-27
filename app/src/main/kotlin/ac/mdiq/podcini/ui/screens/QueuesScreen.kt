package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.mediaBrowser
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.storage.database.Episodes.indexOfItemWithDownloadUrl
import ac.mdiq.podcini.storage.database.Episodes.indexOfItemWithId
import ac.mdiq.podcini.storage.database.Queues.clearQueue
import ac.mdiq.podcini.storage.database.Queues.isQueueKeepSorted
import ac.mdiq.podcini.storage.database.Queues.moveInQueueSync
import ac.mdiq.podcini.storage.database.Queues.queueKeepSortedOrder
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.SpinnerExternalSet
import ac.mdiq.podcini.ui.compose.buildListInfo
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logt
import android.content.ComponentName
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.NumberFormat
import kotlin.math.max

class QueuesVM(val context: Context, val lcScope: CoroutineScope) {
    internal var swipeActions: SwipeActions
    internal var swipeActionsBin: SwipeActions

    internal var infoTextUpdate = ""
    internal var listInfoText = ""
    internal var infoBarText = mutableStateOf("")

    internal var isQueueLocked by mutableStateOf(getPref(AppPrefs.prefQueueLocked, true))

    internal var queueNames = mutableStateListOf<String>()
    internal val spinnerTexts = mutableStateListOf<String>()
    internal var curIndex by mutableIntStateOf(0)
    internal var queues: List<PlayQueue>

    internal var displayUpArrow = false
    internal val queueItems = mutableListOf<Episode>()
    internal val vms = mutableStateListOf<EpisodeVM>()
    internal var feedsAssociated = listOf<Feed>()

    internal var showBin by mutableStateOf(false)
    internal var showFeeds by mutableStateOf(false)
    internal var dragDropEnabled by mutableStateOf(!(isQueueKeepSorted || isQueueLocked))
    var showSortDialog by mutableStateOf(false)
    var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)

    internal val showClearQueueDialog = mutableStateOf(false)
    internal val showRenameQueueDialog = mutableStateOf(false)
    internal val showAddQueueDialog = mutableStateOf(false)

    internal lateinit var browserFuture: ListenableFuture<MediaBrowser>

    init {
        queues = realm.query(PlayQueue::class).find()

        swipeActions = SwipeActions(context, TAG)
        swipeActionsBin = SwipeActions(context, "$TAG.Bin")
    }
    private var eventSink: Job?     = null
    private var eventStickySink: Job? = null
    private var eventKeySink: Job?     = null
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
        eventKeySink?.cancel()
        eventKeySink = null
    }
    internal fun procFlowEvents() {
        if (eventSink == null) eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.QueueEvent -> onQueueEvent(event)
                    is FlowEvent.FeedChangeEvent -> onFeedPrefsChanged(event)
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lcScope.launch {
            EventFlow.stickyEvents.drop(1).collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
                    is FlowEvent.FeedUpdatingEvent -> {
                        infoTextUpdate = if (event.isRunning) "U" else ""
                        infoBarText.value = "$listInfoText $infoTextUpdate"
                    }
                    else -> {}
                }
            }
        }
        if (eventKeySink == null) eventKeySink = lcScope.launch {
            EventFlow.keyEvents.collectLatest { event ->
                Logd(TAG, "Received key event: $event, Ignored!")
//                onKeyUp(event)
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        Logd(TAG, "onQueueEvent() called with ${event.action.name}")
        if (showBin) return
        when (event.action) {
            FlowEvent.QueueEvent.Action.ADDED -> {
                if (event.episodes.isNotEmpty() && !curQueue.contains(event.episodes[0])) {
                    queueItems.addAll(event.episodes)
                    for (e in event.episodes) vms.add(EpisodeVM(e, TAG))
                }
            }
            FlowEvent.QueueEvent.Action.SET_QUEUE, FlowEvent.QueueEvent.Action.SORTED -> {
                queueItems.clear()
                queueItems.addAll(event.episodes)
//                stopMonitor(vms)
                vms.clear()
                for (e in event.episodes) vms.add(EpisodeVM(e, TAG))
            }
            FlowEvent.QueueEvent.Action.REMOVED, FlowEvent.QueueEvent.Action.IRREVERSIBLE_REMOVED -> {
                if (event.episodes.isNotEmpty()) {
                    for (e in event.episodes) {
                        val pos: Int = queueItems.indexOfItemWithId(e.id)
                        if (pos < 0) continue
                        Logd(TAG, "removing episode $pos ${queueItems[pos].title}")
                        if (pos < vms.size) {
                            Logd(TAG, "vms at $pos ${vms[pos].episode.title}")
                            if (vms[pos].episode.id == e.id) {
//                                vms[pos].stopMonitoring()
                                vms.removeAt(pos)
                            }
                        }
                        queueItems.removeAt(pos)
                    }
                }
            }
            FlowEvent.QueueEvent.Action.SWITCH_QUEUE -> {
                loadCurQueue(false)
                playbackService?.notifyCurQueueItemsChanged(event.episodes.size)
            }
            FlowEvent.QueueEvent.Action.CLEARED -> {
                queueItems.clear()
//                stopMonitor(vms)
                vms.clear()
            }
            FlowEvent.QueueEvent.Action.MOVED, FlowEvent.QueueEvent.Action.DELETED_MEDIA -> return
        }
        queues = realm.query(PlayQueue::class).find()
        queueNames = queues.map { it.name }.toMutableStateList()
        curIndex = queues.indexOfFirst { it.id == curQueue.id }
        spinnerTexts.clear()
        spinnerTexts.addAll(queues.map { "${it.name} : ${it.size()}" })
        listInfoText = buildListInfo(queueItems)
        infoBarText.value = "$listInfoText $infoTextUpdate"
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        if (loadItemsRunning) return
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = queueItems.indexOfItemWithDownloadUrl(url)
            if (pos >= 0 && pos < vms.size) vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
        }
    }

    private fun onFeedPrefsChanged(event: FlowEvent.FeedChangeEvent) {
        Logd(TAG,"speedPresetChanged called")
        for (item in queueItems) if (item.feed?.id == event.feed.id) item.feed = null
    }

    private var loadItemsRunning = false
    internal fun loadCurQueue(restoreScrollPosition: Boolean) {
        if (!loadItemsRunning) {
            loadItemsRunning = true
            Logd(TAG, "loadCurQueue() called ${curQueue.name}")
            while (curQueue.name.isEmpty()) runBlocking { delay(100) }
            feedsAssociated = realm.query(Feed::class).query("queueId == ${curQueue.id}").find()
            queueItems.clear()
//            stopMonitor(vms)
            vms.clear()
            if (showBin) queueItems.addAll(realm.query(Episode::class, "id IN $0", curQueue.idsBinList).find().sortedByDescending { curQueue.idsBinList.indexOf(it.id) })
            else {
                curQueue.episodes.clear()
                queueItems.addAll(curQueue.episodes)
            }
            val tag = if (showBin) TAG+"bin" else TAG
            for (e in queueItems) vms.add(EpisodeVM(e, tag))
            Logd(TAG, "loadCurQueue() curQueue.episodes: ${curQueue.episodes.size}")
            queues = realm.query(PlayQueue::class).find()
            curIndex = queues.indexOfFirst { it.id == curQueue.id }
            spinnerTexts.clear()
            spinnerTexts.addAll(queues.map { "${it.name} : ${it.size()}" })
            listInfoText = buildListInfo(queueItems)
            infoBarText.value = "$listInfoText $infoTextUpdate"
            loadItemsRunning = false
        }
    }
    /**
     * Sort the episodes in the queue with the given the named sort order.
     * @param broadcastUpdate `true` if this operation should trigger a
     * QueueUpdateBroadcast. This option should be set to `false`
     * if the caller wants to avoid unexpected updates of the GUI.
     */
    internal fun reorderQueue(sortOrder: EpisodeSortOrder?, broadcastUpdate: Boolean) : Job {
        Logd(TAG, "reorderQueue called")
        if (sortOrder == null) {
            Logd(TAG, "reorderQueue() - sortOrder is null. Do nothing.")
            return Job()
        }
        val permutor = getPermutor(sortOrder)
        return runOnIOScope {
            permutor.reorder(curQueue.episodes)
            val episodes_ = curQueue.episodes.toMutableList()
            curQueue = upsert(curQueue) {
                it.episodeIds.clear()
                for (e in episodes_) it.episodeIds.add(e.id)
                it.update()
            }
            if (broadcastUpdate) EventFlow.postEvent(FlowEvent.QueueEvent.sorted(curQueue.episodes))
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun QueuesScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { QueuesVM(context, scope) }

//        val displayUpArrow by remember { derivedStateOf { navController.backQueue.size > 1 } }
//        var upArrowVisible by rememberSaveable { mutableStateOf(displayUpArrow) }
//        LaunchedEffect(navController.backQueue) { upArrowVisible = displayUpArrow }

//    var displayUpArrow by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    if (isQueueKeepSorted) vm.sortOrder = queueKeepSortedOrder ?: EpisodeSortOrder.DATE_NEW_OLD

                    vm.queueNames = vm.queues.map { it.name }.toMutableStateList()
                    vm.spinnerTexts.clear()
                    vm.spinnerTexts.addAll(vm.queues.map { "${it.name} : ${it.size()}" })
                    lifecycleOwner.lifecycle.addObserver(vm.swipeActions)
                    lifecycleOwner.lifecycle.addObserver(vm.swipeActionsBin)
//                    lifecycleOwner.lifecycle.addObserver(vm.swipeActions)
                }
                Lifecycle.Event.ON_START -> {
                    vm.loadCurQueue(true)
                    vm.procFlowEvents()
                    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    vm.browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
                    vm.browserFuture.addListener({
                        // here we can get the root of media items tree or we can get also the children if it is an album for example.
                        mediaBrowser = vm.browserFuture.get()
                        mediaBrowser?.subscribe("CurQueue", null)
                    }, MoreExecutors.directExecutor())
                }
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {
                    vm.cancelFlowEvents()
                    mediaBrowser?.unsubscribe("CurQueue")
                    mediaBrowser = null
                    MediaBrowser.releaseFuture(vm.browserFuture)
                }
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.queueItems.clear()
//            stopMonitor(vm.vms)
            vm.vms.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showBinLimitDialog by remember { mutableStateOf(false) }
    if (showBinLimitDialog) {
        var limitString by remember { mutableStateOf((curQueue.binLimit).toString()) }
        AlertDialog(modifier = Modifier.fillMaxWidth().padding(10.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showBinLimitDialog = false },
            text = { TextField(value = limitString, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("bin limit") }, singleLine = true,
                    onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) limitString = it }) },
            confirmButton = {
                TextButton(onClick = {
                    curQueue = upsertBlk(curQueue) { it.binLimit = limitString.toInt() }
                    showBinLimitDialog = false
                }) { Text(stringResource(R.string.confirm_label)) }
            },
            dismissButton = { TextButton(onClick = { showBinLimitDialog = false }) { Text(stringResource(R.string.cancel_label)) } } )
    }

    var showTopSpinner by remember { mutableStateOf(!vm.showBin) }
    var title by remember { mutableStateOf(if (vm.showBin) curQueue.name + " Bin" else "") }

    fun refreshQueueOrBin() {
        showTopSpinner = !vm.showBin
        title = if (vm.showBin) curQueue.name + " Bin" else ""
        vm.loadCurQueue(false)
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        var showRename by remember { mutableStateOf(curQueue.name != "Default") }
        TopAppBar(title = {
            if (showTopSpinner) SpinnerExternalSet(items = vm.spinnerTexts, selectedIndex = vm.curIndex) { index: Int ->
                Logd(TAG, "Queue selected: ${vm.queues[index].name}")
                val prevQueueSize = curQueue.size()
                curQueue = upsertBlk(vm.queues[index]) { it.update() }
                showRename = curQueue.name != "Default"
                vm.loadCurQueue(true)
                playbackService?.notifyCurQueueItemsChanged(max(prevQueueSize, curQueue.size()))
            } else Text(title) },
            navigationIcon = if (vm.displayUpArrow) {
                { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { MainActivity.openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_play), contentDescription = "Open Drawer") } }
            },
            actions = {
                val binIconRes by remember(vm.showBin) { derivedStateOf { if (vm.showBin) R.drawable.playlist_play else R.drawable.ic_history } }
                val feedsIconRes by remember(vm.showFeeds) { derivedStateOf { if (vm.showFeeds) R.drawable.playlist_play else R.drawable.baseline_dynamic_feed_24 } }
                if (!vm.showFeeds) IconButton(onClick = {
                    vm.showBin = !vm.showBin
                    refreshQueueOrBin()
                }) { Icon(imageVector = ImageVector.vectorResource(binIconRes), contentDescription = "bin") }
                if (!vm.showBin) {
                    IconButton(onClick = { vm.showFeeds = !vm.showFeeds }) { Icon(imageVector = ImageVector.vectorResource(feedsIconRes), contentDescription = "feeds") }
                    IconButton(onClick = { mainNavController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.bin_limit) + ": ${curQueue.binLimit}") }, onClick = {
                        showBinLimitDialog = true
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_bin_label)) }, onClick = {
                        curQueue = upsertBlk(curQueue) {
                            it.idsBinList.clear()
                            it.update()
                        }
                        if (vm.showBin) vm.loadCurQueue(false)
                        expanded = false
                    })
                    if (!vm.showBin) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort)) }, onClick = {
                            vm.showSortDialog = true
                            expanded = false
                        })
                        if (showRename) DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                            vm.showRenameQueueDialog.value = true
                            expanded = false
                        })
                        if (vm.queueNames.size < 12) DropdownMenuItem(text = { Text(stringResource(R.string.add_queue)) }, onClick = {
                            vm.showAddQueueDialog.value = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_queue_label)) }, onClick = {
                            vm.showClearQueueDialog.value = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                            FeedUpdateManager.runOnceOrAsk(context)
                            expanded = false
                        })
                        if (!isQueueKeepSorted) {
                            fun toggleQL() {
                                vm.isQueueLocked = !vm.isQueueLocked
                                putPref(AppPrefs.prefQueueLocked, vm.isQueueLocked)
                                vm.dragDropEnabled = !(isQueueKeepSorted || vm.isQueueLocked)
                                Logt(TAG, context.getString(if (vm.isQueueLocked) R.string.queue_locked else R.string.queue_unlocked))
                                expanded = false
                            }
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.lock_queue))
                                    Checkbox(checked = vm.isQueueLocked, onCheckedChange = { toggleQL() })
                                }
                            }, onClick = { toggleQL() })
                        }
                    }
                }
            }
        )
    }

    @Composable
    fun RenameQueueDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var newName by remember { mutableStateOf(curQueue.name) }
                        TextField(value = newName, onValueChange = { newName = it }, label = { Text("Rename (Unique name only)") })
                        Button(onClick = {
                            if (newName.isNotEmpty() && curQueue.name != newName && vm.queueNames.indexOf(newName) < 0) {
                                val oldName = curQueue.name
                                curQueue = upsertBlk(curQueue) { it.name = newName }
                                val index_ = vm.queueNames.indexOf(oldName)
                                vm.queueNames[index_] = newName
                                vm.spinnerTexts[index_] = newName + " : " + curQueue.episodeIds.size
                                onDismiss()
                            }
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }
    }

    @Composable
    fun AddQueueDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var newName by remember { mutableStateOf("") }
                        TextField(value = newName, onValueChange = { newName = it }, label = { Text("Add queue (Unique name only)") })
                        Button(onClick = {
                            if (newName.isNotEmpty() && vm.queueNames.indexOf(newName) < 0) {
                                val newQueue = PlayQueue()
                                newQueue.id = vm.queueNames.size.toLong()
                                newQueue.name = newName
                                upsertBlk(newQueue) {}
                                vm.queues = realm.query(PlayQueue::class).find()
                                vm.queueNames = vm.queues.map { it.name }.toMutableStateList()
                                vm.curIndex = vm.queues.indexOfFirst { it.id == curQueue.id }
                                vm.spinnerTexts.clear()
                                vm.spinnerTexts.addAll(vm.queues.map { "${it.name} : ${it.episodeIds.size}" })
                                onDismiss()
                            }
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }
    }

    ComfirmDialog(titleRes = R.string.clear_queue_label, message = stringResource(R.string.clear_queue_confirmation_msg), showDialog = vm.showClearQueueDialog) { clearQueue() }
    RenameQueueDialog(showDialog = vm.showRenameQueueDialog.value, onDismiss = { vm.showRenameQueueDialog.value = false })
    AddQueueDialog(showDialog = vm.showAddQueueDialog.value, onDismiss = { vm.showAddQueueDialog.value = false })
    vm.swipeActions.ActionOptionsDialog()
    vm.swipeActionsBin.ActionOptionsDialog()

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedsGrid() {
        val context = LocalContext.current
        val lazyGridState = rememberLazyGridState()
        LazyVerticalGrid(state = lazyGridState, columns = GridCells.Adaptive(80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)) {
            items(vm.feedsAssociated.size, key = {index -> vm.feedsAssociated[index].id}) { index ->
                val feed by remember { mutableStateOf(vm.feedsAssociated[index]) }
                ConstraintLayout {
                    val (coverImage, episodeCount, rating, _) = createRefs()
                    val imgLoc = remember(feed) { feed.imageUrl }
                    AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                        .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "coverImage",
                        modifier = Modifier.height(100.dp).aspectRatio(1f)
                            .constrainAs(coverImage) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                                start.linkTo(parent.start)
                            }.combinedClickable(onClick = {
                                Logd(TAG, "clicked: ${feed.title}")
                                feedOnDisplay = feed
                                feedScreenMode = FeedScreenMode.List
                                mainNavController.navigate(Screens.FeedDetails.name)
                            }, onLongClick = { Logd(TAG, "long clicked: ${feed.title}") })
                    )
                    Text(NumberFormat.getInstance().format(feed.episodes.size.toLong()), color = Color.Green,
                        modifier = Modifier.background(Color.Gray).constrainAs(episodeCount) {
                            end.linkTo(parent.end)
                            top.linkTo(coverImage.top)
                        })
                    if (feed.rating != Rating.UNRATED.code)
                        Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).constrainAs(rating) {
                                start.linkTo(parent.start)
                                centerVerticallyTo(coverImage)
                            })
                }
            }
        }
    }

//    fun multiSelectCB(index: Int, aboveOrBelow: Int): List<Episode> {
//        return when (aboveOrBelow) {
//            0 -> vm.queueItems
//            -1 -> if (index < vm.queueItems.size) vm.queueItems.subList(0, index+1) else vm.queueItems
//            1 -> if (index < vm.queueItems.size) vm.queueItems.subList(index, vm.queueItems.size) else vm.queueItems
//            else -> listOf()
//        }
//    }

    BackHandler(enabled = vm.showBin || vm.showFeeds) {
        Logd(TAG, "BackHandler ${vm.showBin} ${vm.showFeeds}")
        if (vm.showBin) {
            vm.showBin = false
            refreshQueueOrBin()
        } else if (vm.showFeeds) vm.showFeeds = false
    }

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        if (vm.showBin) {
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                InforBar(vm.infoBarText, vm.swipeActionsBin)
                EpisodeLazyColumn(context as MainActivity, vms = vm.vms, doMonitor = true, swipeActions = vm.swipeActionsBin)
            }
        } else {
            if (vm.showFeeds) Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) { FeedsGrid() }
            else {
                Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    if (vm.showSortDialog) EpisodeSortDialog(initOrder = vm.sortOrder, showKeepSorted = true, onDismissRequest = { vm.showSortDialog = false }) { sortOrder, keep ->
                        if (sortOrder != EpisodeSortOrder.RANDOM && sortOrder != EpisodeSortOrder.RANDOM1) isQueueKeepSorted = keep
                        queueKeepSortedOrder = sortOrder
                        vm.reorderQueue(sortOrder, true)
                    }
                    InforBar(vm.infoBarText, vm.swipeActions)
                    EpisodeLazyColumn(context as MainActivity, vms = vm.vms, doMonitor = true,
                        isDraggable = vm.dragDropEnabled, dragCB = { iFrom, iTo -> runOnIOScope { moveInQueueSync(iFrom, iTo, true) } },
                        swipeActions = vm.swipeActions,
                    )
                }
            }
        }
    }
}


//    @SuppressLint("RestrictedApi")
//    private fun onKeyUp(event: KeyEvent) {
//        if (!isAdded || !isVisible || !isMenuVisible) return
//        when (event.keyCode) {
////            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
////            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(adapter!!.itemCount - 1)
//            else -> {}
//        }
//    }

private val TAG = Screens.Queues.name

private const val KEY_UP_ARROW = "up_arrow"
private const val PREFS = "QueueFragment"
//private const val PREF_SHOW_LOCK_WARNING = "show_lock_warning"


