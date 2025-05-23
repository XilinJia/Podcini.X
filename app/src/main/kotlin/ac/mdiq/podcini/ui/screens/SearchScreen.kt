package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.storage.database.Episodes.indexOfItemWithDownloadUrl
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.NonlazyGrid
import ac.mdiq.podcini.ui.compose.SearchBarRow
import ac.mdiq.podcini.ui.compose.VMS_CHUNK_SIZE
import ac.mdiq.podcini.ui.utils.curSearchString
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.ui.utils.feedToSearchIn
import ac.mdiq.podcini.ui.utils.setOnlineFeedUrl
import ac.mdiq.podcini.ui.utils.setOnlineSearchTerms
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.MiscFormatter
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

class SearchVM(val context: Context, val lcScope: CoroutineScope) {
    internal var automaticSearchDebouncer: Handler

    internal val pafeeds = mutableStateListOf<PAFeed>()

    internal var feedId: Long = 0
    internal val feeds = mutableStateListOf<Feed>()
    internal val episodes = mutableListOf<Episode>()
    internal val vms = mutableStateListOf<EpisodeVM>()
    internal var infoBarText = mutableStateOf("")
    internal var searchInFeed by mutableStateOf(false)
    internal var feedName by mutableStateOf("")
    internal var queryText by mutableStateOf("")

    internal var swipeActions: SwipeActions

    init {
        Logd(TAG, "init $curSearchString")
        queryText = curSearchString
        if (feedToSearchIn != null) {
            this.searchInFeed = true
            feedId = feedToSearchIn!!.id
            feedName = feedToSearchIn?.title ?: "Feed has no title"
        }
        automaticSearchDebouncer = Handler(Looper.getMainLooper())
        swipeActions = SwipeActions(context, TAG)
    }

    fun buildMoreItems() {
//        val nextItems = (vms.size until min(vms.size + VMS_CHUNK_SIZE, episodes.size)).map { EpisodeVM(episodes[it], TAG) }
        val nextItems = (vms.size until (vms.size + VMS_CHUNK_SIZE).coerceAtMost(episodes.size)).map { EpisodeVM(episodes[it], TAG) }
        if (nextItems.isNotEmpty()) vms.addAll(nextItems)
    }
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
//                    is FlowEvent.FeedListEvent, is FlowEvent.EpisodePlayedEvent -> search(queryText)
                    is FlowEvent.FeedListEvent -> search(queryText)
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lcScope.launch {
            EventFlow.stickyEvents.drop(1).collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
                    else -> {}
                }
            }
        }
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
        for (url in event.urls) {
            val pos: Int = episodes.indexOfItemWithDownloadUrl(url)
            if (pos >= 0 && pos < vms.size) vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
        }
    }

    data class Triplet(val episodes: List<Episode>, val feeds: List<Feed>, val pafeeds: List<PAFeed>)

    private var searchJob: Job? = null
    @SuppressLint("StringFormatMatches")
    internal fun search(query: String) {
        if (query.isBlank()) return
        if (searchJob != null) {
            searchJob?.cancel()
            vms.clear()
        }
        searchJob = lcScope.launch {
            try {
                val results_ = withContext(Dispatchers.IO) {
                    if (query.isEmpty()) Triplet(listOf(), listOf(), listOf())
                    else {
                        val queryWords = (if (query.contains(",")) query.split(",").map { it.trim() } else query.split("\\s+".toRegex())).dropWhile { it.isEmpty() }.toTypedArray()
                        val items: List<Episode> = searchEpisodes(feedId, queryWords)
                        val feeds: List<Feed> = searchFeeds(queryWords)
                        val pafeeds = searchPAFeeds(queryWords)
                        Logd(TAG, "performSearch items: ${items.size} feeds: ${feeds.size} pafeeds: ${pafeeds.size}")
                        Triplet(items, feeds, pafeeds)
                    }
                }
                withContext(Dispatchers.Main) {
                    val first_ = results_.episodes
                    episodes.clear()
                    vms.clear()
                    if (first_.isNotEmpty()) {
                        episodes.addAll(first_)
                        buildMoreItems()
                    }
                    infoBarText.value = "${episodes.size} episodes"
                    if (feedId == 0L) {
                        feeds.clear()
                        if (results_.feeds.isNotEmpty()) feeds.addAll(results_.feeds)
                    } else feeds.clear()
                    pafeeds.clear()
                    if (results_.pafeeds.isNotEmpty()) pafeeds.addAll(results_.pafeeds)
                }
            } catch (e: Throwable) { Logs(TAG, e) }
        }.apply { invokeOnCompletion { searchJob = null } }
    }

    private fun searchFeeds(queryWords: Array<String>): List<Feed> {
        Logd(TAG, "searchFeeds called ${SearchBy.AUTHOR.selected}")
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            var isStart = true
            val sb1 = StringBuilder()
            if (SearchBy.TITLE.selected) {
                sb1.append("eigenTitle contains[c] '${queryWords[i]}'")
                sb1.append(" OR ")
                sb1.append("customTitle contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.AUTHOR.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("author contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.DESCRIPTION.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("description contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.COMMENT.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("comment contains[c] '${queryWords[i]}'")
            }
            if (sb1.isEmpty()) continue
            sb.append("(")
            sb.append(sb1)
            sb.append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        if (sb.isEmpty()) return listOf()
        val queryString = sb.toString()
        Logd(TAG, "searchFeeds queryString: $queryString")
        return realm.query(Feed::class).query(queryString).find()
    }

    private fun searchPAFeeds(queryWords: Array<String>): List<PAFeed> {
        Logd(TAG, "searchFeeds called ${SearchBy.AUTHOR.selected}")
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            var isStart = true
            val sb1 = StringBuilder()
            if (SearchBy.TITLE.selected) {
                sb1.append("name contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.AUTHOR.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("author contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.DESCRIPTION.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("description contains[c] '${queryWords[i]}'")
//                isStart = false
            }
//            if (SearchBy.COMMENT.selected) {
//                if (!isStart) sb1.append(" OR ")
//                sb1.append("comment contains[c] '${queryWords[i]}'")
//            }
            if (sb1.isEmpty()) continue
            sb.append("(")
            sb.append(sb1)
            sb.append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        if (sb.isEmpty()) return listOf()
        val queryString = sb.toString()
        Logd(TAG, "searchFeeds queryString: $queryString")
        return realm.query(PAFeed::class).query(queryString).find()
    }

    /**
     * Searches the FeedItems of a specific Feed for a given string.
     * @param feedID  The id of the feed whose episodes should be searched.
     * @return A FutureTask object that executes the search request
     * and returns the search result as a List of FeedItems.
     */
    private fun searchEpisodes(feedID: Long, queryWords: Array<String>): List<Episode> {
        Logd(TAG, "searchEpisodes called")
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            val sb1 = StringBuilder()
            var isStart = true
            if (SearchBy.TITLE.selected) {
                sb1.append("title contains[c] '${queryWords[i]}'" )
                isStart = false
            }
            if (SearchBy.DESCRIPTION.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("description contains[c] '${queryWords[i]}'")
                sb1.append(" OR ")
                sb1.append("transcript contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.COMMENT.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("comment contains[c] '${queryWords[i]}'")
            }
            if (sb1.isEmpty()) continue
            sb.append("(")
            sb.append(sb1)
            sb.append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        if (sb.isEmpty()) return listOf()

        var queryString = sb.toString()
        if (feedID != 0L) queryString = "(feedId == $feedID) AND $queryString"
        Logd(TAG, "searchEpisodes queryString: $queryString")
        return realm.query(Episode::class).query(queryString).find()
    }

    internal fun searchOnline() {
        val query = queryText
        if (query.matches("http[s]?://.*".toRegex())) {
            setOnlineFeedUrl(query)
            mainNavController.navigate(Screens.OnlineFeed.name)
            return
        }
        setOnlineSearchTerms(CombinedSearcher::class.java, query)
        mainNavController.navigate(Screens.SearchResults.name)
    }
}

@Composable
fun SearchScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { SearchVM(context, scope) }

//        val displayUpArrow by remember { derivedStateOf { navController.backQueue.size > 1 } }
//        var upArrowVisible by rememberSaveable { mutableStateOf(displayUpArrow) }
//        LaunchedEffect(navController.backQueue) { upArrowVisible = displayUpArrow }

//    var displayUpArrow by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    lifecycleOwner.lifecycle.addObserver(vm.swipeActions)
                    if (vm.feedId > 0L) vm.searchInFeed = true
//                    vm.refreshSwipeTelltale()
                    if (vm.queryText.isNotBlank()) vm.search(vm.queryText)
                }
                Lifecycle.Event.ON_START -> vm.procFlowEvents()
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> vm.cancelFlowEvents()
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.episodes.clear()
            vm.feeds.clear()
            vm.vms.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar( title = { SearchBarRow(R.string.search_delimit, defaultText = vm.queryText) {
            curSearchString = it
            vm.queryText = it
            vm.search(vm.queryText)
        }},
            navigationIcon = { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
        )
    }

    @Composable
    fun CriteriaList() {
        val textColor = MaterialTheme.colorScheme.onSurface
        var showGrid by remember { mutableStateOf(false) }
        Column {
            Row {
                Button(onClick = {showGrid = !showGrid}) { Text(stringResource(R.string.show_criteria)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { vm.searchOnline() }) { Text(stringResource(R.string.search_online)) }
            }
            if (showGrid) NonlazyGrid(columns = 2, itemCount = SearchBy.entries.size) { index ->
                val c = SearchBy.entries[index]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                    var isChecked by remember { mutableStateOf(true) }
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { newValue ->
                            c.selected = newValue
                            isChecked = newValue
                        }
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(stringResource(c.nameRes), color = textColor)
                }
            }
        }
    }

    @Composable
    fun FeedsColumn() {
        val context = LocalContext.current
        val lazyListState = rememberLazyListState()
        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.feeds, key = { _, feed -> feed.id }) { index, feed ->
                Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    val imgLoc = remember(feed) { feed.imageUrl }
                    AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                        .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "imgvCover",
                        placeholder = painterResource(R.mipmap.ic_launcher),
                        error = painterResource(R.mipmap.ic_launcher),
                        modifier = Modifier.width(80.dp).height(80.dp).clickable(onClick = {
                            Logd(TAG, "icon clicked!")
                            if (!feed.isBuilding) {
                                feedOnDisplay = feed
                                feedScreenMode = FeedScreenMode.Info
                                mainNavController.navigate(Screens.FeedDetails.name)
                            }
                        })
                    )
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column(Modifier.weight(1f).padding(start = 10.dp).clickable(onClick = {
                        Logd(TAG, "clicked: ${feed.title}")
                        if (!feed.isBuilding) {
                            feedOnDisplay = feed
                            feedScreenMode = FeedScreenMode.List
                            mainNavController.navigate(Screens.FeedDetails.name)
                        }
                    })) {
                        Row {
                            if (feed.rating != Rating.UNRATED.code)
                                Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                                    modifier = Modifier.width(20.dp).height(20.dp).background(MaterialTheme.colorScheme.tertiaryContainer))
                            Text(feed.title ?: "No title", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Text(feed.author ?: "No author", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.padding(top = 5.dp)) {
                            val measureString = remember {
                                NumberFormat.getInstance().format(feed.episodes.size.toLong()) + " : " +
                                        DurationConverter.durationInHours(feed.totleDuration / 1000)
                            }
                            Text(measureString, color = textColor, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            var feedSortInfo by remember { mutableStateOf(feed.sortInfo) }
                            Text(feedSortInfo, color = textColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    //                                TODO: need to use state
                    if (feed.lastUpdateFailed) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_error), tint = Color.Red, contentDescription = "error")
                }
            }
        }
    }

    @Composable
    fun PAFeedsColumn() {
        val context = LocalContext.current
        val lazyListState = rememberLazyListState()
        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.pafeeds, key = { _, feed -> feed.id }) { index, feed ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val imgLoc = remember(feed) { feed.imageUrl }
                    AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                        .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "imgvCover",
                        placeholder = painterResource(R.mipmap.ic_launcher),
                        error = painterResource(R.mipmap.ic_launcher),
                        modifier = Modifier.width(60.dp).height(60.dp).clickable(onClick = {
                            Logd(TAG, "feedUrl: ${feed.name} [${feed.feedUrl}] [$]")
                            if (feed.feedUrl.isNotBlank()) {
                                setOnlineFeedUrl(feed.feedUrl)
                                mainNavController.navigate(Screens.OnlineFeed.name)
                            }
                        })
                    )
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column(Modifier.weight(1f).padding(start = 10.dp).clickable(onClick = {
                        Logd(TAG, "feedUrl: ${feed.name} [${feed.feedUrl}]")
                        if (feed.feedUrl.isNotBlank()) {
                            setOnlineFeedUrl(feed.feedUrl)
                            mainNavController.navigate(Screens.OnlineFeed.name)
                        }
                    })) {
                        Text(feed.name, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(feed.author, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(feed.category.joinToString(","), color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Episodes: ${feed.episodesNb} Average duration: ${feed.aveDuration} minutes", color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(MiscFormatter.formatLargeInteger(feed.subscribers) + " subscribers", color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    vm.swipeActions.ActionOptionsDialog()
    val tabTitles = listOf(R.string.episodes_label, R.string.feeds, R.string.pafeeds)
    val tabCounts = listOf(vm.episodes.size, vm.feeds.size, vm.pafeeds.size)
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (vm.searchInFeed) FilterChip(onClick = { }, label = { Text(vm.feedName) }, selected = vm.searchInFeed,
                trailingIcon = {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon", modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = {
                        vm.feedId = 0
                        vm.searchInFeed = false
                    }))
                }
            )
            CriteriaList()
            TabRow(modifier = Modifier.fillMaxWidth(), selectedTabIndex = selectedTabIndex.intValue, divider = {}, indicator = { tabPositions ->
                Box(modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex.intValue]).height(4.dp).background(Color.Blue))
            }) {
                tabTitles.forEachIndexed { index, titleRes ->
                    Tab(text = { Text(stringResource(titleRes)+"(${tabCounts[index]})") }, selected = selectedTabIndex.intValue == index, onClick = { selectedTabIndex.intValue = index })
                }
            }
            when (selectedTabIndex.intValue) {
                0 -> {
                    InforBar(vm.infoBarText, vm.swipeActions)
                    EpisodeLazyColumn(context as MainActivity, vms = vm.vms, buildMoreItems = { vm.buildMoreItems() }, swipeActions = vm.swipeActions, )
                }
                1 -> FeedsColumn()
                2 -> PAFeedsColumn()
            }
        }
    }
}

enum class SearchBy(val nameRes: Int, var selected: Boolean = true) {
    TITLE(R.string.title),
    AUTHOR(R.string.author),
    DESCRIPTION(R.string.description_label),
    COMMENT(R.string.my_opinion_label),
}

//    private fun showInputMethod(view: View) {
//        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//        imm.showSoftInput(view, 0)
//    }

private const val TAG: String = "SearchScreen"

private const val ARG_QUERY = "query"
private const val ARG_FEED = "feed"
private const val ARG_FEED_NAME = "feedName"
private const val SEARCH_DEBOUNCE_INTERVAL = 1500
