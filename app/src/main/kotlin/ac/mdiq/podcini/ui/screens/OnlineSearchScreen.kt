package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.feed.searcher.ItunesPodcastSearcher
import ac.mdiq.podcini.net.feed.searcher.PodcastIndexPodcastSearcher
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.net.feed.searcher.PodcastSearcher
import ac.mdiq.podcini.net.feed.searcher.PodcastSearcherRegistry
import ac.mdiq.podcini.net.utils.NetworkUtils.prepareUrl
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.OpmlBackupAgent.Companion.performRestore
import ac.mdiq.podcini.preferences.OpmlTransporter
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlElement
import ac.mdiq.podcini.storage.database.feedCount
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.SubscriptionLog.Companion.feedLogsMap
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommonDialogSurface
import ac.mdiq.podcini.ui.compose.OnlineFeedItem
import ac.mdiq.podcini.ui.compose.OpmlImportSelectionDialog
import ac.mdiq.podcini.ui.compose.SearchBarRow
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private var searchText by mutableStateOf("")
private var searchProvider by mutableStateOf<PodcastSearcher?>(null)
fun setOnlineSearchTerms(searchProvider_: Class<out PodcastSearcher?>, query: String? = null) {
    searchText = query ?: ""
    searchProvider = searchProvider_.getDeclaredConstructor().newInstance()
}

class OnlineSearchVM(val context: Context, val lcScope: CoroutineScope) {

    internal val feedLogs = feedLogsMap!!

    internal var searchResults = mutableStateListOf<PodcastSearchResult>()
    internal var errorText by mutableStateOf("")
    internal var retryQerry by mutableStateOf("")
    internal var showProgress by mutableStateOf(false)
    internal var noResultText by mutableStateOf("")

    internal val readElements = mutableStateListOf<OpmlElement>()

    init {
        setOnlineSearchTerms(CombinedSearcher::class.java, searchText)
    }

    private var searchJob: Job? = null
    @SuppressLint("StringFormatMatches")
    internal fun search(query: String) {
        if (query.isBlank()) return
        if (searchJob != null) {
            searchJob?.cancel()
            searchResults.clear()
        }
        errorText = ""
        retryQerry = ""
        showProgress = true
        searchJob = lcScope.launch(Dispatchers.IO) {
            val feeds = getFeedList()
            fun feedId(r: PodcastSearchResult): Long {
                for (f in feeds) if (f.downloadUrl == r.feedUrl) return f.id
                return 0L
            }
            try {
                val result = searchProvider?.search(query) ?: listOf()
                for (r in result) r.feedId = feedId(r)
                searchResults.clear()
                searchResults.addAll(result)
                withContext(Dispatchers.Main) {
                    showProgress = false
                    noResultText = context.getString(R.string.no_results_for_query, query)
                }
            } catch (e: Exception) {
                showProgress = false
                errorText = e.toString()
                retryQerry = query
            }
        }.apply { invokeOnCompletion { searchJob = null } }
    }
}

@Composable
fun OnlineSearchScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val vm = remember { OnlineSearchVM(context, scope) }

     var showOpmlImportSelectionDialog by remember { mutableStateOf(false) }
     val showOPMLRestoreDialog = remember { mutableStateOf(false) }
     val numberOPMLFeedsToRestore = remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    if (getPref(AppPrefs.prefOPMLRestore, false) && feedCount == 0) {
                        numberOPMLFeedsToRestore.intValue = getPref(AppPrefs.prefOPMLFeedsToRestore, 0)
                        showOPMLRestoreDialog.value = true
                    }
                    for (info in PodcastSearcherRegistry.searchProviders) {
                        Logd(TAG, "searchProvider: $info")
                        if (info.searcher.javaClass.name == searchProvider?.name) {
                            searchProvider = info.searcher
                            break
                        }
                    }
                    if (searchProvider == null) Loge(TAG,"Podcast searcher not found")
                    vm.search(searchText)
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
            vm.searchResults.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        Box {
            TopAppBar(title = {
                SearchBarRow(R.string.search_podcast_hint, searchText) { queryText ->
                    if (queryText.isBlank()) return@SearchBarRow
                    if (queryText.matches("http[s]?://.*".toRegex())) navController.navigate("${Screens.OnlineFeed.name}?url=${URLEncoder.encode(queryText, StandardCharsets.UTF_8.name())}")
                    else vm.search(queryText)
                }
            }, navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val actionColor = MaterialTheme.colorScheme.tertiary
    ComfirmDialog(R.string.restore_subscriptions_label, stringResource(R.string.restore_subscriptions_summary, numberOPMLFeedsToRestore.intValue), showOPMLRestoreDialog) {
        vm.showProgress = true
        performRestore(context)
        vm.showProgress = false
    }
    val chooseOpmlImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        OpmlTransporter.startImport(context, uri) { vm.readElements.addAll(it) }
        showOpmlImportSelectionDialog = true
    }
    val addLocalFolderLauncher = rememberLauncherForActivityResult(AddLocalFolder()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val feed = withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    requireNotNull(documentFile) { "Unable to retrieve document tree" }
                    val title = documentFile.name ?: context.getString(R.string.local_folder)

                    val dirFeed = Feed(Feed.PREFIX_LOCAL_FOLDER + uri.toString(), null, title)
                    Logd(TAG, "addLocalFolderLauncher dirFeed episodes: ${dirFeed.episodes.size}")
                    //                    dirFeed.episodes.clear()
                    dirFeed.episodeSortOrder = EpisodeSortOrder.EPISODE_TITLE_ASC
                    updateFeedFull(context, dirFeed, removeUnlistedItems = false)
                    val fd: Feed? = getFeed(dirFeed.id)
                    if (fd != null) gearbox.feedUpdater(listOf(fd)).startRefresh(context)
                    Logd(TAG, "addLocalFolderLauncher fromDatabase episodes: ${fd?.episodes?.size}")
                    fd
                }
                withContext(Dispatchers.Main) { if (feed != null) navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.id}") }
            } catch (e: Throwable) { Logs(TAG, e, e.localizedMessage?: "No messaage") }
        }
    }

    if (showOpmlImportSelectionDialog) OpmlImportSelectionDialog(vm.readElements) { showOpmlImportSelectionDialog = false }

    var showAdvanced by remember { mutableStateOf(false) }
    if (showAdvanced) CommonDialogSurface({ showAdvanced = false }) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.search_combined_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { setOnlineSearchTerms(CombinedSearcher::class.java) }))
            gearbox.GearSearchText()
            Text(stringResource(R.string.search_itunes_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { setOnlineSearchTerms(ItunesPodcastSearcher::class.java) }))
            Text(stringResource(R.string.search_podcastindex_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { setOnlineSearchTerms(PodcastIndexPodcastSearcher::class.java) }))
            Text(stringResource(R.string.add_local_folder), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                try { addLocalFolderLauncher.launch(null) } catch (e: ActivityNotFoundException) { Logs(TAG, e, context.getString(R.string.unable_to_start_system_file_manager)) }
            }))
            Text(stringResource(R.string.opml_add_podcast_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                try { chooseOpmlImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Logs(TAG, e, context.getString(R.string.unable_to_start_system_file_manager)) }
            }))
        }
    }

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        ConstraintLayout(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val (controlRow, gridView, progressBar, empty, txtvError, butRetry, powered) = createRefs()
            Row(modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp).fillMaxWidth().constrainAs(controlRow) { top.linkTo(parent.top) }) {
                Text(stringResource(R.string.top_chart), color = actionColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = { navController.navigate(Screens.TopChartFeeds.name) }))
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.advanced), color = actionColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = { showAdvanced = !showAdvanced }))
            }

            if (vm.showProgress) CircularProgressIndicator(progress = { 0.6f }, strokeWidth = 10.dp, modifier = Modifier.size(50.dp).constrainAs(progressBar) { centerTo(parent) })
            if (vm.searchResults.isNotEmpty()) LazyColumn(state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp).constrainAs(gridView) {
                    top.linkTo(controlRow.bottom)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                }) {
                items(vm.searchResults.size) { index ->
                    val result = vm.searchResults[index]
                    val urlPrepared by remember { mutableStateOf(prepareUrl(result.feedUrl!!)) }
                    val sLog = remember { mutableStateOf(vm.feedLogs[urlPrepared] ?: vm.feedLogs[result.title]) }
//                    Logd(TAG, "result: ${result.feedUrl} ${feedLogs[urlPrepared]}")
                    OnlineFeedItem(result, sLog.value)
                }
            }
            if (vm.searchResults.isEmpty()) Text(vm.noResultText, color = textColor, modifier = Modifier.constrainAs(empty) { centerTo(parent) })
            if (vm.errorText.isNotEmpty()) Text(vm.errorText, color = textColor, modifier = Modifier.constrainAs(txtvError) { centerTo(parent) })
            if (vm.retryQerry.isNotEmpty()) Button(modifier = Modifier.padding(16.dp).constrainAs(butRetry) { top.linkTo(txtvError.bottom) }, onClick = { vm.search(vm.retryQerry) }) {
                Text(stringResource(id = R.string.retry_label))
            }
            Text(context.getString(R.string.search_powered_by, searchProvider!!.name), color = Color.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(Color.LightGray)
                .constrainAs(powered) {
                    bottom.linkTo(parent.bottom)
                    end.linkTo(parent.end)
                })
        }
    }
}

class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private const val TAG: String = "OnlineSearchScreen"
