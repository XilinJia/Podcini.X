package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.net.sync.transceive.Sender
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.storage.database.FeedAssistant
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.buildListInfo
import ac.mdiq.podcini.storage.database.feedOperationText
import ac.mdiq.podcini.storage.database.feedsFlow
import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.getEpisodesAsFlow
import ac.mdiq.podcini.storage.database.getHistoryAsFlow
import ac.mdiq.podcini.storage.database.queueToVirtual
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
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
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.bsState
import ac.mdiq.podcini.ui.compose.COME_BACK
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommentEditingDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeScreen
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.EpisodesFilterDialog
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.LayoutMode
import ac.mdiq.podcini.ui.compose.LocalNavController
import ac.mdiq.podcini.ui.compose.RemoveFeedDialog
import ac.mdiq.podcini.ui.compose.Screens
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.TagType
import ac.mdiq.podcini.ui.compose.defaultScreen
import ac.mdiq.podcini.ui.compose.episodeForInfo
import ac.mdiq.podcini.ui.compose.handleBackSubScreens
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
import ac.mdiq.podcini.utils.timeIt
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.input.KeyboardType
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
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.net.URL
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
    val screenModeFlow = MutableStateFlow(FeedScreenMode.valueOf(modeName))

    var enableFilter by  mutableStateOf(true)
    var cameBack by mutableStateOf(false)

    val feedFlow: StateFlow<Feed?> = feedsFlow.map { it.list.firstOrNull { f -> f.id == feedId } }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = Feed())

    val episodesFlow: StateFlow<List<Episode>> = combine(feedFlow.filterNotNull(), screenModeFlow, snapshotFlow { enableFilter })
        { feed, mode, enableFilter -> Triple(feed, mode, enableFilter) }.distinctUntilChanged().flatMapLatest { (feed, mode, enableFilter) ->
            listIdentity = "FeedDetails.${feed.id}"
            when {
                mode == FeedScreenMode.Info -> emptyFlow()
                mode == FeedScreenMode.History -> {
                    listIdentity += ".History"
                    getHistoryAsFlow(feed.id)
                }
                enableFilter && feed.filterString.isNotBlank() -> {
                    listIdentity += ".${feed.filterString}.${feed.episodeSortOrder.name}"
                    try {
                        getEpisodesAsFlow(feed.episodeFilter, feed.episodeSortOrder, feed.id)
                    } catch (e: Throwable) {
                        Loge(TAG, "getEpisodesAsFlow error, retry: ${e.message}")
                        val feed_ = upsert(feed) {
                            it.episodeFilter = EpisodeFilter("")
                            it.episodeSortOrder = EpisodeSortOrder.DATE_DESC
                        }
                        getEpisodesAsFlow(feed_.episodeFilter, feed_.episodeSortOrder, feed_.id)
                    }
                }
                else -> {
                    listIdentity += "..${feed.episodeSortOrder.name}"
                    getEpisodesAsFlow(EpisodeFilter(""), feed.episodeSortOrder, feed.id)
                }
            }.map { it.list }
        }.distinctUntilChanged().stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList())

    var listIdentity by  mutableStateOf("")
    var showHeader by mutableStateOf(true)
    var listInfoText by mutableStateOf("")

    var feedEpisodesSize by mutableIntStateOf(0)

    init {
        Logd(TAG, "FeedDetailsVM init")
        timeIt("$TAG start of init")
        feedEpisodesSize = realm.query(Episode::class).query("feedId == $feedId").count().find().toInt()
        timeIt("$TAG end of init")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedDetailsScreen(feedId: Long = 0L, modeName: String = FeedScreenMode.List.name) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val drawerController = LocalDrawerController.current

    val vm: FeedDetailsVM = viewModel(key = feedId.toString(), factory = viewModelFactory { initializer { FeedDetailsVM(feedId, modeName) } })
    val feed by vm.feedFlow.collectAsStateWithLifecycle()
    val screenMode by vm.screenModeFlow.collectAsStateWithLifecycle()

    val swipeActions = remember { SwipeActions(TAG) }

    val connectLocalFolderLauncher: ActivityResultLauncher<Uri?> = rememberLauncherForActivityResult(contract = AddLocalFolder()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val context = getAppContext()
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runOnIOScope {
            try {
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                requireNotNull(documentFile) { "Unable to retrieve document tree" }
                if (feed != null) {
                    val feed_ = upsert(feed!!) { it.downloadUrl = Feed.PREFIX_LOCAL_FOLDER + uri.toString() }
                    updateFeedFull(feed_, removeUnlistedItems = true)
                }
                Logt(TAG, "Folder $uri connected " + context.getString(R.string.OK))
            } catch (e: Throwable) { Loge(TAG, e.localizedMessage ?: "No message") }
        }
    }

    DisposableEffect(lifecycleOwner) {
        Logd(TAG, "in DisposableEffect")
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect LifecycleEventObserver: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "ON_CREATE feedId: $feedId")
                    //                    val testNum = 1
                    //                    val eList = realm.query(Episode::class).query("feedId == ${vm.feedID} AND playState == ${PlayState.SOON.code} SORT(pubDate DESC) LIMIT($testNum)").find()
                    //                    Logd(TAG, "test eList: ${eList.size}")
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

    val showConnectLocalFolderConfirm = remember { mutableStateOf(false) }
    var showChooseRatingDialog by remember { mutableStateOf(false) }
    var showRemoveFeedDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember {  mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showTagsSettingDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    val episodes by vm.episodesFlow.collectAsStateWithLifecycle()
    LaunchedEffect(episodes.size) {
        Logd(TAG, "snapshotFlow { episodes.size }")
        vm.listInfoText = buildListInfo(episodes, vm.feedEpisodesSize)
    }

    Logd(TAG, "in Composition")
    @Composable
    fun OpenDialogs() {
        ComfirmDialog(0, stringResource(R.string.reconnect_local_folder_warning), showConnectLocalFolderConfirm) {
            try { connectLocalFolderLauncher.launch(null) } catch (e: ActivityNotFoundException) { Logs(TAG, e, "No activity found. Should never happen...") }
        }

        if (showChooseRatingDialog) ChooseRatingDialog(listOf(feed!!)) { showChooseRatingDialog = false }

        if (showRemoveFeedDialog) RemoveFeedDialog(listOf(feed!!), onDismissRequest = { showRemoveFeedDialog = false }) { navController.navigate(defaultScreen) }

        if (feed != null && showFilterDialog) {
            vm.showHeader = false
            EpisodesFilterDialog(filter_ = feed!!.episodeFilter, onDismissRequest = {
                vm.showHeader = true
                showFilterDialog = false
            }) { filter ->
                Logd(TAG, "persist Episode Filter(): feedId = [${feed?.id}], andOr = ${filter.andOr}, ${filter.propertySet.size} filterValues = ${filter.propertySet}")
                runOnIOScope { upsert(feed!!) { it.episodeFilter = filter } }
            }
        }

        if (feed != null && showSortDialog) {
            vm.showHeader = false
            EpisodeSortDialog(initOrder = feed!!.episodeSortOrder, onDismissRequest = {
                vm.showHeader = true
                showSortDialog = false
            }) { order ->
                Logd(TAG, "persist Episode SortOrder_")
                runOnIOScope { upsert(feed!!) { it.episodeSortOrder = order ?: EpisodeSortOrder.DATE_DESC } }
            }
        }

        swipeActions.ActionOptionsDialog()

        if (showTagsSettingDialog) TagSettingDialog(TagType.Feed, feed!!.tags, onDismiss = { showTagsSettingDialog = false }) { tags ->
            runOnIOScope {
                upsert(feed!!) {
                    it.tags.clear()
                    it.tags.addAll(tags)
                }
            }
        }

        if (showDeviceDialog) {
            var host by remember(appAttribs.peerAddress) { mutableStateOf(appAttribs.peerAddress) }
            var port by remember(appAttribs.transceivePort) { mutableIntStateOf(appAttribs.transceivePort) }
            var sendJob by remember { mutableStateOf<Job?>(null) }
            AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = {  },
                title = { Text(stringResource(R.string.send_to_device), style = CustomTextStyles.titleCustom) },
                text = {
                    Column {
                        Text(stringResource(R.string.send_to_device_sum))
                        TextField(value = host, onValueChange = { host = it }, label = { Text(stringResource(R.string.host_label)) })
                        TextField(value = port.toString(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(R.string.port_label)) }, singleLine = true, modifier = Modifier.padding(end = 8.dp), onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) port = it.toInt() })
                    }
                },
                confirmButton = {
                    if (sendJob == null) TextButton(onClick = {
                        upsertBlk(appAttribs) {
                            it.transceivePort = port
                            it.peerAddress = host
                        }
                        sendJob = Sender(host, port).sendFeed(feed!!.id) { showDeviceDialog =  false }
                    }) { Text(stringResource(R.string.send)) }
                },
                dismissButton = { TextButton(onClick = {
                    sendJob?.cancel()
                    showDeviceDialog = false
                }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
    }

    val lazyListState = rememberLazyListState()
    fun onImgLongClick() {
        if (curEpisode?.feedId == feedId) {
            if (screenMode == FeedScreenMode.List) {
                if (episodes.size > 5) {
                    val index = episodes.indexOfFirst { it.id == curEpisode?.id }
                    if (index >= 0) scope.launch { lazyListState.scrollToItem(index) }
                    else Logt(TAG, "can not find curEpisode to scroll to")
                } else Logt(TAG, "only scroll when episodes number is larger than 5")
            } else vm.screenModeFlow.value = (FeedScreenMode.List)
        } else if (curEpisode?.feedId != null) navController.navigate("${Screens.FeedDetails.name}?feedId=${curEpisode!!.feedId}")
    }

    @Composable
    fun FeedDetailsHeader() {
        val textColor = MaterialTheme.colorScheme.onSurface
        ConstraintLayout(modifier = Modifier.fillMaxWidth().height(60.dp)) {
            val (bgImage, bgColor, imgvCover, rating) = createRefs()
            AsyncImage(model = feed?.imageUrl?:"", contentDescription = "bgImage", contentScale = ContentScale.FillBounds, error = painterResource(R.drawable.teaser),
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
                AsyncImage(model = feed?.imageUrl ?: "", alignment = Alignment.TopStart, contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier.width(60.dp).height(60.dp).combinedClickable(
                        onClick = { if (feed != null) vm.screenModeFlow.value = (if (screenMode == FeedScreenMode.Info) FeedScreenMode.List else FeedScreenMode.Info) },
                        onLongClick = { onImgLongClick() }))
                if (feed != null) Text(feed?.title ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val ratingIconRes by remember { derivedStateOf { Rating.fromCode(feed!!.rating).res } }
            Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.padding(end = 10.dp, bottom = 5.dp).background(MaterialTheme.colorScheme.tertiaryContainer)
                .clickable(onClick = { showChooseRatingDialog = true })
                .constrainAs(rating) {
                    end.linkTo(parent.end)
                    bottom.linkTo(parent.bottom)
                })
        }
    }

    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val textColor = MaterialTheme.colorScheme.onSurface
        val buttonColor = Color(0xDDFFD700)
        val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
        Box {
            TopAppBar(title = { Text("") }, navigationIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Open Drawer", modifier = Modifier.padding(7.dp).clickable(onClick = {
                if (navController.previousBackStackEntry != null) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(COME_BACK, true)
                    navController.popBackStack()
                } else drawerController?.open()
            } ))  },
                actions = {
                    if (screenMode == FeedScreenMode.List) {
                        val isFiltered by remember(feed?.filterString) { mutableStateOf(!feed?.filterString.isNullOrBlank() && !feed?.episodeFilter?.propertySet.isNullOrEmpty()) }
                        IconButton(onClick = { showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "butSort") }
                        val filterButtonColor by remember { derivedStateOf { if (vm.enableFilter) if (isFiltered) buttonAltColor else textColor else Color.Red } }
                        if (feed != null) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter_white), tint = filterButtonColor, contentDescription = "butFilter", modifier = Modifier.padding(horizontal = 5.dp).combinedClickable(
                            onClick = { if (vm.enableFilter) showFilterDialog = true },
                            onLongClick = { if (isFiltered) vm.enableFilter = !vm.enableFilter })
                        )
                    }
                    val histColor by remember(screenMode) { derivedStateOf { if (screenMode != FeedScreenMode.History) textColor else buttonAltColor } }
                    if (screenMode in listOf(FeedScreenMode.List, FeedScreenMode.History) && feed != null) IconButton(onClick = {
                        vm.cameBack = false
                        vm.screenModeFlow.value = when(screenMode) {
                            FeedScreenMode.List -> FeedScreenMode.History
                            FeedScreenMode.History -> FeedScreenMode.List
                            else -> FeedScreenMode.List
                        }
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_history), tint = histColor, contentDescription = "history") }
                    if (feed?.queue != null) IconButton(onClick = {
                        navController.navigate("${Screens.Queues.name}?id=${feed?.queue?.id ?: -1L}")
                        bsState = MainActivity.BSState.Partial
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), contentDescription = "queue") }
                    IconButton(onClick = { navController.navigate(Screens.Search.name) }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                    if (feed != null) {
                        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.settings_label)) }, onClick = {
                                feedsToSet = listOf(feed!!)
                                navController.navigate(Screens.FeedsSettings.name)
                                expanded = false
                            })
                            if (!feed?.downloadUrl.isNullOrBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                                shareLink(context, feed?.downloadUrl ?: "")
                                expanded = false
                            })
                            if (!feed?.link.isNullOrBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.visit_website_label)) }, onClick = {
                                val isCallable = if (!feed?.link.isNullOrEmpty()) isCallable(Intent(Intent.ACTION_VIEW, feed!!.link!!.toUri())) else false
                                if (isCallable) openInBrowser(context, feed!!.link!!)
                                else Loge(TAG, "feed link is not valid: ${feed?.link}")
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.send_to_device)) }, onClick = {
                                showDeviceDialog = true
                                expanded = false
                            })
                            if (feed?.isLocalFeed == true) DropdownMenuItem(text = { Text(stringResource(R.string.reconnect_local_folder)) }, onClick = {
                                showConnectLocalFolderConfirm.value = true
                                expanded = false
                            })
                            if (vm.feedEpisodesSize > 0 && feed?.downloadUrl != null && !gearbox.isGearFeed(URL(feed!!.downloadUrl!!))) DropdownMenuItem(text = { Text(stringResource(R.string.fetch_size)) }, onClick = {
                                feedOperationText = context.getString(R.string.fetch_size)
                                scope.launch(Dispatchers.IO) {
                                    val feedEpisodes = getEpisodes(null, null, feedId, copy = false)
                                    for (e in feedEpisodes) e.fetchMediaSize(force = true)
                                    withContext(Dispatchers.Main) { feedOperationText = "" }
                                }
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clean_up)) }, onClick = {
                                feedOperationText = context.getString(R.string.clean_up)
                                runOnIOScope {
                                    val f = realm.copyFromRealm(feed!!)
                                    FeedAssistant(f).clear()
                                    upsert(f) {}
                                    withContext(Dispatchers.Main) { feedOperationText = "" }
                                }
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                                gearbox.feedUpdater(listOf(feed!!), doItAnyway = true).startRefresh()
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.load_complete_feed)) }, onClick = {
                                gearbox.feedUpdater(listOf(feed!!), fullUpdate = true, true).startRefresh()
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
        var editCommentText by remember { mutableStateOf(TextFieldValue(feed?.comment ?: "")) }
        if (feed != null && showEditComment) CommentEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = {showEditComment = false},
            onSave = {
                runOnIOScope {
                    upsert(feed!!) {
                        it.comment = editCommentText.text
                        it.commentTime = localTime
                    }
                }
            })
        var showFeedStats by remember { mutableStateOf(false) }
        if (showFeedStats) FeedStatisticsDialog(feed?.title?: "No title", feed?.id?:0, 0, Long.MAX_VALUE) { showFeedStats = false }

        Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            SelectionContainer {
                Column {
                    Text(feed?.title ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
                    Text(stringResource(R.string.by) + ": " + (feed?.author ?: ""), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                        Text(stringResource(R.string.score) + ": " + (feed!!.score).toString() + " (" + feed!!.scoreCount + ")", textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.weight(0.2f))
                        Text(stringResource(R.string.episodes_label) + ": " + (vm.feedEpisodesSize).toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Text(HtmlToPlainText.getPlainText(feed?.description ?: ""), color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!feed?.langSet.isNullOrEmpty()) Text("Languages: ${feed!!.langSet.joinToString(", ")}")

            Text("Tags: ${feed?.tagsAsString?:""}", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showTagsSettingDialog = true })
            Text(stringResource(R.string.comments) + if (feed?.comment.isNullOrBlank()) " (Add)" else "", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom,
                modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable {
                    editCommentText = TextFieldValue((if (feed?.comment.isNullOrBlank()) "" else feed!!.comment + "\n") + fullDateTimeString(localTime) + ":\n")
                    showEditComment = true
                })
            if (!feed?.comment.isNullOrBlank()) SelectionContainer { Text(feed?.comment ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp)) }

            Text(stringResource(R.string.statistics_label), color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row {
                TextButton({ showFeedStats = true }) { Text(stringResource(R.string.this_podcast)) }
                Spacer(Modifier.width(20.dp))
                TextButton({ navController.navigate(Screens.Statistics.name) }) { Text(stringResource(R.string.all_podcasts)) }
            }
            if (feed?.isSynthetic() == false) {
                Text(stringResource(R.string.feeds_related_to_author), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp).clickable(onClick = {
                        setOnlineSearchTerms(query = "${feed?.author} podcasts")
                        navController.navigate(Screens.FindFeeds.name)
                    }))
                Text(stringResource(R.string.last_full_update) + ": ${formatDateTimeFlex(Date(feed?.lastFullUpdateTime?:0L))}", modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(stringResource(R.string.url_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(text = feed?.downloadUrl ?: "", color = textColor, modifier = Modifier.padding(bottom = 15.dp).combinedClickable(
                    onClick = { if (!feed?.downloadUrl.isNullOrBlank()) openInBrowser(context, feed!!.downloadUrl!!) },
                    onLongClick = {
                        if (!feed?.downloadUrl.isNullOrBlank()) {
                            val url: String = feed!!.downloadUrl!!
                            val clipData: ClipData = ClipData.newPlainText(url, url)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(clipData)
                            Logt(TAG, context.getString(R.string.copied_to_clipboard))
                        }
                    }
                ))
                if (!feed?.paymentLinkList.isNullOrEmpty()) {
                    Text(stringResource(R.string.support_funding_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    fun fundingText(): String {
                        val fundingList: MutableList<FeedFunding> = feed!!.paymentLinkList
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

    DisposableEffect(screenMode, vm.enableFilter, episodeForInfo) {
        Logd(TAG, "DisposableEffect feedScreenMode: $screenMode")
        if (screenMode !in listOf(FeedScreenMode.Info, FeedScreenMode.List) || !vm.enableFilter || episodeForInfo != null) handleBackSubScreens.add(TAG)
        else handleBackSubScreens.remove(TAG)
        onDispose { handleBackSubScreens.remove(TAG) }
    }

    BackHandler(enabled = handleBackSubScreens.contains(TAG)) {
        Logd(TAG, "BackHandler")
        when {
            episodeForInfo != null -> {
                vm.cameBack = true
                episodeForInfo = null
            }
            !vm.enableFilter -> vm.enableFilter = true
            vm.screenModeFlow.value != FeedScreenMode.List -> vm.screenModeFlow.value = FeedScreenMode.List
        }
    }

    OpenDialogs()

    if (episodeForInfo != null) EpisodeScreen(episodeForInfo!!, listFlow = vm.episodesFlow)
    else Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        if (screenMode in listOf(FeedScreenMode.List, FeedScreenMode.History)) {
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                val isHeaderVisible by remember { derivedStateOf { lazyListState.firstVisibleItemIndex < 2 } }
                Logd(TAG, "vm.showHeader: ${vm.showHeader} isHeaderVisible: $isHeaderVisible")
                if (vm.showHeader && (isHeaderVisible || episodes.size < 10)) FeedDetailsHeader()
                InforBar(swipeActions) {
                    if (feedOperationText.isNotBlank()) Text(feedOperationText, style = MaterialTheme.typography.bodyMedium)
                    else {
                        val scoreText = remember(feed?.score, feed?.scoreCount) { if (feed != null) (feed!!.score).toString() + " (" + feed!!.scoreCount + ") " else "" }
                        if (scoreText.isNotBlank()) {
                            Text(scoreText, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(0.1f))
                        }
                        AsyncImage(model = feed?.imageUrl ?: "", alignment = Alignment.TopStart, contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher), modifier = Modifier.width(24.dp).height(24.dp).combinedClickable(
                            onClick = { if (feed != null) vm.screenModeFlow.value = (if (screenMode == FeedScreenMode.Info) FeedScreenMode.List else FeedScreenMode.Info) },
                            onLongClick = { onImgLongClick() }))
                        Spacer(modifier = Modifier.weight(0.1f))
                        Text(vm.listInfoText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                var scrollToOnStart by remember(episodes.size, curEpisode?.id, vm.cameBack, screenMode) { mutableIntStateOf(
                    when {
                        screenMode == FeedScreenMode.History || screenMode == FeedScreenMode.Info -> -1
                        vm.cameBack -> -1
                        curEpisode?.feedId == feedId -> episodes.indexOfFirst { it.id == curEpisode?.id }
                        else -> -1
                    }
                ) }
                Logd(TAG, "feed?.prefActionType: ${feed?.prefActionType}")
                val actionButtonName by remember(feed?.prefActionType) { mutableStateOf(
                    when {
                        feed == null -> null
                        feed!!.prefActionType != null -> feed!!.prefActionType!!
                        feed?.downloadUrl == null -> null
                        gearbox.isGearFeed(URL(feed!!.downloadUrl!!)) -> ButtonTypes.STREAM.name
                        else -> null
                    } ) }
                Logd(TAG, "actionButtonName: $actionButtonName")
                EpisodeLazyColumn(episodes, feed = feed, layoutMode = if (feed?.useWideLayout == true) LayoutMode.WideImage.ordinal else LayoutMode.Normal.ordinal,
                    swipeActions = swipeActions,
                    lazyListState = lazyListState, scrollToOnStart = scrollToOnStart,
                    refreshCB = {
                        if (feed != null && feed!!.inNormalVolume) runOnceOrAsk(feeds = listOf(feed!!))
                        else Logt(TAG, "feed is null or archived, can not refresh")
                    },
                    selectModeCB = { vm.showHeader = !it },
                    actionButtonType = if (actionButtonName != null) ButtonTypes.valueOf(actionButtonName!!) else null,
                    actionButtonCB = { e, type ->
                        Logd(TAG, "actionButtonCB type: $type ${e.feed?.id} ${feed?.id}")
                        if (e.feed?.id == feed?.id && type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAY_LOCAL, ButtonTypes.STREAM)) {
                            runOnIOScope {
                                upsert(feed!!) { it.lastPlayed = Date().time }
                                queueToVirtual(e, episodes, vm.listIdentity, feed!!.episodeSortOrder, true)
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
