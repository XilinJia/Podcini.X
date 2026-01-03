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
import ac.mdiq.podcini.storage.database.addToAssQueue
import ac.mdiq.podcini.storage.database.addToQueue
import ac.mdiq.podcini.storage.database.allowForAutoDelete
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.deleteMedia
import ac.mdiq.podcini.storage.database.eraseEpisodes
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.removeFromAllQueues
import ac.mdiq.podcini.storage.database.removeFromAllQueuesQuiet
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.shelveToFeed
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.database.upsertBlkEmb
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.Companion.DEFAULT_INTERVALS
import ac.mdiq.podcini.storage.model.Feed.Companion.INTERVAL_UNITS
import ac.mdiq.podcini.storage.model.Feed.Companion.duetime
import ac.mdiq.podcini.storage.model.Timer
import ac.mdiq.podcini.storage.model.Todo
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeFilter.EpisodesFilterGroup
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.fromCode
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.durationStringFull
import ac.mdiq.podcini.storage.utils.getDurationStringLocalized
import ac.mdiq.podcini.storage.utils.getDurationStringShort
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.EpisodeActionButton.Companion.playVideoIfNeeded
import ac.mdiq.podcini.ui.screens.SearchBy
import ac.mdiq.podcini.ui.screens.SearchByGrid
import ac.mdiq.podcini.ui.screens.searchEpisodesQuery
import ac.mdiq.podcini.ui.screens.setSearchByAll
import ac.mdiq.podcini.ui.utils.ShownotesWebView
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.formatDateTimeFlex
import ac.mdiq.podcini.utils.fullDateTimeString
import ac.mdiq.podcini.utils.shareFeedItemFile
import ac.mdiq.podcini.utils.shareFeedItemLinkWithDownloadLink
import ac.mdiq.podcini.utils.shareLink
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.Gravity
import androidx.collection.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ComposeEpisodes"

@Composable
fun ShareDialog(item: Episode, act: Activity, onDismissRequest: () -> Unit) {
    val PREF_SHARE_EPISODE_START_AT = "prefShareEpisodeStartAt"
    val PREF_SHARE_EPISODE_TYPE = "prefShareEpisodeType"

    val prefs = remember { act.getSharedPreferences("ShareDialog", Context.MODE_PRIVATE) }
    val hasMedia = remember { true }
    val downloaded = remember { hasMedia && item.downloaded }
    val hasDownloadUrl = remember { hasMedia && item.downloadUrl != null }

    var position by remember { mutableIntStateOf(run {
        val type =prefs.getInt(PREF_SHARE_EPISODE_TYPE, 1)
        if ((type == 2 && !hasDownloadUrl) || (type == 3 && !downloaded)) 1 else type
    }) }

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
    CommonPopupCard(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val lazyListState = rememberLazyListState()
            val chapters = remember { media.chapters }
            val textColor = MaterialTheme.colorScheme.onSurface
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
                            Text(durationStringFull(ch.start.toInt()), color = textColor)
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
fun DateTimeEditor() {

}

@Composable
fun TodoDialog(episode: Episode, todo: Todo? = null, onDismissRequest: () -> Unit) {
    CommonDialogSurface(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            var title by remember { mutableStateOf(TextFieldValue(todo?.title ?: "")) }
            BasicTextField(value = title, onValueChange = { title = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor), modifier = Modifier.fillMaxWidth().height(40.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
            var addNote by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = addNote, onCheckedChange = { addNote = it })
                Text(text = stringResource(R.string.add_notes), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 10.dp))
            }
            var note by remember { mutableStateOf(TextFieldValue(todo?.note ?: "")) }
            if (addNote) BasicTextField(value = note, onValueChange = { note = it }, textStyle = TextStyle(fontSize = 12.sp, color = textColor), modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
            val sysTime = remember { System.currentTimeMillis() }
            var dueTime by remember { mutableLongStateOf(0) }
            var addDueTime by remember { mutableStateOf((todo?.dueTime ?: 0) > 0) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = addDueTime, onCheckedChange = {
                    addDueTime = it
                    if (todo != null) {
                        dueTime = if (addDueTime) todo.dueTime
                        else 0
                    }
                })
                Text(text = stringResource(R.string.set_due_time), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 10.dp))
            }
            val zdt by remember(addDueTime) { mutableStateOf(Instant.ofEpochMilli(
                if (todo != null) {
                    if (todo.dueTime > 0) todo.dueTime
                    else if (addDueTime) sysTime
                    else 0
                } else sysTime
            ).atZone(ZoneId.systemDefault())) }
            var year by remember(zdt) { mutableIntStateOf(zdt.year) }
            var month by remember(zdt) { mutableIntStateOf(zdt.monthValue) }
            var date by remember(zdt) { mutableIntStateOf(zdt.dayOfMonth) }
            var hour by remember(zdt) { mutableIntStateOf(zdt.hour) }
            var minute by remember(zdt) { mutableIntStateOf(zdt.minute) }
            if (addDueTime) {
                Text(stringResource(R.string.year) + " " + stringResource(R.string.month) + " " + stringResource(R.string.date))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberEditor(year, "", nz = true, instant = true, modifier = Modifier.weight(0.5f)) { year = it }
                    NumberEditor(month, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { month = it }
                    NumberEditor(date, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { date = it }
                }
                Text(stringResource(R.string.hour) + " " + stringResource(R.string.minute))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberEditor(hour, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { hour = it }
                    NumberEditor(minute, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { minute = it }
                }
            }
            Button(onClick = {
                runOnIOScope {
                    try {
                        if (addDueTime) dueTime = LocalDateTime.of(year, month, date, hour, minute, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        if (todo == null) {
                            val todo_ = todo ?: Todo()
                            todo_.id = sysTime
                            todo_.title = title.text
                            todo_.note = note.text
                            todo_.dueTime = dueTime
                            upsert(episode) { it.todos.add(todo_) }
                        } else upsertBlkEmb(todo) { todo_ ->
                            todo_.title = title.text
                            todo_.note = note.text
                            todo_.dueTime = dueTime
                        }
                        if (dueTime > 0) playEpisodeAtTime(dueTime, episode.id)
                    } catch (e: Throwable) { Loge(TAG, "editing Todo error: ${e.message}")}
                }
                onDismissRequest()
            }) { Text(stringResource(R.string.confirm_label)) }
        }
    }
}

val webDataCache = LruCache<Long, String>(10)

@Composable
fun EpisodeDetails(episode: Episode, episodeFlow: Flow<SingleQueryChange<Episode>>, fetchWebdata: Boolean = true) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    var webviewData by remember { mutableStateOf<String?>("") }
    var playerLocal: ExoPlayer? by remember { mutableStateOf(null) }

    var showEditComment by remember { mutableStateOf(false) }
    var showTagsSettingDialog by remember { mutableStateOf(false) }
    var showTodoDialog by remember { mutableStateOf(false) }
    var onTodo by remember { mutableStateOf<Todo?>(null) }

    if (showEditComment) {
        var commentText by remember { mutableStateOf(TextFieldValue(episode.compileCommentText())) }
        CommentEditingDialog(textState = commentText, onTextChange = { commentText = it }, onDismissRequest = { showEditComment = false},
            onSave = { runOnIOScope { upsert(episode) { it.addComment(commentText.text, false) } } })
    }
    if (showTagsSettingDialog) TagSettingDialog(TagType.Episode, episode.tags, onDismiss = { showTagsSettingDialog = false }) { tags ->
        runOnIOScope { upsert(episode) {
            it.tags.clear()
            it.tags.addAll(tags)
        } }
    }
    if (showTodoDialog) TodoDialog(episode, onTodo) { showTodoDialog = false}

    LaunchedEffect(episode) {
        Logd(TAG, "LaunchedEffect(episode, episodeId)")
        if (fetchWebdata) {
            webviewData = webDataCache[episode.id]
            if (webviewData.isNullOrBlank()) {
                Logd(TAG, "description: ${episode.description}")
                scope.launch(Dispatchers.IO) {
                    episode.let {
                        webviewData = gearbox.buildWebviewData(context, it)
                        if (webviewData != null) webDataCache.put(it.id, webviewData!!)
                    }
                }
            }
        }
        if (episode.clips.isNotEmpty()) {
            if (playerLocal == null) playerLocal = ExoPlayer.Builder(context).build()
        } else {
            playerLocal?.release()
            playerLocal = null
        }
    }

    val todosFlow = remember(episodeFlow) { episodeFlow.map { ec -> ec.obj?.todos?.toList() ?: emptyList() } }
    val todos by todosFlow.collectAsStateWithLifecycle(initialValue = listOf())

    var show by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    Row(modifier = Modifier.padding(start = 15.dp, end = 10.dp, top = 10.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Todos:", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.clickable {
            onTodo = null
            showTodoDialog = true
        })
        if (todos.isNotEmpty()) {
            Spacer(Modifier.width(50.dp))
            Checkbox(checked = show, onCheckedChange = { show = it })
            Text(text = stringResource(R.string.show), style = MaterialTheme.typography.bodyLarge.merge())
            if (show) {
                Spacer(Modifier.width(50.dp))
                Checkbox(checked = showDone, onCheckedChange = { showDone = it })
                Text(text = stringResource(R.string.show_done), style = MaterialTheme.typography.bodyLarge.merge())
            }
        }
    }
    if (show && todos.isNotEmpty()) Column(modifier = Modifier.padding(start = 20.dp).fillMaxWidth()) {
        val localTime = remember { System.currentTimeMillis() }
        for (todo in todos) {
            var done by remember(todo) { mutableStateOf(todo.completed) }
            if (!done || showDone) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = done, onCheckedChange = {
                        done = it
                        upsertBlkEmb(todo) { todo -> todo.completed = done }
                    })
                    Text(text = todo.title, style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.clickable(onClick = {
                        onTodo = todo
                        showTodoDialog = true
                    }))
                    Spacer(Modifier.weight(1f))
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), contentDescription = "delete", modifier = Modifier.padding(end = 15.dp).clickable(onClick = { upsertBlk(episode) { it.todos.remove(todo) } }))
                }
                if (todo.dueTime > 0) {
                    val dueText by remember(todo.dueTime) { mutableStateOf("D:" + formatDateTimeFlex(Date(todo.dueTime))) }
                    val bgColor = if (localTime > todo.dueTime) Color.Cyan else MaterialTheme.colorScheme.surface
                    Text(dueText, color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp).background(bgColor))
                }
            }
        }
    }

    Text("Tags: ${episode.tagsAsString}", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showTagsSettingDialog = true })
    Text(stringResource(R.string.my_opinion_label) + if (episode.comment.isBlank()) " (Add)" else "", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showEditComment = true })
    Text(episode.comment, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 5.dp))

    if (episode.marks.isNotEmpty()) {
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

    if (episode.clips.isNotEmpty()) {
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
                    if (playerLocal != null && file.exists()) {
                        playerLocal!!.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                        playerLocal!!.prepare()
                        playerLocal!!.play()
                    } else Loge(TAG, "clip file doesn't exist: ${file.path}")
                }, label = { Text(clip.substringBefore(".")) }, selected = false, trailingIcon = { Icon(imageVector = Icons.Filled.Delete, contentDescription = "delete",
                    modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = { cliptToRemove = clip })) })
            }
        }
    }

    //                    if (!episode?.chapters.isNullOrEmpty()) Text(stringResource(id = R.string.chapters_label), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable(onClick = { showChaptersDialog = true }))
    if (episode.chapters.isNotEmpty()) {
        val chapters = remember { episode.chapters }
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
                    if (curEpisode == episode) {
                        if (!isPlaying) playPause()
                    } else {
                        PlaybackStarter(context, episode).shouldStreamThisTime(episode.fileUrl == null).start()
                        playVideoIfNeeded(context, episode)
                    }
                    mPlayer?.seekTo(ch.start.toInt())
                    curChapterIndex = index
                })) {
                    Text(durationStringFull(ch.start.toInt()), color = buttonColor, modifier = Modifier.padding(end = 5.dp))
                    Text(ch.title ?: "No title", color = textColor, fontWeight = if (index == curChapterIndex) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
    fun generateWebViewStyle(context: Context, colorPrimary: Color, colorAccent: Color, marginDp: Dp, density: Density): String {
        val marginPx = with(density) { marginDp.toPx() }.toInt()
        val styleTemplate = try { context.assets.open("shownotes-style.css").use { stream -> stream.bufferedReader().use { it.readText() } }
        } catch (e: Exception) { "" }
        fun Color.toCssRgba(): String {
            val r = (red * 255).toInt()
            val g = (green * 255).toInt()
            val b = (blue * 255).toInt()
            return "rgba($r, $g, $b, $alpha)"
        }
        return String.format(
            Locale.US,
            styleTemplate,
            colorPrimary.toCssRgba(),
            colorAccent.toCssRgba(),
            marginPx, marginPx, marginPx, marginPx
        )
    }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        ShownotesWebView(context).apply {
            setTimecodeSelectedListener { time: Int -> mPlayer?.seekTo(time) }
            setPageFinishedListener { postDelayed({ }, 50) }    // Restoring the scroll position might not always work
        }
    }, update = { it.loadDataWithBaseURL("https://127.0.0.1", if (webviewData.isNullOrBlank()) "No notes" else webviewData!!, "text/html", "utf-8", "about:blank") })

    if (episode.related.isNotEmpty()) {
        var showTodayStats by remember { mutableStateOf(false) }
        if (showTodayStats) RelatedEpisodesDialog(episode) { showTodayStats = false }
        Text(stringResource(R.string.related), color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 10.dp).clickable(onClick = { showTodayStats = true }))
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
    CommonPopupCard(onDismissRequest = onDismissRequest) {
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
fun PlayStateDialog(selected: List<Episode>, onDismissRequest: () -> Unit, futureCB: (EpisodeState)->Unit, ignoreCB: ()->Unit) {
    val context = LocalContext.current
    CommonPopupCard(onDismissRequest = onDismissRequest) {
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
                                    var item_ = upsert(e) { it.setPlayState(state, hasAlmostEnded) }
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
                                                item_ = deleteMedia(item_)
                                                if (getPref(AppPrefs.prefDeleteRemovesFromQueue, true)) removeFromAllQueues(listOf(item_))
                                            } else if (getPref(AppPrefs.prefRemoveFromQueueMarkedPlayed, true)) removeFromAllQueues(listOf(item_))
                                            if (item_.feed?.isLocalFeed != true && (isProviderConnected || wifiSyncEnabledKey)) { // not all items have media, Gpodder only cares about those that do
                                                if (isProviderConnected) {
                                                    val actionPlay: EpisodeAction = EpisodeAction.Builder(item_, EpisodeAction.PLAY).currentTimestamp().started(item_.duration / 1000).position(item_.duration / 1000).total(item_.duration / 1000).build()
                                                    SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, actionPlay)
                                                }
                                            }
                                        }
                                        EpisodeState.QUEUE -> if (item_.feed?.queue != null) addToAssQueue(listOf(e))
                                        else -> {}
                                    }
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
    CommonPopupCard(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            var removeChecked by remember { mutableStateOf(false) }
            var toQueue by remember { mutableStateOf(actQueue) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(10.dp)) {
                for (q in queuesLive) {
                    FilterChip(label = { Text(q.name) }, onClick = { toQueue = q }, selected = toQueue == q, border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary) )
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
                                for (q in queuesLive) {
                                    if (q.contains(e)) {
                                        toRemove.add(e.id)
                                        break
                                    }
                                }
                            }
                            if (toRemove.isNotEmpty()) removeFromAllQueuesQuiet(toRemove.toList(), false)
                            if (toRemoveCur.isNotEmpty()) EventFlow.postEvent(FlowEvent.QueueEvent.removed(toRemoveCur))
                        }
                        addToQueue(selected, toQueue)
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
    CommonPopupCard(onDismissRequest = onDismissRequest) {
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
    CommonPopupCard(onDismissRequest = onDismissRequest) {
        val message = stringResource(R.string.erase_episodes_confirmation_msg)
        val textColor = MaterialTheme.colorScheme.onSurface
        var textState by remember { mutableStateOf(TextFieldValue("")) }

        if (feed == null) Text(stringResource(R.string.not_erase_message), modifier = Modifier.padding(10.dp))
        else Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message + ": ${selected.size}")
            Text(stringResource(R.string.reason_to_delete_msg))
            BasicTextField(value = textState, onValueChange = { textState = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor), modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
            Button(onClick = {
                CoroutineScope(Dispatchers.IO).launch { eraseEpisodes(selected, textState.text) }
                onDismissRequest()
            }) { Text(stringResource(R.string.confirm_label)) }
        }
    }
}

@Composable
fun EpisodeTimetableDialog(episode: Episode, onDismissRequest: () -> Unit, cb: (Timer)->Unit) {
    CommonDialogSurface(onDismissRequest = onDismissRequest) {
        val timers = remember(appAttribs.timetable) { appAttribs.timetable.filter { it.episodeId == episode.id }.toMutableStateList() }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (timer in timers) {
                Row {
                    Text(fullDateTimeString(timer.triggerTime), modifier = Modifier.clickable(onClick = { cb(timer) }))
                    Spacer(Modifier.width(100.dp))
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "delete", modifier = Modifier.clickable(onClick = {
                        timer.cancel()
                        upsertBlk(appAttribs) { it.timetable.remove(timer) }
                    }))
                }
            }
        }
    }
}

@Composable
fun EditTimerDialog(timer: Timer, onDismissRequest: () -> Unit) {
    CommonPopupCard(onDismissRequest = onDismissRequest) {
        val zdt by remember { mutableStateOf(Instant.ofEpochMilli(timer.triggerTime).atZone(ZoneId.systemDefault())) }
        var year by remember(zdt) { mutableIntStateOf(zdt.year) }
        var month by remember(zdt) { mutableIntStateOf(zdt.monthValue) }
        var date by remember(zdt) { mutableIntStateOf(zdt.dayOfMonth) }
        var hour by remember(zdt) { mutableIntStateOf(zdt.hour) }
        var minute by remember(zdt) { mutableIntStateOf(zdt.minute) }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.year) + " " + stringResource(R.string.month) + " " + stringResource(R.string.date))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberEditor(year, "", nz = true, instant = true, modifier = Modifier.weight(0.5f)) { year = it }
                NumberEditor(month, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { month = it }
                NumberEditor(date, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { date = it }
            }
            Text(stringResource(R.string.hour) + " " + stringResource(R.string.minute))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberEditor(hour, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { hour = it }
                NumberEditor(minute, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { minute = it }
            }
            Button(onClick = {
                runOnIOScope {
                    try {
                        val triggerTime = LocalDateTime.of(year, month, date, hour, minute, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val timer_ = upsertBlkEmb(timer) { it.triggerTime = triggerTime }
                        timer_.reset()
                    } catch (e: Throwable) {
                        Loge(TAG, "editing timer error: ${e.message}")
                    }
                }
                onDismissRequest()
            }) { Text(stringResource(R.string.confirm_label)) }
        }
    }
}

@Composable
fun AddTimerDialog(episode: Episode, onDismissRequest: () -> Unit) {
    CommonPopupCard(onDismissRequest = onDismissRequest) {
        val zdt by remember { mutableStateOf(Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())) }
        var year by remember(zdt) { mutableIntStateOf(zdt.year) }
        var month by remember(zdt) { mutableIntStateOf(zdt.monthValue) }
        var date by remember(zdt) { mutableIntStateOf(zdt.dayOfMonth) }
        var hour by remember(zdt) { mutableIntStateOf(zdt.hour) }
        var minute by remember(zdt) { mutableIntStateOf(zdt.minute) }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.year) + " " + stringResource(R.string.month) + " " + stringResource(R.string.date))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberEditor(year, "", nz = true, instant = true, modifier = Modifier.weight(0.5f)) { year = it }
                NumberEditor(month, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { month = it }
                NumberEditor(date, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { date = it }
            }
            Text(stringResource(R.string.hour) + " " + stringResource(R.string.minute))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberEditor(hour, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { hour = it }
                NumberEditor(minute, "", nz = true, instant = true, modifier = Modifier.weight(0.4f)) { minute = it }
            }
            var isRepeat by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isRepeat, onCheckedChange = { isChecked -> isRepeat = isChecked })
                Text(stringResource(R.string.repeat_current_media))
            }
            Button(onClick = {
                runOnIOScope {
                    try {
                        val triggerTime = LocalDateTime.of(year, month, date, hour, minute, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        playEpisodeAtTime(triggerTime, episode.id, isRepeat)
                    } catch (e: Throwable) { Loge(TAG, "editing timer error: ${e.message}") }
                }
                onDismissRequest()
            }) { Text(stringResource(R.string.confirm_label)) }
        }
    }
}

@Composable
fun IgnoreEpisodesDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    CommonPopupCard(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val message = stringResource(R.string.ignore_episodes_confirmation_msg)
            val textColor = MaterialTheme.colorScheme.onSurface
            var textState by remember { mutableStateOf(TextFieldValue("")) }
            Text(message + ": ${selected.size}")
            Text(stringResource(R.string.reason_to_delete_msg))
            BasicTextField(value = textState, onValueChange = { textState = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor), modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
            Button(onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        realm.write {
                            for (e in selected) {
                                val hasAlmostEnded = e.hasAlmostEnded()
                                findLatest(e)?.let {
                                    it.setPlayState(EpisodeState.IGNORED, hasAlmostEnded)
                                    it.addComment("Reason to ignore:\n${textState.text}")
                                }
                            }
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
    CommonPopupCard(onDismissRequest = onDismissRequest) {
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
                        realm.write {
                            for (e in selected) {
                                val hasAlmostEnded = e.hasAlmostEnded()
                                findLatest(e)?.let {
                                    it.setPlayState(state, hasAlmostEnded)
                                    it.repeatTime = dueTime
                                }
                            }
                        }
                    } catch (e: Throwable) { Logs("AgainEpisodesDialog", e) }
                }
                onDismissRequest()
            }) { Text(stringResource(R.string.confirm_label)) }
        }
    }
}

@Composable
fun EpisodesFilterDialog(filter_: EpisodeFilter, disabledSet: MutableSet<EpisodesFilterGroup> = mutableSetOf(), showAndOr: Boolean = true, onDismissRequest: () -> Unit, onFilterChanged: (EpisodeFilter) -> Unit) {
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
                if (appAttribs.episodeTagSet.isNotEmpty()) {
                    val tagList = remember { appAttribs.episodeTagSet.toList().sorted() }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val selectedList = remember { MutableList(tagList.size) { mutableStateOf(false) } }
                        val tagsSel = remember { mutableStateListOf<String>() }
                        var tagsFull by remember { mutableStateOf(tagsSel.size == appAttribs.episodeTagSet.size) }
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
fun EpisodeSortDialog(initOrder: EpisodeSortOrder, includeConditionals: List<EpisodeSortOrder> = listOf(), onDismissRequest: () -> Unit, onSelectionChanged: (EpisodeSortOrder?) -> Unit) {
    val orderList = remember { EpisodeSortOrder.entries.filterIndexed { index, f -> index % 2 != 0 && (!f.conditional || f in includeConditionals || f in gearbox.includeExtraSort()) } }
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
        Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(250.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            var order by remember { mutableStateOf(initOrder) }
            var sortIndex by remember { mutableIntStateOf(orderList.indexOfFirst { it.code == order.code || it.code == order.code+1 }) }
            Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp).verticalScroll(rememberScrollState())) {
                ScrollRowGrid(columns = 2, itemCount = orderList.size) { index ->
                    var dir by remember { mutableStateOf(order.code != orderList[sortIndex].code) }
                    OutlinedButton(modifier = Modifier.padding(2.dp), elevation = null, border = BorderStroke(2.dp, if (sortIndex != index) buttonColor else buttonAltColor),
                        onClick = {
                            if (sortIndex == index) dir = !dir
                            sortIndex = index
                            order = fromCode(orderList[index].code - if (dir) 1 else 0)
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
