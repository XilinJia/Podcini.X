package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayQueue

import ac.mdiq.podcini.ui.compose.PodciniTheme
import ac.mdiq.podcini.ui.screens.QuickAccess
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShortcutsActivity : ComponentActivity() {
    private val feedItems = mutableStateListOf<Feed>()
    private val queueList = mutableStateListOf<PlayQueue>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val componentName = intent.component?.className ?: ""
        val addFeed = componentName.endsWith("FeedAlias")
        val addQueue = componentName.endsWith("QueueAlias")
        val addFacet = componentName.endsWith("FacetAlias")

        setContent {
            PodciniTheme {
                Card(modifier = Modifier.padding(vertical = 30.dp, horizontal = 16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column {
                        val lazyListState = rememberLazyListState()
                        when {
                            addFeed -> {
                                Text(stringResource(R.string.select_podcast), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                                var checkedIndex by remember { mutableIntStateOf(-1) }
                                LazyColumn(state = lazyListState, modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(feedItems) { index, item ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = { checkedIndex = index })) {
                                            var checked by remember { mutableStateOf(false) }
                                            Checkbox(checked = checkedIndex == index, onCheckedChange = {
                                                checkedIndex = index
                                                checked = it
                                            })
                                            Text(item.title ?: "No title", color = textColor, style = MaterialTheme.typography.titleSmall)
                                        }
                                    }
                                }
                                Button(onClick = { if (checkedIndex >= 0) createFeedShortcut(feedItems[checkedIndex]) }) { Text(stringResource(R.string.add_shortcut)) }
                            }
                            addQueue -> {
                                Text(stringResource(R.string.select_queue), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                                var checkedIndex by remember { mutableIntStateOf(-1) }
                                LazyColumn(state = lazyListState, modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(queueList) { index, item ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = { checkedIndex = index })) {
                                            var checked by remember { mutableStateOf(false) }
                                            Checkbox(checked = checkedIndex == index, onCheckedChange = {
                                                checkedIndex = index
                                                checked = it
                                            })
                                            Text(item.name, color = textColor, style = MaterialTheme.typography.titleSmall)
                                        }
                                    }
                                }
                                Button(onClick = { if (checkedIndex >= 0) createQueueShortcut(queueList[checkedIndex]) }) { Text(stringResource(R.string.add_shortcut)) }
                            }
                            addFacet -> {
                                Text(stringResource(R.string.select_facet), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                                var checkedName by remember { mutableStateOf("") }
                                LazyColumn(state = lazyListState, modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(QuickAccess.entries) { index, item ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = { checkedName = item.name })) {
                                            var checked by remember { mutableStateOf(false) }
                                            Checkbox(checked = checkedName == item.name, onCheckedChange = {
                                                checkedName = item.name
                                                checked = it
                                            })
                                            Text(item.name, color = textColor, style = MaterialTheme.typography.titleSmall)
                                        }
                                    }
                                }
                                Button(onClick = { if (checkedName.isNotEmpty()) createFacetShortcut(checkedName) }) { Text(stringResource(R.string.add_shortcut)) }

                            }
                        }
                    }
                }
            }
        }
        if (addFeed) feedItems.addAll(getFeedList())
        if (addQueue) queueList.addAll(queuesLive)
    }

    fun createFeedShortcut(feed: Feed) {
        val context = this
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val detailIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.Extras.feed_id.name, feed.id)
            }
            CoroutineScope(Dispatchers.IO).launch {
                val request = ImageRequest.Builder(context).data(feed.imageUrl).allowHardware(false).build()
                val result = (ImageLoader(context).execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap

                val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "id_${feed.id}")
                    .setShortLabel(feed.title ?: "No title")
                    .setIcon(if (bitmap != null) IconCompat.createWithBitmap(bitmap) else IconCompat.createWithResource(this@ShortcutsActivity, R.drawable.ic_subscriptions_shortcut))
                    .setIntent(detailIntent)
                    .build()
                ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
            }
        }
    }

    fun createQueueShortcut(queue: PlayQueue) {
        val context = this
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val detailIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.Extras.queue_id.name, queue.id)
            }
            val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "id_${queue.id}")
                .setShortLabel(queue.name)
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_playlist_shortcut))
                .setIntent(detailIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
        }
    }

    fun createFacetShortcut(name: String) {
        val context = this
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val detailIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.Extras.facet_name.name, name)
            }
            val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "id_$name")
                .setShortLabel(name)
                .setIcon(IconCompat.createWithResource(this, R.drawable.baseline_view_in_ar_24))
                .setIntent(detailIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
        }
    }

    companion object {
        private val TAG: String = ShortcutsActivity::class.simpleName ?: "Anonymous"
    }
}