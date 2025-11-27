package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.utils.HtmlToPlainText
import ac.mdiq.podcini.playback.base.InTheatre.VIRTUAL_QUEUE_SIZE
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.virQueue
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.AppPreferences.isAutodownloadEnabled
import ac.mdiq.podcini.preferences.AppPreferences.prefStreamOverDownload
import ac.mdiq.podcini.storage.database.FeedAssistant
import ac.mdiq.podcini.storage.database.buildListInfo
import ac.mdiq.podcini.storage.database.feedOperationText
import ac.mdiq.podcini.storage.database.getEpisodesAsFlow
import ac.mdiq.podcini.storage.database.getHistoryAsFlow
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.updateFeedDownloadURL
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.AutoDeleteAction
import ac.mdiq.podcini.storage.model.Feed.AutoDownloadPolicy
import ac.mdiq.podcini.storage.model.Feed.Companion.DEFAULT_INTERVALS
import ac.mdiq.podcini.storage.model.Feed.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.storage.model.Feed.Companion.INTERVAL_UNITS
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_NATURAL_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.FeedAutoDownloadFilter
import ac.mdiq.podcini.storage.specs.FeedFunding
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.specs.VolumeAdaptionSetting
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
import ac.mdiq.podcini.ui.compose.NumberEditor
import ac.mdiq.podcini.ui.compose.PlaybackSpeedDialog
import ac.mdiq.podcini.ui.compose.RemoveFeedDialog
import ac.mdiq.podcini.ui.compose.RenameOrCreateSyntheticFeed
import ac.mdiq.podcini.ui.compose.Spinner
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.VideoModeDialog
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
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.ext.toRealmList
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import kotlinx.coroutines.CoroutineScope
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
    Info,
    Settings
}

enum class ADLIncExc {
    INCLUDE,
    EXCLUDE
}

var feedScreenMode by mutableStateOf(FeedScreenMode.List)

var feedOnDisplay by mutableStateOf(Feed())

val queueSettingOptions = listOf("Default", "Active", "None", "Custom")

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
fun FeedDetailsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = LocalNavController.current

    val swipeActions = remember { SwipeActions(context, TAG) }

    var feed by remember { mutableStateOf<Feed?>(null) }

    var enableFilter by remember { mutableStateOf(true) }

    var feedFlow by remember { mutableStateOf<Flow<SingleQueryChange<Feed>>>(emptyFlow()) }

    fun addLocalFolderResult(uri: Uri?) {
        if (uri == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    requireNotNull(documentFile) { "Unable to retrieve document tree" }
                    feed?.downloadUrl = Feed.PREFIX_LOCAL_FOLDER + uri.toString()
                    if (feed != null) updateFeedFull(context, feed!!, removeUnlistedItems = true)
                }
                withContext(Dispatchers.Main) { Logt(TAG, context.getString(R.string.OK)) }
            } catch (e: Throwable) { withContext(Dispatchers.Main) { Loge(TAG, e.localizedMessage?:"No message") } }
        }
    }
    val addLocalFolderLauncher: ActivityResultLauncher<Uri?> = rememberLauncherForActivityResult(contract = AddLocalFolder()) { uri: Uri? -> addLocalFolderResult(uri) }
    
    DisposableEffect(lifecycleOwner) {
        Logd(TAG, "in DisposableEffect")
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect LifecycleEventObserver: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    feedFlow = realm.query<Feed>("id == $0", feedOnDisplay.id).first().asFlow()
//                    val testNum = 1
//                    val eList = realm.query(Episode::class).query("feedId == ${vm.feedID} AND playState == ${PlayState.SOON.code} SORT(pubDate DESC) LIMIT($testNum)").find()
//                    Logd(TAG, "test eList: ${eList.size}")
                    saveLastNavScreen(TAG, feed?.id?.toString())
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
            TTSObj.tts?.stop()
            TTSObj.tts?.shutdown()
            TTSObj.ttsWorking = false
            TTSObj.ttsReady = false
            TTSObj.tts = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var episodesFlow by remember { mutableStateOf<Flow<ResultsChange<Episode>>>(emptyFlow()) }

    var listIdentity by remember { mutableStateOf("") }

    var layoutModeIndex by remember { mutableIntStateOf(LayoutMode.Normal.ordinal) }

    var isFiltered by remember { mutableStateOf(false) }

    val feedChange by feedFlow.collectAsState(initial = null)
    feed = feedChange?.obj
    LaunchedEffect(feedChange, feedOnDisplay.id) {
        Logd(TAG, "LaunchedEffect(feedResult, feedOnDisplay.id)")
        isFiltered = !feed?.filterString.isNullOrBlank() && !feed?.episodeFilter?.propertySet.isNullOrEmpty()
    }

    val isCallable = remember(feed) { if (!feed?.link.isNullOrEmpty()) isCallable(context, Intent(Intent.ACTION_VIEW, feed!!.link!!.toUri())) else false }

    val showConnectLocalFolderConfirm = remember { mutableStateOf(false) }
    var showEditConfirmDialog by remember { mutableStateOf(false) }
    var editedUrl by remember { mutableStateOf("") }
    var showEditUrlSettingsDialog by remember { mutableStateOf(false) }
    var showChooseRatingDialog by remember { mutableStateOf(false) }
    var showRemoveFeedDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember {  mutableStateOf(false) }
    var showRenameDialog by remember {  mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    val infoBarText = remember { mutableStateOf("") }

    val episodesChange by episodesFlow.collectAsState(initial = null)
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
                                gearbox.feedUpdater(feed).startRefresh(context)
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

        if (feed != null && showFilterDialog) EpisodesFilterDialog(filter_ = feed!!.episodeFilter, onDismissRequest = { showFilterDialog = false }) { filter ->
            Logd(TAG, "persist Episode Filter(): feedId = [${feed?.id}], andOr = ${filter.andOr}, ${filter.propertySet.size} filterValues = ${filter.propertySet}")
            runOnIOScope { upsert(feed!!) { it.episodeFilter = filter } }
        }

        if (showRenameDialog) RenameOrCreateSyntheticFeed(feed) { showRenameDialog = false }

        if (feed != null && showSortDialog) EpisodeSortDialog(initOrder = feed!!.episodeSortOrder, onDismissRequest = { showSortDialog = false }) { order ->
            Logd(TAG, "persist Episode SortOrder_")
            runOnIOScope { upsert(feed!!) { it.episodeSortOrder = order ?: EpisodeSortOrder.DATE_DESC } }
        }

        swipeActions.ActionOptionsDialog()
    }

    @Composable
    fun FeedSettingsScreen() {
        val lifecycleOwner = LocalLifecycleOwner.current

        var queues: List<PlayQueue>? = remember { null }

        var audioType by remember { mutableStateOf(feed?.audioTypeSetting?.tag ?: Feed.AudioType.SPEECH.tag) }

        var audioQuality by remember { mutableStateOf(feed?.audioQualitySetting?.tag ?: Feed.AVQuality.GLOBAL.tag) }
        var videoQuality by remember { mutableStateOf(feed?.videoQualitySetting?.tag ?: Feed.AVQuality.GLOBAL.tag) }

        var autoUpdate by remember { mutableStateOf(feed?.keepUpdated == true) }

        var autoDeleteSummaryResId by remember { mutableIntStateOf(R.string.global_default) }
        var curPrefQueue by remember { mutableStateOf(feed?.queueTextExt ?: "Default") }
        var autoDeletePolicy = remember { AutoDeleteAction.GLOBAL.name }
        var videoModeSummaryResId by remember { mutableIntStateOf(R.string.global_default) }

        fun refresh() {
            audioType = feed?.audioTypeSetting?.tag ?: Feed.AudioType.SPEECH.tag
            audioQuality = feed?.audioQualitySetting?.tag ?: Feed.AVQuality.GLOBAL.tag
            videoQuality = feed?.videoQualitySetting?.tag ?: Feed.AVQuality.GLOBAL.tag
            when (feed?.videoModePolicy) {
                VideoMode.NONE -> videoModeSummaryResId = R.string.global_default
                VideoMode.WINDOW_VIEW -> videoModeSummaryResId = R.string.feed_video_mode_window
                VideoMode.FULL_SCREEN_VIEW -> videoModeSummaryResId = R.string.feed_video_mode_fullscreen
                VideoMode.AUDIO_ONLY -> videoModeSummaryResId = R.string.feed_video_mode_audioonly
                else -> {}
            }
            when (feed?.autoDeleteAction) {
                AutoDeleteAction.GLOBAL -> {
                    autoDeleteSummaryResId = R.string.global_default
                    autoDeletePolicy = AutoDeleteAction.GLOBAL.tag
                }
                AutoDeleteAction.ALWAYS -> {
                    autoDeleteSummaryResId = R.string.feed_auto_download_always
                    autoDeletePolicy = AutoDeleteAction.ALWAYS.tag
                }
                AutoDeleteAction.NEVER -> {
                    autoDeleteSummaryResId = R.string.feed_auto_download_never
                    autoDeletePolicy = AutoDeleteAction.NEVER.tag
                }
                else -> {}
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                Logd(TAG, "LifecycleEventObserver event: $event")
                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
//                        refresh()
                    }
                    Lifecycle.Event.ON_START -> {
                        //                    procFlowEvents()
                    }
                    Lifecycle.Event.ON_STOP -> {}
                    Lifecycle.Event.ON_DESTROY -> {}
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                //            cancelFlowEvents()
//                feed = null
                queues = null
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(feed) {
            Logd(TAG, "LaunchedEffect feed")
            refresh()
        }

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun MyTopAppBar() {
            Box {
                TopAppBar(title = {
                    Column {
                        Text(text = stringResource(R.string.feed_settings_label), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (!feed?.title.isNullOrBlank()) Text(text = feed!!.title!!, fontSize = 16.sp)
                    }
                }, navigationIcon = {
                    IconButton(onClick = {
                        feedScreenMode = FeedScreenMode.List
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                })
                HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        @Composable
        fun AutoDeleteDialog(onDismissRequest: () -> Unit) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(autoDeletePolicy) }
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeedAutoDeleteOptions.forEach { text ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = (text == selectedOption),
                                    onCheckedChange = {
                                        Logd(TAG, "row clicked: $text $selectedOption")
                                        if (text != selectedOption) {
                                            onOptionSelected(text)
                                            val action_ = when (text) {
                                                AutoDeleteAction.GLOBAL.tag -> AutoDeleteAction.GLOBAL
                                                AutoDeleteAction.ALWAYS.tag -> AutoDeleteAction.ALWAYS
                                                AutoDeleteAction.NEVER.tag -> AutoDeleteAction.NEVER
                                                else -> AutoDeleteAction.GLOBAL
                                            }
                                            upsertBlk(feed!!) { it.autoDeleteAction = action_ }
                                            onDismissRequest()
                                        }
                                    }
                                )
                                Text(text = text, style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun VolumeAdaptionDialog(onDismissRequest: () -> Unit) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(feed?.volumeAdaptionSetting ?: VolumeAdaptionSetting.OFF) }
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VolumeAdaptionSetting.entries.forEach { item ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = (item == selectedOption),
                                    onCheckedChange = { _ ->
                                        Logd(TAG, "row clicked: $item $selectedOption")
                                        if (item != selectedOption) {
                                            onOptionSelected(item)
                                            upsertBlk(feed!!) { it.volumeAdaptionSetting = item }
                                            onDismissRequest()
                                        }
                                    }
                                )
                                Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun SetAudioType(selectedOption: String, onDismissRequest: () -> Unit) {
            var selected by remember {mutableStateOf(selectedOption)}
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Feed.AudioType.entries.forEach { option ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = option.tag == selected,
                                    onCheckedChange = { isChecked ->
                                        selected = option.tag
                                        if (isChecked) Logd(TAG, "$option is checked")
                                        val type = Feed.AudioType.fromTag(selected)
                                        upsertBlk(feed!!) { it.audioType = type.code }
                                        onDismissRequest()
                                    }
                                )
                                Text(option.tag)
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun SetAudioQuality(selectedOption: String, onDismissRequest: () -> Unit) {
            var selected by remember {mutableStateOf(selectedOption)}
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Feed.AVQuality.entries.forEach { option ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = option.tag == selected,
                                    onCheckedChange = { isChecked ->
                                        selected = option.tag
                                        if (isChecked) Logd(TAG, "$option is checked")
                                        val type = Feed.AVQuality.fromTag(selected)
                                        upsertBlk(feed!!) { it.audioQuality = type.code }
                                        onDismissRequest()
                                    })
                                Text(option.tag)
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun SetVideoQuality(selectedOption: String, onDismissRequest: () -> Unit) {
            var selected by remember {mutableStateOf(selectedOption)}
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Feed.AVQuality.entries.forEach { option ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = option.tag == selected,
                                    onCheckedChange = { isChecked ->
                                        selected = option.tag
                                        if (isChecked) Logd(TAG, "$option is checked")
                                        val type = Feed.AVQuality.fromTag(selected)
                                        upsertBlk(feed!!) { it.videoQuality = type.code }
                                        onDismissRequest()
                                    })
                                Text(option.tag)
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun AuthenticationDialog(onDismiss: () -> Unit) {
            val context = LocalContext.current
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val oldName = feed?.username?:""
                        var newName by remember { mutableStateOf(oldName) }
                        TextField(value = newName, onValueChange = { newName = it }, label = { Text("Username") })
                        val oldPW = feed?.password?:""
                        var newPW by remember { mutableStateOf(oldPW) }
                        TextField(value = newPW, onValueChange = { newPW = it }, label = { Text("Password") })
                        Button(onClick = {
                            if (newName.isNotEmpty() && oldName != newName) {
                                runOnIOScope {
                                    upsert(feed!!) {
                                        it.username = newName
                                        it.password = newPW
                                    }
                                    gearbox.feedUpdater(feed).startRefresh(context)
                                }
                                onDismiss()
                            }
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }

        @Composable
        fun AutoSkipDialog(onDismiss: () -> Unit) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var intro by remember { mutableIntStateOf((feed?.introSkip ?: 0)) }
                        NumberEditor(intro, label = stringResource(R.string.skip_first_hint), nz = false, instant = true, modifier = Modifier) { intro = it }
                        var ending by remember { mutableIntStateOf((feed?.endingSkip ?: 0)) }
                        NumberEditor(ending, label = stringResource(R.string.skip_last_hint), nz = false, instant = true, modifier = Modifier) { ending = it }
                        Button(onClick = {
                            upsertBlk(feed!!) {
                                it.introSkip = intro
                                it.endingSkip = ending
                            }
                            onDismiss()
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }

        @Composable
        fun RepeatIntervalsDialog(onDismiss: () -> Unit) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var intervals = remember { feed?.repeatIntervals?.toMutableList() }
                        if (intervals.isNullOrEmpty()) intervals = DEFAULT_INTERVALS.toMutableList()
                        val units = INTERVAL_UNITS.map { stringResource(it) }
                        for (i in intervals.indices) {
                            NumberEditor(intervals[i], label = "in " + units[i], nz = false, instant = true, modifier = Modifier) { intervals[i] = it }
                        }
                        Button(onClick = {
                            upsertBlk(feed!!) { it.repeatIntervals = intervals.toRealmList() }
                            onDismiss()
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }

        @Composable
        fun TitleSummarySwitch(titleRes: Int, summaryRes: Int, iconRes: Int, initVal: Boolean, cb: ((Boolean)->Unit)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                    if (iconRes > 0) Icon(ImageVector.vectorResource(id = iconRes), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(titleRes), style = CustomTextStyles.titleCustom, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))
                    var checked by remember { mutableStateOf(initVal) }
                    Switch(checked = checked, modifier = Modifier.height(24.dp), onCheckedChange = {
                        checked = it
                        cb.invoke(it)
                    })
                }
                Text(text = stringResource(summaryRes), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
        }

        val textColor = MaterialTheme.colorScheme.onSurface
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.padding(innerPadding).padding(start = 20.dp, end = 16.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TitleSummarySwitch(R.string.use_wide_layout, R.string.use_wide_layout_summary, R.drawable.rounded_responsive_layout_24, feed?.useWideLayout == true) {
                    upsertBlk(feed!!) { f -> f.useWideLayout = it }
                }
                TitleSummarySwitch(R.string.use_episode_image, R.string.use_episode_image_summary, R.drawable.outline_broken_image_24, feed?.useEpisodeImage == true) {
                    upsertBlk(feed!!) { f ->
                        f.useEpisodeImage = it }
                }
                Column {
                    var showDialog by remember { mutableStateOf(false) }
                    if (showDialog) SetAudioType(selectedOption = audioType, onDismissRequest = { showDialog = false })
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.pref_feed_audio_type), style = CustomTextStyles.titleCustom, color = textColor,
                            modifier = Modifier.clickable(onClick = {
                                audioType = feed!!.audioTypeSetting.tag
                                showDialog = true
                            }))
                        Spacer(modifier = Modifier.width(30.dp))
                        Text(audioType, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    Text(text = stringResource(R.string.pref_feed_audio_type_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                if ((feed?.id ?: 0) >= MAX_NATURAL_SYNTHETIC_ID && feed?.hasVideoMedia == true) {
                    //                    video mode
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            var showDialog by remember { mutableStateOf(false) }
                            if (showDialog) VideoModeDialog(initMode = feed?.videoModePolicy, onDismissRequest = { showDialog = false }) { mode ->
                                upsertBlk(feed!!) { it.videoModePolicy = mode }
                            }
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.feed_video_mode_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog = true }))
                            Spacer(modifier = Modifier.width(30.dp))
                            Text(text = stringResource(videoModeSummaryResId), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }
                }
                if (feed?.type == Feed.FeedType.YOUTUBE.name) {
                    //                    audio quality
                    Column {
                        var showDialog by remember { mutableStateOf(false) }
                        if (showDialog) SetAudioQuality(selectedOption = audioQuality, onDismissRequest = { showDialog = false })
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.pref_feed_audio_quality), style = CustomTextStyles.titleCustom, color = textColor,
                                modifier = Modifier.clickable(onClick = {
                                    audioQuality = feed!!.audioQualitySetting.tag
                                    showDialog = true
                                }))
                            Spacer(modifier = Modifier.width(30.dp))
                            Text(audioQuality, style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        Text(text = stringResource(R.string.pref_feed_audio_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    if (feed?.videoModePolicy != VideoMode.AUDIO_ONLY) {
                        //                    video quality
                        Column {
                            var showDialog by remember { mutableStateOf(false) }
                            if (showDialog) SetVideoQuality(selectedOption = videoQuality, onDismissRequest = { showDialog = false })
                            Row(Modifier.fillMaxWidth()) {
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_videocam), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(text = stringResource(R.string.pref_feed_video_quality), style = CustomTextStyles.titleCustom, color = textColor,
                                    modifier = Modifier.clickable(onClick = {
                                        videoQuality = feed!!.videoQualitySetting.tag
                                        showDialog = true
                                    }))
                                Spacer(modifier = Modifier.width(30.dp))
                                Text(videoQuality, style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                            Text(text = stringResource(R.string.pref_feed_video_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }
                }
                @Composable
                fun SetAssociatedQueue(selectedOption: String, onDismissRequest: () -> Unit) {
                    var selected by remember {mutableStateOf(selectedOption)}
                    Dialog(onDismissRequest = { onDismissRequest() }) {
                        Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                queueSettingOptions.forEach { option ->
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = option == selected,
                                            onCheckedChange = { isChecked ->
                                                selected = option
                                                if (isChecked) Logd(TAG, "$option is checked")
                                                when (selected) {
                                                    "Default" -> {
                                                        upsertBlk(feed!!) { it.queueId = 0L }
                                                        curPrefQueue = selected
                                                        onDismissRequest()
                                                    }
                                                    "Active" -> {
                                                        upsertBlk(feed!!) { it.queueId = -1L }
                                                        curPrefQueue = selected
                                                        onDismissRequest()
                                                    }
                                                    "None" -> {
                                                        upsertBlk(feed!!) {
                                                            it.queueId = -2L
                                                            it.autoDownload = false
                                                            it.autoEnqueue = false
                                                        }
                                                        curPrefQueue = selected
                                                        onDismissRequest()
                                                    }
                                                    "Custom" -> {}
                                                }
                                            }
                                        )
                                        Text(option)
                                    }
                                }
                                if (selected == "Custom") {
                                    if (queues == null) queues = realm.query(PlayQueue::class).find()
                                    Logd(TAG, "queues: ${queues?.size}")
                                    Spinner(items = queues!!.map { it.name }, selectedItem = feed?.queue?.name ?: "Default") { index ->
                                        Logd(TAG, "Queue selected: ${queues!![index].name}")
                                        val q = queues!![index]
                                        upsertBlk(feed!!) { it.queue = q }
                                        curPrefQueue = q.name
                                        onDismissRequest()
                                    }
                                }
                            }
                        }
                    }
                }
                //                    associated queue
                Column {
                    curPrefQueue = feed?.queueTextExt ?: "Default"
                    var showDialog by remember { mutableStateOf(false) }
                    var selectedOption by remember { mutableStateOf(feed?.queueText ?: "Default") }
                    if (showDialog) SetAssociatedQueue(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.pref_feed_associated_queue), style = CustomTextStyles.titleCustom, color = textColor,
                            modifier = Modifier.clickable(onClick = {
                                selectedOption = feed?.queueText ?: "Default"
                                showDialog = true
                            })
                        )
                    }
                    Text(text = curPrefQueue + " : " + stringResource(R.string.pref_feed_associated_queue_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                //                    tags
                Column {
                    var showDialog by remember { mutableStateOf(false) }
                    if (showDialog) TagSettingDialog(feeds_ = listOf(feed!!), onDismiss = { showDialog = false })
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_tag), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.tags_label), style = CustomTextStyles.titleCustom, color = textColor,
                            modifier = Modifier.clickable(onClick = { showDialog = true }))
                    }
                    Text(text = stringResource(R.string.feed_tags_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                //                    playback speed
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        if (showDialog.value) PlaybackSpeedDialog(listOf(feed!!), initSpeed = feed!!.playSpeed, maxSpeed = 3f,
                            onDismiss = { showDialog.value = false }) { newSpeed ->
                            upsertBlk(feed!!) { it.playSpeed = newSpeed }
                        }
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_playback_speed), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.playback_speed), style = CustomTextStyles.titleCustom, color = textColor,
                            modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                    }
                    Text(text = stringResource(R.string.pref_feed_playback_speed_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                //                    volume adaption
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        if (showDialog.value) VolumeAdaptionDialog(onDismissRequest = { showDialog.value = false })
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_volume_adaption), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.feed_volume_adapdation), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                    }
                    Text(text = stringResource(R.string.feed_volume_adaptation_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                //                    authentication
                if ((feed?.id ?: 0) > 0 && feed?.isLocalFeed != true) {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            val showDialog = remember { mutableStateOf(false) }
                            if (showDialog.value) AuthenticationDialog(onDismiss = { showDialog.value = false })
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_key), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.authentication_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                        }
                        Text(text = stringResource(R.string.authentication_descr), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                }
                var autoDownloadChecked by remember { mutableStateOf(feed?.autoDownload == true) }
                var preferStreaming by remember { mutableStateOf(feed?.prefStreamOverDownload == true) }
                if (feed?.type != Feed.FeedType.YOUTUBE.name || !preferStreaming) {
                    //                    prefer streaming
                    TitleSummarySwitch(R.string.pref_stream_over_download_title, R.string.pref_stream_over_download_sum, R.drawable.ic_stream, preferStreaming) {
                        preferStreaming = it
                        if (preferStreaming) {
                            prefStreamOverDownload = true
                            autoDownloadChecked = false
                        }
                        upsertBlk(feed!!) { f ->
                            f.prefStreamOverDownload = preferStreaming
                            if (preferStreaming) f.autoDownload = false
                        }
                    }
                }
                //                    auto skip
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        if (showDialog.value) AutoSkipDialog(onDismiss = { showDialog.value = false })
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_skip_24dp), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.pref_feed_skip), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                    }
                    Text(text = stringResource(R.string.pref_feed_skip_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                //                    repeat intervals
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        if (showDialog.value) RepeatIntervalsDialog(onDismiss = { showDialog.value = false })
                        Icon(ImageVector.vectorResource(id = R.drawable.baseline_replay_24), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.pref_feed_intervals), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                    }
                    Text(text = stringResource(R.string.pref_feed_intervals_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                if (feed?.type != Feed.FeedType.YOUTUBE.name) {
                    //                    auto delete
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            val showDialog = remember { mutableStateOf(false) }
                            if (showDialog.value) AutoDeleteDialog(onDismissRequest = { showDialog.value = false })
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.auto_delete_episode), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                        }
                        Text(text = stringResource(R.string.auto_delete_sum) + ": " + stringResource(autoDeleteSummaryResId), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                }
                if ((feed?.id ?: 0) > MAX_SYNTHETIC_ID) {
                    //                    max episodes
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_refresh), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.limit_episodes_to), style = CustomTextStyles.titleCustom, color = textColor)
                            Spacer(modifier = Modifier.weight(1f))
                            NumberEditor(feed!!.limitEpisodesCount, label = "0 = unlimited", nz = false, modifier = Modifier.width(150.dp)) {
                                upsertBlk(feed!!) { f -> f.limitEpisodesCount = it }
                            }
                        }
                        Text(text = stringResource(R.string.limit_episodes_to_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }

                    //                    refresh
                    TitleSummarySwitch(R.string.keep_updated, R.string.keep_updated_summary, R.drawable.ic_refresh, autoUpdate) {
                        autoUpdate = it
                        upsertBlk(feed!!) { f -> f.keepUpdated = autoUpdate }
                    }
                }
                if (curPrefQueue != "None") {
                    //                    auto add new to queue
                    TitleSummarySwitch(R.string.audo_add_new_queue, R.string.audo_add_new_queue_summary, androidx.media3.session.R.drawable.media3_icon_queue_add, feed?.autoAddNewToQueue != false) {
                        upsertBlk(feed!!) { f -> f.autoAddNewToQueue = it }
                    }
                }
                var autoEnqueueChecked by remember { mutableStateOf(feed?.autoEnqueue == true) }
                Row(Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.auto_colon), style = CustomTextStyles.titleCustom, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = stringResource(R.string.enqueue), style = CustomTextStyles.titleCustom, color = textColor)
                    if (curPrefQueue != "None") {
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(checked = autoEnqueueChecked, modifier = Modifier.height(24.dp),
                            onCheckedChange = {
                                autoEnqueueChecked = it
                                if (autoEnqueueChecked) autoDownloadChecked = false
                                upsertBlk(feed!!) { f ->
                                    f.autoEnqueue = autoEnqueueChecked
                                    f.autoDownload = autoDownloadChecked
                                }
                            })
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = stringResource(R.string.download), style = CustomTextStyles.titleCustom, color = textColor)
                    if (feed?.type != Feed.FeedType.YOUTUBE.name) {
                        if (isAutodownloadEnabled && !preferStreaming) {
                            //                    auto download
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(checked = autoDownloadChecked, modifier = Modifier.height(24.dp),
                                onCheckedChange = {
                                    autoDownloadChecked = it
                                    if (autoDownloadChecked) autoEnqueueChecked = false
                                    upsertBlk(feed!!) { f ->
                                        f.autoDownload = autoDownloadChecked
                                        f.autoEnqueue = autoEnqueueChecked
                                    }
                                })
                        }
                    }
                }
                if (!autoEnqueueChecked && !autoDownloadChecked) {
                    Text(text = stringResource(R.string.auto_enqueue_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    if (curPrefQueue == "None") Text(text = stringResource(R.string.auto_enqueue_sum1), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    Text(text = stringResource(R.string.auto_download_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    if (!isAutodownloadEnabled) Text(text = stringResource(R.string.auto_download_disabled_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                if (autoDownloadChecked || autoEnqueueChecked) {
                    var newCache by remember { mutableIntStateOf((feed?.autoDLMaxEpisodes ?: 2)) }
                    @Composable
                    fun SetAutoDLEQCacheDialog(onDismiss: () -> Unit) {
                        Dialog(onDismissRequest = onDismiss) {
                            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NumberEditor(newCache, label = stringResource(R.string.max_episodes_cache), nz = false, instant = true, modifier = Modifier) { newCache = it }
                                    //                    counting played
                                    var countingPlayed by remember { mutableStateOf(feed?.countingPlayed != false) }
                                    if (autoDownloadChecked) Column {
                                        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                                        Row(Modifier.fillMaxWidth()) {
                                            Checkbox(checked = countingPlayed, modifier = Modifier.height(24.dp), onCheckedChange = { countingPlayed = it })
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(text = stringResource(R.string.pref_auto_download_counting_played_title), style = MaterialTheme.typography.bodyMedium, color = textColor)
                                        }
                                        Text(text = stringResource(R.string.pref_auto_download_counting_played_summary), style = MaterialTheme.typography.bodySmall, color = textColor)
                                        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                                    }
                                    Button(onClick = {
                                        if (newCache > 0) {
                                            upsertBlk(feed!!) {
                                                it.autoDLMaxEpisodes = newCache
                                                if (autoDownloadChecked) it.countingPlayed = countingPlayed
                                            }
                                            onDismiss()
                                        }
                                    }) { Text(stringResource(R.string.confirm_label)) }
                                }
                            }
                        }
                    }
                    //                    episode cache
                    Column(modifier = Modifier.padding(start = 20.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            val showDialog = remember { mutableStateOf(false) }
                            if (showDialog.value) SetAutoDLEQCacheDialog(onDismiss = { showDialog.value = false })
                            Text(text = stringResource(R.string.pref_episode_cache_title), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                            Spacer(modifier = Modifier.width(30.dp))
                            Text(newCache.toString(), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        Text(text = stringResource(R.string.pref_episode_cache_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    //                    include Soon
                    Column(modifier = Modifier.padding(start = 20.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(text = stringResource(R.string.pref_auto_download_include_soon_title), style = CustomTextStyles.titleCustom, color = textColor)
                            Spacer(modifier = Modifier.weight(1f))
                            var checked by remember { mutableStateOf(feed?.autoDLSoon != false) }
                            Switch(checked = checked, modifier = Modifier.height(24.dp),
                                onCheckedChange = {
                                    checked = it
                                    upsertBlk(feed!!) { f -> f.autoDLSoon = checked }
                                }
                            )
                        }
                        Text(text = stringResource(R.string.pref_auto_download_include_soon_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    val (selectedPolicy, onPolicySelected) = remember { mutableStateOf(feed?.autoDLPolicy ?: AutoDownloadPolicy.ONLY_NEW) }
                    @Composable
                    fun AutoDLEQPolicyDialog(onDismissRequest: () -> Unit) {
                        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
                            title = { Text(stringResource(R.string.feed_automation_policy), style = CustomTextStyles.titleCustom) },
                            text = {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    AutoDownloadPolicy.entries.forEach { item ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = (item == selectedPolicy), onCheckedChange = { onPolicySelected(item) })
                                            Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 8.dp))
                                        }
                                        if (selectedPolicy == AutoDownloadPolicy.ONLY_NEW && item == selectedPolicy)
                                            Row(Modifier.fillMaxWidth().padding(start = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                                                var replaceChecked by remember { mutableStateOf(selectedPolicy.replace) }
                                                Checkbox(checked = replaceChecked, onCheckedChange = {
                                                    replaceChecked = it
                                                    selectedPolicy.replace = it
                                                    item.replace = it
                                                })
                                                Text(text = stringResource(R.string.replace), style = MaterialTheme.typography.bodyMedium.merge(), modifier = Modifier.padding(start = 8.dp))
                                            }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    Logd(TAG, "autoDLPolicy: ${selectedPolicy.name} ${selectedPolicy.replace}")
                                    upsertBlk(feed!!) {
                                        it.autoDLPolicy = selectedPolicy
                                        if (selectedPolicy == AutoDownloadPolicy.FILTER_SORT) {
                                            it.episodeFilterADL = feed!!.episodeFilter
                                            it.episodesSortOrderADL = feed!!.episodeSortOrder
                                        }
                                    }
                                    onDismissRequest()
                                }) { Text(stringResource(R.string.confirm_label)) }
                            },
                            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
                        )
                    }
                    //                    automation policy
                    Column(modifier = Modifier.padding(start = 20.dp, bottom = 5.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            val showDialog = remember { mutableStateOf(false) }
                            if (showDialog.value) AutoDLEQPolicyDialog(onDismissRequest = { showDialog.value = false })
                            Text(text = stringResource(R.string.feed_automation_policy) + ":", style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                            Text(stringResource(selectedPolicy.resId), modifier = Modifier.padding(start = 20.dp))
                        }
                    }
                    if (selectedPolicy != AutoDownloadPolicy.FILTER_SORT) {
                        @OptIn(ExperimentalLayoutApi::class)
                        @Composable
                        fun AutoDownloadFilterDialog(filter: FeedAutoDownloadFilter, inexcl: ADLIncExc, onDismiss: () -> Unit, onConfirmed: (FeedAutoDownloadFilter) -> Unit) {
                            fun toFilterString(words: List<String>?): String {
                                val result = StringBuilder()
                                for (word in words!!) result.append("\"").append(word).append("\" ")
                                return result.toString()
                            }
                            Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
                                Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(R.string.episode_filters_label), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                                        val termList = remember { if (inexcl == ADLIncExc.EXCLUDE) filter.excludeTerms.toMutableStateList() else filter.includeTerms.toMutableStateList() }
                                        var filterMinDuration by remember { mutableStateOf(filter.hasMinDurationFilter()) }
                                        var filterMaxDuration by remember { mutableStateOf(filter.hasMaxDurationFilter()) }
                                        var markPlayedChecked by remember { mutableStateOf(filter.markExcludedPlayed) }
                                        fun isFilterEnabled(): Boolean = termList.isNotEmpty() || filterMinDuration || filterMaxDuration || markPlayedChecked
                                        var filtermodifier by remember { mutableStateOf(isFilterEnabled()) }
                                        val textRes = remember { if (inexcl == ADLIncExc.EXCLUDE) R.string.exclude_terms else R.string.include_terms }
                                        Row {
                                            Checkbox(checked = filtermodifier, onCheckedChange = { isChecked ->
                                                filtermodifier = isChecked
                                                if (!filtermodifier) {
                                                    termList.clear()
                                                    filterMinDuration = false
                                                    filterMaxDuration = false
                                                    markPlayedChecked = false
                                                }
                                            })
                                            Text(text = stringResource(textRes), style = MaterialTheme.typography.bodyMedium.merge(), modifier = Modifier.weight(1f))
                                        }
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            termList.forEach {
                                                FilterChip(onClick = {  }, label = { Text(it) }, selected = false, trailingIcon = {
                                                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon", modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = { termList.remove(it) })) })
                                            }
                                        }
                                        var text by remember { mutableStateOf("") }
                                        fun setText() {
                                            if (text.isNotBlank()) {
                                                val newWord = text.replace("\"", "").trim { it <= ' ' }
                                                if (newWord.isNotBlank() && newWord !in termList) {
                                                    termList.add(newWord)
                                                    text = ""
                                                }
                                                filtermodifier = isFilterEnabled()
                                            }
                                        }
                                        TextField(value = text, onValueChange = { newTerm -> text = newTerm },
                                            placeholder = { Text(stringResource(R.string.add_term_hint)) }, keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { setText() }),
                                            trailingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Add term", modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                                setText()
                                            })) },
                                            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth()
                                        )
                                        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                                        var filterMinDurationMinutes by remember { mutableIntStateOf((filter.minDurationFilter / 60)) }
                                        var filterMaxDurationMinutes by remember { mutableIntStateOf((filter.maxDurationFilter / 60)) }
                                        if (inexcl == ADLIncExc.EXCLUDE) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = filterMinDuration, onCheckedChange = { isChecked ->
                                                    filterMinDuration = isChecked
                                                    filtermodifier = isFilterEnabled()
                                                })
                                                Text(text = stringResource(R.string.exclude_shorter_than), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                                if (filterMinDuration) {
                                                    NumberEditor(filterMinDurationMinutes, stringResource(R.string.time_minutes), nz = true, instant = true, modifier = Modifier.width(50.dp).height(30.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)) {
                                                        filterMinDurationMinutes = it
                                                    }
                                                }
                                            }
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = filterMaxDuration, onCheckedChange = { isChecked ->
                                                    filtermodifier = isFilterEnabled()
                                                    filterMaxDuration = isChecked
                                                })
                                                Text(text = stringResource(R.string.exclude_longer_than), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                                if (filterMaxDuration) {
                                                    NumberEditor(filterMaxDurationMinutes, stringResource(R.string.time_minutes), nz = true, instant = true, modifier = Modifier.width(50.dp).height(30.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)) {
                                                        filterMaxDurationMinutes = it
                                                    }
                                                }
                                            }
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = markPlayedChecked, onCheckedChange = { isChecked ->
                                                    filtermodifier = isFilterEnabled()
                                                    markPlayedChecked = isChecked
                                                })
                                                Text(text = stringResource(R.string.mark_excluded_episodes_played), style = MaterialTheme.typography.bodyMedium.merge())
                                            }
                                        }
                                        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                                            Button(onClick = {
                                                if (inexcl == ADLIncExc.EXCLUDE) {
                                                    if (filtermodifier) {
                                                        val minDuration = if (filterMinDuration) filterMinDurationMinutes * 60 else -1
                                                        val maxDuration = if (filterMaxDuration) filterMaxDurationMinutes * 60 else -1
                                                        val excludeFilter = toFilterString(termList)
                                                        onConfirmed(FeedAutoDownloadFilter(filter.includeFilterRaw, excludeFilter, minDuration, maxDuration, markPlayedChecked))
                                                    } else onConfirmed(FeedAutoDownloadFilter())
                                                } else {
                                                    if (filtermodifier) {
                                                        val includeFilter = toFilterString(termList)
                                                        onConfirmed(FeedAutoDownloadFilter(includeFilter, filter.excludeFilterRaw, filter.minDurationFilter, filter.maxDurationFilter, filter.markExcludedPlayed))
                                                    } else onConfirmed(FeedAutoDownloadFilter())
                                                }
                                                onDismiss()
                                            }) { Text(stringResource(R.string.confirm_label)) }
                                            Spacer(Modifier.weight(1f))
                                            Button(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel_label)) }
                                        }
                                    }
                                }
                            }
                        }
                        //                    inclusive filter
                        Column(modifier = Modifier.padding(start = 20.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                val showDialog = remember { mutableStateOf(false) }
                                if (showDialog.value) AutoDownloadFilterDialog(feed?.autoDownloadFilter!!, ADLIncExc.INCLUDE, onDismiss = { showDialog.value = false }) { filter -> upsertBlk(feed!!) { it.autoDownloadFilter = filter } }
                                Text(text = stringResource(R.string.episode_inclusive_filters_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                            }
                            Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        //                    exclusive filter
                        Column(modifier = Modifier.padding(start = 20.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                val showDialog = remember { mutableStateOf(false) }
                                if (showDialog.value) AutoDownloadFilterDialog(feed?.autoDownloadFilter!!, ADLIncExc.EXCLUDE, onDismiss = { showDialog.value = false }) { filter -> upsertBlk(feed!!) { it.autoDownloadFilter = filter } }
                                Text(text = stringResource(R.string.episode_exclusive_filters_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                            }
                            Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    } else {
                        Column(modifier = Modifier.padding(start = 20.dp, bottom = 5.dp)) {
                            Text("Sorted by: " + stringResource(feed?.episodesSortOrderADL?.res ?: 0), modifier = Modifier.padding(start = 10.dp))
                            Text("Filtered by: ", modifier = Modifier.padding(start = 10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(start = 20.dp)) {
                                feed?.episodeFilterADL?.propertySet?.forEach { FilterChip(onClick = { }, label = { Text(it) }, selected = false) }
                            }
                        }
                    }
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
                        if (feedScreenMode == FeedScreenMode.List) Text(episodes.size.toString() + " / " + feed?.episodes?.size?.toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        else Text((feed?.episodes?.size ?: 0).toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        val textColor = MaterialTheme.colorScheme.onSurface
        val buttonColor = Color(0xDDFFD700)
        val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
        Box {
            TopAppBar(title = { Text("") }, navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }, actions = {
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
                IconButton(onClick = {
                    val q = feed?.queue
                    if (q != null && q != curQueue) curQueue = q
                    navController.navigate(Screens.Queues.name)
                    isBSExpanded = false
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), contentDescription = "queue") }
                IconButton(onClick = {
                    setSearchTerms(feed = feed)
                    navController.navigate(Screens.Search.name)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                IconButton(onClick = {
                    if (feed != null) {
                        feedOnDisplay = feed!!
//                        navController.navigate(Screens.FeedSettings.name)
                        feedScreenMode = FeedScreenMode.Settings
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
                        if (!feed?.episodes.isNullOrEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.fetch_size)) }, onClick = {
                            feedOperationText = context.getString(R.string.fetch_size)
                            scope.launch {
                                for (e in feed!!.episodes) e.fetchMediaSize(force = true)
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
                            gearbox.feedUpdater(feed).startRefresh(context)
                            expanded = false
                        })
                        if (feed != null) DropdownMenuItem(text = { Text(stringResource(R.string.load_complete_feed)) }, onClick = {
                            gearbox.feedUpdater(feed, fullUpdate = true).startRefresh(context)
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
        val context = LocalContext.current
        val scrollState = rememberScrollState()
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

        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            SelectionContainer {
                Column {
                    Text(feed?.title ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
                    Text(feed?.author ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Text(HtmlToPlainText.getPlainText(feed?.description ?: ""), color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(stringResource(R.string.my_opinion_label) + if (feed?.comment.isNullOrBlank()) " (Add)" else "", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom,
                modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable {
                    editCommentText = TextFieldValue((if (feed?.comment.isNullOrBlank()) "" else feed!!.comment + "\n") + fullDateTimeString(localTime) + ":\n")
                    showEditComment = true
                })
            if (!feed?.comment.isNullOrBlank()) SelectionContainer { Text(feed?.comment ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp)) }

            Text(stringResource(R.string.statistics_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row {
                TextButton({ showFeedStats = true }) { Text(stringResource(R.string.this_podcast)) }
                Spacer(Modifier.width(20.dp))
                TextButton({ navController.navigate(Screens.Statistics.name) }) { Text(stringResource(R.string.all_podcasts)) }
            }
            if (feed?.isSynthetic() == false) {
                TextButton(modifier = Modifier.padding(top = 10.dp), onClick = {
                    setOnlineSearchTerms(CombinedSearcher::class.java, "${feed?.author} podcasts")
                    navController.navigate(Screens.OnlineResults.name)
                }) { Text(stringResource(R.string.feeds_related_to_author)) }
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
                        // Filter for duplicates, but keep items in the order that they have in the feed.
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
            if (!feed?.episodes.isNullOrEmpty()) {
                var sumR = 0.0
                scoreCount = 0
                for (e in feed!!.episodes) {
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

    if (feedScreenMode == FeedScreenMode.Settings) FeedSettingsScreen()
    else {
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                FeedDetailsHeader()
                if (feedScreenMode in listOf(FeedScreenMode.List, FeedScreenMode.History)) {
                    infoBarText.value = "$listInfoText $feedOperationText"
                    InforBar(infoBarText, swipeActions)
                    EpisodeLazyColumn(context, episodes, feed = feed, layoutMode = layoutModeIndex, swipeActions = swipeActions,
                        refreshCB = { runOnceOrAsk(context, feed = feed) },
                        actionButtonCB = { e, type ->
                            Logd(TAG, "actionButtonCB type: $type")
                            if (e.feed?.id == feed?.id && type in listOf(ButtonTypes.PLAY, ButtonTypes.PLAYLOCAL, ButtonTypes.STREAM)) {
                                runOnIOScope {
                                    upsert(feed!!) { it.lastPlayed = Date().time }
                                    if (virQueue.identity != listIdentity) {
                                        virQueue = upsert(virQueue) { q ->
                                            q.identity = listIdentity
                                            q.sortOrder = feed!!.episodeSortOrder
                                            q.episodeIds.clear()
                                            q.episodeIds.addAll(episodes.take(VIRTUAL_QUEUE_SIZE).map { it.id })
                                        }
                                        virQueue.episodes.clear()
                                        actQueue = virQueue
                                        Logt(TAG, "first $VIRTUAL_QUEUE_SIZE episodes are added to the Virtual queue")
                                    }
                                }
                            }
                        },
                    )
                } else DetailUI()
            }
        }
    }
}

private val TAG = Screens.FeedDetails.name
object TTSObj {
    var tts: TextToSpeech? = null
    var ttsReady = false
    var ttsWorking = false

    fun ensureTTS(context: Context) {
        if (!ttsReady && tts == null) CoroutineScope(Dispatchers.Default).launch {
            Logd(TAG, "starting TTS")
            tts = TextToSpeech(context) { status: Int ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    Logt(TAG, "TTS init success")
                } else Loge(TAG, context.getString(R.string.tts_init_failed))
            }
        }
    }
}
