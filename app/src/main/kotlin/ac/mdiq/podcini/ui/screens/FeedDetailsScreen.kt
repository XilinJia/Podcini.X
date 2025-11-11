package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.utils.HtmlToPlainText
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.Episodes.buildListInfo
import ac.mdiq.podcini.storage.database.Episodes.getHistory
import ac.mdiq.podcini.storage.database.Feeds.FeedAssistant
import ac.mdiq.podcini.storage.database.Feeds.feedOperationText
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.updateFeedDownloadURL
import ac.mdiq.podcini.storage.database.Feeds.updateFeedFull
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.storage.utils.FeedFunding
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.isBSExpanded
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.compose.EpisodesFilterDialog
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.LargeTextEditingDialog
import ac.mdiq.podcini.ui.compose.LayoutMode
import ac.mdiq.podcini.ui.compose.RemoveFeedDialog
import ac.mdiq.podcini.ui.compose.RenameOrCreateSyntheticFeed
import ac.mdiq.podcini.ui.compose.VMS_CHUNK_SIZE
import ac.mdiq.podcini.ui.compose.episodeSortOrder
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
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import ac.mdiq.podcini.util.MiscFormatter.fullDateTimeString
import ac.mdiq.podcini.util.ShareUtils.shareLink
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.util.Date
import java.util.concurrent.ExecutionException


class FeedDetailsVM(val context: Context, val lcScope: CoroutineScope) {
    internal var swipeActions: SwipeActions

    var feedLoaded by mutableIntStateOf(0)

    internal var feedID: Long = 0
    internal var feed by mutableStateOf<Feed?>(null)

    internal var isFiltered by mutableStateOf(false)

    internal var listInfoText by mutableStateOf("")
    internal var infoBarText = mutableStateOf("")
    private var headerCreated = false
    internal var rating by mutableIntStateOf(Rating.UNRATED.code)

    internal var score by mutableIntStateOf(-1000)
    internal var scoreCount by mutableIntStateOf(0)

    internal val episodes = mutableStateListOf<Episode>()
    internal val vms = mutableStateListOf<EpisodeVM>()

    internal var showRemoveFeedDialog by mutableStateOf(false)
    internal var showFilterDialog by mutableStateOf(false)
    internal var showRenameDialog by mutableStateOf(false)
    internal var showSortDialog by mutableStateOf(false)
    internal var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)
    internal var layoutModeIndex by mutableIntStateOf(LayoutMode.Normal.ordinal)

    internal var isCallable by mutableStateOf(false)
    internal var txtvAuthor by mutableStateOf("")
    internal var txtvUrl by mutableStateOf<String?>(null)
    internal val showConnectLocalFolderConfirm = mutableStateOf(false)

    init {
        sortOrder = feed?.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
        swipeActions = SwipeActions(context, TAG)
        layoutModeIndex = if (feed?.useWideLayout == true) LayoutMode.WideImage.ordinal else LayoutMode.Normal.ordinal
    }
    private var eventSink: Job? = null
    private var eventStickySink: Job? = null
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
    }
    internal fun procFlowEvents() {
        if (eventSink == null) eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedChangeEvent -> if (feed?.id == event.feed.id) loadFeed()
                    is FlowEvent.FeedListEvent -> if (feed != null && event.contains(feed!!)) loadFeed()
                    else -> {}
                }
            }
        }
    }

    private fun computeScore() {
        if (!feed?.episodes.isNullOrEmpty()) {
            var sumR = 0.0
            scoreCount = 0
            for (e in feed!!.episodes) {
                if (e.playState >= EpisodeState.PROGRESS.code) {
                    scoreCount++
                    if (e.rating != Rating.UNRATED.code) sumR += e.rating
                    if (e.playState >= EpisodeState.SKIPPED.code) sumR += - 0.5 + 1.0 * e.playedDuration / e.duration
                    else if (e.playState in listOf(EpisodeState.AGAIN.code, EpisodeState.FOREVER.code)) sumR += 0.5
                }
            }
            score = if (scoreCount > 0) (100 * sumR / scoreCount / Rating.SUPER.code).toInt() else -1000
        }
    }

    internal suspend fun assembleList(feed_:Feed?) {
        if (feed_ == null) return
        val eListTmp = mutableListOf<Episode>()
        when {
            showHistory -> eListTmp.addAll(getHistory(0, Int.MAX_VALUE, feed!!.id))
            enableFilter && feed_.filterString.isNotBlank() -> {
                val qstr = feed_.episodeFilter.queryString()
                Logd(TAG, "episodeFilter: $qstr")
                val episodes_ = realm.query(Episode::class).query("feedId == ${feed_.id} AND $qstr").find()
                eListTmp.addAll(episodes_)
            }
            else -> eListTmp.addAll(feed_.episodes)
        }
        if (!showHistory) {
            Logd(TAG, "loadItems eListTmp.size: ${eListTmp.size}")
            sortOrder = feed_.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
            getPermutor(sortOrder).reorder(eListTmp)
            Logd(TAG, "loadItems eListTmp.size1: ${eListTmp.size}")
        }
        withContext(Dispatchers.Main) {
            layoutModeIndex = if (feed_.useWideLayout) LayoutMode.WideImage.ordinal else LayoutMode.Normal.ordinal
            Logd(TAG, "loadItems subscribe called ${feed_.title}")
            rating = feed_.rating
            computeScore()
            isFiltered = feed_.filterString.isNotBlank() && feed_.episodeFilter.propertySet.isNotEmpty()
            if (!headerCreated) headerCreated = true
            listInfoText = buildListInfo(episodes)
            infoBarText.value = "$listInfoText $feedOperationText"
            episodes.clear()
            episodes.addAll(eListTmp)
            vms.clear()
            buildMoreItems()
        }
    }

    internal fun loadFeed() {
        if (feedScreenMode == FeedScreenMode.Info) {
            feed = realm.query(Feed::class).query("id == $0", feedOnDisplay.id).first().find()
            rating = feed?.rating ?: Rating.UNRATED.code
            computeScore()
            return
        }
        Logd(TAG, "loadFeed called $feedID")
        feed = getFeed(feedID)
        if (feed != null) feedLoaded++
    }
    fun buildMoreItems() {
        Logd(TAG, "buildMoreItems ${vms.size}")
        val moreVMs = (vms.size until (vms.size + VMS_CHUNK_SIZE).coerceAtMost(episodes.size)).map { EpisodeVM(episodes[it], TAG) }
        if (moreVMs.isNotEmpty()) vms.addAll(moreVMs)
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
                    if (feed != null) updateFeedFull(context, feed!!, removeUnlistedItems = true)
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
    val navController = LocalNavController.current
    val vm = remember(feedOnDisplay.id) { FeedDetailsVM(context, scope) }

    val addLocalFolderLauncher: ActivityResultLauncher<Uri?> = rememberLauncherForActivityResult(contract = AddLocalFolder()) { uri: Uri? -> vm.addLocalFolderResult(uri) }

    DisposableEffect(lifecycleOwner) {
        Logd(TAG, "in DisposableEffect")
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect LifecycleEventObserver: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    vm.feed = feedOnDisplay
                    vm.feedID = vm.feed?.id ?: 0
//                    val testNum = 1
//                    val eList = realm.query(Episode::class).query("feedId == ${vm.feedID} AND playState == ${PlayState.SOON.code} SORT(pubDate DESC) LIMIT($testNum)").find()
//                    Logd(TAG, "test eList: ${eList.size}")
                    vm.txtvAuthor = vm.feed?.author ?: ""
                    vm.txtvUrl = vm.feed?.downloadUrl
                    if (!vm.feed?.link.isNullOrEmpty()) vm.isCallable = IntentUtils.isCallable(context, Intent(Intent.ACTION_VIEW, vm.feed!!.link!!.toUri()))
                    saveLastNavScreen(TAG, vm.feedID.toString())
                    lifecycleOwner.lifecycle.addObserver(vm.swipeActions)
                    vm.loadFeed()
                    episodeSortOrder = vm.sortOrder
                }
                Lifecycle.Event.ON_START -> vm.procFlowEvents()
                Lifecycle.Event.ON_RESUME -> {
//                    if (episodeChangedWhenScreenOff) vm.loadFeed()
                }
                Lifecycle.Event.ON_STOP -> {
//                    vm.cancelFlowEvents()
                }
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            Logd(TAG, "DisposableEffect onDispose")
            vm.cancelFlowEvents()
            vm.feed = null
            vm.episodes.clear()
            vm.vms.clear()
            TTSObj.tts?.stop()
            TTSObj.tts?.shutdown()
            TTSObj.ttsWorking = false
            TTSObj.ttsReady = false
            TTSObj.tts = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
//    BackHandler { mainNavController.popBackStack() }

    ComfirmDialog(0, stringResource(R.string.reconnect_local_folder_warning), vm.showConnectLocalFolderConfirm) {
        try { addLocalFolderLauncher.launch(null) } catch (e: ActivityNotFoundException) { Logs(TAG, e, "No activity found. Should never happen...") }
    }

    var showEditConfirmDialog by remember { mutableStateOf(false) }
    var editedUrl by remember { mutableStateOf("") }
    @Composable
    fun EditUrlSettingsDialog(onDismiss: () -> Unit) {
        var url by remember { mutableStateOf(vm.feed?.downloadUrl ?: "") }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.edit_url_menu)) },
            text = { TextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth()) },
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
//                            runOnce(context, vm.feed)
                            gearbox.feedUpdater(vm.feed).startRefresh(context)
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
            val (bgImage, bgColor, imgvCover) = createRefs()
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
                            if (vm.episodes.isEmpty()) vm.loadFeed()
                        }
                    }))
                Column(modifier = Modifier.padding(start = 10.dp, top = 4.dp)) {
                    Text(vm.feed?.title ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(vm.feed?.author ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ratingIconRes by remember { derivedStateOf { Rating.fromCode(vm.rating).res } }
                        IconButton(onClick = { showChooseRatingDialog = true }) { Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.padding(start = 5.dp).background(MaterialTheme.colorScheme.tertiaryContainer)) }
                        Spacer(modifier = Modifier.weight(0.1f))
                        if (vm.score > -1000) Text((vm.score).toString() + " (" + vm.scoreCount + ")", textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.weight(0.2f))
                        if (feedScreenMode == FeedScreenMode.List) Text(vm.episodes.size.toString() + " / " + vm.feed?.episodes?.size?.toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        else Text((vm.feed?.episodes?.size ?: 0).toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        val textColor = MaterialTheme.colorScheme.onSurface
        val buttonColor = Color(0xDDFFD700)
        val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
        Box {
            TopAppBar(title = { Text("") }, navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }, actions = {
                if (feedScreenMode == FeedScreenMode.List && !showHistory) {
                    IconButton(onClick = { vm.showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "butSort") }
                    val filterButtonColor by remember { derivedStateOf { if (enableFilter) if (vm.isFiltered) buttonAltColor else textColor else Color.Red } }
                    if (vm.feed != null) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter_white), tint = filterButtonColor, contentDescription = "butFilter", modifier = Modifier.padding(horizontal = 5.dp).combinedClickable(onClick = { if (enableFilter) vm.showFilterDialog = true }, onLongClick = {
                        if (vm.isFiltered) {
                            enableFilter = !enableFilter
//                            vm.reassembleList()
                        }
                    }))
                }
                val histColor by remember(showHistory) { derivedStateOf { if (!showHistory) textColor else buttonAltColor } }
                if (feedScreenMode == FeedScreenMode.List && vm.feed != null) IconButton(onClick = {
                    showHistory = !showHistory
//                    vm.reassembleList()
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_history), tint = histColor, contentDescription = "history") }
                IconButton(onClick = {
                    val q = vm.feed?.queue
                    if (q != null && q != curQueue) curQueue = q
                    navController.navigate(Screens.Queues.name)
                    isBSExpanded = false
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), contentDescription = "queue") }
                IconButton(onClick = {
                    setSearchTerms(feed = vm.feed)
                    navController.navigate(Screens.Search.name)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                IconButton(onClick = {
                    if (vm.feed != null) {
                        feedOnDisplay = vm.feed!!
                        navController.navigate(Screens.FeedSettings.name)
                    }
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings_white), contentDescription = "butShowSettings") }
                if (vm.feed != null) {
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                        if (!vm.feed?.downloadUrl.isNullOrBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                            shareLink(context, vm.feed?.downloadUrl ?: "")
                            expanded = false
                        })
                        if (!vm.feed?.link.isNullOrBlank() && vm.isCallable) DropdownMenuItem(text = { Text(stringResource(R.string.visit_website_label)) }, onClick = {
                            IntentUtils.openInBrowser(context, vm.feed!!.link!!)
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
                        if (vm.episodes.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.fetch_size)) }, onClick = {
                            feedOperationText = context.getString(R.string.fetch_size)
                            scope.launch {
                                for (e in vm.episodes) e.fetchMediaSize(force = true)
                                //                                vm.loadFeed(true)
                                withContext(Dispatchers.Main) { feedOperationText = "" }
                            }
                            expanded = false
                        })
                        if (vm.feed != null) DropdownMenuItem(text = { Text(stringResource(R.string.clean_up)) }, onClick = {
                            feedOperationText = context.getString(R.string.clean_up)
                            runOnIOScope {
                                val f = realm.copyFromRealm(vm.feed!!)
                                FeedAssistant(f).clear()
                                upsert(f) {}
                                withContext(Dispatchers.Main) { feedOperationText = "" }
                            }
                            expanded = false
                        })
                        if (vm.feed != null) DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                            gearbox.feedUpdater(vm.feed).startRefresh(context)
                            expanded = false
                        })
                        if (vm.feed != null) DropdownMenuItem(text = { Text(stringResource(R.string.load_complete_feed)) }, onClick = {
                            gearbox.feedUpdater(vm.feed, fullUpdate = true).startRefresh(context)
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_feed_label)) }, onClick = {
                            vm.showRemoveFeedDialog = true
                            expanded = false
                        })
                    }
                }
            })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    if (vm.showRemoveFeedDialog) RemoveFeedDialog(listOf(vm.feed!!), onDismissRequest = { vm.showRemoveFeedDialog = false }) { navController.navigate(defaultScreen) }
    if (vm.showFilterDialog) EpisodesFilterDialog(filter = vm.feed!!.episodeFilter, onDismissRequest = { vm.showFilterDialog = false }) { filter ->
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
            SelectionContainer {
                Column {
                    Text(vm.feed?.title ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
                    Text(vm.feed?.author ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Text(HtmlToPlainText.getPlainText(vm.feed?.description ?: ""), color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(stringResource(R.string.my_opinion_label) + if (commentTextState.text.isBlank()) " (Add)" else "",
                color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom,
                modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable {
                    editCommentText = TextFieldValue((if (vm.feed?.comment.isNullOrBlank()) "" else vm.feed!!.comment + "\n") + fullDateTimeString(localTime) + ":\n")
                    showEditComment = true
                })
            if (commentTextState.text.isNotBlank()) SelectionContainer { Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp)) }

            Text(stringResource(R.string.statistics_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row {
                TextButton({ showFeedStats = true }) { Text(stringResource(R.string.this_podcast)) }
                Spacer(Modifier.width(20.dp))
                TextButton({ navController.navigate(Screens.Statistics.name) }) { Text(stringResource(R.string.all_podcasts)) }
            }
            if (vm.feed?.isSynthetic() == false) {
                TextButton(modifier = Modifier.padding(top = 10.dp), onClick = {
                    setOnlineSearchTerms(CombinedSearcher::class.java, "${vm.txtvAuthor} podcasts")
                    navController.navigate(Screens.OnlineResults.name)
                }) { Text(stringResource(R.string.feeds_related_to_author)) }
                Text(stringResource(R.string.last_full_update) + ": ${formatDateTimeFlex(Date(vm.feed?.lastFullUpdateTime?:0L))}", modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(stringResource(R.string.url_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(text = vm.txtvUrl ?: "", color = textColor, modifier = Modifier.padding(bottom = 15.dp).combinedClickable(
                    onClick = { if (!vm.feed?.downloadUrl.isNullOrBlank()) IntentUtils.openInBrowser(context, vm.feed!!.downloadUrl!!) },
                    onLongClick = {
                        if (!vm.feed?.downloadUrl.isNullOrBlank()) {
                            val url: String = vm.feed!!.downloadUrl!!
                            val clipData: ClipData = ClipData.newPlainText(url, url)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(clipData)
                            Logt(TAG, context.getString(R.string.copied_to_clipboard))
                        }
                    }
                ))
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
                            str.append(if (funding.content == null || funding.content!!.isEmpty()) context.resources.getString(R.string.support_podcast)
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

    BackHandler(enabled = showHistory || !enableFilter) {
//        Logt(TAG, "BackHandler ")
        showHistory = false
        enableFilter = true
    }

    var reassembleJob: Job? = remember { null }
    LaunchedEffect(showHistory, enableFilter, vm.feedLoaded) {
        if (feedScreenMode == FeedScreenMode.List) {
            if (reassembleJob != null) {
                Logd(TAG, "reassembleList cancelling job")
                reassembleJob?.cancel()
                vm.vms.clear()
            }
            reassembleJob = scope.launch(Dispatchers.IO) {
                vm.assembleList(vm.feed)
            }.apply { invokeOnCompletion { reassembleJob = null } }
        }
    }

    vm.swipeActions.ActionOptionsDialog()
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            FeedDetailsHeader()
            if (feedScreenMode == FeedScreenMode.List) {
                vm.infoBarText.value = "${vm.listInfoText} $feedOperationText"
                InforBar(vm.infoBarText, vm.swipeActions)
                EpisodeLazyColumn(context, vms = vm.vms, feed = vm.feed, layoutMode = vm.layoutModeIndex,
                    buildMoreItems = { vm.buildMoreItems() },
                    refreshCB = { runOnceOrAsk(vm.context, feed = vm.feed) },
                    swipeActions = vm.swipeActions,
                    actionButtonCB = { e, type ->
                        Logd(TAG, "actionButtonCB type: $type")
                        if (e.feed?.id == vm.feed?.id && type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAYLOCAL, ButtonTypes.STREAM)) upsertBlk(vm.feed!!) { it.lastPlayed = Date().time }
                    },
                )
            } else DetailUI()
        }
    }
}

private val TAG = Screens.FeedDetails.name
private var showHistory by mutableStateOf(false)
private var enableFilter by mutableStateOf(true)
object TTSObj {
    var tts: TextToSpeech? = null
    var ttsReady = false
    var ttsWorking = false

    fun ensureTTS(context: Context) {
        if (!ttsReady && tts == null) CoroutineScope(Dispatchers.Default).launch {
            Logd(TAG, "starting TTS")
            tts = TextToSpeech(context) { status: Int ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    Logt(TAG, "TTS init success")
                } else Loge(TAG, context.getString(R.string.tts_init_failed))
            }
        }
    }
}