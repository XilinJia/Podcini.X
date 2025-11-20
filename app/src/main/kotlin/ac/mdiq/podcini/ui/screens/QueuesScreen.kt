package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.mediaBrowser
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.storage.database.buildListInfo
import ac.mdiq.podcini.storage.database.clearQueue
import ac.mdiq.podcini.storage.database.feedOperationText
import ac.mdiq.podcini.storage.database.moveInQueue
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.specs.EnqueueLocation
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.sortPairOf
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.NumberEditor
import ac.mdiq.podcini.ui.compose.SpinnerExternalSet
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchRow
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logt
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import kotlin.math.max

private val TAG = Screens.Queues.name

enum class QueuesScreenMode {
    Queue,
    Bin,
    Feed,
    Settings
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun QueuesScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = LocalNavController.current

    var browserFuture: ListenableFuture<MediaBrowser>? = remember { null }

    var screenMode by remember { mutableStateOf(QueuesScreenMode.Queue) }

    val feedsAssociated = remember {  mutableStateListOf<Feed>() }

    val swipeActions = remember { SwipeActions(context, TAG) }
    val swipeActionsBin = remember { SwipeActions(context, "$TAG.Bin") }
    var listInfoText by remember { mutableStateOf("") }
    val infoBarText = remember { mutableStateOf("") }

    var isQueueLocked by remember {  mutableStateOf(getPref(AppPrefs.prefQueueLocked, true)) }
    var dragDropEnabled by remember { mutableStateOf(!(curQueue.keepSorted || isQueueLocked)) }

    var sortOrder by remember { mutableStateOf<EpisodeSortOrder?>(null) }

    var showSortDialog by remember { mutableStateOf(false) }
    val showClearQueueDialog = remember { mutableStateOf(false) }
    val showRenameQueueDialog = remember { mutableStateOf(false) }
    val showAddQueueDialog = remember { mutableStateOf(false) }

    var queuesFlow by remember { mutableStateOf<Flow<ResultsChange<PlayQueue>>>(emptyFlow()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    queuesFlow = realm.query<PlayQueue>().asFlow()
                    lifecycleOwner.lifecycle.addObserver(swipeActions)
                    lifecycleOwner.lifecycle.addObserver(swipeActionsBin)
                    if (curQueue.keepSorted) sortOrder = curQueue.sortOrder

                    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
                    browserFuture.addListener({
                        // here we can get the root of media items tree or we can get also the children if it is an album for example.
                        mediaBrowser = browserFuture.get()
                        mediaBrowser?.subscribe("CurQueue", null)
                    }, MoreExecutors.directExecutor())
                }
                Lifecycle.Event.ON_START -> {
                    //                    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    //                    vm.browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
                    //                    vm.browserFuture.addListener({
                    //                        // here we can get the root of media items tree or we can get also the children if it is an album for example.
                    //                        mediaBrowser = vm.browserFuture.get()
                    //                        mediaBrowser?.subscribe("CurQueue", null)
                    //                    }, MoreExecutors.directExecutor())
                }
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {
                    //                    mediaBrowser?.unsubscribe("CurQueue")
                    //                    mediaBrowser = null
                    //                    MediaBrowser.releaseFuture(vm.browserFuture)
                }
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            mediaBrowser?.unsubscribe("CurQueue")
            mediaBrowser = null
            if (browserFuture != null) MediaBrowser.releaseFuture(browserFuture)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = screenMode != QueuesScreenMode.Queue) {
        Logd(TAG, "BackHandler $screenMode")
        screenMode = when(screenMode) {
            QueuesScreenMode.Bin, QueuesScreenMode.Feed -> QueuesScreenMode.Queue
            else -> QueuesScreenMode.Queue
        }
    }

    var episodesFlow by remember { mutableStateOf<Flow<ResultsChange<Episode>>>(emptyFlow()) }

    var dragged by remember { mutableIntStateOf(0) }

    var title by remember { mutableStateOf(if (screenMode == QueuesScreenMode.Bin) curQueue.name + " Bin" else "") }

    LaunchedEffect(sortOrder, queuesFlow, screenMode, dragged) {
        Logd(TAG, "LaunchedEffect(sortOrder, queuesFlow, screenMode, dragged)")
        Logd(TAG, "screenMode: $screenMode sortOrder $sortOrder")
        when (screenMode) {
            QueuesScreenMode.Feed -> {
                feedsAssociated.clear()
                feedsAssociated.addAll(realm.query(Feed::class).query("queueId == ${curQueue.id}").find())
                Logd(TAG, "feedsAssociated: ${feedsAssociated.size} ${curQueue.id}")
                title = ""
            }
            QueuesScreenMode.Bin -> {
                episodesFlow = queuesFlow.mapNotNull { resultsChange -> resultsChange.list.firstOrNull { it.id == curQueue.id }?.idsBinList
                }.flatMapLatest { ids ->
                    realm.query(Episode::class, "id IN $0", ids).sort(Pair("timeOutQueue", Sort.DESCENDING)).asFlow()
                }
                title = curQueue.name + " Bin"
            }
            else -> {
                episodesFlow = queuesFlow.mapNotNull { resultsChange -> resultsChange.list.firstOrNull { it.id == curQueue.id }?.episodeIds
                }.flatMapLatest { ids ->
                    realm.query(Episode::class).query("id IN $0", ids).sort(if (sortOrder != null) sortPairOf(sortOrder) else Pair("timeInQueue", Sort.ASCENDING)).asFlow()
                }
                title = ""
            }
        }
    }

    val queuesResults by queuesFlow.collectAsState(initial = null)
    val queues = queuesResults?.list ?: emptyList()
    val queueNames = remember(queues) { queues.map { it.name } }
    val spinnerTexts = remember(queues) { queues.map { "${it.name} : ${it.size()}" } }
    var curIndex by remember(queues, curQueue) {  mutableIntStateOf(queues.indexOfFirst { it.id == curQueue.id } ) }

    fun reorderQueue(broadcastUpdate: Boolean) {
        Logd(TAG, "reorderQueue called")
        if (sortOrder == null) {
            Logt(TAG, "sortOrder is null. Do nothing.")
            return
        }
        runOnIOScope {
            getPermutor(sortOrder!!).reorder(curQueue.episodes)
            val episodes_ = curQueue.episodes.toMutableList()
            curQueue = upsert(curQueue) {
                it.episodeIds.clear()
                for (e in episodes_) it.episodeIds.add(e.id)
                it.update()
            }
            if (broadcastUpdate) EventFlow.postEvent(FlowEvent.QueueEvent.sorted(curQueue.episodes))
        }
    }

    @Composable
    fun OpenDialogs() {
        @Composable
        fun RenameQueueDialog(showDialog: Boolean, onDismiss: () -> Unit) {
            if (showDialog) {
                Dialog(onDismissRequest = onDismiss) {
                    Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            var newName by remember { mutableStateOf(curQueue.name) }
                            TextField(value = newName, onValueChange = { newName = it }, label = { Text("Rename (Unique name only)") })
                            Button(onClick = {
                                Logd(TAG, "RenameQueueDialog $newName")
                                if (newName.isNotEmpty() && curQueue.name != newName && queueNames.indexOf(newName) < 0) {
                                    curQueue = upsertBlk(curQueue) { it.name = newName }
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
                                if (newName.isNotEmpty() && queueNames.indexOf(newName) < 0) {
                                    val newQueue = PlayQueue()
                                    newQueue.id = queueNames.size.toLong()
                                    newQueue.name = newName
                                    upsertBlk(newQueue) {}
                                    onDismiss()
                                }
                            }) { Text(stringResource(R.string.confirm_label)) }
                        }
                    }
                }
            }
        }

        ComfirmDialog(titleRes = R.string.clear_queue_label, message = stringResource(R.string.clear_queue_confirmation_msg), showDialog = showClearQueueDialog) { clearQueue() }
        RenameQueueDialog(showDialog = showRenameQueueDialog.value, onDismiss = { showRenameQueueDialog.value = false })
        AddQueueDialog(showDialog = showAddQueueDialog.value, onDismiss = { showAddQueueDialog.value = false })

        swipeActions.ActionOptionsDialog()
        swipeActionsBin.ActionOptionsDialog()

        if (showSortDialog) EpisodeSortDialog(initOrder = sortOrder, onDismissRequest = { showSortDialog = false },
            includeConditionals = listOf(EpisodeSortOrder.TIME_IN_QUEUE_OLD_NEW, EpisodeSortOrder.TIME_IN_QUEUE_NEW_OLD)) { order ->
            sortOrder = order
            curQueue = upsertBlk(curQueue) { it.sortOrder = sortOrder ?: EpisodeSortOrder.TIME_IN_QUEUE_OLD_NEW }
//            reorderQueue(true)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        var showRename by remember { mutableStateOf(curQueue.name != "Default") }
        val buttonColor = Color(0xDDFFD700)
        Box {
            TopAppBar(title = {
                if (screenMode == QueuesScreenMode.Queue) SpinnerExternalSet(items = spinnerTexts, selectedIndex = curIndex) { index: Int ->
                    Logd(TAG, "Queue selected: ${queues[index].name}")
                    val prevQueueSize = curQueue.size()
                    curIndex = queues.indexOfFirst { it.id == curQueue.id }

                    curQueue = upsertBlk(queues[index]) { it.update() }
                    showRename = curQueue.name != "Default"
                    sortOrder = if (curQueue.keepSorted) curQueue.sortOrder else null
                    playbackService?.notifyCurQueueItemsChanged(max(prevQueueSize, curQueue.size()))
                } else Text(title)
            }, navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_play), contentDescription = "Open Drawer") } }, actions = {
                val binIconRes by remember(screenMode) { derivedStateOf { if (screenMode == QueuesScreenMode.Bin) R.drawable.playlist_play else R.drawable.ic_history } }
                val feedsIconRes by remember(screenMode) { derivedStateOf { if (screenMode == QueuesScreenMode.Feed) R.drawable.playlist_play else R.drawable.baseline_dynamic_feed_24 } }
                if (screenMode != QueuesScreenMode.Feed) IconButton(onClick = {
                    screenMode = when(screenMode) {
                        QueuesScreenMode.Queue -> QueuesScreenMode.Bin
                        QueuesScreenMode.Bin -> QueuesScreenMode.Queue
                        else -> QueuesScreenMode.Queue
                    }
                }) { Icon(imageVector = ImageVector.vectorResource(binIconRes), contentDescription = "bin") }
                if (screenMode != QueuesScreenMode.Bin) IconButton(onClick = {
                    screenMode = when(screenMode) {
                        QueuesScreenMode.Queue -> QueuesScreenMode.Feed
                        QueuesScreenMode.Feed -> QueuesScreenMode.Queue
                        else -> QueuesScreenMode.Queue
                    }
                }) { Icon(imageVector = ImageVector.vectorResource(feedsIconRes), contentDescription = "feeds") }
                if (screenMode == QueuesScreenMode.Queue) IconButton(onClick = { navController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_label)) }, onClick = {
                        screenMode = QueuesScreenMode.Settings
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_bin_label)) }, onClick = {
                        curQueue = upsertBlk(curQueue) {
                            it.idsBinList.clear()
                            it.update()
                        }
                        expanded = false
                    })
                    if (screenMode == QueuesScreenMode.Queue) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort)) }, onClick = {
                            showSortDialog = true
                            expanded = false
                        })
                        if (showRename) DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                            showRenameQueueDialog.value = true
                            expanded = false
                        })
                        if (queueNames.size < 12) DropdownMenuItem(text = { Text(stringResource(R.string.add_queue)) }, onClick = {
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
                        if (!curQueue.keepSorted) {
                            fun toggleQL() {
                                isQueueLocked = !isQueueLocked
                                putPref(AppPrefs.prefQueueLocked, isQueueLocked)
                                dragDropEnabled = !(curQueue.keepSorted || isQueueLocked)
                                Logt(TAG, context.getString(if (isQueueLocked) R.string.queue_locked else R.string.queue_unlocked))
                                expanded = false
                            }
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.lock_queue))
                                    Checkbox(checked = isQueueLocked, onCheckedChange = { toggleQL() })
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
        Column {
            TitleSummarySwitchRow(R.string.keep_queue_sorted, R.string.keep_queue_sorted_sum, curQueue.keepSorted) { v ->
                upsertBlk(curQueue) { it.keepSorted = v }
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
                            upsertBlk(curQueue) {
                                it.enqueueLocation = location.code
                            }
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

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Logd(TAG, "Scaffold screenMode: $screenMode")
        when (screenMode) {
            QueuesScreenMode.Feed -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) { FeedsGrid() }
            QueuesScreenMode.Settings -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) { Settings() }
            else -> {
                val episodesChange by episodesFlow.collectAsState(initial = null)
                val episodes = episodesChange?.list ?: emptyList()
                LaunchedEffect(episodesChange?.list) {
                    Logd(TAG, "LaunchedEffect(results)")
                    scope.launch(Dispatchers.IO) {
                        val info = buildListInfo(episodes)
                        withContext(Dispatchers.Main) {
                            listInfoText = info
                            infoBarText.value = "$listInfoText $feedOperationText"
                        }
                    }
                    reorderQueue(true)
//                    runOnIOScope {
//                        curQueue.episodes.clear()
//                        curQueue = upsert(curQueue) {
//                            it.episodeIds.clear()
//                            for (e in episodes) it.episodeIds.add(e.id)
//                            it.update()
//                        }
//                        EventFlow.postEvent(FlowEvent.QueueEvent.sorted(curQueue.episodes))
//                    }
                }
                if (screenMode == QueuesScreenMode.Bin) {
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        infoBarText.value = "$listInfoText $feedOperationText"
                        InforBar(infoBarText, swipeActionsBin)
                        EpisodeLazyColumn(context as MainActivity, episodes, swipeActions = swipeActionsBin)
                    }
                } else {
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        InforBar(infoBarText, swipeActions)
                        EpisodeLazyColumn(context as MainActivity, episodes,
                            isDraggable = dragDropEnabled, dragCB = { iFrom, iTo ->
                                runOnIOScope {
                                    moveInQueue(iFrom, iTo, true)
                                    withContext(Dispatchers.Main) { dragged++ }
                                }
                            },
                            swipeActions = swipeActions,
                        )
                    }
                }
            }
        }
    }
}


