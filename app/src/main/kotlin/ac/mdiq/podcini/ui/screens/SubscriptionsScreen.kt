package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.DocumentFileExportWorker
import ac.mdiq.podcini.preferences.ExportTypes
import ac.mdiq.podcini.preferences.ExportWorker
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.storage.database.Feeds.compileLanguages
import ac.mdiq.podcini.storage.database.Feeds.feedOperationText
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.getTags
import ac.mdiq.podcini.storage.database.Feeds.languages
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.AutoDeleteAction
import ac.mdiq.podcini.storage.model.Feed.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLong
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.storage.utils.FeedFilter
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.NonlazyGrid
import ac.mdiq.podcini.ui.compose.PlaybackSpeedDialog
import ac.mdiq.podcini.ui.compose.RemoveFeedDialog
import ac.mdiq.podcini.ui.compose.RenameOrCreateSyntheticFeed
import ac.mdiq.podcini.ui.compose.SelectLowerAllUpper
import ac.mdiq.podcini.ui.compose.SimpleSwitchDialog
import ac.mdiq.podcini.ui.compose.SpinnerExternalSet
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
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
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.edit
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            prefs.edit { putString("feedsFilter", filter) }
        }

    internal val languages: MutableList<String> = mutableListOf()
    private var _langsSel: Set<String>? = null
    internal var langsSel: Set<String>
        get() {
            if (_langsSel == null) _langsSel = prefs.getStringSet("langsSel", emptySet())?: languages.toSet()
            return _langsSel!!
        }
        set(valueSet) {
            _langsSel = valueSet
            prefs.edit { putStringSet("langsSel", valueSet) }
        }

    internal val tags = mutableStateListOf<String>()
    private var _tagsSel: Set<String>? = null
    internal var tagsSel: Set<String>
        get() {
            if (_tagsSel == null) _tagsSel = prefs.getStringSet("tagsSel", emptySet())?: tags.toSet()
            return _tagsSel!!
        }
        set(valueSet) {
            _tagsSel = valueSet
            prefs.edit { putStringSet("tagsSel", valueSet) }
        }

    val queueNames = mutableStateListOf<String>()
    internal val queueIds: MutableList<Long> = mutableListOf()
    private var _qSelIds: Set<String>? = null
    internal var qSelIds: Set<Long>
        get() {
            if (_qSelIds == null) _qSelIds = prefs.getStringSet("qSelIds", emptySet()) ?: queueIds.map { it.toString() }.toSet()
            return _qSelIds!!.mapNotNull { it.toLongOrNull() }.toSet()
        }
        set(valueSet) {
            _qSelIds = valueSet.map { it.toString() }.toSet()
            prefs.edit { putStringSet("qSelIds", _qSelIds) }
        }

    internal var isFiltered by mutableStateOf(false)

    //    TODO: currently not used
    internal var displayedFolder by mutableStateOf("")

    internal var feedCountState by mutableStateOf("")
    internal var feedSorted by mutableIntStateOf(0)

    internal var sortIndex by mutableIntStateOf(0)
    internal var titleAscending by mutableStateOf(true)
    internal var dateAscending by mutableStateOf(true)
    internal var timeAscending by mutableStateOf(true)
    internal var countAscending by mutableStateOf(true)
    internal var dateSortIndex by mutableIntStateOf(0)
    internal var timeSortIndex by mutableIntStateOf(0)
    internal val episodeStateSort = MutableList(EpisodeState.entries.size) { mutableStateOf(false) }
    internal val playStateCodeSet = mutableSetOf<String>()
    internal val ratingSort = MutableList(Rating.entries.size) { mutableStateOf(false) }
    internal val ratingCodeSet = mutableSetOf<String>()
    internal var downlaodedSortIndex by mutableIntStateOf(-1)
    internal var commentedSortIndex by mutableIntStateOf(-1)

    internal var feedListFiltered = mutableStateListOf<Feed>()
    internal var showFilterDialog by mutableStateOf(false)
    internal var showSortDialog by mutableStateOf(false)
    internal var noSubscription by mutableStateOf(false)
    internal var showNewSynthetic by mutableStateOf(false)

    internal var useGrid by mutableStateOf<Boolean?>(null)
    internal val useGridLayout by mutableStateOf(getPref(AppPrefs.prefFeedGridLayout, false))

    internal var selectMode by mutableStateOf(false)

    internal fun resetTags() {
        tags.clear()
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
                    is FlowEvent.FeedListEvent -> loadSubscriptions(true)
                    is FlowEvent.FeedTagsChangedEvent -> loadSubscriptions(true)
                    is FlowEvent.FeedChangeEvent -> loadSubscriptions(true)
                    else -> {}
                }
            }
        }
    }

    private var loadingJob: Job? = null
    internal fun loadSubscriptions(force: Boolean = false) {
        if (loadingJob != null) {
            if (force) {
                loadingJob?.cancel()
                feedListFiltered.clear()
            } else return
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
                    isFiltered = feedsFilter.isNotEmpty() || tagsSel.size != tags.size || langsSel.size != languages.size || qSelIds.size != queueIds.size
                }
            } catch (e: Throwable) { Logd(TAG, e.message ?: "No message") }
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
        } catch (e: Exception) { Loge(TAG, "exportOPML error: ${e.message}") }
    }

    private fun sortArrays2CodeSet() {
        playStateCodeSet.clear()
        for (i in episodeStateSort.indices) {
            if (episodeStateSort[i].value) playStateCodeSet.add(EpisodeState.entries[i].code.toString())
        }
        ratingCodeSet.clear()
        for (i in ratingSort.indices) {
            if (ratingSort[i].value) ratingCodeSet.add(Rating.entries[i].code.toString())
        }
    }
    private fun sortArraysFromCodeSet() {
        for (i in episodeStateSort.indices) episodeStateSort[i].value = false
        for (c in playStateCodeSet) episodeStateSort[EpisodeState.fromCode(c.toInt()).ordinal].value = true
        for (i in ratingSort.indices) ratingSort[i].value = false
        for (c in ratingCodeSet) ratingSort[Rating.fromCode(c.toInt()).ordinal].value = true
    }

    private fun saveSortingPrefs() {
        sortArrays2CodeSet()
        prefs.edit {
            putInt("sortIndex", sortIndex)
            putBoolean("titleAscending", titleAscending)
            putBoolean("dateAscending", dateAscending)
            putBoolean("countAscending", countAscending)
            putInt("dateSortIndex", dateSortIndex)
            putInt("downlaodedSortIndex", downlaodedSortIndex)
            putInt("commentedSortIndex", commentedSortIndex)
            putStringSet("playStateCodeSet", playStateCodeSet)
            putStringSet("ratingCodeSet", ratingCodeSet)
        }
    }

    internal fun getSortingPrefs() {
        sortIndex = prefs.getInt("sortIndex", FeedSortIndex.Title.ordinal)
        titleAscending = prefs.getBoolean("titleAscending", true)
        dateAscending = prefs.getBoolean("dateAscending", true)
        countAscending = prefs.getBoolean("countAscending", true)
        dateSortIndex = prefs.getInt("dateSortIndex", FeedDateSortIndex.Publish.ordinal)
        downlaodedSortIndex = prefs.getInt("downlaodedSortIndex", -1)
        commentedSortIndex = prefs.getInt("commentedSortIndex", -1)
        playStateCodeSet.clear()
        playStateCodeSet.addAll(prefs.getStringSet("playStateCodeSet", setOf())!!)
        ratingCodeSet.clear()
        ratingCodeSet.addAll(prefs.getStringSet("ratingCodeSet", setOf())!!)
        sortArraysFromCodeSet()
    }

    internal fun fetchAndSort(build: Boolean = true): List<Feed> {
        fun languagesQS() : String {
            var q  = ""
            when {
                langsSel.isEmpty() -> q = " (language == '' OR language == nil) "
                langsSel.size == languages.size -> q = ""
                else -> {
                    for (l in langsSel) {
                        q += if (q.isEmpty()) " ( language == '$l' " else " OR language == '$l' "
                    }
                    if (q.isNotEmpty()) q += " ) "
                }
            }
            Logd(TAG, "languagesQS: $q")
            return q
        }
        fun tagsQS() : String {
            var q  = ""
            when {
                tagsSel.isEmpty() -> q = " (tags.@count == 0 OR (tags.@count != 0 AND ALL tags == '#root' )) "
                tagsSel.size == tags.size -> q = ""
                else -> {
                    for (t in tagsSel) {
                        q += if (q.isEmpty()) " ( ANY tags == '$t' " else " OR ANY tags == '$t' "
                    }
                    if (q.isNotEmpty()) q += " ) "
                }
            }
            Logd(TAG, "tagsQS: $q")
            return q
        }
        fun queuesQS() : String {
            val qSelIds_ = qSelIds.toMutableSet()
            if (qSelIds_.isEmpty()) qSelIds_.add(-2)
            else {
//                if ((queueIds - qSelIds_).isEmpty()) qSelIds_.add(-2)
//                else qSelIds_.remove(-2)
                if ((queueIds - qSelIds_).isEmpty()) qSelIds_.clear()
                else qSelIds_.remove(-2)
            }
            var q  = ""
            for (id in qSelIds_) {
                q += if (q.isEmpty()) " ( queueId == '$id' " else " OR queueId == '$id' "
            }
            if (q.isNotEmpty()) q += " ) "
            Logd(TAG, "queuesQS: $q")
            return q
        }
        fun getFeedList(): MutableList<Feed> {
            var fQueryStr = FeedFilter(feedsFilter).queryString()
            val langsQueryStr = languagesQS()
            if (langsQueryStr.isNotEmpty())  fQueryStr += " AND $langsQueryStr"
            val tagsQueryStr = tagsQS()
            if (tagsQueryStr.isNotEmpty())  fQueryStr += " AND $tagsQueryStr"
            val queuesQueryStr = queuesQS()
            if (queuesQueryStr.isNotEmpty())  fQueryStr += " AND $queuesQueryStr"
            Logd(TAG, "sortFeeds() called [$feedsFilter] [$fQueryStr]")
            return getFeedList(fQueryStr).toMutableList()
        }

        val feedList_ = getFeedList()
        for (f in feedList_) f.sortInfo = ""
        val comparator = when (sortIndex) {
            FeedSortIndex.Title.ordinal -> {
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
            FeedSortIndex.Date.ordinal -> {
                val dir = if (dateAscending) 1 else -1
                when (dateSortIndex) {
                    FeedDateSortIndex.Publish.ordinal -> {  // date publish
                        var playStateQueries = ""
                        for (i in episodeStateSort.indices) {
                            if (episodeStateSort[i].value) {
                                if (playStateQueries.isNotEmpty()) playStateQueries += " OR "
                                playStateQueries += " playState == ${EpisodeState.entries[i].code} "
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
                    FeedDateSortIndex.Downloaded.ordinal -> {  // date downloaded
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
                    FeedDateSortIndex.Played.ordinal -> {  // date last played
                        val queryString = "feedId == $0 SORT(lastPlayedTime DESC)"
                        val counterMap: MutableMap<Long, Long> = mutableMapOf()
                        for (f in feedList_) {
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.lastPlayedTime ?: 0L
                            counterMap[f.id] = d
                            f.sortInfo = "Last played: " + formatDateTimeFlex(Date(d))
                        }
                        Logd(TAG, "queryString: $queryString")
                        comparator(counterMap, dir)
                    }
                    FeedDateSortIndex.Commented.ordinal -> {  // date last commented
                        val queryString = "feedId == $0 SORT(commentTime DESC)"
                        val counterMap: MutableMap<Long, Long> = mutableMapOf()
                        for (f in feedList_) {
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.commentTime ?: 0L
                            counterMap[f.id] = d
                            f.sortInfo = "Last commented: " + formatDateTimeFlex(Date(d))
                        }
                        Logd(TAG, "queryString: $queryString")
                        comparator(counterMap, dir)
                    }
                    else -> comparator(mutableMapOf(), 0)
                }
            }
            FeedSortIndex.Count.ordinal -> {   // count
                val dir = if (countAscending) 1 else -1
                var playStateQueries = ""
                for (i in episodeStateSort.indices) {
                    if (episodeStateSort[i].value) {
                        if (playStateQueries.isNotEmpty()) playStateQueries += " OR "
                        playStateQueries += " playState == ${EpisodeState.entries[i].code} "
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
            else -> {
                val dir = if (timeAscending) 1 else -1
                when (timeSortIndex) {
                    0 -> { // total duration
                        Comparator { lhs: Feed, rhs: Feed ->
                            val t1 = lhs.totleDuration
                            val t2 = rhs.totleDuration
                            t1.compareTo(t2) * dir
                        }
                    }
                    1 -> {  // min duration
                        val queryString = "feedId == $0 SORT(duration ASC)"
                        val counterMap: MutableMap<Long, Long> = mutableMapOf()
                        for (f in feedList_) {
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.duration?.toLong() ?: 0L
                            counterMap[f.id] = d
                            f.sortInfo = "Min duration: " + getDurationStringLong(d.toInt())
                        }
                        Logd(TAG, "queryString: $queryString")
                        comparator(counterMap, dir)
                    }
                    2 -> {  // max duration
                        val queryString = "feedId == $0 SORT(duration DESC)"
                        val counterMap: MutableMap<Long, Long> = mutableMapOf()
                        for (f in feedList_) {
                            val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.duration?.toLong() ?: 0L
                            counterMap[f.id] = d
                            f.sortInfo = "Min duration: " + getDurationStringLong(d.toInt())
                        }
                        Logd(TAG, "queryString: $queryString")
                        comparator(counterMap, dir)
                    }
                    3 -> {  // average duration
                        Comparator { lhs: Feed, rhs: Feed ->
                            val ln = lhs.episodes.size
                            val t1 = if (ln > 0) lhs.totleDuration / ln else 0
                            val rn = rhs.episodes.size
                            val t2 = if (rn > 0) rhs.totleDuration / rn else 0
                            t1.compareTo(t2) * dir
                        }
                    }
                    else -> comparator(mutableMapOf(), 0)
                }
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
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    compileLanguages()
                    vm.languages.addAll(languages.filter { it.isNotBlank() })

                    vm.getSortingPrefs()
//                    if (arguments != null) vm.displayedFolder = requireArguments().getString(ARGUMENT_FOLDER, null)
                    vm.resetTags()
                    val queues = realm.query(PlayQueue::class).find()
                    vm.queueIds.addAll(queues.map { it.id })
                    vm.queueNames.addAll(queues.map { it.name })
                    vm.feedCountState = vm.feedListFiltered.size.toString() + " / " + feedCount.toString()
                    vm.loadSubscriptions()
                }
                Lifecycle.Event.ON_START -> {
                    vm.procFlowEvents()
                    gearbox.cleanGearData()
                }
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> vm.cancelFlowEvents()
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.feedListFiltered.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        Box {
            TopAppBar(title = {
                Row {
                    if (vm.displayedFolder.isNotEmpty()) Text(vm.displayedFolder, modifier = Modifier.padding(end = 5.dp))
                    if (feedOperationText.isNotEmpty()) Text(feedOperationText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable {})
                    else Text(vm.feedCountState, color = textColor)
                }
            },
                navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_subscriptions), contentDescription = "Open Drawer") } },
                actions = {
                    IconButton(onClick = { mainNavController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                    IconButton(onClick = { vm.showFilterDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), tint = if (vm.isFiltered) buttonAltColor else MaterialTheme.colorScheme.onSurface, contentDescription = "filter") }
                    IconButton(onClick = { vm.showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "sort") }
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded,
                        border = BorderStroke(1.dp, buttonColor),
                        onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_synth_label)) }, onClick = {
                            vm.showNewSynthetic = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                            runOnceOrAsk(vm.context, fullUpdate = true)
                            //                        gearbox.feedUpdater(fullUpdate = true).startRefresh(vm.context)
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.toggle_grid_list)) }, onClick = {
                            vm.useGrid = if (vm.useGrid == null) !vm.useGridLayout else !vm.useGrid!!
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

        var showRemoveFeedDialog by remember { mutableStateOf(false) }
        if (showRemoveFeedDialog) RemoveFeedDialog(selected, onDismissRequest = {showRemoveFeedDialog = false}) {}

        @SuppressLint("LocalContextResourcesRead")
        fun saveFeed(cbBlock: (Feed)->Unit) {
            runOnIOScope { for (feed in selected) upsert(feed) { cbBlock(it) } }
            val numItems = selected.size
            Logt(TAG, context.resources.getQuantityString(R.plurals.updated_feeds_batch_label, numItems, numItems))
        }

        @Composable
        fun AutoDeleteHandlerDialog(onDismissRequest: () -> Unit) {
            val (selectedOption, _) = remember { mutableStateOf("") }
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, buttonColor)) {
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
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, buttonColor)) {
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
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, buttonColor)) {
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
                Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, buttonColor)) {
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
            fun onSelected() {
                isExpanded = false
                vm.selectMode = false
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
                        val actMain: MainActivity? = generateSequence(vm.context) { if (it is ContextWrapper) it.baseContext else null }.filterIsInstance<MainActivity>().firstOrNull()
                        actMain?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                            if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
                            val uri = result.data!!.data
                            vm.exportOPML(uri, selected)
                        }?.launch(intentPickAction)
                        return@clickable
                    } catch (e: ActivityNotFoundException) { Loge(TAG, "No activity found. Should never happen...") }
                    // if on SDK lower than API 21 or the implicit intent failed, fallback to the legacy export process
                    vm.exportOPML(null, selected)
                }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_import_export_24), "")
                    Text(stringResource(id = R.string.opml_export_label)) } },
            )
            val scrollState = rememberScrollState()
            Column(modifier = modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.Bottom) {
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
            if (getPref(AppPrefs.prefSwipeToRefreshAll, true)) {
//                gearbox.feedUpdater().startRefresh(vm.context)
                runOnceOrAsk(vm.context)
            }
            refreshing = false
        }) {
            val context = LocalContext.current
            if (if (vm.useGrid == null) vm.useGridLayout else vm.useGrid!!) {
                val lazyGridState = rememberLazyGridState()
                LazyVerticalGrid(state = lazyGridState, columns = GridCells.Adaptive(80.dp), modifier = Modifier.fillMaxSize(),
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
                                        feedScreenMode = FeedScreenMode.List
                                        mainNavController.navigate(Screens.FeedDetails.name)
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
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                            feedScreenMode = FeedScreenMode.Info
                                            mainNavController.navigate(Screens.FeedDetails.name)
                                        }
                                    }
                                })
                            )
                            Column(Modifier.weight(1f).padding(start = 10.dp).combinedClickable(onClick = {
                                Logd(TAG, "clicked: ${feed.title}")
                                if (!feed.isBuilding) {
                                    if (vm.selectMode) toggleSelected()
                                    else {
                                        feedOnDisplay = feed
                                        feedScreenMode = FeedScreenMode.List
                                        mainNavController.navigate(Screens.FeedDetails.name)
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
                                    if (feed.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = buttonColor, contentDescription = "rating", modifier = Modifier.width(20.dp).height(20.dp).background(MaterialTheme.colorScheme.tertiaryContainer))
                                    Text(feed.title ?: "No title", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Text(feed.author ?: "No author", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.padding(top = 5.dp)) {
                                    val measureString = remember { NumberFormat.getInstance().format(feed.episodes.size.toLong()) + " : " + DurationConverter.durationInHours(feed.totleDuration/1000) }
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
                Row(modifier = Modifier.align(Alignment.TopEnd).width(150.dp).height(45.dp).background(MaterialTheme.colorScheme.onTertiaryContainer),
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
            dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
            Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, buttonColor)) {
                val scrollState = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    val scrollStateH = rememberScrollState()
                    Row(Modifier.fillMaxWidth().horizontalScroll(scrollStateH)) {
                        OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.sortIndex != FeedSortIndex.Title.ordinal) buttonColor else buttonAltColor),
                            onClick = {
                                if (vm.sortIndex == FeedSortIndex.Title.ordinal) vm.titleAscending = !vm.titleAscending
                                vm.sortIndex = FeedSortIndex.Title.ordinal
                                fetchAndSortRoutine()
                            }
                        ) { Text(text = stringResource(FeedSortIndex.Title.res) + if (vm.titleAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                        OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.sortIndex != FeedSortIndex.Date.ordinal) buttonColor else buttonAltColor),
                            onClick = {
                                if (vm.sortIndex == FeedSortIndex.Date.ordinal) vm.dateAscending = !vm.dateAscending
                                vm.sortIndex = FeedSortIndex.Date.ordinal
                                fetchAndSortRoutine()
                            }
                        ) { Text(text = stringResource(FeedSortIndex.Date.res) + if (vm.dateAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                        OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.sortIndex != FeedSortIndex.Time.ordinal) buttonColor else buttonAltColor),
                            onClick = {
                                if (vm.sortIndex == FeedSortIndex.Time.ordinal) vm.timeAscending = !vm.timeAscending
                                vm.sortIndex = FeedSortIndex.Time.ordinal
                                fetchAndSortRoutine()
                            }
                        ) { Text(text = stringResource(FeedSortIndex.Time.res) + if (vm.timeAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                        OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.sortIndex != FeedSortIndex.Count.ordinal) buttonColor else buttonAltColor),
                            onClick = {
                                if (vm.sortIndex == FeedSortIndex.Count.ordinal) vm.countAscending = !vm.countAscending
                                vm.sortIndex = FeedSortIndex.Count.ordinal
                                fetchAndSortRoutine()
                            }
                        ) { Text(text = stringResource(FeedSortIndex.Count.res) + if (vm.countAscending) "\u00A0▲" else "\u00A0▼", color = textColor) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
                    if (vm.sortIndex == FeedSortIndex.Date.ordinal) {
                        Row {
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.dateSortIndex != FeedDateSortIndex.Played.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    vm.dateSortIndex = FeedDateSortIndex.Played.ordinal
                                    fetchAndSortRoutine()
                                }
                            ) { Text(stringResource(FeedDateSortIndex.Played.res)) }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.dateSortIndex != FeedDateSortIndex.Downloaded.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    vm.dateSortIndex = FeedDateSortIndex.Downloaded.ordinal
                                    fetchAndSortRoutine()
                                }
                            ) { Text(stringResource(FeedDateSortIndex.Downloaded.res)) }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.dateSortIndex != FeedDateSortIndex.Commented.ordinal) buttonColor else buttonAltColor),
                                onClick = {
                                    vm.dateSortIndex = FeedDateSortIndex.Commented.ordinal
                                    fetchAndSortRoutine()
                                }
                            ) { Text(stringResource(FeedDateSortIndex.Commented.res)) }
                        }
                        OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.dateSortIndex != FeedDateSortIndex.Publish.ordinal) buttonColor else buttonAltColor),
                            onClick = {
                                vm.dateSortIndex = FeedDateSortIndex.Publish.ordinal
                                fetchAndSortRoutine()
                            }
                        ) { Text(stringResource(FeedDateSortIndex.Publish.res)) }
                    } else if (vm.sortIndex == FeedSortIndex.Time.ordinal) {
                        Row {
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.timeSortIndex != 1) buttonColor else buttonAltColor),
                                onClick = {
                                    vm.timeSortIndex = 1
                                    fetchAndSortRoutine()
                                }
                            ) { Text(stringResource(R.string.min_duration)) }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.timeSortIndex != 2) buttonColor else buttonAltColor),
                                onClick = {
                                    vm.timeSortIndex = 2
                                    fetchAndSortRoutine()
                                }
                            ) { Text(stringResource(R.string.max_duration)) }
                        }
                        Row {
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.timeSortIndex != 0) buttonColor else buttonAltColor),
                                onClick = {
                                    vm.timeSortIndex = 0
                                    fetchAndSortRoutine()
                                }
                            ) { Text(stringResource(R.string.total_duration)) }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(modifier = Modifier.padding(5.dp), elevation = null, border = BorderStroke(2.dp, if (vm.timeSortIndex != 3) buttonColor else buttonAltColor),
                                onClick = {
                                    vm.timeSortIndex = 3
                                    fetchAndSortRoutine()
                                }
                            ) { Text(stringResource(R.string.average_duration)) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
                    Column(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                        if (vm.sortIndex == FeedSortIndex.Count.ordinal) {
                            Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                val item = EpisodeFilter.EpisodesFilterGroup.DOWNLOADED
                                var selectNone by remember { mutableStateOf(false) }
                                if (selectNone) vm.downlaodedSortIndex = -1
                                Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(end = 10.dp))
                                Spacer(Modifier.weight(0.3f))
                                OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.downlaodedSortIndex != 0) buttonColor else buttonAltColor),
                                    onClick = {
                                        if (vm.downlaodedSortIndex != 0) {
                                            selectNone = false
                                            vm.downlaodedSortIndex = 0
                                        } else vm.downlaodedSortIndex = -1
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.properties[0].displayName), color = textColor) }
                                Spacer(Modifier.weight(0.1f))
                                OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.downlaodedSortIndex != 1) buttonColor else buttonAltColor),
                                    onClick = {
                                        if (vm.downlaodedSortIndex != 1) {
                                            selectNone = false
                                            vm.downlaodedSortIndex = 1
                                        } else vm.downlaodedSortIndex = -1
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.properties[1].displayName), color = textColor) }
                                Spacer(Modifier.weight(0.5f))
                            }
                            Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                val item = EpisodeFilter.EpisodesFilterGroup.OPINION
                                var selectNone by remember { mutableStateOf(false) }
                                if (selectNone) vm.commentedSortIndex = -1
                                Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(end = 10.dp))
                                Spacer(Modifier.weight(0.3f))
                                OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.commentedSortIndex != 0) buttonColor else buttonAltColor),
                                    onClick = {
                                        if (vm.commentedSortIndex != 0) {
                                            selectNone = false
                                            vm.commentedSortIndex = 0
                                        } else vm.commentedSortIndex = -1
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.properties[0].displayName), color = textColor) }
                                Spacer(Modifier.weight(0.1f))
                                OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (vm.commentedSortIndex != 1) buttonColor else buttonAltColor),
                                    onClick = {
                                        if (vm.commentedSortIndex != 1) {
                                            selectNone = false
                                            vm.commentedSortIndex = 1
                                        } else vm.commentedSortIndex = -1
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.properties[1].displayName), color = textColor) }
                                Spacer(Modifier.weight(0.5f))
                            }
                        }
                        if ((vm.sortIndex == FeedSortIndex.Date.ordinal && vm.dateSortIndex == FeedDateSortIndex.Publish.ordinal) || vm.sortIndex == FeedSortIndex.Count.ordinal) {
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
                                            val hIndex = vm.episodeStateSort.indexOfLast { it.value }
                                            if (hIndex < 0) return@clickable
                                            if (!lowerSelected) {
                                                for (i in 0..hIndex) vm.episodeStateSort[i].value = true
                                            } else {
                                                for (i in 0..hIndex) vm.episodeStateSort[i].value = false
                                                vm.episodeStateSort[hIndex].value = true
                                            }
                                            lowerSelected = !lowerSelected
                                            fetchAndSortRoutine()
                                        })
                                    Spacer(Modifier.weight(1f))
                                    Text("A", color = buttonColor, style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.clickable {
                                            val selectAll = !(lowerSelected && higherSelected)
                                            lowerSelected = selectAll
                                            higherSelected = selectAll
                                            for (i in item.properties.indices) vm.episodeStateSort[i].value = selectAll
                                            fetchAndSortRoutine()
                                        })
                                    Spacer(Modifier.weight(1f))
                                    Text(">>>", color = if (higherSelected) buttonAltColor else buttonColor, style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.clickable {
                                            val lIndex = vm.episodeStateSort.indexOfFirst { it.value }
                                            if (lIndex < 0) return@clickable
                                            if (!higherSelected) {
                                                for (i in lIndex..<item.properties.size) vm.episodeStateSort[i].value = true
                                            } else {
                                                for (i in lIndex..<item.properties.size) vm.episodeStateSort[i].value = false
                                                vm.episodeStateSort[lIndex].value = true
                                            }
                                            higherSelected = !higherSelected
                                            fetchAndSortRoutine()
                                        })
                                }
                                Spacer(Modifier.weight(1f))
                            }
                            if (expandRow) NonlazyGrid(columns = 3, itemCount = item.properties.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                if (selectNone) vm.episodeStateSort[index].value = false
                                OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (vm.episodeStateSort[index].value) buttonAltColor else buttonColor),
                                    onClick = {
                                        selectNone = false
                                        vm.episodeStateSort[index].value = !vm.episodeStateSort[index].value
                                        fetchAndSortRoutine()
                                    },
                                ) { Text(text = stringResource(item.properties[index].displayName), maxLines = 1, color = textColor) }
                            }
                        }
                        if (vm.sortIndex == FeedSortIndex.Count.ordinal) {
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
                                    Text("A", color = buttonColor, style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.clickable {
                                            val selectAll = !(lowerSelected && higherSelected)
                                            lowerSelected = selectAll
                                            higherSelected = selectAll
                                            for (i in item.properties.indices) vm.ratingSort[i].value = selectAll
                                            fetchAndSortRoutine()
                                        })
                                    Spacer(Modifier.weight(1f))
                                    Text(">>>", color = if (higherSelected) buttonAltColor else buttonColor, style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.clickable {
                                            val lIndex = vm.ratingSort.indexOfFirst { it.value }
                                            if (lIndex < 0) return@clickable
                                            if (!higherSelected) {
                                                for (i in lIndex..<item.properties.size) vm.ratingSort[i].value = true
                                            } else {
                                                for (i in lIndex..<item.properties.size) vm.ratingSort[i].value = false
                                                vm.ratingSort[lIndex].value = true
                                            }
                                            higherSelected = !higherSelected
                                            fetchAndSortRoutine()
                                        })
                                }
                                Spacer(Modifier.weight(1f))
                            }
                            if (expandRow) NonlazyGrid(columns = 3, itemCount = item.properties.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                if (selectNone) vm.ratingSort[index].value = false
                                OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (vm.ratingSort[index].value) buttonAltColor else buttonColor),
                                    onClick = {
                                        selectNone = false
                                        vm.ratingSort[index].value = !vm.ratingSort[index].value
                                        fetchAndSortRoutine()
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

        fun onFilterChanged(newFilterValues: Set<String>) {
            vm.feedsFilter = StringUtils.join(newFilterValues, ",")
            Logd(TAG, "onFilterChanged: ${vm.feedsFilter}")
            vm.loadSubscriptions(true)
        }
        Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
            val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
            dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
            Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, buttonColor)) {
                Column(Modifier.fillMaxSize()) {
                    val scrollStateV = rememberScrollState()
                    Column(Modifier.fillMaxSize().verticalScroll(scrollStateV)) {
                        if (languages.size > 1) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val selectedList = remember { MutableList(vm.languages.size) { mutableStateOf(false) } }
                                var expandRow by remember { mutableStateOf(false) }
                                Row(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                                    Text(stringResource(R.string.languages) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = buttonColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                    if (expandRow) {
                                        val cb = {
                                            val langsSel = mutableSetOf<String>()
                                            for (i in vm.languages.indices) {
                                                if (selectedList[i].value) langsSel.add(vm.languages[i])
                                            }
                                            vm.langsSel = langsSel
                                            vm.loadSubscriptions(true)
                                        }
                                        SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                    }
                                }
                                if (expandRow) NonlazyGrid(columns = 3, itemCount = vm.languages.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                    LaunchedEffect(Unit) {
                                        if (vm.languages[index] in vm.langsSel) selectedList[index].value = true
                                    }
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                        onClick = {
                                            selectedList[index].value = !selectedList[index].value
                                            val langsSel = vm.langsSel.toMutableSet()
                                            if (selectedList[index].value) langsSel.add(vm.languages[index])
                                            else langsSel.remove(vm.languages[index])
                                            vm.langsSel = langsSel
                                            vm.loadSubscriptions(true)
                                        },
                                    ) { Text(text = vm.languages[index], maxLines = 1, color = textColor) }
                                }
                            }
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val selectedList = remember { MutableList(vm.queueNames.size) { mutableStateOf(false) } }
                            var expandRow by remember { mutableStateOf(false) }
                            Row(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                                Text(stringResource(R.string.queue_label) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = buttonColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                if (expandRow) {
                                    val cb = {
                                        val qSelIds = mutableSetOf<Long>()
                                        for (i in vm.queueNames.indices) {
                                            if (selectedList[i].value) qSelIds.add(vm.queueIds[i])
                                        }
                                        vm.qSelIds = qSelIds
                                        vm.loadSubscriptions(true)
                                    }
                                    SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                }
                            }
                            if (expandRow) NonlazyGrid(columns = 3, itemCount = vm.queueNames.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                LaunchedEffect(Unit) {
                                    if (vm.queueIds[index] in vm.qSelIds) selectedList[index].value = true
                                }
                                OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                    onClick = {
                                        selectedList[index].value = !selectedList[index].value
                                        val qSelIds = vm.qSelIds.toMutableSet()
                                        if (selectedList[index].value) qSelIds.add(vm.queueIds[index])
                                        else qSelIds.remove(vm.queueIds[index])
                                        vm.qSelIds = qSelIds
                                        vm.loadSubscriptions(true)
                                    },
                                ) { Text(text = vm.queueNames[index], maxLines = 1, color = textColor) }
                            }
                        }
                        if (vm.tags.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val selectedList = remember { MutableList(vm.tags.size) { mutableStateOf(false) } }
                                var expandRow by remember { mutableStateOf(false) }
                                Row(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                                    Text(stringResource(R.string.tags_label) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = buttonColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                    if (expandRow) {
                                        val cb = {
                                            val tagsSel = mutableSetOf<String>()
                                            for (i in vm.tags.indices) {
                                                if (selectedList[i].value) tagsSel.add(vm.tags[i])
                                            }
                                            vm.tagsSel = tagsSel
                                            vm.loadSubscriptions(true)
                                        }
                                        SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                    }
                                }
                                if (expandRow) NonlazyGrid(columns = 3, itemCount = vm.tags.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                    LaunchedEffect(Unit) {
                                        if (vm.tags[index] in vm.tagsSel) selectedList[index].value = true
                                    }
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                        onClick = {
                                            selectedList[index].value = !selectedList[index].value
                                            val tagsSel = vm.tagsSel.toMutableSet()
                                            if (selectedList[index].value) tagsSel.add(vm.tags[index])
                                            else tagsSel.remove(vm.tags[index])
                                            vm.tagsSel = tagsSel
                                            vm.loadSubscriptions(true)
                                        },
                                    ) { Text(text = vm.tags[index], maxLines = 1, color = textColor) }
                                }
                            }
                        }

                        var selectNone by remember { mutableStateOf(false) }
                        for (item in FeedFilter.FeedFilterGroup.entries) {
                            if (item.values.size == 2) {
                                Row(modifier = Modifier.padding(start = 5.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Absolute.Left, verticalAlignment = Alignment.CenterVertically) {
                                    var selectedIndex by remember { mutableIntStateOf(-1) }
                                    if (selectNone) selectedIndex = -1
                                    LaunchedEffect(Unit) {
                                        if (filter != null) {
                                            if (item.values[0].filterId in filter.properties) selectedIndex = 0
                                            else if (item.values[1].filterId in filter.properties) selectedIndex = 1
                                        }
                                    }
                                    Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = textColor, modifier = Modifier.padding(end = 10.dp))
                                    Spacer(Modifier.width(30.dp))
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp), border = BorderStroke(2.dp, if (selectedIndex != 0) buttonColor else buttonAltColor),
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
                                    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp), border = BorderStroke(2.dp, if (selectedIndex != 1) buttonColor else buttonAltColor),
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
//                                    Spacer(Modifier.weight(0.5f))
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val selectedList = remember { MutableList(item.values.size) { mutableStateOf(false) } }
                                    var expandRow by remember { mutableStateOf(false) }
                                    Row(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                                        Text(stringResource(item.nameRes) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = buttonColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                        if (expandRow) {
                                            val cb = {
                                                for (i in item.values.indices) {
                                                    if (selectedList[i].value) filterValues.add(item.values[i].filterId)
                                                    else filterValues.remove(item.values[i].filterId)
                                                }
                                                onFilterChanged(filterValues)
                                            }
                                            SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                        }
                                    }
                                    if (expandRow) NonlazyGrid(columns = 3, itemCount = item.values.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                                        if (selectNone) selectedList[index].value = false
                                        LaunchedEffect(Unit) {
                                            if (filter != null && item.values[index].filterId in filter.properties) selectedList[index].value = true
                                        }
                                        OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
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
                        Row(modifier = Modifier.fillMaxWidth()) {
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
    }

    if (vm.showFilterDialog) FilterDialog(FeedFilter(vm.feedsFilter)) { vm.showFilterDialog = false }
    if (vm.showSortDialog) SortDialog { vm.showSortDialog = false }
    if (vm.showNewSynthetic) RenameOrCreateSyntheticFeed { vm.showNewSynthetic = false }
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

private const val TAG = "SubscriptionsScreen"

//private var prevFeedUpdatingEvent: FlowEvent.FeedUpdatingEvent? = null

