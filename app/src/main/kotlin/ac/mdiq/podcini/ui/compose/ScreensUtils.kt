package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.feeds
import ac.mdiq.podcini.ui.screens.EpisodeInfoScreen
import ac.mdiq.podcini.ui.screens.FacetsScreen
import ac.mdiq.podcini.ui.screens.FeedDetailsScreen
import ac.mdiq.podcini.ui.screens.FeedScreenMode
import ac.mdiq.podcini.ui.screens.FeedsSettingsScreen
import ac.mdiq.podcini.ui.screens.FindFeedsScreen
import ac.mdiq.podcini.ui.screens.LibraryScreen
import ac.mdiq.podcini.ui.screens.LogsScreen
import ac.mdiq.podcini.ui.screens.OnlineFeedScreen
import ac.mdiq.podcini.ui.screens.QueuesScreen
import ac.mdiq.podcini.ui.screens.QuickAccess
import ac.mdiq.podcini.ui.screens.SearchScreen
import ac.mdiq.podcini.ui.screens.StatisticsScreen
import ac.mdiq.podcini.ui.screens.TopChartScreen
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.timeIt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val TAG = "ScreensUtils"

const val COME_BACK = "comeback"

val handleBackSubScreens = mutableStateSetOf<String>()

val LocalNavController = staticCompositionLocalOf<AppNavigator> { error("NavController not provided") }

val defaultScreen: String
    get() {
        if (feeds.isEmpty()) return Screens.FindFeeds.name

        var value = AppPreferences.getPref(AppPreferences.AppPrefs.prefDefaultPage, "")
        Logd(TAG, "get defaultScreen 0: [$value]")
        val isValid = try {
            Screens.valueOf(value)
            true
        } catch (_: Throwable) { false }
        if (value == AppPreferences.DefaultPages.Remember.name) {
            value = appAttribs.prefLastScreen
            Logd(TAG, "get defaultScreen 1: [$value]")
            if (value.isBlank()) value = Screens.Library.name
            if (value == Screens.FeedDetails.name) {
                val feedId = appAttribs.prefLastScreenArg.toLongOrNull()
                if (feedId != null) value = "${Screens.FeedDetails.name}?feedId=${feedId}"
            }
        } else if (value.isBlank() || !isValid) value = Screens.Library.name
        Logd(TAG, "get defaultScreen: [$value]")
        return value
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Navigate(navController: NavHostController, startScreen: String = "") {
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
    Logd(TAG, "Navigate startScreen: $startScreen")
    timeIt("$TAG start of Navigate")
    var startScreen = startScreen
    if (startScreen.isBlank()) {
        val dfs = defaultScreen
        startScreen = if (isValid(dfs)) dfs else Screens.Library.name
    }
    Logd(TAG, "Navigate startScreen 1: $startScreen")
    NavHost(navController = navController, startDestination = startScreen) { // TODO: defaultScreen
        composable(Screens.Library.name) { LibraryScreen() }
        composable(route = "${Screens.Queues.name}?index={index}", arguments = listOf(navArgument("index") {
            type = NavType.LongType
            defaultValue = -1L
        })) { entry ->
            val index = entry.arguments?.getLong("index") ?: -1L
            QueuesScreen(index)
        }
        composable(route = "${Screens.FeedDetails.name}?feedId={feedId}&modeName={modeName}", arguments = listOf(navArgument("feedId") {
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
        composable(route = "${Screens.EpisodeInfo.name}?episodeId={episodeId}", arguments = listOf(navArgument("episodeId") {
            type = NavType.LongType
            defaultValue = -1L
        })) { entry ->
            val episodeId = entry.arguments?.getLong("episodeId") ?: -1L
            EpisodeInfoScreen(episodeId)
        }
        composable("${Screens.Facets.name}?modeName={modeName}", arguments = listOf(navArgument("modeName") {
            type = NavType.StringType
            defaultValue = QuickAccess.New.name
        })) { entry ->
            val modeName = entry.arguments?.getString("modeName") ?: QuickAccess.New.name
            FacetsScreen(modeName)
        }
        composable(Screens.Search.name) { SearchScreen() }
        composable(Screens.TopChart.name) { TopChartScreen() }
        composable(route = "${Screens.OnlineFeed.name}?url={url}&source={source}&shared={shared}", arguments = listOf(navArgument("url") {
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
        composable(Screens.FindFeeds.name) { FindFeedsScreen() }
        composable(Screens.Logs.name) { LogsScreen() }
        composable(Screens.Statistics.name) { StatisticsScreen() }
    }
    timeIt("$TAG start of Navigate")
}

enum class Screens {
    Library,
    FeedDetails,
    FeedsSettings,
    Facets,
    EpisodeInfo,
    Queues,
    Search,
    FindFeeds,
    OnlineFeed,
    TopChart,
    Logs,
    Statistics
}

fun NavHostController.safeNavigate(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    try {
        this.navigate(route, builder)
    } catch (e: IllegalArgumentException) {
        Loge(TAG, "Navigation failed: ${e.message}")
        this.navigate(Screens.Library.name, builder)
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
            Loge(TAG, "navigate invalid route: $route. Open Library")
            route = Screens.Library.name
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