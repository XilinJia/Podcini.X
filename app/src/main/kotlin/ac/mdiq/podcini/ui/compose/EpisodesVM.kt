package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.wifiSyncEnabledKey
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowEpisodeDownload
import ac.mdiq.podcini.playback.base.InTheatre.curMediaId
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.playedIds
import ac.mdiq.podcini.playback.base.InTheatre.playerStat
import ac.mdiq.podcini.playback.base.InTheatre.rememberPlayedIds
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.TimeLeftMode
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodesWarnLocalRepeat
import ac.mdiq.podcini.storage.database.Episodes.deleteMediaSync
import ac.mdiq.podcini.storage.database.Episodes.hasAlmostEnded
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Episodes.vmIndexWithId
import ac.mdiq.podcini.storage.database.Feeds.addRemoteToMiscSyndicate
import ac.mdiq.podcini.storage.database.Feeds.allowForAutoDelete
import ac.mdiq.podcini.storage.database.Feeds.shelveToFeed
import ac.mdiq.podcini.storage.database.Queues.addToActiveQueue
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesQuiet
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesSync
import ac.mdiq.podcini.storage.database.Queues.smartRemoveFromQueue
import ac.mdiq.podcini.storage.database.RealmDB.MonitorEntity
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.subscribeEpisode
import ac.mdiq.podcini.storage.database.RealmDB.unsubscribeEpisode
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PLAYING
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.Companion.DEFAULT_INTERVALS
import ac.mdiq.podcini.storage.model.Feed.Companion.INTERVAL_UNITS
import ac.mdiq.podcini.storage.model.Feed.Companion.duetime
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLong
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import ac.mdiq.podcini.storage.utils.EpisodeFilter.EpisodesFilterGroup
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoAction
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.downloadStates
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.screens.FeedScreenMode
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.ui.utils.episodeOnDisplay
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import ac.mdiq.podcini.util.MiscFormatter.formatLargeInteger
import ac.mdiq.podcini.util.MiscFormatter.fullDateTimeString
import ac.mdiq.podcini.util.MiscFormatter.localDateTimeString
import ac.mdiq.podcini.util.MiscFormatter.stripDateTimeLines
import android.content.Context
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

const val VMS_CHUNK_SIZE = 50
private const val loadThreshold = (VMS_CHUNK_SIZE * 0.8).toInt()
private const val TAG = "EpisodesVM"

@androidx.annotation.OptIn(UnstableApi::class)
fun buildListInfo(episodes: List<Episode>): String {
    var infoText = String.format(Locale.getDefault(), "%d", episodes.size)
    if (episodes.isNotEmpty()) {
        val useSpeed = getPref(AppPrefs.prefShowTimeLeft, 0) == TimeLeftMode.TimeLeftOnSpeed.ordinal
        var timeLeft: Long = 0
        for (item in episodes) {
            var playbackSpeed = 1f
            if (useSpeed) {
                playbackSpeed = getCurrentPlaybackSpeed(item)
                if (playbackSpeed <= 0) playbackSpeed = 1f
            }
            val itemTimeLeft: Long = (item.duration - item.position).toLong()
            timeLeft = (timeLeft + itemTimeLeft / playbackSpeed).toLong()
        }
        infoText += if (useSpeed) " * "  else " • "
        infoText += DurationConverter.getDurationStringShort(timeLeft, true)
    }
    return infoText
}

var showSwipeActionsDialog by mutableStateOf(false)

@Composable
fun InforBar(text: State<String>, swipeActions: SwipeActions) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val leftAction = remember { mutableStateOf<SwipeAction>(NoAction()) }
    val rightAction = remember { mutableStateOf<SwipeAction>(NoAction()) }
    fun refreshSwipeTelltale() {
        leftAction.value = swipeActions.actions.left[0]
        rightAction.value = swipeActions.actions.right[0]
    }
    refreshSwipeTelltale()
    Logd("InforBar", "textState: ${text.value}")
    Row {
        Icon(imageVector = ImageVector.vectorResource(leftAction.value.iconRes), tint = buttonColor, contentDescription = "left_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {  showSwipeActionsDialog = true }))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "left_arrow", modifier = Modifier.width(24.dp).height(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        Text(text.value, color = textColor, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(24.dp).height(24.dp))
        Icon(imageVector = ImageVector.vectorResource(rightAction.value.iconRes), tint = buttonColor, contentDescription = "right_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {  showSwipeActionsDialog = true }))
    }
}

@Stable
class EpisodeVM(var episode: Episode, val tag: String) {
    var positionState by mutableIntStateOf(episode.position)
    var durationState by mutableIntStateOf(episode.duration)
    var playedState by mutableIntStateOf(episode.playState)
    var hasComment by mutableStateOf(episode.comment.isNotBlank())
    var ratingState by mutableIntStateOf(episode.rating)
    var inProgressState by mutableStateOf(episode.isInProgress)
    var downloadState by mutableIntStateOf(if (episode.downloaded) DownloadStatus.State.COMPLETED.ordinal else DownloadStatus.State.UNKNOWN.ordinal)
    var viewCount by mutableIntStateOf(episode.viewCount)
    var likeCount by mutableIntStateOf(episode.likeCount)
    var actionButton by mutableStateOf(EpisodeActionButton(episode))
    var showAltActionsDialog by mutableStateOf(false)
    var dlPercent by mutableIntStateOf(0)
    var isSelected by mutableStateOf(false)
    var prog by mutableFloatStateOf(0f)

    suspend fun updateVMFromDB() {
        val e = realm.query(Episode::class, "id == ${episode.id}").first().find() ?: return
        updateVM(e, arrayOf())
    }

    suspend fun updateVM(e: Episode,  fa: Array<String>) {
        Logd(TAG, "updateVM onChange ${episode.title} ")
        Logd(TAG, "updateVM onChange ${e.title} ")
        if (episode.id == e.id) {
            episode = e
            withContext(Dispatchers.Main) {
                Logd(TAG, "updateVM fields: ${fa.joinToString()} ${e.position} ${e.downloaded}")
                playedState = e.playState
                ratingState = e.rating
                downloadState = if (e.downloaded) DownloadStatus.State.COMPLETED.ordinal else DownloadStatus.State.UNKNOWN.ordinal
                positionState = e.position
                durationState = e.duration
                inProgressState = e.isInProgress
                if ("playState" in fa || "downloaded" in fa) actionButton.update(e)
            }
        }
    }
}

@Composable
fun ChooseRatingDialog(selected: List<EpisodeVM>, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (rating in Rating.entries.reversed()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)
                        .clickable {
                            runOnIOScope {
                                for (vm in selected) {
                                    upsert(vm.episode) { it.rating = rating.code }
                                    vm.updateVMFromDB()
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
}

@Composable
fun AddCommentDialog(selected: List<EpisodeVM>, onDismissRequest: () -> Unit) {
    var editCommentText by remember { mutableStateOf(TextFieldValue("") ) }
    LargeTextEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = { onDismissRequest() },
        onSave = {
            runOnIOScope {
                val localTime = System.currentTimeMillis()
                for (vm in selected) {
                    upsert(vm.episode) {
                        Logd("AddCommentDialog", "onSave editCommentText [${editCommentText.text}]")
                        val comment = fullDateTimeString(localTime) + ":\n" + editCommentText.text
                        it.comment += if (it.comment.isBlank()) comment else "\n" + comment
                        it.commentTime = localTime
                    }
                    vm.updateVMFromDB()
                }
            }
        })
}

@Composable
fun PlayStateDialog(selected: List<EpisodeVM>, onDismissRequest: () -> Unit, futureCB: (EpisodeState)->Unit, ignoreCB: ()->Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (state in EpisodeState.entries.reversed()) {
                    if (state.userSet) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp).clickable {
                            when (state) {
                                EpisodeState.IGNORED -> ignoreCB()
                                EpisodeState.AGAIN, EpisodeState.LATER -> futureCB(state)
                                else -> runOnIOScope {
                                    for (vm in selected) {
                                        val hasAlmostEnded = hasAlmostEnded(vm.episode)
                                        var item_ = setPlayStateSync(state, vm.episode, hasAlmostEnded, false)
                                        when (state) {
                                            EpisodeState.UNPLAYED -> {
                                                if (isProviderConnected && item_.feed?.isLocalFeed != true) {
                                                    val actionNew: EpisodeAction = EpisodeAction.Builder(item_, EpisodeAction.NEW).currentTimestamp().build()
                                                    SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, actionNew)
                                                }
                                            }
                                            EpisodeState.PLAYED -> {
                                                if (hasAlmostEnded) item_ = upsertBlk(item_) { it.playbackCompletionDate = Date() }
                                                val shouldAutoDelete = if (item_.feed == null) false else allowForAutoDelete(item_.feed!!) //                                            item = item_
                                                if (hasAlmostEnded && shouldAutoDelete) {
                                                    item_ = deleteMediaSync(context, item_)
                                                    if (getPref(AppPrefs.prefDeleteRemovesFromQueue, true)) removeFromAllQueuesSync(item_)
                                                } else if (getPref(AppPrefs.prefRemoveFromQueueMarkedPlayed, true)) removeFromAllQueuesSync(item_)
                                                if (item_.feed?.isLocalFeed != true && (isProviderConnected || wifiSyncEnabledKey)) { // not all items have media, Gpodder only cares about those that do
                                                    if (isProviderConnected) {
                                                        val actionPlay: EpisodeAction = EpisodeAction.Builder(item_, EpisodeAction.PLAY).currentTimestamp().started(item_.duration / 1000).position(item_.duration / 1000).total(item_.duration / 1000).build()
                                                        SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, actionPlay)
                                                    }
                                                }
                                            }
                                            EpisodeState.QUEUE -> if (item_.feed?.queue != null) addToQueueSync(vm.episode, vm.episode.feed?.queue)
                                            else -> {}
                                        }
                                        vm.updateVMFromDB()
                                    }
                                }
                            }
                            onDismissRequest()
                        }) {
                            Icon(imageVector = ImageVector.vectorResource(id = state.res), "")
                            Text(state.name, Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PutToQueueDialog(selected: List<EpisodeVM>, onDismissRequest: () -> Unit) {
    val queues = realm.query(PlayQueue::class).find()
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                var removeChecked by remember { mutableStateOf(false) }
                var toQueue by remember { mutableStateOf(curQueue) }
                for (q in queues) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = toQueue == q, onClick = { toQueue = q })
                        Text(q.name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = removeChecked, onCheckedChange = { removeChecked = it })
                    Text(text = stringResource(R.string.remove_from_other_queues), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 10.dp))
                }
                Row {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        runOnIOScope {
                            if (removeChecked) {
                                val toRemove = mutableSetOf<Long>()
                                val toRemoveCur = mutableListOf<Episode>()
                                selected.forEach { vm -> if (curQueue.contains(vm.episode)) toRemoveCur.add(vm.episode) }
                                selected.forEach { vm ->
                                    for (q in queues) {
                                        if (q.contains(vm.episode)) {
                                            toRemove.add(vm.episode.id)
                                            break
                                        }
                                    }
                                }
                                if (toRemove.isNotEmpty()) removeFromAllQueuesQuiet(toRemove.toList())
                                if (toRemoveCur.isNotEmpty()) EventFlow.postEvent(FlowEvent.QueueEvent.removed(toRemoveCur))
                            }
                            selected.forEach { vm -> addToQueueSync(vm.episode, toQueue) }
                        }
                        onDismissRequest()
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }
}

@Composable
fun ShelveDialog(selected: List<EpisodeVM>, onDismissRequest: () -> Unit) {
    val synthetics = realm.query(Feed::class).query("id >= 100 && id <= 1000").find()
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                var removeChecked by remember { mutableStateOf(false) }
                var toFeed by remember { mutableStateOf<Feed?>(null) }
                if (synthetics.isNotEmpty()) {
                    for (f in synthetics) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = toFeed == f, onClick = { toFeed = f })
                            Text(f.title ?: "No title")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = removeChecked, onCheckedChange = { removeChecked = it })
                        Text(text = stringResource(R.string.remove_from_current_feed), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 10.dp))
                    }
                } else Text(text = stringResource(R.string.create_synthetic_first_note))
                if (toFeed != null) Row {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        runOnIOScope { shelveToFeed(selected.map { it.episode }, toFeed!!, removeChecked) }
                        onDismissRequest()
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }
}

@Composable
fun EraseEpisodesDialog(selected: List<EpisodeVM>, feed: Feed?, onDismissRequest: () -> Unit) {
    val message = stringResource(R.string.erase_episodes_confirmation_msg)
    val textColor = MaterialTheme.colorScheme.onSurface
    var textState by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            if (feed == null || !feed.isSynthetic()) Text(stringResource(R.string.not_erase_message), modifier = Modifier.padding(10.dp))
            else Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message + ": ${selected.size}")
                Text(stringResource(R.string.reason_to_delete_msg))
                BasicTextField(value = textState, onValueChange = { textState = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor), modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
                Button(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            for (vm in selected) {
                                val sLog = SubscriptionLog(vm.episode.id, vm.episode.title?:"", vm.episode.downloadUrl?:"", vm.episode.link?:"", SubscriptionLog.Type.Media.name)
                                upsert(sLog) {
                                    it.rating = vm.episode.rating
                                    it.comment = if (vm.episode.comment.isBlank()) "" else (vm.episode.comment + "\n")
                                    it.comment += localDateTimeString() + "\nReason to remove:\n" + textState.text
                                    it.cancelDate = Date().time
                                }
                            }
                            realm.write {
                                for (vm in selected) {
                                    val url = vm.episode.fileUrl
                                    when {
                                        url != null && url.startsWith("content://") -> DocumentFile.fromSingleUri(context, url.toUri())?.delete()
                                        url != null -> {
                                            val path = url.toUri().path
                                            if (path != null) File(path).delete()
                                        }
                                    }
                                    findLatest(feed)?.episodes?.remove(vm.episode)
                                    findLatest(vm.episode)?.let { delete(it) }
                                }
                            }
                            EventFlow.postStickyEvent(FlowEvent.FeedUpdatingEvent(false))
                        } catch (e: Throwable) { Logs("EraseEpisodesDialog", e) }
                    }
                    onDismissRequest()
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }
}

@Composable
fun IgnoreEpisodesDialog(selected: List<EpisodeVM>, onDismissRequest: () -> Unit) {
    val message = stringResource(R.string.ignore_episodes_confirmation_msg)
    val textColor = MaterialTheme.colorScheme.onSurface
    var textState by remember { mutableStateOf(TextFieldValue("")) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message + ": ${selected.size}")
                Text(stringResource(R.string.reason_to_delete_msg))
                BasicTextField(value = textState, onValueChange = { textState = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor), modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
                Button(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            for (vm in selected) {
                                val hasAlmostEnded = hasAlmostEnded(vm.episode)
                                val item_ = setPlayStateSync(EpisodeState.IGNORED, vm.episode, hasAlmostEnded, false)
                                Logd("IgnoreEpisodesDialog", "item_: ${item_.title} ${item_.playState}")
                                upsert(item_) {
                                    it.comment = if (item_.comment.isBlank()) "" else (item_.comment + "\n")
                                    it.comment += localDateTimeString() + "\nReason to ignore:\n" + textState.text
                                }
                                vm.updateVMFromDB()
                            }
                        } catch (e: Throwable) { Logs("IgnoreEpisodesDialog", e) }
                    }
                    onDismissRequest()
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }
}

@Composable
fun FutureStateDialog(episodes: List<EpisodeVM>, state: EpisodeState, onDismissRequest: () -> Unit) {
    val message = stringResource(R.string.repeat_episodes_msg)
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message + ": ${episodes.size}")
                var intervals = remember { if (episodes.size == 1) episodes[0].episode.feed?.repeatIntervals?.toList() else null }
                if (intervals.isNullOrEmpty()) intervals = DEFAULT_INTERVALS
                val units = INTERVAL_UNITS.map { stringResource(it) }
                var sel by remember { mutableIntStateOf(2) }
                for (i in intervals.indices) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i==sel, onClick = { sel = i })
                        Text(intervals[i].toString() + " " + units[i])
                    }
                }
                Button(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            var dueTime = duetime(intervals[sel], sel)
                            for (vm in episodes) {
                                val hasAlmostEnded = hasAlmostEnded(vm.episode)
                                val item_ = setPlayStateSync(state, vm.episode, hasAlmostEnded, false)
                                Logd("AgainEpisodesDialog", "item_: ${item_.title} ${item_.playState} ${Date(dueTime)}")
                                upsert(item_) { it.repeatTime = dueTime }
                                vm.updateVMFromDB()
                            }
                        } catch (e: Throwable) { Logs("AgainEpisodesDialog", e) }
                    }
                    onDismissRequest()
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }
}

enum class LayoutMode {
    Normal, WideImage, FeedTitle
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun EpisodeLazyColumn(activity: Context, vms: SnapshotStateList<EpisodeVM>, feed: Feed? = null, layoutMode: Int = LayoutMode.Normal.ordinal,
                      showCoverImage: Boolean = true, forceFeedImage: Boolean = false,
                      showActionButtons: Boolean = true, showComment: Boolean = false,
                      buildMoreItems: (()->Unit) = {},
                      isDraggable: Boolean = false, dragCB: ((Int, Int)->Unit)? = null,
                      swipeActions: SwipeActions? = null,
                      refreshCB: (()->Unit)? = null,
                      actionButton_: ((Episode)->EpisodeActionButton)? = null, actionButtonCB: ((Episode, String)->Unit)? = null) {
    var selectMode by remember { mutableStateOf(false) }
    var selectedSize by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<EpisodeVM>() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var longPressIndex by remember { mutableIntStateOf(-1) }
//    val dls = remember { DownloadServiceInterface.impl }
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary

    fun multiSelectCB(index: Int, aboveOrBelow: Int): List<EpisodeVM> {
        return when (aboveOrBelow) {
            0 -> vms
            -1 -> if (index < vms.size) vms.subList(0, index+1) else vms
            1 -> if (index < vms.size) vms.subList(index, vms.size) else vms
            else -> listOf()
        }
    }

    var leftSwipeCB: ((EpisodeVM)->Unit)? = null
    var rightSwipeCB: ((EpisodeVM)->Unit)? = null
    val leftActionState = remember { mutableStateOf<SwipeAction>(NoAction()) }
    val rightActionState = remember { mutableStateOf<SwipeAction>(NoAction()) }
    if (swipeActions != null) {
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
        leftSwipeCB = {
            if (leftActionState.value is NoAction) showSwipeActionsDialog = true
            else leftActionState.value.performAction(it)
        }
        rightSwipeCB = {
            if (rightActionState.value is NoAction) showSwipeActionsDialog = true
            else rightActionState.value.performAction(it)
        }
        if (showSwipeActionsDialog) SwipeActionsSettingDialog(swipeActions, onDismissRequest = { showSwipeActionsDialog = false }) { actions ->
            swipeActions.actions = actions
            leftActionState.value = swipeActions.actions.left[0]
            rightActionState.value = swipeActions.actions.right[0]
        }
    }

    val showConfirmYoutubeDialog = remember { mutableStateOf(false) }
    val ytUrls = remember { mutableListOf<String>() }
    gearbox.ConfirmAddEpisode(ytUrls, showConfirmYoutubeDialog.value, onDismissRequest = { showConfirmYoutubeDialog.value = false })

    var showChooseRatingDialog by remember { mutableStateOf(false) }
    if (showChooseRatingDialog) ChooseRatingDialog(selected) { showChooseRatingDialog = false }

    var showAddCommentDialog by remember { mutableStateOf(false) }
    if (showAddCommentDialog) AddCommentDialog(selected) { showAddCommentDialog = false }

    var showIgnoreDialog by remember { mutableStateOf(false) }
    var futureState by remember { mutableStateOf(EpisodeState.UNSPECIFIED) }
    var showPlayStateDialog by remember { mutableStateOf(false) }
    if (showPlayStateDialog) PlayStateDialog(selected, onDismissRequest = { showPlayStateDialog = false }, { futureState = it },{ showIgnoreDialog = true })

    var showPutToQueueDialog by remember { mutableStateOf(false) }
    if (showPutToQueueDialog) PutToQueueDialog(selected) { showPutToQueueDialog = false }

    var showShelveDialog by remember { mutableStateOf(false) }
    if (showShelveDialog) ShelveDialog(selected) { showShelveDialog = false }

    var showEraseDialog by remember { mutableStateOf(false) }
    if (showEraseDialog && feed != null) EraseEpisodesDialog(selected, feed, onDismissRequest = { showEraseDialog = false })

    if (showIgnoreDialog) IgnoreEpisodesDialog(selected, onDismissRequest = { showIgnoreDialog = false })
    if (futureState in listOf(EpisodeState.AGAIN, EpisodeState.LATER)) FutureStateDialog(selected, futureState, onDismissRequest = { futureState = EpisodeState.UNSPECIFIED })

    @Composable
    fun EpisodeSpeedDial(modifier: Modifier = Modifier) {
        var isExpanded by remember { mutableStateOf(false) }
        fun onSelected() {
            isExpanded = false
            selectMode = false
        }
        val options = mutableListOf<@Composable () -> Unit>(
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                showPlayStateDialog = true
                onSelected()
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mark_played), "Set played state")
                Text(stringResource(id = R.string.set_play_state_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                showChooseRatingDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), "Set rating")
                Text(stringResource(id = R.string.set_rating_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                showAddCommentDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_comment_24), "Add comment")
                Text(stringResource(id = R.string.add_opinion_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                fun download(now: Boolean) {
                    for (vm in selected) if (vm.episode.feed != null && !vm.episode.feed!!.isLocalFeed) DownloadServiceInterface.impl?.downloadNow(activity, vm.episode, now)
                }
                if (mobileAllowEpisodeDownload || !isNetworkRestricted) download(true)
                else {
                    commonConfirm = CommonConfirmAttrib(
                        title = context.getString(R.string.confirm_mobile_download_dialog_title),
                        message = context.getString(if (isNetworkRestricted && NetworkUtils.isVpnOverWifi) R.string.confirm_mobile_download_dialog_message_vpn else R.string.confirm_mobile_download_dialog_message),
                        confirmRes = R.string.confirm_mobile_download_dialog_download_later,
                        cancelRes = R.string.cancel_label,
                        neutralRes = R.string.confirm_mobile_download_dialog_allow_this_time,
                        onConfirm = { download(false) },
                        onNeutral = { download(true) })
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_download), "Download")
                Text(stringResource(id = R.string.download_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                runOnIOScope { selected.forEach { addToQueueSync(it.episode) } }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to associated or active queue")
                Text(stringResource(id = R.string.add_to_associated_queue)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                addToActiveQueue(*(selected.map { it.episode }).toTypedArray())
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to active queue")
                Text(stringResource(id = R.string.add_to_queue_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                showPutToQueueDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to queue...")
                Text(stringResource(id = R.string.put_in_queue_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                runOnIOScope { for (vm in selected) smartRemoveFromQueue(vm.episode) }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_remove), "Remove from active queue")
                Text(stringResource(id = R.string.remove_from_queue_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                runOnIOScope {
                    realm.write {
                        val selected_ = query(Episode::class, "id IN $0", selected.map { it.episode.id }.toList()).find()
                        for (e in selected_) {
                            for (e1 in selected_) {
                                if (e.id == e1.id) continue
                                e.related.add(e1)
                            }
                        }
                    }
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "Set related")
                Text(stringResource(id = R.string.set_related)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                runOnIOScope {
                    for (vm in selected) {
                        if (!vm.episode.downloaded && vm.episode.feed?.isLocalFeed != true) continue
                        val almostEnded = hasAlmostEnded(vm.episode)
                        if (almostEnded && vm.episode.playState < EpisodeState.PLAYED.code) vm.episode = setPlayStateSync(state = EpisodeState.PLAYED, episode = vm.episode, resetMediaPosition = true, removeFromQueue = false)
                        if (almostEnded) upsert(vm.episode) { it.playbackCompletionDate = Date() }
                    }
                    deleteEpisodesWarnLocalRepeat(activity, selected.map { it.episode })
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "Delete media")
                Text(stringResource(id = R.string.delete_episode_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                onSelected()
                showShelveDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_shelves_24), "Shelve")
                Text(stringResource(id = R.string.shelve_label)) } },
        )
        if (selected.isNotEmpty() && selected[0].episode.isRemote.value)
            options.add {
                Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    onSelected()
                    CoroutineScope(Dispatchers.IO).launch {
                        ytUrls.clear()
                        for (vm in selected) {
                            val url = URL(vm.episode.downloadUrl ?: "")
                            if (gearbox.isGearUrl(url)) ytUrls.add(vm.episode.downloadUrl!!)
                            else addRemoteToMiscSyndicate(vm.episode)
                        }
                        withContext(Dispatchers.Main) { showConfirmYoutubeDialog.value = ytUrls.isNotEmpty() }
                    }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AddCircle, "Reserve episodes")
                    Text(stringResource(id = R.string.reserve_episodes_label))
                }
            }
        if (feed != null && feed.isSynthetic()) {
            options.add {
                Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    onSelected()
                    showEraseDialog = true
                }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_delete_forever_24), "Erase episodes")
                    Text(stringResource(id = R.string.erase_episodes_label))
                }
            }
        }

        val scrollState = rememberScrollState()
        Column(modifier = modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.Bottom) {
            if (isExpanded) options.forEachIndexed { _, button -> FloatingActionButton(containerColor = MaterialTheme.colorScheme.onTertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).height(40.dp),onClick = {}) { button() } }
            FloatingActionButton(containerColor = MaterialTheme.colorScheme.onTertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiary, onClick = { isExpanded = !isExpanded }) { Icon(Icons.Filled.Edit, "Edit") }
        }
    }

    fun toggleSelected(vm: EpisodeVM) {
        vm.isSelected = !vm.isSelected
        if (vm.isSelected) selected.add(vm)
        else selected.remove(vm)
    }

    val titleMaxLines = if (layoutMode == LayoutMode.Normal.ordinal) if (showComment) 1 else 2 else 3
    @Composable
    fun TitleColumn(vm: EpisodeVM, index: Int, modifier: Modifier) {
        Column(modifier.padding(start = 6.dp, end = 6.dp).combinedClickable(
            onClick = {
                Logd(TAG, "clicked: ${vm.episode.title}")
                if (selectMode) toggleSelected(vm)
                else {
                    episodeOnDisplay = vm.episode
                    mainNavController.navigate(Screens.EpisodeInfo.name)
                }
            },
            onLongClick = {
                selectMode = !selectMode
                vm.isSelected = selectMode
                selected.clear()
                if (selectMode) {
                    selected.add(vms[index])
                    longPressIndex = index
                } else {
                    selectedSize = 0
                    longPressIndex = -1
                }
                Logd(TAG, "long clicked: ${vm.episode.title}")
            })) {
            Text(vm.episode.title ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
            @Composable
            fun Comment() {
                val comment = remember { stripDateTimeLines(vm.episode.comment).replace("\n", "  ") }
                Text(comment, color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            @Composable
            fun StatusRow() {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val episodeStateRes = EpisodeState.fromCode(vm.playedState).res
                    Icon(imageVector = ImageVector.vectorResource(episodeStateRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (vm.playedState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                    val ratingIconRes = Rating.fromCode(vm.ratingState).res
                    if (vm.ratingState != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                    if (vm.hasComment) Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_comment_24), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "comment", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                    if (vm.episode.getMediaType() == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                    val curContext = LocalContext.current
                    val dateSizeText = remember {
                        " · " + formatDateTimeFlex(vm.episode.getPubDate()) + " · " + getDurationStringLong(vm.durationState) +
                                (if (vm.episode.size > 0) " · " + Formatter.formatShortFileSize(curContext, vm.episode.size) else "") +
                                (if (vm.viewCount > 0) " · " + formatLargeInteger(vm.viewCount) else "") +
                                (if (vm.likeCount > 0) " · " + formatLargeInteger(vm.likeCount) else "")
                    }
                    Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when (layoutMode) {
                LayoutMode.Normal.ordinal -> {
                    if (showComment) Comment()
                    else StatusRow()
                }
                LayoutMode.WideImage.ordinal -> {
                    val curContext = LocalContext.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = ImageVector.vectorResource(EpisodeState.fromCode(vm.playedState).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (vm.playedState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                        if (vm.ratingState != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(vm.ratingState).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                        if (vm.episode.getMediaType() == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                        val dateSizeText = remember { " · " + getDurationStringLong(vm.durationState) + (if (vm.episode.size > 0) " · " + Formatter.formatShortFileSize(curContext, vm.episode.size) else "") }
                        Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dateSizeText = remember { formatDateTimeFlex(vm.episode.getPubDate()) }
                        Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (vm.viewCount > 0) {
                            val viewText = remember { " · " + formatLargeInteger(vm.viewCount) }
                            Text(viewText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_people_alt_24), tint = textColor, contentDescription = "people", modifier = Modifier.width(16.dp).height(16.dp))
                        }
                        if (vm.likeCount > 0) {
                            val likeText = remember { " · " + formatLargeInteger(vm.likeCount) }
                            Text(likeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_thumb_up_24), tint = textColor, contentDescription = "likes", modifier = Modifier.width(16.dp).height(16.dp))
                        }
                    }
                }
                LayoutMode.FeedTitle.ordinal -> {
                    Text(vm.episode.feed?.title ?: "", color = textColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (showComment) Comment()
                    else StatusRow()
                }
            }
            when (vm.playedState) {
                 EpisodeState.AGAIN.code, EpisodeState.LATER.code -> {
                    val dueText = remember { if (vm.episode.repeatTime > 0) "D:" + formatDateTimeFlex(vm.episode.dueDate) else "" }
                    Text(dueText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                EpisodeState.SKIPPED.code -> {
                    val playedText = if (vm.episode.lastPlayedTime > 0) "P:" + formatDateTimeFlex(vm.episode.lastPlayedDate) else ""
                    val completionText = if (vm.episode.playbackCompletionTime > 0) " · C:" + formatDateTimeFlex(vm.episode.playbackCompletionDate) else ""
                    val stateSetText = if (vm.episode.playStateSetTime > 0) " · S:" + formatDateTimeFlex(vm.episode.playStateSetDate) else ""
                    val durationText = if (vm.episode.playedDuration > 0) " · " + getDurationStringLong(vm.episode.playedDuration) else ""
                    val dateSizeText = remember { playedText + completionText + stateSetText + durationText }
                    Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                else -> {}
            }
        }
    }

    @Composable
    fun MainRow(vm: EpisodeVM, index: Int, isBeingDragged: Boolean, yOffset: Float, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
        val density = LocalDensity.current
        val imageWidth = if (layoutMode == LayoutMode.WideImage.ordinal) 150.dp else 56.dp
        val imageHeight = if (layoutMode == LayoutMode.WideImage.ordinal) 100.dp else 56.dp
        Row(Modifier.background(if (vm.isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface).offset(y = with(density) { yOffset.toDp() })) {
            if (isDraggable) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.dragview_background, typedValue, true)
                Icon(imageVector = ImageVector.vectorResource(typedValue.resourceId), tint = buttonColor, contentDescription = "drag handle", modifier = Modifier.width(50.dp).align(Alignment.CenterVertically).padding(start = 10.dp, end = 15.dp).draggable(orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> onDrag(delta) }, onDragStarted = { onDragStart() }, onDragStopped = { onDragEnd() } ))
            }
            if (showCoverImage) {
                val imgLoc = remember(vm.episode) { vm.episode.imageLocation(forceFeedImage) }
                AsyncImage(model = ImageRequest.Builder(context).data(imgLoc).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(), contentDescription = "imgvCover",
                    modifier = Modifier.width(imageWidth).height(imageHeight)
                        .clickable(onClick = {
                            Logd(TAG, "icon clicked!")
                            when {
                                selectMode -> toggleSelected(vm)
                                vm.episode.feed != null && vm.episode.feed?.isSynthetic() != true -> {
                                    feedOnDisplay = vm.episode.feed!!
                                    feedScreenMode = FeedScreenMode.Info
                                    mainNavController.navigate(Screens.FeedDetails.name)
                                }
                                else -> {
                                    episodeOnDisplay = vm.episode
                                    mainNavController.navigate(Screens.EpisodeInfo.name)
                                }
                            }
                        }))
            }
            Box(Modifier.weight(1f).wrapContentHeight()) {
                TitleColumn(vm, index, modifier = Modifier.fillMaxWidth())
                if (showActionButtons) {
                    if (actionButton_ == null) {
                        LaunchedEffect(playerStat) {
//                            if (vm.episode.id == curMediaId) {
//                                Logd(TAG, "LaunchedEffect playerStat: $playerStat")
//                                if (playerStat == PLAYER_STATUS_PLAYING) vm.actionButton.type = ButtonTypes.PAUSE
//                                else vm.actionButton.update(vm.episode)
//                            }
                            Logd(TAG, "LaunchedEffect playerStat: $playerStat")
                            if (vm.episode.id == curMediaId) {
                                if (playerStat == PLAYER_STATUS_PLAYING) vm.actionButton.type = ButtonTypes.PAUSE
                            } else if (vm.actionButton.type == ButtonTypes.PAUSE) vm.actionButton.update(vm.episode)
                        }
                    } else LaunchedEffect(Unit) { vm.actionButton = actionButton_(vm.episode) }
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).padding(end = 10.dp).align(Alignment.BottomEnd).pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { vms[index].showAltActionsDialog = true },
                            onTap = {
                                vm.actionButton.onClick(activity)
                                Logd(TAG, "onTap vm.actionButton: ${vm.actionButton.TAG}")
                                actionButtonCB?.invoke(vm.episode, vm.actionButton.TAG)
                            }) }) {
                        val dlStats = downloadStates[vm.episode.downloadUrl]
                        if (dlStats != null) {
//                            Logd(TAG, "${vm.episode.id} dlStats: ${dlStats.progress} ${dlStats.state}")
                            vm.actionButton.processing = dlStats.progress
                            when (dlStats.state) {
                                DownloadStatus.State.COMPLETED.ordinal -> vm.actionButton.type = ButtonTypes.PLAY
                                DownloadStatus.State.INCOMPLETE.ordinal -> vm.actionButton.type = ButtonTypes.DOWNLOAD
                                else -> {}
                            }
                        }
//                        val res by remember { derivedStateOf { vm.actionButton.drawable } }
                        Icon(imageVector = ImageVector.vectorResource(vm.actionButton.drawable), tint = buttonColor, contentDescription = null, modifier = Modifier.size(33.dp))
                        if (vm.actionButton.processing > -1) CircularProgressIndicator(progress = { 0.01f * vm.actionButton.processing }, strokeWidth = 4.dp, color = textColor, modifier = Modifier.size(37.dp).offset(y = 4.dp))
                    }
                    if (vm.showAltActionsDialog) vm.actionButton.AltActionsDialog(activity, onDismiss = { vm.showAltActionsDialog = false })
                }
            }
        }
    }

    val isLoading = remember { mutableStateOf(false) }
    val loadedIndices = remember { mutableSetOf<Int>() }
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
//            .debounce(300)
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= 0 && lastVisibleIndex >= vms.size - loadThreshold && !isLoading.value && !loadedIndices.contains(lastVisibleIndex)) {
                    loadedIndices.add(lastVisibleIndex)
                    isLoading.value = true
                    buildMoreItems()
                    isLoading.value = false
                }
            }
    }

    var refreshing by remember { mutableStateOf(false)}
    PullToRefreshBox(modifier = Modifier.fillMaxSize(), isRefreshing = refreshing, indicator = {}, onRefresh = {
        refreshing = true
        refreshCB?.invoke()
        refreshing = false
    }) {
        fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
            if (fromIndex != toIndex && fromIndex in indices && toIndex in indices) {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
        }
        var curVM by remember { mutableStateOf<EpisodeVM?>(null) }
        var subscribeLock by remember { mutableStateOf(false) }
        fun unsubscribeCurVM() {
            if (curVM != null) {
                unsubscribeEpisode(curVM!!.episode,  curVM!!.tag)
                curVM!!.actionButton.update(curVM!!.episode)
                curVM = null
            }
        }
        fun subscribeCurVM() {
            if (subscribeLock) return
            subscribeLock = true
            if (curVM?.episode?.id != curMediaId) {
                Logd(TAG, "subscribeCurVM curVM?.episode: ${curVM?.episode?.id} curMediaId: $curMediaId")
                unsubscribeCurVM()
                if (curMediaId > 0) {
                    coroutineScope.launch {
                        snapshotFlow { lazyListState.layoutInfo }
                            .filter { it.visibleItemsInfo.isNotEmpty() }
                            .first()
                            .let { it0 ->
                                val visibleItems = it0.visibleItemsInfo.mapNotNull { vms.getOrNull(it.index) }
                                val vm = visibleItems.find { it.episode.id == curMediaId }
                                if (vm != null) {
                                    Logd(TAG, "subscribeCurVM start monitor ${vm.episode.title}")
                                    subscribeEpisode(vm.episode, MonitorEntity(vm.tag, onChanges = { e, fields -> vm.updateVM(e, fields) }))
                                    curVM = vm
                                    if (playerStat == PLAYER_STATUS_PLAYING) curVM!!.actionButton.type = ButtonTypes.PAUSE
                                    else curVM!!.actionButton.update(curVM!!.episode)
                                }
                            }
                    }
                }
            }
            subscribeLock = false
        }

        val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
        LaunchedEffect(curMediaId, vms.size, episodeSortOrder) {
            Logd(TAG, "LaunchedEffect curMediaId vms")
            unsubscribeCurVM()
            subscribeCurVM()
        }

        DisposableEffect(lazyListState) {
            val job = CoroutineScope(Dispatchers.Main.immediate).launch {
                snapshotFlow { lazyListState.isScrollInProgress }
                    .distinctUntilChanged()
                    .collectLatest { inProgress ->
                        if (!inProgress) {
                            delay(100)
                            Logd(TAG, "DisposableEffect lazyListState subscribeCurVM")
                            subscribeCurVM()
                        }
                    }
            }
            onDispose { job.cancel() }
        }
        var forceRecomposeKey by remember { mutableIntStateOf(0) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        coroutineScope.launch {
                            lazyListState.scrollToItem(
                                lazyListState.firstVisibleItemIndex,
                                lazyListState.firstVisibleItemScrollOffset
                            )
            //                        forceRecomposeKey++
                            Logd(TAG, "Screen on, triggered scroll for recomposition")
                        }
                        rememberPlayedIds = false
                        runOnIOScope {
                            if (playedIds.isNotEmpty()) {
                                val playedVMs = mutableListOf<EpisodeVM>()
                                for (id in playedIds) {
                                    val ind = vms.vmIndexWithId(id)
                                    if (ind >= 0) playedVMs.add(vms[ind])
                                }
                                if (playedVMs.isNotEmpty()) {
                                    for (vm in playedVMs) {
                                        vm.updateVMFromDB()
                                        withContext(Dispatchers.Main) { vm.actionButton.update(vm.episode) }
                                    }
                                }
                                playedIds.clear()
                            }
                            if (curVM?.episode?.id == curMediaId) {
                                curVM?.updateVMFromDB()
                                withContext(Dispatchers.Main) {
                                    if (playerStat == PLAYER_STATUS_PLAYING) curVM!!.actionButton.type = ButtonTypes.PAUSE
                                    else curVM!!.actionButton.update(curVM!!.episode)
                                }
                            } else withContext(Dispatchers.Main) { subscribeCurVM() }
                        }
                    }
//                    Lifecycle.Event.ON_RESUME -> {
//                        Logd(TAG, "DisposableEffect lifecycleOwner ${curVM?.episode?.id} $curMediaId")
//                    }
                    Lifecycle.Event.ON_STOP -> {
                        rememberPlayedIds = true
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                Logd(TAG, "DisposableEffect lifecycleOwner onDispose")
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(vms.size) {
            if (vms.isEmpty()) {
                Logd(TAG, "vms is empty, performing cleanup")
                unsubscribeCurVM()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                Logd(TAG, "DisposableEffect onDispose")
                unsubscribeCurVM()
            }
        }

        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vms, key = { _, vm -> vm.episode.id to forceRecomposeKey }) { index, vm ->
                val velocityTracker = remember { VelocityTracker() }
                val offsetX = remember { Animatable(0f) }
                Box(modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { velocityTracker.resetTracking() },
                        onHorizontalDrag = { change, dragAmount ->
//                            Logd(TAG, "onHorizontalDrag $dragAmount")
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            coroutineScope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                val velocity = velocityTracker.calculateVelocity().x
                                if (velocity > 1000f || velocity < -1000f) {
                                    if (velocity > 0) rightSwipeCB?.invoke(vm)
                                    else leftSwipeCB?.invoke(vm)
                                }
                                offsetX.animateTo(targetValue = 0f, animationSpec = tween(500))
                            }
                        }
                    )
                }.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
                    LaunchedEffect(key1 = selectMode, key2 = selectedSize) {
                        vm.isSelected = selectMode && vm in selected
//                        Logd(TAG, "LaunchedEffect $index ${vm.isSelected} ${selected.size}")
                    }
                    Column {
                        var yOffset by remember { mutableFloatStateOf(0f) }
                        var draggedIndex by remember { mutableStateOf<Int?>(null) }
                        MainRow(vm, index,
                            isBeingDragged = draggedIndex == index,
                            yOffset = if (draggedIndex == index) yOffset else 0f,
                            onDragStart = { draggedIndex = index },
                            onDrag = { delta -> yOffset += delta },
                            onDragEnd = {
                                draggedIndex?.let { startIndex ->
                                    val newIndex = (startIndex + (yOffset / rowHeightPx).toInt()).coerceIn(0, vms.lastIndex)
                                    Logd(TAG, "onDragEnd draggedIndex: $draggedIndex newIndex: $newIndex")
                                    if (newIndex != startIndex) {
                                        dragCB?.invoke(startIndex, newIndex)
                                        val item = vms.removeAt(startIndex)
                                        vms.add(newIndex, item)
                                    }
                                }
                                draggedIndex = null
                                yOffset = 0f
                            })
                        if (showActionButtons && (vm.inProgressState || curVM?.episode?.id == vm.episode.id)) {
                            vm.prog = remember(vm.episode.id, vm.positionState) {
                                val pos = vm.positionState
                                val dur = vm.episode.duration
                                if (dur > 0 && pos >= 0 && dur >= pos) 1f * pos / dur else 0f
                            }
                            val durText = getDurationStringLong(vm.episode.duration)
                            val posText = getDurationStringLong(vm.positionState)
                            Row {
                                Text(posText, color = textColor, style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(progress = { vm.prog }, modifier = Modifier.weight(1f).height(4.dp).align(Alignment.CenterVertically))
                                Text(durText, color = textColor, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        if (selectMode) {
            Row(modifier = Modifier.align(Alignment.TopEnd).width(150.dp).height(45.dp).background(MaterialTheme.colorScheme.onTertiaryContainer),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_upward_24), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                    .clickable(onClick = {
                        selected.clear()
                        val eList = multiSelectCB(longPressIndex, -1)
                        if (eList.isEmpty()) for (i in 0..longPressIndex) selected.add(vms[i])
                        else selected.addAll(eList)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_downward_24), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                    .clickable(onClick = {
                        selected.clear()
                        val eList = multiSelectCB(longPressIndex, 1)
                        if (eList.isEmpty()) for (i in longPressIndex..<vms.size) selected.add(vms[i])
                        else selected.addAll(eList)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                var selectAllRes by remember { mutableIntStateOf(R.drawable.ic_select_all) }
                Icon(imageVector = ImageVector.vectorResource(selectAllRes), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                    .clickable(onClick = {
                        if (selectedSize != vms.size) {
                            selected.clear()
                            val eList = multiSelectCB(longPressIndex, 0)
                            if (eList.isEmpty()) for (vm in vms) selected.add(vm)
                            else selected.addAll(eList)
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
            EpisodeSpeedDial(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp, start = 16.dp))
        }
    }
}

@Composable
fun EpisodesFilterDialog(filter: EpisodeFilter, filtersDisabled: MutableSet<EpisodesFilterGroup> = mutableSetOf(),
                         onDismissRequest: () -> Unit, onFilterChanged: (EpisodeFilter) -> Unit) {
//    val filterValuesSet = remember {  filter.propertySet ?: mutableSetOf() }
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
        Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            val buttonColor = MaterialTheme.colorScheme.tertiary
            val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
            val scrollStateV = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scrollStateV)) {
                var selectNone by remember { mutableStateOf(false) }
                for (item in EpisodesFilterGroup.entries) {
                    if (item in filtersDisabled) continue
                    if (item.properties.size == 2) {
                        Row(modifier = Modifier.padding(start = 5.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Absolute.Left, verticalAlignment = Alignment.CenterVertically) {
                            var selectedIndex by remember { mutableIntStateOf(-1) }
                            if (selectNone) selectedIndex = -1
                            LaunchedEffect(Unit) {
                                if (item.properties[0].filterId in filter.propertySet) selectedIndex = 0
                                else if (item.properties[1].filterId in filter.propertySet) selectedIndex = 1
                            }
                            Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge , color = textColor, modifier = Modifier.padding(end = 10.dp))
                            Spacer(Modifier.width(30.dp))
                            OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (selectedIndex != 0) buttonColor else buttonAltColor),
                                onClick = {
                                    if (selectedIndex != 0) {
                                        selectNone = false
                                        selectedIndex = 0
                                        filter.propertySet.add(item.properties[0].filterId)
                                        filter.propertySet.remove(item.properties[1].filterId)
                                    } else {
                                        selectedIndex = -1
                                        filter.propertySet.remove(item.properties[0].filterId)
                                    }
                                    Logd("EpisodeFilterDialog", "filterValues = [${filter.propertySet}]")
                                    onFilterChanged(filter)
                                },
                            ) { Text(text = stringResource(item.properties[0].displayName), color = textColor) }
                            Spacer(Modifier.width(20.dp))
                            OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (selectedIndex != 1) buttonColor else buttonAltColor),
                                onClick = {
                                    if (selectedIndex != 1) {
                                        selectNone = false
                                        selectedIndex = 1
                                        filter.propertySet.add(item.properties[1].filterId)
                                        filter.propertySet.remove(item.properties[0].filterId)
                                    } else {
                                        selectedIndex = -1
                                        filter.propertySet.remove(item.properties[1].filterId)
                                    }
                                    onFilterChanged(filter)
                                },
                            ) { Text(text = stringResource(item.properties[1].displayName), color = textColor) }
//                            Spacer(Modifier.weight(0.5f))
                        }
                    } else {
                        Column(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                            val selectedList = remember { MutableList(item.properties.size) { mutableStateOf(false)} }
                            var expandRow by remember { mutableStateOf(false) }
                            when (item) {
                                EpisodesFilterGroup.DURATION -> {
                                    Text(stringResource(item.nameRes) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = buttonColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                    if (expandRow) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            var showIcon by remember { mutableStateOf(false) }
                                            var floor by remember { mutableStateOf((filter.durationFloor/1000).toString()) }
                                            var ceiling by remember { mutableStateOf((filter.durationCeiling/1000).toString()) }
                                            TextField(value = floor, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Floor(seconds)") },
                                                singleLine = true, modifier = Modifier.weight(0.4f),
                                                onValueChange = {
                                                    if (it.isEmpty() || it.toIntOrNull() != null) {
                                                        floor = it
                                                        showIcon = true
                                                    }
                                                })
                                            TextField(value = ceiling, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Ceiling(seconds)") },
                                                singleLine = true, modifier = Modifier.padding(start = 10.dp).weight(0.4f),
                                                onValueChange = {
                                                    if (it.isEmpty() || it.toIntOrNull() != null) {
                                                        ceiling = it
                                                        showIcon = true
                                                    }
                                                })
                                            if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                                                modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                                    if (floor.isNotBlank() || ceiling.isNotBlank()) {
                                                        val f = if (floor.isBlank() || floor.toIntOrNull() == null) 0 else floor.toInt()
                                                        val c = if (ceiling.isBlank() || ceiling.toIntOrNull() == null) Int.MAX_VALUE else ceiling.toInt()
//                                                        Logd("EpisodeFilterDialog", "f = $f c = $c")
                                                        filter.durationFloor = f * 1000
                                                        filter.durationCeiling = if (c < Int.MAX_VALUE) c * 1000 else c
                                                    }
                                                    showIcon = false
                                                }))
                                        }
                                    }
                                }
                                EpisodesFilterGroup.TITLE_TEXT -> {
                                    Text(stringResource(item.nameRes) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = buttonColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                    if (expandRow) {
                                        var showIcon by remember { mutableStateOf(false) }
                                        var titleText by remember { mutableStateOf((filter.titleText)) }
                                        TextField(value = titleText, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), label = { Text("Text in titles") }, singleLine = true,
                                            onValueChange = {
                                                titleText = it
                                                showIcon = true
                                            },
                                            trailingIcon = {
                                                if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                                                    modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                                        filter.titleText = titleText
                                                        showIcon = false
                                                    }))
                                            })
                                    }
                                }
                                else -> {
                                    Row(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Absolute.Left, verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(item.nameRes) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = buttonColor, modifier = Modifier.clickable { expandRow = !expandRow })
                                        if (expandRow) {
                                            val cb = {
                                                for (i in item.properties.indices) {
                                                    if (selectedList[i].value) filter.propertySet.add(item.properties[i].filterId)
                                                    else filter.propertySet.remove(item.properties[i].filterId)
                                                }
                                                onFilterChanged(filter)
                                            }
                                            SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                        }
//                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            if (expandRow) NonlazyGrid(columns = 3, itemCount = item.properties.size) { index ->
                                if (selectNone) selectedList[index].value = false
                                LaunchedEffect(Unit) {
                                    if (item.properties[index].filterId in filter.propertySet) selectedList[index].value = true
                                }
                                OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                    onClick = {
                                        selectNone = false
                                        selectedList[index].value = !selectedList[index].value
                                        if (selectedList[index].value) {
                                            filter.propertySet.add(item.properties[index].filterId)
                                            if (item.exclusive) for (i in selectedList.indices) {
                                                if (i != index) {
                                                    selectedList[i].value = false
                                                    filter.propertySet.remove(item.properties[i].filterId)
                                                }
                                            }
                                        }
                                        else filter.propertySet.remove(item.properties[index].filterId)
                                        onFilterChanged(filter)
                                    },
                                ) { Text(text = stringResource(item.properties[index].displayName), maxLines = 1, color = textColor) }
                            }
                        }
                    }
                }
                Row {
                    Spacer(Modifier.weight(0.3f))
                    Button(onClick = {
                        selectNone = true
                        filter.propertySet.clear()
                        onFilterChanged(filter)
                    }) { Text(stringResource(R.string.reset)) }
                    Spacer(Modifier.weight(0.4f))
                    Button(onClick = { onDismissRequest() }) { Text(stringResource(R.string.close_label)) }
                    Spacer(Modifier.weight(0.3f))
                }
            }
        }
    }
}

var episodeSortOrder by mutableStateOf<EpisodeSortOrder?>(null)

@Composable
fun EpisodeSortDialog(initOrder: EpisodeSortOrder?, showKeepSorted: Boolean = false, onDismissRequest: () -> Unit, onSelectionChanged: (EpisodeSortOrder, Boolean) -> Unit) {
    val orderList = remember { EpisodeSortOrder.entries.filterIndexed { index, f -> index % 2 != 0 && (!f.conditional || gearbox.SupportExtraSort()) } }
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
        Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(250.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            val scrollState = rememberScrollState()
            var sortIndex by remember { mutableIntStateOf(if (initOrder != null) initOrder.ordinal/2 else -1) }
            var keepSorted by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp).verticalScroll(scrollState)) {
                NonlazyGrid(columns = 2, itemCount = orderList.size) { index ->
                    var dir by remember { mutableStateOf(if (sortIndex == index) initOrder!!.ordinal % 2 == 0 else true) }
                    OutlinedButton(modifier = Modifier.padding(2.dp), elevation = null, border = BorderStroke(2.dp, if (sortIndex != index) buttonColor else buttonAltColor),
                        onClick = {
                            if (sortIndex == index) dir = !dir
                            sortIndex = index
                            episodeSortOrder = EpisodeSortOrder.entries[2 * index + if (dir) 0 else 1]
                            onSelectionChanged(episodeSortOrder!!, keepSorted)
                        }
                    ) { Text(text = stringResource(orderList[index].res) + if (dir) "\u00A0▲" else "\u00A0▼", color = textColor) }
                }
                if (showKeepSorted) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keepSorted, onCheckedChange = { keepSorted = it })
                    Text(text = stringResource(R.string.remove_from_other_queues), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

@Composable
fun DatesFilterDialog(from: Long? = null, to: Long? = null, oldestDate: Long, onDismissRequest: ()->Unit, callback: (Long, Long) -> Unit) {
    @Composable
    fun MonthYearInput(default: String, onMonthYearChange: (String) -> Unit) {
        fun formatMonthYear(input: String): String {
            val sanitized = input.replace(Regex("[^0-9/]"), "")
            return when {
                sanitized.length > 7 -> sanitized.substring(0, 7)
                else -> sanitized
            }
        }
        fun isValidMonthYear(input: String): Boolean {
            val regex = Regex("^(0[1-9]|1[0-2])/\\d{4}$")
            return regex.matches(input)
        }
        var monthYear by remember { mutableStateOf(TextFieldValue(default)) }
        var isValid by remember { mutableStateOf(isValidMonthYear(monthYear.text)) }
        Column(modifier = Modifier.padding(16.dp)) {
            TextField(value = monthYear, label = { Text(stringResource(R.string.statistics_month_year)) },
                onValueChange = { input ->
                    monthYear = input
                    val formattedInput = formatMonthYear(monthYear.text)
                    isValid = isValidMonthYear(formattedInput)
                    if (isValid) onMonthYearChange(formattedInput)
                },
                isError = !isValidMonthYear(monthYear.text),
                modifier = Modifier.fillMaxWidth()
            )
            if (!isValid) Text(text = "Invalid format. Please use MM/YYYY.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
    fun convertMonthYearToUnixTime(monthYear: String, start: Boolean = true): Long? {
        val regex = Regex("^(0[1-9]|1[0-2])/\\d{4}$")
        if (!regex.matches(monthYear)) return null
        val (month, year) = monthYear.split("/").map { it.toInt() }
        val localDate = if (start) LocalDate.of(year, month, 1) else LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    fun convertUnixTimeToMonthYear(unixTime: Long): String {
        return Instant.ofEpochMilli(unixTime).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MM/yyyy"))
    }
    var timeFilterFrom by remember { mutableLongStateOf(from ?: 0L) }
    var timeFilterTo by remember { mutableLongStateOf(to ?: Long.MAX_VALUE) }
    var useAllTime by remember { mutableStateOf((from == null || from == 0L) && (to == null || to == Long.MAX_VALUE)) }
    val timeFrom by remember { derivedStateOf { if (timeFilterFrom == 0L) oldestDate else timeFilterFrom  } }
    val timeTo by remember { derivedStateOf { if (timeFilterTo == Long.MAX_VALUE) System.currentTimeMillis() else timeFilterTo } }
    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
        title = { Text(stringResource(R.string.share_label), style = CustomTextStyles.titleCustom) },
        text = {
            Column {
                if (!useAllTime) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.statistics_start_month))
                        MonthYearInput(convertUnixTimeToMonthYear(timeFrom)) { timeFilterFrom = convertMonthYearToUnixTime(it) ?: timeFrom }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.statistics_end_month))
                        MonthYearInput(convertUnixTimeToMonthYear(timeTo)) { timeFilterTo = convertMonthYearToUnixTime(it, false) ?: timeTo }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useAllTime, onCheckedChange = {
                        useAllTime = it
                        if (!useAllTime) {
                            timeFilterFrom = convertMonthYearToUnixTime(convertUnixTimeToMonthYear(timeFrom)) ?: timeFrom
                            timeFilterTo = convertMonthYearToUnixTime(convertUnixTimeToMonthYear(timeTo), false) ?: timeTo
                        }
                    })
                    Text(stringResource(R.string.statistics_filter_all_time))
                }
                Text(stringResource(R.string.statistics_speed_not_counted))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (useAllTime) {
                    timeFilterFrom = 0L
                    timeFilterTo = Long.MAX_VALUE
                }
                callback(timeFilterFrom, timeFilterTo)
                onDismissRequest()
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
    )
}
