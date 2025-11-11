package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.setCurTempSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.curPBSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isFallbackSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isSpeedForward
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.prefPlaybackSpeed
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.fallbackSpeed
import ac.mdiq.podcini.preferences.AppPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.AppPreferences.getPrefOrNull
import ac.mdiq.podcini.preferences.AppPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.AppPreferences.rewindSecs
import ac.mdiq.podcini.preferences.AppPreferences.speedforwardSpeed
import ac.mdiq.podcini.preferences.OpmlTransporter
import ac.mdiq.podcini.storage.database.Feeds.compileTags
import ac.mdiq.podcini.storage.database.Feeds.createSynthetic
import ac.mdiq.podcini.storage.database.Feeds.deleteFeedSync
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.getPreserveSyndicate
import ac.mdiq.podcini.storage.database.Feeds.getTags
import ac.mdiq.podcini.storage.database.Feeds.shelveToFeed
import ac.mdiq.podcini.storage.database.Feeds.updateFeedFull
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.screens.FeedScreenMode
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.ui.utils.setOnlineFeedUrl
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.MiscFormatter
import ac.mdiq.podcini.util.MiscFormatter.localDateTimeString
import android.view.Gravity
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import java.util.Locale
import kotlin.math.round

@Composable
fun ChooseRatingDialog(selected: List<Feed>, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
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
}

@Composable
fun RemoveFeedDialog(feeds: List<Feed>, onDismissRequest: () -> Unit, callback: ()->Unit) {
    val message = if (feeds.size == 1) {
        if (feeds[0].isLocalFeed) stringResource(R.string.feed_delete_confirmation_local_msg) + feeds[0].title
        else stringResource(R.string.feed_delete_confirmation_msg) + feeds[0].title
    } else stringResource(R.string.feed_delete_confirmation_msg_batch)
    val textColor = MaterialTheme.colorScheme.onSurface
    var textState by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
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
                                    for (e in f.episodes) {
                                        val sLog = SubscriptionLog(e.id, e.title ?: "", e.downloadUrl ?: "", e.link ?: "", SubscriptionLog.Type.Media.name)
                                        upsert(sLog) {
                                            it.rating = e.rating
                                            it.comment = if (e.comment.isBlank()) "" else (e.comment + "\n")
                                            it.comment += localDateTimeString() + "\nReason to remove:\n" + textState.text
                                            it.cancelDate = Date().time
                                        }
                                    }
                                }
                                deleteFeedSync(context, f.id, false)
                            }
                            EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.REMOVED, feeds.map { it.id }))
                        } catch (e: Throwable) { Logs("RemoveFeedDialog", e) }
                    }
                    onDismissRequest()
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnlineFeedItem(feed: PodcastSearchResult, log: SubscriptionLog? = null) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val showSubscribeDialog = remember { mutableStateOf(false) }
    @Composable
    fun confirmSubscribe(feed: PodcastSearchResult, showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
                        Text("Subscribe: \"${feed.title}\" ?", color = textColor, modifier = Modifier.padding(bottom = 10.dp))
                        Button(onClick = {
                            gearbox.subscribeFeed(feed, context)
                            onDismissRequest()
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }
    }
    if (showSubscribeDialog.value) confirmSubscribe(feed, showSubscribeDialog.value, onDismissRequest = { showSubscribeDialog.value = false })

    Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp).combinedClickable(
        onClick = {
            if (feed.feedUrl != null) {
                if (feed.feedId > 0) {
                    val feed_ = getFeed(feed.feedId) ?: return@combinedClickable
                    feedOnDisplay = feed_
                    feedScreenMode = FeedScreenMode.List
                    navController.navigate(Screens.FeedDetails.name)
                } else {
                    setOnlineFeedUrl(feed.feedUrl, source = feed.source)
                    navController.navigate(Screens.OnlineFeed.name)
                }
            }
        }, onLongClick = { showSubscribeDialog.value = true })) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Text(feed.title, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
        Row {
            Box(modifier = Modifier.width(56.dp).height(56.dp)) {
                val img = remember(feed) { ImageRequest.Builder(context).data(feed.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                AsyncImage(model = img, contentDescription = "imgvCover", modifier = Modifier.fillMaxSize())
                if (feed.feedId > 0 || log != null) {
                    Logd("OnlineFeedItem", "${feed.feedId} $log")
                    val alpha = 1.0f
                    val iRes = if (feed.feedId > 0) R.drawable.ic_check else R.drawable.baseline_clear_24
                    Icon(imageVector = ImageVector.vectorResource(iRes), tint = textColor, contentDescription = "played_mark", modifier = Modifier.background(Color.Green).alpha(alpha).align(Alignment.BottomEnd))
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                var authorText by remember { mutableStateOf("") }
                authorText = when {
                    !feed.author.isNullOrBlank() -> feed.author.trim { it <= ' ' }
                    feed.feedUrl != null && !feed.feedUrl.contains("itunes.apple.com") -> feed.feedUrl
                    else -> ""
                }
                if (authorText.isNotEmpty()) Text(authorText, color = textColor, style = MaterialTheme.typography.bodyMedium)
                if (feed.subscriberCount > 0) Text(MiscFormatter.formatLargeInteger(feed.subscriberCount) + " subscribers", color = textColor, style = MaterialTheme.typography.bodyMedium)
                Row {
                    if (feed.count != null && feed.count > 0) Text(feed.count.toString() + " episodes", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    if (feed.update != null) Text(feed.update, color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                Text(feed.source + ": " + feed.feedUrl, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun RenameOrCreateSyntheticFeed(feed_: Feed? = null, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.rename_feed_label), color = textColor, style = MaterialTheme.typography.bodyLarge)
                var name by remember { mutableStateOf("") }
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
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagSettingDialog(feeds_: List<Feed>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val feeds = realm.query(Feed::class).query("id IN $0", feeds_.map {it.id}).find()
        val suggestions = remember { getTags() }
        val commonTags = remember {
            if (feeds.size == 1) feeds[0].tags.toMutableStateList()
            else {
                val commons = feeds[0].tags.toMutableSet()
                if (commons.isNotEmpty()) for (f in feeds) commons.retainAll(f.tags)
                commons.toMutableStateList()
            }
        }
        Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tags_label), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                var text by remember { mutableStateOf("") }
                var filteredSuggestions by remember { mutableStateOf(suggestions) }
                var showSuggestions by remember { mutableStateOf(false) }
                val tags = remember { commonTags.toMutableStateList() }
                if (feeds.size > 1 && commonTags.isNotEmpty()) Text(stringResource(R.string.multi_feed_common_tags_info))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    tags.forEach { FilterChip(onClick = {  }, label = { Text(it) }, selected = false, trailingIcon = { Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon",
                        modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = { tags.remove(it) })) }) }
                }
                ExposedDropdownMenuBox(expanded = showSuggestions, onExpandedChange = { }) {
                    TextField(value = text, placeholder = { Text("Type something...") }, keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true), // Material3 requirement
                        onValueChange = {
                            text = it
                            filteredSuggestions = suggestions.filter { item -> item.contains(text, ignoreCase = true) && item !in tags }
                            showSuggestions = text.isNotEmpty() && filteredSuggestions.isNotEmpty()
                        },
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (text.isNotBlank()) {
                                    if (text !in tags) tags.add(text)
                                    text = ""
                                }
                            }
                        ),
                        trailingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Add icon",
                            modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                if (text.isNotBlank()) {
                                    if (text !in tags) tags.add(text)
                                    text = ""
                                }
                            })) }
                    )
                    ExposedDropdownMenu(expanded = showSuggestions, onDismissRequest = { showSuggestions = false }) {
                        for (i in filteredSuggestions.indices) {
                            DropdownMenuItem(text = { Text(filteredSuggestions[i]) },
                                onClick = {
                                    text = filteredSuggestions[i]
                                    showSuggestions = false
                                }
                            )
                        }
                    }
                }
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                    Button(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel_label)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        Logd("TagsSettingDialog", "tags: [${tags.joinToString()}] commonTags: [${commonTags.joinToString()}]")
                        if ((tags.toSet() + commonTags.toSet()).isNotEmpty() || text.isNotBlank()) {
                            for (f in feeds) upsertBlk(f) {
                                if (commonTags.isNotEmpty()) it.tags.removeAll(commonTags)
                                if (tags.isNotEmpty()) it.tags.addAll(tags)
                                if (text.isNotBlank()) it.tags.add(text)
                            }
                            compileTags()
                        }
                        onDismiss()
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }
}

private fun speed2Slider(speed: Float, maxSpeed: Float): Float {
    return if (speed < 1) (speed - 0.1f) / 1.8f else (speed - 2f + maxSpeed) / 2 / (maxSpeed - 1f)
}
private fun slider2Speed(slider: Float, maxSpeed: Float): Float {
    return if (slider < 0.5) 1.8f * slider + 0.1f else 2 * (maxSpeed - 1f) * slider + 2f - maxSpeed
}
@Composable
fun SpeedSetter(initSpeed: Float, maxSpeed: Float, speedCB: (Float) -> Unit) {
//    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
//        Text(text = String.format(Locale.getDefault(), "%.2fx", speed))
//    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        var speed by remember { mutableFloatStateOf(initSpeed) }
        var sliderPosition by remember { mutableFloatStateOf(speed2Slider(if (speed == Feed.SPEED_USE_GLOBAL) 1f else speed, maxSpeed)) }
        val stepSize = 0.05f
        Text("-", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = {
                val speed_ = round(speed / stepSize) * stepSize - stepSize
                if (speed_ >= 0.1f) {
                    speed = speed_
                    sliderPosition = speed2Slider(speed, maxSpeed)
                    speedCB(speed)
                }
            }))
        Slider(value = sliderPosition, modifier = Modifier.weight(1f).height(5.dp).padding(start = 20.dp, end = 20.dp),
            onValueChange = {
                sliderPosition = it
                speed = slider2Speed(sliderPosition, maxSpeed)
                speedCB(speed)
            })
        Text("+", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = {
                val speed_ = round(speed / stepSize) * stepSize + stepSize
                if (speed_ <= maxSpeed) {
                    speed = speed_
                    sliderPosition = speed2Slider(speed, maxSpeed)
                    speedCB(speed)
                }
            }))
        Spacer(Modifier.width(40.dp))
        Text(text = String.format(Locale.getDefault(), "%.2fx", speed))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedDialog(feeds: List<Feed>, initSpeed: Float, maxSpeed: Float, isGlobal: Boolean = false, onDismiss: () -> Unit, speedCB: (Float) -> Unit) {
    // min speed set to 0.1 and max speed at 10
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column {
                Text(stringResource(R.string.playback_speed), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                var speed by remember { mutableFloatStateOf(initSpeed) }
//                var sliderPosition by remember { mutableFloatStateOf(speed2Slider(if (speed == Feed.SPEED_USE_GLOBAL) 1f else speed, maxSpeed)) }
                var useGlobal by remember { mutableStateOf(!isGlobal && speed == Feed.SPEED_USE_GLOBAL) }
                if (!isGlobal) Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useGlobal, onCheckedChange = { isChecked ->
                        useGlobal = isChecked
                        speed = when {
                            useGlobal -> Feed.SPEED_USE_GLOBAL
                            feeds.size == 1 -> {
                                if (feeds[0].playSpeed == Feed.SPEED_USE_GLOBAL) prefPlaybackSpeed
                                else feeds[0].playSpeed
                            }
                            else -> 1f
                        }
//                        if (!useGlobal) sliderPosition = speed2Slider(speed, maxSpeed)
                    })
                    Text(stringResource(R.string.global_default))
                }
                if (!useGlobal) SpeedSetter(initSpeed, maxSpeed) { speed = it }
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                    Button(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel_label)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val newSpeed = if (useGlobal) Feed.SPEED_USE_GLOBAL else speed
                        speedCB(newSpeed)
                        onDismiss()
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaybackSpeedFullDialog(settingCode: BooleanArray, indexDefault: Int, maxSpeed: Float, onDismiss: () -> Unit) {
    val TAG = "PlaybackSpeedFullDialog"
    fun readPlaybackSpeedArray(valueFromPrefs: String?): List<Float> {
        if (valueFromPrefs != null) {
            try {
                val jsonArray = JSONArray(valueFromPrefs)
                val selectedSpeeds: MutableList<Float> = mutableListOf()
                for (i in 0 until jsonArray.length()) selectedSpeeds.add(jsonArray.getDouble(i).toFloat())
                return selectedSpeeds
            } catch (e: JSONException) { Logs(TAG, e, "Got JSON error when trying to get speeds from JSONArray") }
        }
        return mutableListOf(1.0f, 1.25f, 1.5f)
    }
    fun setPlaybackSpeedArray(speeds: List<Float>) {
        val format = DecimalFormatSymbols(Locale.US)
        format.decimalSeparator = '.'
        val speedFormat = DecimalFormat("0.00", format)
        val jsonArray = JSONArray()
        for (speed in speeds) jsonArray.put(speedFormat.format(speed.toDouble()))
        putPref(AppPrefs.prefPlaybackSpeedArray, jsonArray.toString())
    }
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
        Card(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(top = 10.dp, bottom = 10.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                var speed by remember { mutableFloatStateOf(curPBSpeed) }
                val speeds = remember { readPlaybackSpeedArray(getPrefOrNull<String>(AppPrefs.prefPlaybackSpeedArray, null)).toMutableStateList() }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.playback_speed), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.width(50.dp))
                    FilterChip(onClick = {
                        if (speed !in speeds) {
                            speeds.add(speed)
                            speeds.sort()
                            setPlaybackSpeedArray(speeds)
                    } }, label = { Text(String.format(Locale.getDefault(), "%.2f", speed)) }, selected = false,
                        trailingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Add icon", modifier = Modifier.size(FilterChipDefaults.IconSize)) })
                }
                var sliderPosition by remember { mutableFloatStateOf(speed2Slider(if (speed == Feed.SPEED_USE_GLOBAL) 1f else speed, maxSpeed)) }
                val stepSize = 0.05f
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("-", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = {
                            val speed_ = round(speed / stepSize) * stepSize - stepSize
                            if (speed_ >= 0.1f) {
                                speed = speed_
                                sliderPosition = speed2Slider(speed, maxSpeed)
                            }
                        }))
                    Slider(value = sliderPosition, modifier = Modifier.weight(1f).height(10.dp).padding(start = 20.dp, end = 20.dp),
                        onValueChange = {
                            sliderPosition = it
                            speed = slider2Speed(sliderPosition, maxSpeed)
                            Logd("PlaybackSpeedDialog", "slider value: $it $speed}")
                        })
                    Text("+", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = {
                            val speed_ = round(speed / stepSize) * stepSize + stepSize
                            if (speed_ <= maxSpeed) {
                                speed = speed_
                                sliderPosition = speed2Slider(speed, maxSpeed)
                            }
                        }))
                }
                var forCurrent by remember { mutableStateOf(indexDefault == 0) }
                var forPodcast by remember { mutableStateOf(indexDefault == 1) }
                var forGlobal by remember { mutableStateOf(indexDefault == 2) }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = forCurrent, onCheckedChange = { isChecked -> forCurrent = isChecked })
                    Text(stringResource(R.string.current_episode))
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = forPodcast, onCheckedChange = { isChecked -> forPodcast = isChecked })
                    Text(stringResource(R.string.current_podcast))
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = forGlobal, onCheckedChange = { isChecked -> forGlobal = isChecked })
                    Text(stringResource(R.string.global))
                    Spacer(Modifier.weight(1f))
                }
                FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    speeds.forEach { chipSpeed ->
                        FilterChip(onClick = {
                            Logd("VariableSpeedDialog", "holder.chip settingCode0: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                            settingCode[0] = forCurrent
                            settingCode[1] = forPodcast
                            settingCode[2] = forGlobal
                            Logd("VariableSpeedDialog", "holder.chip settingCode: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                            if (playbackService != null) {
                                isSpeedForward = false
                                isFallbackSpeed = false
                                if (settingCode.size == 3) {
                                    Logd(TAG, "setSpeed codeArray: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                                    if (settingCode[2]) putPref(AppPrefs.prefPlaybackSpeed, chipSpeed.toString())
                                    if (settingCode[1] && curEpisode?.feed != null) upsertBlk(curEpisode!!.feed!!) { it.playSpeed = chipSpeed }
                                    if (settingCode[0]) {
                                        setCurTempSpeed(chipSpeed)
                                        mPlayer?.setPlaybackParams(chipSpeed, isSkipSilence)
                                    }
                                } else {
                                    setCurTempSpeed(chipSpeed)
                                    mPlayer?.setPlaybackParams(chipSpeed, isSkipSilence)
                                }
                            }
                            else {
                                putPref(AppPrefs.prefPlaybackSpeed, chipSpeed.toString())
                                EventFlow.postEvent(FlowEvent.SpeedChangedEvent(chipSpeed))
                            }
                            onDismiss()
                        }, label = { Text(String.format(Locale.getDefault(), "%.2f", chipSpeed)) }, selected = false,
                            trailingIcon = { Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon",
                                modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                    speeds.remove(chipSpeed)
                                    setPlaybackSpeedArray(speeds)
                                })) })
                    }
                }
                var showMore by remember { mutableStateOf(false) }
                TextButton(onClick = { showMore = !showMore }) { Text("More>>", style = MaterialTheme.typography.headlineSmall) }
                if (showMore) {
                    var isSkipSilence_ by remember { mutableStateOf(isSkipSilence) }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isSkipSilence_, onCheckedChange = { isChecked ->
                            isSkipSilence_ = isChecked
                            isSkipSilence = isSkipSilence_
                            mPlayer?.setPlaybackParams(mPlayer!!.getPlaybackSpeed(), isChecked)
                        })
                        Text(stringResource(R.string.pref_skip_silence_title))
                    }
                    HorizontalDivider(thickness = 5.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pref_rewind), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        NumberEditor(rewindSecs, modifier = Modifier.weight(0.6f)) { rewindSecs = it }
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pref_fast_forward), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        NumberEditor(fastForwardSecs, modifier = Modifier.weight(0.6f)) { fastForwardSecs = it }
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    Text(stringResource(R.string.pref_fallback_speed), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    SpeedSetter(fallbackSpeed, maxSpeed = 3f) {
                        val speed_ = when {
                            it < 0.0f -> 0.0f
                            it > 3.0f -> 3.0f
                            else -> it
                        }
                        fallbackSpeed = round(100 * speed_) / 100f
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    Text(stringResource(R.string.pref_speed_forward), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    SpeedSetter(speedforwardSpeed, maxSpeed = 10f) {
                        val speed_ = when {
                            it < 0.0f -> 0.0f
                            it > 10.0f -> 10.0f
                            else -> it
                        }
                        speedforwardSpeed = round(10 * speed_) / 10
                    }
                }
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
                                    updateFeedFull(context, feed, removeUnlistedItems = false)
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
    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
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
}
