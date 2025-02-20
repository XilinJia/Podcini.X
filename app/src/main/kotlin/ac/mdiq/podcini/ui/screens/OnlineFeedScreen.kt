package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.FeedBuilderBase
import ac.mdiq.podcini.net.feed.FeedUrlNotFoundException
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.feed.searcher.PodcastSearcherRegistry
import ac.mdiq.podcini.net.utils.HtmlToPlainText
import ac.mdiq.podcini.preferences.AppPreferences.isAutodownloadEnabled
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.getFeedByTitleAndAuthor
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.isSubscribed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Rating.Companion.fromCode
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.storage.model.SubscriptionLog.Companion.feedLogsMap
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Screens
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.VMS_CHUNK_SIZE
import ac.mdiq.podcini.ui.compose.stopMonitor
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.ui.utils.isOnlineFeedShared
import ac.mdiq.podcini.ui.utils.onlineFeedSource
import ac.mdiq.podcini.ui.utils.onlineFeedUrl
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.MiscFormatter.formatAbbrev
import android.app.Dialog
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Downloads a feed from a feed URL and parses it. Subclasses can display the
 * feed object that was parsed. This activity MUST be started with a given URL
 * or an Exception will be thrown.
 * If the feed cannot be downloaded or parsed, an error dialog will be displayed
 * and the activity will finish as soon as the error dialog is closed.
 */
class OnlineFeedVM(val context: Context, val lcScope: CoroutineScope) {
    var feedSource: String = ""

    internal var displayUpArrow = false

    internal var feedUrl: String = ""
    internal var urlToLog: String = ""
    internal lateinit var feedBuilder: FeedBuilderBase
    internal var showTabsDialog by mutableStateOf(false)

    internal var isShared: Boolean = false

    internal var showEpisodes by mutableStateOf(false)
    internal var showFeedDisplay by mutableStateOf(false)
    internal var showProgress by mutableStateOf(true)
    internal var autoDownloadChecked by mutableStateOf(false)
    internal var enableSubscribe by mutableStateOf(true)
    internal var enableEpisodes by mutableStateOf(true)
    internal var subButTextRes by mutableIntStateOf(R.string.subscribe_label)

    internal val feedId: Long
        get() {
            if (feeds == null) return 0
            for (f in feeds!!) if (f.downloadUrl == selectedDownloadUrl) return f.id
            return 0
        }


    internal var infoBarText = mutableStateOf("")
    internal var swipeActions: SwipeActions
    internal var leftActionState = mutableStateOf<SwipeAction>(NoAction())
    internal var rightActionState = mutableStateOf<SwipeAction>(NoAction())

    internal var showSwipeActionsDialog by mutableStateOf(false)

    internal var episodes = mutableListOf<Episode>()
    internal val vms = mutableStateListOf<EpisodeVM>()

    init {
        feedSource = onlineFeedSource
        feedUrl = onlineFeedUrl
        isShared = isOnlineFeedShared

        swipeActions = SwipeActions(context, TAG)
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
    }

    @Volatile
    internal var feeds: List<Feed>? = null
    internal var feed by mutableStateOf<Feed?>(null)
    internal var selectedDownloadUrl: String? = null
    //    private var downloader: Downloader? = null
    internal var username: String? = null
    internal var password: String? = null

    internal var isPaused = false
    internal var didPressSubscribe = false
    internal var isFeedFoundBySearch = false

    internal var dialog: Dialog? = null

    internal fun buildMoreItems() {
//        val nextItems = (vms.size until min(vms.size + VMS_CHUNK_SIZE, episodes.size)).map { EpisodeVM(episodes[it], TAG) }
        val nextItems = (vms.size until (vms.size + VMS_CHUNK_SIZE).coerceAtMost(episodes.size)).map { EpisodeVM(episodes[it], TAG) }
        if (nextItems.isNotEmpty()) vms.addAll(nextItems)
    }

    internal fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
    }

    internal fun handleFeed(feed_: Feed, map: Map<String, String>) {
        selectedDownloadUrl = feedBuilder.selectedDownloadUrl
        feed = feed_
        if (isShared) {
            val log = realm.query(ShareLog::class).query("url == $0", urlToLog).first().find()
            if (log != null) upsertBlk(log) {
                it.title = feed_.title
                it.author = feed_.author
            }
        }
        showFeedInformation(feed_, map)
    }

    internal fun lookupUrlAndBuild(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            urlToLog = url
            val urlString = PodcastSearcherRegistry.lookupUrl1(url)
            Logd(TAG, "lookupUrlAndBuild: urlString: $urlString")
            try {
                feeds = getFeedList()
                gearbox.buildFeed(urlString, username ?: "", password ?: "", feedBuilder, handleFeed = { feed_, map -> handleFeed(feed_, map) }) { showTabsDialog = true }
            } catch (e: FeedUrlNotFoundException) { tryToRetrieveFeedUrlBySearch(e)
            } catch (e: Throwable) {
                Logs(TAG, e)
                withContext(Dispatchers.Main) { showNoPodcastFoundDialog = true }
            }
        }
    }

    private fun tryToRetrieveFeedUrlBySearch(error: FeedUrlNotFoundException) {
        Logd(TAG, "Unable to retrieve feed url, trying to retrieve feed url from search")
        CoroutineScope(Dispatchers.IO).launch {
            var url: String? = null
            val searcher = CombinedSearcher()
            val query = "${error.trackName} ${error.artistName}"
            val results = searcher.search(query)
            if (results.isEmpty()) return@launch
            for (result in results) {
                if (result.feedUrl != null && result.author != null && result.author.equals(error.artistName, ignoreCase = true)
                        && result.title.equals(error.trackName, ignoreCase = true)) {
                    url = result.feedUrl
                    break
                }
            }
            if (url != null) {
                urlToLog = url
                Logd(TAG, "Successfully retrieve feed url")
                isFeedFoundBySearch = true
                feeds = getFeedList()
                gearbox.buildFeed(url, username?:"", password?:"", feedBuilder, handleFeed = { feed_, map -> handleFeed(feed_, map) }) { showTabsDialog = true }
            } else {
                withContext(Dispatchers.Main) { showNoPodcastFoundDialog = true }
                Logd(TAG, "Failed to retrieve feed url")
            }
        }
    }

//    private fun searchFeedUrlByTrackName(trackName: String, artistName: String): String? {
//        val searcher = CombinedSearcher()
//        val query = "$trackName $artistName"
//        val results = searcher.search(query).blockingGet()
//        if (results.isNullOrEmpty()) return null
//        for (result in results) {
//            if (result?.feedUrl != null && result.author != null && result.author.equals(artistName, ignoreCase = true)
//                    && result.title.equals(trackName, ignoreCase = true)) return result.feedUrl
//        }
//        return null
//    }

    private var eventSink: Job?     = null
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
                    is FlowEvent.FeedListEvent -> onFeedListChanged(event)
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lcScope.launch {
            EventFlow.stickyEvents.drop(1).collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> handleUpdatedFeedStatus()
                    else -> {}
                }
            }
        }
    }

    private fun onFeedListChanged(event: FlowEvent.FeedListEvent) {
        lcScope.launch {
            try {
                val feeds_ = withContext(Dispatchers.IO) { getFeedList() }
                withContext(Dispatchers.Main) {
                    feeds = feeds_
                    handleUpdatedFeedStatus()
                }
            } catch (e: Throwable) {
                Logs(TAG, e)
                withContext(Dispatchers.Main) {
                    errorMessage = e.message ?: "No message"
                    errorDetails = ""
                    showErrorDialog = true
                }
            }
        }
    }

    /**
     * Called when feed parsed successfully.
     * This method is executed on the GUI thread.
     */
    private fun showFeedInformation(feed: Feed, alternateFeedUrls: Map<String, String>) {
        showProgress = false
        showFeedDisplay = true
        if (isFeedFoundBySearch) Loge(TAG, context.getString(R.string.no_feed_url_podcast_found_by_search))

//        if (alternateFeedUrls.isEmpty()) binding.alternateUrlsSpinner.visibility = View.GONE
//        else {
//            binding.alternateUrlsSpinner.visibility = View.VISIBLE
//            val alternateUrlsList: MutableList<String> = ArrayList()
//            val alternateUrlsTitleList: MutableList<String?> = ArrayList()
//            if (feed.downloadUrl != null) alternateUrlsList.add(feed.downloadUrl!!)
//            alternateUrlsTitleList.add(feed.title)
//            alternateUrlsList.addAll(alternateFeedUrls.keys)
//            for (url in alternateFeedUrls.keys) {
//                alternateUrlsTitleList.add(alternateFeedUrls[url])
//            }
//            val adapter: ArrayAdapter<String> = object : ArrayAdapter<String>(requireContext(),
//                R.layout.alternate_urls_item, alternateUrlsTitleList) {
//                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
//                    // reusing the old view causes a visual bug on Android <= 10
//                    return super.getDropDownView(position, null, parent)
//                }
//            }
//            adapter.setDropDownViewResource(R.layout.alternate_urls_dropdown_item)
//            binding.alternateUrlsSpinner.adapter = adapter
//            binding.alternateUrlsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//                override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
//                    selectedDownloadUrl = alternateUrlsList[position]
//                }
//                override fun onNothingSelected(parent: AdapterView<*>?) {}
//            }
//        }
        handleUpdatedFeedStatus()
    }

    internal fun showEpisodes(episodes_: MutableList<Episode>) {
        episodes = episodes_
        stopMonitor(vms)
        vms.clear()
        buildMoreItems()
        infoBarText.value = "${episodes.size} episodes"

        Logd(TAG, "showEpisodes ${episodes.size}")
        if (episodes.isEmpty()) return
        episodes.sortByDescending { it.pubDate }
        var id_ = Feed.newId()
        for (i in 0..<episodes.size) {
            episodes[i].id = id_++
            episodes[i].isRemote.value = true
        }
        showEpisodes = true
    }

    internal fun handleUpdatedFeedStatus() {
        val dli = DownloadServiceInterface.impl
        if (dli == null || selectedDownloadUrl == null) return

        when {
            dli.isDownloadingEpisode(selectedDownloadUrl!!) -> {
                enableSubscribe = false
                subButTextRes = R.string.subscribe_label
            }
            feedId != 0L -> {
                enableSubscribe = true
                subButTextRes = R.string.open
                if (didPressSubscribe) {
                    didPressSubscribe = false
                    val feed1 = getFeed(feedId, true)?: return
//                    if (feed1.preferences == null) feed1.preferences = FeedPreferences(feed1.id, false,
//                        FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
                    if (feedSource == "VistaGuide") {
                        feed1.prefStreamOverDownload = true
                        feed1.autoDownload = false
                    } else if (isAutodownloadEnabled) feed1.autoDownload = autoDownloadChecked
                    if (username != null) {
                        feed1.username = username
                        feed1.password = password
                    }
                    runOnIOScope { upsert(feed1) {} }
                }
            }
            else -> {
                enableSubscribe = true
                subButTextRes = R.string.subscribe_label
            }
        }
    }

    internal var showNoPodcastFoundDialog by mutableStateOf(false)
    internal var showErrorDialog by mutableStateOf(false)
    internal var errorMessage by mutableStateOf("")
    internal var errorDetails by mutableStateOf("")
}

@Composable
fun OnlineFeedScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { OnlineFeedVM(context, scope) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "feedUrl: ${vm.feedUrl}")
                    vm.feedBuilder = gearbox.formFeedBuilder(vm.feedUrl, vm.feedSource, context) { message, details ->
                        vm.errorMessage = message ?: "No message"
                        vm.errorDetails = details
                        vm.showErrorDialog = true
                    }
                    if (vm.feedUrl.isEmpty()) {
                        Loge(TAG, "feedUrl is null.")
                        vm.showNoPodcastFoundDialog = true
                    } else {
                        Logd(TAG, "Activity was started with url ${vm.feedUrl}")
                        vm.showProgress = true
                        // Remove subscribeonandroid.com from feed URL in order to subscribe to the actual feed URL
                        if (vm.feedUrl.contains("subscribeonandroid.com")) vm.feedUrl = vm.feedUrl.replaceFirst("((www.)?(subscribeonandroid.com/))".toRegex(), "")
//                            if (savedInstanceState != null) {
//                                vm.username = savedInstanceState.getString("username")
//                                vm.password = savedInstanceState.getString("password")
//                            }
                        vm.lookupUrlAndBuild(vm.feedUrl)
                    }
                    lifecycleOwner.lifecycle.addObserver(vm.swipeActions)
                    vm.refreshSwipeTelltale()
                }
                Lifecycle.Event.ON_START -> {
                    vm.isPaused = false
                    vm.procFlowEvents()
                    stopMonitor(vm.vms)
                    vm.vms.clear()
                    vm.buildMoreItems()
                    vm.infoBarText.value = "${vm.episodes.size} episodes"
                }
                Lifecycle.Event.ON_STOP -> {
                    vm.isPaused = true
                    vm.cancelFlowEvents()
//        if (downloader != null && !downloader!!.isFinished) downloader!!.cancel()
                    if (vm.dialog != null && vm.dialog!!.isShowing) vm.dialog!!.dismiss()
                }
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.feeds = null
            vm.episodes.clear()
            stopMonitor(vm.vms)
            vm.vms.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    if (vm.showTabsDialog) gearbox.ShowTabsDialog(vm.feedBuilder, onDismissRequest = { vm.showTabsDialog = false }) { feed, map -> vm.handleFeed(feed, map) }
    val feedLogsMap_ = feedLogsMap!!
    if (vm.showNoPodcastFoundDialog) AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { vm.showNoPodcastFoundDialog = false },
        title = { Text(stringResource(R.string.error_label)) },
        text = { Text(stringResource(R.string.null_value_podcast_error)) },
        confirmButton = { TextButton(onClick = { vm.showNoPodcastFoundDialog = false }) { Text("OK") } })

    @Composable
    fun FoundDialog(errorMsg: String?, details: String, onDismiss: () -> Unit) {
        val errorMessage = if (errorMsg != null) {
            val total = """
                    $errorMsg
                    
                    $details
                    """.trimIndent()
            val msg = SpannableString(total)
            msg.setSpan(ForegroundColorSpan(-0x77777778), errorMsg.length, total.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            msg
        } else { context.getString(R.string.download_error_error_unknown) }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.error_label)) },
            text = { Text(errorMessage.toString()) },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text("OK") } })
    }
    if (vm.showErrorDialog) FoundDialog(vm.errorMessage, vm.errorDetails) { vm.showErrorDialog = false}

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar(title = { Text(text = "Online feed") },
            navigationIcon = if (vm.displayUpArrow) {
                { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { MainActivity.openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }
            }
        )
    }

    if (vm.showSwipeActionsDialog) SwipeActionsSettingDialog(vm.swipeActions, onDismissRequest = { vm.showSwipeActionsDialog = false }) { actions ->
        vm.swipeActions.actions = actions
        vm.refreshSwipeTelltale()
    }

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        if (vm.showProgress) Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            CircularProgressIndicator(progress = {0.6f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp).align(Alignment.Center))
        }
        else Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(start = 10.dp, end = 10.dp).background(MaterialTheme.colorScheme.surface)) {
            if (vm.showFeedDisplay) ConstraintLayout(modifier = Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surface)) {
                val (coverImage, taColumn, buttons) = createRefs()
                AsyncImage(model = vm.feed?.imageUrl ?: "", contentDescription = "coverImage", error = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier.width(100.dp).height(100.dp).padding(start = 10.dp, end = 16.dp, bottom = 10.dp).constrainAs(coverImage) {
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                    })
                Column(Modifier.constrainAs(taColumn) {
                    top.linkTo(coverImage.top)
                    start.linkTo(coverImage.end)
                }) {
                    Text(vm.feed?.title ?: "No title", color = textColor, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(vm.feed?.author ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.constrainAs(buttons) {
                    start.linkTo(coverImage.end)
                    top.linkTo(taColumn.bottom)
                    end.linkTo(parent.end)
                }) {
                    Spacer(modifier = Modifier.weight(0.2f))
                    if (vm.enableSubscribe) Button(onClick = {
                        if (vm.feedId != 0L || isSubscribed(vm.feed!!)) {
                            if (vm.isShared) {
                                val log = realm.query(ShareLog::class).query("url == $0", vm.feedUrl).first().find()
                                if (log != null) upsertBlk(log) { it.status = ShareLog.Status.EXISTING.ordinal }
                            }
                            val feed = getFeedByTitleAndAuthor(vm.feed?.eigenTitle ?: "", vm.feed?.author ?: "")
                            if (feed != null) {
                                feedOnDisplay = feed
                                feedScreenMode = FeedScreenMode.Info
                                mainNavController.navigate(Screens.FeedDetails.name)
                            }
                        } else {
                            vm.enableSubscribe = false
                            vm.enableEpisodes = false
                            CoroutineScope(Dispatchers.IO).launch {
                                vm.feedBuilder.subscribe(vm.feed!!)
                                if (vm.isShared) {
                                    val log = realm.query(ShareLog::class).query("url == $0", vm.feedUrl).first().find()
                                    if (log != null) upsertBlk(log) { it.status = ShareLog.Status.SUCCESS.ordinal }
                                }
                                withContext(Dispatchers.Main) {
                                    vm.enableSubscribe = true
                                    vm.didPressSubscribe = true
                                    vm.handleUpdatedFeedStatus()
                                }
                            }
                        }
                    }) { Text(stringResource(vm.subButTextRes)) }
                    Spacer(modifier = Modifier.weight(0.1f))
                    if (vm.showEpisodes) {
                        Button(onClick = { vm.showEpisodes = false }) { Text(stringResource(R.string.feed)) }
                    } else {
                        if (vm.enableEpisodes && vm.feed != null) Button(onClick = { vm.showEpisodes(vm.feed!!.episodes) }) { Text(stringResource(R.string.episodes_label)) }
                    }
                    Spacer(modifier = Modifier.weight(0.2f))
                }
            }
            if (vm.showEpisodes) {
               InforBar(vm.infoBarText, leftAction = vm.leftActionState, rightAction = vm.rightActionState, actionConfig = { vm.showSwipeActionsDialog = true })
               EpisodeLazyColumn(context as MainActivity, vms = vm.vms,
                   buildMoreItems = { vm.buildMoreItems() },
                   leftSwipeCB = {
                       if (vm.leftActionState.value is NoAction) vm.showSwipeActionsDialog = true
                       else vm.leftActionState.value.performAction(it)
                   },
                   rightSwipeCB = {
                       if (vm.rightActionState.value is NoAction) vm.showSwipeActionsDialog = true
                       else vm.rightActionState.value.performAction(it)
                   },
               )
           } else {
               Column {
//                    alternate_urls_spinner
                   if (vm.feedSource != "VistaGuide" && isAutodownloadEnabled) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                       Checkbox(checked = vm.autoDownloadChecked, onCheckedChange = { vm.autoDownloadChecked = it })
                       Text(text = stringResource(R.string.auto_download_label),
                           style = MaterialTheme.typography.bodyMedium.merge(), color = textColor,
                           modifier = Modifier.padding(start = 16.dp)
                       )
                   }
               }
               val scrollState = rememberScrollState()
               var numEpisodes by remember { mutableIntStateOf(vm.feed?.episodes?.size ?: 0) }
               LaunchedEffect(Unit) {
                   while (true) {
                       delay(1000)
                       numEpisodes = vm.feed?.episodes?.size ?: 0
                   }
               }
               Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
                   Text("$numEpisodes episodes", color = textColor, style = MaterialTheme.typography.bodyLarge,
                       modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
                   Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.bodyLarge,
                       modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                   Text(HtmlToPlainText.getPlainText(vm.feed?.description ?: ""), color = textColor, style = MaterialTheme.typography.bodyMedium)
                   val sLog = remember { feedLogsMap_[vm.feed?.downloadUrl ?: ""] }
                   if (sLog != null) {
                       val commentTextState by remember { mutableStateOf(TextFieldValue(sLog.comment)) }
                       val context = LocalContext.current
                       val cancelDate = remember { formatAbbrev(context, Date(sLog.cancelDate)) }
                       val ratingRes = remember { fromCode(sLog.rating).res }
                       if (commentTextState.text.isNotEmpty()) {
                           Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp)) {
                               Text(stringResource(R.string.my_opinion_label), color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom)
                               Icon(imageVector = ImageVector.vectorResource(ratingRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = null, modifier = Modifier.padding(start = 5.dp))
                           }
                           Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium,
                               modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                           Text(stringResource(R.string.cancelled_on_label) + ": " + cancelDate, color = textColor, style = MaterialTheme.typography.bodyMedium,
                               modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                       }
                   }
                   Text(vm.feed?.mostRecentItem?.title ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                   Text("${vm.feed?.language ?: ""} ${vm.feed?.type ?: ""} ${vm.feed?.lastUpdate ?: ""}", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                   Text(vm.feed?.link ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                   Text(vm.feed?.downloadUrl ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
               }
           }
        }
    }
}

const val ARG_WAS_MANUAL_URL: String = "manual_url"
private const val TAG: String = "OnlineFeedScreen"
