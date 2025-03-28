package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
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
import ac.mdiq.podcini.util.Logs
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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

private lateinit var prefs: SharedPreferences

class NavDrawerVM(val context: Context, val lcScope: CoroutineScope) {
    internal val feeds = mutableStateListOf<Feed>()

    internal fun getRecentPodcasts() {
        var feeds_ = realm.query(Feed::class).sort("lastPlayed", sortOrder = Sort.DESCENDING).limit(5).find()
        feeds.clear()
        feeds.addAll(feeds_)
    }

    init {
        prefs = context.getSharedPreferences("NavDrawerPrefs", Context.MODE_PRIVATE)
    }

    fun loadData() {
        lcScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getRecentPodcasts()
                    getDatasetStats()
                }
            } catch (e: Throwable) { Logs(TAG, e) }
        }
    }
}

@Composable
fun NavDrawerScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { NavDrawerVM(context, scope) }

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

    fun loadScreen(tag: String?, args: Bundle?) {
        var tag = tag
        var args = args
        Logd(TAG, "loadFragment(tag: $tag, args: $args)")
        when (tag) {
            Screens.Subscriptions.name, Screens.Queues.name, Screens.Logs.name, Screens.OnlineSearch.name, Screens.Facets.name, Screens.Statistics.name ->
                mainNavController.navigate(tag)
            Screens.FeedDetails.name -> {
                if (args == null) {
                    val feedId = getLastNavScreenArg().toLongOrNull()
                    if (feedId != null) {
                        val feed = getFeed(feedId)
                        if (feed != null) {
                            feedOnDisplay = feed
                            mainNavController.navigate(tag)
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
        val toPage = getPref(AppPrefs.prefDefaultPage, "")
        val openDrawer = getPref(AppPrefs.prefBackButtonOpensDrawer, false)
        when {
            drawerState.isOpen -> closeDrawer()
            isBSExpanded -> isBSExpanded = false
            mainNavController.previousBackStackEntry != null -> mainNavController.popBackStack()
            toPage.isNotBlank() && getLastNavScreen() != toPage && AppPreferences.DefaultPages.Remember.name != toPage -> loadScreen(toPage, null)
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
                        mainNavController.navigate(nav.key) { popUpTo(nav.key) { inclusive = true } }
                        closeDrawer()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(nav.value.iconRes), tint = textColor, contentDescription = nav.key, modifier = Modifier.padding(start = 10.dp))
//                    val nametag = if (nav.tag != QueuesFragment.TAG) stringResource(nav.nameRes) else curQueue.name
                        Text(stringResource(nav.value.nameRes), color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 20.dp))
                        Spacer(Modifier.weight(1f))
                        if (nav.value.count > 0) Text(nav.value.count.toString(), color = textColor, modifier = Modifier.padding(end = 10.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                Column {
                    for (f in vm.feeds) {
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp).clickable {
                            feedOnDisplay = f
                            feedScreenMode = FeedScreenMode.List
                            mainNavController.navigate(Screens.FeedDetails.name) { popUpTo(Screens.FeedDetails.name) { inclusive = true } }
                            closeDrawer()
                            isBSExpanded = false
                        }) {
                            AsyncImage(model = f.imageUrl, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                                modifier = Modifier.width(40.dp).height(40.dp))
                            Text(f.title ?: "No title", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
//            Text("Formal listing on Google Play has been approved - many thanks to all for the kind support!", color = textColor,
//                modifier = Modifier.clickable(onClick = {}))
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

const val PREF_LAST_FRAGMENT_TAG: String = "prefLastFragmentTag"
const val PREF_LAST_FRAGMENT_ARG: String = "prefLastFragmentArg"

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

val navMap: LinkedHashMap<String, NavItem> = linkedMapOf(
    Screens.Subscriptions.name to NavItem(R.drawable.ic_subscriptions, R.string.subscriptions_label),
    Screens.Queues.name to NavItem(R.drawable.ic_playlist_play, R.string.queue_label),
    Screens.Facets.name to NavItem(R.drawable.baseline_view_in_ar_24, R.string.facets),
    Screens.Logs.name to NavItem(R.drawable.ic_history, R.string.logs_label),
    Screens.Statistics.name to NavItem(R.drawable.ic_chart_box, R.string.statistics_label),
    Screens.OnlineSearch.name to NavItem(R.drawable.ic_add, R.string.add_feed_label)
)

@Composable
fun Navigate(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screens.Subscriptions.name) {
        composable(Screens.Subscriptions.name) { SubscriptionsScreen() }
        composable(Screens.FeedDetails.name) { FeedDetailsScreen() }
        composable(Screens.FeedSettings.name) { FeedSettingsScreen() }
        composable(Screens.EpisodeInfo.name) { EpisodeInfoScreen() }
        composable(Screens.EpisodeText.name) { EpisodeTextScreen() }
        composable(Screens.Facets.name) { FacetsScreen() }
        composable(Screens.Queues.name) { QueuesScreen() }
        composable(Screens.Search.name) { SearchScreen() }
        composable(Screens.OnlineSearch.name) { OnlineSearchScreen() }
        composable(Screens.Discovery.name) { DiscoveryScreen() }
        composable(Screens.OnlineFeed.name) { OnlineFeedScreen() }
        composable(Screens.SearchResults.name) { SearchResultsScreen() }
        composable(Screens.Logs.name) { LogsScreen() }
        composable(Screens.Statistics.name) { StatisticsScreen() }
        composable("DefaultPage") { SubscriptionsScreen() }
    }
}

fun saveLastNavScreen(tag: String?, arg: String? = null) {
    Logd(TAG, "saveLastNavScreen(tag: $tag)")
    val edit: SharedPreferences.Editor? = prefs.edit()
    if (tag != null) {
        edit?.putString(PREF_LAST_FRAGMENT_TAG, tag)
        if (arg != null) edit?.putString(PREF_LAST_FRAGMENT_ARG, arg)
    }
    else edit?.remove(PREF_LAST_FRAGMENT_TAG)
    edit?.apply()
}

fun getLastNavScreen(): String = prefs.getString(PREF_LAST_FRAGMENT_TAG, "") ?: ""
fun getLastNavScreenArg(): String = prefs.getString(PREF_LAST_FRAGMENT_ARG, "0") ?: ""

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


