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
import ac.mdiq.podcini.net.utils.NetworkUtils.isAllowMobileEpisodeDownload
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodeMedia
import ac.mdiq.podcini.storage.database.Episodes.deleteMediaSync
import ac.mdiq.podcini.storage.database.Episodes.hasAlmostEnded
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Feeds.addToMiscSyndicate
import ac.mdiq.podcini.storage.database.Feeds.allowForAutoDelete
import ac.mdiq.podcini.storage.database.Queues
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesQuiet
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesSync
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.EpisodeFilter.EpisodesFilterGroup
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.newId
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLong
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.NullActionButton
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Screens
import ac.mdiq.podcini.ui.screens.FeedScreenMode
import ac.mdiq.podcini.ui.utils.episodeOnDisplay
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.util.*
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import ac.mdiq.podcini.util.MiscFormatter.formatLargeInteger
import ac.mdiq.podcini.util.MiscFormatter.localDateTimeString
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.notifications.UpdatedObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.roundToInt

const val VMS_CHUNK_SIZE = 50
const val loadThreshold = (VMS_CHUNK_SIZE * 0.8).toInt()

@Composable
fun InforBar(text: MutableState<String>, leftAction: MutableState<SwipeAction>, rightAction: MutableState<SwipeAction>, actionConfig: () -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    Logd("InforBar", "textState: ${text.value}")
    Row {
        Icon(imageVector = ImageVector.vectorResource(leftAction.value.getActionIcon()), tint = buttonColor, contentDescription = "left_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = actionConfig))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "left_arrow",
            modifier = Modifier.width(24.dp).height(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        Text(text.value, color = textColor, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow",
            modifier = Modifier.width(24.dp).height(24.dp))
        Icon(imageVector = ImageVector.vectorResource(rightAction.value.getActionIcon()), tint = buttonColor, contentDescription = "right_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = actionConfig))
    }
}

fun stopMonitor(vms: List<EpisodeVM>) {
    vms.forEach { it.stopMonitoring() }
}

@Stable
class EpisodeVM(var episode: Episode, val tag: String) {
    var positionState by mutableStateOf(episode.position)
    var durationState by mutableStateOf(episode.duration)
    var playedState by mutableIntStateOf(episode.playState)
    var isPlayingState by mutableStateOf(false)
    var ratingState by mutableIntStateOf(episode.rating)
    var inProgressState by mutableStateOf(episode.isInProgress)
    var downloadState by mutableIntStateOf(if (episode.downloaded == true) DownloadStatus.State.COMPLETED.ordinal else DownloadStatus.State.UNKNOWN.ordinal)
    var viewCount by mutableIntStateOf(episode.viewCount)
    var actionButton by mutableStateOf<EpisodeActionButton>(NullActionButton(episode).forItem(episode))
    var actionRes by mutableIntStateOf(actionButton.drawable)
    var showAltActionsDialog by mutableStateOf(false)
    var dlPercent by mutableIntStateOf(0)
    var isSelected by mutableStateOf(false)
    var prog by mutableFloatStateOf(0f)

    private var episodeMonitor: Job? by mutableStateOf(null)

    fun stopMonitoring() {
        episodeMonitor?.cancel()
        episodeMonitor = null
//        showStackTrace()
        Logd("EpisodeVM", "cancel monitoring")
    }

    fun startMonitoring() {
        if (episodeMonitor == null) {
            episodeMonitor = CoroutineScope(Dispatchers.Default).launch {
                val item_ = realm.query(Episode::class).query("id == ${episode.id}").first()
                Logd("EpisodeVM", "start monitoring episode: ${episode.id} ${episode.title}")
                val episodeFlow = item_.asFlow()
                episodeFlow.collect { changes: SingleQueryChange<Episode> ->
                    when (changes) {
                        is UpdatedObject -> {
                            Logd("EpisodeVM", "episodeMonitor UpdatedObject $tag ${changes.obj.title} ${changes.changedFields.joinToString()}")
                            if (episode.id == changes.obj.id) {
                                withContext(Dispatchers.Main) {
                                    playedState = changes.obj.playState
                                    ratingState = changes.obj.rating
                                    positionState = changes.obj.position
                                    durationState = changes.obj.duration
                                    inProgressState = changes.obj.isInProgress
                                    downloadState = if (changes.obj.downloaded == true) DownloadStatus.State.COMPLETED.ordinal else DownloadStatus.State.UNKNOWN.ordinal
                                    episode = changes.obj     // direct assignment doesn't update member like media??
                                }
//                                Logd("EpisodeVM", "episodeMonitor $playedState $playedState ")
                            } else Logd("EpisodeVM", "episodeMonitor index out bound")
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun ChooseRatingDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (rating in Rating.entries.reversed()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                        .padding(4.dp)
                        .clickable {
                            for (item in selected) Episodes.setRating(item, rating.code)
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
fun PlayStateDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (state in PlayState.entries.reversed()) {
                    if (state.userSet) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)
                            .clickable {
                                for (item in selected) {
                                    val hasAlmostEnded = hasAlmostEnded(item)
                                    var item_ = runBlocking { setPlayStateSync(state.code, item, hasAlmostEnded, false) }
                                    when (state) {
                                        PlayState.UNPLAYED -> {
                                            if (isProviderConnected && item_.feed?.isLocalFeed != true) {
                                                val actionNew: EpisodeAction = EpisodeAction.Builder(item_, EpisodeAction.NEW).currentTimestamp().build()
                                                SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, actionNew)
                                            }
                                        }
                                        PlayState.PLAYED -> {
                                            if (hasAlmostEnded) item_ = upsertBlk(item_) { it.playbackCompletionDate = Date() }
                                            val shouldAutoDelete = if (item_.feed == null) false else allowForAutoDelete(item_.feed!!)
//                                            item = item_
                                            if (hasAlmostEnded && shouldAutoDelete) {
                                                item_ = deleteMediaSync(context, item_)
                                                if (getPref(AppPrefs.prefDeleteRemovesFromQueue, true)) removeFromAllQueuesSync(item_)
                                            } else if (getPref(AppPrefs.prefRemoveFromQueueMarkedPlayed, true)) removeFromAllQueuesSync(item_)
                                            if (item_.feed?.isLocalFeed != true && (isProviderConnected || wifiSyncEnabledKey)) {
                                                // not all items have media, Gpodder only cares about those that do
                                                if (isProviderConnected) {
                                                    val actionPlay: EpisodeAction = EpisodeAction.Builder(item_, EpisodeAction.PLAY)
                                                        .currentTimestamp()
                                                        .started(item_.duration / 1000)
                                                        .position(item_.duration / 1000)
                                                        .total(item_.duration / 1000)
                                                        .build()
                                                    SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, actionPlay)
                                                }
                                            }
                                        }
                                        PlayState.QUEUE -> {
                                            if (item_.feed?.queue != null) runBlocking { addToQueueSync(item, item.feed?.queue) }
                                        }
                                        else -> {}
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
fun PutToQueueDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
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
                        if (removeChecked) {
                            val toRemove = mutableSetOf<Long>()
                            val toRemoveCur = mutableListOf<Episode>()
                            selected.forEach { e -> if (curQueue.contains(e)) toRemoveCur.add(e) }
                            selected.forEach { e ->
                                for (q in queues) {
                                    if (q.contains(e)) {
                                        toRemove.add(e.id)
                                        break
                                    }
                                }
                            }
                            if (toRemove.isNotEmpty()) runBlocking { removeFromAllQueuesQuiet(toRemove.toList()) }
                            if (toRemoveCur.isNotEmpty()) EventFlow.postEvent(FlowEvent.QueueEvent.removed(toRemoveCur))
                        }
                        selected.forEach { e -> runBlocking { addToQueueSync(e, toQueue) } }
                        onDismissRequest()
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }
}

@Composable
fun ShelveDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
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
                        val eList: MutableList<Episode> = mutableListOf()
                        for (e in selected) {
                            var e_ = e
                            if (!removeChecked || (e.feedId != null && e.feedId!! >= MAX_SYNTHETIC_ID)) {
                                e_ = realm.copyFromRealm(e)
                                e_.id = newId()
                            } else {
                                val feed = realm.query(Feed::class).query("id == $0", e_.feedId).first().find()
                                if (feed != null) upsertBlk(feed) { it.episodes.remove(e_) }
                            }
                            upsertBlk(e_) {
                                it.feed = toFeed
                                it.feedId = toFeed!!.id
                                eList.add(it)
                            }
                        }
                        upsertBlk(toFeed!!) { it.episodes.addAll(eList) }
                        onDismissRequest()
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }
}

@Composable
fun EraseEpisodesDialog(selected: List<Episode>, feed: Feed?, onDismissRequest: () -> Unit) {
    val message = stringResource(R.string.erase_episodes_confirmation_msg)
    val textColor = MaterialTheme.colorScheme.onSurface
    var textState by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            if (feed == null || !feed.isSynthetic()) Text(stringResource(R.string.not_erase_message), modifier = Modifier.padding(10.dp))
            else Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message + ": ${selected.size}")
                Text(stringResource(R.string.feed_delete_reason_msg))
                BasicTextField(value = textState, onValueChange = { textState = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor),
                    modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                )
                Button(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            for (e in selected) {
                                val sLog = SubscriptionLog(e.id, e.title?:"", e.downloadUrl?:"", e.link?:"", SubscriptionLog.Type.Media.name)
                                upsert(sLog) {
                                    it.rating = e.rating
                                    it.comment = if (e.comment.isBlank()) "" else (e.comment + "\n")
                                    it.comment += localDateTimeString() + "\nReason to remove:\n" + textState.text
                                    it.cancelDate = Date().time
                                }
                            }
                            realm.write {
                                for (e in selected) {
                                    val url = e.fileUrl
                                    when {
                                        url != null && url.startsWith("content://") -> DocumentFile.fromSingleUri(context, Uri.parse(url))?.delete()
                                        url != null -> {
                                            val path = Uri.parse(url).path
                                            if (path != null) File(path).delete()
                                        }
                                    }
                                    findLatest(feed)?.episodes?.remove(e)
                                    findLatest(e)?.let { delete(it) }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun EpisodeLazyColumn(activity: Context, vms: MutableList<EpisodeVM>, feed: Feed? = null, layoutMode: Int = 0,
                      buildMoreItems: (()-> Unit) = {},
                      isDraggable: Boolean = false, dragCB: ((Int, Int)->Unit)? = null,
                      refreshCB: (()->Unit)? = null, leftSwipeCB: ((Episode) -> Unit)? = null, rightSwipeCB: ((Episode) -> Unit)? = null,
                      actionButton_: ((Episode)-> EpisodeActionButton)? = null) {
    val TAG = "EpisodeLazyColumn"
    var selectMode by remember { mutableStateOf(false) }
    var selectedSize by remember { mutableStateOf(0) }
    val selected = remember { mutableStateListOf<Episode>() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var longPressIndex by remember { mutableIntStateOf(-1) }
    val dls = remember { DownloadServiceInterface.impl }
    val context = LocalContext.current

    val showConfirmYoutubeDialog = remember { mutableStateOf(false) }
    val ytUrls = remember { mutableListOf<String>() }
    gearbox.ConfirmAddEpisode(ytUrls, showConfirmYoutubeDialog.value, onDismissRequest = { showConfirmYoutubeDialog.value = false })

    var showChooseRatingDialog by remember { mutableStateOf(false) }
    if (showChooseRatingDialog) ChooseRatingDialog(selected) { showChooseRatingDialog = false }

    var showPlayStateDialog by remember { mutableStateOf(false) }
    if (showPlayStateDialog) PlayStateDialog(selected) { showPlayStateDialog = false }

    var showPutToQueueDialog by remember { mutableStateOf(false) }
    if (showPutToQueueDialog) PutToQueueDialog(selected) { showPutToQueueDialog = false }

    var showShelveDialog by remember { mutableStateOf(false) }
    if (showShelveDialog) ShelveDialog(selected) { showShelveDialog = false }

    var showEraseDialog by remember { mutableStateOf(false) }
    if (showEraseDialog && feed != null) EraseEpisodesDialog(selected, feed, onDismissRequest = { showEraseDialog = false })

    @Composable
    fun EpisodeSpeedDial(modifier: Modifier = Modifier) {
        var isExpanded by remember { mutableStateOf(false) }
        val options = mutableListOf<@Composable () -> Unit>(
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                isExpanded = false
                selectMode = false
                Logd(TAG, "ic_delete: ${selected.size}")
                runOnIOScope {
                    for (item_ in selected) {
                        var item = item_
                        if (!item.downloaded && item.feed?.isLocalFeed != true) continue
                        val almostEnded = hasAlmostEnded(item)
                        if (almostEnded && item.playState < PlayState.PLAYED.code) item = setPlayStateSync(PlayState.PLAYED.code, item, almostEnded, false)
                        if (almostEnded) item = upsert(item) { it.playbackCompletionDate = Date() }
                        deleteEpisodeMedia(activity, item)
                    }
                }
//                    LocalDeleteModal.deleteEpisodesWarnLocal(activity, selected)
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "Delete media")
                Text(stringResource(id = R.string.delete_episode_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                isExpanded = false
                selectMode = false
                Logd(TAG, "ic_download: ${selected.size}")
                fun download(now: Boolean) {
                    for (episode in selected) {
                        if (episode.feed != null && !episode.feed!!.isLocalFeed) DownloadServiceInterface.impl?.downloadNow(activity, episode, now)
                    }
                }
                if (isAllowMobileEpisodeDownload || !isNetworkRestricted) download(true)
                else {
                    val builder = MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.confirm_mobile_download_dialog_title)
                        .setPositiveButton(R.string.confirm_mobile_download_dialog_download_later) { _: DialogInterface?, _: Int -> download(false) }
                        .setNeutralButton(R.string.confirm_mobile_download_dialog_allow_this_time) { _: DialogInterface?, _: Int -> download(true) }
                        .setNegativeButton(R.string.cancel_label, null)
                    if (isNetworkRestricted && NetworkUtils.isVpnOverWifi) builder.setMessage(R.string.confirm_mobile_download_dialog_message_vpn)
                    else builder.setMessage(R.string.confirm_mobile_download_dialog_message)
                    builder.show()
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_download), "Download")
                Text(stringResource(id = R.string.download_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                showPlayStateDialog = true
                isExpanded = false
                selectMode = false
                Logd(TAG, "ic_mark_played: ${selected.size}")
//                    setPlayState(PlayState.UNSPECIFIED.code, false, *selected.toTypedArray())
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mark_played), "Toggle played state")
                Text(stringResource(id = R.string.set_play_state_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                isExpanded = false
                selectMode = false
                Logd(TAG, "ic_playlist_remove: ${selected.size}")
                runOnIOScope {
                    for (item_ in selected) {
                        var item = item_
                        val almostEnded = hasAlmostEnded(item)
                        if (almostEnded && item.playState < PlayState.PLAYED.code) item = setPlayStateSync(PlayState.PLAYED.code, item, almostEnded, false)
                        if (almostEnded) item = upsert(item) { it.playbackCompletionDate = Date() }
                        if (item.playState < PlayState.SKIPPED.code) setPlayState(PlayState.SKIPPED.code, false, item)
                    }
                    removeFromQueueSync(curQueue, *selected.toTypedArray())
//                        removeFromQueue(*selected.toTypedArray())
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_remove), "Remove from active queue")
                Text(stringResource(id = R.string.remove_from_queue_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                isExpanded = false
                selectMode = false
                Logd(TAG, "ic_playlist_play: ${selected.size}")
                Queues.addToQueue(*selected.toTypedArray())
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to active queue")
                Text(stringResource(id = R.string.add_to_queue_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                isExpanded = false
                selectMode = false
                Logd(TAG, "shelve_label: ${selected.size}")
                showShelveDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_shelves_24), "Shelve")
                Text(stringResource(id = R.string.shelve_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                isExpanded = false
                selectMode = false
                Logd(TAG, "ic_playlist_play: ${selected.size}")
                showPutToQueueDialog = true
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to queue...")
                Text(stringResource(id = R.string.put_in_queue_label)) } },
            { Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                selectMode = false
                Logd(TAG, "ic_star: ${selected.size}")
                showChooseRatingDialog = true
                isExpanded = false
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), "Set rating")
                Text(stringResource(id = R.string.set_rating_label)) } },
        )
        if (selected.isNotEmpty() && selected[0].isRemote.value)
            options.add {
                Row(modifier = Modifier.padding(horizontal = 16.dp).clickable {
                    isExpanded = false
                    selectMode = false
                    Logd(TAG, "reserve: ${selected.size}")
                    CoroutineScope(Dispatchers.IO).launch {
                        ytUrls.clear()
                        for (e in selected) {
                            Logd(TAG, "downloadUrl: ${e.downloadUrl}")
                            val url = URL(e.downloadUrl ?: "")
                            if (gearbox.isGearUrl(url)) ytUrls.add(e.downloadUrl!!)
                            else addToMiscSyndicate(e)
                        }
                        Logd(TAG, "youtubeUrls: ${ytUrls.size}")
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
                    isExpanded = false
                    selectMode = false
                    showEraseDialog = true
                    Logd(TAG, "reserve: ${selected.size}")
                }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_delete_forever_24), "Erase episodes")
                    Text(stringResource(id = R.string.erase_episodes_label))
                }
            }
        }

        val scrollState = rememberScrollState()
        Column(modifier = modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.Bottom) {
            if (isExpanded) options.forEachIndexed { _, button ->
                FloatingActionButton(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).height(40.dp),onClick = {}) { button() }
            }
            FloatingActionButton(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.tertiary,
                onClick = { isExpanded = !isExpanded }) { Icon(Icons.Filled.Edit, "Edit") }
        }
    }

    fun toggleSelected(vm: EpisodeVM) {
        vm.isSelected = !vm.isSelected
        if (vm.isSelected) selected.add(vm.episode)
        else selected.remove(vm.episode)
    }

    val titleMaxLines = if (layoutMode == 0) 2 else 3
    @Composable
    fun TitleColumn(vm: EpisodeVM, index: Int, modifier: Modifier) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(modifier.padding(start = 6.dp, end = 6.dp)
            .combinedClickable(onClick = {
                Logd(TAG, "clicked: ${vm.episode.title}")
                if (selectMode) toggleSelected(vm)
                else {
                    episodeOnDisplay = vm.episode
                    mainNavController.navigate(Screens.EpisodeInfo.name)
                }
            }, onLongClick = {
                selectMode = !selectMode
                vm.isSelected = selectMode
                selected.clear()
                if (selectMode) {
                    selected.add(vms[index].episode)
                    longPressIndex = index
                } else {
                    selectedSize = 0
                    longPressIndex = -1
                }
                Logd(TAG, "long clicked: ${vm.episode.title}")
            })) {
            Text(vm.episode.title ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
            if (layoutMode == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val playStateRes = PlayState.fromCode(vm.playedState).res
                    Icon(imageVector = ImageVector.vectorResource(playStateRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "playState",
                        modifier = Modifier.background(if (vm.playedState >= PlayState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                    val ratingIconRes = Rating.fromCode(vm.ratingState).res
                    if (vm.ratingState != Rating.UNRATED.code)
                        Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                    if (vm.episode.getMediaType() == MediaType.VIDEO)
                        Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                    val curContext = LocalContext.current
                    val dateSizeText = " · " + formatDateTimeFlex(vm.episode.getPubDate()) +
                            " · " + getDurationStringLong(vm.durationState) +
                            (if (vm.episode.size > 0) " · " + Formatter.formatShortFileSize(curContext, vm.episode.size) else "") +
                            (if (vm.viewCount > 0) " · " + formatLargeInteger(vm.viewCount) else "")
                    Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                val curContext = LocalContext.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val playStateRes = PlayState.fromCode(vm.playedState).res
                    Icon(imageVector = ImageVector.vectorResource(playStateRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "playState",
                        modifier = Modifier.background(if (vm.playedState >= PlayState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                    val ratingIconRes = Rating.fromCode(vm.ratingState).res
                    if (vm.ratingState != Rating.UNRATED.code)
                        Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                    if (vm.episode.getMediaType() == MediaType.VIDEO)
                        Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                    val dateSizeText = " · " + getDurationStringLong(vm.durationState) +
                            (if (vm.episode.size > 0) " · " + Formatter.formatShortFileSize(curContext, vm.episode.size) else "")
                    Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dateSizeText = formatDateTimeFlex(vm.episode.getPubDate()) + (if (vm.viewCount > 0) " · " + formatLargeInteger(vm.viewCount) else "")
                    Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (vm.viewCount > 0)
                        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_people_alt_24), tint = textColor, contentDescription = "people", modifier = Modifier.width(16.dp).height(16.dp))
                }
            }
        }
    }

    @Composable
    fun MainRow(vm: EpisodeVM, index: Int, isBeingDragged: Boolean, yOffset: Float, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
        val textColor = MaterialTheme.colorScheme.onSurface
        val buttonColor = MaterialTheme.colorScheme.tertiary
        val density = LocalDensity.current
        val imageWidth = if (layoutMode == 0) 56.dp else 150.dp
        val imageHeight = if (layoutMode == 0) 56.dp else 100.dp
        Row(Modifier.background(if (vm.isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .offset(y = with(density) { yOffset.toDp() })) {
            if (isDraggable) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.dragview_background, typedValue, true)
                Icon(imageVector = ImageVector.vectorResource(typedValue.resourceId), tint = buttonColor, contentDescription = "drag handle",
                    modifier = Modifier.width(50.dp).align(Alignment.CenterVertically).padding(start = 10.dp, end = 15.dp)
                        .draggable(orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta -> onDrag(delta) },
                            onDragStarted = { onDragStart() },
                            onDragStopped = { onDragEnd() }
                        ))
            }
            val imgLoc = remember(vm.episode) { vm.episode.getEpisodeListImageLocation() }
            AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                contentDescription = "imgvCover",
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
            Box(Modifier.weight(1f).height(imageHeight)) {
                TitleColumn(vm, index, modifier = Modifier.fillMaxWidth())
//                var actionButton by remember(vm.episode.id) { mutableStateOf(vm.actionButton.forItem(vm.episode)) }
                fun isDownloading(): Boolean {
                    return vms[index].downloadState > DownloadStatus.State.UNKNOWN.ordinal && vms[index].downloadState < DownloadStatus.State.COMPLETED.ordinal
                }
                if (actionButton_ == null) {
                    LaunchedEffect(key1 = status, key2 = vm.downloadState) {
                        if (index >= vms.size) return@LaunchedEffect
                        if (isDownloading()) vm.dlPercent = dls?.getProgress(vms[index].episode.downloadUrl ?: "") ?: 0
                        Logd(TAG, "LaunchedEffect $index isPlayingState: ${vms[index].isPlayingState} ${vm.episode.playState} ${vms[index].episode.title}")
                        Logd(TAG, "LaunchedEffect $index downloadState: ${vms[index].downloadState} ${vm.episode.downloaded} ${vm.dlPercent}")
                        vm.actionButton = vm.actionButton.forItem(vm.episode)
//                        if (vm.actionButton.label != actionButton.label) actionButton = vm.actionButton
                        Logd(TAG, "LaunchedEffect vm.actionButton: ${vm.actionButton.TAG}")
                    }
                } else {
                    LaunchedEffect(Unit) {
                        Logd(TAG, "LaunchedEffect init actionButton")
                        vm.actionButton = actionButton_(vm.episode)
//                        actionButton = vm.actionButton
                    }
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.width(40.dp).height(40.dp).padding(end = 10.dp).align(Alignment.BottomEnd)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { vms[index].showAltActionsDialog = true }, onTap = { vm.actionButton.onClick(activity) })
                    }) {
//                    val actionRes by remember(vm.actionButton.drawable) { derivedStateOf { vm.actionButton.drawable } }
//                    Logd(TAG, "actionRes: $index ${vm.actionButton.TAG}")
                    Icon(imageVector = ImageVector.vectorResource(vm.actionButton.drawable), tint = buttonColor, contentDescription = null, modifier = Modifier.width(28.dp).height(32.dp))
                    if (isDownloading() && vm.dlPercent >= 0) CircularProgressIndicator(progress = { 0.01f * vm.dlPercent },
                        strokeWidth = 4.dp, color = buttonColor, modifier = Modifier.width(33.dp).height(37.dp))
                    if (vm.actionButton.processing > -1) CircularProgressIndicator(progress = { 0.01f * vm.actionButton.processing },
                        strokeWidth = 4.dp, color = buttonColor, modifier = Modifier.width(33.dp).height(37.dp))
                }
                if (vm.showAltActionsDialog) vm.actionButton.AltActionsDialog(activity, onDismiss = { vm.showAltActionsDialog = false })
            }
        }
    }

    @Composable
    fun ProgressRow(vm: EpisodeVM, index: Int) {
        val textColor = MaterialTheme.colorScheme.onSurface
        if (vm.inProgressState || InTheatre.isCurMedia(vm.episode)) {
            val pos = vm.positionState
            val dur = remember(vm.episode) { vm.episode.duration }
            val durText = remember(dur) { getDurationStringLong(dur) }
            vm.prog = if (dur > 0 && pos >= 0 && dur >= pos) 1.0f * pos / dur else 0f
//            Logd(TAG, "$index vm.prog: ${vm.prog}")
            Row {
                Text(getDurationStringLong(vm.positionState), color = textColor, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { vm.prog }, modifier = Modifier.weight(1f).height(4.dp).align(Alignment.CenterVertically))
                Text(durText, color = textColor, style = MaterialTheme.typography.bodySmall)
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
    PullToRefreshBox(modifier = Modifier.fillMaxWidth(), isRefreshing = refreshing, indicator = {}, onRefresh = {
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
        val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vms, key = { _, vm -> vm.episode.id}) { index, vm ->
                vm.startMonitoring()
                DisposableEffect(Unit) {
                    onDispose {
                        Logd(TAG, "cancelling monitoring $index")
                        vm.stopMonitoring()
                    }
                }
                val velocityTracker = remember { VelocityTracker() }
                val offsetX = remember { Animatable(0f) }
                Box(modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragStart = { velocityTracker.resetTracking() },
                        onHorizontalDrag = { change, dragAmount ->
//                            Logd(TAG, "onHorizontalDrag $dragAmount")
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            coroutineScope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                val velocity = velocityTracker.calculateVelocity().x
//                                Logd(TAG, "velocity: $velocity")
                                if (velocity > 1000f || velocity < -1000f) {
//                                        Logd(TAG, "velocity: $velocity")
//                                        if (velocity > 0) rightSwipeCB?.invoke(vms[index].episode)
//                                        else leftSwipeCB?.invoke(vms[index].episode)
                                    if (velocity > 0) rightSwipeCB?.invoke(vm.episode)
                                    else leftSwipeCB?.invoke(vm.episode)
                                }
                                offsetX.animateTo(targetValue = 0f, animationSpec = tween(500))
                            }
                        }
                    )
                }.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
                    LaunchedEffect(key1 = selectMode, key2 = selectedSize) {
                        vm.isSelected = selectMode && vm.episode in selected
                        Logd(TAG, "LaunchedEffect $index ${vm.isSelected} ${selected.size}")
                    }
                    Column {
                        var yOffset by remember { mutableStateOf(0f) }
                        var draggedIndex by remember { mutableStateOf<Int?>(null) }
                        MainRow(vm, index, isBeingDragged = draggedIndex == index,
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
                        ProgressRow(vm, index)
                    }
                }
            }
        }
        if (selectMode) {
            val buttonColor = MaterialTheme.colorScheme.tertiary
            Row(modifier = Modifier.align(Alignment.TopEnd).width(150.dp).height(45.dp).background(MaterialTheme.colorScheme.tertiaryContainer),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_upward_24), tint = buttonColor, contentDescription = null,
                    modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                    .clickable(onClick = {
                        selected.clear()
                        for (i in 0..longPressIndex) selected.add(vms[i].episode)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_downward_24), tint = buttonColor, contentDescription = null,
                    modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                    .clickable(onClick = {
                        selected.clear()
                        for (i in longPressIndex..<vms.size) selected.add(vms[i].episode)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                var selectAllRes by remember { mutableIntStateOf(R.drawable.ic_select_all) }
                Icon(imageVector = ImageVector.vectorResource(selectAllRes), tint = buttonColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                    .clickable(onClick = {
                        if (selectedSize != vms.size) {
                            selected.clear()
                            for (vm in vms) selected.add(vm.episode)
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
            val scrollState = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                var selectNone by remember { mutableStateOf(false) }
                for (item in EpisodesFilterGroup.entries) {
                    if (item in filtersDisabled) continue
                    if (item.values.size == 2) {
                        Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            var selectedIndex by remember { mutableStateOf(-1) }
                            if (selectNone) selectedIndex = -1
                            LaunchedEffect(Unit) {
                                if (item.values[0].filterId in filter.propertySet) selectedIndex = 0
                                else if (item.values[1].filterId in filter.propertySet) selectedIndex = 1
                            }
                            Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge , color = textColor, modifier = Modifier.padding(end = 10.dp))
                            Spacer(Modifier.weight(0.3f))
                            OutlinedButton(
                                modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (selectedIndex != 0) textColor else Color.Green),
                                onClick = {
                                    if (selectedIndex != 0) {
                                        selectNone = false
                                        selectedIndex = 0
                                        filter.propertySet.add(item.values[0].filterId)
                                        filter.propertySet.remove(item.values[1].filterId)
                                    } else {
                                        selectedIndex = -1
                                        filter.propertySet.remove(item.values[0].filterId)
                                    }
                                    Logd("EpisodeFilterDialog", "filterValues = [${filter.propertySet}]")
                                    onFilterChanged(filter)
                                },
                            ) { Text(text = stringResource(item.values[0].displayName), color = textColor) }
                            Spacer(Modifier.weight(0.1f))
                            OutlinedButton(
                                modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (selectedIndex != 1) textColor else Color.Green),
                                onClick = {
                                    if (selectedIndex != 1) {
                                        selectNone = false
                                        selectedIndex = 1
                                        filter.propertySet.add(item.values[1].filterId)
                                        filter.propertySet.remove(item.values[0].filterId)
                                    } else {
                                        selectedIndex = -1
                                        filter.propertySet.remove(item.values[1].filterId)
                                    }
                                    onFilterChanged(filter)
                                },
                            ) { Text(text = stringResource(item.values[1].displayName), color = textColor) }
                            Spacer(Modifier.weight(0.5f))
                        }
                    } else {
                        Column(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                            val selectedList = remember { MutableList(item.values.size) { mutableStateOf(false)} }
                            var expandRow by remember { mutableStateOf(false) }
                            if (item.name != EpisodesFilterGroup.DURATION.name) {
                                Row {
                                    Text(stringResource(item.nameRes) + ".. :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = textColor,
                                        modifier = Modifier.clickable { expandRow = !expandRow })
                                    var lowerSelected by remember { mutableStateOf(false) }
                                    var higherSelected by remember { mutableStateOf(false) }
                                    Spacer(Modifier.weight(1f))
                                    if (expandRow) Text("<<<", color = if (lowerSelected) Color.Green else buttonColor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable {
                                        val hIndex = selectedList.indexOfLast { it.value }
                                        if (hIndex < 0) return@clickable
                                        if (!lowerSelected) for (i in 0..hIndex) selectedList[i].value = true
                                        else {
                                            for (i in 0..hIndex) selectedList[i].value = false
                                            selectedList[hIndex].value = true
                                        }
                                        lowerSelected = !lowerSelected
                                        for (i in item.values.indices) {
                                            if (selectedList[i].value) filter.propertySet.add(item.values[i].filterId)
                                            else filter.propertySet.remove(item.values[i].filterId)
                                        }
                                        onFilterChanged(filter)
                                    })
                                    Spacer(Modifier.weight(1f))
                                    if (expandRow) Text("X", color = buttonColor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable {
                                        lowerSelected = false
                                        higherSelected = false
                                        for (i in item.values.indices) {
                                            selectedList[i].value = false
                                            filter.propertySet.remove(item.values[i].filterId)
                                        }
                                        onFilterChanged(filter)
                                    })
                                    Spacer(Modifier.weight(1f))
                                    if (expandRow) Text(">>>", color = if (higherSelected) Color.Green else buttonColor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable {
                                        val lIndex = selectedList.indexOfFirst { it.value }
                                        if (lIndex < 0) return@clickable
                                        if (!higherSelected) {
                                            for (i in lIndex..item.values.size - 1) selectedList[i].value = true
                                        } else {
                                            for (i in lIndex..item.values.size - 1) selectedList[i].value = false
                                            selectedList[lIndex].value = true
                                        }
                                        higherSelected = !higherSelected
                                        for (i in item.values.indices) {
                                            if (selectedList[i].value) filter.propertySet.add(item.values[i].filterId)
                                            else filter.propertySet.remove(item.values[i].filterId)
                                        }
                                        onFilterChanged(filter)
                                    })
                                    Spacer(Modifier.weight(1f))
                                }
                            } else {
                                Text(stringResource(item.nameRes) + ".. :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = textColor,
                                    modifier = Modifier.clickable { expandRow = !expandRow })
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
                                                var f = 0
                                                var c = Int.MAX_VALUE
                                                if (floor.isNotBlank() || ceiling.isNotBlank()) {
                                                    f = if (floor.isBlank() || floor.toIntOrNull() == null) 0 else floor.toInt()
                                                    c = if (ceiling.isBlank() || ceiling.toIntOrNull() == null) Int.MAX_VALUE else ceiling.toInt()
                                                    Logd("EpisodeFilterDialog", "f = $f c = $c")
                                                    filter.durationFloor = f * 1000
                                                    filter.durationCeiling = if (c < Int.MAX_VALUE) c * 1000 else c
                                                }
                                                showIcon = false
                                            }))
                                    }
                                }
                            }
                            if (expandRow) NonlazyGrid(columns = 3, itemCount = item.values.size) { index ->
                                if (selectNone) selectedList[index].value = false
                                LaunchedEffect(Unit) {
                                    if (filter != null && item.values[index].filterId in filter.propertySet) selectedList[index].value = true
                                }
                                OutlinedButton(
                                    modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(),
                                    border = BorderStroke(2.dp, if (selectedList[index].value) Color.Green else textColor),
                                    onClick = {
                                        selectNone = false
                                        selectedList[index].value = !selectedList[index].value
                                        if (selectedList[index].value) filter.propertySet.add(item.values[index].filterId)
                                        else filter.propertySet.remove(item.values[index].filterId)
                                        onFilterChanged(filter)
                                    },
                                ) { Text(text = stringResource(item.values[index].displayName), maxLines = 1, color = textColor) }
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

@Composable
fun EpisodeSortDialog(initOrder: EpisodeSortOrder, showKeepSorted: Boolean = false, onDismissRequest: () -> Unit, onSelectionChanged: (EpisodeSortOrder, Boolean) -> Unit) {
    val orderList = remember { EpisodeSortOrder.entries.filterIndexed { index, _ -> index % 2 != 0 } }
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
        Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            val scrollState = rememberScrollState()
            var sortIndex by remember { mutableIntStateOf(initOrder.ordinal/2) }
            var keepSorted by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp).verticalScroll(scrollState)) {
                NonlazyGrid(columns = 2, itemCount = orderList.size) { index ->
                    var dir by remember { mutableStateOf(if (sortIndex == index) initOrder.ordinal % 2 == 0 else true) }
                    OutlinedButton(modifier = Modifier.padding(2.dp), elevation = null, border = BorderStroke(2.dp, if (sortIndex != index) textColor else Color.Green),
                        onClick = {
                            if (sortIndex == index) dir = !dir
                            sortIndex = index
                            val sortOrder = EpisodeSortOrder.entries[2*index + if(dir) 0 else 1]
                            onSelectionChanged(sortOrder, keepSorted)
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
fun DatesFilterDialog(inclPlayed: Boolean = false, from: Long? = null, to: Long? = null, oldestDate: Long, onDismissRequest: ()->Unit, callback: (Long, Long, Boolean) -> Unit) {
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
    var includeMarkedAsPlayed by remember { mutableStateOf(inclPlayed) }
    var timeFilterFrom by remember { mutableLongStateOf(from ?: oldestDate) }
    var timeFilterTo by remember { mutableLongStateOf(to ?: Date().time) }
    var useAllTime by remember { mutableStateOf(false) }
    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
        title = { Text(stringResource(R.string.share_label), style = CustomTextStyles.titleCustom) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeMarkedAsPlayed, onCheckedChange = {
                        includeMarkedAsPlayed = it
//                        if (includeMarkedAsPlayed) {
//                            timeFilterFrom = 0
//                            timeFilterTo = Long.MAX_VALUE
//                        }
                    })
                    Text(stringResource(R.string.statistics_include_marked))
                }
                if (!useAllTime) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.statistics_start_month))
                        MonthYearInput(convertUnixTimeToMonthYear(oldestDate)) { timeFilterFrom = convertMonthYearToUnixTime(it) ?: oldestDate }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.statistics_end_month))
                        MonthYearInput(convertUnixTimeToMonthYear(System.currentTimeMillis())) { timeFilterTo = convertMonthYearToUnixTime(it, false) ?: Date().time }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useAllTime, onCheckedChange = { useAllTime = it })
                    Text(stringResource(R.string.statistics_filter_all_time))
                }
                Text(stringResource(R.string.statistics_speed_not_counted))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (useAllTime) {
                    timeFilterFrom = oldestDate
                    timeFilterTo = Date().time
                }
                callback(timeFilterFrom, timeFilterTo, includeMarkedAsPlayed)
                onDismissRequest()
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
    )
}

@Composable
fun ShareDialog(item: Episode, act: Activity, onDismissRequest: ()->Unit) {
    val PREF_SHARE_EPISODE_START_AT = "prefShareEpisodeStartAt"
    val PREF_SHARE_EPISODE_TYPE = "prefShareEpisodeType"

    val prefs = remember { act.getSharedPreferences("ShareDialog", Context.MODE_PRIVATE) }
    // TODO: ensure hasMedia
//    val hasMedia = remember { item.media != null }
    val hasMedia = remember { true }
    val downloaded = remember { hasMedia && item.downloaded }
    val hasDownloadUrl = remember { hasMedia && item.downloadUrl != null }

    var type = remember { prefs.getInt(PREF_SHARE_EPISODE_TYPE, 1) }
    if ((type == 2 && !hasDownloadUrl) || (type == 3 && !downloaded)) type = 1

    var position by remember { mutableIntStateOf(type) }
    var isChecked by remember { mutableStateOf(false) }
    var ctx = LocalContext.current

    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
        title = { Text(stringResource(R.string.share_label), style = CustomTextStyles.titleCustom) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = position == 1, onClick = { position = 1 })
                    Text(stringResource(R.string.share_dialog_for_social))
                }
                if (hasDownloadUrl) Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = position == 2, onClick = { position = 2 })
                    Text(stringResource(R.string.share_dialog_media_address))
                }
                if (downloaded) Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = position == 3, onClick = { position = 3 })
                    Text(stringResource(R.string.share_dialog_media_file_label))
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isChecked, onCheckedChange = { isChecked = it })
                    Text(stringResource(R.string.share_playback_position_dialog_label))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when(position) {
                    1 -> ShareUtils.shareFeedItemLinkWithDownloadLink(ctx, item, isChecked)
                    2 -> ShareUtils.shareMediaDownloadLink(ctx, item)
                    3 -> ShareUtils.shareFeedItemFile(ctx, item)
                }
                prefs.edit().putBoolean(PREF_SHARE_EPISODE_START_AT, isChecked).putInt(PREF_SHARE_EPISODE_TYPE, position).apply()
                onDismissRequest()
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
    )
}

@Composable
fun SkipDialog(direction: SkipDirection, onDismissRequest: ()->Unit, callBack: (Int)->Unit) {
    val titleRes = if (direction == SkipDirection.SKIP_FORWARD) R.string.pref_fast_forward else R.string.pref_rewind
    var interval by remember { mutableStateOf((if (direction == SkipDirection.SKIP_FORWARD) AppPreferences.fastForwardSecs else AppPreferences.rewindSecs).toString()) }
    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
        title = { Text(stringResource(titleRes), style = CustomTextStyles.titleCustom) },
        text = {
            TextField(value = interval,  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Companion.Number), label = { Text("seconds") }, singleLine = true,
                onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) interval = it })
        },
        confirmButton = {
            TextButton(onClick = {
                if (interval.isNotBlank()) {
                    val value = interval.toInt()
                    if (direction == SkipDirection.SKIP_FORWARD) AppPreferences.fastForwardSecs = value
                    else AppPreferences.rewindSecs = value
                    callBack(value)
                    onDismissRequest()
                }
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
    )
}

enum class SkipDirection {
    SKIP_FORWARD, SKIP_REWIND
}

@Composable
fun ChaptersDialog(media: Episode, onDismissRequest: () -> Unit) {
    val lazyListState = rememberLazyListState()
    val chapters = remember { media.chapters }
    val textColor = MaterialTheme.colorScheme.onSurface
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.Companion.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.chapters_label))
                var currentChapterIndex by remember { mutableIntStateOf(-1) }
                LazyColumn(state = lazyListState, modifier = Modifier.Companion.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chapters.size, key = { index -> chapters[index].start }) { index ->
                        val ch = chapters[index]
                        Row(verticalAlignment = Alignment.Companion.CenterVertically, modifier = Modifier.Companion.fillMaxWidth()) {
//                            if (!ch.imageUrl.isNullOrEmpty()) {
//                                val imgUrl = ch.imageUrl
//                                AsyncImage(model = imgUrl, contentDescription = "imgvCover",
//                                    placeholder = painterResource(R.mipmap.ic_launcher),
//                                    error = painterResource(R.mipmap.ic_launcher),
//                                    modifier = Modifier.width(56.dp).height(56.dp))
//                            }
                            Column(modifier = Modifier.Companion.weight(1f)) {
                                Text(getDurationStringLong(ch.start.toInt()), color = textColor)
                                Text(ch.title ?: "No title", color = textColor, fontWeight = FontWeight.Companion.Bold)
//                                Text(ch.link?: "")
                                val duration = if (index + 1 < chapters.size) chapters[index + 1].start - ch.start
                                else media.duration - ch.start
                                Text(stringResource(R.string.chapter_duration0) + DurationConverter.getDurationStringLocalized(duration), color = textColor)
                            }
                            val playRes = if (index == currentChapterIndex) R.drawable.ic_replay else R.drawable.ic_play_48dp
                            Icon(imageVector = ImageVector.Companion.vectorResource(playRes), tint = textColor, contentDescription = "play button",
                                modifier = Modifier.Companion.width(28.dp).height(32.dp).clickable {
                                    if (status != PlayerStatus.PLAYING) PlaybackService.Companion.playPause()
                                    PlaybackService.Companion.seekTo(ch.start.toInt())
                                    currentChapterIndex = index
                                })
                        }
                    }
                }
            }
        }
    }
}