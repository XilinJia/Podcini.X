package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.automation.autodownloadForQueue
import ac.mdiq.podcini.automation.autoenqueueForQueue
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.mediaBrowser
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.buildListInfo
import ac.mdiq.podcini.storage.database.feedOperationText
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.resetIds
import ac.mdiq.podcini.storage.database.resetInQueueTime
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.setPlayState
import ac.mdiq.podcini.storage.database.stateToPreserve
import ac.mdiq.podcini.storage.database.trimBin
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.VIRTUAL_QUEUE_ID
import ac.mdiq.podcini.storage.specs.EnqueueLocation
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.sortPairOf
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.NumberEditor
import ac.mdiq.podcini.ui.compose.SpinnerExternalSet
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchRow
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
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
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

private val TAG = Screens.Queues.name

private const val QUEUES_LIMIT = 15

enum class QueuesScreenMode {
    Queue,
    Bin,
    Feed,
    Settings
}

private var queuesMode by mutableStateOf(QueuesScreenMode.Queue)

var curQueue by mutableStateOf(PlayQueue())

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun QueuesScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = LocalNavController.current

    var browserFuture: ListenableFuture<MediaBrowser>? = remember { null }
    
    val feedsAssociated = remember {  mutableStateListOf<Feed>() }

    var swipeActions by remember { mutableStateOf(SwipeActions(context, TAG)) }
    var listInfoText by remember { mutableStateOf("") }
    val infoBarText = remember { mutableStateOf("") }

    var showSortDialog by remember { mutableStateOf(false) }
    val showClearQueueDialog = remember { mutableStateOf(false) }
    val showAddQueueDialog = remember { mutableStateOf(false) }

    var queuesFlow by remember { mutableStateOf<Flow<ResultsChange<PlayQueue>>>(emptyFlow()) }
    var curIndex by remember {  mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    queuesFlow = realm.query<PlayQueue>().asFlow()
                    lifecycleOwner.lifecycle.addObserver(swipeActions)
                    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
                    browserFuture.addListener({
                        // here we can get the root of media items tree or we can get also the children if it is an album for example.
                        mediaBrowser = browserFuture.get()
                        mediaBrowser?.subscribe("ActQueue", null)
                    }, MoreExecutors.directExecutor())
                }
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            mediaBrowser?.unsubscribe("ActQueue")
            mediaBrowser = null
            if (browserFuture != null) MediaBrowser.releaseFuture(browserFuture)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = subscreenHandleBack.value) {
        Logd(TAG, "BackHandler $queuesMode")
        when(queuesMode) {
            QueuesScreenMode.Bin, QueuesScreenMode.Feed, QueuesScreenMode.Settings -> queuesMode = QueuesScreenMode.Queue
            else -> {}
        }
    }

    var episodesFlow by remember { mutableStateOf<Flow<ResultsChange<Episode>>>(emptyFlow()) }

    var dragged by remember { mutableIntStateOf(0) }

    var title by remember { mutableStateOf(if (queuesMode == QueuesScreenMode.Bin) curQueue.name + " Bin" else "") }

    val queuesResults by queuesFlow.collectAsState(initial = null)
    val queues = queuesResults?.list ?: emptyList()

    val queueNames = remember(queues) { queues.map { it.name } }
    val spinnerTexts = remember(queues) { queues.map { "${if (it.id == actQueue.id) "> " else ""}${it.name} : ${it.size()}" } }
    var actIndex by remember(queues) {  mutableIntStateOf(queues.indexOfFirst { it.id == actQueue.id } ) }

    LaunchedEffect(queues) { curIndex = queues.indexOfFirst { it.id == appAttribs.curQueueId }  }
    LaunchedEffect(curIndex, queues) { if (curIndex >= 0 && queues.isNotEmpty()) curQueue = queues[curIndex] }

    var dragDropEnabled by remember(curQueue.id) { mutableStateOf(!(curQueue.isSorted || curQueue.isSorted)) }

    DisposableEffect(queuesMode) {
        subscreenHandleBack.value = queuesMode != QueuesScreenMode.Queue
        onDispose { subscreenHandleBack.value = false }
    }

    LaunchedEffect(curQueue, queuesFlow, queuesMode, dragged) {
        Logd(TAG, "LaunchedEffect(curQueue, queuesFlow, screenMode, dragged)")
        lifecycleOwner.lifecycle.removeObserver(swipeActions)
        when (queuesMode) {
            QueuesScreenMode.Feed -> {
                feedsAssociated.clear()
                feedsAssociated.addAll(realm.query(Feed::class).query("queueId == ${curQueue.id}").find())
                Logd(TAG, "feedsAssociated: ${feedsAssociated.size} ${curQueue.id}")
                title = "${feedsAssociated.size} Feeds"
            }
            QueuesScreenMode.Bin -> {
                swipeActions = SwipeActions(context, "${TAG}_Bin")
                lifecycleOwner.lifecycle.addObserver(swipeActions)
                episodesFlow = queuesFlow.mapNotNull { resultsChange -> resultsChange.list.firstOrNull { it.id == curQueue.id }?.idsBinList
                }.flatMapLatest { ids ->
                    realm.query(Episode::class, "id IN $0", ids).sort(Pair("timeOutQueue", Sort.DESCENDING)).asFlow()
                }
                title = curQueue.name + " Bin"
            }
            QueuesScreenMode.Queue -> {
                swipeActions = SwipeActions(context, TAG)
                lifecycleOwner.lifecycle.addObserver(swipeActions)
                episodesFlow = queuesFlow.mapNotNull { resultsChange -> resultsChange.list.firstOrNull { it.id == curQueue.id }?.episodeIds
                }.flatMapLatest { ids ->
                    realm.query(Episode::class).query("id IN $0", ids).sort(sortPairOf(curQueue.sortOrder)).asFlow()
                }
                title = ""
            }
            else -> {
                title = "Settings"
            }
        }
    }

    @Composable
    fun OpenDialogs() {
        fun clearQueue() : Job {
            Logd(TAG, "clearQueue called")
            return runOnIOScope {
                curQueue = upsert(curQueue) {
                    it.idsBinList.addAll(it.episodeIds)
                    trimBin(it)
                    it.episodeIds.clear()
                    it.update()
                }
                val t = System.currentTimeMillis()
                var c = 0L
                for (e in curQueue.episodes) {
                    var e_ = e
                    if (e.playState < EpisodeState.SKIPPED.code && !stateToPreserve(e.playState))
                        e_ = setPlayState(EpisodeState.SKIPPED, e, false)
                    upsert(e_) { it.timeOutQueue = t+c++}
                }
                curQueue.episodes.clear()
                if (curQueue.id == actQueue.id) EventFlow.postEvent(FlowEvent.QueueEvent.cleared())
                if (!curQueue.isVirtual()) {
                    autoenqueueForQueue(curQueue)
                    if (curQueue.launchAutoEQDlWhenEmpty) autodownloadForQueue(getAppContext(), curQueue)
                }
            }
        }

        fun reorderQueue() {
            Logd(TAG, "reorderQueue called")
            runOnIOScope {
                val episodes_ = curQueue.episodes.toMutableList()
                getPermutor(curQueue.sortOrder).reorder(episodes_)
                curQueue = resetIds(curQueue, episodes_)
                curQueue.episodes.clear()
                if (curQueue.id == actQueue.id) EventFlow.postEvent(FlowEvent.QueueEvent.sorted(curQueue.episodes))
            }
        }

        ComfirmDialog(titleRes = R.string.clear_queue_label, message = stringResource(R.string.clear_queue_confirmation_msg), showDialog = showClearQueueDialog) { clearQueue() }

        if (showAddQueueDialog.value) Dialog(onDismissRequest = { showAddQueueDialog.value = false }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var newName by remember { mutableStateOf("") }
                    TextField(value = newName, onValueChange = { newName = it }, label = { Text("Add queue (Unique name only)") })
                    Button(onClick = {
                        if (newName.isNotEmpty() && queueNames.indexOf(newName) < 0) {
                            val newQueue = PlayQueue()
                            val maxId = queues.map { it.id }.filter { it < VIRTUAL_QUEUE_ID }.maxOrNull() ?: -1
                            newQueue.id = maxId + 1
                            newQueue.name = newName
                            upsertBlk(newQueue) {}
                            showAddQueueDialog.value = false
                        }
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }

        swipeActions.ActionOptionsDialog()
        if (showSortDialog) EpisodeSortDialog(initOrder = curQueue.sortOrder, onDismissRequest = { showSortDialog = false },
            includeConditionals = listOf(EpisodeSortOrder.TIME_IN_QUEUE_ASC, EpisodeSortOrder.TIME_IN_QUEUE_DESC)) { order ->
            curQueue = upsertBlk(curQueue) { it.sortOrder = order ?: EpisodeSortOrder.TIME_IN_QUEUE_ASC }
            reorderQueue()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        val buttonColor = Color(0xDDFFD700)
        Box {
            TopAppBar(title = {
                if (queuesMode == QueuesScreenMode.Queue) SpinnerExternalSet(items = spinnerTexts, selectedIndex = curIndex) { index: Int ->
                    Logd(TAG, "Queue selected: ${queues[index].name}")
                    curQueue = upsertBlk(queues[index]) { it.update() }
                    curIndex = queues.indexOfFirst { it.id == curQueue.id }
                    upsertBlk(appAttribs) { it.curQueueId = curQueue.id }
//                    playbackService?.notifyCurQueueItemsChanged(max(prevQueueSize, curQueue.size()))
                } else Text(title)
            }, navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_play), contentDescription = "Open Drawer") } }, actions = {
                val binIconRes by remember(queuesMode) { derivedStateOf { if (queuesMode != QueuesScreenMode.Queue) R.drawable.playlist_play else R.drawable.ic_history } }
                val feedsIconRes by remember(queuesMode) { derivedStateOf { if (queuesMode == QueuesScreenMode.Feed) R.drawable.playlist_play else R.drawable.baseline_dynamic_feed_24 } }
                if (queuesMode != QueuesScreenMode.Feed) IconButton(onClick = {
                    queuesMode = when(queuesMode) {
                        QueuesScreenMode.Queue -> QueuesScreenMode.Bin
                        QueuesScreenMode.Bin -> QueuesScreenMode.Queue
                        else -> QueuesScreenMode.Queue
                    }
                }) { Icon(imageVector = ImageVector.vectorResource(binIconRes), contentDescription = "bin") }
                if (queuesMode in listOf(QueuesScreenMode.Queue, QueuesScreenMode.Feed)) IconButton(onClick = {
                    queuesMode = when(queuesMode) {
                        QueuesScreenMode.Queue -> QueuesScreenMode.Feed
                        QueuesScreenMode.Feed -> QueuesScreenMode.Queue
                        else -> QueuesScreenMode.Queue
                    }
                }) { Icon(imageVector = ImageVector.vectorResource(feedsIconRes), contentDescription = "feeds") }
                if (queuesMode == QueuesScreenMode.Feed) IconButton(onClick = {
                    facetsMode = QuickAccess.Custom
                    facetsCustomTag = spinnerTexts[curIndex]
                    facetsCustomQuery = realm.query(Episode::class).query("feedId IN $0", feedsAssociated.map { it.id })
                    navController.navigate(Screens.Facets.name)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_view_in_ar_24), contentDescription = "facets") }
                if (queuesMode == QueuesScreenMode.Queue) IconButton(onClick = { navController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_label)) }, onClick = {
                        queuesMode = QueuesScreenMode.Settings
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_bin_label)) }, onClick = {
                        curQueue = upsertBlk(curQueue) {
                            it.idsBinList.clear()
                            it.update()
                        }
                        expanded = false
                    })
                    if (queuesMode == QueuesScreenMode.Queue) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort)) }, onClick = {
                            showSortDialog = true
                            expanded = false
                        })
                        if (queueNames.size < QUEUES_LIMIT) DropdownMenuItem(text = { Text(stringResource(R.string.add_queue)) }, onClick = {
                            showAddQueueDialog.value = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_queue_label)) }, onClick = {
                            showClearQueueDialog.value = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                            runOnceOrAsk(context)
                            expanded = false
                        })
                        if (!curQueue.isSorted) {
                            fun toggleQL() {
                                curQueue = upsertBlk(curQueue) { it.isLocked = !it.isLocked}
                                dragDropEnabled = !(curQueue.isSorted || curQueue.isLocked)
                                Logt(TAG, context.getString(if (curQueue.isLocked) R.string.queue_locked else R.string.queue_unlocked))
                                expanded = false
                            }
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.lock_queue))
                                    Checkbox(checked = curQueue.isLocked, onCheckedChange = { toggleQL() })
                                }
                            }, onClick = { toggleQL() })
                        }
                    }
                }
            })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @Composable
    fun Settings() {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val showRename by remember { mutableStateOf(curQueue.name != "Default" && curQueue.name != "Virtual") }
            if (showRename) Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rename), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(0.2f))
                var showIcon by remember { mutableStateOf(false) }
                var newName by remember { mutableStateOf(curQueue.name) }
                TextField(value = newName, label = { Text("Rename (Unique name only)") }, singleLine = true, modifier = Modifier.weight(1f),
                    onValueChange = {
                        newName = it
                        showIcon = true
                    },
                    trailingIcon = {
                        if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon", modifier = Modifier.size(30.dp).padding(start = 5.dp).clickable(
                            onClick = {
                                if (newName.isNotEmpty() && curQueue.name != newName && queueNames.indexOf(newName) < 0) {
                                    curQueue = upsertBlk(curQueue) { it.name = newName }
                                }
                                showIcon = false
                        }))
                })
            }
            var showLocationOptions by remember { mutableStateOf(false) }
            var location by remember { mutableStateOf(EnqueueLocation.BACK) }
            TitleSummaryActionColumn(R.string.pref_enqueue_location_title, R.string.pref_enqueue_location_sum) { showLocationOptions = true }
            Text(location.name, modifier = Modifier.padding(start = 30.dp), style = MaterialTheme.typography.bodyMedium)

            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bin_limit) + ": ${curQueue.binLimit}", style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                var limitString by remember { mutableIntStateOf((curQueue.binLimit)) }
                NumberEditor(limitString, stringResource(R.string.bin_limit), nz = true, modifier = Modifier.width(150.dp)) { v ->
                    limitString = v
                    curQueue = upsertBlk(curQueue) { it.binLimit = limitString }
                }
            }
            if (showLocationOptions) {
                AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showLocationOptions = false },
                    title = { Text(stringResource(R.string.pref_hardware_previous_button_title), style = CustomTextStyles.titleCustom) },
                    text = {
                        Column {
                            EnqueueLocation.entries.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                                    .clickable { location = option }) {
                                    Checkbox(checked = location == option, onCheckedChange = { location = option })
                                    Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            upsertBlk(curQueue) { it.enqueueLocation = location.code }
                            showLocationOptions = false
                        }) { Text(text = "OK") }
                    },
                    dismissButton = { TextButton(onClick = { showLocationOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
                )
            }
            TitleSummarySwitchRow(R.string.pref_autodl_queue_empty_title, R.string.pref_autodl_queue_empty_sum, curQueue.launchAutoEQDlWhenEmpty) { v ->
                upsertBlk(curQueue) { it.launchAutoEQDlWhenEmpty = v }
            }
            TitleSummarySwitchRow(R.string.pref_auto_download_include_queues_title, R.string.pref_auto_download_include_queues_sum, curQueue.autoDownloadEpisodes) { v ->
                upsertBlk(curQueue) { it.autoDownloadEpisodes = v }
            }
            if (showRename) {
                HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 80.dp))
                TitleSummaryActionColumn(R.string.remove_queue, R.string.remove_queue_sum) {
                    commonConfirm = CommonConfirmAttrib(
                        title = context.getString(R.string.remove_queue) + "?",
                        message = "",
                        confirmRes = R.string.confirm_label,
                        cancelRes = R.string.cancel_label,
                        onConfirm = {
                            runOnIOScope {
                                Logd(TAG, "remove_queue ")
                                realm.write {
                                    Logd(TAG, "remove_queue episodes: ${curQueue.episodes.size}")
                                    curQueue.episodes.forEach {
                                        val e = findLatest(it)
                                        e?.setPlayState(EpisodeState.UNPLAYED)
                                    }
                                    Logd(TAG, "remove_queue feedsAssociated: ${feedsAssociated.size}")
                                    feedsAssociated.forEach {
                                        val f = findLatest(it)
                                        f?.queueId = 0
                                    }
                                    val q = findLatest(curQueue)
                                    if (q != null) delete(q)
                                }
                                withContext(Dispatchers.Main) {
                                    curQueue = queues[0]
                                    curIndex = 0
                                    queuesMode = QueuesScreenMode.Queue
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedsGrid() {
        val context = LocalContext.current
        val lazyGridState = rememberLazyGridState()
        Logd(TAG, "FeedsGrid")
        LazyVerticalGrid(state = lazyGridState, columns = GridCells.Adaptive(80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)) {
            items(feedsAssociated.size, key = {index -> feedsAssociated[index].id}) { index ->
                val feed by remember { mutableStateOf(feedsAssociated[index]) }
                ConstraintLayout {
                    val (coverImage, episodeCount, rating, _) = createRefs()
                    val img = remember(feed) { ImageRequest.Builder(context).data(feed.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                    AsyncImage(model = img, contentDescription = "coverImage", modifier = Modifier.height(100.dp).aspectRatio(1f)
                        .constrainAs(coverImage) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                        }.combinedClickable(onClick = {
                            Logd(TAG, "clicked: ${feed.title}")
                            feedOnDisplay = feed
                            feedScreenMode = FeedScreenMode.List
                            navController.navigate(Screens.FeedDetails.name)
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

    OpenDialogs()

    fun moveInQueue(from: Int, to: Int) {
        val episodes = curQueue.episodes.toMutableList()
        if (episodes.isNotEmpty()) {
            if ((from in 0 ..< episodes.size) && (to in 0..<episodes.size)) {
                val episode = episodes.removeAt(from)
                try { episodes.add(to, episode) } catch (e: Throwable) { Loge(TAG, "moveInQueue: ${e.message}")}
                if (actQueue.id == curQueue.id) EventFlow.postEvent(FlowEvent.QueueEvent.moved(episode, to))
            }
            resetInQueueTime(episodes)
            curQueue.episodes.clear()
            curQueue = resetIds(curQueue, episodes)
            if (actQueue.id == curQueue.id) actQueue = curQueue
        } else Loge(TAG, "moveQueueItemHelper: Could not load queue")
    }

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Logd(TAG, "Scaffold screenMode: $queuesMode")
        when (queuesMode) {
            QueuesScreenMode.Feed -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) { FeedsGrid() }
            QueuesScreenMode.Settings -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) { Settings() }
            else -> {
                val episodesChange by episodesFlow.collectAsState(initial = null)
                val episodes = episodesChange?.list ?: emptyList()
                LaunchedEffect(episodes.size) {
                    Logd(TAG, "LaunchedEffect(episodes.size)")
                    scope.launch(Dispatchers.IO) {
                        val info = buildListInfo(episodes)
                        withContext(Dispatchers.Main) {
                            listInfoText = info
                            infoBarText.value = "$listInfoText $feedOperationText"
                        }
                    }
                }
                if (queuesMode == QueuesScreenMode.Bin) {
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        infoBarText.value = "$listInfoText $feedOperationText"
                        InforBar(infoBarText, swipeActions)
                        EpisodeLazyColumn(context as MainActivity, episodes, swipeActions = swipeActions)
                    }
                } else {
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        InforBar(infoBarText, swipeActions)
                        EpisodeLazyColumn(context as MainActivity, episodes, swipeActions = swipeActions,
                            isDraggable = dragDropEnabled, dragCB = { iFrom, iTo ->
                                runOnIOScope {
                                    moveInQueue(iFrom, iTo)
                                    withContext(Dispatchers.Main) { dragged++ }
                                }
                            },
                            actionButtonCB = { e, type ->
                                if (type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAYLOCAL, ButtonTypes.STREAM)) {
                                    if (actQueue.id != curQueue.id) {
                                        actQueue = curQueue
//                                        upsertBlk(virQueue) { q ->
//                                            q.identity = ""
//                                            q.episodeIds = realmListOf()
//                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

