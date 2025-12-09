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
import ac.mdiq.podcini.storage.database.feedOperationText
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.AutoDeleteAction
import ac.mdiq.podcini.storage.model.Feed.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.SubscriptionsPrefs
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.FeedFilter
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.durationInHours
import ac.mdiq.podcini.storage.utils.getDurationStringLong
import ac.mdiq.podcini.storage.utils.getDurationStringShort
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CommonDialogSurface
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.PlaybackSpeedDialog
import ac.mdiq.podcini.ui.compose.RemoveFeedDialog
import ac.mdiq.podcini.ui.compose.RenameOrCreateSyntheticFeed
import ac.mdiq.podcini.ui.compose.ScrollRowGrid
import ac.mdiq.podcini.ui.compose.SelectLowerAllUpper
import ac.mdiq.podcini.ui.compose.SimpleSwitchDialog
import ac.mdiq.podcini.ui.compose.SpinnerExternalSet
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.TagType
import ac.mdiq.podcini.ui.compose.commonConfirm
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.xilinjia.krdb.ext.toRealmSet
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.query.Sort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SubscriptionsScreen"

@Composable
fun SubscriptionsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)

    var prefsFlow by remember { mutableStateOf<Flow<SingleQueryChange<SubscriptionsPrefs>>>(emptyFlow()) }

    val queueNames = remember { mutableStateListOf<String>() }
    val queueIds = remember { mutableListOf<Long>() }

    //    TODO: currently not used
    var displayedFolder by remember { mutableStateOf("") }

    val episodeStateSort = remember { MutableList(EpisodeState.entries.size) { mutableStateOf(false) } }
    val ratingSort = remember { MutableList(Rating.entries.size) { mutableStateOf(false) } }

    var feedsFlow by remember { mutableStateOf<Flow<ResultsChange<Feed>>>(emptyFlow()) }

    var filterSorted by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showNewSynthetic by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf(false) }

    val prefsChange by prefsFlow.collectAsState(initial = null)
    val subPrefs = prefsChange?.obj ?: realm.query(SubscriptionsPrefs::class).first().find() ?: SubscriptionsPrefs()
    var prefsReset by remember { mutableStateOf(false) }

    val feedsChange by feedsFlow.collectAsState(initial = null)
    val feeds = feedsChange?.list ?: emptyList()

    fun languagesQS() : String {
        var qrs  = ""
        when {
            subPrefs.langsSel.isEmpty() -> qrs = " (languages.@count > 0) "
            subPrefs.langsSel.size == appAttribs.languages.size -> qrs = ""
            else -> {
                for (l in subPrefs.langsSel) {
                    qrs += if (qrs.isEmpty()) " ( ANY languages == '$l' " else " OR ANY languages == '$l' "
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
            subPrefs.tagsSel.isEmpty() -> qrs = " (tags.@count == 0 OR (tags.@count != 0 AND ALL tags == '#root' )) "
            subPrefs.tagsSel.size == appAttribs.feedTags.size -> qrs = ""
            else -> {
                for (t in subPrefs.tagsSel) {
                    qrs += if (qrs.isEmpty()) " ( ANY tags == '$t' " else " OR ANY tags == '$t' "
                }
                if (qrs.isNotEmpty()) qrs += " ) "
            }
        }
        Logd(TAG, "tagsQS: $qrs")
        return qrs
    }
    fun queuesQS() : String {
        val qSelIds_ = subPrefs.queueSelIds.toMutableSet()
        if (qSelIds_.isEmpty()) qSelIds_.add(-2)
        else {
            if ((queueIds - qSelIds_).isEmpty()) qSelIds_.clear()
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

    val langsQueryStr by remember(subPrefs.langsSel.size) { mutableStateOf(languagesQS()) }
    val tagsQueryStr by remember(subPrefs.tagsSel.size) { mutableStateOf(tagsQS()) }
    val queuesQueryStr by remember(subPrefs.queueSelIds.size) { mutableStateOf(queuesQS()) }
    val filterQueryStr by remember(subPrefs.feedsFilter) { mutableStateOf(FeedFilter(subPrefs.feedsFilter).queryString()) }

    fun buildFlow() {
        Logd(TAG, "buildFlow")
        fun feedFetchQString(): String {
            var fQueryStr = filterQueryStr
            if (langsQueryStr.isNotEmpty())  fQueryStr += " AND $langsQueryStr"
            if (tagsQueryStr.isNotEmpty())  fQueryStr += " AND $tagsQueryStr"
            if (queuesQueryStr.isNotEmpty())  fQueryStr += " AND $queuesQueryStr"
            return fQueryStr
        }
        val fetchQS = feedFetchQString()
        val sortPair = Pair(subPrefs.sortProperty.ifBlank { "eigenTitle" }, if (subPrefs.sortDirCode == 0) Sort.ASCENDING else Sort.DESCENDING)
        Logd(TAG, "fetchQS: $fetchQS ${sortPair.first} ${sortPair.second.name}")
        feedsFlow = realm.query(Feed::class).query(fetchQS).sort(sortPair).asFlow()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect Lifecycle.Event: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    if (!prefsReset) {
                        Logd(TAG, "prefsReset: $prefsReset")
                        prefsReset = true
                        runOnIOScope {
                            upsert(subPrefs) {
                                it.feedsSorted = 0
                                it.feedsFiltered = 0
                            }
                        }
                    }
                    prefsFlow = realm.query(SubscriptionsPrefs::class).first().asFlow()
                    val queues = realm.query(PlayQueue::class).find()
                    queueIds.addAll(queues.map { it.id })
                    queueNames.addAll(queues.map { it.name })
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

    var playStateQueries by remember { mutableStateOf("") }
    var ratingQueries by remember { mutableStateOf("") }

    val downloadedQuery by remember(subPrefs.downlaodedSortIndex) { mutableStateOf(if (subPrefs.downlaodedSortIndex == 0) " downloaded == true " else if (subPrefs.downlaodedSortIndex == 1) " downloaded == false " else "") }
    val commentedQuery by remember(subPrefs.commentedSortIndex) { mutableStateOf( if (subPrefs.commentedSortIndex == 0) " comment != '' " else if (subPrefs.commentedSortIndex == 1) " comment == '' " else "") }

    fun prepareDateSort(subIndex: FeedDateSortIndex? = null) {
        val subIndexOrdinal = subIndex?.ordinal ?: subPrefs.dateSortIndex
        Logd(TAG, "prepareDateSort")
        suspend fun persistDateSort() {
            upsert(subPrefs) {
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
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.pubDate ?: 0L
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
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.downloadTime ?: 0L
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
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.lastPlayedTime ?: 0L
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
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.commentTime ?: 0L
                            f.sortValue = d
                            f.sortInfo = "C: ${formatDateTimeFlex(Date(d))}"
                        }
                    }
                    Logd(TAG, "prepareSort queryString: $queryString")
                    persistDateSort()
                }
                else -> Loge(TAG, "No such date sorting ${subPrefs.dateSortIndex}")
            }
        }
    }

    fun prepareTimeSort(subIndex: FeedTimeSortIndex? = null) {
        val subIndexOrdinal = subIndex?.ordinal ?: subPrefs.timeSortIndex
        Logd(TAG, "prepareTimeSort")
        suspend fun persistTimeSort() {
            upsert(subPrefs) {
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
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.duration?.toLong() ?: 0L
                            f.sortValue = d
                            f.sortInfo = "Min D: ${getDurationStringLong(d.toInt())}"
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
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.duration?.toLong() ?: 0L
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
                            val ln = f.episodes.size
                            val aveDur = if (ln > 0) f.totleDuration/ln else 0
                            f.sortValue = aveDur
                            f.sortInfo = "Ave D: ${getDurationStringShort(aveDur, true)}"
                        }
                    }
                    persistTimeSort()
                }
                else -> Loge(TAG, "No such time sorting ${subPrefs.timeSortIndex}")
            }
        }
    }

    fun prepareCountSort() {
        Logd(TAG, "prepareCountSort")
        runOnIOScope {
            var queryString = "feedId == $0"
            if (playStateQueries.isNotEmpty()) queryString += " AND ($playStateQueries)"
            if (ratingQueries.isNotEmpty()) queryString += " AND ($ratingQueries)"
            if (downloadedQuery.isNotEmpty()) queryString += " AND ($downloadedQuery)"
            if (commentedQuery.isNotEmpty()) queryString += " AND ($commentedQuery)"
            Logd(TAG, "prepareSort queryString: $queryString")
            realm.write {
                for (f_ in feeds) {
                    val f = findLatest(f_) ?: continue
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    f.sortValue = c
                    f.sortInfo = "$c counts"
                }
            }
            upsert(subPrefs) {
                it.sortIndex = FeedSortIndex.Count.ordinal
                it.sortProperty = "sortValue"
//                it.countAscending = !it.countAscending
                it.feedsSorted++
            }
        }
    }

    LaunchedEffect(subPrefs.playStateCodeSet) {
        playStateQueries = ""
        Logd(TAG, "LaunchedEffect subPrefs.playStateCodeSet")
        for (i in episodeStateSort.indices) episodeStateSort[i].value = false
        for (c in subPrefs.playStateCodeSet) episodeStateSort[EpisodeState.fromCode(c.toInt()).ordinal].value = true
        for (i in episodeStateSort.indices) {
            if (episodeStateSort[i].value) {
                if (playStateQueries.isNotEmpty()) playStateQueries += " OR "
                playStateQueries += " playState == ${EpisodeState.entries[i].code} "
            }
        }
        when (subPrefs.sortIndex) {
            FeedSortIndex.Date.ordinal -> prepareDateSort()
            FeedSortIndex.Count.ordinal -> prepareCountSort()
            else -> {}
        }
    }
    LaunchedEffect(subPrefs.ratingCodeSet) {
        ratingQueries = ""
        Logd(TAG, "LaunchedEffect subPrefs.ratingCodeSet")
        for (i in ratingSort.indices) ratingSort[i].value = false
        for (c in subPrefs.ratingCodeSet) ratingSort[Rating.fromCode(c.toInt()).ordinal].value = true
        for (i in ratingSort.indices) {
            if (ratingSort[i].value) {
                if (ratingQueries.isNotEmpty()) ratingQueries += " OR "
                ratingQueries += " rating == ${Rating.entries[i].code} "
            }
        }
        when (subPrefs.sortIndex) {
            FeedSortIndex.Count.ordinal -> prepareCountSort()
            else -> {}
        }
    }

    LaunchedEffect(downloadedQuery, commentedQuery) { prepareCountSort() }

    LaunchedEffect(feedOperationText, feeds.size) {
        Logd(TAG, "LaunchedEffect feedOperationText: $feedOperationText feeds.size: ${feeds.size}")
        if (feedOperationText.isBlank()) when (subPrefs.sortIndex) {
            FeedSortIndex.Date.ordinal -> prepareDateSort()
            FeedSortIndex.Time.ordinal -> prepareTimeSort()
            FeedSortIndex.Count.ordinal -> prepareCountSort()
            else -> {}
        }
    }

    LaunchedEffect(subPrefs.feedsFiltered, subPrefs.feedsSorted) {
        Logd(TAG, "LaunchedEffect feedsFiltered, sortPair.first, sortPair")
        buildFlow()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val isFiltered by remember(subPrefs.feedsFilter, subPrefs.tagsSel.size, subPrefs.langsSel.size, subPrefs.queueSelIds.size) { mutableStateOf(subPrefs.feedsFilter.isNotEmpty() || subPrefs.tagsSel.size != appAttribs.feedTags.size || subPrefs.langsSel.size != appAttribs.languages.size || subPrefs.queueSelIds.size != queueIds.size) }
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
                navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_subscriptions), contentDescription = "Open Drawer") } },
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
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_synth_label)) }, onClick = {
                            showNewSynthetic = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.full_refresh_label)) }, onClick = {
                            runOnceOrAsk(context, fullUpdate = true)
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.toggle_grid_list)) }, onClick = {
                            runOnIOScope { upsert(subPrefs) { it.prefFeedGridLayout = !it.prefFeedGridLayout } }
                            expanded = false
                        })
                    }
                })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun LazyList() {
        var selectedSize by remember { mutableIntStateOf(0) }
        val selected = remember { mutableStateListOf<Feed>() }
        var longPressIndex by remember { mutableIntStateOf(-1) }
        var refreshing by remember { mutableStateOf(false)}

        @SuppressLint("LocalContextResourcesRead")
        fun saveFeed(cbBlock: (Feed)->Unit) {
            runOnIOScope {
                runOnIOScope {
                    realm.write {
                        for (f_ in selected) {
                            val f = findLatest(f_)
                            if (f != null) cbBlock(f)
                        }
                    }
                }
            }
            val numItems = selected.size
            Logt(TAG, context.resources.getQuantityString(R.plurals.updated_feeds_batch_label, numItems, numItems))
        }

        var showRemoveFeedDialog by remember { mutableStateOf(false) }
        var showChooseRatingDialog by remember { mutableStateOf(false) }
        var showAutoDeleteHandlerDialog by remember { mutableStateOf(false) }
        var showAssociateDialog by remember { mutableStateOf(false) }
        var showKeepUpdateDialog by remember { mutableStateOf(false) }
        var showTagsSettingDialog by remember { mutableStateOf(false) }
        var showSpeedDialog by remember { mutableStateOf(false) }
        var showAutoDownloadSwitchDialog by remember { mutableStateOf(false) }

        @Composable
        fun OpenDialogs() {
            @Composable
            fun AutoDeleteHandlerDialog(onDismissRequest: () -> Unit) {
                CommonDialogSurface(onDismissRequest = { onDismissRequest() }) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val (selectedOption, _) = remember { mutableStateOf("") }
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

            @Composable
            fun SetAssociateQueueDialog(onDismissRequest: () -> Unit) {
                CommonDialogSurface(onDismissRequest = { onDismissRequest() }) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var selectedOption by remember {mutableStateOf("")}
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

            @Composable
            fun SetKeepUpdateDialog(onDismissRequest: () -> Unit) {
                CommonDialogSurface(onDismissRequest = { onDismissRequest() }) {
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

            @Composable
            fun ChooseRatingDialog(selected: List<Feed>, onDismissRequest: () -> Unit) {
                CommonDialogSurface(onDismissRequest = onDismissRequest) {
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

            if (showRemoveFeedDialog) RemoveFeedDialog(selected, onDismissRequest = {showRemoveFeedDialog = false}) {}
            if (showChooseRatingDialog) ChooseRatingDialog(selected) { showChooseRatingDialog = false }
            if (showAutoDeleteHandlerDialog) AutoDeleteHandlerDialog {showAutoDeleteHandlerDialog = false}
            if (showAssociateDialog) SetAssociateQueueDialog {showAssociateDialog = false}
            if (showKeepUpdateDialog) SetKeepUpdateDialog {showKeepUpdateDialog = false}
            if (showTagsSettingDialog) TagSettingDialog(TagType.Feed, setOf(), multiples = true, { showTagsSettingDialog = false } ) { tags ->
                runOnIOScope {
                    realm.write {
                        for (f_ in selected) {
                            val f = findLatest(f_)
                            if (f != null) f.tags.addAll(tags)
                        }
                    }
                }
            }
            if (showSpeedDialog) PlaybackSpeedDialog(selected, initSpeed = 1f, maxSpeed = 3f, onDismiss = {showSpeedDialog = false}) { newSpeed ->
                saveFeed { it: Feed -> it.playSpeed = newSpeed }
            }
            if (showAutoDownloadSwitchDialog) SimpleSwitchDialog(stringResource(R.string.auto_download_settings_label), stringResource(R.string.auto_download_label), onDismissRequest = { showAutoDownloadSwitchDialog = false }) { enabled ->
                saveFeed { it: Feed -> it.autoDownload = enabled }
            }
            if (commonConfirm != null) CommonConfirmDialog(commonConfirm!!)
        }

        OpenDialogs()

        @Composable
        fun EpisodeSpeedDial(selected: SnapshotStateList<Feed>, modifier: Modifier = Modifier) {
            val TAG = "EpisodeSpeedDial ${selected.size}"
            var isExpanded by remember { mutableStateOf(false) }
            fun onSelected() {
                isExpanded = false
                selectMode = false
            }
            val options = listOf<@Composable () -> Unit>(
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    showKeepUpdateDialog = true
                    onSelected()
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh), "")
                    Text(stringResource(id = R.string.keep_updated)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    onSelected()
                    showAutoDownloadSwitchDialog = true
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_download), "")
                    Text(stringResource(id = R.string.auto_download_label)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    showAutoDeleteHandlerDialog = true
                    onSelected()
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete_auto), "")
                    Text(stringResource(id = R.string.auto_delete_episode)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    onSelected()
                    showSpeedDialog = true
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playback_speed), "")
                    Text(stringResource(id = R.string.playback_speed)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    onSelected()
                    showTagsSettingDialog = true
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_tag), "")
                    Text(stringResource(id = R.string.edit_tags)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    showAssociateDialog = true
                    onSelected()
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "")
                    Text(stringResource(id = R.string.pref_feed_associated_queue)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    onSelected()
                    showChooseRatingDialog = true
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), "Set rating")
                    Text(stringResource(id = R.string.set_rating_label)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    showRemoveFeedDialog = true
                    onSelected()
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "")
                    Text(stringResource(id = R.string.remove_feed_label)) } },
                { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    onSelected()
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
                                    Logd(TAG, "selectedFeeds: ${selected.size}")
                                    if (uri == null) ExportWorker(OpmlWriter(), context).exportFile(selected)
                                    else {
                                        val worker = DocumentFileExportWorker(OpmlWriter(), context, uri)
                                        worker.exportFile(selected)
                                    }
                                } catch (e: Exception) { Loge(TAG, "exportOPML error: ${e.message}") }
                            }
                        }?.launch(intentPickAction)
                        return@clickable
                    } catch (e: ActivityNotFoundException) { Loge(TAG, "No activity found. Should never happen...") }
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_import_export_24), "")
                    Text(stringResource(id = R.string.opml_export_label)) } },
            )
            Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Bottom) {
                if (isExpanded) options.forEachIndexed { _, button ->
                    FloatingActionButton(containerColor = MaterialTheme.colorScheme.onTertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).height(40.dp), onClick = {}) { button() }
                }
                FloatingActionButton(containerColor = MaterialTheme.colorScheme.onTertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiary,
                    onClick = { isExpanded = !isExpanded }) { Icon(Icons.Filled.Edit, "Edit") }
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
            val context = LocalContext.current
            Logd(TAG, "PullToRefreshBox ")
            if (subPrefs.prefFeedGridLayout) {
                LazyVerticalGrid(state = rememberLazyGridState(), columns = GridCells.Adaptive(80.dp), modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)) {
                    items(feeds.size, key = {index -> feeds[index].id}) { index ->
                        val feed by remember { mutableStateOf(feeds[index]) }
                        var isSelected by remember(selectMode, selectedSize, feed.id) { mutableStateOf(selectMode && feed in selected) }
                        fun toggleSelected() {
                            isSelected = !isSelected
                            if (isSelected) selected.add(feed)
                            else selected.remove(feed)
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
                                    selected.clear()
                                    if (selectMode) {
                                        selected.add(feed)
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
                                Text(NumberFormat.getInstance().format(feed.episodes.size.toLong()), color = buttonAltColor,
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
                            upsert(subPrefs) {
                                it.positionIndex = index
                                it.positionOffset = offset
                            }
                        }
                    }
                }

                val currentEntry = navController.navController.currentBackStackEntryAsState().value
                LaunchedEffect(feeds.size) {
                    val cameBack = currentEntry?.savedStateHandle?.get<Boolean>("returned") ?: false
                    Logd(TAG, "LaunchedEffect(feeds.size) cameBack: $cameBack")
                    if (!restored && feeds.isNotEmpty()) {
                        if (!cameBack) {
                            scope.launch {
                                val (index, offset) = Pair(subPrefs.positionIndex, subPrefs.positionOffset)
                                val safeIndex = index.coerceIn(0, feeds.size - 1)
                                val safeOffset = if (safeIndex == index) offset else 0
                                listState.scrollToItem(safeIndex, safeOffset)
                                currentEntry?.savedStateHandle?.remove<Boolean>("comingBack")
                            }
                        } else {
                            scope.launch {
                                listState.scrollToItem(
                                    listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset
                                )
                            }
                        }
                        restored = true
                    }
                }
                LaunchedEffect(filterSorted) {
                    Logd(TAG, "LaunchedEffect(filterSorted) $filterSorted")
                    if (feeds.isNotEmpty() && filterSorted) scope.launch { listState.scrollToItem(0) }
                    filterSorted = false
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(feeds, key = { _, feed -> feed.id}) { index, feed_ ->
                        val feed by rememberUpdatedState(feed_)
                        var isSelected by remember(key1 = selectMode, key2 = selectedSize) { mutableStateOf(selectMode && feed in selected) }
                        fun toggleSelected() {
                            isSelected = !isSelected
                            if (isSelected) selected.add(feed)
                            else selected.remove(feed)
                            Logd(TAG, "toggleSelected: selected: ${selected.size}")
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
                                Logd(TAG, "clicked: ${feed.title}")
                                if (!feed.isBuilding) {
                                    if (selectMode) toggleSelected()
                                    else navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.id}")
                                }
                            }, onLongClick = {
                                if (!feed.isBuilding) {
                                    selectMode = !selectMode
                                    isSelected = selectMode
                                    selected.clear()
                                    if (selectMode) {
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
                                    if (feed.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = buttonColor, contentDescription = "rating", modifier = Modifier.width(20.dp).height(20.dp).background(MaterialTheme.colorScheme.tertiaryContainer))
                                    Text(feed.title ?: "No title", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Text(feed.author ?: "No author", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.padding(top = 5.dp)) {
                                    val measureString = remember { NumberFormat.getInstance().format(feed.episodes.size.toLong()) + " : " + durationInHours(feed.totleDuration/1000, false) }
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
                Row(modifier = Modifier.align(Alignment.TopEnd).width(150.dp).height(45.dp).background(MaterialTheme.colorScheme.onTertiaryContainer),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_upward_24), tint = buttonColor, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp).clickable(onClick = {
                            selected.clear()
                            for (i in 0..longPressIndex) selected.add(feeds[i])
                            selectedSize = selected.size
                            Logd(TAG, "selectedIds: ${selected.size}")
                        }))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_downward_24), tint = buttonColor, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp).clickable(onClick = {
                            selected.clear()
                            for (i in longPressIndex..<feeds.size) selected.add(feeds[i])
                            selectedSize = selected.size
                            Logd(TAG, "selectedIds: ${selected.size}")
                        }))
                    var selectAllRes by remember { mutableIntStateOf(R.drawable.ic_select_all) }
                    Icon(imageVector = ImageVector.vectorResource(selectAllRes), tint = buttonColor, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).clickable(onClick = {
                            if (selectedSize != feeds.size) {
                                selected.clear()
                                selected.addAll(feeds)
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
    fun OpenDialogs() {
        @Composable
        fun SortDialog(onDismissRequest: () -> Unit) {
            Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
                val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
                dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
                Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, buttonColor)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.sortIndex != FeedSortIndex.Title.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    runOnIOScope {
                                        upsert(subPrefs) {
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
                            ) { Text(text = stringResource(FeedSortIndex.Title.res) + if (subPrefs.titleAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.sortIndex != FeedSortIndex.Date.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    if (subPrefs.sortIndex == FeedSortIndex.Date.ordinal)
                                        runOnIOScope {
                                            upsert(subPrefs) {
                                                it.dateAscending = !it.dateAscending
                                                it.sortDirCode = if (it.dateAscending) 0 else 1
                                                it.feedsSorted += 1
                                            }
                                        }
                                    else prepareDateSort()
                                }
                            ) { Text(text = stringResource(FeedSortIndex.Date.res) + if (subPrefs.dateAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.sortIndex != FeedSortIndex.Time.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    if (subPrefs.sortIndex == FeedSortIndex.Time.ordinal)
                                        runOnIOScope {
                                            upsert(subPrefs) {
                                                it.timeAscending = !it.timeAscending
                                                it.sortDirCode = if (it.timeAscending) 0 else 1
                                                it.feedsSorted += 1
                                            }
                                        }
                                    else prepareTimeSort()
                                }
                            ) { Text(text = stringResource(FeedSortIndex.Time.res) + if (subPrefs.timeAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.sortIndex != FeedSortIndex.Count.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    if (subPrefs.sortIndex == FeedSortIndex.Count.ordinal)
                                        runOnIOScope {
                                            upsert(subPrefs) {
                                                it.countAscending = !it.countAscending
                                                it.sortDirCode = if (it.countAscending) 0 else 1
                                                it.feedsSorted += 1
                                            }
                                        }
                                    else prepareCountSort()
                                }
                            ) { Text(text = stringResource(FeedSortIndex.Count.res) + if (subPrefs.countAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
                        if (subPrefs.sortIndex == FeedSortIndex.Date.ordinal) {
                            Row {
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.dateSortIndex != FeedDateSortIndex.Played.ordinal) buttonColor else buttonAltColor),
                                    onClick = { prepareDateSort(FeedDateSortIndex.Played) }
                                ) { Text(stringResource(FeedDateSortIndex.Played.res)) }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.dateSortIndex != FeedDateSortIndex.Downloaded.ordinal) buttonColor else buttonAltColor),
                                    onClick = { prepareDateSort(FeedDateSortIndex.Downloaded) }
                                ) { Text(stringResource(FeedDateSortIndex.Downloaded.res)) }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.dateSortIndex != FeedDateSortIndex.Commented.ordinal) buttonColor else buttonAltColor),
                                    onClick = { prepareDateSort(FeedDateSortIndex.Commented) }
                                ) { Text(stringResource(FeedDateSortIndex.Commented.res)) }
                            }
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.dateSortIndex != FeedDateSortIndex.Publish.ordinal) buttonColor else buttonAltColor),
                                onClick = { prepareDateSort(FeedDateSortIndex.Publish) }
                            ) { Text(stringResource(FeedDateSortIndex.Publish.res)) }
                        } else if (subPrefs.sortIndex == FeedSortIndex.Time.ordinal) {
                            Row {
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.timeSortIndex != 1) buttonColor else buttonAltColor),
                                    onClick = { prepareTimeSort(FeedTimeSortIndex.Min) }
                                ) { Text(stringResource(R.string.min_duration)) }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.timeSortIndex != 2) buttonColor else buttonAltColor),
                                    onClick = { prepareTimeSort(FeedTimeSortIndex.Max) }
                                ) { Text(stringResource(R.string.max_duration)) }
                            }
                            Row {
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.timeSortIndex != 0) buttonColor else buttonAltColor),
                                    onClick = { prepareTimeSort(FeedTimeSortIndex.Total) }
                                ) { Text(stringResource(R.string.total_duration)) }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (subPrefs.timeSortIndex != 3) buttonColor else buttonAltColor),
                                    onClick = { prepareTimeSort(FeedTimeSortIndex.Average) }
                                ) { Text(stringResource(R.string.average_duration)) }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
                        Column(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                            if (subPrefs.sortIndex == FeedSortIndex.Count.ordinal) {
                                Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    val item = EpisodeFilter.EpisodesFilterGroup.DOWNLOADED
                                    var selectNone by remember { mutableStateOf(false) }
                                    LaunchedEffect(selectNone) {
                                        if (selectNone) runOnIOScope {
                                            upsert(subPrefs) {
                                                it.downlaodedSortIndex = -1
//                                                it.feedsSorted += 1
                                            }
//                                            prepareCountSort()
                                        }
                                    }
                                    Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(end = 10.dp))
                                    Spacer(Modifier.weight(0.3f))
                                    OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (subPrefs.downlaodedSortIndex != 0) buttonColor else buttonAltColor),
                                        onClick = {
                                            runOnIOScope {
                                                upsert(subPrefs) {
                                                    if (it.downlaodedSortIndex != 0) {
                                                        it.downlaodedSortIndex = 0
                                                    } else it.downlaodedSortIndex = -1
//                                                    it.feedsSorted += 1
                                                }
//                                                prepareCountSort()
                                            }
                                            selectNone = false
                                        },
                                    ) { Text(text = stringResource(item.properties[0].displayName), color = textColor) }
                                    Spacer(Modifier.weight(0.1f))
                                    OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (subPrefs.downlaodedSortIndex != 1) buttonColor else buttonAltColor),
                                        onClick = {
                                            runOnIOScope {
                                                upsert(subPrefs) {
                                                    if (it.downlaodedSortIndex != 1) {
                                                        selectNone = false
                                                        it.downlaodedSortIndex = 1
                                                    } else it.downlaodedSortIndex = -1
//                                                    it.feedsSorted += 1
                                                }
//                                                prepareCountSort()
                                            }
                                        },
                                    ) { Text(text = stringResource(item.properties[1].displayName), color = textColor) }
                                    Spacer(Modifier.weight(0.5f))
                                }
                                Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    val item = EpisodeFilter.EpisodesFilterGroup.OPINION
                                    var selectNone by remember { mutableStateOf(false) }
                                    LaunchedEffect(selectNone) {
                                        if (selectNone) runOnIOScope {
                                            upsert(subPrefs) {
                                                it.commentedSortIndex = -1
//                                                it.feedsSorted += 1
                                            }
//                                            prepareCountSort()
                                        }
                                    }
                                    Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(end = 10.dp))
                                    Spacer(Modifier.weight(0.3f))
                                    OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (subPrefs.commentedSortIndex != 0) buttonColor else buttonAltColor),
                                        onClick = {
                                            runOnIOScope {
                                                upsert(subPrefs) {
                                                    if (it.commentedSortIndex != 0) {
                                                        selectNone = false
                                                        it.commentedSortIndex = 0
                                                    } else it.commentedSortIndex = -1
//                                                    it.feedsSorted += 1
                                                }
//                                                prepareCountSort()
                                            }
                                        },
                                    ) { Text(text = stringResource(item.properties[0].displayName), color = textColor) }
                                    Spacer(Modifier.weight(0.1f))
                                    OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (subPrefs.commentedSortIndex != 1) buttonColor else buttonAltColor),
                                        onClick = {
                                            runOnIOScope {
                                                upsert(subPrefs) {
                                                    if (it.commentedSortIndex != 1) {
                                                        selectNone = false
                                                        it.commentedSortIndex = 1
                                                    } else it.commentedSortIndex = -1
//                                                    it.feedsSorted += 1
                                                }
//                                                prepareCountSort()
                                            }
                                        },
                                    ) { Text(text = stringResource(item.properties[1].displayName), color = textColor) }
                                    Spacer(Modifier.weight(0.5f))
                                }
                            }
                            if ((subPrefs.sortIndex == FeedSortIndex.Date.ordinal && subPrefs.dateSortIndex == FeedDateSortIndex.Publish.ordinal) || subPrefs.sortIndex == FeedSortIndex.Count.ordinal) {
                                fun persistStateSort() {
                                    runOnIOScope {
                                        runOnIOScope {
                                            upsert(subPrefs) {
                                                it.playStateCodeSet.clear()
                                                for (i in episodeStateSort.indices) {
                                                    if (episodeStateSort[i].value) it.playStateCodeSet.add(EpisodeState.entries[i].code.toString())
                                                }
//                                                it.feedsSorted += 1
                                            }
                                        }
                                    }
                                }
                                val item = EpisodeFilter.EpisodesFilterGroup.PLAY_STATE
                                var selectNone by remember { mutableStateOf(false) }
                                var expandRow by remember { mutableStateOf(false) }
                                Row {
                                    Text(stringResource(item.nameRes) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor,
                                        modifier = Modifier.clickable { expandRow = !expandRow })
                                    if (expandRow) {
                                        var lowerSelected by remember { mutableStateOf(false) }
                                        var higherSelected by remember { mutableStateOf(false) }
                                        Spacer(Modifier.weight(1f))
                                        Text("<<<", color = if (lowerSelected) buttonAltColor else buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                val hIndex = episodeStateSort.indexOfLast { it.value }
                                                if (hIndex < 0) return@clickable
                                                if (!lowerSelected) {
                                                    for (i in 0..hIndex) episodeStateSort[i].value = true
                                                } else {
                                                    for (i in 0..hIndex) episodeStateSort[i].value = false
                                                    episodeStateSort[hIndex].value = true
                                                }
                                                lowerSelected = !lowerSelected
                                                persistStateSort()
                                            })
                                        Spacer(Modifier.weight(1f))
                                        Text("A", color = buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                val selectAll = !(lowerSelected && higherSelected)
                                                lowerSelected = selectAll
                                                higherSelected = selectAll
                                                for (i in item.properties.indices) episodeStateSort[i].value = selectAll
                                                persistStateSort()
                                            })
                                        Spacer(Modifier.weight(1f))
                                        Text(">>>", color = if (higherSelected) buttonAltColor else buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                val lIndex = episodeStateSort.indexOfFirst { it.value }
                                                if (lIndex < 0) return@clickable
                                                if (!higherSelected) {
                                                    for (i in lIndex..<item.properties.size) episodeStateSort[i].value = true
                                                } else {
                                                    for (i in lIndex..<item.properties.size) episodeStateSort[i].value = false
                                                    episodeStateSort[lIndex].value = true
                                                }
                                                higherSelected = !higherSelected
                                                persistStateSort()
                                            })
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                                if (expandRow) ScrollRowGrid(columns = 3, itemCount = item.properties.size, modifier = Modifier.padding(start = 10.dp)) { index ->
//                                    val stateName = EpisodeFilter.EpisodesFilterGroup.PLAY_STATE.properties[index_].filterId
////                                    Logd(TAG, "index_: $index_ stateName: $stateName")
//                                    val index = EpisodeState.entries.indexOfFirst { it.name == stateName }
                                    if (selectNone) episodeStateSort[index].value = false
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (episodeStateSort[index].value) buttonAltColor else buttonColor),
                                        onClick = {
                                            selectNone = false
                                            episodeStateSort[index].value = !episodeStateSort[index].value
                                            persistStateSort()
                                        },
                                    ) { Text(text = stringResource(item.properties[index].displayName), maxLines = 1, color = textColor) }
                                }
                            }
                            if (subPrefs.sortIndex == FeedSortIndex.Count.ordinal) {
                                fun persistRatingSort() {
                                    runOnIOScope {
                                        upsert(subPrefs) {
                                            it.ratingCodeSet.clear()
                                            for (i in ratingSort.indices) {
                                                if (ratingSort[i].value) it.ratingCodeSet.add(Rating.entries[i].code.toString())
                                            }
//                                            it.feedsSorted += 1
                                        }
//                                        prepareCountSort()
                                    }
                                }
                                val item = EpisodeFilter.EpisodesFilterGroup.RATING
                                var selectNone by remember { mutableStateOf(false) }
                                var expandRow by remember { mutableStateOf(false) }
                                Row {
                                    Text(stringResource(item.nameRes) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor,
                                        modifier = Modifier.clickable { expandRow = !expandRow })
                                    if (expandRow) {
                                        var lowerSelected by remember { mutableStateOf(false) }
                                        var higherSelected by remember { mutableStateOf(false) }
                                        Spacer(Modifier.weight(1f))
                                        Text("<<<", color = if (lowerSelected) buttonAltColor else buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                val hIndex = ratingSort.indexOfLast { it.value }
                                                if (hIndex < 0) return@clickable
                                                if (!lowerSelected) {
                                                    for (i in 0..hIndex) ratingSort[i].value = true
                                                } else {
                                                    for (i in 0..hIndex) ratingSort[i].value = false
                                                    ratingSort[hIndex].value = true
                                                }
                                                lowerSelected = !lowerSelected
                                                persistRatingSort()
                                            })
                                        Spacer(Modifier.weight(1f))
                                        Text("A", color = buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                val selectAll = !(lowerSelected && higherSelected)
                                                lowerSelected = selectAll
                                                higherSelected = selectAll
                                                for (i in item.properties.indices) ratingSort[i].value = selectAll
                                                persistRatingSort()
                                            })
                                        Spacer(Modifier.weight(1f))
                                        Text(">>>", color = if (higherSelected) buttonAltColor else buttonColor, style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.clickable {
                                                val lIndex = ratingSort.indexOfFirst { it.value }
                                                if (lIndex < 0) return@clickable
                                                if (!higherSelected) {
                                                    for (i in lIndex..<item.properties.size) ratingSort[i].value = true
                                                } else {
                                                    for (i in lIndex..<item.properties.size) ratingSort[i].value = false
                                                    ratingSort[lIndex].value = true
                                                }
                                                higherSelected = !higherSelected
                                                persistRatingSort()
                                            })
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                                if (expandRow) ScrollRowGrid(columns = 3, itemCount = item.properties.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                    if (selectNone) ratingSort[index].value = false
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (ratingSort[index].value) buttonAltColor else buttonColor),
                                        onClick = {
                                            selectNone = false
                                            ratingSort[index].value = !ratingSort[index].value
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
            var langFull by remember(subPrefs.langsSel.size) { mutableStateOf(subPrefs.langsSel.size == appAttribs.languages.size) }
            var tagsFull by remember(subPrefs.tagsSel.size) { mutableStateOf(subPrefs.tagsSel.size == appAttribs.feedTags.size) }
            var queuesFull by remember(subPrefs.queueSelIds.size) { mutableStateOf(subPrefs.queueSelIds.size == queueNames.size) }
            fun onFilterChanged(newFilterValues: Set<String>) {
                runOnIOScope {
                    upsert(subPrefs) {
                        it.feedsFilter = StringUtils.join(newFilterValues, ",")
                        it.feedsFiltered++
                    }
                }
                Logd(TAG, "onFilterChanged: ${subPrefs.feedsFilter}")
            }
            Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
                val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
                dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
                Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, buttonColor)) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            if (appAttribs.languages.size > 1) {
                                val langs = remember { appAttribs.languages.toList().sorted() }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val selectedList = remember { MutableList(langs.size) { mutableStateOf(false) } }
                                    LaunchedEffect(reset) {
                                        Logd(TAG, "LaunchedEffect(reset) lang")
                                        for (index in selectedList.indices) {
                                            if (langs[index] in subPrefs.langsSel) selectedList[index].value = true
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
                                                    for (i in langs.indices) {
                                                        if (selectedList[i].value) langsSel.add(langs[i])
                                                    }
                                                    upsert(subPrefs) {
                                                        it.langsSel = langsSel.toRealmSet()
                                                        it.feedsFiltered += 1
                                                    }
                                                }
                                                Logd(TAG, "langsSel: ${subPrefs.langsSel.size} ${langs.size}")
                                            }
                                            SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                        }
                                    }
                                    if (expandRow) ScrollRowGrid(columns = 3, itemCount = langs.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                        OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                            onClick = {
                                                selectedList[index].value = !selectedList[index].value
                                                runOnIOScope {
                                                    val langsSel = subPrefs.langsSel.toMutableSet()
                                                    if (selectedList[index].value) langsSel.add(langs[index])
                                                    else langsSel.remove(langs[index])
                                                    upsert(subPrefs) {
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
                                val selectedList = remember { MutableList(queueNames.size) { mutableStateOf(false) } }
                                LaunchedEffect(reset) {
                                    Logd(TAG, "LaunchedEffect(reset) queue")
                                    for (index in selectedList.indices) {
                                        if (queueIds[index] in subPrefs.queueSelIds) selectedList[index].value = true
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
                                                for (i in queueNames.indices) {
                                                    if (selectedList[i].value) qSelIds.add(queueIds[i])
                                                }
                                                upsert(subPrefs) {
                                                    it.queueSelIds = qSelIds.toRealmSet()
                                                    it.feedsFiltered += 1
                                                }
                                            }
                                            Unit
                                        }
                                        SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                    }
                                }
                                if (expandRow) ScrollRowGrid(columns = 3, itemCount = queueNames.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                        onClick = {
                                            selectedList[index].value = !selectedList[index].value
                                            runOnIOScope {
                                                val qSelIds = subPrefs.queueSelIds.toMutableSet()
                                                if (selectedList[index].value) qSelIds.add(queueIds[index])
                                                else qSelIds.remove(queueIds[index])
                                                upsert(subPrefs) {
                                                    it.queueSelIds = qSelIds.toRealmSet()
                                                    it.feedsFiltered += 1
                                                }
                                            }
                                        },
                                    ) { Text(text = queueNames[index], maxLines = 1, color = textColor) }
                                }
                            }
                            if (appAttribs.feedTags.isNotEmpty()) {
                                val tagList = remember { appAttribs.feedTags.toList().sorted() }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val selectedList = remember { MutableList(tagList.size) { mutableStateOf(false) } }
                                    LaunchedEffect(reset) {
                                        Logd(TAG, "LaunchedEffect(reset) tag")
                                        for (index in selectedList.indices) {
                                            if (tagList[index] in subPrefs.tagsSel) selectedList[index].value = true
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
                                                    for (i in tagList.indices) {
                                                        if (selectedList[i].value) tagsSel.add(tagList[i])
                                                    }
                                                    upsert(subPrefs) {
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
                                                    val tagsSel = subPrefs.tagsSel.toMutableSet()
                                                    if (selectedList[index].value) tagsSel.add(tagList[index])
                                                    else tagsSel.remove(tagList[index])
                                                    upsert(subPrefs) {
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
                                        upsert(subPrefs) {
                                            it.tagsSel = appAttribs.feedTags.toRealmSet()
                                            it.queueSelIds = queueIds.toRealmSet()
                                            it.langsSel = appAttribs.languages.toRealmSet()
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

        if (showFilterDialog) {
            filterSorted = true
            FilterDialog(FeedFilter(subPrefs.feedsFilter)) {
                filterSorted = false
                showFilterDialog = false
            }
        }
        if (showSortDialog) {
            filterSorted = true
            SortDialog {
                filterSorted = false
                showSortDialog = false
            }
        }
        if (showNewSynthetic) RenameOrCreateSyntheticFeed { showNewSynthetic = false }
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