package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.MediaFilesTransporter
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.getHistory
import ac.mdiq.podcini.storage.database.Episodes.vmIndexWithUrl
import ac.mdiq.podcini.storage.database.Episodes.indexWithId
import ac.mdiq.podcini.storage.database.Feeds.feedIdsOfAllEpisodes
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.storage.utils.StorageUtils.customMediaUriString
import ac.mdiq.podcini.ui.actions.DeleteActionButton
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.DatesFilterDialog
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.compose.EpisodesFilterDialog
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.SpinnerExternalSet
import ac.mdiq.podcini.ui.compose.VMS_CHUNK_SIZE
import ac.mdiq.podcini.ui.compose.buildListInfo
import ac.mdiq.podcini.ui.utils.episodeOnDisplay
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.net.URLDecoder
import java.text.NumberFormat
import java.util.Date

class FacetsVM(val context: Context, val lcScope: CoroutineScope) {
    internal var infoBarText = mutableStateOf("")
    var swipeActions: SwipeActions = SwipeActions(context, TAG)

    internal var showFeeds by mutableStateOf(false)

    val episodes = mutableListOf<Episode>()
    internal val vms = mutableStateListOf<EpisodeVM>()
    internal val feedsAssociated = mutableStateListOf<Feed>()

    var showFilterDialog by mutableStateOf(false)
    var showSortDialog by mutableStateOf(false)
    internal var showDatesFilter by mutableStateOf(false)

    internal var isFiltered by mutableStateOf(false)
    internal var filterButtonColor = mutableStateOf(Color.White)

    var actionButtonToPass by mutableStateOf<((Episode) -> EpisodeActionButton)?>(null)

    internal val spinnerTexts = QuickAccess.entries.map { it.name }
    internal var curIndex by mutableIntStateOf(0)
    var tag = TAG+QuickAccess.entries[0]

    internal var startDate: Long = 0L
    internal var endDate: Long = Date().time


    var progressing by mutableStateOf(false)

    internal val showClearHistoryDialog = mutableStateOf(false)

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

    init {
        filter = EpisodeFilter(prefFilterEpisodes)
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = vms.vmIndexWithUrl(url)
            if (pos >= 0 && pos < vms.size) vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
        }
    }

    private var eventSink: Job? = null
    private var eventStickySink: Job? = null
    private var eventKeySink: Job?     = null
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
        eventKeySink?.cancel()
        eventKeySink = null
    }
    internal fun procFlowEvents() {
        if (eventSink == null) eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeEvent -> onEpisodeEvent(event)
                    is FlowEvent.EpisodeMediaEvent -> onEpisodeMediaEvent(event)
                    is FlowEvent.HistoryEvent -> onHistoryEvent(event)
//                    is FlowEvent.FeedListEvent, is FlowEvent.EpisodePlayedEvent -> loadItems()
                    is FlowEvent.FeedListEvent -> loadItems()
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lcScope.launch {
            EventFlow.stickyEvents.drop(1).collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
//                    is FlowEvent.FeedUpdatingEvent -> onFeedUpdateRunningEvent(event)
                    else -> {}
                }
            }
        }
        if (eventKeySink == null) eventKeySink = lcScope.launch {
            EventFlow.keyEvents.collectLatest { event ->
                Logd(TAG, "Received key event: $event, ignored")
//                onKeyUp(event)
            }
        }
    }

    internal fun loadAssociatedFeeds() {
        feedsAssociated.clear()
        if (spinnerTexts[curIndex] == QuickAccess.All.name) feedsAssociated.addAll(getFeedList())
        else feedsAssociated.addAll(episodes.mapNotNull { it.feed }.distinctBy { it.id })
    }
    private var loadJob: Job? = null
    internal fun loadItems() {
        Logd(TAG, "loadItems() called")
        if (loadJob != null) {
            loadJob?.cancel()
            vms.clear()
        }
        loadJob = lcScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    episodes.clear()
                    episodes.addAll(when (spinnerTexts[curIndex]) {
                        QuickAccess.New.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.new.name), episodesSortOrder, false)
                        QuickAccess.Planned.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.soon.name, EpisodeFilter.States.later.name), episodesSortOrder, false)
                        QuickAccess.Repeats.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.again.name, EpisodeFilter.States.forever.name), episodesSortOrder, false)
                        QuickAccess.Liked.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.good.name, EpisodeFilter.States.superb.name), episodesSortOrder, false)
                        QuickAccess.Commented.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.has_comments.name), episodesSortOrder, false)
                        QuickAccess.History.name -> getHistory(0, Int.MAX_VALUE).toMutableList()
                        QuickAccess.Downloaded.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(prefFilterDownloads), episodesSortOrder, false)
                        QuickAccess.All.name -> getEpisodes(0, Int.MAX_VALUE, filter, episodesSortOrder, false)
                        else -> getEpisodes(0, Int.MAX_VALUE, filter, episodesSortOrder, false)
                    })
                }
                withContext(Dispatchers.Main) {
                    if (showFeeds) loadAssociatedFeeds()
                    vms.clear()
                    buildMoreItems()
                    updateToolbar()
                }
            } catch (e: Throwable) { Logs(TAG, e) }
        }.apply { invokeOnCompletion { loadJob = null } }
    }

    internal fun buildMoreItems() {
        val nextItems = (vms.size until (vms.size + VMS_CHUNK_SIZE).coerceAtMost(episodes.size)).map { EpisodeVM(episodes[it], tag) }
        if (nextItems.isNotEmpty()) vms.addAll(nextItems)
    }

    fun updateToolbar() {
        var info = buildListInfo(episodes)
        isFiltered = filter.propertySet.isNotEmpty()
        filterButtonColor.value = if (isFiltered) Color.Green else Color.White
        if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name && episodes.isNotEmpty()) {
            var sizeMB: Long = 0
            for (item in episodes) sizeMB += item.size
            info += " • " + (sizeMB / 1000000) + " MB"
        }
        infoBarText.value = info
    }

    internal fun clearNew() {
        progressing = true
        runOnIOScope {
            for (e in episodes) if (e.isNew) upsert(e) { it.setPlayed(false) }
            Logt(TAG, "New items cleared")
            withContext(Dispatchers.Main) { progressing = false }
            loadItems()
        }
    }

    internal fun reconcile() {
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
            var eList = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(prefFilterDownloads), episodesSortOrder, false)
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
            loadItems()
            withContext(Dispatchers.Main) { progressing = false }
        }
    }

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

    fun onFilterChanged(filter_: EpisodeFilter) {
        filter = filter_
        val filterValues = filter.propertySet
        if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name || spinnerTexts[curIndex] == QuickAccess.All.name) {
            val fSet = filterValues.toMutableSet()
            if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) fSet.add(EpisodeFilter.States.downloaded.name)
            prefFilterEpisodes = StringUtils.join(fSet, ",")
            loadItems()
        }
    }

    fun onSort(order: EpisodeSortOrder) {
        episodesSortOrder = order
        loadItems()
    }

    fun filtersDisabled(): MutableSet<EpisodeFilter.EpisodesFilterGroup> {
        return if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED)
        else mutableSetOf()
    }

    fun onHistoryEvent(event: FlowEvent.HistoryEvent) {
        if (spinnerTexts[curIndex] == QuickAccess.History.name) {
            sortOrder = event.sortOrder
            if (event.startDate > 0) startDate = event.startDate
            endDate = event.endDate
            loadItems()
            updateToolbar()
        }
    }

    fun onEpisodeEvent(event: FlowEvent.EpisodeEvent) {
        if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) {
            var i = 0
            val size: Int = event.episodes.size
            while (i < size) {
                val item: Episode = event.episodes[i++]
                val pos = episodes.indexWithId(item.id)
                if (pos >= 0) {
                    episodes.removeAt(pos)
                    if (pos < vms.size) vms.removeAt(pos)
                    if (item.downloaded) {
                        episodes.add(pos, item)
                        vms.add(pos, EpisodeVM(item, tag))
                    }
                }
            }
            updateToolbar()
        }
    }

    fun onEpisodeMediaEvent(event: FlowEvent.EpisodeMediaEvent) {
        if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) {
            var i = 0
            val size: Int = event.episodes.size
            while (i < size) {
                val item: Episode = event.episodes[i++]
                val pos = episodes.indexWithId(item.id)
                if (pos >= 0) {
                    episodes.removeAt(pos)
                    if (pos < vms.size) vms.removeAt(pos)
                    if (item.downloaded) {
                        episodes.add(pos, item)
                        vms.add(pos, EpisodeVM(item, tag))
                    }
                }
            }
            updateToolbar()
        }
    }
}

@Composable
fun FacetsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember(episodeOnDisplay.id) {  FacetsVM(context, scope) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    lifecycleOwner.lifecycle.addObserver(vm.swipeActions)
                    vm.curIndex = getPref(AppPrefs.prefFacetsCurIndex, 0)
                    vm.tag = TAG+QuickAccess.entries[vm.curIndex]
                    sortOrder = vm.episodesSortOrder
                    vm.updateToolbar()
                    vm.loadItems()
                }
                Lifecycle.Event.ON_START -> vm.procFlowEvents()
                Lifecycle.Event.ON_STOP -> vm.cancelFlowEvents()
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.episodes.clear()
            vm.feedsAssociated.clear()
            vm.vms.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @Composable
    fun OpenDialogs() {
        if (vm.showFilterDialog) EpisodesFilterDialog(filter = filter, filtersDisabled = vm.filtersDisabled(),
            onDismissRequest = { vm.showFilterDialog = false }) { filter -> vm.onFilterChanged(filter) }
        if (vm.showSortDialog) EpisodeSortDialog(initOrder = sortOrder, onDismissRequest = { vm.showSortDialog = false }) { order, _ ->
            sortOrder = order
            vm.onSort(order)
        }
        vm.swipeActions.ActionOptionsDialog()
        ComfirmDialog(titleRes = R.string.clear_history_label, message = stringResource(R.string.clear_playback_history_msg), showDialog = vm.showClearHistoryDialog) { vm.clearHistory() }
        if (vm.showDatesFilter) DatesFilterDialog(oldestDate = 0L, onDismissRequest = { vm.showDatesFilter = false} ) { timeFilterFrom, timeFilterTo ->
            EventFlow.postEvent(FlowEvent.HistoryEvent(sortOrder, timeFilterFrom, timeFilterTo))
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
                SpinnerExternalSet(items = vm.spinnerTexts, selectedIndex = vm.curIndex) { index: Int ->
                    Logd(TAG, "Item selected: $index")
                    vm.curIndex = index
                    vm.tag = TAG + QuickAccess.entries[vm.curIndex]
                    putPref(AppPrefs.prefFacetsCurIndex, index)
                    vm.actionButtonToPass = if (vm.spinnerTexts[vm.curIndex] == QuickAccess.Downloaded.name) { it -> DeleteActionButton(it) } else null
                    vm.loadItems()
                }
            }, navigationIcon = { IconButton(onClick = { MainActivity.openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_view_in_ar_24), contentDescription = "Open Drawer") } }, actions = {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val feedsIconRes by remember(vm.showFeeds) { derivedStateOf { if (vm.showFeeds) R.drawable.baseline_list_alt_24 else R.drawable.baseline_dynamic_feed_24 } }
                    IconButton(onClick = {
                        vm.showFeeds = !vm.showFeeds
                        if (vm.showFeeds) vm.loadAssociatedFeeds()
                    }) { Icon(imageVector = ImageVector.vectorResource(feedsIconRes), contentDescription = "feeds") }
                    IconButton(onClick = { mainNavController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                    IconButton(onClick = { vm.showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "sort") }
                    if (vm.spinnerTexts[vm.curIndex] == QuickAccess.All.name) IconButton(onClick = { vm.showFilterDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), tint = if (vm.filterButtonColor.value == Color.White) textColor else vm.filterButtonColor.value, contentDescription = "filter") }
                    if (vm.spinnerTexts[vm.curIndex] == QuickAccess.History.name) IconButton(onClick = { vm.showDatesFilter = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), contentDescription = "filter") }
                    if (vm.spinnerTexts[vm.curIndex] in listOf(QuickAccess.History.name, QuickAccess.Downloaded.name, QuickAccess.New.name)) {
                        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                            if (vm.vms.isNotEmpty() && vm.spinnerTexts[vm.curIndex] == QuickAccess.History.name) DropdownMenuItem(text = { Text(stringResource(R.string.clear_history_label)) }, onClick = {
                                vm.showClearHistoryDialog.value = true
                                expanded = false
                            })
                            if (vm.spinnerTexts[vm.curIndex] == QuickAccess.Downloaded.name) DropdownMenuItem(text = { Text(stringResource(R.string.reconcile_label)) }, onClick = {
                                vm.reconcile()
                                expanded = false
                            })
                            if (vm.vms.isNotEmpty() && vm.spinnerTexts[vm.curIndex] == QuickAccess.New.name) DropdownMenuItem(text = { Text(stringResource(R.string.clear_new_label)) }, onClick = {
                                vm.clearNew()
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
            items(vm.feedsAssociated.size, key = {index -> vm.feedsAssociated[index].id}) { index ->
                val feed by remember { mutableStateOf(vm.feedsAssociated[index]) }
                ConstraintLayout {
                    val (coverImage, episodeCount, rating, _) = createRefs()
                    val imgLoc = remember(feed) { feed.imageUrl }
                    AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                        .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "coverImage",
                        modifier = Modifier.height(100.dp).aspectRatio(1f)
                            .constrainAs(coverImage) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                                start.linkTo(parent.start)
                            }.combinedClickable(onClick = {
                                Logd(TAG, "clicked: ${feed.title}")
                                feedOnDisplay = feed
                                feedScreenMode = FeedScreenMode.List
                                mainNavController.navigate(Screens.FeedDetails.name)
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
//    fun multiSelectCB(index: Int, aboveOrBelow: Int): List<Episode> {
//        return when (aboveOrBelow) {
//            0 -> vm.episodes
//            -1 -> if (index < vm.episodes.size) vm.episodes.subList(0, index+1) else vm.episodes
//            1 -> if (index < vm.episodes.size) vm.episodes.subList(index, vm.episodes.size) else vm.episodes
//            else -> listOf()
//        }
//    }

    OpenDialogs()
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        if (vm.showFeeds) Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) { FeedsGrid() }
        else Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val info = remember(vm.infoBarText, vm.progressing) { derivedStateOf { vm.infoBarText.value + if (vm.progressing) " - ${context.getString(R.string.progressing_label)}" else "" }}
            InforBar(info, vm.swipeActions)
            val showComment = vm.spinnerTexts[vm.curIndex] == QuickAccess.Commented.name
            EpisodeLazyColumn(context, vms = vm.vms, showComment = showComment, showActionButtons = !showComment,
                buildMoreItems = { vm.buildMoreItems() }, swipeActions = vm.swipeActions, actionButton_ = vm.actionButtonToPass,
            )
        }
    }
}

//    private fun onKeyUp(event: KeyEvent) {
//        if (!isAdded || !isVisible || !isMenuVisible) return
//        when (event.keyCode) {
////            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
////            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(adapter.itemCount)
//            else -> {}
//        }
//    }


enum class QuickAccess {
    New, Planned, Repeats, Liked, Commented, Downloaded, History, All
}

private val TAG = Screens.Facets.name
private const val KEY_UP_ARROW = "up_arrow"

private var filter: EpisodeFilter = EpisodeFilter()
private var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)
