package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.VIRTUAL_QUEUE_SIZE
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.virQueue
import ac.mdiq.podcini.preferences.MediaFilesTransporter
import ac.mdiq.podcini.storage.database.buildListInfo
import ac.mdiq.podcini.storage.database.feedIdsOfAllEpisodes
import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.getEpisodesAsFlow
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.getHistoryAsFlow
import ac.mdiq.podcini.storage.database.inQueueEpisodeIdSet
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.FacetsPrefs
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
import ac.mdiq.podcini.ui.compose.StatusRowMode
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logt
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
import androidx.compose.runtime.rememberUpdatedState
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
import io.github.xilinjia.krdb.notifications.DeletedObject
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.PendingObject
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import io.github.xilinjia.krdb.query.RealmQuery
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

enum class QuickAccess {
    New, Planned, Repeats, Liked, Commented, Tagged, Recorded, Queued, Downloaded, History, All, Custom
}

var facetsMode by mutableStateOf(QuickAccess.New)

var facetsCustomTag by mutableStateOf("")

var facetsCustomQuery: RealmQuery<Episode> = realm.query(Episode::class)

private val TAG = Screens.Facets.name

class FacetsVM(val context: Context, val lcScope: CoroutineScope) {
    var tag = TAG+QuickAccess.entries[0]

    var facetsPrefs: FacetsPrefs = realm.query(FacetsPrefs::class).first().find() ?: FacetsPrefs()

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
}

@Composable
fun FacetsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val vm = remember {  FacetsVM(context, scope) }
    val navController = LocalNavController.current

    var facetsPrefsJob: Job? by remember { mutableStateOf(null) }

    var episodesFlow by remember { mutableStateOf<Flow<ResultsChange<Episode>>>(emptyFlow()) }

    var listIdentity by remember { mutableStateOf("") }

    val feedsAssociated = remember { mutableStateListOf<Feed>() }

    var infoBarText by remember {  mutableStateOf("") }

    var showFeeds by remember { mutableStateOf(false) }

    val filterButtonColor = remember { mutableStateOf(Color.White) }

    val spinnerTexts by remember { mutableStateOf(QuickAccess.entries.map { it.name })  }
    var curIndex by remember { mutableIntStateOf(0) }

    var swipeActions by remember { mutableStateOf(SwipeActions(context, TAG+"_${facetsMode.name}")) }

    var actionButtonToPass by remember { mutableStateOf<((Episode) -> EpisodeActionButton)?>(null) }

    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDatesFilterDialog by remember { mutableStateOf(false) }
    val showClearHistoryDialog = remember { mutableStateOf(false) }

    var historyStartDate by remember { mutableStateOf(0L) }
    var historyEndDate = remember { Date().time }

    val episodesChange by episodesFlow.collectAsState(initial = null)
    val episodes = episodesChange?.list ?: emptyList()

    fun buildFlow() {
        Logd(TAG, "loadItems() called")
        listIdentity = "Facets.${facetsMode.name}"
        lifecycleOwner.lifecycle.removeObserver(swipeActions)
        swipeActions = SwipeActions(context, "${TAG}_${facetsMode.name}")
        lifecycleOwner.lifecycle.addObserver(swipeActions)
        episodesFlow = when (facetsMode) {
            QuickAccess.New -> {
                listIdentity += ".${vm.sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.NEW.name).add(vm.filter), vm.sortOrder)
            }
            QuickAccess.Planned -> {
                listIdentity += ".${vm.sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.SOON.name, EpisodeFilter.States.LATER.name).add(vm.filter), vm.sortOrder)
            }
            QuickAccess.Repeats -> {
                listIdentity += ".${vm.sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.AGAIN.name, EpisodeFilter.States.FOREVER.name).add(vm.filter), vm.sortOrder)
            }
            QuickAccess.Liked -> {
                listIdentity += ".${vm.sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.good.name, EpisodeFilter.States.superb.name).add(vm.filter), vm.sortOrder)
            }
            QuickAccess.Commented -> {
                listIdentity += ".${vm.sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.has_comments.name).add(vm.filter), vm.sortOrder)
            }
            QuickAccess.Tagged -> {
                listIdentity += ".${vm.sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.tagged.name).add(vm.filter), vm.sortOrder)
            }
            QuickAccess.Recorded -> {
                listIdentity += ".${vm.sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.has_clips.name, EpisodeFilter.States.has_marks.name, andOr = "OR"), vm.sortOrder)
            }
            QuickAccess.Queued -> {
                val qstr = EpisodeFilter(EpisodeFilter.States.QUEUE.name).add(vm.filter).queryString()
                val ids = inQueueEpisodeIdSet()
                val sortPair = sortPairOf(vm.sortOrder)
                listIdentity += ".${vm.sortOrder.name}"
                realm.query(Episode::class).query("$qstr OR id IN $0", ids).sort(sortPair).asFlow()
            }
            QuickAccess.History -> {
                listIdentity += ".$historyStartDate-$historyEndDate"
                getHistoryAsFlow(start = historyStartDate, end = historyEndDate, filter = vm.filter)
            }
            QuickAccess.Downloaded -> {
                listIdentity += ".${vm.sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(EpisodeFilter.States.downloaded.name).add(vm.filter), vm.sortOrder)
            }
            QuickAccess.Custom -> {
                if (facetsCustomTag.isNotBlank()) {
                    listIdentity += ".${vm.sortOrder.name}"
                    facetsCustomQuery.query(vm.filter.queryString()).sort(sortPairOf(vm.sortOrder)).asFlow()
                } else facetsCustomQuery.query("id == 0").asFlow()
            }
            else -> {
                listIdentity += ".${vm.sortOrder.name}"
                getEpisodesAsFlow(EpisodeFilter(vm.facetsPrefs.filtersMap[QuickAccess.All.name] ?: ""), vm.sortOrder)
            }
        }
    }

    fun updateToolbar() {
        Logd(TAG, "updateToolbar")
        scope.launch(Dispatchers.IO) {
            var info = buildListInfo(episodes)
            if (facetsMode == QuickAccess.Downloaded && episodes.isNotEmpty()) {
                var sizeMB: Long = 0
                for (item in episodes) sizeMB += item.size
                info += " • " + (sizeMB / 1000000) + " MB"
            }
            withContext(Dispatchers.Main) {
                val isFiltered = vm.filter.propertySet.isNotEmpty()
                filterButtonColor.value = if (isFiltered) Color.Green else Color.White
                infoBarText = info
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    lifecycleOwner.lifecycle.addObserver(swipeActions)
                    if (facetsPrefsJob == null) facetsPrefsJob = scope.launch(Dispatchers.IO) {
                        val flow = realm.query(FacetsPrefs::class).first().asFlow()
                        flow.collect { changes: SingleQueryChange<FacetsPrefs> ->
                            when (changes) {
                                is InitialObject -> vm.facetsPrefs = changes.obj
                                is UpdatedObject -> vm.facetsPrefs = changes.obj
                                is DeletedObject -> {}
                                is PendingObject -> {}
                            }
                        }
                    }
                    if (facetsMode != QuickAccess.Custom) {
                        curIndex = vm.facetsPrefs.prefFacetsCurIndex
                        facetsMode = QuickAccess.valueOf(spinnerTexts[curIndex])
                        vm.filter = EpisodeFilter(vm.facetsPrefs.filtersMap[facetsMode.name] ?: "")
                    } else curIndex = QuickAccess.Custom.ordinal
                    vm.tag = TAG+QuickAccess.entries[curIndex]
                    updateToolbar()
                    buildFlow()
                }
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            feedsAssociated.clear()
            facetsMode = QuickAccess.New
            facetsPrefsJob?.cancel()
            facetsPrefsJob = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(episodes.size, showFeeds) {
        updateToolbar()
        if (showFeeds) {
            feedsAssociated.clear()
            if (facetsMode == QuickAccess.All) feedsAssociated.addAll(getFeedList())
            else feedsAssociated.addAll(episodes.mapNotNull { it.feed }.distinctBy { it.id })
        }
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

            }
        }
        fun filtersDisabled(): MutableSet<EpisodeFilter.EpisodesFilterGroup> {
            return if (facetsMode == QuickAccess.Downloaded) mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED)
            else mutableSetOf()
        }
        if (showFilterDialog) EpisodesFilterDialog(filter_ = vm.filter, disabledSet = filtersDisabled(), showAndOr = facetsMode in listOf(QuickAccess.All, QuickAccess.Custom), onDismissRequest = { showFilterDialog = false }) { filter ->
            vm.filter = filter
            upsertBlk(vm.facetsPrefs) { it.filtersMap[facetsMode.name] = StringUtils.join(vm.filter.propertySet, ",") }
            buildFlow()
        }
        if (showSortDialog) EpisodeSortDialog(initOrder = vm.sortOrder, onDismissRequest = { showSortDialog = false }) { order ->
            if (order != null) {
                vm.sortOrder = order
                buildFlow()
            }
        }
        swipeActions.ActionOptionsDialog()
        ComfirmDialog(titleRes = R.string.clear_history_label, message = stringResource(R.string.clear_playback_history_msg), showDialog = showClearHistoryDialog) { clearHistory() }
        if (showDatesFilterDialog) DatesFilterDialog(oldestDate = 0L, onDismissRequest = { showDatesFilterDialog = false} ) { timeFilterFrom, timeFilterTo ->
            historyStartDate = timeFilterFrom
            historyEndDate = timeFilterTo
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
            var eList = getEpisodes(EpisodeFilter(vm.facetsPrefs.filtersMap[QuickAccess.Downloaded.name] ?: EpisodeFilter.States.downloaded.name), vm.sortOrder, copy=false)
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
                    el.forEach { e ->
                        count++
                        e.setfileUrlOrNull(null)
//                        copyToRealm(e, UpdatePolicy.ALL)
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
            withContext(Dispatchers.Main) {
                buildFlow()
                progressing = false
            }
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
                    facetsMode = QuickAccess.valueOf(spinnerTexts[curIndex])
                    vm.tag = TAG + QuickAccess.entries[curIndex]
                    upsertBlk(vm.facetsPrefs) { it.prefFacetsCurIndex = index}
                    actionButtonToPass = if (facetsMode == QuickAccess.Downloaded) { it -> EpisodeActionButton(it, ButtonTypes.DELETE) } else null
                    buildFlow()
                }
            }, navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_view_in_ar_24), contentDescription = "Open Drawer") } }, actions = {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val feedsIconRes by remember(showFeeds) { derivedStateOf { if (showFeeds) R.drawable.baseline_list_alt_24 else R.drawable.baseline_dynamic_feed_24 } }
                    IconButton(onClick = { showFeeds = !showFeeds }) { Icon(imageVector = ImageVector.vectorResource(feedsIconRes), contentDescription = "feeds") }
                    IconButton(onClick = { navController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                    IconButton(onClick = { showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "sort") }
                    if (facetsMode != QuickAccess.Recorded) IconButton(onClick = { showFilterDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), tint = if (filterButtonColor.value == Color.White) textColor else filterButtonColor.value, contentDescription = "filter") }
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
                                reconcile()
                                expanded = false
                            })
                            if (episodes.isNotEmpty() && facetsMode == QuickAccess.New) DropdownMenuItem(text = { Text(stringResource(R.string.clear_new_label)) }, onClick = {
                                progressing = true
                                runOnIOScope {
                                    for (e in episodes) if (e.isNew) upsert(e) { it.setPlayed(false) }
                                    Logt(TAG, "New items cleared")
                                    withContext(Dispatchers.Main) { progressing = false }
                                    buildFlow()
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
                            navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.id}")
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
            val statusMode by remember(facetsMode) { mutableStateOf(
                when (facetsMode) {
                    QuickAccess.Commented -> StatusRowMode.Comment
                    QuickAccess.Tagged -> StatusRowMode.Tags
                    else -> StatusRowMode.Normal
                })
            }
            val info = remember(infoBarText, progressing, facetsMode) { mutableStateOf(
                (if (facetsMode == QuickAccess.Custom) "$facetsCustomTag | " else "") + infoBarText + (if (progressing) " - ${context.getString(R.string.progressing_label)}" else "")
            ) }
            InforBar(info, swipeActions)
            EpisodeLazyColumn(context, episodes, statusRowMode = statusMode, showActionButtons = facetsMode != QuickAccess.Commented, swipeActions = swipeActions, actionButton_ = actionButtonToPass,
                actionButtonCB = { e, type ->
                    if (type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAYLOCAL, ButtonTypes.STREAM)) {
                        if (virQueue.identity != listIdentity) {
                            runOnIOScope {
                                virQueue = upsert(virQueue) { q ->
                                    q.identity = listIdentity
                                    q.playInSequence = true
                                    q.sortOrder = vm.sortOrder
                                    q.episodeIds.clear()
                                    q.episodeIds.addAll(episodes.take(VIRTUAL_QUEUE_SIZE).map { it.id })
                                }
                                virQueue.episodes.clear()
                                actQueue = virQueue
                            }
                            Logt(TAG, "first $VIRTUAL_QUEUE_SIZE episodes are added to the Virtual queue")
                        }
                    }
                })
        }
    }
}

