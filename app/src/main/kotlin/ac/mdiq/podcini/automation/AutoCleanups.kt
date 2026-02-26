package ac.mdiq.podcini.automation

import ac.mdiq.podcini.storage.database.EPISODE_CACHE_SIZE_UNLIMITED
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.deleteMedias
import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.getEpisodesCount
import ac.mdiq.podcini.storage.database.inQueueEpisodeIdSet
import ac.mdiq.podcini.storage.database.removeFromAllQueues
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.ui.screens.prefscreens.EpisodeCleanupOptions
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.storage.utils.nowInMillis
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private const val TAG: String = "AutoCleanups"

fun cleanupAlgorithm(): EpisodeCleanupAlgorithm {
    if (!appPrefs.enableAutoDl) return APNullCleanupAlgorithm()
    val cleanupValue = appPrefs.episodeCleanup.toIntOrNull() ?: EpisodeCleanupOptions.Never.num
    return when (cleanupValue) {
        EpisodeCleanupOptions.ExceptFavorites.num -> ExceptFavoriteCleanupAlgorithm()
        EpisodeCleanupOptions.NotInQueue.num -> APQueueCleanupAlgorithm()
        EpisodeCleanupOptions.Never.num -> APNullCleanupAlgorithm()
        else -> APCleanupAlgorithm(cleanupValue)
    }
}

/**
 * A cleanup algorithm that removes any item that isn't a favorite but only if space is needed.
 */
class ExceptFavoriteCleanupAlgorithm : EpisodeCleanupAlgorithm() {
    private val candidates: List<Episode>
        get() {
            val candidates: MutableList<Episode> = mutableListOf()
            val downloadedItems = getEpisodes(EpisodeFilter(EpisodeFilter.States.downloaded.name), EpisodeSortOrder.DATE_DESC)
            for (item in downloadedItems) if (item.downloaded && item.rating < Rating.GOOD.code) candidates.add(item)
            return candidates
        }
    override fun getReclaimableItems(): Int {
        return candidates.size
    }

    public override fun performCleanup(numToRemove: Int): Int {
        var candidates = candidates
        // in the absence of better data, we'll sort by item publication date
        candidates = candidates.sortedWith { lhs: Episode, rhs: Episode ->
            val l = lhs.pubDate
            val r = rhs.pubDate
            if (l != r) return@sortedWith l.compareTo(r)
            else return@sortedWith lhs.id.compareTo(rhs.id)  // No date - compare by id which should be always incremented
        }
        return cleanup(candidates, numToRemove)
    }
    public override fun getDefaultCleanupParameter(): Int {
        val cacheSize = appPrefs.episodeCacheSize
        if (cacheSize > EPISODE_CACHE_SIZE_UNLIMITED) {
            val downloadedEpisodes = getEpisodesCount(EpisodeFilter(EpisodeFilter.States.downloaded.name))
            if (downloadedEpisodes > cacheSize) return downloadedEpisodes - cacheSize
        }
        return 0
    }
}

/**
 * A cleanup algorithm that removes any item that isn't in the queue and isn't a favorite
 * but only if space is needed.
 */
class APQueueCleanupAlgorithm : EpisodeCleanupAlgorithm() {
    private val candidates: List<Episode>
        get() {
            val candidates: MutableList<Episode> = mutableListOf()
            val downloadedItems = getEpisodes(EpisodeFilter(EpisodeFilter.States.downloaded.name), EpisodeSortOrder.DATE_DESC)
            val idsInQueues = inQueueEpisodeIdSet()
            for (item in downloadedItems) if (item.downloaded && !idsInQueues.contains(item.id) && item.rating < Rating.GOOD.code) candidates.add(item)
            return candidates
        }
    override fun getReclaimableItems(): Int {
        return candidates.size
    }
    public override fun performCleanup(numToRemove: Int): Int {
        var candidates = candidates
        // in the absence of better data, we'll sort by item publication date
        candidates = candidates.sortedWith { lhs: Episode, rhs: Episode ->
            val l = lhs.pubDate
            val r = rhs.pubDate
            l.compareTo(r)
        }
        return cleanup(candidates, numToRemove)
    }
    public override fun getDefaultCleanupParameter(): Int {
        return getNumEpisodesToCleanup(0)
    }
}

/**
 * A cleanup algorithm that never removes anything
 */
class APNullCleanupAlgorithm : EpisodeCleanupAlgorithm() {
    public override fun performCleanup(numToRemove: Int): Int {
        // never clean anything up
        Logt(TAG, "performCleanup: Not removing anything")
        return 0
    }
    public override fun getDefaultCleanupParameter(): Int {
        return 0
    }
    override fun getReclaimableItems(): Int {
        return 0
    }
}

/** the number of days after playback to wait before an item is eligible to be cleaned up.
 * Fractional for number of hours, e.g., 0.5 = 12 hours, 0.0416 = 1 hour.   */

class APCleanupAlgorithm( val numberOfHoursAfterPlayback: Int) : EpisodeCleanupAlgorithm() {
    private val candidates: List<Episode>
        get() {
            val candidates: MutableList<Episode> = mutableListOf()
            val downloadedItems = getEpisodes(EpisodeFilter(EpisodeFilter.States.downloaded.name), EpisodeSortOrder.DATE_DESC)
            val idsInQueues = inQueueEpisodeIdSet()
            val mostRecentDateForDeletion = Clock.System.now().minusHours(numberOfHoursAfterPlayback).toEpochMilliseconds()
            for (item in downloadedItems) {
                if (item.downloaded && !idsInQueues.contains(item.id) && item.playState >= EpisodeState.PLAYED.code && item.rating < Rating.GOOD.code) {
                    // make sure this candidate was played at least the proper amount of days prior to now
                    if (item.playbackCompletionTime < mostRecentDateForDeletion) candidates.add(item)
                }
            }
            return candidates
        }
    override fun getReclaimableItems(): Int {
        return candidates.size
    }
    public override fun performCleanup(numToRemove: Int): Int {
        val candidates = candidates.toMutableList()
        candidates.sortWith { lhs: Episode, rhs: Episode ->
            val l = lhs.playbackCompletionTime
            val r = rhs.playbackCompletionTime
            l.compareTo(r)
        }
        return cleanup(candidates, numToRemove)
    }

    fun Instant.minusHours(count: Int): Instant = this.minus(count.hours)

    public override fun getDefaultCleanupParameter(): Int = getNumEpisodesToCleanup(0)
}

abstract class EpisodeCleanupAlgorithm {
    /**
     * Deletes downloaded episodes that are no longer needed. What episodes are deleted and how many
     * of them depends on the implementation.
     * @param context     Can be used for accessing the database
     * @param numToRemove An additional parameter. This parameter is either returned by getDefaultCleanupParameter
     * or getPerformCleanupParameter.
     * @return The number of episodes that were deleted.
     */
    protected abstract fun performCleanup(numToRemove: Int): Int

    protected fun cleanup(candidates: List<Episode>, numToRemove: Int): Int {
        val delete = if (candidates.size > numToRemove) candidates.subList(0, numToRemove) else candidates
        try {
            deleteMedias(delete)
            if (appPrefs.deleteRemovesFromQueue) removeFromAllQueues(delete)
        }  catch (e: Throwable) { Logs(TAG, e) }
        val counter = delete.size
        Logt(TAG, "Auto-delete deleted $counter episodes ($numToRemove requested)")
        return counter
    }

    /**
     * Returns a parameter for performCleanup. The implementation of this interface should decide how much
     * space to free to satisfy the episode cache conditions. If the conditions are already satisfied, this
     * method should not have any effects.
     */
    protected abstract fun getDefaultCleanupParameter(): Int
    /**
     * Cleans up just enough episodes to make room for the requested number
     * @param amountOfRoomNeeded the number of episodes we need space for
     * @return The number of epiosdes that were deleted
     */
    fun makeRoomForEpisodes(amountOfRoomNeeded: Int): Int {
        val numToRemove = getNumEpisodesToCleanup(amountOfRoomNeeded)
        Logt("EpisodeCleanupAlgorithm", "makeRoomForEpisodes: $numToRemove")
        if (numToRemove <= 0) return 0
        return performCleanup(numToRemove)
    }
    /**
     * @return the number of episodes/items that *could* be cleaned up, if needed
     */
    abstract fun getReclaimableItems(): Int
    /**
     * @param amountOfRoomNeeded the number of episodes we want to download
     * @return the number of episodes to delete in order to make room
     */
    fun getNumEpisodesToCleanup(amountOfRoomNeeded: Int): Int {
        if (amountOfRoomNeeded >= 0 && appPrefs.episodeCacheSize > EPISODE_CACHE_SIZE_UNLIMITED) {
            val downloadedEpisodes = getEpisodesCount(EpisodeFilter(EpisodeFilter.States.downloaded.name))
            if (downloadedEpisodes + amountOfRoomNeeded >= appPrefs.episodeCacheSize) return (downloadedEpisodes + amountOfRoomNeeded - appPrefs.episodeCacheSize)
        }
        return 0
    }
}

