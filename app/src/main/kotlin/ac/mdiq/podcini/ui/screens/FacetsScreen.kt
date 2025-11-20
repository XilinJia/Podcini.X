package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.MediaFilesTransporter
import ac.mdiq.podcini.storage.database.buildListInfo
import ac.mdiq.podcini.storage.database.feedIdsOfAllEpisodes
import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.getEpisodesAsFlow
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.getHistoryAsFlow
import ac.mdiq.podcini.storage.database.getInQueueEpisodeIds
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.sortPairOf
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.customMediaUriString
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.DatesFilterDialog
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.EpisodesFilterDialog
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.SpinnerExternalSet
import ac.mdiq.podcini.ui.utils.episodeOnDisplay
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logt
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.xilinjia.krdb.UpdatePolicy
import io.github.xilinjia.krdb.notifications.ResultsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.net.URLDecoder
import java.text.NumberFormat
import java.util.Date

class FacetsVM(val context: Context, val lcScope: CoroutineScope) {
    var tag = TAG+QuickAccess.entries[0]

    internal var episodesSortOrder: EpisodeSortOrder
        get() = EpisodeSortOrder.fromCodeString(getPref(AppPrefs.prefEpisodesSort, "" + EpisodeSortOrder.DATE_NEW_OLD.code))
        set(s) {
            putPref(AppPrefs.prefEpisodesSort, "" + s.code)
        }
    internal var prefFilterEpisodes: String
        get() = getPref(AppPrefs.prefEpisodesFilter, "")
        set(filter) {
            putPref(AppPrefs.prefEpisodesFilter, filter)
        }
    internal var prefFilterDownloads: String
        get() = getPref(AppPrefs.prefDownloadsFilter, EpisodeFilter.States.downloaded.name)
        set(filter) {
            putPref(AppPrefs.prefDownloadsFilter, filter)
        }
}

@Composable
fun FacetsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember(episodeOnDisplay.id) {  FacetsVM(context, scope) }
    val navController = LocalNavController.current

    var episodesFlow by remember { mutableStateOf<Flow<ResultsChange<Episode>>>(emptyFlow()) }

    val feedsAssociated = remember { mutableStateListOf<Feed>() }

    val infoBarText = remember {  mutableStateOf("") }

    var showFeeds by remember { mutableStateOf(false) }


    var filter by remember { mutableStateOf(EpisodeFilter()) }
    var sortOrder by remember { mutableStateOf(vm.episodesSortOrder) }
    val filterButtonColor = remember { mutableStateOf(Color.White) }

    val spinnerTexts by remember { mutableStateOf(QuickAccess.entries.map { it.name })  }
    var curIndex by remember { mutableIntStateOf(0) }

    val swipeActions = remember { SwipeActions(context, TAG) }

    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDatesFilter by remember { mutableStateOf(false) }
    var actionButtonToPass by remember { mutableStateOf<((Episode) -> EpisodeActionButton)?>(null) }
    val showClearHistoryDialog = remember { mutableStateOf(false) }

    var startDate = remember { 0L }
    var endDate = remember { Date().time }

    val episodesChange by episodesFlow.collectAsState(initial = null)
    val episodes = episodesChange?.list ?: emptyList()

    fun buildFlow() {
        Logd(TAG, "loadItems() called")
        episodesFlow = when (spinnerTexts[curIndex]) {
            QuickAccess.New.name -> getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.new.name), vm.episodesSortOrder)
            QuickAccess.Planned.name -> getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.soon.name, EpisodeFilter.States.later.name), vm.episodesSortOrder)
            QuickAccess.Repeats.name -> getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.again.name, EpisodeFilter.States.forever.name), vm.episodesSortOrder)
            QuickAccess.Liked.name -> getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.good.name, EpisodeFilter.States.superb.name), vm.episodesSortOrder)
            QuickAccess.Commented.name -> getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.has_comments.name), vm.episodesSortOrder)
            QuickAccess.Recorded.name -> getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.has_clips.name, EpisodeFilter.States.has_marks.name, andOr = "OR"), vm.episodesSortOrder)
            QuickAccess.Queued.name -> {
                val qstr = EpisodeFilter(EpisodeFilter.States.inQueue.name).queryString()
                val ids = getInQueueEpisodeIds()
                val sortPair = sortPairOf(vm.episodesSortOrder)
                realm.query(Episode::class).query("$qstr OR id IN $0", ids).sort(sortPair).asFlow()
            }
            QuickAccess.History.name -> getHistoryAsFlow(start = startDate, end = endDate)
            QuickAccess.Downloaded.name -> getEpisodesAsFlow(EpisodeFilter(vm.prefFilterDownloads), vm.episodesSortOrder)
            else -> getEpisodesAsFlow(filter, vm.episodesSortOrder)
        }
    }

    fun updateToolbar() {
        scope.launch(Dispatchers.IO) {
            var info = buildListInfo(episodes)
            withContext(Dispatchers.Main) {
                val isFiltered = filter.propertySet.isNotEmpty()
                filterButtonColor.value = if (isFiltered) Color.Green else Color.White
                if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name && episodes.isNotEmpty()) {
                    var sizeMB: Long = 0
                    for (item in episodes) sizeMB += item.size
                    info += " • " + (sizeMB / 1000000) + " MB"
                }
                infoBarText.value = info
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    lifecycleOwner.lifecycle.addObserver(swipeActions)
                    filter = EpisodeFilter(vm.prefFilterEpisodes)
                    curIndex = getPref(AppPrefs.prefFacetsCurIndex, 0)
                    vm.tag = TAG+QuickAccess.entries[curIndex]
//                    sortOrder = vm.episodesSortOrder
                    updateToolbar()
                    buildFlow()
                }
                Lifecycle.Event.ON_START -> {
//                    vm.procFlowEvents()
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
//            episodes.clear()
            feedsAssociated.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun loadAssociatedFeeds() {
        feedsAssociated.clear()
        if (spinnerTexts[curIndex] == QuickAccess.All.name) feedsAssociated.addAll(getFeedList())
        else feedsAssociated.addAll(episodes.mapNotNull { it.feed }.distinctBy { it.id })
    }

    LaunchedEffect(episodesChange?.list) {
        updateToolbar()
        if (showFeeds) loadAssociatedFeeds()
    }

    var progressing by remember { mutableStateOf(false) }

    @Composable
    fun OpenDialogs() {
        fun clearHistory() : Job {
            Logd(TAG, "clearHistory called")
            return runOnIOScope {
                progressing = true
                while (realm.query(Episode::class).query("playbackCompletionTime > 0 || lastPlayedTime > 0").count().find() > 0) {
                    realm.write {
                        val episodes = query(Episode::class).query("playbackCompletionTime > 0 || lastPlayedTime > 0").find()
                        for (e in episodes) {
                            e.playbackCompletionDate = null
                            e.lastPlayedTime = 0
                        }
                    }
                }
                Logt(TAG, "History cleared")
                withContext(Dispatchers.Main) { progressing = false }
                EventFlow.postEvent(FlowEvent.HistoryEvent())

            }
        }
        fun filtersDisabled(): MutableSet<EpisodeFilter.EpisodesFilterGroup> {
            return if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED)
            else mutableSetOf()
        }
        fun onFilterChanged(filter_: EpisodeFilter) {
            filter = filter_
            val filterValues = filter.propertySet
            if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name || spinnerTexts[curIndex] == QuickAccess.All.name) {
                val fSet = filterValues.toMutableSet()
                if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) fSet.add(EpisodeFilter.States.downloaded.name)
                vm.prefFilterEpisodes = StringUtils.join(fSet, ",")
                buildFlow()
            }
        }
        if (showFilterDialog) EpisodesFilterDialog(filter_ = filter, filtersDisabled = filtersDisabled(),
            onDismissRequest = { showFilterDialog = false }) { filter -> onFilterChanged(filter) }
        if (showSortDialog) EpisodeSortDialog(initOrder = sortOrder, onDismissRequest = { showSortDialog = false }) { order ->
            if (order != null) {
                sortOrder = order
                vm.episodesSortOrder = order
                buildFlow()
            }
        }
        swipeActions.ActionOptionsDialog()
        ComfirmDialog(titleRes = R.string.clear_history_label, message = stringResource(R.string.clear_playback_history_msg), showDialog = showClearHistoryDialog) { clearHistory() }
        if (showDatesFilter) DatesFilterDialog(oldestDate = 0L, onDismissRequest = { showDatesFilter = false} ) { timeFilterFrom, timeFilterTo ->
            startDate = timeFilterFrom
            endDate = timeFilterTo
            EventFlow.postEvent(FlowEvent.HistoryEvent(sortOrder, timeFilterFrom, timeFilterTo))
        }
    }

    fun clearNew() {
        progressing = true
        runOnIOScope {
            for (e in episodes) if (e.isNew) upsert(e) { it.setPlayed(false) }
            Logt(TAG, "New items cleared")
            withContext(Dispatchers.Main) { progressing = false }
            buildFlow()
        }
    }

    fun reconcile() {
        val nameEpisodeMap: MutableMap<String, Episode> = mutableMapOf()
        val filesRemoved: MutableList<String> = mutableListOf()
        fun traverse(srcFile: File) {
            val filename = srcFile.name
            if (srcFile.isDirectory) {
                Logd(TAG, "traverse folder title: $filename")
                val dirFiles = srcFile.listFiles()
                dirFiles?.forEach { file -> traverse(file) }
            } else {
                Logd(TAG, "traverse: $srcFile filename: $filename")
                val episode = nameEpisodeMap.remove(filename)
                if (episode == null) {
                    Logd(TAG, "traverse: error: episode not exist in map: $filename")
                    filesRemoved.add(filename)
                    srcFile.delete()
                    return
                }
                Logd(TAG, "traverse found episode: ${episode.title}")
            }
        }
        fun traverse(srcFile: DocumentFile) {
            val filename = srcFile.name
            if (srcFile.isDirectory) {
                Logd(TAG, "traverse folder title: $filename")
                val dirFiles = srcFile.listFiles()
                dirFiles.forEach { file -> traverse(file) }
            } else {
                Logd(TAG, "traverse: $srcFile filename: $filename")
                val episode = nameEpisodeMap.remove(filename)
                if (episode == null) {
                    Logd(TAG, "traverse: error: episode not exist in map: $filename")
                    if (filename != null) filesRemoved.add(filename)
                    srcFile.delete()
                    return
                }
                Logd(TAG, "traverse found episode: ${episode.title}")
            }
        }
        runOnIOScope {
            progressing = true
            nameEpisodeMap.clear()
            MediaFilesTransporter("").updateDB(context)
            var eList = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(vm.prefFilterDownloads), vm.episodesSortOrder, false)
            for (e in eList) {
                var fileUrl = e.fileUrl
                if (fileUrl.isNullOrBlank()) continue
                Logd(TAG, "reconcile: fileUrl: $fileUrl")
                fileUrl = fileUrl.substring(fileUrl.lastIndexOf('/') + 1)
                fileUrl = URLDecoder.decode(fileUrl, "UTF-8")
                Logd(TAG, "reconcile: add to map: fileUrl: $fileUrl")
                nameEpisodeMap[fileUrl] = e
            }
            eList = listOf()
            if (customMediaUriString.isBlank()) {
                val mediaDir = context.getExternalFilesDir("media") ?: return@runOnIOScope
                mediaDir.listFiles()?.forEach { file -> traverse(file) }
            } else {
                val customUri = customMediaUriString.toUri()
                val baseDir = DocumentFile.fromTreeUri(getAppContext(), customUri)
                baseDir?.listFiles()?.forEach { file -> traverse(file) }
            }
            Logd(TAG, "reconcile: end, episodes missing file: ${nameEpisodeMap.size}")
            if (nameEpisodeMap.isNotEmpty()) for (e in nameEpisodeMap.values) upsertBlk(e) { it.setfileUrlOrNull(null) }
            var count = nameEpisodeMap.size
            nameEpisodeMap.clear()
            realm.write {
                while (true) {
                    val el = query(Episode::class, "fileUrl != nil AND downloaded == false LIMIT(50)").find()
                    if (el.isEmpty()) break
                    Logd(TAG, "batch processing episodes not downloaded with fileUrl not null $count")
                    el.map { e ->
                        count++
                        e.setfileUrlOrNull(null)
                        copyToRealm(e, UpdatePolicy.ALL)
                    }
                }
            }
            Logt(TAG, "Episodes reconciled: $count\nFiles removed: ${filesRemoved.size}")
            realm.write {
                val el = query(Episode::class, "feedId == nil").find()
                if (el.isNotEmpty()) {
                    val size = el.size
                    for (e in el) Logd(TAG, "deleting ${e.title}")
                    delete(el)
                    Logt(TAG, "reconcile deleted $size loose episodes")
                }
            }
            val ids = feedIdsOfAllEpisodes()
            for (id in ids) {
                val f = getFeed(id)
                if (f == null) {
                    realm.write {
                        val el = query(Episode::class, "feedId == $id").find()
                        if (el.isNotEmpty()) {
                            val size = el.size
                            for (e in el) Logd(TAG, "deleting ${e.title}")
                            delete(el)
                            Logt(TAG, "reconcile deleted $size episodes in non-existent feed $id")
                        }
                    }
                }
            }
            buildFlow()
            withContext(Dispatchers.Main) { progressing = false }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val textColor = MaterialTheme.colorScheme.onSurface
        val buttonColor = Color(0xDDFFD700)
        Box {
            TopAppBar(title = {
                SpinnerExternalSet(items = spinnerTexts, selectedIndex = curIndex) { index: Int ->
                    Logd(TAG, "Item selected: $index")
                    curIndex = index
                    vm.tag = TAG + QuickAccess.entries[curIndex]
                    putPref(AppPrefs.prefFacetsCurIndex, index)
                    actionButtonToPass = if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) { it -> EpisodeActionButton(it, ButtonTypes.DELETE) } else null
                    buildFlow()
                }
            }, navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_view_in_ar_24), contentDescription = "Open Drawer") } }, actions = {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val feedsIconRes by remember(showFeeds) { derivedStateOf { if (showFeeds) R.drawable.baseline_list_alt_24 else R.drawable.baseline_dynamic_feed_24 } }
                    IconButton(onClick = {
                        showFeeds = !showFeeds
                        if (showFeeds) loadAssociatedFeeds()
                    }) { Icon(imageVector = ImageVector.vectorResource(feedsIconRes), contentDescription = "feeds") }
                    IconButton(onClick = { navController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                    IconButton(onClick = { showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "sort") }
                    if (spinnerTexts[curIndex] == QuickAccess.All.name) IconButton(onClick = { showFilterDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), tint = if (filterButtonColor.value == Color.White) textColor else filterButtonColor.value, contentDescription = "filter") }
                    if (spinnerTexts[curIndex] == QuickAccess.History.name) IconButton(onClick = { showDatesFilter = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), contentDescription = "filter") }
                    if (spinnerTexts[curIndex] in listOf(QuickAccess.History.name, QuickAccess.Downloaded.name, QuickAccess.New.name)) {
                        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                            if (episodes.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.History.name) DropdownMenuItem(text = { Text(stringResource(R.string.clear_history_label)) }, onClick = {
                                showClearHistoryDialog.value = true
                                expanded = false
                            })
                            if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) DropdownMenuItem(text = { Text(stringResource(R.string.reconcile_label)) }, onClick = {
                                reconcile()
                                expanded = false
                            })
                            if (episodes.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.New.name) DropdownMenuItem(text = { Text(stringResource(R.string.clear_new_label)) }, onClick = {
                                clearNew()
                                expanded = false
                            })
                        }
                    }
                }
            })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedsGrid() {
        val context = LocalContext.current
        val lazyGridState = rememberLazyGridState()
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
        if (showFeeds) Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) { FeedsGrid() }
        else Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val showComment = spinnerTexts[curIndex] == QuickAccess.Commented.name
            val info = remember(infoBarText, progressing) { derivedStateOf { infoBarText.value + if (progressing) " - ${context.getString(R.string.progressing_label)}" else "" }}
            InforBar(info, swipeActions)
            EpisodeLazyColumn(context, episodes, showComment = showComment, showActionButtons = !showComment, swipeActions = swipeActions, actionButton_ = actionButtonToPass)
        }
    }
}

enum class QuickAccess {
    New, Planned, Repeats, Liked, Commented, Recorded, Queued, Downloaded, History, All
}

private val TAG = Screens.Facets.name

