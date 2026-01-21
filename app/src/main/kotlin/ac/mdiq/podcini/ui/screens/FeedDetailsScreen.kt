package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.storage.database.FeedAssistant
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.buildListInfo
import ac.mdiq.podcini.storage.database.feedOperationText
import ac.mdiq.podcini.storage.database.getEpisodesAsFlow
import ac.mdiq.podcini.storage.database.getHistoryAsFlow
import ac.mdiq.podcini.storage.database.queueToVirtual
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.ARCHIVED_VOLUME_ID
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.FeedFunding
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.AddLocalFolder
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.bsState
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommentEditingDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.EpisodesFilterDialog
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.LayoutMode
import ac.mdiq.podcini.ui.compose.RemoveFeedDialog
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.TagType
import ac.mdiq.podcini.ui.utils.HtmlToPlainText
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.formatDateTimeFlex
import ac.mdiq.podcini.utils.fullDateTimeString
import ac.mdiq.podcini.utils.isCallable
import ac.mdiq.podcini.utils.openInBrowser
import ac.mdiq.podcini.utils.shareLink
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
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
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.util.Date

enum class FeedScreenMode {
    List,
    History,
    Info
}

enum class ADLIncExc {
    INCLUDE,
    EXCLUDE
}

//private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
//    if (isGranted) return@registerForActivityResult
//    if (notificationPermissionDenied) {
//        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//        val uri = Uri.fromParts("package", requireContext().packageName, null)
//        intent.setData(uri)
//        startActivity(intent)
//        return@registerForActivityResult
//    }
//    Toast.makeText(context, R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
//    notificationPermissionDenied = true
//}

class FeedDetailsVM(feedId: Long = 0L, modeName: String = FeedScreenMode.List.name): ViewModel() {
    var feedScreenMode by mutableStateOf(FeedScreenMode.valueOf(modeName))
    var enableFilter by  mutableStateOf(true)

    var feed by mutableStateOf<Feed?>(null)

    var feedFlow by  mutableStateOf<Flow<SingleQueryChange<Feed>>>(emptyFlow())
    var feedEpisodesFlow by mutableStateOf<Flow<ResultsChange<Episode>>>(emptyFlow())
    var feedEpisodes by mutableStateOf<List<Episode>>(listOf())

    var episodesFlow by  mutableStateOf<Flow<ResultsChange<Episode>>>(emptyFlow())
    var episodes by mutableStateOf<List<Episode>>(listOf())
    var listIdentity by  mutableStateOf("")
    var showHeader by mutableStateOf(true)
    var listInfoText by mutableStateOf("")

    data class EpisodesKeys(
        val id: Long?,
        val filterString: String?,
        val sortOrderCode: Int?,
        val enableFilter: Boolean,
        val feedScreenMode: FeedScreenMode
    )

    init {
        feedEpisodesFlow = getEpisodesAsFlow(null, null, feedId)
        feedFlow = realm.query<Feed>("id == $0", feedId).first().asFlow()

        viewModelScope.launch { snapshotFlow { episodes.size }.distinctUntilChanged().collect { viewModelScope.launch(Dispatchers.IO) { listInfoText = buildListInfo(episodes, feedEpisodes.size) } } }

        viewModelScope.launch { snapshotFlow { EpisodesKeys(feed?.id, feed?.filterString, feed?.sortOrderCode, enableFilter, feedScreenMode) }.distinctUntilChanged().collect { getEpisodes() } }
    }

    fun getEpisodes() {
        if (feed != null && feedScreenMode in listOf(FeedScreenMode.List, FeedScreenMode.History)) viewModelScope.launch(Dispatchers.IO) {
            Logd(TAG, "assembleList feed!!.episodeFilter: ${feed!!.episodeFilter.propertySet}")
            listIdentity = "FeedDetails.${feed!!.id}"
            episodesFlow = when {
                feedScreenMode == FeedScreenMode.History -> {
                    listIdentity += ".History"
                    getHistoryAsFlow(feed!!.id)
                }
                enableFilter && feed!!.filterString.isNotBlank() -> {
                    listIdentity += ".${feed!!.filterString}.${feed!!.episodeSortOrder.name}"
                    try {
                        getEpisodesAsFlow(feed!!.episodeFilter, feed!!.episodeSortOrder, feed!!.id)
                    } catch (e: Throwable) {
                        Loge(TAG, "getEpisodesAsFlow error, retry: ${e.message}")
                        feed = upsert(feed!!) {
                            it.episodeFilter = EpisodeFilter("")
                            it.episodeSortOrder = EpisodeSortOrder.DATE_DESC
                        }
                        getEpisodesAsFlow(feed!!.episodeFilter, feed!!.episodeSortOrder, feed!!.id)
                    }
                }
                else -> {
                    listIdentity += "..${feed!!.episodeSortOrder.name}"
                    getEpisodesAsFlow(EpisodeFilter(""), feed!!.episodeSortOrder, feed!!.id)
                }
            }
        }
    }

    fun reconnectFloder(uri: Uri) {
        val context = getAppContext()
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runOnIOScope {
            try {
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                requireNotNull(documentFile) { "Unable to retrieve document tree" }
                if (feed != null) {
                    feed = upsert(feed!!) { it.downloadUrl = Feed.PREFIX_LOCAL_FOLDER + uri.toString() }
                    updateFeedFull(feed!!, removeUnlistedItems = true)
                }
                Logt(TAG, "Folder $uri connected " + context.getString(R.string.OK))
            } catch (e: Throwable) { Loge(TAG, e.localizedMessage ?: "No message") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedDetailsScreen(feedId: Long = 0L, modeName: String = FeedScreenMode.List.name) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val drawerController = LocalDrawerController.current

    val vm: FeedDetailsVM = viewModel(key = feedId.toString(), factory = viewModelFactory { initializer { FeedDetailsVM(feedId, modeName) } })

    val swipeActions = remember { SwipeActions(TAG) }

    val connectLocalFolderLauncher: ActivityResultLauncher<Uri?> = rememberLauncherForActivityResult(contract = AddLocalFolder()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.reconnectFloder(uri)
    }

    val stackEntry = navController.navController.currentBackStackEntryAsState().value

    DisposableEffect(lifecycleOwner) {
        Logd(TAG, "in DisposableEffect")
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect LifecycleEventObserver: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    val cameBack = stackEntry?.savedStateHandle?.get<Boolean>(COME_BACK) ?: false
                    Logd(TAG, "prefLastScreen: ${appAttribs.prefLastScreen} cameBack: $cameBack")
                    Logd(TAG, "ON_CREATE feedId: $feedId")
                    //                    val testNum = 1
                    //                    val eList = realm.query(Episode::class).query("feedId == ${vm.feedID} AND playState == ${PlayState.SOON.code} SORT(pubDate DESC) LIMIT($testNum)").find()
                    //                    Logd(TAG, "test eList: ${eList.size}")
                    lifecycleOwner.lifecycle.addObserver(swipeActions)
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
            Logd(TAG, "DisposableEffect onDispose")
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val cameBack = stackEntry?.savedStateHandle?.get<Boolean>(COME_BACK) ?: false
    LaunchedEffect(cameBack) { if (cameBack) vm.feedScreenMode = FeedScreenMode.List }

    var layoutModeIndex by remember((vm.feed?.useWideLayout)) { mutableIntStateOf(if (vm.feed?.useWideLayout == true) LayoutMode.WideImage.ordinal else LayoutMode.Normal.ordinal) }
    var isFiltered by remember(vm.feed?.filterString) { mutableStateOf(!vm.feed?.filterString.isNullOrBlank() && !vm.feed?.episodeFilter?.propertySet.isNullOrEmpty()) }

    val feedChange by vm.feedFlow.collectAsStateWithLifecycle(initialValue = null)
    if (feedChange?.obj != null) vm.feed = feedChange?.obj

    val isCallable = remember(vm.feed) { if (!vm.feed?.link.isNullOrEmpty()) isCallable(Intent(Intent.ACTION_VIEW, vm.feed!!.link!!.toUri())) else false }

    val showConnectLocalFolderConfirm = remember { mutableStateOf(false) }
    var showChooseRatingDialog by remember { mutableStateOf(false) }
    var showRemoveFeedDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember {  mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showTagsSettingDialog by remember { mutableStateOf(false) }

    val feChange by vm.feedEpisodesFlow.collectAsStateWithLifecycle(initialValue = null)
    if (feChange?.list != null) vm.feedEpisodes = feChange!!.list

    val episodesChange by vm.episodesFlow.collectAsStateWithLifecycle(initialValue = null)
    if (episodesChange?.list != null) vm.episodes = episodesChange!!.list

    @Composable
    fun OpenDialogs() {
        ComfirmDialog(0, stringResource(R.string.reconnect_local_folder_warning), showConnectLocalFolderConfirm) {
            try { connectLocalFolderLauncher.launch(null) } catch (e: ActivityNotFoundException) { Logs(TAG, e, "No activity found. Should never happen...") }
        }

        if (showChooseRatingDialog) ChooseRatingDialog(listOf(vm.feed!!)) { showChooseRatingDialog = false }

        if (showRemoveFeedDialog) RemoveFeedDialog(listOf(vm.feed!!), onDismissRequest = { showRemoveFeedDialog = false }) { navController.navigate(defaultScreen) }

        if (vm.feed != null && showFilterDialog) {
            vm.showHeader = false
            EpisodesFilterDialog(filter_ = vm.feed!!.episodeFilter, onDismissRequest = {
                vm.showHeader = true
                showFilterDialog = false
            }) { filter ->
                Logd(TAG, "persist Episode Filter(): feedId = [${vm.feed?.id}], andOr = ${filter.andOr}, ${filter.propertySet.size} filterValues = ${filter.propertySet}")
                runOnIOScope { upsert(vm.feed!!) { it.episodeFilter = filter } }
            }
        }

        if (vm.feed != null && showSortDialog) {
            vm.showHeader = false
            EpisodeSortDialog(initOrder = vm.feed!!.episodeSortOrder, onDismissRequest = {
                vm.showHeader = true
                showSortDialog = false
            }) { order ->
                Logd(TAG, "persist Episode SortOrder_")
                runOnIOScope { upsert(vm.feed!!) { it.episodeSortOrder = order ?: EpisodeSortOrder.DATE_DESC } }
            }
        }

        swipeActions.ActionOptionsDialog()

        if (showTagsSettingDialog) TagSettingDialog(TagType.Feed, vm.feed!!.tags, onDismiss = { showTagsSettingDialog = false }) { tags ->
            runOnIOScope {
                upsert(vm.feed!!) {
                    it.tags.clear()
                    it.tags.addAll(tags)
                }
            }
        }
    }

    val lazyListState = rememberLazyListState()
    fun onImgLongClick() {
        if (curEpisode?.feedId == feedId) {
            if (vm.feedScreenMode == FeedScreenMode.List) {
                if (vm.episodes.size > 5) {
                    val index = vm.episodes.indexOfFirst { it.id == curEpisode?.id }
                    if (index >= 0) scope.launch { lazyListState.scrollToItem(index) }
                    else Logt(TAG, "can not find curEpisode to scroll to")
                } else Logt(TAG, "only scroll when episodes number is larger than 5")
            } else vm.feedScreenMode = FeedScreenMode.List
        } else if (curEpisode?.feedId != null) navController.navigate("${Screens.FeedDetails.name}?feedId=${curEpisode!!.feedId}")
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedDetailsHeader() {
        val textColor = MaterialTheme.colorScheme.onSurface
        ConstraintLayout(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            val (bgImage, bgColor, imgvCover) = createRefs()
            AsyncImage(model = vm.feed?.imageUrl?:"", contentDescription = "bgImage", contentScale = ContentScale.FillBounds, error = painterResource(R.drawable.teaser),
                modifier = Modifier.fillMaxSize().blur(radiusX = 15.dp, radiusY = 15.dp).constrainAs(bgImage) {
                    bottom.linkTo(parent.bottom)
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end) })
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)).constrainAs(bgColor) {
                bottom.linkTo(parent.bottom)
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end) })
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().constrainAs(imgvCover) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints
            }) {
                AsyncImage(model = vm.feed?.imageUrl ?: "", alignment = Alignment.TopStart, contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier.width(80.dp).height(80.dp).combinedClickable(
                        onClick = { if (vm.feed != null) vm.feedScreenMode = if (vm.feedScreenMode == FeedScreenMode.Info) FeedScreenMode.List else FeedScreenMode.Info },
                        onLongClick = { onImgLongClick() }))
                if (vm.feed != null) Column(modifier = Modifier.padding(start = 10.dp, top = 4.dp)) {
                    Text(vm.feed?.title ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(vm.feed?.author ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ratingIconRes by remember { derivedStateOf { Rating.fromCode(vm.feed!!.rating).res } }
                        IconButton(onClick = { showChooseRatingDialog = true }) { Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.padding(start = 5.dp).background(MaterialTheme.colorScheme.tertiaryContainer)) }
                        Spacer(modifier = Modifier.weight(0.1f))
                        if (vm.feed!!.score > -1000) Text((vm.feed!!.score).toString() + " (" + vm.feed!!.scoreCount + ")", textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.weight(0.2f))
                        if (vm.feedScreenMode == FeedScreenMode.List) Text(vm.episodes.size.toString() + " / " + vm.feedEpisodes.size.toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        else Text((vm.feedEpisodes.size).toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        val context by rememberUpdatedState(LocalContext.current)
        var expanded by remember { mutableStateOf(false) }
        val textColor = MaterialTheme.colorScheme.onSurface
        val buttonColor = Color(0xDDFFD700)
        val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
        Box {
            TopAppBar(title = { Text("") }, navigationIcon = { IconButton(onClick = {
                if (navController.previousBackStackEntry != null) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(COME_BACK, true)
                    navController.popBackStack()
                } else drawerController.open()
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Open Drawer") } },
                actions = {
                    if (vm.feedScreenMode == FeedScreenMode.List) {
                        IconButton(onClick = { showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "butSort") }
                        val filterButtonColor by remember { derivedStateOf { if (vm.enableFilter) if (isFiltered) buttonAltColor else textColor else Color.Red } }
                        if (vm.feed != null) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter_white), tint = filterButtonColor, contentDescription = "butFilter", modifier = Modifier.padding(horizontal = 5.dp).combinedClickable(
                            onClick = { if (vm.enableFilter) showFilterDialog = true },
                            onLongClick = { if (isFiltered) vm.enableFilter = !vm.enableFilter })
                        )
                    }
                    val histColor by remember(vm.feedScreenMode) { derivedStateOf { if (vm.feedScreenMode != FeedScreenMode.History) textColor else buttonAltColor } }
                    if (vm.feedScreenMode == FeedScreenMode.List && vm.feed != null) IconButton(onClick = {
                        vm.feedScreenMode = when(vm.feedScreenMode) {
                            FeedScreenMode.List -> FeedScreenMode.History
                            FeedScreenMode.History -> FeedScreenMode.List
                            else -> FeedScreenMode.List
                        }
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_history), tint = histColor, contentDescription = "history") }
                    if (vm.feed?.queue != null) IconButton(onClick = {
                        navController.navigate("${Screens.Queues.name}?id=${vm.feed?.queue?.id ?: -1L}")
                        bsState = MainActivity.BSState.Partial
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), contentDescription = "queue") }
                    IconButton(onClick = { navController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                    if (vm.feed != null) {
                        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.settings_label)) }, onClick = {
                                if (vm.feed != null) {
                                    feedsToSet = listOf(vm.feed!!)
                                    navController.navigate(Screens.FeedsSettings.name)
                                }
                                expanded = false
                            })
                            if (!vm.feed?.downloadUrl.isNullOrBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                                shareLink(context, vm.feed?.downloadUrl ?: "")
                                expanded = false
                            })
                            if (!vm.feed?.link.isNullOrBlank() && isCallable) DropdownMenuItem(text = { Text(stringResource(R.string.visit_website_label)) }, onClick = {
                                openInBrowser(context, vm.feed!!.link!!)
                                expanded = false
                            })
                            if (vm.feed?.isLocalFeed == true) DropdownMenuItem(text = { Text(stringResource(R.string.reconnect_local_folder)) }, onClick = {
                                showConnectLocalFolderConfirm.value = true
                                expanded = false
                            })
                            if (vm.feedEpisodes.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.fetch_size)) }, onClick = {
                                feedOperationText = context.getString(R.string.fetch_size)
                                scope.launch {
                                    for (e in vm.feedEpisodes) e.fetchMediaSize(force = true)
                                    withContext(Dispatchers.Main) { feedOperationText = "" }
                                }
                                expanded = false
                            })
                            if (vm.feed != null) DropdownMenuItem(text = { Text(stringResource(R.string.clean_up)) }, onClick = {
                                feedOperationText = context.getString(R.string.clean_up)
                                runOnIOScope {
                                    val f = realm.copyFromRealm(vm.feed!!)
                                    FeedAssistant(f).clear()
                                    upsert(f) {}
                                    withContext(Dispatchers.Main) { feedOperationText = "" }
                                }
                                expanded = false
                            })
                            if (vm.feed != null) DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                                gearbox.feedUpdater(listOf(vm.feed!!), doItAnyway = true).startRefresh()
                                expanded = false
                            })
                            if (vm.feed != null) DropdownMenuItem(text = { Text(stringResource(R.string.load_complete_feed)) }, onClick = {
                                if (vm.feed != null) gearbox.feedUpdater(listOf(vm.feed!!), fullUpdate = true, true).startRefresh()
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.remove_feed_label)) }, onClick = {
                                showRemoveFeedDialog = true
                                expanded = false
                            })
                        }
                    }
                })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @Composable
    fun DetailUI() {
        var showEditComment by remember { mutableStateOf(false) }
        val localTime = remember { System.currentTimeMillis() }
        var editCommentText by remember { mutableStateOf(TextFieldValue(vm.feed?.comment ?: "")) }
        if (vm.feed != null && showEditComment) CommentEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = {showEditComment = false},
            onSave = {
                runOnIOScope {
                    upsert(vm.feed!!) {
                        it.comment = editCommentText.text
                        it.commentTime = localTime
                    }
                }
            })
        var showFeedStats by remember { mutableStateOf(false) }
        if (showFeedStats) FeedStatisticsDialog(vm.feed?.title?: "No title", vm.feed?.id?:0, 0, Long.MAX_VALUE) { showFeedStats = false }

        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            SelectionContainer {
                Column {
                    Text(vm.feed?.title ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
                    Text(vm.feed?.author ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Text(HtmlToPlainText.getPlainText(vm.feed?.description ?: ""), color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!vm.feed?.langSet.isNullOrEmpty()) Text("Languages: ${vm.feed!!.langSet.joinToString(", ")}")

            Text("Tags: ${vm.feed?.tagsAsString?:""}", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showTagsSettingDialog = true })
            Text(stringResource(R.string.my_opinion_label) + if (vm.feed?.comment.isNullOrBlank()) " (Add)" else "", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom,
                modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable {
                    editCommentText = TextFieldValue((if (vm.feed?.comment.isNullOrBlank()) "" else vm.feed!!.comment + "\n") + fullDateTimeString(localTime) + ":\n")
                    showEditComment = true
                })
            if (!vm.feed?.comment.isNullOrBlank()) SelectionContainer { Text(vm.feed?.comment ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp)) }

            Text(stringResource(R.string.statistics_label), color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row {
                TextButton({ showFeedStats = true }) { Text(stringResource(R.string.this_podcast)) }
                Spacer(Modifier.width(20.dp))
                TextButton({ navController.navigate(Screens.Statistics.name) }) { Text(stringResource(R.string.all_podcasts)) }
            }
            if (vm.feed?.isSynthetic() == false) {
                Text(stringResource(R.string.feeds_related_to_author), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp).clickable(onClick = {
                        setOnlineSearchTerms(query = "${vm.feed?.author} podcasts")
                        navController.navigate(Screens.FindFeeds.name)
                    }))
                Text(stringResource(R.string.last_full_update) + ": ${formatDateTimeFlex(Date(vm.feed?.lastFullUpdateTime?:0L))}", modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(stringResource(R.string.url_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(text = vm.feed?.downloadUrl ?: "", color = textColor, modifier = Modifier.padding(bottom = 15.dp).combinedClickable(
                    onClick = { if (!vm.feed?.downloadUrl.isNullOrBlank()) openInBrowser(context, vm.feed!!.downloadUrl!!) },
                    onLongClick = {
                        if (!vm.feed?.downloadUrl.isNullOrBlank()) {
                            val url: String = vm.feed!!.downloadUrl!!
                            val clipData: ClipData = ClipData.newPlainText(url, url)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(clipData)
                            Logt(TAG, context.getString(R.string.copied_to_clipboard))
                        }
                    }
                ))
                if (!vm.feed?.paymentLinkList.isNullOrEmpty()) {
                    Text(stringResource(R.string.support_funding_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    fun fundingText(): String {
                        val fundingList: MutableList<FeedFunding> = vm.feed!!.paymentLinkList
                        val i: MutableIterator<FeedFunding> = fundingList.iterator()
                        while (i.hasNext()) {
                            val funding: FeedFunding = i.next()
                            for (other in fundingList) {
                                if (other.url == funding.url) {
                                    if (other.content != null && funding.content != null && other.content!!.length > funding.content!!.length) {
                                        i.remove()
                                        break
                                    }
                                }
                            }
                        }
                        val sb = StringBuilder()
                        val supportPodcast = getAppContext().resources.getString(R.string.support_podcast)
                        for (funding in fundingList) {
                            sb.append(if (funding.content == null || funding.content!!.isEmpty())  supportPodcast else funding.content).append(" ").append(funding.url)
                            sb.append("\n")
                        }
                        return StringBuilder(StringUtils.trim(sb.toString())).toString()
                    }
                    val fundText = remember { fundingText() }
                    Text(fundText, color = textColor)
                }
            }
        }
    }

    DisposableEffect(vm.feedScreenMode, vm.enableFilter) {
        Logd(TAG, "DisposableEffect feedScreenMode: ${vm.feedScreenMode}")
        if (vm.feedScreenMode !in listOf(FeedScreenMode.Info, FeedScreenMode.List) || !vm.enableFilter) handleBackSubScreens.add(TAG)
        else handleBackSubScreens.remove(TAG)
        onDispose { handleBackSubScreens.remove(TAG) }
    }

    BackHandler(enabled = handleBackSubScreens.contains(TAG)) {
        vm.feedScreenMode = FeedScreenMode.List
        vm.enableFilter = true
    }

    OpenDialogs()

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        if (vm.feedScreenMode in listOf(FeedScreenMode.List, FeedScreenMode.History)) {
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                val isHeaderVisible by remember { derivedStateOf { lazyListState.firstVisibleItemIndex < 2 } }
                Logd(TAG, "vm.showHeader: ${vm.showHeader} isHeaderVisible: $isHeaderVisible")
                if (vm.showHeader && (isHeaderVisible || vm.episodes.size < 10)) FeedDetailsHeader()
                val cameBack = stackEntry?.savedStateHandle?.get<Boolean>(COME_BACK) ?: false
                var scrollToOnStart by remember(vm.episodes, curEpisode) { mutableIntStateOf(if (cameBack) -1 else if (curEpisode?.feedId == feedId) vm.episodes.indexOfFirst { it.id == curEpisode?.id } else -1) }
                InforBar(swipeActions) {
                    if (feedOperationText.isNotBlank()) Text(feedOperationText, style = MaterialTheme.typography.bodyMedium)
                    else {
                        val scoreText = remember(vm.feed?.score, vm.feed?.scoreCount) { if (vm.feed != null) (vm.feed!!.score).toString() + " (" + vm.feed!!.scoreCount + ") " else "" }
                        if (scoreText.isNotBlank()) {
                            Text(scoreText, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(0.1f))
                        }
                        AsyncImage(model = vm.feed?.imageUrl ?: "", alignment = Alignment.TopStart, contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher), modifier = Modifier.width(24.dp).height(24.dp).combinedClickable(
                            onClick = { if (vm.feed != null) vm.feedScreenMode = if (vm.feedScreenMode == FeedScreenMode.Info) FeedScreenMode.List else FeedScreenMode.Info },
                            onLongClick = { onImgLongClick() }))
                        Spacer(modifier = Modifier.weight(0.1f))
                        Text(vm.listInfoText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                EpisodeLazyColumn(vm.episodes, feed = vm.feed, layoutMode = layoutModeIndex, swipeActions = swipeActions,
                    lazyListState = lazyListState, scrollToOnStart = scrollToOnStart,
                    refreshCB = {
                        if (vm.feed != null && vm.feed!!.volumeId != ARCHIVED_VOLUME_ID) runOnceOrAsk(feeds = listOf(vm.feed!!))
                        else Logt(TAG, "feed is null or archived, can not refresh")
                    },
                    selectModeCB = { vm.showHeader = !it },
                    actionButtonCB = { e, type ->
                        Logd(TAG, "actionButtonCB type: $type ${e.feed?.id} ${vm.feed?.id}")
                        if (e.feed?.id == vm.feed?.id && type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAY_LOCAL, ButtonTypes.STREAM)) {
                            runOnIOScope {
                                upsert(vm.feed!!) { it.lastPlayed = Date().time }
                                queueToVirtual(e, vm.episodes, vm.listIdentity, vm.feed!!.episodeSortOrder, true)
                            }
                        }
                    },
                )
            }
        } else {
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
                FeedDetailsHeader()
                DetailUI()
            }
        }
    }
}

private val TAG = Screens.FeedDetails.name
