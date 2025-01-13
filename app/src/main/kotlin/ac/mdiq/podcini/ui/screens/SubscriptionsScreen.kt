package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.DocumentFileExportWorker
import ac.mdiq.podcini.preferences.ExportTypes
import ac.mdiq.podcini.preferences.ExportWorker
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.getTags
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.Feed.AutoDeleteAction
import ac.mdiq.podcini.storage.model.Feed.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Screens
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.setSearchTerms
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import android.app.Activity.RESULT_OK
import android.content.*
import android.net.Uri
import android.util.Log
import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.apache.commons.lang3.StringUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SubscriptionsVM(val context: Context, val lcScope: CoroutineScope) {
    val prefs: SharedPreferences by lazy { context.getSharedPreferences("SubscriptionsFragmentPrefs", Context.MODE_PRIVATE) }

    private var _feedsFilter: String? = null
    internal var feedsFilter: String
        get() {
            if (_feedsFilter == null) _feedsFilter = prefs.getString("feedsFilter", "") ?: ""
            return _feedsFilter ?: ""
        }
        set(filter) {
            _feedsFilter = filter
            prefs.edit().putString("feedsFilter", filter).apply()
        }

    private var _tagFilterIndex: Int = -1
    internal var tagFilterIndex: Int
        get() {
            if (_tagFilterIndex < 0) _tagFilterIndex = prefs.getInt("tagFilterIndex", 0)
            return _tagFilterIndex
        }
        set(index) {
            _tagFilterIndex = index
            prefs.edit().putInt("tagFilterIndex", index).apply()
        }
    private var _queueFilterIndex: Int = -1
    internal var queueFilterIndex: Int
        get() {
            if (_queueFilterIndex < 0) _queueFilterIndex = prefs.getInt("queueFilterIndex", 0)
            return _queueFilterIndex
        }
        set(index) {
            _queueFilterIndex = index
            prefs.edit().putInt("queueFilterIndex", index).apply()
        }

    internal val tags: MutableList<String> = mutableListOf()
    internal val queueIds: MutableList<Long> = mutableListOf()
    internal val spinnerTexts: MutableList<String> = mutableListOf("Any queue", "No queue")

    private var infoTextFiltered = ""
    private var infoTextUpdate = ""

    //    TODO: currently not used
    internal var displayedFolder by mutableStateOf("")

    internal var txtvInformation by mutableStateOf("")
    internal var feedCountState by mutableStateOf("")
    internal var feedSorted by mutableIntStateOf(0)

    internal var sortIndex by mutableStateOf(0)
    internal var titleAscending by mutableStateOf(true)
    internal var dateAscending by mutableStateOf(true)
    internal var countAscending by mutableStateOf(true)
    internal var dateSortIndex by mutableStateOf(0)
    internal val playStateSort = MutableList(PlayState.entries.size) { mutableStateOf(false) }
    internal val playStateCodeSet = mutableSetOf<String>()
    internal val ratingSort = MutableList(Rating.entries.size) { mutableStateOf(false) }
    internal val ratingCodeSet = mutableSetOf<String>()
    internal var downlaodedSortIndex by mutableStateOf(-1)
    internal var commentedSortIndex by mutableStateOf(-1)

    internal var feedListFiltered = mutableStateListOf<Feed>()
    internal var showFilterDialog by mutableStateOf(false)
    internal var showSortDialog by mutableStateOf(false)
    internal var noSubscription by mutableStateOf(false)
    internal var showNewSynthetic by mutableStateOf(false)

    internal var useGrid by mutableStateOf<Boolean?>(null)
    internal val useGridLayout by mutableStateOf(getPref(AppPrefs.prefFeedGridLayout, false))

    internal var selectMode by mutableStateOf(false)

    private fun queryStringOfTags() : String {
        return when (tagFilterIndex) {
            0 ->  ""    // All feeds
//            TODO: #root appears not used in RealmDB, is it a SQLite specialty
            1 ->  " (tags.@count == 0 OR (tags.@count != 0 AND ALL tags == '#root' )) "
            else -> {   // feeds with the chosen tag
                val tag = tags[tagFilterIndex]
                " ANY tags == '$tag' "
            }
        }
    }

    private fun queryStringOfQueues() : String {
        return when (queueFilterIndex) {
            0 ->  ""    // All feeds
            1 -> " queueId == -2 "
            else -> {   // feeds associated with the chosen queue
                val qid = queueIds[queueFilterIndex-2]
                " queueId == '$qid' "
            }
        }
    }

    internal fun resetTags() {
        tags.clear()
        tags.add("All tags")
        tags.add("Untagged")
        tags.addAll(getTags())
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
                    is FlowEvent.FeedListEvent -> loadSubscriptions()
                    is FlowEvent.EpisodePlayedEvent -> loadSubscriptions()
                    is FlowEvent.FeedTagsChangedEvent -> loadSubscriptions()
                    is FlowEvent.FeedChangeEvent -> loadSubscriptions()
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lcScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedUpdatingEvent -> {
                        Logd(TAG, "FeedUpdateRunningEvent: ${event.isRunning}")
                        infoTextUpdate = if (event.isRunning) " " + context.getString(R.string.refreshing_label) else ""
                        txtvInformation = (infoTextFiltered + infoTextUpdate)
                        if (!event.isRunning && event.id != prevFeedUpdatingEvent?.id) loadSubscriptions()
                        prevFeedUpdatingEvent = event
                    }
                    else -> {}
                }
            }
        }
    }

    private var loadingJob: Job? = null
    internal fun loadSubscriptions() {
        if (loadingJob != null) {
            loadingJob?.cancel()
            feedListFiltered.clear()
        }
        loadingJob = lcScope.launch {
            val feedList: List<Feed>
            try {
                withContext(Dispatchers.IO) {
                    resetTags()
                    feedList = fetchAndSort(false)
                }
                withContext(Dispatchers.Main) {
                    noSubscription = feedList.isEmpty()
                    feedListFiltered.clear()
                    feedListFiltered.addAll(feedList)
                    feedCountState = feedListFiltered.size.toString() + " / " + feedCount.toString()
                    infoTextFiltered = " "
                    if (feedsFilter.isNotEmpty()) infoTextFiltered = context.getString(R.string.filtered_label)
                    txtvInformation = (infoTextFiltered + infoTextUpdate)
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }.apply { invokeOnCompletion { loadingJob = null } }
    }

    private fun comparator(counterMap: Map<Long, Long>, dir: Int): Comparator<Feed> {
        return Comparator { lhs: Feed, rhs: Feed ->
            val counterLhs = counterMap[lhs.id]?:0
            val counterRhs = counterMap[rhs.id]?:0
            when {
                // reverse natural order: podcast with most unplayed episodes first
                counterLhs > counterRhs -> -dir
                counterLhs == counterRhs -> (lhs.title?.compareTo(rhs.title!!, ignoreCase = true) ?: -1) * dir
                else -> dir
            }
        }
    }

    internal fun exportOPML(uri: Uri?, selectedItems: List<Feed>) {
        try {
            runBlocking {
                Logd(TAG, "selectedFeeds: ${selectedItems.size}")
                if (uri == null) ExportWorker(OpmlWriter(), context).exportFile(selectedItems)
                else {
                    val worker = DocumentFileExportWorker(OpmlWriter(), context, uri)
                    worker.exportFile(selectedItems)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "exportOPML error: ${e.message}") }
    }

    private fun sortArrays2CodeSet() {
        playStateCodeSet.clear()
        for (i in playStateSort.indices) {
            if (playStateSort[i].value) playStateCodeSet.add(PlayState.entries[i].code.toString())
        }
        ratingCodeSet.clear()
        for (i in ratingSort.indices) {
            if (ratingSort[i].value) ratingCodeSet.add(Rating.entries[i].code.toString())
        }
    }
    private fun sortArraysFromCodeSet() {
        for (i in playStateSort.indices) playStateSort[i].value = false
        for (c in playStateCodeSet) playStateSort[PlayState.fromCode(c.toInt()).ordinal].value = true
        for (i in ratingSort.indices) ratingSort[i].value = false
        for (c in ratingCodeSet) ratingSort[Rating.fromCode(c.toInt()).ordinal].value = true
    }

    private fun saveSortingPrefs() {
        prefs.edit().putInt("sortIndex", sortIndex).apply()
        prefs.edit().putBoolean("titleAscending", titleAscending).apply()
        prefs.edit().putBoolean("dateAscending", dateAscending).apply()
        prefs.edit().putBoolean("countAscending", countAscending).apply()
        prefs.edit().putInt("dateSortIndex", dateSortIndex).apply()
        prefs.edit().putInt("downlaodedSortIndex", downlaodedSortIndex).apply()
        prefs.edit().putInt("commentedSortIndex", commentedSortIndex).apply()
        sortArrays2CodeSet()
        prefs.edit().putStringSet("playStateCodeSet", playStateCodeSet).apply()
        prefs.edit().putStringSet("ratingCodeSet", ratingCodeSet).apply()
    }

    internal fun getSortingPrefs() {
        sortIndex = prefs.getInt("sortIndex", 0)
        titleAscending = prefs.getBoolean("titleAscending", true)
        dateAscending = prefs.getBoolean("dateAscending", true)
        countAscending = prefs.getBoolean("countAscending", true)
        dateSortIndex = prefs.getInt("dateSortIndex", 0)
        downlaodedSortIndex = prefs.getInt("downlaodedSortIndex", -1)
        commentedSortIndex = prefs.getInt("commentedSortIndex", -1)
        playStateCodeSet.clear()
        playStateCodeSet.addAll(prefs.getStringSet("playStateCodeSet", setOf())!!)
        ratingCodeSet.clear()
        ratingCodeSet.addAll(prefs.getStringSet("ratingCodeSet", setOf())!!)
        sortArraysFromCodeSet()
    }

    internal fun fetchAndSort(build: Boolean = true): List<Feed> {
        fun getFeedList(): MutableList<Feed> {
            var fQueryStr = FeedFilter(feedsFilter).queryString()
            val tagsQueryStr = queryStringOfTags()
            if (tagsQueryStr.isNotEmpty())  fQueryStr += " AND $tagsQueryStr"
            val queuesQueryStr = queryStringOfQueues()
            if (queuesQueryStr.isNotEmpty())  fQueryStr += " AND $queuesQueryStr"
            Logd(TAG, "sortFeeds() called $feedsFilter $fQueryStr")
            return getFeedList(fQueryStr).toMutableList()
        }

        val feedList_ = getFeedList()
        for (f in feedList_) f.sortInfo = ""
        val comparator = when (sortIndex) {
            0 -> {
                val dir = if (titleAscending) 1 else -1
                Comparator { lhs: Feed, rhs: Feed ->
                    val t1 = lhs.title
                    val t2 = rhs.title
                    when {
                        t1 == null -> dir
                        t2 == null -> -dir
                        else -> t1.compareTo(t2, ignoreCase = true) * dir
                    }
                }
            }
            1 -> {
                val dir = if (dateAscending) 1 else -1
                when (dateSortIndex) {
                    0 -> {  // date publish
                        var playStateQueries = ""
                        for (i in playStateSort.indices) {
                            if (playStateSort[i].value) {
                                if (playStateQueries.isNotEmpty()) playStateQueries += " OR "
                                playStateQueries += " playState == ${PlayState.entries[i].code} "
                            }
                        }
                        var queryString = "feedId == $0"
                        if (playStateQueries.isNotEmpty()) queryString += " AND ($playStateQueries)"
                        queryString += " SORT(pubDate DESC)"
                        Logd(TAG, "queryString: $queryString")
                        val counterMap: MutableMap<Long, Long> = mutableMapOf()
                        for (f in feedList_) {
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.pubDate ?: 0L
                            counterMap[f.id] = d
                            f.sortInfo = formatDateTimeFlex(Date(d))
                        }
                        comparator(counterMap, dir)
                    }
                    1 -> {  // date downloaded
                        val queryString = "feedId == $0 SORT(downloadTime DESC)"
                        val counterMap: MutableMap<Long, Long> = mutableMapOf()
                        for (f in feedList_) {
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.downloadTime ?: 0L
                            counterMap[f.id] = d
                            f.sortInfo = "Downloaded: " + formatDateTimeFlex(Date(d))
                        }
                        Logd(TAG, "queryString: $queryString")
                        comparator(counterMap, dir)
                    }
                    else -> comparator(mutableMapOf(), 0)
                }
            }
            else -> {   // count
                val dir = if (countAscending) 1 else -1
                var playStateQueries = ""
                for (i in playStateSort.indices) {
                    if (playStateSort[i].value) {
                        if (playStateQueries.isNotEmpty()) playStateQueries += " OR "
                        playStateQueries += " playState == ${PlayState.entries[i].code} "
                    }
                }
                var ratingQueries = ""
                for (i in ratingSort.indices) {
                    if (ratingSort[i].value) {
                        if (ratingQueries.isNotEmpty()) ratingQueries += " OR "
                        ratingQueries += " rating == ${Rating.entries[i].code} "
                    }
                }
                val downloadedQuery = if (downlaodedSortIndex == 0) " downloaded == true " else if (downlaodedSortIndex == 1) " downloaded == false " else ""
                val commentedQuery = if (commentedSortIndex == 0) " comment != '' " else if (commentedSortIndex == 1) " comment == '' " else ""

                var queryString = "feedId == $0"
                if (playStateQueries.isNotEmpty()) queryString += " AND ($playStateQueries)"
                if (ratingQueries.isNotEmpty()) queryString += " AND ($ratingQueries)"
                if (downloadedQuery.isNotEmpty()) queryString += " AND ($downloadedQuery)"
                if (commentedQuery.isNotEmpty()) queryString += " AND ($commentedQuery)"
                Logd(TAG, "queryString: $queryString")
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feedList_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = "$c counts"
                }
                comparator(counterMap, dir)
            }
        }
        feedSorted++
        if (!build) return feedList_.sortedWith(comparator)

        saveSortingPrefs()
        feedListFiltered.clear()
        feedListFiltered.addAll(feedList_.sortedWith(comparator))
        return listOf()
    }

}

@Composable
fun SubscriptionsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { SubscriptionsVM(context, scope) }

//        val displayUpArrow by remember { derivedStateOf { navController.backQueue.size > 1 } }
//        var upArrowVisible by rememberSaveable { mutableStateOf(displayUpArrow) }
//        LaunchedEffect(navController.backQueue) { upArrowVisible = displayUpArrow }

    var displayUpArrow by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "ON_CREATE")
//                        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
//                        displayUpArrow = savedInstanceState?.getBoolean(KEY_UP_ARROW) ?: false
                    vm.getSortingPrefs()
//                    if (arguments != null) vm.displayedFolder = requireArguments().getString(ARGUMENT_FOLDER, null)
                    vm.resetTags()

                    val queues = realm.query(PlayQueue::class).find()
                    vm.queueIds.addAll(queues.map { it.id })
                    vm.spinnerTexts.addAll(queues.map { it.name })
                    vm.feedCountState = vm.feedListFiltered.size.toString() + " / " + feedCount.toString()
                    vm.loadSubscriptions()
                }
                Lifecycle.Event.ON_START -> {
                    Logd(TAG, "ON_START")
                    vm.procFlowEvents()
                }
                Lifecycle.Event.ON_RESUME -> {
                    Logd(TAG, "ON_RESUME")
                }
                Lifecycle.Event.ON_STOP -> {
                    Logd(TAG, "ON_STOP")
                    vm.cancelFlowEvents()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Logd(TAG, "ON_DESTROY")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.feedListFiltered.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler { mainNavController.popBackStack() }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar(displayUpArrow: Boolean) {
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = { Text( if (vm.displayedFolder.isNotEmpty()) vm.displayedFolder else "") },
            navigationIcon = if (displayUpArrow) {
                { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { MainActivity.openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_subscriptions), contentDescription = "Open Drawer") } }
            },
            actions = {
                IconButton(onClick = {
                    setSearchTerms("")
                    mainNavController.navigate(Screens.Search.name)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                IconButton(onClick = { vm.showFilterDialog = true
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), contentDescription = "filter") }
                IconButton(onClick = { vm.showSortDialog = true
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "sort") }
//                IconButton(onClick = {
//                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_chart_box), contentDescription = "statistics") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.new_synth_label)) }, onClick = {
                        vm.showNewSynthetic = true
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                        FeedUpdateManager.runOnceOrAsk(vm.context, fullUpdate = true)
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.toggle_grid_list)) }, onClick = {
                        vm.useGrid = if (vm.useGrid == null) !vm.useGridLayout else !vm.useGrid!!
                        expanded = false
                    })
                }
            }
        )
    }

    @Composable
    fun InforBar() {
        Row(Modifier.padding(start = 20.dp, end = 20.dp)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_info), contentDescription = "info", tint = textColor)
            Spacer(Modifier.weight(1f))
            Text(vm.txtvInformation, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable {
                if (vm.feedsFilter.isNotEmpty()) vm.showFilterDialog = true
            } )
            Spacer(Modifier.weight(1f))
            Text(vm.feedCountState, color = textColor)
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun LazyList() {
        var selectedSize by remember { mutableStateOf(0) }
        val selected = remember { mutableStateListOf<Feed>() }
        var longPressIndex by remember { mutableIntStateOf(-1) }
        var refreshing by remember { mutableStateOf(false)}

        var showRemoveFeedDialog by remember { mutableStateOf(false) }
        if (showRemoveFeedDialog) RemoveFeedDialog(selected, onDismissRequest = {showRemoveFeedDialog = false}, null)

        fun saveFeed(cbBlock: (Feed)->Unit) {
            runOnIOScope { for (feed in selected) upsert(feed) { cbBlock(it) } }
            val numItems = selected.size
//        (vm.context as MainActivity).showSnackbarAbovePlayer(vm.context.resources.getQuantityString(R.plurals.updated_feeds_batch_label, numItems, numItems), Snackbar.LENGTH_LONG)
        }

        @Composable
        fun AutoDeleteHandlerDialog(onDismissRequest: () -> Unit) {
            val (selectedOption, _) = remember { mutableStateOf("") }
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeedAutoDeleteOptions.forEach { text ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectable(selected = (text == selectedOption), onClick = {
                                if (text != selectedOption) {
                                    val autoDeleteAction: AutoDeleteAction = AutoDeleteAction.fromTag(text)
                                    saveFeed { it: Feed -> it.autoDeleteAction = autoDeleteAction }
                                    onDismissRequest()
                                }
                            })) {
                                RadioButton(selected = (text == selectedOption), onClick = { })
                                Text(text = text, style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun SetAssociateQueueDialog(onDismissRequest: () -> Unit) {
            var selectedOption by remember {mutableStateOf("")}
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        queueSettingOptions.forEach { option ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = option == selectedOption,
                                    onCheckedChange = { isChecked ->
                                        selectedOption = option
                                        if (isChecked) Logd(TAG, "$option is checked")
                                        when (selectedOption) {
                                            "Default" -> {
                                                saveFeed { it: Feed -> it.queueId = 0L }
                                                onDismissRequest()
                                            }
                                            "Active" -> {
                                                saveFeed { it: Feed -> it.queueId = -1L }
                                                onDismissRequest()
                                            }
                                            "None" -> {
                                                saveFeed { it: Feed -> it.queueId = -2L }
                                                onDismissRequest()
                                            }
                                            "Custom" -> {}
                                        }
                                    }
                                )
                                Text(option)
                            }
                        }
                        if (selectedOption == "Custom") {
                            val queues = realm.query(PlayQueue::class).find()
                            SpinnerExternalSet(items = queues.map { it.name }, selectedIndex = 0) { index ->
                                Logd(TAG, "Queue selected: ${queues[index]}")
                                saveFeed { it: Feed -> it.queueId = queues[index].id }
                                onDismissRequest()
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun SetKeepUpdateDialog(onDismissRequest: () -> Unit) {
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_refresh), "")
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.keep_updated), style = CustomTextStyles.titleCustom)
                            Spacer(modifier = Modifier.weight(1f))
                            var checked by remember { mutableStateOf(false) }
                            Switch(checked = checked, onCheckedChange = {
                                checked = it
                                saveFeed { pref: Feed -> pref.keepUpdated = checked }
                            })
                        }
                        Text(text = stringResource(R.string.keep_updated_summary), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        @Composable
        fun ChooseRatingDialog(selected: List<Feed>, onDismissRequest: () -> Unit) {
            Dialog(onDismissRequest = onDismissRequest) {
                Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (rating in Rating.entries.reversed()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp).clickable {
                                for (item in selected) upsertBlk(item) { it.rating = rating.code }
                                onDismissRequest()
                            }) {
                                Icon(imageVector = ImageVector.vectorResource(id = rating.res), "")
                                Text(rating.name, Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(selected) { showChooseRatingDialog = false }
        var showAutoDeleteHandlerDialog by remember { mutableStateOf(false) }
        if (showAutoDeleteHandlerDialog) AutoDeleteHandlerDialog {showAutoDeleteHandlerDialog = false}
        var showAssociateDialog by remember { mutableStateOf(false) }
        if (showAssociateDialog) SetAssociateQueueDialog {showAssociateDialog = false}
        var showKeepUpdateDialog by remember { mutableStateOf(false) }
        if (showKeepUpdateDialog) SetKeepUpdateDialog {showKeepUpdateDialog = false}
        var showTagsSettingDialog by remember { mutableStateOf(false) }
        if (showTagsSettingDialog) TagSettingDialog(selected) { showTagsSettingDialog = false }
        var showSpeedDialog by remember { mutableStateOf(false) }
        if (showSpeedDialog) PlaybackSpeedDialog(selected, initSpeed = 1f, maxSpeed = 3f, onDismiss = {showSpeedDialog = false}) { newSpeed ->
            saveFeed { it: Feed -> it.playSpeed = newSpeed }
        }
        var showAutoDownloadSwitchDialog by remember { mutableStateOf(false) }
        if (showAutoDownloadSwitchDialog) SimpleSwitchDialog(stringResource(R.string.auto_download_settings_label), stringResource(R.string.auto_download_label), onDismissRequest = { showAutoDownloadSwitchDialog = false }) { enabled ->
            saveFeed { it: Feed -> it.autoDownload = enabled }
        }

        @Composable
        fun EpisodeSpeedDial(selected: SnapshotStateList<Feed>, modifier: Modifier = Modifier) {
            val TAG = "EpisodeSpeedDial ${selected.size}"
            var isExpanded by remember { mutableStateOf(false) }
            val options = listOf<@Composable () -> Unit>(
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    showRemoveFeedDialog = true
                    isExpanded = false
                    vm.selectMode = false
                    Logd(TAG, "ic_delete: ${selected.size}")
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "")
                    Text(stringResource(id = R.string.remove_feed_label)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    showKeepUpdateDialog = true
                    isExpanded = false
                    vm.selectMode = false
                    Logd(TAG, "ic_refresh: ${selected.size}")
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh), "")
                    Text(stringResource(id = R.string.keep_updated)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    isExpanded = false
                    vm.selectMode = false
                    Logd(TAG, "ic_download: ${selected.size}")
                    showAutoDownloadSwitchDialog = true
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_download), "")
                    Text(stringResource(id = R.string.auto_download_label)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    showAutoDeleteHandlerDialog = true
                    isExpanded = false
                    vm.selectMode = false
                    Logd(TAG, "ic_delete_auto: ${selected.size}")
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete_auto), "")
                    Text(stringResource(id = R.string.auto_delete_label)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    isExpanded = false
                    vm.selectMode = false
                    Logd(TAG, "ic_playback_speed: ${selected.size}")
                    showSpeedDialog = true
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playback_speed), "")
                    Text(stringResource(id = R.string.playback_speed)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    isExpanded = false
                    vm.selectMode = false
                    Logd(TAG, "ic_tag: ${selected.size}")
                    showTagsSettingDialog = true
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_tag), "")
                    Text(stringResource(id = R.string.edit_tags)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    showAssociateDialog = true
                    isExpanded = false
                    vm.selectMode = false
                    Logd(TAG, "ic_playlist_play: ${selected.size}")
//                        associatedQueuePrefHandler()
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "")
                    Text(stringResource(id = R.string.pref_feed_associated_queue)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    vm.selectMode = false
                    Logd(TAG, "ic_star: ${selected.size}")
                    showChooseRatingDialog = true
                    isExpanded = false
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), "Set rating")
                    Text(stringResource(id = R.string.set_rating_label)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    isExpanded = false
                    vm.selectMode = false
                    Logd(TAG, "baseline_import_export_24: ${selected.size}")
                    val exportType = ExportTypes.OPML_SELECTED
                    val title = String.format(exportType.outputNameTemplate, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                    val intentPickAction = Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(exportType.contentType)
                        .putExtra(Intent.EXTRA_TITLE, title)
                    try {
                        val actMain: MainActivity? = generateSequence(vm.context) { if (it is ContextWrapper) it.baseContext else null }.filterIsInstance<MainActivity>().firstOrNull()
                        actMain?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                            if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
                            val uri = result.data!!.data
                            vm.exportOPML(uri, selected)
                        }?.launch(intentPickAction)
                        return@clickable
                    } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") }
                    // if on SDK lower than API 21 or the implicit intent failed, fallback to the legacy export process
                    vm.exportOPML(null, selected)
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_import_export_24), "")
                    Text(stringResource(id = R.string.opml_export_label)) } },
            )
            val scrollState = rememberScrollState()
            Column(modifier = modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.Bottom) {
                if (isExpanded) options.forEachIndexed { _, button ->
                    FloatingActionButton(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).height(40.dp), onClick = {}) { button() }
                }
                FloatingActionButton(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { isExpanded = !isExpanded }) { Icon(Icons.Filled.Edit, "Edit") }
            }
        }

        PullToRefreshBox(modifier = Modifier.fillMaxWidth(), isRefreshing = refreshing, indicator = {}, onRefresh = {
//            coroutineScope.launch {
            refreshing = true
            if (getPref(AppPrefs.prefSwipeToRefreshAll, true)) FeedUpdateManager.runOnceOrAsk(vm.context)
            refreshing = false
//            }
        }) {
            val context = LocalContext.current
            if (if (vm.useGrid == null) vm.useGridLayout else vm.useGrid!!) {
                val lazyGridState = rememberLazyGridState()
                LazyVerticalGrid(state = lazyGridState, columns = GridCells.Adaptive(80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)) {
                    items(vm.feedListFiltered.size, key = {index -> vm.feedListFiltered[index].id}) { index ->
                        val feed by remember { mutableStateOf(vm.feedListFiltered[index]) }
                        var isSelected by remember { mutableStateOf(false) }
                        LaunchedEffect(key1 = vm.selectMode, key2 = selectedSize) {
                            isSelected = vm.selectMode && feed in selected
                        }
                        fun toggleSelected() {
                            isSelected = !isSelected
                            if (isSelected) selected.add(feed)
                            else selected.remove(feed)
                        }
                        Column(Modifier.background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .combinedClickable(onClick = {
                                Logd(TAG, "clicked: ${feed.title}")
                                if (!feed.isBuilding) {
                                    if (vm.selectMode) toggleSelected()
                                    else {
                                        feedOnDisplay = feed
                                        mainNavController.navigate(Screens.FeedEpisodes.name)
                                    }
                                }
                            }, onLongClick = {
                                if (!feed.isBuilding) {
                                    vm.selectMode = !vm.selectMode
                                    isSelected = vm.selectMode
                                    selected.clear()
                                    if (vm.selectMode) {
                                        selected.add(feed)
                                        longPressIndex = index
                                    } else {
                                        selectedSize = 0
                                        longPressIndex = -1
                                    }
                                }
                                Logd(TAG, "long clicked: ${feed.title}")
                            })) {
                            val textColor = MaterialTheme.colorScheme.onSurface
                            ConstraintLayout(Modifier.fillMaxSize()) {
                                val (coverImage, episodeCount, rating, error) = createRefs()
                                val imgLoc = remember(feed) { feed.imageUrl }
                                AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                                    .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                                    contentDescription = "coverImage",
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).constrainAs(coverImage) {
                                        top.linkTo(parent.top)
                                        bottom.linkTo(parent.bottom)
                                        start.linkTo(parent.start)
                                    })
                                Text(NumberFormat.getInstance().format(feed.episodes.size.toLong()), color = Color.Green,
                                    modifier = Modifier.background(Color.Gray).constrainAs(episodeCount) {
                                        end.linkTo(parent.end)
                                        top.linkTo(coverImage.top)
                                    })
                                if (feed.rating != Rating.UNRATED.code)
                                    Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                                        modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).constrainAs(rating) {
                                            start.linkTo(coverImage.start)
                                            bottom.linkTo(coverImage.bottom)
                                        })
//                                TODO: need to use state
                                if (feed.lastUpdateFailed) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_error), tint = Color.Red, contentDescription = "error",
                                    modifier = Modifier.background(Color.Gray).constrainAs(error) {
                                        end.linkTo(parent.end)
                                        bottom.linkTo(coverImage.bottom)
                                    })
                            }
                            Text(feed.title ?: "No title", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                val lazyListState = rememberLazyListState()
                LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(vm.feedListFiltered, key = { _, feed -> feed.id}) { index, feed ->
                        var isSelected by remember { mutableStateOf(false) }
                        LaunchedEffect(key1 = vm.selectMode, key2 = selectedSize) {
                            isSelected = vm.selectMode && feed in selected
                        }
                        fun toggleSelected() {
                            isSelected = !isSelected
                            if (isSelected) selected.add(feed)
                            else selected.remove(feed)
                            Logd(TAG, "toggleSelected: selected: ${selected.size}")
                        }
                        Row(Modifier.background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                            val imgLoc = remember(feed) { feed.imageUrl }
                            AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                                .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                                contentDescription = "imgvCover",
                                placeholder = painterResource(R.mipmap.ic_launcher),
                                error = painterResource(R.mipmap.ic_launcher),
                                modifier = Modifier.width(80.dp).height(80.dp).clickable(onClick = {
                                    Logd(TAG, "icon clicked!")
                                    if (!feed.isBuilding) {
                                        if (vm.selectMode) toggleSelected()
                                        else {
                                            feedOnDisplay = feed
                                            mainNavController.navigate(Screens.FeedInfo.name)
                                        }
                                    }
                                })
                            )
                            val textColor = MaterialTheme.colorScheme.onSurface
                            Column(Modifier.weight(1f).padding(start = 10.dp).combinedClickable(onClick = {
                                Logd(TAG, "clicked: ${feed.title}")
                                if (!feed.isBuilding) {
                                    if (vm.selectMode) toggleSelected()
                                    else {
                                        feedOnDisplay = feed
                                        mainNavController.navigate(Screens.FeedEpisodes.name)
                                    }
                                }
                            }, onLongClick = {
                                if (!feed.isBuilding) {
                                    vm.selectMode = !vm.selectMode
                                    isSelected = vm.selectMode
                                    selected.clear()
                                    if (vm.selectMode) {
                                        selected.add(feed)
                                        longPressIndex = index
                                    } else {
                                        selectedSize = 0
                                        longPressIndex = -1
                                    }
                                }
                                Logd(TAG, "long clicked: ${feed.title}")
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
                                    val measureString = remember { NumberFormat.getInstance().format(feed.episodes.size.toLong()) + " : " +
                                            DurationConverter.durationInHours(feed.totleDuration/1000) }
                                    Text(measureString, color = textColor, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.weight(1f))
                                    var feedSortInfo by remember { mutableStateOf(feed.sortInfo) }
                                    LaunchedEffect(vm.feedSorted) { feedSortInfo = feed.sortInfo }
                                    Text(feedSortInfo, color = textColor, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            //                                TODO: need to use state
                            if (feed.lastUpdateFailed) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_error), tint = Color.Red, contentDescription = "error")
                        }
                    }
                }
            }
            if (vm.selectMode) {
                val buttonColor = MaterialTheme.colorScheme.tertiary
                Row(modifier = Modifier.align(Alignment.TopEnd).width(150.dp).height(45.dp).background(MaterialTheme.colorScheme.tertiaryContainer),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_upward_24), tint = buttonColor, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp).clickable(onClick = {
                            selected.clear()
                            for (i in 0..longPressIndex) selected.add(vm.feedListFiltered[i])
                            selectedSize = selected.size
                            Logd(TAG, "selectedIds: ${selected.size}")
                        }))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_downward_24), tint = buttonColor, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp).clickable(onClick = {
                            selected.clear()
                            for (i in longPressIndex..<vm.feedListFiltered.size) selected.add(vm.feedListFiltered[i])
                            selectedSize = selected.size
                            Logd(TAG, "selectedIds: ${selected.size}")
                        }))
                    var selectAllRes by remember { mutableIntStateOf(R.drawable.ic_select_all) }
                    Icon(imageVector = ImageVector.vectorResource(selectAllRes), tint = buttonColor, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).clickable(onClick = {
                            if (selectedSize != vm.feedListFiltered.size) {
                                selected.clear()
                                selected.addAll(vm.feedListFiltered)
                                selectAllRes = R.drawable.ic_select_none
                            } else {
                                selected.clear()
                                longPressIndex = -1
                                selectAllRes = R.drawable.ic_select_all
                            }
                            selectedSize = selected.size
                            Logd(TAG, "selectedIds: ${selected.size}")
                        }))
                }
                EpisodeSpeedDial(selected.toMutableStateList(), modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp, start = 16.dp))
            }
        }
    }

    @Composable
    fun SortDialog(onDismissRequest: () -> Unit) {
        var sortingJob = remember<Job?> { null }
        fun fetchAndSortRoutine() {
            sortingJob?.cancel()
            sortingJob = runOnIOScope { vm.fetchAndSort() }.apply { invokeOnCompletion { sortingJob = null } }
        }
        Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
            val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
            dialogWindowProvider?.window?.let { window ->
                window.setGravity(Gravity.BOTTOM)
//                window.setDimAmount(0f)
//                window.setBackgroundDrawableResource(android.R.color.transparent)
//                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                val textColor = MaterialTheme.colorScheme.onSurface
                val scrollState = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    Row {
                        OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.sortIndex != 0) textColor else Color.Green),
                            onClick = {
                                vm.titleAscending = !vm.titleAscending
                                vm.sortIndex = 0
                                fetchAndSortRoutine()
                            }
                        ) { Text(text = stringResource(R.string.title) + if (vm.titleAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.sortIndex != 1) textColor else Color.Green),
                            onClick = {
                                vm.dateAscending = !vm.dateAscending
                                vm.sortIndex = 1
                                fetchAndSortRoutine()
                            }
                        ) { Text(text = stringResource(R.string.date) + if (vm.dateAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.sortIndex != 2) textColor else Color.Green),
                            onClick = {
                                vm.countAscending = !vm.countAscending
                                vm.sortIndex = 2
                                fetchAndSortRoutine()
                            }
                        ) { Text(text = stringResource(R.string.count) + if (vm.countAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
                    if (vm.sortIndex == 1) {
                        Row {
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.dateSortIndex != 0) textColor else Color.Green),
                                onClick = {
                                    vm.dateSortIndex = 0
                                    fetchAndSortRoutine()
                                }
                            ) { Text(stringResource(R.string.publish_date)) }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.dateSortIndex != 1) textColor else Color.Green),
                                onClick = {
                                    vm.dateSortIndex = 1
                                    fetchAndSortRoutine()
                                }
                            ) { Text(stringResource(R.string.download_date)) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
                    Column(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                        if (vm.sortIndex == 2) {
                            Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                val item = EpisodeFilter.EpisodesFilterGroup.DOWNLOADED
                                var selectNone by remember { mutableStateOf(false) }
                                if (selectNone) vm.downlaodedSortIndex = -1
                                Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(end = 10.dp))
                                Spacer(Modifier.weight(0.3f))
                                OutlinedButton(
                                    modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.downlaodedSortIndex != 0) textColor else Color.Green),
                                    onClick = {
                                        if (vm.downlaodedSortIndex != 0) {
                                            selectNone = false
                                            vm.downlaodedSortIndex = 0
                                        } else vm.downlaodedSortIndex = -1
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.values[0].displayName), color = textColor) }
                                Spacer(Modifier.weight(0.1f))
                                OutlinedButton(
                                    modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.downlaodedSortIndex != 1) textColor else Color.Green),
                                    onClick = {
                                        if (vm.downlaodedSortIndex != 1) {
                                            selectNone = false
                                            vm.downlaodedSortIndex = 1
                                        } else vm.downlaodedSortIndex = -1
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.values[1].displayName), color = textColor) }
                                Spacer(Modifier.weight(0.5f))
                            }
                            Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                val item = EpisodeFilter.EpisodesFilterGroup.OPINION
                                var selectNone by remember { mutableStateOf(false) }
                                if (selectNone) vm.commentedSortIndex = -1
                                Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(end = 10.dp))
                                Spacer(Modifier.weight(0.3f))
                                OutlinedButton(
                                    modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.commentedSortIndex != 0) textColor else Color.Green),
                                    onClick = {
                                        if (vm.commentedSortIndex != 0) {
                                            selectNone = false
                                            vm.commentedSortIndex = 0
                                        } else vm.commentedSortIndex = -1
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.values[0].displayName), color = textColor) }
                                Spacer(Modifier.weight(0.1f))
                                OutlinedButton(
                                    modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.commentedSortIndex != 1) textColor else Color.Green),
                                    onClick = {
                                        if (vm.commentedSortIndex != 1) {
                                            selectNone = false
                                            vm.commentedSortIndex = 1
                                        } else vm.commentedSortIndex = -1
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.values[1].displayName), color = textColor) }
                                Spacer(Modifier.weight(0.5f))
                            }
                        }
                        if ((vm.sortIndex == 1 && vm.dateSortIndex == 0) || vm.sortIndex == 2) {
                            val item = EpisodeFilter.EpisodesFilterGroup.PLAY_STATE
                            var selectNone by remember { mutableStateOf(false) }
                            var expandRow by remember { mutableStateOf(false) }
                            Row {
                                Text(stringResource(item.nameRes) + ".. :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor,
                                    modifier = Modifier.clickable { expandRow = !expandRow })
                                var lowerSelected by remember { mutableStateOf(false) }
                                var higherSelected by remember { mutableStateOf(false) }
                                Spacer(Modifier.weight(1f))
                                if (expandRow) Text("<<<", color = if (lowerSelected) Color.Green else textColor, style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.clickable {
                                        val hIndex = vm.playStateSort.indexOfLast { it.value }
                                        if (hIndex < 0) return@clickable
                                        if (!lowerSelected) {
                                            for (i in 0..hIndex) vm.playStateSort[i].value = true
                                        } else {
                                            for (i in 0..hIndex) vm.playStateSort[i].value = false
                                            vm.playStateSort[hIndex].value = true
                                        }
                                        lowerSelected = !lowerSelected
                                        fetchAndSortRoutine()
                                    })
                                Spacer(Modifier.weight(1f))
                                if (expandRow) Text("X", color = textColor, style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.clickable {
                                        lowerSelected = false
                                        higherSelected = false
                                        for (i in item.values.indices) vm.playStateSort[i].value = false
                                        fetchAndSortRoutine()
                                    })
                                Spacer(Modifier.weight(1f))
                                if (expandRow) Text(">>>", color = if (higherSelected) Color.Green else textColor, style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.clickable {
                                        val lIndex = vm.playStateSort.indexOfFirst { it.value }
                                        if (lIndex < 0) return@clickable
                                        if (!higherSelected) {
                                            for (i in lIndex..<item.values.size) vm.playStateSort[i].value = true
                                        } else {
                                            for (i in lIndex..<item.values.size) vm.playStateSort[i].value = false
                                            vm.playStateSort[lIndex].value = true
                                        }
                                        higherSelected = !higherSelected
                                        fetchAndSortRoutine()
                                    })
                                Spacer(Modifier.weight(1f))
                            }
                            if (expandRow) NonlazyGrid(columns = 3, itemCount = item.values.size) { index ->
                                if (selectNone) vm.playStateSort[index].value = false
                                LaunchedEffect(Unit) {
//                                if (filter != null && item.values[index].filterId in filter.properties) selectedList[index].value = true
                                }
                                OutlinedButton(
                                    modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(),
                                    border = BorderStroke(2.dp, if (vm.playStateSort[index].value) Color.Green else textColor),
                                    onClick = {
                                        selectNone = false
                                        vm.playStateSort[index].value = !vm.playStateSort[index].value
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.values[index].displayName), maxLines = 1, color = textColor) }
                            }
                        }
                        if (vm.sortIndex == 2) {
                            val item = EpisodeFilter.EpisodesFilterGroup.RATING
                            var selectNone by remember { mutableStateOf(false) }
                            var expandRow by remember { mutableStateOf(false) }
                            Row {
                                Text(stringResource(item.nameRes) + ".. :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor,
                                    modifier = Modifier.clickable { expandRow = !expandRow })
                                var lowerSelected by remember { mutableStateOf(false) }
                                var higherSelected by remember { mutableStateOf(false) }
                                Spacer(Modifier.weight(1f))
                                if (expandRow) Text("<<<", color = if (lowerSelected) Color.Green else textColor, style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.clickable {
                                        val hIndex = vm.ratingSort.indexOfLast { it.value }
                                        if (hIndex < 0) return@clickable
                                        if (!lowerSelected) {
                                            for (i in 0..hIndex) vm.ratingSort[i].value = true
                                        } else {
                                            for (i in 0..hIndex) vm.ratingSort[i].value = false
                                            vm.ratingSort[hIndex].value = true
                                        }
                                        lowerSelected = !lowerSelected
                                        fetchAndSortRoutine()
                                    })
                                Spacer(Modifier.weight(1f))
                                if (expandRow) Text("X", color = textColor, style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.clickable {
                                        lowerSelected = false
                                        higherSelected = false
                                        for (i in item.values.indices) vm.ratingSort[i].value = false
                                        fetchAndSortRoutine()
                                    })
                                Spacer(Modifier.weight(1f))
                                if (expandRow) Text(">>>", color = if (higherSelected) Color.Green else textColor, style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.clickable {
                                        val lIndex = vm.ratingSort.indexOfFirst { it.value }
                                        if (lIndex < 0) return@clickable
                                        if (!higherSelected) {
                                            for (i in lIndex..<item.values.size) vm.ratingSort[i].value = true
                                        } else {
                                            for (i in lIndex..<item.values.size) vm.ratingSort[i].value = false
                                            vm.ratingSort[lIndex].value = true
                                        }
                                        higherSelected = !higherSelected
                                        fetchAndSortRoutine()
                                    })
                                Spacer(Modifier.weight(1f))
                            }
                            if (expandRow) NonlazyGrid(columns = 3, itemCount = item.values.size) { index ->
                                if (selectNone) vm.ratingSort[index].value = false
                                OutlinedButton(
                                    modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(),
                                    border = BorderStroke(2.dp, if (vm.ratingSort[index].value) Color.Green else textColor),
                                    onClick = {
                                        selectNone = false
                                        vm.ratingSort[index].value = !vm.ratingSort[index].value
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.values[index].displayName), maxLines = 1, color = textColor) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun FilterDialog(filter: FeedFilter? = null, onDismissRequest: () -> Unit) {
        val filterValues = remember { filter?.properties ?: mutableSetOf() }

        fun onFilterChanged(newFilterValues: Set<String>) {
            vm.feedsFilter = StringUtils.join(newFilterValues, ",")
            Logd(TAG, "onFilterChanged: ${vm.feedsFilter}")
            vm.loadSubscriptions()
        }
        Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
            val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
            dialogWindowProvider?.window?.let { window ->
                window.setGravity(Gravity.BOTTOM)
//                window.setDimAmount(0f)
            }
            Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                val textColor = MaterialTheme.colorScheme.onSurface
                val scrollState = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    var selectNone by remember { mutableStateOf(false) }
                    for (item in FeedFilter.FeedFilterGroup.entries) {
                        if (item.values.size == 2) {
                            Row(modifier = Modifier.padding(start = 5.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Absolute.Left, verticalAlignment = Alignment.CenterVertically) {
                                var selectedIndex by remember { mutableStateOf(-1) }
                                if (selectNone) selectedIndex = -1
                                LaunchedEffect(Unit) {
                                    if (filter != null) {
                                        if (item.values[0].filterId in filter.properties) selectedIndex = 0
                                        else if (item.values[1].filterId in filter.properties) selectedIndex = 1
                                    }
                                }
                                Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge , color = textColor, modifier = Modifier.padding(end = 10.dp))
                                Spacer(Modifier.weight(0.3f))
                                OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp),
                                    border = BorderStroke(2.dp, if (selectedIndex != 0) textColor else Color.Green),
                                    onClick = {
                                        if (selectedIndex != 0) {
                                            selectNone = false
                                            selectedIndex = 0
                                            filterValues.add(item.values[0].filterId)
                                            filterValues.remove(item.values[1].filterId)
                                        } else {
                                            selectedIndex = -1
                                            filterValues.remove(item.values[0].filterId)
                                        }
                                        onFilterChanged(filterValues)
                                    },
                                ) { Text(text = stringResource(item.values[0].displayName), color = textColor) }
                                Spacer(Modifier.weight(0.1f))
                                OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp),
                                    border = BorderStroke(2.dp, if (selectedIndex != 1) textColor else Color.Green),
                                    onClick = {
                                        if (selectedIndex != 1) {
                                            selectNone = false
                                            selectedIndex = 1
                                            filterValues.add(item.values[1].filterId)
                                            filterValues.remove(item.values[0].filterId)
                                        } else {
                                            selectedIndex = -1
                                            filterValues.remove(item.values[1].filterId)
                                        }
                                        onFilterChanged(filterValues)
                                    },
                                ) { Text(text = stringResource(item.values[1].displayName), color = textColor) }
                                Spacer(Modifier.weight(0.5f))
                            }
                        } else {
                            Column(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                                val selectedList = remember { MutableList(item.values.size) { mutableStateOf(false)} }
                                var expandRow by remember { mutableStateOf(false) }
                                Row {
                                    Text(stringResource(item.nameRes) + ".. :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge , color = textColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                    var lowerSelected by remember { mutableStateOf(false) }
                                    var higherSelected by remember { mutableStateOf(false) }
                                    Spacer(Modifier.weight(1f))
                                    if (expandRow) Text("<<<", color = if (lowerSelected) Color.Green else textColor, style = MaterialTheme.typography.titleLarge , modifier = Modifier.clickable {
                                        val hIndex = selectedList.indexOfLast { it.value }
                                        if (hIndex < 0) return@clickable
                                        if (!lowerSelected) {
                                            for (i in 0..hIndex) selectedList[i].value = true
                                        } else {
                                            for (i in 0..hIndex) selectedList[i].value = false
                                            selectedList[hIndex].value = true
                                        }
                                        lowerSelected = !lowerSelected
                                        for (i in item.values.indices) {
                                            if (selectedList[i].value) filterValues.add(item.values[i].filterId)
                                            else filterValues.remove(item.values[i].filterId)
                                        }
                                        onFilterChanged(filterValues)
                                    })
                                    Spacer(Modifier.weight(1f))
                                    if (expandRow) Text("X", color = textColor, style = MaterialTheme.typography.titleLarge , modifier = Modifier.clickable {
                                        lowerSelected = false
                                        higherSelected = false
                                        for (i in item.values.indices) {
                                            selectedList[i].value = false
                                            filterValues.remove(item.values[i].filterId)
                                        }
                                        onFilterChanged(filterValues)
                                    })
                                    Spacer(Modifier.weight(1f))
                                    if (expandRow) Text(">>>", color = if (higherSelected) Color.Green else textColor, style = MaterialTheme.typography.titleLarge , modifier = Modifier.clickable {
                                        val lIndex = selectedList.indexOfFirst { it.value }
                                        if (lIndex < 0) return@clickable
                                        if (!higherSelected) {
                                            for (i in lIndex..<item.values.size) selectedList[i].value = true
                                        } else {
                                            for (i in lIndex..<item.values.size) selectedList[i].value = false
                                            selectedList[lIndex].value = true
                                        }
                                        higherSelected = !higherSelected
                                        for (i in item.values.indices) {
                                            if (selectedList[i].value) filterValues.add(item.values[i].filterId)
                                            else filterValues.remove(item.values[i].filterId)
                                        }
                                        onFilterChanged(filterValues)
                                    })
                                    Spacer(Modifier.weight(1f))
                                }
                                if (expandRow) NonlazyGrid(columns = 3, itemCount = item.values.size) { index ->
                                    if (selectNone) selectedList[index].value = false
                                    LaunchedEffect(Unit) {
                                        if (filter != null && item.values[index].filterId in filter.properties) selectedList[index].value = true
                                    }
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(),
                                        border = BorderStroke(2.dp, if (selectedList[index].value) Color.Green else textColor),
                                        onClick = {
                                            selectNone = false
                                            selectedList[index].value = !selectedList[index].value
                                            if (selectedList[index].value) filterValues.add(item.values[index].filterId)
                                            else filterValues.remove(item.values[index].filterId)
                                            onFilterChanged(filterValues)
                                        },
                                    ) { Text(text = stringResource(item.values[index].displayName), maxLines = 1, color = textColor) }
                                }
                            }
                        }
                    }
                    Row {
                        Spacer(Modifier.weight(0.3f))
                        Button(onClick = {
                            selectNone = true
                            onFilterChanged(setOf(""))
                        }) { Text(stringResource(R.string.reset)) }
                        Spacer(Modifier.weight(0.4f))
                        Button(onClick = { onDismissRequest() }) { Text(stringResource(R.string.close_label)) }
                        Spacer(Modifier.weight(0.3f))
                    }
                }
            }
        }
    }

    if (vm.showFilterDialog) FilterDialog(FeedFilter(vm.feedsFilter)) { vm.showFilterDialog = false }
    if (vm.showSortDialog) SortDialog { vm.showSortDialog = false }
    if (vm.showNewSynthetic) RenameOrCreateSyntheticFeed { vm.showNewSynthetic = false }
    Scaffold(topBar = { MyTopAppBar(displayUpArrow) }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            InforBar()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 20.dp)) {
                Spinner(items = vm.spinnerTexts, selectedIndex = vm.queueFilterIndex) { index: Int ->
                    vm.queueFilterIndex = index
                    vm.loadSubscriptions()
                }
                Spacer(Modifier.weight(1f))
                Spinner(items = vm.tags, selectedIndex = vm.tagFilterIndex) { index: Int ->
                    vm.tagFilterIndex = index
                    vm.loadSubscriptions()
                }
            }
            if (vm.noSubscription) Text(stringResource(R.string.no_subscriptions_label))
            else LazyList()
        }
    }
}

private const val TAG = "SubscriptionsScreen"

private const val KEY_UP_ARROW = "up_arrow"
private const val ARGUMENT_FOLDER = "folder"

private var prevFeedUpdatingEvent: FlowEvent.FeedUpdatingEvent? = null

//fun newInstance(folderTitle: String?): SubscriptionsFragment {
//    val fragment = SubscriptionsFragment()
//    val args = Bundle()
//    args.putString(ARGUMENT_FOLDER, folderTitle)
//    fragment.arguments = args
//    return fragment
//}

