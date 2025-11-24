package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.feed.searcher.ItunesPodcastSearcher
import ac.mdiq.podcini.net.feed.searcher.ItunesTopListLoader
import ac.mdiq.podcini.net.feed.searcher.PodcastIndexPodcastSearcher
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.OpmlBackupAgent.Companion.performRestore
import ac.mdiq.podcini.preferences.OpmlTransporter
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlElement
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.NonlazyGrid
import ac.mdiq.podcini.ui.compose.OpmlImportSelectionDialog
import ac.mdiq.podcini.ui.compose.SearchBarRow
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
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
import kotlinx.coroutines.withContext
import java.util.Locale


class OnlineSearchVM(val context: Context, val lcScope: CoroutineScope) {
    val prefs: SharedPreferences by lazy { context.getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE) }

    init {
        lcScope.launch(Dispatchers.IO) { prefs }
    }

    internal var mainAct: MainActivity? = null

    internal var showError by mutableStateOf(false)
    internal var errorText by mutableStateOf("")
    internal var showPowerBy by mutableStateOf(false)
    internal var showRetry by mutableStateOf(false)
    internal var retryTextRes by mutableIntStateOf(0)
    internal var showGrid by mutableStateOf(false)
    internal var showProgress by mutableStateOf(false)

    internal val showOPMLRestoreDialog = mutableStateOf(false)
    internal val numberOPMLFeedsToRestore = mutableIntStateOf(0)
    internal var numColumns by mutableIntStateOf(4)
    internal val searchResult = mutableStateListOf<PodcastSearchResult>()

    internal var showOpmlImportSelectionDialog by mutableStateOf(false)
    internal val readElements = mutableStateListOf<OpmlElement>()

    internal var eventSink: Job?     = null
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    internal fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.DiscoveryDefaultUpdateEvent -> loadToplist()
                    else -> {}
                }
            }
        }
    }

    internal fun loadToplist() {
        showError = false
        showPowerBy = true
        showRetry = false
        retryTextRes = R.string.retry_label
        val loader = ItunesTopListLoader(context)
        val countryCode: String = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)!!
        if (prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)) {
            showError = true
            errorText = context.getString(R.string.discover_is_hidden)
            showPowerBy = false
            showRetry = false
            return
        }
        if (BuildConfig.FLAVOR == "free" && prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)) {
            showError = true
            errorText = ""
            showGrid = true
            showRetry = true
            retryTextRes = R.string.discover_confirm
            showPowerBy = true
            return
        }

        lcScope.launch {
            try {
                val searchResults_ = withContext(Dispatchers.IO) { loader.loadToplist(countryCode, NUM_SUGGESTIONS, getFeedList()) }
                withContext(Dispatchers.Main) {
                    showError = false
                    if (searchResults_.isEmpty()) {
                        errorText = context.getString(R.string.search_status_no_results)
                        showError = true
                        showGrid = false
                    } else {
                        showGrid = true
                        searchResult.clear()
                        searchResult.addAll(searchResults_)
                    }
                }
            } catch (e: Throwable) {
                Logs(TAG, e)
                showError = true
                showGrid = false
                showRetry = true
                errorText = e.localizedMessage ?: ""
            }
        }
    }
}

@Composable
fun OnlineSearchScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = LocalNavController.current
    val vm = remember { OnlineSearchVM(context, scope) }
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    vm.mainAct = context as? MainActivity
                    Logd(TAG, "fragment onCreateView")
                    val windowWidthDp: Dp = with(density) { windowSize.width.toDp() }
                    if (windowWidthDp > 600.dp) vm.numColumns = 6

                    // Fill with dummy elements to have a fixed height and
                    // prevent the UI elements below from jumping on slow connections
                    for (i in 0 until NUM_SUGGESTIONS) vm.searchResult.add(PodcastSearchResult.dummy())
                    val PAFeed = realm.query(PAFeed::class).find()
                    Logd(TAG, "size of directory: ${PAFeed.size}")
                    vm.loadToplist()
                    if (getPref(AppPrefs.prefOPMLRestore, false) && feedCount == 0) {
                        vm.numberOPMLFeedsToRestore.intValue = getPref(AppPrefs.prefOPMLFeedsToRestore, 0)
                        vm.showOPMLRestoreDialog.value = true
                    }
                }
                Lifecycle.Event.ON_START -> vm.procFlowEvents()
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> vm.cancelFlowEvents()
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val actionColor = MaterialTheme.colorScheme.tertiary
    val scrollState = rememberScrollState()
    ComfirmDialog(R.string.restore_subscriptions_label, stringResource(R.string.restore_subscriptions_summary, vm.numberOPMLFeedsToRestore.intValue), vm.showOPMLRestoreDialog) {
        vm.showProgress = true
        performRestore(context)
        vm.showProgress = false
    }
    val chooseOpmlImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        OpmlTransporter.startImport(context, uri) { vm.readElements.addAll(it) }
        vm.showOpmlImportSelectionDialog = true
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
                    dirFeed.episodeSortOrder = EpisodeSortOrder.EPISODE_TITLE_A_Z
                    updateFeedFull(context, dirFeed, removeUnlistedItems = false)
                    val fromDatabase: Feed? = getFeed(dirFeed.id)
                    gearbox.feedUpdater(fromDatabase).startRefresh(context)
                    Logd(TAG, "addLocalFolderLauncher fromDatabase episodes: ${fromDatabase?.episodes?.size}")
                    fromDatabase
                }
                withContext(Dispatchers.Main) {
                    if (feed != null) {
                        feedOnDisplay = feed
                        feedScreenMode = FeedScreenMode.List
                        navController.navigate(Screens.FeedDetails.name)
                    }
                }
            } catch (e: Throwable) { Logs(TAG, e, e.localizedMessage?: "No messaage") }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
     fun MyTopAppBar() {
        Box {
            TopAppBar(title = {
                SearchBarRow(R.string.search_podcast_hint, "") { queryText ->
                    if (queryText.isBlank()) return@SearchBarRow
                    if (queryText.matches("http[s]?://.*".toRegex())) {
                        setOnlineFeedUrl(queryText)
                        navController.navigate(Screens.OnlineFeed.name)
                    } else {
                        setOnlineSearchTerms(CombinedSearcher::class.java, queryText)
                        navController.navigate(Screens.OnlineResults.name)
                    }
                }
            }, navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @Composable
     fun QuickDiscoveryView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val context = LocalContext.current
        val actionColor = MaterialTheme.colorScheme.tertiary
        Column(modifier = Modifier.padding(vertical = 5.dp)) {
            Row(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(stringResource(R.string.discover), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.discover_more), color = actionColor, modifier = Modifier.clickable(onClick = { navController.navigate(Screens.Discovery.name) }))
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                if (vm.showGrid) NonlazyGrid(columns = vm.numColumns, itemCount = vm.searchResult.size, modifier = Modifier.fillMaxWidth()) { index ->
                    AsyncImage(model = ImageRequest.Builder(context).data(vm.searchResult[index].imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(), contentDescription = "imgvCover",
                        modifier = Modifier.padding(top = 8.dp)
                            .clickable(onClick = {
                                Logd(TAG, "icon clicked!")
                                val podcast: PodcastSearchResult = vm.searchResult[index]
                                if (!podcast.feedUrl.isNullOrEmpty()) {
                                    setOnlineFeedUrl(podcast.feedUrl)
                                    navController.navigate(Screens.OnlineFeed.name)
                                }
                            }))
                }
                if (vm.showError) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(vm.errorText, color = textColor)
                    if (vm.showRetry) Button(onClick = {
                        vm.prefs.edit { putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false) }
                        vm.loadToplist()
                    }) { Text(stringResource(vm.retryTextRes)) }
                }
            }
            Text(stringResource(R.string.discover_powered_by_itunes), color = textColor, modifier = Modifier.align(Alignment.End))
        }
    }

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        if (vm.showProgress) Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            CircularProgressIndicator(progress = {0.6f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp).align(Alignment.Center))
        } else Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 10.dp).background(MaterialTheme.colorScheme.surface).verticalScroll(scrollState)) {
            QuickDiscoveryView()
            Text(stringResource(R.string.advanced), color = textColor, fontWeight = FontWeight.Bold)
//            Text(stringResource(R.string.add_podcast_by_url), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { vm.showAddViaUrlDialog() }))
            Text(stringResource(R.string.add_local_folder), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                try { addLocalFolderLauncher.launch(null) } catch (e: ActivityNotFoundException) { Logs(TAG, e, context.getString(R.string.unable_to_start_system_file_manager)) }
            }))
            gearbox.GearSearchText()
            Text(stringResource(R.string.search_itunes_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                setOnlineSearchTerms(ItunesPodcastSearcher::class.java)
                navController.navigate(Screens.OnlineResults.name)
            }))
            Text(stringResource(R.string.search_podcastindex_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                setOnlineSearchTerms(PodcastIndexPodcastSearcher::class.java)
                navController.navigate(Screens.OnlineResults.name)
            }))
            if (vm.showOpmlImportSelectionDialog) OpmlImportSelectionDialog(vm.readElements) { vm.showOpmlImportSelectionDialog = false }
            Text(stringResource(R.string.opml_add_podcast_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                try { chooseOpmlImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Logs(TAG, e, context.getString(R.string.unable_to_start_system_file_manager)) }
            }))
        }
    }
}

class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private const val TAG = "OnlineSearchScreen"

private const val NUM_SUGGESTIONS = 12


