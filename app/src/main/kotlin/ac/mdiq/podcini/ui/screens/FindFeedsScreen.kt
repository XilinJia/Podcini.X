package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.config.settings.OpmlBackupAgent.Companion.performRestore
import ac.mdiq.podcini.config.settings.OpmlTransporter
import ac.mdiq.podcini.config.settings.OpmlTransporter.OpmlElement
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.CombinedSearcher
import ac.mdiq.podcini.net.feed.ItunesPodcastSearcher
import ac.mdiq.podcini.net.feed.PodcastIndexPodcastSearcher
import ac.mdiq.podcini.net.feed.PodcastSearchResult
import ac.mdiq.podcini.net.feed.PodcastSearcher
import ac.mdiq.podcini.net.utils.NetworkUtils.prepareUrl
import ac.mdiq.podcini.storage.database.addNewFeed
import ac.mdiq.podcini.storage.database.allFeeds
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.feedByIdentityOrID
import ac.mdiq.podcini.storage.database.feedCount
import ac.mdiq.podcini.storage.database.getId
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.SearchHistorySize
import ac.mdiq.podcini.storage.model.SubscriptionLog.Companion.feedLogsMap
import ac.mdiq.podcini.storage.model.Volume
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.utils.AddLocalFolder
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.OnlineFeedItem
import ac.mdiq.podcini.ui.compose.OpmlImportSelectionDialog
import ac.mdiq.podcini.ui.compose.SearchBarRow
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private var searchText by mutableStateOf("")
private var searchProvider by mutableStateOf<PodcastSearcher>(CombinedSearcher::class.java.getDeclaredConstructor().newInstance())

fun setOnlineSearchTerms(searchProvider_: Class<out PodcastSearcher?> = CombinedSearcher::class.java, query: String? = null) {
    Logd(TAG, "setOnlineSearchTerms: query: $query")
    searchText = query ?: ""
    searchProvider = searchProvider_.getDeclaredConstructor().newInstance()
}

class FindFeedsVM: ViewModel() {
    internal val searchResults = mutableStateListOf<PodcastSearchResult>()

    internal val readElements = mutableStateListOf<OpmlElement>()

    var showOpmlImportSelectionDialog by mutableStateOf(false)
    val showOPMLRestoreDialog = mutableStateOf(false)
    val numberOPMLFeedsToRestore = mutableIntStateOf(0)

    var showProgress by mutableStateOf(false)
    var errorText by mutableStateOf("")
    var retryQerry by mutableStateOf("")

    var searchJob: Job? = null

    init {
        if (appPrefs.OPMLRestored && feedCount == 0) {
            numberOPMLFeedsToRestore.intValue = appPrefs.OPMLFeedsToRestore
            showOPMLRestoreDialog.value = true
        }
        search(searchText)
    }

    @SuppressLint("StringFormatMatches")
    fun search(query: String) {
        if (query.isBlank()) return
        if (searchJob != null) {
            searchJob?.cancel()
            searchResults.clear()
        }
        errorText = ""
        retryQerry = ""
        showProgress = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            fun feedId(r: PodcastSearchResult): Long {
                for (f in allFeeds) if (f.downloadUrl == r.feedUrl) return f.id
                return 0L
            }
            try {
                val result = searchProvider.search(query)
                for (r in result) r.feedId = feedId(r)
                searchResults.clear()
                searchResults.addAll(result)
                searchResults.sortBy { it.title }
                withContext(Dispatchers.Main) { showProgress = false }
            } catch (e: Exception) {
                showProgress = false
                errorText = e.toString()
                retryQerry = query
            }
        }.apply { invokeOnCompletion { searchJob = null } }
    }

    fun addLocalFloder(uri: Uri) {
        val context = getAppContext()
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        runOnIOScope {
            try {
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                requireNotNull(documentFile) { "Unable to retrieve document tree" }

                val feeds = mutableListOf<Feed>()
                val volumes = mutableListOf<Volume>()
                fun traverseDirectory(directory: DocumentFile?, parentId: Long = -1) {
                    if (directory == null || !directory.isDirectory) return

                    val content = directory.listFiles()
                    val filesInThisDir = content.filter { it.isFile }

                    if (filesInThisDir.isNotEmpty()) {
                        Logd(TAG,"Found files in folder: ${directory.uri}")
                        val uri = directory.uri
                        val title = directory.name ?: context.getString(R.string.local_folder)
                        val dirFeed = Feed(Feed.PREFIX_LOCAL_FOLDER + uri.toString(), null, title)
                        val fExist = feedByIdentityOrID(dirFeed)
                        if (fExist == null) {
                            dirFeed.volumeId = parentId
                            dirFeed.episodeSortOrder = EpisodeSortOrder.EPISODE_TITLE_ASC
                            dirFeed.keepUpdated = false
                            dirFeed.autoDownload = false
                            dirFeed.description = context.getString(R.string.local_feed_description)
                            dirFeed.author = context.getString(R.string.local_folder)
                            addNewFeed(dirFeed)
                            feeds.add(dirFeed)
                        } else Logt(TAG, "local feed already exists: $title $uri")
                    }

                    val subDirsInThisDir = content.filter { it.isDirectory }
                    if (subDirsInThisDir.isNotEmpty()) {
                        val v: Volume
                        val vExist = realm.query(Volume::class).query("uriString == $0", directory.uri.toString()).first().find()
                        if (vExist == null) {
                            v = Volume()
                            v.id = getId()
                            v.name = directory.name ?: "no name"
                            v.uriString = directory.uri.toString()
                            v.parentId = parentId
                            v.isLocal = true
                            volumes.add(v)
                            Logd(TAG, "Created volume: ${v.name} $parentId")
                        } else v = realm.copyFromRealm(vExist)

                        for (subDir in subDirsInThisDir) traverseDirectory(subDir, v.id)
                    }
                }

                traverseDirectory(documentFile)
                if (volumes.isNotEmpty()) realm.write { for (v in volumes) copyToRealm(v) }
                if (feeds.isNotEmpty()) gearbox.feedUpdater(feeds, doItAnyway = true).startRefresh()
                Logt(TAG, "Imported ${feeds.size} local feeds in ${volumes.size} volumes")
            } catch (e: Throwable) { Logs(TAG, e, e.localizedMessage?: "No messaage") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
        searchJob = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindFeedsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val drawerController = LocalDrawerController.current

    val vm: FindFeedsVM = viewModel()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect Lifecycle.Event: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {}
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> {}
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

    
    @Composable
    fun MyTopAppBar() {
        Box {
            TopAppBar(title = {
                Logd(TAG, "Topbar searchText: $searchText")
                SearchBarRow(R.string.search_podcast_hint, modifier = Modifier.fillMaxWidth(), defaultText = searchText, history = appAttribs.onlineSearchHistory) { str ->
                    if (str.isBlank()) return@SearchBarRow
                    searchText = str
                    upsertBlk(appAttribs) {
                        if (str in it.onlineSearchHistory) it.onlineSearchHistory.remove(str)
                        it.onlineSearchHistory.add(0, str)
                        if (it.onlineSearchHistory.size > SearchHistorySize+4) it.onlineSearchHistory.apply { subList(SearchHistorySize, size).clear() }
                    }
                    if (str.matches("http[s]?://.*".toRegex())) navController.navigate("${Screens.OnlineFeed.name}?url=${str.encodeURLParameter()}")
                    else vm.search(str)
                }
            }, navigationIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Open Drawer", modifier = Modifier.padding(7.dp).clickable(onClick = { drawerController?.open() })) } )
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val actionColor = MaterialTheme.colorScheme.tertiary
    ComfirmDialog(R.string.restore_subscriptions_label, stringResource(R.string.restore_subscriptions_summary, vm.numberOPMLFeedsToRestore.intValue), vm.showOPMLRestoreDialog) {
        vm.showProgress = true
        performRestore()
        vm.showProgress = false
    }
    val chooseOpmlImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        OpmlTransporter.startImport(uri) { vm.readElements.addAll(it) }
        vm.showOpmlImportSelectionDialog = true
    }
    val addLocalFolderLauncher = rememberLauncherForActivityResult(AddLocalFolder()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.addLocalFloder(uri)
    }

    if (vm.showOpmlImportSelectionDialog) OpmlImportSelectionDialog(vm.readElements) { vm.showOpmlImportSelectionDialog = false }

    var showAdvanced by remember { mutableStateOf(false) }
    if (showAdvanced) CommonPopupCard({ showAdvanced = false }) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.search_combined_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                setOnlineSearchTerms(CombinedSearcher::class.java)
                showAdvanced = false
            }))
            val gearProviderRes = remember { gearbox.gearProviderRes() }
            if (gearProviderRes > 0) Text(stringResource(gearProviderRes), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                setOnlineSearchTerms(gearbox.gearSearchProvider())
                showAdvanced = false
            }))
            Text(stringResource(R.string.search_itunes_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                setOnlineSearchTerms(ItunesPodcastSearcher::class.java)
                showAdvanced = false
            }))
            Text(stringResource(R.string.search_podcastindex_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                setOnlineSearchTerms(PodcastIndexPodcastSearcher::class.java)
                showAdvanced = false
            }))
            Text(stringResource(R.string.opml_add_podcast_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                try { chooseOpmlImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Logs(TAG, e, context.getString(R.string.unable_to_start_system_file_manager)) }
                showAdvanced = false
            }))
        }
    }

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        ConstraintLayout(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val (controlRow, gridView, progressBar, empty, txtvError, butRetry, powered) = createRefs()
            Row(modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp).fillMaxWidth().constrainAs(controlRow) { top.linkTo(parent.top) }) {
                Text(stringResource(R.string.top_chart), color = actionColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = { navController.navigate(Screens.TopChart.name) }))
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.local),color = actionColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = {
                    try { addLocalFolderLauncher.launch(null) } catch (e: ActivityNotFoundException) { Logs(TAG, e, context.getString(R.string.unable_to_start_system_file_manager)) }
                }))
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.advanced), color = actionColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = { showAdvanced = !showAdvanced }))
            }

            if (vm.showProgress) CircularProgressIndicator(progress = { 0.6f }, strokeWidth = 10.dp, modifier = Modifier.size(50.dp).constrainAs(progressBar) { centerTo(parent) })
            if (vm.searchResults.isNotEmpty()) LazyColumn(state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp).constrainAs(gridView) {
                    top.linkTo(controlRow.bottom)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                }) {
                items(vm.searchResults) { result ->
                    val urlPrepared = remember(result.feedUrl) { prepareUrl(result.feedUrl!!) }
                    val sLog = remember(urlPrepared, result.title, feedLogsMap) { feedLogsMap?.get(urlPrepared) ?: feedLogsMap?.get(result.title) }
                    OnlineFeedItem(result, sLog)
                }
            } else Text(stringResource(R.string.no_results_for_query, searchText), color = textColor, modifier = Modifier.constrainAs(empty) { centerTo(parent) })
            if (vm.errorText.isNotEmpty()) Text(vm.errorText, color = textColor, modifier = Modifier.constrainAs(txtvError) { centerTo(parent) })
            if (vm.retryQerry.isNotEmpty()) Button(modifier = Modifier.padding(16.dp).constrainAs(butRetry) { top.linkTo(txtvError.bottom) }, onClick = { vm.search(vm.retryQerry) }) { Text(stringResource(id = R.string.retry_label)) }
            Text(context.getString(R.string.search_powered_by, searchProvider.name), color = Color.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(Color.LightGray)
                .constrainAs(powered) {
                    bottom.linkTo(parent.bottom)
                    end.linkTo(parent.end)
                })
        }
    }
}

private const val TAG: String = "OnlineSearchScreen"
