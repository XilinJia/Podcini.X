package ac.mdiq.podcini.automation

import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.isAutodownloadEnabled
import ac.mdiq.podcini.preferences.screens.EpisodeCleanupOptions
import ac.mdiq.podcini.storage.database.Episodes.deleteAndRemoveFromQueues
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.database.Queues.getInQueueEpisodeIds
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import android.content.Context
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException

object AutoCleanups {
    private val TAG: String = AutoCleanups::class.simpleName ?: "Anonymous"

    @JvmStatic
    fun build(): EpisodeCleanupAlgorithm {
        if (!isAutodownloadEnabled) return APNullCleanupAlgorithm()
        val cleanupValue = getPref(AppPrefs.prefEpisodeCleanup, "0").toIntOrNull() ?: EpisodeCleanupOptions.Never.num
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
                val downloadedItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.downloaded.name), EpisodeSortOrder.DATE_NEW_OLD)
                for (item in downloadedItems) if (item.downloaded && !item.isSUPER) candidates.add(item)
                return candidates
            }
        override fun getReclaimableItems(): Int {
            return candidates.size
        }
        
        public override fun performCleanup(context: Context, numToRemove: Int): Int {
            var candidates = candidates
            // in the absence of better data, we'll sort by item publication date
            candidates = candidates.sortedWith { lhs: Episode, rhs: Episode ->
                val l = lhs.getPubDate()
                val r = rhs.getPubDate()
                if (l != r) return@sortedWith l.compareTo(r)
                else return@sortedWith lhs.id.compareTo(rhs.id)  // No date - compare by id which should be always incremented
            }
            val delete = if (candidates.size > numToRemove) candidates.subList(0, numToRemove) else candidates
            for (item in delete) {
                try { deleteAndRemoveFromQueues(context, item) } catch (e: InterruptedException) { Logs(TAG, e) } catch (e: ExecutionException) { Logs(TAG, e) }
            }
            val counter = delete.size
            Logt(TAG, String.format(Locale.US, "Auto-delete deleted %d episodes (%d requested)", counter, numToRemove))
            return counter
        }
        public override fun getDefaultCleanupParameter(): Int {
            val cacheSize = getPref(AppPrefs.prefEpisodeCacheSize, "0").toInt()
            if (cacheSize > AppPreferences.EPISODE_CACHE_SIZE_UNLIMITED) {
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
                val downloadedItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.downloaded.name), EpisodeSortOrder.DATE_NEW_OLD)
                val idsInQueues = getInQueueEpisodeIds()
                for (item in downloadedItems) if (item.downloaded && !idsInQueues.contains(item.id) && !item.isSUPER) candidates.add(item)
                return candidates
            }
        override fun getReclaimableItems(): Int {
            return candidates.size
        }
         public override fun performCleanup(context: Context, numToRemove: Int): Int {
            var candidates = candidates
            // in the absence of better data, we'll sort by item publication date
            candidates = candidates.sortedWith { lhs: Episode, rhs: Episode ->
                val l = lhs.getPubDate()
                val r = rhs.getPubDate()
                l.compareTo(r)
            }
            val delete = if (candidates.size > numToRemove) candidates.subList(0, numToRemove) else candidates
            for (item in delete) {
                try { deleteAndRemoveFromQueues(context, item) } catch (e: InterruptedException) { Logs(TAG, e) } catch (e: ExecutionException) { Logs(TAG, e) }
            }
            val counter = delete.size
            Logt(TAG, String.format(Locale.US, "Auto-delete deleted %d episodes (%d requested)", counter, numToRemove))
            return counter
        }
        public override fun getDefaultCleanupParameter(): Int {
            return getNumEpisodesToCleanup(0)
        }
    }

    /**
     * A cleanup algorithm that never removes anything
     */
    class APNullCleanupAlgorithm : EpisodeCleanupAlgorithm() {
        public override fun performCleanup(context: Context, numToRemove: Int): Int {
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

    class APCleanupAlgorithm(@JvmField val numberOfHoursAfterPlayback: Int) : EpisodeCleanupAlgorithm() {
        private val candidates: List<Episode>
            get() {
                val candidates: MutableList<Episode> = mutableListOf()
                val downloadedItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.downloaded.name), EpisodeSortOrder.DATE_NEW_OLD)
                val idsInQueues = getInQueueEpisodeIds()
                val mostRecentDateForDeletion = calcMostRecentDateForDeletion(Date())
                for (item in downloadedItems) {
                    if (item.downloaded && !idsInQueues.contains(item.id) && item.playState >= EpisodeState.PLAYED.code && !item.isSUPER) {
                        // make sure this candidate was played at least the proper amount of days prior to now
                        if (item.playbackCompletionDate != null && item.playbackCompletionDate!!.before(mostRecentDateForDeletion)) candidates.add(item)
                    }
                }
                return candidates
            }
        override fun getReclaimableItems(): Int {
            return candidates.size
        }
         public override fun performCleanup(context: Context, numToRemove: Int): Int {
            val candidates = candidates.toMutableList()
            candidates.sortWith { lhs: Episode, rhs: Episode ->
                var l = lhs.playbackCompletionDate
                var r = rhs.playbackCompletionDate
                if (l == null) l = Date()
                if (r == null) r = Date()
                l.compareTo(r)
            }
            val delete = if (candidates.size > numToRemove) candidates.subList(0, numToRemove) else candidates
            for (item in delete) {
                try { deleteAndRemoveFromQueues(context, item) } catch (e: InterruptedException) { Logs(TAG, e) } catch (e: ExecutionException) { Logs(TAG, e) }
            }
            val counter = delete.size
            Logt(TAG, String.format(Locale.US, "Auto-delete deleted %d episodes (%d requested)", counter, numToRemove))
            return counter
        }
        fun calcMostRecentDateForDeletion(currentDate: Date): Date = minusHours(currentDate, numberOfHoursAfterPlayback)
        public override fun getDefaultCleanupParameter(): Int = getNumEpisodesToCleanup(0)
        companion object {
            private fun minusHours(baseDate: Date, numberOfHours: Int): Date {
                val cal = Calendar.getInstance()
                cal.time = baseDate
                cal.add(Calendar.HOUR_OF_DAY, -1 * numberOfHours)
                return cal.time
            }
        }
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
        protected abstract fun performCleanup(context: Context, numToRemove: Int): Int

        //    only used in tests
        fun performCleanup(context: Context): Int {
            val numToRemove = getDefaultCleanupParameter()
            if (numToRemove <= 0) return 0
            return performCleanup(context, numToRemove)
        }
        /**
         * Returns a parameter for performCleanup. The implementation of this interface should decide how much
         * space to free to satisfy the episode cache conditions. If the conditions are already satisfied, this
         * method should not have any effects.
         */
        protected abstract fun getDefaultCleanupParameter(): Int
        /**
         * Cleans up just enough episodes to make room for the requested number
         * @param context            Can be used for accessing the database
         * @param amountOfRoomNeeded the number of episodes we need space for
         * @return The number of epiosdes that were deleted
         */
        fun makeRoomForEpisodes(context: Context, amountOfRoomNeeded: Int): Int {
            val numToRemove = getNumEpisodesToCleanup(amountOfRoomNeeded)
            Logd("EpisodeCleanupAlgorithm", "makeRoomForEpisodes: $numToRemove")
            if (numToRemove <= 0) return 0
            return performCleanup(context, numToRemove)
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
            if (amountOfRoomNeeded >= 0 && getPref(AppPrefs.prefEpisodeCacheSize, "20").toInt() > AppPreferences.EPISODE_CACHE_SIZE_UNLIMITED) {
                val downloadedEpisodes = getEpisodesCount(EpisodeFilter(EpisodeFilter.States.downloaded.name))
                if (downloadedEpisodes + amountOfRoomNeeded >= getPref(AppPrefs.prefEpisodeCacheSize, "20").toInt()) return (downloadedEpisodes + amountOfRoomNeeded - getPref(AppPrefs.prefEpisodeCacheSize, "20").toInt())
            }
            return 0
        }
    }
}
