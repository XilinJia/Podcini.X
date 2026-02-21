package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.config.settings.MediaFilesTransporter
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.buildListInfo
import ac.mdiq.podcini.storage.database.feedsMap
import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.getEpisodesAsFlow
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.getHistoryAsFlow
import ac.mdiq.podcini.storage.database.inQueueEpisodeIdSet
import ac.mdiq.podcini.storage.database.queueToVirtual
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.ARCHIVED_VOLUME_ID
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.FROZEN_VOLUME_ID
import ac.mdiq.podcini.storage.model.FacetsPrefs
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.sortPairOf
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.utils.customMediaUriString
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.compose.AssociatedFeedsGrid
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.DatesFilterDialog
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeScreen
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.EpisodesFilterDialog
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.LocalNavController
import ac.mdiq.podcini.ui.compose.Screens
import ac.mdiq.podcini.ui.compose.StatusRowMode
import ac.mdiq.podcini.ui.compose.episodeForInfo
import ac.mdiq.podcini.ui.compose.filterChipBorder
import ac.mdiq.podcini.ui.compose.handleBackSubScreens
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.timeIt
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xilinjia.krdb.notifications.DeletedObject
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.PendingObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import io.github.xilinjia.krdb.query.RealmQuery
import io.github.xilinjia.krdb.query.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.net.URLDecoder
import java.util.Date

enum class QuickAccess {
    New, Planned, Repeats, Liked, Todos, Timers, Commented, Tagged, Recorded, Queued, Downloaded, History, Archived, Frozen, All, Custom
}

var facetsMode by mutableStateOf(QuickAccess.New)

var facetsCustomTag by mutableStateOf("")

var facetsCustomQuery: RealmQuery<Episode> = realm.query(Episode::class)

private val TAG = Screens.Facets.name

class FacetsVM(modeName_: String): ViewModel() {
    val modeName = modeName_
    var cameBack by mutableStateOf(false)

    var tag = TAG+QuickAccess.entries[0]

    var facetsPrefsJob: Job? by mutableStateOf(null)
    var facetsPrefs: FacetsPrefs = realm.query(FacetsPrefs::class).query("id == 0").first().find() ?: FacetsPrefs()

    var listIdentity by mutableStateOf("")

    var infoBarText by mutableStateOf("")
    val filterButtonColor = mutableStateOf(Color.White)

    var showFeeds by mutableStateOf(false)

    val spinnerTexts by mutableStateOf(QuickAccess.entries.map { it.name })  
    var curIndex by mutableIntStateOf(0)

    var historyStartDate by mutableLongStateOf(0L)
    var historyEndDate by mutableLongStateOf(Date().time)

    var progressing by mutableStateOf(false)

    internal var sortOrder: EpisodeSortOrder
        get() = EpisodeSortOrder.fromCode(facetsPrefs.sortCodesMap[facetsMode.name] ?: EpisodeSortOrder.DATE_DESC.code)
        set(s) {
            upsertBlk(facetsPrefs) { it.sortCodesMap[facetsMode.name] = s.code }
        }

    var filter: EpisodeFilter
        get() = EpisodeFilter(facetsPrefs.filtersMap[facetsMode.name] ?: "")
        set(value) {
            upsertBlk(facetsPrefs) { it.filtersMap[facetsMode.name] = value.propertySet.joinToString() }
        }

    suspend fun updateToolbar(episodes: List<Episode>) {
        Logd(TAG, "updateToolbar")
        withContext(Dispatchers.IO) {
            var info = buildListInfo(episodes)
            if (facetsMode == QuickAccess.Downloaded && episodes.isNotEmpty()) {
                var sizeMB: Long = 0
                for (item in episodes) sizeMB += item.size
                info += " • " + (sizeMB / 1000000) + " MB"
            }
            withContext(Dispatchers.Main) {
                val isFiltered = filter.propertySet.isNotEmpty()
                filterButtonColor.value = if (isFiltered) Color.Green else Color.White
                infoBarText = info
            }
        }
    }

    private fun buildFlow(): Flow<RealmResults<Episode>> {
        Logd(TAG, "loadItems() called")
        listIdentity = "Facets.${facetsMode.name}"
        val realmFlow = when (facetsMode) {
            QuickAccess.New -> {
                listIdentity += ".${sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.NEW.name).add(filter), sortOrder)
            }
            QuickAccess.Planned -> {
                listIdentity += ".${sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.SOON.name, EpisodeFilter.States.LATER.name).add(filter), sortOrder)
            }
            QuickAccess.Repeats -> {
                listIdentity += ".${sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.AGAIN.name, EpisodeFilter.States.FOREVER.name).add(filter), sortOrder)
            }
            QuickAccess.Liked -> {
                listIdentity += ".${sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.good.name, EpisodeFilter.States.superb.name).add(filter), sortOrder)
            }
            QuickAccess.Commented -> {
                listIdentity += ".${sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.has_comments.name).add(filter), sortOrder)
            }
            QuickAccess.Tagged -> {
                listIdentity += ".${sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.tagged.name).add(filter), sortOrder)
            }
            QuickAccess.Timers -> {
                listIdentity += ".${sortOrder.name}"
                val time = System.currentTimeMillis()
                val ids = appAttribs.timetable.filter { it.triggerTime > time }.map { it.episodeId }
                val sortPair = sortPairOf(sortOrder)
                runOnIOScope {
                    upsert(appAttribs) {
                        val oldTimers = it.timetable.filter { t -> t.triggerTime < time }
                        it.timetable.removeAll(oldTimers)
                    }
                }
                realm.query(Episode::class).query("id IN $0", ids).sort(sortPair).asFlow()
            }
            QuickAccess.Todos -> {
                listIdentity += ".${sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.has_todos.name).add(filter), sortOrder)
            }
            QuickAccess.Recorded -> {
                listIdentity += ".${sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.has_clips.name, EpisodeFilter.States.has_marks.name, andOr = "OR"), sortOrder)
            }
            QuickAccess.Queued -> {
                val qstr = EpisodeFilter(EpisodeFilter.States.QUEUE.name).add(filter).queryString()
                val ids = inQueueEpisodeIdSet()
                val sortPair = sortPairOf(sortOrder)
                listIdentity += ".${sortOrder.name}"
                realm.query(Episode::class).query("$qstr OR id IN $0", ids).sort(sortPair).asFlow()
            }
            QuickAccess.History -> {
                listIdentity += ".${historyStartDate}-${historyEndDate}"
                getHistoryAsFlow(start = historyStartDate, end = historyEndDate, filter = filter)
            }
            QuickAccess.Archived -> {
                listIdentity += ".${sortOrder.name}"
                val archFeeds = getFeedList("volumeId == $ARCHIVED_VOLUME_ID")
                val sortPair = sortPairOf(sortOrder)
                realm.query(Episode::class).query("feedId IN $0", archFeeds.map { it.id }).sort(sortPair).asFlow()
            }
            QuickAccess.Frozen -> {
                listIdentity += ".${sortOrder.name}"
                val archFeeds = getFeedList("volumeId == $FROZEN_VOLUME_ID")
                val sortPair = sortPairOf(sortOrder)
                realm.query(Episode::class).query("feedId IN $0", archFeeds.map { it.id }).sort(sortPair).asFlow()
            }
            QuickAccess.Downloaded -> {
                listIdentity += ".${sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.downloaded.name).add(filter), sortOrder)
            }
            QuickAccess.Custom -> {
                if (facetsCustomTag.isNotBlank()) {
                    listIdentity += ".${sortOrder.name}"
                    facetsCustomQuery.query(filter.queryString()).sort(sortPairOf(sortOrder)).asFlow()
                } else facetsCustomQuery.query("id == 0").asFlow()
            }
            else -> {
                listIdentity += ".${sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(facetsPrefs.filtersMap[QuickAccess.All.name] ?: ""), sortOrder)
            }
        }
        return realmFlow.map { it.list }
    }

    val episodesFlow: StateFlow<List<Episode>> = snapshotFlow { Triple(facetsMode, filter, sortOrder) }.distinctUntilChanged().flatMapLatest { buildFlow() }
        .distinctUntilChanged().stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList())

    val feedsAssFlow: StateFlow<List<Feed>> = combine(episodesFlow, snapshotFlow { showFeeds }) { episodes, showFeeds -> Pair(episodes, showFeeds) }.distinctUntilChanged().flatMapLatest { (episodes, showFeeds) ->
        if (!showFeeds) updateToolbar(episodes)
        when {
            !showFeeds -> emptyFlow()
            facetsMode == QuickAccess.All -> realm.query(Feed::class).asFlow().map { it.list }
            else -> episodesFlow.map { episodes -> episodes.mapNotNull { it.feed }.distinctBy { it.id } }
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearHistory() : Job {
        Logd(TAG, "clearHistory called")
        return runOnIOScope {
            progressing = true
            while (realm.query(Episode::class).query("playbackCompletionTime > 0 || lastPlayedTime > 0").count().find() > 0) {
                realm.write {
                    val episodes_ = query(Episode::class).query("playbackCompletionTime > 0 || lastPlayedTime > 0").find()
                    for (e in episodes_) {
                        e.playbackCompletionDate = null
                        e.lastPlayedTime = 0
                    }
                }
            }
            Logt(TAG, "History cleared")
            withContext(Dispatchers.Main) { progressing = false }
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
            MediaFilesTransporter("").updateDB()    // TODO: check out, may run out of memory?
            var eList = getEpisodes(EpisodeFilter(facetsPrefs.filtersMap[QuickAccess.Downloaded.name] ?: EpisodeFilter.States.downloaded.name), sortOrder, copy=false)
            val feMap = eList.associateBy { it.feedId }.toMutableMap()
            val iterator = feMap.iterator()
            while (iterator.hasNext()) {
                val en = iterator.next()
                if (en.key == null) continue
                val f = getFeed(en.key!!) ?: continue
                if (f.isLocalFeed) iterator.remove()
            }
            val el = feMap.values
            for (e in el) {
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
                val mediaDir = getAppContext().getExternalFilesDir("media") ?: return@runOnIOScope
                mediaDir.listFiles()?.forEach { file -> traverse(file) }
            } else {
                val customUri = customMediaUriString.toUri()
                val baseDir = DocumentFile.fromTreeUri(getAppContext(), customUri)
                baseDir?.listFiles()?.forEach { file -> traverse(file) }
            }
            Logd(TAG, "reconcile: end, episodes missing file: ${nameEpisodeMap.size}")
            if (nameEpisodeMap.isNotEmpty()) for (e in nameEpisodeMap.values) upsertBlk(e) { it.fileUrl = null }
            val count = nameEpisodeMap.size
            nameEpisodeMap.clear()
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
            val ids = realm.query(Episode::class).find().mapNotNull { it.feedId }.toSet()
            for (id in ids) {
                val f = feedsMap[id]
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
            withContext(Dispatchers.Main) { progressing = false }
        }
    }

    init {
        timeIt("$TAG start of init")
        if (facetsPrefsJob == null) facetsPrefsJob = viewModelScope.launch(Dispatchers.IO) {
            val flow = realm.query(FacetsPrefs::class).first().asFlow()
            flow.collect { changes: SingleQueryChange<FacetsPrefs> ->
                when (changes) {
                    is InitialObject -> facetsPrefs = changes.obj
                    is UpdatedObject -> facetsPrefs = changes.obj
                    is DeletedObject -> {}
                    is PendingObject -> {}
                }
            }
        }
        if (facetsMode != QuickAccess.Custom) {
            if (modeName.isNotEmpty()) {
                facetsMode = QuickAccess.entries.find { it.name == modeName } ?: QuickAccess.New
                curIndex = facetsMode.ordinal
            } else {
                curIndex = facetsPrefs.prefFacetsCurIndex
                facetsMode = QuickAccess.valueOf(spinnerTexts[curIndex])
            }
            filter = EpisodeFilter(facetsPrefs.filtersMap[facetsMode.name] ?: "")
        } else curIndex = QuickAccess.Custom.ordinal
        tag = TAG + QuickAccess.entries[curIndex]

        timeIt("$TAG end of init")
    }

    override fun onCleared() {
        super.onCleared()
        facetsPrefsJob?.cancel()
        facetsPrefsJob = null
        facetsMode = QuickAccess.New
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacetsScreen(modeName: String = "") {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val drawerController = LocalDrawerController.current

    val vm: FacetsVM = viewModel(factory = viewModelFactory { initializer { FacetsVM(modeName) } })

    var swipeActions by remember { mutableStateOf(SwipeActions(TAG+"_${facetsMode.name}")) }

    var actionButtonType by remember { mutableStateOf<ButtonTypes?>(null) }

    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDatesFilterDialog by remember { mutableStateOf(false) }
    val showClearHistoryDialog = remember { mutableStateOf(false) }

    val episodes by vm.episodesFlow.collectAsStateWithLifecycle()
    val feedsAssociated by vm.feedsAssFlow.collectAsStateWithLifecycle()

    fun resetSwipes() {
        swipeActions = SwipeActions("${TAG}_${facetsMode.name}")
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    resetSwipes()
                }
                Lifecycle.Event.ON_START -> {}
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

    DisposableEffect(vm.showFeeds, episodeForInfo) {
        if (vm.showFeeds || episodeForInfo != null) handleBackSubScreens.add(TAG)
        else handleBackSubScreens.remove(TAG)
        onDispose { handleBackSubScreens.remove(TAG) }
    }

    BackHandler(enabled = handleBackSubScreens.contains(TAG)) {
        vm.cameBack = true
        if (episodeForInfo != null) episodeForInfo = null
        else vm.showFeeds = false
    }

    var showChooseMode by remember { mutableStateOf(false) }

    @Composable
    fun OpenDialogs() {
        fun filtersDisabled(): MutableSet<EpisodeFilter.EpisodesFilterGroup> {
            return if (facetsMode == QuickAccess.Downloaded) mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED)
            else mutableSetOf()
        }
        if (showFilterDialog) EpisodesFilterDialog(filter_ = vm.filter, disabledSet = filtersDisabled(), showAndOr = facetsMode in listOf(QuickAccess.All, QuickAccess.Custom), onDismissRequest = { showFilterDialog = false }) { filter ->
            vm.filter = filter
            upsertBlk(vm.facetsPrefs) { it.filtersMap[facetsMode.name] = StringUtils.join(vm.filter.propertySet, ",") }
            resetSwipes()
//            vm.buildFlow()
        }
        if (showSortDialog) EpisodeSortDialog(initOrder = vm.sortOrder, onDismissRequest = { showSortDialog = false }) { order ->
            if (order != null) {
                vm.sortOrder = order
                resetSwipes()
//                vm.buildFlow()
            }
        }
        swipeActions.ActionOptionsDialog()
        ComfirmDialog(titleRes = R.string.clear_history_label, message = stringResource(R.string.clear_playback_history_msg), showDialog = showClearHistoryDialog) { vm.clearHistory() }
        if (showDatesFilterDialog) DatesFilterDialog(oldestDate = 0L, onDismissRequest = { showDatesFilterDialog = false} ) { timeFilterFrom, timeFilterTo ->
            vm.historyStartDate = timeFilterFrom
            vm.historyEndDate = timeFilterTo
        }

        @Composable
        fun ChooseMode() {
            Popup(onDismissRequest = { showChooseMode = false }, alignment = Alignment.TopStart, offset = IntOffset(100, 100), properties = PopupProperties(focusable = true)) {
                Card(modifier = Modifier.width(300.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(10.dp)) {
                        for (index in vm.spinnerTexts.indices) {
                            FilterChip(onClick = {
                                vm.cameBack = false
                                vm.curIndex = index
                                facetsMode = QuickAccess.valueOf(vm.spinnerTexts[vm.curIndex])
                                vm.tag = TAG + QuickAccess.entries[vm.curIndex]
                                upsertBlk(vm.facetsPrefs) { it.prefFacetsCurIndex = index}
                                actionButtonType = if (facetsMode == QuickAccess.Downloaded) ButtonTypes.DELETE else null
                                resetSwipes()
//                                vm.buildFlow()
                                showChooseMode = false
                            }, label = { Text(vm.spinnerTexts[index]) }, selected = vm.curIndex == index, border = filterChipBorder(vm.curIndex == index))
                        }
                    }
                }
            }
        }
        if (showChooseMode) ChooseMode()
    }

    
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val textColor = MaterialTheme.colorScheme.onSurface
        val buttonColor = Color(0xDDFFD700)
        Box {
            TopAppBar(title = { Text(facetsMode.name, maxLines=1, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.scale(scaleX = 1f, scaleY = 1.8f).clickable(onClick = { showChooseMode = true })) },
                navigationIcon = { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_view_in_ar_24), contentDescription = "Open Drawer", modifier = Modifier.padding(7.dp).clickable(onClick = { drawerController?.open() })) },
                actions = {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        val feedsIconRes by remember(vm.showFeeds) { derivedStateOf { if (vm.showFeeds) R.drawable.baseline_list_alt_24 else R.drawable.baseline_dynamic_feed_24 } }
                        IconButton(onClick = { vm.showFeeds = !vm.showFeeds }) { Icon(imageVector = ImageVector.vectorResource(feedsIconRes), contentDescription = "feeds") }
                        IconButton(onClick = { showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "sort") }
                        if (facetsMode != QuickAccess.Recorded) IconButton(onClick = { showFilterDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), tint = if (vm.filterButtonColor.value == Color.White) textColor else vm.filterButtonColor.value, contentDescription = "filter") }
                        if (vm.showFeeds) IconButton(onClick = {
                            feedIdsToUse.clear()
                            feedIdsToUse.addAll(feedsAssociated.map { it.id })
                            navController.navigate(Screens.Library.name)
                        }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_subscriptions), contentDescription = "library") }
                        IconButton(onClick = { navController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                        if (facetsMode in listOf(QuickAccess.History, QuickAccess.Downloaded, QuickAccess.New)) {
                            IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                            DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                                if (episodes.isNotEmpty() && facetsMode == QuickAccess.History) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.between_dates)) }, onClick = {
                                        showDatesFilterDialog = true
                                        expanded = false
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_history_label)) }, onClick = {
                                        showClearHistoryDialog.value = true
                                        expanded = false
                                    })
                                }
                                if (facetsMode == QuickAccess.Downloaded) DropdownMenuItem(text = { Text(stringResource(R.string.reconcile_label)) }, onClick = {
                                    vm.reconcile()
                                    expanded = false
                                })
                                if (episodes.isNotEmpty() && facetsMode == QuickAccess.New) DropdownMenuItem(text = { Text(stringResource(R.string.clear_new_label)) }, onClick = {
                                    vm.progressing = true
                                    runOnIOScope {
                                        for (e in episodes) if (e.playState == EpisodeState.NEW.code) upsert(e) { it.setPlayState(EpisodeState.UNPLAYED) }
                                        Logt(TAG, "New items cleared")
                                        withContext(Dispatchers.Main) { vm.progressing = false }
                                        resetSwipes()
//                                        vm.buildFlow()
                                    }
                                    expanded = false
                                })
                            }
                        }
                    }
                })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    OpenDialogs()

    if (episodeForInfo != null) EpisodeScreen(episodeForInfo!!, listFlow = vm.episodesFlow)
    else Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        if (vm.showFeeds) Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) { AssociatedFeedsGrid(feedsAssociated) }
        else Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val statusMode by remember(facetsMode) { mutableStateOf(
                when (facetsMode) {
                    QuickAccess.Commented -> StatusRowMode.Comment
                    QuickAccess.Tagged -> StatusRowMode.Tags
                    QuickAccess.Todos -> StatusRowMode.Todos
                    else -> StatusRowMode.Normal
                })
            }
            val info = remember(vm.infoBarText, vm.progressing, facetsMode) { mutableStateOf(
                (if (facetsMode == QuickAccess.Custom) "$facetsCustomTag | " else "") + vm.infoBarText + (if (vm.progressing) " - ${context.getString(R.string.progressing_label)}" else "")
            ) }
            InforBar(swipeActions) { Text(info.value, style = MaterialTheme.typography.bodyMedium) }
            EpisodeLazyColumn(episodes, statusRowMode = statusMode, showActionButtons = facetsMode != QuickAccess.Commented, swipeActions = swipeActions, actionButtonType = actionButtonType,
                actionButtonCB = { e, type ->
                    if (type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAY_LOCAL, ButtonTypes.STREAM)) runOnIOScope { queueToVirtual(e, episodes, vm.listIdentity, vm.sortOrder) }
                })
        }
    }
}

