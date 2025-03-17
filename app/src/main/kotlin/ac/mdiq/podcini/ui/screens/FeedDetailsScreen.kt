package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.utils.HtmlToPlainText
import ac.mdiq.podcini.storage.database.Episodes.indexOfItem
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.updateFeedFull
import ac.mdiq.podcini.storage.database.Feeds.updateFeedDownloadURL
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.fromCode
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.FeedFunding
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.isBSExpanded
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Screens
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.compose.EpisodesFilterDialog
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.LargeTextEditingDialog
import ac.mdiq.podcini.ui.compose.RemoveFeedDialog
import ac.mdiq.podcini.ui.compose.RenameOrCreateSyntheticFeed
import ac.mdiq.podcini.ui.compose.VMS_CHUNK_SIZE
import ac.mdiq.podcini.ui.compose.buildListInfo
import ac.mdiq.podcini.ui.compose.stopMonitor
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.ui.utils.setOnlineSearchTerms
import ac.mdiq.podcini.ui.utils.setSearchTerms
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.MiscFormatter.fullDateTimeString
import ac.mdiq.podcini.util.ShareUtils
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import java.util.Date
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils


class FeedDetailsVM(val context: Context, val lcScope: CoroutineScope) {
    internal var swipeActions: SwipeActions
    internal var leftActionState = mutableStateOf<SwipeAction>(NoAction())
    internal var rightActionState = mutableStateOf<SwipeAction>(NoAction())
    internal var showSwipeActionsDialog by mutableStateOf(false)

    internal var feedID: Long = 0
    internal var feed by mutableStateOf<Feed?>(null)

//    internal var screenMode by mutableStateOf<ScreenMode>(ScreenMode.List)

    internal var isFiltered by mutableStateOf(false)

    internal var listInfoText = ""
    private var updateInfoText = ""
    internal var infoBarText = mutableStateOf("")
    //        internal var displayUpArrow by mutableStateOf(false)
    private var headerCreated = false
    internal var rating by mutableStateOf(Rating.UNRATED.code)

    internal val episodes = mutableStateListOf<Episode>()
    internal val vms = mutableStateListOf<EpisodeVM>()

    internal var enableFilter: Boolean = true
    internal var filterButtonColor = mutableStateOf(Color.White)

    internal var showRemoveFeedDialog by mutableStateOf(false)
    internal var showFilterDialog by mutableStateOf(false)
    internal var showRenameDialog by mutableStateOf(false)
    internal var showSortDialog by mutableStateOf(false)
    internal var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)
    internal var layoutModeIndex by mutableIntStateOf(0)

    private var onInit: Boolean = true
    private var filterJob: Job? = null

    internal var isCallable by mutableStateOf(false)
    internal var txtvAuthor by mutableStateOf("")
    internal var txtvUrl by mutableStateOf<String?>(null)
    internal val showConnectLocalFolderConfirm = mutableStateOf(false)

    init {
        sortOrder = feed?.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
        swipeActions = SwipeActions(context, TAG)
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
        layoutModeIndex = if (feed?.useWideLayout == true) 1 else 0
    }
    private var eventSink: Job? = null
    private var eventStickySink: Job? = null
//    private var eventKeySink: Job? = null
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
//        eventKeySink?.cancel()
//        eventKeySink = null
    }
    internal fun procFlowEvents() {
        if (eventSink == null) eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.PlayEvent -> if (feedScreenMode == FeedScreenMode.List) onPlayEvent(event)
                    is FlowEvent.FeedChangeEvent -> if (feed?.id == event.feed.id) loadFeed(true)
                    is FlowEvent.FeedListEvent -> if (feed != null && event.contains(feed!!)) loadFeed(true)
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lcScope.launch {
            EventFlow.stickyEvents.drop(1).collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> if (feedScreenMode == FeedScreenMode.List) onEpisodeDownloadEvent(event)
                    is FlowEvent.FeedUpdatingEvent -> if (feedScreenMode == FeedScreenMode.List) onFeedUpdateRunningEvent(event)
                    else -> {}
                }
            }
        }
//        if (eventKeySink == null) eventKeySink = lifecycleScope.launch {
//            EventFlow.keyEvents.collectLatest { event ->
//                Logd(TAG, "Received key event: $event, ignored")
////                onKeyUp(event)
//            }
//        }
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        if (feed != null) {
            val pos: Int = vms.indexOfItem(event.episode.id)
            if (pos >= 0) {
                if (!isFilteredOut(event.episode) && pos < vms.size)  vms[pos].isPlayingState = event.isPlaying()
                if (event.isPlaying()) upsertBlk(feed!!) { it.lastPlayed = Date().time }
            }
        }
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEpisodeDownloadEvent() called with ${event.TAG}")
        if (feed == null || episodes.isEmpty()) return
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = vms.indexOfItem(url)
            if (pos >= 0 && pos < vms.size) {
                Logd(TAG, "onEpisodeDownloadEvent $pos ${event.map[url]?.state} ${episodes[pos].downloaded} ${episodes[pos].title}")
                vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
            }
        }
    }

    private fun onFeedUpdateRunningEvent(event: FlowEvent.FeedUpdatingEvent) {
        updateInfoText = if (event.isRunning) context.getString(R.string.refreshing_label) else ""
        infoBarText.value = "$listInfoText $updateInfoText"
        if (!event.isRunning) loadFeed(true)
    }

    internal fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
    }

    private fun refreshHeaderView() {
        if (feed == null) {
            Loge(TAG, "Unable to refresh header view")
            return
        }
        isFiltered = !feed?.filterString.isNullOrEmpty() && feed!!.episodeFilter.propertySet.isNotEmpty()
        filterButtonColor.value = if (enableFilter) if (isFiltered) Color.Green else Color.White else Color.Red
        if (!headerCreated) headerCreated = true
        listInfoText = buildListInfo(context, episodes)
        infoBarText.value = "$listInfoText $updateInfoText"
    }

    private fun isFilteredOut(episode: Episode): Boolean {
        if (enableFilter && !feed?.filterString.isNullOrEmpty()) {
            val episodes_ = realm.query(Episode::class).query("feedId == ${feed!!.id}").query(feed!!.episodeFilter.queryString()).find()
            if (!episodes_.contains(episode)) {
                episodes.remove(episode)
                return true
            }
            return false
        }
        return false
    }

    private var loadJob: Job? = null
    internal fun loadFeed(force: Boolean = false) {
        if (feedScreenMode == FeedScreenMode.Info) {
            feed = realm.query(Feed::class).query("id == $0", feedOnDisplay.id).first().find()
            rating = feed?.rating ?: Rating.UNRATED.code
            return
        }
        Logd(TAG, "loadFeed called $feedID")
        if (loadJob != null) {
            if (force) {
                loadJob?.cancel()
                stopMonitor(vms)
                vms.clear()
            } else return
        }
        loadJob = lcScope.launch {
            try {
                feed = withContext(Dispatchers.IO) {
                    val feed_ = getFeed(feedID)
                    if (feed_ != null) {
                        Logd(TAG, "loadItems feed_.episodes.size: ${feed_.episodes.size}")
                        val eListTmp = mutableListOf<Episode>()
                        if (enableFilter && feed_.filterString.isNotEmpty()) {
                            Logd(TAG, "episodeFilter: ${feed_.episodeFilter.queryString()}")
                            val episodes_ = realm.query(Episode::class).query("feedId == ${feed_.id}").query(feed_.episodeFilter.queryString()).find()
                            eListTmp.addAll(episodes_)
                        } else eListTmp.addAll(feed_.episodes)
                        sortOrder = feed_.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
                        getPermutor(sortOrder).reorder(eListTmp)
                        episodes.clear()
                        episodes.addAll(eListTmp)
                        withContext(Dispatchers.Main) {
                            layoutModeIndex = if (feed_.useWideLayout == true) 1 else 0
                            stopMonitor(vms)
                            vms.clear()
                            buildMoreItems()
                        }
                        if (onInit) {
                            var hasNonMediaItems = false
                            // TODO: ensure
                            for (item in episodes) {
                                if (item.downloadUrl == null) {
                                    hasNonMediaItems = true
                                    break
                                }
                            }
                            if (hasNonMediaItems) {
                                lcScope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (!FEObj.ttsReady) {
                                            initializeTTS(context)
                                            semaphore.acquire()
                                        }
                                    }
                                }
                            }
                            onInit = false
                        }
                    }
                    feed_
                }
                withContext(Dispatchers.Main) {
                    Logd(TAG, "loadItems subscribe called ${feed?.title}")
                    rating = feed?.rating ?: Rating.UNRATED.code
                    refreshHeaderView()
                }
            } catch (e: Throwable) {
                feed = null
                Logs(TAG, e)
            } catch (e: Exception) { Logs(TAG, e) }
        }.apply { invokeOnCompletion { loadJob = null } }
    }
    fun buildMoreItems() {
//        val nextItems = (vms.size until min(vms.size + VMS_CHUNK_SIZE, episodes.size)).map { EpisodeVM(episodes[it], TAG) }
        val nextItems = (vms.size until (vms.size + VMS_CHUNK_SIZE).coerceAtMost(episodes.size)).map { EpisodeVM(episodes[it], TAG) }
        if (nextItems.isNotEmpty()) vms.addAll(nextItems)
    }
    private val semaphore = Semaphore(0)
    private fun initializeTTS(context: Context) {
        Logd(TAG, "starting TTS")
        if (FEObj.tts == null) {
            FEObj.tts = TextToSpeech(context) { status: Int ->
                if (status == TextToSpeech.SUCCESS) {
                    FEObj.ttsReady = true
                    semaphore.release()
                    Loge(TAG, "TTS init success")
                } else Loge(TAG, context.getString(R.string.tts_init_failed))
            }
        }
    }

    internal fun filterLongClick() {
        if (feed == null) return
        enableFilter = !enableFilter
        if (filterJob != null) {
            Logd(TAG, "filterLongClick")
            filterJob?.cancel()
            stopMonitor(vms)
            vms.clear()
        }
        filterJob = lcScope.launch {
            val eListTmp = mutableListOf<Episode>()
            withContext(Dispatchers.IO) {
                if (enableFilter) {
                    filterButtonColor.value = if (isFiltered) Color.Green else Color.White
                    val episodes_ = realm.query(Episode::class).query("feedId == ${feed!!.id}").query(feed!!.episodeFilter.queryString()).find()
                    eListTmp.addAll(episodes_)
                } else {
                    filterButtonColor.value = Color.Red
                    eListTmp.addAll(feed!!.episodes)
                }
                getPermutor(fromCode(feed?.sortOrderCode ?: 0)).reorder(eListTmp)
                episodes.clear()
                episodes.addAll(eListTmp)
            }
            withContext(Dispatchers.Main) {
                stopMonitor(vms)
                vms.clear()
                for (e in eListTmp) vms.add(EpisodeVM(e, TAG))
            }
        }.apply { invokeOnCompletion { filterJob = null } }
    }

    internal fun addLocalFolderResult(uri: Uri?) {
        if (uri == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    requireNotNull(documentFile) { "Unable to retrieve document tree" }
                    feed?.downloadUrl = Feed.PREFIX_LOCAL_FOLDER + uri.toString()
                    if (feed != null) updateFeedFull(context, feed!!, true)
                }
                withContext(Dispatchers.Main) { Logt(TAG, context.getString(R.string.OK)) }
            } catch (e: Throwable) { withContext(Dispatchers.Main) { Loge(TAG, e.localizedMessage?:"No message") } }
        }
    }

}

enum class FeedScreenMode {
    List,
    Info
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedDetailsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember(feedOnDisplay.id) { FeedDetailsVM(context, scope) }

    val addLocalFolderLauncher: ActivityResultLauncher<Uri?> = rememberLauncherForActivityResult(contract = AddLocalFolder()) { uri: Uri? -> vm.addLocalFolderResult(uri) }

    //        val displayUpArrow by remember { derivedStateOf { navController.backQueue.size > 1 } }
//        var upArrowVisible by rememberSaveable { mutableStateOf(displayUpArrow) }
//        LaunchedEffect(navController.backQueue) { upArrowVisible = displayUpArrow }

    var displayUpArrow by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    vm.feed = feedOnDisplay
                    vm.feedID = vm.feed?.id ?: 0
//                    val testNum = 1
//                    val eList = realm.query(Episode::class).query("feedId == ${vm.feedID} AND playState == ${PlayState.SOON.code} SORT(pubDate DESC) LIMIT($testNum)").find()
//                    Logd(TAG, "test eList: ${eList.size}")
                    vm.txtvAuthor = vm.feed?.author ?: ""
                    vm.txtvUrl = vm.feed?.downloadUrl
                    if (!vm.feed?.link.isNullOrEmpty()) vm.isCallable = IntentUtils.isCallable(context, Intent(Intent.ACTION_VIEW, Uri.parse(vm.feed!!.link)))
                    saveLastNavScreen(TAG, vm.feedID.toString())
                    lifecycleOwner.lifecycle.addObserver(vm.swipeActions)
                    vm.refreshSwipeTelltale()
                }
                Lifecycle.Event.ON_START -> {
                    vm.loadFeed()
                    vm.procFlowEvents()
                }
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> vm.cancelFlowEvents()
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.feed = null
            vm.episodes.clear()
            stopMonitor(vm.vms)
            vm.vms.clear()
            FEObj.tts?.stop()
            FEObj.tts?.shutdown()
            FEObj.ttsWorking = false
            FEObj.ttsReady = false
            FEObj.tts = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
//    BackHandler { mainNavController.popBackStack() }

    ComfirmDialog(0, stringResource(R.string.reconnect_local_folder_warning), vm.showConnectLocalFolderConfirm) {
        try { addLocalFolderLauncher.launch(null) } catch (e: ActivityNotFoundException) { Loge(TAG, "No activity found. Should never happen...") }
    }

    var showEditConfirmDialog by remember { mutableStateOf(false) }
    var editedUrl by remember { mutableStateOf("") }
    @Composable
    fun EditUrlSettingsDialog(onDismiss: () -> Unit) {
        var url by remember { mutableStateOf(vm.feed?.downloadUrl ?: "") }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.edit_url_menu)) },
            text = {
                TextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    editedUrl = url
                    showEditConfirmDialog = true
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
        )
    }
    @Composable
    fun EditConfirmDialog(onDismiss: () -> Unit) {
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.edit_url_menu)) },
            text = { Text(stringResource(R.string.edit_url_confirmation_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    runOnIOScope {
                        try {
                            updateFeedDownloadURL(vm.feed?.downloadUrl ?: "", editedUrl)
                            vm.feed?.downloadUrl = editedUrl
                            runOnce(context, vm.feed)
                        } catch (e: ExecutionException) { throw RuntimeException(e) } catch (e: InterruptedException) { throw RuntimeException(e) }
                        vm.feed?.downloadUrl = editedUrl
                        withContext(Dispatchers.Main) { vm.txtvUrl = vm.feed?.downloadUrl }
                    }
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var showEditUrlSettingsDialog by remember { mutableStateOf(false) }
    if (showEditUrlSettingsDialog) EditUrlSettingsDialog { showEditUrlSettingsDialog = false }
    if (showEditConfirmDialog) EditConfirmDialog { showEditConfirmDialog = false }

    var showChooseRatingDialog by remember { mutableStateOf(false) }
    if (showChooseRatingDialog) ChooseRatingDialog(listOf(vm.feed!!)) {
        showChooseRatingDialog = false
        vm.feed = realm.query(Feed::class).query("id == $0", vm.feed!!.id).first().find()!!
        vm.rating = vm.feed!!.rating
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedDetailsHeader() {
        val textColor = MaterialTheme.colorScheme.onSurface
        ConstraintLayout(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            val (bgImage, bgColor, controlRow, imgvCover) = createRefs()
            AsyncImage(model = vm.feed?.imageUrl?:"", contentDescription = "bgImage", contentScale = ContentScale.FillBounds, error = painterResource(R.drawable.teaser),
                modifier = Modifier.fillMaxSize().blur(radiusX = 15.dp, radiusY = 15.dp).constrainAs(bgImage) {
                    bottom.linkTo(parent.bottom)
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end) })
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)).constrainAs(bgColor) {
                bottom.linkTo(parent.bottom)
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end) })
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().constrainAs(imgvCover) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints
            }) {
                AsyncImage(model = vm.feed?.imageUrl ?: "", alignment = Alignment.TopStart, contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier.width(80.dp).height(80.dp).clickable(onClick = {
                        if (vm.feed != null) {
                            feedScreenMode = if (feedScreenMode == FeedScreenMode.Info) FeedScreenMode.List else FeedScreenMode.Info
                            if (vm.episodes.isEmpty()) vm.loadFeed(true)
                        }
                    }))
                Column(modifier = Modifier.padding(start = 10.dp, top = 4.dp)) {
                    Text(vm.feed?.title ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(vm.feed?.author ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ratingIconRes by derivedStateOf { Rating.fromCode(vm.rating).res }
                        IconButton(onClick = { showChooseRatingDialog = true }) { Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.padding(start = 5.dp).background(MaterialTheme.colorScheme.tertiaryContainer)) }
                        Spacer(modifier = Modifier.weight(0.3f))
                        if (feedScreenMode == FeedScreenMode.List) Text(vm.episodes.size.toString() + " / " + vm.feed?.episodes?.size?.toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        else Text((vm.feed?.episodes?.size ?: 0).toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar(displayUpArrow: Boolean) {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        val textColor = MaterialTheme.colorScheme.onSurface
        TopAppBar(title = { Text("") },
            navigationIcon = if (displayUpArrow) {
                { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { MainActivity.openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }
            },
            actions = {
                IconButton(onClick = {
                    mainNavController.navigate(Screens.Queues.name)
                    isBSExpanded = false
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), contentDescription = "queue") }
                if (vm.feed != null) IconButton(onClick = {
                    setSearchTerms(feed = vm.feed)
                    mainNavController.navigate(Screens.Search.name)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                if (!vm.feed?.link.isNullOrBlank() && vm.isCallable) IconButton(onClick = { IntentUtils.openInBrowser(context, vm.feed!!.link!!) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "web") }
                if (feedScreenMode == FeedScreenMode.List) {
                    IconButton(onClick = { vm.showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "butSort") }
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter_white), tint = if (vm.filterButtonColor.value == Color.White) textColor else vm.filterButtonColor.value, contentDescription = "butFilter",
                        modifier = Modifier.padding(horizontal = 5.dp).combinedClickable(onClick = { if (vm.enableFilter && vm.feed != null) vm.showFilterDialog = true }, onLongClick = { vm.filterLongClick() }))
                }
                IconButton(onClick = {
                    if (vm.feed != null) {
                        feedOnDisplay = vm.feed!!
                        mainNavController.navigate(Screens.FeedSettings.name)
                    }
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings_white), contentDescription = "butShowSettings")}
                if (vm.feed != null) {
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                            ShareUtils.shareFeedLinkNew(context, vm.feed!!)
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.rename_feed_label)) }, onClick = {
                            vm.showRenameDialog = true
                            expanded = false
                        })
                        if (vm.feed?.isLocalFeed == true) DropdownMenuItem(text = { Text(stringResource(R.string.reconnect_local_folder)) }, onClick = {
                            vm.showConnectLocalFolderConfirm.value = true
                            expanded = false
                        }) else DropdownMenuItem(text = { Text(stringResource(R.string.edit_url_menu)) }, onClick = {
                            showEditUrlSettingsDialog = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.fetch_size)) }, onClick = {
                            scope.launch {
                                for (e in vm.episodes) e.fetchMediaSize()
                                vm.loadFeed(true)
                            }
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                            FeedUpdateManager.runOnceOrAsk(context, vm.feed)
                            expanded = false
                        })
                        if (vm.feed?.isPaged == true) DropdownMenuItem(text = { Text(stringResource(R.string.load_complete_feed)) }, onClick = {
                            runOnIOScope {
                                try {
                                    if (vm.feed != null) {
                                        val feed_ = upsert(vm.feed!!) {
                                            it.nextPageLink = it.downloadUrl
                                            it.pageNr = 0
                                        }
                                        runOnce(context, feed_)
                                    }
                                } catch (e: ExecutionException) { throw RuntimeException(e) } catch (e: InterruptedException) { throw RuntimeException(e) }
                            }
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_feed_label)) }, onClick = {
                            vm.showRemoveFeedDialog = true
                            expanded = false
                        })
                    }
                }
            }
        )
    }

    if (vm.showRemoveFeedDialog) RemoveFeedDialog(listOf(vm.feed!!), onDismissRequest = { vm.showRemoveFeedDialog = false }) {
        mainNavController.navigate("DefaultPage")
    }
    if (vm.showFilterDialog) EpisodesFilterDialog(filter = vm.feed!!.episodeFilter,
        onDismissRequest = { vm.showFilterDialog = false }) { filter ->
        if (vm.feed != null) {
            Logd(TAG, "persist Episode Filter(): feedId = [${vm.feed?.id}], filterValues = [${filter.propertySet}]")
            runOnIOScope {
                val feed_ = realm.query(Feed::class, "id == ${vm.feed!!.id}").first().find()
                if (feed_ != null) vm.feed = upsert(feed_) { it.episodeFilter = filter }
            }
        }
    }
    if (vm.showRenameDialog) RenameOrCreateSyntheticFeed(vm.feed) { vm.showRenameDialog = false }
    if (vm.showSortDialog) EpisodeSortDialog(initOrder = vm.sortOrder, onDismissRequest = { vm.showSortDialog = false }) { sortOrder_, _ ->
        if (vm.feed != null) {
            Logd(TAG, "persist Episode SortOrder_")
            vm.sortOrder = sortOrder_
            runOnIOScope {
                val feed_ = realm.query(Feed::class, "id == ${vm.feed!!.id}").first().find()
                if (feed_ != null) vm.feed = upsert(feed_) { it.sortOrder = sortOrder_ }
            }
        }
    }
    if (vm.showSwipeActionsDialog) SwipeActionsSettingDialog(vm.swipeActions, onDismissRequest = { vm.showSwipeActionsDialog = false }) { actions ->
        vm.swipeActions.actions = actions
        vm.refreshSwipeTelltale()
    }

    @Composable
    fun DetailUI() {
        val context = LocalContext.current
        val scrollState = rememberScrollState()
        var showEditComment by remember { mutableStateOf(false) }
        val localTime = remember { System.currentTimeMillis() }
        var editCommentText by remember { mutableStateOf(TextFieldValue(vm.feed?.comment ?: "")) }
        var commentTextState by remember { mutableStateOf(TextFieldValue(vm.feed?.comment ?: "")) }
        if (showEditComment) LargeTextEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = {showEditComment = false},
            onSave = {
                commentTextState = editCommentText
                if (vm.feed != null) {
                    runOnIOScope {
                        vm.feed = upsert(vm.feed!!) {
                            it.comment = editCommentText.text
                            it.commentTime = localTime
                        }
                        vm.rating = vm.feed!!.rating
                    }
                }
            })
        var showFeedStats by remember { mutableStateOf(false) }
        if (showFeedStats) FeedStatisticsDialog(vm.feed?.title?: "No title", vm.feed?.id?:0, 0, Long.MAX_VALUE) { showFeedStats = false }

        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Text(vm.feed?.title ?:"", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
            Text(vm.feed?.author ?:"", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Text(HtmlToPlainText.getPlainText(vm.feed?.description?:""), color = textColor, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.my_opinion_label) + if (commentTextState.text.isBlank()) " (Add)" else "",
                color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom,
                modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable {
                    editCommentText = TextFieldValue((if (vm.feed?.comment.isNullOrBlank()) "" else vm.feed!!.comment + "\n") + fullDateTimeString(localTime) + ":\n")
                    showEditComment = true
                })
            if (commentTextState.text.isNotBlank()) Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))

            Text(stringResource(R.string.statistics_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row {
                TextButton({ showFeedStats = true }) { Text(stringResource(R.string.this_podcast)) }
                Spacer(Modifier.width(20.dp))
                TextButton({ mainNavController.navigate(Screens.Statistics.name) }) { Text(stringResource(R.string.all_podcasts)) }
            }
            if (vm.feed?.isSynthetic() == false) {
                TextButton(modifier = Modifier.padding(top = 10.dp), onClick = {
                    setOnlineSearchTerms(CombinedSearcher::class.java, "${vm.txtvAuthor} podcasts")
                    mainNavController.navigate(Screens.SearchResults.name)
                }) { Text(stringResource(R.string.feeds_related_to_author)) }
                Text(stringResource(R.string.url_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(text = vm.txtvUrl ?: "", color = textColor, modifier = Modifier.clickable {
                    if (!vm.feed?.downloadUrl.isNullOrBlank()) {
                        val url: String = vm.feed!!.downloadUrl!!
                        val clipData: ClipData = ClipData.newPlainText(url, url)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(clipData)
                        Logt(TAG, context.getString(R.string.copied_to_clipboard))
                    }
                })
                if (!vm.feed?.paymentLinkList.isNullOrEmpty()) {
                    Text(stringResource(R.string.support_funding_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    fun fundingText(): String {
                        val fundingList: MutableList<FeedFunding> = vm.feed!!.paymentLinkList
                        // Filter for duplicates, but keep items in the order that they have in the feed.
                        val i: MutableIterator<FeedFunding> = fundingList.iterator()
                        while (i.hasNext()) {
                            val funding: FeedFunding = i.next()
                            for (other in fundingList) {
                                if (other.url == funding.url) {
                                    if (other.content != null && funding.content != null && other.content!!.length > funding.content!!.length) {
                                        i.remove()
                                        break
                                    }
                                }
                            }
                        }
                        val str = StringBuilder()
                        for (funding in fundingList) {
                            str.append(if (funding.content == null || funding.content!!.isEmpty()) context.resources.getString(
                                R.string.support_podcast)
                            else funding.content).append(" ").append(funding.url)
                            str.append("\n")
                        }
                        return StringBuilder(StringUtils.trim(str.toString())).toString()
                    }
                    val fundText = remember { fundingText() }
                    Text(fundText, color = textColor)
                }
            }
        }
    }

    fun multiSelectCB(index: Int, aboveOrBelow: Int): List<Episode> {
        return when (aboveOrBelow) {
            0 -> vm.episodes
            -1 -> if (index < vm.episodes.size) vm.episodes.subList(0, index+1) else vm.episodes
            1 -> if (index < vm.episodes.size) vm.episodes.subList(index, vm.episodes.size) else vm.episodes
            else -> listOf()
        }
    }

    vm.swipeActions.ActionOptionsDialog()
    Scaffold(topBar = { MyTopAppBar(displayUpArrow) }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            FeedDetailsHeader()
            if (feedScreenMode == FeedScreenMode.List) {
                InforBar(vm.infoBarText, leftAction = vm.leftActionState, rightAction = vm.rightActionState, actionConfig = { vm.showSwipeActionsDialog = true })
                EpisodeLazyColumn(context, vms = vm.vms, feed = vm.feed, layoutMode = vm.layoutModeIndex,
                    buildMoreItems = { vm.buildMoreItems() },
                    refreshCB = { FeedUpdateManager.runOnceOrAsk(context, vm.feed) },
                    leftSwipeCB = {
                        if (vm.leftActionState.value is NoAction) vm.showSwipeActionsDialog = true
                        else vm.leftActionState.value.performAction(it)
                    },
                    rightSwipeCB = {
                        Logd(TAG, "vm.rightActionState: ${vm.rightActionState.value.getId()}")
                        if (vm.rightActionState.value is NoAction) vm.showSwipeActionsDialog = true
                        else vm.rightActionState.value.performAction(it)
                    },
                    multiSelectCB = { index, aboveOrBelow -> multiSelectCB(index, aboveOrBelow) }
                )
            } else DetailUI()
        }
    }
}

private val TAG = Screens.FeedDetails.name
const val ARGUMENT_FEED_ID = "argument.ac.mdiq.podcini.feed_id"
private const val KEY_UP_ARROW = "up_arrow"

object FEObj {
    var tts: TextToSpeech? = null
    var ttsReady = false
    var ttsWorking = false
}