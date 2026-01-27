package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.FeedBuilderBase
import ac.mdiq.podcini.net.feed.FeedUrlNotFoundException
import ac.mdiq.podcini.net.feed.CombinedSearcher
import ac.mdiq.podcini.net.feed.PodcastSearchResult
import ac.mdiq.podcini.net.feed.PodcastSearcherRegistry
import ac.mdiq.podcini.ui.utils.HtmlToPlainText
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.preferences.AppPreferences.isAutodownloadEnabled
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.storage.model.SubscriptionLog.Companion.feedLogsMap
import ac.mdiq.podcini.storage.model.tmpQueue
import ac.mdiq.podcini.storage.specs.Rating.Companion.fromCode
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.NumberEditor
import ac.mdiq.podcini.ui.compose.COME_BACK
import ac.mdiq.podcini.ui.compose.EpisodeScreen
import ac.mdiq.podcini.ui.compose.LocalNavController
import ac.mdiq.podcini.ui.compose.Screens
import ac.mdiq.podcini.ui.compose.episodeForInfo
import ac.mdiq.podcini.ui.compose.handleBackSubScreens
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.formatAbbrev
import ac.mdiq.podcini.utils.timeIt
import android.app.Dialog
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineFeedVM(url: String = "", source: String = "", shared: Boolean = false): ViewModel() {
    var feedSource: String = ""
    internal var feedUrl: String = ""
    internal var isShared: Boolean = false

    internal var urlToLog: String = ""
    internal lateinit var feedBuilder: FeedBuilderBase
    internal var showTabsDialog by mutableStateOf(false)

    internal var showEpisodes by mutableStateOf(false)
    internal var showFeedDisplay by mutableStateOf(false)
    internal var showProgress by mutableStateOf(true)
    internal var autoDownloadChecked by mutableStateOf(false)
    internal var limitEpisodesCount by mutableIntStateOf(0)
    internal var enableSubscribe by mutableStateOf(true)
    internal var enableEpisodes by mutableStateOf(true)
    internal var subButTextRes by mutableIntStateOf(R.string.subscribe_label)

    internal val feedId: Long
        get() {
            if (feeds == null) feeds = getFeedList()
            for (f in feeds!!) if (gearbox.isSameFeed(f, selectedDownloadUrl, feed?.title)) return f.id
            return 0
        }

    internal var infoBarText = mutableStateOf("")

    internal var episodes = mutableListOf<Episode>()

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

    val relatedFeeds = mutableStateListOf<PodcastSearchResult>()

    internal var showNoPodcastFoundDialog by mutableStateOf(false)
    internal var showErrorDialog by mutableStateOf(false)
    internal var errorMessage by mutableStateOf("")
    internal var errorDetails by mutableStateOf("")

    init {
        timeIt("$TAG start of init")
        feedUrl = url
        feedSource = source
        isShared = shared

        feedBuilder = gearbox.formFeedBuilder(feedUrl, feedSource) { message, details ->
            errorMessage = message ?: "No message"
            errorDetails = details
            showErrorDialog = true
        }
        if (feedUrl.isEmpty()) {
            Loge(TAG, "feedUrl is null.")
            showNoPodcastFoundDialog = true
        } else {
            Logd(TAG, "Activity was started with url $feedUrl")
            showProgress = true
            // Remove subscribeonandroid.com from feed URL in order to subscribe to the actual feed URL
            if (feedUrl.contains("subscribeonandroid.com")) feedUrl = feedUrl.replaceFirst("((www.)?(subscribeonandroid.com/))".toRegex(), "")
            //                            if (savedInstanceState != null) {
            //                                vm.username = savedInstanceState.getString("username")
            //                                vm.password = savedInstanceState.getString("password")
            //                            }
            lookupUrlAndBuild(feedUrl)
        }
        timeIt("$TAG end of init")
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
        relatedFeeds.clear()
        viewModelScope.launch(Dispatchers.IO) {
            val fl = CombinedSearcher::class.java.getDeclaredConstructor().newInstance().search("${feed?.author} podcasts")
            withContext(Dispatchers.Main) { if (fl.isNotEmpty()) relatedFeeds.addAll(fl) }
        }
        showFeedInformation(feed_, map)
    }

    internal fun lookupUrlAndBuild(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            urlToLog = url
            try {
                val urlString = PodcastSearcherRegistry.lookupUrl1(url)
                Logd(TAG, "lookupUrlAndBuild: urlString: $urlString")
                gearbox.buildFeed(urlString, username ?: "", password ?: "", feedBuilder, handleFeed = { feed_, map -> handleFeed(feed_, map) }) { showTabsDialog = true }
            } catch (error: FeedUrlNotFoundException) {
                Logd(TAG, "lookupUrlAndBuild in error, trying to Retrieve FeedUrl By Search")
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
                    //                feeds = getFeedList()
                    gearbox.buildFeed(url, username?:"", password?:"", feedBuilder, handleFeed = { feed_, map -> handleFeed(feed_, map) }) { showTabsDialog = true }
                } else {
                    withContext(Dispatchers.Main) { showNoPodcastFoundDialog = true }
                    Logd(TAG, "Failed to retrieve feed url")
                }
            } catch (e: Throwable) {
                Logs(TAG, e)
                withContext(Dispatchers.Main) { showNoPodcastFoundDialog = true }
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
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    internal fun procFlowEvents() {
        if (eventSink == null) eventSink = viewModelScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedListEvent -> onFeedListChanged(event)
                    else -> {}
                }
            }
        }
    }

    private fun onFeedListChanged(event: FlowEvent.FeedListEvent) {
        viewModelScope.launch(Dispatchers.IO) {
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
    // TODO: what to do?
    private fun showFeedInformation(feed: Feed, alternateFeedUrls: Map<String, String>) {
        showProgress = false
        showFeedDisplay = true
        if (isFeedFoundBySearch) Loge(TAG, getAppContext().getString(R.string.no_feed_url_podcast_found_by_search))

//        if (alternateFeedUrls.isEmpty()) binding.alternateUrlsSpinner.visibility = View.GONE
//        else {
//            binding.alternateUrlsSpinner.visibility = View.VISIBLE
//            val alternateUrlsList: MutableList<String> = mutableListOf()
//            val alternateUrlsTitleList: MutableList<String?> = mutableListOf()
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

    internal fun showEpisodes() {
        if (feed == null) return
        if (episodes.isEmpty()) {
            episodes = feed!!.episodes
            infoBarText.value = "${episodes.size} episodes"

            Logd(TAG, "showEpisodes ${episodes.size}")
            if (episodes.isEmpty()) return
            episodes.sortByDescending { it.pubDate }
            var id_ = Feed.newId()
            for (i in 0..<episodes.size) {
                episodes[i].id = id_++
                episodes[i].origFeedlink = feed!!.link
                episodes[i].origFeeddownloadUrl = feed!!.downloadUrl
                episodes[i].origFeedTitle = feed!!.title
            }
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

    override fun onCleared() {
        super.onCleared()
        feeds = null
        episodes.clear()
    }
}

@ExperimentalMaterial3Api
@Composable
fun OnlineFeedScreen(url: String = "", source: String = "", shared: Boolean = false) {
    val lifecycleOwner = LocalLifecycleOwner.current
//    val scope = rememberCoroutineScope()
    val drawerController = LocalDrawerController.current
    val context by rememberUpdatedState(LocalContext.current)
    val textColor = MaterialTheme.colorScheme.onSurface
    val navController = LocalNavController.current

    val vm: OnlineFeedVM = viewModel(factory = viewModelFactory { initializer { OnlineFeedVM(url, source, shared) } })

    var swipeActions by remember { mutableStateOf(SwipeActions(TAG)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "feedUrl: ${vm.feedUrl}")
                    lifecycleOwner.lifecycle.addObserver(swipeActions)
                }
                Lifecycle.Event.ON_START -> {
                    vm.isPaused = false
                    vm.procFlowEvents()
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
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(vm.showEpisodes, episodeForInfo) {
        if (vm.showEpisodes || episodeForInfo != null) handleBackSubScreens.add(TAG)
        else handleBackSubScreens.remove(TAG)
        onDispose { handleBackSubScreens.remove(TAG) }
    }

    BackHandler(enabled = handleBackSubScreens.contains(TAG)) {
        episodeForInfo = null
        vm.showEpisodes = false
    }

    if (vm.showTabsDialog) gearbox.ShowTabsDialog(vm.feedBuilder, onDismissRequest = { vm.showTabsDialog = false }) { feed, map -> vm.handleFeed(feed, map) }
    if (vm.showNoPodcastFoundDialog) AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { vm.showNoPodcastFoundDialog = false },
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
        AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.error_label)) },
            text = { Text(errorMessage.toString()) },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text("OK") } })
    }
    if (vm.showErrorDialog) FoundDialog(vm.errorMessage, vm.errorDetails) { vm.showErrorDialog = false}

    
    @Composable
    fun MyTopAppBar() {
        Box {
            TopAppBar(title = { Text(text = "Online feed") }, navigationIcon = { IconButton(onClick = {
                if (navController.previousBackStackEntry != null) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(COME_BACK, true)
                    navController.popBackStack()
                } else drawerController?.close()
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Open Drawer") } })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    if (episodeForInfo != null) EpisodeScreen(episodeForInfo!!)
    else Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        if (vm.showEpisodes) Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(start = 10.dp, end = 10.dp).background(MaterialTheme.colorScheme.surface)) {
            InforBar(swipeActions) { Text(vm.infoBarText.value, style = MaterialTheme.typography.bodyMedium) }
            EpisodeLazyColumn(vm.episodes.toList(), isRemote = true, swipeActions = swipeActions,
                actionButtonCB = { e, type -> if (type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAY_LOCAL, ButtonTypes.STREAM)) actQueue = tmpQueue() })
        } else Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp).background(MaterialTheme.colorScheme.surface)) {
            ConstraintLayout(modifier = Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surface)) {
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
                    if (vm.showFeedDisplay && vm.enableSubscribe) Button(onClick = {
                        if (vm.feedId != 0L || realm.query(Feed::class, "eigenTitle == $0 && author == $1", vm.feed!!.eigenTitle, vm.feed!!.author).first().find() != null) {
                            if (vm.isShared) {
                                val log = realm.query(ShareLog::class).query("url == $0", vm.feedUrl).first().find()
                                if (log != null) upsertBlk(log) { it.status = ShareLog.Status.EXISTING.ordinal }
                            }
                            val feed = realm.query(Feed::class, "eigenTitle == $0 && author == $1", vm.feed?.eigenTitle ?: "", vm.feed?.author ?: "").first().find()
                            if (feed != null) navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.id}&modeName=${FeedScreenMode.Info.name}")
                        } else {
                            vm.enableSubscribe = false
                            vm.enableEpisodes = false
                            CoroutineScope(Dispatchers.IO).launch {
                                if (vm.limitEpisodesCount > 0) vm.feed?.limitEpisodesCount = vm.limitEpisodesCount
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
                    when {
                        vm.showEpisodes -> Button(onClick = { vm.showEpisodes = false }) { Text(stringResource(R.string.feed)) }
                        vm.enableEpisodes && vm.feed != null -> Button(onClick = { vm.showEpisodes() }) { Text(stringResource(R.string.episodes_label)) }
                        else -> {}
                    }
                    Spacer(modifier = Modifier.weight(0.2f))
                }
            }
            Column(Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary)) {
//                    TODO: alternate_urls_spinner
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.limit_episodes_to), modifier = Modifier.weight(0.5f))
                    NumberEditor(vm.limitEpisodesCount, label = "0 = unlimited", nz = false, instant = true, modifier = Modifier.weight(0.5f)) {
                        Logd(TAG, "limitEpisodesCount: $it")
                        vm.limitEpisodesCount = it
                    }
                }
                if (gearbox.isFeedAutoDownloadable(vm.feedUrl) && isAutodownloadEnabled) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vm.autoDownloadChecked, onCheckedChange = { vm.autoDownloadChecked = it })
                    Text(text = stringResource(R.string.auto_download_label), style = MaterialTheme.typography.bodyMedium.merge(), color = textColor, modifier = Modifier.padding(start = 16.dp))
                }
            }
            var numEpisodes by remember { mutableIntStateOf(vm.feed?.episodes?.size ?: 0) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000)
                    numEpisodes = vm.feed?.episodes?.size ?: 0
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp)) {
                Text("$numEpisodes episodes", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
                Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                Text(HtmlToPlainText.getPlainText(vm.feed?.description ?: ""), color = textColor, style = MaterialTheme.typography.bodyMedium)
                val sLog = remember { feedLogsMap!![vm.feed?.downloadUrl ?: ""] ?: feedLogsMap!![vm.feed?.title ?: ""] }
                if (sLog != null) {
                    val commentTextState by remember { mutableStateOf(TextFieldValue(sLog.comment)) }
                    val context = LocalContext.current
                    val cancelDate = remember { formatAbbrev(Date(sLog.cancelDate)) }
                    val ratingRes = remember { fromCode(sLog.rating).res }
                    if (commentTextState.text.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp)) {
                            Text(stringResource(R.string.my_opinion_label), color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom)
                            Icon(imageVector = ImageVector.vectorResource(ratingRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = null, modifier = Modifier.padding(start = 5.dp))
                        }
                        Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                        Text(stringResource(R.string.cancelled_on_label) + ": " + cancelDate, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                    }
                }
                if (!vm.feed?.episodes.isNullOrEmpty()) {
                    Text(stringResource(R.string.recent_episode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                    Text(vm.feed?.episodes[0]?.title ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                }

                Text(stringResource(R.string.feeds_related_to_author), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp).clickable(onClick = {
                        setOnlineSearchTerms(query = "${vm.feed?.author} podcasts")
                        navController.navigate(Screens.FindFeeds.name)
                    }))
                LazyRow(state = rememberLazyListState(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(vm.relatedFeeds.size) { index ->
                        val feed = remember(index) { vm.relatedFeeds[index] }
                        val img = remember(feed) { ImageRequest.Builder(context).data(feed.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                        AsyncImage(model = img, contentDescription = "imgvCover", modifier = Modifier.width(100.dp).height(100.dp).clickable(onClick = {
                            navController.navigate("${Screens.OnlineFeed.name}?url=${URLEncoder.encode(feed.feedUrl, StandardCharsets.UTF_8.name())}&source=${feed.source}")
                        }))
                    }
                }
                val info by remember(vm.feed) {
                    derivedStateOf {
                        if (vm.feed == null) return@derivedStateOf ""
                        val languageString = vm.feed!!.langSet.joinToString(" ")
                        "$languageString ${vm.feed!!.type.orEmpty()} ${vm.feed!!.lastUpdate.orEmpty()}"
                    }
                }
                Text(info, color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                Text(vm.feed?.link ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                Text(vm.feed?.downloadUrl ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
            }
        }
        if (vm.showProgress) Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            CircularProgressIndicator(progress = {0.6f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp).align(Alignment.Center))
        }
    }
}

private val TAG: String = Screens.OnlineFeed.name
