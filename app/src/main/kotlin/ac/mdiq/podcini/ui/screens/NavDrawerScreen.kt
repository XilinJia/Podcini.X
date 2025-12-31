package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.feedCount
import ac.mdiq.podcini.storage.database.getEpisodesCount
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.storage.specs.EpisodeFilter.Companion.unfiltered
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.isBSExpanded
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import io.github.xilinjia.krdb.query.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val TAG = "NavDrawerScreen"

const val COME_BACK = "comeback"

val isRemember: Boolean
    get() = getPref(AppPrefs.prefDefaultPage, "") == AppPreferences.DefaultPages.Remember.name

val defaultScreen: String
    get() {
        var value = getPref(AppPrefs.prefDefaultPage, "")
        Logd(TAG, "get defaultScreen 0: [$value]")
        val isValid = try {
            Screens.valueOf(value)
            true
        } catch (_: Throwable) { false }
        if (value == AppPreferences.DefaultPages.Remember.name) {
            value = appAttribs.prefLastScreen
            Logd(TAG, "get defaultScreen 1: [$value]")
            if (value.isBlank()) value = Screens.Subscriptions.name
            if (value == Screens.FeedDetails.name) {
                val feedId = appAttribs.prefLastScreenArg.toLongOrNull()
                if (feedId != null) value = "${Screens.FeedDetails.name}?feedId=${feedId}"
            }
        } else if (value.isBlank() || !isValid) value = Screens.Subscriptions.name
        Logd(TAG, "get defaultScreen: [$value]")
        return value
    }

var subscreenHandleBack = mutableStateOf(false)

data class FeedBrief(val id: Long, val title: String?, val imageUrl: String?)

val LocalDrawerController = staticCompositionLocalOf<DrawerController> { error("DrawerController not provided") }

@Composable
fun NavDrawerScreen(navigator: AppNavigator) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val drawerState = LocalDrawerController.current

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

    LaunchedEffect(drawerState) {
        Logd(TAG, "LaunchedEffect(drawerState.currentValue): ${drawerState.isOpen()}")
        if (drawerState.isOpen()) scope.launch(Dispatchers.IO) {
            navMap[Screens.Queues.name]?.count = queuesLive.sumOf { it.size()}
            navMap[Screens.Subscriptions.name]?.count = feedCount
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

    fun haveCommonPrefix(a: String, b: String): Boolean {
        val min = minOf(a.length, b.length)
        var count = 0
        for (i in 0 until min) {
            if (a[i] != b[i]) break
            count++
        }
        return count > 0
    }

    BackHandler(enabled = !subscreenHandleBack.value) {
        Logd(TAG, "BackHandler isBSExpanded: $isBSExpanded")
        val openDrawer = getPref(AppPrefs.prefBackButtonOpensDrawer, false)
        val defPage = defaultScreen
        val currentDestination = navigator.currentDestination
        curruntRoute = currentDestination?.route ?: ""
        Logd(TAG, "BackHandler curruntRoute0: $curruntRoute defPage: $defPage")
        when {
            drawerState.isOpen() -> drawerState.close()
            isBSExpanded -> isBSExpanded = false
            navigator.previousBackStackEntry != null -> {
                Logd(TAG, "nav to back")
                navigator.previousBackStackEntry?.savedStateHandle?.set(COME_BACK, true)
                navigator.popBackStack()
                curruntRoute = currentDestination?.route ?: ""
                Logd(TAG, "BackHandler curruntRoute: [$curruntRoute]")
            }
            !isRemember && defPage.isNotBlank() && curruntRoute.isNotBlank() && !haveCommonPrefix(curruntRoute, defPage) -> {
                Logd(TAG, "nav to defPage: $defPage")
                navigator.navigate(defPage) { popUpTo(0) { inclusive = true } }
                curruntRoute = currentDestination?.route ?: ""
                Logd(TAG, "BackHandler curruntRoute1: [$curruntRoute]")
            }
            openDrawer -> drawerState.open()
            else -> Logt(TAG, context.getString(R.string.no_more_screens_back))
        }
    }

    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val windowWidthDp = with(density) { windowSize.width.toDp() }
    val drawerWidth = windowWidthDp * 0.7f

    Box(Modifier.width(drawerWidth).fillMaxHeight()) {
        Scaffold { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp).background(MaterialTheme.colorScheme.surface), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                for (nav in navMap.entries) {
                    if (nav.value.show) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        Logd(TAG, "nav.key: ${nav.key}")
                        navigator.navigate(nav.key) {
                            if (nav.key in listOf(Screens.Subscriptions.name, Screens.Queues.name, Screens.Facets.name)) popUpTo(0) { inclusive = true }
                            else popUpTo(nav.key) { inclusive = true }
                        }
                        drawerState.close()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(nav.value.iconRes), tint = textColor, contentDescription = nav.key, modifier = Modifier.padding(start = 10.dp))
                        Text(stringResource(nav.value.nameRes), color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 20.dp))
                        Spacer(Modifier.weight(1f))
                        if (nav.value.count > 0) Text(nav.value.count.toString(), color = textColor, modifier = Modifier.padding(end = 10.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                LazyColumn(state = rememberLazyListState()) {
                    itemsIndexed(feedBriefs) { _, f ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp).clickable {
                            navigator.navigate("${Screens.FeedDetails.name}?feedId=${f.id}")
                            drawerState.close()
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
                    drawerState.close()
                }) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings), tint = textColor, contentDescription = "settings", modifier = Modifier.padding(start = 10.dp))
                    Text(stringResource(R.string.settings_label), color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 20.dp))
                }
            }
        }
    }
}

class NavItem(val iconRes: Int, val nameRes: Int) {
    var count by mutableIntStateOf(0)
    var show by mutableStateOf(true)
}

enum class Screens {
    Subscriptions,
    FeedDetails,
    FeedsSettings,
    Facets,
    EpisodeInfo,
    Queues,
    Search,
    OnlineSearch,
    OnlineFeed,
    TopChartFeeds,
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

fun isValid(fullRoute: String): Boolean {
    val pathEndIndex = fullRoute.indexOf('/')
    if (pathEndIndex > 0) return false

    val queryStartIndex = fullRoute.indexOf('?')
    val endIndex = when {
        pathEndIndex != -1 && queryStartIndex != -1 -> minOf(pathEndIndex, queryStartIndex)
        pathEndIndex != -1 -> pathEndIndex
        queryStartIndex != -1 -> queryStartIndex
        else -> -1
    }
    val r = if (endIndex != -1) fullRoute.take(endIndex) else { fullRoute }
    return (Screens.entries.any { it.name == r })
}

@Composable
fun Navigate(navController: NavHostController, startScreen: String = "") {
    Logd(TAG, "Navigate startScreen: $startScreen")
    var startScreen = startScreen
    if (startScreen.isBlank()) {
        val dfs = defaultScreen
        startScreen = if (isValid(dfs)) dfs else Screens.Subscriptions.name
    }
    Logd(TAG, "Navigate startScreen 1: $startScreen")
    NavHost(navController = navController, startDestination = startScreen) { // TODO: defaultScreen
        composable(Screens.Subscriptions.name) { SubscriptionsScreen() }
        composable(route = "${Screens.FeedDetails.name}?feedId={feedId}&modeName={modeName}", arguments = listOf(
            navArgument("feedId") {
                type = NavType.LongType
                defaultValue = -1L
            }, navArgument("modeName") {
                type = NavType.StringType
                defaultValue = FeedScreenMode.List.name
        })) { entry ->
            val feedId = entry.arguments?.getLong("feedId") ?: -1L
            val modeName = entry.arguments?.getString("modeName") ?: FeedScreenMode.List.name
            FeedDetailsScreen(feedId, modeName)
        }
        composable(Screens.FeedsSettings.name) { FeedsSettingsScreen() }
        composable(route = "${Screens.EpisodeInfo.name}?episodeId={episodeId}", arguments = listOf(
            navArgument("episodeId") {
                type = NavType.LongType
                defaultValue = -1L
            })) { entry ->
            val episodeId = entry.arguments?.getLong("episodeId") ?: -1L
            EpisodeInfoScreen(episodeId)
        }
        composable(Screens.Facets.name) { FacetsScreen() }
        composable(route = "${Screens.Queues.name}?index={index}", arguments = listOf(navArgument("index") {
            type = NavType.LongType
            defaultValue = -1L
        })) { entry ->
            val index = entry.arguments?.getLong("index") ?: -1L
            QueuesScreen(index)
        }
        composable(Screens.Search.name) { SearchScreen() }
        composable(Screens.TopChartFeeds.name) { TopChartFeeds() }
        composable(route = "${Screens.OnlineFeed.name}?url={url}&source={source}&shared={shared}", arguments = listOf(
            navArgument("url") {
                type = NavType.StringType
                defaultValue = ""
            }, navArgument("source") {
                type = NavType.StringType
                defaultValue = ""
            }, navArgument("shared") {
                type = NavType.BoolType
                defaultValue = false
            })) { entry ->
            val encodedUrl = entry.arguments?.getString("url") ?: "Error"
            val url = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.name())
            val source = entry.arguments?.getString("source") ?: ""
            val shared = entry.arguments?.getBoolean("shared") ?: false
            OnlineFeedScreen(url, source, shared)
        }
        composable(Screens.OnlineSearch.name) { OnlineSearchScreen() }
        composable(Screens.Logs.name) { LogsScreen() }
        composable(Screens.Statistics.name) { StatisticsScreen() }
    }
}

interface DrawerController {
    fun isOpen(): Boolean
    fun open()
    fun close()
    fun toggle()
}

private var navStackJob: Job? = null
fun monitorNavStack(navController: NavHostController) {
    fun NavBackStackEntry.resolvedRoute(): String {
        val template = destination.route ?: return ""
        var resolved = template
        arguments?.keySet()?.forEach { key ->
            val value = arguments?.get(key)?.toString() ?: ""
            resolved = resolved.replace("{$key}", value)
        }
        return resolved
    }
    if (navStackJob == null) navStackJob = CoroutineScope(Dispatchers.Default).launch {
        navController.currentBackStackEntryFlow.collect { entry ->
            val resolved = entry.resolvedRoute()
            runOnIOScope { upsertBlk(appAttribs) { it.prefLastScreen = resolved } }
            Logd(TAG, "currentBackStackEntryFlow Now at: $resolved")
        }
    }
}

fun cancelMonitornavStack() {
    navStackJob?.cancel()
    navStackJob = null
}

fun NavHostController.safeNavigate(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    try {
        this.navigate(route, builder)
    } catch (e: IllegalArgumentException) {
        Loge(TAG, "Navigation failed: ${e.message}")
        this.navigate(Screens.Subscriptions.name, builder)
    }
}

fun NavHostController.routeExists(route: String): Boolean {
    return try { this.graph.findNode(route) != null } catch (e: Exception) { false }
}

class AppNavigator(
    val navController: NavHostController,
    private val onNavigated: (String) -> Unit
) {
    fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
        var route = route
        if (!navController.routeExists(route)) {
            Loge(TAG, "navigate invalid route: $route. Open Subscriptions")
            route = Screens.Subscriptions.name
        }
        onNavigated(route)
        navController.safeNavigate(route, builder)
    }

    val currentDestination:  NavDestination?
        get() = navController.currentDestination

    val previousBackStackEntry: NavBackStackEntry?
        get() = navController.previousBackStackEntry

    fun popBackStack(): Boolean {
        return navController.popBackStack()
    }

    fun popBackStack(route: String, inclusive: Boolean, saveState: Boolean = false): Boolean {
        return navController.popBackStack(route, inclusive, saveState)
    }
}