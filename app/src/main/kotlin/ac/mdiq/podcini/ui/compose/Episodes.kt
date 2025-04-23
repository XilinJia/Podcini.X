package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.LocalMediaPlayer.Companion.exoPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.playPause
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.storage.database.Episodes.getClipFile
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLong
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringShort
import ac.mdiq.podcini.ui.actions.NullZapActionButton
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.ShareUtils
import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                when (position) {
                    1 -> ShareUtils.shareFeedItemLinkWithDownloadLink(ctx, item, isChecked)
                    2 -> ShareUtils.shareMediaDownloadLink(ctx, item)
                    3 -> ShareUtils.shareFeedItemFile(ctx, item)
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
                                    if (status != PlayerStatus.PLAYING) playPause()
                                    mPlayer?.seekTo(ch.start.toInt())
                                    currentChapterIndex = index
                                })
                        }
                    }
                }
            }
        }
    }
}

//@Composable
//fun SkipDialog(direction: SkipDirection, onDismissRequest: () -> Unit, callBack: (Int) -> Unit) {
//    val titleRes = if (direction == SkipDirection.SKIP_FORWARD) R.string.pref_fast_forward else R.string.pref_rewind
//    var interval by remember { mutableStateOf((if (direction == SkipDirection.SKIP_FORWARD) AppPreferences.fastForwardSecs else AppPreferences.rewindSecs).toString()) }
//    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
//        title = { Text(stringResource(titleRes), style = CustomTextStyles.titleCustom) },
//        text = {
//            TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Companion.Number), label = { Text("seconds") }, singleLine = true,
//                onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) interval = it })
//        },
//        confirmButton = {
//            TextButton(onClick = {
//                if (interval.isNotBlank()) {
//                    val value = interval.toInt()
//                    if (direction == SkipDirection.SKIP_FORWARD) AppPreferences.fastForwardSecs = value
//                    else AppPreferences.rewindSecs = value
//                    callBack(value)
//                    onDismissRequest()
//                }
//            }) { Text(text = "OK") }
//        },
//        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
//    )
//}

//enum class SkipDirection {
//    SKIP_FORWARD, SKIP_REWIND
//}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeMarks(episode: Episode?) {
    if (!episode?.marks.isNullOrEmpty()) {
        val context = LocalContext.current
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
                        if (status != PlayerStatus.PLAYING) playPause()
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
                            val file = getClipFile(episode, cliptToRemove)
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
                    val file = getClipFile(episode, clip)
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
    var episode = realm.query(Episode::class, "id == ${episode.id}").first().find() ?: return
    val vmsr = remember { mutableStateListOf<EpisodeVM>() }
    LaunchedEffect(Unit) {
        vmsr.clear()
        for (e in episode.related) vmsr.add(EpisodeVM(e, TAG))
    }
    AlertDialog(properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth().padding(10.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },  confirmButton = {},
        text = { EpisodeLazyColumn(LocalContext.current, vms = vmsr, showCoverImage = false,
            actionButton_ = { NullZapActionButton(it) },
            actionButtonCB = {e1, _ ->
                runOnIOScope {
                    realm.write {
                        val es = query(Episode::class, "id IN $0", listOf(episode.id, e1.id)).find()
                        if (es.size == 2) {
                            es[0].related.remove(es[1])
                            es[1].related.remove(es[0])
                        }
                    }
                    episode = realm.query(Episode::class, "id == ${episode.id}").first().find() ?: return@runOnIOScope
                    Logd("RelatedEpisodesDialog", "episode.related: ${episode.related.size}")
                    withContext(Dispatchers.Main) {
                        vmsr.clear()
                        for (e in episode.related) vmsr.add(EpisodeVM(e, TAG))
                    }
                }
            } ) },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } } )
}
