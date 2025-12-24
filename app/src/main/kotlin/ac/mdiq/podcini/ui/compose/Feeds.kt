package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.AppPreferences.isAutodownloadEnabled
import ac.mdiq.podcini.preferences.AppPreferences.prefStreamOverDownload
import ac.mdiq.podcini.preferences.OpmlTransporter
import ac.mdiq.podcini.storage.database.createSynthetic
import ac.mdiq.podcini.storage.database.deleteFeed
import ac.mdiq.podcini.storage.database.getPreserveSyndicate
import ac.mdiq.podcini.storage.database.queues
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.shelveToFeed
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.database.volumes
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.AutoDeleteAction
import ac.mdiq.podcini.storage.model.Feed.AutoDownloadPolicy
import ac.mdiq.podcini.storage.model.Feed.Companion.DEFAULT_INTERVALS
import ac.mdiq.podcini.storage.model.Feed.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.storage.model.Feed.Companion.INTERVAL_UNITS
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.storage.model.Volume
import ac.mdiq.podcini.storage.specs.FeedAutoDownloadFilter
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.specs.VolumeAdaptionSetting
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.screens.ADLIncExc
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.formatLargeInteger
import ac.mdiq.podcini.utils.localDateTimeString
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.xilinjia.krdb.ext.toRealmList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date

@Composable
fun ChooseRatingDialog(selected: List<Feed>, onDismissRequest: () -> Unit) {
    CommonPopupCard(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (rating in Rating.entries.reversed()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp).clickable {
                    for (item in selected) upsertBlk(item) { it.rating = rating.code }
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
fun RemoveFeedDialog(feeds: List<Feed>, onDismissRequest: () -> Unit, callback: ()->Unit) {
    val message = if (feeds.size == 1) {
        if (feeds[0].isLocalFeed) stringResource(R.string.feed_delete_confirmation_local_msg) + feeds[0].title
        else stringResource(R.string.feed_delete_confirmation_msg) + feeds[0].title
    } else stringResource(R.string.feed_delete_confirmation_msg_batch)
    val textColor = MaterialTheme.colorScheme.onSurface
    var textState by remember { mutableStateOf(TextFieldValue("")) }

    CommonPopupCard(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            var saveImportant by remember { mutableStateOf(true) }
            Text(message)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = saveImportant, onCheckedChange = { saveImportant = it })
                Text(text = stringResource(R.string.shelve_important), style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.padding(start = 10.dp))
            }
            Text(stringResource(R.string.reason_to_delete_msg))
            BasicTextField(value = textState, onValueChange = { textState = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor), modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
            Button(onClick = {
                callback()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val preserveFeed = if (saveImportant) getPreserveSyndicate() else null
                        for (f in feeds) {
                            if (saveImportant) {
                                val eList = f.getWorthyEpisodes()
                                if (eList.isNotEmpty()) shelveToFeed(eList, preserveFeed!!)
                            }
                            if (!f.isSynthetic()) {
                                val sLog = SubscriptionLog(f.id, f.title ?: "", f.downloadUrl ?: "", f.link ?: "", SubscriptionLog.Type.Feed.name)
                                upsert(sLog) {
                                    it.rating = f.rating
                                    it.comment = if (f.comment.isBlank()) "" else (f.comment + "\n")
                                    it.comment += localDateTimeString() + "\nReason to remove:\n" + textState.text
                                    it.cancelDate = Date().time
                                }
                            } else {
                                val episodes = f.episodes
                                for (e in episodes) {
                                    val sLog = SubscriptionLog(e.id, e.title ?: "", e.downloadUrl ?: "", e.link ?: "", SubscriptionLog.Type.Media.name)
                                    upsert(sLog) {
                                        it.rating = e.rating
                                        it.comment = if (e.comment.isBlank()) "" else (e.comment + "\n")
                                        it.comment += localDateTimeString() + "\nReason to remove:\n" + textState.text
                                        it.cancelDate = Date().time
                                    }
                                }
                            }
                            deleteFeed(f.id)
                        }
                        EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.REMOVED, feeds.map { it.id }))
                    } catch (e: Throwable) { Logs("RemoveFeedDialog", e) }
                }
                onDismissRequest()
            }) { Text(stringResource(R.string.confirm_label)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnlineFeedItem(feed: PodcastSearchResult, log: SubscriptionLog? = null) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val showSubscribeDialog = remember { mutableStateOf(false) }
    if (showSubscribeDialog.value) CommonPopupCard(onDismissRequest = { showSubscribeDialog.value = false }) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text("Subscribe: \"${feed.title}\" ?", color = textColor, modifier = Modifier.padding(bottom = 10.dp))
            Button(onClick = {
                gearbox.subscribeFeed(feed)
                showSubscribeDialog.value = false
            }) { Text(stringResource(R.string.confirm_label)) }
        }
    }

    Column(Modifier.padding(start = 5.dp, end = 5.dp, top = 4.dp, bottom = 4.dp).combinedClickable(
        onClick = {
            if (feed.feedUrl != null) {
                if (feed.feedId > 0) navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.feedId}")
                else navController.navigate("${Screens.OnlineFeed.name}?url=${URLEncoder.encode(feed.feedUrl, StandardCharsets.UTF_8.name())}&source=${feed.source}")
            }
        }, onLongClick = { showSubscribeDialog.value = true })) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Row {
            Box(modifier = Modifier.width(80.dp).height(80.dp)) {
                val img = remember(feed) { ImageRequest.Builder(context).data(feed.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                AsyncImage(model = img, contentDescription = "imgvCover", modifier = Modifier.fillMaxSize())
                if (feed.feedId > 0 || log != null) {
                    Logd("OnlineFeedItem", "${feed.feedId} $log")
                    val iRes = remember(feed) { if (feed.feedId > 0) R.drawable.ic_check else R.drawable.baseline_clear_24 }
                    Icon(imageVector = ImageVector.vectorResource(iRes), tint = textColor, contentDescription = "played_mark", modifier = Modifier.background(Color.Green).alpha(1.0f).align(Alignment.BottomEnd))
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(feed.title, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
                val authorText by remember(feed) { mutableStateOf(
                    when {
                        !feed.author.isNullOrBlank() -> feed.author.trim { it <= ' ' }
                        feed.feedUrl != null && !feed.feedUrl.contains("itunes.apple.com") -> feed.feedUrl
                        else -> ""
                    }) }
                if (authorText.isNotEmpty()) Text(authorText, color = textColor, style = MaterialTheme.typography.bodyMedium)
                if (feed.subscriberCount > 0) Text(formatLargeInteger(feed.subscriberCount) + " subscribers", color = textColor, style = MaterialTheme.typography.bodyMedium)
                Row {
                    if (feed.count != null && feed.count > 0) Text(feed.count.toString() + " episodes", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    if (feed.update != null) Text(feed.update, color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                Text(feed.source + ": " + feed.feedUrl, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun RenameOrCreateSyntheticFeed(feed_: Feed? = null, onDismissRequest: () -> Unit) {
    CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.rename_feed_label), color = textColor, style = MaterialTheme.typography.bodyLarge)
            var name by remember { mutableStateOf(feed_?.title ?:"") }
            TextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.new_namee)) })
            var hasVideo by remember { mutableStateOf(true) }
            var isYoutube by remember { mutableStateOf(false) }
            if (feed_ == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasVideo, onCheckedChange = { hasVideo = it })
                    Text(text = stringResource(R.string.has_video), style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.padding(start = 10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isYoutube, onCheckedChange = { isYoutube = it })
                    Text(text = stringResource(R.string.youtube), style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.padding(start = 10.dp))
                }
            }
            Row {
                Button({ onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) }
                Spacer(Modifier.weight(1f))
                Button({
                    val feed = feed_ ?: createSynthetic(0, name, hasVideo)
                    if (feed_ == null) {
                        feed.type = if (isYoutube) Feed.FeedType.YOUTUBE.name else Feed.FeedType.RSS.name
                        if (hasVideo) feed.videoModePolicy = VideoMode.WINDOW_VIEW
                    }
                    upsertBlk(feed) { if (feed_ != null) it.setCustomTitle1(name) }
                    onDismissRequest()
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }
}

@Composable
fun RenameOrCreateVolume(volume: Volume? = null, parent: Volume? = null, onDismissRequest: () -> Unit) {
    CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.rename_feed_label), color = textColor, style = MaterialTheme.typography.bodyLarge)
            var name by remember { mutableStateOf(volume?.name ?: "") }
            TextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.new_namee)) })
            Row {
                Button({ onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) }
                Spacer(Modifier.weight(1f))
                Button({
                    val v = if (volume == null) {
                        val v_ = Volume()
                        v_.id = System.currentTimeMillis()
                        v_
                    } else volume
                    upsertBlk(v) {
                        it.name = name
                        it.parentId = parent?.id ?: -1L
                    }
                    onDismissRequest()
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }
}

@Composable
fun OpmlImportSelectionDialog(readElements: SnapshotStateList<OpmlTransporter.OpmlElement>, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val selectedItems = remember {  mutableStateMapOf<Int, Boolean>() }
    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
        title = { Text("Import OPML file") },
        text = {
            var isSelectAllChecked by remember { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Select/Deselect All", modifier = Modifier.weight(1f))
                    Checkbox(checked = isSelectAllChecked, onCheckedChange = { isChecked ->
                        isSelectAllChecked = isChecked
                        readElements.forEachIndexed { index, _ -> selectedItems[index] = isChecked }
                    })
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(readElements) { index, item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = item.text?:"", modifier = Modifier.weight(1f))
                            Checkbox(checked = selectedItems[index] == true, onCheckedChange = { checked -> selectedItems[index] = checked })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Logd("OpmlImportSelectionDialog", "checked: $selectedItems")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (readElements.isNotEmpty()) {
                                for (i in selectedItems.keys) {
                                    if (selectedItems[i] != true) continue
                                    val element = readElements[i]
                                    val feed = Feed(element.xmlUrl, null, if (element.text != null) element.text else "Unknown podcast")
                                    feed.episodes.clear()
                                    updateFeedFull(feed, removeUnlistedItems = false)
                                }
                                // TODO: seems not needed
//                                runOnce(context)
                            }
                        }
                    } catch (e: Throwable) { Logs("OpmlImportSelectionDialog", e) }
                }
                onDismissRequest()
            }) { Text(stringResource(R.string.confirm_label)) }
        },
        dismissButton = { Button(onClick = { onDismissRequest() }) { Text("Dismiss") } }
    )
}

@Composable
fun VideoModeDialog(initMode: VideoMode?, onDismissRequest: () -> Unit, callback: (VideoMode) -> Unit) {
    var selectedOption by remember { mutableStateOf(initMode?.tag ?: VideoMode.NONE.tag) }
    CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                VideoMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val text = remember { mode.tag }
                        Checkbox(checked = (text == selectedOption), onCheckedChange = {
                            if (text != selectedOption) {
                                selectedOption = text
                                callback(mode)
                                onDismissRequest()
                            }
                        })
                        Text(text = text, style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FeedSettingsScreen(feeds: List<Feed>,  onDismissRequest: () -> Unit) {
    val TAG = "FeedSettingsScreen"

    var audioType by remember { mutableStateOf(Feed.AudioType.SPEECH.tag) }

    var audioQuality by remember { mutableStateOf(Feed.AVQuality.GLOBAL.tag) }
    var videoQuality by remember { mutableStateOf(Feed.AVQuality.GLOBAL.tag) }

    var autoUpdate by remember { mutableStateOf(false) }

    var autoDeleteSummaryResId by remember { mutableIntStateOf(R.string.global_default) }
    var curPrefQueue by remember { mutableStateOf("Default") }
    var autoDeletePolicy by remember { mutableStateOf(AutoDeleteAction.GLOBAL.name) }
    var videoModeSummaryResId by remember { mutableIntStateOf(R.string.global_default) }

    @Composable
    fun AutoDeleteDialog(onDismissRequest: () -> Unit) {
        CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(autoDeletePolicy) }
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
                                    runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.autoDeleteAction = action_ } } }
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

    @Composable
    fun VolumeAdaptionDialog(onDismissRequest: () -> Unit) {
        CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(VolumeAdaptionSetting.OFF) }
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VolumeAdaptionSetting.entries.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = (item == selectedOption),
                            onCheckedChange = { _ ->
                                Logd(TAG, "row clicked: $item $selectedOption")
                                if (item != selectedOption) {
                                    onOptionSelected(item)
                                    runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.volumeAdaptionSetting = item } } }
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

    @Composable
    fun SetAudioType(selectedOption: String, onDismissRequest: () -> Unit) {
        CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
            var selected by remember {mutableStateOf(selectedOption)}
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Feed.AudioType.entries.forEach { option ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = option.tag == selected,
                            onCheckedChange = { isChecked ->
                                selected = option.tag
                                if (isChecked) Logd(TAG, "$option is checked")
                                val type = Feed.AudioType.fromTag(selected)
                                runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.audioType = type.code } } }
                                onDismissRequest()
                            }
                        )
                        Text(option.tag)
                    }
                }
            }
        }
    }

    @Composable
    fun SetAudioQuality(selectedOption: String, onDismissRequest: () -> Unit) {
        CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
            var selected by remember {mutableStateOf(selectedOption)}
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Feed.AVQuality.entries.forEach { option ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = option.tag == selected,
                            onCheckedChange = { isChecked ->
                                selected = option.tag
                                if (isChecked) Logd(TAG, "$option is checked")
                                val type = Feed.AVQuality.fromTag(selected)
                                runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.audioQuality = type.code } } }
                                onDismissRequest()
                            })
                        Text(option.tag)
                    }
                }
            }
        }
    }

    @Composable
    fun SetVideoQuality(selectedOption: String, onDismissRequest: () -> Unit) {
        CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
            var selected by remember {mutableStateOf(selectedOption)}
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Feed.AVQuality.entries.forEach { option ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = option.tag == selected,
                            onCheckedChange = { isChecked ->
                                selected = option.tag
                                if (isChecked) Logd(TAG, "$option is checked")
                                val type = Feed.AVQuality.fromTag(selected)
                                runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.videoQuality = type.code } } }
                                onDismissRequest()
                            })
                        Text(option.tag)
                    }
                }
            }
        }
    }

    @Composable
    fun AuthenticationDialog(onDismiss: () -> Unit) {
        CommonPopupCard(onDismissRequest = onDismiss) {
            val context = LocalContext.current
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val oldName = ""
                var newName by remember { mutableStateOf(oldName) }
                TextField(value = newName, onValueChange = { newName = it }, label = { Text("Username") })
                val oldPW = ""
                var newPW by remember { mutableStateOf(oldPW) }
                TextField(value = newPW, onValueChange = { newPW = it }, label = { Text("Password") })
                Button(onClick = {
                    if (newName.isNotEmpty() && oldName != newName) {
                        runOnIOScope {
                            realm.write { for (f in feeds) { findLatest(f)?.let {
                                it.username = newName
                                it.password = newPW
                            } } }
                            gearbox.feedUpdater(feeds).startRefresh(context)
                        }
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }

    @Composable
    fun AutoSkipDialog(onDismiss: () -> Unit) {
        CommonPopupCard(onDismissRequest = onDismiss) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var intro by remember { mutableIntStateOf((0)) }
                NumberEditor(intro, label = stringResource(R.string.skip_first_hint), nz = false, instant = true, modifier = Modifier) { intro = it }
                var ending by remember { mutableIntStateOf((0)) }
                NumberEditor(ending, label = stringResource(R.string.skip_last_hint), nz = false, instant = true, modifier = Modifier) { ending = it }
                Button(onClick = {
                    runOnIOScope {
                        realm.write { for (f in feeds) { findLatest(f)?.let {
                            it.introSkip = intro
                            it.endingSkip = ending
                        } } }
                    }
                    onDismiss()
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }

    @Composable
    fun RepeatIntervalsDialog(onDismiss: () -> Unit) {
        CommonPopupCard(onDismissRequest = onDismiss) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var intervals = remember { DEFAULT_INTERVALS.toMutableList() }
                if (intervals.isNullOrEmpty()) intervals = DEFAULT_INTERVALS.toMutableList()
                val units = INTERVAL_UNITS.map { stringResource(it) }
                for (i in intervals.indices) {
                    NumberEditor(intervals[i], label = "in " + units[i], nz = false, instant = true, modifier = Modifier) { intervals[i] = it }
                }
                Button(onClick = {
                    runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.repeatIntervals = intervals.toRealmList() } } }
                    onDismiss()
                }) { Text(stringResource(R.string.confirm_label)) }
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
    Dialog(onDismissRequest = { onDismissRequest() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var textChanged by remember { mutableStateOf(false) }
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { //                    associated volume
                Column {
                    val none = "None"
                    var curVolumeName by remember { mutableStateOf( none ) }

                    @Composable
                    fun SetVolume(selectedOption: String, onDismissRequest: () -> Unit) {
                        CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val custom = "Custom"
                                var selected by remember { mutableStateOf(if (selectedOption == none) none else custom) }
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = none == selected, onCheckedChange = { isChecked ->
                                        selected = none
                                        runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.volumeId = -1L } } }
                                        curVolumeName = selected
                                        onDismissRequest()
                                    })
                                    Text(none)
                                    Spacer(Modifier.width(50.dp))
                                    Checkbox(checked = custom == selected, onCheckedChange = { isChecked -> selected = custom })
                                    Text(custom)
                                }
                                if (selected == custom) {
                                    Logd(TAG, "volumes: ${volumes.size}")
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        for (i in volumes.indices) {
                                            FilterChip(onClick = {
                                                val v = volumes[i]
                                                runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.volumeId = v.id } } }
                                                curVolumeName = v.name
                                                onDismissRequest()
                                            }, label = { Text(volumes[i].name) }, selected = false, border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    var showDialog by remember { mutableStateOf(false) } //                    var selectedOption by remember { mutableStateOf(feed?.queueText ?: "None") }
                    if (showDialog) SetVolume(selectedOption = curVolumeName, onDismissRequest = { showDialog = false })
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.rounded_books_movies_and_music_24), "", tint = textColor, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.pref_parent_volume), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog = true }))
                    }
                    Text(text = curVolumeName + " : " + stringResource(R.string.pref_parent_volume_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }

                TitleSummarySwitch(R.string.use_wide_layout, R.string.use_wide_layout_summary, R.drawable.rounded_responsive_layout_24, false) {
                    runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.useWideLayout = it } } }
                }
                TitleSummarySwitch(R.string.use_episode_image, R.string.use_episode_image_summary, R.drawable.outline_broken_image_24, false) {
                    runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.useEpisodeImage = it } } }
                }
                Column {
                    var showDialog by remember { mutableStateOf(false) }
                    if (showDialog) SetAudioType(selectedOption = audioType, onDismissRequest = { showDialog = false })
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.pref_feed_audio_type), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = {
//                            audioType = feed!!.audioTypeSetting.tag
                            showDialog = true
                        }))
                        Spacer(modifier = Modifier.width(30.dp))
                        Text(audioType, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    Text(text = stringResource(R.string.pref_feed_audio_type_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }

                    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                        Text(stringResource(R.string.preferred_languages), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                        var showIcon by remember { mutableStateOf(false) }
                        var newName by remember { mutableStateOf("") }
                        TextField(value = newName, onValueChange = {
                            newName = it
                            showIcon = true
                        }, trailingIcon = {
                            if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon", modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                runOnIOScope {
                                    realm.write { for (f in feeds) { findLatest(f)?.let { att ->
                                        att.preferredLnaguages.clear()
                                        att.preferredLnaguages.addAll(newName.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                                    } } }
                                }
                                showIcon = false
                            }))
                        })
                        Text("", color = textColor, style = MaterialTheme.typography.bodySmall)
                    }


                    //                    video mode
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            var showDialog by remember { mutableStateOf(false) }
                            if (showDialog) VideoModeDialog(initMode = VideoMode.AUDIO_ONLY, onDismissRequest = { showDialog = false }) { mode ->
                                runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.videoModePolicy = mode } } }
                            }
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.feed_video_mode_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog = true }))
                            Spacer(modifier = Modifier.width(30.dp))
                            Text(text = stringResource(videoModeSummaryResId), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }


                    //                    audio quality
                    Column {
                        var showDialog by remember { mutableStateOf(false) }
                        if (showDialog) SetAudioQuality(selectedOption = audioQuality, onDismissRequest = { showDialog = false })
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.pref_feed_audio_quality), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = {
//                                audioQuality = feed!!.audioQualitySetting.tag
                                showDialog = true
                            }))
                            Spacer(modifier = Modifier.width(30.dp))
                            Text(audioQuality, style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        Text(text = stringResource(R.string.pref_feed_audio_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }

                        //                    video quality
                        Column {
                            var showDialog by remember { mutableStateOf(false) }
                            if (showDialog) SetVideoQuality(selectedOption = videoQuality, onDismissRequest = { showDialog = false })
                            Row(Modifier.fillMaxWidth()) {
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_videocam), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(text = stringResource(R.string.pref_feed_video_quality), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = {
//                                    videoQuality = feed!!.videoQualitySetting.tag
                                    showDialog = true
                                }))
                                Spacer(modifier = Modifier.width(30.dp))
                                Text(videoQuality, style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                            Text(text = stringResource(R.string.pref_feed_video_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }

                @Composable
                fun SetAssociatedQueue(selectedOption: String, onDismissRequest: () -> Unit) {
                    CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val custom = "Custom"
                            val none = "None"
                            var selected by remember { mutableStateOf(if (selectedOption == none) none else custom) }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = none == selected, onCheckedChange = { isChecked ->
                                    selected = none
                                    runOnIOScope {
                                        realm.write { for (f in feeds) { findLatest(f)?.let {
                                            it.queueId = -2L
                                            it.autoDownload = false
                                            it.autoEnqueue = false
                                        } } }
                                    }
                                    curPrefQueue = selected
                                    onDismissRequest()
                                })
                                Text(none)
                                Spacer(Modifier.width(50.dp))
                                Checkbox(checked = custom == selected, onCheckedChange = { isChecked -> selected = custom })
                                Text(custom)
                            }
                            if (selected == custom) {
                                Logd(TAG, "queues: ${queues.size}")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (i in queues.indices) {
                                        FilterChip(onClick = {
                                            val q = queues[i]
                                            runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.queue = q } } }
                                            curPrefQueue = q.name
                                            onDismissRequest()
                                        }, label = { Text(queues[i].name) }, selected = false, border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary))
                                    }
                                }
                            }
                        }
                    }
                } //                    associated queue
                Column {
                    curPrefQueue = "Default"
                    var showDialog by remember { mutableStateOf(false) }
                    var selectedOption by remember { mutableStateOf("Default") }
                    if (showDialog) SetAssociatedQueue(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.pref_feed_associated_queue), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = {
//                            selectedOption = feed?.queueText ?: "Default"
                            showDialog = true
                        }))
                    }
                    Text(text = curPrefQueue + " : " + stringResource(R.string.pref_feed_associated_queue_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                } //                    tags
                var showTagsSettingDialog by remember { mutableStateOf(false) }
                if (showTagsSettingDialog) TagSettingDialog(TagType.Feed, setOf(), onDismiss = { showTagsSettingDialog = false }) { tags ->
                    runOnIOScope {
                        realm.write { for (f in feeds) { findLatest(f)?.let {
                            it.tags.clear()
                            it.tags.addAll(tags)
                        } } }
                    }
                }

                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_tag), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.tags_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showTagsSettingDialog = true }))
                    }
                    Text(text = stringResource(R.string.feed_tags_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                } //                    playback speed
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        if (showDialog.value) PlaybackSpeedDialog(feeds, initSpeed = 1f, maxSpeed = 3f, onDismiss = { showDialog.value = false }) { newSpeed -> runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.playSpeed = newSpeed } } } }
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_playback_speed), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.playback_speed), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                    }
                    Text(text = stringResource(R.string.pref_feed_playback_speed_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                } //                    volume adaption
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        if (showDialog.value) VolumeAdaptionDialog(onDismissRequest = { showDialog.value = false })
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_volume_adaption), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.feed_volume_adapdation), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                    }
                    Text(text = stringResource(R.string.feed_volume_adaptation_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                } //                    authentication
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
                var autoDownloadChecked by remember { mutableStateOf(false) }
                var preferStreaming by remember { mutableStateOf(false) }
                    //                    prefer streaming
                    TitleSummarySwitch(R.string.pref_stream_over_download_title, R.string.pref_stream_over_download_sum, R.drawable.ic_stream, preferStreaming) {
                        preferStreaming = it
                        if (preferStreaming) {
                            prefStreamOverDownload = true
                            autoDownloadChecked = false
                        }
                        runOnIOScope {
                            realm.write { for (f in feeds) { findLatest(f)?.let { f ->
                                f.prefStreamOverDownload = preferStreaming
                                if (preferStreaming) f.autoDownload = false
                            } } }
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
                } //                    repeat intervals
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


                    //                    max episodes
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_refresh), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.limit_episodes_to), style = CustomTextStyles.titleCustom, color = textColor)
                            Spacer(modifier = Modifier.weight(1f))
                            NumberEditor(0, label = "0 = unlimited", nz = false, modifier = Modifier.width(150.dp)) {
                                runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.limitEpisodesCount = it } } }
                            }
                        }
                        Text(text = stringResource(R.string.limit_episodes_to_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }

                    //                    refresh
                    TitleSummarySwitch(R.string.keep_updated, R.string.keep_updated_summary, R.drawable.ic_refresh, autoUpdate) {
                        autoUpdate = it
                        runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.keepUpdated = autoUpdate } } }
                    }

                if (curPrefQueue != "None") { //                    auto add new to queue
                    TitleSummarySwitch(R.string.audo_add_new_queue, R.string.audo_add_new_queue_summary, androidx.media3.session.R.drawable.media3_icon_queue_add, false) {
                        runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.autoAddNewToQueue = it } } }
                    }
                }
                var autoEnqueueChecked by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.auto_colon), style = CustomTextStyles.titleCustom, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = stringResource(R.string.enqueue), style = CustomTextStyles.titleCustom, color = textColor)
                    if (curPrefQueue != "None") {
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(checked = autoEnqueueChecked, modifier = Modifier.height(24.dp), onCheckedChange = {
                            autoEnqueueChecked = it
                            if (autoEnqueueChecked) autoDownloadChecked = false
                            runOnIOScope {
                                realm.write { for (f in feeds) { findLatest(f)?.let { f ->
                                    f.autoEnqueue = autoEnqueueChecked
                                    f.autoDownload = autoDownloadChecked
                                } } }
                            }
                        })
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = stringResource(R.string.download), style = CustomTextStyles.titleCustom, color = textColor)

                        if (isAutodownloadEnabled && !preferStreaming) { //                    auto download
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(checked = autoDownloadChecked, modifier = Modifier.height(24.dp), onCheckedChange = {
                                autoDownloadChecked = it
                                if (autoDownloadChecked) autoEnqueueChecked = false
                                runOnIOScope {
                                    realm.write { for (f in feeds) { findLatest(f)?.let { f ->
                                        f.autoDownload = autoDownloadChecked
                                        f.autoEnqueue = autoEnqueueChecked
                                    } } }
                                }
                            })
                        }
                }
                if (!autoEnqueueChecked && !autoDownloadChecked) {
                    Text(text = stringResource(R.string.auto_enqueue_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    if (curPrefQueue == "None") Text(text = stringResource(R.string.auto_enqueue_sum1), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    Text(text = stringResource(R.string.auto_download_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    if (!isAutodownloadEnabled) Text(text = stringResource(R.string.auto_download_disabled_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                if (autoDownloadChecked || autoEnqueueChecked) {
                    var newCache by remember { mutableIntStateOf((2)) }

                    @Composable
                    fun SetAutoDLEQCacheDialog(onDismiss: () -> Unit) {
                        CommonPopupCard(onDismissRequest = onDismiss) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberEditor(newCache, label = stringResource(R.string.max_episodes_cache), nz = false, instant = true, modifier = Modifier) { newCache = it } //                    counting played
                                var countingPlayed by remember { mutableStateOf(false) }
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
                                        runOnIOScope {
                                            realm.write { for (f in feeds) { findLatest(f)?.let {
                                                it.autoDLMaxEpisodes = newCache
                                                if (autoDownloadChecked) it.countingPlayed = countingPlayed
                                            } } }
                                        }
                                        onDismiss()
                                    }
                                }) { Text(stringResource(R.string.confirm_label)) }
                            }
                        }
                    } //                    episode cache
                    Column(modifier = Modifier.padding(start = 20.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            val showDialog = remember { mutableStateOf(false) }
                            if (showDialog.value) SetAutoDLEQCacheDialog(onDismiss = { showDialog.value = false })
                            Text(text = stringResource(R.string.pref_episode_cache_title), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                            Spacer(modifier = Modifier.width(30.dp))
                            Text(newCache.toString(), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        Text(text = stringResource(R.string.pref_episode_cache_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    } //                    include Soon
                    Column(modifier = Modifier.padding(start = 20.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(text = stringResource(R.string.pref_auto_download_include_soon_title), style = CustomTextStyles.titleCustom, color = textColor)
                            Spacer(modifier = Modifier.weight(1f))
                            var checked by remember { mutableStateOf(false) }
                            Switch(checked = checked, modifier = Modifier.height(24.dp), onCheckedChange = {
                                checked = it
                                runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.autoDLSoon = checked } } }
                            })
                        }
                        Text(text = stringResource(R.string.pref_auto_download_include_soon_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    val (selectedPolicy, onPolicySelected) = remember { mutableStateOf(AutoDownloadPolicy.ONLY_NEW) }
                    @Composable
                    fun AutoDLEQPolicyDialog(onDismissRequest: () -> Unit) {
                        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() }, title = { Text(stringResource(R.string.feed_automation_policy), style = CustomTextStyles.titleCustom) }, text = {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AutoDownloadPolicy.entries.forEach { item ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = (item == selectedPolicy), onCheckedChange = { onPolicySelected(item) })
                                        Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 8.dp))
                                    }
                                    if (selectedPolicy == AutoDownloadPolicy.ONLY_NEW && item == selectedPolicy) Row(Modifier.fillMaxWidth().padding(start = 30.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        }, confirmButton = {
                            TextButton(onClick = {
                                Logd(TAG, "autoDLPolicy: ${selectedPolicy.name} ${selectedPolicy.replace}")
                                runOnIOScope {
                                    realm.write { for (f in feeds) { findLatest(f)?.let {
                                        it.autoDLPolicy = selectedPolicy
                                        if (selectedPolicy == AutoDownloadPolicy.FILTER_SORT) {
//                                            it.episodeFilterADL = feed!!.episodeFilter
//                                            it.episodesSortOrderADL = feed!!.episodeSortOrder
                                        }
                                    } } }
                                }
                                onDismissRequest()
                            }) { Text(stringResource(R.string.confirm_label)) }
                        }, dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } })
                    } //                    automation policy
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
                                                FilterChip(onClick = { }, label = { Text(it) }, selected = false, trailingIcon = {
                                                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon", modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = { termList.remove(it) }))
                                                })
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
                                        TextField(value = text, onValueChange = { newTerm -> text = newTerm }, placeholder = { Text(stringResource(R.string.add_term_hint)) }, keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { setText() }), trailingIcon = {
                                            Icon(imageVector = Icons.Filled.Add, contentDescription = "Add term", modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                                setText()
                                            }))
                                        }, textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
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
                        } //                    inclusive filter
                        Column(modifier = Modifier.padding(start = 20.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                val showDialog = remember { mutableStateOf(false) }
                                if (showDialog.value) AutoDownloadFilterDialog( FeedAutoDownloadFilter(), ADLIncExc.INCLUDE, onDismiss = { showDialog.value = false }) { filter -> runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.autoDownloadFilter = filter } } } }
                                Text(text = stringResource(R.string.episode_inclusive_filters_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                            }
                            Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        } //                    exclusive filter
                        Column(modifier = Modifier.padding(start = 20.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                val showDialog = remember { mutableStateOf(false) }
                                if (showDialog.value) AutoDownloadFilterDialog( FeedAutoDownloadFilter(), ADLIncExc.EXCLUDE, onDismiss = { showDialog.value = false }) { filter -> runOnIOScope { realm.write { for (f in feeds) { findLatest(f)?.autoDownloadFilter = filter } } } }
                                Text(text = stringResource(R.string.episode_exclusive_filters_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                            }
                            Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    } else {
                        Column(modifier = Modifier.padding(start = 20.dp, bottom = 5.dp)) {
                            Text("Sorted by: " + stringResource( 0), modifier = Modifier.padding(start = 10.dp))
                            Text("Filtered by: ", modifier = Modifier.padding(start = 10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(start = 20.dp)) {
//                                feed?.episodeFilterADL?.propertySet?.forEach { FilterChip(onClick = { }, label = { Text(it) }, selected = false) }
                            }
                        }
                    }
                }
            }
        }
    }
}
