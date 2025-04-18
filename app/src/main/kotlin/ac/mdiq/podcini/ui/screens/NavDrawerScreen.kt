package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.getFeedCount
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.EpisodeFilter.Companion.unfiltered
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.closeDrawer
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.drawerState
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.isBSExpanded
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.openDrawer
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import io.github.xilinjia.krdb.query.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "NavDrawerScreen"

val defaultScreen: String
    get() {
        var value = getPref(AppPrefs.prefDefaultPage, "")
        val isValid = try {
            Screens.valueOf(value)
            true
        } catch (e: Throwable) { false }
        if (value.isBlank() || !isValid) value = Screens.Subscriptions.name
        if (value == AppPreferences.DefaultPages.Remember.name) {
            value = getPref(AppPrefs.prefLastScreen, "")
            if (value.isBlank()) value = Screens.Subscriptions.name
            if (value == Screens.FeedDetails.name) {
                val feedId = getPref(AppPrefs.prefLastScreenArg, "0L").toLongOrNull()
                if (feedId != null) feedOnDisplay = getFeed(feedId) ?: Feed()
            }
        }
        Logd(TAG, "get defaultScreen: [$value]")
        return value
    }

class NavDrawerVM(val context: Context, val lcScope: CoroutineScope) {
    internal val feeds = mutableStateListOf<Feed>()

    internal fun getRecentPodcasts() {
        val feeds_ = realm.query(Feed::class).sort("lastPlayed", sortOrder = Sort.DESCENDING).limit(8).find()
        feeds.clear()
        feeds.addAll(feeds_)
    }

    fun loadData() {
        lcScope.launch {
            withContext(Dispatchers.IO) { getDatasetStats() }
            withContext(Dispatchers.Main) { getRecentPodcasts() }
        }
    }
}

@Composable
fun NavDrawerScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { NavDrawerVM(context, scope) }
    var curruntRoute: String

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> vm.getRecentPodcasts()
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> vm.loadData()
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

    fun loadScreen(tag: String?, args: Bundle?, popto: Boolean = false) {
        var tag = tag
//        val args = args
        Logd(TAG, "loadScreen(tag: $tag, args: $args, popto: $popto)")
        when (tag) {
            Screens.Subscriptions.name, Screens.Queues.name, Screens.Logs.name, Screens.OnlineSearch.name, Screens.Facets.name, Screens.Statistics.name -> {
                if (popto) mainNavController.navigate(tag) { popUpTo(0) { inclusive = true } }
                else mainNavController.navigate(tag)
            }
            Screens.FeedDetails.name -> {
                if (args == null) {
                    val feedId = getPref(AppPrefs.prefLastScreenArg, "0L").toLongOrNull()
                    if (feedId != null) {
                        val feed = getFeed(feedId)
                        if (feed != null) {
                            feedOnDisplay = feed
                            if (popto) mainNavController.navigate(tag) { popUpTo(0) { inclusive = true } }
                            else mainNavController.navigate(tag)
                        }
                    } else mainNavController.navigate(Screens.Subscriptions.name)
                } else mainNavController.navigate(Screens.Subscriptions.name)
            }
            else -> {
                tag = Screens.Subscriptions.name
                mainNavController.navigate(tag)
            }
        }
        runOnIOScope { saveLastNavScreen(tag) }
    }

    BackHandler(enabled = true) {
        Logd(TAG, "BackHandler: $isBSExpanded")
        val openDrawer = getPref(AppPrefs.prefBackButtonOpensDrawer, false)
        val defPage = defaultScreen
        val currentDestination = mainNavController.currentDestination
        curruntRoute = currentDestination?.route ?: ""
        Logd(TAG, "BackHandler curruntRoute0: $curruntRoute")
        when {
            drawerState.isOpen -> closeDrawer()
            isBSExpanded -> isBSExpanded = false
            mainNavController.previousBackStackEntry != null -> {
                mainNavController.popBackStack()
                curruntRoute = currentDestination?.route ?: ""
                runOnIOScope { saveLastNavScreen(curruntRoute) }
                Logd(TAG, "BackHandler curruntRoute: [$curruntRoute]")
            }
            defPage.isNotBlank() && curruntRoute.isNotBlank() && curruntRoute != defPage -> {
                loadScreen(defPage, null, true)
                curruntRoute = currentDestination?.route ?: ""
                Logd(TAG, "BackHandler curruntRoute1: [$curruntRoute]")
            }
            openDrawer -> openDrawer()
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val drawerWidth = screenWidth * 0.7f

    Box(Modifier.width(drawerWidth).fillMaxHeight()) {
        Scaffold { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp).background(MaterialTheme.colorScheme.surface),
                verticalArrangement = Arrangement.spacedBy(15.dp)) {
                val textColor = MaterialTheme.colorScheme.onSurface
                for (nav in navMap.entries) {
                    if (nav.value.show) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        mainNavController.navigate(nav.key) {
                            if (nav.key in listOf(Screens.Subscriptions.name, Screens.Queues.name, Screens.Facets.name)) popUpTo(0) { inclusive = true }
                            else popUpTo(nav.key) { inclusive = true }
                        }
                        closeDrawer()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(nav.value.iconRes), tint = textColor, contentDescription = nav.key, modifier = Modifier.padding(start = 10.dp))
                        Text(stringResource(nav.value.nameRes), color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 20.dp))
                        Spacer(Modifier.weight(1f))
                        if (nav.value.count > 0) Text(nav.value.count.toString(), color = textColor, modifier = Modifier.padding(end = 10.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                val lazyListState = rememberLazyListState()
                LazyColumn(state = lazyListState) {
                    itemsIndexed(vm.feeds) { _, f ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp).clickable {
                            feedOnDisplay = f
                            feedScreenMode = FeedScreenMode.List
                            mainNavController.navigate(Screens.FeedDetails.name) { popUpTo(Screens.FeedDetails.name) { inclusive = true } }
                            closeDrawer()
                            isBSExpanded = false
                        }) {
                            AsyncImage(model = f.imageUrl, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher), modifier = Modifier.width(40.dp).height(40.dp))
                            Text(f.title ?: "No title", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                    context.startActivity(Intent(context, PreferenceActivity::class.java))
                    closeDrawer()
                }) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings), tint = textColor, contentDescription = "settings", modifier = Modifier.padding(start = 10.dp))
                    Text(stringResource(R.string.settings_label), color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 20.dp))
                }
            }
        }
    }
}

var feedCount: Int = -1
    get() {
        if (field < 0) field = getFeedCount()
        return field
    }

class NavItem(val iconRes: Int, val nameRes: Int) {
    var count by mutableIntStateOf(0)
    var show by mutableStateOf(true)
}

enum class Screens {
    Subscriptions,
    FeedDetails,
    FeedSettings,
    Facets,
    EpisodeInfo,
    EpisodeText,
    Queues,
    Search,
    OnlineSearch,
    OnlineFeed,
    OnlineEpisodes,
    Discovery,
    SearchResults,
    Logs,
    Statistics
}

private val navMap: LinkedHashMap<String, NavItem> = linkedMapOf(
    Screens.Subscriptions.name to NavItem(R.drawable.ic_subscriptions, R.string.subscriptions_label),
    Screens.Queues.name to NavItem(R.drawable.ic_playlist_play, R.string.queue_label),
    Screens.Facets.name to NavItem(R.drawable.baseline_view_in_ar_24, R.string.facets),
    Screens.Logs.name to NavItem(R.drawable.ic_history, R.string.logs_label),
    Screens.Statistics.name to NavItem(R.drawable.ic_chart_box, R.string.statistics_label),
    Screens.OnlineSearch.name to NavItem(R.drawable.ic_add, R.string.add_feed_label)
)

private val navHostMap: HashMap<Screens, @Composable ()->Unit> = hashMapOf(
    Screens.Subscriptions to { SubscriptionsScreen() },
    Screens.FeedDetails to { FeedDetailsScreen() },
    Screens.FeedSettings to { FeedSettingsScreen() },
    Screens.EpisodeInfo to { EpisodeInfoScreen() },
    Screens.EpisodeText to { EpisodeTextScreen() },
    Screens.Facets to { FacetsScreen() },
    Screens.Queues to { QueuesScreen() },
    Screens.Search to { SearchScreen() },
    Screens.OnlineSearch to { OnlineSearchScreen() },
    Screens.Discovery to { DiscoveryScreen() },
    Screens.OnlineFeed to { OnlineFeedScreen() },
    Screens.SearchResults to { SearchResultsScreen() },
    Screens.Logs to { LogsScreen() },
    Screens.Statistics to { StatisticsScreen() }
)

@Composable
fun Navigate(navController: NavHostController) {
    NavHost(navController = navController, startDestination = defaultScreen) {
        for (nv in navHostMap.entries) composable(nv.key.name) { nv.value() }
    }
}

fun saveLastNavScreen(tag: String?, arg: String? = null) {
    Logd(TAG, "saveLastNavScreen(tag: $tag)")
    putPref(AppPrefs.prefLastScreen, tag ?:"")
    if (arg == null && tag in listOf(Screens.FeedDetails.name, Screens.FeedSettings.name)) {
        val arg_ = feedOnDisplay.id.toString()
        putPref(AppPrefs.prefLastScreenArg, arg_)
    } else putPref(AppPrefs.prefLastScreenArg, arg ?:"")
}

/**
 * Returns data necessary for displaying the navigation drawer. This includes
 * the number of downloaded episodes, the number of episodes in the queue, the number of total episodes, and number of subscriptions
 */
fun getDatasetStats() {
    Logd(TAG, "getNavDrawerData() called")
    val numItems = getEpisodesCount(unfiltered())
    feedCount = getFeedCount()
    navMap[Screens.Queues.name]?.count = realm.query(PlayQueue::class).find().sumOf { it.size()}
    navMap[Screens.Subscriptions.name]?.count = feedCount
    navMap[Screens.Facets.name]?.count = numItems
    navMap[Screens.Logs.name]?.count = realm.query(ShareLog::class).count().find().toInt() +
            realm.query(SubscriptionLog::class).count().find().toInt() +
            realm.query(DownloadResult::class).count().find().toInt()
}


