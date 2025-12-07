package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.automation.playEpisodeAtTime
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.wifiSyncEnabledKey
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.LocalMediaPlayer.Companion.exoPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isPlaying
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.playPause
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.database.addToAssOrActQueue
import ac.mdiq.podcini.storage.database.allowForAutoDelete
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.deleteMedia
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.removeFromAllQueues
import ac.mdiq.podcini.storage.database.removeFromAllQueuesQuiet
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.setPlayState
import ac.mdiq.podcini.storage.database.shelveToFeed
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.Companion.DEFAULT_INTERVALS
import ac.mdiq.podcini.storage.model.Feed.Companion.INTERVAL_UNITS
import ac.mdiq.podcini.storage.model.Feed.Companion.duetime
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeFilter.EpisodesFilterGroup
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.getDurationStringLocalized
import ac.mdiq.podcini.storage.utils.getDurationStringLong
import ac.mdiq.podcini.storage.utils.getDurationStringShort
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.EpisodeActionButton.Companion.playVideoIfNeeded
import ac.mdiq.podcini.ui.screens.SearchBy
import ac.mdiq.podcini.ui.screens.SearchByGrid
import ac.mdiq.podcini.ui.screens.searchEpisodesQuery
import ac.mdiq.podcini.ui.screens.setSearchByAll
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.localDateTimeString
import ac.mdiq.podcini.utils.shareFeedItemFile
import ac.mdiq.podcini.utils.shareFeedItemLinkWithDownloadLink
import ac.mdiq.podcini.utils.shareLink
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.Gravity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date

private const val TAG = "ComposeEpisodes"

@Composable
fun ShareDialog(item: Episode, act: Activity, onDismissRequest: () -> Unit) {
    val PREF_SHARE_EPISODE_START_AT = "prefShareEpisodeStartAt"
    val PREF_SHARE_EPISODE_TYPE = "prefShareEpisodeType"

    val prefs = remember { act.getSharedPreferences("ShareDialog", Context.MODE_PRIVATE) }
    // TODO: ensure hasMedia
//    val hasMedia = remember { item.media != null }
    val hasMedia = remember { true }
    val downloaded = remember { hasMedia && item.downloaded }
    val hasDownloadUrl = remember { hasMedia && item.downloadUrl != null }

    // TODO: what's this
    var type = remember { prefs.getInt(PREF_SHARE_EPISODE_TYPE, 1) }
    if ((type == 2 && !hasDownloadUrl) || (type == 3 && !downloaded)) type = 1

    var position by remember { mutableIntStateOf(type) }
    var isChecked by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

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
                when (position) {
                    1 -> shareFeedItemLinkWithDownloadLink(ctx, item, isChecked)
                    2 -> {
                        if (!item.downloadUrl.isNullOrEmpty()) shareLink(ctx, item.downloadUrl!!)
                        else Logt(TAG, "Episode download url is not valid, ignored.")
                    }
                    3 -> shareFeedItemFile(ctx, item)
                }
                prefs.edit {
                    putBoolean(PREF_SHARE_EPISODE_START_AT, isChecked)
                    putInt(PREF_SHARE_EPISODE_TYPE, position)
                }
                onDismissRequest()
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ChaptersDialog(media: Episode, onDismissRequest: () -> Unit) {
    val lazyListState = rememberLazyListState()
    val chapters = remember { media.chapters }
    val textColor = MaterialTheme.colorScheme.onSurface
    CommonDialogSurface(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.chapters_label))
            var currentChapterIndex by remember { mutableIntStateOf(-1) }
            LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chapters.size, key = { index -> chapters[index].start }) { index ->
                    val ch = chapters[index]
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        //                            if (!ch.imageUrl.isNullOrEmpty()) {
                        //                                val imgUrl = ch.imageUrl
                        //                                AsyncImage(model = imgUrl, contentDescription = "imgvCover",
                        //                                    placeholder = painterResource(R.mipmap.ic_launcher),
                        //                                    error = painterResource(R.mipmap.ic_launcher),
                        //                                    modifier = Modifier.width(56.dp).height(56.dp))
                        //                            }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(getDurationStringLong(ch.start.toInt()), color = textColor)
                            Text(ch.title ?: "No title", color = textColor, fontWeight = FontWeight.Bold)
                            //                                Text(ch.link?: "")
                            val duration = if (index + 1 < chapters.size) chapters[index + 1].start - ch.start
                            else media.duration - ch.start
                            Text(stringResource(R.string.chapter_duration0) + getDurationStringLocalized(duration), color = textColor)
                        }
                        val playRes = if (index == currentChapterIndex) R.drawable.ic_replay else R.drawable.ic_play_48dp
                        Icon(imageVector = ImageVector.vectorResource(playRes), tint = textColor, contentDescription = "play button",
                            modifier = Modifier.width(28.dp).height(32.dp).clickable {
                                if (!isPlaying) playPause()
                                mPlayer?.seekTo(ch.start.toInt())
                                currentChapterIndex = index
                            })
                    }
                }
            }
        }
    }
}

@Composable
fun ChaptersColumn(media: Episode) {
    val chapters = remember { media.chapters }
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val context = LocalContext.current
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.chapters_label))
        var curChapterIndex by remember { mutableIntStateOf(-1) }
        for (index in chapters.indices) {
            val ch = remember(index) { chapters[index] }
//            Text(ch.link?: "")
            Row(modifier = Modifier.clickable(onClick = {
                if (curEpisode == media) {
                    if (!isPlaying) playPause()
                } else {
                    PlaybackStarter(context, media).shouldStreamThisTime(media.fileUrl == null).start()
                    playVideoIfNeeded(context, media)
                }
                mPlayer?.seekTo(ch.start.toInt())
                curChapterIndex = index
            })) {
                Text(getDurationStringLong(ch.start.toInt()), color = buttonColor, modifier = Modifier.padding(end = 5.dp))
                Text(ch.title ?: "No title", color = textColor, fontWeight = if (index == curChapterIndex) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeMarks(episode: Episode?) {
    if (!episode?.marks.isNullOrEmpty()) {
        val context by rememberUpdatedState(LocalContext.current)
        var markToRemove by remember { mutableLongStateOf(0L) }
        if (markToRemove != 0L) {
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { markToRemove = 0L },
                text = { Text(stringResource(R.string.ask_remove_mark, markToRemove)) },
                confirmButton = {
                    TextButton(onClick = {
                        upsertBlk(episode) { it.marks.remove(markToRemove) }
                        markToRemove = 0L
                    }) { Text(stringResource(R.string.confirm_label)) }
                },
                dismissButton = { TextButton(onClick = { markToRemove = 0L }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        Text(stringResource(R.string.marks), style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp))
        FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            episode.marks.forEach { mark ->
                FilterChip(onClick = {
                    if (curEpisode != null && exoPlayer != null && episode.id == curEpisode?.id) {
                        if (!isPlaying) playPause()
                        mPlayer?.seekTo(mark.toInt())
                    }
                    else Logt(TAG, context.getString(R.string.play_mark_msg))
                }, label = { Text(getDurationStringShort(mark, false)) }, selected = false, trailingIcon = { Icon(imageVector = Icons.Filled.Delete, contentDescription = "delete",
                    modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = { markToRemove = mark })) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeClips(episode: Episode?, player: ExoPlayer?) {
    if (!episode?.clips.isNullOrEmpty()) {
        var cliptToRemove by remember { mutableStateOf("") }
        if (cliptToRemove.isNotBlank()) {
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { cliptToRemove = "" },
                text = { Text(stringResource(R.string.ask_remove_clip, cliptToRemove.substringBefore("."))) },
                confirmButton = {
                    TextButton(onClick = {
                        runOnIOScope {
                            val file = episode.getClipFile(cliptToRemove)
                            file.delete()
                            upsert(episode) { it.clips.remove(cliptToRemove) }
                            withContext(Dispatchers.Main) { cliptToRemove = "" }
                        }
                    }) { Text(stringResource(R.string.confirm_label)) }
                },
                dismissButton = { TextButton(onClick = { cliptToRemove = "" }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        Text(stringResource(R.string.clips), style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp))
        FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            episode.clips.forEach { clip ->
                FilterChip(onClick = {
                    val file = episode.getClipFile(clip)
                    if (player != null && file.exists()) {
                        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                        player.prepare()
                        player.play()
                    } else Loge(TAG, "clip file doesn't exist: ${file.path}")
                }, label = { Text(clip.substringBefore(".")) }, selected = false, trailingIcon = { Icon(imageVector = Icons.Filled.Delete, contentDescription = "delete",
                    modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = { cliptToRemove = clip })) })
            }
        }
    }
}

@Composable
fun RelatedEpisodesDialog(episode: Episode, onDismissRequest: () -> Unit) {
    // TODO: somehow, episode is not updated after unrelate
//    var episode = realm.query(Episode::class, "id == ${episode.id}").first().find() ?: return

    AlertDialog(properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth().padding(5.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },  confirmButton = {},
        text = {
            EpisodeLazyColumn(LocalContext.current, episode.related.toList(), layoutMode = LayoutMode.FeedTitle.ordinal, forceFeedImage = true,
                actionButton_ = { EpisodeActionButton(it) },
                actionButtonCB = {e1, _ ->
                    runOnIOScope {
                        realm.write {
                            val es = query(Episode::class, "id IN $0", listOf(episode.id, e1.id)).find()
                            if (es.size == 2) {
                                es[0].related.remove(es[1])
                                es[1].related.remove(es[0])
                            }
                        }
                        //                        episode = realm.query(Episode::class, "id == ${episode.id}").first().find() ?: return@runOnIOScope
//                        Logd("RelatedEpisodesDialog", "episode.related: ${episode.related.size}")
//                        withContext(Dispatchers.Main) {
////                            vmsr.clear()
////                            for (e in episode.related) vmsr.add(EpisodeVM(e, TAG))
//                        }
                    }
                } ) },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } } )
}

@Composable
fun ChooseRatingDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    CommonDialogSurface(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (rating in Rating.entries.reversed()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)
                    .clickable {
                        runOnIOScope { for (e in selected) upsert(e) { it.rating = rating.code } }
                        onDismissRequest()
                    }) {
                    Icon(imageVector = ImageVector.vectorResource(id = rating.res), "")
                    Text(rating.name, Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
fun AddCommentDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    var editCommentText by remember { mutableStateOf(TextFieldValue("") ) }
    CommentEditingDialog(textState = editCommentText, autoSave = false, onTextChange = { editCommentText = it }, onDismissRequest = { onDismissRequest() },
        onSave = {
            runOnIOScope { for (e in selected) upsert(e) { it.addComment(editCommentText.text) } }
        })
}

@Composable
fun PlayStateDialog(selected: List<Episode>, onDismissRequest: () -> Unit, futureCB: (EpisodeState)->Unit, ignoreCB: ()->Unit) {
    val context = LocalContext.current
    CommonDialogSurface(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (state in EpisodeState.entries.reversed()) {
                if (state.userSet) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp).clickable {
                        when (state) {
                            EpisodeState.IGNORED -> ignoreCB()
                            EpisodeState.AGAIN, EpisodeState.LATER -> futureCB(state)
                            else -> runOnIOScope {
                                for (e in selected) {
                                    val hasAlmostEnded = e.hasAlmostEnded()
                                    var item_ = setPlayState(state, e, hasAlmostEnded, false)
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
                                                item_ = deleteMedia(context, item_)
                                                if (getPref(AppPrefs.prefDeleteRemovesFromQueue, true)) removeFromAllQueues(listOf(item_))
                                            } else if (getPref(AppPrefs.prefRemoveFromQueueMarkedPlayed, true)) removeFromAllQueues(listOf(item_))
                                            if (item_.feed?.isLocalFeed != true && (isProviderConnected || wifiSyncEnabledKey)) { // not all items have media, Gpodder only cares about those that do
                                                if (isProviderConnected) {
                                                    val actionPlay: EpisodeAction = EpisodeAction.Builder(item_, EpisodeAction.PLAY).currentTimestamp().started(item_.duration / 1000).position(item_.duration / 1000).total(item_.duration / 1000).build()
                                                    SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, actionPlay)
                                                }
                                            }
                                        }
                                        EpisodeState.QUEUE -> if (item_.feed?.queue != null) addToAssOrActQueue(e, e.feed?.queue)
                                        else -> {}
                                    }
                                    //                                        vm.updateVMFromDB()
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

@Composable
fun PutToQueueDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    val queues = realm.query(PlayQueue::class).sort("name").find()
    CommonDialogSurface(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            var removeChecked by remember { mutableStateOf(false) }
            var toQueue by remember { mutableStateOf(actQueue) }
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
                            selected.forEach { e -> if (actQueue.contains(e)) toRemoveCur.add(e) }
                            selected.forEach { e ->
                                for (q in queues) {
                                    if (q.contains(e)) {
                                        toRemove.add(e.id)
                                        break
                                    }
                                }
                            }
                            if (toRemove.isNotEmpty()) removeFromAllQueuesQuiet(toRemove.toList(), false)
                            if (toRemoveCur.isNotEmpty()) EventFlow.postEvent(FlowEvent.QueueEvent.removed(toRemoveCur))
                        }
                        selected.forEach { e -> addToAssOrActQueue(e, toQueue) }
                    }
                    onDismissRequest()
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }
}

@Composable
fun ShelveDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    val synthetics = realm.query(Feed::class).query("id >= 100 && id <= 1000").find()
    CommonDialogSurface(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
                    runOnIOScope { shelveToFeed(selected, toFeed!!, removeChecked) }
                    onDismissRequest()
                }) { Text(stringResource(R.string.confirm_label)) }
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

    CommonDialogSurface(onDismissRequest = onDismissRequest) {
        if (feed == null) Text(stringResource(R.string.not_erase_message), modifier = Modifier.padding(10.dp))
        else Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message + ": ${selected.size}")
            Text(stringResource(R.string.reason_to_delete_msg))
            BasicTextField(value = textState, onValueChange = { textState = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor), modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
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
                                    url != null && url.startsWith("content://") -> DocumentFile.fromSingleUri(context, url.toUri())?.delete()
                                    url != null -> {
                                        val path = url.toUri().path
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

@Composable
fun AlarmEpisodeDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface
    var showIcon by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("") }
    val hm = remember { (if (startTime.contains(":")) startTime.split(":") else listOf("", "")).toMutableList() }
    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
        title = { Text(stringResource(R.string.alarm_start_time), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var hour by remember { mutableIntStateOf(hm[0].toIntOrNull() ?: 0) }
                    var minute by remember { mutableIntStateOf(hm[1].toIntOrNull() ?: 0) }
                    NumberEditor(hour, stringResource(R.string.time_hours), nz = true, instant = true, modifier = Modifier.weight(0.4f)) {
                        hour = it
                        hm[0] = it.toString()
                        showIcon = true
                    }
                    NumberEditor(minute, stringResource(R.string.time_minutes), nz = true, instant = true,  modifier = Modifier.weight(0.4f)) {
                        minute = it
                        hm[1] = it.toString()
                        showIcon = true
                    }
                }
                Text(stringResource(R.string.alarm_start_time_sum), color = textColor, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (showIcon) TextButton(onClick = {
                if (hm[0].isNotBlank() || hm[1].isNotBlank()) {
                    val hour = if (hm[0].isBlank()) 0 else (hm[0].toIntOrNull() ?: 0)
                    val minute = if (hm[1].isBlank()) 0 else (hm[1].toIntOrNull() ?: 0)
                    val targetTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                    }
                    val currentTime = Calendar.getInstance()
                    if (targetTime.before(currentTime)) targetTime.add(Calendar.DAY_OF_MONTH, 1)
                    Logd(TAG, "start time: $targetTime")
                    playEpisodeAtTime(context, targetTime.timeInMillis, selected[0])
                }
                onDismissRequest()
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
    )
}

@Composable
fun IgnoreEpisodesDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    val message = stringResource(R.string.ignore_episodes_confirmation_msg)
    val textColor = MaterialTheme.colorScheme.onSurface
    var textState by remember { mutableStateOf(TextFieldValue("")) }

    CommonDialogSurface(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message + ": ${selected.size}")
            Text(stringResource(R.string.reason_to_delete_msg))
            BasicTextField(value = textState, onValueChange = { textState = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor), modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
            Button(onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (e in selected) {
                            val hasAlmostEnded = e.hasAlmostEnded()
                            val item_ = setPlayState(EpisodeState.IGNORED, e, hasAlmostEnded, false)
                            Logd("IgnoreEpisodesDialog", "item_: ${item_.title} ${item_.playState}")
                            upsert(item_) { it.addComment("Reason to ignore:\n${textState.text}") }
                            //                                vm.updateVMFromDB()
                        }
                    } catch (e: Throwable) { Logs("IgnoreEpisodesDialog", e) }
                }
                onDismissRequest()
            }) { Text(stringResource(R.string.confirm_label)) }
        }
    }
}

@Composable
fun FutureStateDialog(selected: List<Episode>, state: EpisodeState, onDismissRequest: () -> Unit) {
    val message = stringResource(R.string.repeat_episodes_msg)
    CommonDialogSurface(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message + ": ${selected.size}")
            var intervals = remember { if (selected.size == 1) selected[0].feed?.repeatIntervals?.toMutableList() else null }
            if (intervals.isNullOrEmpty()) intervals = DEFAULT_INTERVALS.toMutableList()
            val units = INTERVAL_UNITS.map { stringResource(it) }
            var sel by remember { mutableIntStateOf(2) }
            for (i in intervals.indices) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = i==sel, onClick = { sel = i })
                    NumberEditor(intervals[i], label = units[i], nz = false, instant = true, modifier = Modifier.width(150.dp)) { intervals[i] = it }
                }
            }
            Button(onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dueTime = duetime(intervals[sel], sel)
                        for (e in selected) {
                            val hasAlmostEnded = e.hasAlmostEnded()
                            val item_ = setPlayState(state, e, hasAlmostEnded, false)
                            Logd("AgainEpisodesDialog", "item_: ${item_.title} ${item_.playState} ${Date(dueTime)}")
                            upsert(item_) { it.repeatTime = dueTime }
                            //                                vm.updateVMFromDB()
                        }
                    } catch (e: Throwable) { Logs("AgainEpisodesDialog", e) }
                }
                onDismissRequest()
            }) { Text(stringResource(R.string.confirm_label)) }
        }
    }
}

@Composable
fun EpisodesFilterDialog(filter_: EpisodeFilter, disabledSet: MutableSet<EpisodesFilterGroup> = mutableSetOf(), showAndOr: Boolean = true,
                         onDismissRequest: () -> Unit, onFilterChanged: (EpisodeFilter) -> Unit) {
    //    val filterValuesSet = remember {  filter.propertySet ?: mutableSetOf() }
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
        Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            val buttonColor = MaterialTheme.colorScheme.tertiary
            val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                var filter by remember { mutableStateOf(filter_.apply { if (andOr.isBlank()) andOr = "AND" }) }
                var andOr by remember { mutableStateOf(filter.andOr.ifBlank { "AND" }) }
                if (showAndOr) {
                    Row(modifier = Modifier.padding(start = 5.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Absolute.Left, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.join_categories_with) + " :", style = MaterialTheme.typography.bodyMedium , color = textColor, modifier = Modifier.padding(end = 10.dp))
                        Spacer(Modifier.width(30.dp))
                        OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (andOr == "OR") buttonColor else buttonAltColor),
                            onClick = {
                                andOr = "AND"
                                filter.andOr = andOr
                                onFilterChanged(filter)
                            },
                        ) { Text(text = "AND", color = textColor) }
                        Spacer(Modifier.width(20.dp))
                        OutlinedButton(modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (andOr == "AND") buttonColor else buttonAltColor),
                            onClick = {
                                andOr = "OR"
                                filter.andOr = andOr
                                onFilterChanged(filter)
                            },
                        ) { Text(text = "OR", color = textColor) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
                }
                var selectNone by remember { mutableStateOf(false) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    var expandRow by remember { mutableStateOf(false) }
                    var queryText by remember { mutableStateOf(filter.extractText()) }
                    var showSearchBy by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { setSearchByAll() }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                        Text(stringResource(R.string.text_label) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = if (queryText.isBlank()) buttonColor else buttonAltColor, modifier = Modifier.clickable { expandRow = !expandRow })
                        Spacer(Modifier.width(20.dp))
                        if (expandRow) Text(stringResource(R.string.show_criteria), color = buttonColor, modifier = Modifier.clickable(
                            onClick = {showSearchBy = !showSearchBy}
                        ))
                    }
                    if (expandRow) {
                        SearchBarRow(R.string.search_hint, defaultText = queryText, modifier = Modifier.padding(start = 10.dp)) { query ->
                            Logd(TAG, "SearchBarRow cb query: $query")
                            if (query.isNotBlank()) {
                                selectNone = false
                                val queryWords = (if (query.contains(",")) query.split(",").map { it.trim() } else query.split("\\s+".toRegex())).dropWhile { it.isEmpty() }
                                val queryString = searchEpisodesQuery(0L, queryWords)
                                Logd(TAG, "SearchBarRow cb queryString: $queryString")
                                filter.addTextQuery(queryString)
                            } else filter.addTextQuery("")
                            queryText = query
                            onFilterChanged(filter)
                        }
                        if (showSearchBy) SearchByGrid(setOf(SearchBy.AUTHOR))
                    }
                }
                if (appAttribs.episodeTags.isNotEmpty()) {
                    val tagList = remember { appAttribs.episodeTags.toList().sorted() }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val selectedList = remember { MutableList(tagList.size) { mutableStateOf(false) } }
                        val tagsSel = remember { mutableStateListOf<String>() }
                        var tagsFull by remember { mutableStateOf(tagsSel.size == appAttribs.episodeTags.size) }
                        var expandRow by remember { mutableStateOf(false) }
                        Row(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                            Text(stringResource(R.string.tags_label) + "… :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = if (tagsFull) buttonColor else buttonAltColor, modifier = Modifier.clickable { expandRow = !expandRow })
                            if (expandRow) {
                                val cb = {
                                    for (i in tagList.indices) {
                                        if (selectedList[i].value) filter.addTag(tagList[i])
                                        else filter.removeTag(tagList[i])
                                    }
                                    onFilterChanged(filter)
                                }
                                SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                            }
                        }
                        if (expandRow) ScrollRowGrid(columns = 3, itemCount = tagList.size, modifier = Modifier.padding(start = 10.dp)) { index ->
                            LaunchedEffect(Unit) { if (filter.containsTag(tagList[index])) selectedList[index].value = true }
                            OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                onClick = {
                                    selectNone = false
                                    selectedList[index].value = !selectedList[index].value
                                    if (selectedList[index].value) filter.addTag(tagList[index])
                                    else filter.removeTag(tagList[index])
                                    onFilterChanged(filter)
                                },
                            ) { Text(text = tagList[index], maxLines = 1, color = textColor) }
                        }
                    }
                }
                for (item in EpisodesFilterGroup.entries) {
                    if (item in disabledSet) continue
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
                                        filter.add(item.properties[0].filterId)
                                        filter.remove(item.properties[1].filterId)
                                    } else {
                                        selectedIndex = -1
                                        filter.remove(item.properties[0].filterId)
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
                                        filter.add(item.properties[1].filterId)
                                        filter.remove(item.properties[0].filterId)
                                    } else {
                                        selectedIndex = -1
                                        filter.remove(item.properties[1].filterId)
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
                                            var floor by remember { mutableIntStateOf(filter.durationFloor/1000) }
                                            var ceiling by remember { mutableIntStateOf(filter.durationCeiling/1000) }
                                            NumberEditor(floor, stringResource(R.string.floor_seconds), nz = true, instant = true, modifier = Modifier.weight(0.4f)) {
                                                floor = it
                                                showIcon = true
                                            }
                                            NumberEditor(ceiling, stringResource(R.string.ceiling_seconds), nz = true, instant = true, modifier = Modifier.weight(0.4f)) {
                                                ceiling = it
                                                showIcon = true
                                            }
                                            if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                                                modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                                    val f = floor
                                                    val c = if (ceiling == 0) Int.MAX_VALUE else ceiling
                                                    filter.durationFloor = f * 1000
                                                    filter.durationCeiling = if (c < Int.MAX_VALUE) c * 1000 else c
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
                                                    if (selectedList[i].value) filter.add(item.properties[i].filterId)
                                                    else filter.remove(item.properties[i].filterId)
                                                }
                                                onFilterChanged(filter)
                                            }
                                            SelectLowerAllUpper(selectedList, lowerCB = cb, allCB = cb, upperCB = cb)
                                        }
                                    }
                                }
                            }
                            if (expandRow) ScrollRowGrid(columns = 3, itemCount = item.properties.size) { index ->
                                if (selectNone) selectedList[index].value = false
                                LaunchedEffect(Unit) {
                                    if (item.properties[index].filterId in filter.propertySet) selectedList[index].value = true
                                }
                                OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (selectedList[index].value) buttonAltColor else buttonColor),
                                    onClick = {
                                        selectNone = false
                                        selectedList[index].value = !selectedList[index].value
                                        if (selectedList[index].value) {
                                            filter.add(item.properties[index].filterId)
                                            if (item.exclusive) for (i in selectedList.indices) {
                                                if (i != index) {
                                                    selectedList[i].value = false
                                                    filter.remove(item.properties[i].filterId)
                                                }
                                            }
                                        } else filter.remove(item.properties[index].filterId)
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

@Composable
fun EpisodeSortDialog(initOrder: EpisodeSortOrder?, includeConditionals: List<EpisodeSortOrder> = listOf(), onDismissRequest: () -> Unit, onSelectionChanged: (EpisodeSortOrder?) -> Unit) {
    val orderList = remember { EpisodeSortOrder.entries.filterIndexed { index, f -> index % 2 != 0 && (!f.conditional || f in includeConditionals || f in gearbox.includeExtraSort()) } }
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
        Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(250.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            var order by remember { mutableStateOf(initOrder) }
            var sortIndex by remember { mutableIntStateOf(if (initOrder != null) initOrder.ordinal/2 else -1) }
            Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp).verticalScroll(rememberScrollState())) {
                ScrollRowGrid(columns = 2, itemCount = orderList.size) { index ->
                    var dir by remember { mutableStateOf(if (sortIndex == index) initOrder!!.ordinal % 2 == 0 else true) }
                    OutlinedButton(modifier = Modifier.padding(2.dp), elevation = null, border = BorderStroke(2.dp, if (sortIndex != index) buttonColor else buttonAltColor),
                        onClick = {
                            if (sortIndex == index) dir = !dir
                            sortIndex = index
                            order = EpisodeSortOrder.entries[2 * index + if (dir) 0 else 1]
                            onSelectionChanged(order)
                        }
                    ) { Text(text = stringResource(orderList[index].res) + if (dir) "\u00A0▲" else "\u00A0▼", color = textColor, maxLines = 1) }
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
                sanitized.length > 7 -> sanitized.take(7)
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
