package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.getEpisodesCount
import ac.mdiq.podcini.storage.database.queueToVirtual
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.storage.model.SearchHistorySize
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.durationInHours
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeScreen
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.LocalNavController
import ac.mdiq.podcini.ui.compose.Screens
import ac.mdiq.podcini.ui.compose.SearchBarRow
import ac.mdiq.podcini.ui.compose.episodeForInfo
import ac.mdiq.podcini.ui.compose.handleBackSubScreens
import ac.mdiq.podcini.ui.utils.SearchAlgo
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.formatLargeInteger
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.xilinjia.krdb.notifications.ResultsChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.NumberFormat

private var curSearchString by mutableStateOf("")
fun setSearchTerms(query: String? = null) {
    Logd("setSearchTerms", "query: $query")
    if (query != null) curSearchString = query
}

class SearchVM: ViewModel() {
    val algo = SearchAlgo()

    internal val pafeeds = mutableStateListOf<PAFeed>()
    internal val feeds = mutableStateListOf<Feed>()

    var showAdvanced by mutableStateOf(false)

    val tabTitles = listOf(R.string.episodes_label, R.string.feeds, R.string.pafeeds)
    val selectedTabIndex = mutableIntStateOf(0)

    var listIdentity by mutableStateOf("")

    private val _searchQuery = MutableStateFlow(curSearchString)

    init {
        Logd(TAG, "init $curSearchString")
        algo.setSearchByAll()
    }

    data class Triplet(val episodes:  Flow<ResultsChange<Episode>>, val feeds: List<Feed>, val pafeeds: List<PAFeed>)

    fun onSearch(query: String) {
        _searchQuery.value = query
    }

    val episodesFlow: StateFlow<List<Episode>> = _searchQuery.filter { it.isNotEmpty() }.flatMapLatest { queryText ->
        val results_ = withContext(Dispatchers.IO) {
            if (queryText.isEmpty()) Triplet(emptyFlow(), listOf(), listOf())
            else {
                val queryWords = (if (queryText.contains(",")) queryText.split(",").map { it.trim() } else queryText.split("\\s+".toRegex())).dropWhile { it.isEmpty() }
                listIdentity = "Search.${queryWords.joinToString()}"
                val items = algo.searchEpisodes(0L, queryWords)
                val feeds: List<Feed> = algo.searchFeeds(queryWords)
                val pafeeds = algo.searchPAFeeds(queryWords)
                Triplet(items, feeds, pafeeds)
            }
        }
        withContext(Dispatchers.Main) {
            feeds.clear()
            if (results_.feeds.isNotEmpty()) feeds.addAll(results_.feeds)
            pafeeds.clear()
            if (results_.pafeeds.isNotEmpty()) pafeeds.addAll(results_.pafeeds)
            Logd(TAG, "Search found feeds: ${feeds.size}")
        }
        results_.episodes.map { it.list }
    }.distinctUntilChanged().stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList())
}

@ExperimentalMaterial3Api
@Composable
fun SearchScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = LocalNavController.current
    val drawerController = LocalDrawerController.current

    val textColor = MaterialTheme.colorScheme.onSurface
    val actionColor = MaterialTheme.colorScheme.tertiary

    val vm: SearchVM = viewModel()

    var swipeActions by remember { mutableStateOf(SwipeActions(TAG)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> lifecycleOwner.lifecycle.addObserver(swipeActions)
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(episodeForInfo) {
        if (episodeForInfo != null) handleBackSubScreens.add(TAG)
        else handleBackSubScreens.remove(TAG)
        onDispose { handleBackSubScreens.remove(TAG) }
    }

    BackHandler(enabled = handleBackSubScreens.contains(TAG)) { episodeForInfo = null }

    var queryText by remember { mutableStateOf(curSearchString) }

    @Composable
    fun MyTopAppBar() {
        Box {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchBarRow(R.string.search_hint, defaultText = queryText, modifier = Modifier.weight(1f) , history = appAttribs.searchHistory) { str ->
                        if (str.isBlank()) return@SearchBarRow
                        curSearchString = str
                        queryText = str
                        upsertBlk(appAttribs) {
                            if (str in it.searchHistory) it.searchHistory.remove(str)
                            it.searchHistory.add(0, str)
                            if (it.searchHistory.size > SearchHistorySize+4) it.searchHistory.apply { subList(SearchHistorySize, size).clear() }
                        }
//                        vm.search()
                        vm.onSearch(queryText)
                    }
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings), contentDescription = "Advanced", modifier = Modifier.clickable(onClick = { vm.showAdvanced = !vm.showAdvanced}))
                }
            }, navigationIcon = { IconButton(onClick = { if (navController.previousBackStackEntry != null) navController.popBackStack() else drawerController?.open()  }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @Composable
    fun FeedsColumn() {
        val context = LocalContext.current
        val lazyListState = rememberLazyListState()
        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.feeds, key = { feed -> feed.id }) { feed ->
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

    val episodes by vm.episodesFlow.collectAsStateWithLifecycle()

    val infoBarText = remember(episodes) { mutableStateOf("${episodes.size} episodes") }
    val tabCounts = remember(episodes.size, vm.feeds.size, vm.pafeeds.size) { listOf(episodes.size, vm.feeds.size, vm.pafeeds.size) }
    swipeActions.ActionOptionsDialog()

    if (episodeForInfo != null) EpisodeScreen(episodeForInfo!!)
    else Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (vm.showAdvanced) {
                var showSearchBy by remember { mutableStateOf(false) }
                Row {
                    Text(stringResource(R.string.show_criteria), color = actionColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = { showSearchBy = !showSearchBy }))
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.search_online), color = actionColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = {
                        val query = queryText
                        if (query.matches("http[s]?://.*".toRegex())) {
                            navController.navigate("${Screens.OnlineFeed.name}?url=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}")
                            return@clickable
                        }
                        setOnlineSearchTerms(query = query)
                        navController.navigate(Screens.FindFeeds.name)
                    }))
                }
                if (showSearchBy) vm.algo.SearchByGrid()
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                vm.tabTitles.forEachIndexed { index, titleRes ->
                    Tab(modifier = Modifier.wrapContentWidth().padding(horizontal = 2.dp, vertical = 4.dp).background(shape = RoundedCornerShape(8.dp),
                        color = if (vm.selectedTabIndex.intValue == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else { Color.Transparent }), selected = vm.selectedTabIndex.intValue == index,
                        onClick = { vm.selectedTabIndex.intValue = index },
                        text = { Text(text = stringResource(titleRes) + "(${tabCounts[index]})", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            color = if (vm.selectedTabIndex.intValue == index) MaterialTheme.colorScheme.primary else { MaterialTheme.colorScheme.onSurface }) }
                    )
                }
            }
            when (vm.selectedTabIndex.intValue) {
                0 -> {
                    InforBar(swipeActions) { Text(infoBarText.value, style = MaterialTheme.typography.bodyMedium) }
                    EpisodeLazyColumn(episodes, swipeActions = swipeActions,
                        actionButtonCB = { e, type ->
                            if (type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAY_LOCAL, ButtonTypes.STREAM))
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
