package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.automation.autodownloadForQueue
import ac.mdiq.podcini.automation.autoenqueueForQueue
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.mediaBrowser
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.buildListInfo
import ac.mdiq.podcini.storage.database.feedOperationText
import ac.mdiq.podcini.storage.database.persistOrdered
import ac.mdiq.podcini.storage.database.queuesFlow
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.setPlayState
import ac.mdiq.podcini.storage.database.shouldPreserve
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.QueueEntry
import ac.mdiq.podcini.storage.model.VIRTUAL_QUEUE_ID
import ac.mdiq.podcini.storage.specs.EnqueueLocation
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.compose.AssociatedFeedsGrid
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeScreen
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.screens.LocalNavController
import ac.mdiq.podcini.ui.compose.NumberEditor
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchRow
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.compose.episodeForInfo
import ac.mdiq.podcini.ui.compose.filterChipBorder
import ac.mdiq.podcini.ui.screens.handleBackSubScreens
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.timeIt
import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.github.xilinjia.krdb.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val TAG = Screens.Queues.name

private const val QUEUES_LIMIT = 20

enum class QueuesScreenMode {
    Queue,
    Bin,
    Feed,
    Settings
}

class QueuesVM(id_: Long): ViewModel() {
    val id: Long = id_

    var cameBack by mutableStateOf(false)

    var queues by mutableStateOf<List<PlayQueue>>(listOf())
    val queueNames = mutableStateListOf<String>()
    val spinnerTexts = mutableStateListOf<String>()

    val feedsAssociated = mutableStateListOf<Feed>()
    var queuesMode by  mutableStateOf( if (appAttribs.queuesMode.isNotBlank()) QueuesScreenMode.valueOf(appAttribs.queuesMode) else QueuesScreenMode.Queue)

    val curQueueFlow: StateFlow<PlayQueue?> = snapshotFlow { appAttribs.curQueueId }.distinctUntilChanged().flatMapLatest { id ->
        queuesFlow.map { it.list.firstOrNull { q -> q.id == id }}
    }.distinctUntilChanged().stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = null)

    var curIndex by  mutableIntStateOf(-1)
    var curQueue by mutableStateOf(actQueue)

    val queueEntriesFlow: StateFlow<List<QueueEntry>> = snapshotFlow { Pair(curQueue.id, queuesMode) }.distinctUntilChanged().flatMapLatest {
        when (queuesMode) {
            QueuesScreenMode.Queue -> {
                val qeRealmFlow = realm.query<QueueEntry>("queueId == $0 SORT(position ASC)", curQueue.id).asFlow()
                qeRealmFlow.map { it.list }
            }
            else -> emptyFlow()
        }
    }.distinctUntilChanged().stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList())

    val episodesSortedFlow: StateFlow<List<Episode>> = snapshotFlow { Triple(curQueue.id, curQueue.sortOrderCode, queuesMode) }.distinctUntilChanged().flatMapLatest {
        fun initBinFlow(): Flow<List<Episode>> {
            Logd(TAG, "initBinFlow idsBinList: ${curQueue.idsBinList.size} ${curQueue.idsBinList.toSet().size}")
            return realm.query(Episode::class, "id IN $0", curQueue.idsBinList).asFlow().map { it.list }
                .map { episodes ->
                    val orderMap = curQueue.idsBinList.withIndex().associate { it.value to it.index }
                    episodes.sortedBy { episode -> orderMap[episode.id] ?: Int.MAX_VALUE }.reversed()
                }
        }
        fun initQueueFlow():  Flow<List<Episode>> {
            Logd(TAG, "initQueueFlow ")
            val orderedEpisodeIdsFlow = realm.query<QueueEntry>("queueId == $0 SORT(position ASC)", curQueue.id).asFlow().map { results -> results.list.map { it.episodeId } }
            val episodesFlow = orderedEpisodeIdsFlow.flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList()) else realm.query<Episode>("id IN $0", ids).asFlow().map { it.list }
            }
            return combine(orderedEpisodeIdsFlow, episodesFlow) { ids, episodes ->
                val episodeMap = episodes.associateBy { it.id }
                ids.mapNotNull { episodeMap[it] }
            }
        }
        when (queuesMode) {
            QueuesScreenMode.Queue -> initQueueFlow()
            QueuesScreenMode.Bin -> initBinFlow()
            else -> emptyFlow()
        }
    }.distinctUntilChanged().stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList())

    init {
        timeIt("$TAG start of init")

        viewModelScope.launch { snapshotFlow { Pair(queues, actQueue.id) }.distinctUntilChanged().collect {
            spinnerTexts.clear()
            spinnerTexts.addAll(queues.map { "${if (it.id == actQueue.id) "> " else ""}${it.name} : ${it.size()}" })
        } }
        viewModelScope.launch { snapshotFlow { queues.size }.distinctUntilChanged().collect {
            queueNames.clear()
            queueNames.addAll(queues.map { it.name })
            if (curIndex < 0) {
                val qid = if (id >= 0) id else appAttribs.curQueueId
                curIndex = queues.indexOfFirst { it.id == qid }
            }
        } }
        viewModelScope.launch { snapshotFlow { curQueue.id }.distinctUntilChanged().collect {
            feedsAssociated.clear()
            feedsAssociated.addAll(realm.query(Feed::class).query("queueId == ${curQueue.id}").find())
        } }
        timeIt("$TAG end of init")
    }

    override fun onCleared() {
        super.onCleared()
        Logd(TAG, "VM onCleared")
    }
}

@ExperimentalMaterial3Api
@Composable
fun QueuesScreen(id: Long = -1L) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val drawerController = LocalDrawerController.current

    val vm: QueuesVM = viewModel(factory = viewModelFactory { initializer { QueuesVM(id) } })
    
    var browserFuture: ListenableFuture<MediaBrowser>? by remember { mutableStateOf(null) }

    var swipeActions by remember { mutableStateOf(SwipeActions(TAG)) }

    var curQueuePosition by remember(vm.curQueue) {  mutableIntStateOf(vm.curQueue.scrollPosition) }
    Logd(TAG, "curQueuePosition: $curQueuePosition ${vm.curQueue.id}")

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect LifecycleEventObserver: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
                    browserFuture?.addListener({
                        // here we can get the root of media items tree or we can get also the children if it is an album for example.
                        mediaBrowser = browserFuture!!.get()
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
            runOnIOScope {
                upsertBlk(vm.curQueue) {
                    it.scrollPosition = curQueuePosition
                    it.update()
                }
            }
            if (browserFuture != null) MediaBrowser.releaseFuture(browserFuture!!)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(vm.queuesMode, episodeForInfo) {
        Logd(TAG, "DisposableEffect queuesMode: ${vm.queuesMode}")
        if (vm.queuesMode != QueuesScreenMode.Queue || episodeForInfo != null) handleBackSubScreens.add(TAG)
        else handleBackSubScreens.remove(TAG)
        onDispose { handleBackSubScreens.remove(TAG) }
    }

    BackHandler(enabled = handleBackSubScreens.contains(TAG)) {
        Logd(TAG, "BackHandler ${vm.queuesMode}")
        if (episodeForInfo != null) {
            vm.cameBack = true
            episodeForInfo = null
        } else when(vm.queuesMode) {
            QueuesScreenMode.Bin, QueuesScreenMode.Feed, QueuesScreenMode.Settings -> {
                vm.queuesMode = QueuesScreenMode.Queue
                runOnIOScope { upsert(appAttribs) { it.queuesMode = vm.queuesMode.name } }
            }
            else -> {}
        }
    }
    
    val episodes by vm.episodesSortedFlow.collectAsStateWithLifecycle()
    val queueEntries by vm.queueEntriesFlow.collectAsStateWithLifecycle()

    val queuesResults by queuesFlow.collectAsStateWithLifecycle(initialValue = null)
    if (queuesResults?.list != null) vm.queues = queuesResults!!.list

    val cq by vm.curQueueFlow.collectAsStateWithLifecycle(initialValue = null)
    if (cq != null) vm.curQueue = cq!!

    Logd(TAG, "episodes: ${episodes.size}")

    LaunchedEffect( vm.queuesMode) {
        Logd(TAG, "LaunchedEffect(vm.curQueue, screenMode, dragged)")
        when (vm.queuesMode) {
            QueuesScreenMode.Bin -> swipeActions = SwipeActions("${TAG}_Bin")
            QueuesScreenMode.Queue -> swipeActions = SwipeActions(TAG)
            QueuesScreenMode.Feed -> {}
            else -> {}
        }
    }

    var showSortDialog by remember { mutableStateOf(false) }
    val showClearQueueDialog = remember { mutableStateOf(false) }
    val showAddQueueDialog = remember { mutableStateOf(false) }
    var showChooseQueue by remember { mutableStateOf(false) }

    fun setCurIndex(index: Int) {
        vm.curIndex = index
        runOnIOScope {
            upsert(vm.queues[index]) { it.update() }
            upsert(appAttribs) { it.curQueueId = vm.queues[index].id }
        }
    }

    Logd(TAG, "in Composition")

    val lazyListState = rememberLazyListState()

    @Composable
    fun OpenDialogs() {
        ComfirmDialog(titleRes = R.string.clear_queue_label, message = stringResource(R.string.clear_queue_confirmation_msg), showDialog = showClearQueueDialog) {
            runOnIOScope {
                val qes = vm.curQueue.entries
                val episodeIds = qes.map { it.episodeId }
                upsert(vm.curQueue) {
                    it.idsBinList.removeAll(episodeIds)
                    it.idsBinList.addAll(episodeIds)
                    it.trimBin()
                    it.update()
                }
                val toSetStat = episodes.filter { it.playState < EpisodeState.SKIPPED.code && !shouldPreserve(it.playState) }
                if (toSetStat.isNotEmpty()) setPlayState(EpisodeState.SKIPPED, toSetStat, false)
                if (vm.curQueue.id == actQueue.id) EventFlow.postEvent(FlowEvent.QueueEvent.cleared())
                vm.curQueue.checkAndFill()
                realm.writeBlocking {
                    for (qe in qes) {
                        val qe_ = findLatest(qe)
                        if (qe_ != null) delete(qe_)
                    }
                }
            }
        }

        if (showAddQueueDialog.value) CommonPopupCard(onDismissRequest = { showAddQueueDialog.value = false }) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var newName by remember { mutableStateOf("") }
                TextField(value = newName, onValueChange = { newName = it }, label = { Text("Add queue (Unique name only)") })
                Button(onClick = {
                    if (newName.isNotEmpty() && vm.queueNames.indexOf(newName) < 0) {
                        val newQueue = PlayQueue()
                        val maxId = vm.queues.map { it.id }.filter { it < VIRTUAL_QUEUE_ID }.maxOrNull() ?: -1
                        newQueue.id = maxId + 1
                        newQueue.name = newName
                        upsertBlk(newQueue) {}
                        showAddQueueDialog.value = false
                    }
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }

        swipeActions.ActionOptionsDialog()

        if (showSortDialog) EpisodeSortDialog(initOrder = vm.curQueue.sortOrder, onDismissRequest = { showSortDialog = false },
            includeConditionals = listOf(EpisodeSortOrder.RANDOM, EpisodeSortOrder.RANDOM1, EpisodeSortOrder.SMART_SHUFFLE_ASC, EpisodeSortOrder.SMART_SHUFFLE_DESC )) { order ->
            upsertBlk(vm.curQueue) { it.sortOrder = order ?: EpisodeSortOrder.DATE_DESC }
            runOnIOScope {
                val episodes_ = episodes.toMutableList()
                getPermutor(order ?: EpisodeSortOrder.DATE_DESC).reorder(episodes_)
                persistOrdered(episodes_, queueEntries)
            }
        }

        if (showChooseQueue) Popup(onDismissRequest = { showChooseQueue = false }, alignment = Alignment.TopStart, offset = IntOffset(100, 100), properties = PopupProperties(focusable = true)) {
            Card(modifier = Modifier.width(300.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(10.dp)) {
                    for (index in vm.queues.indices) {
                        FilterChip(onClick = {
                            if (vm.queuesMode == QueuesScreenMode.Queue) upsertBlk(vm.curQueue) { it.scrollPosition = lazyListState.firstVisibleItemIndex }
                            setCurIndex(index)
                            showChooseQueue = false
                        }, label = { Text(vm.spinnerTexts[index]) }, selected = vm.curIndex == index, border = filterChipBorder(vm.curIndex == index))
                    }
                }
            }
        }
    }

    OpenDialogs()
    
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val buttonColor = Color(0xDDFFD700)
        Box {
            TopAppBar(title = {
                if (vm.queuesMode == QueuesScreenMode.Queue) {
                    Text((if (vm.curQueue.id == actQueue.id) "> " else "") + if (vm.curIndex in vm.queueNames.indices) vm.queueNames[vm.curIndex].ifBlank { "No name" } else "No name", maxLines = 1, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.scale(scaleX = 1f, scaleY = 1.8f).combinedClickable(
                        onClick = { showChooseQueue = true },
                        onLongClick = {
                            if (vm.curQueue.id == actQueue.id) {
                                if (episodes.size > 5) {
                                    val index = episodes.indexOfFirst { it.id == curEpisode?.id }
                                    if (index >= 0) scope.launch { lazyListState.scrollToItem(index) }
                                    else Logt(TAG, "can not find curEpisode to scroll to")
                                } else Logt(TAG, "only scroll in actQueue when size is larger than 5")
                            } else {
                                upsertBlk(vm.curQueue) { it.scrollPosition = lazyListState.firstVisibleItemIndex }
                                val index = vm.queues.indexOfFirst { it.id == actQueue.id }
                                if (index >= 0) setCurIndex(index)
                                else Logt(TAG, "actQueue is not available")
                            }
                        }))
                } else {
                    val title = remember(vm.queuesMode) { when (vm.queuesMode) {
                        QueuesScreenMode.Bin -> vm.curQueue.name + " Bin"
                        QueuesScreenMode.Queue -> ""
                        QueuesScreenMode.Feed -> "${vm.feedsAssociated.size} Feeds"
                        else -> "Settings"
                    } }
                    Text(title)
                } },
                navigationIcon = { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_play), contentDescription = "Open Drawer", modifier = Modifier.padding(7.dp).clickable(onClick = { drawerController?.open() })) },
                actions = {
                    val binIconRes by remember(vm.queuesMode) { derivedStateOf { if (vm.queuesMode != QueuesScreenMode.Queue) R.drawable.playlist_play else R.drawable.ic_history } }
                    val feedsIconRes by remember(vm.queuesMode) { derivedStateOf { if (vm.queuesMode == QueuesScreenMode.Feed) R.drawable.playlist_play else R.drawable.baseline_dynamic_feed_24 } }
                    if (vm.queuesMode != QueuesScreenMode.Feed) IconButton(onClick = {
                        vm.cameBack = false
                        vm.queuesMode = when(vm.queuesMode) {
                            QueuesScreenMode.Queue -> QueuesScreenMode.Bin
                            QueuesScreenMode.Bin -> QueuesScreenMode.Queue
                            else -> QueuesScreenMode.Queue
                        }
                        runOnIOScope { upsert(appAttribs) { it.queuesMode = vm.queuesMode.name } }
                    }) { Icon(imageVector = ImageVector.vectorResource(binIconRes), contentDescription = "bin") }
                    if (vm.queuesMode in listOf(QueuesScreenMode.Queue, QueuesScreenMode.Feed)) IconButton(onClick = {
                        vm.cameBack = false
                        vm.queuesMode = when(vm.queuesMode) {
                            QueuesScreenMode.Queue -> QueuesScreenMode.Feed
                            QueuesScreenMode.Feed -> QueuesScreenMode.Queue
                            else -> QueuesScreenMode.Queue
                        }
                        runOnIOScope { upsert(appAttribs) { it.queuesMode = vm.queuesMode.name } }
                    }) { Icon(imageVector = ImageVector.vectorResource(feedsIconRes), contentDescription = "feeds") }
                    if (vm.queuesMode == QueuesScreenMode.Feed) {
                        IconButton(onClick = {
                            facetsMode = QuickAccess.Custom
                            facetsCustomTag = vm.spinnerTexts[vm.curIndex]
                            facetsCustomQuery = realm.query(Episode::class).query("feedId IN $0", vm.feedsAssociated.map { it.id })
                            navController.navigate("${Screens.Facets.name}?modeName=${QuickAccess.Custom.name}")
//                            navController.navigate(Screens.Facets.name)
                        }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_view_in_ar_24), contentDescription = "facets") }
                        IconButton(onClick = {
                            feedIdsToUse.clear()
                            feedIdsToUse.addAll(vm.feedsAssociated.map { it.id })
                            navController.navigate(Screens.Library.name)
                        }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_subscriptions), contentDescription = "library") }
                    }
                    if (vm.queuesMode == QueuesScreenMode.Queue) IconButton(onClick = { navController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.settings_label)) }, onClick = {
                            vm.queuesMode = QueuesScreenMode.Settings
                            runOnIOScope { upsert(appAttribs) { it.queuesMode = vm.queuesMode.name } }
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_bin_label)) }, onClick = {
                            upsertBlk(vm.curQueue) {
                                it.idsBinList.clear()
                                it.update()
                            }
                            expanded = false
                        })
                        if (vm.queuesMode == QueuesScreenMode.Queue) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.sort)) }, onClick = {
                                showSortDialog = true
                                expanded = false
                            })
                            if (vm.queueNames.size < QUEUES_LIMIT) DropdownMenuItem(text = { Text(stringResource(R.string.add_queue)) }, onClick = {
                                showAddQueueDialog.value = true
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clear_queue_label)) }, onClick = {
                                showClearQueueDialog.value = true
                                expanded = false
                            })
                            fun toggleQL() {
                                upsertBlk(vm.curQueue) {
                                    it.isLocked = !it.isLocked
                                    if (!it.isLocked) it.autoSort = false
                                }
                                //                                dragDropEnabled = !(vm.curQueue.isSorted || vm.curQueue.isLocked)
                                Logt(TAG, context.getString(if (vm.curQueue.isLocked) R.string.queue_locked else R.string.queue_unlocked))
                                expanded = false
                            }
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.lock_queue))
                                    Checkbox(checked = vm.curQueue.isLocked, onCheckedChange = { toggleQL() })
                                }
                            }, onClick = { toggleQL() })
                        }
                    }
                })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @Composable
    fun Settings() {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val showRename by remember { mutableStateOf(vm.curQueue.name != "Default" && vm.curQueue.name != "Virtual") }
            if (showRename) Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rename), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(0.2f))
                var showIcon by remember { mutableStateOf(false) }
                var newName by remember { mutableStateOf(vm.curQueue.name) }
                TextField(value = newName, label = { Text("Rename (Unique name only)") }, singleLine = true, modifier = Modifier.weight(1f),
                    onValueChange = {
                        newName = it
                        showIcon = true
                    },
                    trailingIcon = {
                        if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon", modifier = Modifier.size(30.dp).padding(start = 5.dp).clickable(
                            onClick = {
                                if (newName.isNotEmpty() && vm.curQueue.name != newName && vm.queueNames.indexOf(newName) < 0) {
                                    upsertBlk(vm.curQueue) { it.name = newName }
                                }
                                showIcon = false
                        }))
                })
            }
            TitleSummarySwitchRow(R.string.pref_followQueue_title, R.string.pref_followQueue_sum, vm.curQueue.playInSequence) { v ->
                upsertBlk(vm.curQueue) { it.playInSequence = v }
            }

            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bin_limit) + ": ${vm.curQueue.binLimit}", style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                var limitString by remember { mutableIntStateOf((vm.curQueue.binLimit)) }
                NumberEditor(limitString, stringResource(R.string.bin_limit), nz = true, modifier = Modifier.width(150.dp)) { v ->
                    limitString = v
                    upsertBlk(vm.curQueue) { it.binLimit = limitString }
                }
            }
            var autoSort by remember { mutableStateOf(vm.curQueue.autoSort) }
            TitleSummarySwitchRow(R.string.pref_auto_sort_queue, R.string.pref_auto_sort_queue_sum, autoSort) { v ->
                autoSort = v
                upsertBlk(vm.curQueue) {
                    it.autoSort = v
                    if (v) it.isLocked = true
                }
            }
            if (!autoSort) {
                var showLocationOptions by remember { mutableStateOf(false) }
                var location by remember { mutableStateOf(EnqueueLocation.BACK) }
                TitleSummaryActionColumn(R.string.pref_enqueue_location_title, R.string.pref_enqueue_location_sum) { showLocationOptions = true }
                Text(location.name, modifier = Modifier.padding(start = 30.dp), style = MaterialTheme.typography.bodyMedium)
                if (showLocationOptions) {
                    AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { showLocationOptions = false },
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
                                upsertBlk(vm.curQueue) { it.enqueueLocation = location.code }
                                showLocationOptions = false
                            }) { Text(text = "OK") }
                        },
                        dismissButton = { TextButton(onClick = { showLocationOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
                    )
                }
            }
            TitleSummarySwitchRow(R.string.pref_autodl_queue_empty_title, R.string.pref_autodl_queue_empty_sum, vm.curQueue.launchAutoEQDlWhenEmpty) { v ->
                upsertBlk(vm.curQueue) { it.launchAutoEQDlWhenEmpty = v }
            }
            TitleSummarySwitchRow(R.string.pref_auto_download_include_queues_title, R.string.pref_auto_download_include_queues_sum, vm.curQueue.autoDownloadEpisodes) { v ->
                upsertBlk(vm.curQueue) { it.autoDownloadEpisodes = v }
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
                                    Logd(TAG, "remove_queue episodes: ${episodes.size}")
                                    episodes.forEach { findLatest(it)?.setPlayState(EpisodeState.UNPLAYED) }
                                    Logd(TAG, "remove_queue vm.feedsAssociated: ${vm.feedsAssociated.size}")
                                    vm.feedsAssociated.forEach { findLatest(it)?.queueId = 0 }
                                    val q = findLatest(vm.curQueue)
                                    if (q != null) delete(q)
                                }
                                upsert(appAttribs) { it.queuesMode = QueuesScreenMode.Queue.name }
                                withContext(Dispatchers.Main) {
                                    vm.curIndex = 0
                                    vm.queuesMode = QueuesScreenMode.Queue
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (episodeForInfo != null) EpisodeScreen(episodeForInfo!!, listFlow = vm.episodesSortedFlow)
    else Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
//        Logd(TAG, "Scaffold screenMode: $queuesMode")
        when (vm.queuesMode) {
            QueuesScreenMode.Feed -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) { AssociatedFeedsGrid(vm.feedsAssociated) }
            QueuesScreenMode.Settings -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) { Settings() }
            else -> {
                var listInfoText by remember { mutableStateOf("") }
                LaunchedEffect(episodes.size) {
                    Logd(TAG, "LaunchedEffect(episodes.size) ${episodes.size}")
                    scope.launch(Dispatchers.IO) { listInfoText = buildListInfo(episodes) }
                }
                if (vm.queuesMode == QueuesScreenMode.Bin) {
                    Logd(TAG, "vm.queuesMode == QueuesScreenMode.Bin")
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        InforBar(swipeActions) { Text("$listInfoText $feedOperationText", style = MaterialTheme.typography.bodyMedium) }
                        EpisodeLazyColumn(episodes, swipeActions = swipeActions)
                    }
                } else {
                    val dragDropEnabled by remember(vm.curQueue.id, vm.curQueue.isLocked) { mutableStateOf(!vm.curQueue.isLocked) }
                    if (dragDropEnabled) {
                        val episodes_ = remember(episodes) { episodes.toMutableStateList() }
                        val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(episodes_, key = { _, e -> e.id }) { index, episode_ ->
                                val episode by rememberUpdatedState(episode_)
                                fun <T> MutableList<T>.move(from: Int, to: Int) {
                                    if (from == to) return
                                    val item = removeAt(from)
                                    add(to, item)
                                }
                                val buttonColor = MaterialTheme.colorScheme.tertiary
                                val imageWidth = 56.dp
                                val imageHeight = 56.dp
                                var isDragging by remember(episode.id) { mutableStateOf(false) }
                                var yOffset by remember(index) { mutableFloatStateOf(0f) }
                                var draggedIndex by remember { mutableStateOf<Int?>(null) }
                                Row(Modifier.background(MaterialTheme.colorScheme.surface).zIndex(if (draggedIndex == index) 1f else 0f).offset { IntOffset(0, if (draggedIndex == index) yOffset.roundToInt() else 0) }) {
                                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_drag_darktheme), tint = buttonColor, contentDescription = "drag handle", modifier = Modifier.width(30.dp).align(Alignment.CenterVertically).padding(start = 5.dp, end = 5.dp).zIndex(if (draggedIndex == index) 1f else 0f)
                                        .draggable(orientation = Orientation.Vertical,
                                            state = rememberDraggableState { delta ->
                                                yOffset += delta
                                                val from = draggedIndex ?: return@rememberDraggableState
                                                while (yOffset > rowHeightPx) {
                                                    val to = (from + 1).coerceAtMost(episodes_.lastIndex)
                                                    if (to == from) break
                                                    episodes_.move(from, to)
                                                    draggedIndex = to
                                                    yOffset -= rowHeightPx
                                                }
                                                while (yOffset < -rowHeightPx) {
                                                    val to = (from - 1).coerceAtLeast(0)
                                                    if (to == from) break
                                                    episodes_.move(from, to)
                                                    draggedIndex = to
                                                    yOffset += rowHeightPx
                                                }
                                            },
                                            onDragStarted = {
                                                Logd(TAG, "MainRow onDragStart")
                                                isDragging = true
                                                draggedIndex = index
                                            },
                                            onDragStopped = {
                                                Logd(TAG, "MainRow onDragEnd")
                                                isDragging = false
                                                persistOrdered(episodes_, queueEntries)
                                                draggedIndex = null
                                                yOffset = 0f
                                            }))
                                    Box(modifier = Modifier.width(imageWidth).height(imageHeight)) {
                                        AsyncImage(model = ImageRequest.Builder(context).data(episode.imageLocation(false)).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.drawable.ic_launcher_foreground).error(R.drawable.ic_launcher_foreground).build(), contentDescription = "imgvCover", modifier = Modifier.fillMaxSize())
                                    }
                                    Text(episode.title?: "No title")
                                }
                            }
                        }
                    } else Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        LaunchedEffect(Unit) {
                            snapshotFlow { lazyListState.isScrollInProgress }
                                .collect { isScrolling ->
                                    if (!isScrolling) {
                                        val index = lazyListState.firstVisibleItemIndex
                                        Logd(TAG, "Scroll settled at: $index")
                                        curQueuePosition = index
                                    }
                                }
                        }
                        var scrollToOnStart by remember(vm.queuesMode, vm.curQueue, episodes.size, curEpisode?.id, vm.cameBack) { mutableIntStateOf(
                            when {
                                vm.queuesMode != QueuesScreenMode.Queue -> -1
                                vm.cameBack -> -1
                                vm.curQueue.id == actQueue.id -> episodes.indexOfFirst { it.id == curEpisode?.id }
                                else -> curQueuePosition
                            }
                        ) }
                        Logd(TAG, "Scaffold scrollToOnStart: cameBack: ${vm.cameBack} $scrollToOnStart $curQueuePosition")
                        InforBar(swipeActions) { Text("$listInfoText $feedOperationText", style = MaterialTheme.typography.bodyMedium) }
                        EpisodeLazyColumn(episodes, swipeActions = swipeActions,
                            lazyListState = lazyListState, scrollToOnStart = scrollToOnStart,
                            refreshCB = {
                                commonConfirm = CommonConfirmAttrib(
                                    title = context.getString(R.string.refresh_associates) + "?",
                                    message = "",
                                    cancelRes = R.string.cancel_label,
                                    confirmRes = R.string.enqueue,
                                    onConfirm = {
                                        autoenqueueForQueue(vm.curQueue)
                                        if (vm.curQueue.launchAutoEQDlWhenEmpty) autodownloadForQueue(vm.curQueue)
                                    },
                                    neutralRes = R.string.refresh_label,
                                    onNeutral = { runOnceOrAsk(feeds = vm.feedsAssociated)  }
                                )
                            },
                            actionButtonCB = { e, type ->
                                if (type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAY_LOCAL, ButtonTypes.STREAM))
                                    if (actQueue.id != vm.curQueue.id) {
                                        val q = queuesLive.find { it.id == vm.curQueue.id }
                                        if (q != null) actQueue = q
                                    }
                            }
                        )
                    }
                }
            }
        }
    }
}

