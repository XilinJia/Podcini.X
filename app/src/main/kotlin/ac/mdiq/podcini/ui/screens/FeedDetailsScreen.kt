package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.utils.HtmlToPlainText
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
import ac.mdiq.podcini.storage.database.updateFeedDownloadURL
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.FeedFunding
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.isBSExpanded
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
import ac.mdiq.podcini.ui.compose.RenameOrCreateSyntheticFeed
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.TagType
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.util.Date
import java.util.concurrent.ExecutionException

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedDetailsScreen(feedId: Long = 0L, modeName: String = FeedScreenMode.List.name) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current

    var feedScreenMode by remember { mutableStateOf(FeedScreenMode.valueOf(modeName)) }

    val swipeActions = remember { SwipeActions(context, TAG) }

    var feed by remember { mutableStateOf<Feed?>(null) }

    var enableFilter by remember { mutableStateOf(true) }

    var feedFlow by remember { mutableStateOf<Flow<SingleQueryChange<Feed>>>(emptyFlow()) }

    val addLocalFolderLauncher: ActivityResultLauncher<Uri?> = rememberLauncherForActivityResult(contract = AddLocalFolder()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runOnIOScope {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                requireNotNull(documentFile) { "Unable to retrieve document tree" }
                feed?.downloadUrl = Feed.PREFIX_LOCAL_FOLDER + uri.toString()
                if (feed != null) updateFeedFull(feed!!, removeUnlistedItems = true)
                Logt(TAG, context.getString(R.string.OK))
            } catch (e: Throwable) { Loge(TAG, e.localizedMessage?:"No message") }
        }
    }

    var feedEpisodesFlow by remember { mutableStateOf<Flow<ResultsChange<Episode>>>(emptyFlow()) }

    val currentEntry = navController.navController.currentBackStackEntryAsState().value

    DisposableEffect(lifecycleOwner) {
        Logd(TAG, "in DisposableEffect")
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect LifecycleEventObserver: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    val cameBack = currentEntry?.savedStateHandle?.get<Boolean>(COME_BACK) ?: false
                    Logd(TAG, "prefLastScreen: ${appAttribs.prefLastScreen} cameBack: $cameBack")
                    feedEpisodesFlow = getEpisodesAsFlow(null, null, feedId)
                    feedFlow = realm.query<Feed>("id == $0", feedId).first().asFlow()
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
            feed = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val cameBack = currentEntry?.savedStateHandle?.get<Boolean>(COME_BACK) ?: false
    LaunchedEffect(cameBack) { if (cameBack) feedScreenMode = FeedScreenMode.List }

    var episodesFlow by remember { mutableStateOf<Flow<ResultsChange<Episode>>>(emptyFlow()) }

    var listIdentity by remember { mutableStateOf("") }

    var layoutModeIndex by remember { mutableIntStateOf(LayoutMode.Normal.ordinal) }

    var isFiltered by remember { mutableStateOf(false) }

    val feedChange by feedFlow.collectAsStateWithLifecycle(initialValue = null)
    feed = feedChange?.obj
    LaunchedEffect(feedChange, feedId) {
        Logd(TAG, "LaunchedEffect(feedResult, feedId)")
        isFiltered = !feed?.filterString.isNullOrBlank() && !feed?.episodeFilter?.propertySet.isNullOrEmpty()
    }

    val isCallable = remember(feed) { if (!feed?.link.isNullOrEmpty()) isCallable(context, Intent(Intent.ACTION_VIEW, feed!!.link!!.toUri())) else false }

    var showHeader by remember { mutableStateOf(true) }

    val showConnectLocalFolderConfirm = remember { mutableStateOf(false) }
    var showEditConfirmDialog by remember { mutableStateOf(false) }
    var editedUrl by remember { mutableStateOf("") }
    var showEditUrlSettingsDialog by remember { mutableStateOf(false) }
    var showChooseRatingDialog by remember { mutableStateOf(false) }
    var showRemoveFeedDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember {  mutableStateOf(false) }
    var showRenameDialog by remember {  mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showTagsSettingDialog by remember { mutableStateOf(false) }

    val infoBarText = remember { mutableStateOf("") }

    val feChange by feedEpisodesFlow.collectAsStateWithLifecycle(initialValue = null)
    val feedEpisodes = feChange?.list ?: listOf()

    val episodesChange by episodesFlow.collectAsStateWithLifecycle(initialValue = null)
    val episodes = episodesChange?.list ?: listOf()

    var listInfoText by remember { mutableStateOf("") }
    LaunchedEffect(episodes.size) {
        Logd(TAG, "LaunchedEffect(episodes.size)")
        scope.launch(Dispatchers.IO) {
            val info = buildListInfo(episodes)
            withContext(Dispatchers.Main) {
                listInfoText = info
                infoBarText.value = "$listInfoText $feedOperationText"
            }
        }
    }

    var score by remember { mutableIntStateOf(-1000) }
    var scoreCount by remember { mutableIntStateOf(0) }

    @Composable
    fun OpenDialogs() {
        ComfirmDialog(0, stringResource(R.string.reconnect_local_folder_warning), showConnectLocalFolderConfirm) {
            try { addLocalFolderLauncher.launch(null) } catch (e: ActivityNotFoundException) { Logs(TAG, e, "No activity found. Should never happen...") }
        }
        @Composable
        fun EditConfirmDialog(onDismiss: () -> Unit) {
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.edit_url_menu)) },
                text = { Text(stringResource(R.string.edit_url_confirmation_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        runOnIOScope {
                            try {
                                updateFeedDownloadURL(feed?.downloadUrl ?: "", editedUrl)
                                feed?.downloadUrl = editedUrl
                                //                            runOnce(context, feed)
                                if (feed != null) gearbox.feedUpdater(listOf(feed!!)).startRefresh(context)
                            } catch (e: ExecutionException) { throw RuntimeException(e) } catch (e: InterruptedException) { throw RuntimeException(e) }
                            feed?.downloadUrl = editedUrl
                            //                        withContext(Dispatchers.Main) { vm.txtvUrl = feed?.downloadUrl }
                        }
                        onDismiss()
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        if (showEditConfirmDialog) EditConfirmDialog { showEditConfirmDialog = false }

        @Composable
        fun EditUrlSettingsDialog(onDismiss: () -> Unit) {
            var url by remember { mutableStateOf(feed?.downloadUrl ?: "") }
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.edit_url_menu)) },
                text = { TextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    TextButton(onClick = {
                        editedUrl = url
                        showEditConfirmDialog = true
                        onDismiss()
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        if (showEditUrlSettingsDialog) EditUrlSettingsDialog { showEditUrlSettingsDialog = false }

        if (showChooseRatingDialog) ChooseRatingDialog(listOf(feed!!)) { showChooseRatingDialog = false }

        if (showRemoveFeedDialog) RemoveFeedDialog(listOf(feed!!), onDismissRequest = { showRemoveFeedDialog = false }) { navController.navigate(defaultScreen) }

        if (feed != null && showFilterDialog) {
            showHeader = false
            EpisodesFilterDialog(filter_ = feed!!.episodeFilter, onDismissRequest = {
                showHeader = true
                showFilterDialog = false
            }) { filter ->
                Logd(TAG, "persist Episode Filter(): feedId = [${feed?.id}], andOr = ${filter.andOr}, ${filter.propertySet.size} filterValues = ${filter.propertySet}")
                runOnIOScope { upsert(feed!!) { it.episodeFilter = filter } }
            }
        }

        if (showRenameDialog) RenameOrCreateSyntheticFeed(feed) { showRenameDialog = false }

        if (feed != null && showSortDialog) {
            showHeader = false
            EpisodeSortDialog(initOrder = feed!!.episodeSortOrder, onDismissRequest = {
                showHeader = true
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
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedDetailsHeader() {
        val textColor = MaterialTheme.colorScheme.onSurface
        ConstraintLayout(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            val (bgImage, bgColor, imgvCover) = createRefs()
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
                    modifier = Modifier.width(80.dp).height(80.dp).clickable(onClick = {
                        if (feed != null) feedScreenMode = if (feedScreenMode == FeedScreenMode.Info) FeedScreenMode.List else FeedScreenMode.Info
//                        if (feed != null) {
//                            if (feedScreenMode == FeedScreenMode.Info) {
//                                feedScreenMode = FeedScreenMode.List
//                                runOnIOScope { upsertBlk(appAttribs) { it.prefLastScreen = it.prefLastScreen.replace("modeName=Info", "modeName=List") } }
//                            } else {
//                                feedScreenMode = FeedScreenMode.Info
//                                runOnIOScope { upsertBlk(appAttribs) { it.prefLastScreen = it.prefLastScreen.replace("modeName=List", "modeName=Info") } }
//                            }
//                        }
                    }))
                if (feed != null) Column(modifier = Modifier.padding(start = 10.dp, top = 4.dp)) {
                    Text(feed?.title ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(feed?.author ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ratingIconRes by remember { derivedStateOf { Rating.fromCode(feed!!.rating).res } }
                        IconButton(onClick = { showChooseRatingDialog = true }) { Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.padding(start = 5.dp).background(MaterialTheme.colorScheme.tertiaryContainer)) }
                        Spacer(modifier = Modifier.weight(0.1f))
                        if (score > -1000) Text((score).toString() + " (" + scoreCount + ")", textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.weight(0.2f))
                        if (feedScreenMode == FeedScreenMode.List) Text(episodes.size.toString() + " / " + feedEpisodes.size.toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        else Text((feedEpisodes.size).toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
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
                } else openDrawer()
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Open Drawer") } },
                actions = {
                    if (feedScreenMode == FeedScreenMode.List) {
                        IconButton(onClick = { showSortDialog = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "butSort") }
                        val filterButtonColor by remember { derivedStateOf { if (enableFilter) if (isFiltered) buttonAltColor else textColor else Color.Red } }
                        if (feed != null) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter_white), tint = filterButtonColor, contentDescription = "butFilter", modifier = Modifier.padding(horizontal = 5.dp).combinedClickable(onClick = { if (enableFilter) showFilterDialog = true }, onLongClick = {
                            if (isFiltered) {
                                enableFilter = !enableFilter
                            }
                        }))
                    }
                    val histColor by remember(feedScreenMode) { derivedStateOf { if (feedScreenMode != FeedScreenMode.History) textColor else buttonAltColor } }
                    if (feedScreenMode == FeedScreenMode.List && feed != null) IconButton(onClick = {
                        feedScreenMode = when(feedScreenMode) {
                            FeedScreenMode.List -> FeedScreenMode.History
                            FeedScreenMode.History -> FeedScreenMode.List
                            else -> FeedScreenMode.List
                        }
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_history), tint = histColor, contentDescription = "history") }
                    if (feed?.queue != null) IconButton(onClick = {
                        navController.navigate("${Screens.Queues.name}?id=${feed?.queue?.id ?: -1L}")
                        isBSExpanded = false
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), contentDescription = "queue") }
                    IconButton(onClick = { navController.navigate(Screens.Search.name)
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                    IconButton(onClick = {
                        if (feed != null) {
                            feedsToSet = listOf(feed!!)
                            navController.navigate(Screens.FeedsSettings.name)
                        }
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings_white), contentDescription = "butShowSettings") }
                    if (feed != null) {
                        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                            if (!feed?.downloadUrl.isNullOrBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                                shareLink(context, feed?.downloadUrl ?: "")
                                expanded = false
                            })
                            if (!feed?.link.isNullOrBlank() && isCallable) DropdownMenuItem(text = { Text(stringResource(R.string.visit_website_label)) }, onClick = {
                                openInBrowser(context, feed!!.link!!)
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename_feed_label)) }, onClick = {
                                showRenameDialog = true
                                expanded = false
                            })
                            if (feed?.isLocalFeed == true) DropdownMenuItem(text = { Text(stringResource(R.string.reconnect_local_folder)) }, onClick = {
                                showConnectLocalFolderConfirm.value = true
                                expanded = false
                            }) else DropdownMenuItem(text = { Text(stringResource(R.string.edit_url_menu)) }, onClick = {
                                showEditUrlSettingsDialog = true
                                expanded = false
                            })
                            if (feedEpisodes.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.fetch_size)) }, onClick = {
                                feedOperationText = context.getString(R.string.fetch_size)
                                scope.launch {
                                    for (e in feedEpisodes) e.fetchMediaSize(force = true)
                                    withContext(Dispatchers.Main) { feedOperationText = "" }
                                }
                                expanded = false
                            })
                            if (feed != null) DropdownMenuItem(text = { Text(stringResource(R.string.clean_up)) }, onClick = {
                                feedOperationText = context.getString(R.string.clean_up)
                                runOnIOScope {
                                    val f = realm.copyFromRealm(feed!!)
                                    FeedAssistant(f).clear()
                                    upsert(f) {}
                                    withContext(Dispatchers.Main) { feedOperationText = "" }
                                }
                                expanded = false
                            })
                            if (feed != null) DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                                gearbox.feedUpdater(listOf(feed!!), doItAnyway = true).startRefresh(context)
                                expanded = false
                            })
                            if (feed != null) DropdownMenuItem(text = { Text(stringResource(R.string.load_complete_feed)) }, onClick = {
                                if (feed != null) gearbox.feedUpdater(listOf(feed!!), fullUpdate = true, true).startRefresh(context)
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

        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(rememberScrollState())) {
            val textColor = MaterialTheme.colorScheme.onSurface
            SelectionContainer {
                Column {
                    Text(feed?.title ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
                    Text(feed?.author ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Text(HtmlToPlainText.getPlainText(feed?.description ?: ""), color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!feed?.langSet.isNullOrEmpty()) Text("Languages: ${feed!!.langSet.joinToString(", ")}")

            Text("Tags: ${feed?.tagsAsString?:""}", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showTagsSettingDialog = true })
            Text(stringResource(R.string.my_opinion_label) + if (feed?.comment.isNullOrBlank()) " (Add)" else "", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom,
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
                        setOnlineSearchTerms(CombinedSearcher::class.java, "${feed?.author} podcasts")
                        navController.navigate(Screens.OnlineSearch.name)
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

    DisposableEffect(feedScreenMode) {
        subscreenHandleBack.value = feedScreenMode !in listOf(FeedScreenMode.Info, FeedScreenMode.List) || !enableFilter
        onDispose { subscreenHandleBack.value = false }
    }

    BackHandler(enabled = subscreenHandleBack.value) {
        feedScreenMode = FeedScreenMode.List
        enableFilter = true
    }

    suspend fun assembleList() {
        if (feed == null) return
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
        withContext(Dispatchers.Main) {
            layoutModeIndex = if (feed!!.useWideLayout) LayoutMode.WideImage.ordinal else LayoutMode.Normal.ordinal
            Logd(TAG, "loadItems subscribe called ${feed?.title}")
            if (feedEpisodes.isNotEmpty()) {
                var sumR = 0.0
                scoreCount = 0
                for (e in feedEpisodes) {
                    if (e.playState >= EpisodeState.PROGRESS.code) {
                        scoreCount++
                        if (e.rating != Rating.UNRATED.code) sumR += e.rating
                        if (e.playState >= EpisodeState.SKIPPED.code) sumR += - 0.5 + 1.0 * e.playedDuration / e.duration
                        else if (e.playState in listOf(EpisodeState.AGAIN.code, EpisodeState.FOREVER.code)) sumR += 0.5
                    }
                }
                score = if (scoreCount > 0) (100 * sumR / scoreCount / Rating.SUPER.code).toInt() else -1000
            }
        }
    }

    LaunchedEffect(feed, enableFilter, feedScreenMode) {
        Logd(TAG, "LaunchedEffect(feed, enableFilter, feedScreenMode)")
        if (feedScreenMode in listOf(FeedScreenMode.List, FeedScreenMode.History)) scope.launch(Dispatchers.IO) { assembleList() }
    }

    OpenDialogs()

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (showHeader) FeedDetailsHeader()
            if (feedScreenMode in listOf(FeedScreenMode.List, FeedScreenMode.History)) {
                val cameBack = currentEntry?.savedStateHandle?.get<Boolean>(COME_BACK) ?: false
                var scrollToOnStart by remember(episodes, curEpisode) {
                    mutableIntStateOf(if (cameBack) -1 else if (curEpisode?.feedId == feedId) episodes.indexOfFirst { it.id == curEpisode?.id } else -1) }
                infoBarText.value = "$listInfoText $feedOperationText"
                InforBar(infoBarText, swipeActions)
                EpisodeLazyColumn(context, episodes, feed = feed, layoutMode = layoutModeIndex, swipeActions = swipeActions,
                    scrollToOnStart = scrollToOnStart,
                    refreshCB = {
                        if (feed != null) runOnceOrAsk(context, feeds = listOf(feed!!))
                        else Logt(TAG, "feed is null, can not refresh")
                    },
                    selectModeCB = { showHeader = !it },
                    actionButtonCB = { e, type ->
                        Logd(TAG, "actionButtonCB type: $type ${e.feed?.id} ${feed?.id}")
                        if (e.feed?.id == feed?.id && type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAYLOCAL, ButtonTypes.STREAM)) {
                            runOnIOScope {
                                upsert(feed!!) { it.lastPlayed = Date().time }
                                queueToVirtual(e, episodes, listIdentity, feed!!.episodeSortOrder, true)
                            }
                        }
                    },
                )
            } else DetailUI()
        }
    }
}

private val TAG = Screens.FeedDetails.name
