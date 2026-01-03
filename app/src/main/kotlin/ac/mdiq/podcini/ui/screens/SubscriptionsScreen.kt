package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.FeedUpdateManager.checkAndscheduleUpdateTaskOnce
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.preferences.DocumentFileExportWorker
import ac.mdiq.podcini.preferences.ExportTypes
import ac.mdiq.podcini.preferences.ExportWorker
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.feedCount
import ac.mdiq.podcini.storage.database.feedOperationText
import ac.mdiq.podcini.storage.database.getEpisodesCount
import ac.mdiq.podcini.storage.database.queuesFlow
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.SubscriptionsPrefs
import ac.mdiq.podcini.storage.model.Volume
import ac.mdiq.podcini.storage.model.deleteVolumeTree
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.FeedFilter
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.durationInHours
import ac.mdiq.podcini.storage.utils.durationStringFull
import ac.mdiq.podcini.storage.utils.getDurationStringShort
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.PutToQueueDialog
import ac.mdiq.podcini.ui.compose.RemoveFeedDialog
import ac.mdiq.podcini.ui.compose.RenameOrCreateSyntheticFeed
import ac.mdiq.podcini.ui.compose.ScrollRowGrid
import ac.mdiq.podcini.ui.compose.SelectLowerAllUpper
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.TagType
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.compose.complementaryColorOf
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.formatDateTimeFlex
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.xilinjia.krdb.ext.toRealmSet
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SubscriptionsScreen"

class SubscriptionsVM : ViewModel() {
    var subPrefs by mutableStateOf( realm.query(SubscriptionsPrefs::class).query("id == 0").first().find() ?: SubscriptionsPrefs())

    var prefsFlow by mutableStateOf<Flow<SingleQueryChange<SubscriptionsPrefs>>>(emptyFlow())

    var feedsFlow by mutableStateOf<Flow<ResultsChange<Feed>>>(emptyFlow())
    var subVolumesFlow by mutableStateOf<Flow<ResultsChange<Volume>>>(emptyFlow())

    var curVolume by mutableStateOf<Volume?>(null)
    val queueNames = mutableStateListOf<String>() 
    val queueIds = mutableListOf<Long>()

    val shouldHandleBack: Boolean get() = curVolume != null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            queuesFlow.collect { changes ->
                val ids = changes.list.map { it.id }
                val names = changes.list.map { it.name }
                withContext(Dispatchers.Main) {
                    queueIds.clear()
                    queueIds.addAll(ids)
                    queueNames.clear()
                    queueNames.addAll(names)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        curVolume = null
        queueIds.clear()
        queueNames.clear()
    }
}

@Composable
fun SubscriptionsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val drawerState = LocalDrawerController.current

    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)

    val vm: SubscriptionsVM = viewModel()
    
    //    TODO: currently not used
    var displayedFolder by remember { mutableStateOf("") }

    var filterAndSort by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showNewSynthetic by remember { mutableStateOf(false) }
    var showNewVolume by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf(false) }

    val prefsChange by vm.prefsFlow.collectAsStateWithLifecycle(initialValue = null)
    if (prefsChange?.obj != null) vm.subPrefs = prefsChange?.obj!!

    val feedsChange by vm.feedsFlow.collectAsStateWithLifecycle(initialValue = null)
    val feeds = feedsChange?.list ?: emptyList()

    val volumesChange by vm.subVolumesFlow.collectAsStateWithLifecycle(initialValue = null)
    val volumes = volumesChange?.list ?: emptyList()
//    Logd(TAG, "volumes: ${volumes.size}")

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect Lifecycle.Event: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
//                    Logd(TAG, "check vm.subPrefs count: ${realm.query(SubscriptionsPrefs::class).count().find()}")
                    vm.subVolumesFlow = realm.query(Volume::class).query("parentId == ${vm.curVolume?.id ?: -1L}").asFlow()
                    runOnIOScope {
                        upsert(vm.subPrefs) {
                            it.feedsSorted = 0
                            it.feedsFiltered = 0
                        }
                    }
                    vm.prefsFlow = realm.query(SubscriptionsPrefs::class).query("id == 0").first().asFlow()
                }
                Lifecycle.Event.ON_START -> gearbox.cleanGearData()
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            Logd(TAG, "Lifecycle.Event onDispose")
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    subscreenHandleBack.value = vm.shouldHandleBack

    BackHandler(enabled = subscreenHandleBack.value) {
        vm.curVolume = realm.query(Volume::class).query("id == ${vm.curVolume?.parentId ?: -1L}").first().find()
    }

    DisposableEffect(Unit) {
        onDispose { subscreenHandleBack.value = false }
    }

    var playStateQueries by remember { mutableStateOf("") }
    var ratingQueries by remember { mutableStateOf("") }
    var downloadedQuery by remember { mutableStateOf("") }
    var commentedQuery by remember { mutableStateOf("") }

    fun prepareDateSort(subIndex: FeedDateSortIndex? = null) {
        val subIndexOrdinal = subIndex?.ordinal ?: vm.subPrefs.dateSortIndex
        Logd(TAG, "prepareDateSort")
        suspend fun persistDateSort() {
            upsert(vm.subPrefs) {
                it.sortIndex = FeedSortIndex.Date.ordinal
                it.dateSortIndex = subIndexOrdinal
                it.sortProperty = "sortValue"
                it.feedsSorted++
            }
        }
        runOnIOScope {
            when (subIndexOrdinal) {
                FeedDateSortIndex.Publish.ordinal -> {  // date publish
                    var queryString = "feedId == $0"
                    if (playStateQueries.isNotEmpty()) queryString += " AND ($playStateQueries)"
                    queryString += " SORT(pubDate DESC)"
                    Logd(TAG, "prepareSort queryString: $queryString")
                    realm.write {
                        for (f_ in feeds) {
                            val f = findLatest(f_) ?: continue
                            val d = query(Episode::class).query(queryString, f.id).first().find()?.pubDate ?: 0L
                            f.sortValue = d
                            f.sortInfo = formatDateTimeFlex(Date(d))
                        }
                    }
                    persistDateSort()
                }
                FeedDateSortIndex.Downloaded.ordinal -> {  // date downloaded
                    val queryString = "feedId == $0 SORT(downloadTime DESC)"
                    realm.write {
                        for (f_ in feeds) {
                            val f = findLatest(f_) ?: continue
                            val d = query(Episode::class).query(queryString, f.id).first().find()?.downloadTime ?: 0L
                            f.sortValue = d
                            f.sortInfo = "D: ${formatDateTimeFlex(Date(d))}"
                        }
                    }
                    Logd(TAG, "prepareSort queryString: $queryString")
                    persistDateSort()
                }
                FeedDateSortIndex.Played.ordinal -> {  // date last played
                    val queryString = "feedId == $0 SORT(lastPlayedTime DESC)"
                    realm.write {
                        for (f_ in feeds) {
                            val f = findLatest(f_) ?: continue
                            val d = query(Episode::class).query(queryString, f.id).first().find()?.lastPlayedTime ?: 0L
                            f.sortValue = d
                            f.sortInfo = "P: ${formatDateTimeFlex(Date(d))}"
                        }
                    }
                    Logd(TAG, "prepareSort queryString: $queryString")
                    persistDateSort()
                }
                FeedDateSortIndex.Commented.ordinal -> {  // date last commented
                    val queryString = "feedId == $0 SORT(commentTime DESC)"
                    realm.write {
                        for (f_ in feeds) {
                            val f = findLatest(f_) ?: continue
                            val d = query(Episode::class).query(queryString, f.id).first().find()?.commentTime ?: 0L
                            f.sortValue = d
                            f.sortInfo = "C: ${formatDateTimeFlex(Date(d))}"
                        }
                    }
                    Logd(TAG, "prepareSort queryString: $queryString")
                    persistDateSort()
                }
                else -> Loge(TAG, "No such date sorting ${vm.subPrefs.dateSortIndex}")
            }
        }
    }

    fun prepareTimeSort(subIndex: FeedTimeSortIndex? = null) {
        val subIndexOrdinal = subIndex?.ordinal ?: vm.subPrefs.timeSortIndex
        Logd(TAG, "prepareTimeSort")
        suspend fun persistTimeSort() {
            upsert(vm.subPrefs) {
                it.sortIndex = FeedSortIndex.Time.ordinal
                it.timeSortIndex = subIndexOrdinal
                it.sortProperty = "sortValue"
//                it.timeAscending = !it.timeAscending
                it.feedsSorted++
            }
        }
        runOnIOScope {
            when (subIndexOrdinal) {
                FeedTimeSortIndex.Total.ordinal -> { // total duration
                    realm.write {
                        for (f_ in feeds) {
                            val f = findLatest(f_) ?: continue
                            f.sortValue = f.totleDuration
                            f.sortInfo = "Total D: ${getDurationStringShort(f.totleDuration, true)}"
                        }
                    }
                    persistTimeSort()
                }
                FeedTimeSortIndex.Min.ordinal -> {  // min duration
                    val queryString = "feedId == $0 SORT(duration ASC)"
                    realm.write {
                        for (f_ in feeds) {
                            val f = findLatest(f_) ?: continue
                            val d = query(Episode::class).query(queryString, f.id).first().find()?.duration?.toLong() ?: 0L
                            f.sortValue = d
                            f.sortInfo = "Min D: ${durationStringFull(d.toInt())}"
                        }
                    }
                    Logd(TAG, "prepareSort queryString: $queryString")
                    persistTimeSort()
                }
                FeedTimeSortIndex.Max.ordinal -> {  // max duration
                    val queryString = "feedId == $0 SORT(duration DESC)"
                    realm.write {
                        for (f_ in feeds) {
                            val f = findLatest(f_) ?: continue
                            val d = query(Episode::class).query(queryString, f.id).first().find()?.duration?.toLong() ?: 0L
                            f.sortValue = d
                            f.sortInfo = "Max D: ${getDurationStringShort(d, true)}"
                        }
                    }
                    Logd(TAG, "prepareSort queryString: $queryString")
                    persistTimeSort()
                }
                FeedTimeSortIndex.Average.ordinal -> {  // average duration
                    realm.write {
                        for (f_ in feeds) {
                            val f = findLatest(f_) ?: continue
                            val ln = getEpisodesCount(null, f.id)
                            val aveDur = if (ln > 0) f.totleDuration/ln else 0
                            f.sortValue = aveDur
                            f.sortInfo = "Ave D: ${getDurationStringShort(aveDur, true)}"
                        }
                    }
                    persistTimeSort()
                }
                else -> Loge(TAG, "No such time sorting ${vm.subPrefs.timeSortIndex}")
            }
        }
    }

    fun prepareCountSort() {
        Logd(TAG, "prepareCountSort")
        runOnIOScope {
            val sb = StringBuilder("feedId == $0")
            if (playStateQueries.isNotEmpty()) sb.append(" AND ($playStateQueries)")
            if (ratingQueries.isNotEmpty()) sb.append(" AND ($ratingQueries)")
            if (downloadedQuery.isNotEmpty()) sb.append(" AND ($downloadedQuery)")
            if (commentedQuery.isNotEmpty()) sb.append(" AND ($commentedQuery)")
            val queryString = sb.toString()
            Logd(TAG, "prepareCountSort queryString: $queryString")
            realm.write {
                for (f_ in feeds) {
                    val f = findLatest(f_) ?: continue
                    val c = query(Episode::class).query(queryString, f.id).count().find()
                    f.sortValue = c
                    f.sortInfo = "$c counts"
                }
            }
            upsert(vm.subPrefs) {
                it.sortIndex = FeedSortIndex.Count.ordinal
                it.sortProperty = "sortValue"
//                it.countAscending = !it.countAscending
                it.feedsSorted++
            }
        }
    }

    LaunchedEffect(feedOperationText, feeds.size) {
        Logd(TAG, "LaunchedEffect feedOperationText: $feedOperationText feeds.size: ${feeds.size}")
        if (feedOperationText.isBlank()) when (vm.subPrefs.sortIndex) {
            FeedSortIndex.Date.ordinal -> prepareDateSort()
            FeedSortIndex.Time.ordinal -> prepareTimeSort()
            FeedSortIndex.Count.ordinal -> prepareCountSort()
            else -> {}
        }
    }

    fun languagesQS() : String {
        var qrs  = ""
        when {
            vm.subPrefs.langsSel.isEmpty() -> qrs = " (langSet.@count > 0) "
            vm.subPrefs.langsSel.size == appAttribs.langSet.size -> qrs = ""
            else -> {
                for (l in vm.subPrefs.langsSel) {
                    qrs += if (qrs.isEmpty()) " ( ANY langSet == '$l' " else " OR ANY langSet == '$l' "
                }
                if (qrs.isNotEmpty()) qrs += " ) "
            }
        }
        Logd(TAG, "languagesQS: $qrs")
        return qrs
    }
    fun tagsQS() : String {
        var qrs  = ""
        when {
            vm.subPrefs.tagsSel.isEmpty() -> qrs = " (tags.@count == 0 OR (tags.@count != 0 AND ALL tags == '#root' )) "
            vm.subPrefs.tagsSel.size == appAttribs.feedTagSet.size -> qrs = ""
            else -> {
                for (t in vm.subPrefs.tagsSel) {
                    qrs += if (qrs.isEmpty()) " ( ANY tags == '$t' " else " OR ANY tags == '$t' "
                }
                if (qrs.isNotEmpty()) qrs += " ) "
            }
        }
        Logd(TAG, "tagsQS: $qrs")
        return qrs
    }
    fun queuesQS() : String {
        val qSelIds_ = vm.subPrefs.queueSelIds.toMutableSet()
        if (qSelIds_.isEmpty()) qSelIds_.add(-2)
        else {
            if ((vm.queueIds - qSelIds_).isEmpty()) qSelIds_.clear()
            else qSelIds_.remove(-2)
        }
        var qrs  = ""
        for (id in qSelIds_) {
            qrs += if (qrs.isEmpty()) " ( queueId == '$id' " else " OR queueId == '$id' "
        }
        if (qrs.isNotEmpty()) qrs += " ) "
        Logd(TAG, "queuesQS: $qrs")
        return qrs
    }

    val langsQueryStr by remember(vm.subPrefs.langsSel.size) { mutableStateOf(languagesQS()) }
    val tagsQueryStr by remember(vm.subPrefs.tagsSel.size) { mutableStateOf(tagsQS()) }
    val queuesQueryStr by remember(vm.subPrefs.queueSelIds.size) { mutableStateOf(queuesQS()) }
    val filterQueryStr by remember(vm.subPrefs.feedsFilter) { mutableStateOf(FeedFilter(vm.subPrefs.feedsFilter).queryString()) }

    LaunchedEffect(vm.curVolume) {
        vm.subVolumesFlow = realm.query(Volume::class).query("parentId == ${vm.curVolume?.id ?: -1L}").asFlow()
    }

    LaunchedEffect(vm.curVolume,vm.subPrefs.feedsFiltered, vm.subPrefs.feedsSorted) {
        Logd(TAG, "LaunchedEffect feedsFiltered, sortPair.first, sortPair")
        val sb = StringBuilder(filterQueryStr)
        if (langsQueryStr.isNotEmpty())  sb.append(" AND $langsQueryStr")
        if (tagsQueryStr.isNotEmpty())  sb.append(" AND $tagsQueryStr")
        if (queuesQueryStr.isNotEmpty())  sb.append(" AND $queuesQueryStr")
        val fetchQS = sb.toString()
        val sortPair = Pair(vm.subPrefs.sortProperty.ifBlank { "eigenTitle" }, if (vm.subPrefs.sortDirCode == 0) Sort.ASCENDING else Sort.DESCENDING)
        Logd(TAG, "fetchQS: $fetchQS ${sortPair.first} ${sortPair.second.name}")
        vm.feedsFlow = realm.query(Feed::class).query("volumeId == ${vm.curVolume?.id ?: -1L}").query(fetchQS).sort(sortPair).asFlow()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val isFiltered by remember(vm.subPrefs.feedsFilter, vm.subPrefs.tagsSel.size, vm.subPrefs.langsSel.size, vm.subPrefs.queueSelIds.size) { mutableStateOf(vm.subPrefs.feedsFilter.isNotEmpty() || vm.subPrefs.tagsSel.size != appAttribs.feedTagSet.size || vm.subPrefs.langsSel.size != appAttribs.langSet.size || vm.subPrefs.queueSelIds.size != vm.queueIds.size) }
        Box {
            TopAppBar(title = {
                Row {
                    if (displayedFolder.isNotEmpty()) Text(displayedFolder, modifier = Modifier.padding(end = 5.dp))
                    if (feedOperationText.isNotEmpty()) Text(feedOperationText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable {})
                    else {
                        var feedCountState by remember(feeds.size) { mutableStateOf(feeds.size.toString() + " / " + feedCount.toString()) }
                        Text(feedCountState, color = textColor)
                    }
                }
            },
                navigationIcon = { IconButton(onClick = { drawerState.open() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_subscriptions), contentDescription = "Open Drawer") } },
                actions = {
                    IconButton(onClick = { navController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                    IconButton(onClick = { showFilterDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), tint = if (isFiltered) buttonAltColor else MaterialTheme.colorScheme.onSurface, contentDescription = "filter") }
                    IconButton(onClick = { showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "sort") }
                    IconButton(onClick = {
                        facetsMode = QuickAccess.Custom
                        facetsCustomTag = "Subscriptions"
                        facetsCustomQuery = realm.query(Episode::class).query("feedId IN $0", feeds.map { it.id })
                        navController.navigate(Screens.Facets.name)
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_view_in_ar_24), contentDescription = "facets") }
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded,
                        border = BorderStroke(1.dp, buttonColor),
                        onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_volume_label)) }, onClick = {
                            showNewVolume = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_synth_label)) }, onClick = {
                            showNewSynthetic = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.full_refresh_label)) }, onClick = {
                            runOnceOrAsk(context, fullUpdate = true)
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.toggle_grid_list)) }, onClick = {
                            runOnIOScope { upsert(vm.subPrefs) { it.prefFeedGridLayout = !it.prefFeedGridLayout } }
                            expanded = false
                        })
                    }
                })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @Composable
    fun EditVolume(volume: Volume, onDismissRequest: () -> Unit) {
        CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.rename_feed_label), color = textColor, style = MaterialTheme.typography.bodyLarge)
                var name by remember { mutableStateOf(volume.name) }
                TextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.rename)) })
                var parent by remember { mutableStateOf<Volume?>(null) }
                var selectedOption by remember {mutableStateOf("")}
                val custom = "Custom"
                val none = "None"
                var selected by remember {mutableStateOf(if (selectedOption == none) none else custom)}
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = none == selected,
                        onCheckedChange = { isChecked ->
                            selected = none
                            parent = null
                        })
                    Text(none)
                    Spacer(Modifier.width(50.dp))
                    Checkbox(checked = custom == selected, onCheckedChange = { isChecked -> selected = custom })
                    Text(custom)
                }
                if (selected == custom) {
                    Logd(TAG, "volumes: ${volumes.size}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (index in volumes.indices) FilterChip(onClick = { parent = volumes[index] }, label = { Text(volumes[index].name) }, selected = parent == volumes[index])
                    }
                }
                Row {
                    Button({ onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) }
                    Spacer(Modifier.weight(1f))
                    Button({
                        upsertBlk(volume) {
                            it.name = name
                            it.parentId = parent?.id ?: -1L
                        }
                        onDismissRequest()
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun LazyList() {
        var selectedSize by remember { mutableIntStateOf(0) }
        val feedsSelected = remember { mutableStateListOf<Feed>() }
        var longPressIndex by remember { mutableIntStateOf(-1) }
        var refreshing by remember { mutableStateOf(false)}

        @SuppressLint("LocalContextResourcesRead")
        fun saveFeed(cbBlock: (Feed)->Unit) {
            runOnIOScope {
                realm.write {
                    for (f_ in feedsSelected) {
                        val f = findLatest(f_)
                        if (f != null) cbBlock(f)
                    }
                }
            }
            val numItems = feedsSelected.size
            Logt(TAG, context.resources.getQuantityString(R.plurals.updated_feeds_batch_label, numItems, numItems))
        }

        var showRemoveFeedDialog by remember { mutableStateOf(false) }
        var showChooseRatingDialog by remember { mutableStateOf(false) }
        var showAssociateDialog by remember { mutableStateOf(false) }
        var showToVolumeDialog by remember { mutableStateOf(false) }
        var showTagsSettingDialog by remember { mutableStateOf(false) }

        val episodesToQueue = remember { mutableStateListOf<Episode>() }
        if (episodesToQueue.isNotEmpty()) PutToQueueDialog(episodesToQueue.toList()) { episodesToQueue.clear() }

        var isFeedsOptionsExpanded by remember { mutableStateOf(false) }
        fun exitSelectMode() {
            isFeedsOptionsExpanded = false
            selectMode = false
        }
        val feedsOptionsMap = remember { linkedMapOf<String, @Composable ()->Unit>(
            "AddToQueue" to { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
            exitSelectMode()
            episodesToQueue.clear()
            val eps = realm.query(Episode::class).query("feedId IN $0", feedsSelected.map { it.id }).limit(200).find()
            if (eps.isNotEmpty()) episodesToQueue.addAll(eps)
        }) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.outline_playlist_add_24), "Add to queue")
            Text(stringResource(id = R.string.add_to_queue) + " (max 200)") } },
            "SetRating" to { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                exitSelectMode()
                showChooseRatingDialog = true
            }) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), "Set rating")
                Text(stringResource(id = R.string.set_rating_label)) } },
            "AssociatedQueue" to { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                showAssociateDialog = true
                exitSelectMode()
            }) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "associated queue")
                Text(stringResource(id = R.string.pref_feed_associated_queue)) } },
            "ParentVolume" to { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                showToVolumeDialog = true
                exitSelectMode()
            }) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.rounded_books_movies_and_music_24), "set parent volume", modifier = Modifier.height(24.dp))
                Text(stringResource(id = R.string.pref_parent_volume)) } },
            "FullSettings" to { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                feedsToSet = feedsSelected
                navController.navigate(Screens.FeedsSettings.name)
                exitSelectMode()
            }) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_settings), "full settings")
                Text(stringResource(id = R.string.full_settings)) } },
            "RemoveFeeds" to { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                showRemoveFeedDialog = true
                exitSelectMode()
            }) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "remove feed")
                Text(stringResource(id = R.string.remove_feed_label)) } },
            "OPMLExport" to { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                exitSelectMode()
                val exportType = ExportTypes.OPML_SELECTED
                val title = String.format(exportType.outputNameTemplate, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                val intentPickAction = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(exportType.contentType)
                    .putExtra(Intent.EXTRA_TITLE, title)
                try {
                    val actMain: MainActivity? = generateSequence(context) { if (it is ContextWrapper) it.baseContext else null }.filterIsInstance<MainActivity>().firstOrNull()
                    actMain?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
                        val uri = result.data!!.data
                        runOnIOScope {
                            try {
                                Logd(TAG, "selectedFeeds: ${feedsSelected.size}")
                                if (uri == null) ExportWorker(OpmlWriter(), context).exportFile(feedsSelected)
                                else {
                                    val worker = DocumentFileExportWorker(OpmlWriter(), context, uri)
                                    worker.exportFile(feedsSelected)
                                }
                            } catch (e: Exception) { Loge(TAG, "exportOPML error: ${e.message}") }
                        }
                    }?.launch(intentPickAction)
                    return@clickable
                } catch (e: ActivityNotFoundException) { Loge(TAG, "No activity found. Should never happen...") }
            }) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_import_export_24), "")
                Text(stringResource(id = R.string.opml_export_label)) } }
            ) }

        @Composable
        fun OpenDialogs() {
            @Composable
            fun SetToVolumeDialog(onDismissRequest: () -> Unit) {
                CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var selectedOption by remember {mutableStateOf("")}
                        val custom = "Custom"
                        val none = "None"
                        var selected by remember {mutableStateOf(if (selectedOption == none) none else custom)}
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = none == selected,
                                onCheckedChange = { isChecked ->
                                    selected = none
                                    saveFeed { it: Feed -> it.volumeId = -1L }
                                    onDismissRequest()
                                })
                            Text(none)
                            Spacer(Modifier.width(50.dp))
                            Checkbox(checked = custom == selected, onCheckedChange = { isChecked -> selected = custom })
                            Text(custom)
                        }
                        if (selected == custom) {
                            Logd(TAG, "volumes: ${volumes.size}")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                for (index in volumes.indices) {
                                    FilterChip(onClick = {
                                        saveFeed { it: Feed -> it.volumeId = volumes[index].id }
                                        onDismissRequest()
                                    }, label = { Text(volumes[index].name) }, selected = false )
                                }
                            }
                        }
                    }
                }
            }

            @Composable
            fun SetAssociateQueueDialog(onDismissRequest: () -> Unit) {
                CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var selectedOption by remember {mutableStateOf("")}
                        val custom = "Custom"
                        val none = "None"
                        var selected by remember {mutableStateOf(if (selectedOption == none) none else custom)}
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = none == selected,
                                onCheckedChange = { isChecked ->
                                    selected = none
                                    saveFeed { it: Feed ->
                                        it.queueId = -2L
                                        it.autoDownload = false
                                        it.autoEnqueue = false
                                    }
                                    onDismissRequest()
                                })
                            Text(none)
                            Spacer(Modifier.width(50.dp))
                            Checkbox(checked = custom == selected, onCheckedChange = { isChecked -> selected = custom })
                            Text(custom)
                        }
                        if (selected == custom) {
                            Logd(TAG, "queues: ${queuesLive.size}")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                for (index in queuesLive.indices) {
                                    FilterChip(onClick = {
                                        saveFeed { it: Feed -> it.queueId = queuesLive[index].id }
                                        onDismissRequest()
                                    }, label = { Text(queuesLive[index].name) }, selected = false )
                                }
                            }
                        }
                    }
                }
            }

            @Composable
            fun ChooseRatingDialog(selected: List<Feed>, onDismissRequest: () -> Unit) {
                CommonPopupCard(onDismissRequest = onDismissRequest) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (rating in Rating.entries.reversed()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp).clickable {
                                runOnIOScope {
                                    realm.write {
                                        for (f_ in selected) {
                                            val f = findLatest(f_)
                                            if (f != null) f.rating = rating.code
                                        }
                                    }
                                }
                                onDismissRequest()
                            }) {
                                Icon(imageVector = ImageVector.vectorResource(id = rating.res), "")
                                Text(rating.name, Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }

            if (showRemoveFeedDialog) RemoveFeedDialog(feedsSelected, onDismissRequest = {showRemoveFeedDialog = false}) {}
            if (showChooseRatingDialog) ChooseRatingDialog(feedsSelected) { showChooseRatingDialog = false }
            if (showAssociateDialog) SetAssociateQueueDialog {showAssociateDialog = false}
            if (showToVolumeDialog) SetToVolumeDialog {showToVolumeDialog = false}
            if (showTagsSettingDialog) TagSettingDialog(TagType.Feed, setOf(), multiples = true, { showTagsSettingDialog = false } ) { tags ->
                runOnIOScope { realm.write { for (f_ in feedsSelected) findLatest(f_)?.tags?.addAll(tags) } }
            }
        }

        OpenDialogs()

        var volumeToOperate by remember { mutableStateOf<Volume?>(null) }

        var showEditVolume by remember { mutableStateOf(false) }
        if (showEditVolume) EditVolume(volumeToOperate!!) { showEditVolume = false }

        @Composable
        fun VolumeOptionsMenu(onDismissRequest: ()->Unit) {
            CommonPopupCard(onDismissRequest = onDismissRequest) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    feedsOptionsMap.entries.forEachIndexed { _, entry -> if (entry.key != "ParentVolume") entry.value() }
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                        showEditVolume = true
                    }) {
                        Icon(imageVector = Icons.Filled.Edit, "edit volume")
                        Text(stringResource(id = R.string.edit_volume)) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                        commonConfirm = CommonConfirmAttrib(
                            title = context.getString(R.string.remove_volume) + "?",
                            message = context.getString(R.string.remove_volume_msg) + "\n" + volumeToOperate?.name,
                            confirmRes = R.string.confirm_label,
                            cancelRes = R.string.cancel_label,
                            onConfirm = {
                                Logd(TAG, "removing volume: ${volumeToOperate?.name}")
                                deleteVolumeTree(volumeToOperate!!)
                                commonConfirm = null
                            },
                        )
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "remove volume")
                        Text(stringResource(id = R.string.remove_volume)) }
                }
            }
        }
        if (volumeToOperate != null) VolumeOptionsMenu { volumeToOperate = null}

        @Composable
        fun FeedsSpeedDial(selected: SnapshotStateList<Feed>, modifier: Modifier = Modifier) {
            val bgColor = MaterialTheme.colorScheme.tertiaryContainer
            val fgColor = remember { complementaryColorOf(bgColor) }
            Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Bottom) {
                if (isFeedsOptionsExpanded) feedsOptionsMap.values.forEachIndexed { _, button ->
                    FloatingActionButton(containerColor = bgColor, contentColor = fgColor,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).height(40.dp), onClick = {}) { button() }
                }
                FloatingActionButton(containerColor = bgColor, contentColor = fgColor,
                    onClick = { isFeedsOptionsExpanded = !isFeedsOptionsExpanded }) { Icon(Icons.Filled.Edit, "Edit") }
            }
        }

        PullToRefreshBox(modifier = Modifier.fillMaxSize(), isRefreshing = refreshing, indicator = {}, onRefresh = {
            refreshing = true
            commonConfirm = CommonConfirmAttrib(
                title = context.getString(R.string.feed_refresh_title) + "?",
                message = "",
                confirmRes = R.string.confirm_label,
                cancelRes = R.string.cancel_label,
                onConfirm = { checkAndscheduleUpdateTaskOnce(context, replace = true, force = true) },
            )
            refreshing = false
        }) {
            if (vm.subPrefs.prefFeedGridLayout) {
                LazyVerticalGrid(state = rememberLazyGridState(), columns = GridCells.Adaptive(80.dp), modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)) {
                    if (volumes.isNotEmpty()) items(volumes.size, key = { i -> volumes[i].id}) { index ->
                        val volume = volumes[index]
                        Column(Modifier.background(MaterialTheme.colorScheme.surface)
                            .combinedClickable(onClick = { vm.curVolume = volume},
                                onLongClick = {
                                    feedsSelected.clear()
                                    feedsSelected.addAll(volume.allFeeds)
                                    volumeToOperate = volume
                            })) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.rounded_books_movies_and_music_24), tint = buttonColor, contentDescription = null, modifier = Modifier.fillMaxWidth())
                            Text(volume.name, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(feeds.size, key = {index -> feeds[index].id}) { index ->
                        val feed by remember { mutableStateOf(feeds[index]) }
                        var isSelected by remember(selectMode, selectedSize, feed.id) { mutableStateOf(selectMode && feed in feedsSelected) }
                        fun toggleSelected() {
                            isSelected = !isSelected
                            if (isSelected) feedsSelected.add(feed)
                            else feedsSelected.remove(feed)
                        }
                        Column(Modifier.background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .combinedClickable(onClick = {
                                Logd(TAG, "clicked: ${feed.title}")
                                if (!feed.isBuilding) {
                                    if (selectMode) toggleSelected()
                                    else navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.id}")
                                }
                            }, onLongClick = {
                                if (!feed.isBuilding) {
                                    selectMode = !selectMode
                                    isSelected = selectMode
                                    feedsSelected.clear()
                                    if (selectMode) {
                                        feedsSelected.add(feed)
                                        longPressIndex = index
                                    } else {
                                        selectedSize = 0
                                        longPressIndex = -1
                                    }
                                }
                                Logd(TAG, "long clicked: ${feed.title}")
                            })) {
                            ConstraintLayout(Modifier.fillMaxSize()) {
                                val (coverImage, episodeCount, rating, error) = createRefs()
                                val img = remember(feed) { ImageRequest.Builder(context).data(feed.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                                AsyncImage(model = img, contentDescription = "coverImage", modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                    .constrainAs(coverImage) {
                                        top.linkTo(parent.top)
                                        bottom.linkTo(parent.bottom)
                                        start.linkTo(parent.start)
                                    })
                                val numEpisodes by remember { mutableStateOf(getEpisodesCount(null, feed.id)) }
                                Text(NumberFormat.getInstance().format(numEpisodes.toLong()), color = buttonAltColor,
                                    modifier = Modifier.background(Color.Gray).constrainAs(episodeCount) {
                                        end.linkTo(parent.end)
                                        top.linkTo(coverImage.top)
                                    })
                                if (feed.rating != Rating.UNRATED.code)
                                    Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = buttonColor, contentDescription = "rating",
                                        modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).constrainAs(rating) {
                                            start.linkTo(coverImage.start)
                                            bottom.linkTo(coverImage.bottom)
                                        })
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
                val listState = rememberLazyListState()
                var restored by remember { mutableStateOf(false) }
                DisposableEffect(LocalLifecycleOwner.current) {
                    onDispose {
                        val index = listState.firstVisibleItemIndex
                        val offset = listState.firstVisibleItemScrollOffset
                        Logd(TAG, "DisposableEffect onDispose save positions: $index $offset")
                        runOnIOScope {
                            upsert(vm.subPrefs) {
                                it.positionIndex = index
                                it.positionOffset = offset
                            }
                        }
                    }
                }

                val currentEntry = navController.navController.currentBackStackEntryAsState().value
                LaunchedEffect(feeds.size) {
                    val cameBack = currentEntry?.savedStateHandle?.get<Boolean>(COME_BACK) ?: false
                    Logd(TAG, "LaunchedEffect(feeds.size) cameBack: $cameBack")
                    if (!restored && feeds.size > 5) {
                        if (!cameBack) {
                            scope.launch {
                                val (index, offset) = Pair(vm.subPrefs.positionIndex, vm.subPrefs.positionOffset)
                                val safeIndex = index.coerceIn(0, feeds.size - 1)
                                val safeOffset = if (safeIndex == index) offset else 0
                                listState.scrollToItem(safeIndex, safeOffset)
                                currentEntry?.savedStateHandle?.remove<Boolean>(COME_BACK)
                            }
                        } else scope.launch { listState.scrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
                        restored = true
                    }
                }
                LaunchedEffect(filterAndSort) {
                    Logd(TAG, "LaunchedEffect(filterSorted) $filterAndSort")
                    if (feeds.isNotEmpty() && filterAndSort) scope.launch { listState.scrollToItem(0) }
                    filterAndSort = false
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (volumes.isNotEmpty()) itemsIndexed(volumes, key = { _, v -> v.id}) { index, volume ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.combinedClickable(
                            onClick = { vm.curVolume = volume },
                            onLongClick = {
                                feedsSelected.clear()
                                feedsSelected.addAll(volume.allFeeds)
                                volumeToOperate = volume
                            }
                            )) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.rounded_books_movies_and_music_24), tint = buttonColor, contentDescription = null, modifier = Modifier.width(50.dp).height(50.dp))
                            Text(volume.name, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically))
                        }
                    }

                    itemsIndexed(feeds, key = { _, feed -> feed.id}) { index, feed_ ->
                        val feed by rememberUpdatedState(feed_)
                        var isSelected by remember(key1 = selectMode, key2 = selectedSize) { mutableStateOf(selectMode && feed in feedsSelected) }
                        fun toggleSelected() {
                            isSelected = !isSelected
                            if (isSelected) feedsSelected.add(feed)
                            else feedsSelected.remove(feed)
                            Logd(TAG, "toggleSelected: selected: ${feedsSelected.size}")
                        }
                        Row(Modifier.background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                            val img = remember(feed) { ImageRequest.Builder(context).data(feed.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                            AsyncImage(model = img, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                                modifier = Modifier.width(80.dp).height(80.dp).clickable(onClick = {
                                    Logd(TAG, "icon clicked!")
                                    if (!feed.isBuilding) {
                                        if (selectMode) toggleSelected()
                                        else navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.id}&modeName=${FeedScreenMode.Info.name}")
                                    }
                                })
                            )
                            Column(Modifier.weight(1f).padding(start = 10.dp).combinedClickable(onClick = {
                                if (!feed.isBuilding) {
                                    if (selectMode) toggleSelected()
                                    else navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.id}")
                                }
                            }, onLongClick = {
                                if (!feed.isBuilding) {
                                    selectMode = !selectMode
                                    isSelected = selectMode
                                    feedsSelected.clear()
                                    if (selectMode) {
                                        feedsSelected.add(feed)
                                        longPressIndex = index
                                    } else {
                                        selectedSize = 0
                                        longPressIndex = -1
                                    }
                                }
                            })) {
                                Row {
                                    if (feed.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = buttonColor, contentDescription = "rating", modifier = Modifier.width(20.dp).height(20.dp).background(MaterialTheme.colorScheme.tertiaryContainer))
                                    Text(feed.title ?: "No title", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Text(feed.author ?: "No author", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.padding(top = 5.dp)) {
                                    val measureString = remember { run {
                                        val numEpisodes = getEpisodesCount(null, feed.id)
                                        NumberFormat.getInstance().format(numEpisodes.toLong()) + " : " + durationInHours(feed.totleDuration/1000, false)
                                    } }
                                    Text(measureString, color = textColor, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(feed.sortInfo, color = textColor, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (feed.lastUpdateFailed) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_error), tint = Color.Red, contentDescription = "error")
                        }
                    }
                }
            }
            if (selectMode) {
                Row(modifier = Modifier.align(Alignment.TopEnd).width(150.dp).height(45.dp).background(MaterialTheme.colorScheme.tertiaryContainer),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_upward_24), tint = buttonColor, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp).clickable(onClick = {
                            feedsSelected.clear()
                            for (i in 0..longPressIndex) feedsSelected.add(feeds[i])
                            selectedSize = feedsSelected.size
                            Logd(TAG, "selectedIds: ${feedsSelected.size}")
                        }))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_downward_24), tint = buttonColor, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp).clickable(onClick = {
                            feedsSelected.clear()
                            for (i in longPressIndex..<feeds.size) feedsSelected.add(feeds[i])
                            selectedSize = feedsSelected.size
                            Logd(TAG, "selectedIds: ${feedsSelected.size}")
                        }))
                    var selectAllRes by remember { mutableIntStateOf(R.drawable.ic_select_all) }
                    Icon(imageVector = ImageVector.vectorResource(selectAllRes), tint = buttonColor, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).clickable(onClick = {
                            if (selectedSize != feeds.size) {
                                feedsSelected.clear()
                                feedsSelected.addAll(feeds)
                                selectAllRes = R.drawable.ic_select_none
                            } else {
                                feedsSelected.clear()
                                longPressIndex = -1
                                selectAllRes = R.drawable.ic_select_all
                            }
                            selectedSize = feedsSelected.size
                            Logd(TAG, "selectedIds: ${feedsSelected.size}")
                        }))
                }
                FeedsSpeedDial(feedsSelected.toMutableStateList(), modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp, start = 16.dp))
            }
        }
    }

    @Composable
    fun OpenDialogs() {
        @Composable
        fun SortDialog(onDismissRequest: () -> Unit) {
            Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
                val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
                dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
                Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, buttonColor)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.sortIndex != FeedSortIndex.Title.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    runOnIOScope {
                                        upsert(vm.subPrefs) {
                                            if (it.sortIndex == FeedSortIndex.Title.ordinal) {
                                                it.titleAscending = !it.titleAscending
                                                it.sortDirCode = if (it.titleAscending) 0 else 1
                                            }
                                            else {
                                                it.sortIndex = FeedSortIndex.Title.ordinal
                                                it.sortProperty = "eigenTitle"
                                            }
                                            it.feedsSorted += 1
                                        }
                                    }
                                }
                            ) { Text(text = stringResource(FeedSortIndex.Title.res) + if (vm.subPrefs.titleAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.sortIndex != FeedSortIndex.Date.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    if (vm.subPrefs.sortIndex == FeedSortIndex.Date.ordinal)
                                        runOnIOScope {
                                            upsert(vm.subPrefs) {
                                                it.dateAscending = !it.dateAscending
                                                it.sortDirCode = if (it.dateAscending) 0 else 1
                                                it.feedsSorted += 1
                                            }
                                        }
                                    else prepareDateSort()
                                }
                            ) { Text(text = stringResource(FeedSortIndex.Date.res) + if (vm.subPrefs.dateAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.sortIndex != FeedSortIndex.Time.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    if (vm.subPrefs.sortIndex == FeedSortIndex.Time.ordinal)
                                        runOnIOScope {
                                            upsert(vm.subPrefs) {
                                                it.timeAscending = !it.timeAscending
                                                it.sortDirCode = if (it.timeAscending) 0 else 1
                                                it.feedsSorted += 1
                                            }
                                        }
                                    else prepareTimeSort()
                                }
                            ) { Text(text = stringResource(FeedSortIndex.Time.res) + if (vm.subPrefs.timeAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.sortIndex != FeedSortIndex.Count.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    if (vm.subPrefs.sortIndex == FeedSortIndex.Count.ordinal)
                                        runOnIOScope {
                                            upsert(vm.subPrefs) {
                                                it.countAscending = !it.countAscending
                                                it.sortDirCode = if (it.countAscending) 0 else 1
                                                it.feedsSorted += 1
                                            }
                                        }
                                    else prepareCountSort()
                                }
                            ) { Text(text = stringResource(FeedSortIndex.Count.res) + if (vm.subPrefs.countAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
                        if (vm.subPrefs.sortIndex == FeedSortIndex.Date.ordinal) {
                            Row {
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.dateSortIndex != FeedDateSortIndex.Played.ordinal) buttonColor else buttonAltColor),
                                    onClick = { if (vm.subPrefs.dateSortIndex != FeedDateSortIndex.Played.ordinal) prepareDateSort(FeedDateSortIndex.Played) }
                                ) { Text(stringResource(FeedDateSortIndex.Played.res)) }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.dateSortIndex != FeedDateSortIndex.Downloaded.ordinal) buttonColor else buttonAltColor),
                                    onClick = { if (vm.subPrefs.dateSortIndex != FeedDateSortIndex.Downloaded.ordinal) prepareDateSort(FeedDateSortIndex.Downloaded) }
                                ) { Text(stringResource(FeedDateSortIndex.Downloaded.res)) }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.dateSortIndex != FeedDateSortIndex.Commented.ordinal) buttonColor else buttonAltColor),
                                    onClick = { if (vm.subPrefs.dateSortIndex != FeedDateSortIndex.Commented.ordinal) prepareDateSort(FeedDateSortIndex.Commented) }
                                ) { Text(stringResource(FeedDateSortIndex.Commented.res)) }
                            }
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.dateSortIndex != FeedDateSortIndex.Publish.ordinal) buttonColor else buttonAltColor),
                                onClick = { if (vm.subPrefs.dateSortIndex != FeedDateSortIndex.Publish.ordinal)  prepareDateSort(FeedDateSortIndex.Publish) }
                            ) { Text(stringResource(FeedDateSortIndex.Publish.res)) }
                        } else if (vm.subPrefs.sortIndex == FeedSortIndex.Time.ordinal) {
                            Row {
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.timeSortIndex != FeedTimeSortIndex.Min.ordinal) buttonColor else buttonAltColor),
                                    onClick = { if (vm.subPrefs.timeSortIndex != FeedTimeSortIndex.Min.ordinal) prepareTimeSort(FeedTimeSortIndex.Min) }
                                ) { Text(stringResource(R.string.min_duration)) }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.timeSortIndex != FeedTimeSortIndex.Max.ordinal) buttonColor else buttonAltColor),
                                    onClick = { if (vm.subPrefs.timeSortIndex != FeedTimeSortIndex.Max.ordinal) prepareTimeSort(FeedTimeSortIndex.Max) }
                                ) { Text(stringResource(R.string.max_duration)) }
                            }
                            Row {
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.timeSortIndex != FeedTimeSortIndex.Total.ordinal) buttonColor else buttonAltColor),
                                    onClick = { if (vm.subPrefs.timeSortIndex != FeedTimeSortIndex.Total.ordinal) prepareTimeSort(FeedTimeSortIndex.Total) }
                                ) { Text(stringResource(R.string.total_duration)) }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.subPrefs.timeSortIndex != FeedTimeSortIndex.Average.ordinal) buttonColor else buttonAltColor),
                                    onClick = { if (vm.subPrefs.timeSortIndex != FeedTimeSortIndex.Average.ordinal) prepareTimeSort(FeedTimeSortIndex.Average) }
                                ) { Text(stringResource(R.string.average_duration)) }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
                        Column(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                            if (vm.subPrefs.sortIndex == FeedSortIndex.Count.ordinal) {
                                Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    val item = EpisodeFilter.EpisodesFilterGroup.DOWNLOADED
                                    Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(end = 10.dp))
                                    Spacer(Modifier.weight(0.3f))
                                    fun persistDLSort(i: Int) {
                                        runOnIOScope {
                                            val downlaodedSortIndex = if (vm.subPrefs.downlaodedSortIndex != i) i else  -1
                                            upsert(vm.subPrefs) { it.downlaodedSortIndex = downlaodedSortIndex }
                                            downloadedQuery = when (downlaodedSortIndex) {
                                                0 -> " downloaded == true "
                                                1 -> " downloaded == false "
                                                else -> ""
                                            }
                                            prepareCountSort()
                                        }
                                    }
                                    OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.subPrefs.downlaodedSortIndex != 0) buttonColor else buttonAltColor),
                                        onClick = { persistDLSort(0) },
                                    ) { Text(text = stringResource(item.properties[0].displayName), color = textColor) }
                                    Spacer(Modifier.weight(0.1f))
                                    OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.subPrefs.downlaodedSortIndex != 1) buttonColor else buttonAltColor),
                                        onClick = { persistDLSort(1) },
                                    ) { Text(text = stringResource(item.properties[1].displayName), color = textColor) }
                                    Spacer(Modifier.weight(0.5f))
                                }
                                Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    val item = EpisodeFilter.EpisodesFilterGroup.OPINION
                                    Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(end = 10.dp))
                                    Spacer(Modifier.weight(0.3f))
                                    fun persistCommentSort(i: Int) {
                                        runOnIOScope {
                                            val commentedSortIndex = if (vm.subPrefs.commentedSortIndex != i) i else -1
                                            upsert(vm.subPrefs) { it.commentedSortIndex = commentedSortIndex }
                                            commentedQuery = when (vm.subPrefs.commentedSortIndex) {
                                                0 -> " comment != '' "
                                                1 -> " comment == '' "
                                                else -> ""
                                            }
                                            prepareCountSort()
                                        }
                                    }
                                    OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.subPrefs.commentedSortIndex != 0) buttonColor else buttonAltColor),
                                        onClick = { persistCommentSort(0) },
                                    ) { Text(text = stringResource(item.properties[0].displayName), color = textColor) }
                                    Spacer(Modifier.weight(0.1f))
                                    OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.subPrefs.commentedSortIndex != 1) buttonColor else buttonAltColor),
                                        onClick = { persistCommentSort(1) },
                                    ) { Text(text = stringResource(item.properties[1].displayName), color = textColor) }
                                    Spacer(Modifier.weight(0.5f))
                                }
                            }
                            val episodeStateSort = remember { MutableList(EpisodeState.entries.size) { mutableStateOf(false) } }
                            val ratingSort = remember { MutableList(Rating.entries.size) { mutableStateOf(false) } }
                            if ((vm.subPrefs.sortIndex == FeedSortIndex.Date.ordinal && vm.subPrefs.dateSortIndex == FeedDateSortIndex.Publish.ordinal) || vm.subPrefs.sortIndex == FeedSortIndex.Count.ordinal) {
                                var allOrNone by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    for (i in episodeStateSort.indices) {
                                        if (EpisodeState.entries[i].code.toString() in vm.subPrefs.playStateCodeSet) episodeStateSort[i].value = true
                                    }
                                    val c = episodeStateSort.count { it.value }
                                    allOrNone = c == 0 || c == episodeStateSort.size
                                }
                                fun persistStateSort() {
                                    runOnIOScope {
                                        val sb = StringBuilder()
                                        val playStateCodeSet = mutableSetOf<String>()
                                        for (i in episodeStateSort.indices) {
                                            if (episodeStateSort[i].value) {
                                                playStateCodeSet.add(EpisodeState.entries[i].code.toString())
                                                if (sb.isNotEmpty()) sb.append(" OR ")
                                                sb.append(" playState == ${EpisodeState.entries[i].code} ")
                                            }
                                        }
                                        playStateQueries = sb.toString()
                                        upsert(vm.subPrefs) {
                                            it.playStateCodeSet.clear()
                                            it.playStateCodeSet.addAll(playStateCodeSet)
                                        }
                                        when (vm.subPrefs.sortIndex) {
                                            FeedSortIndex.Date.ordinal -> prepareDateSort()
                                            FeedSortIndex.Count.ordinal -> prepareCountSort()
                                            else -> {}
                                        }
                                    }
                                }
                                val item = EpisodeFilter.EpisodesFilterGroup.PLAY_STATE
                                var expandRow by remember { mutableStateOf(false) }
                                Row {
                                    Text(stringResource(item.nameRes) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = if (allOrNone) buttonColor else buttonAltColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                    if (expandRow) {
                                        var lowerSelected by remember { mutableStateOf(false) }
                                        var higherSelected by remember { mutableStateOf(false) }
                                        Spacer(Modifier.weight(1f))
                                        Text("<<<", color = if (lowerSelected) buttonAltColor else buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                runOnIOScope {
                                                    val hIndex = episodeStateSort.indexOfLast { it.value }
                                                    if (hIndex < 0) return@runOnIOScope
                                                    if (!lowerSelected) {
                                                        for (i in 0..hIndex) episodeStateSort[i].value = true
                                                    } else {
                                                        for (i in 0..hIndex) episodeStateSort[i].value = false
                                                        episodeStateSort[hIndex].value = true
                                                    }
                                                    val c = episodeStateSort.count { it.value }
                                                    allOrNone = c == 0 || c == episodeStateSort.size
                                                    lowerSelected = !lowerSelected
                                                    persistStateSort()
                                                }
                                            })
                                        Spacer(Modifier.weight(1f))
                                        Text("A", color = buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                runOnIOScope {
                                                    val selectAll = !(lowerSelected && higherSelected)
                                                    lowerSelected = selectAll
                                                    higherSelected = selectAll
                                                    for (i in item.properties.indices) episodeStateSort[i].value = selectAll
                                                    val c = episodeStateSort.count { it.value }
                                                    allOrNone = c == 0 || c == episodeStateSort.size
                                                    persistStateSort()
                                                }
                                            })
                                        Spacer(Modifier.weight(1f))
                                        Text(">>>", color = if (higherSelected) buttonAltColor else buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                runOnIOScope {
                                                    val lIndex = episodeStateSort.indexOfFirst { it.value }
                                                    if (lIndex < 0) return@runOnIOScope
                                                    if (!higherSelected) {
                                                        for (i in lIndex..<item.properties.size) episodeStateSort[i].value = true
                                                    } else {
                                                        for (i in lIndex..<item.properties.size) episodeStateSort[i].value = false
                                                        episodeStateSort[lIndex].value = true
                                                    }
                                                    val c = episodeStateSort.count { it.value }
                                                    allOrNone = c == 0 || c == episodeStateSort.size
                                                    higherSelected = !higherSelected
                                                    persistStateSort()
                                                }
                                            })
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                                if (expandRow) ScrollRowGrid(columns = 3, itemCount = item.properties.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (episodeStateSort[index].value) buttonAltColor else buttonColor),
                                        onClick = {
                                            episodeStateSort[index].value = !episodeStateSort[index].value
                                            val c = episodeStateSort.count { it.value }
                                            allOrNone = c == 0 || c == episodeStateSort.size
                                            persistStateSort()
                                        },
                                    ) { Text(text = stringResource(item.properties[index].displayName), maxLines = 1, color = textColor) }
                                }
                            }
                            if (vm.subPrefs.sortIndex == FeedSortIndex.Count.ordinal) {
                                var allOrNone by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    for (i in ratingSort.indices) {
                                        if (Rating.entries[i].code.toString() in vm.subPrefs.ratingCodeSet) ratingSort[i].value = true
                                    }
                                    val c = ratingSort.count { it.value }
                                    allOrNone = c == 0 || c == ratingSort.size
                                }
                                fun persistRatingSort() {
                                    runOnIOScope {
                                        val sb = StringBuilder()
                                        val ratingCodeSet = mutableSetOf<String>()
                                        for (i in ratingSort.indices) {
                                            if (ratingSort[i].value) {
                                                ratingCodeSet.add(Rating.entries[i].code.toString())
                                                if (sb.isNotEmpty()) sb.append(" OR ")
                                                sb.append(" rating == ${Rating.entries[i].code} ")
                                            }
                                        }
                                        ratingQueries = sb.toString()
                                        upsert(vm.subPrefs) {
                                            it.ratingCodeSet.clear()
                                            it.ratingCodeSet.addAll(ratingCodeSet)
                                        }
                                        when (vm.subPrefs.sortIndex) {
                                            FeedSortIndex.Count.ordinal -> prepareCountSort()
                                            else -> {}
                                        }
                                    }
                                }
                                val item = EpisodeFilter.EpisodesFilterGroup.RATING
                                var expandRow by remember { mutableStateOf(false) }
                                Row {
                                    Text(stringResource(item.nameRes) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = if (allOrNone) buttonColor else buttonAltColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                    if (expandRow) {
                                        var lowerSelected by remember { mutableStateOf(false) }
                                        var higherSelected by remember { mutableStateOf(false) }
                                        Spacer(Modifier.weight(1f))
                                        Text("<<<", color = if (lowerSelected) buttonAltColor else buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                runOnIOScope {
                                                    val hIndex = ratingSort.indexOfLast { it.value }
                                                    if (hIndex < 0) return@runOnIOScope
                                                    if (!lowerSelected) {
                                                        for (i in 0..hIndex) ratingSort[i].value = true
                                                    } else {
                                                        for (i in 0..hIndex) ratingSort[i].value = false
                                                        ratingSort[hIndex].value = true
                                                    }
                                                    val c = ratingSort.count { it.value }
                                                    allOrNone = c == 0 || c == ratingSort.size
                                                    lowerSelected = !lowerSelected
                                                    persistRatingSort()
                                                }
                                            })
                                        Spacer(Modifier.weight(1f))
                                        Text("A", color = buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                runOnIOScope {
                                                    val selectAll = !(lowerSelected && higherSelected)
                                                    lowerSelected = selectAll
                                                    higherSelected = selectAll
                                                    for (i in item.properties.indices) ratingSort[i].value = selectAll
                                                    val c = ratingSort.count { it.value }
                                                    allOrNone = c == 0 || c == ratingSort.size
                                                    persistRatingSort()
                                                }
                                            })
                                        Spacer(Modifier.weight(1f))
                                        Text(">>>", color = if (higherSelected) buttonAltColor else buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                runOnIOScope {
                                                    val lIndex = ratingSort.indexOfFirst { it.value }
                                                    if (lIndex >= 0) {
                                                        if (!higherSelected) {
                                                            for (i in lIndex..<item.properties.size) ratingSort[i].value = true
                                                        } else {
                                                            for (i in lIndex..<item.properties.size) ratingSort[i].value = false
                                                            ratingSort[lIndex].value = true
                                                        }
                                                        val c = ratingSort.count { it.value }
                                                        allOrNone = c == 0 || c == ratingSort.size
                                                        higherSelected = !higherSelected
                                                        persistRatingSort()
                                                    }
                                                }
                                            })
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                                if (expandRow) ScrollRowGrid(columns = 3, itemCount = item.properties.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (ratingSort[index].value) buttonAltColor else buttonColor),
                                        onClick = {
                                            ratingSort[index].value = !ratingSort[index].value
                                            val c = ratingSort.count { it.value }
                                            allOrNone = c == 0 || c == ratingSort.size
                                            persistRatingSort()
                                        },
                                    ) { Text(text = stringResource(item.properties[index].displayName), maxLines = 1, color = textColor) }
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
            var reset by remember { mutableIntStateOf(0) }
            var langFull by remember(vm.subPrefs.langsSel.size) { mutableStateOf(vm.subPrefs.langsSel.size == appAttribs.langSet.size) }
            var tagsFull by remember(vm.subPrefs.tagsSel.size) { mutableStateOf(vm.subPrefs.tagsSel.size == appAttribs.feedTagSet.size) }
            var queuesFull by remember(vm.subPrefs.queueSelIds.size) { mutableStateOf(vm.subPrefs.queueSelIds.size == vm.queueNames.size) }
            fun onFilterChanged(newFilterValues: Set<String>) {
                runOnIOScope {
                    upsert(vm.subPrefs) {
                        it.feedsFilter = StringUtils.join(newFilterValues, ",")
                        it.feedsFiltered++
                    }
                }
                Logd(TAG, "onFilterChanged: ${vm.subPrefs.feedsFilter}")
            }
            Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
                val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
                dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
                Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, buttonColor)) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Logd(TAG, "appAttribs.langSet: ${appAttribs.langSet.size}")
                            if (appAttribs.langSet.isNotEmpty()) {
                                val langs = remember(appAttribs.langSet) { appAttribs.langSet.toList().sorted().toMutableStateList() }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val selectedList = remember { MutableList(langs.size) { mutableStateOf(false) } }
                                    LaunchedEffect(reset) {
                                        Logd(TAG, "LaunchedEffect(reset) lang")
                                        for (index in selectedList.indices) {
                                            if (langs[index] in vm.subPrefs.langsSel) selectedList[index].value = true
                                            langFull = selectedList.count { it.value } == selectedList.size
                                        }
                                    }
                                    var expandRow by remember { mutableStateOf(false) }
                                    Row(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                                        Text(stringResource(R.string.languages) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = if (langFull) buttonColor else buttonAltColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                        if (expandRow) {
                                            val cb = {
                                                runOnIOScope {
                                                    val langsSel = mutableSetOf<String>()
                                                    for (i in langs.indices) if (selectedList[i].value) langsSel.add(langs[i])
                                                    upsert(vm.subPrefs) {
                                                        it.langsSel = langsSel.toRealmSet()
                                                        it.feedsFiltered += 1
                                                    }
                                                }
                                                Logd(TAG, "langsSel: ${vm.subPrefs.langsSel.size} ${langs.size}")
                                            }
                                            SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                        }
                                    }
                                    if (expandRow) ScrollRowGrid(columns = 3, itemCount = langs.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                        OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                            onClick = {
                                                selectedList[index].value = !selectedList[index].value
                                                runOnIOScope {
                                                    val langsSel = vm.subPrefs.langsSel.toMutableSet()
                                                    if (selectedList[index].value) langsSel.add(langs[index])
                                                    else langsSel.remove(langs[index])
                                                    upsert(vm.subPrefs) {
                                                        it.langsSel = langsSel.toRealmSet()
                                                        it.feedsFiltered += 1
                                                    }
                                                }
                                            },
                                        ) { Text(text = langs[index], maxLines = 1, color = textColor) }
                                    }
                                }
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val selectedList = remember { MutableList(vm.queueNames.size) { mutableStateOf(false) } }
                                LaunchedEffect(reset) {
                                    Logd(TAG, "LaunchedEffect(reset) queue")
                                    for (index in selectedList.indices) {
                                        if (vm.queueIds[index] in vm.subPrefs.queueSelIds) selectedList[index].value = true
                                        queuesFull = selectedList.count { it.value } == selectedList.size
                                    }
                                }
                                var expandRow by remember { mutableStateOf(false) }
                                Row(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                                    Text(stringResource(R.string.queue_label) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = if (queuesFull) buttonColor else buttonAltColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                    if (expandRow) {
                                        val cb = {
                                            runOnIOScope {
                                                val qSelIds = mutableSetOf<Long>()
                                                for (i in vm.queueNames.indices) if (selectedList[i].value) qSelIds.add(vm.queueIds[i])
                                                upsert(vm.subPrefs) {
                                                    it.queueSelIds = qSelIds.toRealmSet()
                                                    it.feedsFiltered += 1
                                                }
                                            }
                                            Unit
                                        }
                                        SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                    }
                                }
                                if (expandRow) ScrollRowGrid(columns = 3, itemCount = vm.queueNames.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                        onClick = {
                                            selectedList[index].value = !selectedList[index].value
                                            runOnIOScope {
                                                val qSelIds = vm.subPrefs.queueSelIds.toMutableSet()
                                                if (selectedList[index].value) qSelIds.add(vm.queueIds[index])
                                                else qSelIds.remove(vm.queueIds[index])
                                                upsert(vm.subPrefs) {
                                                    it.queueSelIds = qSelIds.toRealmSet()
                                                    it.feedsFiltered += 1
                                                }
                                            }
                                        },
                                    ) { Text(text = vm.queueNames[index], maxLines = 1, color = textColor) }
                                }
                            }
                            if (appAttribs.feedTagSet.isNotEmpty()) {
                                val tagList = remember(appAttribs.feedTagSet) { appAttribs.feedTagSet.toList().sorted().toMutableStateList() }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val selectedList = remember { MutableList(tagList.size) { mutableStateOf(false) } }
                                    LaunchedEffect(reset) {
                                        Logd(TAG, "LaunchedEffect(reset) tag")
                                        for (index in selectedList.indices) {
                                            if (tagList[index] in vm.subPrefs.tagsSel) selectedList[index].value = true
                                            tagsFull = selectedList.count { it.value } == selectedList.size
                                        }
                                    }
                                    var expandRow by remember { mutableStateOf(false) }
                                    Row(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                                        Text(stringResource(R.string.tags_label) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = if (tagsFull) buttonColor else buttonAltColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                        if (expandRow) {
                                            val cb = {
                                                runOnIOScope {
                                                    val tagsSel = mutableSetOf<String>()
                                                    for (i in tagList.indices) if (selectedList[i].value) tagsSel.add(tagList[i])
                                                    upsert(vm.subPrefs) {
                                                        it.tagsSel = tagsSel.toRealmSet()
                                                        it.feedsFiltered += 1
                                                    }
                                                }
                                                Unit
                                            }
                                            SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                        }
                                    }
                                    if (expandRow) ScrollRowGrid(columns = 3, itemCount = tagList.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                        OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                            onClick = {
                                                selectedList[index].value = !selectedList[index].value
                                                runOnIOScope {
                                                    val tagsSel = vm.subPrefs.tagsSel.toMutableSet()
                                                    if (selectedList[index].value) tagsSel.add(tagList[index])
                                                    else tagsSel.remove(tagList[index])
                                                    upsert(vm.subPrefs) {
                                                        it.tagsSel = tagsSel.toRealmSet()
                                                        it.feedsFiltered += 1
                                                    }
                                                }
                                            },
                                        ) { Text(text = tagList[index], maxLines = 1, color = textColor) }
                                    }
                                }
                            }
                            var selectNone by remember { mutableStateOf(false) }
                            for (item in FeedFilter.FeedFilterGroup.entries) {
                                if (item.values.size == 2) {
                                    Row(modifier = Modifier.padding(start = 5.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Absolute.Left, verticalAlignment = Alignment.CenterVertically) {
                                        var selectedIndex by remember(selectNone) { mutableIntStateOf(
                                            if (selectNone) -1
                                            else if (filter != null) {
                                                if (item.values[0].filterId in filter.properties) 0
                                                else if (item.values[1].filterId in filter.properties) 1
                                                else -1
                                            } else -1
                                        ) }
                                        Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = textColor, modifier = Modifier.padding(end = 10.dp))
                                        Spacer(Modifier.width(30.dp))
                                        OutlinedButton(
                                            modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp), border = BorderStroke(2.dp, if (selectedIndex != 0) buttonColor else buttonAltColor),
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
                                        Spacer(Modifier.width(20.dp))
                                        OutlinedButton(
                                            modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp), border = BorderStroke(2.dp, if (selectedIndex != 1) buttonColor else buttonAltColor),
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
                                        ) { Text(text = stringResource(item.values[1].displayName), color = textColor) } //                                    Spacer(Modifier.weight(0.5f))
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        val selectedList = remember { MutableList(item.values.size) { mutableStateOf(false) } }
                                        var allOrNone by remember { mutableStateOf(false) }
                                        LaunchedEffect(reset) {
                                            Logd(TAG, "LaunchedEffect(reset) filter")
                                            if (filter != null) {
                                                for (index in selectedList.indices) {
                                                    if (item.values[index].filterId in filter.properties) selectedList[index].value = true
                                                }
                                                val c = selectedList.count { it.value }
                                                allOrNone = c == 0 || c == item.values.size
                                            } else allOrNone = true
                                        }
                                        var expandRow by remember { mutableStateOf(false) }
                                        Row(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                                            Text(stringResource(item.nameRes) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = if (allOrNone) buttonColor else buttonAltColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                            if (expandRow) {
                                                val cb = {
                                                    for (i in item.values.indices) {
                                                        if (selectedList[i].value) filterValues.add(item.values[i].filterId)
                                                        else filterValues.remove(item.values[i].filterId)
                                                    }
                                                    val c = selectedList.count { it.value }
                                                    allOrNone = c == 0 || c == item.values.size
                                                    onFilterChanged(filterValues)
                                                }
                                                SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                            }
                                        }
                                        if (expandRow) ScrollRowGrid(columns = 3, itemCount = item.values.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                            if (selectNone) selectedList[index].value = false
                                            OutlinedButton(
                                                modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                                onClick = {
                                                    selectNone = false
                                                    selectedList[index].value = !selectedList[index].value
                                                    if (selectedList[index].value) filterValues.add(item.values[index].filterId)
                                                    else filterValues.remove(item.values[index].filterId)
                                                    val c = selectedList.count { it.value }
                                                    allOrNone = c == 0 || c == item.values.size
                                                    onFilterChanged(filterValues)
                                                },
                                            ) { Text(text = stringResource(item.values[index].displayName), maxLines = 1, color = textColor) }
                                        }
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Spacer(Modifier.weight(0.3f))
                                Button(onClick = {
                                    runOnIOScope {
                                        upsert(vm.subPrefs) {
                                            it.tagsSel = appAttribs.feedTagSet.toRealmSet()
                                            it.queueSelIds = vm.queueIds.toRealmSet()
                                            it.langsSel = appAttribs.langSet.toRealmSet()
                                            it.feedsFiltered += 1
                                        }
                                    }
                                    selectNone = true
                                    reset++
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
        }

        @Composable
        fun CreateVolume(parent: Volume?, onDismissRequest: () -> Unit) {
            CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                val textColor = MaterialTheme.colorScheme.onSurface
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.rename_feed_label), color = textColor, style = MaterialTheme.typography.bodyLarge)
                    var name by remember { mutableStateOf("") }
                    TextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.new_namee)) })
                    Row {
                        Button({ onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) }
                        Spacer(Modifier.weight(1f))
                        Button({
                            val v = Volume()
                            v.id = System.currentTimeMillis()
                            v
                            upsertBlk(v) {
                                it.name = name
                                it.parentId = parent?.id ?: -1L
                            }
                            onDismissRequest()
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }

        if (showFilterDialog) {
            filterAndSort = true
            FilterDialog(FeedFilter(vm.subPrefs.feedsFilter)) {
                filterAndSort = false
                showFilterDialog = false
            }
        }
        if (showSortDialog) {
            filterAndSort = true
            SortDialog {
                filterAndSort = false
                showSortDialog = false
            }
        }
        if (showNewSynthetic) RenameOrCreateSyntheticFeed { showNewSynthetic = false }
        if (showNewVolume) CreateVolume(parent = vm.curVolume) { showNewVolume = false }
    }

    OpenDialogs()

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            LazyList()
        }
    }
}

enum class FeedSortIndex(val res: Int) {
    Title(R.string.title),
    Date(R.string.date),
    Time(R.string.time),
    Count(R.string.count)
}
enum class FeedDateSortIndex(val res: Int) {
    Publish(R.string.publish_date),
    Downloaded(R.string.downloaded_label),
    Played(R.string.played),
    Commented(R.string.commented)
}

enum class FeedTimeSortIndex(val res: Int) {
    Total(R.string.total_duration),
    Min(R.string.min_duration),
    Max(R.string.max_duration),
    Average(R.string.average_duration)
}