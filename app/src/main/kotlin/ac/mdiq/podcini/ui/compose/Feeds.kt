package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.PodcastSearchResult
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.OpmlTransporter
import ac.mdiq.podcini.storage.database.createSynthetic
import ac.mdiq.podcini.storage.database.deleteFeed
import ac.mdiq.podcini.storage.database.getEpisodesCount
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.formatLargeInteger
import ac.mdiq.podcini.utils.localDateTimeString
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.NumberFormat
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
                        for (f in feeds) {
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
                            val worthyEps = f.getWorthyEpisodes()
                            deleteFeed(f.id, saveImportant && worthyEps.isNotEmpty())
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
fun OpmlImportSelectionDialog(readElements: SnapshotStateList<OpmlTransporter.OpmlElement>, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val selectedItems = remember {  mutableStateMapOf<Int, Boolean>() }
    AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { onDismissRequest() },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssociatedFeedsGrid(feedsAssociated: List<Feed>) {
    val TAG = "AssociatedFeedsGrid"
    val navController = LocalNavController.current
    val context = LocalContext.current
    LazyVerticalGrid(state = rememberLazyGridState(), columns = GridCells.Adaptive(80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)) {
        items(feedsAssociated.size, key = {index -> feedsAssociated[index].id}) { index ->
            val feed by remember { mutableStateOf(feedsAssociated[index]) }
            ConstraintLayout {
                val (coverImage, episodeCount, rating, _) = createRefs()
                val img = remember(feed) { ImageRequest.Builder(context).data(feed.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                AsyncImage(model = img, contentDescription = "coverImage", modifier = Modifier.height(100.dp).aspectRatio(1f)
                    .constrainAs(coverImage) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                    }.combinedClickable(onClick = {
                        Logd(TAG, "clicked: ${feed.title}")
                        navController.navigate("${Screens.FeedDetails.name}?feedId=${feed.id}")
                    }, onLongClick = { Logd(TAG, "long clicked: ${feed.title}") })
                )
                val numEpisodes by remember { mutableIntStateOf(getEpisodesCount(null, feed.id)) }
                Text(NumberFormat.getInstance().format(numEpisodes.toLong()), color = Color.Green,
                    modifier = Modifier.background(Color.Gray).constrainAs(episodeCount) {
                        end.linkTo(parent.end)
                        top.linkTo(coverImage.top)
                    })
                if (feed.rating != Rating.UNRATED.code)
                    Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                        modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).constrainAs(rating) {
                            start.linkTo(parent.start)
                            centerVerticallyTo(coverImage)
                        })
            }
        }
    }
}
