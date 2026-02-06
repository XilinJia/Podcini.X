package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.feedCount
import ac.mdiq.podcini.storage.database.getEpisodesCount
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.storage.specs.EpisodeFilter.Companion.unfiltered
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.bsState
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.LocalNavController
import ac.mdiq.podcini.ui.compose.Screens
import ac.mdiq.podcini.utils.Logd
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import io.github.xilinjia.krdb.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "NavDrawerScreen"

data class FeedBrief(val id: Long, val title: String?, val imageUrl: String?)

val LocalDrawerController = staticCompositionLocalOf<DrawerController?> { null }
val LocalDrawerState = staticCompositionLocalOf<DrawerState> { error("DrawerState not provided") }

@Composable
fun NavDrawerScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navigator = LocalNavController.current
    val drawerCtrl = LocalDrawerController.current
    val drawerState = LocalDrawerState.current

    val textColor = MaterialTheme.colorScheme.onSurface
    var curruntRoute: String

    val feedBriefs = remember { mutableStateListOf<FeedBrief>() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> { }
                Lifecycle.Event.ON_START -> { }
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        Logd(TAG, "LaunchedEffect(drawerState.currentValue): ${drawerState.isOpen}")
        if (drawerState.isOpen) scope.launch(Dispatchers.IO) {
            navMap[Screens.Queues.name]?.count = queuesLive.sumOf { it.size()}
            navMap[Screens.Library.name]?.count = feedCount
            navMap[Screens.Facets.name]?.count = getEpisodesCount(unfiltered())
            navMap[Screens.Logs.name]?.count = realm.query(ShareLog::class).count().find().toInt() +
                    realm.query(SubscriptionLog::class).count().find().toInt() +
                    realm.query(DownloadResult::class).count().find().toInt()
            val feeds_ = realm.query(Feed::class).sort("lastPlayed", sortOrder = Sort.DESCENDING).limit(8).find()
            withContext(Dispatchers.Main) {
                feedBriefs.clear()
                for (f in feeds_) feedBriefs.add(FeedBrief(f.id, f.title, f.imageUrl))
            }
        }
    }

    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowWidthDp = with(density) { windowSize.width.toDp() }
    val drawerWidth = min(350.dp,windowWidthDp * 0.7f)
    val myShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

    ModalDrawerSheet(modifier = Modifier.width(drawerWidth).border(1.dp, MaterialTheme.colorScheme.tertiary, myShape),
        drawerContainerColor = MaterialTheme.colorScheme.surface, drawerTonalElevation = 0.dp, drawerShape = myShape, windowInsets = WindowInsets.systemBars) {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(start = 10.dp, end = 5.dp, top = 10.dp, bottom = 10.dp).verticalScroll(rememberScrollState())) {
            for (nav in navMap.entries) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 15.dp).clickable {
                    Logd(TAG, "nav.key: ${nav.key}")
                    navigator.navigate(nav.key) {
                        if (nav.key in listOf(Screens.Library.name, Screens.Queues.name, Screens.Facets.name)) popUpTo(0) { inclusive = true }
                        else popUpTo(nav.key) { inclusive = true }
                    }
                    drawerCtrl?.close()
                }) {
                    Icon(imageVector = ImageVector.vectorResource(nav.value.iconRes), tint = textColor, contentDescription = nav.key, modifier = Modifier.padding(start = 10.dp))
                    Text(stringResource(nav.value.nameRes), color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 20.dp))
                    Spacer(Modifier.weight(1f))
                    if (nav.value.count > 0) Text(nav.value.count.toString(), color = textColor, modifier = Modifier.padding(end = 10.dp))
                }
            }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth().clickable {
                context.startActivity(Intent(context, PreferenceActivity::class.java))
                drawerCtrl?.close()
            }) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings), tint = textColor, contentDescription = "settings", modifier = Modifier.padding(start = 10.dp))
                Text(stringResource(R.string.settings_label), color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 20.dp))
            }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
            for (f in feedBriefs) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable {
                    navigator.navigate("${Screens.FeedDetails.name}?feedId=${f.id}")
                    drawerCtrl?.close()
                    bsState = MainActivity.BSState.Partial
                }) {
                    AsyncImage(model = f.imageUrl, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher), modifier = Modifier.width(40.dp).height(40.dp))
                    Text(f.title ?: "No title", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                }
            }
            if (bsState == MainActivity.BSState.Hidden) {
                Spacer(Modifier.height(50.dp))
                AsyncImage(model = R.drawable.teaser, contentDescription = "PlayerUI", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxWidth().height(60.dp).clickable(onClick = { bsState = MainActivity.BSState.Partial }))
            }
        }
    }
}

class NavItem(val iconRes: Int, val nameRes: Int) {
    var count by mutableIntStateOf(0)
}

private val navMap: LinkedHashMap<String, NavItem> = linkedMapOf(
    Screens.Library.name to NavItem(R.drawable.ic_subscriptions, R.string.library),
    Screens.Queues.name to NavItem(R.drawable.ic_playlist_play, R.string.queue_label),
    Screens.Facets.name to NavItem(R.drawable.baseline_view_in_ar_24, R.string.facets),
    Screens.Logs.name to NavItem(R.drawable.ic_history, R.string.logs_label),
    Screens.Statistics.name to NavItem(R.drawable.ic_chart_box, R.string.statistics_label),
    Screens.FindFeeds.name to NavItem(R.drawable.ic_add, R.string.add_feed_label)
)

interface DrawerController {
    fun isOpen(): Boolean
    fun open()
    fun close()
    fun toggle()
}