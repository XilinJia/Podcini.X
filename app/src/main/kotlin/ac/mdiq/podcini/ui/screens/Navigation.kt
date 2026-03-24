package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.allFeeds
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.ui.screens.DefaultPages.Companion.toNavKey
import ac.mdiq.podcini.ui.screens.prefscreens.PrefsScreen
import ac.mdiq.podcini.utils.Logd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import kotlinx.serialization.Serializable

private const val TAG = "ScreensUtils"

val handleBackSubScreens = mutableStateSetOf<String>()

val backStack = mutableStateListOf(defaultNavKey)

private const val MAX_STACK = 10

enum class PopMode {
    None,
    UpTo,
    Clear
}

fun navTo(key: NavKey, popMode: PopMode = PopMode.None) {
    if (popMode != PopMode.None) {
        var from = if (popMode == PopMode.UpTo) {
            val index = backStack.indexOfFirst { it.javaClass == key.javaClass }
            if (index >= 0) index else 0
        } else 0
        val toKeep = backStack.subList(from, backStack.size).filter { it::class in setOf(Library::class, Queues::class) }
        backStack.subList(from, backStack.size).clear()
        backStack.addAll(toKeep)
    }
    if (backStack.lastOrNull() != key) backStack.add(key)
    if (backStack.size > MAX_STACK + 5) backStack.removeRange(0, 5)
}

fun navBack(): Boolean {
    if (backStack.size > 1) {
        backStack.removeLastOrNull()
        return true
    }
    return false
}

@Serializable
sealed class NavKey

@Serializable
data object Library : NavKey()

@Serializable
data class Queues(val id: Long = -1L) : NavKey()

@Serializable
data class FeedDetails(val feedId: Long = -1L, val modeName: String = FeedScreenMode.List.name) : NavKey()

@Serializable
data object FeedsSettings : NavKey()

@Serializable
data class EpisodeInfo(val episodeId: Long = -1L) : NavKey()

@Serializable
data class Facets(val modeName: String = QuickAccess.None.name) : NavKey()

@Serializable
data object Search : NavKey()

@Serializable
data object TopChart : NavKey()

@Serializable
data class OnlineFeed(val url: String = "Error", val source: String = "", val shared: Boolean = false) : NavKey()

@Serializable
data object FindFeeds : NavKey()

@Serializable
data object Logs : NavKey()

@Serializable
data object Statistics : NavKey()

@Serializable
data object Settings : NavKey()

val defaultNavKey: NavKey
    get() {
        if (allFeeds.isEmpty()) return FindFeeds
        val value = appPrefs.defaultPage
        Logd(TAG, "get defaultScreen defaultPage: [$value]")
        fun isValid(): Boolean = runCatching { Screens.valueOf(value) }.isSuccess
        if (value.isBlank() || !isValid()) return Library
        Logd(TAG, "get defaultScreen value: [$value]")
        return toNavKey(value)
    }

@OptIn(ExperimentalMaterial3Api::class)
val myEntryProvider = entryProvider {
    entry<Library>{ LibraryScreen() }
    entry<Queues>{ k-> QueuesScreen(k.id) }
    entry<FeedDetails>{ k-> FeedDetailsScreen(k.feedId, k.modeName) }
    entry<FeedsSettings>{ FeedsSettingsScreen() }
    entry<EpisodeInfo>{ k-> EpisodeInfoScreen(k.episodeId) }
    entry<Facets>{ k-> FacetsScreen(k.modeName) }
    entry<Search>{ SearchScreen() }
    entry<TopChart>{ TopChartScreen() }
    entry<OnlineFeed>{ k-> OnlineFeedScreen(k.url, k.source, k.shared) }
    entry<FindFeeds>{ FindFeedsScreen() }
    entry<Logs>{ LogsScreen() }
    entry<Statistics>{ StatisticsScreen() }
    entry<Settings>{ PrefsScreen() }
}

val anyEntryProvider: (Any) -> NavEntry<Any> = { key ->
    myEntryProvider(key as NavKey) as NavEntry<Any>
}

enum class DefaultPages(val res: Int) {
    Library(R.string.library),
    Queues(R.string.queue_label),
    Facets(R.string.facets),
    OnlineSearch(R.string.add_feed_label),
    Statistics(R.string.statistics_label);

    companion object {
        @Suppress("RemoveRedundantQualifierName")
        fun toNavKey(p: String): NavKey {
            return when (p) {
                Library.name -> ac.mdiq.podcini.ui.screens.Library
                Queues.name -> Queues()
                Facets.name -> Facets()
                OnlineSearch.name -> FindFeeds
                Statistics.name -> ac.mdiq.podcini.ui.screens.Statistics
                else -> ac.mdiq.podcini.ui.screens.Library
            }
        }
    }
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
    Statistics,
    Settings
}
