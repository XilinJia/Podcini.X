package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.utils.NetworkUtils.isImageDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.MonitorEntity
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.subscribeEpisode
import ac.mdiq.podcini.storage.database.RealmDB.unsubscribeEpisode
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringShort
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.downloadStates
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.compose.ChaptersDialog
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeClips
import ac.mdiq.podcini.ui.compose.EpisodeMarks
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.compose.IgnoreEpisodesDialog
import ac.mdiq.podcini.ui.compose.LargeTextEditingDialog
import ac.mdiq.podcini.ui.compose.PlayStateDialog
import ac.mdiq.podcini.ui.compose.RelatedEpisodesDialog
import ac.mdiq.podcini.ui.compose.FutureStateDialog
import ac.mdiq.podcini.ui.compose.ShareDialog
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.ShownotesWebView
import ac.mdiq.podcini.ui.utils.episodeOnDisplay
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import ac.mdiq.podcini.util.MiscFormatter.fullDateTimeString
import android.content.Context
import android.content.ContextWrapper
import android.text.format.Formatter.formatShortFileSize
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class EpisodeInfoVM(val context: Context, val lcScope: CoroutineScope) {
    internal val actMain: MainActivity? = generateSequence(context) { if (it is ContextWrapper) it.baseContext else null }.filterIsInstance<MainActivity>().firstOrNull()

    internal lateinit var shownotesCleaner: ShownotesCleaner

    internal var playerLocal: ExoPlayer? = null

    internal var itemLoaded = false
    internal var episode by mutableStateOf<Episode?>(null)    // managed
//    internal var episodeMonitor: Job? = null

    internal var txtvPodcast by mutableStateOf("")
    internal var txtvTitle by mutableStateOf("")
    internal var txtvPublished by mutableStateOf("")
    internal var txtvSize by mutableStateOf("")
    internal var txtvDuration by mutableStateOf("")
    internal var hasMedia by mutableStateOf(true)
    var rating by mutableIntStateOf(episode?.rating ?: Rating.UNRATED.code)
    internal var inQueue by mutableStateOf(false)
    var isPlayed by mutableIntStateOf(episode?.playState ?: EpisodeState.UNSPECIFIED.code)
    var hasRelations by mutableStateOf(false)

    var showShareDialog by mutableStateOf(false)

    internal var webviewData by mutableStateOf("")
    internal var showHomeScreen by mutableStateOf(false)
    internal var actionButton by mutableStateOf<EpisodeActionButton?>(null)

    init {
        episode = episodeOnDisplay
        hasRelations = !episode?.related.isNullOrEmpty()
        inQueue = if (episode == null) false else (episode!!.feed?.queue ?: curQueue).contains(episode!!)
        actionButton = EpisodeActionButton(episode!!)
    }

    private var eventSink: Job?     = null
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
                    is FlowEvent.QueueEvent -> onQueueEvent(event)
                    else -> {}
                }
            }
        }
    }

    internal fun monitor() {
        if (episode != null) subscribeEpisode(episode!!, MonitorEntity(TAG,
            onChanges = { e, fields ->
                Logd(TAG, "monitor: ${e.title}")
                withContext(Dispatchers.Main) {
                    Logd(TAG, "monitor: ${fields.joinToString()}")
                    if (e.id != episode?.id) return@withContext
                    var isChanged = false
                    for (f in fields) {
                        if (f in listOf("startPosition", "timeSpent", "playedDurationWhenStarted", "timeSpentOnStart", "position", "startTime", "lastPlayedTime")) continue
                        isChanged = true
                    }
                    Logd(TAG, "monitor: isChanged: $isChanged")
                    Logd(TAG, "monitor: hasRelations0: $hasRelations ${episode?.related?.size}")
                    if (isChanged) {
                        episode = e
                        hasRelations = !episode?.related.isNullOrEmpty()
                        episodeOnDisplay = e
                        rating = e.rating
                        isPlayed = e.playState
                    }
                    Logd(TAG, "monitor: hasRelations: $hasRelations ${episode?.related?.size}")
                }
            }))
    }

    internal fun updateAppearance() {
        if (episode == null) return
        if (episode!!.feed != null)
            txtvPodcast = if (episode!!.feed!!.isSynthetic() && episode!!.origFeedTitle != null) episode!!.origFeedTitle!! else episode!!.feed!!.title ?: ""
        
        txtvTitle = episode!!.title ?:""
        if (episode?.pubDate != null) txtvPublished = formatDateTimeFlex(Date(episode!!.pubDate))

        val media = episode
        when {
            media == null -> txtvSize = ""
            media.size > 0 -> txtvSize = formatShortFileSize(context, media.size)
            isImageDownloadAllowed && !media.isSizeSetUnknown() -> {
                txtvSize = "{faw_spinner}"
                lcScope.launch {
                    val sizeValue = if (episode?.feed?.prefStreamOverDownload == false) episode?.fetchMediaSize() ?: 0L else 0L
                    txtvSize = if (sizeValue <= 0) "" else formatShortFileSize(context, sizeValue)
                }
            }
            else -> txtvSize = ""
        }
        updateButtons()
    }

    internal fun setButton() {
        actionButton?.type = when {
            InTheatre.isCurrentlyPlaying(episode) -> ButtonTypes.PAUSE
            episode?.feed != null && episode!!.feed!!.isLocalFeed -> ButtonTypes.PLAYLOCAL
            episode?.downloaded == true -> ButtonTypes.PLAY
            else -> ButtonTypes.STREAM
        }
    }

    private fun updateButtons() {
//        val dls = DownloadServiceInterface.impl
        if (episode?.downloadUrl.isNullOrBlank()) {
            hasMedia = false
            return
        }
        val media = episode!!
        hasMedia = true
        if (media.duration > 0) txtvDuration = DurationConverter.getDurationStringLong(media.duration)
        setButton()
    }

    internal fun openPodcast() {
        if (episode?.feedId == null) return
        feedOnDisplay = episode?.feed ?: Feed()
        feedScreenMode = FeedScreenMode.List
        mainNavController.navigate(Screens.FeedDetails.name)
    }

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        if (episode == null) return
        var i = 0
        val size: Int = event.episodes.size
        while (i < size) {
            val item_ = event.episodes[i]
            if (item_.id == episode?.id) {
                inQueue = (episode!!.feed?.queue ?: curQueue).contains(episode!!)
                break
            }
            i++
        }
    }

    private var loadItemsRunning = false
    internal fun load() {
        Logd(TAG, "load() called")
        if (!loadItemsRunning) {
            loadItemsRunning = true
            lcScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (episode != null && !episode!!.isRemote.value) episode = realm.query(Episode::class).query("id == $0", episode!!.id).first().find()
                        Logd(TAG, "episode feedId: ${episode?.feedId} ${episode?.feed?.title}")
                        if (episode != null && webviewData.isBlank()) {
                            val duration = episode!!.duration
                            Logd(TAG, "description: ${episode?.description}")
                            val result = gearbox.buildWebviewData(episode!!, shownotesCleaner)
                            webviewData = result?.second ?: shownotesCleaner.processShownotes(episode!!.description ?: "", duration)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Logd(TAG, "chapters: ${episode?.chapters?.size}")
                        Logd(TAG, "files: [${episode?.feed?.fileUrl}] [${episode?.fileUrl}]")
//                        Logd(TAG, "webviewData: [${episode!!.webviewData}]")
                        if (episode != null) {
                            rating = episode!!.rating
                            inQueue = (episode!!.feed?.queue ?: curQueue).contains(episode!!)
                            isPlayed = episode!!.playState
                        }
                        updateAppearance()
                        itemLoaded = true
                    }
                } catch (e: Throwable) { Logs(TAG, e)
                } finally { loadItemsRunning = false }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun EpisodeInfoScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember(episodeOnDisplay.id) { EpisodeInfoVM(context, scope) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    vm.shownotesCleaner = ShownotesCleaner(context)
                    vm.updateAppearance()
                    vm.load()
                    if (!vm.episode?.clips.isNullOrEmpty()) vm.playerLocal = ExoPlayer.Builder(context).build()
                }
                Lifecycle.Event.ON_START -> {
                    vm.procFlowEvents()
                    vm.monitor()
                }
                Lifecycle.Event.ON_RESUME -> if (vm.itemLoaded) vm.updateAppearance()
                Lifecycle.Event.ON_STOP -> {
                    vm.cancelFlowEvents()
                    if (vm.episode != null) unsubscribeEpisode(vm.episode!!, TAG)
                }
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (vm.episode != null) unsubscribeEpisode(vm.episode!!, TAG)
            vm.episode = null
            vm.playerLocal?.release()
            vm.playerLocal = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface

    var offerStreaming by remember { mutableStateOf(false) }
    @Composable
    fun OnDemandConfigDialog(onDismiss: () -> Unit) {
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { },
            text = { Text(stringResource(if (offerStreaming) R.string.on_demand_config_stream_text else R.string.on_demand_config_download_text)) },
            confirmButton = {
                TextButton(onClick = {
                    if (offerStreaming) AppPreferences.prefStreamOverDownload = true
                    if (vm.episode?.feed != null) vm.episode!!.feed = upsertBlk(vm.episode!!.feed!!) { it.prefStreamOverDownload = offerStreaming }
                    // Update all visible lists to reflect new streaming action button
                    //            TODO: need another event type?
//                    EventFlow.postEvent(FlowEvent.EpisodePlayedEvent())
                    vm.load()
                    Logt(TAG, context.getString(R.string.on_demand_config_setting_changed))
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = {
                UsageStatistics.doNotAskAgain(UsageStatistics.ACTION_STREAM)
                onDismiss()
            }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var showOnDemandConfigDialog by remember { mutableStateOf(false) }
    if (showOnDemandConfigDialog) OnDemandConfigDialog { showOnDemandConfigDialog = false }

    var showEditComment by remember { mutableStateOf(false) }
    val localTime = remember { System.currentTimeMillis() }
    var editCommentText by remember { mutableStateOf(TextFieldValue( vm.episode?.comment ?: "") ) }
    var commentTextState by remember { mutableStateOf(TextFieldValue(vm.episode?.comment?:"")) }
    if (showEditComment) LargeTextEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = { showEditComment = false},
        onSave = {
            commentTextState = editCommentText
            if (vm.episode != null) upsertBlk(vm.episode!!) {
                Logd(TAG, "onSave editCommentText [${editCommentText.text}]")
                it.comment = editCommentText.text
                it.commentTime = localTime
            }
        })

    var showChooseRatingDialog by remember { mutableStateOf(false) }
    if (showChooseRatingDialog) ChooseRatingDialog(listOf(EpisodeVM(vm.episode!!, TAG))) { showChooseRatingDialog = false }

    var showChaptersDialog by remember { mutableStateOf(false) }
    if (showChaptersDialog && vm.episode != null) ChaptersDialog(media = vm.episode!!, onDismissRequest = {showChaptersDialog = false})

    var showIgnoreDialog by remember { mutableStateOf(false) }
    var futureState by remember { mutableStateOf(EpisodeState.UNSPECIFIED) }
    var showPlayStateDialog by remember { mutableStateOf(false) }
    if (showPlayStateDialog) PlayStateDialog(listOf(EpisodeVM(vm.episode!!, TAG)), onDismissRequest = { showPlayStateDialog = false }, { futureState = it },{ showIgnoreDialog = true })

    if (futureState in listOf(EpisodeState.AGAIN, EpisodeState.LATER)) FutureStateDialog(listOf(EpisodeVM(vm.episode!!, TAG)), futureState, onDismissRequest = { futureState = EpisodeState.UNSPECIFIED })
    if (showIgnoreDialog) IgnoreEpisodesDialog(listOf(EpisodeVM(vm.episode!!, TAG)), onDismissRequest = { showIgnoreDialog = false })

    if (vm.showShareDialog && vm.episode != null && vm.actMain != null) ShareDialog(vm.episode!!, vm.actMain) { vm.showShareDialog = false }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        val buttonColor = Color(0xDDFFD700)
        Box {
            TopAppBar(title = { Text("") }, navigationIcon = { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack() }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } }, actions = {
                IconButton(onClick = { showPlayStateDialog = true }) { Icon(imageVector = ImageVector.vectorResource(EpisodeState.fromCode(vm.isPlayed).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "isPlayed", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)) }
                if (vm.episode != null) {
                    if (!vm.inQueue) IconButton(onClick = { runOnIOScope { addToQueueSync(vm.episode!!) } }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_play), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "inQueue", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)) }
                    else IconButton(onClick = { runOnIOScope { removeFromQueueSync(vm.episode!!.feed?.queue ?: curQueue, vm.episode!!) } }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_remove), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "inQueue", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)) }
                }
                IconButton(onClick = { showChooseRatingDialog = true }) { Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(vm.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)) }
                if (!vm.episode?.link.isNullOrEmpty()) IconButton(onClick = {
                    vm.showHomeScreen = true
                    episodeOnDisplay = vm.episode!!
                    mainNavController.navigate(Screens.EpisodeText.name)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.outline_article_shortcut_24), contentDescription = "home") }
                IconButton(onClick = {
                    val url = vm.episode?.getLinkWithFallback()
                    if (url != null) IntentUtils.openInBrowser(context, url)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "web") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                    if (vm.episode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                        val notes = vm.episode!!.description
                        if (!notes.isNullOrEmpty()) {
                            val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            val context = context
                            val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                            context.startActivity(intent)
                        }
                        expanded = false
                    })
                    if (vm.episode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                        vm.showShareDialog = true
                        expanded = false
                    })
                }
            })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        val buttonColor = MaterialTheme.colorScheme.tertiary
        var showAltActionsDialog by remember { mutableStateOf(false) }
        if (showAltActionsDialog) vm.actionButton?.AltActionsDialog(context, onDismiss = { showAltActionsDialog = false })
        LaunchedEffect(key1 = status) { vm.setButton() }
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SelectionContainer { Text(vm.txtvPodcast, color = textColor, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { vm.openPodcast() }) }
            }
            Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val imgLoc = vm.episode?.imageLocation()
                AsyncImage(model = imgLoc, contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher), modifier = Modifier.width(80.dp).height(80.dp).clickable(onClick = { vm.openPodcast() }))
                Box(Modifier.weight(1f).padding(start = 10.dp).height(80.dp)) {
                    Column {
                        SelectionContainer { Text(vm.txtvTitle, color = textColor, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        Text("${vm.txtvPublished} · ${vm.txtvDuration} · ${vm.txtvSize}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (vm.actionButton != null && vm.episode != null) {
                        val dlStats = downloadStates[vm.episode!!.downloadUrl]
                        if (dlStats != null) {
                            vm.actionButton!!.processing = dlStats.progress
                            if (dlStats.state == DownloadStatus.State.COMPLETED.ordinal) vm.actionButton!!.type = ButtonTypes.PLAY
                        }
                        Icon(imageVector = ImageVector.vectorResource(vm.actionButton!!.drawable), tint = buttonColor, contentDescription = null, modifier = Modifier.width(28.dp).height(32.dp).align(Alignment.BottomEnd).combinedClickable(
                            onClick = { vm.actionButton?.onClick(context) },
                            onLongClick = { showAltActionsDialog = true }
                        ))
                    }
                }
            }
            if (!vm.hasMedia) Text("noMediaLabel", color = textColor, style = MaterialTheme.typography.bodyMedium)
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                AndroidView(modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        ShownotesWebView(context).apply {
                            setTimecodeSelectedListener { time: Int -> mPlayer?.seekTo(time) }
                            setPageFinishedListener { postDelayed({ }, 50) }    // Restoring the scroll position might not always work
                        }
                    }, update = { it.loadDataWithBaseURL("https://127.0.0.1", vm.webviewData, "text/html", "utf-8", "about:blank") })
                if (!vm.episode?.chapters.isNullOrEmpty()) Text(stringResource(id = R.string.chapters_label), color = textColor, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable(onClick = { showChaptersDialog = true }))
                EpisodeMarks(vm.episode)
                EpisodeClips(vm.episode, vm.playerLocal)
                Text(stringResource(R.string.my_opinion_label) + if (commentTextState.text.isBlank()) " (Add)" else "",
                    color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom,
                    modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable {
                        editCommentText = TextFieldValue((if (vm.episode?.comment.isNullOrBlank()) "" else vm.episode!!.comment + "\n") + fullDateTimeString(localTime) + ":\n")
                        showEditComment = true
                    })
                Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                Text(vm.episode?.link?: "", color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 15.dp).clickable(onClick = {
                    if (!vm.episode?.link.isNullOrBlank()) IntentUtils.openInBrowser(context, vm.episode!!.link!!)
                }))
                Row {
                    Text("Time spent: " + getDurationStringShort(vm.episode?.timeSpent?:0L, true))
                    Spacer(Modifier.width(50.dp))
                    Text("Played duration: " + getDurationStringShort(vm.episode?.playedDuration?.toLong()?:0L, true))
                }
                if (vm.hasRelations) {
                    var showTodayStats by remember { mutableStateOf(false) }
                    if (showTodayStats) RelatedEpisodesDialog(vm.episode!!) { showTodayStats = false }
                    Text(stringResource(R.string.related), color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 10.dp).clickable(onClick = { showTodayStats = true }))
                }
            }
        }
    }
}

private const val TAG: String = "EpisodeInfoScreen"
