package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.storage.database.getEpisodesCount
import ac.mdiq.podcini.storage.database.queueToVirtual
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.sortPairOf
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.durationInHours
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.NonlazyGrid
import ac.mdiq.podcini.ui.compose.SearchBarRow
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.formatLargeInteger
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.xilinjia.krdb.notifications.ResultsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.NumberFormat

private var curSearchString by mutableStateOf("")
fun setSearchTerms(query: String? = null) {
    Logd("setSearchTerms", "query: $query")
    if (query != null) curSearchString = query
}

private val searchBIES = SearchBy.entries.toMutableSet()
fun setSearchByAll() {
    searchBIES.addAll(SearchBy.entries)
}
private fun isSelected(by: SearchBy) = searchBIES.contains(by)
private fun setSelected(by: SearchBy, selected: Boolean) {
    if (selected) searchBIES.add(by) else searchBIES.remove(by)
}

private fun contains(s: String): String {
    return if (s.startsWith('-')) {
        val s1 = s.substring(1).trim()
        "contains[c] '$s1'"
    } else "contains[c] '$s'"
}

fun searchEpisodesQuery(feedID: Long, queryWords: List<String>): String {
    Logd("searchEpisodesQuery", "searchEpisodes called")
    val sb = StringBuilder()
    for (i in queryWords.indices) {
        val sb1 = StringBuilder()
        var isStart = true
        val qw = queryWords[i]
        val exl = qw.startsWith('-')
        val command = contains(qw)
        if (isSelected(SearchBy.TITLE)) {
            if (exl) sb1.append("NOT " )
            sb1.append("(title $command)")
            isStart = false
        }
        if (isSelected(SearchBy.DESCRIPTION)) {
            if (!isStart) sb1.append(if (exl) " AND " else " OR ")
            if (exl) sb1.append("NOT ")
            sb1.append("(description $command)")
            if (exl) sb1.append(" AND ") else sb1.append(" OR ")
            if (exl) sb1.append("NOT ")
            sb1.append("(transcript $command)")
            isStart = false
        }
        if (isSelected(SearchBy.COMMENT)) {
            if (!isStart) sb1.append(if (exl) " AND " else " OR ")
            if (exl) sb1.append("NOT ")
            sb1.append("(comment $command)")
        }
        if (sb1.isEmpty()) continue
        sb.append("(")
        sb.append(sb1)
        sb.append(") ")
        if (i != queryWords.size - 1) sb.append("AND ")
    }
    if (sb.isEmpty()) return ""

    var queryString = sb.toString()
    if (feedID != 0L) queryString = "(feedId == $feedID) AND $queryString"
    Logd(TAG, "searchEpisodes queryString: $queryString")

    return queryString
}

@Composable
fun SearchByGrid(exl: Set<SearchBy> = setOf()) {
    NonlazyGrid(columns = 2, itemCount = SearchBy.entries.size) { index ->
        val c = SearchBy.entries[index]
        if (c !in exl) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
            var isChecked by remember { mutableStateOf(isSelected(c)) }
            Checkbox(checked = isChecked,
                onCheckedChange = { newValue ->
                    setSelected(c, newValue)
                    isChecked = newValue
                }
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(stringResource(c.nameRes))
        }
    }
}

class SearchVM(val context: Context, val lcScope: CoroutineScope) {
    internal var automaticSearchDebouncer: Handler

    internal val pafeeds = mutableStateListOf<PAFeed>()
    internal val feeds = mutableStateListOf<Feed>()

    var episodesFlow by mutableStateOf<Flow<ResultsChange<Episode>>>(emptyFlow())
    var listIdentity by mutableStateOf("")

    internal var queryText by mutableStateOf("")

    init {
        Logd(TAG, "init $curSearchString")
        setSearchByAll()
        queryText = curSearchString
        automaticSearchDebouncer = Handler(Looper.getMainLooper())
    }

    data class Triplet(val episodes:  Flow<ResultsChange<Episode>>, val feeds: List<Feed>, val pafeeds: List<PAFeed>)

    private var searchJob: Job? = null
    @SuppressLint("StringFormatMatches")
    internal fun search(query: String) {
        fun searchFeeds(queryWords: List<String>): List<Feed> {
            val sb = StringBuilder()
            for (i in queryWords.indices) {
                var isStart = true
                val sb1 = StringBuilder()
                val qw = queryWords[i]
                val exl = qw.startsWith('-')
                val command = contains(qw)
                if (isSelected(SearchBy.TITLE)) {
                    if (exl) sb1.append("NOT ")
                    sb1.append("(eigenTitle $command)")
                    if (exl) sb1.append(" AND ") else sb1.append(" OR ")
                    if (exl) sb1.append("NOT ")
                    sb1.append("(customTitle $command)")
                    isStart = false
                }
                if (isSelected(SearchBy.AUTHOR)) {
                    if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                    if (exl) sb1.append("NOT ")
                    sb1.append("(author $command)")
                    isStart = false
                }
                if (isSelected(SearchBy.DESCRIPTION)) {
                    if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                    if (exl) sb1.append("NOT ")
                    sb1.append("(description $command)")
                    isStart = false
                }
                if (isSelected(SearchBy.COMMENT)) {
                    if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                    if (exl) sb1.append("NOT ")
                    sb1.append("(comment $command)")
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
        fun searchPAFeeds(queryWords: List<String>): List<PAFeed> {
            val sb = StringBuilder()
            for (i in queryWords.indices) {
                var isStart = true
                val sb1 = StringBuilder()
                val qw = queryWords[i]
                val exl = qw.startsWith('-')
                val command = contains(qw)
                if (isSelected(SearchBy.TITLE)) {
                    if (exl) sb1.append("NOT ")
                    sb1.append("(name $command)")
                    isStart = false
                }
                if (isSelected(SearchBy.AUTHOR)) {
                    if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                    if (exl) sb1.append("NOT ")
                    sb1.append("(author $command)")
                    isStart = false
                }
                if (isSelected(SearchBy.DESCRIPTION)) {
                    if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                    if (exl) sb1.append("NOT ")
                    sb1.append("(description $command)")
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
            return realm.query(PAFeed::class).query(queryString).find()
        }
        fun searchEpisodes(feedID: Long, queryWords: List<String>): Flow<ResultsChange<Episode>> {
            val queryString = searchEpisodesQuery(feedID, queryWords)
            if (queryString.isBlank()) return emptyFlow()
            return realm.query(Episode::class).query(queryString).sort(sortPairOf(EpisodeSortOrder.DATE_DESC)).asFlow()
        }

        if (query.isBlank()) return
        if (searchJob != null) {
            searchJob?.cancel()
        }
        searchJob = lcScope.launch {
            try {
                val results_ = withContext(Dispatchers.IO) {
                    if (query.isEmpty()) Triplet(emptyFlow(), listOf(), listOf())
                    else {
                        val queryWords = (if (query.contains(",")) query.split(",").map { it.trim() } else query.split("\\s+".toRegex())).dropWhile { it.isEmpty() }
                        listIdentity = "Search.${queryWords.joinToString()}"
                        val items = searchEpisodes(0L, queryWords)
                        val feeds: List<Feed> = searchFeeds(queryWords)
                        val pafeeds = searchPAFeeds(queryWords)
//                        Logd(TAG, "performSearch items: ${items.size} feeds: ${feeds.size} pafeeds: ${pafeeds.size}")
                        Triplet(items, feeds, pafeeds)
                    }
                }
                withContext(Dispatchers.Main) {
                    episodesFlow = results_.episodes
                    feeds.clear()
                    if (results_.feeds.isNotEmpty()) feeds.addAll(results_.feeds)
                    pafeeds.clear()
                    if (results_.pafeeds.isNotEmpty()) pafeeds.addAll(results_.pafeeds)
                }
            } catch (e: Throwable) { Logs(TAG, e) }
        }.apply { invokeOnCompletion { searchJob = null } }
    }
}

@Composable
fun SearchScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = LocalNavController.current
    val drawerState = LocalDrawerController.current

    val textColor = MaterialTheme.colorScheme.onSurface
    val vm = remember { SearchVM(context, scope) }

    var swipeActions by remember { mutableStateOf(SwipeActions(context, TAG)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    lifecycleOwner.lifecycle.addObserver(swipeActions)
                    if (vm.queryText.isNotBlank()) vm.search(vm.queryText)
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
            vm.feeds.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        Box {
            TopAppBar(title = {
                SearchBarRow(R.string.search_hint, defaultText = vm.queryText) {
                    curSearchString = it
                    vm.queryText = it
                    vm.search(vm.queryText)
                }
            }, navigationIcon = { IconButton(onClick = { if (navController.previousBackStackEntry != null) navController.popBackStack() else drawerState.open()  }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @Composable
    fun FeedsColumn() {
        val context = LocalContext.current
        val lazyListState = rememberLazyListState()
        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.feeds, key = { _, feed -> feed.id }) { index, feed ->
                Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    val img = remember(feed) { ImageRequest.Builder(context).data(feed.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                    AsyncImage(model = img, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                        modifier = Modifier.width(80.dp).height(80.dp).clickable(onClick = {
                            Logd(TAG, "icon clicked!")
                            if (!feed.isBuilding) navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.id}&modeName=${FeedScreenMode.Info.name}")
                        })
                    )
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column(Modifier.weight(1f).padding(start = 10.dp).clickable(onClick = {
                        Logd(TAG, "clicked: ${feed.title}")
                        if (!feed.isBuilding) navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.id}")
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
                            val measureString = remember { run {
                                val numEpisodes = getEpisodesCount(null, feed.id)
                                NumberFormat.getInstance().format(numEpisodes.toLong()) + " : " + durationInHours(feed.totleDuration / 1000)
                            }
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
                fun navToOnlineFeed() {
                    if (feed.feedUrl.isNotBlank()) navController.navigate("${Screens.OnlineFeed.name}?url=${URLEncoder.encode(feed.feedUrl, StandardCharsets.UTF_8.name())}")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val img = remember(feed) { ImageRequest.Builder(context).data(feed.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                    AsyncImage(model = img, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                        modifier = Modifier.width(60.dp).height(60.dp).clickable(onClick = {
                            Logd(TAG, "feedUrl: ${feed.name} [${feed.feedUrl}] [$]")
                            navToOnlineFeed()
                        })
                    )
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column(Modifier.weight(1f).padding(start = 10.dp).clickable(onClick = {
                        Logd(TAG, "feedUrl: ${feed.name} [${feed.feedUrl}]")
                        navToOnlineFeed()
                    })) {
                        Text(feed.name, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(feed.author, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(feed.category.joinToString(","), color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Episodes: ${feed.episodesNb} Average duration: ${feed.aveDuration} minutes", color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatLargeInteger(feed.subscribers) + " subscribers", color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    val episodesChange by vm.episodesFlow.collectAsStateWithLifecycle(initialValue = null)
    val episodes = episodesChange?.list ?: emptyList()
    val infoBarText = remember(episodes) { mutableStateOf("${episodes.size} episodes") }

    swipeActions.ActionOptionsDialog()
    val tabTitles = remember { listOf(R.string.episodes_label, R.string.feeds, R.string.pafeeds) }
    val tabCounts = listOf(episodes.size, vm.feeds.size, vm.pafeeds.size)
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            var showSearchBy by remember { mutableStateOf(false) }
            Row {
                OutlinedButton(onClick = {showSearchBy = !showSearchBy}) { Text(stringResource(R.string.show_criteria)) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = {
                    val query = vm.queryText
                    if (query.matches("http[s]?://.*".toRegex())) {
                        navController.navigate("${Screens.OnlineFeed.name}?url=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}")
                        return@OutlinedButton
                    }
                    setOnlineSearchTerms(CombinedSearcher::class.java, query)
                    navController.navigate(Screens.OnlineSearch.name)
                }) { Text(stringResource(R.string.search_online)) }
            }
            if (showSearchBy) SearchByGrid()
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                tabTitles.forEachIndexed { index, titleRes ->
                    Tab(modifier = Modifier.wrapContentWidth().padding(horizontal = 2.dp, vertical = 4.dp).background(shape = RoundedCornerShape(8.dp),
                        color = if (selectedTabIndex.intValue == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else { Color.Transparent }),
                        selected = selectedTabIndex.intValue == index,
                        onClick = { selectedTabIndex.intValue = index },
                        text = { Text(text = stringResource(titleRes) + "(${tabCounts[index]})", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedTabIndex.intValue == index) MaterialTheme.colorScheme.primary else { MaterialTheme.colorScheme.onSurface }) }
                    )
                }
            }
            when (selectedTabIndex.intValue) {
                0 -> {
                    InforBar(infoBarText, swipeActions)
                    EpisodeLazyColumn(context as MainActivity, episodes, swipeActions = swipeActions,
                        actionButtonCB = { e, type ->
                            if (type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAYLOCAL, ButtonTypes.STREAM))
                                runOnIOScope { queueToVirtual(e, episodes, vm.listIdentity, EpisodeSortOrder.DATE_DESC) }
                        })
                }
                1 -> FeedsColumn()
                2 -> PAFeedsColumn()
            }
        }
    }
}

enum class SearchBy(val nameRes: Int) {
    TITLE(R.string.title),
    DESCRIPTION(R.string.description_label),
    COMMENT(R.string.my_opinion_label),
    AUTHOR(R.string.author),
}

//    private fun showInputMethod(view: View) {
//        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//        imm.showSoftInput(view, 0)
//    }

private const val TAG: String = "SearchScreen"
